package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.joml.Vector3fc;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Block model supporting the standard model parameters plus elements, used as a base for Mantle model loaders.
 * If the model defines no elements, the elements of the parent model are used.
 */
@SuppressWarnings("WeakerAccess")
public class SimpleBlockModel extends AbstractUnbakedModel {
  /** Model loader for vanilla block model, mainly intended for use in fallback registration */
  public static final UnbakedModelLoader<SimpleBlockModel> LOADER = SimpleBlockModel::deserialize;
  /** Location used for baking dynamic models, name does not matter so just using a constant */
  public static final Identifier BAKE_LOCATION = Mantle.getResource("dynamic_model_baking");
  /** Debug name used for baking dynamic models */
  public static final ModelDebugName BAKE_NAME = BAKE_LOCATION::toString;

  /** Model parts for baked model, if null uses parent parts */
  @Nullable
  protected final List<CuboidModelElement> elements;

  /**
   * Creates a new simple block model
   * @param parameters  Standard model parameters, including parent and textures
   * @param elements    List of parts in the model, null to use the parent parts
   */
  public SimpleBlockModel(StandardModelParameters parameters, @Nullable List<CuboidModelElement> elements) {
    super(parameters);
    this.elements = elements;
  }

  public SimpleBlockModel(SimpleBlockModel base) {
    this(base.parameters, base.elements);
  }


  /* Properties */

  /** Gets the standard model parameters for this model */
  public StandardModelParameters getParameters() {
    return parameters;
  }

  /** Gets the parent model location, if set */
  @Nullable
  public Identifier getParentLocation() {
    return parameters.parent();
  }

  /** Gets the texture data defined by this model directly */
  public TextureSlots.Data getTextures() {
    return parameters.textures();
  }

  /**
   * Gets the elements defined directly in this model
   * @return  Elements, or null if this model uses its parent's elements
   */
  @Nullable
  public List<CuboidModelElement> getOwnElements() {
    return elements;
  }

  /**
   * Gets the elements in this simple block model, using the parent elements if this model has none
   * @param model  Resolved model, used to find parent elements. Generally either this model resolved or a child of this model.
   * @return  Elements in the model
   */
  public List<CuboidModelElement> getElements(@Nullable ResolvedModel model) {
    if (elements != null) {
      return elements;
    }
    return findElements(model);
  }

  /**
   * Gets the elements in this model using the debug name passed to geometry baking, which is the resolved model when baked by vanilla.
   * @param name  Debug name from geometry baking
   * @return  Elements in the model
   */
  public List<CuboidModelElement> getElements(ModelDebugName name) {
    if (elements != null) {
      return elements;
    }
    if (name instanceof ResolvedModel model) {
      return findElements(model);
    }
    Mantle.logger.warn("Unable to find parent elements for model {}, as it was not baked from a resolved model", name.debugName());
    return List.of();
  }

  /**
   * Finds the first list of elements in the model chain
   * @param model  Model to search, will search all parents
   * @return  List of elements, empty if none are found
   */
  public static List<CuboidModelElement> findElements(@Nullable ResolvedModel model) {
    for (ResolvedModel current = model; current != null; current = current.parent()) {
      UnbakedModel unbaked = current.wrapped();
      if (unbaked instanceof SimpleBlockModel simple) {
        if (simple.elements != null) {
          return simple.elements;
        }
      } else if (unbaked.geometry() instanceof UnbakedCuboidGeometry(List<CuboidModelElement> parentElements)) {
        return parentElements;
      }
    }
    return List.of();
  }

  /**
   * Finds a model of the given type in the model chain
   * @param model  Model to search, will search all parents
   * @param clazz  Model class
   * @param <T>  Model type
   * @return  Model instance, or null if not found
   */
  @Nullable
  public static <T extends UnbakedModel> T findModel(@Nullable ResolvedModel model, Class<T> clazz) {
    for (ResolvedModel current = model; current != null; current = current.parent()) {
      if (clazz.isInstance(current.wrapped())) {
        return clazz.cast(current.wrapped());
      }
    }
    return null;
  }

  @Nullable
  @Override
  public UnbakedGeometry geometry() {
    // no elements means use the parent geometry, matching vanilla
    if (elements == null) {
      return null;
    }
    return new Geometry(this);
  }


  /* Baking */

  /**
   * Applies the root transformation to the model state.
   * @param modelState      Model state from baking
   * @param transformation  Root transform, typically from {@link net.neoforged.neoforge.client.model.NeoForgeModelProperties#TRANSFORM}
   * @return  Model state with the root transform applied
   */
  public static ModelState applyTransform(ModelState modelState, @Nullable Transformation transformation) {
    if (transformation == null || transformation.isIdentity()) {
      return modelState;
    }
    return UnbakedElementsHelper.composeRootTransformIntoModelState(modelState, transformation);
  }

  /**
   * Adds a quad to the builder, applying the cull face
   * @param builder  Builder instance
   * @param face     Face being baked, used for cull face
   * @param quad     Quad to add
   * @param state    Model state, used to rotate the cull face
   */
  public static void addQuad(QuadCollection.Builder builder, CuboidFace face, BakedQuad quad, ModelState state) {
    Direction cull = face.cullForDirection();
    if (cull == null) {
      builder.addUnculledFace(quad);
    } else {
      builder.addCulledFace(Direction.rotate(state.transformation().getMatrix(), cull), quad);
    }
  }

  /**
   * Checks if the given face should be drawn on the element, skipping faces with no area like vanilla
   * @param part  Element
   * @param side  Face side
   * @return  True if the face has area
   */
  public static boolean shouldDrawFace(CuboidModelElement part, Direction side) {
    Vector3fc from = part.from();
    Vector3fc to = part.to();
    return switch (side.getAxis()) {
      case X -> from.y() != to.y() && from.z() != to.z();
      case Y -> from.x() != to.x() && from.z() != to.z();
      case Z -> from.x() != to.x() && from.y() != to.y();
    };
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder    Quad collection builder
   * @param baker      Model baker, see {@link ModelHelper#dynamicBaker(ModelBaker)} for baking after model loading
   * @param part       Part to bake
   * @param materials  Material getter, mapping a texture name to a material. See {@link ModelHelper#materialGetter} and {@link GeometryContextWrapper}
   * @param state      Model state including the root transform
   */
  public static void bakePart(QuadCollection.Builder builder, ModelBaker baker, CuboidModelElement part, Function<String,Material.Baked> materials, ModelState state) {
    for (Entry<Direction,CuboidFace> entry : part.faces().entrySet()) {
      Direction side = entry.getKey();
      if (shouldDrawFace(part, side)) {
        CuboidFace face = entry.getValue();
        BakedQuad quad = FaceBakery.bakeQuad(baker, part.from(), part.to(), face, materials.apply(face.texture()), side, state, part.rotation(), part.shadeDirectionOverride(), part.lightEmission());
        addQuad(builder, face, quad, state);
      }
    }
  }

  /**
   * Bakes a list of block part elements into quads
   * @param baker      Model baker
   * @param elements   Model elements
   * @param materials  Material getter
   * @param state      Model state including the root transform
   * @return  Baked quads
   */
  public static QuadCollection bakeModel(ModelBaker baker, List<CuboidModelElement> elements, Function<String,Material.Baked> materials, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    for (CuboidModelElement part : elements) {
      bakePart(builder, baker, part, materials, state);
    }
    return builder.build();
  }

  /**
   * Bakes the given elements using the logic of this model. Overridden by subclasses to add properties such as colors.
   * @param baker      Model baker
   * @param elements   Elements to bake
   * @param materials  Material getter
   * @param state      Model state including the root transform
   * @return  Baked quads
   */
  public QuadCollection bakeWithElements(ModelBaker baker, List<CuboidModelElement> elements, Function<String,Material.Baked> materials, ModelState state) {
    return bakeModel(baker, elements, materials, state);
  }

  /**
   * Bakes this model with the given materials, used by dynamic models to swap textures.
   * @param baker      Model baker
   * @param model      Resolved model for finding parent elements
   * @param materials  Material getter
   * @param state      Model state including the root transform
   * @return  Baked quads
   */
  public QuadCollection bakeDynamic(ModelBaker baker, @Nullable ResolvedModel model, Function<String,Material.Baked> materials, ModelState state) {
    return bakeWithElements(baker, getElements(model), materials, state);
  }

  /**
   * Bakes this model as a block model part with the given materials, used by dynamic models to swap textures
   * @param baker                Model baker
   * @param model                Resolved model for finding parent elements
   * @param materials            Material getter
   * @param state                Model state including the root transform
   * @param ambientOcclusion     If true, the part uses ambient occlusion
   * @param particle             Particle material
   * @return  Baked model part
   */
  public SimpleModelWrapper bakeDynamic(ModelBaker baker, @Nullable ResolvedModel model, Function<String,Material.Baked> materials, ModelState state, boolean ambientOcclusion, Material.Baked particle) {
    return new SimpleModelWrapper(bakeDynamic(baker, model, materials, state), ambientOcclusion, particle);
  }

  /** Geometry baking this model's elements through {@link #bakeWithElements(ModelBaker, List, Function, ModelState)} */
  protected record Geometry(SimpleBlockModel model) implements ExtendedUnbakedGeometry {
    @Override
    public QuadCollection bake(TextureSlots textureSlots, ModelBaker baker, ModelState state, ModelDebugName debugName, ContextMap additionalProperties) {
      return model.bakeWithElements(baker, model.getElements(debugName), ModelHelper.materialGetter(baker, textureSlots, debugName), ModelHelper.applyRootTransform(state, additionalProperties));
    }
  }


  /* Deserializing */

  /**
   * Deserializes a SimpleBlockModel from JSON
   * @param json     Json element containing the model
   * @param context  Json Context
   * @return  Serialized JSON
   */
  public static SimpleBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new SimpleBlockModel(StandardModelParameters.parse(json, context), deserializeElements(json, context));
  }

  /**
   * Gets the elements from the model JSON
   * @param json     Model JSON
   * @param context  JSON context
   * @return  Elements, or null if the model has none
   */
  @Nullable
  public static List<CuboidModelElement> deserializeElements(JsonObject json, JsonDeserializationContext context) {
    if (json.has("elements")) {
      return getModelElements(context, json.get("elements"), "elements");
    }
    return null;
  }

  /**
   * Gets a list of models from a JSON array
   * @param context  Json Context
   * @param element  Json array
   * @return  Model list
   */
  public static List<CuboidModelElement> getModelElements(JsonDeserializationContext context, JsonElement element, String name) {
    // if just one element, array is optional
    if (element.isJsonObject()) {
      // cast ensures we call List.of(CuboidModelElement) instead of List.of(CuboidModelElement[]) as the type is vague
      return List.of((CuboidModelElement)context.deserialize(element.getAsJsonObject(), CuboidModelElement.class));
    }
    // if an array, get array of elements
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      List<CuboidModelElement> builder = new ArrayList<>(array.size());
      for(JsonElement json : array) {
        builder.add(context.deserialize(json, CuboidModelElement.class));
      }
      return List.copyOf(builder);
    }

    throw new JsonSyntaxException("Missing " + name + ", expected to find a JsonArray or JsonObject");
  }

  /**
   * Gets a list of models from a JSON array
   * @param json  JSON object
   * @param key   Key to fetch
   * @param context  Json context
   * @return  Model list
   */
  public static List<CuboidModelElement> getModelElements(JsonObject json, String key, JsonDeserializationContext context) {
    return getModelElements(context, GsonHelper.getNonNull(json, key), key);
  }
}
