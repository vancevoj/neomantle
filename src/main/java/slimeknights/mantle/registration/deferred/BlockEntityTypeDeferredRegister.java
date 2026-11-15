package slimeknights.mantle.registration.deferred;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.object.EnumObject;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Deferred register to register tile entity instances
 */
@SuppressWarnings("unused")  // API
public class BlockEntityTypeDeferredRegister extends DeferredRegisterWrapper<BlockEntityType<?>> {
  public BlockEntityTypeDeferredRegister(String modID) {
    super(Registries.BLOCK_ENTITY_TYPE, modID);
  }

  /**
   * Registers a tile entity type for a single block
   * @param name     Tile entity name
   * @param factory  Tile entity factory
   * @param block    Single block to add
   * @param <T>      Tile entity type
   * @return  Registry object instance
   */
  public <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String name, BlockEntitySupplier<? extends T> factory, Supplier<? extends Block> block) {
    return register.register(name, () -> new BlockEntityType<T>(factory, Set.of(block.get())));
  }

  /**
   * Registers a new tile entity type using a tile entity factory and a block supplier
   * @param name     Tile entity name
   * @param factory  Tile entity factory
   * @param blocks   Enum object
   * @param <T>      Tile entity type
   * @return  Tile entity type registry object
   */
  public <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String name, BlockEntitySupplier<? extends T> factory, EnumObject<?, ? extends Block> blocks) {
    return register.register(name, () -> new BlockEntityType<T>(factory, ImmutableSet.copyOf(blocks.values())));
  }

  /**
   * Registers a new tile entity type using a tile entity factory and a block supplier
   * @param name             Tile entity name
   * @param factory          Tile entity factory
   * @param blockCollector   Function to get block list
   * @param <T>              Tile entity type
   * @return  Tile entity type registry object
   */
  public <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String name, BlockEntitySupplier<? extends T> factory, Consumer<ImmutableSet.Builder<Block>> blockCollector) {
    return register.register(name, () ->  {
      ImmutableSet.Builder<Block> blocks = ImmutableSet.builder();
      blockCollector.accept(blocks);
      return new BlockEntityType<T>(factory, blocks.build());
    });
  }
}
