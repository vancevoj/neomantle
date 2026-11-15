package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.IngredientHelper;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extension of the vanilla ingredient to make stack size checks
 */
@RequiredArgsConstructor(staticName = "of")
public class SizedIngredient implements Predicate<ItemStack> {
  /** Empty sized ingredient wrapper. Matches only the empty stack of size 0 */
  public static final SizedIngredient EMPTY = of(EmptyIngredient.VANILLA, 0);

  public static final RecordLoadable<SizedIngredient> LOADABLE = RecordLoadable.create(
    IngredientLoadable.DISALLOW_EMPTY.tryDirectField("ingredient", SizedIngredient::getIngredient, "amount_needed"),
    IntLoadable.FROM_ONE.defaultField("amount_needed", 1, SizedIngredient::getAmountNeeded),
    SizedIngredient::new);

  /** Ingredient to use in recipe match */
  @Getter
  private final Ingredient ingredient;
  /** Amount of this ingredient needed */
  @Getter
  private final int amountNeeded;

  /** Items matched by the ingredient last time the stacks were computed, used to detect tag reloads */
  private List<Holder<Item>> lastIngredientMatch;
  /** Cached matching stacks from last time it was requested */
  private List<ItemStack> matchingStacks;

  /**
   * Gets a new sized ingredient with a size of 1
   * @param ingredient  Ingredient
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient of(Ingredient ingredient) {
    return of(ingredient, 1);
  }

  /**
   * Gets a new sized ingredient with a size of 1
   * @param amountNeeded  Number that must match of this ingredient
   * @param items         List of items
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromItems(int amountNeeded, ItemLike... items) {
    return of(Ingredient.of(items), amountNeeded);
  }

  /**
   * Gets a new sized ingredient with a size of 1
   * @param items  List of items
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromItems(ItemLike... items) {
    return fromItems(1, items);
  }

  /**
   * Gets a new sized ingredient with a size of 1
   * @param tag           Tag to match
   * @param amountNeeded  Number that must match of this ingredient
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromTag(TagKey<Item> tag, int amountNeeded) {
    return of(IngredientHelper.tag(tag), amountNeeded);
  }

  /**
   * Gets a new sized ingredient for a tag using the given item lookup, preferred in datagen
   * @param items         Item lookup, such as from the recipe output
   * @param tag           Tag to match
   * @param amountNeeded  Number that must match of this ingredient
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromTag(HolderGetter<Item> items, TagKey<Item> tag, int amountNeeded) {
    return of(IngredientHelper.tag(items, tag), amountNeeded);
  }

  /**
   * Gets a new sized ingredient for a tag with a size of 1 using the given item lookup, preferred in datagen
   * @param items  Item lookup, such as from the recipe output
   * @param tag    Tag to match
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromTag(HolderGetter<Item> items, TagKey<Item> tag) {
    return fromTag(items, tag, 1);
  }

  /**
   * Gets a new sized ingredient with a size of 1
   * @param tag  Tag to match
   * @return  Sized ingredient matching any size
   */
  public static SizedIngredient fromTag(TagKey<Item> tag) {
    return fromTag(tag, 1);
  }

  @Override
  public boolean test(ItemStack stack) {
    return stack.getCount() >= amountNeeded && ingredient.test(stack);
  }

  /**
   * Checks if the ingredient has no matching stacks
   * @return  True if the ingredient has no matching stacks
   */
  public boolean isEmpty() {
    return ingredient.isEmpty();
  }

  /**
   * Gets a list of matching stacks for display in JEI
   * @return  List of matching stacks
   */
  public List<ItemStack> getMatchingStacks() {
    List<Holder<Item>> ingredientMatch;
    try {
      ingredientMatch = ingredient.items().toList();
    } catch (RuntimeException e) {
      // tags not yet bound, or a tag set made before binding that is never bound (display stacks look the tag up by key), cannot tell when to recache
      ingredientMatch = null;
    }
    // if we never cached, or the matched items changed since we last cached (tag reload), recache
    if (matchingStacks == null || ingredientMatch == null || !ingredientMatch.equals(lastIngredientMatch)) {
      matchingStacks = IngredientHelper.getStacks(ingredient).stream().map(stack -> {
        if (stack.getCount() != amountNeeded) {
          stack = stack.copy();
          stack.setCount(amountNeeded);
        }
        return stack;
      }).collect(Collectors.toList());
      lastIngredientMatch = ingredientMatch;
    }
    return matchingStacks;
  }

  /** use {@link #LOADABLE} with {@link slimeknights.mantle.data.loadable.Loadable#encode(RegistryFriendlyByteBuf, Object)} */
  @Deprecated(forRemoval = true)
  public void write(RegistryFriendlyByteBuf buffer) {
    LOADABLE.encode(buffer, this);
  }

  /** @deprecated use {@link #LOADABLE} with {@link slimeknights.mantle.data.loadable.Loadable#serialize(Object)} or {@link RecordLoadable#serialize(Object, JsonObject)} */
  @Deprecated(forRemoval = true)
  public JsonObject serialize() {
    JsonObject json = new JsonObject();
    LOADABLE.serialize(this, json);
    return json;
  }

  /** @deprecated use {@link #LOADABLE} with {@link slimeknights.mantle.data.loadable.Loadable#decode(RegistryFriendlyByteBuf)}  */
  @Deprecated(forRemoval = true)
  public static SizedIngredient read(RegistryFriendlyByteBuf buffer) {
    return LOADABLE.decode(buffer);
  }

  /** @deprecated use {@link #LOADABLE} with {@link RecordLoadable#deserialize(JsonObject)} */
  @Deprecated(forRemoval = true)
  public static SizedIngredient deserialize(JsonObject json) {
    return LOADABLE.deserialize(json);
  }
}
