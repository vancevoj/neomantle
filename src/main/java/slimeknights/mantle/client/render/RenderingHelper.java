package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class RenderingHelper {
  /* Rotation */

  /**
   * Applies horizontal rotation to the given TESR
   * @param matrices  Matrix stack
   * @param state     Block state, checked for {@link BlockStateProperties#HORIZONTAL_FACING}
   * @return  True if rotation was applied. Caller is expected to call {@link PoseStack#popPose()} if true
   */
  public static boolean applyRotation(PoseStack matrices, BlockState state) {
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      return applyRotation(matrices, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }
    return false;
  }

  /**
   * Applies horizontal rotation to the given TESR
   * @param matrices  Matrix stack
   * @param facing    Direction of rotation
   * @return  True if rotation was applied. Caller is expected to call {@link PoseStack#popPose()} if true
   */
  public static boolean applyRotation(PoseStack matrices, Direction facing) {
    // south has a facing of 0, no rotation needed
    if (facing.getAxis().isHorizontal() && facing != Direction.SOUTH) {
      matrices.pushPose();
      matrices.translate(0.5, 0, 0.5);
      matrices.rotate(Axis.YP.rotationDegrees(-90f * (facing.get2DDataValue())));
      matrices.translate(-0.5, 0, -0.5);
      return true;
    }
    return false;
  }


  /* Items */

  /**
   * Resolves the item model for a single item in a block entity renderer. Call during render state extraction and store
   * the item state in the render state, then submit it with {@link #renderItem(PoseStack, SubmitNodeCollector, ItemStackRenderState, RenderItem, int)}.
   * @param resolver    Item model resolver, from {@link net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context#itemModelResolver()}
   * @param itemState   Item state to fill, cleared if nothing should render
   * @param item        Item to render
   * @param renderItem  Render item for render information
   * @param level       Level containing the block entity
   * @param seed        Seed for random item models
   */
  public static void extractItem(ItemModelResolver resolver, ItemStackRenderState itemState, ItemStack item, RenderItem renderItem, @Nullable Level level, int seed) {
    if (renderItem.isHidden()) {
      itemState.clear();
    } else {
      resolver.updateForTopItem(itemState, item, renderItem.getTransform(), level, null, seed);
    }
  }

  /**
   * Submits a single item in a block entity renderer
   * @param matrices    Matrix stack instance
   * @param collector   Submit node collector
   * @param item        Item state to render, see {@link #extractItem(ItemModelResolver, ItemStackRenderState, ItemStack, RenderItem, Level, int)}
   * @param renderItem  Render item for render information
   * @param light       Model light
   */
  public static void renderItem(PoseStack matrices, SubmitNodeCollector collector, ItemStackRenderState item, RenderItem renderItem, int light) {
    // if the item says skip, skip
    if (renderItem.isHidden()) return;
    // if no stack, skip
    if (item.isEmpty()) return;

    // start rendering
    matrices.pushPose();
    Vector3f center = renderItem.getCenterScaled();
    matrices.translate(center.x(), center.y(), center.z());

    // scale
    float scale = renderItem.getSizeScaled();
    matrices.scale(scale, scale, scale);

    // rotate X, then Y
    float x = renderItem.getX();
    if (x != 0) {
      matrices.rotate(Axis.XP.rotationDegrees(x));
    }
    float y = renderItem.getY();
    if (y != 0) {
      matrices.rotate(Axis.YP.rotationDegrees(y));
    }

    // render the actual item
    item.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, 0);
    matrices.popPose();
  }

  /**
   * Submits a single item in a block entity renderer, resolving its model immediately using the client level.
   * Prefer resolving the item during extraction with {@link #extractItem(ItemModelResolver, ItemStackRenderState, ItemStack, RenderItem, Level, int)}.
   * @param matrices    Matrix stack instance
   * @param collector   Submit node collector
   * @param item        Item to render
   * @param renderItem  Render item for render information
   * @param light       Model light
   */
  public static void renderItem(PoseStack matrices, SubmitNodeCollector collector, ItemStack item, RenderItem renderItem, int light) {
    // if the item says skip, skip
    if (renderItem.isHidden()) return;
    // if no stack, skip
    if (item.isEmpty()) return;

    Minecraft minecraft = Minecraft.getInstance();
    ItemStackRenderState itemState = new ItemStackRenderState();
    extractItem(minecraft.getItemModelResolver(), itemState, item, renderItem, minecraft.level, 0);
    renderItem(matrices, collector, itemState, renderItem, light);
  }

  /**
   * Renders faucet fluids at the relevant location
   * @param world       World instance
   * @param pos         Base position
   * @param direction   Direction to render
   * @param pose        Pose to render with, typically from {@link SubmitNodeCollector.CustomGeometryRenderer}
   * @param buffer      Vertex consumer for {@link MantleRenderTypes#FLUID}
   * @param still       Still fluid texture
   * @param flowing     Flowing fluid texture
   * @param color       Color to tint fluid
   * @param light       Fluid light value
   */
  public static void renderFaucetFluids(LevelAccessor world, BlockPos pos, Direction direction, PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    int i = 0;
    FaucetFluid faucetFluid;
    do {
      // get the faucet data for the block
      i++;
      faucetFluid = FaucetFluid.REGISTRY.get(world.getBlockState(pos.below(i)));
      // if nothing to render, skip the pose work, but allow the next iteration of the loop on the off chance they continue
      List<FluidCuboid> cubes = faucetFluid.getFluids(direction);
      if (cubes.isEmpty()) continue;

      // render all down cubes with the given offset
      PoseStack.Pose offset = pose.copy();
      offset.translate(0, -i, 0);
      for (FluidCuboid cube : cubes) {
        FluidRenderer.renderCuboid(offset, buffer, cube, still, flowing, cube.getFromScaled(), cube.getToScaled(), color, light);
      }
    } while (faucetFluid.isContinued());
  }

  /**
   * Renders faucet fluids at the relevant location
   * @param world       World instance
   * @param pos         Base position
   * @param direction   Direction to render
   * @param matrices    Matrix instance
   * @param buffer      Builder instance
   * @param still       Still fluid texture
   * @param flowing     Flowing fluid texture
   * @param color       Color to tint fluid
   * @param light       Fluid light value
   */
  public static void renderFaucetFluids(LevelAccessor world, BlockPos pos, Direction direction, PoseStack matrices, VertexConsumer buffer, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    renderFaucetFluids(world, pos, direction, matrices.last(), buffer, still, flowing, color, light);
  }
}
