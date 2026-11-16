package slimeknights.mantle.client.model.util;

import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wrapper around a material getter to allow easier extending, mostly for dynamic textures.
 * In 26.x the only per model context needed when baking elements is the texture lookup, so this replaces the old {@code IGeometryBakingContext} wrapper.
 * Instances can be passed anywhere a {@code Function<String,Material.Baked>} material getter is expected, such as {@link SimpleBlockModel#bakeModel}.
 */
@SuppressWarnings("WeakerAccess")
public class GeometryContextWrapper implements Function<String,Material.Baked> {
  private final Function<String,Material.Baked> base;
  private final Predicate<String> hasMaterial;

  /**
   * Creates a new context wrapper
   * @param base         Base material getter
   * @param hasMaterial  Predicate checking if a texture name is defined
   */
  public GeometryContextWrapper(Function<String,Material.Baked> base, Predicate<String> hasMaterial) {
    this.base = base;
    this.hasMaterial = hasMaterial;
  }

  /**
   * Creates a new context wrapper around another wrapper
   * @param base  Base model context
   */
  public GeometryContextWrapper(GeometryContextWrapper base) {
    this(base, base::hasMaterial);
  }

  /**
   * Creates a new context wrapper for the given texture slots
   * @param baker  Model baker, used to bake materials
   * @param slots  Texture slots, typically from {@link net.minecraft.client.resources.model.ResolvedModel#getTopTextureSlots()}
   * @param name   Model name for error reporting
   */
  public GeometryContextWrapper(ModelBaker baker, TextureSlots slots, ModelDebugName name) {
    this(ModelHelper.materialGetter(baker, slots, name), texture -> slots.getMaterial(texture) != null);
  }

  /**
   * Checks if the given texture is defined
   * @param name  Texture name without the {@code #}
   * @return  True if the texture is defined
   */
  public boolean hasMaterial(String name) {
    return hasMaterial.test(name);
  }

  /**
   * Gets the material for the given texture
   * @param name  Texture name without the {@code #}
   * @return  Baked material, missing texture if undefined
   */
  public Material.Baked getMaterial(String name) {
    return base.apply(name);
  }

  @Override
  public final Material.Baked apply(String texture) {
    return getMaterial(ModelHelper.stripReference(texture));
  }
}
