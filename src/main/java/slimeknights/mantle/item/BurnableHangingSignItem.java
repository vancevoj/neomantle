package slimeknights.mantle.item;

import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.level.block.Block;

/**
 * Hanging sign item usable as furnace fuel.
 * @see BurnableBlockItem#withBurnTime(Properties, int)
 */
public class BurnableHangingSignItem extends HangingSignItem {
  public BurnableHangingSignItem(Properties propertiesIn, Block hangingBlock, Block wallBlock, int burnTime) {
    super(hangingBlock, wallBlock, BurnableBlockItem.withBurnTime(propertiesIn.signText(), burnTime));
  }
}
