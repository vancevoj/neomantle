package slimeknights.mantle.registration.adapter;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import slimeknights.mantle.item.BlockTooltipItem;
import slimeknights.mantle.item.TooltipItem;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.FenceBuildingBlockObject;
import slimeknights.mantle.registration.object.WallBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Provides utility registration methods when registering itemblocks.
 * <p>
 * Items need their ID stamped into their properties before construction, so methods creating items take a constructor from properties
 * along with an operator creating the properties. Never share a properties instance between items.
 */
@SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue"})
public class ItemRegistryAdapter extends EnumRegistryAdapter<Item> {
  private final UnaryOperator<Item.Properties> defaultProps;

  /**
   * Registers a new item registry adapter with default mod ID and item properties
   * @param registry  Item registry instance
   */
  public ItemRegistryAdapter(Registry<Item> registry) {
    this(registry, null);
  }

  /**
   * Registers a new item registry adapter with default mod ID
   * @param registry      Item registry instance
   * @param defaultProps  Operator applied to the default item properties
   */
  public ItemRegistryAdapter(Registry<Item> registry, @Nullable UnaryOperator<Item.Properties> defaultProps) {
    super(registry);
    this.defaultProps = Objects.requireNonNullElse(defaultProps, UnaryOperator.identity());
  }

  /**
   * Registers a new item registry adapter with a specific mod ID
   * @param registry      Item registry instance
   * @param modid         Mod ID override
   * @param defaultProps  Operator applied to the default item properties
   */
  public ItemRegistryAdapter(Registry<Item> registry, String modid, @Nullable UnaryOperator<Item.Properties> defaultProps) {
    super(registry, modid);
    this.defaultProps = Objects.requireNonNullElse(defaultProps, UnaryOperator.identity());
  }

  /* Properties */

  /**
   * Creates item properties for the item with the given name
   * @param name   Item name
   * @param props  Operator applied to new properties
   * @return  Item properties with the ID set
   */
  protected Item.Properties props(String name, UnaryOperator<Item.Properties> props) {
    return RegistrationHelper.itemProperties(getResource(name), props);
  }

  /**
   * Creates item properties for a block item, which shares the name of the block
   * @param block  Block for the item
   * @param props  Operator applied to new properties
   * @return  Item properties with the ID and block description set
   */
  protected Item.Properties blockProps(Block block, UnaryOperator<Item.Properties> props) {
    return props.apply(RegistrationHelper.blockItemProperties(BuiltInRegistries.BLOCK.getKey(block), block));
  }

  /* Item helpers */

  /**
   * Registers an item, passing the constructor properties with the ID set
   * @param constructor  Item constructor
   * @param props        Operator applied to new properties
   * @param name         Item name
   * @param <T>          Item type
   * @return  Registered item
   */
  public <T extends Item> T register(Function<Properties,T> constructor, UnaryOperator<Item.Properties> props, String name) {
    return register(constructor.apply(props(name, props)), name);
  }

  /**
   * Registers a generic tooltip item using the default props
   * @param name  Item name
   * @return  Registered item
   */
  public TooltipItem registerDefault(String name) {
    return registerTooltip(defaultProps, name);
  }

  /**
   * Registers a generic tooltip item from the given props
   * @param props  Operator applied to new item properties
   * @param name   Item name
   * @return  Registered item
   */
  public TooltipItem registerTooltip(UnaryOperator<Item.Properties> props, String name) {
    return register(TooltipItem::new, props, name);
  }

  /**
   * Registers an item with the default properties
   * @param constructor  Item constructor
   * @param name         Item name
   * @param <T>          Item type
   * @return  Registered item
   */
  public <T extends Item> T registerDefault(Function<Properties,T> constructor, String name) {
    return register(constructor, defaultProps, name);
  }


  /* Standard block items */

  /** Registers a block item using the passed block as the name */
  protected  <I extends BlockItem> I register(I entry, Block name) {
    return this.register(entry, Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(name)));
  }

  /**
   * Registers a generic item block for a block.
   * If your block does not have its own item, just use this method to make it available as an item.
   * The item uses the same name as the block for registration.
   * The registered BlockItem has tooltip support by default, see {@link BlockTooltipItem}
   *
   * @param block The block you want to have an item for
   * @return The registered item for the block
   */
  public BlockItem registerDefaultBlockItem(Block block) {
    return registerBlockItem(block, defaultProps);
  }

  /**
   * Registers a block item using the given constructor and properties
   * @param block        Block instance
   * @param constructor  Constructor
   * @param props        Operator applied to new item properties, which have the ID and block description set
   * @param <T>          Result block item type
   * @return  Registered block item
   */
  public <T extends BlockItem> T registerBlockItem(Block block, BiFunction<Block,Properties,T> constructor, UnaryOperator<Item.Properties> props) {
    return register(constructor.apply(block, blockProps(block, props)), block);
  }

  /**
   * Registers a block item with default properties using the given constructor
   * @param block        Block instance
   * @param constructor  Constructor
   * @param <T>          Result block item type
   * @return  Registered block item
   */
  public <T extends BlockItem> T registerBlockItem(Block block, BiFunction<Block,Properties,T> constructor) {
    return registerBlockItem(block, constructor, defaultProps);
  }

  /**
   * Registers a tooltip block item with the given properties
   * @param block  The block you want to have an item for
   * @param props  Operator applied to new item properties
   */
  public BlockItem registerBlockItem(Block block, UnaryOperator<Item.Properties> props) {
    return registerBlockItem(block, BlockTooltipItem::new, props);
  }

  /**
   * Shortcut method to register your own BlockItem, registering with the same name as the block it represents.
   * The item properties must have the item ID set, see {@link RegistrationHelper#blockItemProperties(net.minecraft.resources.Identifier, Block)}.
   * @param blockItem Item block instance to register
   * @return Registered item block, should be the same as teh one passed in.
   */
  public <T extends BlockItem> T registerBlockItem(T blockItem) {
    return register(blockItem, blockItem.getBlock());
  }

  /* Block wrappers */

  /**
   * Registers block items for all entries in a building block object
   * @param object  Building block object instance
   */
  public void registerDefaultBlockItem(BuildingBlockObject object) {
    registerDefaultBlockItem(object.get());
    registerDefaultBlockItem(object.getSlab());
    registerDefaultBlockItem(object.getStairs());
  }

  /**
   * Registers block items for all entries in a wall building block object
   * @param object  Building block object instance
   */
  public void registerDefaultBlockItem(WallBuildingBlockObject object) {
    registerDefaultBlockItem((BuildingBlockObject)object);
    registerDefaultBlockItem(object.getWall());
  }

  /**
   * Registers block items for all entries in a fence building block object
   * @param object  Building block object instance
   */
  public void registerDefaultBlockItem(FenceBuildingBlockObject object) {
    registerDefaultBlockItem((BuildingBlockObject)object);
    registerDefaultBlockItem(object.getFence());
  }

  /**
   * Creates an operator applying the default properties and optionally a furnace fuel
   * @param isBurnable  If true, the item is a fuel
   * @param burnTime    Vanilla burn time provider
   * @return  Properties operator
   */
  private UnaryOperator<Item.Properties> fuel(boolean isBurnable, ResourceKey<ContextIntProvider> burnTime) {
    if (isBurnable) {
      return props -> defaultProps.apply(props).cookingFuel(burnTime);
    }
    return defaultProps;
  }

  /**
   * Registers block items for all entries in a fence building block object
   * @param object      Wood object instance
   * @param isBurnable  If true, the items are furnace fuels, using the burn times of the matching vanilla wooden blocks
   */
  public void registerDefaultBlockItem(WoodBlockObject object, boolean isBurnable) {
    // many of these are already burnable via tags, but simplier to set them all here
    UnaryOperator<Item.Properties> blocks = fuel(isBurnable, ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);

    // planks
    registerBlockItem(object.get(), BlockItem::new, blocks);
    registerBlockItem(object.getSlab(), BlockItem::new, fuel(isBurnable, ContextIntProviders.COOKING_TIME_WOOD_SLABS));
    registerBlockItem(object.getStairs(), BlockItem::new, blocks);
    registerBlockItem(object.getFence(), BlockItem::new, blocks);
    // logs and wood
    registerBlockItem(object.getLog(), BlockItem::new, blocks);
    registerBlockItem(object.getWood(), BlockItem::new, blocks);
    registerBlockItem(object.getStrippedLog(), BlockItem::new, blocks);
    registerBlockItem(object.getStrippedWood(), BlockItem::new, blocks);
    // doors
    registerBlockItem(object.getDoor(), DoubleHighBlockItem::new, fuel(isBurnable, ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE));
    registerBlockItem(object.getTrapdoor(), BlockItem::new, blocks);
    registerBlockItem(object.getFenceGate(), BlockItem::new, blocks);
    // redstone
    registerBlockItem(object.getPressurePlate(), BlockItem::new, blocks);
    registerBlockItem(object.getButton(), BlockItem::new, fuel(isBurnable, ContextIntProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL));
    // sign, does not use the default properties
    UnaryOperator<Item.Properties> sign = props -> {
      props.stacksTo(16).signText();
      return isBurnable ? props.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE) : props;
    };
    registerBlockItem(new StandingAndWallBlockItem(object.getSign(), object.getWallSign(), Direction.DOWN, sign.apply(RegistrationHelper.blockItemProperties(BuiltInRegistries.BLOCK.getKey(object.getSign()), object.getSign()))));
    registerBlockItem(new HangingSignItem(object.getHangingSign(), object.getWallHangingSign(), sign.apply(RegistrationHelper.blockItemProperties(BuiltInRegistries.BLOCK.getKey(object.getHangingSign()), object.getHangingSign()))));
  }

  /**
   * Registers block items for an enum object
   * @param enumObject  Enum object instance
   */
  public void registerDefaultBlockItem(EnumObject<?, ? extends Block> enumObject) {
    enumObject.values().forEach(this::registerDefaultBlockItem);
  }

  /**
   * Registers block items for an enum object
   * @param enumObject  Enum object instance
   * @param props       Operator applied to new item properties, called once per block
   */
  public <B extends Block> void registerBlockItem(EnumObject<?, B> enumObject, UnaryOperator<Item.Properties> props) {
    enumObject.values().forEach(block -> this.registerBlockItem(block, props));
  }

  /**
   * Registers block items for an enum object
   * @param enumObject  Enum object instance
   * @param blockItem   Block item constructor, receives properties with the ID and block description set
   */
  public <B extends Block> void registerBlockItem(EnumObject<?, B> enumObject, BiFunction<? super B, Item.Properties, ? extends BlockItem> blockItem) {
    enumObject.values().forEach(block -> this.registerBlockItem(blockItem.apply(block, blockProps(block, defaultProps))));
  }


  /* Misc */

  /**
   * Registers the bucket for a fluid
   * @param fluid     Fluid supplier
   * @param baseName  Fluid name, unfortunately cannot be fetched from the fluid as it does not exist yet
   * @return  Bucket instance
   */
  public BucketItem registerBucket(Supplier<? extends Fluid> fluid, String baseName) {
    String name = baseName + "_bucket";
    return register(new BucketItem(fluid.get(), props(name, RegistrationHelper.BUCKET_PROPS)), name);
  }

  /**
   * Registers a spawn egg for the entity type.
   * Spawn egg colors are no longer part of the item, they come from the egg's client item model.
   * @param type       Entity type supplier
   * @param baseName   Entity name, as it may or may not be present in the entity type
   * @return  Spawn egg item instance
   */
  public SpawnEggItem registerSpawnEgg(Supplier<? extends EntityType<? extends Mob>> type, String baseName) {
    String name = baseName + "_spawn_egg";
    return register(new SpawnEggItem(props(name, UnaryOperator.identity()).spawnEgg(type.get())), name);
  }
}
