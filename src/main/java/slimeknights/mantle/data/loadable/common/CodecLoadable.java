package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;

/**
 * Implementation of a loadable using a codec. Note this will be inefficient when reading from and writing to the network.
 * Since 1.21.2 many codecs (tags, holders) require {@link RegistryOps}, so registry access is used where available.
 */
public record CodecLoadable<T>(DynamicOps<Tag> ops, Codec<T> codec) implements Loadable<T> {
  public CodecLoadable(Codec<T> codec) {
    this(NbtOps.INSTANCE, codec);
  }

  @Override
  public T convert(JsonElement element, String key, TypedMap context) {
    try {
      return codec.parse(ContextKey.jsonOps(context), element).getOrThrow(ErrorFactory.JSON_SYNTAX_ERROR::create);
    } catch (IllegalStateException e) {
      // thrown by registries whose tags are not bound yet, happens if no registry access was passed in the context during data loading
      throw new JsonSyntaxException("Failed to parse " + key + ", tags are not available. Pass ContextKey.REGISTRIES in the loadable context", e);
    }
  }

  @Override
  public JsonElement serialize(T object) {
    return codec.encodeStart(JsonHelper.registryOps(), object).getOrThrow(ErrorFactory.RUNTIME::create);
  }

  /** Gets the ops to use for the given buffer, adding registry access if available */
  private DynamicOps<Tag> networkOps(FriendlyByteBuf buffer) {
    if (!(ops instanceof RegistryOps<Tag>) && buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
      return registryBuffer.registryAccess().createSerializationContext(ops);
    }
    return ops;
  }

  @Override
  public T decode(FriendlyByteBuf buffer, TypedMap context) {
    return buffer.readWithCodec(networkOps(buffer), codec, NbtAccounter.unlimitedHeap());
  }

  @Override
  public void encode(FriendlyByteBuf buffer, T object) {
    buffer.writeWithCodec(networkOps(buffer), codec, object);
  }
}
