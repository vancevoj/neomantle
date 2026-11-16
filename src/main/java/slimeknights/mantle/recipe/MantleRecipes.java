package slimeknights.mantle.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.cooking.BlastingResultRecipe;
import slimeknights.mantle.recipe.cooking.CampfireResultRecipe;
import slimeknights.mantle.recipe.cooking.SmeltingResultRecipe;
import slimeknights.mantle.recipe.cooking.SmokingResultRecipe;
import slimeknights.mantle.recipe.crafting.ShapedFallbackRecipe;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;

/** Handles any custom recipes added by Mantle */
public class MantleRecipes {
  private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mantle.modId);
  private static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, Mantle.modId);

  private MantleRecipes() {}

  /** Registers this to the bus */
  public static void init(IEventBus bus) {
    RECIPES.register(bus);
    RECIPE_BOOK_CATEGORIES.register(bus);
  }

  // crafting
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapedFallbackRecipe>> CRAFTING_SHAPED_FALLBACK = RECIPES.register("crafting_shaped_fallback", () -> new ShapedFallbackRecipe.Serializer().toSerializer());
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapedRetexturedRecipe>> CRAFTING_SHAPED_RETEXTURED = RECIPES.register("crafting_shaped_retextured", () -> new ShapedRetexturedRecipe.Serializer().toSerializer());
  // cooking
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmeltingResultRecipe>> SMELTING = RECIPES.register("smelting", () -> LoadableRecipeSerializer.of(SmeltingResultRecipe.LOADABLE));
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BlastingResultRecipe>> BLASTING = RECIPES.register("blasting", () -> LoadableRecipeSerializer.of(BlastingResultRecipe.LOADABLE));
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmokingResultRecipe>> SMOKING = RECIPES.register("smoking", () -> LoadableRecipeSerializer.of(SmokingResultRecipe.LOADABLE));
  public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CampfireResultRecipe>> CAMPFIRE = RECIPES.register("campfire", () -> LoadableRecipeSerializer.of(CampfireResultRecipe.LOADABLE));

  // recipe book
  /** Recipe book category for recipes that are never displayed in the recipe book, used by default by {@link ICommonRecipe#recipeBookCategory()} */
  public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> UNCATEGORIZED = RECIPE_BOOK_CATEGORIES.register("uncategorized", RecipeBookCategory::new);
}
