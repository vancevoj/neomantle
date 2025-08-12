package slimeknights.mantle.fluid.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.mantle.client.model.TextureColorHelper;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.Objects;

/** Record representing a fluid texture */
@Accessors(fluent = true)
@Data
@AllArgsConstructor
public final class FluidTexture {
  private final ResourceLocation still;
  private final ResourceLocation flowing;
  @Nullable
  private final ResourceLocation overlay;
  @Nullable
  private final ResourceLocation camera;
  private final float cameraOpacity;
  private final int color;
  private int fogColor;
  private final boolean calculateFogColor;

  /** @deprecated use {@link #FluidTexture(ResourceLocation, ResourceLocation, ResourceLocation, ResourceLocation, float, int, int, boolean)} */
  @Deprecated(forRemoval = true)
  public FluidTexture(ResourceLocation still, ResourceLocation flowing, @Nullable ResourceLocation overlay, @Nullable ResourceLocation camera, int color) {
    this(still, flowing, overlay, camera, 0.1f, color, -1, false);
  }

  /** Gets the fog color for this fluid */
  public int fogColor() {
    if (calculateFogColor && fogColor == -1) {
      fogColor = TextureColorHelper.getAverageColor(still);
    }
    return fogColor;
  }

  /** Serializes this to JSON */
  public JsonObject serialize() {
    JsonObject json = new JsonObject();
    json.addProperty("still", still.toString());
    json.addProperty("flowing", flowing.toString());
    if (overlay != null) {
      json.addProperty("overlay", overlay.toString());
    }
    // during datagen, we just write the texture directly, we will include the needed prefix/suffix on read
    if (camera != null) {
      if (cameraOpacity <= 0 || cameraOpacity > 1) {
        throw new IllegalStateException("Camera opacity must be between 0 (exclusive) and 1 (inclusive)");
      }
      json.addProperty("camera", camera.toString());
      json.addProperty("camera_opacity", cameraOpacity);
    }
    json.add("color", ColorLoadable.ALPHA.serialize(color));
    if (fogColor != -1) {
      json.add("fog_color", ColorLoadable.NO_ALPHA.serialize(fogColor));
    } else if (calculateFogColor) {
      json.addProperty("calculate_fog_color", true);
    }
    return json;
  }

  /** Deserializes this from JSON */
  public static FluidTexture deserialize(JsonObject json) {
    ResourceLocation still = JsonHelper.getResourceLocation(json, "still");
    ResourceLocation flowing = JsonHelper.getResourceLocation(json, "flowing");
    ResourceLocation overlay = JsonHelper.getResourceLocation(json, "overlay", null);
    ResourceLocation camera = null;
    float cameraOpacity = 0;
    if (json.has("camera")) {
      camera = JsonHelper.wrap(JsonHelper.getResourceLocation(json, "camera"), "textures/", ".png");
      cameraOpacity = GsonHelper.getAsFloat(json, "camera_opacity");
      if (cameraOpacity <= 0 || cameraOpacity > 1) {
        throw new JsonSyntaxException("Camera opacity must be between 0 (exclusive) and 1 (inclusive)");
      }
    }
    int color = ColorLoadable.ALPHA.getOrWhite(json, "color");
    int fogColor = color | 0xFF000000; // default fog color to opaque variant of fluid color. If no tint this will end up as -1
    boolean calculateFogColor = false;
    if (json.has("fog_color")) {
      fogColor = ColorLoadable.NO_ALPHA.getIfPresent(json, "fog_color");
    } else if (color == -1) {
      calculateFogColor = GsonHelper.getAsBoolean(json, "calculate_fog_color", false);
    }
    return new FluidTexture(still, flowing, overlay, camera, cameraOpacity, color, fogColor, calculateFogColor);
  }


  /**
   * Builder for this object
   */
  @SuppressWarnings("unused") // API
  @Setter
  @Accessors(fluent = true)
  @RequiredArgsConstructor
  public static class Builder {

    private final FluidType fluid;
    /**
     * Base path, make sure to include the trailing "_" or "/"
     */
    private ResourceLocation root;
    private ResourceLocation still;
    private ResourceLocation flowing;
    @Nullable
    private ResourceLocation overlay = null;
    @Nullable
    private ResourceLocation camera = null;
    private float cameraOpacity = 0.1f;
    private int color = -1;
    private int fogColor = -1;
    private boolean calculateFogColor = false;

    /**
     * Adds textures using the fluid registry ID
     *
     * @param prefix  Prefix for where to place textures
     * @param suffix  Suffix for placing textures, included before "still" or "flowing". Typically will want "/" or "_".
     * @param overlay If true, include an overlay texture
     * @param camera  If true, include a camera texture
     * @return Builder instance
     */
    public Builder wrapId(String prefix, String suffix, boolean overlay, boolean camera) {
      return textures(JsonHelper.wrap(Objects.requireNonNull(ForgeRegistries.FLUID_TYPES.get().getKey(fluid)), prefix, suffix), overlay, camera);
    }

    /**
     * Sets the still texture from {@link #root}
     */
    public Builder still() {
      if (root == null) {
        throw new IllegalStateException("Automatic still texture requires root to be set");
      }
      return still(root.withSuffix("still"));
    }

    /**
     * Sets the flowing texture from {@link #root}
     */
    public Builder flowing() {
      if (root == null) {
        throw new IllegalStateException("Automatic flowing texture requires root to be set");
      }
      return flowing(root.withSuffix("flowing"));
    }

    /**
     * Sets the overlay texture from {@link #root}
     */
    public Builder overlay() {
      if (root == null) {
        throw new IllegalStateException("Automatic overlay texture requires root to be set");
      }
      return overlay(root.withSuffix("overlay"));
    }

    /**
     * Sets the camera texture from {@link #root}
     */
    public Builder camera() {
      if (root == null) {
        throw new IllegalStateException("Automatic camera texture requires root to be set");
      }
      return camera(root.withSuffix("camera"));
    }

    /**
     * Sets all textures by suffixing the given path
     *
     * @param path    Base path, make sure to include the trailing "_" or "/"
     * @param overlay If true, include an overlay texture
     * @param camera  If true, include a camera texture
     * @return Builder instance
     * @deprecated use {@link #root(ResourceLocation)}, {@link #still()}, {@link #flowing()}, {@link #camera()}, and {@link #overlay()}
     */
    @Deprecated
    public Builder textures(ResourceLocation path, boolean overlay, boolean camera) {
      root(path).still().flowing();
      if (overlay) {
        overlay();
      }
      if (camera) {
        camera();
      }
      return this;
    }

    /**
     * Builds the fluid texture instance
     */
    public FluidTexture build() {
      if (still == null || flowing == null) {
        throw new IllegalStateException("Must set both still and flowing");
      }
      return new FluidTexture(still, flowing, overlay, camera, cameraOpacity, color, fogColor, fogColor == -1 && calculateFogColor);
    }

    /* Getters for other datagen */

    /**
     * Gets the still texture for the builder
     */
    public ResourceLocation getStill() {
      return Objects.requireNonNull(still, "Still must be set");
    }

    /**
     * Gets the flowing texture for the builder
     */
    public ResourceLocation getFlowing() {
      return Objects.requireNonNull(flowing, "Flowing must be set");
    }

    /**
     * Gets the camera texture for the builder
     */
    @Nullable
    public ResourceLocation getCamera() {
      return camera;
    }

    /**
     * Gets the overlay texture for the builder
     */
    @Nullable
    public ResourceLocation getOverlay() {
      return overlay;
    }
  }
}
