package slimeknights.mantle.client.book.data.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import slimeknights.mantle.client.book.repository.BookRepository;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class IngredientData implements IDataElement {
  /** Handlers turning the NBT of legacy {@code forge:nbt} ingredients into components, see {@link #registerLegacyNbtHandler(LegacyNbtHandler)} */
  private static final List<LegacyNbtHandler> LEGACY_NBT_HANDLERS = new CopyOnWriteArrayList<>();

  public SizedIngredient[] ingredients = new SizedIngredient[0];
  public String action;

  private transient String error;
  private transient NonNullList<ItemStack> items;
  private transient boolean customData;

  public NonNullList<ItemStack> getItems() {
    return this.items;
  }

  public static IngredientData getItemStackData(ItemStack stack) {
    IngredientData data = new IngredientData();
    data.items = NonNullList.withSize(1, stack);
    data.customData = true;

    return data;
  }

  public static IngredientData getItemStackData(NonNullList<ItemStack> items) {
    IngredientData data = new IngredientData();
    data.items = items;
    data.customData = true;

    return data;
  }

  @Override
  public void load(BookRepository source) {
    if (this.customData) {
      return;
    }

    ArrayList<ItemStack> stacks = new ArrayList<>();
    for(SizedIngredient ingredient : ingredients) {
      if(ingredient == null) {
        continue;
      }

      stacks.addAll(ingredient.getMatchingStacks());
    }

    if(ingredients == null || stacks.isEmpty() || !StringUtil.isNullOrEmpty(error)) {
      items = NonNullList.withSize(1, getMissingItem());
      return;
    }

    items = NonNullList.of(getMissingItem(), stacks.toArray(new ItemStack[0]));
  }

  private ItemStack getMissingItem() {
    return getMissingItem(this.error);
  }

  private ItemStack getMissingItem(String error) {
    ItemStack missingItem = new ItemStack(Items.BARRIER);

    missingItem.set(DataComponents.CUSTOM_NAME, Component.literal("\u00A7rError Loading Item"));
    if(!StringUtil.isNullOrEmpty(error)) {
      missingItem.set(DataComponents.LORE, new ItemLore(List.of(
        Component.literal("\u00A7r\u00A7eError:"),
        Component.literal("\u00A7r\u00A7e" + error))));
    }

    return missingItem;
  }

  /**
   * Registers a handler that applies the NBT of a legacy {@code {"type": "forge:nbt", "item": ..., "nbt": {...}}} ingredient from 1.20 book JSON to the display stack.
   * Handlers run in registration order until one handles the stack. If none does, the NBT is stored as custom data, like the vanilla upgrade of 1.20 item stacks.
   * @param handler  Handler to register
   */
  public static void registerLegacyNbtHandler(LegacyNbtHandler handler) {
    LEGACY_NBT_HANDLERS.add(handler);
  }

  /** Applies the NBT of a legacy {@code forge:nbt} book ingredient to a display stack */
  @FunctionalInterface
  public interface LegacyNbtHandler {
    /**
     * Applies the NBT to the stack
     * @param stack  Display stack, without components from the NBT
     * @param nbt    NBT from the book JSON, as 1.20 stored it in the item stack tag
     * @return  True if handled, false to let the next handler try
     */
    boolean apply(ItemStack stack, CompoundTag nbt);
  }

  public static class Deserializer implements JsonDeserializer<IngredientData> {
    @Override
    public IngredientData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      IngredientData data = new IngredientData();

      if(json.isJsonArray()) {
        JsonArray array = json.getAsJsonArray();
        data.ingredients = new SizedIngredient[array.size()];

        for(int i = 0; i < array.size(); i++) {
          try {
            data.ingredients[i] = readIngredient(array.get(i));
          } catch (Exception e) {
            // keep the error message on the displayed item through a component ingredient
            data.ingredients[i] = SizedIngredient.of(DataComponentIngredient.of(false, data.getMissingItem(e.getMessage())));
          }
        }

        return data;
      }

      try {
        data.ingredients = new SizedIngredient[]{ readIngredient(json) };
      } catch (Exception e) {
        data.error = e.getMessage();
        return data;
      }

      if(json.isJsonObject()) {
        JsonObject object = json.getAsJsonObject();
        if (object.has("action")) {
          JsonElement action = object.get("action");
          if (action.isJsonPrimitive()) {
            JsonPrimitive primitive = action.getAsJsonPrimitive();
            if (primitive.isString()) {
              data.action = primitive.getAsString();
            }
          }
        }
      }

      return data;
    }

    private SizedIngredient readIngredient(JsonElement json) {
      if(json.isJsonPrimitive()) {
        JsonPrimitive primitive = json.getAsJsonPrimitive();

        if(primitive.isString()) {
          Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(primitive.getAsString()));
          return SizedIngredient.fromItems(item);
        }
      }

      if(!json.isJsonObject()) {
        throw new JsonParseException("Must be an array, string or JSON object");
      }

      JsonObject object = json.getAsJsonObject();
      try {
        return SizedIngredient.deserialize(object);
      } catch (RuntimeException e) {
        SizedIngredient legacy = readLegacyIngredient(object);
        if (legacy != null) {
          return legacy;
        }
        throw e;
      }
    }

    /**
     * Reads the 1.20 ingredient forms book JSON still uses: {@code {"item": ...}}, {@code {"tag": ...}} and {@code {"type": "forge:nbt", "item": ..., "nbt": ...}},
     * each with an optional {@code amount_needed}. Item and tag objects no longer parse as ingredients since 1.21.2, and the {@code forge:nbt} type is gone since 1.20.5.
     * @param object  Ingredient JSON
     * @return  Sized ingredient, or null if this is not a legacy ingredient
     */
    @Nullable
    private static SizedIngredient readLegacyIngredient(JsonObject object) {
      int amount = GsonHelper.getAsInt(object, "amount_needed", 1);
      if (object.has("tag") && !object.has("item")) {
        return SizedIngredient.fromTag(TagKey.create(Registries.ITEM, Identifier.parse(GsonHelper.getAsString(object, "tag"))), amount);
      }
      if (!object.has("item")) {
        return null;
      }
      Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(GsonHelper.getAsString(object, "item")));
      if (item == Items.AIR) {
        return null;
      }
      if (!object.has("nbt")) {
        return SizedIngredient.fromItems(amount, item);
      }
      // forge:nbt ingredients matched the item with the given tag, so display the item with that tag converted to components
      ItemStack stack = new ItemStack(item);
      CompoundTag nbt = parseLegacyNbt(object.get("nbt"));
      boolean handled = false;
      for (LegacyNbtHandler handler : LEGACY_NBT_HANDLERS) {
        if (handler.apply(stack, nbt)) {
          handled = true;
          break;
        }
      }
      if (!handled) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
      }
      return SizedIngredient.of(DataComponentIngredient.of(false, stack), amount);
    }

    /** Parses legacy ingredient NBT, which is either an SNBT string or a JSON object read as SNBT, the same way Forge read it */
    private static CompoundTag parseLegacyNbt(JsonElement element) {
      String snbt = element.isJsonObject() ? element.toString() : GsonHelper.convertToString(element, "nbt");
      try {
        return TagParser.parseCompoundFully(snbt);
      } catch (CommandSyntaxException e) {
        throw new JsonParseException("Invalid NBT entry: " + e.getMessage(), e);
      }
    }
  }
}
