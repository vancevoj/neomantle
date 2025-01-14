package slimeknights.mantle.data.loadable.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;

/** Loadable for an integer array */
public record IntArrayLoadable(Loadable<Integer> base, int minSize, int maxSize) implements ArrayLoadable.SizeRange<int[]> {
  @Override
  public int getLength(int[] array) {
    return array.length;
  }

  @Override
  public int[] convertCompact(JsonElement element, String key, TypedMap context) {
    return new int[] { base.convert(element, key, context) };
  }

  @Override
  public int[] convertArray(JsonArray array, String key, TypedMap context) {
    int[] result = new int[array.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = base.convert(array.get(i), key + '[' + i + ']', context);
    }
    return result;
  }

  @Override
  public JsonElement serializeFirst(int[] object) {
    return base.serialize(object[0]);
  }

  @Override
  public void serializeAll(JsonArray array, int[] object) {
    for (int element : object) {
      array.add(base.serialize(element));
    }
  }

  @Override
  public int[] decode(FriendlyByteBuf buffer, TypedMap context) {
    int max = buffer.readVarInt();
    int[] array = new int[max];
    for (int i = 0; i < max; i++) {
      array[i] = base.decode(buffer, context);
    }
    return array;
  }

  @Override
  public void encode(FriendlyByteBuf buffer, int[] array) {
    buffer.writeVarInt(array.length);
    for (int element : array) {
      base.encode(buffer, element);
    }
  }
}
