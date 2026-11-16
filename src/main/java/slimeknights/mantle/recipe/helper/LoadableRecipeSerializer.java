package slimeknights.mantle.recipe.helper;

import com.google.common.collect.MapMaker;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Recipe serializer factories using loadables. Bridges a {@link RecordLoadable} to the vanilla {@link MapCodec} + {@link StreamCodec}
 * pair required by {@link RecipeSerializer}, which since 26.1 is a final record.
 * <p>
 * The recipe's {@link Identifier} is not available during decode (it is the registry key, assigned to the {@link net.minecraft.world.item.crafting.RecipeHolder}
 * only after decoding). Recipes that need a non-null {@link ContextKey#ID} (for JEI display and logging) therefore receive a stable synthetic id derived from
 * the serializer's registry name plus the recipe contents. The id is written into the network stream so the client recipe receives the same id as the server.
 * Nothing in game logic should key off this id; matching, caches, and authoritative id lookups should use the real {@link net.minecraft.world.item.crafting.RecipeHolder} id.
 * <p>
 * The parse context is rebuilt for every decode, and includes the registry ops and condition context from the {@link DynamicOps} used to parse the recipe.
 */
public final class LoadableRecipeSerializer {
  /** Context key to use if you want the recipe serializer passed into your recipe */
  public static final ContextKey<RecipeSerializer<?>> SERIALIZER = new ContextKey<>("serializer");
  /** Context key to use if you want a type aware serializer in the recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<TypeAwareRecipeSerializer<?>> TYPED_SERIALIZER = new ContextKey<>("typed_serializer");
  /** Context key to use if you want the recipe type passed into your recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<RecipeType<?>> TYPE = new ContextKey<>("type");
  /** Field for a group key in a recipe (common requirement) */
  public static final LoadableField<String,Recipe<?>> RECIPE_GROUP = StringLoadable.DEFAULT.defaultField("group", "", Recipe::group);

  /** Fallback namespace used when the serializer is not yet registered (should not happen in practice) */
  private static final String FALLBACK_NAMESPACE = "mantle";
  /** Ids given to each recipe during decode, weak identity keys so recipes from old reloads are released */
  private static final Map<Recipe<?>,Identifier> RECIPE_IDS = new MapMaker().weakKeys().makeMap();
  /** Type aware serializer for each serializer created by {@link #of(RecordLoadable, Supplier)} */
  private static final Map<RecipeSerializer<?>,TypeAwareRecipeSerializer<?>> TYPE_AWARE = new MapMaker().weakKeys().makeMap();

  private LoadableRecipeSerializer() {}

  /** Creates a standard serializer from a loadable */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable) {
    return new Factory<>(loadable, null).serializer;
  }

  /**
   * Creates a type aware serializer from a loadable. The recipe receives the {@link TypeAwareRecipeSerializer} through {@link #TYPED_SERIALIZER}
   * and the type through {@link #TYPE}; the type supplier is only called during decode, so it is safe to pass an unbound registry object.
   * @param loadable  Recipe loadable
   * @param type      Recipe type supplier
   * @return  Serializer to register. Use {@link #getTypeAware(RecipeSerializer)} to fetch the matching type aware serializer, e.g. for datagen.
   */
  public static <T extends R, R extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable, Supplier<? extends RecipeType<R>> type) {
    Factory<T> factory = new Factory<>(loadable, type);
    TYPE_AWARE.put(factory.serializer, factory);
    return factory.serializer;
  }

  /**
   * Gets the type aware serializer for a serializer created by {@link #of(RecordLoadable, Supplier)}.
   * @param serializer  Registered serializer
   * @return  Type aware serializer, or null if the serializer is not type aware
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public static <T extends Recipe<?>> TypeAwareRecipeSerializer<T> getTypeAware(RecipeSerializer<T> serializer) {
    return (TypeAwareRecipeSerializer<T>) TYPE_AWARE.get(serializer);
  }

  /**
   * Gets the ID given to the recipe through {@link ContextKey#ID} when it was decoded by a loadable serializer.
   * This is the synthetic ID described in the class javadoc, not the registry key.
   * @param recipe  Recipe instance
   * @return  Recipe ID, or null if the recipe was not decoded by a loadable serializer
   */
  @Nullable
  public static Identifier getRecipeId(Recipe<?> recipe) {
    return RECIPE_IDS.get(recipe);
  }

  /** Namespace for synthetic recipe ids, the registry name of the serializer if available */
  private static String syntheticNamespace(RecipeSerializer<?> serializer) {
    Identifier key = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
    return key != null ? key.getNamespace() : FALLBACK_NAMESPACE;
  }

  /**
   * Builds a stable synthetic id from a hash. The real recipe id is unavailable during decode (it is the registry key assigned to the
   * {@link net.minecraft.world.item.crafting.RecipeHolder} after decoding), so we derive a deterministic placeholder.
   * Uniqueness keeps JEI recipe UIDs from colliding; nothing in game logic depends on it.
   */
  private static Identifier syntheticId(RecipeSerializer<?> serializer, int hash) {
    // mask off the sign so the path is always a valid lowercase hex string
    return Identifier.fromNamespaceAndPath(syntheticNamespace(serializer), "synthetic/" + Integer.toHexString(hash & 0x7fffffff));
  }

  /**
   * Adds registry and condition context from the given ops to the context builder.
   * @param builder  Builder instance
   * @param ops      Ops used to parse the object
   * @param input    Map being parsed, used to retrieve the condition context
   * @return  Builder instance
   */
  public static <O> TypedMapBuilder withOps(TypedMapBuilder builder, DynamicOps<O> ops, MapLike<O> input) {
    ContextKey.withOps(builder, ops);
    ConditionalOps.retrieveContext().decode(ops, input).result()
                  .filter(context -> context != ICondition.IContext.EMPTY)
                  .ifPresent(context -> builder.put(ContextKey.CONDITION_CONTEXT, context));
    return builder;
  }

  /** Holds all logic for a serializer instance. Doubles as the type aware serializer when a type is given. */
  private static final class Factory<T extends Recipe<?>> implements TypeAwareRecipeSerializer<T> {
    private final RecordLoadable<T> loadable;
    @Nullable
    private final Supplier<? extends RecipeType<?>> type;
    private final RecipeSerializer<T> serializer;

    private Factory(RecordLoadable<T> loadable, @Nullable Supplier<? extends RecipeType<?>> type) {
      this.loadable = loadable;
      this.type = type;
      this.serializer = new RecipeSerializer<>(new LoadableMapCodec<>(loadable) {
        @Override
        protected void addContext(JsonObject json, TypedMapBuilder builder) {
          Factory.this.addContext(builder, syntheticId(Factory.this.serializer, json.toString().hashCode()));
        }

        @Override
        protected T finish(T value, TypedMap context) {
          return Factory.this.finish(value, context);
        }
      }, LoggingRecipeSerializer.wrap(this::name, StreamCodec.of(this::encode, this::decode)));
    }

    /** Name for error messages */
    private String name() {
      return "Recipe serializer " + BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
    }

    /** Adds the recipe context for the given ID */
    private void addContext(TypedMapBuilder builder, Identifier id) {
      builder.put(ContextKey.ID, id).put(ContextKey.DEBUG, "Recipe " + id).put(SERIALIZER, serializer);
      if (type != null) {
        builder.put(TYPE, type.get()).put(TYPED_SERIALIZER, this);
      }
    }

    /** Records the ID of the given recipe so it can be written to the network */
    private T finish(T recipe, TypedMap context) {
      Identifier id = context.get(ContextKey.ID);
      if (id != null) {
        RECIPE_IDS.put(recipe, id);
      }
      return recipe;
    }

    /** Writes the recipe to the network, prefixed by its ID so the client gets the same ID as the server */
    private void encode(RegistryFriendlyByteBuf buffer, T recipe) {
      Identifier id = RECIPE_IDS.get(recipe);
      buffer.writeBoolean(id != null);
      if (id != null) {
        buffer.writeIdentifier(id);
      }
      loadable.encode(buffer, recipe);
    }

    /** Reads the recipe from the network, rebuilding the parse context */
    private T decode(RegistryFriendlyByteBuf buffer) {
      // recipes not decoded by us (should not happen) fall back to a synthetic ID from the read position
      Identifier id = buffer.readBoolean() ? buffer.readIdentifier() : syntheticId(serializer, buffer.readerIndex());
      TypedMapBuilder builder = TypedMapBuilder.builder().put(ContextKey.REGISTRIES, buffer.registryAccess());
      addContext(builder, id);
      TypedMap context = builder.build();
      T recipe;
      try {
        recipe = loadable.decode(buffer, context);
      } catch (RuntimeException e) {
        throw new DecoderException("Failed to decode recipe " + id + ": " + e.getMessage(), e);
      }
      return finish(recipe, context);
    }

    @Override
    public RecipeType<?> getType() {
      if (type == null) {
        throw new IllegalStateException("Serializer " + name() + " is not type aware");
      }
      return type.get();
    }

    @Override
    public RecipeSerializer<T> getSerializer() {
      return serializer;
    }
  }

  /**
   * Map codec wrapping a {@link RecordLoadable}, bridging between arbitrary dynamic ops and the loadable's JSON form.
   * As Mantle loadables operate on {@link JsonObject}, we convert any map to JSON, deserialize, and on encode
   * serialize to JSON then copy the fields back into the target ops.
   * The parse context contains the registry ops and condition context of the passed ops, see {@link #withOps(TypedMapBuilder, DynamicOps, MapLike)}.
   */
  public static class LoadableMapCodec<T> extends MapCodec<T> {
    private final RecordLoadable<T> loadable;

    public LoadableMapCodec(RecordLoadable<T> loadable) {
      this.loadable = loadable;
    }

    /** Adds additional context for parsing the given JSON */
    protected void addContext(JsonObject json, TypedMapBuilder builder) {}

    /** Called after a value is decoded to allow post processing */
    protected T finish(T value, TypedMap context) {
      return value;
    }

    @Override
    public <O> Stream<O> keys(DynamicOps<O> ops) {
      // we cannot know the keys ahead of time, so return none; vanilla only uses this for compression which we opt out of
      return Stream.empty();
    }

    @Override
    public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
      try {
        JsonObject json = new JsonObject();
        input.entries().forEach(pair -> {
          String key = ops.getStringValue(pair.getFirst()).result().orElse(null);
          if (key != null) {
            json.add(key, ops.convertTo(JsonOps.INSTANCE, pair.getSecond()));
          }
        });
        // build the loadable context fresh for each decode, the real recipe id is not available so the recipe serializer derives
        // a stable synthetic id from the json contents. this gives required context fields (ID, SERIALIZER, TYPE, TYPED_SERIALIZER)
        // a non-null value, keeping recipes that require them loading 1:1 with the original
        TypedMapBuilder builder = withOps(TypedMapBuilder.builder(), ops, input);
        addContext(json, builder);
        TypedMap context = builder.build();
        return DataResult.success(finish(loadable.deserialize(json, context), context));
      } catch (RuntimeException e) {
        return DataResult.error(e::getMessage);
      }
    }

    @Override
    public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
      try {
        JsonObject json = new JsonObject();
        loadable.serialize(input, json);
        for (Entry<String,JsonElement> entry : json.entrySet()) {
          prefix.add(entry.getKey(), JsonOps.INSTANCE.convertTo(ops, entry.getValue()));
        }
      } catch (RuntimeException e) {
        return prefix.withErrorsFrom(DataResult.error(e::getMessage));
      }
      return prefix;
    }
  }
}
