package slimeknights.mantle.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Slot for an item resource handler, allowing picking up from an empty slot and limiting stack size by both the handler and the item.
 * Replaces the old {@code SlotItemHandler} extension.
 */
public class SmartItemHandlerSlot extends ResourceHandlerSlot {
	public SmartItemHandlerSlot(ResourceHandler<ItemResource> itemHandler, IndexModifier<ItemResource> modifier, int index, int xPosition, int yPosition) {
		super(itemHandler, modifier, index, xPosition, yPosition);
	}

	/**
	 * Creates a slot using the handler as the index modifier if it implements {@link IndexModifier} (such as {@link SingleItemHandler},
	 * {@link ContainerItemHandler} or NeoForge's {@code StacksResourceHandler}), otherwise sets the slot by extracting and inserting.
	 */
	public SmartItemHandlerSlot(ResourceHandler<ItemResource> itemHandler, int index, int xPosition, int yPosition) {
		this(itemHandler, getModifier(itemHandler), index, xPosition, yPosition);
	}

	/**
	 * Gets an index modifier for the given handler
	 * @param handler  Handler to modify
	 * @return  The handler if it is an index modifier, otherwise a modifier replacing the contents by extracting the current item and inserting the new one
	 */
	@SuppressWarnings("unchecked")
	public static IndexModifier<ItemResource> getModifier(ResourceHandler<ItemResource> handler) {
		if (handler instanceof IndexModifier<?> modifier) {
			return (IndexModifier<ItemResource>) modifier;
		}
		return (index, resource, amount) -> {
			try (Transaction transaction = Transaction.openRoot()) {
				ItemResource current = handler.getResource(index);
				int currentAmount = handler.getAmountAsInt(index);
				if (!current.isEmpty() && currentAmount > 0 && handler.extract(index, current, currentAmount, transaction) != currentAmount) {
					return;
				}
				if (!resource.isEmpty() && amount > 0 && handler.insert(index, resource, amount, transaction) != amount) {
					return;
				}
				transaction.commit();
			}
		};
	}

	@Override
	public boolean mayPickup(Player playerIn) {
		return getItem().isEmpty() || super.mayPickup(playerIn);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return Math.min(stack.getMaxStackSize(), getResourceHandler().getCapacityAsInt(getSlotIndex(), ItemResource.of(stack)));
	}
}
