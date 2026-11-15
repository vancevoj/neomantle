package slimeknights.mantle.recipe.data;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Common logic to create a recipe builder class
 * @param <T>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class AbstractRecipeBuilder<T extends AbstractRecipeBuilder<T>> {
  /** Advancement builder for this class */
  protected final Advancement.Builder advancementBuilder = Advancement.Builder.advancement();
  /** Tracks whether any criterion has been added, since {@link Advancement.Builder} no longer exposes its criteria map */
  private boolean hasCriteria = false;
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
    this.advancementBuilder.addCriterion(name, criterion);
    this.hasCriteria = true;
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

  /**
   * Base logic for advancement building
   * @param id      Recipe ID
   * @param folder  Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement
   */
  private AdvancementHolder buildAdvancementInternal(Identifier id, String folder) {
    this.advancementBuilder
        .parent(Identifier.withDefaultNamespace("recipes/root"))
        .rewards(AdvancementRewards.Builder.recipe(id))
        .requirements(AdvancementRequirements.Strategy.OR)
        .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id));
    Identifier advancementId = Identifier.fromNamespaceAndPath(id.getNamespace(), "recipes/" + folder + "/" + id.getPath());
    return this.advancementBuilder.build(advancementId);
  }

  /**
   * Builds and validates the advancement, intended to be called in {@link #save(RecipeOutput, Identifier)}
   * @param id      Recipe ID
   * @param folder  Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement
   */
  protected AdvancementHolder buildAdvancement(Identifier id, String folder) {
    if (!this.hasCriteria) {
      throw new IllegalStateException("No way of obtaining recipe " + id);
    }
    return buildAdvancementInternal(id, folder);
  }

  /**
   * Builds an optional advancement, intended to be called in {@link #save(RecipeOutput, Identifier)}
   * @param id        Recipe ID
   * @param folder    Group folder for saving recipes. Vanilla typically uses item groups, but for mods might as well base on the recipe
   * @return  Built advancement, or null if the advancement was not defined
   */
  @SuppressWarnings("SameParameterValue")  // API
  @Nullable
  protected AdvancementHolder buildOptionalAdvancement(Identifier id, String folder) {
    if (!this.hasCriteria) {
      return null;
    }
    return buildAdvancementInternal(id, folder);
  }
}
