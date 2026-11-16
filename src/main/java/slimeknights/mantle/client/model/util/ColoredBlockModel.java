package slimeknights.mantle.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.CuboidRotation;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.joml.Vector3fc;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.LogicHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Block model for setting color, luminosity, and per element uv lock. Similar to {@link MantleItemLayerModel} but for blocks.
 * Unlike {@link SimpleBlockModel}, the colors are applied to the parent elements if this model defines no elements.
 */
@SuppressWarnings("unused")  // API
public class ColoredBlockModel extends SimpleBlockModel {
  /** Model loader to allow doing basic coloring outside of other models */
  public static final UnbakedModelLoader<ColoredBlockModel> LOADER = ColoredBlockModel::deserialize;

  /** Colors to use for each piece */
  private final List<ColorData> colorData;

  /**
   * Creates a new colored block model
   * @param parameters  Standard model parameters, including parent and textures
   * @param elements    List of parts in the model, null to use the parent parts
   * @param colorData   Additional information about colors in the model
   */
  public ColoredBlockModel(StandardModelParameters parameters, @Nullable List<CuboidModelElement> elements, List<ColorData> colorData) {
    super(parameters, elements);
    this.colorData = colorData;
  }

  public ColoredBlockModel(SimpleBlockModel base, List<ColorData> colorData) {
    super(base);
    this.colorData = colorData;
  }

  public ColoredBlockModel(ColoredBlockModel base) {
    this(base, base.colorData);
  }

  /** Gets the colors to use for each element */
  public List<ColorData> getColorData() {
    return colorData;
  }

  @Override
  public UnbakedGeometry geometry() {
    // always use our geometry, as we need to apply colors to parent elements
    return new Geometry(this);
  }

  /**
   * Bakes a single face with the given color data
   * @param baker     Model baker
   * @param part      Part containing the face
   * @param side      Side of the face
   * @param face      Face data
   * @param material  Material for the face
   * @param colors    Color data for the part
   * @param state     Model state, will be updated with the part UV lock
   * @return  Baked quad
   */
  public static BakedQuad bakeFace(ModelBaker baker, CuboidModelElement part, Direction side, CuboidFace face, Material.Baked material, ColorData colors, ModelState state) {
    int color = colors.color();
    // translucent colors require the translucent layer, which in 26.x is chosen from the material
    if (color != -1 && (color >>> 24) < 0xFF && !material.forceTranslucent()) {
      material = new Material.Baked(material.sprite(), true);
    }
    BakedQuad quad = bakeFace(baker, part, face, material, side, colors.uvlock() == null ? state : ModelHelper.withUvLock(state, colors.uvlock()), colors.luminosity());
    if (color != -1) {
      quad = applyColor(quad, color, baker.interner());
    }
    return quad;
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder    Quad collection builder
   * @param baker      Model baker, see {@link ModelHelper#dynamicBaker(ModelBaker)} for baking after model loading
   * @param part       Part to bake
   * @param colors     Color, luminosity, and UV lock for the part
   * @param materials  Material getter, see {@link ModelHelper#materialGetter} and {@link GeometryContextWrapper}
   * @param state      Model state including the root transform
   */
  public static void bakePart(QuadCollection.Builder builder, ModelBaker baker, CuboidModelElement part, ColorData colors, Function<String,Material.Baked> materials, ModelState state) {
    if (ColorData.DEFAULT.equals(colors)) {
      bakePart(builder, baker, part, materials, state);
      return;
    }
    for (Entry<Direction,CuboidFace> entry : part.faces().entrySet()) {
      Direction side = entry.getKey();
      if (shouldDrawFace(part, side)) {
        CuboidFace face = entry.getValue();
        addQuad(builder, face, bakeFace(baker, part, side, face, materials.apply(face.texture()), colors, state), state);
      }
    }
  }

  /**
   * Bakes a list of block part elements into quads
   * @param baker      Model baker
   * @param elements   Model elements
   * @param colorData  Color data for each element, elements past the end of the list use {@link ColorData#DEFAULT}
   * @param materials  Material getter
   * @param state      Model state including the root transform
   * @return  Baked quads
   */
  public static QuadCollection bakeModel(ModelBaker baker, List<CuboidModelElement> elements, List<ColorData> colorData, Function<String,Material.Baked> materials, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    int size = elements.size();
    for (int i = 0; i < size; i++) {
      bakePart(builder, baker, elements.get(i), getColorData(colorData, i), materials, state);
    }
    return builder.build();
  }

  /**
   * Gets the color data for the given element index
   * @param colorData  Color data list
   * @param index      Element index
   * @return  Color data, default if missing
   */
  public static ColorData getColorData(List<ColorData> colorData, int index) {
    return LogicHelper.getOrDefault(colorData, index, ColorData.DEFAULT);
  }

  @Override
  public QuadCollection bakeWithElements(ModelBaker baker, List<CuboidModelElement> elements, Function<String,Material.Baked> materials, ModelState state) {
    return bakeModel(baker, elements, colorData, materials, state);
  }

  /**
   * Data class for setting properties when baking colored elements
   */
  public record ColorData(int color, int luminosity, @Nullable Boolean uvlock) {
    public static final ColorData DEFAULT = new ColorData(-1, -1, null);
    public static final RecordLoadable<ColorData> LOADABLE = RecordLoadable.create(
      ColorLoadable.ALPHA.defaultField("color", false, ColorData::color),
      IntLoadable.range(-1, 15).defaultField("luminosity", -1, ColorData::luminosity),
      BooleanLoadable.INSTANCE.nullableField("uvlock", ColorData::uvlock),
      ColorData::new);
    public static final Loadable<List<ColorData>> LIST_LOADABLE = LOADABLE.list(0);

    /** Gets the UV lock for the given part */
    public boolean isUvLock(boolean defaultLock) {
      if (uvlock == null) {
        return defaultLock;
      }
      return uvlock;
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public static ColorData fromJson(JsonObject json) {
      return LOADABLE.deserialize(json);
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      LOADABLE.serialize(this, json);
      return json;
    }
  }


  /* Deserializing */

  /** Deserializes the model from JSON */
  public static ColoredBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);
    List<ColorData> colorData = ColorData.LIST_LOADABLE.getOrDefault(json, "colors", List.of());
    return new ColoredBlockModel(model, colorData);
  }


  /* Face bakery */

  /**
   * Converts an ARGB color to an ABGR color, as some formats such as {@link com.mojang.blaze3d.platform.NativeImage#setPixelABGR(int, int, int)} use ABGR.
   * Note that colors on quads in 26.x are ARGB, so no swap is needed for them.
   * This function doubles as its own inverse, not that its needed.
   * @param color  ARGB color
   * @return  ABGR color
   */
  public static int swapColorRedBlue(int color) {
    return (color & 0xFF00FF00) // alpha and green same spot
           | ((color >> 16) & 0x000000FF) // red moves to blue
           | ((color << 16) & 0x00FF0000); // blue moves to red
  }

  /**
   * Sets the color on the given quad, replacing any existing color
   * @param quad      Quad to color
   * @param color     Color in ARGB format
   * @param interner  Interner for the colors, may be null
   * @return  Colored quad
   */
  public static BakedQuad applyColor(BakedQuad quad, int color, @Nullable ModelBaker.Interner interner) {
    BakedColors colors = BakedColors.of(color);
    if (interner != null) {
      colors = interner.colors(colors);
    }
    if (colors.equals(quad.bakedColors())) {
      return quad;
    }
    return new BakedQuad(
      quad.position0(), quad.position1(), quad.position2(), quad.position3(),
      quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
      quad.direction(), quad.materialInfo(), quad.bakedNormals(), colors);
  }

  /**
   * Sets the color on all the given quads, replacing any existing color
   * @param quads  Quads to color
   * @param color  Color in ARGB format
   * @return  List of colored quads
   */
  public static List<BakedQuad> applyColor(List<BakedQuad> quads, int color) {
    List<BakedQuad> colored = new ArrayList<>(quads.size());
    for (BakedQuad quad : quads) {
      colored.add(applyColor(quad, color, null));
    }
    return colored;
  }

  /**
   * Quad transformer applying a static color. Quads are immutable in 26.x, so the transformer returns a new quad.
   * @param color  Color in ARGB format
   * @return  Transformer setting the color on the quad
   */
  public static UnaryOperator<BakedQuad> applyColorQuadTransformer(int color) {
    return quad -> applyColor(quad, color, null);
  }

  /**
   * Extension of {@link FaceBakery#bakeQuad(ModelBaker, Vector3fc, Vector3fc, CuboidFace, Material.Baked, Direction, ModelState, CuboidRotation, Direction, int)} with an emissivity override.
   * UV lock is part of the model state in 26.x, see {@link ModelHelper#withUvLock(ModelState, boolean)}.
   * @param baker       Model baker
   * @param part        Part containing the face
   * @param face        Face data
   * @param material    Material for the face
   * @param facing      Direction of the face
   * @param state       Model state for the face
   * @param emissivity  Emissivity for fullbright, -1 will leave the element in charge, 0-15 will override it
   * @return  Baked quad
   */
  public static BakedQuad bakeFace(ModelBaker baker, CuboidModelElement part, CuboidFace face, Material.Baked material, Direction facing, ModelState state, int emissivity) {
    return bakeQuad(baker, part.from(), part.to(), face, material, facing, state, part.rotation(), part.shadeDirectionOverride(), emissivity == -1 ? part.lightEmission() : emissivity);
  }

  /**
   * Bakes a single quad with emissivity override.
   * @param baker          Model baker
   * @param posFrom        Face start position
   * @param posTo          Face end position
   * @param face           Face data
   * @param material       Material for the face
   * @param facing         Direction of the face
   * @param state          Model state for the face, including UV lock
   * @param partRotation   Rotation for the part
   * @param shade          Shade direction override, null for normal shading. {@link Direction#UP} matches the old unshaded faces
   * @param emissivity     Emissivity from 0-15. Face data may increase it
   * @return  Baked quad
   */
  public static BakedQuad bakeQuad(ModelBaker baker, Vector3fc posFrom, Vector3fc posTo, CuboidFace face, Material.Baked material, Direction facing, ModelState state,
                                   @Nullable CuboidRotation partRotation, @Nullable Direction shade, int emissivity) {
    return FaceBakery.bakeQuad(baker, posFrom, posTo, face, material, facing, state, partRotation, shade, Math.max(emissivity, 0));
  }
}
