package slimeknights.mantle.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.helper.RecipeHelper;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This interface is intended to be used on dynamic recipes to return a full list of valid recipes.
 * For vanilla categories, in order to use this interface the recipe must return true for {@link net.minecraft.world.item.crafting.Recipe#isSpecial()} and not match any registered extension to make JEI ignore it.
 * @param <T>  Recipe type for the return
 */
public interface IMultiRecipe<T> {
  /**
   * Gets a list of recipes for display in JEI
   * @return  List of recipes
   * @param access  Registry access instance
   */
  List<T> getRecipes(RegistryAccess access);


  /**
   * Gets a list of all expanded multi-recipes for the given recipe type. Used to expand multi recipes in vanilla categories.
   * For custom categories, usually it's better to call {@link RecipeHelper#getJEIRecipes(RegistryAccess, RecipeMap, RecipeType, Class)} as that includes regular recipes too.
   * <p>
   * Vanilla JEI categories display {@link RecipeHolder}, so each child recipe is wrapped in a holder whose ID is the parent ID with the child index appended.
   * On the client, pass {@link RecipeHelper#recipes(net.minecraft.world.level.Level)}, which only contains synced recipe types.
   * @see RecipeHelper#getJEIRecipes(RegistryAccess, RecipeMap, RecipeType, Class)
   */
  @SuppressWarnings("SameParameterValue") // might want it later for other recipe types
  static <I extends RecipeInput, T extends Recipe<I>, C extends Recipe<?>> Stream<RecipeHolder<C>> getMultiRecipes(RegistryAccess access, RecipeMap recipes, RecipeType<T> type, Class<C> clazz) {
    return recipes.byType(type).stream().filter(holder -> holder.value().isSpecial()).flatMap(holder -> {
      if (holder.value() instanceof IMultiRecipe<?> multi) {
        // multi recipes often iterate an external registry to list their contents; a mod registering a broken
        // object there throws, and JEI discards the whole plugin on an exception, so guard and skip just this one
        List<?> recipeList;
        try {
          recipeList = multi.getRecipes(access);
        } catch (Exception e) {
          Mantle.logger.error("Failed to fetch JEI recipes for multi recipe {} ({})", holder.id(), multi, e);
          return Stream.<RecipeHolder<C>>empty();
        }
        return IntStream.range(0, recipeList.size())
          .filter(i -> clazz.isInstance(recipeList.get(i)))
          .mapToObj(i -> new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, holder.id().identifier().withSuffix("/" + i)), clazz.cast(recipeList.get(i))));
      }
      return Stream.<RecipeHolder<C>>empty();
    });
  }

  /** Gets a list of all expanded multi-recipes from the recipe manager, see {@link #getMultiRecipes(RegistryAccess, RecipeMap, RecipeType, Class)} */
  static <I extends RecipeInput, T extends Recipe<I>, C extends Recipe<?>> Stream<RecipeHolder<C>> getMultiRecipes(RegistryAccess access, RecipeManager manager, RecipeType<T> type, Class<C> clazz) {
    return getMultiRecipes(access, manager.recipeMap(), type, clazz);
  }
}
