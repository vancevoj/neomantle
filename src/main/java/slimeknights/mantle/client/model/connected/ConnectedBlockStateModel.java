package slimeknights.mantle.client.model.connected;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.block.IMultipartConnectedBlock;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ExtraTextureContext;
import slimeknights.mantle.client.model.util.GeometryContextWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dynamic block state model for {@link ConnectedModel}, choosing texture variants based on the neighboring blocks.
 * Used in blockstate JSON as a variant with {@code "type": "mantle:connected"}, for example {@code {"type": "mantle:connected", "model": "mantle:block/example"}}.
 * Works in multipart blockstates, in which case it is recommended to use {@link IMultipartConnectedBlock} for when the model is rendered without a level.
 */
public class ConnectedBlockStateModel extends DynamicBakedWrapper<BlockStateModel> {
  /** ID of this block state model type */
  public static final Identifier ID = Mantle.getResource("connected");

  /** Connected model logic */
  private final ConnectedModel model;
  /** Base model part, used for no connections */
  private final BlockStateModelPart base;
  /** Baker used to rebake quads */
  private final ModelBaker baker;
  /** Elements in the model, may come from a parent */
  private final List<CuboidModelElement> elements;
  /** Materials for all textures including suffixed textures */
  private final GeometryContextWrapper materials;
  /** Model transforms for baking, including the root transform */
  private final ModelState transform;
  /** Model rotation, used to rotate the sides when checking neighbors */
  private final Transformation rotation;
  /** Map of texture name to the connected texture it references */
  private final Map<String,String> connectedNames;
  /** If true, the model uses ambient occlusion */
  private final boolean ambientOcclusion;
  /** Cache of model parts for each connection combination */
  private final AtomicReferenceArray<BlockStateModelPart> cache = new AtomicReferenceArray<>(64);

  public ConnectedBlockStateModel(BlockStateModelPart base, ModelBaker baker, ResolvedModel resolved, ConnectedModel model, ModelState state) {
    super(new SingleVariant(base));
    this.model = model;
    this.base = base;
    this.baker = ModelHelper.dynamicBaker(baker);
    this.elements = model.getElements(resolved);
    this.transform = ModelHelper.applyRootTransform(state, resolved.getTopAdditionalProperties());
    this.rotation = state.transformation();
    this.ambientOcclusion = resolved.getTopAmbientOcclusion();

    // resolve suffixed textures now so missing textures get reported
    TextureSlots slots = resolved.getTopTextureSlots();
    Map<String,Material.Baked> extraTextures = new HashMap<>();
    for (Entry<String,Material> entry : model.getExtraTextures(slots).entrySet()) {
      extraTextures.put(entry.getKey(), baker.materials().get(entry.getValue(), resolved));
    }
    String debugName = resolved.debugName();
    this.materials = new ExtraTextureContext(new GeometryContextWrapper(this.baker, slots, () -> debugName), Map.copyOf(extraTextures));
    this.connectedNames = ModelHelper.findReferences(resolved, model.getConnectedTextures().keySet());

    // all directions false gives cache key of 0, that is ourself
    this.cache.set(0, base);
  }

  /**
   * Gets the direction rotated
   * @param direction  Original direction to rotate
   * @param rotation   Rotation origin, aka the face of the block we are looking at. As a result, UP is identity
   * @return  Rotated direction
   */
  private static Direction rotateDirection(Direction direction, Direction rotation) {
    if (rotation == Direction.UP) {
      return direction;
    }
    if (rotation == Direction.DOWN) {
      // Z is backwards on the bottom
      if (direction.getAxis() == Axis.Z) {
        return direction.getOpposite();
      }
      // X is normal
      return direction;
    }
    // sides all just have the next side for left and right, and consistent up and down
    return switch (direction) {
      case NORTH -> Direction.UP;
      case SOUTH -> Direction.DOWN;
      case EAST -> rotation.getCounterClockWise();
      case WEST -> rotation.getClockWise();
      default -> throw new IllegalArgumentException("Direction must be horizontal axis");
    };
  }

  /**
   * Gets a transform function based on the block part UV and block face
   * @param side   Block face in question
   * @param face   Block face data
   * @return  Direction transform function
   */
  private static Function<Direction,Direction> getTransform(Direction side, CuboidFace face) {
    // TODO: how do I apply UV lock?
    // final transform switches from face (NSWE) to world direction, the rest are composed in to apply first
    Function<Direction,Direction> transform = (d) -> rotateDirection(d, side);

    // flipping, default UVs are never flipped
    CuboidFace.UVs uvs = face.uvs();
    if (uvs != null) {
      boolean flipV = uvs.minV() > uvs.maxV();
      if (uvs.minU() > uvs.maxU()) {
        // flip both
        if (flipV) {
          transform = transform.compose(Direction::getOpposite);
        } else {
          // flip U
          transform = transform.compose((d) -> {
            if (d.getAxis() == Axis.X) {
              return d.getOpposite();
            }
            return d;
          });
        }
      } else if (flipV) {
        transform = transform.compose((d) -> {
          if (d.getAxis() == Axis.Z) {
            return d.getOpposite();
          }
          return d;
        });
      }
    }

    // rotation
    return switch (face.rotation()) {
      // 90 degrees
      case R90 -> transform.compose(Direction::getClockWise);
      case R180 -> transform.compose(Direction::getOpposite);
      case R270 -> transform.compose(Direction::getCounterClockWise);
      default -> transform;
    };
  }

  /**
   * Gets the texture suffix
   * @param texture      Texture name, must be a connected texture
   * @param connections  Connections byte
   * @param transform    Rotations to apply to faces
   * @return  Key used to cache it
   */
  private String getTextureSuffix(String texture, byte connections, Function<Direction,Direction> transform) {
    int key = 0;
    for (Direction dir : Plane.HORIZONTAL) {
      int flag = 1 << transform.apply(dir).get3DDataValue();
      if ((connections & flag) == flag) {
        key |= 1 << dir.get2DDataValue();
      }
    }
    // if empty, do not prefix
    String[] suffixes = model.getConnectedTextures().get(texture);
    assert suffixes != null;
    String suffix = suffixes[key];
    if (suffix.isEmpty()) {
      return suffix;
    }
    return "_" + suffix;
  }

  /**
   * Gets the model based on the connections in the given model data
   * @param connections  Array of face connections, true at indexes of connected sides
   * @return  Model with connections applied
   */
  private BlockStateModelPart applyConnections(byte connections) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    List<ColorData> colorData = model.getColorData();
    int size = elements.size();
    for (int i = 0; i < size; i++) {
      CuboidModelElement part = elements.get(i);
      ColorData colors = ColoredBlockModel.getColorData(colorData, i);
      for (Entry<Direction,CuboidFace> entry : part.faces().entrySet()) {
        Direction side = entry.getKey();
        if (!SimpleBlockModel.shouldDrawFace(part, side)) {
          continue;
        }
        // first, determine which texture to use on this side
        CuboidFace face = entry.getValue();
        String texture = ModelHelper.stripReference(face.texture());

        // follow the texture name back to the original name
        // if it never reaches a connected texture, skip
        String connectedTexture = connectedNames.get(texture);
        if (connectedTexture != null) {
          // if empty string, we can keep the old face
          String suffix = getTextureSuffix(connectedTexture, connections, getTransform(side, face));
          if (!suffix.isEmpty()) {
            // suffix the texture
            texture = connectedTexture + suffix;
          }
        }
        // bake the face with the updated texture
        BakedQuad quad = ColoredBlockModel.bakeFace(baker, part, side, face, materials.apply(texture), colors, transform);
        SimpleBlockModel.addQuad(builder, face, quad, transform);
      }
    }
    return new SimpleModelWrapper(builder.build(), ambientOcclusion, base.particleMaterial());
  }

  /**
   * Gets an array of directions to whether a block exists on the side, indexed using direction indexes
   * @param predicate  Function that returns true if the block is connected on the given side
   * @return  Boolean array of data
   */
  private static byte getConnections(Predicate<Direction> predicate) {
    byte connections = 0;
    for (Direction dir : Direction.values()) {
      if (predicate.test(dir)) {
        connections |= (byte) (1 << dir.get3DDataValue());
      }
    }
    return connections;
  }

  /**
   * Gets the connections for the block at the given position
   * @param level  Level containing the block
   * @param pos    Block position
   * @param state  Block state
   * @return  Byte with 6 bits for the 6 different sides
   */
  private byte getConnections(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    // no level means we cannot check neighbors, so fall back to block state properties
    // relevant for multipart models rendered outside the world
    if (level == BlockAndTintGetter.EMPTY) {
      return getConnections(dir -> {
        if (!model.getSides().contains(dir)) {
          return false;
        }
        BooleanProperty prop = IMultipartConnectedBlock.CONNECTED_DIRECTIONS.get(rotation.rotateTransform(dir));
        return state.hasProperty(prop) && state.getValue(prop);
      });
    }
    return getConnections(dir -> model.getSides().contains(dir) && model.getConnectionPredicate().test(state, level.getBlockState(pos.relative(rotation.rotateTransform(dir)))));
  }

  /**
   * Gets the model part for the given connections, baking it if missing
   * @param connections  Byte with 6 bits for the 6 different sides
   * @return  Model part for the given connections
   */
  protected BlockStateModelPart getCachedPart(byte connections) {
    BlockStateModelPart part = cache.get(connections);
    if (part == null) {
      // bake a new model if the orientation is not yet baked, meshing runs on multiple threads so the first to finish wins
      part = applyConnections(connections);
      if (!cache.compareAndSet(connections, null, part)) {
        part = cache.get(connections);
      }
    }
    return part;
  }

  @Override
  public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
    return new GeometryKey(this, getConnections(level, pos, state));
  }

  @Override
  public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
    parts.add(getCachedPart(getConnections(level, pos, state)));
  }

  @Override
  public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return base.particleMaterial();
  }

  @Override
  @BakedQuad.MaterialFlags
  public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return getCachedPart(getConnections(level, pos, state)).materialFlags();
  }

  /** Key for geometry caching */
  private record GeometryKey(ConnectedBlockStateModel model, byte connections) {}

  /** Unbaked model, parsed from blockstate JSON */
  public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<Unbaked> CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

    public Unbaked(Identifier model) {
      this(new Variant(model));
    }

    @Override
    public MapCodec<Unbaked> codec() {
      return CODEC;
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
      variant.resolveDependencies(resolver);
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
      ResolvedModel resolved = baker.getModel(variant.modelLocation());
      ModelState state = variant.modelState().asModelState();
      BlockStateModelPart base = SimpleModelWrapper.bake(baker, resolved, state);
      ConnectedModel model = ConnectedModel.find(resolved);
      if (model == null) {
        Mantle.logger.warn("Model {} used with blockstate type {} does not use the connected loader, it will not connect", variant.modelLocation(), ID);
        return new SingleVariant(base);
      }
      // model was rejected by vanilla, no sense connecting the missing model
      if (base == baker.missingBlockModelPart()) {
        return new SingleVariant(base);
      }
      return new ConnectedBlockStateModel(base, baker, resolved, model, state);
    }
  }
}
