package slimeknights.mantle.registration.deferred;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.object.EntityObject;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Deferred register for an entity, building the type from a builder instance and adding an egg
 */
@SuppressWarnings("unused")
public class EntityTypeDeferredRegister extends DeferredRegisterWrapper<EntityType<?>> {

  private final SynchronizedDeferredRegister<Item> itemRegistry;
  public EntityTypeDeferredRegister(String modID) {
    super(Registries.ENTITY_TYPE, modID);
    itemRegistry = SynchronizedDeferredRegister.create(Registries.ITEM, modID);
  }

  @Override
  public void register(IEventBus bus) {
    super.register(bus);
    itemRegistry.register(bus);
  }

  /**
   * Registers a entity type for the given entity type builder
   * @param name  Entity name
   * @param sup   Entity builder instance
   * @param <T>   Entity class type
   * @return  Entity registry object
   */
  public <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> sup) {
    return register.register(name, id -> sup.get().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
  }

  /**
   * Registers a entity type for the given entity type builder, and registers a spawn egg for it.
   * Spawn egg colors are no longer part of the item, they come from the egg's client item model.
   * @param name       Entity name
   * @param sup        Entity builder instance
   * @param eggProps   Operator applied to the spawn egg properties
   * @param <T>   Entity class type
   * @return  Entity registry object
   */
  public <T extends Mob> EntityObject<T> registerWithEgg(String name, Supplier<EntityType.Builder<T>> sup, UnaryOperator<Item.Properties> eggProps) {
    DeferredHolder<EntityType<?>, EntityType<T>> object = register(name, sup);
    // entity types register before items, so the type is available when the egg is constructed
    return new EntityObject<>(object, itemRegistry.register(name + "_spawn_egg", id -> new SpawnEggItem(RegistrationHelper.itemProperties(id, eggProps).spawnEgg(object.get()))));
  }

  /**
   * Registers a entity type for the given entity type builder, and registers a spawn egg for it with default properties.
   * Spawn egg colors are no longer part of the item, they come from the egg's client item model.
   * @param name       Entity name
   * @param sup        Entity builder instance
   * @param <T>   Entity class type
   * @return  Entity registry object
   */
  public <T extends Mob> EntityObject<T> registerWithEgg(String name, Supplier<EntityType.Builder<T>> sup) {
    return registerWithEgg(name, sup, UnaryOperator.identity());
  }
}
