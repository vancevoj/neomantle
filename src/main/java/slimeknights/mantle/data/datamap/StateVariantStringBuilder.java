package slimeknights.mantle.data.datamap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Builder for a block state string used in {@link BlockStateDataMapLoader} */
public class StateVariantStringBuilder {
  private final Block owner;
  private final Collection<Property<?>> properties;
  private final SortedMap<Property<?>, Comparable<?>> setStates = new TreeMap<>(Comparator.comparing(Property::getName));

  public StateVariantStringBuilder(Block owner) {
    this.owner = owner;
    this.properties = owner.getStateDefinition().getProperties();
  }

  /** Sets a property in the builder */
  public <T extends Comparable<T>> StateVariantStringBuilder set(Property<T> prop, T value) {
    // property must be valid
    if (!properties.contains(prop)) {
      throw new IllegalArgumentException("Property " + prop + " is not valid for " + BuiltInRegistries.BLOCK.getKey(owner));
    }
    // property must not be set already
    Comparable<?> oldValue = setStates.putIfAbsent(prop, value);
    if (oldValue != null) {
      throw new IllegalArgumentException("Property " + prop + " has already been set");
    }
    return this;
  }

  /** Builds the final string. Based on {@link net.minecraftforge.client.model.generators.VariantBlockStateBuilder#toString()}*/
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})  // not another good way to handle it
  public String toString() {
    StringBuilder ret = new StringBuilder();
    for (Map.Entry<Property<?>, Comparable<?>> entry : setStates.entrySet()) {
      if (ret.length() > 0) {
        ret.append(',');
      }
      ret.append(entry.getKey().getName())
         .append('=')
         .append(((Property) entry.getKey()).getName(entry.getValue()));
    }
    return ret.toString();
  }
}
