package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Inverted form of {@link TagEmptyCondition} as filled is way more common a desire than empty. */
public class TagFilledCondition<T> extends TagCondition<T> implements LootItemCondition {
  public static final MapCodec<TagFilledCondition<?>> CODEC = TagCondition.makeCodec(TagFilledCondition::new);

  public TagFilledCondition(TagKey<T> tag) {
    super(tag);
  }

  public TagFilledCondition(ResourceKey<? extends Registry<T>> registry, Identifier name) {
    this(TagKey.create(registry, name));
  }

  /** Codec for both recipe and loot conditions, the same codec instance is registered in both registries */
  @Override
  public MapCodec<TagFilledCondition<?>> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    return !context.getTag(tag).isEmpty();
  }

  @Override
  public boolean test(LootContext context) {
    Registry<T> registry = registry(context);
    return registry != null && registry.getTagOrEmpty(tag).iterator().hasNext();
  }
}
