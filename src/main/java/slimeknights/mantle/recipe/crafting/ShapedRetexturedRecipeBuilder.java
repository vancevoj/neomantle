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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.data.RecipeOutputWrapper;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fromShaped")
public class ShapedRetexturedRecipeBuilder {
  private final ShapedRecipeBuilder parent;
  private Ingredient texture = null;
  /** Texture tag, resolved on build as since 1.21.2 tag ingredients need the item lookup from the recipe output */
  private TagKey<Item> textureTag = null;
  private boolean matchAll = false;

  /**
   * Sets the texture source to the given ingredient
   * @param texture Ingredient to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(Ingredient texture) {
    this.texture = texture;
    this.textureTag = null;
    return this;
  }

  /**
   * Sets the texture source to the given tag
   * @param tag Tag to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(TagKey<Item> tag) {
    this.texture = null;
    this.textureTag = tag;
    return this;
  }

  /**
   * Sets the match first property on the recipe.
   * If set, the recipe uses the first ingredient match for the texture. If unset, all items that match the ingredient must be the same or no texture is applied
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setMatchAll() {
    this.matchAll = true;
    return this;
  }

  /**
   * Builds the recipe with the default name using the given output
   * @param output Recipe output
   */
  public void build(RecipeOutput output) {
    Ingredient texture = getTexture(output);
    ResourceKey<Recipe<?>> key = parent.defaultId();
    if (key == null) {
      throw new IllegalStateException("Recipe has no name to derive an id from, build it with an explicit id");
    }
    // resolve the key against the original output so the default mod ID of a wrapper output applies
    parent.save(new Output(output, texture, matchAll), RecipeOutputWrapper.modifyKey(output, key));
  }

  /**
   * Builds the recipe using the given output
   * @param output   Recipe output
   * @param location Recipe location
   */
  public void build(RecipeOutput output, Identifier location) {
    parent.save(new Output(output, getTexture(output), matchAll), ResourceKey.create(Registries.RECIPE, location));
  }

  /**
   * Ensures this recipe can be built, and gets the texture ingredient
   * @throws IllegalStateException If the recipe cannot be built
   */
  private Ingredient getTexture(RecipeOutput output) {
    if (texture != null) {
      return texture;
    }
    if (textureTag != null) {
      return Ingredient.of(output.lookup(Registries.ITEM).getOrThrow(textureTag));
    }
    throw new IllegalStateException("No texture defined for texture recipe");
  }

  /** Recipe output wrapper that re-emits the built shaped recipe wrapped in a {@link ShapedRetexturedRecipe} */
  private record Output(RecipeOutput wrapped, Ingredient texture, boolean matchAll) implements RecipeOutput {
    @Override
    public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      ShapedRetexturedRecipe retextured = new ShapedRetexturedRecipe((ShapedRecipe) recipe, texture, matchAll);
      wrapped.accept(id, retextured, advancement, conditions);
    }

    @Override
    public Advancement.Builder advancement() {
      return wrapped.advancement();
    }

    @Override
    public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
      return wrapped.lookup(key);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
      return wrapped.listContextElements(key);
    }

    @Override
    public <S> Optional<HolderLookup<S>> holderLookup(ResourceKey<? extends Registry<? extends S>> registry) {
      return wrapped.holderLookup(registry);
    }
  }
}
