package slimeknights.mantle.client.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** @deprecated use {@link InventoryBlockEntityRenderer} for the new render item registry. */
@Deprecated(forRemoval = true)
public class InventoryTileEntityRenderer<T extends BlockEntity & Container> extends InventoryBlockEntityRenderer<T> {
  public InventoryTileEntityRenderer(BlockEntityRendererProvider.Context context) {
    super(context);
  }

  @SuppressWarnings("removal")
  @Override
  protected List<RenderItem> getRenderItems(BlockState state) {
    return RenderItem.REGISTRY.get(state.getBlock(), List.of());
  }
}
