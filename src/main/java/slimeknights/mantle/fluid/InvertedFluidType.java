package slimeknights.mantle.fluid;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Fluid type adding an extra flipped texture for the in world block.
 * NeoForge 26.3 removed {@code FluidType#initializeClient}, so this type is now a marker: the client extensions
 * ({@link slimeknights.mantle.fluid.texture.ClientInvertedFluidType}) and fluid model are registered for every instance of this
 * type from the client registration events.
 */
public class InvertedFluidType extends FluidType {
  public InvertedFluidType(Properties properties) {
    super(properties);
  }
}
