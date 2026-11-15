package slimeknights.mantle.loot.entry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.SingleEntryContainerBase;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.mantle.recipe.helper.TagPreference;

import java.util.Optional;
import java.util.function.Consumer;

/** Loot entry that returns the preferred item from a tag. See {@link TagPreference} */
public class TagPreferenceLootEntry extends SingleEntryContainerBase {
  /** Codec for this entry */
  public static final MapCodec<TagPreferenceLootEntry> CODEC = RecordCodecBuilder.mapCodec(inst ->
    inst.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(e -> e.tag))
        .and(uniformFields(inst))
        .apply(inst, TagPreferenceLootEntry::new));

  private final TagKey<Item> tag;
  protected TagPreferenceLootEntry(TagKey<Item> tag, int weight, int quality, Optional<Holder<LootItemCondition>> condition, Optional<Holder<LootItemFunction>> modifier) {
    super(weight, quality, condition, modifier);
    this.tag = tag;
  }

  @Override
  public MapCodec<TagPreferenceLootEntry> codec() {
    return CODEC;
  }

  @Override
  protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
    TagPreference.getPreference(tag).ifPresent(item -> consumer.accept(new ItemStack(item)));
  }

  /** Creates a new builder */
  @SuppressWarnings("unused") // API
  public static UniformContainerBase.Builder<?> tagPreference(TagKey<Item> tag) {
    return simpleBuilder((weight, quality, condition, modifier) -> new TagPreferenceLootEntry(tag, weight, quality, condition, modifier));
  }
}
