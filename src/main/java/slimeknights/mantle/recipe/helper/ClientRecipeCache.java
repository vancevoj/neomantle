package slimeknights.mantle.recipe.helper;

import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Set;

/**
 * Holds the recipes synced to the client. Since 1.21.2 the client level has no recipe manager, and since NeoForge 21.4 recipes are only synced
 * for the recipe types requested in {@link net.neoforged.neoforge.event.OnDatapackSyncEvent#sendRecipes(RecipeType[])}.
 * <p>
 * This class references no client only classes so it is safe to load on a dedicated server, the cache is filled by {@link ClientRecipeEvents}.
 * Generally you want {@link RecipeHelper#recipes(net.minecraft.world.level.Level)} instead of calling this directly.
 */
public final class ClientRecipeCache {
  /** Recipes received from the server */
  private static volatile RecipeMap recipes = RecipeMap.EMPTY;
  /** Recipe types the server sent */
  private static volatile Set<RecipeType<?>> syncedTypes = Set.of();

  private ClientRecipeCache() {}

  /** Gets the recipes synced from the server, empty on a dedicated server or before joining a world */
  public static RecipeMap get() {
    return recipes;
  }

  /** Gets the recipe types sent from the server */
  public static Set<RecipeType<?>> getSyncedTypes() {
    return syncedTypes;
  }

  /** Checks if the given recipe type was synced from the server */
  public static boolean isSynced(RecipeType<?> type) {
    return syncedTypes.contains(type);
  }

  /**
   * Updates the cached recipes
   * @param recipes  New recipe map
   * @param types    Recipe types contained in the map
   */
  public static void set(RecipeMap recipes, Set<RecipeType<?>> types) {
    ClientRecipeCache.recipes = recipes;
    ClientRecipeCache.syncedTypes = Set.copyOf(types);
  }

  /** Clears the cached recipes, called on logout */
  public static void clear() {
    set(RecipeMap.EMPTY, Set.of());
  }
}
