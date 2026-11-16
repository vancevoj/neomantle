package slimeknights.mantle.fluid.texture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.fluid.InvertedFluidType;
import slimeknights.mantle.fluid.TextureFluidType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Manager for handling fluid textures.
 * <p>
 * 26.3: fluid textures and tints live in the vanilla fluid model, which is baked during the model manager's reload. To make
 * sure the models see the current textures, the JSON is loaded in {@link #prepareSharedState(SharedState)}, which runs
 * for every listener before any reload work starts. Tints read the color at render time, so they never go stale.
 */
public class FluidTextureManager implements PreparableReloadListener {
  /** Folder containing the logic */
  public static final String FOLDER = "mantle/fluid_texture";
  /** Key for the reload listener */
  public static final Identifier LISTENER_ID = Mantle.getResource("fluid_texture");

  /* Instance data */
  private static final FluidTextureManager INSTANCE = new FluidTextureManager();
  /** Map of fluid type to texture, replaced on reload. Volatile as fluid models are baked on a worker thread */
  private volatile Map<FluidType,FluidTexture> textures = Collections.emptyMap();
  /** Fallback texture instance */
  public static final FluidTexture FALLBACK = new FluidTexture(Identifier.parse("block/water_still"), Identifier.parse("block/water_flow"), null, null, 0, -1, -1, false, null, 0, 0);

  private FluidTextureManager() {}


  /**
   * Initializes this manager, registering it with the resource manager
   */
  public static void init(AddClientReloadListenersEvent event) {
    event.addListener(LISTENER_ID, INSTANCE);
  }

  @Override
  public void prepareSharedState(SharedState currentReload) {
    if (!ModLoader.hasErrors()) {
      load(currentReload.resourceManager());
    }
  }

  @Override
  public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor, PreparationBarrier preparationBarrier, Executor reloadExecutor) {
    // all loading happens in prepareSharedState, so fluid models baked during this reload see the new textures
    return preparationBarrier.wait(null);
  }

  /** Loads all fluid textures from the resource manager */
  private void load(ResourceManager resourceManager) {
    long time = System.nanoTime();
    // fetch JSONs
    Map<Identifier,JsonElement> jsons = new HashMap<>();
    SimpleJsonResourceReloadListener.scanDirectory(resourceManager, FileToIdConverter.json(FOLDER), JsonOps.INSTANCE, ExtraCodecs.JSON, jsons);

    // start building fluid type map
    Map<FluidType, FluidTexture> map = new HashMap<>();
    Registry<FluidType> fluidTypeRegistry = NeoForgeRegistries.FLUID_TYPES;


    for (Map.Entry<Identifier,JsonElement> entry : jsons.entrySet()) {
      Identifier id = entry.getKey();
      // first step is to find the matching fluid type, if there is none ignore the file
      FluidType type = fluidTypeRegistry.getValue(id);
      if (type == null || !id.equals(fluidTypeRegistry.getKey(type))) {
        Mantle.logger.debug("Ignoring fluid texture {} as no fluid type exists with that name", id);
      } else {
        try {
          // parse it if valid
          map.put(type, FluidTexture.deserialize(GsonHelper.convertToJsonObject(entry.getValue(), "fluid_texture")));
        } catch (RuntimeException e) {
          Mantle.logger.error("Failed to deserialize fluid texture {}", id, e);
        }
      }
    }
    this.textures = map;
    Mantle.logger.info("Loaded {} fluid textures in {} ms", map.size(), (System.nanoTime() - time) / 1000000f);
  }

  /** Gets the texture for the given fluid */
  public static FluidTexture getData(FluidType fluid) {
    return INSTANCE.textures.getOrDefault(fluid, FALLBACK);
  }

  /** Gets the still texture for the given fluid */
  public static Identifier getStillTexture(FluidType fluid) {
    return getData(fluid).still();
  }

  /** Gets the flowing texture for the given fluid */
  public static Identifier getFlowingTexture(FluidType fluid) {
    return getData(fluid).flowing();
  }

  /** Gets the overlay texture for the given fluid */
  @Nullable
  public static Identifier getOverlayTexture(FluidType fluid) {
    return getData(fluid).overlay();
  }

  /** Gets the camera texture for the given fluid */
  @Nullable
  public static Identifier getCameraTexture(FluidType fluid) {
    return getData(fluid).camera();
  }

  /** Gets the still texture for the given fluid */
  public static int getColor(FluidType fluid) {
    return getData(fluid).color();
  }


  /* Fluid models */

  /**
   * Checks if Mantle handles the client side of the given fluid type automatically.
   * @param type  Fluid type
   * @return  True if Mantle registers its fluid model and client extension
   */
  public static boolean isMantleFluidType(FluidType type) {
    return type instanceof TextureFluidType || type instanceof InvertedFluidType;
  }

  /**
   * Creates the vanilla fluid model for a fluid from its {@link FluidTexture}. Must be called after the textures are loaded,
   * which is the case during {@link RegisterFluidModelsEvent}.
   * @param fluid  Fluid to create the model for. If it is inverted (see {@link ClientInvertedFluidType#isInverted(Fluid)}), it uses the inverted flowing texture.
   * @param tint   Tint source for the fluid, null for no tint
   * @return  Unbaked fluid model
   */
  public static FluidModel.Unbaked createModel(Fluid fluid, @Nullable FluidTintSource tint) {
    FluidTexture texture = getData(fluid.getFluidType());
    Identifier flowing = texture.flowing();
    if (ClientInvertedFluidType.isInverted(fluid)) {
      flowing = ClientInvertedFluidType.getInvertedTexture(flowing);
    }
    boolean translucent = texture.translucent();
    Identifier overlay = texture.overlay();
    return new FluidModel.Unbaked(new Material(texture.still(), translucent), new Material(flowing, translucent), overlay == null ? null : new Material(overlay, translucent), tint);
  }

  /**
   * Creates the vanilla fluid model for a fluid from its {@link FluidTexture}, tinted with the texture color.
   * @param fluid  Fluid to create the model for
   * @return  Unbaked fluid model
   * @see #createModel(Fluid, FluidTintSource)
   */
  public static FluidModel.Unbaked createModel(Fluid fluid) {
    return createModel(fluid, new Tint(fluid.getFluidType()));
  }

  /**
   * Registers fluid models for all fluids with a Mantle fluid type that do not have a model yet.
   * Call at {@link net.neoforged.bus.api.EventPriority#LOWEST} so other mods can register their own models first.
   */
  public static void registerFluidModels(RegisterFluidModelsEvent event) {
    for (Fluid fluid : BuiltInRegistries.FLUID) {
      if (isMantleFluidType(fluid.getFluidType())) {
        try {
          event.register(createModel(fluid), fluid);
        } catch (IllegalStateException e) {
          // the event has no way to query existing models, a duplicate means another mod registered a custom model
          Mantle.logger.debug("Skipping Mantle fluid model for {}: {}", fluid, e.getMessage());
        }
      }
    }
  }

  /**
   * Registers client extensions for all Mantle fluid types that do not have one yet.
   * Call at {@link net.neoforged.bus.api.EventPriority#LOWEST} so other mods can register their own extensions first.
   */
  public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    for (FluidType type : NeoForgeRegistries.FLUID_TYPES) {
      if (isMantleFluidType(type) && !event.isFluidTypeRegistered(type)) {
        event.registerFluidType(type instanceof InvertedFluidType ? new ClientInvertedFluidType(type) : new ClientTextureFluidType(type), type);
      }
    }
  }

  /**
   * Fluid tint reading the color from the fluid texture at render time, so a resource reload never needs a new model.
   * @param type  Fluid type to fetch the color for
   */
  public record Tint(FluidType type) implements FluidTintSource {
    @Override
    public int color(FluidState state) {
      return getColor(type);
    }
  }
}
