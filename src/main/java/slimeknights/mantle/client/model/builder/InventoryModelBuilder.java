package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.inventory.ModelItem;

import java.util.ArrayList;
import java.util.List;

/** Builder for {@link slimeknights.mantle.client.model.inventory.InventoryModel} */
public class InventoryModelBuilder<T extends ModelBuilder<T>> extends ColoredModelBuilder<T> {
  public final List<ModelItem> items = new ArrayList<>();
  public InventoryModelBuilder(T parent, ExistingFileHelper existingFileHelper) {
    super(Mantle.getResource("inventory"), parent, existingFileHelper);
  }

  /** Adds an item to this builder */
  public InventoryModelBuilder<T> addItem(ModelItem item) {
    this.items.add(item);
    return this;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    JsonArray items = new JsonArray();
    for (ModelItem item : this.items) {
      items.add(item.toJson());
    }
    json.add("items", items);
    return json;
  }
}
