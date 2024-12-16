package slimeknights.mantle.data.loadable.mapping;

import com.google.common.collect.ImmutableCollection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import slimeknights.mantle.data.loadable.Loadable;

import java.util.Collection;

/** Shared base class for a loadable of a collection of elements */
@SuppressWarnings("unused") // API
@RequiredArgsConstructor
public abstract class CollectionLoadable<T,C extends Collection<T>,B extends ImmutableCollection.Builder<T>> implements Loadable<C> {
  /** Special size representing compact where empty is allowed */
  public static final int COMPACT_OR_EMPTY = -2;
  /** Special size representing comapct where empty is disallowed */
  public static final int COMPACT = -1;
  /** Loadable for an object */
  private final Loadable<T> base;
  /** Minimum list size allowed */
  private final int minSize;

  /** Creates a builder for the collection */
  protected abstract B makeBuilder();

  /** Builds the final collection */
  protected abstract C build(B builder);

  /** Gets the minimum size for the list */
  private int getMinSize() {
    if (minSize == COMPACT) {
      return 1;
    }
    // all sizes -2 and below are treated same as COMPACT_OR_EMPTY.
    return Math.max(minSize, 0);
  }

  @Override
  public C convert(JsonElement element, String key) {
    if (minSize < 0 && !element.isJsonArray()) {
      B builder = makeBuilder();
      builder.add(base.convert(element, key));
      return build(builder);
    }
    JsonArray array = GsonHelper.convertToJsonArray(element, key);
    if (array.size() < getMinSize()) {
      throw new JsonSyntaxException(key + " must have at least " + getMinSize() + " elements");
    }
    B builder = makeBuilder();
    for (int i = 0; i < array.size(); i++) {
      builder.add(base.convert(array.get(i), key + '[' + i + ']'));
    }
    return build(builder);
  }

  @Override
  public JsonElement serialize(C collection) {
    // if we support compact, serialize compact
    if (minSize < 0 && collection.size() == 1) {
      JsonElement element = base.serialize(collection.iterator().next());
      // only return if its not an array; arrays means a conflict with deserializing
      // there is a small waste of work here in the case of array but you shouldn't be using compact with array serializing elements anyway
      if (!element.isJsonArray()) {
        return element;
      }
    }
    if (collection.size() < getMinSize()) {
      throw new RuntimeException("Collection must have at least " + getMinSize() + " elements");
    }
    JsonArray array = new JsonArray();
    for (T element : collection) {
      array.add(base.serialize(element));
    }
    return array;
  }

  @Override
  public C decode(FriendlyByteBuf buffer) {
    B builder = makeBuilder();
    int max = buffer.readVarInt();
    for (int i = 0; i < max; i++) {
      builder.add(base.decode(buffer));
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
