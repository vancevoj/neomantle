package slimeknights.mantle.block.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import slimeknights.mantle.registration.deferred.FluidDeferredRegister;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Liquid block setting the entity on fire */
public class MobEffectLiquidBlock extends LiquidBlock {
  private final Supplier<MobEffectInstance> effect;
  public MobEffectLiquidBlock(Supplier<? extends FlowingFluid> supplier, Properties properties, Supplier<MobEffectInstance> effect) {
    super(supplier.get(), properties);
    this.effect = effect;
  }

  @Override
  protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
    if (entity.getFluidTypeHeight(this.fluid.getFluidType()) > 0 && entity instanceof LivingEntity living) {
      living.addEffect(this.effect.get());
    }
  }

  /** Checks if the entity is standing in this fluid and it applies the given effect */
  private boolean appliesWhileInside(LivingEntity living, Holder<MobEffect> effect) {
    return living.getFluidTypeHeight(this.fluid.getFluidType()) > 0 && this.effect.get().is(effect);
  }

  /**
   * Standing in the fluid reapplies the effect, so milk should not cure it.
   * 26.3 has no effect cures, so instead cancel removing the effect while the entity is still inside a fluid applying it.
   */
  public static void preventRemovingWhileInside(MobEffectEvent.Remove event) {
    LivingEntity living = event.getEntity();
    Level level = living.level();
    AABB bounds = living.getBoundingBox();
    for (BlockPos check : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
      if (level.getBlockState(check).getBlock() instanceof MobEffectLiquidBlock block && block.appliesWhileInside(living, event.getEffect())) {
        event.setCanceled(true);
        return;
      }
    }
  }

  /**
   * Creates a new block constructor for {@link FluidDeferredRegister.Builder#block(BiFunction, Supplier)}.
   * Since 1.21.2 block properties need the block ID, so the properties come from the register, this sets the map color and light level on them.
   */
  public static BiFunction<Supplier<? extends FlowingFluid>, BlockBehaviour.Properties, LiquidBlock> createEffect(MapColor color, int lightLevel, Supplier<MobEffectInstance> effect) {
    return (fluid, props) -> new MobEffectLiquidBlock(fluid, props.mapColor(color).lightLevel(state -> lightLevel), effect);
  }
}
