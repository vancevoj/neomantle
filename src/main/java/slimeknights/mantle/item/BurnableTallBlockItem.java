package slimeknights.mantle.item;

import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Double high block item usable as furnace fuel.
 * @see BurnableBlockItem#withBurnTime(Properties, int)
 */
public class BurnableTallBlockItem extends DoubleHighBlockItem {
  public BurnableTallBlockItem(Block blockIn, Properties builder, int burnTime) {
    super(blockIn, BurnableBlockItem.withBurnTime(builder, burnTime));
  }
}
