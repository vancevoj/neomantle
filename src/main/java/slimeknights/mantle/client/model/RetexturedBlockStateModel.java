package slimeknights.mantle.client.model;

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
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.GeometryContextWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic block state model for {@link RetexturedModel}, swapping the retextured textures for the particle texture of the block in {@link RetexturedHelper#BLOCK_PROPERTY}.
 * Used in blockstate JSON as a variant with {@code "type": "mantle:retextured"}, for example {@code {"type": "mantle:retextured", "model": "mantle:block/example", "y": 90}}.
 */
public class RetexturedBlockStateModel extends DynamicBakedWrapper<BlockStateModel> {
  /** ID of this block state model type */
  public static final Identifier ID = Mantle.getResource("retextured");

  /** Base model part, used when no texture is set */
  private final BlockStateModelPart base;
  /** Model logic for baking */
  private final RetexturedModel model;
  /** Baker used to rebake quads */
  private final ModelBaker baker;
  /** Elements in the model, may come from a parent */
  private final List<CuboidModelElement> elements;
  /** Original materials of the model */
  private final GeometryContextWrapper materials;
  /** Model transforms, including the root transform */
  private final ModelState transform;
  /** List of texture names that are retextured, including texture references */
  private final Set<String> retextured;
  /** If true, the model uses ambient occlusion */
  private final boolean ambientOcclusion;
  /** Cache of retextured model parts, the model is rebaked on resource reload so no need to clear it */
  private final Map<Material.Baked,BlockStateModelPart> cache = new ConcurrentHashMap<>();

  public RetexturedBlockStateModel(BlockStateModelPart base, ModelBaker baker, ResolvedModel resolved, RetexturedModel model, ModelState state) {
    super(new SingleVariant(base));
    this.base = base;
    this.model = model;
    this.baker = ModelHelper.dynamicBaker(baker);
    this.elements = model.getElements(resolved);
    String debugName = resolved.debugName();
    this.materials = new GeometryContextWrapper(this.baker, resolved.getTopTextureSlots(), () -> debugName);
    this.transform = ModelHelper.applyRootTransform(state, resolved.getTopAdditionalProperties());
    this.retextured = model.getAllRetextured(resolved);
    this.ambientOcclusion = resolved.getTopAmbientOcclusion();
  }

  /** Gets the texture block from the level */
  @Nullable
  private static Block getTexture(BlockAndTintGetter level, BlockPos pos) {
    return level.getModelData(pos).get(RetexturedHelper.BLOCK_PROPERTY);
  }

  /**
   * Gets the model with the given texture applied
   * @param texture  Texture material
   * @return  Retextured model
   */
  private BlockStateModelPart getRetexturedModel(Material.Baked texture) {
    Material.Baked particle = retextured.contains("particle") ? texture : base.particleMaterial();
    return new SimpleModelWrapper(model.bakeWithElements(baker, elements, new RetexturedModel.RetexturedContext(materials, retextured, texture), transform), ambientOcclusion, particle);
  }

  /**
   * Gets a cached retextured model, computing it if missing from the cache
   * @param block  Block determining the texture
   * @return  Retextured model
   */
  public BlockStateModelPart getCachedModel(Block block) {
    return cache.computeIfAbsent(ModelHelper.getParticleMaterial(block), this::getRetexturedModel);
  }

  /** Gets the model part for the given position */
  private BlockStateModelPart getPart(BlockAndTintGetter level, BlockPos pos) {
    Block block = getTexture(level, pos);
    if (block == null) {
      return base;
    }
    return getCachedModel(block);
  }

  @Override
  public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
    return new GeometryKey(this, getTexture(level, pos));
  }

  @Override
  public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
    parts.add(getPart(level, pos));
  }

  @Override
  public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    // if particle is retextured, fetch particle from the block
    if (retextured.contains("particle")) {
      Block block = getTexture(level, pos);
      if (block != null) {
        return ModelHelper.getParticleMaterial(block);
      }
    }
    return base.particleMaterial();
  }

  @Override
  @BakedQuad.MaterialFlags
  public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return getPart(level, pos).materialFlags();
  }

  /** Key for geometry caching */
  private record GeometryKey(RetexturedBlockStateModel model, @Nullable Block texture) {}

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
      RetexturedModel model = RetexturedModel.find(resolved);
      if (model == null) {
        Mantle.logger.warn("Model {} used with blockstate type {} does not use the retextured loader, it will not be retextured", variant.modelLocation(), ID);
        return new SingleVariant(base);
      }
      // model was rejected by vanilla, no sense retexturing the missing model
      if (base == baker.missingBlockModelPart()) {
        return new SingleVariant(base);
      }
      return new RetexturedBlockStateModel(base, baker, resolved, model, state);
    }
  }
}
