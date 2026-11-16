package slimeknights.mantle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Food item showing its optional tooltip and the effects it applies when eaten.
 * Food must be set through {@link Item.Properties#food(net.minecraft.world.food.FoodProperties)} or
 * {@link Item.Properties#food(net.minecraft.world.food.FoodProperties, Consumable)}. Since 26.1 default components are bound
 * after registration, so this can no longer be validated in the constructor.
 */
public class EdibleItem extends Item {
  private final ItemUseAnimation useAnim;

  public EdibleItem(ItemUseAnimation useAnim, Item.Properties properties) {
    super(properties);
    this.useAnim = useAnim;
  }

  public EdibleItem(Item.Properties properties) {
    this(ItemUseAnimation.EAT, properties);
  }

  @Override
  public ItemUseAnimation getUseAnimation(ItemStack stack) {
    return useAnim;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    TooltipItem.addOptionalTooltip(stack, tooltip);
    Consumable consumable = stack.get(DataComponents.CONSUMABLE);
    if (consumable != null) {
      ContainerFoodItem.addEffectTooltip(consumable, tooltip);
    }
  }
}
