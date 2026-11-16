package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import slimeknights.mantle.client.render.FluidCuboid.FluidFace;

import java.util.List;

/**
 * Helpers for rendering fluid cuboids.
 * <p>
 * 26.3: nothing is drawn immediately any more. Renderers submit geometry to a {@link SubmitNodeCollector} and the vertex
 * methods here run later, inside {@link SubmitNodeCollector#submitCustomGeometry(PoseStack, net.minecraft.client.renderer.rendertype.RenderType, SubmitNodeCollector.CustomGeometryRenderer)},
 * which hands out a {@link PoseStack.Pose} snapshot and a {@link VertexConsumer} for {@link MantleRenderTypes#FLUID}.
 * The methods taking a {@link PoseStack.Pose} are meant to be called inside such a lambda, the methods taking a
 * {@link SubmitNodeCollector} do the submission for you. Resolve sprites with {@link FluidSprites#of(FluidStack)} during
 * render state extraction where possible, and only capture immutable values in submitted lambdas.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class FluidRenderer {
  /**
   * Gets a block sprite from the given location.
   * <p>
   * {@link net.minecraft.client.resources.model.sprite.AtlasManager#getAtlasOrThrow(Identifier)} takes the atlas definition ID
   * ({@link AtlasIds#BLOCKS}), not the atlas texture location ({@link TextureAtlas#LOCATION_BLOCKS}), which is what a
   * {@link net.minecraft.client.resources.model.sprite.SpriteId} uses. Unknown sprites return the missing sprite.
   * @param sprite  Sprite name
   * @return  Sprite location
   */
  public static TextureAtlasSprite getBlockSprite(Identifier sprite) {
    return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(sprite);
  }

  /**
   * Gets the vanilla fluid model for the given fluid, containing its textures and tint.
   * Fluids without a registered model get the missing model.
   * @param fluid  Fluid to fetch
   * @return  Fluid model
   */
  public static net.minecraft.client.renderer.block.FluidModel getFluidModel(Fluid fluid) {
    return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
  }

  /**
   * Gets the sprites and tint for the given fluid stack
   * @param fluid  Fluid stack
   * @return  Fluid sprites
   * @see FluidSprites#of(FluidStack)
   */
  public static FluidSprites getFluidSprites(FluidStack fluid) {
    return FluidSprites.of(fluid);
  }

  /**
   * Takes the larger light value between combinedLight and the passed block light
   * @param combinedLight  Sky light/block light lightmap value
   * @param blockLight     New 0-15 block light value
   * @return  Updated packed light including the new light value
   */
  public static int withBlockLight(int combinedLight, int blockLight) {
    // skylight from the combined plus larger block light between combined and parameter
    // not using methods from LightCoordsUtil to reduce number of operations
    return (combinedLight & 0xFFFF0000) | Math.max(blockLight << 4, combinedLight & 0xFFFF);
  }

  /**
   * Gets the light to use for rendering the given fluid, accounting for the fluid's own light level
   * @param light  Packed light from the renderer
   * @param fluid  Fluid to render
   * @return  Packed light
   */
  public static int getFluidLight(int light, FluidStack fluid) {
    return withBlockLight(light, fluid.getFluid().getFluidType().getLightLevel(fluid));
  }

  /* Fluid cuboids */

  /**
   * Forces the UV to be between 0 and 1
   * @param value  Original value
   * @param upper  If true, this is the larger UV. Needed to enforce integer values end up at 1
   * @return  UV mapped between 0 and 1
   */
  private static float boundUV(float value, boolean upper) {
    // to get the remainder, we effectively do `value - Math.floor(value)`
    // its optimized by using the implementation from `Mth.floor`,
    // and further optimized by inlining the implementation to allow simplifying the zero check
    int floor = (int) value;
    if (value == floor) {
      // if the value is a whole number, map that to 1 for larger UV and 0 for smaller
      return upper ? 1 : 0;
    }
    // if negative, need to add an extra 1 to ensure the result is positive
    return value - (value < floor ? floor - 1 : floor);
  }

  /**
   * Adds a quad to the renderer
   * @param renderer    Renderer instnace
   * @param matrix      Render matrix
   * @param sprite      Sprite to render
   * @param from        Quad start
   * @param to          Quad end
   * @param face        Face to render
   * @param color       Color to use in rendering
   * @param brightness  Face brightness
   * @param flowing     If true, half texture coordinates
   */
  public static void putTexturedQuad(VertexConsumer renderer, Matrix4f matrix, TextureAtlasSprite sprite, Vector3f from, Vector3f to, Direction face, int color, int brightness, int rotation, boolean flowing) {
    // start with texture coordinates
    float x1 = from.x(), y1 = from.y(), z1 = from.z();
    float x2 = to.x(), y2 = to.y(), z2 = to.z();
    // choose UV based on the directions, some need to negate UV due to the direction
    // note that we use -UV instead of 1-UV as its slightly simpler and the later logic deals with negatives
    float u1, u2, v1, v2;
    switch (face) {
      default -> { // DOWN
        u1 = x1; u2 = x2;
        v1 = z2; v2 = z1;
      }
      case UP -> {
        u1 = x1; u2 = x2;
        v1 = -z1; v2 = -z2;
      }
      case NORTH -> {
        u1 = -x1; u2 = -x2;
        v1 = y1; v2 = y2;
      }
      case SOUTH -> {
        u1 = x2; u2 = x1;
        v1 = y1; v2 = y2;
      }
      case WEST -> {
        u1 = z2; u2 = z1;
        v1 = y1; v2 = y2;
      }
      case EAST -> {
        u1 = -z1; u2 = -z2;
        v1 = y1; v2 = y2;
      }
    }

    // flip V when relevant
    if (rotation == 0 || rotation == 270) {
      float temp = v1;
      v1 = -v2;
      v2 = -temp;
    }
    // flip U when relevant
    if (rotation >= 180) {
      float temp = u1;
      u1 = -u2;
      u2 = -temp;
    }

    // bound UV to be between 0 and 1
    boolean reverse = u1 > u2;
    u1 = boundUV(u1, reverse);
    u2 = boundUV(u2, !reverse);
    reverse = v1 > v2;
    v1 = boundUV(v1, reverse);
    v2 = boundUV(v2, !reverse);

    // if rotating by 90 or 270, swap U and V
    float minU, maxU, minV, maxV;
    float size = flowing ? 0.5f : 1f;
    if (rotation == 90 || rotation == 270) {
      minU = sprite.getU(v1 * size);
      maxU = sprite.getU(v2 * size);
      minV = sprite.getV(u1 * size);
      maxV = sprite.getV(u2 * size);
    } else {
      minU = sprite.getU(u1 * size);
      maxU = sprite.getU(u2 * size);
      minV = sprite.getV(v1 * size);
      maxV = sprite.getV(v2 * size);
    }
    // based on rotation, put coords into place
    float u3, u4, v3, v4;
    switch(rotation) {
      default -> { // 0
        u1 = minU; v1 = maxV;
        u2 = minU; v2 = minV;
        u3 = maxU; v3 = minV;
        u4 = maxU; v4 = maxV;
      }
      case 90 -> {
        u1 = minU; v1 = minV;
        u2 = maxU; v2 = minV;
        u3 = maxU; v3 = maxV;
        u4 = minU; v4 = maxV;
      }
      case 180 -> {
        u1 = maxU; v1 = minV;
        u2 = maxU; v2 = maxV;
        u3 = minU; v3 = maxV;
        u4 = minU; v4 = minV;
      }
      case 270 -> {
        u1 = maxU; v1 = maxV;
        u2 = minU; v2 = maxV;
        u3 = minU; v3 = minV;
        u4 = maxU; v4 = minV;
      }
    }
    // add quads
    int light1 = brightness & 0xFFFF;
    int light2 = brightness >> 0x10 & 0xFFFF;
    int a = color >> 24 & 0xFF;
    int r = color >> 16 & 0xFF;
    int g = color >> 8 & 0xFF;
    int b = color & 0xFF;
    // face normal for the ENTITY vertex format (required by the entity translucent render type so shaders render it)
    int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
    switch (face) {
      case DOWN -> {
        renderer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
      case UP -> {
        renderer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
      case NORTH -> {
        renderer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
      case SOUTH -> {
        renderer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
      case WEST -> {
        renderer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
      case EAST -> {
        renderer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setUv(u2, v2).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(u3, v3).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
        renderer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setUv(u4, v4).setUv2(light1, light2).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nx, ny, nz);
      }
    }
  }

  /* Cuboids */

  /**
   * Renders a full fluid cuboid for the given data
   * @param pose      Pose to render with, typically from {@link SubmitNodeCollector.CustomGeometryRenderer}
   * @param buffer    Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param from      Fluid start
   * @param to        Fluid end
   * @param color     Fluid color
   * @param light     Quad lighting
   */
  public static void renderCuboid(PoseStack.Pose pose, VertexConsumer buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, Vector3f from, Vector3f to, int color, int light) {
    Matrix4f matrix = pose.pose();
    for (Direction dir : Direction.values()) {
      FluidFace face = cube.getFace(dir);
      if (face != null) {
        boolean isFlowing = face.isFlowing();
        putTexturedQuad(buffer, matrix, isFlowing ? flowing : still, from, to, dir, color, light, face.rotation(), isFlowing);
      }
    }
  }

  /**
   * Renders a full fluid cuboid for the given data
   * @param matrices  Matrix stack instance
   * @param buffer    Buffer type
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param cube      Fluid cuboid
   * @param from      Fluid start
   * @param to        Fluid end
   * @param color     Fluid color
   * @param light     Quad lighting
   * @see #renderCuboid(PoseStack.Pose, VertexConsumer, FluidCuboid, TextureAtlasSprite, TextureAtlasSprite, Vector3f, Vector3f, int, int)
   */
  public static void renderCuboid(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, Vector3f from, Vector3f to, int color, int light) {
    renderCuboid(matrices.last(), buffer, cube, still, flowing, from, to, color, light);
  }

  /** @deprecated use {@link #renderCuboid(PoseStack.Pose, VertexConsumer, FluidCuboid, TextureAtlasSprite, TextureAtlasSprite, Vector3f, Vector3f, int, int)} */
  @Deprecated(forRemoval = true)
  public static void renderCuboid(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, Vector3f from, Vector3f to, int color, int light, boolean isGas) {
    renderCuboid(matrices.last(), buffer, cube, still, flowing, from, to, color, light);
  }

  /**
   * Renders a fluid cuboid at its own bounds, used to manually place cuboids from a list for rendering {@link #renderCuboids(PoseStack.Pose, VertexConsumer, List, FluidStack, int)}
   * @param pose      Pose to render with
   * @param buffer    Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param color     Fluid color
   * @param light     Quad lighting from the renderer
   */
  public static void renderCuboid(PoseStack.Pose pose, VertexConsumer buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    renderCuboid(pose, buffer, cube, still, flowing, cube.getFromScaled(), cube.getToScaled(), color, light);
  }

  /**
   * Renders a fluid cuboid at its own bounds using the given fluid sprites and tint
   * @param pose      Pose to render with
   * @param buffer    Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid
   * @param sprites   Fluid sprites and tint
   * @param light     Quad lighting, see {@link #getFluidLight(int, FluidStack)}
   */
  public static void renderCuboid(PoseStack.Pose pose, VertexConsumer buffer, FluidCuboid cube, FluidSprites sprites, int light) {
    renderCuboid(pose, buffer, cube, sprites.still(), sprites.flowing(), sprites.color(), light);
  }

  /**
   * Renders a fluid cuboid at its own bounds, used to manually place cuboids from a list for rendering {@link #renderCuboids(PoseStack, VertexConsumer, List, FluidStack, int)}
   * @param matrices  Matrix stack instance
   * @param buffer    Buffer type
   * @param cube      Fluid cuboid
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param color     Fluid color
   * @param light     Quad lighting from the renderer
   */
  public static void renderCuboid(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    renderCuboid(matrices.last(), buffer, cube, still, flowing, color, light);
  }

  /** @deprecated use {@link #renderCuboid(PoseStack.Pose, VertexConsumer, FluidCuboid, TextureAtlasSprite, TextureAtlasSprite, int, int)}, translating the pose if needed */
  @Deprecated(forRemoval = true)
  public static void renderCuboid(PoseStack matrices, VertexConsumer buffer, FluidCuboid cube, float yOffset, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light, boolean isGas) {
    PoseStack.Pose pose = matrices.last();
    if (yOffset != 0) {
      pose = pose.copy();
      pose.translate(0, yOffset, 0);
    }
    renderCuboid(pose, buffer, cube, still, flowing, color, light);
  }

  /**
   * Renders a list of fluid cuboids
   * @param pose     Pose to render with
   * @param buffer   Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cubes    List of cubes to render
   * @param sprites  Fluid sprites and tint
   * @param light    Quad lighting, see {@link #getFluidLight(int, FluidStack)}
   */
  public static void renderCuboids(PoseStack.Pose pose, VertexConsumer buffer, List<FluidCuboid> cubes, FluidSprites sprites, int light) {
    for (FluidCuboid cube : cubes) {
      renderCuboid(pose, buffer, cube, sprites, light);
    }
  }

  /**
   * Renders a list of fluid cuboids
   * @param pose    Pose to render with
   * @param buffer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cubes   List of cubes to render
   * @param fluid   Fluid to use in rendering
   * @param light   Light level from the renderer
   */
  public static void renderCuboids(PoseStack.Pose pose, VertexConsumer buffer, List<FluidCuboid> cubes, FluidStack fluid, int light) {
    if (fluid.isEmpty()) {
      return;
    }
    // fluid attributes, fetch once for all fluids to save effort
    renderCuboids(pose, buffer, cubes, FluidSprites.of(fluid), getFluidLight(light, fluid));
  }

  /**
   * Renders a list of fluid cuboids
   * @param matrices  Matrix stack instance
   * @param buffer    Buffer instance
   * @param cubes     List of cubes to render
   * @param fluid     Fluid to use in rendering
   * @param light     Light level from the renderer
   */
  public static void renderCuboids(PoseStack matrices, VertexConsumer buffer, List<FluidCuboid> cubes, FluidStack fluid, int light) {
    renderCuboids(matrices.last(), buffer, cubes, fluid, light);
  }

  /**
   * Submits a list of fluid cuboids for rendering with {@link MantleRenderTypes#FLUID}.
   * Sprites, tint and light are resolved immediately, so the fluid stack may change after this call.
   * @param matrices   Matrix stack instance, the current pose is captured
   * @param collector  Submit node collector
   * @param cubes      List of cubes to render
   * @param fluid      Fluid to use in rendering
   * @param light      Light level from the renderer
   */
  public static void renderCuboids(PoseStack matrices, SubmitNodeCollector collector, List<FluidCuboid> cubes, FluidStack fluid, int light) {
    if (fluid.isEmpty() || cubes.isEmpty()) {
      return;
    }
    FluidSprites sprites = FluidSprites.of(fluid);
    int fluidLight = getFluidLight(light, fluid);
    List<FluidCuboid> toRender = List.copyOf(cubes);
    collector.submitCustomGeometry(matrices, MantleRenderTypes.FLUID, (pose, buffer) -> renderCuboids(pose, buffer, toRender, sprites, fluidLight));
  }


  /* Scaling */

  /**
   * Computes the Y value at which to stop a partially filled fluid
   * @param minY      Bottom of the fluid area
   * @param maxY      Top of the fluid area
   * @param amount    Fluid amount
   * @param offset    Fluid amount offset, used to animate transitions
   * @param capacity  Fluid capacity, must be above 0
   * @param flip      If true, the fluid fills from the top (gases)
   * @return  Y value to stop the fluid. If {@code flip} this is a minimum, otherwise a maximum.
   */
  public static float getScaledY(float minY, float maxY, int amount, float offset, int capacity, boolean flip) {
    float height = (amount - offset) / capacity;
    if (flip) {
      return maxY + (height * (minY - maxY));
    }
    return minY + (height * (maxY - minY));
  }

  /**
   * Checks if the given fluid should render flipped
   * @param fluid    Fluid to check
   * @param flipGas  If true, gases are flipped
   * @return  True if the fluid fills from the top
   */
  public static boolean shouldFlip(FluidStack fluid, boolean flipGas) {
    return flipGas && fluid.getFluid().getFluidType().isLighterThanAir();
  }

  /**
   * Renders a fluid cuboid scaled to the given height
   * @param pose      Pose to render with
   * @param consumer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid instance
   * @param scaledY   Y value to stop the fluid. If {@code flip} this is a minimum, otherwise a maximum.
   * @param flip      If true, the fluid fills from the top
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param color     Fluid color
   * @param light     Quad lighting from the renderer
   */
  public static void renderScaledCuboid(PoseStack.Pose pose, VertexConsumer consumer, FluidCuboid cube, float scaledY, boolean flip, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    Vector3f from = cube.getFromScaled();
    Vector3f to = cube.getToScaled();
    if (flip) {
      // if the scaled min is above the end, skip this cube entirely
      if (scaledY > to.y) return;
      // if the scaled min is below our start, render the full thing
      if (scaledY > from.y) {
        from = new Vector3f(from);
        from.y = scaledY;
      }
    } else {
      // if the scaled max is below the start, skip this cube entirely
      if (scaledY < from.y) return;
      // if the scaled max is above our end, render the full thing
      if (scaledY < to.y) {
        to = new Vector3f(to);
        to.y = scaledY;
      }
    }
    // draw cuboid
    renderCuboid(pose, consumer, cube, still, flowing, from, to, color, light);
  }

  /**
   * Renders a fluid cuboid scaled to the given height
   * @param matrices  Matrix stack instance
   * @param consumer  Vertex consumer for the fluid
   * @param cube      Fluid cuboid instance
   * @param scaledY   Y value to stop the fluid. If {@code flip} this is a minimum, otherwise a maximum.
   * @param flip      If true, the fluid fills from the top
   * @param still     Still sprite
   * @param flowing   Flowing sprite
   * @param color     Fluid color
   * @param light     Quad lighting from the renderer
   */
  public static void renderScaledCuboid(PoseStack matrices, VertexConsumer consumer, FluidCuboid cube, float scaledY, boolean flip, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    renderScaledCuboid(matrices.last(), consumer, cube, scaledY, flip, still, flowing, color, light);
  }

  /**
   * Renders a fluid cuboid scaled to the given height using the given fluid sprites and tint
   * @param pose      Pose to render with
   * @param consumer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid instance
   * @param sprites   Fluid sprites and tint
   * @param scaledY   Y value to stop the fluid, see {@link #getScaledY(float, float, int, float, int, boolean)}
   * @param flip      If true, the fluid fills from the top
   * @param light     Quad lighting, see {@link #getFluidLight(int, FluidStack)}
   */
  public static void renderScaledCuboid(PoseStack.Pose pose, VertexConsumer consumer, FluidCuboid cube, FluidSprites sprites, float scaledY, boolean flip, int light) {
    renderScaledCuboid(pose, consumer, cube, scaledY, flip, sprites.still(), sprites.flowing(), sprites.color(), light);
  }

  /**
   * Renders a fluid cuboid with partial height based on the capacity
   * @param pose      Pose to render with
   * @param consumer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param cube      Fluid cuboid instance
   * @param fluid     Fluid to render
   * @param offset    Fluid amount offset, used to animate transitions
   * @param capacity  Fluid tank capacity, must be above 0
   * @param light     Quad lighting from the renderer
   * @param flipGas   If true, flips gas cubes
   * @deprecated use {@link #renderScaledModel(PoseStack.Pose, VertexConsumer, FluidModel, FluidStack, float, int, int, boolean)}
   */
  @Deprecated
  public static void renderScaledCuboid(PoseStack.Pose pose, VertexConsumer consumer, FluidCuboid cube, FluidStack fluid, float offset, int capacity, int light, boolean flipGas) {
    // nothing to render
    if (fluid.isEmpty() || capacity <= 0) {
      return;
    }
    // figure out the height to render the fluids
    boolean flip = shouldFlip(fluid, flipGas);
    float scaledY = getScaledY(cube.getFromScaled().y, cube.getToScaled().y, fluid.getAmount(), offset, capacity, flip);
    renderScaledCuboid(pose, consumer, cube, FluidSprites.of(fluid), scaledY, flip, getFluidLight(light, fluid));
  }

  /**
   * Submits a fluid cuboid with partial height based on the capacity for rendering with {@link MantleRenderTypes#FLUID}.
   * Sprites, tint, light and height are resolved immediately, so the fluid stack may change after this call.
   * @param matrices   Matrix stack instance, the current pose is captured
   * @param collector  Submit node collector
   * @param cube       Fluid cuboid instance
   * @param fluid      Fluid to render
   * @param offset     Fluid amount offset, used to animate transitions
   * @param capacity   Fluid tank capacity, must be above 0
   * @param light      Quad lighting from the renderer
   * @param flipGas    If true, flips gas cubes
   * @deprecated use {@link #renderScaledModel(PoseStack, SubmitNodeCollector, FluidModel, FluidStack, float, int, int, boolean)}
   */
  @Deprecated
  public static void renderScaledCuboid(PoseStack matrices, SubmitNodeCollector collector, FluidCuboid cube, FluidStack fluid, float offset, int capacity, int light, boolean flipGas) {
    // nothing to render
    if (fluid.isEmpty() || capacity <= 0) {
      return;
    }
    boolean flip = shouldFlip(fluid, flipGas);
    float scaledY = getScaledY(cube.getFromScaled().y, cube.getToScaled().y, fluid.getAmount(), offset, capacity, flip);
    FluidSprites sprites = FluidSprites.of(fluid);
    int fluidLight = getFluidLight(light, fluid);
    collector.submitCustomGeometry(matrices, MantleRenderTypes.FLUID, (pose, buffer) -> renderScaledCuboid(pose, buffer, cube, sprites, scaledY, flip, fluidLight));
  }

  /**
   * Renders a fluid model scaled to the given height using the given fluid sprites and tint
   * @param pose      Pose to render with
   * @param consumer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param model     Fluid model instance
   * @param sprites   Fluid sprites and tint
   * @param scaledY   Y value to stop the fluid, see {@link #getScaledY(FluidModel, FluidStack, float, int, boolean)}
   * @param flip      If true, the fluid fills from the top
   * @param light     Quad lighting, see {@link #getFluidLight(int, FluidStack)}
   */
  public static void renderScaledModel(PoseStack.Pose pose, VertexConsumer consumer, FluidModel model, FluidSprites sprites, float scaledY, boolean flip, int light) {
    for (FluidCuboid cube : model.getFluids()) {
      renderScaledCuboid(pose, consumer, cube, sprites, scaledY, flip, light);
    }
  }

  /**
   * Computes the Y value at which to stop a partially filled fluid model
   * @param model     Fluid model instance
   * @param fluid     Fluid to render
   * @param offset    Fluid amount offset, used to animate transitions
   * @param capacity  Fluid tank capacity, must be above 0
   * @param flipGas   If true, flips gas cubes
   * @return  Y value to stop the fluid
   */
  public static float getScaledY(FluidModel model, FluidStack fluid, float offset, int capacity, boolean flipGas) {
    return getScaledY(model.getMinScaled().y, model.getMaxScaled().y, fluid.getAmount(), offset, capacity, shouldFlip(fluid, flipGas));
  }

  /**
   * Renders a fluid cuboid with partial height based on the capacity
   * @param pose      Pose to render with
   * @param consumer  Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param model     Fluid model instance
   * @param fluid     Fluid to render
   * @param offset    Fluid amount offset, used to animate transitions
   * @param capacity  Fluid tank capacity, must be above 0
   * @param light     Quad lighting from the renderer
   * @param flipGas   If true, flips gas cubes
   */
  public static void renderScaledModel(PoseStack.Pose pose, VertexConsumer consumer, FluidModel model, FluidStack fluid, float offset, int capacity, int light, boolean flipGas) {
    // nothing to render
    if (fluid.isEmpty() || capacity <= 0 || model.isEmpty()) {
      return;
    }
    renderScaledModel(pose, consumer, model, FluidSprites.of(fluid), getScaledY(model, fluid, offset, capacity, flipGas), shouldFlip(fluid, flipGas), getFluidLight(light, fluid));
  }

  /**
   * Submits a fluid model with partial height based on the capacity for rendering with {@link MantleRenderTypes#FLUID}.
   * Sprites, tint, light and height are resolved immediately, so the fluid stack may change after this call.
   * @param matrices   Matrix stack instance, the current pose is captured
   * @param collector  Submit node collector
   * @param model      Fluid model instance
   * @param fluid      Fluid to render
   * @param offset     Fluid amount offset, used to animate transitions
   * @param capacity   Fluid tank capacity, must be above 0
   * @param light      Quad lighting from the renderer
   * @param flipGas    If true, flips gas cubes
   */
  public static void renderScaledModel(PoseStack matrices, SubmitNodeCollector collector, FluidModel model, FluidStack fluid, float offset, int capacity, int light, boolean flipGas) {
    // nothing to render
    if (fluid.isEmpty() || capacity <= 0 || model.isEmpty()) {
      return;
    }
    FluidSprites sprites = FluidSprites.of(fluid);
    float scaledY = getScaledY(model, fluid, offset, capacity, flipGas);
    boolean flip = shouldFlip(fluid, flipGas);
    int fluidLight = getFluidLight(light, fluid);
    collector.submitCustomGeometry(matrices, MantleRenderTypes.FLUID, (pose, buffer) -> renderScaledModel(pose, buffer, model, sprites, scaledY, flip, fluidLight));
  }


  /* Camera */

  /**
   * Sets up a fluid camera overlay for the player, same as the vanilla underwater overlay but with opacity and color control.
   * Call from {@link net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions#extractOverlay}.
   * <p>
   * 26.3 replacement for the old {@code renderCamera(Minecraft, PoseStack, Identifier, float, int)}, which drew immediately.
   * @param player      Player in the fluid
   * @param state       Player render state receiving the overlay renderer
   * @param texture     Full path to the overlay texture
   * @param opacity     Overlay opacity
   * @param color       Overlay tint, -1 for no tint
   * @param brightness  Brightness at the player's eye position
   */
  public static void extractCamera(LocalPlayer player, PlayerRenderState state, Identifier texture, float opacity, int color, float brightness) {
    int argb;
    // apply fluid tint if one is set
    if (color != -1) {
      argb = ARGB.colorFromFloat(opacity * ARGB.alphaFloat(color), brightness * ARGB.redFloat(color), brightness * ARGB.greenFloat(color), brightness * ARGB.blueFloat(color));
    } else {
      argb = ARGB.colorFromFloat(opacity, brightness, brightness, brightness);
    }
    PlayerRenderState.WaterOverlay overlay = new PlayerRenderState.WaterOverlay(argb, -player.getYRot() / 64, player.getXRot() / 64);
    state.customFluidOverlayRenderer = (playerState, camera, collector, poseStack, sprites, partialTicks, hideGui) -> {
      ScreenEffectRenderer.submitFluid(overlay, poseStack, collector, texture);
      return true;
    };
  }
}
