package slimeknights.mantle.recipe.cooking;

import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;

import java.util.List;

/** Extension of {@link SmokingRecipe} to support {@link ItemOutput} */
@Getter
public class SmokingResultRecipe extends SmokingRecipe implements CookingResultRecipe {
  public static LoadableField<Integer, AbstractCookingRecipe> COOKING_TIME_FIELD = IntLoadable.FROM_ONE.defaultField("cooking_time", 100, true, AbstractCookingRecipe::cookingTime);
  public static final RecordLoadable<SmokingResultRecipe> LOADABLE = RecordLoadable.create(
    LoadableRecipeSerializer.RECIPE_GROUP, CookingResultRecipe.CATEGORY_FIELD,
    IngredientLoadable.DISALLOW_EMPTY.requiredField("ingredient", r -> r.input()),
    RESULT_FIELD, EXPERIENCE_FIELD, COOKING_TIME_FIELD,
    SmokingResultRecipe::new);

  private final ItemOutput result;
  public SmokingResultRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemOutput result, float experience, int cookingTime) {
    super(CookingResultRecipe.commonInfo(), new AbstractCookingRecipe.CookingBookInfo(category, group), ingredient, CookingResultRecipe.vanillaResult(result), experience, cookingTime);
    this.result = result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})  // vanilla types the serializer as the parent class
  @Override
  public RecipeSerializer<SmokingRecipe> getSerializer() {
    return (RecipeSerializer) MantleRecipes.SMOKING.get();
  }

  /** Gets the result of this recipe for display, Mantle API as {@link net.minecraft.world.item.crafting.Recipe} no longer has it */
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return result.get();
  }

  @Override
  public ItemStack assemble(SingleRecipeInput input) {
    return result.copy();
  }

  @Override
  public List<RecipeDisplay> display() {
    return CookingResultRecipe.display(this, result, furnaceIcon());
  }
}
