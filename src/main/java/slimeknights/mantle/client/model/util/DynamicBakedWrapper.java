package slimeknights.mantle.client.model.util;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

import java.util.List;

/**
 * Cross between {@link DelegateBlockStateModel} and {@link DynamicBlockStateModel}.
 * Used to create a block state model wrapper that has dynamic parts based on the world, while delegating particles and material flags to the original model.
 * Implementations must return a non-null key from {@link #createGeometryKey(BlockAndTintGetter, BlockPos, BlockState, RandomSource)} covering every input to
 * {@link #collectParts(BlockAndTintGetter, BlockPos, BlockState, RandomSource, List)}, as both run on meshing threads.
 * @param <T>  Block state model parent
 */
@SuppressWarnings("WeakerAccess")
public abstract class DynamicBakedWrapper<T extends BlockStateModel> implements DynamicBlockStateModel {
  protected final T originalModel;

  protected DynamicBakedWrapper(T originalModel) {
    this.originalModel = originalModel;
  }

  @Override
  public abstract Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random);

  @Override
  public abstract void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts);

  @Deprecated
  @Override
  public Material.Baked particleMaterial() {
    return originalModel.particleMaterial();
  }

  @Override
  public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return originalModel.particleMaterial(level, pos, state);
  }

  @Deprecated
  @Override
  @BakedQuad.MaterialFlags
  public int materialFlags() {
    return originalModel.materialFlags();
  }

  @Override
  @BakedQuad.MaterialFlags
  public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return originalModel.materialFlags(level, pos, state);
  }
}
