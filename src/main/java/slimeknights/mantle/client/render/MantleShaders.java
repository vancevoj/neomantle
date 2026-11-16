package slimeknights.mantle.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Render pipelines backing Mantle's render types.
 * <p>
 * 26.3 removed core shader programs ({@code ShaderInstance}, {@code RegisterShadersEvent}); render pipelines replace them.
 * Mantle no longer needs any custom shader: in world fluids use the vanilla entity translucent cull pipeline, which applies
 * fog itself (so the old fluid fog fix shader is obsolete), and the book structure renderer uses the vanilla translucent
 * item pipeline with full bright light. Nothing needs to be registered.
 */
public final class MantleShaders {
  private MantleShaders() {}

  /** Pipeline behind {@link MantleRenderTypes#FLUID} */
  public static final RenderPipeline FLUID = RenderPipelines.ENTITY_TRANSLUCENT_CULL;
  /** Pipeline behind {@link MantleRenderTypes#TRANSLUCENT_FULLBRIGHT} */
  public static final RenderPipeline BLOCK_FULLBRIGHT = RenderPipelines.ITEM_TRANSLUCENT;

  /** Gets the pipeline used for the book structure renderer */
  public static RenderPipeline getBlockFullBrightShader() {
    return BLOCK_FULLBRIGHT;
  }

  /** Gets the pipeline used for fluids */
  public static RenderPipeline getFluidShader() {
    return FLUID;
  }

  /**
   * Gets the pipeline used for fluids. The fog fix and text shader config options no longer select a shader, as the
   * vanilla entity pipeline already renders fog correctly.
   */
  public static RenderPipeline getConfiguredFluidShader() {
    return FLUID;
  }
}
