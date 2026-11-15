package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.common.NBTLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import javax.annotation.Nullable;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class representing an item stack output. Supports both direct stacks and tag output, behaving like an ingredient used for output
 */
public abstract class ItemOutput implements Supplier<ItemStack> {
  /* Codecs - just adding these as needed */
  /** Codec for an output that may not be empty with any size */
  public static Codec<ItemOutput> REQUIRED_STACK_CODEC = new LoadableCodec<>(Loadable.REQUIRED_STACK);

  /** Empty instance */
  public static final ItemOutput EMPTY = new OfStack(ItemStack.EMPTY);


  /**
   * Gets the item output of this recipe
   * @return  Item output
   */
  @Override
  public abstract ItemStack get();

  /**
   * Gets a copy of the result stack
   * @return  Item output
   */
  public final ItemStack copy() {
    return get().copy();
  }

  /** Gets the size of the output without resolving the stack */
  public abstract int getCount();

  /** Checks if the contents are empty without resolving the stack */
  public boolean isEmpty() {
    return getCount() <= 0;
  }

  /** Gets the tag for this output. Will be {@code null} if this is not a tag output. */
  @Nullable
  public TagKey<Item> getTag() {
    return null;
  }

  /**
   * Writes this output to JSON
   * @param  writeCount  If true, serializes the count
   * @return  Json element
   */
  public abstract JsonElement serialize(boolean writeCount);

  /**
   * Creates a new output for the given stack
   * @param stack  Stack
   * @return  Output
   */
  public static ItemOutput fromStack(ItemStack stack) {
    if (stack.isEmpty()) {
      return EMPTY;
    }
    return new OfStack(stack);
  }

  /**
   * Creates a new output for the given stack template. Unlike {@link #fromStack(ItemStack)} this is safe to call before
   * item components are bound, such as during datagen, as the stack is only created on first use.
   * @param template  Stack template
   * @return  Output
   */
  public static ItemOutput fromTemplate(ItemStackTemplate template) {
    if (template.components().isEmpty()) {
      return fromItem(template.item().value(), template.count());
    }
    return new OfTemplate(template);
  }

  /**
   * Creates a new output for the given item
   * @param item  Item
   * @param count Stack count
   * @return  Output
   */
  public static ItemOutput fromItem(ItemLike item, int count) {
    return new OfItem(item.asItem(), count);
  }

  /**
   * Creates a new output for the given item
   * @param item  Item
   * @return  Output
   */
  public static ItemOutput fromItem(ItemLike item) {
    return fromItem(item, 1);
  }

  /**
   * Creates a new output for the given tag
   * @param tag   Tag
   * @param count Stack count
   * @param nbt   Stack NBT
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag, int count, @Nullable CompoundTag nbt) {
    return new OfTagPreference(tag, count, nbt);
  }

  /**
   * Creates a new output for the given tag
   * @param tag   Tag
   * @param count Stack count
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag, int count) {
    return fromTag(tag, count, null);
  }

  /**
   * Creates a new output for the given tag
   * @param tag  Tag
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag) {
    return fromTag(tag, 1);
  }

  /**
   * Writes this output to the packet buffer
   * @param buffer  Packet buffer instance
   */
  public void write(RegistryFriendlyByteBuf buffer) {
    ItemStack.STREAM_CODEC.encode(buffer, get());
  }

  /**
   * Reads an item output from the packet buffer
   * @param buffer  Buffer instance
   * @return  Item output
   */
  public static ItemOutput read(RegistryFriendlyByteBuf buffer) {
    return fromStack(ItemStack.STREAM_CODEC.decode(buffer));
  }

  /** Class for an output that is just an item, simplifies NBT for serializing as vanilla forces NBT to be set for tools and forge goes through extra steps when NBT is set */
  @RequiredArgsConstructor
  private static class OfItem extends ItemOutput {
    private final Item item;
    @Getter
    private final int count;
    private ItemStack cachedStack;

    @Override
    public ItemStack get() {
      if (cachedStack == null) {
        cachedStack = new ItemStack(item, count);
      }
      return cachedStack;
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      JsonElement item = Loadables.ITEM.serialize(this.item);
      if (writeCount && count > 1) {
        JsonObject json = new JsonObject();
        json.add("item", item);
        json.addProperty("count", count);
        return json;
      } else {
        return item;
      }
    }
  }

  /** Class for an output that is just a stack */
  @RequiredArgsConstructor
  private static class OfStack extends ItemOutput {
    private final ItemStack stack;

    @Override
    public ItemStack get() {
      return stack;
    }

    @Override
    public int getCount() {
      return stack.getCount();
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      if (writeCount) {
        return ItemStackLoadable.OPTIONAL_STACK_NBT.serialize(stack);
      }
      return ItemStackLoadable.OPTIONAL_ITEM_NBT.serialize(stack);
    }
  }

  /** Class for an output from a stack template, the stack is created lazily as components cannot be used during datagen */
  @RequiredArgsConstructor
  private static class OfTemplate extends ItemOutput {
    private final ItemStackTemplate template;
    private ItemStack cachedStack;

    @Override
    public ItemStack get() {
      if (cachedStack == null) {
        cachedStack = template.create();
      }
      return cachedStack;
    }

    @Override
    public int getCount() {
      return template.count();
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      // same format as the NBT stack loadables
      JsonObject json = new JsonObject();
      json.add("item", Loadables.ITEM.serialize(template.item().value()));
      if (writeCount && template.count() != 1) {
        json.addProperty("count", template.count());
      }
      DataComponentPatch patch = template.components();
      CustomData nbt = patch.keySet().contains(DataComponents.CUSTOM_DATA) ? patch.getPatch(DataComponents.CUSTOM_DATA) : null;
      if (nbt != null && !nbt.isEmpty()) {
        json.add("nbt", NBTLoadable.ALLOW_STRING.serialize(nbt.copyTag()));
      }
      DataComponentPatch components = patch.forget(type -> type == DataComponents.CUSTOM_DATA);
      if (!components.isEmpty()) {
        json.add("components", ItemStackLoadable.serializeComponents(components));
      }
      return json;
    }
  }

  /** Class for an output from a tag preference */
  @RequiredArgsConstructor
  private static class OfTagPreference extends ItemOutput {
    @Getter
    private final TagKey<Item> tag;
    @Getter
    private final int count;
    @Nullable
    private final CompoundTag nbt;
    private ItemStack cachedResult = null;

    @Override
    public ItemStack get() {
      // cache the result from the tag preference to save effort, especially helpful if the tag becomes invalid
      // this object should only exist in recipes so no need to invalidate the cache
      if (cachedResult == null) {
        // if the preference is empty, do not cache it.
        // This should only happen if someone scans recipes before tag are computed in which case we cache the wrong result.
        // We protect against empty tags in our recipes via conditions.
        Optional<Item> preference = TagPreference.getPreference(tag);
        if (preference.isEmpty()) {
          return ItemStack.EMPTY;
        }
        cachedResult = new ItemStack(preference.orElseThrow(), count);
        // TODO(neoport): 1.21 dropped ItemStack#setTag; applying legacy NBT as a DataComponentPatch needs a
        // HolderLookup.Provider not available here. Tag-preference outputs with custom NBT no longer apply that NBT.
      }
      return cachedResult;
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      JsonObject json = new JsonObject();
      if (!writeCount || count > 0) {
        json.addProperty("tag", tag.location().toString());
      }
      if (writeCount) {
        json.addProperty("count", count);
      }
      if (count > 0 && nbt != null) {
        json.add("nbt", NBTLoadable.ALLOW_STRING.serialize(nbt));
      }
      return json;
    }
  }

  /** Loadable logic for an ItemOutput */
  public enum Loadable implements RecordLoadable<ItemOutput> {
    /** Loadable for an output that may be empty with a fixed size of 1 */
    OPTIONAL_ITEM(false, false),
    /** Loadable for an output that may be empty with any size */
    OPTIONAL_STACK(false, true),
    /** Loadable for an output that may not empty with a fixed size of 1 */
    REQUIRED_ITEM(true, false),
    /** Loadable for an output that may not be empty with any size */
    REQUIRED_STACK(true, true);

    private final boolean nonEmpty;
    private final boolean readCount;
    private final RecordLoadable<ItemStack> stack;
    Loadable(boolean nonEmpty, boolean readCount) {
      this.nonEmpty = nonEmpty;
      this.readCount = readCount;
      // figure out the stack serializer to use based on the two parameters
      // we always do NBT, just those that vary
      if (nonEmpty) {
        this.stack = readCount ? ItemStackLoadable.REQUIRED_STACK_NBT : ItemStackLoadable.REQUIRED_ITEM_NBT;
      } else {
        this.stack = readCount ? ItemStackLoadable.OPTIONAL_STACK_NBT : ItemStackLoadable.OPTIONAL_ITEM_NBT;
      }
    }

    @Override
    public ItemOutput deserialize(JsonObject json, TypedMap context) {
      if (json.has("tag")) {
        TagKey<Item> tag = Loadables.ITEM_TAG.getIfPresent(json, "tag", context);
        int count = 1;
        // 0 count field means we load count from JSON
        if (readCount) {
          count = IntLoadable.FROM_ONE.getOrDefault(json, "count", 1, context);
        }
        return fromTag(tag, count, NBTLoadable.ALLOW_STRING.getOrDefault(json, "nbt", null));
      }
      // since 26.1 item components are only bound after data pack loading finishes, so stacks cannot be created while
      // parsing recipes or other data; read the stack as a template and create it on first use instead
      Item item = Loadables.ITEM.getOrDefault(json, "item", Items.AIR, context);
      int count = readCount ? IntLoadable.FROM_ZERO.getOrDefault(json, "count", 1, context) : 1;
      PatchedDataComponentMap components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
      if (json.has("components")) {
        components.applyPatch(ItemStackLoadable.parseComponents(json.get("components"), context));
      }
      CompoundTag nbt = NBTLoadable.ALLOW_STRING.getOrDefault(json, "nbt", null);
      if (nbt != null && !nbt.isEmpty()) {
        components.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
      }
      return fromParsed(item, count, components.asPatch());
    }

    @Override
    public ItemOutput convert(JsonElement element, String key, TypedMap context) {
      // if it's a primitive, its just an item
      if (element.isJsonPrimitive()) {
        return fromParsed(Loadables.ITEM.convert(element, key, context), 1, DataComponentPatch.EMPTY);
      }
      return deserialize(GsonHelper.convertToJsonObject(element, key), context);
    }

    /** Creates an output from parsed stack data, validating emptiness */
    private ItemOutput fromParsed(Item item, int count, DataComponentPatch components) {
      if (item == Items.AIR || count <= 0) {
        if (nonEmpty) {
          throw new JsonSyntaxException("ItemStack cannot be empty");
        }
        return EMPTY;
      }
      return fromTemplate(new ItemStackTemplate(item, count, components));
    }

    @Override
    public void serialize(ItemOutput object, JsonObject json) {
      JsonElement element = serialize(object);
      if (element.isJsonObject()) {
        for (Entry<String,JsonElement> entry : element.getAsJsonObject().entrySet()) {
          json.add(entry.getKey(), entry.getValue());
        }
      } else {
        // if its a primitive, it must be the item field, so add that directly
        json.add("item", element);
      }
    }

    @Override
    public JsonElement serialize(ItemOutput output) {
      if (nonEmpty && output.isEmpty()) {
        throw new IllegalArgumentException("ItemOutput cannot be empty for this recipe");
      }
      return output.serialize(readCount);
    }

    @Override
    public ItemOutput decode(FriendlyByteBuf buffer, TypedMap context) {
      // a present flag precedes the stack: the resolved output may be empty even for a non-empty loadable (e.g. a
      // tag output whose tag is empty in this environment). without this, the strict stack codec throws mid-packet
      // and aborts the whole update_recipes sync, disconnecting the client.
      if (!buffer.readBoolean()) {
        return ItemOutput.EMPTY;
      }
      // recipes sync with registry access, which lets us send all components instead of just the custom data
      if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
        return fromStack(ItemStack.STREAM_CODEC.decode(registryBuffer));
      }
      return fromStack(stack.decode(buffer, context));
    }

    @Override
    public void encode(FriendlyByteBuf buffer, ItemOutput object) {
      ItemStack out = object.get();
      boolean present = !out.isEmpty();
      buffer.writeBoolean(present);
      if (present) {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
          ItemStack.STREAM_CODEC.encode(registryBuffer, out);
        } else {
          stack.encode(buffer, out);
        }
      }
    }


    /* Defaulting behavior */

    /** Gets the output, defaulting to empty. Note this will not stop you from getting empty with a non-empty loadable, thats on you for weirdly calling. */
    public ItemOutput getOrEmpty(JsonObject parent, String key) {
      return getOrDefault(parent, key, ItemOutput.EMPTY);
    }

    /** Creates a field defaulting to empty */
    public <P> LoadableField<ItemOutput,P> emptyField(String key, boolean serializeDefault, Function<P,ItemOutput> getter) {
      return defaultField(key, ItemOutput.EMPTY, serializeDefault, getter);
    }

    /** Creates a field defaulting to empty that does not serialize if empty */
    public <P> LoadableField<ItemOutput,P> emptyField(String key, Function<P,ItemOutput> getter) {
      return emptyField(key, false, getter);
    }
  }
}
