package slimeknights.mantle.recipe.data;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ingredient for a non-NBT sensitive item from another mod, should never be used outside datagen.
 * <p>
 * Behavior change for the NeoForge 1.21 port: instead of producing raw JSON via {@code toJson()}, this is now a
 * {@link ICustomIngredient} and serializes via {@link #TYPE}'s {@link MapCodec}. It is written at datagen while the
 * referenced items may not be loaded, but the generated recipe is guarded by a {@code neoforge:registered} condition,
 * so it only ever loads at runtime once the item is present. It therefore must support {@link #test} and {@link #items()}
 * for that live recipe, which it does by resolving the names against the item registry.
 */
public class ItemNameIngredient implements ICustomIngredient {
  /** Ingredient type. Encode-only since the referenced items may not be loaded during datagen. Registered in MantleIngredients. */
  public static final IngredientType<ItemNameIngredient> TYPE = new IngredientType<>(
    RecordCodecBuilder.mapCodec(inst -> inst.group(
      Identifier.CODEC.listOf().fieldOf("items").forGetter(i -> i.names)
    ).apply(inst, ItemNameIngredient::new)));

  private final List<Identifier> names;
  protected ItemNameIngredient(List<Identifier> names) {
    this.names = names;
  }

  /** Creates a new ingredient from a list of names */
  public static ItemNameIngredient from(List<Identifier> names) {
    return new ItemNameIngredient(names);
  }

  /** Creates a new ingredient from a list of names */
  public static ItemNameIngredient from(Identifier... names) {
    return from(Arrays.asList(names));
  }

  @Override
  public boolean test(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    // resolve each name against the registry; a live recipe reaches here only because the registered condition passed, but a name
    // may still be absent if several are listed and only some are loaded, so skip the ones that resolve to air
    for (Identifier name : names) {
      Item item = BuiltInRegistries.ITEM.getValue(name);
      if (item != Items.AIR && stack.is(item)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Stream<Holder<Item>> items() {
    // datagen-only; return the items that happen to resolve so the ingredient is not considered empty
    return names.stream()
                .flatMap(name -> BuiltInRegistries.ITEM.get(name).stream())
                .filter(holder -> holder.value() != Items.AIR)
                .<Holder<Item>>map(holder -> holder);
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ItemNameIngredient other && names.equals(other.names);
  }

  @Override
  public int hashCode() {
    return names.hashCode();
  }
}
