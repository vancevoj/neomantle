package slimeknights.mantle.client.model;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.connected.ConnectedBlockStateModel;
import slimeknights.mantle.client.model.connected.ConnectedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.model.util.ModelHelper;

/** Registers all model loaders, block state model types, item model types and model related reload listeners */
public final class MantleModelEvents {
  private MantleModelEvents() {}

  /**
   * Registers all model event listeners
   * @param modBus  Mantle mod event bus
   */
  public static void init(IEventBus modBus) {
    modBus.addListener(MantleModelEvents::registerModelLoaders);
    modBus.addListener(MantleModelEvents::registerBlockStateModels);
    modBus.addListener(MantleModelEvents::registerItemModels);
    modBus.addListener(MantleModelEvents::registerReloadListeners);
  }

  /** Registers model JSON loaders */
  private static void registerModelLoaders(ModelEvent.RegisterLoaders event) {
    // standard models - useful in resource packs for any model
    event.register(Mantle.getResource("connected"), ConnectedModel.LOADER);
    event.register(Mantle.getResource("item_layer"), MantleItemLayerModel.LOADER);
    event.register(Mantle.getResource("colored_block"), ColoredBlockModel.LOADER);
    event.register(Mantle.getResource("fallback"), FallbackModelLoader.INSTANCE);

    // NBT dynamic models - require specific data defined in the block/item to use
    event.register(Mantle.getResource("nbt_key"), NBTKeyModel.LOADER);
    event.register(Mantle.getResource("retextured"), RetexturedModel.LOADER);
  }

  /** Registers the dynamic block state model types, used in blockstate JSON with {@code "type"} */
  private static void registerBlockStateModels(RegisterBlockStateModels event) {
    event.registerModel(ConnectedBlockStateModel.ID, ConnectedBlockStateModel.Unbaked.CODEC);
    event.registerModel(RetexturedBlockStateModel.ID, RetexturedBlockStateModel.Unbaked.CODEC);
  }

  /** Registers the dynamic item model types, used in client item JSON with {@code "type"} */
  private static void registerItemModels(RegisterItemModelsEvent event) {
    event.register(RetexturedItemModel.ID, RetexturedItemModel.Unbaked.MAP_CODEC);
    event.register(NBTKeyItemModel.ID, NBTKeyItemModel.Unbaked.MAP_CODEC);
  }

  /** Registers reload listeners clearing model caches */
  private static void registerReloadListeners(AddClientReloadListenersEvent event) {
    event.addListener(Mantle.getResource("model_helper"), ModelHelper.LISTENER);
    event.addListener(Mantle.getResource("texture_colors"), TextureColorHelper.RELOAD_LISTENER);
    event.addListener(Mantle.getResource("texture_maps"), TextureMapManager.INSTANCE);
    // caches hold onto block models and sprites, so clear them after models reload
    event.addDependency(VanillaClientListeners.MODELS, Mantle.getResource("model_helper"));
    event.addDependency(VanillaClientListeners.MODELS, Mantle.getResource("texture_colors"));
    event.addDependency(VanillaClientListeners.MODELS, Mantle.getResource("texture_maps"));
  }
}
