package slimeknights.mantle.inventory;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;
import slimeknights.mantle.block.entity.MantleBlockEntity;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Item handler containing exactly one item.
 * <p>
 * Changes made through the transfer API are kept in the handler while the transaction is open, and are applied using {@link #setStack(ItemStack)}
 * when the root transaction commits. This means overrides of {@link #setStack(ItemStack)} (for instance to sync the stack) run once per change,
 * and never for simulated transfers.
 */
@SuppressWarnings("unused")
public abstract class SingleItemHandler<T extends MantleBlockEntity> extends SnapshotJournal<SingleItemHandler.Pending> implements ResourceHandler<ItemResource>, IndexModifier<ItemResource> {
  private static final Logger LOGGER = LogUtils.getLogger();

  protected final T parent;
  private final int maxStackSize;

  /** Current item in this slot */
  private ItemStack stack = ItemStack.EMPTY;
  /** Stack set during the current transaction, null if unchanged. Never modified, only replaced */
  @Nullable
  private ItemStack pending = null;

  public SingleItemHandler(T parent, int maxStackSize) {
    this.parent = parent;
    this.maxStackSize = maxStackSize;
  }

  /** Gets the current item in this slot, ignoring changes in transactions that are not yet committed */
  public ItemStack getStack() {
    return stack;
  }

  /**
   * Sets the stack in this duct
   * @param newStack  New stack
   */
  public void setStack(ItemStack newStack) {
    this.stack = newStack;
    parent.setChangedFast();
  }

  /**
   * Checks if the given stack is valid for this slot
   * @param stack  Stack
   * @return  True if valid
   */
  protected abstract boolean isItemValid(ItemStack stack);

  /** Gets the stack including changes from the current transaction. Must not be modified */
  private ItemStack getCurrent() {
    return pending != null ? pending : stack;
  }

  /** Gets the slot limit for the given item */
  private int getLimit(ItemResource resource) {
    return Math.min(maxStackSize, resource.getMaxStackSize());
  }


  /* Properties */

  @Override
  public int size() {
    return 1;
  }

  @Override
  public ItemResource getResource(int index) {
    Objects.checkIndex(index, size());
    return ItemResource.of(getCurrent());
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return getCurrent().getCount();
  }

  @Override
  public long getCapacityAsLong(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    return resource.isEmpty() ? maxStackSize : getLimit(resource);
  }

  @Override
  public boolean isValid(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmpty(resource);
    return isItemValid(resource.toStack());
  }


  /* Interaction */

  /** Sets the stack immediately, should not be called during a transaction */
  @Override
  public void set(int index, ItemResource resource, int amount) {
    Objects.checkIndex(index, size());
    setStack(resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount));
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    ItemStack current = getCurrent();
    if (current.isEmpty()) {
      // insert up to the stack limit
      int size = Math.min(amount, getLimit(resource));
      if (size > 0 && isItemValid(resource.toStack(size))) {
        updateSnapshots(transaction);
        pending = resource.toStack(size);
        return size;
      }
    } else if (resource.matches(current)) {
      // increase up to the stack limit
      int added = Math.min(amount, getLimit(resource) - current.getCount());
      if (added > 0) {
        updateSnapshots(transaction);
        pending = current.copyWithCount(current.getCount() + added);
        return added;
      }
    }
    return 0;
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    ItemStack current = getCurrent();
    if (amount == 0 || current.isEmpty() || !resource.matches(current)) {
      return 0;
    }
    int extracted = Math.min(amount, current.getCount());
    updateSnapshots(transaction);
    pending = extracted < current.getCount() ? current.copyWithCount(current.getCount() - extracted) : ItemStack.EMPTY;
    return extracted;
  }


  /* Transactions */

  /** Snapshot of the pending stack */
  protected record Pending(@Nullable ItemStack stack) {}

  @Override
  protected Pending createSnapshot() {
    return new Pending(pending);
  }

  @Override
  protected void revertToSnapshot(Pending snapshot) {
    pending = snapshot.stack();
  }

  @Override
  protected void onRootCommit(Pending originalState) {
    if (pending != null) {
      ItemStack newStack = pending;
      pending = null;
      setStack(newStack);
    }
  }


  /* NBT */

  /**
   * Writes the stack to the given output, using the same fields as the old NBT format. Typically called with a child output.
   * @param output  Output
   */
  public void writeToNBT(ValueOutput output) {
    if (!stack.isEmpty()) {
      output.store(ItemStack.MAP_CODEC, stack);
    }
  }

  /**
   * Reads the stack from the given input
   * @param input  Input
   */
  public void readFromNBT(ValueInput input) {
    // empty slots store no item (or an empty compound), reading those would report a decode problem for each
    if (input.getStringOr("id", "").isEmpty()) {
      stack = ItemStack.EMPTY;
    } else {
      stack = input.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY);
    }
  }

  /**
   * Writes this module to NBT
   * @param registries  Registry access
   * @return  Module in NBT
   */
  public CompoundTag writeToNBT(HolderLookup.Provider registries) {
    if (!stack.isEmpty()) {
      return (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }
    return new CompoundTag();
  }

  /**
   * Reads this module from NBT
   * @param nbt         NBT
   * @param registries  Registry access
   */
  public void readFromNBT(CompoundTag nbt, HolderLookup.Provider registries) {
    stack = nbt.isEmpty() ? ItemStack.EMPTY : ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt)
                                                                      .resultOrPartial(error -> LOGGER.error("Failed to read item: {}", error))
                                                                      .orElse(ItemStack.EMPTY);
  }
}
