package slimeknights.mantle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.util.TranslationHelper;

import javax.annotation.Nullable;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
import static slimeknights.mantle.util.TranslationHelper.COMMA_FORMAT;

/**
 * Decorative block to place on the side of a tank, reads fluid value.
 * @see slimeknights.mantle.datagen.MantleTags.Blocks#ATTACHED_GAUGES
 */
public class GaugeBlock extends Block {
  private static final String CAPACITY_KEY = Mantle.makeDescriptionId("gui", "fluid.capacity");
  private static final String CONTENTS_KEY = Mantle.makeDescriptionId("gui", "fluid.contents");
  private static final String CONTENTS_FORMAT = Mantle.makeDescriptionId("gui", "fluid.format");

  private static final VoxelShape[] BOUNDS = {
    box( 4,15,  4, 12, 16, 12), // D
    box( 4, 0,  4, 12,  1, 12), // U
    box( 4, 4, 15, 12, 12, 16), // N
    box( 4, 4,  0, 12, 12,  1), // S
    box(15, 4,  4, 16, 12, 12), // W
    box( 0, 4,  4,  1, 12, 12)  // E
  };

  public GaugeBlock(Properties builder) {
    super(builder);
    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
  }


  /* Behavior */

  /** Formats the capacity tooltip */
  public static MutableComponent formatCapacity(int capacity) {
    return Component.translatable(CAPACITY_KEY, TranslationHelper.COMMA_FORMAT.format(capacity));
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    // display adjacent tank contents
    if (!world.isClientSide()) {
      Direction side = state.getValue(FACING);
      BlockPos neighbor = pos.relative(side.getOpposite());
      BlockEntity te = world.getBlockEntity(neighbor);
      if (te != null) {
        ResourceHandler<FluidResource> handler = world.getCapability(Capabilities.Fluid.BLOCK, neighbor, side);
        if (handler == null) {
          handler = EmptyResourceHandler.instance();
        }
        if (handler.size() > 0) {
          FluidStack fluid = FluidUtil.getStack(handler, 0);
          int capacity = handler.getCapacityAsInt(0, FluidResource.EMPTY);
          if (fluid.isEmpty()) {
            // show simple empty message if gauge amount is hidden
            player.sendOverlayMessage(formatCapacity(capacity));
          } else {
            Component contents = Component.translatable(CONTENTS_FORMAT, COMMA_FORMAT.format(fluid.getAmount()), COMMA_FORMAT.format(capacity), fluid.getHoverName());
            player.sendOverlayMessage(Component.translatable(CONTENTS_KEY, contents));
          }
        }
      }
    }

    return InteractionResult.SUCCESS;
  }


  /* Visuals */

  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
    return BOUNDS[state.getValue(FACING).get3DDataValue()];
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
    Direction direction = state.getValue(FACING);
    BlockPos neighbor = pos.relative(direction.getOpposite());
    if (!(world instanceof Level level)) {
      return world.getBlockEntity(neighbor) != null;
    }
    return level.getCapability(Capabilities.Fluid.BLOCK, neighbor, direction) != null;
  }

  @Override
  @Nullable
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = this.defaultBlockState();
    LevelReader world = context.getLevel();
    BlockPos pos = context.getClickedPos();
    Direction[] nearestDir = context.getNearestLookingDirections();
    for (Direction direction : nearestDir) {
      state = state.setValue(FACING, direction.getOpposite());
      if (state.canSurvive(world, pos)) {
        return state;
      }
    }

    return null;
  }

  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  protected BlockState updateShape(BlockState state, LevelReader worldIn, ScheduledTickAccess ticks, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
    return facing.getOpposite() == state.getValue(FACING) && !state.canSurvive(worldIn, currentPos) ? Blocks.AIR.defaultBlockState() : state;
  }

  @Override
  @SuppressWarnings("deprecation")
  @Deprecated
  public BlockState rotate(BlockState state, Rotation rot) {
    return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
  }

  @Override
  @SuppressWarnings("deprecation")
  @Deprecated
  public BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(FACING)));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }
}
