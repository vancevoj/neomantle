package slimeknights.mantle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Consumer;

/**
 * Armor item with automatic tooltip support.
 * Vanilla removed {@code ArmorItem} in 1.21.5, so this is a plain item given the armor components through {@link Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)}.
 */
public class ArmorTooltipItem extends Item {
  public ArmorTooltipItem(ArmorMaterial armorMaterial, ArmorType type, Properties builder) {
    super(builder.humanoidArmor(armorMaterial, type));
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    TooltipItem.addOptionalTooltip(stack, tooltip);
    super.appendHoverText(stack, context, display, tooltip, flag);
  }
}
