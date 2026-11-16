package slimeknights.mantle.recipe.ingredient;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;
import slimeknights.mantle.registration.object.FluidObject;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Stream;

/** Ingredient that matches a container of fluid */
@SuppressWarnings("unused")  // API
public class FluidContainerIngredient implements ICustomIngredient {
  /** Loadable for parsing and serializing this ingredient */
  public static final RecordLoadable<FluidContainerIngredient> LOADABLE = RecordLoadable.create(
    FluidIngredient.LOADABLE.requiredField("fluid", i -> i.fluidIngredient),
    IngredientLoadable.ALLOW_EMPTY.nullableField("display", i -> i.display),
    FluidContainerIngredient::new);
  /** Ingredient type instance, registered by {@link MantleIngredients} */
  public static final IngredientType<FluidContainerIngredient> TYPE = new LoadableIngredientSerializer<>(LOADABLE).type();

  /** Ingredient to use for matching */
  private final FluidIngredient fluidIngredient;
  /** Internal ingredient to display the ingredient recipe viewers */
  @Nullable
  private final Ingredient display;
  protected FluidContainerIngredient(FluidIngredient fluidIngredient, @Nullable Ingredient display) {
    this.fluidIngredient = fluidIngredient;
    this.display = display;
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient, Ingredient display) {
    return new FluidContainerIngredient(ingredient, display);
  }

  /** Creates an instance from a fluid ingredient with no display, not recommended */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient) {
    return new FluidContainerIngredient(ingredient, null);
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromFluid(FluidObject<?> fluid) {
    return fromIngredient(fluid.ingredient(FluidType.BUCKET_VOLUME), Ingredient.of(fluid.asItem()));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    // first, must have a fluid capability
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    // work on a copy in a private single slot handler, so the container is free to change item when drained (e.g. bucket to empty bucket)
    ItemStacksResourceHandler container = new ItemStacksResourceHandler(1);
    container.set(0, ItemResource.of(stack), 1);
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndexStrict(container, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler == null || handler.size() != 1) {
      return false;
    }
    // second, must contain enough fluid
    FluidResource contained = handler.getResource(0);
    if (contained.isEmpty()) {
      return false;
    }
    Fluid fluid = contained.getFluid();
    int amount = fluidIngredient.getAmount(fluid);
    if (amount != handler.getAmountAsInt(0) || !fluidIngredient.test(fluid)) {
      return false;
    }
    // alright, we know it has the fluid, the question is just whether draining the fluid will give us the desired result
    // nested in the current transaction if one is open, as opening a root transaction would throw. never committed, so nothing changes
    Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
    if (lifecycle != Transaction.Lifecycle.NONE && lifecycle != Transaction.Lifecycle.OPEN) {
      // cannot open a transaction while one is closing, so we cannot check the drain result
      return false;
    }
    @SuppressWarnings("deprecation")
    TransactionContext parent = lifecycle == Transaction.Lifecycle.OPEN ? Transaction.getCurrentOpenedTransaction() : null;
    try (Transaction transaction = Transaction.open(parent)) {
      int drained = handler.extract(0, contained, amount, transaction);
      // we need an exact match, and we need the resulting container item to be the same as the item stack's container item
      if (drained != amount) {
        return false;
      }
      ItemStackTemplate remainder = stack.getCraftingRemainder();
      ItemStack expected = remainder == null ? ItemStack.EMPTY : remainder.create();
      return ItemStack.matches(expected, ItemUtil.getStack(container, 0));
    }
  }

  @Override
  public Stream<Holder<Item>> items() {
    // no container? unfortunately hard to display this recipe so show nothing
    if (display == null) {
      return Stream.empty();
    }
    return display.items();
  }

  @Override
  public SlotDisplay display() {
    if (display == null) {
      return SlotDisplay.Empty.INSTANCE;
    }
    return display.display();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof FluidContainerIngredient other && fluidIngredient.equals(other.fluidIngredient) && Objects.equals(display, other.display);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fluidIngredient, display);
  }
}
