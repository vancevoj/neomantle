package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.Mantle;

import java.util.function.Supplier;

/**
 * Recipe serializer helper that logs network exceptions before throwing them as otherwise the exceptions may be invisible.
 * <p>
 * Since 26.1 {@link RecipeSerializer} is a final record of a {@link MapCodec} and a {@link StreamCodec}, so this is no longer a serializer itself.
 * Implementors provide {@link #codec()} along with {@link #fromNetworkSafe(RegistryFriendlyByteBuf)} and {@link #toNetworkSafe(RegistryFriendlyByteBuf, Recipe)},
 * then register the result of {@link #toSerializer()}. The stream codec is built automatically and wraps both directions in logging.
 * Serializers built directly from codecs can use {@link #wrap(String, StreamCodec)} or {@link #of(String, MapCodec, StreamCodec)} for the same behavior.
 * @param <T>  Recipe class
 */
public interface LoggingRecipeSerializer<T extends Recipe<?>> {
  /** Gets the map codec used to parse this recipe from JSON */
  MapCodec<T> codec();

  /**
   * Read the recipe from the packet
   * @param buffer  Buffer instance
   * @return  Parsed recipe
   * @throws RuntimeException  If any errors happen, the exception will be logged automatically
   */
  T fromNetworkSafe(RegistryFriendlyByteBuf buffer);

  /**
   * Write the recipe to the buffer
   * @param buffer  Buffer instance
   * @param recipe  Recipe instance
   * @throws RuntimeException  If any errors happen, the exception will be logged automatically
   */
  void toNetworkSafe(RegistryFriendlyByteBuf buffer, T recipe);

  /** Reads the recipe from the network, logging any errors before rethrowing them */
  default T fromNetwork(RegistryFriendlyByteBuf buffer) {
    return decode(this.getClass().getSimpleName(), buffer, this::fromNetworkSafe);
  }

  /** Writes the recipe to the network, logging any errors before rethrowing them */
  default void toNetwork(RegistryFriendlyByteBuf buffer, T recipe) {
    encode(this.getClass().getSimpleName(), buffer, recipe, this::toNetworkSafe);
  }

  /** Gets the stream codec for this serializer, wrapping both directions in logging */
  default StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    return StreamCodec.of(this::toNetwork, this::fromNetwork);
  }

  /**
   * Creates the vanilla serializer record for registration. Recipes must return the registered instance from {@link Recipe#getSerializer()},
   * as recipe dispatch looks the serializer up in the registry, so call this once in the registration supplier.
   */
  default RecipeSerializer<T> toSerializer() {
    return new RecipeSerializer<>(codec(), streamCodec());
  }


  /* Static helpers */

  /** Shared logic to decode a recipe from the network, logging and wrapping any exception */
  private static <T extends Recipe<?>> T decode(String name, RegistryFriendlyByteBuf buffer, StreamDecoder<? super RegistryFriendlyByteBuf,T> decoder) {
    try {
      return RecipeHelper.decodeFromNetwork(() -> decoder.decode(buffer));
    } catch (RuntimeException e) {
      // wrap in a decoder exception so the client disconnect screen names the serializer instead of a bare error
      String error = name + ": Error reading recipe from packet";
      Mantle.logger.error("{}", error, e);
      throw new DecoderException(error + " - " + e.getMessage(), e);
    }
  }

  /** Shared logic to encode a recipe to the network, logging and wrapping any exception */
  private static <T extends Recipe<?>> void encode(String name, RegistryFriendlyByteBuf buffer, T recipe, StreamEncoder<? super RegistryFriendlyByteBuf,T> encoder) {
    try {
      encoder.encode(buffer, recipe);
    } catch (RuntimeException e) {
      // wrap in an encoder exception so the disconnect screen names the serializer instead of a bare error
      String error = name + ": Error writing recipe of class " + recipe.getClass().getSimpleName() + " and type " + recipe.getType() + " to packet";
      Mantle.logger.error("{}", error, e);
      throw new EncoderException(error + " - " + e.getMessage(), e);
    }
  }

  /**
   * Wraps the given stream codec so any exception during encoding or decoding is logged and rethrown with a message naming the serializer.
   * Replaces the pre 26.1 behavior of implementing this interface on a serializer.
   * @param name   Name for the serializer in error messages, fetched lazily as the registry name is typically not known on creation
   * @param codec  Codec to wrap
   * @param <T>    Recipe type
   * @return  Wrapped codec
   */
  static <T extends Recipe<?>> StreamCodec<RegistryFriendlyByteBuf,T> wrap(Supplier<String> name, StreamCodec<? super RegistryFriendlyByteBuf,T> codec) {
    return StreamCodec.of(
      (buffer, recipe) -> encode(name.get(), buffer, recipe, codec),
      buffer -> decode(name.get(), buffer, codec));
  }

  /** Wraps the given stream codec, see {@link #wrap(Supplier, StreamCodec)} */
  static <T extends Recipe<?>> StreamCodec<RegistryFriendlyByteBuf,T> wrap(String name, StreamCodec<? super RegistryFriendlyByteBuf,T> codec) {
    return wrap(() -> name, codec);
  }

  /**
   * Creates a serializer from the given codecs with logging on the stream codec.
   * @param name         Name for the serializer in error messages
   * @param codec        Map codec for JSON
   * @param streamCodec  Stream codec for networking
   * @param <T>  Recipe type
   * @return  Serializer to register
   */
  static <T extends Recipe<?>> RecipeSerializer<T> of(String name, MapCodec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf,T> streamCodec) {
    return new RecipeSerializer<>(codec, wrap(name, streamCodec));
  }
}
