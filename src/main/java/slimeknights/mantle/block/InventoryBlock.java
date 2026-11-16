package slimeknights.mantle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import slimeknights.mantle.block.entity.INameableMenuProvider;
import slimeknights.mantle.block.entity.InventoryBlockEntity;
import slimeknights.mantle.inventory.BaseContainerMenu;

import javax.annotation.Nullable;

/**
 * Base class for blocks with an inventory
 */
@SuppressWarnings("WeakerAccess")
public abstract class InventoryBlock extends Block implements EntityBlock {

  protected InventoryBlock(BlockBehaviour.Properties builder) {
    super(builder);
  }

  /**
   * Called when the block is activated to open the UI. Override to return false for blocks with no inventory
   * @param player Player instance
   * @param world  World instance
   * @param pos    Block position
   * @return true if the GUI opened, false if not
   */
  protected boolean openGui(Player player, Level world, BlockPos pos) {
    if (!world.isClientSide()) {
      MenuProvider container = this.getMenuProvider(world.getBlockState(pos), world, pos);
      if (container != null && player instanceof ServerPlayer serverPlayer) {
        serverPlayer.openMenu(container, pos);
        if (player.containerMenu instanceof BaseContainerMenu<?> menu) {
          menu.syncOnOpen(serverPlayer);
        }
      }
    }

    return true;
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult rayTraceResult) {
    // mirrors vanilla ChestBlock/AbstractFurnaceBlock: no bounce-suppression gate (block placement already ran in
    // useItemOn), and consume on the server so the menu open is not double counted
    if (!world.isClientSide()) {
      return this.openGui(player, world, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    return InteractionResult.SUCCESS;
  }


  /* Naming */

  @Override
  public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(worldIn, pos, state, placer, stack);

    // set custom name from named stack
    if (stack.has(DataComponents.CUSTOM_NAME)) {
      if (worldIn.getBlockEntity(pos) instanceof INameableMenuProvider provider) {
        provider.setCustomName(stack.getHoverName());
      }
    }
  }

  @Override
  @Nullable
  protected MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
    return worldIn.getBlockEntity(pos) instanceof MenuProvider menu ? menu : null;
  }


  /* Inventory handling */

  /**
   * Drops the inventory of the given block entity when it is removed. Called by {@link slimeknights.mantle.block.entity.MantleBlockEntity#preRemoveSideEffects(BlockPos, BlockState)},
   * as since 1.21.5 blocks no longer get a removal hook while the block entity still exists. Block entities not extending {@code MantleBlockEntity} use the vanilla
   * behavior of dropping their contents if they are a {@link net.minecraft.world.Container}.
   * @param state   Block state being removed
   * @param level   Level instance
   * @param pos     Block position
   * @param te      Block entity being removed
   */
  public void onBlockEntityRemoved(BlockState state, Level level, BlockPos pos, BlockEntity te) {
    // by the time the block entity is removed, the chunk already stores the new state at pos, so
    // level.getCapability(Item.BLOCK, pos, null) would look up providers for the new block (typically air, no provider)
    // and return null. Read the handler of an InventoryBlockEntity directly; block entities that are not an
    // InventoryBlockEntity only expose their items through the capability, so query it with the old state and block
    // entity passed explicitly, which skips the world lookup (neotinkers #25).
    ResourceHandler<ItemResource> inventory = te instanceof InventoryBlockEntity inv
                                              ? inv.getItemHandler()
                                              : level.getCapability(Capabilities.Item.BLOCK, pos, state, te, null);
    if (inventory != null) {
      dropInventoryItems(state, level, pos, inventory);
    }
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    Containers.updateNeighboursAfterDestroy(state, level, pos);
  }

  /**
   * Called when the block is replaced to drop contained items.
   * @param state       Block state
   * @param worldIn     Tile world
   * @param pos         Tile position
   * @param inventory   Item handler
   */
  @SuppressWarnings("unused") // API
  protected void dropInventoryItems(BlockState state, Level worldIn, BlockPos pos, ResourceHandler<ItemResource> inventory) {
    dropInventoryItems(worldIn, pos, inventory);
  }

  /**
   * Drops all items from the given inventory in world
   * @param world      World instance
   * @param pos        Position to drop
   * @param inventory  Inventory instance
   */
  public static void dropInventoryItems(Level world, BlockPos pos, ResourceHandler<ItemResource> inventory) {
    double x = pos.getX();
    double y = pos.getY();
    double z = pos.getZ();
    for(int i = 0; i < inventory.size(); ++i) {
      ItemStack stack = ItemUtil.getStack(inventory, i);
      if (!stack.isEmpty()) {
        Containers.dropItemStack(world, x, y, z, stack);
      }
    }
  }

  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public boolean triggerEvent(BlockState state, Level worldIn, BlockPos pos, int id, int param) {
    super.triggerEvent(state, worldIn, pos, id, param);
    BlockEntity be = worldIn.getBlockEntity(pos);
    return be != null && be.triggerEvent(id, param);
  }
}
