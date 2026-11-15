package slimeknights.mantle.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import slimeknights.mantle.data.MantleCodecs;
import slimeknights.mantle.recipe.helper.ItemOutput;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * Loot modifier to replace an item with another.
 * <p>
 * JSON since 26.3: the loot conditions are a single {@code condition} plus a {@code priority} (NeoForge base fields), and the functions applied
 * to the replacement are a single {@code modifier}, which may be an inline list as in vanilla loot tables.
 */
public class ReplaceItemLootModifier extends LootModifier {
  public static final MapCodec<ReplaceItemLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).and(
    inst.group(
      MantleCodecs.INGREDIENT.fieldOf("original").forGetter(m -> m.original),
      ItemOutput.REQUIRED_STACK_CODEC.fieldOf("replacement").forGetter(m -> m.replacement),
      LootItemFunctions.CODEC.optionalFieldOf("modifier").forGetter(m -> m.modifier)
    )).apply(inst, ReplaceItemLootModifier::new));

  /** Ingredient to test for the original item */
  private final Ingredient original;
  /** Item for the replacement */
  private final ItemOutput replacement;
  /** Function to apply to the replacement */
  private final Optional<Holder<LootItemFunction>> modifier;

  protected ReplaceItemLootModifier(Optional<Holder<LootItemCondition>> condition, int priority, Ingredient original, ItemOutput replacement, Optional<Holder<LootItemFunction>> modifier) {
    super(condition, priority);
    this.original = original;
    this.replacement = replacement;
    this.modifier = modifier;
  }

  /** Creates a builder to create a loot modifier */
  public static Builder builder(Ingredient original, ItemOutput replacement) {
    return new Builder(original, replacement);
  }

  @Nonnull
  @Override
  protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
    ListIterator<ItemStack> iterator = generatedLoot.listIterator();
    while (iterator.hasNext()) {
      ItemStack stack = iterator.next();
      if (original.test(stack)) {
        ItemStack replacement = this.replacement.get();
        replacement = replacement.copyWithCount(replacement.getCount() * stack.getCount());
        if (modifier.isPresent()) {
          replacement = modifier.get().value().apply(replacement, context);
        }
        iterator.set(replacement);
      }
    }
    return generatedLoot;
  }

  @Override
  public MapCodec<? extends IGlobalLootModifier> codec() {
    return CODEC;
  }

  /** Logic to build this modifier for datagen */
  public static class Builder extends AbstractLootModifierBuilder<Builder> {
    private final Ingredient input;
    private final ItemOutput replacement;
    private final List<Holder<LootItemFunction>> functions = new ArrayList<>();

    private Builder(Ingredient input, ItemOutput replacement) {
      this.input = input;
      this.replacement = replacement;
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
    public ReplaceItemLootModifier build() {
      return new ReplaceItemLootModifier(getCondition(), getPriority(), input, replacement, FunctionUserBuilder.buildFunction(functions));
    }
  }
}
