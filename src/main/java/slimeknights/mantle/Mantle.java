package slimeknights.mantle;

import net.minecraft.util.Util;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;
import slimeknights.mantle.block.fluid.MobEffectLiquidBlock;
import slimeknights.mantle.client.ClientEvents;
import slimeknights.mantle.client.model.MantleModelEvents;
import slimeknights.mantle.command.MantleCommand;
import slimeknights.mantle.command.argument.ResourceOrTagKeyArgument;
import slimeknights.mantle.config.Config;
import slimeknights.mantle.data.predicate.block.BlockPredicate;
import slimeknights.mantle.data.predicate.block.BlockPropertiesPredicate;
import slimeknights.mantle.data.predicate.damage.DamageSourcePredicate;
import slimeknights.mantle.data.predicate.damage.DamageTypePredicate;
import slimeknights.mantle.data.predicate.damage.SourceAttackerPredicate;
import slimeknights.mantle.data.predicate.damage.SourceMessagePredicate;
import slimeknights.mantle.data.predicate.entity.BlockAtEntityPredicate;
import slimeknights.mantle.data.predicate.entity.HasEnchantmentEntityPredicate;
import slimeknights.mantle.data.predicate.entity.HasMobEffectPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.mantle.data.predicate.entity.MobTypePredicate;
import slimeknights.mantle.data.predicate.fluid.FluidPredicate;
import slimeknights.mantle.data.predicate.fluid.FluidTypePredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.mantle.datagen.MantleFluidTransferProvider;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.datagen.client.MantleFluidTooltipProvider;
import slimeknights.mantle.datagen.client.MantleSpriteSourceProvider;
import slimeknights.mantle.datagen.tag.MantleBlockTagProvider;
import slimeknights.mantle.datagen.tag.MantleFluidTagProvider;
import slimeknights.mantle.datagen.tag.MantleMenuTagProvider;
import slimeknights.mantle.fluid.transfer.EmptyFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.EmptyFluidWithNBTTransfer;
import slimeknights.mantle.fluid.transfer.EmptyPotionTransfer;
import slimeknights.mantle.fluid.transfer.FillFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.FillFluidWithNBTTransfer;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.item.LecternBookItem;
import slimeknights.mantle.loot.LootTableInjector;
import slimeknights.mantle.loot.MantleLoot;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.condition.MantleConditions;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.mantle.recipe.helper.TagPreference;
import slimeknights.mantle.recipe.ingredient.MantleIngredients;
import slimeknights.mantle.registration.MantleRegistrations;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.adapter.BlockEntityTypeRegistryAdapter;
import slimeknights.mantle.util.OffhandCooldownTracker;

import java.util.Objects;
import java.util.Set;

/**
 * Mantle
 *
 * Central mod object for Mantle
 *
 * @author Sunstrike <sun@sunstrike.io>
 */
@Mod(Mantle.modId)
public class Mantle {
  public static final String modId = "mantle";
  public static final Logger logger = LogManager.getLogger("Mantle");
  /** Namespace for common tags; 1.21/NeoForge uses the "c" convention (was "forge" in 1.20). */
  public static final String COMMON = "c";

  /* Instance of this mod, used for grabbing prototype fields */
  public static Mantle instance;

  /* Proxies for sides, used for graphics processing */
  public Mantle(net.neoforged.bus.api.IEventBus modBus, ModContainer container) {
    container.registerConfig(Type.CLIENT, Config.CLIENT_SPEC);
    // server configs are synced configs since 26.3, the file is now mantle-synced.toml
    container.registerConfig(Type.SYNCED, Config.SERVER_SPEC);

    FluidContainerTransferManager.INSTANCE.init();
    MantleTags.init();

    instance = this;
    modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, this::commonSetup);
    // GatherDataEvent is sealed in 26.3, listen to the subtypes. The build runs a single client data run, which fires only the client event
    modBus.addListener(EventPriority.NORMAL, false, GatherDataEvent.Client.class, this::gatherClientData);
    modBus.addListener(EventPriority.NORMAL, false, GatherDataEvent.Server.class, this::gatherServerData);
    modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, this::register);
    modBus.addListener(MantleNetwork::registerPackets);
    OffhandCooldownTracker.register(modBus);
    MantleConditions.init(modBus);
    MantleIngredients.init(modBus);
    MantleRecipes.init(modBus);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, LecternBookItem::interactWithBlock);
    // recipes are no longer synced by default since 26.3, send the types Mantle and its dependents need
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDatapackSyncEvent.class, RecipeHelper::sendRecipesToClient);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobEffectEvent.Remove.class, MobEffectLiquidBlock::preventRemovingWhileInside);

    if (FMLEnvironment.getDist() == Dist.CLIENT) {
      ClientEvents.onConstruct();
      MantleModelEvents.init(modBus);
    }
  }

  private void commonSetup(final FMLCommonSetupEvent event) {
    MantleCommand.init();
    OffhandCooldownTracker.init();
    TagPreference.init();
    LootTableInjector.init();
  }

  private void register(RegisterEvent event) {
    ResourceKey<?> key = event.getRegistryKey();
    if (key == Registries.RECIPE_SERIALIZER) {
      // recipe conditions and custom ingredient types are registered on the mod bus via
      // MantleConditions / MantleIngredients (CONDITION_CODECS / INGREDIENT_TYPES registries)

      // fluid container transfer
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyFluidContainerTransfer.ID, EmptyFluidContainerTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(FillFluidContainerTransfer.ID, FillFluidContainerTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyFluidWithNBTTransfer.ID, EmptyFluidWithNBTTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(FillFluidWithNBTTransfer.ID, FillFluidWithNBTTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyPotionTransfer.ID, EmptyPotionTransfer.DESERIALIZER);

      // predicates
      {
        // block predicates
        BlockPredicate.LOADER.register(getResource("requires_tool"), BlockPredicate.REQUIRES_TOOL.getLoader());
        BlockPredicate.LOADER.register(getResource("blocks_motion"), BlockPredicate.BLOCKS_MOTION.getLoader());
        BlockPredicate.LOADER.register(getResource("can_be_replaced"), BlockPredicate.CAN_BE_REPLACED.getLoader());
        BlockPredicate.LOADER.register(getResource("block_properties"), BlockPropertiesPredicate.LOADER);

        // item predicates
        ItemPredicate.LOADER.register(getResource("has_container"), ItemPredicate.HAS_CONTAINER.getLoader());
        ItemPredicate.LOADER.register(getResource("may_have_transfer"), ItemPredicate.MAY_HAVE_TRANSFER.getLoader());

        // fluid predicates
        FluidPredicate.LOADER.register(getResource("fluid_type"), FluidTypePredicate.LOADER);
        FluidPredicate.LOADER.register(getResource("is_source"), FluidPredicate.SOURCE.getLoader());
        FluidPredicate.LOADER.register(getResource("has_bucket"), FluidPredicate.HAS_BUCKET.getLoader());
        FluidPredicate.LOADER.register(getResource("lighter_than_air"), FluidPredicate.LIGHTER_THAN_AIR.getLoader());

        // entity predicates
        // simple
        LivingEntityPredicate.LOADER.register(getResource("fire_immune"), LivingEntityPredicate.FIRE_IMMUNE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("can_freeze"), LivingEntityPredicate.CAN_FREEZE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("water_sensitive"), LivingEntityPredicate.WATER_SENSITIVE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("on_fire"), LivingEntityPredicate.ON_FIRE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("is_freezing"), LivingEntityPredicate.IS_FREEZING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("is_in_powdered_snow"), LivingEntityPredicate.IS_IN_POWDERED_SNOW.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("on_ground"), LivingEntityPredicate.ON_GROUND.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("crouching"), LivingEntityPredicate.CROUCHING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("sprinting"), LivingEntityPredicate.SPRINTING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("blocking"), LivingEntityPredicate.BLOCKING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("elytra_flying"), LivingEntityPredicate.ELYTRA_FLYING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("eyes_in_water"), LivingEntityPredicate.EYES_IN_WATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("feet_in_water"), LivingEntityPredicate.FEET_IN_WATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("underwater"), LivingEntityPredicate.UNDERWATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("raining_at"), LivingEntityPredicate.RAINING.getLoader());
        // property
        LivingEntityPredicate.LOADER.register(getResource("mob_type"), MobTypePredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("has_enchantment"), HasEnchantmentEntityPredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("has_effect"), HasMobEffectPredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("block_at_entity"), BlockAtEntityPredicate.LOADER);
        // register mob types (former MobType values are now entity type tags)
        MobTypePredicate.MOB_TYPES.register(Identifier.parse("undead"), EntityTypeTags.UNDEAD);
        MobTypePredicate.MOB_TYPES.register(Identifier.parse("arthropod"), EntityTypeTags.ARTHROPOD);
        MobTypePredicate.MOB_TYPES.register(Identifier.parse("illager"), EntityTypeTags.ILLAGER);
        MobTypePredicate.MOB_TYPES.register(Identifier.parse("water"), EntityTypeTags.AQUATIC);

        // damage predicates
        // simple
        DamageSourcePredicate.LOADER.register(getResource("has_entity"), DamageSourcePredicate.HAS_ENTITY.getLoader());
        DamageSourcePredicate.LOADER.register(getResource("is_indirect"), DamageSourcePredicate.IS_INDIRECT.getLoader());
        DamageSourcePredicate.LOADER.register(getResource("can_protect"), DamageSourcePredicate.CAN_PROTECT.getLoader());
        // fields
        DamageSourcePredicate.LOADER.register(getResource("damage_type"), DamageTypePredicate.LOADER);
        DamageSourcePredicate.LOADER.register(getResource("message"), SourceMessagePredicate.LOADER);
        DamageSourcePredicate.LOADER.register(getResource("attacker"), SourceAttackerPredicate.LOADER);
      }
    }
    else if (key == Registries.BLOCK_ENTITY_TYPE) {
      BlockEntityTypeRegistryAdapter adapter = new BlockEntityTypeRegistryAdapter(Objects.requireNonNull(event.getRegistry(Registries.BLOCK_ENTITY_TYPE)));
      Set<Block> signs = MantleSignBlockEntity.buildSignBlocks();
      if (!signs.isEmpty()) {
        MantleRegistrations.SIGN = adapter.register(MantleSignBlockEntity::new, signs, "sign");
      }
      signs = MantleHangingSignBlockEntity.buildSignBlocks();
      if (!signs.isEmpty()) {
        MantleRegistrations.HANGING_SIGN = adapter.register(MantleHangingSignBlockEntity::new, signs, "hanging_sign");
      }
    }
    else if (key == Registries.COMMAND_ARGUMENT_TYPE) {
      ResourceOrTagKeyArgument.Info<?> info = new ResourceOrTagKeyArgument.Info<>();
      event.register(Registries.COMMAND_ARGUMENT_TYPE, getResource("resource_or_tag_key"), () -> info);
      ArgumentTypeInfos.registerByClass(RegistrationHelper.genericArgumentType(ResourceOrTagKeyArgument.class), info);
    }
    else {
      MantleLoot.registerGlobalLootModifiers(event);
    }
  }

  /** Adds providers for server data, called for both server and client data runs */
  private static void addServerProviders(GatherDataEvent event) {
    event.createProvider(MantleBlockTagProvider::new);
    event.createProvider(MantleFluidTagProvider::new);
    event.createProvider(MantleMenuTagProvider::new);
    event.createProvider(MantleFluidTransferProvider::new);
  }

  /** Gathers data for a server data run */
  private void gatherServerData(final GatherDataEvent.Server event) {
    addServerProviders(event);
  }

  /** Gathers data for a client data run. Like NeoForge, client runs generate both client and server data so a single run covers everything. */
  private void gatherClientData(final GatherDataEvent.Client event) {
    addServerProviders(event);
    event.createProvider(MantleFluidTooltipProvider::new);
    event.createProvider(MantleSpriteSourceProvider::new);
  }

  /**
   * Gets a resource location for Mantle
   * @param name  Name
   * @return  Resource location instance
   */
  public static Identifier getResource(String name) {
    return Identifier.fromNamespaceAndPath(modId, name);
  }

  /**
   * Gets a resource location for the common namespace, which is "forge" for 1.20 and "c" for 1.21.
   * @param name  Name
   * @return  Resource location instance
   */
  public static Identifier commonResource(String name) {
    return Identifier.fromNamespaceAndPath(COMMON, name);
  }

  /**
   * Makes a translation key for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @return  Translation key
   */
  public static String makeDescriptionId(String base, String name) {
    return Util.makeDescriptionId(base, getResource(name));
  }

  /**
   * Makes a translation text component for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @return  Translation key
   */
  public static MutableComponent makeComponent(String base, String name) {
    return Component.translatable(makeDescriptionId(base, name));
  }

  /**
   * Makes a translation text component for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @param args  Additional arguments to format strings
   * @return  Translation key
   */
  public static MutableComponent makeComponent(String base, String name, Object... args) {
    return Component.translatable(makeDescriptionId(base, name), args);
  }
}
