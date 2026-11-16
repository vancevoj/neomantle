package slimeknights.mantle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.block.RetexturedBlock;

import java.util.function.Consumer;

public class BlockTooltipItem extends BlockItem {
  public BlockTooltipItem(Block blockIn, Item.Properties builder) {
    super(blockIn, builder);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, context, display, tooltip, flag);
    // blocks no longer add item tooltips since 1.21.5, so add the retextured block tooltip here
    if (getBlock() instanceof RetexturedBlock) {
      RetexturedBlock.appendTextureTooltip(stack, tooltip, flag);
    }
    TooltipItem.addOptionalTooltip(stack, tooltip);
  }
}
