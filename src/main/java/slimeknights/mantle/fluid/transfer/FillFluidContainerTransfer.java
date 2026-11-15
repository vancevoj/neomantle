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
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

import java.lang.reflect.Type;
import java.util.function.Consumer;

/** Fluid transfer info that fills a fluid into an item */
@RequiredArgsConstructor
public class FillFluidContainerTransfer implements IFluidContainerTransfer {
  public static final Identifier ID = Mantle.getResource("fill_item");

  private final Ingredient input;
  private final ItemOutput result;
  private final FluidIngredient fluid;

  @Override
  public void addRepresentativeItems(Consumer<Item> consumer) {
    input.items().forEach(item -> consumer.accept(item.value()));
  }

  @Override
  public boolean matches(ItemStack stack, FluidStack fluid) {
    return input.test(stack) && this.fluid.test(fluid);
  }

  /** Gets the output filled with the given fluid */
  protected ItemStack getFilled(FluidStack drained) {
    return this.result.get().copy();
  }

  @Nullable
  @Override
  public TransferResult transfer(ItemStack stack, FluidStack fluid, ResourceHandler<FluidResource> handler, TransferDirection direction, TransactionContext transaction) {
    if (!direction.canFill() || fluid.isEmpty()) {
      return null;
    }
    int amount = this.fluid.getAmount(fluid.getFluid());
    if (amount <= 0) {
      return null;
    }
    FluidStack toDrain = fluid.copyWithAmount(amount);
    FluidResource resource = FluidResource.of(toDrain);
    try (Transaction draining = Transaction.open(transaction)) {
      // must be able to drain the full amount
      int simulated;
      try (Transaction simulate = Transaction.open(draining)) {
        simulated = handler.extract(resource, amount, simulate);
      }
      if (simulated == amount) {
        int actual = handler.extract(resource, amount, draining);
        if (actual != amount) {
          Mantle.logger.error("Wrong amount drained from {}, expected {}, filled {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), fluid.getAmount(), actual);
        }
        draining.commit();
        return new TransferResult(getFilled(toDrain), toDrain, true);
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
    json.add("fluid", fluid.serialize());
    return json;
  }

  /**
   * Unique loader instance
   */
  public static final JsonDeserializer<FillFluidContainerTransfer> DESERIALIZER = new Deserializer<>(FillFluidContainerTransfer::new);

  public record Deserializer<T extends FillFluidContainerTransfer>(TriFunction<Ingredient, ItemOutput, FluidIngredient, T> factory) implements JsonDeserializer<T> {
    @Override
    public T deserialize(JsonElement element, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject json = element.getAsJsonObject();
      Ingredient input = IngredientLoadable.DISALLOW_EMPTY.getIfPresent(json, "input", FluidContainerTransferManager.getLoadContext());
      ItemOutput result = EmptyFluidContainerTransfer.getResult(json);
      FluidIngredient fluid = FluidIngredient.LOADABLE.getIfPresent(json, "fluid", FluidContainerTransferManager.getLoadContext());
      return factory.apply(input, result, fluid);
    }
  }
}
