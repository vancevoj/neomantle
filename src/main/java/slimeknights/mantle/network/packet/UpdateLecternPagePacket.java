package slimeknights.mantle.network.packet;

import lombok.AllArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.BookHelper;
import slimeknights.mantle.util.BlockEntityHelper;

/**
 * Packet to update the book page in a lectern
 */
@AllArgsConstructor
public class UpdateLecternPagePacket implements IThreadsafePacket {
  private final BlockPos pos;
  private final String page;
  public UpdateLecternPagePacket(FriendlyByteBuf buffer) {
    this.pos = buffer.readBlockPos();
    this.page = buffer.readUtf(100);
  }

  @Override
  public void encode(FriendlyByteBuf buf) {
    buf.writeBlockPos(pos);
    buf.writeUtf(page);
  }

  @Override
  public void handleThreadsafe(Context context) {
    Player player = context.getSender();
    if (player != null && this.page != null) {
      Level world = player.getCommandSenderWorld();
      if (BlockEntityHelper.isBlockLoaded(world, pos)) {
        if (world.getBlockEntity(pos) instanceof LecternBlockEntity te) {
          ItemStack stack = te.getBook();
          if (!stack.isEmpty()) {
            BookHelper.writeSavedPageToBook(stack, this.page);
          }
        } else {
          Mantle.logger.error("Failed to find lectern at {} to update page for {}.", pos, player.getScoreboardName());
        }
      } else {
        Mantle.logger.error("Attempted to update lectern page at {} for {}, but world is not loaded", pos, player.getScoreboardName());
      }
    }
  }
}
