package slimeknights.mantle.client.model.connected;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Model that handles generating variants for connected textures.
 * The model JSON uses the loader {@code mantle:connected} to define the connection data, while the connections are computed by the blockstate type {@code mantle:connected} ({@link ConnectedBlockStateModel}).
 * When used without the blockstate type, it bakes as a static {@link ColoredBlockModel} with no connections.
 */
public class ConnectedModel extends ColoredBlockModel {
  /** Loader instance */
  public static final UnbakedModelLoader<ConnectedModel> LOADER = ConnectedModel::deserialize;

  /** Map of texture name to index of suffixes (indexed as 0bENWS) */
  @Getter
  private final Map<String,String[]> connectedTextures;
  /** Function to run to check if this block connects to another */
  @Getter
  private final BiPredicate<BlockState,BlockState> connectionPredicate;
  /** List of sides to check when getting block directions */
  @Getter
  private final Set<Direction> sides;

  protected ConnectedModel(ColoredBlockModel model, Map<String,String[]> connectedTextures, BiPredicate<BlockState,BlockState> connectionPredicate, Set<Direction> sides) {
    super(model);
    this.connectedTextures = connectedTextures;
    this.connectionPredicate = connectionPredicate;
    this.sides = sides;
  }

  /**
   * Gets the suffixed textures for all connected textures
   * @param slots  Resolved texture slots for the model
   * @return  Map of full texture name to the resulting material
   */
  public Map<String,Material> getExtraTextures(TextureSlots slots) {
    // for all connected textures, add suffix textures
    Map<String,Material> extraTextures = new HashMap<>();
    for (Entry<String,String[]> entry : connectedTextures.entrySet()) {
      // fetch data from the base texture
      String name = entry.getKey();
      Material base = slots.getMaterial(name);
      // skip if missing
      if (base == null) {
        continue;
      }

      // use base texture, but suffix the name
      String[] suffixes = entry.getValue();
      for (String suffix : suffixes) {
        if (suffix.isEmpty()) {
          continue;
        }
        // skip running if we have seen it before
        String suffixedName = name + "_" + suffix;
        if (!extraTextures.containsKey(suffixedName)) {
          // allow overriding a specific texture
          Material mat = slots.getMaterial(suffixedName);
          if (mat == null) {
            mat = base.withSuffix("/" + suffix);
          }
          // cache the texture name, we use it a lot in rebaking
          extraTextures.put(suffixedName, mat);
        }
      }
    }
    // copy into immutable for better performance
    return Map.copyOf(extraTextures);
  }

  /**
   * Finds the connected model in the given model chain
   * @param model  Resolved model
   * @return  Connected model, or null if the model is not connected
   */
  @Nullable
  public static ConnectedModel find(@Nullable ResolvedModel model) {
    return SimpleBlockModel.findModel(model, ConnectedModel.class);
  }

  /** Loader class containing singleton instance */
  public static ConnectedModel deserialize(JsonObject json, JsonDeserializationContext context) {
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);

    // root object for all model data
    JsonObject data = GsonHelper.getAsJsonObject(json, "connection");

    // need at least one connected texture
    JsonObject connected = GsonHelper.getAsJsonObject(data, "textures");
    if (connected.size() == 0) {
      throw new JsonSyntaxException("Must have at least one texture in connected");
    }

    // build texture list
    Map<String,String[]> connectedTextures = new HashMap<>(connected.size());
    for (Entry<String,JsonElement> entry : connected.entrySet()) {
      // don't validate texture as it may be contained in a child model that is not yet loaded
      // get type, put in map
      String name = entry.getKey();
      connectedTextures.put(name, ConnectedModelRegistry.deserializeType(entry.getValue(), "textures[" + name + "]"));
    }

    // get a list of sides to pay attention to
    Set<Direction> sides;
    if (data.has("sides")) {
      JsonArray array = GsonHelper.getAsJsonArray(data, "sides");
      sides = EnumSet.noneOf(Direction.class);
      for (int i = 0; i < array.size(); i++) {
        String side = GsonHelper.convertToString(array.get(i), "sides[" + i + "]");
        Direction dir = Direction.byName(side);
        if (dir == null) {
          throw new JsonParseException("Invalid side " + side);
        }
        sides.add(dir);
      }
    } else {
      sides = EnumSet.allOf(Direction.class);
    }

    // other data
    BiPredicate<BlockState,BlockState> predicate = ConnectedModelRegistry.deserializePredicate(data, "predicate");

    // final model instance
    return new ConnectedModel(model, Map.copyOf(connectedTextures), predicate, sides);
  }
}
