package slimeknights.mantle.client.book.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;

import javax.annotation.Nullable;

/**
 * Renders the 3D structure preview of book structure pages into a texture, which is then drawn on the page.
 * @see StructurePreviewRenderState
 * @see slimeknights.mantle.client.screen.book.element.StructureElement
 */
public class StructurePreviewRenderer extends PictureInPictureRenderer<StructurePreviewRenderState> {
  /** Picture in picture depth goes from -1000 to 1000, keep the structure comfortably within that range */
  private static final float MAX_DEPTH = 900;

  /** State used for the current texture contents, if equal to the next state we can just draw the texture again */
  @Nullable
  private StructurePreviewRenderState lastState;
  /** Time we last logged an error, prevents spamming the log every frame */
  private long lastErrorTime = -1;

  /** Registers this renderer, call from the mod event bus on the client */
  public static void register(RegisterPictureInPictureRenderersEvent event) {
    event.register(StructurePreviewRenderState.class, StructurePreviewRenderer::new);
  }

  @Override
  public Class<StructurePreviewRenderState> getRenderStateClass() {
    return StructurePreviewRenderState.class;
  }

  @Override
  protected String getTextureLabel() {
    return "mantle_book_structure";
  }

  @Override
  protected float getTranslateY(int height, int guiScale) {
    // origin at the center of the area
    return height / 2f;
  }

  @Override
  protected boolean textureIsReadyToBlit(StructurePreviewRenderState state) {
    return state.equals(this.lastState);
  }

  @Override
  protected void renderToTexture(StructurePreviewRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    this.lastState = state;
    // pose is currently centered on the area with a scale of the GUI scale times state scale, Z is flipped
    float scale = state.scale();
    int length = state.structureLength();
    int height = state.structureHeight();
    int width = state.structureWidth();
    // move from the area center to the structure center, translation is applied after the scale so divide out the scale
    poseStack.translate(state.offsetX() / scale, state.offsetY() / scale, 0);
    // flip Y so up is up, flip Z back so the structure faces the viewer, limit depth to the PIP depth range
    float textureScale = scale * Minecraft.getInstance().getWindow().getGuiScale();
    float radius = Mth.sqrt(length * length + height * height + width * width) / 2;
    float depthScale = radius * textureScale > MAX_DEPTH ? MAX_DEPTH / (radius * textureScale) : 1;
    poseStack.scale(1, -1, -depthScale);
    poseStack.mulPose(state.rotation());
    poseStack.translate(length / -2f, height / -2f, width / -2f);

    // submit each block layer separately so they get the proper render type
    for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
      collector.submitCustomGeometry(poseStack, renderType(layer), (pose, buffer) -> tesselate(state, layer, pose, buffer));
    }
  }

  /** Gets the render type to use for the given block layer */
  private static RenderType renderType(ChunkSectionLayer layer) {
    return switch (layer) {
      case SOLID -> RenderTypes.solidMovingBlock();
      case CUTOUT -> RenderTypes.cutoutMovingBlock();
      case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
    };
  }

  /** Tesselates all visible blocks in the structure for the given layer */
  private void tesselate(StructurePreviewRenderState state, ChunkSectionLayer layer, PoseStack.Pose pose, VertexConsumer buffer) {
    Minecraft minecraft = Minecraft.getInstance();
    BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
    boolean cutoutLeaves = minecraft.options.cutoutLeaves().get();
    ModelBlockRenderer blockRenderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), true, minecraft.getBlockColors());
    TemplateLevel level = state.level();
    PoseStack poseStack = new PoseStack();
    poseStack.last().set(pose);

    try {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int h = 0; h < state.structureHeight(); h++) {
        for (int l = 0; l < state.structureLength(); l++) {
          for (int w = 0; w < state.structureWidth(); w++) {
            pos.set(l, h, w);
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) {
              continue;
            }
            boolean forceOpaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, blockState);
            blockRenderer.tesselateBlock((x, y, z, quad, instance) -> {
              ChunkSectionLayer quadLayer = forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
              if (quadLayer == layer) {
                // the book has no light sources, so draw everything fully lit
                instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                buffer.putBakedQuad(poseStack.last(), quad, instance);
                poseStack.popPose();
              }
            }, l, h, w, level, pos.immutable(), blockState, models.get(blockState), blockState.getSeed(pos));
          }
        }
      }
    } catch (RuntimeException e) {
      long now = System.currentTimeMillis();
      if (now > this.lastErrorTime + 1000) {
        Mantle.logger.error("Failed to render book structure preview", e);
        this.lastErrorTime = now;
      }
    }
  }
}
