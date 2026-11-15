package slimeknights.mantle.recipe.crafting;

import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.data.RecipeOutputWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Builder for a shaped recipe with fallbacks */
@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fallback")
public class ShapedFallbackRecipeBuilder {
  private final ShapedRecipeBuilder base;
  private final List<Identifier> alternatives = new ArrayList<>();

  /**
   * Adds a single alternative to this recipe. Any matching alternative causes this recipe to fail
   * @param location  Alternative
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternative(Identifier location) {
    this.alternatives.add(location);
    return this;
  }

  /**
   * Adds a list of alternatives to this recipe. Any matching alternative causes this recipe to fail
   * @param locations  Alternative list
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternatives(Collection<Identifier> locations) {
    this.alternatives.addAll(locations);
    return this;
  }

  /**
   * Builds the recipe using the output as the name
   * @param output  Recipe output
   */
  public void build(RecipeOutput output) {
    ResourceKey<Recipe<?>> key = base.defaultId();
    if (key == null) {
      throw new IllegalStateException("Recipe has no name to derive an id from, build it with an explicit id");
    }
    // resolve the key against the original output so the default mod ID of a wrapper output applies
    base.save(new Output(output, List.copyOf(alternatives)), RecipeOutputWrapper.modifyKey(output, key));
  }

  /**
   * Builds the recipe using the given ID
   * @param output  Recipe output
   * @param id      Recipe ID
   */
  public void build(RecipeOutput output, Identifier id) {
    base.save(new Output(output, List.copyOf(alternatives)), ResourceKey.create(Registries.RECIPE, id));
  }

  /** Recipe output wrapper that re-emits the built shaped recipe wrapped in a {@link ShapedFallbackRecipe} */
  private record Output(RecipeOutput parent, List<Identifier> alternatives) implements RecipeOutput {
    @Override
    public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      ShapedFallbackRecipe fallback = new ShapedFallbackRecipe((ShapedRecipe) recipe, alternatives);
      parent.accept(id, fallback, advancement, conditions);
    }

    @Override
    public Advancement.Builder advancement() {
      return parent.advancement();
    }

    @Override
    public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
      return parent.lookup(key);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
      return parent.listContextElements(key);
    }

    @Override
    public <S> Optional<HolderLookup<S>> holderLookup(ResourceKey<? extends Registry<? extends S>> registry) {
      return parent.holderLookup(registry);
    }
  }
}
