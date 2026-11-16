package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Builder for {@link slimeknights.mantle.client.model.connected.ConnectedModel}.
 * The blockstate must use {@link VariantBlockStateModelBuilder#connected(net.minecraft.resources.Identifier)} for the connections to apply.
 */
public class ConnectedModelBuilder extends ColoredModelBuilder {
  private final JsonObject connectedTextures = new JsonObject();
  @Nullable
  private Set<Direction> sides = null;
  @Nullable
  private String predicate = null;

  public ConnectedModelBuilder() {
    super(Mantle.getResource("connected"));
  }

  /**
   * Makes the given texture connected using the given connection type.
   * @param name  Name of the texture from the textures list, not the full path.
   * @param type  Connection type, see {@link slimeknights.mantle.client.model.connected.ConnectedModelRegistry}
   */
  public ConnectedModelBuilder connected(String name, String type) {
    connectedTextures.addProperty(name, type);
    return this;
  }

  /** Sets the sides of the block that receive connected textures, used to simplify some logic */
  public ConnectedModelBuilder setSides(Direction first, Direction... other) {
    this.sides = EnumSet.of(first, other);
    return this;
  }

  /** Sets the connection predicate, must be registered with the {@link slimeknights.mantle.client.model.connected.ConnectedModelRegistry} */
  public ConnectedModelBuilder setPredicate(String predicate) {
    this.predicate = predicate;
    return this;
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    ConnectedModelBuilder builder = copyColors(new ConnectedModelBuilder());
    for (Map.Entry<String,JsonElement> entry : this.connectedTextures.entrySet()) {
      builder.connectedTextures.add(entry.getKey(), entry.getValue().deepCopy());
    }
    builder.sides = this.sides == null ? null : EnumSet.copyOf(this.sides);
    builder.predicate = this.predicate;
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    JsonObject data = new JsonObject();
    json.add("connection", data);
    data.add("textures", connectedTextures.deepCopy());
    if (sides != null) {
      JsonArray sides = new JsonArray();
      for (Direction side : this.sides) {
        sides.add(side.getSerializedName());
      }
      data.add("sides", sides);
    }
    if (predicate != null) {
      data.addProperty("predicate", predicate);
    }
    return json;
  }
}
