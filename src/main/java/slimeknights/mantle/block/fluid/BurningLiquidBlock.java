package slimeknights.mantle.block.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import slimeknights.mantle.registration.deferred.FluidDeferredRegister;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Liquid block setting the entity on fire */
public class BurningLiquidBlock extends LiquidBlock {
  /** Burn time in seconds. Lava uses 15 */
  private final int burnTime;
  /** Damage from being in the fluid, lava uses 4 */
  private final float damage;
  public BurningLiquidBlock(Supplier<? extends FlowingFluid> supplier, Properties properties, int burnTime, float damage) {
    super(supplier.get(), properties);
    this.burnTime = burnTime;
    this.damage = damage;
  }

  @Override
  protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
    if (!entity.fireImmune() && entity.getFluidTypeHeight(this.fluid.getFluidType()) > 0) {
      entity.igniteForSeconds(burnTime);
      // damage is only dealt serverside since 1.21.2
      if (level instanceof ServerLevel serverLevel && entity.hurtServer(serverLevel, entity.damageSources().lava(), damage)) {
        entity.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + level.getRandom().nextFloat() * 0.4F);
      }
    }
  }

  /**
   * Creates a new block constructor for {@link FluidDeferredRegister.Builder#block(BiFunction, Supplier)}.
   * Since 1.21.2 block properties need the block ID, so the properties come from the register, this sets the map color and light level on them.
   */
  public static BiFunction<Supplier<? extends FlowingFluid>, BlockBehaviour.Properties, LiquidBlock> createBurning(MapColor color, int lightLevel, int burnTime, float damage) {
    return (fluid, props) -> new BurningLiquidBlock(fluid, props.mapColor(color).lightLevel(state -> lightLevel), burnTime, damage);
  }
}
