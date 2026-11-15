package slimeknights.mantle.recipe.ingredient;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * Ingredient matching only empty stacks. Since 1.21.2 vanilla ingredients can no longer be empty, so this represents the empty ingredient
 * from before, used notably by {@link slimeknights.mantle.data.loadable.common.IngredientLoadable#ALLOW_EMPTY}.
 * Registered by {@link MantleIngredients} so it can be synced.
 */
public enum EmptyIngredient implements ICustomIngredient {
  INSTANCE;

  /** Ingredient type instance, registered by {@link MantleIngredients} */
  public static final IngredientType<EmptyIngredient> TYPE = new IngredientType<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));
  /** Vanilla ingredient instance wrapping this ingredient */
  public static final Ingredient VANILLA = INSTANCE.toVanilla();

  @Override
  public boolean test(ItemStack stack) {
    return stack.isEmpty();
  }

  @Override
  public Stream<Holder<Item>> items() {
    return Stream.empty();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  @Override
  public SlotDisplay display() {
    return SlotDisplay.Empty.INSTANCE;
  }
}
