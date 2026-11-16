package slimeknights.mantle.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.TransformingVertexPipeline;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.mantle.util.LogicHelper;
import slimeknights.mantle.util.ReversedListBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

/**
 * Clone of the vanilla item layer generator to propagate a hardcoded color in, allows reducing rendering time by bypassing item tints for a static color.
 * Also supports luminosity, and when used as a model loader supports telling a layer to not use a tint index.
 * Unlike the vanilla generator, it avoids z-fighting between layers by not drawing side faces where a higher layer already has a pixel.
 */
public class MantleItemLayerModel extends AbstractUnbakedModel {
  /** Model loader instance */
  public static final UnbakedModelLoader<MantleItemLayerModel> LOADER = MantleItemLayerModel::deserialize;

  private static final Direction[] HORIZONTALS = {Direction.UP, Direction.DOWN};
  private static final Direction[] VERTICALS = {Direction.WEST, Direction.EAST};

  /** Layers in the model */
  @Getter
  private final List<LayerData> layers;
  /** Texture data including a default particle */
  private final TextureSlots.Data textures;

  public MantleItemLayerModel(StandardModelParameters parameters, List<LayerData> layers) {
    super(parameters);
    this.layers = layers;
    // if no particle is set, use layer0, matching old behavior
    this.textures = ModelHelper.withDefaultReference(parameters.textures(), "particle", "layer0");
  }

  /** Gets the layer at the given index */
  private LayerData getLayer(int index) {
    return LogicHelper.getOrDefault(layers, index, LayerData.DEFAULT);
  }

  @Override
  public TextureSlots.Data textureSlots() {
    return textures;
  }

  @Override
  public UnbakedGeometry geometry() {
    return (ExtendedUnbakedGeometry) this::bake;
  }

  /**
   * Applies the transformation to the model state for an item layer model.
   */
  public static ModelState applyTransform(ModelState modelState, Transformation transformation) {
    if (transformation.isIdentity()) {
      return modelState;
    } else {
      return UnbakedElementsHelper.composeRootTransformIntoModelState(modelState, transformation);
    }
  }

  /**
   * Gets the transformation to pass into {@link #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)} for the given model state.
   * Model states in 26.x transform around the block center, while the quads for sprites are built with coordinates from 0 to 1.
   * @param modelState  Model state, see {@link #applyTransform(ModelState, Transformation)} to include the root transform
   * @return  Transformation for item layer quads
   */
  public static Transformation getItemTransform(ModelState modelState) {
    return modelState.transformation().blockCenterToCorner();
  }

  /** Bakes the quads for this model */
  private QuadCollection bake(TextureSlots textureSlots, ModelBaker baker, ModelState modelState, ModelDebugName name, ContextMap additionalProperties) {
    List<Material> textures = new ArrayList<>();
    for (int i = 0; ; i++) {
      Material material = textureSlots.getMaterial("layer" + i);
      if (material == null) {
        break;
      }
      textures.add(material);
    }
    if (textures.isEmpty()) {
      throw new IllegalStateException("Empty textures list in " + name.debugName());
    }

    // setup quad building
    ReversedListBuilder<Collection<BakedQuad>> quadBuilder = new ReversedListBuilder<>();
    // skip the pixel tracking if using a single texture only
    ItemLayerPixels pixels = textures.size() == 1 ? null : new ItemLayerPixels();
    Transformation transform = getItemTransform(ModelHelper.applyRootTransform(modelState, additionalProperties));
    for (int i = textures.size() - 1; i >= 0; i--) {
      Material.Baked material = baker.materials().get(textures.get(i), name);
      LayerData data = getLayer(i);
      if (data.isTranslucent() && !material.forceTranslucent()) {
        material = new Material.Baked(material.sprite(), true);
      }
      quadBuilder.add(getQuadsForSprite(data.color(), data.noTint() ? -1 : i, material, transform, data.luminosity(), pixels, baker.interner()));
    }

    // build final model
    QuadCollection.Builder builder = new QuadCollection.Builder();
    quadBuilder.build(quads -> quads.forEach(builder::addUnculledFace));
    return builder.build();
  }

  /**
   * Gets all quads for an item layer for the given sprite
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.item.ItemTintSource}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply, see {@link #getItemTransform(ModelState)}
   * @param luminosity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @return  List of baked quads
   */
  public static List<BakedQuad> getQuadsForSprite(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int luminosity) {
    return getQuadsForSprite(color, tint, sprite, transform, luminosity, null);
  }

  /**
   * Gets all quads including side quads for an item layer for the given sprite.
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.item.ItemTintSource}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply, see {@link #getItemTransform(ModelState)}
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @param pixels      Object to keep track of used pixels across multiple layers to help prevent z-fighting. To effective use, sprites must be built in reverse order. Use null to skip this logic
   * @return  List of baked quads
   * @see #getQuadForGui(int, int, TextureAtlasSprite, Transformation, int)
   */
  public static List<BakedQuad> getQuadsForSprite(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int emissivity, @Nullable ItemLayerPixels pixels) {
    return getQuadsForSprite(color, tint, new Material.Baked(sprite, false), transform, emissivity, pixels, null);
  }

  /**
   * Gets all quads including side quads for an item layer for the given material.
   * @param color       Color for the sprite in AARRGGBB format. If the alpha is less than 255, the quads are placed in the translucent layer.
   * @param tint        Tint index for {@link net.minecraft.client.color.item.ItemTintSource}. Generally unused
   * @param material    Material to convert into quads, determines the sprite and whether it is forced translucent
   * @param transform   Transforms to apply, see {@link #getItemTransform(ModelState)}
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @param pixels      Object to keep track of used pixels across multiple layers to help prevent z-fighting. To effective use, sprites must be built in reverse order. Use null to skip this logic
   * @param interner    Interner from the model baker to reduce memory usage, null to skip interning
   * @return  List of baked quads
   */
  public static List<BakedQuad> getQuadsForSprite(int color, int tint, Material.Baked material, Transformation transform, int emissivity, @Nullable ItemLayerPixels pixels, @Nullable ModelBaker.Interner interner) {
    List<BakedQuad> builder = new ArrayList<>();

    TextureAtlasSprite sprite = material.sprite();
    SpriteContents contents = sprite.contents();
    int uMax = contents.width();
    int vMax = contents.height();
    FaceData faceData = new FaceData(uMax, vMax);
    boolean translucent = false;

    IntList frames = contents.getUniqueFrames();
    boolean hasFrames = !frames.isEmpty();
    for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
      int f = frames.getInt(frameIndex);
      boolean ptu;
      boolean[] ptv = new boolean[uMax];
      Arrays.fill(ptv, true);
      for(int v = 0; v < vMax; v++) {
        ptu = true;
        for(int u = 0; u < uMax; u++) {
          // pixel is ARGB, alpha is the high byte in both ARGB and ABGR
          int alpha = sprite.getPixelARGB(f, u, vMax - v - 1) >>> 24;
          boolean t = alpha / 255f <= 0.1f;

          if (!t && alpha < 255) {
            translucent = true;
          }

          if(ptu && !t) { // left - transparent, right - opaque
            faceData.set(Direction.WEST, u, v);
          }
          if(!ptu && t) { // left - opaque, right - transparent
            faceData.set(Direction.EAST, u-1, v);
          }
          if(ptv[u] && !t) { // up - transparent, down - opaque
            faceData.set(Direction.UP, u, v);
          }
          if(!ptv[u] && t) { // up - opaque, down - transparent
            faceData.set(Direction.DOWN, u, v-1);
          }

          ptu = t;
          ptv[u] = t;
        }
        if(!ptu) { // last - opaque
          faceData.set(Direction.EAST, uMax-1, v);
        }
      }
      // last line
      for(int u = 0; u < uMax; u++) {
        if(!ptv[u]) {
          faceData.set(Direction.DOWN, u, vMax-1);
        }
      }
    }

    // setup quad builder
    QuadBakingVertexConsumer quadBuilder = new QuadBakingVertexConsumer();
    // only need to set up transforms once, isn't that nice?
    VertexConsumer quadConsumer = quadBuilder;
    if (!transform.isIdentity()) {
      quadConsumer = new TransformingVertexPipeline(quadBuilder, transform);
    }
    QuadSettings settings = new QuadSettings(getMaterial(material, color), tint, emissivity, interner);

    // horizontal quads
    for (Direction facing : HORIZONTALS) {
      for (int v = 0; v < vMax; v++) {
        int uStart = 0, uEnd = uMax;
        boolean building = false;
        for (int u = 0; u < uMax; u++) {
          boolean canDraw = pixels == null || !pixels.get(u, v, uMax, vMax);
          boolean face = canDraw && faceData.get(facing, u, v);
          // set the end for translucent to draw right after this pixel
          if (face) {
            uEnd = u + 1;
            // if not currently building and we have data, start new quad
            if (!building) {
              building = true;
              uStart = u;
            }
          }
          // make quad [uStart, u]
          else if (building) {
            // finish current quad if translucent (minimize overdraw) or we are forbidden from touching this pixel (previous layer drew here)
            if (!canDraw || translucent) {
              int off = facing == Direction.DOWN ? 1 : 0;
              buildSideQuad(builder, quadBuilder, quadConsumer, settings, facing, color, sprite, uStart, v + off, uEnd - uStart);
              building = false;
            }
          }
        }
        if (building) { // build remaining quad
          // make quad [uStart, uEnd]
          int off = facing == Direction.DOWN ? 1 : 0;
          buildSideQuad(builder, quadBuilder, quadConsumer, settings, facing, color, sprite, uStart, v+off, uEnd-uStart);
        }
      }
    }

    // vertical quads
    for (Direction facing : VERTICALS) {
      for (int u = 0; u < uMax; u++) {
        int vStart = 0, vEnd = vMax;
        boolean building = false;
        for (int v = 0; v < vMax; v++) {
          boolean canDraw = pixels == null || !pixels.get(u, v, uMax, vMax);
          boolean face = canDraw && faceData.get(facing, u, v);
          // set the end for translucent to draw right after this pixel
          if (face) {
            vEnd = v + 1;
            // if not currently building and we have data, start new quad
            if (!building) {
              building = true;
              vStart = v;
            }
          }
          // make quad [vStart, v]
          else if (building) {
            // finish current quad if translucent (minimize overdraw) or we are forbidden from touching this pixel (future layer drew here)
            if (!canDraw || translucent) {
              int off = facing == Direction.EAST ? 1 : 0;
              buildSideQuad(builder, quadBuilder, quadConsumer, settings, facing, color, sprite, u + off, vStart, vEnd - vStart);
              building = false;
            }
          }
        }
        if (building) { // build remaining quad
          // make quad [vStart, vEnd]
          int off = facing == Direction.EAST ? 1 : 0;
          buildSideQuad(builder, quadBuilder, quadConsumer, settings, facing, color, sprite, u+off, vStart, vEnd-vStart);
        }
      }
    }

    // back
    buildQuad(builder, quadBuilder, quadConsumer, settings, Direction.NORTH, color,
              0, 0, 7.5f / 16f, sprite.getU0(), sprite.getV1(),
              0, 1, 7.5f / 16f, sprite.getU0(), sprite.getV0(),
              1, 1, 7.5f / 16f, sprite.getU1(), sprite.getV0(),
              1, 0, 7.5f / 16f, sprite.getU1(), sprite.getV1());
    // front
    buildQuad(builder, quadBuilder, quadConsumer, settings, Direction.SOUTH, color,
              0, 0, 8.5f / 16f, sprite.getU0(), sprite.getV1(),
              1, 0, 8.5f / 16f, sprite.getU1(), sprite.getV1(),
              1, 1, 8.5f / 16f, sprite.getU1(), sprite.getV0(),
              0, 1, 8.5f / 16f, sprite.getU0(), sprite.getV0());

    // fill in the pixel map with new pixels from the sprite
    if (pixels != null) {
      // animated textures are tricky, as we have three choices:
      //  1. if a pixel is only potentially there, don't draw lower layers - leads to gaps
      //  2. if a pixel is only potentially there, always draw lower layers - leads to z-fighting
      //  3. only use the first frame
      // of these, 2 would give the most accurate result. However, its also the hardest to calculate
      // of the remaining methods, 3 is both more accurate and easier to calculate than 1, so I opted for that approach
      if (hasFrames) {
        for(int v = 0; v < vMax; v++) {
          for(int u = 0; u < uMax; u++) {
            int alpha = sprite.getPixelARGB(0, u, vMax - v - 1) >>> 24;
            if (alpha / 255f > 0.1f) {
              pixels.set(u, v, uMax, vMax);
            }
          }
        }
      }
    }

    return List.copyOf(builder);
  }

  /**
   * Gets the quad to display in GUIs for the given sprite. Unlike {@link #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)}, this method will not build side quads as GUIs don't see those.
   * This method is notably more efficient when you are only building a GUI model, if you want a full model its probably more efficient to call the other method then filter out {@link Direction#SOUTH} quads.
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.item.ItemTintSource}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @return  List of baked quads
   * @see #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)
   */
  @SuppressWarnings("unused")  // API
  public static BakedQuad getQuadForGui(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int emissivity) {
    return getQuadForGui(color, tint, new Material.Baked(sprite, false), transform, emissivity, null);
  }

  /**
   * Gets the quad to display in GUIs for the given material.
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index, generally unused
   * @param material    Material to convert into quads
   * @param transform   Transforms to apply
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @param interner    Interner from the model baker, null to skip interning
   * @return  Baked quad
   */
  public static BakedQuad getQuadForGui(int color, int tint, Material.Baked material, Transformation transform, int emissivity, @Nullable ModelBaker.Interner interner) {
    TextureAtlasSprite sprite = material.sprite();
    // setup quad builder
    QuadBakingVertexConsumer quadBuilder = new QuadBakingVertexConsumer();
    // only need to set up transforms once, isn't that nice?
    VertexConsumer quadConsumer = quadBuilder;
    if (!transform.isIdentity()) {
      quadConsumer = new TransformingVertexPipeline(quadBuilder, transform);
    }
    // only need south
    List<BakedQuad> output = new ArrayList<>(1);
    buildQuad(output, quadBuilder, quadConsumer, new QuadSettings(getMaterial(material, color), tint, emissivity, interner), Direction.SOUTH, color,
              0, 0, 8.5f / 16f, sprite.getU0(), sprite.getV1(),
              1, 0, 8.5f / 16f, sprite.getU1(), sprite.getV1(),
              1, 1, 8.5f / 16f, sprite.getU1(), sprite.getV0(),
              0, 1, 8.5f / 16f, sprite.getU0(), sprite.getV0());
    return output.getFirst();
  }

  /** Gets the material to use for quads, forcing translucency if the color is translucent */
  private static Material.Baked getMaterial(Material.Baked material, int color) {
    if ((color >>> 24) < 0xFF && !material.forceTranslucent()) {
      return new Material.Baked(material.sprite(), true);
    }
    return material;
  }

  /** Settings shared by all quads in a layer */
  private record QuadSettings(Material.Baked material, int tint, int luminosity, @Nullable ModelBaker.Interner interner) {}

  /**
   * Builds a single quad on the side of the sprite
   * @param output     List of quads to add the quad into
   * @param builder    Quad builder instance, just used to set direction so we can avoid redundancy
   * @param consumer   Quad consumer, where we place the actual quad
   * @param settings   Common quad settings
   * @param side       Side to build
   * @param color      Color for the sprite
   * @param sprite     Sprite to render
   * @param u          Sprite U
   * @param v          Sprite V
   * @param size       Size of the quad in the correct direction (depth is always 1 pixel)
   */
  private static void buildSideQuad(List<BakedQuad> output, QuadBakingVertexConsumer builder, VertexConsumer consumer, QuadSettings settings, Direction side, int color, TextureAtlasSprite sprite, int u, int v, int size) {
    final float eps = 1e-2f;
    SpriteContents contents = sprite.contents();
    int width = contents.width();
    int height = contents.height();
    float x0 = (float) u / width;
    float y0 = (float) v / height;
    float x1 = x0, y1 = y0;
    float z0 = 7.5f / 16f, z1 = 8.5f / 16f;
    switch(side) {
      case WEST:
        z0 = 8.5f / 16f;
        z1 = 7.5f / 16f;
        // continue into EAST
      case EAST:
        y1 = (float) (v + size) / height;
        break;
      case DOWN:
        z0 = 8.5f / 16f;
        z1 = 7.5f / 16f;
        // continue into UP
      case UP:
        x1 = (float) (u + size) / width;
        break;
      default:
        throw new IllegalArgumentException("can't handle z-oriented side");
    }

    // for the side, Y axis's use of getOpposite is related to the swapping of V direction
    float dx = side.getUnitVec3i().getX() * eps / width;
    float dy = side.getUnitVec3i().getY() * eps / height;
    // TextureAtlasSprite#getU/#getV take a normalized 0..1 interpolant
    float u0 = (x0 - dx);
    float u1 = (x1 - dx);
    float v0 = (1f - y0 - dy);
    float v1 = (1f - y1 - dy);
    buildQuad(output, builder, consumer, settings, (side.getAxis() == Axis.Y ? side.getOpposite() : side), color,
      x0, y0, z0, sprite.getU(u0), sprite.getV(v0),
      x1, y1, z0, sprite.getU(u1), sprite.getV(v1),
      x1, y1, z1, sprite.getU(u1), sprite.getV(v1),
      x0, y0, z1, sprite.getU(u0), sprite.getV(v0));
  }

  /** Internal version of the public buildQuad method using the shared settings, as the quad builder clears its settings after each quad */
  private static void buildQuad(List<BakedQuad> output, QuadBakingVertexConsumer builder, VertexConsumer consumer, QuadSettings settings, Direction side, int color,
                                float x0, float y0, float z0, float u0, float v0,
                                float x1, float y1, float z1, float u1, float v1,
                                float x2, float y2, float z2, float u2, float v2,
                                float x3, float y3, float z3, float u3, float v3) {
    // the builder clears its settings after baking a quad, so set them each time
    builder.setSprite(settings.material);
    builder.setTintIndex(settings.tint);
    // matches the old unshaded quads
    builder.setShadeOverride(Direction.UP);
    builder.setAmbientOcclusion(true);
    builder.setLightEmission(settings.luminosity);
    builder.setDirection(side);
    putVertex(consumer, side, x0, y0, z0, u0, v0, color);
    putVertex(consumer, side, x1, y1, z1, u1, v1, color);
    putVertex(consumer, side, x2, y2, z2, u2, v2, color);
    putVertex(consumer, side, x3, y3, z3, u3, v3, color);
    output.add(builder.bakeQuad(settings.interner));
  }

  /**
   * Builds a single quad in the model, based on the vanilla item layer generator but with color added.
   * The builder must already have the sprite set, as it is not part of the vertex data.
   * @param output       List of quads to add the quad into
   * @param builder      Quad builder instance, just used to set direction so we can avoid redundancy
   * @param consumer     Quad consumer, where we place the actual quad
   * @param side         Quad side
   * @param color        Color for the sprite in AARRGGBB format
   * @param luminosity   Extra light to add to the quad between 0 and 15
   */
  public static void buildQuad(List<BakedQuad> output, QuadBakingVertexConsumer builder, VertexConsumer consumer, Direction side, int color, int luminosity,
                                     float x0, float y0, float z0, float u0, float v0,
                                     float x1, float y1, float z1, float u1, float v1,
                                     float x2, float y2, float z2, float u2, float v2,
                                     float x3, float y3, float z3, float u3, float v3) {
    builder.setLightEmission(luminosity);
    builder.setDirection(side);
    putVertex(consumer, side, x0, y0, z0, u0, v0, color);
    putVertex(consumer, side, x1, y1, z1, u1, v1, color);
    putVertex(consumer, side, x2, y2, z2, u2, v2, color);
    putVertex(consumer, side, x3, y3, z3, u3, v3, color);
    output.add(builder.bakeQuad());
  }

  /**
   * Clone of the vertex logic in the vanilla item layer generator with the color parameter added. Light is set on the quad material, not per vertex.
   * @param consumer   Vertex consumer
   * @param side       Side for the quad
   * @param x          Quad X position
   * @param y          Quad Y position
   * @param z          Quad Z position
   * @param u          Quad texture U
   * @param v          Quad texture V
   * @param color      Quad color in AARRGGBB format
   */
  private static void putVertex(VertexConsumer consumer, Direction side, float x, float y, float z, float u, float v, int color) {
    consumer.addVertex(x, y, z)
            .setColor(color)
            .setNormal(side.getStepX(), side.getStepY(), side.getStepZ())
            .setUv(u, v);
  }

  /** Cloned from the old NeoForge item layer model's FaceData subclass */
  private static class FaceData {
    private final EnumMap<Direction,BitSet> data = new EnumMap<>(Direction.class);
    private final int vMax;

    FaceData(int uMax, int vMax) {
      this.vMax = vMax;

      data.put(Direction.WEST, new BitSet(uMax * vMax));
      data.put(Direction.EAST, new BitSet(uMax * vMax));
      data.put(Direction.UP,   new BitSet(uMax * vMax));
      data.put(Direction.DOWN, new BitSet(uMax * vMax));
    }

    public void set(Direction facing, int u, int v) {
      data.get(facing).set(getIndex(u, v));
    }

    public boolean get(Direction facing, int u, int v) {
      return data.get(facing).get(getIndex(u, v));
    }

    private int getIndex(int u, int v) {
      return v * vMax + u;
    }
  }

  /**
   * Class holding details about a single layer in the model
   * @param renderType  Legacy render type name. Render types are chosen per quad from the texture in 26.x, so the only effect is that a translucent render type forces the layer translucent.
   */
  public record LayerData(int color, int luminosity, boolean noTint, @Nullable Identifier renderType) {
    public static final LayerData DEFAULT = new LayerData(-1, 0, false, null);
    public static final RecordLoadable<LayerData> LOADABLE = RecordLoadable.create(
      ColorLoadable.ALPHA.defaultField("color", false, LayerData::color),
      // TODO: rename this field?
      IntLoadable.range(0, 15).defaultField("luminosity", 0, LayerData::luminosity),
      BooleanLoadable.INSTANCE.defaultField("no_tint", false, false, LayerData::noTint),
      Loadables.RESOURCE_LOCATION.nullableField("render_type", LayerData::renderType),
      LayerData::new);
    public static final Loadable<List<LayerData>> LIST_LOADABLE = LOADABLE.list(1);

    /** Checks if this layer requested a translucent render type, which forces the layer into the translucent layer */
    public boolean isTranslucent() {
      return renderType != null && renderType.getPath().contains("translucent");
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public static LayerData fromJson(JsonObject json) {
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

  /** Deserializes this model from JSON */
  public static MantleItemLayerModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new MantleItemLayerModel(StandardModelParameters.parse(json, context), LayerData.LIST_LOADABLE.getOrDefault(json, "layers", List.of()));
  }
}
