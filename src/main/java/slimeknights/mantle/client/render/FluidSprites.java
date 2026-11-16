package slimeknights.mantle.client.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.fluid.texture.ClientInvertedFluidType;
import slimeknights.mantle.fluid.texture.FluidTextureManager;

import javax.annotation.Nullable;

/**
 * Sprites and tint used to render a fluid outside of the world, such as in block entity renderers, entity renderers and GUIs.
 * <p>
 * 26.3 moved fluid textures and tints from {@code IClientFluidTypeExtensions} to the vanilla fluid model, so this works for
 * every fluid with a registered fluid model (vanilla, Mantle data driven fluids and third party fluids alike).
 * Resolve it in {@code extractRenderState} and store it in the render state. Never cache it across resource reloads, the
 * model manager is replaced on reload.
 * @param still    Still sprite
 * @param flowing  Flowing sprite
 * @param overlay  Overlay sprite, null if the fluid has none
 * @param color    ARGB tint color, -1 if the fluid is not tinted
 */
public record FluidSprites(TextureAtlasSprite still, TextureAtlasSprite flowing, @Nullable TextureAtlasSprite overlay, int color) {
  /**
   * Gets the sprites and tint for the given fluid stack.
   * <p>
   * Inverted fluids use their regular flowing texture outside the world, the inverted one is only used for the fluid block.
   * @param stack  Fluid stack, color may depend on its components
   * @return Fluid sprites
   */
  public static FluidSprites of(FluidStack stack) {
    Fluid fluid = stack.getFluid();
    net.minecraft.client.renderer.block.FluidModel model = FluidRenderer.getFluidModel(fluid);
    TextureAtlasSprite flowing;
    if (ClientInvertedFluidType.isInverted(fluid)) {
      flowing = FluidRenderer.getBlockSprite(FluidTextureManager.getFlowingTexture(fluid.getFluidType()));
    } else {
      flowing = model.flowingMaterial().sprite();
    }
    Material.Baked overlay = model.overlayMaterial();
    FluidTintSource tint = model.fluidTintSource();
    return new FluidSprites(model.stillMaterial().sprite(), flowing, overlay == null ? null : overlay.sprite(), tint == null ? -1 : tint.colorAsStack(stack));
  }
}
