package slimeknights.mantle.registration.deferred;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.block.MantleCeilingHangingSignBlock;
import slimeknights.mantle.block.MantleStandingSignBlock;
import slimeknights.mantle.block.MantleWallHangingSignBlock;
import slimeknights.mantle.block.MantleWallSignBlock;
import slimeknights.mantle.block.StrippableLogBlock;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.EnumObject.Builder;
import slimeknights.mantle.registration.object.FenceBuildingBlockObject;
import slimeknights.mantle.registration.object.ItemObject;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.mantle.registration.object.WallBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject.WoodVariant;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Deferred register to handle registering blocks with possible item forms.
 * <p>
 * Blocks and items need their ID stamped into their properties before construction, so every register method takes a constructor from properties
 * plus a supplier creating new properties. The supplier must return a new instance each call as blocks keep their properties object.
 * Block items are passed item properties that already have the ID and the block description prefix set.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class BlockDeferredRegister extends DeferredRegisterWrapper<Block> {
  protected final SynchronizedDeferredRegister<Item> itemRegister;
  public BlockDeferredRegister(String modID) {
    super(Registries.BLOCK, modID);
    this.itemRegister = SynchronizedDeferredRegister.create(Registries.ITEM, modID);
  }

  @Override
  public void register(IEventBus bus) {
    super.register(bus);
    itemRegister.register(bus);
  }

  /** Creates properties for a potted plant. Matches the vanilla flower pot except for the push reaction. */
  private static BlockBehaviour.Properties pottedProps() {
    return BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED);
  }

  /**
   * Creates properties for the wall variant of a sign, which drops the standing sign and shares its name. Matches vanilla wall signs.
   * @param props     Base properties
   * @param standing  Standing sign block
   * @return  Properties for the wall sign
   */
  public static BlockBehaviour.Properties wallVariant(BlockBehaviour.Properties props, Block standing) {
    return props.overrideLootTable(standing.getLootTable()).overrideDescription(standing.getDescriptionId());
  }


  /* Blocks with no items */

  /**
   * Registers a block with the block registry
   * @param name   Block ID
   * @param block  Block constructor, receives the properties with the ID set
   * @param props  Supplier creating new block properties
   * @param <B>    Block class
   * @return  Block registry object
   */
  public <B extends Block> DeferredHolder<Block, B> registerNoItem(String name, Function<BlockBehaviour.Properties, ? extends B> block, Supplier<BlockBehaviour.Properties> props) {
    return register.register(name, id -> block.apply(RegistrationHelper.blockProperties(id, props)));
  }

  /**
   * Registers a block with the block registry
   * @param name   Block ID
   * @param props  Supplier creating new block properties
   * @return  Block registry object
   */
  public DeferredHolder<Block, Block> registerNoItem(String name, Supplier<BlockBehaviour.Properties> props) {
    return registerNoItem(name, Block::new, props);
  }


  /* Block item pairs */

  /**
   * Registers a block item for the given block. The item is passed properties with the ID and block description prefix set.
   * @param name   Item ID
   * @param block  Block for the item
   * @param item   Block item constructor
   * @param <B>    Block type
   * @return  Item registry object
   */
  protected <B extends Block> DeferredHolder<Item, BlockItem> registerBlockItem(String name, Supplier<? extends B> block, BiFunction<? super B, Item.Properties, ? extends BlockItem> item) {
    return itemRegister.register(name, id -> {
      B value = block.get();
      return item.apply(value, RegistrationHelper.blockItemProperties(id, value));
    });
  }

  /**
   * Registers a block with the block registry, using the function for the BlockItem
   * @param name   Block ID
   * @param block  Block constructor, receives the properties with the ID set
   * @param props  Supplier creating new block properties
   * @param item   Function to create a BlockItem from a Block and item properties
   * @param <B>    Block class
   * @return  Block item registry object pair
   */
  public <B extends Block> ItemObject<B> register(String name, Function<BlockBehaviour.Properties, ? extends B> block, Supplier<BlockBehaviour.Properties> props, BiFunction<? super B, Item.Properties, ? extends BlockItem> item) {
    DeferredHolder<Block, B> blockObj = registerNoItem(name, block, props);
    registerBlockItem(name, blockObj, item);
    return new ItemObject<>(blockObj);
  }

  /**
   * Registers a block with the block registry, using the function for the BlockItem
   * @param name   Block ID
   * @param props  Supplier creating new block properties
   * @param item   Function to create a BlockItem from a Block and item properties
   * @return  Block item registry object pair
   */
  public ItemObject<Block> register(String name, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    return register(name, Block::new, props, item);
  }


  /* Building */

  /**
   * Registers a building block with slabs and stairs, using a custom block. Slabs and stairs copy the properties of the built block.
   * @param name   Block name
   * @param block  Block constructor
   * @param props  Supplier creating new block properties
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public BuildingBlockObject registerBuilding(String name, Function<BlockBehaviour.Properties, ? extends Block> block, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    ItemObject<Block> blockObj = register(name, block, props, item);
    Supplier<BlockBehaviour.Properties> copy = () -> BlockBehaviour.Properties.ofFullCopy(blockObj.get());
    return new BuildingBlockObject(
        blockObj,
        this.register(name + "_slab", SlabBlock::new, copy, item),
        this.register(name + "_stairs", p -> new StairBlock(blockObj.get().defaultBlockState(), p), copy, item));
  }

  /**
   * Registers a block with slab, and stairs
   * @param name      Name of the block
   * @param props     Supplier creating new block properties, called once per block
   * @param item      Function to get an item from the block
   * @return  BuildingBlockObject class that returns different block types
   */
  public BuildingBlockObject registerBuilding(String name, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    ItemObject<Block> blockObj = register(name, props, item);
    return new BuildingBlockObject(blockObj,
      register(name + "_slab", SlabBlock::new, props, item),
      register(name + "_stairs", p -> new StairBlock(blockObj.get().defaultBlockState(), p), props, item)
    );
  }

  /**
   * Registers a building block with slabs, stairs and wall, using a custom block
   * @param name   Block name
   * @param block  Block constructor
   * @param props  Supplier creating new block properties
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public WallBuildingBlockObject registerWallBuilding(String name, Function<BlockBehaviour.Properties, ? extends Block> block, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, block, props, item);
    return new WallBuildingBlockObject(obj, this.register(name + "_wall", WallBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(obj.get()), item));
  }

  /**
   * Registers a block with slab, stairs, and wall
   * @param name      Name of the block
   * @param props     Supplier creating new block properties, called once per block
   * @param item      Function to get an item from the block
   * @return  StoneBuildingBlockObject class that returns different block types
   */
  public WallBuildingBlockObject registerWallBuilding(String name, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    return new WallBuildingBlockObject(
      registerBuilding(name, props, item),
      register(name + "_wall", WallBlock::new, props, item)
    );
  }

  /**
   * Registers a building block with slabs, stairs and fence, using a custom block
   * @param name   Block name
   * @param block  Block constructor
   * @param props  Supplier creating new block properties
   * @param item   Item block, used for all variants
   * @return  Building block object
   */
  public FenceBuildingBlockObject registerFenceBuilding(String name, Function<BlockBehaviour.Properties, ? extends Block> block, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    BuildingBlockObject obj = this.registerBuilding(name, block, props, item);
    return new FenceBuildingBlockObject(obj, this.register(name + "_fence", FenceBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(obj.get()), item));
  }

  /**
   * Registers a block with slab, stairs, and fence
   * @param name      Name of the block
   * @param props     Supplier creating new block properties, called once per block
   * @param item      Function to get an item from the block
   * @return  WoodBuildingBlockObject class that returns different block types
   */
  public FenceBuildingBlockObject registerFenceBuilding(String name, Supplier<BlockBehaviour.Properties> props, BiFunction<? super Block, Item.Properties, ? extends BlockItem> item) {
    return new FenceBuildingBlockObject(
      registerBuilding(name, props, item),
      register(name + "_fence", FenceBlock::new, props, item)
    );
  }


  /* Wood */

  /**
   * Creates a block item function that optionally makes the item a furnace fuel
   * @param flammable  If true, the item will be a fuel
   * @param burnTime   Vanilla burn time provider to use
   * @return  Block item function
   */
  private static BiFunction<Block, Item.Properties, BlockItem> fuelItem(boolean flammable, ResourceKey<ContextIntProvider> burnTime) {
    if (flammable) {
      return (block, props) -> new BlockItem(block, props.cookingFuel(burnTime));
    }
    return BlockItem::new;
  }

  /**
   * Registers a new wood object
   * @param name             Name of the wood object
   * @param behaviorCreator  Logic to create the behavior, must return a new properties instance each call
   * @param flammable        If true, this wood type is flammable
   * @return Wood object
   */
  public WoodBlockObject registerWood(String name, Function<WoodVariant,BlockBehaviour.Properties> behaviorCreator, boolean flammable) {
    BlockSetType setType = new BlockSetType(resourceName(name));
    WoodType woodType = new WoodType(resourceName(name), setType);
    BlockSetType.register(setType);
    RegistrationHelper.registerWoodType(woodType);

    // many of these are already burnable via tags, but simpler to set them all here
    // burn times use the vanilla fuel providers for the matching wooden blocks, so furnace types scale them like vanilla wood
    BiFunction<Block, Item.Properties, BlockItem> woodBlockItem = fuelItem(flammable, ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);
    BiFunction<Block, Item.Properties, BlockItem> woodSlabItem = fuelItem(flammable, ContextIntProviders.COOKING_TIME_WOOD_SLABS);
    BiFunction<Block, Item.Properties, BlockItem> tallItem = (block, props) -> new DoubleHighBlockItem(block, flammable ? props.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE) : props);
    UnaryOperator<Item.Properties> signFuel = props -> flammable ? props.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE) : props;

    // planks
    BuildingBlockObject planks = registerBuilding(name + "_planks",
      () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f),
      (block, props) -> (block instanceof SlabBlock ? woodSlabItem : woodBlockItem).apply(block, props));
    ItemObject<FenceBlock> fence = register(name + "_fence", FenceBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(planks.get()).forceSolidOn(), woodBlockItem);
    // logs and wood
    Supplier<BlockBehaviour.Properties> strippedProps = () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f);
    ItemObject<RotatedPillarBlock> strippedLog = register("stripped_" + name + "_log", RotatedPillarBlock::new, strippedProps, woodBlockItem);
    ItemObject<RotatedPillarBlock> strippedWood = register("stripped_" + name + "_wood", RotatedPillarBlock::new, strippedProps, woodBlockItem);
    ItemObject<RotatedPillarBlock> log = register(name + "_log", props -> new StrippableLogBlock(strippedLog, props), () -> behaviorCreator.apply(WoodVariant.LOG).instrument(NoteBlockInstrument.BASS).strength(2.0f), woodBlockItem);
    ItemObject<RotatedPillarBlock> wood = register(name + "_wood", props -> new StrippableLogBlock(strippedWood, props), () -> behaviorCreator.apply(WoodVariant.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0f), woodBlockItem);

    // doors
    ItemObject<DoorBlock> door = register(name + "_door", props -> new DoorBlock(setType, props), () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED), tallItem);
    ItemObject<TrapDoorBlock> trapdoor = register(name + "_trapdoor", props -> new TrapDoorBlock(setType, props), () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never), woodBlockItem);
    ItemObject<FenceGateBlock> fenceGate = register(name + "_fence_gate", props -> new FenceGateBlock(woodType, props), () -> BlockBehaviour.Properties.ofFullCopy(fence.get()), woodBlockItem);
    // redstone
    Supplier<BlockBehaviour.Properties> redstoneProps = () -> behaviorCreator.apply(WoodVariant.PLANKS).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().pushReaction(PushReaction.POPPED).strength(0.5F);
    ItemObject<PressurePlateBlock> pressurePlate = register(name + "_pressure_plate", props -> new PressurePlateBlock(setType, props), redstoneProps, woodBlockItem);
    ItemObject<ButtonBlock> button = register(name + "_button", props -> new ButtonBlock(setType, 30, props), redstoneProps, fuelItem(flammable, ContextIntProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL));
    // signs
    Supplier<BlockBehaviour.Properties> signProps = () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).forceSolidOn().noCollision().strength(1.0F);
    DeferredHolder<Block, StandingSignBlock> standingSign = registerNoItem(name + "_sign", props -> new MantleStandingSignBlock(props, woodType), signProps);
    DeferredHolder<Block, WallSignBlock> wallSign = registerNoItem(name + "_wall_sign", props -> new MantleWallSignBlock(props, woodType), () -> wallVariant(signProps.get(), standingSign.get()));
    DeferredHolder<Block, MantleCeilingHangingSignBlock> hangingSign = registerNoItem(name + "_hanging_sign", props -> new MantleCeilingHangingSignBlock(props, woodType), signProps);
    DeferredHolder<Block, MantleWallHangingSignBlock> wallHangingSign = registerNoItem(name + "_wall_hanging_sign", props -> new MantleWallHangingSignBlock(props, woodType), () -> wallVariant(signProps.get(), hangingSign.get()));
    // tell mantle to inject these into the TE
    MantleSignBlockEntity.registerSignBlock(standingSign);
    MantleSignBlockEntity.registerSignBlock(wallSign);
    MantleHangingSignBlockEntity.registerSignBlock(hangingSign);
    MantleHangingSignBlockEntity.registerSignBlock(wallHangingSign);
    // sign is included automatically in asItem of the standing sign
    this.itemRegister.register(name + "_sign", id -> new StandingAndWallBlockItem(standingSign.get(), wallSign.get(), Direction.DOWN,
      signFuel.apply(RegistrationHelper.blockItemProperties(id, standingSign.get()).stacksTo(16).signText())));
    this.itemRegister.register(name + "_hanging_sign", id -> new HangingSignItem(hangingSign.get(), wallHangingSign.get(),
      signFuel.apply(RegistrationHelper.blockItemProperties(id, hangingSign.get()).stacksTo(16).signText())));
    // finally, return
    return new WoodBlockObject(resource(name), woodType,
                               planks, log, strippedLog, wood, strippedWood,
                               fence, fenceGate, door, trapdoor, pressurePlate, button,
                               standingSign, wallSign, hangingSign, wallHangingSign);
  }


  /* Flower pots */

  /**
   * Registers a potted form of the given block using the vanilla pot.
   * NeoForge fills the empty pot's plant table automatically when the block registry is baked.
   * @param name  Name of the flower
   * @param block Block to put in the block
   * @return  Potted block instance
   */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(String name, Supplier<? extends Block> block) {
    return registerNoItem("potted_" + name, props -> new FlowerPotBlock(() -> (FlowerPotBlock)Blocks.FLOWER_POT, block, props), BlockDeferredRegister::pottedProps);
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(DeferredHolder<? super Block, ? extends Block> block) {
    return registerPotted(block.getId().getPath(), block);
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public DeferredHolder<Block, FlowerPotBlock> registerPotted(ItemObject<? extends Block> block) {
    return registerPotted(block.getId().getPath(), block);
  }


  /* Enum */

  /**
   * Registers an item with multiple variants, prefixing the name with the value name
   * @param values    Enum values to use for this block
   * @param name      Name of the block
   * @param mapper    Function to get a block for the given enum value and block properties
   * @param props     Function creating new block properties for the given enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      T[] values, String name, BiFunction<T, BlockBehaviour.Properties, ? extends B> mapper, Function<T, BlockBehaviour.Properties> props, BiFunction<? super B, Item.Properties, ? extends BlockItem> item) {
    return registerEnum(values, name, (fullName, value) -> register(fullName, p -> mapper.apply(value, p), () -> props.apply(value), item));
  }

  /**
   * Registers a block with multiple variants, suffixing the name with the value name
   * @param name      Name of the block
   * @param values    Enum values to use for this block
   * @param mapper    Function to get a block for the given enum value and block properties
   * @param props     Function creating new block properties for the given enum value
   * @param item      Function to get an item from the block
   * @return  EnumObject mapping between different block types
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T,B> registerEnum(
      String name, T[] values, BiFunction<T, BlockBehaviour.Properties, ? extends B> mapper, Function<T, BlockBehaviour.Properties> props, BiFunction<? super B, Item.Properties, ? extends BlockItem> item) {
    return registerEnum(name, values, (fullName, value) -> register(fullName, p -> mapper.apply(value, p), () -> props.apply(value), item));
  }

  /**
   * Registers a block with enum variants, but no item form
   * @param values  Enum value list
   * @param name    Suffix after value name
   * @param mapper  Function to map types and block properties to blocks
   * @param props   Function creating new block properties for the given enum value
   * @param <T>  Type of enum
   * @param <B>  Type of block
   * @return  Enum object
   */
  public <T extends Enum<T>, B extends Block> EnumObject<T, B> registerEnumNoItem(T[] values, String name, BiFunction<T, BlockBehaviour.Properties, ? extends B> mapper, Function<T, BlockBehaviour.Properties> props) {
    return registerEnum(values, name, (fullName, value) -> registerNoItem(fullName, p -> mapper.apply(value, p), () -> props.apply(value)));
  }

  /** Registers a potted form of the given block using the vanilla pot */
  public <T extends Enum<T> & StringRepresentable, B extends Block> EnumObject<T, FlowerPotBlock> registerPottedEnum(T[] values, String name, EnumObject<T, B> block) {
    EnumObject.Builder<T, FlowerPotBlock> builder = new Builder<>(values[0].getDeclaringClass());
    for (T value : values) {
      Supplier<? extends B> supplier = block.getSupplier(value);
      if (supplier != null) {
        builder.put(value, registerPotted(value.getSerializedName() + "_" + name, supplier));
      }
    }
    return builder.build();
  }

  /** Registers a potted form of the given blocks using the vanilla pot, automatically choosing the values based on the passed object */
  public <T extends Enum<T> & StringRepresentable, B extends Block> EnumObject<T, FlowerPotBlock> registerPottedEnum(String name, EnumObject<T, B> block) {
    Collection<Entry<T,Supplier<? extends B>>> entries = block.entries();
    EnumObject.Builder<T, FlowerPotBlock> builder = new Builder<>(entries.iterator().next().getKey().getDeclaringClass());
    for (Entry<T,Supplier<? extends B>> entry : entries) {
      T value = entry.getKey();
      builder.put(value, registerPotted(value.getSerializedName() + "_" + name, entry.getValue()));
    }
    return builder.build();
  }


  /* Metal */

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param tagName     Name to use for tags for this block
   * @param block       Block constructor
   * @param blockProps  Supplier creating new block properties
   * @param blockItem   Block item
   * @param itemProps   Operator for the ingot and nugget properties, called once per item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, String tagName, Function<BlockBehaviour.Properties, ? extends Block> block, Supplier<BlockBehaviour.Properties> blockProps,
                                       BiFunction<? super Block, Item.Properties, ? extends BlockItem> blockItem, UnaryOperator<Item.Properties> itemProps) {
    ItemObject<Block> blockObj = register(name + "_block", block, blockProps, blockItem);
    DeferredHolder<Item, Item> ingot = itemRegister.register(name + "_ingot", id -> new Item(RegistrationHelper.itemProperties(id, itemProps)));
    DeferredHolder<Item, Item> nugget = itemRegister.register(name + "_nugget", id -> new Item(RegistrationHelper.itemProperties(id, itemProps)));
    return new MetalItemObject(tagName, blockObj, ingot, nugget);
  }

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param block       Block constructor
   * @param blockProps  Supplier creating new block properties
   * @param blockItem   Block item
   * @param itemProps   Operator for the ingot and nugget properties, called once per item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, Function<BlockBehaviour.Properties, ? extends Block> block, Supplier<BlockBehaviour.Properties> blockProps,
                                       BiFunction<? super Block, Item.Properties, ? extends BlockItem> blockItem, UnaryOperator<Item.Properties> itemProps) {
    return registerMetal(name, name, block, blockProps, blockItem, itemProps);
  }

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param tagName     Name to use for tags for this block
   * @param blockProps  Supplier creating new block properties
   * @param blockItem   Block item
   * @param itemProps   Operator for the ingot and nugget properties, called once per item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, String tagName, Supplier<BlockBehaviour.Properties> blockProps, BiFunction<? super Block, Item.Properties, ? extends BlockItem> blockItem, UnaryOperator<Item.Properties> itemProps) {
    return registerMetal(name, tagName, Block::new, blockProps, blockItem, itemProps);
  }

  /**
   * Creates a new metal item object
   * @param name        Metal name
   * @param blockProps  Supplier creating new block properties
   * @param blockItem   Block item
   * @param itemProps   Operator for the ingot and nugget properties, called once per item
   * @return  Metal item object
   */
  public MetalItemObject registerMetal(String name, Supplier<BlockBehaviour.Properties> blockProps, BiFunction<? super Block, Item.Properties, ? extends BlockItem> blockItem, UnaryOperator<Item.Properties> itemProps) {
    return registerMetal(name, name, blockProps, blockItem, itemProps);
  }
}
