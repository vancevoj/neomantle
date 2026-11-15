package slimeknights.mantle.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Item resource handler over a vanilla {@link Container}, replacing NeoForge's removed {@code InvWrapper}.
 * <p>
 * Unlike {@link net.neoforged.neoforge.transfer.item.VanillaContainerWrapper}, changes are kept in this handler while a transaction is open and are only
 * applied to the container through {@link Container#setItem(int, ItemStack)} once the root transaction commits, followed by {@link Container#setChanged()}.
 * This means side effects of {@code setItem} (syncing packets, block updates) run once per actual change and never for simulated transfers, matching the old
 * {@code InvWrapper} behavior. Capacity respects {@link Container#getMaxStackSize(ItemStack)} for each item, and insertion is validated using
 * {@link Container#canPlaceItem(int, ItemStack)} with the stack that would be inserted.
 * <p>
 * Also implements {@link IndexModifier} to directly set a slot outside of transactions, for use with {@link net.neoforged.neoforge.transfer.item.ResourceHandlerSlot}.
 */
public class ContainerItemHandler extends SnapshotJournal<Int2ObjectMap<ItemStack>> implements ResourceHandler<ItemResource>, IndexModifier<ItemResource> {
  /** Wrapped container */
  protected final Container container;
  /** Stacks changed in the current transaction by slot, empty outside of transactions. Stacks in this map are never modified, only replaced */
  private Int2ObjectMap<ItemStack> pending = new Int2ObjectOpenHashMap<>();

  public ContainerItemHandler(Container container) {
    this.container = container;
  }

  /**
   * Gets the stack in the given slot, including changes made in the current transaction.
   * @param index  Slot index
   * @return  Stack in the slot, must not be modified
   */
  protected ItemStack getStack(int index) {
    ItemStack stack = pending.get(index);
    return stack != null ? stack : container.getItem(index);
  }

  @Override
  public int size() {
    return container.getContainerSize();
  }

  @Override
  public ItemResource getResource(int index) {
    Objects.checkIndex(index, size());
    return ItemResource.of(getStack(index));
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return getStack(index).getCount();
  }

  @Override
  public long getCapacityAsLong(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    return resource.isEmpty() ? container.getMaxStackSize() : container.getMaxStackSize(resource.toStack());
  }

  @Override
  public boolean isValid(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmpty(resource);
    return container.canPlaceItem(index, resource.toStack());
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    ItemStack current = getStack(index);
    if (!current.isEmpty() && !resource.matches(current)) {
      return 0;
    }
    int inserted = Math.min(amount, container.getMaxStackSize(resource.toStack()) - current.getCount());
    if (inserted <= 0 || !container.canPlaceItem(index, resource.toStack(inserted))) {
      return 0;
    }
    updateSnapshots(transaction);
    pending.put(index, resource.toStack(current.getCount() + inserted));
    return inserted;
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    ItemStack current = getStack(index);
    if (current.isEmpty() || !resource.matches(current)) {
      return 0;
    }
    int extracted = Math.min(amount, current.getCount());
    if (extracted <= 0) {
      return 0;
    }
    updateSnapshots(transaction);
    int remaining = current.getCount() - extracted;
    pending.put(index, remaining > 0 ? current.copyWithCount(remaining) : ItemStack.EMPTY);
    return extracted;
  }

  /** Sets the given slot immediately, should not be called during a transaction */
  @Override
  public void set(int index, ItemResource resource, int amount) {
    Objects.checkIndex(index, size());
    container.setItem(index, resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount));
    container.setChanged();
  }


  /* Transactions */

  @Override
  protected Int2ObjectMap<ItemStack> createSnapshot() {
    // stacks are replaced rather than modified, so a shallow copy is enough
    return new Int2ObjectOpenHashMap<>(pending);
  }

  @Override
  protected void revertToSnapshot(Int2ObjectMap<ItemStack> snapshot) {
    pending = snapshot;
  }

  @Override
  protected void onRootCommit(Int2ObjectMap<ItemStack> originalState) {
    if (!pending.isEmpty()) {
      Int2ObjectMap<ItemStack> changes = pending;
      pending = new Int2ObjectOpenHashMap<>();
      int size = container.getContainerSize();
      for (Int2ObjectMap.Entry<ItemStack> entry : changes.int2ObjectEntrySet()) {
        int index = entry.getIntKey();
        if (index < size) {
          container.setItem(index, entry.getValue());
        }
      }
      container.setChanged();
    }
  }

  @Override
  public String toString() {
    return "ContainerItemHandler[" + container + "]";
  }
}
