package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.field.RecordField;
import slimeknights.mantle.data.loadable.field.UnsyncedField;
import slimeknights.mantle.util.typed.TypedMap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Abstract ingredient that matches a list of items or a tag, mirroring the vanilla syntax */
public abstract class ItemIngredient implements ICustomIngredient {
  /** Field for the item tag */
  protected static final LoadableField<TagKey<Item>,ItemIngredient> TAG_FIELD = new UnsyncedField<>(Loadables.ITEM_TAG.nullableField("tag", i -> i.tag));

  protected final List<Item> items;
  @Nullable
  protected final TagKey<Item> tag;

  /** Constructor taking a list of items and an optional tag */
  protected ItemIngredient(List<Item> items, @Nullable TagKey<Item> tag) {
    this.items = items;
    this.tag = tag;
  }

  /** Maps the list to a list of items */
  protected static List<Item> toItem(List<ItemLike> items) {
    return items.stream().map(ItemLike::asItem).toList();
  }

  /** Gets the list of items directly matched by this ingredient (excludes tag contents) */
  public List<Item> getItemList() {
    return items;
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    // for tag checks it's way easier to just check directly
    // also ensures we never match empty just because our lists are empty
    return stack != null && (items.contains(stack.getItem()) || tag != null && stack.is(tag));
  }

  @Override
  public Stream<Holder<Item>> items() {
    Stream<Holder<Item>> itemHolders = items.stream().map(Item::builtInRegistryHolder);
    if (tag != null) {
      Stream<Holder<Item>> tagHolders = StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false);
      return Stream.concat(itemHolders, tagHolders);
    }
    return itemHolders;
  }

  /** Gets the list of display stacks for this ingredient, replacing {@code getItems()} from before 1.21.2. Subclasses may add components. */
  public Stream<ItemStack> getStacks() {
    return items().map(ItemStack::new);
  }

  /** Display showing each stack from {@link #getStacks()}, so subclasses adding components are displayed properly */
  @Override
  public SlotDisplay display() {
    return new SlotDisplay.Composite(getStacks().filter(stack -> !stack.isEmpty()).<SlotDisplay>map(stack -> new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack))).toList());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj != null && obj.getClass() == this.getClass() && obj instanceof ItemIngredient other && items.equals(other.items) && Objects.equals(tag, other.tag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), items, tag);
  }

  /** Custom field that syncs the item tag as items to the client */
  public enum ItemsField implements RecordField<List<Item>,ItemIngredient> {
    INSTANCE;

    private static final Loadable<List<Item>> ITEM_LIST = Loadables.ITEM.list(ArrayLoadable.COMPACT_OR_EMPTY);

    @Override
    public List<Item> get(JsonObject json, TypedMap context) {
      return ITEM_LIST.getOrDefault(json, "item", List.of(), context);
    }

    @Override
    public void serialize(ItemIngredient parent, JsonObject json) {
      if (!parent.items.isEmpty()) {
        json.add("item", ITEM_LIST.serialize(parent.items));
      }
    }

    @Override
    public List<Item> decode(FriendlyByteBuf buffer, TypedMap context) {
      return ITEM_LIST.decode(buffer, context);
    }

    @Override
    public void encode(FriendlyByteBuf buffer, ItemIngredient parent) {
      // sync both tag and item values to client
      ITEM_LIST.encode(buffer, parent.items().map(Holder::value).toList());
    }
  }
}
