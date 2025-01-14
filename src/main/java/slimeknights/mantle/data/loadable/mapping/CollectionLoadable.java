package slimeknights.mantle.data.loadable.mapping;

import com.google.common.collect.ImmutableCollection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.Collection;

/** Shared base class for a loadable of a collection of elements */
@SuppressWarnings("unused") // API
@RequiredArgsConstructor
public abstract class CollectionLoadable<T,C extends Collection<T>,B extends ImmutableCollection.Builder<T>> implements ArrayLoadable<C> {
  /** Loadable for an object */
  private final Loadable<T> base;
  /** Minimum list size allowed */
  private final int minSize;

  /** Creates a builder for the collection */
  protected abstract B makeBuilder();

  /** Builds the final collection */
  protected abstract C build(B builder);

  @Override
  public void checkSize(String key, int size, ErrorFactory error) {
    int minSize = this.minSize;
    if (minSize == COMPACT) {
      minSize = 1;
    }
    if (size < minSize) {
      throw error.create(key + " must have at least " + minSize + " elements");
    }
  }

  @Override
  public boolean allowCompact() {
    return minSize < 0;
  }

  @Override
  public int getLength(C array) {
    return array.size();
  }

  @Override
  public C convertCompact(JsonElement element, String key, TypedMap context) {
    B builder = makeBuilder();
    builder.add(base.convert(element, key, context));
    return build(builder);
  }

  @Override
  public C convertArray(JsonArray array, String key, TypedMap context) {
    B builder = makeBuilder();
    for (int i = 0; i < array.size(); i++) {
      builder.add(base.convert(array.get(i), key + '[' + i + ']', context));
    }
    return build(builder);
  }

  @Override
  public JsonElement serializeFirst(C collection) {
    return base.serialize(collection.iterator().next());
  }

  @Override
  public void serializeAll(JsonArray array, C collection) {
    for (T element : collection) {
      array.add(base.serialize(element));
    }
  }

  @Override
  public C decode(FriendlyByteBuf buffer, TypedMap context) {
    B builder = makeBuilder();
    int max = buffer.readVarInt();
    for (int i = 0; i < max; i++) {
      builder.add(base.decode(buffer, context));
    }
    return build(builder);
  }

  @Override
  public void encode(FriendlyByteBuf buffer, C collection) {
    buffer.writeVarInt(collection.size());
    for (T element : collection) {
      base.encode(buffer, element);
    }
  }
}
