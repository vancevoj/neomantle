package slimeknights.mantle.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import slimeknights.mantle.data.MantleCodecs;
import slimeknights.mantle.loot.condition.ILootModifierCondition;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Loot modifier to inject an additional loot entry into an existing table.
 * <p>
 * JSON since 26.3: the loot conditions are a single {@code condition} plus a {@code priority} (NeoForge base fields), and the functions applied
 * to the entry are a single {@code modifier}, which may be an inline list as in vanilla loot tables.
 */
public class AddEntryLootModifier extends LootModifier {
  public static final MapCodec<AddEntryLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).and(inst.group(
    ILootModifierCondition.CODEC.listOf().fieldOf("post_conditions").forGetter(m -> m.modifierConditions),
    MantleCodecs.LOOT_ENTRY.fieldOf("entry").forGetter(m -> m.entry),
    LootItemFunctions.CODEC.optionalFieldOf("modifier").forGetter(m -> m.modifier))).apply(inst, AddEntryLootModifier::new));

  /** Additional conditions that can consider the previously generated loot */
  private final List<ILootModifierCondition> modifierConditions;
  /** Entry for generating loot */
  private final LootPoolEntryContainer entry;
  /** Function to apply to the entry, allows adding functions to parented loot entries such as alternatives */
  private final Optional<Holder<LootItemFunction>> modifier;

  protected AddEntryLootModifier(Optional<Holder<LootItemCondition>> condition, int priority, List<ILootModifierCondition> modifierConditions, LootPoolEntryContainer entry, Optional<Holder<LootItemFunction>> modifier) {
    super(condition, priority);
    this.modifierConditions = modifierConditions;
    this.entry = entry;
    this.modifier = modifier;
  }

  /** Creates a builder for this loot modifier */
  public static Builder builder(LootPoolEntryContainer entry) {
    return new Builder(entry);
  }

  /** Creates a builder for this loot modifier */
  public static Builder builder(LootPoolEntryContainer.Builder<?> builder) {
    return builder(builder.build());
  }

  @Nonnull
  @Override
  protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
    // if any condition fails, exit immediately
    for (ILootModifierCondition modifierCondition : modifierConditions) {
      if (!modifierCondition.test(generatedLoot, context)) {
        return generatedLoot;
      }
    }
    // generate the actual entry
    Consumer<ItemStack> consumer = LootItemFunction.decorate(this.modifier, generatedLoot::add, context);
    entry.expand(context, generator -> generator.createItemStack(consumer, context));
    return generatedLoot;
  }

  @Override
  public MapCodec<? extends IGlobalLootModifier> codec() {
    return CODEC;
  }

  /** Builder for a conditional loot entry */
  public static class Builder extends AbstractLootModifierBuilder<Builder> {
    private final List<ILootModifierCondition> modifierConditions = new ArrayList<>();
    private final LootPoolEntryContainer entry;
    private final List<Holder<LootItemFunction>> functions = new ArrayList<>();

    private Builder(LootPoolEntryContainer entry) {
      this.entry = entry;
    }

    /**
     * Adds a loot entry condition to the builder
     */
    public Builder addCondition(ILootModifierCondition condition) {
      modifierConditions.add(condition);
      return this;
    }

    /**
     * Adds a loot function to the builder. Multiple functions are combined into a sequence.
     */
    public Builder addFunction(LootItemFunction function) {
      return addFunction(Holder.direct(function));
    }

    /**
     * Adds a loot function to the builder, which may be a reference to a registered item modifier. Multiple functions are combined into a sequence.
     */
    public Builder addFunction(Holder<LootItemFunction> function) {
      functions.add(function);
      return this;
    }

    /** Builds the final modifier */
    public AddEntryLootModifier build() {
      return new AddEntryLootModifier(getCondition(), getPriority(), modifierConditions, entry, FunctionUserBuilder.buildFunction(functions));
    }
  }
}
