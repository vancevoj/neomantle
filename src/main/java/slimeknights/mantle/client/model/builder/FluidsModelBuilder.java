package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.fluid.FluidCuboid;

import java.util.ArrayList;
import java.util.List;

/** Builder for {@link slimeknights.mantle.client.model.fluid.FluidsModel} */
public class FluidsModelBuilder<T extends ModelBuilder<T>> extends ColoredModelBuilder<T>{
  private final List<FluidCuboid> fluids = new ArrayList<>();
  public FluidsModelBuilder(T parent, ExistingFileHelper existingFileHelper) {
    super(Mantle.getResource("fluids"), parent, existingFileHelper);
  }

  /** Adds a fluid to this builder */
  public FluidsModelBuilder<T> addFluid(FluidCuboid fluid) {
    this.fluids.add(fluid);
    return this;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    JsonArray items = new JsonArray();
    for (FluidCuboid fluid : this.fluids) {
      items.add(fluid.toJson());
    }
    json.add("fluids", items);
    return json;
  }
}
