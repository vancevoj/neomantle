package slimeknights.mantle.fluid.texture;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.Objects;

/** Record representing a fluid texture */
public record FluidTexture(ResourceLocation still, ResourceLocation flowing, @Nullable ResourceLocation overlay, @Nullable ResourceLocation camera, int color) {

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
      json.addProperty("camera", camera.toString());
    }
    json.addProperty("color", String.format("%08X", color));
    return json;
  }

  /** Deserializes this from JSON */
  public static FluidTexture deserialize(JsonObject json) {
    ResourceLocation still = JsonHelper.getResourceLocation(json, "still");
    ResourceLocation flowing = JsonHelper.getResourceLocation(json, "flowing");
    ResourceLocation overlay = JsonHelper.getResourceLocation(json, "overlay", null);
    ResourceLocation camera = null;
    if (json.has("camera")) {
      camera = JsonHelper.wrap(JsonHelper.getResourceLocation(json, "camera"), "textures/", ".png");
    }
    int color = ColorLoadable.ALPHA.getOrWhite(json, "color");
    return new FluidTexture(still, flowing, overlay, camera, color);
  }

  /** Builder for this object */
  @SuppressWarnings("unused") // API
  @Setter
  @Accessors(fluent = true)
  @RequiredArgsConstructor
  public static class Builder {
    private final FluidType fluid;
    /** Base path, make sure to include the trailing "_" or "/" */
    private ResourceLocation root;
    private ResourceLocation still;
    private ResourceLocation flowing;
    @Nullable
    private ResourceLocation overlay = null;
    @Nullable
    private ResourceLocation camera = null;
    private int color = -1;

    /**
     * Adds textures using the fluid registry ID
     * @param prefix     Prefix for where to place textures
     * @param suffix     Suffix for placing textures, included before "still" or "flowing". Typically will want "/" or "_".
     * @param overlay    If true, include an overlay texture
     * @param camera     If true, include a camera texture
     * @return  Builder instance
     */
    public Builder wrapId(String prefix, String suffix, boolean overlay, boolean camera) {
      return textures(JsonHelper.wrap(Objects.requireNonNull(ForgeRegistries.FLUID_TYPES.get().getKey(fluid)), prefix, suffix), overlay, camera);
    }

    /** Sets the still texture from {@link #root} */
    public Builder still() {
      if (root == null) {
        throw new IllegalStateException("Automatic still texture requires root to be set");
      }
      return still(root.withSuffix("still"));
    }

    /** Sets the flowing texture from {@link #root} */
    public Builder flowing() {
      if (root == null) {
        throw new IllegalStateException("Automatic flowing texture requires root to be set");
      }
      return flowing(root.withSuffix("flowing"));
    }

    /** Sets the overlay texture from {@link #root} */
    public Builder overlay() {
      if (root == null) {
        throw new IllegalStateException("Automatic overlay texture requires root to be set");
      }
      return overlay(root.withSuffix("overlay"));
    }

    /** Sets the camera texture from {@link #root} */
    public Builder camera() {
      if (root == null) {
        throw new IllegalStateException("Automatic camera texture requires root to be set");
      }
      return camera(root.withSuffix("camera"));
    }

    /**
     * Sets all textures by suffixing the given path
     * @param path     Base path, make sure to include the trailing "_" or "/"
     * @param overlay  If true, include an overlay texture
     * @param camera   If true, include a camera texture
     * @return  Builder instance
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

    /** Builds the fluid texture instance */
    public FluidTexture build() {
      if (still == null || flowing == null) {
        throw new IllegalStateException("Must set both still and flowing");
      }
      return new FluidTexture(still, flowing, overlay, camera, color);
    }

    /* Getters for other datagen */

    /** Gets the still texture for the builder */
    public ResourceLocation getStill() {
      return Objects.requireNonNull(still, "Still must be set");
    }

    /** Gets the flowing texture for the builder */
    public ResourceLocation getFlowing() {
      return Objects.requireNonNull(flowing, "Flowing must be set");
    }

    /** Gets the camera texture for the builder */
    @Nullable
    public ResourceLocation getCamera() {
      return camera;
    }

    /** Gets the overlay texture for the builder */
    @Nullable
    public ResourceLocation getOverlay() {
      return overlay;
    }
  }
}
