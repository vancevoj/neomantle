package slimeknights.mantle.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.GeometryContextWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Model that dynamically retextures a list of textures based on data from {@link RetexturedHelper}.
 * The model JSON uses the loader {@code mantle:retextured}, while the dynamic behavior comes from the blockstate type {@code mantle:retextured} ({@link RetexturedBlockStateModel})
 * and the client item type {@code mantle:retextured} ({@link RetexturedItemModel}). When used without those, it bakes as a static {@link ColoredBlockModel}.
 */
@SuppressWarnings("WeakerAccess")
public class RetexturedModel extends ColoredBlockModel {
  /** Loader instance */
  public static final UnbakedModelLoader<RetexturedModel> LOADER = RetexturedModel::deserialize;

  /** Texture names directly marked as retextured */
  @Getter
  private final Set<String> retextured;

  protected RetexturedModel(ColoredBlockModel model, Set<String> retextured) {
    super(model);
    this.retextured = retextured;
  }

  /**
   * Gets a list of all names to retexture based on the model texture references
   * @param model  Resolved model, used to find references to retextured textures in parents and children
   * @return  Set of textures including references to retextured textures
   */
  public Set<String> getAllRetextured(ResolvedModel model) {
    return getAllRetextured(model, retextured);
  }

  /**
   * Gets a list of all names to retexture based on the model texture references
   * @param model        Resolved model
   * @param originalSet  Original list of names to retexture
   * @return  Set of textures including references to the original textures
   */
  public static Set<String> getAllRetextured(ResolvedModel model, Set<String> originalSet) {
    Set<String> retextured = new HashSet<>(originalSet);
    retextured.addAll(ModelHelper.findReferences(model, originalSet).keySet());
    return Set.copyOf(retextured);
  }

  /**
   * Finds the retextured model in the given model chain
   * @param model  Resolved model
   * @return  Retextured model, or null if the model is not retextured
   */
  @Nullable
  public static RetexturedModel find(@Nullable ResolvedModel model) {
    return SimpleBlockModel.findModel(model, RetexturedModel.class);
  }

  /** Deserializes a retextured model from JSON */
  public static RetexturedModel deserialize(JsonObject json, JsonDeserializationContext context) {
    // get base model
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);
    // get list of textures to retexture
    Set<String> retextured = getRetexturedNames(json);
    // return retextured model
    return new RetexturedModel(model, retextured);
  }

  /**
   * Gets the list of retextured textures from the model
   * @param json  Model json
   * @return  Set of textures
   */
  public static Set<String> getRetexturedNames(JsonObject json) {
    if (json.has("retextured")) {
      // if an array, set from each texture in array
      JsonElement retextured = json.get("retextured");
      if (retextured.isJsonArray()) {
        JsonArray array = retextured.getAsJsonArray();
        if (array.isEmpty()) {
          throw new JsonSyntaxException("Must have at least one texture in retextured");
        }
        List<String> builder = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
          builder.add(GsonHelper.convertToString(array.get(i), "retextured[" + i + "]"));
        }
        return Set.copyOf(builder);
      }
      // if string, single texture
      if (retextured.isJsonPrimitive()) {
        return Set.of(retextured.getAsString());
      }
    }
    // if neither or missing, error
    throw new JsonSyntaxException("Missing retextured, expected to find a String or a JsonArray");
  }

  /**
   * Material getter wrapper to retexture the block
   */
  public static class RetexturedContext extends GeometryContextWrapper {
    /** List of textures to retexture */
    private final Set<String> retextured;
    /** Replacement texture */
    private final Material.Baked texture;

    /**
     * Creates a new configuration wrapper
     * @param base        Original model configuration
     * @param retextured  Set of textures that should be retextured, should include references, see {@link #getAllRetextured(ResolvedModel, Set)}
     * @param texture     New texture to replace those in the set
     */
    public RetexturedContext(GeometryContextWrapper base, Set<String> retextured, Material.Baked texture) {
      super(base);
      this.retextured = retextured;
      this.texture = texture;
    }

    /**
     * Creates a new configuration wrapper
     * @param base        Original material getter
     * @param retextured  Set of textures that should be retextured, should include references, see {@link #getAllRetextured(ResolvedModel, Set)}
     * @param texture     New texture to replace those in the set
     */
    public RetexturedContext(Function<String,Material.Baked> base, Set<String> retextured, Material.Baked texture) {
      super(base, name -> true);
      this.retextured = retextured;
      this.texture = texture;
    }

    @Override
    public boolean hasMaterial(String name) {
      if (retextured.contains(name)) {
        return !MissingTextureAtlasSprite.getLocation().equals(texture.sprite().contents().name());
      }
      return super.hasMaterial(name);
    }

    @Override
    public Material.Baked getMaterial(String name) {
      if (retextured.contains(name)) {
        return texture;
      }
      return super.getMaterial(name);
    }
  }
}
