package slimeknights.mantle.block.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;
import slimeknights.mantle.block.InventoryBlock;

import javax.annotation.Nullable;

public class MantleBlockEntity extends BlockEntity {
  private static final Logger LOGGER = LogUtils.getLogger();

  public MantleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  public boolean isClient() {
    return this.getLevel() != null && this.getLevel().isClientSide();
  }

  /**
   * Marks the chunk dirty without performing comparator updates (twice!!) or block state checks
   * Used since most of our markDirty calls only adjust TE data
   */
  @SuppressWarnings("deprecation")
  public void setChangedFast() {
    if (level != null) {
      if (level.hasChunkAt(worldPosition)) {
        level.getChunkAt(worldPosition).markUnsaved();
      }
    }
  }


  /* Removal */

  /**
   * Vanilla drops the contents of any {@link net.minecraft.world.Container} here. Mantle block entities instead only drop their contents
   * when their block is an {@link InventoryBlock}, matching the removal behavior Mantle had before 1.21.5. Override to drop contents for other blocks.
   */
  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    if (level != null && state.getBlock() instanceof InventoryBlock inventoryBlock) {
      inventoryBlock.onBlockEntityRemoved(state, level, pos, this);
    }
  }


  /* Syncing */

  /**
   * If true, this TE syncs when {@link net.minecraft.world.level.Level#blockUpdated(BlockPos, Block) is called
   * Syncs data from {@link #saveSynced(ValueOutput)}
   */
  protected boolean shouldSyncOnUpdate() {
    return false;
  }

  @Override
  @Nullable
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    // number is just used for vanilla, -1 ensures it skips all instanceof checks as its not a vanilla TE
    return shouldSyncOnUpdate() ? ClientboundBlockEntityDataPacket.create(this) : null;
  }

  /**
   * Write data that is synced to the client in {@link #getUpdateTag(HolderLookup.Provider)} and saved in {@link #saveAdditional(ValueOutput)}
   * @param output  Output for the data, registry aware
   */
  protected void saveSynced(ValueOutput output) {}

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
      TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
      saveSynced(output);
      return output.buildResult();
    }
  }

  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    saveSynced(output);
  }
}
