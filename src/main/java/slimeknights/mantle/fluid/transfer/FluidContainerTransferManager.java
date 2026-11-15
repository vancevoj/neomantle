package slimeknights.mantle.fluid.transfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonParseException;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition.IContext;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.gson.GenericRegisteredSerializer;
import slimeknights.mantle.data.gson.ResourceLocationSerializer;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Logic for filling and emptying fluid containers that are not fluid handlers */
@Log4j2
public class FluidContainerTransferManager extends SimpleJsonResourceReloadListener<JsonElement> {
  /** Map of all modifier types that are expected to load in data packs */
  public static final GenericRegisteredSerializer<IFluidContainerTransfer> TRANSFER_LOADERS = new GenericRegisteredSerializer<>();
  /** Folder for saving the logic */
  public static final String FOLDER = "mantle/fluid_transfer";
  /** GSON instance */
  public static final Gson GSON = (new GsonBuilder())
    .registerTypeAdapter(Identifier.class, ResourceLocationSerializer.resourceLocation("minecraft"))
    .registerTypeHierarchyAdapter(IFluidContainerTransfer.class, TRANSFER_LOADERS)
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();
  /** Singleton instance of the manager */
  public static final FluidContainerTransferManager INSTANCE = new FluidContainerTransferManager();
  /** Loadable context while parsing transfers, see {@link #getLoadContext()} */
  private static TypedMap loadContext = TypedMap.EMPTY;

  /** List of loaded transfer logic, only exists serverside */
  private List<IFluidContainerTransfer> transfers = Collections.emptyList();

  /** Set of all items that match a recipe, exists on both sides */
  @Setter @Nullable
  private Set<Item> containerItems = Collections.emptySet();

  private FluidContainerTransferManager() {
    super(ExtraCodecs.JSON, FileToIdConverter.json(FOLDER));
  }

  /** Lazily initializes the set of container items */
  protected Set<Item> getContainerItems() {
    if (this.containerItems == null) {
      List<Item> builder = new ArrayList<>();
      Consumer<Item> consumer = builder::add;
      for (IFluidContainerTransfer transfer : transfers) {
        transfer.addRepresentativeItems(consumer);
      }
      this.containerItems = Set.copyOf(builder);
    }
    return this.containerItems;
  }

  /** For internal use only */
  public void init() {
    // conditions under "neoforge:conditions" are evaluated by the NeoForge reload listener before apply, using the context it injects
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddServerReloadListenersEvent.class, e -> e.addListener(Mantle.getResource("fluid_container_transfer"), this));
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDatapackSyncEvent.class, e -> JsonHelper.syncPackets(e, MantleNetwork.INSTANCE, new FluidContainerTransferPacket(this.getContainerItems())));
  }

  /** Loads transfer from JSON */
  @Nullable
  private IFluidContainerTransfer loadFluidTransfer(Identifier key, JsonObject json) {
    try {
      return GSON.fromJson(json, IFluidContainerTransfer.class);
    } catch (JsonParseException e) {
      log.error("Failed to load fluid container transfer info from {}", key, e);
    }
    return null;
  }

  /**
   * Gets the loadable context to use while parsing transfers from {@link #GSON}, containing the registries and condition context of the reload.
   * Since 1.21.2, item tags in ingredients can only be resolved through registry aware ops, so transfer deserializers should pass this to their loadables.
   * Outside of parsing, this is {@link TypedMap#EMPTY}.
   */
  public static TypedMap getLoadContext() {
    return loadContext;
  }

  /** Builds the load context from the registries and conditions provided to this listener */
  private TypedMap createLoadContext() {
    TypedMapBuilder builder = TypedMapBuilder.builder();
    HolderLookup.Provider registries = getRegistryLookup();
    if (registries != RegistryAccess.EMPTY) {
      builder.put(ContextKey.REGISTRIES, registries);
    }
    IContext conditions = getContext();
    if (conditions != IContext.EMPTY) {
      builder.put(ContextKey.CONDITION_CONTEXT, conditions);
    }
    return builder.build();
  }

  @Override
  protected void apply(Map<Identifier,JsonElement> splashList, ResourceManager manager, ProfilerFiller profiler) {
    long time = System.nanoTime();
    loadContext = createLoadContext();
    try {
      this.transfers = splashList.entrySet().stream()
                                 .map(entry -> loadFluidTransfer(entry.getKey(), entry.getValue().getAsJsonObject()))
                                 .filter(Objects::nonNull)
                                 .toList();
    } finally {
      loadContext = TypedMap.EMPTY;
    }
    this.containerItems = null;
    log.info("Loaded {} dynamic modifiers in {} ms", transfers.size(), (System.nanoTime() - time) / 1000000f);
  }

  /**
   * Checks if the given stack could possibly match, used client side to determine if the fluid transfer falls back to opening the UI
   * @param item  Item to check
   * @return  True if a match is possible, basically just checks item ID
   */
  public boolean mayHaveTransfer(ItemLike item) {
    return getContainerItems().contains(item.asItem());
  }

  /**
   * Checks if the given stack could possibly match, used client side to determine if the fluid transfer falls back to opening the UI
   * @param stack  Stack to check
   * @return  True if a match is possible, basically just checks item ID
   */
  public boolean mayHaveTransfer(ItemStack stack) {
    return getContainerItems().contains(stack.getItem());
  }

  /** Gets the transfer for the given item and fluid, or null if its not a valid item and fluid */
  @Nullable
  public IFluidContainerTransfer getTransfer(ItemStack stack, FluidStack fluid) {
    for (IFluidContainerTransfer transfer : transfers) {
      if (transfer.matches(stack, fluid)) {
        return transfer;
      }
    }
    return null;
  }

  /**
   * Wraps a record loadable so that when registered in {@link #TRANSFER_LOADERS} and deserialized through {@link #GSON}, it parses using {@link #getLoadContext()}.
   * The plain {@link com.google.gson.JsonDeserializer} entry point of loadables otherwise uses an empty context.
   */
  public static <T> RecordLoadable<T> withLoadContext(RecordLoadable<T> loadable) {
    return new ContextLoadable<>(loadable);
  }

  /** Record loadable that uses {@link #getLoadContext()} when used as a GSON deserializer */
  private record ContextLoadable<T>(RecordLoadable<T> loadable) implements RecordLoadable<T> {
    @Override
    public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
      return loadable.convert(json, type.getTypeName(), getLoadContext());
    }

    @Override
    public T deserialize(JsonObject json, TypedMap context) {
      return loadable.deserialize(json, context);
    }

    @Override
    public void serialize(T object, JsonObject json) {
      loadable.serialize(object, json);
    }

    @Override
    public T decode(FriendlyByteBuf buffer, TypedMap context) {
      return loadable.decode(buffer, context);
    }

    @Override
    public void encode(FriendlyByteBuf buffer, T value) {
      loadable.encode(buffer, value);
    }
  }
}
