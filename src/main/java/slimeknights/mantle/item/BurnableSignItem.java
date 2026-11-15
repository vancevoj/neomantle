package slimeknights.mantle.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Sign item usable as furnace fuel.
 * Vanilla removed {@code SignItem} in 26.3, signs are now a {@link StandingAndWallBlockItem} with {@link Item.Properties#signText()}.
 * @see BurnableBlockItem#withBurnTime(Item.Properties, int)
 */
public class BurnableSignItem extends StandingAndWallBlockItem {
  public BurnableSignItem(Item.Properties propertiesIn, Block floorBlockIn, Block wallBlockIn, int burnTime) {
    super(floorBlockIn, wallBlockIn, Direction.DOWN, BurnableBlockItem.withBurnTime(propertiesIn.signText(), burnTime));
  }
}
