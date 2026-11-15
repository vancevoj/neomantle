package slimeknights.mantle.inventory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Item handler that contains no items. Use similarly to {@link net.neoforged.neoforge.transfer.EmptyResourceHandler}, which can be used instead.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmptyItemHandler implements ResourceHandler<ItemResource> {
  public static final EmptyItemHandler INSTANCE = new EmptyItemHandler();

  @Override
  public int size() {
    return 0;
  }

  @Override
  public ItemResource getResource(int index) {
    Objects.checkIndex(index, size());
    return ItemResource.EMPTY;
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return 0;
  }

  @Override
  public long getCapacityAsLong(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    return 0;
  }

  @Override
  public boolean isValid(int index, ItemResource resource) {
    Objects.checkIndex(index, size());
    return false;
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    return 0;
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    return 0;
  }
}
