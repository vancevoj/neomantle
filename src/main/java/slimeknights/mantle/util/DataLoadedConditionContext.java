package slimeknights.mantle.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import slimeknights.mantle.client.SafeClientAccess;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Condition context to use when data has already been loaded, used in books for processing their conditions for instance. */
public enum DataLoadedConditionContext implements ICondition.IContext {
  INSTANCE;

  /** Gets the named tag from the built-in registry, if present */
  private static <T> Optional<HolderSet.Named<T>> tag(TagKey<T> key) {
    Registry<T> registry = RegistryHelper.getRegistry(key.registry());
    if (registry != null) {
      // tags are only bound once data packs finished loading, before that treat all tags as unloaded
      try {
        return registry.get(key);
      } catch (IllegalStateException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  @Override
  public <T> boolean isTagLoaded(TagKey<T> key) {
    return tag(key).isPresent();
  }

  @Override
  public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
    return tag(key).<Collection<Holder<T>>>map(set -> set.stream().toList()).orElse(List.of());
  }

  @Override
  public HolderGetter.Provider registries() {
    // data is loaded, so use the current server's registries if present, otherwise the client's
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server != null) {
      return server.registryAccess();
    }
    RegistryAccess client = SafeClientAccess.getRegistryAccess();
    return client != null ? client : RegistryAccess.EMPTY;
  }
}
