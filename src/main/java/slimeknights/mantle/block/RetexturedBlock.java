package slimeknights.mantle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Logic for a retexturable block. Use alongside {@link IRetexturedBlockEntity} and {@link RetexturedHelper}
 */
@SuppressWarnings("WeakerAccess")
public abstract class RetexturedBlock extends Block implements EntityBlock {
  public RetexturedBlock(Properties properties) {
    super(properties);
  }

  @Override
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(world, pos, state, placer, stack);
    updateTextureBlock(world, pos, stack);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData, Player player) {
    return getPickBlock(world, pos, state);
  }

  /**
   * Adds the texture tooltip for a stack of this block. Since 1.21.5 blocks no longer add item tooltips, so this is called by
   * {@link slimeknights.mantle.item.BlockTooltipItem} for retextured blocks; other block items should call it from {@code appendHoverText}.
   * @param stack    Block item stack
   * @param tooltip  Tooltip consumer
   * @param flag     Tooltip flag
   */
  public static void appendTextureTooltip(ItemStack stack, Consumer<Component> tooltip, TooltipFlag flag) {
    List<Component> lines = new ArrayList<>();
    RetexturedHelper.addTooltip(stack, lines, flag);
    lines.forEach(tooltip);
  }


  /* Utils */

  /**
   * Call in {@link Block#setPlacedBy(Level, BlockPos, BlockState, LivingEntity, ItemStack)} to set the texture tag to the Tile Entity
   * @param world World where the block was placed
   * @param pos   Block position
   * @param stack Item stack
   */
  public static void updateTextureBlock(Level world, BlockPos pos, ItemStack stack) {
    if (!stack.isComponentsPatchEmpty() && world.getBlockEntity(pos) instanceof IRetexturedBlockEntity te) {
      te.updateTexture(RetexturedHelper.getTextureName(stack));
    }
  }

  /**
   * Called in blocks to get the item stack for the current block
   * @param world World
   * @param pos   Pos
   * @param state State
   * @return Pickblock stack with proper NBT
   */
  public static ItemStack getPickBlock(BlockGetter world, BlockPos pos, BlockState state) {
    Block block = state.getBlock();
    ItemStack stack = new ItemStack(block);
    if (world.getBlockEntity(pos) instanceof IRetexturedBlockEntity te) {
      RetexturedHelper.setTexture(stack, te.getTextureName());
    }
    return stack;
  }
}
