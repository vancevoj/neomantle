package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.recipe.ingredient.EmptyIngredient;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;

/**
 * Loadable for ingredients, handling NeoForge ingredients.
 * Since 1.21.2 vanilla ingredients can no longer be empty, so {@link #ALLOW_EMPTY} represents empty using {@link EmptyIngredient#VANILLA}, read from and written to JSON as an empty array.
 * Tags are resolved through the registry ops in the context, see {@link ContextKey#jsonOps(TypedMap)}.
 */
public enum IngredientLoadable implements Loadable<Ingredient> {
  ALLOW_EMPTY,
  DISALLOW_EMPTY;

  /** Checks if the given ingredient is the empty ingredient */
  public static boolean isEmpty(Ingredient ingredient) {
    return ingredient == EmptyIngredient.VANILLA || ingredient.getCustomIngredient() instanceof EmptyIngredient;
  }

  @Override
  public Ingredient convert(JsonElement element, String key, TypedMap context) {
    // empty ingredients were serialized as an empty array before 1.21.2
    if (element.isJsonNull() || (element.isJsonArray() && element.getAsJsonArray().isEmpty())) {
      if (this == ALLOW_EMPTY) {
        return EmptyIngredient.VANILLA;
      }
      throw new JsonSyntaxException("Ingredient at " + key + " cannot be empty");
    }
    Ingredient ingredient;
    try {
      ingredient = Ingredient.CODEC.parse(ContextKey.jsonOps(context), element)
                                   .getOrThrow(error -> new JsonSyntaxException("Failed to parse ingredient at " + key + ": " + error));
    } catch (IllegalStateException e) {
      // thrown by registries whose tags are not bound yet, happens if no registry access was passed in the context during data loading
      throw new JsonSyntaxException("Failed to parse ingredient at " + key + ", tags are not available. Pass ContextKey.REGISTRIES in the loadable context", e);
    }
    if (this == DISALLOW_EMPTY && isEmpty(ingredient)) {
      throw new JsonSyntaxException("Ingredient at " + key + " cannot be empty");
    }
    return ingredient;
  }

  @Override
  public JsonElement serialize(Ingredient object) {
    if (isEmpty(object)) {
      if (this == DISALLOW_EMPTY) {
        throw new IllegalArgumentException("Ingredient cannot be empty");
      }
      return new JsonArray();
    }
    // registry ops are required to write tags as tags
    return Ingredient.CODEC.encodeStart(JsonHelper.registryOps(), object).getOrThrow(JsonSyntaxException::new);
  }

  @Override
  public Ingredient decode(FriendlyByteBuf buffer, TypedMap context) {
    return Ingredient.CONTENTS_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer);
  }

  @Override
  public void encode(FriendlyByteBuf buffer, Ingredient object) {
    Ingredient.CONTENTS_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, object);
  }
}
