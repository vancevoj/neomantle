package slimeknights.mantle.util.sync;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;

import java.util.function.Consumer;

/**
 * Int reference holder that starts the "lastKnownValue" at an invalid value.
 * Fixes a bug where a non-zero value on the client is not updated on UI open as the new value is 0
 */
@SuppressWarnings("unused")
public class ValidZeroDataSlot extends DataSlot {
  private final ContainerData data;
  private final int idx;
  /** Last value sent to the client, vanilla's copy is private so we track our own */
  private int lastKnownValue = Integer.MIN_VALUE;

  public ValidZeroDataSlot(ContainerData data, int idx) {
    this.data = data;
    this.idx = idx;
  }

  @Override
  public int get() {
    return data.get(idx);
  }

  @Override
  public void set(int value) {
    data.set(idx, value);
  }

  @Override
  public boolean checkAndClearUpdateFlag() {
    int value = get();
    boolean changed = value != lastKnownValue;
    lastKnownValue = value;
    return changed;
  }

  /**
   * Creates smart int reference holders and adds them to the given consumer
   * @param consumer  Consumer for reference holders
   * @param array     Array source
   */
  public static void trackIntArray(Consumer<DataSlot> consumer, ContainerData array) {
    for(int i = 0; i < array.getCount(); ++i) {
      consumer.accept(new ValidZeroDataSlot(array, i));
    }
  }
}
