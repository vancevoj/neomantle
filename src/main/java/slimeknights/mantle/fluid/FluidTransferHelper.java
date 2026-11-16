package slimeknights.mantle.fluid;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundAction;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferDirection;
import slimeknights.mantle.fluid.transfer.IFluidContainerTransfer.TransferResult;

import javax.annotation.Nullable;

import static slimeknights.mantle.util.TranslationHelper.COMMA_FORMAT;

/**
 * Alternative to {@link net.neoforged.neoforge.transfer.fluid.FluidUtil} supporting Mantle's data driven {@link IFluidContainerTransfer},
 * fish buckets and transfer results for UIs.
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FluidTransferHelper {
  private static final String KEY_FILLED = Mantle.makeDescriptionId("block", "tank.filled");
  private static final String KEY_DRAINED = Mantle.makeDescriptionId("block", "tank.drained");

  /** Gets the given sound from the fluid */
  public static SoundEvent getSound(FluidStack fluid, SoundAction action, SoundEvent fallback) {
    SoundEvent event = fluid.getFluid().getFluidType().getSound(fluid, action);
    if (event == null) {
      return fallback;
    }
    return event;
  }

  /** Gets the empty sound for a fluid */
  public static SoundEvent getEmptySound(FluidStack fluid) {
    return getSound(fluid, SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
  }

  /** Gets the fill sound for a fluid */
  public static SoundEvent getFillSound(FluidStack fluid) {
    return getSound(fluid, SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL);
  }

  /**
   * Gets the fluid that would be drained from the handler, without draining it. Replacement for the old {@code drain(maxDrain, SIMULATE)}.
   * @param handler      Handler to drain
   * @param maxDrain     Maximum amount to drain
   * @param transaction  Parent transaction, or null to simulate in a new root transaction
   * @return  Fluid that can be drained, or empty if nothing can be drained
   */
  public static FluidStack getDrainable(ResourceHandler<FluidResource> handler, int maxDrain, @Nullable TransactionContext transaction) {
    if (maxDrain <= 0) {
      return FluidStack.EMPTY;
    }
    try (Transaction simulate = Transaction.open(transaction)) {
      ResourceStack<FluidResource> drained = ResourceHandlerUtil.extractFirst(handler, resource -> true, maxDrain, simulate);
      return drained == null ? FluidStack.EMPTY : drained.resource().toStack(drained.amount());
    }
  }

  /**
   * Attempts to transfer fluid, committing the transfer immediately.
   * @param input    Fluid source
   * @param output   Fluid destination
   * @param maxFill  Maximum to transfer
   * @return  Fluid transferred, empty if nothing was transferred
   */
  public static FluidStack tryTransfer(ResourceHandler<FluidResource> input, ResourceHandler<FluidResource> output, int maxFill) {
    return tryTransfer(input, output, maxFill, null);
  }

  /**
   * Attempts to transfer fluid
   * @param input        Fluid source
   * @param output       Fluid destination
   * @param maxFill      Maximum to transfer
   * @param transaction  Parent transaction, or null to commit the transfer immediately
   * @return  Fluid transferred, empty if nothing was transferred
   */
  public static FluidStack tryTransfer(ResourceHandler<FluidResource> input, ResourceHandler<FluidResource> output, int maxFill, @Nullable TransactionContext transaction) {
    return tryTransfer(input, output, getDrainable(input, maxFill, transaction), transaction);
  }

  /**
   * Attempts to transfer fluid, committing the transfer immediately.
   * @param input    Fluid source
   * @param output   Fluid destination
   * @param fluid    Fluid to transfer, will not be modified. Precondition is it must be valid to drain from the input.
   * @return  Fluid transferred, empty if nothing was transferred
   */
  public static FluidStack tryTransfer(ResourceHandler<FluidResource> input, ResourceHandler<FluidResource> output, FluidStack fluid) {
    return tryTransfer(input, output, fluid, null);
  }

  /**
   * Attempts to transfer fluid
   * @param input        Fluid source
   * @param output       Fluid destination
   * @param fluid        Fluid to transfer, will not be modified. Precondition is it must be valid to drain from the input.
   * @param transaction  Parent transaction, or null to commit the transfer immediately
   * @return  Fluid transferred, empty if nothing was transferred
   */
  public static FluidStack tryTransfer(ResourceHandler<FluidResource> input, ResourceHandler<FluidResource> output, FluidStack fluid, @Nullable TransactionContext transaction) {
    if (!fluid.isEmpty()) {
      FluidResource resource = FluidResource.of(fluid);
      try (Transaction transfer = Transaction.open(transaction)) {
        // next, find out how much we can fill
        int simulatedFill;
        try (Transaction simulate = Transaction.open(transfer)) {
          simulatedFill = output.insert(resource, fluid.getAmount(), simulate);
        }
        if (simulatedFill > 0) {
          // actually drain, use the fluid we successfully filled with just in case that changes
          int drained = input.extract(resource, simulatedFill, transfer);
          if (drained > 0) {
            // actually fill
            int filled = output.insert(resource, drained, transfer);
            // failed to fill everything we drained, so try putting the extra back
            if (filled < drained) {
              int toReturn = drained - filled;
              int returned = input.insert(resource, toReturn, transfer);
              // failed to put the rest back, so abort the whole transfer instead of deleting fluid
              if (returned < toReturn) {
                Mantle.logger.error("Failed to return {} fluid during transfer, aborting transfer", toReturn - returned);
                return FluidStack.EMPTY;
              }
            }
            if (filled > 0) {
              transfer.commit();
              return resource.toStack(filled);
            }
          }
        }
      }
    }
    return FluidStack.EMPTY;
  }

  /** Return options for interaction methods */
  public enum FluidInteractionResult {
    /** Indicates fluid filled the item stack, draining the block entity */
    FILLED_STACK,
    /** Indicates fluid drained the stack, filling the block entity */
    DRAINED_STACK,
    /** Indicates there was a fluid container, but no fluid was transferred. Note that client side will never attempt transfer */
    CONTAINER,
    /** Indicates there was no block entity or the player was not holding a fluid container */
    MISSING;

    /** Returns true if fluid did move */
    public boolean didTransfer() {
      return this == FILLED_STACK || this == DRAINED_STACK;
    }

    /** Returns true if a container is present */
    public boolean hasContainer() {
      return this != MISSING;
    }
  }

  /** @deprecated use {@link #interactWithFilledBucket(Level, BlockPos, ResourceHandler, Player, InteractionHand, Direction)} or {@link #interactWithTank(Level, BlockPos, Player, InteractionHand, Direction, Direction)} */
  @Deprecated(forRemoval = true)
  public static boolean interactWithBucket(Level world, BlockPos pos, Player player, InteractionHand hand, Direction hit, Direction offset) {
    if (player.getItemInHand(hand).getItem() instanceof BucketItem) {
      ResourceHandler<FluidResource> handler = world.getCapability(Capabilities.Fluid.BLOCK, pos, hit);
      if (handler != null) {
        return interactWithFilledBucket(world, pos, handler, player, hand, offset).hasContainer();
      }
    }
    return false;
  }

  /** Gets the item left behind after emptying the given bucket */
  private static ItemStack getEmptyBucket(ItemStack bucket) {
    ItemStackTemplate remainder = bucket.getCraftingRemainder();
    if (remainder != null) {
      return remainder.create();
    }
    // fish buckets have no crafting remainder, but are emptied into a regular bucket like vanilla does
    return new ItemStack(Items.BUCKET);
  }

  /**
   * Attempts to interact with a flilled bucket on a fluid tank. This is unique as it handles fish buckets, which don't expose fluid capabilities
   * @param world     World instance
   * @param pos       Block position
   * @param handler   Fluid handler in the block entity
   * @param player    Player
   * @param hand      Hand
   * @param offset    Direction to place fish
   * @return {@link FluidInteractionResult} indicating the type of interaction that happened.
   */
  public static FluidInteractionResult interactWithFilledBucket(Level world, BlockPos pos, ResourceHandler<FluidResource> handler, Player player, InteractionHand hand, Direction offset) {
    ItemStack held = player.getItemInHand(hand);
    if (held.getItem() instanceof BucketItem bucket) {
      Fluid fluid = bucket.content;
      if (fluid != Fluids.EMPTY) {
        if (!world.isClientSide()) {
          FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
          // must empty the whole bucket
          boolean filled;
          try (Transaction transaction = Transaction.openRoot()) {
            filled = handler.insert(FluidResource.of(fluidStack), FluidType.BUCKET_VOLUME, transaction) == FluidType.BUCKET_VOLUME;
            if (filled) {
              transaction.commit();
            }
          }
          if (filled) {
            SoundEvent sound = getEmptySound(fluidStack);
            bucket.checkExtraContent(player, world, held, pos.relative(offset));
            world.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable(KEY_FILLED, COMMA_FORMAT.format(FluidType.BUCKET_VOLUME), fluidStack.getHoverName()));
            if (!player.isCreative()) {
              player.setItemInHand(hand, getEmptyBucket(held));
            }
            return FluidInteractionResult.DRAINED_STACK;
          }
        }
        return FluidInteractionResult.CONTAINER;
      }
    }
    return FluidInteractionResult.MISSING;
  }

  /** Plays the sound from filling a TE */
  public static void playEmptySound(Level world, BlockPos pos, Player player, FluidStack transferred) {
    world.playSound(null, pos, getEmptySound(transferred), SoundSource.BLOCKS, 1.0F, 1.0F);
    player.sendOverlayMessage(Component.translatable(KEY_FILLED, COMMA_FORMAT.format(transferred.getAmount()), transferred.getHoverName()));
  }

  /** Plays the sound from draining a TE */
  public static void playFillSound(Level world, BlockPos pos, Player player, FluidStack transferred) {
    world.playSound(null, pos, getFillSound(transferred), SoundSource.BLOCKS, 1.0F, 1.0F);
    player.sendOverlayMessage(Component.translatable(KEY_DRAINED, COMMA_FORMAT.format(transferred.getAmount()), transferred.getHoverName()));
  }

  /** @deprecated use {@link #interactWithContainer(Level, BlockPos, Player, InteractionHand, BlockHitResult)} */
  @Deprecated(forRemoval = true)
  public static boolean interactWithFluidItem(Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    return interactWithContainer(world, pos, player, hand, hit).hasContainer();
  }

  /**
   * Base logic to interact with a tank by fetching it from the block entity.
   * @param world   World instance
   * @param pos     Tank position
   * @param player  Player instance
   * @param hand    Hand used
   * @param hit     Hit position
   * @return {@link FluidInteractionResult} indicating the type of interaction that happened.
   * @see #interactWithTank(Level, BlockPos, Player, InteractionHand, BlockHitResult)
   */
  public static FluidInteractionResult interactWithContainer(Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (!player.getItemInHand(hand).isEmpty()) {
      // block must have a fluid handler capability
      ResourceHandler<FluidResource> handler = world.getCapability(Capabilities.Fluid.BLOCK, pos, hit.getDirection());
      if (handler != null) {
        return interactWithContainer(world, pos, handler, player, hand);
      }
    }
    return FluidInteractionResult.MISSING;
  }

  /**
   * Base logic to interact with a tank within a block entity.
   * @param world     World instance
   * @param pos       Tank position
   * @param teHandler Fluid handler in the block entity
   * @param player    Player instance
   * @param hand      Hand used
   * @return {@link FluidInteractionResult} indicating the type of interaction that happened.
   * @see #interactWithContainer(Level, BlockPos, Player, InteractionHand, BlockHitResult)
   */
  public static FluidInteractionResult interactWithContainer(Level world, BlockPos pos, ResourceHandler<FluidResource> teHandler, Player player, InteractionHand hand) {
    // fallback to JSON based transfer
    ItemStack stack = player.getItemInHand(hand);
    if (FluidContainerTransferManager.INSTANCE.mayHaveTransfer(stack)) {
      // only actually transfer on the serverside, client just has items
      if (!world.isClientSide()) {
        FluidStack currentFluid = getDrainable(teHandler, Integer.MAX_VALUE, null);
        IFluidContainerTransfer transfer = FluidContainerTransferManager.INSTANCE.getTransfer(stack, currentFluid);
        if (transfer != null) {
          TransferResult result = transfer.transfer(stack, currentFluid, teHandler, TransferDirection.AUTO);
          if (result != null) {
            if (result.didFill()) {
              playFillSound(world, pos, player, result.fluid());
            } else {
              playEmptySound(world, pos, player, result.fluid());
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result.stack()));
            return result.didFill() ? FluidInteractionResult.FILLED_STACK : FluidInteractionResult.DRAINED_STACK;
          }
        }
      }
      return FluidInteractionResult.CONTAINER;
    }

    // if the item has a capability, do a direct transfer
    ContainerSlot container = new ContainerSlot(stack);
    ResourceHandler<FluidResource> itemHandler = container.getFluidHandler();
    if (itemHandler != null) {
      FluidInteractionResult result = FluidInteractionResult.CONTAINER;
      if (!world.isClientSide()) {
        FluidStack transferred;
        try (Transaction transaction = Transaction.openRoot()) {
          // first, try filling the TE from the item
          transferred = tryTransfer(itemHandler, teHandler, Integer.MAX_VALUE, transaction);
          if (!transferred.isEmpty()) {
            result = FluidInteractionResult.DRAINED_STACK;
          } else {
            // if that failed, try filling the item handler from the TE
            transferred = tryTransfer(teHandler, itemHandler, Integer.MAX_VALUE, transaction);
            if (!transferred.isEmpty()) {
              result = FluidInteractionResult.FILLED_STACK;
            }
          }
          transaction.commit();
        }
        // if either worked, update the player's inventory
        if (!transferred.isEmpty()) {
          if (result == FluidInteractionResult.DRAINED_STACK) {
            playEmptySound(world, pos, player, transferred);
          } else {
            playFillSound(world, pos, player, transferred);
          }
          player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, container.getContainer()));
        }
      }
      return result;
    }
    return FluidInteractionResult.MISSING;
  }

  /**
   * Utility to try fluid item then bucket.
   * @param world   World instance
   * @param pos     Tank position
   * @param player  Player instance
   * @param hand    Hand used
   * @param hit     Hit position
   * @return  True if interacted
   * @see #interactWithTank(Level, BlockPos, Player, InteractionHand, Direction, Direction)
   * @see #interactWithContainer(Level, BlockPos, Player, InteractionHand, BlockHitResult)
   */
  public static boolean interactWithTank(Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    Direction direction = hit.getDirection();
    return interactWithTank(world, pos, player, hand, direction, direction);
  }

  /**
   * Utility to try fluid item then bucket
   * @param world   World instance
   * @param pos     Tank position
   * @param player  Player instance
   * @param hand    Hand used
   * @param hit     Hit direction
   * @param offset  Offset to spawn the mob in the bucket, if present
   * @return  True if interacted
   * @see #interactWithTank(Level, BlockPos, Player, InteractionHand, BlockHitResult)
   * @see #interactWithContainer(Level, BlockPos, Player, InteractionHand, BlockHitResult)
   */
  public static boolean interactWithTank(Level world, BlockPos pos, Player player, InteractionHand hand, Direction hit, Direction offset) {
    if (!player.getItemInHand(hand).isEmpty()) {
      ResourceHandler<FluidResource> handler = world.getCapability(Capabilities.Fluid.BLOCK, pos, hit);
      if (handler != null) {
        return interactWithContainer(world, pos, handler, player, hand).hasContainer()
          || interactWithFilledBucket(world, pos, handler, player, hand, offset).hasContainer();
      }
    }
    return false;
  }

  /**
   * Attempts to transfer fluid from the passed stack into a tank.
   * @param teHandler  Tank handler
   * @param stack      Input stack, may be modified
   * @param direction  Determines whether we may empty the item, fill, or both
   * @return  Resulting stack after transfer
   */
  public static ItemStack interactWithTankSlot(ResourceHandler<FluidResource> teHandler, ItemStack stack, TransferDirection direction) {
    TransferResult result = interactWithStack(teHandler, stack, direction);
    return result != null ? result.stack() : ItemStack.EMPTY;
  }

  /**
   * Attempts to transfer fluid from the passed stack into a tank.
   * @param teHandler  Tank handler
   * @param stack      Input stack, may be modified
   * @param direction  Determines whether we may empty the item, fill, or both
   * @return  What was transferred and the resulting stack, or null if no transfer happened.
   */
  @Nullable
  public static TransferResult interactWithStack(ResourceHandler<FluidResource> teHandler, ItemStack stack, TransferDirection direction) {
    if (!stack.isEmpty()) {
      // fallback to JSON based transfer
      if (FluidContainerTransferManager.INSTANCE.mayHaveTransfer(stack)) {
        // only actually transfer on the serverside, client just has items
        FluidStack currentFluid = getDrainable(teHandler, Integer.MAX_VALUE, null);
        IFluidContainerTransfer transfer = FluidContainerTransferManager.INSTANCE.getTransfer(stack, currentFluid);
        if (transfer != null) {
          TransferResult result = transfer.transfer(stack, currentFluid, teHandler, direction);
          if (result != null) {
            stack.shrink(1);
            return result;
          }
        }
      }

      // if the item has a capability, do a direct transfer
      ContainerSlot container = new ContainerSlot(stack);
      ResourceHandler<FluidResource> itemHandler = container.getFluidHandler();
      if (itemHandler != null) {
        // first, try filling the TE from the item
        FluidStack transferred = FluidStack.EMPTY;
        // reverse means try TE to item first
        boolean didFill = true;
        try (Transaction transaction = Transaction.openRoot()) {
          if (direction == TransferDirection.REVERSE) {
            transferred = tryTransfer(teHandler, itemHandler, Integer.MAX_VALUE, transaction);
          }
          // if not reverse or reverse failed, try filling TE from item
          if (direction.canEmpty() && transferred.isEmpty()) {
            transferred = tryTransfer(itemHandler, teHandler, Integer.MAX_VALUE, transaction);
            if (!transferred.isEmpty()) {
              didFill = false;
            }
          }
          // if that failed, try filling the item handler from the TE
          if (direction != TransferDirection.REVERSE && direction.canFill() && transferred.isEmpty()) {
            transferred = tryTransfer(teHandler, itemHandler, Integer.MAX_VALUE, transaction);
          }
          transaction.commit();
        }
        // if either worked, update the player's inventory
        if (!transferred.isEmpty()) {
          stack.shrink(1);
          return new TransferResult(container.getContainer(), transferred, didFill);
        }
      }
    }
    return null;
  }

  /**
   * Attempts to transfer fluid into the passed stack from the given handler.
   * Similar to {@link #interactWithTankSlot(ResourceHandler, ItemStack, TransferDirection)} except filtered and unable to set direction.
   * @param teHandler  Tank handler
   * @param stack      Input stack, may be modified
   * @param fluid      Determines the fluid used to fill the item
   * @return  Resulting stack after transfer
   */
  public static ItemStack fillFromTankSlot(ResourceHandler<FluidResource> teHandler, ItemStack stack, FluidStack fluid) {
    TransferResult result = fillStack(teHandler, stack, fluid);
    return result != null ? result.stack() : ItemStack.EMPTY;
  }

  /**
   * Attempts to transfer fluid into the passed stack from the given handler.
   * Similar to {@link #interactWithTankSlot(ResourceHandler, ItemStack, TransferDirection)} except filtered and unable to set direction.
   * @param teHandler  Tank handler
   * @param stack      Input stack, may be modified
   * @param fluid      Determines the fluid used to fill the item
   * @return  Resulting stack after transfer
   */
  @Nullable
  public static TransferResult fillStack(ResourceHandler<FluidResource> teHandler, ItemStack stack, FluidStack fluid) {
    if (!stack.isEmpty()) {
      // fallback to JSON based transfer
      if (FluidContainerTransferManager.INSTANCE.mayHaveTransfer(stack)) {
        // only actually transfer on the serverside, client just has items
        IFluidContainerTransfer transfer = FluidContainerTransferManager.INSTANCE.getTransfer(stack, fluid);
        if (transfer != null) {
          TransferResult result = transfer.transfer(stack, fluid, teHandler, TransferDirection.FILL_ITEM);
          if (result != null) {
            stack.shrink(1);
            return result;
          }
        }
      }

      // if the item has a capability, do a direct transfer
      ContainerSlot container = new ContainerSlot(stack);
      ResourceHandler<FluidResource> itemHandler = container.getFluidHandler();
      if (itemHandler != null) {
        // first, try filling the TE from the item
        FluidStack transferred = tryTransfer(teHandler, itemHandler, fluid.copy());
        if (!transferred.isEmpty()) {
          stack.shrink(1);
          return new TransferResult(container.getContainer(), transferred, true);
        }
      }
    }
    return null;
  }

  /**
   * Same as {@link net.minecraft.world.item.ItemUtils#createFilledResult(ItemStack, Player, ItemStack)} but doesn't shrink results or check creative.
   * Useful in UIs along {@link #interactWithTankSlot(ResourceHandler, ItemStack, TransferDirection)} or {@link #fillFromTankSlot(ResourceHandler, ItemStack, FluidStack)}
   */
  public static ItemStack getOrTransferFilled(Player player, ItemStack emptyStack, ItemStack filledStack) {
    // if no more helpd
    if (emptyStack.isEmpty()) {
      return filledStack;
    }
    if (!player.getInventory().add(filledStack)) {
      player.drop(filledStack, false, Prediction.PREDICTED);
    }
    return emptyStack;
  }

  /** Plays sound only to the targeted player. Works by sending a targeted packet to server players, or a local packet to client. */
  @SuppressWarnings("deprecation")
  public static void playUISound(Player player, SoundEvent sound) {
    if (player.level().isClientSide()) {
      player.playSound(sound);
    } else if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), player.getSoundSource(), player.getX(), player.getY(), player.getZ(), 1, 1, player.getRandom().nextLong()));
    }
  }

  /**
   * Combination of {@link #getOrTransferFilled(Player, ItemStack, ItemStack)} and {@link #playUISound(Player, SoundEvent)}.
   * For working with {@link #interactWithStack(ResourceHandler, ItemStack, TransferDirection)} and {@link #fillStack(ResourceHandler, ItemStack, FluidStack)} in UIs.
   */
  public static ItemStack handleUIResult(Player player, ItemStack emptyStack, @Nullable TransferResult result) {
    if (result == null) {
      return emptyStack;
    }
    playUISound(player, result.getSound());
    return getOrTransferFilled(player, emptyStack, result.stack());
  }

  /**
   * Single slot holding a copy of one item from a stack, allowing the item's fluid capability to replace the item through an {@link ItemAccess}
   * (for instance, a bucket becoming an empty bucket). Changes to the slot are transactional, the result is read through {@link #getContainer()} after commit.
   */
  private static class ContainerSlot extends ItemStackResourceHandler {
    private ItemStack stack;

    private ContainerSlot(ItemStack stack) {
      this.stack = stack.copyWithCount(1);
    }

    @Override
    protected ItemStack getStack() {
      return stack;
    }

    @Override
    protected void setStack(ItemStack stack) {
      this.stack = stack;
    }

    /** Gets the container after the transfer */
    public ItemStack getContainer() {
      return stack;
    }

    /** Gets the fluid handler for the contained item, or null if it is not a fluid container */
    @Nullable
    public ResourceHandler<FluidResource> getFluidHandler() {
      if (stack.isEmpty()) {
        return null;
      }
      return ItemAccess.forHandlerIndexStrict(this, 0).getCapability(Capabilities.Fluid.ITEM);
    }
  }
}
