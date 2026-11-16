package slimeknights.mantle.client.model.util;

import lombok.AllArgsConstructor;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.TextureSlots;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates over the texture slot data in a model and all its parents, starting from the child model.
 * Note that {@link TextureSlots.Data} does not expose texture references, use {@link ModelHelper#findReferences(ResolvedModel, java.util.Collection)} to follow them.
 */
@AllArgsConstructor
public class ModelTextureIteratable implements Iterable<TextureSlots.Data> {
  /** Initial map for iteration */
  @Nullable
  private final TextureSlots.Data startMap;
  /** Initial model for iteration */
  @Nullable
  private final ResolvedModel startModel;

  /**
   * Creates an iterable over the given model
   * @param model  Model
   */
  public ModelTextureIteratable(ResolvedModel model) {
    this(null, model);
  }

  /**
   * Creates an iterable over the given model and its parents
   * @param model  Model to iterate
   * @return  Iteratable over model texture data
   */
  public static ModelTextureIteratable of(ResolvedModel model) {
    return new ModelTextureIteratable(null, model);
  }

  @Override
  public Iterator<TextureSlots.Data> iterator() {
    return new MapIterator(startMap, startModel);
  }

  @AllArgsConstructor
  private static class MapIterator implements Iterator<TextureSlots.Data> {
    /** Initial map for iteration */
    @Nullable
    private TextureSlots.Data initial;
    /** current model in the iterator */
    @Nullable
    private ResolvedModel model;

    @Override
    public boolean hasNext() {
      return initial != null || model != null;
    }

    @Override
    public TextureSlots.Data next() {
      TextureSlots.Data map;
      if (initial != null) {
        map = initial;
        initial = null;
      } else if (model != null) {
        map = model.wrapped().textureSlots();
        model = model.parent();
      } else {
        throw new NoSuchElementException();
      }
      return map;
    }
  }
}
