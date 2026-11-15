package slimeknights.mantle.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.util.BlockEntityHelper;

import javax.annotation.Nullable;

/**
 * Helper to create a packet that updates a block entity on the receiving side.
 * <p>
 * 1.21: the block entity class getter is named {@link #blockEntityType()} as {@code type()} is taken by {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload#type()}.
 * @param <T> class type receiving updates.
 */
public interface BlockEntityPacket<T> extends IThreadsafePacket {
  /** Gets the block position for this packet */
  BlockPos pos();

  /** Gets the class for the block entity filter */
  Class<T> blockEntityType();

  @Override
  default void handleThreadsafe(IPayloadContext context) {
    BlockPos pos = pos();
    BlockEntity be = getBlockEntity(context, pos, this);
    if (be != null) {
      Class<T> type = blockEntityType();
      if (type.isInstance(be)) {
        handleBlockEntity(context, type.cast(be));
      } else {
        Mantle.logger.error("Failed to handle packet {}: Block entity type mismatch at {}, expected {}, found {}", this, pos, type, be.getClass());
      }
    } else {
      Mantle.logger.error("Failed to handle packet {}: No block entity at {}", this, pos);
    }
  }

  /** Handles the block entity, assuming it's not null and the correct type */
  void handleBlockEntity(IPayloadContext context, T be);


  /* Helpers */

  /**
   * Gets a block entity in a packet, ensuring the world is loaded before attempting.
   * @param world   World instance.
   * @param pos     Position
   * @param packet  Object to print for debug
   * @return Block entity instance. Null if: world null, position not loaded, or block entity does not exist.
   * @see BlockEntityHelper#getLoaded(BlockGetter, BlockPos)
   */
  @Nullable
  static BlockEntity getBlockEntity(@Nullable BlockGetter world, BlockPos pos, Object packet) {
    if (BlockEntityHelper.isBlockLoaded(world, pos)) {
      return world.getBlockEntity(pos);
    }
    Mantle.logger.error("Failed to handle packet {}: World is not loaded at {}", packet, pos);
    return null;
  }

  /**
   * Gets a block entity in a packet using the level of the context player, so it works on both sides.
   * Falls back to the client level if the context has no player.
   * @param context Packet context
   * @param pos     Position
   * @param packet  Object to print for debug
   * @return Block entity instance. Null if: no level, position not loaded, or block entity does not exist.
   */
  @Nullable
  static BlockEntity getBlockEntity(IPayloadContext context, BlockPos pos, Object packet) {
    Player player = context.player();
    //noinspection ConstantValue  defensive, player should never be null in play packets
    return getBlockEntity(player != null ? player.level() : SafeClientAccess.getLevel(), pos, packet);
  }

  /**
   * Gets a block entity in a packet client side, ensuring the world is loaded before attempting. Only works on the client side.
   * @param pos     Position
   * @param packet  Object to print for debug
   * @return Block entity instance. Null if: not client, position not loaded, or block entity does not exist.
   */
  @Nullable
  static BlockEntity getBlockEntity(BlockPos pos, Object packet) {
    return getBlockEntity(SafeClientAccess.getLevel(), pos, packet);
  }
}
