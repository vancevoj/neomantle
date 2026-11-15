package slimeknights.mantle.recipe.helper;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * Client only listeners filling {@link ClientRecipeCache}. Only reference this class from client only code.
 * <p>
 * Register both on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} on the client. {@link #onRecipesReceived(RecipesReceivedEvent)} should use
 * {@link net.neoforged.bus.api.EventPriority#HIGHEST} so the cache is filled before JEI and other consumers react to the same event.
 */
public final class ClientRecipeEvents {
  private ClientRecipeEvents() {}

  /** Stores the recipes received from the server */
  public static void onRecipesReceived(RecipesReceivedEvent event) {
    ClientRecipeCache.set(event.getRecipeMap(), event.getRecipeTypes());
  }

  /** Clears the recipes when leaving the server */
  public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
    ClientRecipeCache.clear();
  }
}
