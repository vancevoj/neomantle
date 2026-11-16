package slimeknights.mantle.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Represents a fluid handler for a container item with a constant fluid.
 * The fluid can only be drained, one full container at a time; each drained container is exchanged for the empty container through the item access.
 * Register it for an item using {@code event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new ConstantFluidContainerWrapper(fluid, access), item)}.
 */
public class ConstantFluidContainerWrapper implements ResourceHandler<FluidResource> {
  /** Contained fluid in a single container */
  private final FluidStack fluid;
  /** Access to the container stack */
  protected final ItemAccess access;
  /** Filled container item, if the access no longer contains this item then the container was drained */
  private final Item filled;
  /** Empty version of the container, if empty the container is consumed on drain */
  private final ItemResource emptyContainer;

  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access, ItemStack emptyStack) {
    this.fluid = fluid;
    this.access = access;
    this.filled = access.getResource().getItem();
    this.emptyContainer = ItemResource.of(emptyStack);
  }

  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access) {
    this(fluid, access, getRemainder(access.getResource()));
  }

  /** Gets the crafting remainder of the given item, used as the default empty container */
  private static ItemStack getRemainder(ItemResource resource) {
    if (resource.isEmpty()) {
      return ItemStack.EMPTY;
    }
    ItemStackTemplate remainder = resource.toStack().getCraftingRemainder();
    return remainder == null ? ItemStack.EMPTY : remainder.create();
  }

  /** Gets the number of filled containers currently in the access */
  private int getFilledContainers() {
    if (fluid.isEmpty()) {
      return 0;
    }
    ItemResource current = access.getResource();
    return !current.isEmpty() && current.getItem() == filled ? access.getAmount() : 0;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public FluidResource getResource(int index) {
    Objects.checkIndex(index, size());
    return getFilledContainers() > 0 ? FluidResource.of(fluid) : FluidResource.EMPTY;
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return (long) fluid.getAmount() * getFilledContainers();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    Objects.checkIndex(index, size());
    return (long) fluid.getAmount() * access.getAmount();
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmpty(resource);
    return resource.getFluid() == fluid.getFluid();
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return 0;
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    // cannot drain if: already drained, requested the wrong type, or requested too little
    int containers = getFilledContainers();
    if (containers == 0 || !resource.matches(fluid)) {
      return 0;
    }
    int toEmpty = Math.min(containers, amount / fluid.getAmount());
    if (toEmpty <= 0) {
      return 0;
    }
    int emptied;
    if (emptyContainer.isEmpty()) {
      emptied = access.extract(access.getResource(), toEmpty, transaction);
    } else {
      emptied = access.exchange(emptyContainer, toEmpty, transaction);
    }
    return emptied * fluid.getAmount();
  }
}
