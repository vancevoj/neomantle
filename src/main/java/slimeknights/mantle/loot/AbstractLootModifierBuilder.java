package slimeknights.mantle.loot;

import lombok.RequiredArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/** Base builder for a global loot modifier during datagen, intended to be used with {@link GlobalLootModifierProvider} */
public abstract class AbstractLootModifierBuilder<B extends AbstractLootModifierBuilder<B>> {
  private final List<Holder<LootItemCondition>> conditions = new ArrayList<>();
  private int priority = IGlobalLootModifier.DEFAULT_PRIORITY;

  /** Returns this builder casted to the subclass */
  @SuppressWarnings("unchecked")
  private B self() {
    return (B) this;
  }

  /** Adds a condition to the builder */
  public B addCondition(LootItemCondition condition) {
    return addCondition(Holder.direct(condition));
  }

  /** Adds a condition to the builder, which may be a reference to a registered predicate */
  public B addCondition(Holder<LootItemCondition> condition) {
    conditions.add(condition);
    return self();
  }

  /**
   * Sets the priority of this modifier. Modifiers with higher priority run first.
   * Since 26.3 NeoForge has no global ordering list, so this is the only way to order modifiers.
   */
  public B priority(int priority) {
    this.priority = priority;
    return self();
  }

  /** Gets the conditions merged into a single condition, using all of if there are multiple */
  protected Optional<Holder<LootItemCondition>> getCondition() {
    return ConditionUserBuilder.buildCondition(conditions);
  }

  /** Gets the priority for this modifier */
  protected int getPriority() {
    return priority;
  }

  /** Generic builder for a modifier that just takes a condition and priority */
  @RequiredArgsConstructor
  public static class GenericLootModifierBuilder<M extends LootModifier> extends AbstractLootModifierBuilder<GenericLootModifierBuilder<M>> {
    private final BiFunction<Optional<Holder<LootItemCondition>>, Integer, M> constructor;

    /** Builds the final instance */
    public M build() {
      return constructor.apply(getCondition(), getPriority());
    }
  }
}
