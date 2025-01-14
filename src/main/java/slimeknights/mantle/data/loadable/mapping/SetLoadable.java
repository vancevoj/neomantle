package slimeknights.mantle.data.loadable.mapping;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import slimeknights.mantle.data.loadable.Loadable;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loadable of set of elements */
public class SetLoadable<T> extends CollectionLoadable<T,Set<T>,Builder<T>> {
  public SetLoadable(Loadable<T> base, int minSize) {
    super(base, minSize);
  }

  @Override
  protected Builder<T> makeBuilder() {
    return ImmutableSet.builder();
  }

  @Override
  protected Set<T> build(Builder<T> builder) {
    return builder.build();
  }

  /** Creates a map from this collection using the given getter to find values for the map */
  public <V> Loadable<Map<T,V>> mapWithValues(Function<T,V> valueGetter) {
    return flatXmap(collection -> collection.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), valueGetter)), Map::keySet);
  }
}
