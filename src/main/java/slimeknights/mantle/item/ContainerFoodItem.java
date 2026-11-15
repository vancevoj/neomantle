package slimeknights.mantle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Food item with a container that is returned when the item is consumed. Supports eating stackable items with containers.
 * Technically also works for items with no container.
 * <p>
 * Since 1.21.2, the food effects are part of the {@link Consumable} component, set using {@link Item.Properties#food(net.minecraft.world.food.FoodProperties, Consumable)}.
 */
@SuppressWarnings("unused") // API
public class ContainerFoodItem extends Item {
  private final ItemUseAnimation useAnim;
  public ContainerFoodItem(Properties props, ItemUseAnimation useAnim) {
    super(props);
    this.useAnim = useAnim;
  }

  public ContainerFoodItem(Properties props) {
    this(props, ItemUseAnimation.DRINK);
  }

  @Override
  public ItemUseAnimation getUseAnimation(ItemStack pStack) {
    return useAnim;
  }

  /** Adds a single effect to the tooltip, code based on potion items */
  public static void addEffectTooltip(MobEffectInstance effect, Consumer<Component> tooltip) {
    MutableComponent mutable = Component.translatable(effect.getDescriptionId());
    if (effect.getAmplifier() > 0) {
      mutable = Component.translatable("potion.withAmplifier", mutable, Component.translatable("potion.potency." + effect.getAmplifier()));
    }
    if (effect.getDuration() > 20) {
      mutable = Component.translatable("potion.withDuration", mutable, MobEffectUtil.formatDuration(effect, 1.0f, 20.0f));
    }
    tooltip.accept(mutable.withStyle(effect.getEffect().value().getCategory().getTooltipFormatting()));
  }

  /** Adds the given effects to the tooltip */
  public static void addEffectTooltip(Collection<MobEffectInstance> effects, Consumer<Component> tooltip) {
    for (MobEffectInstance effect : effects) {
      addEffectTooltip(effect, tooltip);
    }
  }

  /** Adds all effects applied when consumed to the tooltip */
  public static void addEffectTooltip(Consumable consumable, Consumer<Component> tooltip) {
    for (ConsumeEffect effect : consumable.onConsumeEffects()) {
      if (effect instanceof ApplyStatusEffectsConsumeEffect statusEffects) {
        addEffectTooltip(statusEffects.effects(), tooltip);
      }
    }
  }

  /** Adds all effects applied when consumed to the tooltip */
  public static void addEffectTooltip(Consumable consumable, List<Component> tooltip) {
    addEffectTooltip(consumable, (Consumer<Component>) tooltip::add);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    Consumable consumable = stack.get(DataComponents.CONSUMABLE);
    if (consumable != null) {
      addEffectTooltip(consumable, tooltip);
    }
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
    ItemStackTemplate remainder = stack.getCraftingRemainder();
    ItemStack result = super.finishUsingItem(stack, level, living);
    Player player = living instanceof Player p ? p : null;
    if (remainder != null && (player == null || !player.getAbilities().instabuild)) {
      ItemStack container = remainder.create();
      if (!container.isEmpty()) {
        if (result.isEmpty()) {
          return container;
        }
        if (player != null) {
          // adds to the inventory or drops serverside, like vanilla use remainders
          player.handleExtraItemsCreatedOnUse(container);
        }
      }
    }
    return result;
  }

  /**
   * Fluid containing variant of {@link ContainerFoodItem}.
   * <p>
   * Item capabilities are not provided by the item; instead the owning mod must register the fluid handler for this item
   * in {@code RegisterCapabilitiesEvent} via
   * {@code event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> item.getFluidHandler(access), item)}.
   * {@link #getFluidHandler(ItemAccess)} builds the handler for that registration.
   */
  public static class FluidContainerFoodItem extends ContainerFoodItem {
    private final Supplier<FluidStack> fluid;
    public FluidContainerFoodItem(Properties props, Supplier<FluidStack> fluid) {
      super(props);
      this.fluid = fluid;
    }

    /** Builds the fluid handler for the given item access, for registration in {@code RegisterCapabilitiesEvent}. */
    public ResourceHandler<FluidResource> getFluidHandler(ItemAccess access) {
      return new ConstantFluidContainerWrapper(fluid.get(), access);
    }
  }
}
