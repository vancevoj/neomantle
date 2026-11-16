package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Block entity renderer displaying the contents of an inventory using the locations from {@link RenderItem#STATE_REGISTRY}.
 * <p>
 * 26.3: block entity renderers extract a render state, then submit it. Items are resolved into {@link ItemStackRenderState}s
 * during extraction, so nothing reads the block entity while submitting.
 */
public class InventoryBlockEntityRenderer<T extends BlockEntity & Container> implements BlockEntityRenderer<T, InventoryBlockEntityRenderer.State> {
  /** Resolver for item models */
  protected final ItemModelResolver itemModelResolver;

  public InventoryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  /**
   * In 1.21 {@code getRenderBoundingBox} moved from {@link BlockEntity} onto the renderer. Block entities rendered by this
   * renderer may enlarge their cull box (e.g. tables displaying tall items floating above) by implementing
   * {@link IRenderBoundingBox}; delegate to that here so the contents are not culled early.
   */
  @Override
  public AABB getRenderBoundingBox(T blockEntity) {
    if (blockEntity instanceof IRenderBoundingBox bounded) {
      return bounded.getRenderBoundingBox();
    }
    return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity);
  }

  /** Gets the render items for the given block state */
  protected List<RenderItem> getRenderItems(BlockState state) {
    return RenderItem.STATE_REGISTRY.get(state, List.of());
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public void extractRenderState(T inventory, State state, float partialTicks, Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(inventory, state, partialTicks, cameraPosition, breakProgress);
    state.items.clear();
    if (inventory.isEmpty()) {
      state.renderItems = List.of();
      return;
    }

    // first, find the model for item display locations
    BlockState blockState = inventory.getBlockState();
    state.blockState = blockState;
    state.renderItems = getRenderItems(blockState);
    // resolve item models
    int size = inventory.getContainerSize();
    for (int i = 0; i < state.renderItems.size(); i++) {
      ItemStackRenderState itemState = new ItemStackRenderState();
      if (i < size) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty()) {
          RenderingHelper.extractItem(itemModelResolver, itemState, stack, state.renderItems.get(i), inventory.getLevel(), 0);
        }
      }
      state.items.add(itemState);
    }
  }

  @Override
  public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    List<RenderItem> renderItems = state.renderItems;
    if (!renderItems.isEmpty()) {
      // if the block is rotatable, rotate item display
      boolean isRotated = RenderingHelper.applyRotation(matrices, state.blockState);

      // render items
      for (int i = 0; i < renderItems.size() && i < state.items.size(); i++) {
        RenderingHelper.renderItem(matrices, collector, state.items.get(i), renderItems.get(i), state.lightCoords);
      }

      // pop back rotation
      if (isRotated) {
        matrices.popPose();
      }
    }
  }

  /**
   * Renders the contents even when the block itself is off screen, as items may extend past the block.
   * 26.3 decides this once per renderer instead of per block entity; empty inventories submit nothing.
   */
  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  /** Render state for an inventory block entity */
  public static class State extends BlockEntityRenderState {
    /** Block state of the inventory, used for rotation */
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    /** Item display locations */
    public List<RenderItem> renderItems = List.of();
    /** Resolved items, one per entry in {@link #renderItems} */
    public final List<ItemStackRenderState> items = new ArrayList<>();
  }
}
