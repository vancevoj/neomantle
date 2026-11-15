package slimeknights.mantle.registration.adapter;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import slimeknights.mantle.block.MantleCeilingHangingSignBlock;
import slimeknights.mantle.block.MantleStandingSignBlock;
import slimeknights.mantle.block.MantleWallHangingSignBlock;
import slimeknights.mantle.block.MantleWallSignBlock;
import slimeknights.mantle.block.StrippableLogBlock;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.deferred.BlockDeferredRegister;
import slimeknights.mantle.registration.deferred.FluidDeferredRegister;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.FenceBuildingBlockObject;
import slimeknights.mantle.registration.object.WallBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject.WoodVariant;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static slimeknights.mantle.util.RegistryHelper.getHolder;


/**
 * Provides utility registration methods when registering blocks.
 * <p>
 * Blocks need their ID stamped into their properties before construction, so methods creating blocks take a constructor from properties
 * along with a supplier creating new properties. Blocks keep their properties object, so the supplier must return a new instance each call.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class BlockRegistryAdapter extends EnumRegistryAdapter<Block> {

  /** @inheritDoc */
  public BlockRegistryAdapter(Registry<Block> registry) {
    super(registry);
  }

  /** @inheritDoc */
  public BlockRegistryAdapter(Registry<Block> registry, String modid) {
    super(registry, modid);
  }

  /**
   * Registers a block, passing the constructor properties with the ID set
   * @param constructor  Block constructor
   * @param props        Supplier creating new block properties
   * @param name         Block name
   * @param <T>          Block type
   * @return  Registered block
   */
  public <T extends Block> T register(Function<Properties, T> constructor, Supplier<Properties> props, String name) {
    return register(constructor.apply(RegistrationHelper.blockProperties(getResource(name), props)), name);
  }

  /**
   * Registers a block override based on the given block
   * @param constructor  Override constructor
   * @param base         Base block
   * @param <T>          Block type
   * @return  Registered block
   */
  public <T extends Block> T registerOverride(Function<Properties, T> constructor, Block base) {
    return register(constructor.apply(RegistrationHelper.blockProperties(Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(base)), () -> BlockBehaviour.Properties.ofFullCopy(base))), base);
  }

  /* Building */

  /**
   * Registers the given block as well as a slab and a stair variant for it.
   * Uses the vanilla slab and stair blocks. Uses the passed blocks properties for both.
   * Slabs and stairs are registered with a "_slab" and "_stairs" prefix
   *
   * @param constructor  Constructor for the main block
   * @param props        Supplier creating new properties for the main block. Slabs and stairs copy the properties of the main block.
   * @param name         The registry name to use for the block and as base for the slab and stairs
   * @return  BuildingBlockObject for the given block
   */
  public BuildingBlockObject registerBuilding(Function<Properties, ? extends Block> constructor, Supplier<Properties> props, String name) {
    Block block = this.register(constructor, props, name);
    return new BuildingBlockObject(
      block,
      this.register(SlabBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(block), name + "_slab"),
      this.register(p -> new StairBlock(block.defaultBlockState(), p), () -> BlockBehaviour.Properties.ofFullCopy(block), name + "_stairs")
    );
  }

  /**
   * Same as {@link #registerBuilding(Function, Supplier, String)}, but also includes a wall variant
   *
   * @param constructor  Constructor for the main block
   * @param props        Supplier creating new properties for the main block. Other variants copy the properties of the main block.
   * @param name         The registry name to use for the block and as base for the slab and stairs
   * @return  BuildingBlockObject for the given block
   */
  public WallBuildingBlockObject registerWallBuilding(Function<Properties, ? extends Block> constructor, Supplier<Properties> props, String name) {
    BuildingBlockObject building = registerBuilding(constructor, props, name);
    return new WallBuildingBlockObject(
      building,
      this.register(WallBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(building.get()), name + "_wall")
    );
  }

  /**
   * Same as {@link #registerBuilding(Function, Supplier, String)}, but also includes a fence variant
   *
   * @param constructor  Constructor for the main block
   * @param props        Supplier creating new properties for the main block. Other variants copy the properties of the main block.
   * @param name         The registry name to use for the block and as base for the slab and stairs
   * @return  BuildingBlockObject for the given block
   */
  public FenceBuildingBlockObject registerFenceBuilding(Function<Properties, ? extends Block> constructor, Supplier<Properties> props, String name) {
    BuildingBlockObject building = registerBuilding(constructor, props, name);
    return new FenceBuildingBlockObject(
      building,
      this.register(FenceBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(building.get()), name + "_fence")
    );
  }


  /**
   * Registers a new wood object
   * @param name             Name of the wood object
   * @param behaviorCreator  Logic to create the behavior, must return a new properties instance each call
   * @return Wood object
   */
  public WoodBlockObject registerWood(String name, Function<WoodVariant,BlockBehaviour.Properties> behaviorCreator) {
    BlockSetType setType = new BlockSetType(resourceName(name));
    WoodType woodType = new WoodType(resourceName(name), setType);
    BlockSetType.register(setType);
    RegistrationHelper.registerWoodType(woodType);

    // planks
    BuildingBlockObject planks = registerBuilding(Block::new, () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f), name + "_planks");
    FenceBlock fence = register(FenceBlock::new, () -> Properties.ofFullCopy(planks.get()).forceSolidOn(), name + "_fence");
    // logs and wood
    Supplier<Properties> stripped = () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(2.0f);
    RotatedPillarBlock strippedLog = register(RotatedPillarBlock::new, stripped, "stripped_" + name + "_log");
    RotatedPillarBlock strippedWood = register(RotatedPillarBlock::new, stripped, "stripped_" + name + "_wood");
    RotatedPillarBlock log = register(p -> new StrippableLogBlock(getHolder(BuiltInRegistries.BLOCK, strippedLog), p), () -> behaviorCreator.apply(WoodVariant.LOG).instrument(NoteBlockInstrument.BASS).strength(2.0f), name + "_log");
    RotatedPillarBlock wood = register(p -> new StrippableLogBlock(getHolder(BuiltInRegistries.BLOCK, strippedWood), p), () -> behaviorCreator.apply(WoodVariant.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0f), name + "_wood");

    // doors
    DoorBlock door = register(p -> new DoorBlock(setType, p), () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED), name + "_door");
    TrapDoorBlock trapdoor = register(p -> new TrapDoorBlock(setType, p), () -> behaviorCreator.apply(WoodVariant.PLANKS).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never), name + "_trapdoor");
    FenceGateBlock fenceGate = register(p -> new FenceGateBlock(woodType, p), () -> Properties.ofFullCopy(fence), name + "_fence_gate");
    // redstone
    Supplier<Properties> redstoneProps = () -> behaviorCreator.apply(WoodVariant.PLANKS).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().pushReaction(PushReaction.POPPED).strength(0.5F);
    PressurePlateBlock pressurePlate = register(p -> new PressurePlateBlock(setType, p), redstoneProps, name + "_pressure_plate");
    ButtonBlock button = register(p -> new ButtonBlock(setType, 30, p), redstoneProps, name + "_button");
    // signs
    Supplier<Properties> signProps = () -> behaviorCreator.apply(WoodVariant.PLANKS).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F);
    StandingSignBlock standingSign = register(p -> new MantleStandingSignBlock(p, woodType), signProps, name + "_sign");
    WallSignBlock wallSign = register(p -> new MantleWallSignBlock(p, woodType), () -> BlockDeferredRegister.wallVariant(signProps.get(), standingSign), name + "_wall_sign");
    MantleCeilingHangingSignBlock hangingSign = register(p -> new MantleCeilingHangingSignBlock(p, woodType), signProps, name + "_hanging_sign");
    MantleWallHangingSignBlock wallHangingSign = register(p -> new MantleWallHangingSignBlock(p, woodType), () -> BlockDeferredRegister.wallVariant(signProps.get(), hangingSign), name + "_wall_hanging_sign");
    // tell mantle to inject these into the TE
    MantleSignBlockEntity.registerSignBlock(() -> standingSign);
    MantleSignBlockEntity.registerSignBlock(() -> wallSign);
    MantleHangingSignBlockEntity.registerSignBlock(() -> hangingSign);
    MantleHangingSignBlockEntity.registerSignBlock(() -> wallHangingSign);
    // finally, return
    return new WoodBlockObject(getResource(name), woodType, planks, log, strippedLog, wood, strippedWood, fence, fenceGate, door, trapdoor, pressurePlate, button, standingSign, wallSign, hangingSign, wallHangingSign);
  }

  /* Fluid */

  /**
   * Registers a fluid block from a fluid
   * @param fluid       Fluid supplier
   * @param color       Fluid color
   * @param lightLevel  Fluid light level
   * @param name        Fluid name, unfortunately no way to fetch from the fluid as it does not exist yet
   * @return  Fluid block instance
   */
  public LiquidBlock registerFluidBlock(Supplier<? extends BaseFlowingFluid> fluid, MapColor color, int lightLevel, String name) {
    return register(p -> new LiquidBlock(fluid.get(), p), () -> FluidDeferredRegister.createProperties(color, lightLevel), name + "_fluid");
  }
}
