package slimeknights.mantle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import slimeknights.mantle.util.TranslationHelper;

import java.util.List;

public class EdibleItem extends Item {
  private final UseAnim useAnim;
  public EdibleItem(FoodProperties foodIn) {
    this(new Properties().food(foodIn));
  }

  public EdibleItem(UseAnim useAnim, Item.Properties properties) {
    super(properties);
    this.useAnim = useAnim;
    if (!this.components().has(DataComponents.FOOD)) {
      throw new IllegalArgumentException("Must set food to make an EdibleItem");
    }
  }

  public EdibleItem(Item.Properties properties) {
    this(UseAnim.EAT, properties);
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    return useAnim;
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
    TranslationHelper.addOptionalTooltip(stack, tooltip);
    FoodProperties food = stack.get(DataComponents.FOOD);
    if (food != null) {
      ContainerFoodItem.addEffectTooltip(food, tooltip);
    }
  }
}
