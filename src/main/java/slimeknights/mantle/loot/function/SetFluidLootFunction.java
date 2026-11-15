package slimeknights.mantle.loot.function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Optional;

/**
 * Loot function to set the fluid on a dropped item
 */
public class SetFluidLootFunction extends LootItemConditionalFunction {
  /** Codec for this function */
  public static final MapCodec<SetFluidLootFunction> CODEC = RecordCodecBuilder.mapCodec(inst -> commonFields(inst).and(
    FluidStack.CODEC.fieldOf("fluid").forGetter(f -> f.fluid)
  ).apply(inst, SetFluidLootFunction::new));

  /** Fluid to add to the item */
  private final FluidStack fluid;
  protected SetFluidLootFunction(Optional<Holder<LootItemCondition>> condition, FluidStack fluid) {
    super(condition);
    this.fluid = fluid;
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    if (stack.isEmpty() || fluid.isEmpty()) {
      return stack;
    }
    // the fluid handler changes its container through the item access, which may swap the item (e.g. empty bucket into filled bucket)
    // so give it a single slot inventory holding a copy of the stack, then return whatever ends up in that slot
    ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY, stack.copy()));
    ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndex(inventory, 0).getCapability(Capabilities.Fluid.ITEM);
    if (handler != null) {
      try (Transaction transaction = Transaction.openRoot()) {
        handler.insert(FluidResource.of(fluid), fluid.getAmount(), transaction);
        transaction.commit();
      }
      return ItemUtil.getStack(inventory, 0);
    }
    return stack;
  }

  @Override
  public MapCodec<SetFluidLootFunction> codec() {
    return CODEC;
  }

  /**
   * Creates a new builder with the given fluid
   * @param fluid  Fluid to set
   * @return  Builder instance
   */
  public static Builder<?> builder(FluidStack fluid) {
    return simpleBuilder(condition -> new SetFluidLootFunction(condition, fluid));
  }
}
