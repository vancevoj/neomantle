package slimeknights.mantle.loot.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;
import java.util.Set;

/** Variant of {@link net.minecraft.world.level.storage.loot.predicates.MatchBlock} that allows using a tag for block type instead of a block */
public record BlockTagLootCondition(TagKey<Block> tag, Optional<StatePropertiesPredicate> properties) implements LootItemCondition {
  /** Codec for this condition */
  public static final MapCodec<BlockTagLootCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(BlockTagLootCondition::tag),
    StatePropertiesPredicate.CODEC.optionalFieldOf("properties").forGetter(BlockTagLootCondition::properties)
  ).apply(inst, BlockTagLootCondition::new));

  public BlockTagLootCondition(TagKey<Block> tag) {
    this(tag, Optional.empty());
  }

  public BlockTagLootCondition(TagKey<Block> tag, StatePropertiesPredicate.Builder builder) {
    this(tag, builder.build());
  }

  @Override
  public boolean test(LootContext context) {
    BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
    return state != null && state.is(tag) && (this.properties.isEmpty() || this.properties.get().matches(state));
  }

  @Override
  public Set<ContextKey<?>> getReferencedContextParams() {
    return Set.of(LootContextParams.BLOCK_STATE);
  }

  @Override
  public MapCodec<BlockTagLootCondition> codec() {
    return CODEC;
  }
}
