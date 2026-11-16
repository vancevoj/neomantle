package slimeknights.mantle.fluid.texture;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.InvertedFluid;

/**
 * Client logic for {@link slimeknights.mantle.fluid.InvertedFluidType}.
 * <p>
 * 26.3: textures are part of the vanilla fluid model, so the inverted flowing texture is chosen when the model is created.
 * Fluids extending {@link InvertedFluid}, or whose fluid type uses this extension, get the {@code _inverted} flowing
 * texture on the fluid block, see {@link FluidTextureManager#createModel(Fluid)}. Outside the world (block entities,
 * GUIs) the regular flowing texture is used, as before.
 */
public class ClientInvertedFluidType extends ClientTextureFluidType {
  public ClientInvertedFluidType(FluidType type) {
    super(type);
  }

  /**
   * Gets the inverted variant of a flowing texture
   * @param flowing  Flowing texture
   * @return  Inverted flowing texture
   */
  public static Identifier getInvertedTexture(Identifier flowing) {
    return flowing.withSuffix("_inverted");
  }

  /**
   * Checks if the given fluid uses the inverted flowing texture in world
   * @param fluid  Fluid to check
   * @return  True if the fluid block uses the inverted texture
   */
  public static boolean isInverted(Fluid fluid) {
    return fluid instanceof InvertedFluid || IClientFluidTypeExtensions.of(fluid) instanceof ClientInvertedFluidType;
  }
}
