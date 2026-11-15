package slimeknights.mantle.recipe.helper;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Type aware recipe serializer, for the sake of recipes that serializers to swap out the type.
 * <p>
 * Since 26.1 {@link RecipeSerializer} is a final record, so this no longer extends it. Instead it is passed to recipes through
 * {@link LoadableRecipeSerializer#TYPED_SERIALIZER} and links the recipe type to the registered serializer record.
 * Recipes should return {@link #getSerializer()} from {@link Recipe#getSerializer()}.
 */
public interface TypeAwareRecipeSerializer<T extends Recipe<?>> {
  /** Gets the type of this recipe */
  RecipeType<?> getType();

  /** Gets the registered serializer for this recipe */
  RecipeSerializer<T> getSerializer();
}
