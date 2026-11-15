package slimeknights.mantle.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import slimeknights.mantle.recipe.helper.RecipeHelper;

/**
 * Extension of {@link Recipe} to set some methods that always set.
 * <p>
 * Provides defaults for the methods vanilla made abstract since 1.21.2, suitable for machine recipes that are not displayed in the recipe book:
 * not placeable, no notification, a Mantle recipe book category, and a group from {@link #getGroup()}.
 * {@link #getResultItem(HolderLookup.Provider)} is no longer part of {@link Recipe}, it is kept here as Mantle API for recipe viewers and books.
 * @param <C>  Inventory type
 */
public interface ICommonRecipe<C extends RecipeInput> extends Recipe<C> {
  /**
   * Gets the result of this recipe for display. Was part of {@link Recipe} before 1.21.2.
   * @param access  Registry access
   * @return  Display result, should not be modified
   */
  default ItemStack getResultItem(HolderLookup.Provider access) {
    return ItemStack.EMPTY;
  }

  /**
   * Assembles the recipe result with access to registries. Before 26.1 this was the vanilla signature, override this or {@link #assemble(RecipeInput)}.
   * @param inv     Recipe input
   * @param access  Registry access
   * @return  Recipe result
   */
  default ItemStack assemble(C inv, HolderLookup.Provider access) {
    return getResultItem(access).copy();
  }

  @Override
  default ItemStack assemble(C inv) {
    return assemble(inv, RecipeHelper.registryAccess());
  }

  /**
   * Gets the group for this recipe, empty for no group. Lombok getters for a {@code group} field automatically implement this.
   * @return  Recipe group
   */
  default String getGroup() {
    return "";
  }

  @Override
  default String group() {
    return getGroup();
  }

  /** Machine recipes do not show a toast on unlock */
  @Override
  default boolean showNotification() {
    return false;
  }

  /** Machine recipes cannot be placed by the recipe book */
  @Override
  default PlacementInfo placementInfo() {
    return PlacementInfo.NOT_PLACEABLE;
  }

  /** Category for the recipe book. As these recipes are not displayed, they use the uncategorized category from Mantle. */
  @Override
  default RecipeBookCategory recipeBookCategory() {
    return MantleRecipes.UNCATEGORIZED.get();
  }

  /**
   * Returns true to hide this recipe from the recipe book. Needed until Forge has proper recipe book support.
   * Also prevents vanilla from warning about the recipe not being placeable.
   * @return  True
   */
  @Override
  default boolean isSpecial() {
    return true;
  }
}
