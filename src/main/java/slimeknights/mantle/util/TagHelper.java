package slimeknights.mantle.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Helpers to aid in reading and writing of NBT
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TagHelper {
  /* BlockPos */

  /** @deprecated use {@link CompoundTag#store(String, com.mojang.serialization.Codec, Object)} with {@link BlockPos#CODEC} */
  @Deprecated(forRemoval = true)
  public static CompoundTag writePos(BlockPos pos) {
    CompoundTag tag = new CompoundTag();
    tag.putInt("X", pos.getX());
    tag.putInt("Y", pos.getY());
    tag.putInt("Z", pos.getZ());
    return tag;
  }

  /** @deprecated use {@link CompoundTag#read(String, com.mojang.serialization.Codec)} with {@link BlockPos#CODEC} */
  @Nullable
  @Deprecated(forRemoval = true)
  public static BlockPos readPos(CompoundTag tag) {
    // numeric getters return empty if the tag is missing or not a number
    Optional<Integer> x = tag.getInt("X");
    Optional<Integer> y = tag.getInt("Y");
    Optional<Integer> z = tag.getInt("Z");
    if (x.isPresent() && y.isPresent() && z.isPresent()) {
      return new BlockPos(x.get(), y.get(), z.get());
    }
    return null;
  }
}
