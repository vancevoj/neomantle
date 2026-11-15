package slimeknights.mantle.block;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.registries.datamaps.builtin.Transformable;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Log block that can be stripped.
 * <p>
 * Since NeoForge 26.3, vanilla axes strip blocks using the {@code neoforge:transformables} block data map instead of asking the block,
 * so each stripped log needs a data map entry, created in datagen using {@link #getTransformable()}.
 */
public class StrippableLogBlock extends RotatedPillarBlock {
  /** Former NeoForge item ability for stripping, NeoForge no longer defines it but tools from other mods may still use it with {@link #getToolModifiedState(BlockState, UseOnContext, ItemAbility, boolean)} */
  public static final ItemAbility AXE_STRIP = ItemAbility.get("axe_strip");

  private final Supplier<? extends Block> stripped;
  public StrippableLogBlock(Supplier<? extends Block> stripped, Properties properties) {
    super(properties);
    this.stripped = stripped;
  }

  /** Gets the block this log turns into when stripped */
  public Block getStripped() {
    return stripped.get();
  }

  /** Gets the data map entry making vanilla axes strip this log, for use in a data map provider for {@code NeoForgeDataMaps.TRANSFORMABLES} */
  public Transformable getTransformable() {
    return Transformable.stripping(this, getStripped());
  }

  @Nullable
  @Override
  public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
    if (itemAbility == AXE_STRIP) {
      return getStripped().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
    }
    return super.getToolModifiedState(state, context, itemAbility, simulate);
  }
}
