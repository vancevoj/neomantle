package slimeknights.mantle.fluid;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Fluid type whose color and textures are determined by the model.
 * NeoForge 26.3 removed {@code FluidType#initializeClient}, so this type is now a marker: the client extensions
 * ({@link slimeknights.mantle.fluid.texture.ClientTextureFluidType}) and fluid model are registered for every instance of this
 * type from the client registration events.
 */
public class TextureFluidType extends FluidType {
  public TextureFluidType(Properties properties) {
    super(properties);
  }
}
