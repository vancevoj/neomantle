package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for {@link slimeknights.mantle.client.model.util.ColoredBlockModel}, used as a base for other model builders.
 * Use with {@link net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder#customLoader(java.util.function.Supplier, java.util.function.Consumer)}.
 */
public class ColoredModelBuilder extends CustomLoaderBuilder {
  protected final List<ColorData> colors = new ArrayList<>();

  public ColoredModelBuilder() {
    this(Mantle.getResource("colored_block"));
  }

  protected ColoredModelBuilder(Identifier loaderId) {
    // supports inline elements, so may be marked optional
    super(loaderId, true);
  }

  /** Adds a full color data for the next element */
  public ColoredModelBuilder colorData(ColorData data) {
    colors.add(data);
    return this;
  }

  /** Sets the color for the next element */
  public ColoredModelBuilder color(int color) {
    return colorData(new ColorData(color, -1, null));
  }

  /** Sets the luminosity for the next element */
  public ColoredModelBuilder luminosity(int luminosity) {
    return colorData(new ColorData(-1, luminosity, null));
  }

  /** Copies the data from this builder into the given builder, used for {@link #copyInternal()} in subclasses */
  protected <B extends ColoredModelBuilder> B copyColors(B builder) {
    builder.colors.addAll(this.colors);
    return builder;
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    return copyColors(new ColoredModelBuilder(this.loaderId));
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    if (!colors.isEmpty()) {
      json.add("colors", ColorData.LIST_LOADABLE.serialize(colors));
    }
    return json;
  }
}
