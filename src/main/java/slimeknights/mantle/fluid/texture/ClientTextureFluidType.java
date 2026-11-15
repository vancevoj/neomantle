package slimeknights.mantle.fluid.texture;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector4f;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.mantle.fluid.texture.FluidTexture.FogShape;

import javax.annotation.Nullable;

/**
 * Implementation of {@link IClientFluidTypeExtensions} using {@link FluidTexture}.
 * <p>
 * 26.3: client extensions only handle the camera overlay and fog. Textures and tint are part of the vanilla fluid model,
 * created by {@link FluidTextureManager#createModel(net.minecraft.world.level.material.Fluid)}. Register this with
 * {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent}; Mantle registers it automatically
 * for every {@link slimeknights.mantle.fluid.TextureFluidType} without another extension.
 */
@RequiredArgsConstructor
public class ClientTextureFluidType implements IClientFluidTypeExtensions {
  protected final FluidType type;
  /** Fluid color used to compute the cached fog multipliers, to detect resource reloads */
  private int lastFogColor = -1;
  /** Cached fog color multipliers */
  private float fogRed = 1, fogGreen = 1, fogBlue = 1;

  @Nullable
  @Override
  public Identifier getRenderOverlayTexture(Minecraft mc) {
    return FluidTextureManager.getCameraTexture(type);
  }

  @Override
  public void extractOverlay(Minecraft minecraft, LocalPlayer player, PlayerRenderState playerRenderState, BlockPos eyePos, float brightness) {
    FluidTexture data = FluidTextureManager.getData(type);
    Identifier camera = data.camera();
    if (camera != null) {
      FluidRenderer.extractCamera(player, playerRenderState, camera, data.cameraOpacity(), data.color(), brightness);
    }
  }

  @Override
  public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
    // nothing to do if fog color is white
    int fluidColor = FluidTextureManager.getData(type).fogColor();
    if (fluidColor != -1) {
      // cache the multipliers for fog color to reduce computation time
      if (fluidColor != lastFogColor) {
        fogRed = ARGB.redFloat(fluidColor);
        fogGreen = ARGB.greenFloat(fluidColor);
        fogBlue = ARGB.blueFloat(fluidColor);
        lastFogColor = fluidColor;
      }
      fluidFogColor.x *= fogRed;
      fluidFogColor.y *= fogGreen;
      fluidFogColor.z *= fogBlue;
    }
  }

  @Override
  public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance, float partialTick, FogData fogData) {
    FluidTexture data = FluidTextureManager.getData(type);
    FogShape shape = data.fogShape();
    if (shape != null) {
      float start = data.fogStart();
      float end = data.fogEnd();
      // spherical fog is the environmental fog, cylindrical fog is the render distance fog
      if (shape == FogShape.CYLINDER) {
        fogData.renderDistanceStart = Math.min(fogData.renderDistanceStart, start);
        fogData.renderDistanceEnd = Math.min(fogData.renderDistanceEnd, end);
      } else {
        fogData.environmentalStart = Math.min(fogData.environmentalStart, start);
        fogData.environmentalEnd = Math.min(fogData.environmentalEnd, end);
      }
      // like lava, the sky and clouds disappear into the fog
      fogData.skyEnd = Math.min(fogData.skyEnd, end);
      fogData.cloudEnd = Math.min(fogData.cloudEnd, end);
    }
  }
}
