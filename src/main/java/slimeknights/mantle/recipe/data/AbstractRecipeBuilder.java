package slimeknights.mantle.recipe.data;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common logic to create a recipe builder class
 * @param <T>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class AbstractRecipeBuilder<T extends AbstractRecipeBuilder<T>> {
  /** Criteria for the recipe advancement. Since 1.21.2 the advancement is built from the recipe output, so criteria are stored until save. */
  private final Map<String,Criterion<?>> criteria = new LinkedHashMap<>();
  /** Group for this recipe */
  @Nonnull
  protected String group = "";

  /** Gets this builder casted to the given type. */
  @SuppressWarnings("unchecked")
  protected T self() {
    return (T) this;
  }

  /**
   * Adds a criteria to the recipe
   * @param name      Criteria name
   * @param criterion Criterion instance
   * @return  Builder
   */
  public T unlockedBy(String name, Criterion<?> criterion) {
    this.criteria.put(name, criterion);
    return self();
  }

  /**
   * Sets the group for this recipe
   * @param group  Recipe group
   * @return  Builder
   */
  public T group(String group) {
    this.group = group;
    return self();
  }

  /**
   * Sets the group for this recipe
   * @param group  Recipe resource location group
   * @return  Builder
   */
  public T group(Identifier group) {
    // if minecraft, no namepsace. Groups are technically not namespaced so this is for consistency with vanilla
    if ("minecraft".equals(group.getNamespace())) {
      return group(group.getPath());
    }
    return group(group.toString());
  }

  /**
   * Builds the recipe with a default recipe ID, typically based on the output
   * @param output  Recipe output
   */
  public abstract void save(RecipeOutput output);

  /**
   * Builds the recipe
   * @param output  Recipe output
   * @param id      Recipe ID
   */
  public abstract void save(RecipeOutput output, Identifier id);

  /** Creates the recipe key for the given ID, for use with {@link RecipeOutput#accept(ResourceKey, Recipe, AdvancementHolder)} */
  protected static ResourceKey<Recipe<?>> recipeKey(Identifier id) {
    return ResourceKey.create(Registries.RECIPE, id);
  }

  /**
   * Base logic for advancement building
   * @param output  Recipe output, used to fetch the recipe holder and the base advancement
   * @param id      Recipe ID
   * @param folder  Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement
   */
  @SuppressWarnings("removal")  // vanilla still uses the ID parent for recipe advancements
  private AdvancementHolder buildAdvancementInternal(RecipeOutput output, Identifier id, String folder) {
    ResourceKey<Recipe<?>> key = recipeKey(id);
    Advancement.Builder advancement = output.advancement()
      .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
      .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(key)))
      .rewards(AdvancementRewards.Builder.recipe(key))
      .requirements(AdvancementRequirements.Strategy.OR);
    this.criteria.forEach(advancement::addCriterion);
    Identifier advancementId = Identifier.fromNamespaceAndPath(id.getNamespace(), "recipes/" + folder + "/" + id.getPath());
    return advancement.build(advancementId);
  }

  /**
   * Builds and validates the advancement, intended to be called in {@link #save(RecipeOutput, Identifier)}
   * @param output  Recipe output
   * @param id      Recipe ID
   * @param folder  Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement
   */
  protected AdvancementHolder buildAdvancement(RecipeOutput output, Identifier id, String folder) {
    if (this.criteria.isEmpty()) {
      throw new IllegalStateException("No way of obtaining recipe " + id);
    }
    return buildAdvancementInternal(output, id, folder);
  }

  /**
   * Builds an optional advancement, intended to be called in {@link #save(RecipeOutput, Identifier)}
   * @param output    Recipe output
   * @param id        Recipe ID
   * @param folder    Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement, or null if the advancement was not defined
   */
  @SuppressWarnings("SameParameterValue")  // API
  @Nullable
  protected AdvancementHolder buildOptionalAdvancement(RecipeOutput output, Identifier id, String folder) {
    if (this.criteria.isEmpty()) {
      return null;
    }
    return buildAdvancementInternal(output, id, folder);
  }
}
