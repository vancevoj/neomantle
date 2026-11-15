package slimeknights.mantle.recipe.helper;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import slimeknights.mantle.client.SafeClientAccess;

import java.util.List;
import java.util.Optional;

/**
 * Helpers for working with {@link Ingredient}, which since 1.21.2 is backed by a {@link HolderSet} and no longer exposes item stacks.
 */
public final class IngredientHelper {
  private IngredientHelper() {}

  /**
   * Gets a named holder set for the given tag in a built-in registry. Unlike {@link Registry#getOrThrow(TagKey)}, this works before tags are bound
   * and for tags that do not exist yet. Tests against the returned set work at any time, but listing its contents requires the tag to be loaded.
   * @param registry  Built-in registry
   * @param tag       Tag key
   * @param <T>  Registry type
   * @return  Holder set for the tag
   */
  public static <T> HolderSet<T> namedSet(Registry<T> registry, TagKey<T> tag) {
    try {
      Optional<HolderSet.Named<T>> named = registry.get(tag);
      if (named.isPresent()) {
        return named.get();
      }
    } catch (IllegalStateException e) {
      // tags are unbound while data packs are loading, fall through to the unbound set
    }
    return HolderSet.emptyNamed(registry, tag);
  }

  /**
   * Creates an ingredient matching the given item tag. Since 1.21.2 there is no {@code Ingredient.of(TagKey)}.
   * In datagen, prefer {@link #tag(HolderGetter, TagKey)} with the lookup from the recipe output.
   * @param tag  Item tag
   * @return  Ingredient matching the tag
   */
  public static Ingredient tag(TagKey<Item> tag) {
    return Ingredient.of(namedSet(BuiltInRegistries.ITEM, tag));
  }

  /**
   * Creates an ingredient matching the given item tag from a holder lookup, such as {@code recipeOutput.lookup(Registries.ITEM)} in datagen.
   * @param items  Item lookup
   * @param tag    Item tag
   * @return  Ingredient matching the tag
   */
  public static Ingredient tag(HolderGetter<Item> items, TagKey<Item> tag) {
    return Ingredient.of(items.getOrThrow(tag));
  }

  /**
   * Gets the display stacks for the given ingredient, replacing {@code Ingredient#getItems()} from before 1.21.2.
   * Uses the ingredient display so custom ingredients can show components (such as potions).
   * @param ingredient  Ingredient to display
   * @return  List of display stacks, empty if the ingredient cannot be resolved (for instance, unbound tags)
   */
  public static List<ItemStack> getStacks(Ingredient ingredient) {
    try {
      return ingredient.display().resolveForStacks(displayContext());
    } catch (RuntimeException e) {
      // unbound tags throw when dereferenced, treat them as empty for display
      return List.of();
    }
  }

  /** Gets the context for resolving slot displays on the current side */
  public static ContextMap displayContext() {
    RegistryAccess registries;
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server != null) {
      registries = server.registryAccess();
    } else {
      registries = SafeClientAccess.getRegistryAccess();
    }
    if (registries == null) {
      return ContextMap.EMPTY;
    }
    return ContextMap.builder().set(SlotDisplayContext.REGISTRIES, registries).buildAndValidate(SlotDisplayContext.CONTEXT);
  }
}
