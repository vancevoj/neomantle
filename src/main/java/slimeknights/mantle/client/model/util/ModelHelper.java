package slimeknights.mantle.client.model.util;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.ComposedModelState;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import slimeknights.mantle.Mantle;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Utilities to help in custom models
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelHelper {
  private static final Map<Block,Identifier> TEXTURE_NAME_CACHE = new ConcurrentHashMap<>();
  /** Listener instance to clear cache */
  public static final ResourceManagerReloadListener LISTENER = manager -> TEXTURE_NAME_CACHE.clear();
  /** Interner used for quads baked by dynamic models after model baking finished */
  private static final ModelBaker.Interner DYNAMIC_INTERNER = new DynamicInterner();
  /** Model state that applies no UV lock to faces, used to remove UV lock from custom model states */
  private static final ModelState NO_UV_LOCK = new ModelState() {};

  /**
   * Gets the particle material for a block from the model manager. Not cached, so it is safe to call from meshing threads around a reload.
   * @param block  Block to fetch
   * @return Particle material for the block
   */
  @SuppressWarnings("deprecation")
  public static Material.Baked getParticleMaterial(Block block) {
    return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block.defaultBlockState());
  }

  /**
   * Gets the texture name for a block from the model manager
   * @param block  Block to fetch
   * @return Texture name for the block
   */
  private static Identifier getParticleTextureInternal(Block block) {
    Material.Baked particle = getParticleMaterial(block);
    //noinspection ConstantConditions  dumb mods returning null particle icons
    if (particle != null && particle.sprite() != null) {
      return particle.sprite().contents().name();
    }
    return MissingTextureAtlasSprite.getLocation();
  }

  /**
   * Gets the name of a particle texture for a block, using the cached value if present
   * @param block Block to fetch
   * @return Texture name for the block
   */
  public static Identifier getParticleTexture(Block block) {
    return TEXTURE_NAME_CACHE.computeIfAbsent(block, ModelHelper::getParticleTextureInternal);
  }


  /* Model baking */

  /**
   * Removes the reference prefix from a texture name, if present
   * @param texture  Texture name, may start with {@code #}
   * @return  Texture name without the {@code #}
   */
  public static String stripReference(String texture) {
    if (!texture.isEmpty() && texture.charAt(0) == '#') {
      return texture.substring(1);
    }
    return texture;
  }

  /**
   * Creates a material getter resolving texture names against the given texture slots. This is the replacement for {@code IGeometryBakingContext#getMaterial}.
   * @param baker  Model baker
   * @param slots  Resolved texture slots, typically from {@link ResolvedModel#getTopTextureSlots()}
   * @param name   Model name for errors
   * @return  Function from texture name (with or without {@code #}) to baked material
   */
  public static Function<String,Material.Baked> materialGetter(ModelBaker baker, TextureSlots slots, ModelDebugName name) {
    MaterialBaker materials = baker.materials();
    return texture -> materials.resolveSlot(slots, texture, name);
  }

  /**
   * Applies the root transform from the model's additional properties to the model state.
   * @param state       Model state
   * @param properties  Additional properties, from {@link ResolvedModel#getTopAdditionalProperties()} or the geometry bake method
   * @return  Model state including the root transform
   */
  public static ModelState applyRootTransform(ModelState state, ContextMap properties) {
    Transformation root = properties.get(NeoForgeModelProperties.TRANSFORM);
    if (root == null) {
      return state;
    }
    return UnbakedElementsHelper.composeRootTransformIntoModelState(state, root);
  }

  /**
   * Gets a model state with the same transformation but the given UV lock. Replacement for passing a separate UV lock boolean to face baking.
   * @param state   Base model state
   * @param uvlock  If true, UV lock is applied to faces
   * @return  Model state with the given UV lock
   */
  public static ModelState withUvLock(ModelState state, boolean uvlock) {
    ModelState faces = uvLockFaces(state, uvlock);
    if (faces == state) {
      return state;
    }
    return new UvLockModelState(state, faces);
  }

  /** Gets the model state to use for face transformations for the given UV lock */
  private static ModelState uvLockFaces(ModelState state, boolean uvlock) {
    return switch (state) {
      case BlockModelRotation rotation -> uvlock ? rotation.withUvLock() : rotation;
      case BlockModelRotation.WithUvLock locked -> uvlock ? locked : locked.parent();
      case ComposedModelState composed -> uvLockFaces(composed.parent(), uvlock);
      case UvLockModelState wrapped -> uvLockFaces(wrapped.faces(), uvlock);
      default -> uvlock ? new ComputedUvLock(state.transformation()) : NO_UV_LOCK;
    };
  }

  /**
   * Creates a model baker that can be used by dynamic models after model baking finished, for instance to rebake quads on meshing threads.
   * The returned baker cannot resolve models and does not cache shared operations, but it can bake quads and materials.
   * @param baker  Baker passed to the model during baking
   * @return  Baker that is safe to keep after baking finished
   */
  public static ModelBaker dynamicBaker(ModelBaker baker) {
    if (baker instanceof DynamicModelBaker) {
      return baker;
    }
    return new DynamicModelBaker(baker.materials(), baker.missingBlockModelPart());
  }

  /**
   * Adds a texture reference to the given texture data if the slot is not already defined.
   * Used to replace fallbacks such as {@code owner.hasMaterial("particle") ? owner.getMaterial("particle") : layer0}, though note it only checks the given data, not parents.
   * @param data    Texture data
   * @param slot    Slot that should be defined
   * @param target  Slot to reference if {@code slot} is undefined
   * @return  Texture data with the slot defined
   */
  public static TextureSlots.Data withDefaultReference(TextureSlots.Data data, String slot, String target) {
    if (data.values().containsKey(slot)) {
      return data;
    }
    Map<String,TextureSlots.SlotContents> values = new HashMap<>(data.values());
    // TextureSlots.Reference is private, so create it through the builder
    values.put(slot, new TextureSlots.Data.Builder().addReference(slot, target).build().values().get(slot));
    return new TextureSlots.Data(Map.copyOf(values));
  }

  /**
   * Finds all texture slots in the model chain that resolve through one of the given texture names.
   * Replacement for walking texture references in the old {@code BlockModel#textureMap}, as {@link TextureSlots} does not expose references.
   * @param model    Model to search
   * @param targets  Texture names that are being looked for
   * @return  Map from texture slot name to the target name it resolves through. Targets map to themselves.
   */
  public static Map<String,String> findReferences(ResolvedModel model, Collection<String> targets) {
    if (targets.isEmpty()) {
      return Map.of();
    }
    // override each target with a unique marker material, any slot resolving to the marker references that target
    List<String> targetList = List.copyOf(new LinkedHashSet<>(targets));
    Map<Identifier,String> markers = new HashMap<>();
    TextureSlots.Data.Builder overlay = new TextureSlots.Data.Builder();
    for (int i = 0; i < targetList.size(); i++) {
      Identifier marker = Mantle.getResource("texture_reference_marker/" + i);
      markers.put(marker, targetList.get(i));
      overlay.addTexture(targetList.get(i), new Material(marker));
    }
    TextureSlots.Resolver resolver = new TextureSlots.Resolver();
    resolver.addLast(overlay.build());
    // gather all slot names while adding the chain
    Set<String> slotNames = new LinkedHashSet<>(targetList);
    for (TextureSlots.Data data : ModelTextureIteratable.of(model)) {
      resolver.addLast(data);
      slotNames.addAll(data.values().keySet());
    }
    TextureSlots slots = resolver.resolve(model);
    Map<String,String> result = new HashMap<>();
    for (String slot : slotNames) {
      Material material = slots.getMaterial(slot);
      if (material != null) {
        String target = markers.get(material.sprite());
        if (target != null) {
          result.put(slot, target);
        }
      }
    }
    return Map.copyOf(result);
  }


  /* JSON */

  /**
   * Converts a JSON float array to the specified object
   * @param json    JSON object
   * @param name    Name of the array in the object to fetch
   * @param size    Expected array size
   * @param mapper  Functon to map from the array to the output type
   * @param <T> Output type
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.array.FloatArrayLoadable}
   */
  @Deprecated(forRemoval = true)
  public static <T> T arrayToObject(JsonObject json, String name, int size, Function<float[], T> mapper) {
    JsonArray array = GsonHelper.getAsJsonArray(json, name);
    if (array.size() != size) {
      throw new JsonParseException("Expected " + size + " " + name + " values, found: " + array.size());
    }
    float[] vec = new float[size];
    for(int i = 0; i < size; ++i) {
      vec[i] = GsonHelper.convertToFloat(array.get(i), name + "[" + i + "]");
    }
    return mapper.apply(vec);
  }

  /**
   * Converts a JSON array with 3 elements into a Vector3f
   * @param json  JSON object
   * @param name  Name of the array in the object to fetch
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable}
   */
  @Deprecated(forRemoval = true)
  public static Vector3f arrayToVector(JsonObject json, String name) {
    return arrayToObject(json, name, 3, arr -> new Vector3f(arr[0], arr[1], arr[2]));
  }

  /** @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable} */
  @Deprecated(forRemoval = true)
  public static JsonArray vectorToJson(Vector3f vector) {
    JsonArray array = new JsonArray();
    array.add(vector.x());
    array.add(vector.y());
    array.add(vector.z());
    return array;
  }

  /** Validates the given rotation is in 90 degree increments */
  public static boolean checkRotation(float rotation) {
    return rotation >= 0 && rotation % 90 == 0 && rotation <= 270;
  }

  /**
   * Gets a rotation from JSON
   * @param json  JSON parent
   * @return  Integer of 0, 90, 180, or 270
   */
  public static int getRotation(JsonObject json, String key) {
    int i = GsonHelper.getAsInt(json, key, 0);
    if (checkRotation(i)) {
      return i;
    } else {
      throw new JsonParseException("Invalid '" + key + "' " + i + " found, only 0/90/180/270 allowed");
    }
  }


  /* Helper classes */

  /** Model state using the transformation from one state and the face (UV lock) transformations from another */
  private record UvLockModelState(ModelState transform, ModelState faces) implements ModelState {
    @Override
    public Transformation transformation() {
      return transform.transformation();
    }

    @Override
    public Matrix4fc faceTransformation(Direction face) {
      return faces.faceTransformation(face);
    }

    @Override
    public Matrix4fc inverseFaceTransformation(Direction face) {
      return faces.inverseFaceTransformation(face);
    }

    @Override
    public boolean mayApplyArbitraryRotation() {
      return transform.mayApplyArbitraryRotation();
    }
  }

  /** UV lock face transformations computed from an arbitrary transformation, used when the model state is not a vanilla rotation */
  private static class ComputedUvLock implements ModelState {
    private final Transformation transformation;
    private final Map<Direction,Matrix4fc> faceMapping = new EnumMap<>(Direction.class);
    private final Map<Direction,Matrix4fc> inverseFaceMapping = new EnumMap<>(Direction.class);

    private ComputedUvLock(Transformation transformation) {
      this.transformation = transformation;
      for (Direction face : Direction.values()) {
        Matrix4fc faceTransform = BlockMath.getFaceTransformation(transformation, face).getMatrix();
        faceMapping.put(face, faceTransform);
        inverseFaceMapping.put(face, faceTransform.invertAffine(new Matrix4f()));
      }
    }

    @Override
    public Transformation transformation() {
      return transformation;
    }

    @Override
    public Matrix4fc faceTransformation(Direction face) {
      return faceMapping.getOrDefault(face, NO_TRANSFORM);
    }

    @Override
    public Matrix4fc inverseFaceTransformation(Direction face) {
      return inverseFaceMapping.getOrDefault(face, NO_TRANSFORM);
    }
  }

  /** Thread safe interner that does not hold onto values, used for quads baked after model loading */
  private static class DynamicInterner implements ModelBaker.Interner {
    private final Interner<Vector3fc> vectors = Interners.newWeakInterner();
    private final Interner<BakedQuad.MaterialInfo> materialInfos = Interners.newWeakInterner();
    private final Interner<BakedNormals> normals = Interners.newWeakInterner();
    private final Interner<BakedColors> colors = Interners.newWeakInterner();

    @Override
    public Vector3fc vector(Vector3fc vector) {
      return vectors.intern(vector);
    }

    @Override
    public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
      return materialInfos.intern(material);
    }

    @Override
    public BakedNormals normals(BakedNormals normals) {
      return this.normals.intern(normals);
    }

    @Override
    public BakedColors colors(BakedColors colors) {
      return this.colors.intern(colors);
    }
  }

  /** Model baker that survives model baking, used for rebaking quads in dynamic models */
  private record DynamicModelBaker(MaterialBaker materials, BlockStateModelPart missingBlockModelPart) implements ModelBaker {
    @Override
    public ResolvedModel getModel(Identifier location) {
      throw new IllegalStateException("Cannot resolve model " + location + " after model baking finished");
    }

    @Override
    public ModelBaker.Interner interner() {
      return DYNAMIC_INTERNER;
    }

    @Override
    public <T> T compute(ModelBaker.SharedOperationKey<T> key) {
      return key.compute(this);
    }
  }
}
