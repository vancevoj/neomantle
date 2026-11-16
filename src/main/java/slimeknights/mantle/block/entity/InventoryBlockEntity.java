package slimeknights.mantle.block.entity;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import slimeknights.mantle.inventory.ContainerItemHandler;
import org.slf4j.Logger;
import slimeknights.mantle.util.ItemStackList;

// Updated version of InventoryLogic in Mantle. Also contains a few bugfixes DOES NOT OVERRIDE createMenu
public abstract class InventoryBlockEntity extends NameableBlockEntity implements Container, MenuProvider, Nameable {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String TAG_INVENTORY_SIZE = "InventorySize";
  private static final String TAG_ITEMS = "Items";
  /**
   * Default stack size limit. Acts as a high ceiling so the real cap is each item's own max stack size
   * ({@link ItemStack#getMaxStackSize()}), matching the rest of the game. Previously this was a flat 64,
   * which capped tinker station / workbench slots at 64 even when a mod (e.g. Stack Size Tweaks) raised
   * item stacks to 256, producing a stack-size mismatch and an item duplication bug. Inventories that
   * truly need a smaller cap (e.g. the casting table at 1) still pass an explicit limit.
   */
  public static final int DEFAULT_STACK_SIZE_LIMIT = 1_000_000_000;

  private NonNullList<ItemStack> inventory;
  /** If true, the inventory size is saved to NBT, false means you are responsible for serializing it if it changes */
  private final boolean saveSizeToNBT;
  protected int stackSizeLimit;
  /**
   * Item handler exposing this inventory. The capability is not provided by the block entity;
   * the owning mod registers this handler in {@code RegisterCapabilitiesEvent} via
   * {@code event.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.getItemHandler())}.
   * By default a {@link ContainerItemHandler}, which applies changes through {@link #setItem(int, ItemStack)} when the transaction commits
   * and respects {@link #getMaxStackSize(ItemStack)}.
   */
  @Getter
  protected ResourceHandler<ItemResource> itemHandler;

  /**
   * @param name Localization String for the inventory title. Can be overridden through setCustomName
   */
  public InventoryBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state, Component name, boolean saveSizeToNBT, int inventorySize) {
    this(tileEntityTypeIn, pos, state, name, saveSizeToNBT, inventorySize, DEFAULT_STACK_SIZE_LIMIT);
  }

  /**
   * @param name Localization String for the inventory title. Can be overridden through setCustomName
   */
  public InventoryBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state, Component name, boolean saveSizeToNBT, int inventorySize, int maxStackSize) {
    super(tileEntityTypeIn, pos, state, name);
    this.saveSizeToNBT = saveSizeToNBT;
    this.inventory = NonNullList.withSize(inventorySize, ItemStack.EMPTY);
    this.stackSizeLimit = maxStackSize;
    this.itemHandler = new ContainerItemHandler(this);
  }

  /* Inventory management */

  @Override
  public ItemStack getItem(int slot) {
    if (slot < 0 || slot >= this.inventory.size()) {
      return ItemStack.EMPTY;
    }

    return this.inventory.get(slot);
  }

  public boolean isStackInSlot(int slot) {
    return !this.getItem(slot).isEmpty();
  }

  /**
   * Same as resize, but does not call markDirty. Used on loading from NBT
   */
  private void resizeInternal(int size) {
    // save effort if the size did not change
    if (size == this.inventory.size()) {
      return;
    }
    ItemStackList newInventory = ItemStackList.withSize(size);

    for (int i = 0; i < size && i < this.inventory.size(); i++) {
      newInventory.set(i, this.inventory.get(i));
    }
    this.inventory = newInventory;
  }

  public void resize(int size) {
    this.resizeInternal(size);
    this.setChangedFast();
  }

  @Override
  public int getContainerSize() {
    return this.inventory.size();
  }

  @Override
  public int getMaxStackSize() {
    return this.stackSizeLimit;
  }

  /**
   * Per-item stack limit: the smaller of this inventory's configured limit and the item's own max stack size.
   * This keeps slots and storage in sync with the rest of the game (e.g. a 256-stack item stacks to 256 here,
   * a 16-stack item to 16, a tool to 1), instead of a flat 64 that mismatched modded stack sizes and duped items.
   */
  @Override
  public int getMaxStackSize(ItemStack stack) {
    return Math.min(this.stackSizeLimit, stack.getMaxStackSize());
  }

  @Override
  public void setItem(int slot, ItemStack itemstack) {
    if (slot < 0 || slot >= this.inventory.size()) {
      return;
    }

    ItemStack current = this.inventory.get(slot);
    this.inventory.set(slot, itemstack);

    int limit = this.getMaxStackSize(itemstack);
    if (!itemstack.isEmpty() && itemstack.getCount() > limit) {
      itemstack.setCount(limit);
    }
    if (!ItemStack.matches(current, itemstack)) {
      this.setChangedFast();
    }
  }

  @Override
  public ItemStack removeItem(int slot, int quantity) {
    if (quantity <= 0) {
      return ItemStack.EMPTY;
    }
    ItemStack itemStack = this.getItem(slot);

    if (itemStack.isEmpty()) {
      return ItemStack.EMPTY;
    }

    // whole itemstack taken out
    if (itemStack.getCount() <= quantity) {
      this.setItem(slot, ItemStack.EMPTY);
      this.setChangedFast();
      return itemStack;
    }

    // split itemstack
    itemStack = itemStack.split(quantity);
    // slot is empty, set to ItemStack.EMPTY
    // isn't this redundant to the above check?
    if (this.getItem(slot).getCount() == 0) {
      this.setItem(slot, ItemStack.EMPTY);
    }

    this.setChangedFast();
    // return remainder
    return itemStack;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    ItemStack itemStack = this.getItem(slot);
    this.setItem(slot, ItemStack.EMPTY);
    return itemStack;
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack itemstack) {
    if (slot < this.getContainerSize()) {
      return this.inventory.get(slot).isEmpty() || itemstack.getCount() + this.inventory.get(slot).getCount() <= this.getMaxStackSize(itemstack);
    }
    return false;
  }

  @Override
  public void clearContent() {
    for (int i = 0; i < this.inventory.size(); i++) {
      this.inventory.set(i, ItemStack.EMPTY);
    }
  }

  /* Supporting methods */
  @Override
  public boolean stillValid(Player entityplayer) {
    // block changed/got broken?
    if (level == null || this.level.getBlockEntity(this.worldPosition) != this || this.getBlockState().getBlock() == Blocks.AIR) {
      return false;
    }

    return entityplayer.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64D;
  }

  /* NBT */

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    if (saveSizeToNBT) {
      this.resizeInternal(input.getIntOr(TAG_INVENTORY_SIZE, 0));
    }
    this.readInventoryFromNBT(input);
  }

  @Override
  public void saveSynced(ValueOutput output) {
    super.saveSynced(output);
    // only sync the size to the client by default
    if (saveSizeToNBT) {
      output.putInt(TAG_INVENTORY_SIZE, this.inventory.size());
    }
  }

  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    this.writeInventoryToNBT(output);
  }

  /**
   * Writes the contents of the inventory to the output, in the vanilla {@code Items} list format with a {@code Slot} byte per stack
   */
  public void writeInventoryToNBT(ValueOutput output) {
    Container inventory = this;
    ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        list.add(new ItemStackWithSlot(i, stack));
      }
    }
  }

  /**
   * Reads an inventory from the input. Overwrites current content of each slot present in the input
   */
  public void readInventoryFromNBT(ValueInput input) {
    for (ItemStackWithSlot entry : input.listOrEmpty(TAG_ITEMS, ItemStackWithSlot.CODEC)) {
      if (entry.isValidInContainer(this.inventory.size())) {
        ItemStack stack = entry.stack();
        int limit = this.getMaxStackSize(stack);
        if (!stack.isEmpty() && stack.getCount() > limit) {
          stack.setCount(limit);
        }
        this.inventory.set(entry.slot(), stack);
      }
    }
  }

  /**
   * Writes the contents of the inventory to the tag, for code still working with raw tags (such as update tags)
   * @see #writeInventoryToNBT(ValueOutput)
   */
  public void writeInventoryToNBT(CompoundTag tag, HolderLookup.Provider registries) {
    try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
      TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
      writeInventoryToNBT(output);
      tag.merge(output.buildResult());
    }
  }

  /**
   * Reads an inventory from the tag, for code still working with raw tags (such as update tags)
   * @see #readInventoryFromNBT(ValueInput)
   */
  public void readInventoryFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
    try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
      readInventoryFromNBT(TagValueInput.create(problems, registries, tag));
    }
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack itemstack : this.inventory) {
      if (!itemstack.isEmpty()) {
        return false;
      }
    }

    return true;
  }
}
