package slimeknights.mantle.client.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.ModelTextureIteratable;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Model which uses a key in NBT to select which texture variant to load.
 * The model JSON uses the loader {@code mantle:nbt_key} to define the textures and key, while the client item JSON uses the type {@code mantle:nbt_key} ({@link NBTKeyItemModel}) to select the variant.
 * When used as a plain model, it bakes the default texture.
 */
public class NBTKeyModel extends AbstractUnbakedModel {
  /** Model loader instance */
  public static final UnbakedModelLoader<NBTKeyModel> LOADER = NBTKeyModel::deserialize;

  /** Map of statically registered extra textures, used for addon mods */
  private static final Multimap<Identifier,Pair<String,Identifier>> EXTRA_TEXTURES = HashMultimap.create();

  /**
   * Registers an extra variant texture for the model with the given key. Note that resource packs can override the extra texture
   * @param key          Model key, should be defined in the model JSON if supported
   * @param textureName  Name of the texture defined, corresponds to a possible value of the NBT key
   * @param texture      Texture to use, same format as in resource packs
   * @deprecated create a JSON under {@link TextureMapManager#FOLDER} at {@code key} with the texture name and texture.
   */
  @Deprecated(forRemoval = true)
  public static void registerExtraTexture(Identifier key, String textureName, Identifier texture) {
    EXTRA_TEXTURES.put(key, Pair.of(textureName, texture));
  }

  /** Key to check in item NBT */
  @Getter
  private final String nbtKey;
  /** Key denoting which extra textures to fetch from the map */
  @Getter
  @Nullable
  private final Identifier extraTexturesKey;
  /** Texture data with a default particle */
  private final TextureSlots.Data textures;

  public NBTKeyModel(StandardModelParameters parameters, String nbtKey, @Nullable Identifier extraTexturesKey) {
    super(parameters);
    this.nbtKey = nbtKey;
    this.extraTexturesKey = extraTexturesKey;
    // particle defaults to the default texture, matching the old behavior
    this.textures = ModelHelper.withDefaultReference(parameters.textures(), "particle", "default");
  }

  @Override
  public TextureSlots.Data textureSlots() {
    return textures;
  }

  @Override
  public UnbakedGeometry geometry() {
    return (ExtendedUnbakedGeometry) NBTKeyModel::bakeDefault;
  }

  /** Bakes the default texture as the static geometry */
  private static QuadCollection bakeDefault(TextureSlots textureSlots, ModelBaker baker, ModelState modelState, ModelDebugName name, ContextMap additionalProperties) {
    return bakeTexture(baker, baker.materials().resolveSlot(textureSlots, "default", name), ModelHelper.applyRootTransform(modelState, additionalProperties));
  }

  /**
   * Bakes quads for the given texture
   * @param baker     Model baker
   * @param texture   Texture to bake
   * @param state     Model state including the root transform
   * @return  Quads for the texture
   */
  public static QuadCollection bakeTexture(ModelBaker baker, Material.Baked texture, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    for (BakedQuad quad : MantleItemLayerModel.getQuadsForSprite(-1, -1, texture, MantleItemLayerModel.getItemTransform(state), 0, null, baker.interner())) {
      builder.addUnculledFace(quad);
    }
    return builder.build();
  }

  /**
   * Gets all textures for the model
   * @param model  Resolved model, used to find textures defined in parent models
   * @return  Map of texture name to material, will always contain {@code default}
   */
  public Map<String,Material> getTextures(ResolvedModel model) {
    TextureSlots slots = model.getTopTextureSlots();
    Map<String,Material> textures = new LinkedHashMap<>();
    // must have a default
    Material defaultTexture = slots.getMaterial("default");
    textures.put("default", defaultTexture != null ? defaultTexture : new Material(MissingTextureAtlasSprite.getLocation()));
    // fetch others from each model in the chain
    for (TextureSlots.Data data : ModelTextureIteratable.of(model)) {
      for (String key : data.values().keySet()) {
        if (!textures.containsKey(key)) {
          Material material = slots.getMaterial(key);
          if (material != null) {
            textures.put(key, material);
          }
        }
      }
    }
    // fetch extra textures
    if (extraTexturesKey != null) {
      // new system
      Map<String,Identifier> extraTextures = TextureMapManager.INSTANCE.getTextureMap(Minecraft.getInstance().getResourceManager(), extraTexturesKey);
      for (Entry<String,Identifier> extra : extraTextures.entrySet()) {
        textures.putIfAbsent(extra.getKey(), new Material(extra.getValue()));
      }
      // legacy system
      for (Pair<String,Identifier> extra : EXTRA_TEXTURES.get(extraTexturesKey)) {
        textures.putIfAbsent(extra.getFirst(), new Material(extra.getSecond()));
      }
    }
    return textures;
  }

  /**
   * Finds the NBT key model in the given model chain
   * @param model  Resolved model
   * @return  NBT key model, or null if not found
   */
  @Nullable
  public static NBTKeyModel find(@Nullable ResolvedModel model) {
    return SimpleBlockModel.findModel(model, NBTKeyModel.class);
  }

  /** Deserializes this model from JSON */
  public static NBTKeyModel deserialize(JsonObject json, JsonDeserializationContext context) {
    String key = GsonHelper.getAsString(json, "nbt_key");
    Identifier extraTexturesKey = null;
    if (json.has("extra_textures_key")) {
      extraTexturesKey = JsonHelper.getResourceLocation(json, "extra_textures_key");
    }
    return new NBTKeyModel(StandardModelParameters.parse(json, context), key, extraTexturesKey);
  }
}
