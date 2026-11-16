package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Supplier;

/**
 * Simple implementation of a recipe serializer with no properties.
 * Since 26.1 {@link RecipeSerializer} is a final record, so this is a factory: register {@link #of(Supplier)}.
 */
public final class SimpleRecipeSerializer {
  private SimpleRecipeSerializer() {}

  /**
   * Creates a serializer for a recipe with no properties
   * @param constructor  Recipe constructor, called for every decode
   * @param <T>  Recipe type
   * @return  Serializer to register
   */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(Supplier<T> constructor) {
    return new RecipeSerializer<>(codec(constructor), streamCodec(constructor));
  }

  /** Creates the map codec for a recipe with no properties */
  public static <T extends Recipe<?>> MapCodec<T> codec(Supplier<T> constructor) {
    return MapCodec.unit(constructor);
  }

  /** Creates the stream codec for a recipe with no properties */
  public static <T extends Recipe<?>> StreamCodec<RegistryFriendlyByteBuf,T> streamCodec(Supplier<T> constructor) {
    // no data to sync; build a fresh instance on decode. Must NOT use StreamCodec.unit(constructor.get()),
    // as that captures a single instance and asserts identity equality on encode, which fails because the
    // map codec (MapCodec.unit(constructor)) decodes a distinct instance per datapack load.
    return StreamCodec.of((buf, value) -> {}, buf -> RecipeHelper.decodeFromNetwork(constructor));
  }
}
