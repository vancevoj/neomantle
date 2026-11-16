package slimeknights.mantle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Block item usable as furnace fuel.
 * Since 26.3 the burn time is the {@link DataComponents#COOKING_FUEL} component, which this item adds to its properties.
 */
public class BurnableBlockItem extends BlockItem {
  public BurnableBlockItem(Block blockIn, Properties builder, int burnTime) {
    super(blockIn, withBurnTime(builder, burnTime));
  }

  /**
   * Sets the given constant burn time as the cooking fuel on the properties, using the vanilla speed multiplier.
   * @param properties  Properties to modify
   * @param burnTime    Burn time in ticks
   * @return  Properties instance
   */
  public static Item.Properties withBurnTime(Item.Properties properties, int burnTime) {
    return properties.component(DataComponents.COOKING_FUEL, new CookingFuel(new ResolvableInt.Constant(burnTime), ResolvableFloat.fromKey(ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)));
  }
}
