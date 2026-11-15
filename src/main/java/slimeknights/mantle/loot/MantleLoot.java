package slimeknights.mantle.loot;

import com.google.gson.JsonDeserializer;
import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.loot.condition.BlockTagLootCondition;
import slimeknights.mantle.loot.condition.ContainsItemModifierLootCondition;
import slimeknights.mantle.loot.condition.EmptyModifierLootCondition;
import slimeknights.mantle.loot.condition.HasLootContextSetCondition;
import slimeknights.mantle.loot.condition.ILootModifierCondition;
import slimeknights.mantle.loot.condition.InvertedModifierLootCondition;
import slimeknights.mantle.loot.entry.TagPreferenceLootEntry;
import slimeknights.mantle.loot.function.RetexturedLootFunction;
import slimeknights.mantle.loot.function.SetFluidLootFunction;
import slimeknights.mantle.recipe.condition.TagEmptyCondition;
import slimeknights.mantle.recipe.condition.TagFilledCondition;

import static slimeknights.mantle.loot.condition.ILootModifierCondition.MODIFIER_CONDITIONS;

/**
 * Registration for Mantle loot logic.
 * <p>
 * Since 26.1 the loot type registries hold the {@link MapCodec} directly instead of a type wrapper, so these fields are the registered codecs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MantleLoot {
  /** Matches if the passed tag is empty */
  public static final MapCodec<TagEmptyCondition<?>> TAG_EMPTY = TagEmptyCondition.CODEC;
  /** Matches if the passed tag is filled */
  public static final MapCodec<TagFilledCondition<?>> TAG_FILLED = TagFilledCondition.CODEC;
  /** Condition to match a block tag and property predicate */
  public static final MapCodec<BlockTagLootCondition> BLOCK_TAG_CONDITION = BlockTagLootCondition.CODEC;
  /** Condition for global loot modifiers that ensures a context set is present. Useful to check if we are in a specific context like entity. */
  public static final MapCodec<HasLootContextSetCondition> HAS_CONTEXT_SET = HasLootContextSetCondition.CODEC;
  /** Function to add block entity texture to a dropped item */
  public static final MapCodec<RetexturedLootFunction> RETEXTURED_FUNCTION = RetexturedLootFunction.CODEC;
  /** Function to add a fluid to an item fluid capability */
  public static final MapCodec<SetFluidLootFunction> SET_FLUID_FUNCTION = SetFluidLootFunction.CODEC;
  /** Entry to pull a value from a tag preference */
  public static final MapCodec<TagPreferenceLootEntry> TAG_PREFERENCE = TagPreferenceLootEntry.CODEC;


  /**
   * Called during registration to register any relevant loot logic.
   * <p>
   * Loot conditions, functions, entries, and global loot modifier serializers are all codec based ({@link MapCodec}).
   */
  public static void registerGlobalLootModifiers(final RegisterEvent event) {
    ResourceKey<?> key = event.getRegistryKey();

    if (key == NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS) {
      event.register(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mantle.getResource("add_entry"), () -> AddEntryLootModifier.CODEC);
      event.register(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mantle.getResource("replace_item"), () -> ReplaceItemLootModifier.CODEC);

      // loot modifier conditions (still JSON based, used only within global loot modifiers)
      MODIFIER_CONDITIONS.registerDeserializer(InvertedModifierLootCondition.ID, (JsonDeserializer<? extends ILootModifierCondition>) InvertedModifierLootCondition::deserialize);
      MODIFIER_CONDITIONS.registerDeserializer(EmptyModifierLootCondition.ID, EmptyModifierLootCondition.INSTANCE);
      MODIFIER_CONDITIONS.registerDeserializer(ContainsItemModifierLootCondition.ID, (JsonDeserializer<? extends ILootModifierCondition>) ContainsItemModifierLootCondition::deserialize);
    } else if (key == Registries.LOOT_FUNCTION_TYPE) {
      event.register(Registries.LOOT_FUNCTION_TYPE, Mantle.getResource("fill_retextured_block"), () -> RETEXTURED_FUNCTION);
      event.register(Registries.LOOT_FUNCTION_TYPE, Mantle.getResource("set_fluid"), () -> SET_FLUID_FUNCTION);

    } else if (key == Registries.LOOT_CONDITION_TYPE) {
      event.register(Registries.LOOT_CONDITION_TYPE, Mantle.getResource("block_tag"), () -> BLOCK_TAG_CONDITION);
      event.register(Registries.LOOT_CONDITION_TYPE, Mantle.getResource("has_context_set"), () -> HAS_CONTEXT_SET);
      event.register(Registries.LOOT_CONDITION_TYPE, Mantle.getResource("tag_empty"), () -> TAG_EMPTY);
      event.register(Registries.LOOT_CONDITION_TYPE, Mantle.getResource("tag_filled"), () -> TAG_FILLED);

    } else if (key == Registries.LOOT_POOL_ENTRY_TYPE) {
      event.register(Registries.LOOT_POOL_ENTRY_TYPE, Mantle.getResource("tag_preference"), () -> TAG_PREFERENCE);
    }
  }
}
