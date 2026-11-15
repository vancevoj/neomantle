package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Condition that checks when a tag is empty. Same as {@link net.neoforged.neoforge.common.conditions.TagEmptyCondition} but for any registry */
public class TagEmptyCondition<T> extends TagCondition<T> implements LootItemCondition {
  public static final MapCodec<TagEmptyCondition<?>> CODEC = TagCondition.makeCodec(TagEmptyCondition::new);

  public TagEmptyCondition(TagKey<T> tag) {
    super(tag);
  }

  public TagEmptyCondition(ResourceKey<? extends Registry<T>> registry, Identifier name) {
    this(TagKey.create(registry, name));
  }

  /** Codec for both recipe and loot conditions, the same codec instance is registered in both registries */
  @Override
  public MapCodec<TagEmptyCondition<?>> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    return context.getTag(tag).isEmpty();
  }

  @Override
  public boolean test(LootContext context) {
    Registry<T> registry = registry(context);
    return registry != null && !registry.getTagOrEmpty(tag).iterator().hasNext();
  }
}
