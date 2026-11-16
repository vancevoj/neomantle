package slimeknights.mantle.registration.deferred;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.ItemObject;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Deferred register that registers items with wrappers.
 * <p>
 * Items need their ID stamped into their properties before construction, so every register method takes a constructor from properties
 * and an operator creating the properties. Never share a properties instance between items.
 */
@SuppressWarnings("unused")
public class ItemDeferredRegister extends DeferredRegisterWrapper<Item> {

  public ItemDeferredRegister(String modID) {
    super(Registries.ITEM, modID);
  }

  /**
   * Adds a new item to the list to be registered, using the given constructor
   * @param name   Item name
   * @param item   Item constructor, receives the item properties with the ID set
   * @param props  Operator applied to new item properties, use {@link UnaryOperator#identity()} for default properties
   * @return  Item registry object
   */
  public <I extends Item> ItemObject<I> register(String name, Function<Item.Properties, ? extends I> item, UnaryOperator<Item.Properties> props) {
    return new ItemObject<>(register.register(name, id -> item.apply(RegistrationHelper.itemProperties(id, props))));
  }

  /**
   * Adds a new item to the list to be registered, based on the given item properties
   * @param name   Item name
   * @param props  Operator applied to new item properties
   * @return  Item registry object
   */
  public ItemObject<Item> register(String name, UnaryOperator<Item.Properties> props) {
    return register(name, Item::new, props);
  }

  /**
   * Adds a new item to the list to be registered, with default item properties
   * @param name   Item name
   * @return  Item registry object
   */
  public ItemObject<Item> register(String name) {
    return register(name, UnaryOperator.identity());
  }


  /* Specialty */

  /**
   * Registers an item with multiple variants, prefixing the name with the value name
   * @param values   Enum values to use for this item
   * @param name     Name of the block
   * @param mapper   Function to get a item for the given enum value and properties
   * @param props    Operator applied to new item properties, called once per value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(T[] values, String name, BiFunction<T, Item.Properties, ? extends I> mapper, UnaryOperator<Item.Properties> props) {
    return registerEnum(values, name, (fullName, type) -> register(fullName, p -> mapper.apply(type, p), props));
  }

  /**
   * Registers an item with multiple variants, suffixing the name with the value name
   * @param values   Enum values to use for this item
   * @param name     Name of the block
   * @param mapper   Function to get a item for the given enum value and properties
   * @param props    Operator applied to new item properties, called once per value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(String name, T[] values, BiFunction<T, Item.Properties, ? extends I> mapper, UnaryOperator<Item.Properties> props) {
    return registerEnum(name, values, (fullName, type) -> register(fullName, p -> mapper.apply(type, p), props));
  }
}
