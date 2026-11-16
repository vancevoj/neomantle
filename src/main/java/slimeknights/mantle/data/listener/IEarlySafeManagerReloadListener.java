package slimeknights.mantle.data.listener;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Same as {@link ISafeManagerReloadListener}, but reloads earlier. Needed to work with some parts of models. */
public interface IEarlySafeManagerReloadListener extends PreparableReloadListener {
  @Override
  default CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor, PreparationBarrier barrier, Executor gameExecutor) {
    return CompletableFuture.runAsync(() -> {
      if (!ModLoader.hasErrors()) {
        onReloadSafe(state.resourceManager());
      }
    }, backgroundExecutor).thenCompose(barrier::wait);
  }

  /**
   * Safely handle a resource manager reload. Only runs if the mod loading state is valid
   * @param resourceManager  Resource manager
   */
  void onReloadSafe(ResourceManager resourceManager);
}
