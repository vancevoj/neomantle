package slimeknights.mantle.data.loadable.mapping;

import slimeknights.mantle.data.loadable.Loadable;

import java.util.Collection;
import java.util.List;

/** Loadable of list of elements */
public class ListLoadable<T> extends CollectionLoadable<T,List<T>> {
  public ListLoadable(Loadable<T> base, int minSize) {
    super(base, minSize);
  }

  @Override
  protected List<T> build(Collection<T> builder) {
    return List.copyOf(builder);
  }
}
