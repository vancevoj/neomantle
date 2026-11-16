package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.MantleItemLayerModel.LayerData;

import java.util.ArrayList;
import java.util.List;

/** Builder for {@link slimeknights.mantle.client.model.util.MantleItemLayerModel} */
@SuppressWarnings("unused")  // API
public class MantleItemLayerBuilder extends CustomLoaderBuilder {
  private final List<LayerData> layers = new ArrayList<>();
  protected MantleItemLayerBuilder(Identifier loaderId) {
    super(loaderId, false);
  }

  public MantleItemLayerBuilder() {
    this(Mantle.getResource("item_layer"));
  }

  /** Adds data for the next element */
  public MantleItemLayerBuilder addLayer(LayerData data) {
    this.layers.add(data);
    return this;
  }

  /** Sets the color for the next element */
  public MantleItemLayerBuilder color(int color) {
    return addLayer(new LayerData(color, 0, false, null));
  }

  /** Sets the luminosity for the next element */
  public MantleItemLayerBuilder luminosity(int luminosity) {
    return addLayer(new LayerData(-1, luminosity, false, null));
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    MantleItemLayerBuilder builder = new MantleItemLayerBuilder(this.loaderId);
    builder.layers.addAll(this.layers);
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    if (!layers.isEmpty()) {
      json.add("layers", LayerData.LIST_LOADABLE.serialize(layers));
    }
    return json;
  }
}
