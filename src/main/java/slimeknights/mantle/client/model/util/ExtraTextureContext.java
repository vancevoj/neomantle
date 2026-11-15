package slimeknights.mantle.client.model.util;

import net.minecraft.client.resources.model.sprite.Material;

import java.util.Map;
import java.util.function.Function;

/**
 * Model configuration wrapper to add in an extra set of textures
 */
public class ExtraTextureContext extends GeometryContextWrapper {
  private final Map<String,Material.Baked> textures;

  /**
   * Creates a new wrapper using the given textures
   * @param base      Base configuration
   * @param textures  Textures map, any textures in this map will take precedence over those in the base configuration
   */
  public ExtraTextureContext(GeometryContextWrapper base, Map<String,Material.Baked> textures) {
    super(base);
    this.textures = textures;
  }

  /**
   * Creates a new wrapper using the given textures
   * @param base      Base material getter
   * @param textures  Textures map, any textures in this map will take precedence over those in the base configuration
   */
  public ExtraTextureContext(Function<String,Material.Baked> base, Map<String,Material.Baked> textures) {
    super(base, name -> true);
    this.textures = textures;
  }

  /**
   * Creates a new wrapper for a single texture
   * @param base     Base configuration
   * @param name     Texture name, if it matches texture is returned
   * @param texture  Texture material
   */
  public ExtraTextureContext(GeometryContextWrapper base, String name, Material.Baked texture) {
    this(base, Map.of(name, texture));
  }

  @Override
  public Material.Baked getMaterial(String name) {
    Material.Baked connected = textures.get(name);
    if (connected != null) {
      return connected;
    }
    return super.getMaterial(name);
  }

  @Override
  public boolean hasMaterial(String name) {
    return textures.containsKey(name) || super.hasMaterial(name);
  }
}
