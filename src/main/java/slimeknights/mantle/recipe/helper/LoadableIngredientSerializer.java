package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMapBuilder;

/**
 * Helper building the {@link IngredientType} pieces (codec + stream codec) for a custom ingredient from a {@link RecordLoadable}.
 * @param <T>  Custom ingredient type
 */
public record LoadableIngredientSerializer<T extends ICustomIngredient>(RecordLoadable<T> loadable) {
  /** Builds a map codec bridging the loadable to the dynamic ops form vanilla expects */
  public MapCodec<T> mapCodec() {
    // custom ingredients do not need recipe context (no ID/SERIALIZER fields), but receive the registry context from the ops for nested tags
    return new LoadableRecipeSerializer.LoadableMapCodec<>(loadable);
  }

  /** Builds a stream codec from the loadable */
  public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    return StreamCodec.of(
      (buf, val) -> loadable.encode(buf, val),
      buf -> loadable.decode(buf, TypedMapBuilder.builder().put(ContextKey.REGISTRIES, buf.registryAccess()).build()));
  }

  /** Builds the ingredient type for registration */
  public IngredientType<T> type() {
    return new IngredientType<>(mapCodec(), streamCodec());
  }
}
