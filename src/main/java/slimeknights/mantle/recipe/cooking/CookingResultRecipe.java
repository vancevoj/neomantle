package slimeknights.mantle.recipe.cooking;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.recipe.helper.ItemOutput;

import java.util.List;

/** Simplifies the serializers for result recipes */
public interface CookingResultRecipe {
  LoadableField<CookingBookCategory, AbstractCookingRecipe> CATEGORY_FIELD = new EnumLoadable<>(CookingBookCategory.class).defaultField("category", CookingBookCategory.MISC, true, AbstractCookingRecipe::category);
  LoadableField<Float, AbstractCookingRecipe> EXPERIENCE_FIELD = FloatLoadable.FROM_ZERO.defaultField("experience",0f, AbstractCookingRecipe::experience);
  LoadableField<ItemOutput,CookingResultRecipe> RESULT_FIELD = ItemOutput.Loadable.REQUIRED_STACK.requiredField("result", CookingResultRecipe::getResult);

  /** Gets the recipe result */
  ItemOutput getResult();


  /* Helpers for the vanilla constructor, since 26.1 vanilla cooking recipes take an {@link ItemStackTemplate} that cannot be empty */

  /** Common info for result recipes, the JSON format has no notification field so it matches the vanilla default */
  static Recipe.CommonInfo commonInfo() {
    return new Recipe.CommonInfo(true);
  }

  /**
   * Gets the template to pass to the vanilla constructor. Tag outputs are resolved lazily so cannot be passed to vanilla, they use a placeholder instead.
   * The placeholder is never used as the result recipes override {@link Recipe#assemble(net.minecraft.world.item.crafting.RecipeInput)} and {@link Recipe#display()}.
   */
  static ItemStackTemplate vanillaResult(ItemOutput result) {
    if (result.getTag() == null) {
      ItemStack stack = result.get();
      if (!stack.isEmpty()) {
        return ItemStackTemplate.fromNonEmptyStack(stack);
      }
    }
    return new ItemStackTemplate(Items.BARRIER);
  }

  /** Gets the display for the resolved result, or empty if the result does not resolve */
  static SlotDisplay resultDisplay(ItemOutput result) {
    ItemStack stack = result.get();
    if (stack.isEmpty()) {
      return SlotDisplay.Empty.INSTANCE;
    }
    return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
  }

  /**
   * Creates the recipe display for a result recipe, matching {@link AbstractCookingRecipe#display()} but using the resolved result
   * @param recipe       Recipe instance
   * @param result       Recipe result
   * @param furnaceIcon  Crafting station shown in the display
   * @return  Recipe display list
   */
  static List<RecipeDisplay> display(AbstractCookingRecipe recipe, ItemOutput result, Item furnaceIcon) {
    return List.of(new FurnaceRecipeDisplay(
      recipe.input().display(),
      SlotDisplay.AnyFuel.INSTANCE,
      resultDisplay(result),
      new SlotDisplay.ItemSlotDisplay(furnaceIcon),
      recipe.cookingTime(),
      recipe.experience()
    ));
  }
}
