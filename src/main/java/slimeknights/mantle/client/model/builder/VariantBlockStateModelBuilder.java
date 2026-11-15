package slimeknights.mantle.client.model.builder;

import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator;
import slimeknights.mantle.client.model.RetexturedBlockStateModel;
import slimeknights.mantle.client.model.connected.ConnectedBlockStateModel;

import java.util.function.Function;

/**
 * Datagen builder for Mantle dynamic block state models that wrap a single {@link Variant}, such as {@code mantle:connected} and {@code mantle:retextured}.
 * Supports rotations through {@link VariantMutator} like vanilla variants. Use with {@link MultiVariant#of(CustomBlockStateModelBuilder)}.
 */
public class VariantBlockStateModelBuilder extends CustomBlockStateModelBuilder {
  private final Variant variant;
  private final Function<Variant,? extends CustomUnbakedBlockStateModel> constructor;

  public VariantBlockStateModelBuilder(Variant variant, Function<Variant,? extends CustomUnbakedBlockStateModel> constructor) {
    this.variant = variant;
    this.constructor = constructor;
  }

  /** Creates a builder for a {@code mantle:connected} block state model */
  public static VariantBlockStateModelBuilder connected(Identifier model) {
    return new VariantBlockStateModelBuilder(new Variant(model), ConnectedBlockStateModel.Unbaked::new);
  }

  /** Creates a builder for a {@code mantle:retextured} block state model */
  public static VariantBlockStateModelBuilder retextured(Identifier model) {
    return new VariantBlockStateModelBuilder(new Variant(model), RetexturedBlockStateModel.Unbaked::new);
  }

  /** Creates a multi variant for a {@code mantle:connected} block state model */
  public static MultiVariant connectedVariant(Identifier model) {
    return MultiVariant.of(connected(model));
  }

  /** Creates a multi variant for a {@code mantle:retextured} block state model */
  public static MultiVariant retexturedVariant(Identifier model) {
    return MultiVariant.of(retextured(model));
  }

  @Override
  public VariantBlockStateModelBuilder with(VariantMutator variantMutator) {
    return new VariantBlockStateModelBuilder(variant.with(variantMutator), constructor);
  }

  @Override
  public CustomBlockStateModelBuilder with(UnbakedMutator variantMutator) {
    return new CustomBlockStateModelBuilder.Simple(variantMutator.apply(toUnbaked()));
  }

  @Override
  public CustomUnbakedBlockStateModel toUnbaked() {
    return constructor.apply(variant);
  }
}
