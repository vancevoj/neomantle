package slimeknights.mantle.data;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Tag provider for any registry from {@link net.minecraft.core.registries.BuiltInRegistries} that lacks a standard tag provider.
 * Since 1.21.6 vanilla tag providers are key based, this provider restores the ability to add registry values directly through {@link IntrinsicTagAppender}.
 */
public abstract class BuiltinRegistryTagProvider<T> extends TagsProvider<T> {
  private final Function<T,ResourceKey<T>> keyExtractor;

  public BuiltinRegistryTagProvider(PackOutput packOutput, Registry<T> registry, CompletableFuture<Provider> lookupProvider, String modId) {
    super(packOutput, registry.key(), lookupProvider, modId);
    this.keyExtractor = value -> registry.getResourceKey(value).orElseThrow(() -> new IllegalArgumentException("Value " + value + " is not registered in " + registry.key().identifier()));
  }

  @Override
  protected IntrinsicTagAppender<T> tag(TagKey<T> tag) {
    return new IntrinsicTagAppender<>(super.tag(tag), keyExtractor);
  }

  @Override
  protected IntrinsicTagAppender<T> tag(TagKey<T> tag, boolean replace) {
    return new IntrinsicTagAppender<>(super.tag(tag, replace), keyExtractor);
  }

  /** Tag appender which supports adding registry values in addition to keys */
  public static class IntrinsicTagAppender<T> implements TagAppender<T> {
    private final TagAppender<T> parent;
    private final Function<T,ResourceKey<T>> keyExtractor;

    protected IntrinsicTagAppender(TagAppender<T> parent, Function<T,ResourceKey<T>> keyExtractor) {
      this.parent = parent;
      this.keyExtractor = keyExtractor;
    }

    /** Adds a value to the tag */
    public IntrinsicTagAppender<T> add(T value) {
      parent.add(keyExtractor.apply(value));
      return this;
    }

    /** Adds the given values to the tag */
    @SafeVarargs
    public final IntrinsicTagAppender<T> add(T... values) {
      for (T value : values) {
        add(value);
      }
      return this;
    }

    /** Removes a value from the tag */
    public IntrinsicTagAppender<T> remove(T value) {
      parent.remove(keyExtractor.apply(value));
      return this;
    }

    /** Removes the given values from the tag */
    @SafeVarargs
    public final IntrinsicTagAppender<T> remove(T... values) {
      for (T value : values) {
        remove(value);
      }
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> add(ResourceKey<T> element) {
      parent.add(element);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptional(ResourceKey<T> element) {
      parent.addOptional(element);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> addTag(TagKey<T> tag) {
      parent.addTag(tag);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptionalTag(TagKey<T> tag) {
      parent.addOptionalTag(tag);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> add(TagEntry entry) {
      parent.add(entry);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> replace(boolean value) {
      parent.replace(value);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> remove(ResourceKey<T> element) {
      parent.remove(element);
      return this;
    }

    @Override
    public IntrinsicTagAppender<T> remove(TagKey<T> tag) {
      parent.remove(tag);
      return this;
    }
  }
}
