package slimeknights.mantle.mixin;

import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.mantle.Mantle;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Compatibility patch for extensible enums.
 * <p>
 * {@link StringRepresentable#createNameLookup} builds its name -> value map with a two-argument
 * {@link Collectors#toMap} that has no merge function, so it throws {@code IllegalStateException: Duplicate key}
 * if two enum constants share a serialized name. Vanilla enums never collide, but NeoForge's extensible enums let
 * mods add constants, and a buggy mod can register two with the same serialized name. When that enum is one
 * Tinkers touches early (e.g. {@code ItemDisplayContext}), the whole game fails to load.
 * <p>
 * Example: Petrolpark's Library registers two {@code ItemDisplayContext} values that both serialize to
 * {@code petrolpark:belt}, which hard-crashes the client on startup (see neotinkers issue #5).
 * <p>
 * This redirect swaps in a merge function that keeps the first value and logs a warning, so a duplicate name
 * degrades gracefully instead of crashing. It only changes behavior when a collision actually exists; with the
 * unique serialized names every well-behaved enum has, the merge function never runs.
 * <p>
 * Since 26.x there are two {@code createNameLookup} overloads and only the two argument one builds the map (for enums with more than 16 values),
 * so the target method is given with its full descriptor.
 */
@Mixin(StringRepresentable.class)
public interface StringRepresentableMixin {
  @Redirect(
    method = "createNameLookup([Ljava/lang/Object;Ljava/util/function/Function;)Ljava/util/function/Function;",
    at = @At(value = "INVOKE", target = "Ljava/util/stream/Collectors;toMap(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/stream/Collector;"),
    require = 0)
  private static <T, K, U> Collector<T, ?, Map<K, U>> mantle$tolerateDuplicateSerializedNames(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
    return Collectors.toMap(keyMapper, valueMapper, (first, second) -> {
      Mantle.logger.warn("Two values collide on the same serialized name in an extensible enum (keeping {}, ignoring {}). This is a bug in whichever mod registered the duplicate value; Neo Mantle keeps the game alive instead of crashing.", first, second);
      return first;
    });
  }
}
