package slimeknights.mantle.recipe.cooking;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.datafixers.util.Function7;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.mantle.recipe.helper.ItemOutput;

import java.util.function.Consumer;

/** Builder for {@link SmeltingResultRecipe}, {@link BlastingResultRecipe}, {@link SmokingResultRecipe}, and {@link CampfireResultRecipe} */
@SuppressWarnings({"unchecked", "unused"})
@CanIgnoreReturnValue
@RequiredArgsConstructor(access = AccessLevel.PROTECTED, staticName = "builder")
public class CookingRecipeBuilder<T extends CookingRecipeBuilder<T>> extends AbstractRecipeBuilder<T> {
  protected final ItemOutput result;
  protected float experience = 1.0f;
  protected int cookingTime = 200;
  protected Ingredient ingredient = Ingredient.EMPTY;
  protected CookingBookCategory category = CookingBookCategory.MISC;
  protected CookingType type = CookingType.SMELTING;

  /** Creates a new builder instance */
  public static CookingRecipeBuilder<?> builder(ItemLike output, int amount) {
    return builder(ItemOutput.fromItem(output, amount));
  }

  /** Creates a new builder instance */
  public static CookingRecipeBuilder<?> builder(ItemLike output) {
    return builder(output, 1);
  }

  /** Creates a new builder instance for a tag with the given size */
  public static CookingRecipeBuilder<?> builder(TagKey<Item> result, int amount) {
    return builder(ItemOutput.fromTag(result, amount));
  }

  /** Creates a new builder instance for a tag with size of 1 */
  public static CookingRecipeBuilder<?> builder(TagKey<Item> result) {
    return builder(result, 1);
  }


  /**
   * Sets the type of {@link #save(Consumer, ResourceLocation)} for the sake of {@link net.minecraftforge.common.crafting.ConditionalRecipe}.
   * Note you can also just directly use {@link #saveSmelting(Consumer, ResourceLocation)}, {@link #saveBlasting(Consumer, ResourceLocation)},
   * {@link #saveSmoking(Consumer, ResourceLocation)}, and {@link #saveCampfire(Consumer, ResourceLocation)} directly.
   */
  public T type(CookingType type) {
    this.type = type;
    return (T) this;
  }

  /** Sets the input ingredient */
  public T requires(Ingredient ingredient) {
    this.ingredient = ingredient;
    return (T) this;
  }

  /** Sets the input ingredient */
  public T requires(ItemLike item) {
    return requires(Ingredient.of(item));
  }

  /** Sets the input ingredient */
  public T requires(TagKey<Item> tag) {
    return requires(Ingredient.of(tag));
  }


  /** Helper to save a recipe */
  @SuppressWarnings("unchecked")
  private <R extends Recipe<?>> T save(Consumer<FinishedRecipe> consumer, ResourceLocation id, RecordLoadable<R> loadable, Function7<ResourceLocation,String,CookingBookCategory,Ingredient,ItemOutput,Float,Integer,R> constructor, int cookingTime) {
    if (ingredient == Ingredient.EMPTY) {
      throw new IllegalStateException("Ingredient must be set");
    }
    ResourceLocation advancementID = buildOptionalAdvancement(id, "cooking");
    consumer.accept(new LoadableFinishedRecipe<>(constructor.apply(id, group, category, ingredient, result, experience, cookingTime), loadable, advancementID));
    return (T) this;
  }

  /** Saves the smelting recipe */
  public T saveSmelting(Consumer<FinishedRecipe> consumer, ResourceLocation id) {
    return save(consumer, id, SmeltingResultRecipe.LOADABLE, SmeltingResultRecipe::new, cookingTime);
  }

  /** Saves the blasting recipe */
  public T saveBlasting(Consumer<FinishedRecipe> consumer, ResourceLocation id) {
    return save(consumer, id, BlastingResultRecipe.LOADABLE, BlastingResultRecipe::new, cookingTime / 2);
  }

  /** Saves the smoking recipe */
  public T saveSmoking(Consumer<FinishedRecipe> consumer, ResourceLocation id) {
    return save(consumer, id, SmokingResultRecipe.LOADABLE, SmokingResultRecipe::new, cookingTime / 2);
  }

  /** Saves the campfire recipe */
  public T saveCampfire(Consumer<FinishedRecipe> consumer, ResourceLocation id) {
    return save(consumer, id, CampfireResultRecipe.LOADABLE, CampfireResultRecipe::new, cookingTime * 3);
  }

  @Override
  public void save(Consumer<FinishedRecipe> consumer) {
    save(consumer, Loadables.ITEM.getKey(result.get().getItem()));
  }

  @Override
  public void save(Consumer<FinishedRecipe> consumer, ResourceLocation id) {
    switch (type) {
      case SMELTING -> saveSmelting(consumer, id);
      case BLASTING -> saveBlasting(consumer, id);
      case SMOKING -> saveSmoking(consumer, id);
      case CAMPFIRE -> saveCampfire(consumer, id);
    }
  }

  /** Helper to change the cooking type in {@link #save(Consumer, ResourceLocation)} for the sake of {@link net.minecraftforge.common.crafting.ConditionalRecipe} */
  public enum CookingType { SMELTING, BLASTING, SMOKING, CAMPFIRE }
}
