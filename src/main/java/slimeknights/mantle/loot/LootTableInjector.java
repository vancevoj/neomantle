package slimeknights.mantle.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.listener.IEarlyReloadListener;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.loot.LootTableInjection.LootPoolInjection;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class handling injecting additional entries into loot tables.
 * <p>
 * Since loot tables became a reloadable registry, they load (and fire {@link net.neoforged.neoforge.event.LootTableLoadEvent}) before any reload listener runs,
 * so injecting from the load event would always use the previous reload's injections. Instead, this listener loads the injections during the reload and
 * injects them directly into the freshly loaded tables of that reload, before the server starts using them.
 */
public enum LootTableInjector implements IEarlyReloadListener {
  INSTANCE;

  /** Datapack folder for the injector */
  public static final String FOLDER = "mantle/loot_injectors";
  /** ID for this reload listener */
  public static final Identifier LISTENER_ID = Mantle.getResource("loot_table_injector");

  /** Initializes the loot table injector */
  public static void init() {
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddServerReloadListenersEvent.class, event -> {
      INSTANCE.serverResources = event.getServerResources();
      event.addListener(LISTENER_ID, INSTANCE);
    });
  }

  /** Server resources for the reload in progress, contains the loot tables to inject into */
  @Nullable
  private ReloadableServerResources serverResources = null;
  /** Map of injections from the last reload */
  private Map<Identifier,LootTableInjection> injections = Collections.emptyMap();

  @Override
  public void onResourceManagerReload(ResourceManager manager) {
    ReloadableServerResources resources = this.serverResources;
    this.serverResources = null;
    if (resources == null) {
      Mantle.logger.error("Loot table injector reloaded without server resources, skipping injection");
      return;
    }
    long time = System.nanoTime();
    // registry backed ops for evaluating load conditions and parsing loot entries, matching the context of the loot tables themselves
    RegistryOps<JsonElement> ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, resources.fullRegistries().lookup()), resources.getConditionContext());
    TypedMap context = ContextKey.fromOps(ops);
    Map<Identifier,LootTableInjection.Builder> builders = new HashMap<>();
    int loaded = 0;
    for (Entry<Identifier,Resource> entry : manager.listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
      try (Reader reader = entry.getValue().openAsReader()) {
        JsonObject json = GsonHelper.fromJson(JsonHelper.DEFAULT_GSON, reader, JsonObject.class);
        if (json != null) {
          // skip if empty for easy removals, and honor neoforge load conditions (e.g. mod-loaded gating)
          if (!json.keySet().isEmpty() && ICondition.conditionsMatched(ops, json)) {
            // the builder allows us to merge from multiple sources, for efficiency
            // ensures a given table name and pool name both show just once
            LootTableInjection injection = LootTableInjection.LOADABLE.deserialize(json, context);
            LootTableInjection.Builder builder = builders.computeIfAbsent(injection.name(), id -> new LootTableInjection.Builder());
            for (LootPoolInjection pool : injection.pools()) {
              builder.addToPool(pool);
            }
            loaded++;
          }
        } else {
          Mantle.logger.error("Couldn't parse loot table injection from {} as it's null or empty", entry.getKey());
        }
      } catch (IllegalArgumentException | IllegalStateException | IOException | JsonParseException ex) {
        Mantle.logger.error("Couldn't parse loot injection from {}", entry.getKey(), ex);
      }
    }
    // build final map
    injections = builders.entrySet().stream().map(entry -> entry.getValue().build(entry.getKey()))
                         .collect(Collectors.toUnmodifiableMap(LootTableInjection::name, Function.identity()));

    // inject into the loot tables of this reload
    int injected = 0;
    for (LootTableInjection injection : injections.values()) {
      LootTable table = resources.fullRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, injection.name()));
      if (table == LootTable.EMPTY) {
        Mantle.logger.debug("Skipping loot injection into missing table {}", injection.name());
        continue;
      }
      Mantle.logger.debug("Injecting into {} pools in the table {}", injection.pools().size(), injection.name());
      for (LootPoolInjection pool : injection.pools()) {
        pool.inject(table);
      }
      injected++;
    }
    // log timer
    Mantle.logger.info("Loaded {} loot table injectors injecting into {} tables in {} ms", loaded, injected, (System.nanoTime() - time) / 1000000f);
  }

  /** Gets the injection for the given loot table from the last reload, or null if none */
  @Nullable
  public LootTableInjection getInjection(Identifier table) {
    return injections.get(table);
  }
}
