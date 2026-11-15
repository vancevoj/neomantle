package slimeknights.mantle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.book.BookScreenOpener;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.util.RegistryHelper;

import java.util.function.Consumer;

/** Item implementing all standard book behaviors, just requires calling methods from {@link slimeknights.mantle.client.book.data.BookData} in a few abstract methods. */
@SuppressWarnings("unused")  // API
public abstract class AbstractBookItem extends LecternBookItem {
  private static final Component CLICK_TO_OPEN = Mantle.makeComponent("item", "book.click_to_open").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);

  public AbstractBookItem(Properties properties) {
    super(properties);
  }

  /** Gets the book for the given item stack */
  public abstract BookScreenOpener getBook(ItemStack stack);

  /** Checks if the given menu supports opening the menu */
  public static boolean isValidContainer(AbstractContainerMenu menu) {
    // player inventory has a null type, which throws when used through the getter (vanilla only throws for a null type)
    MenuType<?> type;
    try {
      type = menu.getType();
    }
    catch (UnsupportedOperationException e) {
      return true;
    }
    // because vanilla set the throw precedent, add protection for other cases, just in case
    // the try here is basically free
    try {
      return RegistryHelper.contains(BuiltInRegistries.MENU, MantleTags.MenuTypes.REPLACEABLE, type);
    }
    catch (UnsupportedOperationException e) {
      return false;
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    // if the stack is in the player inventory, show the right click to open tooltip
    Level world = context.level();
    if (world != null && world.isClientSide()) {
      Player player = SafeClientAccess.getPlayer();
      if (player != null && isValidContainer(player.containerMenu)) {
        Inventory inventory = player.getInventory();
        if (inventory.getNonEquipmentItems().contains(stack) || player.getOffhandItem() == stack) {
          tooltip.accept(CLICK_TO_OPEN);
        }
      }
    }
    super.appendHoverText(stack, context, display, tooltip, flag);
  }

  /** Called on the client to open the screen when used on right click in the hand */
  public void openScreen(Player player, InteractionHand hand, ItemStack stack) {
    getBook(stack).openGui(hand, stack);
  }

  @Override
  public InteractionResult use(Level world, Player player, InteractionHand hand) {
    if (world.isClientSide()) {
      openScreen(player, hand, player.getItemInHand(hand));
    }
    return InteractionResult.SUCCESS;
  }

  /** Called on the client to open the screen when right-clicked in the GUI */
  public void openScreen(Player player, int slotIndex, ItemStack stack) {
    getBook(stack).openGui(slotIndex, stack);
  }

  @Override
  public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    // on right-clicking the book with empty held, if this container allows we close and reopen the book page
    if (action == ClickAction.SECONDARY && held.isEmpty() && slot.container == player.getInventory() && slot.allowModification(player) && isValidContainer(player.containerMenu)) {
      if (player.level().isClientSide()) {
        player.containerMenu.resumeRemoteUpdates();
        player.closeContainer();
        openScreen(player, slot.getSlotIndex(), stack);
      }
      return true;
    }
    return false;
  }

  @Override
  public void openLecternScreenClient(BlockPos pos, ItemStack book) {
    getBook(book).openGui(pos, book);
  }
}
