package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Builder for {@link slimeknights.mantle.client.model.FallbackModelLoader} */
public class FallbackModelBuilder extends CustomLoaderBuilder {
  private final List<DomainModel> models = new ArrayList<>();
  public FallbackModelBuilder() {
    super(Mantle.getResource("fallback"), false);
  }

  /**
   * Adds a fallback model with a domain restriction
   * @param template  Template to generate the model
   * @param textures  Textures for the template
   * @param modId     Mod ID required for this model, null to use the loader ID
   */
  public FallbackModelBuilder fallback(ModelTemplate template, TextureMapping textures, @Nullable String modId) {
    this.models.add(new DomainModel(template, textures, modId));
    return this;
  }

  /** Adds a fallback model using the loader ID as the domain restriction */
  public FallbackModelBuilder fallback(ModelTemplate template, TextureMapping textures) {
    return fallback(template, textures, null);
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    FallbackModelBuilder builder = new FallbackModelBuilder();
    builder.models.addAll(this.models);
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    if (this.models.size() < 2) {
      throw new IllegalStateException("Must have at least two models to use the fallback loader");
    }
    JsonArray fallbacks = new JsonArray();
    for (DomainModel builder : models) {
      fallbacks.add(builder.toJson());
    }
    json.add("models", fallbacks);
    return json;
  }

  /** Model template with an optional domain restriction */
  private record DomainModel(ModelTemplate template, TextureMapping textures, @Nullable String domain) {
    /** Converts this to JSON */
    public JsonObject toJson() {
      JsonObject[] result = new JsonObject[1];
      serializeNestedTemplate(template, textures, element -> result[0] = element.getAsJsonObject());
      JsonObject json = result[0];
      if (json == null) {
        throw new IllegalStateException("Fallback model template produced no JSON");
      }
      if (domain != null) {
        json.addProperty("fallback_mod_id", domain);
      }
      return json;
    }
  }
}
