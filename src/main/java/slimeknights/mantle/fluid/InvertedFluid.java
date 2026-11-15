package slimeknights.mantle.fluid;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Fluid where up is down and down is up.
 * <p>
 * Lithium compatibility: Lithium {@code @Overwrite}s several private {@link net.minecraft.world.level.material.FlowingFluid}
 * methods (notably {@code canPassThrough} and {@code isWaterHole}) keeping them private, so widening any of them through
 * the access transformer makes its FlowingFluidMixin fail to apply ("cannot reduce visibility of PROTECTED target method").
 * As a result, this class does not rely on any widened vanilla member: every private vanilla helper it needs is
 * reimplemented below as a private helper (inverted where relevant), built only from public and protected API.
 * The vanilla methods calling them ({@link #spread}, {@link #getNewLiquid}, {@link #getSpread}) are all overridden,
 * so the vanilla downward helpers never run for inverted fluids.
 */
public abstract class InvertedFluid extends BaseFlowingFluid {
  /** Matches the vanilla occlusion cache size */
  private static final int CACHE_SIZE = 200;
  /** Local copy of the vanilla occlusion cache, as the vanilla one is private */
  private static final ThreadLocal<Object2ByteLinkedOpenHashMap<BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
    Object2ByteLinkedOpenHashMap<BlockStatePairKey> map = new Object2ByteLinkedOpenHashMap<>(CACHE_SIZE) {
      @Override
      protected void rehash(int newN) {}
    };
    map.defaultReturnValue((byte)127);
    return map;
  });

  protected InvertedFluid(Properties properties) {
    super(properties);
  }

  @Override
  public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluid) {
    double xHeight = 0.0D;
    double zHeight = 0.0D;
    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    for (Direction direction : Direction.Plane.HORIZONTAL) {
      mutable.setWithOffset(pos, direction);
      FluidState sideFluid = level.getFluidState(mutable);
      if (this.affectsFlow(sideFluid)) {
        float sideHeight = sideFluid.getOwnHeight();
        float deltaHeight = 0.0F;
        if (sideHeight == 0.0F) {
          if (!level.getBlockState(mutable).is(BlockTags.BLOCKS_FLUID_FLOW)) {
            BlockPos above = mutable.above();
            FluidState aboveFluid = level.getFluidState(above);
            if (this.affectsFlow(aboveFluid)) {
              sideHeight = aboveFluid.getOwnHeight();
              if (sideHeight > 0.0F) {
                deltaHeight = fluid.getOwnHeight() - sideHeight + 0.8888889F;
              }
            }
          }
        } else if (sideHeight > 0.0F) {
          deltaHeight = fluid.getOwnHeight() - sideHeight;
        }

        if (deltaHeight != 0.0F) {
          xHeight += direction.getStepX() * deltaHeight;
          zHeight += direction.getStepZ() * deltaHeight;
        }
      }
    }

    Vec3 vector = new Vec3(xHeight, 0.0D, zHeight);
    if (fluid.getValue(FALLING)) {
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        mutable.setWithOffset(pos, direction);
        if (this.isSolidFace(level, mutable, direction) || this.isSolidFace(level, mutable.below(), direction)) {
          vector = vector.normalize().add(0.0D, 6.0D, 0.0D);
          break;
        }
      }
    }

    return vector.normalize();
  }

  @Override
  protected boolean isSolidFace(BlockGetter level, BlockPos neighbor, Direction side) {
    BlockState block = level.getBlockState(neighbor);
    FluidState fluid = level.getFluidState(neighbor);
    return !fluid.getType().isSame(this) && (side == Direction.DOWN || !(block.getBlock() instanceof IceBlock) && block.isFaceSturdy(level, neighbor, side));
  }

  @Override
  protected void spread(ServerLevel level, BlockPos pos, BlockState block, FluidState fluid) {
    // recreation that swaps downs for ups
    if (!fluid.isEmpty()) {
      BlockPos above = pos.above();
      BlockState aboveBlock = level.getBlockState(above);
      FluidState aboveFluid = aboveBlock.getFluidState();
      if (this.canMaybePassThrough(level, pos, block, Direction.UP, above, aboveBlock, aboveFluid)) {
        FluidState newAboveFluid = this.getNewLiquid(level, above, aboveBlock);
        Fluid newAboveType = newAboveFluid.getType();
        if (aboveFluid.canBeReplacedWith(level, above, newAboveType, Direction.UP) && canHoldSpecificFluid(level, above, aboveBlock, newAboveType)) {
          this.spreadTo(level, above, aboveBlock, Direction.UP, newAboveFluid);
          if (this.sourceNeighborCount(level, pos) >= 3) {
            this.spreadToSides(level, pos, fluid, block);
          }
          return;
        }
      }

      if (fluid.isSource() || !this.isWaterHole(level, pos, block, above, aboveBlock)) {
        this.spreadToSides(level, pos, fluid, block);
      }
    }
  }

  /** Local reimplementation of the private vanilla {@code FlowingFluid#spreadToSides}, unchanged as it has no vertical logic. */
  private void spreadToSides(ServerLevel level, BlockPos pos, FluidState fluid, BlockState block) {
    int sideAmount = fluid.getAmount() - this.getDropOff(level);
    if (fluid.getValue(FALLING)) {
      sideAmount = 7;
    }

    if (sideAmount > 0) {
      for (Entry<Direction, FluidState> entry : this.getSpread(level, pos, block).entrySet()) {
        Direction direction = entry.getKey();
        BlockPos side = pos.relative(direction);
        this.spreadTo(level, side, level.getBlockState(side), direction, entry.getValue());
      }
    }
  }

  @Override
  protected FluidState getNewLiquid(ServerLevel level, BlockPos pos, BlockState block) {
    int maxSide = 0;
    int sourceSides = 0;
    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPos side = mutable.setWithOffset(pos, direction);
      BlockState sideBlock = level.getBlockState(side);
      FluidState sideFluid = sideBlock.getFluidState();
      if (sideFluid.getType().isSame(this) && canPassThroughWall(direction, level, pos, block, side, sideBlock)) {
        if (sideFluid.isSource() && EventHooks.canCreateFluidSource(level, side, sideBlock)) {
          sourceSides++;
        }
        maxSide = Math.max(maxSide, sideFluid.getAmount());
      }
    }

    if (sourceSides >= 2) {
      BlockState aboveBlock = level.getBlockState(mutable.setWithOffset(pos, Direction.UP));
      FluidState aboveFluid = aboveBlock.getFluidState();
      if (aboveBlock.isSolid() || this.isSourceBlockOfThisType(aboveFluid)) {
        return this.getSource(false);
      }
    }

    BlockPos below = mutable.setWithOffset(pos, Direction.DOWN);
    BlockState belowBlock = level.getBlockState(below);
    FluidState belowFluid = belowBlock.getFluidState();
    if (!belowFluid.isEmpty() && belowFluid.getType().isSame(this) && canPassThroughWall(Direction.DOWN, level, pos, block, below, belowBlock)) {
      return this.getFlowing(8, true);
    }
    int newHeight = maxSide - this.getDropOff(level);
    return newHeight <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(newHeight, false);
  }

  @Override
  protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState block) {
    int minDistance = 1000;
    Map<Direction, FluidState> spread = Maps.newEnumMap(Direction.class);
    SpreadCache cache = null;

    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPos side = pos.relative(direction);
      BlockState sideBlock = level.getBlockState(side);
      FluidState sideFluid = sideBlock.getFluidState();
      if (this.canMaybePassThrough(level, pos, block, direction, side, sideBlock, sideFluid)) {
        FluidState newFluid = this.getNewLiquid(level, side, sideBlock);
        if (canHoldSpecificFluid(level, side, sideBlock, newFluid.getType())) {
          if (cache == null) {
            cache = new SpreadCache(level, pos);
          }
          int distance = cache.isHole(side) ? 0 : this.getInvertedSlopeDistance(level, side, 1, direction.getOpposite(), sideBlock, cache);
          if (distance < minDistance) {
            spread.clear();
          }
          if (distance <= minDistance) {
            if (sideFluid.canBeReplacedWith(level, side, newFluid.getType(), direction)) {
              spread.put(direction, newFluid);
            }
            minDistance = distance;
          }
        }
      }
    }

    return spread;
  }

  /**
   * Inverted version of vanilla {@code FlowingFluid#getSlopeDistance}. The vanilla method takes a {@code SpreadContext}
   * which cannot be constructed outside {@code FlowingFluid} and whose hole check looks down, so this uses its own cache.
   */
  private int getInvertedSlopeDistance(LevelReader level, BlockPos pos, int distance, Direction from, BlockState block, SpreadCache cache) {
    int minSlope = 1000;

    for (Direction direction : Direction.Plane.HORIZONTAL) {
      if (direction != from) {
        BlockPos side = pos.relative(direction);
        BlockState sideBlock = cache.getBlockState(side);
        FluidState sideFluid = sideBlock.getFluidState();
        if (this.canPassThrough(level, this.getFlowing(), pos, block, direction, side, sideBlock, sideFluid)) {
          if (cache.isHole(side)) {
            return distance;
          }
          if (distance < this.getSlopeFindDistance(level)) {
            int slopeDistance = this.getInvertedSlopeDistance(level, side, distance + 1, direction.getOpposite(), sideBlock, cache);
            if (slopeDistance < minSlope) {
              minSlope = slopeDistance;
            }
          }
        }
      }
    }

    return minSlope;
  }


  /* Private vanilla helpers, reimplemented to avoid access transformers (see class javadoc) */

  /** Local reimplementation of vanilla {@code FlowingFluid#affectsFlow} */
  private boolean affectsFlow(FluidState fluid) {
    return fluid.isEmpty() || fluid.getType().isSame(this);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#isSourceBlockOfThisType} */
  private boolean isSourceBlockOfThisType(FluidState fluid) {
    return fluid.getType().isSame(this) && fluid.isSource();
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#sourceNeighborCount} */
  private int sourceNeighborCount(LevelReader level, BlockPos pos) {
    int count = 0;
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      if (this.isSourceBlockOfThisType(level.getFluidState(pos.relative(direction)))) {
        count++;
      }
    }
    return count;
  }

  /**
   * Local reimplementation of vanilla {@code FlowingFluid#isWaterHole} (inverted: checks UP instead of DOWN). As with
   * {@link #canPassThrough}, we deliberately do not widen the vanilla method through the access transformer: Lithium
   * {@code @Overwrite}s isWaterHole with private visibility, and widening it to protected makes Lithium's
   * FlowingFluidMixin fail to apply ("cannot reduce visibility of PROTECTED target method"). InvertedFluid overrides
   * every vanilla method that calls isWaterHole ({@link #spread}, {@link #getSpread}), so this private helper fully
   * replaces it for inverted fluids.
   */
  private boolean isWaterHole(BlockGetter level, BlockPos pos, BlockState block, BlockPos spreadPos, BlockState spreadBlock) {
    // recreation swapping downs for ups
    return canPassThroughWall(Direction.UP, level, pos, block, spreadPos, spreadBlock)
      && (spreadBlock.getFluidState().getType().isSame(this) || canHoldFluid(level, spreadPos, spreadBlock, this.getFlowing()));
  }

  /**
   * Local reimplementation of vanilla {@code FlowingFluid#canPassThrough}. We deliberately do not widen that
   * method through the access transformer: Lithium @Overwrites it with private visibility, and widening it to
   * protected makes Lithium's FlowingFluidMixin fail to apply.
   */
  private boolean canPassThrough(BlockGetter level, Fluid fluid, BlockPos pos, BlockState block, Direction direction, BlockPos spreadPos, BlockState spreadBlock, FluidState spreadFluid) {
    return this.canMaybePassThrough(level, pos, block, direction, spreadPos, spreadBlock, spreadFluid)
      && canHoldSpecificFluid(level, spreadPos, spreadBlock, fluid);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#canMaybePassThrough} */
  private boolean canMaybePassThrough(BlockGetter level, BlockPos pos, BlockState block, Direction direction, BlockPos spreadPos, BlockState spreadBlock, FluidState spreadFluid) {
    return !this.isSourceBlockOfThisType(spreadFluid)
      && canHoldAnyFluid(spreadBlock)
      && canPassThroughWall(direction, level, pos, block, spreadPos, spreadBlock);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#canHoldAnyFluid} */
  private static boolean canHoldAnyFluid(BlockState state) {
    return state.getBlock() instanceof LiquidBlockContainer || state.is(BlockTags.WASHED_AWAY_BY_FLUIDS);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#canHoldFluid} */
  private static boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
    return canHoldAnyFluid(state) && canHoldSpecificFluid(level, pos, state, fluid);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#canHoldSpecificFluid} */
  private static boolean canHoldSpecificFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
    return !(state.getBlock() instanceof LiquidBlockContainer container) || container.canPlaceLiquid(null, level, pos, state, fluid);
  }

  /** Local reimplementation of vanilla {@code FlowingFluid#canPassThroughWall}, including its occlusion cache */
  private static boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState block, BlockPos spreadPos, BlockState spreadBlock) {
    VoxelShape spreadShape = spreadBlock.getCollisionShape(level, spreadPos);
    if (spreadShape == Shapes.block()) {
      return false;
    }
    VoxelShape shape = block.getCollisionShape(level, pos);
    if (shape == Shapes.block()) {
      return false;
    }
    if (shape == Shapes.empty() && spreadShape == Shapes.empty()) {
      return true;
    }

    // cache is only valid if neither shape is dynamic
    Object2ByteLinkedOpenHashMap<BlockStatePairKey> cache = null;
    BlockStatePairKey key = null;
    if (!block.getBlock().hasDynamicShape() && !spreadBlock.getBlock().hasDynamicShape()) {
      cache = OCCLUSION_CACHE.get();
      key = new BlockStatePairKey(block, spreadBlock, direction);
      byte cached = cache.getAndMoveToFirst(key);
      if (cached != 127) {
        return cached != 0;
      }
    }

    boolean result = !Shapes.mergedFaceOccludes(shape, spreadShape, direction);
    if (cache != null) {
      if (cache.size() == CACHE_SIZE) {
        cache.removeLastByte();
      }
      cache.putAndMoveToFirst(key, (byte)(result ? 1 : 0));
    }
    return result;
  }

  /** Key for the occlusion cache, compares states by identity */
  private record BlockStatePairKey(BlockState first, BlockState second, Direction direction) {
    @Override
    public boolean equals(Object o) {
      return o instanceof BlockStatePairKey that && this.first == that.first && this.second == that.second && this.direction == that.direction;
    }

    @Override
    public int hashCode() {
      int result = System.identityHashCode(this.first);
      result = 31 * result + System.identityHashCode(this.second);
      return 31 * result + this.direction.hashCode();
    }
  }

  /** Inverted version of vanilla {@code FlowingFluid.SpreadContext}, caching block states and holes (which look up instead of down) */
  private class SpreadCache {
    private final BlockGetter level;
    private final BlockPos origin;
    private final Short2ObjectMap<BlockState> stateCache = new Short2ObjectOpenHashMap<>();
    private final Short2BooleanMap holeCache = new Short2BooleanOpenHashMap();

    private SpreadCache(BlockGetter level, BlockPos origin) {
      this.level = level;
      this.origin = origin;
    }

    /** Gets the block state at the given position */
    public BlockState getBlockState(BlockPos pos) {
      return this.getBlockState(pos, this.getCacheKey(pos));
    }

    /** Gets the block state at the given position using a precomputed key */
    private BlockState getBlockState(BlockPos pos, short key) {
      return this.stateCache.computeIfAbsent(key, k -> this.level.getBlockState(pos));
    }

    /** Checks if the given position is a hole, i.e. the fluid can flow up from it */
    public boolean isHole(BlockPos pos) {
      return this.holeCache.computeIfAbsent(this.getCacheKey(pos), key -> {
        BlockState state = this.getBlockState(pos, key);
        BlockPos above = pos.above();
        return InvertedFluid.this.isWaterHole(this.level, pos, state, above, this.level.getBlockState(above));
      });
    }

    /** Gets the cache key for the given position relative to the origin */
    private short getCacheKey(BlockPos pos) {
      int relativeX = pos.getX() - this.origin.getX();
      int relativeZ = pos.getZ() - this.origin.getZ();
      return (short)((relativeX + 128 & 0xFF) << 8 | relativeZ + 128 & 0xFF);
    }
  }

  public static class Flowing extends InvertedFluid {
    public Flowing(Properties properties) {
      super(properties);
      this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 7));
    }

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
      super.createFluidStateDefinition(builder);
      builder.add(LEVEL);
    }

    @Override
    public int getAmount(FluidState state) {
      return state.getValue(LEVEL);
    }

    @Override
    public boolean isSource(FluidState state) {
      return false;
    }
  }

  public static class Source extends InvertedFluid {
    public Source(Properties properties) {
      super(properties);
    }

    @Override
    public int getAmount(FluidState state) {
      return 8;
    }

    @Override
    public boolean isSource(FluidState state) {
      return true;
    }
  }
}
