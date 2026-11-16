package slimeknights.mantle.client.render;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Class for render types defined by Mantle.
 * <p>
 * 26.3: render types can no longer be subclassed and all shader, blend and cull state lives in render pipelines, so this
 * is a plain holder. Every Mantle render type is a vanilla render type, which keeps them compatible with shader packs.
 * @see MantleShaders
 */
public final class MantleRenderTypes {
  private MantleRenderTypes() {}

  /**
   * Render type used for the fluid renderer.
   * <p>
   * Uses the vanilla entity translucent cull pipeline on the block atlas with the {@code ENTITY} vertex format, so vertices
   * must include color, UV, overlay, light and normal (see {@link FluidRenderer#putTexturedQuad}). A custom fluid shader
   * was invisible under Iris/Oculus, which only render geometry on render types they recognize; the vanilla entity
   * translucent pipeline is fully supported and applies fog on its own. Submit geometry with
   * {@link net.minecraft.client.renderer.SubmitNodeCollector#submitCustomGeometry}.
   */
  public static final RenderType FLUID = RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);

  /**
   * Render type used for the structure renderer.
   * <p>
   * 26.3: the custom fullbright block shader is gone, this is the vanilla translucent block atlas item render type.
   * Submit with {@link net.minecraft.util.LightCoordsUtil#FULL_BRIGHT} light to keep the structure fully lit.
   */
  public static final RenderType TRANSLUCENT_FULLBRIGHT = Sheets.translucentBlockItemSheet();
}
