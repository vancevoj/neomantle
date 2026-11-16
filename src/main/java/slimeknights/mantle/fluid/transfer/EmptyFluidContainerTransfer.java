package slimeknights.mantle.fluid.transfer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;

import java.lang.reflect.Type;
import java.util.function.Consumer;

/** Fluid transfer info that empties a fluid from an item */
@RequiredArgsConstructor
public class EmptyFluidContainerTransfer implements IFluidContainerTransfer {
  public static final Identifier ID = Mantle.getResource("empty_item");

  protected final Ingredient input;
  protected final ItemOutput result;
  protected final FluidOutput fluid;

  /** @deprecated use {@link #EmptyFluidContainerTransfer(Ingredient, ItemOutput, FluidOutput)} */
  @Deprecated(forRemoval = true)
  public EmptyFluidContainerTransfer(Ingredient input, ItemOutput result, FluidStack fluid) {
    this(input, result, FluidOutput.fromStack(fluid));
  }

  @Override
  public void addRepresentativeItems(Consumer<Item> consumer) {
    input.items().forEach(item -> consumer.accept(item.value()));
  }

  @Override
  public boolean matches(ItemStack stack, FluidStack fluid) {
    return input.test(stack);
  }

  /** Gets the contained fluid in the given stack */
  protected FluidStack getFluid(ItemStack stack) {
    return fluid.get();
  }

  @Nullable
  @Override
  public TransferResult transfer(ItemStack stack, FluidStack fluid, ResourceHandler<FluidResource> handler, TransferDirection direction, TransactionContext transaction) {
    if (!direction.canEmpty()) {
      return null;
    }
    FluidStack contained = getFluid(stack);
    if (contained.isEmpty()) {
      return null;
    }
    FluidResource resource = FluidResource.of(contained);
    try (Transaction filling = Transaction.open(transaction)) {
      // must be able to fill the whole contents
      int simulated;
      try (Transaction simulate = Transaction.open(filling)) {
        simulated = handler.insert(resource, contained.getAmount(), simulate);
      }
      if (simulated == contained.getAmount()) {
        int actual = handler.insert(resource, contained.getAmount(), filling);
        if (actual > 0) {
          if (actual != this.fluid.getAmount()) {
            Mantle.logger.error("Wrong amount filled from {}, expected {}, filled {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), this.fluid.getAmount(), actual);
          }
          filling.commit();
          return new TransferResult(result.copy(), contained, false);
        }
      }
    }
    return null;
  }

  @Override
  public JsonObject serialize(JsonSerializationContext context) {
    JsonObject json = new JsonObject();
    json.addProperty("type", ID.toString());
    json.add("input", IngredientLoadable.DISALLOW_EMPTY.serialize(input));
    if (!result.isEmpty()) {
      json.add("result", result.serialize(false));
    }
    JsonObject fluidJson = new JsonObject();
    FluidOutput.Loadable.REQUIRED.serialize(fluid, fluidJson);
    json.add("fluid", fluidJson);
    return json;
  }

  /** Unique loader instance */
  public static final JsonDeserializer<EmptyFluidContainerTransfer> DESERIALIZER = new Deserializer<>(EmptyFluidContainerTransfer::new);

  /** Gets the result for the fluid transfer. */
  static ItemOutput getResult(JsonObject json) {
    String key = "result";
    if (!json.has(key) && json.has("filled")) {
      Mantle.logger.warn("Using deprecated field 'filled' for fluid container transfer, use 'result' instead.");
      key = "filled";
    }
    return ItemOutput.Loadable.OPTIONAL_ITEM.getOrEmpty(json, key);
  }

  /**
   * Generic deserializer
   */
  public record Deserializer<T extends EmptyFluidContainerTransfer>(TriFunction<Ingredient,ItemOutput,FluidOutput,T> factory) implements JsonDeserializer<T> {
    @Override
    public T deserialize(JsonElement element, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject json = element.getAsJsonObject();
      Ingredient input = IngredientLoadable.DISALLOW_EMPTY.getIfPresent(json, "input", FluidContainerTransferManager.getLoadContext());
      ItemOutput result = getResult(json);
      FluidOutput fluid = FluidOutput.Loadable.REQUIRED.getIfPresent(json, "fluid", FluidContainerTransferManager.getLoadContext());
      return factory.apply(input, result, fluid);
    }
  }
}
