package slimeknights.mantle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import slimeknights.mantle.util.TranslationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Item with automatic tooltip support
 */
public class TooltipItem extends Item {

  public TooltipItem(Properties properties) {
    super(properties);
  }

  /** Adds the optional tooltip for the given stack to the tooltip consumer */
  static void addOptionalTooltip(ItemStack stack, Consumer<Component> tooltip) {
    List<Component> lines = new ArrayList<>();
    TranslationHelper.addOptionalTooltip(stack, lines);
    lines.forEach(tooltip);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    addOptionalTooltip(stack, tooltip);
    super.appendHoverText(stack, context, display, tooltip, flag);
  }
}
