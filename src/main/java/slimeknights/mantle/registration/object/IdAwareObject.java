package slimeknights.mantle.registration.object;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Interface for an object that holds its own name, used to simplify some utilities */
public interface IdAwareObject {
  /** Gets the ID for this object */
  Identifier getId();

  /**
   * Gets the resource key for this object in the given registry.
   * Mostly useful for datagen, as tag appenders and loot tables reference entries by key.
   * @param registry  Registry containing this object
   * @param <T>       Registry type
   * @return  Resource key for this object
   */
  default <T> ResourceKey<T> getKey(ResourceKey<? extends Registry<T>> registry) {
    return ResourceKey.create(registry, getId());
  }
}
