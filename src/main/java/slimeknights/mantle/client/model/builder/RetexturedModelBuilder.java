package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

/**
 * Builder for {@link slimeknights.mantle.client.model.RetexturedModel}.
 * The blockstate must use {@link VariantBlockStateModelBuilder#retextured(net.minecraft.resources.Identifier)} and the client item {@link slimeknights.mantle.client.model.RetexturedItemModel.Unbaked} for the retexturing to apply.
 */
public class RetexturedModelBuilder extends ColoredModelBuilder {
  private final JsonArray retextured = new JsonArray();
  public RetexturedModelBuilder() {
    super(Mantle.getResource("retextured"));
  }

  /** Marks the given texture as retextured. Uses the texture name, not path. */
  public RetexturedModelBuilder retexture(String name) {
    this.retextured.add(name);
    return this;
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    RetexturedModelBuilder builder = copyColors(new RetexturedModelBuilder());
    builder.retextured.addAll(this.retextured.deepCopy());
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    if (retextured.isEmpty()) {
      throw new IllegalStateException("Must have at least 1 texture to be retextured.");
    }
    json = super.toJson(json);
    json.add("retextured", retextured.deepCopy());
    return json;
  }
}
