package slimeknights.mantle.recipe.helper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.recipe.IMultiRecipe;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helpers used in creation of recipes
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecipeHelper {
  /** Comparator sorting recipe holders by ID, as {@link ResourceKey} is not comparable */
  public static final Comparator<RecipeHolder<?>> ID_COMPARATOR = Comparator.comparing(holder -> holder.id().identifier());

  /* Network decode tracking */

  /** Set while the current thread reads recipes from a packet, see {@link #isNetworkDecode()} */
  private static final ThreadLocal<Boolean> NETWORK_DECODE = ThreadLocal.withInitial(() -> false);

  /**
   * Checks if the current thread is reading a recipe from a packet.
   * Since 1.20.5 the in memory connection used by singleplayer (and the LAN host) encodes every packet, so the integrated client
   * decodes its own copy of each recipe the server already parsed. Recipes that register themselves into static caches from
   * their constructor can check this (along with a running integrated server) to avoid registering the client copy a second time.
   * @return  True if a recipe is being decoded from the network on this thread
   */
  public static boolean isNetworkDecode() {
    return NETWORK_DECODE.get();
  }

  /**
   * Runs the given recipe decode with {@link #isNetworkDecode()} returning true
   * @param decoder  Logic reading the recipe from the packet
   * @param <T>  Recipe type
   * @return  Decoded recipe
   */
  public static <T> T decodeFromNetwork(Supplier<T> decoder) {
    boolean previous = NETWORK_DECODE.get();
    NETWORK_DECODE.set(true);
    try {
      return decoder.get();
    } finally {
      NETWORK_DECODE.set(previous);
    }
  }


  /* Recipe lookup */

  /**
   * Gets the recipes available on the given side.
   * On the server, this is the full recipe map from the {@link RecipeManager}.
   * On the client, this is the recipes synced by the server, which only contains recipe types requested through {@link OnDatapackSyncEvent#sendRecipes(RecipeType[])}.
   * @param level  Level instance, if null returns an empty map
   * @return  Recipe map for the given side
   */
  public static RecipeMap recipes(@Nullable Level level) {
    if (level == null) {
      return RecipeMap.EMPTY;
    }
    if (level.recipeAccess() instanceof RecipeManager manager) {
      return manager.recipeMap();
    }
    if (level.isClientSide()) {
      return ClientRecipeCache.get();
    }
    return RecipeMap.EMPTY;
  }

  /**
   * Gets the first recipe matching the given input. Uses the recipe manager lookup on the server and the synced recipes on the client.
   * @param level  Level instance
   * @param type   Recipe type
   * @param input  Recipe input
   * @return  Matching recipe, or empty if none matches
   */
  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(@Nullable Level level, RecipeType<T> type, I input) {
    if (level == null) {
      return Optional.empty();
    }
    if (level.recipeAccess() instanceof RecipeManager manager) {
      return manager.getRecipeFor(type, input, level);
    }
    return recipes(level).getRecipesFor(type, input, level).findFirst();
  }

  /**
   * Gets the first recipe matching the given input, checking the hint first.
   * @param level  Level instance
   * @param type   Recipe type
   * @param input  Recipe input
   * @param hint   Recipe to check first, typically the last recipe matched
   * @return  Matching recipe, or empty if none matches
   */
  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(@Nullable Level level, RecipeType<T> type, I input, @Nullable RecipeHolder<T> hint) {
    if (level != null && hint != null && hint.value().matches(input, level)) {
      return Optional.of(hint);
    }
    return getRecipeFor(level, type, input);
  }

  /** Creates a recipe key for the given ID */
  public static ResourceKey<Recipe<?>> key(Identifier id) {
    return ResourceKey.create(Registries.RECIPE, id);
  }

  /**
   * Gets the registry access for the current side, for use where vanilla no longer passes it (such as {@link Recipe#assemble(RecipeInput)}).
   * Prefers the server registries as they are complete, falls back to the client level registries, then to the built-in registries.
   */
  public static HolderLookup.Provider registryAccess() {
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server != null) {
      return server.registryAccess();
    }
    RegistryAccess client = SafeClientAccess.getRegistryAccess();
    if (client != null) {
      return client;
    }
    return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
  }


  /* Client sync */

  /**
   * Requests the vanilla recipe types used by Mantle on the client, namely books and the JEI multi recipe expansion.
   * Since 1.21.2 recipes are not synced unless requested. Register on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}.
   */
  public static void sendRecipesToClient(OnDatapackSyncEvent event) {
    event.sendRecipes(RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING, RecipeType.STONECUTTING, RecipeType.SMITHING);
  }


  /* Recipe map utils */

  /**
   * Gets a recipe of a specific class type by name from the recipe map
   * @param recipes  Recipe map
   * @param name     Recipe name
   * @param clazz    Output class
   * @param <C>      Return type
   * @return  Optional of the recipe, or empty if the recipe is missing
   */
  public static <C extends Recipe<?>> Optional<C> getRecipe(RecipeMap recipes, ResourceKey<Recipe<?>> name, Class<C> clazz) {
    RecipeHolder<?> holder = recipes.byKey(name);
    if (holder != null && clazz.isInstance(holder.value())) {
      return Optional.of(clazz.cast(holder.value()));
    }
    return Optional.empty();
  }

  /** Gets a recipe of a specific class type by name from the recipe map, see {@link #getRecipe(RecipeMap, ResourceKey, Class)} */
  public static <C extends Recipe<?>> Optional<C> getRecipe(RecipeMap recipes, Identifier name, Class<C> clazz) {
    return getRecipe(recipes, key(name), clazz);
  }

  /** Gets a recipe of a specific class type by name from the recipe manager, see {@link #getRecipe(RecipeMap, ResourceKey, Class)} */
  public static <C extends Recipe<?>> Optional<C> getRecipe(RecipeManager manager, Identifier name, Class<C> clazz) {
    return getRecipe(manager.recipeMap(), name, clazz);
  }

  /** Gets a recipe of a specific class type by name from the level's recipes, see {@link #getRecipe(RecipeMap, ResourceKey, Class)} and {@link #recipes(Level)} */
  public static <C extends Recipe<?>> Optional<C> getRecipe(@Nullable Level level, Identifier name, Class<C> clazz) {
    return getRecipe(recipes(level), name, clazz);
  }

  /**
   * Gets a list of all recipes from the recipe map, safely casting to the specified type. Multi Recipes are kept as a single recipe instance
   * @param recipes  Recipe map
   * @param type     Recipe type
   * @param clazz    Preferred recipe class type
   * @param <T>  Recipe class
   * @param <C>  Return type
   * @return  List of recipes from the manager
   */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getRecipes(RecipeMap recipes, RecipeType<T> type, Class<C> clazz) {
    return recipes.byType(type).stream()
                  .map(RecipeHolder::value)
                  .filter(clazz::isInstance)
                  .map(clazz::cast)
                  .collect(Collectors.toList());
  }

  /** Gets a list of all recipes from the recipe manager, see {@link #getRecipes(RecipeMap, RecipeType, Class)} */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getRecipes(RecipeManager manager, RecipeType<T> type, Class<C> clazz) {
    return getRecipes(manager.recipeMap(), type, clazz);
  }

  /** Gets a list of all recipes from the level's recipes, see {@link #getRecipes(RecipeMap, RecipeType, Class)} and {@link #recipes(Level)} */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getRecipes(@Nullable Level level, RecipeType<T> type, Class<C> clazz) {
    return getRecipes(recipes(level), type, clazz);
  }

  /**
   * Gets a list of recipes for display in a UI list, such as UI buttons. Will be sorted to keep the order the same on both sides, and filtered based on the given predicate and class
   * @param recipes  Recipe map
   * @param type     Recipe type
   * @param clazz    Preferred recipe class type
   * @param filter   Filter for which recipes to add to the list
   * @param <T>  Recipe class
   * @param <C>  Return type
   * @return  Recipe list
   */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getUIRecipes(RecipeMap recipes, RecipeType<T> type, Class<C> clazz, Predicate<? super C> filter) {
    return recipes.byType(type).stream()
                  .sorted(ID_COMPARATOR)
                  .map(RecipeHolder::value)
                  .filter(clazz::isInstance)
                  .map(clazz::cast)
                  .filter(filter)
                  .collect(Collectors.toList());
  }

  /** Gets a list of recipes for display in a UI list from the recipe manager, see {@link #getUIRecipes(RecipeMap, RecipeType, Class, Predicate)} */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getUIRecipes(RecipeManager manager, RecipeType<T> type, Class<C> clazz, Predicate<? super C> filter) {
    return getUIRecipes(manager.recipeMap(), type, clazz, filter);
  }

  /** Gets a list of recipes for display in a UI list from the level's recipes, see {@link #getUIRecipes(RecipeMap, RecipeType, Class, Predicate)} and {@link #recipes(Level)} */
  public static <I extends RecipeInput, T extends Recipe<I>, C extends T> List<C> getUIRecipes(@Nullable Level level, RecipeType<T> type, Class<C> clazz, Predicate<? super C> filter) {
    return getUIRecipes(recipes(level), type, clazz, filter);
  }

  /**
   * Gets a list of all recipes from the manager, expanding multi recipes. Intended for use in recipe display such as JEI
   * @param <C>  Return type
   * @param access   Registry access instance
   * @param recipes  Stream of recipe holders
   * @param clazz    Preferred recipe class type
   * @return  List of flattened recipes from the manager
   */
  public static <C> List<C> getJEIRecipes(RegistryAccess access, Stream<? extends RecipeHolder<?>> recipes, Class<C> clazz) {
    return recipes
        .sorted((h1, h2) -> {
          // if one is multi, and the other not, the multi recipe is larger
          boolean m1 = h1.value() instanceof IMultiRecipe<?>;
          boolean m2 = h2.value() instanceof IMultiRecipe<?>;
          if (m1 && !m2) return 1;
          if (!m1 && m2) return -1;
          // fall back to recipe ID
          return h1.id().identifier().compareTo(h2.id().identifier());
        })
        .flatMap((holder) -> {
          // if its a multi recipe, extract child recipes and stream those
          Recipe<?> recipe = holder.value();
          if (recipe instanceof IMultiRecipe<?>) {
            // multi recipes often iterate an external registry to list their contents; a mod registering a broken
            // object there throws, and JEI discards the whole plugin on an exception, so guard and skip just this one
            try {
              return ((IMultiRecipe<?>)recipe).getRecipes(access).stream();
            } catch (Exception e) {
              Mantle.logger.error("Failed to fetch JEI recipes for multi recipe {} ({})", holder.id(), recipe, e);
              return Stream.<Recipe<?>>empty();
            }
          }
          return Stream.of(recipe);
        })
        .filter(clazz::isInstance)
        .map(clazz::cast)
        .collect(Collectors.toList());
  }

  /**
   * Gets a list of all recipes from the recipe map, expanding multi recipes. Intended for use in recipe display such as JEI
   * @param <C>  Return type
   * @param access   Registry access instance
   * @param recipes  Recipe map, on the client typically {@link #recipes(Level)}
   * @param type     Recipe type
   * @param clazz    Preferred recipe class type
   * @return  List of flattened recipes from the manager
   */
  public static <I extends RecipeInput, T extends Recipe<I>, C> List<C> getJEIRecipes(RegistryAccess access, RecipeMap recipes, RecipeType<T> type, Class<C> clazz) {
    return getJEIRecipes(access, recipes.byType(type).stream(), clazz);
  }

  /** Gets a list of all recipes from the recipe manager, expanding multi recipes, see {@link #getJEIRecipes(RegistryAccess, RecipeMap, RecipeType, Class)} */
  public static <I extends RecipeInput, T extends Recipe<I>, C> List<C> getJEIRecipes(RegistryAccess access, RecipeManager manager, RecipeType<T> type, Class<C> clazz) {
    return getJEIRecipes(access, manager.recipeMap(), type, clazz);
  }
}
