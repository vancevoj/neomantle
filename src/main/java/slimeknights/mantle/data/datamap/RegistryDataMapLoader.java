package slimeknights.mantle.data.datamap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Simple loader mapping from a registry object to a piece of data parsed from JSON. Supports parenting to reuse data from another file */
public class RegistryDataMapLoader<R,D> extends SimpleJsonResourceReloadListener {
  private final String name;
  @Getter
  private final String folder;
  @Getter
  private final Registry<R> registry;
  @Getter
  private final RecordLoadable<D> dataLoader;
  private Map<R,D> dataMap = Map.of();

  /**
   * Creates a new data loader instance
   * @param name        Name for error messages
   * @param registry    Vanilla registry representing keys in the map
   * @param dataLoader  Loadable for parsing values
   * @param folder      Folder to load data from
   */
  public RegistryDataMapLoader(String name, String folder, Registry<R> registry, RecordLoadable<D> dataLoader) {
    super(JsonHelper.DEFAULT_GSON, folder);
    this.name = name;
    this.registry = registry;
    this.dataLoader = dataLoader;
    this.folder = folder;
  }

  @Override
  protected void apply(Map<ResourceLocation,JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
    long time = System.nanoTime();
    // the final data map being built
    Map<R,D> dataMap = new HashMap<>();
    // map of location to data to prevent needing to parse the same element twice, saves memory
    Map<ResourceLocation,D> locationMap = new HashMap<>();
    // stack to prevent circular dependencies
    List<ResourceLocation> loadingStack = new ArrayList<>();

    // we only care about registry entry JSONs, so load by iterating the registry and seeing which ones have a JSON in the list
    // any in the list that are not in the registry may be used in parenting but won't be used directly.
    for (Entry<ResourceKey<R>,R> entry : registry.entrySet()) {
      ResourceLocation location = entry.getKey().location();
      JsonElement element = jsons.get(location);
      if (element != null) {
        try {
          JsonObject json = GsonHelper.convertToJsonObject(element, location.toString());
          // skip empty objects, its the way to disable it
          if (json.keySet().isEmpty()) {
            continue;
          }
          // process any parents to get the final JSON to parse
          JsonFile resolved = processParents(jsons, loadingStack, location, json);
          // clear the loading stack to prepare for the next parsing
          loadingStack.clear();

          // if we already parsed this element, use it. Otherwise parse and cache it
          D parsed = locationMap.get(resolved.location);
          if (parsed == null) {
            parsed = dataLoader.deserialize(resolved.json);
            locationMap.put(resolved.location, parsed);
          }

          // store the result into the final datamap
          dataMap.put(entry.getValue(), parsed);
        } catch (Exception e) {
          Mantle.logger.error("Failed to parse {} data for {}", name, location, e);
        }
      }
    }
    // update the datamap
    this.dataMap = dataMap;
    Mantle.logger.info("Finished loading {} {} in {} ms", dataMap.size(), name, (System.nanoTime() - time) / 1000000f);
  }

  /** Record paring a location to a return JSON object */
  private record JsonFile(ResourceLocation location, JsonObject json) {}

  /** Fetchs the parent from the JSON map for the given location */
  private JsonObject fetchParent(Map<ResourceLocation,JsonElement> jsons, ResourceLocation parentLocation, ResourceLocation location, List<ResourceLocation> loadingStack) {
    // first, ensure no circular dependency
    loadingStack.add(location);
    if (loadingStack.contains(parentLocation)) {
      throw new JsonSyntaxException("Caught circular dependency trying to resolve " + name + " parent for " + location + ", ignoring parent. Full stack " + loadingStack);
    }
    // next, check that the parent exists
    JsonElement element = jsons.get(parentLocation);
    if (element == null) {
      throw new JsonSyntaxException("Missing parent at " + parentLocation + " for " + name + ", used in " + location);
    }
    // finally, ensure its a JSON object
    return GsonHelper.convertToJsonObject(element, parentLocation.toString());
  }

  /**
   * Resolves the parent of the JSON
   * @param jsons         Map of all JSON elements. Elements in the map may be modified if they have parents along the loading chain.
   * @param location      Location of the JSON being parsed
   * @param loadingStack  Stack of all parents being resolved currently
   * @param json          JSON object being parsed. May be modified to include data from the parent.
   * @return Pair of the location of the resolved parent and its JSON data.
   */
  private JsonFile processParents(Map<ResourceLocation,JsonElement> jsons, List<ResourceLocation> loadingStack, ResourceLocation location, JsonObject json) {
    // process the parent until we no longer have one
    while (json.has("parent")) {
      ResourceLocation parentLocation = JsonHelper.getResourceLocation(json, "parent");
      JsonObject parentJson = fetchParent(jsons, parentLocation, location, loadingStack);

      // if the parent is the only key, treat this as a redirect, don't mutate the JSON, may have to resolve the parent again
      if (json.keySet().size() == 1) {
        json = parentJson;
        location = parentLocation;
      } else {
        // we have a parent but more than 1 key, means it's not an exact copy of the parent. Copy all data from the parent to the current element after resolving it
        parentJson = processParents(jsons, loadingStack, parentLocation, parentJson).json;

        // copy all keys from the parent to the current element
        for (Entry<String,JsonElement> entry : parentJson.entrySet()) {
          String key = entry.getKey();
          if (!json.has(key)) {
            json.add(key, entry.getValue());
          }
        }
        // remove parent key so next time this model is encountered we don't process the parent again
        json.remove("parent");
        break;
      }
    }
    // return the json, plus the location of the JSON for use in the caller's cache
    return new JsonFile(location, json);
  }

  /** Fetches a value from the registry, returning null if missing */
  @Nullable
  public D getData(R object) {
    return dataMap.get(object);
  }

  /** Fetches a value from the registry, returning the default value if missing */
  public D getData(R object, D defaultValue) {
    return dataMap.getOrDefault(object, defaultValue);
  }
}
