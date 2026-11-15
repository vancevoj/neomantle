package slimeknights.mantle.recipe.data;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Builds a {@link RecipeOutput} wrapper, which adds conditions to all recipes passed through it.
 * <p>
 * In 1.21 recipes carry their own serializer, so the legacy serializer-type override is no longer
 * meaningful and has been dropped. For condition-only wrapping you may also use
 * {@link RecipeOutput#withConditions(ICondition...)} directly.
 */
@SuppressWarnings("unused")  // API
public class ConsumerWrapperBuilder {
  private final List<ICondition> conditions = new ArrayList<>();

  private ConsumerWrapperBuilder() {}

  /**
   * Creates a wrapper builder
   * @return Wrapper builder
   */
  public static ConsumerWrapperBuilder wrap() {
    return new ConsumerWrapperBuilder();
  }

  /**
   * Adds a conditional to the consumer
   * @param condition Condition to add
   * @return Added condition
   */
  @CanIgnoreReturnValue
  public ConsumerWrapperBuilder addCondition(ICondition condition) {
    conditions.add(condition);
    return this;
  }

  /**
   * Builds the recipe output for the wrapper builder
   * @param base  Base recipe output
   * @return Built wrapper recipe output that injects the accumulated conditions
   */
  public RecipeOutput build(RecipeOutput base) {
    return new Wrapped(base, List.copyOf(conditions));
  }

  /** Recipe output that prepends a set of conditions to every recipe before delegating */
  private record Wrapped(RecipeOutput base, List<ICondition> conditions) implements RecipeOutput {
    @Override
    public Advancement.Builder advancement() {
      return base.advancement();
    }

    @Override
    public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
      return base.lookup(key);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
      return base.listContextElements(key);
    }

    @Override
    public <S> Optional<HolderLookup<S>> holderLookup(ResourceKey<? extends Registry<? extends S>> registry) {
      return base.holderLookup(registry);
    }

    @Override
    public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      ICondition[] combined;
      if (this.conditions.isEmpty()) {
        combined = conditions;
      } else {
        combined = new ICondition[this.conditions.size() + conditions.length];
        this.conditions.toArray(combined);
        System.arraycopy(conditions, 0, combined, this.conditions.size(), conditions.length);
      }
      base.accept(id, recipe, advancement, combined);
    }
  }
}
