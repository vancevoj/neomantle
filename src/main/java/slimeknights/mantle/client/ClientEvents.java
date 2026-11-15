package slimeknights.mantle.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.block.GaugeBlock;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.repository.FileRepository;
import slimeknights.mantle.client.book.structure.StructurePreviewRenderer;
import slimeknights.mantle.client.render.FluidModel;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.mantle.command.client.MantleClientCommand;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.fluid.texture.FluidTextureManager;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;
import slimeknights.mantle.recipe.helper.ClientRecipeEvents;
import slimeknights.mantle.registration.MantleRegistrations;
import slimeknights.mantle.util.OffhandCooldownTracker;
import slimeknights.mantle.util.RegistryHelper;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Mantle.modId, value = Dist.CLIENT)
public class ClientEvents {
  /** Called on construct to initiatlize things that need early entry */
  public static void onConstruct() {
    // recipes only reach the client through the NeoForge recipe sync, cache them for books and other client lookups
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RecipesReceivedEvent.class, ClientRecipeEvents::onRecipesReceived);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class, ClientRecipeEvents::onLoggingOut);
  }

  /**
   * Registers the sign renderers for Mantle's sign block entities.
   * In 26.3 the sign board itself is a block model, these renderers only draw the text.
   */
  @SuppressWarnings("ConstantConditions")
  @SubscribeEvent
  static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
    if (MantleRegistrations.SIGN != null) {
      event.registerBlockEntityRenderer(MantleRegistrations.SIGN, StandingSignRenderer::new);
    }
    if (MantleRegistrations.HANGING_SIGN != null) {
      event.registerBlockEntityRenderer(MantleRegistrations.HANGING_SIGN, HangingSignRenderer::new);
    }
  }

  @SuppressWarnings("removal")
  @SubscribeEvent
  static void registerListeners(AddClientReloadListenersEvent event) {
    // books
    event.addListener(Mantle.getResource("books"), new BookLoader());
    // rendering data
    ResourceColorManager.init(event);
    FluidTooltipHandler.init(event);
    FluidTextureManager.init(event);
    event.addListener(Mantle.getResource("block_fluids"), FluidModel.REGISTRY);
    event.addListener(Mantle.getResource("render_items"), RenderItem.REGISTRY);
    event.addListener(Mantle.getResource("item_lists"), RenderItem.STATE_REGISTRY);
  }

  /**
   * Registers {@link slimeknights.mantle.fluid.texture.ClientTextureFluidType} for Mantle fluid types.
   * Runs last so other mods may register their own extensions for Mantle fluid types first.
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    FluidTextureManager.registerClientExtensions(event);
  }

  /**
   * Registers fluid models for Mantle fluid types from their fluid textures.
   * Runs last so other mods may register their own models for Mantle fluid types first.
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  static void registerFluidModels(RegisterFluidModelsEvent event) {
    FluidTextureManager.registerFluidModels(event);
  }

  @SubscribeEvent
  static void clientSetup(FMLClientSetupEvent event) {
    // 26.3: signs are block models and sign GUI textures are looked up by wood type name, so wood types need no client registration
    BookLoader.registerBook(Mantle.getResource("test"), new FileRepository(Mantle.getResource("books/test")));
    MantleClientCommand.init();
  }

  @SubscribeEvent
  static void commonSetup(FMLCommonSetupEvent event) {
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiLayerEvent.Post.class, ClientEvents::renderOffhandAttackIndicator);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiLayerEvent.Post.class, ClientEvents::renderGaugeTooltip);
  }

  /**
   * Registers the custom heart renderer as a wrapper around the vanilla player health GUI layer.
   * Mirrors the 1.20 behavior of {@code MinecraftForge.EVENT_BUS.register(new ExtraHeartRenderHandler())},
   * which took over heart rendering by cancelling and redrawing the vanilla hearts.
   */
  @SubscribeEvent
  static void registerGuiLayers(RegisterGuiLayersEvent event) {
    ExtraHeartRenderHandler.register(event);
  }

  /** Book structure pages render their 3D preview through picture in picture */
  @SubscribeEvent
  static void registerPictureInPicture(RegisterPictureInPictureRenderersEvent event) {
    StructurePreviewRenderer.register(event);
  }

  /** Vanilla attack indicator sprites, split out of the old GUI_ICONS atlas in 1.21 */
  private static final Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
  private static final Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
  private static final Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
  private static final Identifier HOTBAR_ATTACK_INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

  // registered with the game bus
  private static void renderOffhandAttackIndicator(RenderGuiLayerEvent.Post event) {
    // must have a player, not be in spectator, and have the indicator enabled
    Minecraft minecraft = Minecraft.getInstance();
    Options settings = minecraft.options;
    AttackIndicatorStatus indicator = settings.attackIndicator().get();
    if (minecraft.player == null || minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR || indicator == AttackIndicatorStatus.OFF) {
      return;
    }

    // only care about hotbar and crosshair
    Identifier overlay = event.getName();
    // will be true for hotbar, false for crosshair
    boolean isHotbar = VanillaGuiLayers.HOTBAR.equals(overlay);
    if (!isHotbar && !VanillaGuiLayers.CROSSHAIR.equals(overlay)) {
      return;
    }

    // fetch the current cooldown
    OffhandCooldownTracker tracker = OffhandCooldownTracker.get(minecraft.player);
    if (tracker == null) {
      return;
    }
    float cooldown = tracker.getCooldown();
    if (cooldown >= 1.0f) {
      return;
    }

    // show attack indicator
    GuiGraphicsExtractor graphics = event.getGuiGraphics();
    switch (indicator) {
      case CROSSHAIR:
        if (!isHotbar && minecraft.options.getCameraType().isFirstPerson()) {
          // vanilla skips the 2D crosshair when the 3D debug crosshair is shown
          if (!minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
            // mostly cloned from vanilla attack indicator, the crosshair pipeline inverts the colors behind it
            int scaledHeight = minecraft.getWindow().getGuiScaledHeight();
            // integer division makes this a pain to line up, there might be a simplier version of this formula but I cannot think of one
            int y = (scaledHeight / 2) - 14 + (2 * (scaledHeight % 2));
            int x = minecraft.getWindow().getGuiScaledWidth() / 2 - 8;
            int width = (int)(cooldown * 17.0F);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4);
            if (width > 0) {
              graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, width, 4);
            }
          }
        }
        break;
      case HOTBAR:
        if (isHotbar && minecraft.getCameraEntity() == minecraft.player) {
          int centerWidth = minecraft.getWindow().getGuiScaledWidth() / 2;
          int y = minecraft.getWindow().getGuiScaledHeight() - 20;
          int x;
          // opposite of the vanilla hand location, extra bit to offset past the offhand slot
          if (minecraft.player.getMainArm() == HumanoidArm.RIGHT) {
            x = centerWidth - 91 - 22 - 32;
          } else {
            x = centerWidth + 91 + 6 + 32;
          }
          int l1 = (int)(cooldown * 19.0F);
          // progress grows from the bottom
          graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_BACKGROUND, x, y, 18, 18);
          if (l1 > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_PROGRESS, 18, 18, 0, 18 - l1, x, y + 18 - l1, 18, l1);
          }
        }
        break;
    }
  }



  /** Renders the tooltip when targeting the gauge block */
  private static void renderGaugeTooltip(RenderGuiLayerEvent.Post event) {
    if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
      return;
    }
    // must not be in a screen, though chat is fine
    Minecraft minecraft = Minecraft.getInstance();
    Screen screen = minecraft.gui.screen();
    if (screen != null && screen.getClass() != ChatScreen.class) {
      return;
    }
    // must have a hit result
    if (minecraft.level == null || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
      return;
    }
    BlockHitResult blockHit = (BlockHitResult) minecraft.hitResult;
    BlockPos pos = blockHit.getBlockPos();

    // must be targeting a gauge
    BlockState targeted = minecraft.level.getBlockState(blockHit.getBlockPos());
    if (!targeted.is(MantleTags.Blocks.GAUGES)) {
      return;
    }
    BlockEntity gaugeContainer;
    BlockPos gaugePos;
    Direction side;
    if (targeted.is(MantleTags.Blocks.ATTACHED_GAUGES)) {
      side = targeted.getValue(BlockStateProperties.FACING);
      gaugePos = pos.relative(side.getOpposite());
    } else {
      side = blockHit.getDirection();
      gaugePos = pos;
    }
    gaugeContainer = minecraft.level.getBlockEntity(gaugePos);
    // must have a block entity behind the gauge that is not blacklisted
    if (gaugeContainer == null || RegistryHelper.contains(BuiltInRegistries.BLOCK_ENTITY_TYPE, MantleTags.BlockEntities.GAUGE_BLACKLIST, gaugeContainer.getType())) {
      return;
    }
    // block entity must have a fluid handler; capabilities are queried through the level and return null if absent
    ResourceHandler<FluidResource> handler = minecraft.level.getCapability(Capabilities.Fluid.BLOCK, gaugePos, side);
    if (handler == null || handler.size() <= 0) {
      return;
    }
    // if the fluid is empty, just render the capacity
    FluidStack fluid = FluidUtil.getStack(handler, 0);
    int capacity = handler.getCapacityAsInt(0, handler.getResource(0));
    List<Component> tooltip;
    if (fluid.isEmpty()) {
      tooltip = List.of(GaugeBlock.formatCapacity(capacity));
    } else if (RegistryHelper.contains(BuiltInRegistries.BLOCK_ENTITY_TYPE, MantleTags.BlockEntities.HIDES_GAUGE_AMOUNT, gaugeContainer.getType())) {
      // in the tag, don't show capacity
      Identifier id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
      tooltip = new ArrayList<>(3);
      tooltip.add(fluid.getHoverName());
      FluidTooltipHandler.appendAdvanced(id, tooltip);
      tooltip.add(GaugeBlock.formatCapacity(capacity).withStyle(ChatFormatting.GRAY));
      tooltip.add(FluidTooltipHandler.formatModName(id));
    } else {
      // render full fluid tooltip
      tooltip = FluidTooltipHandler.getFluidTooltip(fluid);
    }

    // the deferred tooltip only renders inside screens, so draw it directly on a new stratum above the HUD
    GuiGraphicsExtractor graphics = event.getGuiGraphics();
    int x = graphics.guiWidth() / 2;
    int y = graphics.guiHeight() / 2;
    List<ClientTooltipComponent> components = ClientHooks.gatherTooltipComponents(ItemStack.EMPTY, tooltip, x, graphics.guiWidth(), graphics.guiHeight(), minecraft.font);
    graphics.nextStratum();
    graphics.tooltip(minecraft.font, components, x, y, DefaultTooltipPositioner.INSTANCE, null, false);
  }
}
