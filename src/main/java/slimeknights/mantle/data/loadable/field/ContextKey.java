package slimeknights.mantle.data.loadable.field;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.RegistryOps.HolderLookupAdapter;
import net.minecraft.resources.RegistryOps.RegistryInfoLookup;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.conditions.ICondition.IContext;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMap.Key;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Key for fetching properties from a loadable context. This key doubles as a record field for a required context key.
 * @param <T>  Field type
 */
@RequiredArgsConstructor
public class ContextKey<T> implements Key<T> {
  /** Context field representing the object's ID */
  public static final ContextKey<Identifier> ID = new ContextKey<>("id");
  /** Key for adding debug info to log messages. Generally not useful as a field */
  public static final ContextKey<String> DEBUG = new ContextKey<>("debug info");
  /** Key for adding condition context, used in {@link slimeknights.mantle.data.loadable.mapping.ConditionalLoadable} */
  public static final ContextKey<IContext> CONDITION_CONTEXT = new ContextKey<>("condition context");
  /** Key for the registry access / holder lookup, used to resolve data-driven registry entries (e.g. 1.21 enchantments) during JSON parsing */
  public static final ContextKey<HolderLookup.Provider> REGISTRIES = new ContextKey<>("registries");
  /**
   * Key for registry aware JSON ops, set when a loadable is parsed through a codec running on {@link RegistryOps} (e.g. recipes or reloadable registries).
   * Since 1.21.2 tags in codecs (such as ingredients) can only be resolved through registry ops. Prefer {@link #jsonOps(TypedMap)} or {@link #getter(TypedMap, ResourceKey)} over reading this directly.
   */
  public static final ContextKey<RegistryOps<JsonElement>> REGISTRY_OPS = new ContextKey<>("registry ops");

  /** Name of the field, used primarily for debug */
  @Getter
  private final String name;



  @Override
  public String toString() {
    return "ContextKey('" + name + "')'";
  }


  /* Registry helpers */

  /** Adds registry context from the given ops to the builder. Does nothing if the ops are not {@link RegistryOps}. */
  public static TypedMapBuilder withOps(TypedMapBuilder builder, DynamicOps<?> ops) {
    if (ops instanceof RegistryOps<?> registryOps) {
      builder.put(REGISTRY_OPS, registryOps.withParent(JsonOps.INSTANCE));
      if (registryOps.lookupProvider instanceof HolderLookupAdapter adapter) {
        builder.put(REGISTRIES, adapter.lookupProvider);
      }
    }
    return builder;
  }

  /** Creates a context from the given ops, containing registry access if the ops are {@link RegistryOps}. */
  public static TypedMap fromOps(DynamicOps<?> ops) {
    if (ops instanceof RegistryOps<?>) {
      return withOps(TypedMapBuilder.builder(), ops).build();
    }
    return TypedMap.EMPTY;
  }

  /** Gets the registry getters from the condition context, if it is present and has registries */
  @Nullable
  private static HolderGetter.Provider conditionRegistries(TypedMap context) {
    IContext conditions = context.get(CONDITION_CONTEXT);
    if (conditions != null && conditions != IContext.EMPTY && conditions != IContext.TAGS_INVALID) {
      HolderGetter.Provider registries = conditions.registries();
      if (registries != RegistryAccess.EMPTY) {
        return registries;
      }
    }
    return null;
  }

  /**
   * Gets registry aware JSON ops for the given context. Checks {@link #REGISTRY_OPS}, {@link #REGISTRIES}, then the registries of {@link #CONDITION_CONTEXT}.
   * If none are present, falls back to ops for the built-in registries.
   */
  public static DynamicOps<JsonElement> jsonOps(TypedMap context) {
    RegistryOps<JsonElement> ops = context.get(REGISTRY_OPS);
    if (ops != null) {
      return ops;
    }
    HolderLookup.Provider registries = context.get(REGISTRIES);
    if (registries != null) {
      return registries.createSerializationContext(JsonOps.INSTANCE);
    }
    HolderGetter.Provider getters = conditionRegistries(context);
    if (getters != null) {
      return RegistryOps.create(JsonOps.INSTANCE, new RegistryInfoLookup() {
        @Override
        public <E> Optional<HolderGetter<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
          return getters.<E>lookup(registryKey).map(getter -> getter);
        }
      });
    }
    return JsonHelper.registryOps();
  }

  /**
   * Gets the holder getter for the given registry from the context. Checks {@link #REGISTRY_OPS}, {@link #REGISTRIES}, then the registries of {@link #CONDITION_CONTEXT}.
   * Used to resolve data driven registry entries such as enchantments.
   * @return  Holder getter, or empty if the context has no registry access.
   */
  public static <E> Optional<HolderGetter<E>> getter(TypedMap context, ResourceKey<? extends Registry<? extends E>> registryKey) {
    RegistryOps<JsonElement> ops = context.get(REGISTRY_OPS);
    if (ops != null) {
      Optional<HolderGetter<E>> getter = ops.<E>getter(registryKey);
      if (getter.isPresent()) {
        return getter;
      }
    }
    HolderLookup.Provider registries = context.get(REGISTRIES);
    if (registries != null) {
      Optional<HolderGetter<E>> getter = registries.<E>lookup(registryKey).map(lookup -> lookup);
      if (getter.isPresent()) {
        return getter;
      }
    }
    HolderGetter.Provider getters = conditionRegistries(context);
    if (getters != null) {
      return getters.<E>lookup(registryKey).map(lookup -> lookup);
    }
    return Optional.empty();
  }


  /* Fields */
  private RecordField<T,Object> requiredField;
  private RecordField<T,Object> nullableField;

  /** Gets a field requiring this context parameter */
  public RecordField<T,Object> requiredField() {
    if (requiredField == null) {
      requiredField = new Required<>(this, (t,e) -> t);
    }
    return requiredField;
  }

  /** Gets a field requiring this context parameter, but mapped using the passed function */
  public <M> RecordField<M,Object> mappedField(BiFunction<T,ErrorFactory,M> mapper) {
    return new Required<>(this, mapper);
  }

  /** Field using null in place of this parameter if missing */
  public RecordField<T,Object> nullableField() {
    if (nullableField == null) {
      nullableField = new Defaulting<>(this, null);
    }
    return nullableField;
  }

  /** Creates a defaulting field for this key */
  public RecordField<T,Object> defaultField(T defaultValue) {
    return new Defaulting<>(this, defaultValue);
  }


  /** Field instance for making the key required */
  private record Required<T,M>(ContextKey<T> key, BiFunction<T,ErrorFactory,M> mapper) implements ContextField<M> {
    @Override
    public M get(TypedMap context, ErrorFactory error) {
      T value = context.get(key);
      if (value != null) {
        return mapper.apply(value, error);
      }
      throw error.create("Unable to fetch " + key.name + " from context, this usually implements a broken JSON deserializer");
    }
  }

  /** Field instance that defaults to a given value */
  private record Defaulting<T>(ContextKey<T> key, @Nullable T defaultValue) implements ContextField<T> {
    @Override
    public T get(TypedMap context, ErrorFactory error) {
      // it is potentially faster to use get over getOrDefault so call it directly if we have no default value
      if (defaultValue == null) {
        return context.get(key);
      }
      return context.getOrDefault(key, defaultValue);
    }
  }
}
