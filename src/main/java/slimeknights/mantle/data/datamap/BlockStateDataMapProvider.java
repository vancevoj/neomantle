package slimeknights.mantle.data.datamap;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.data.loadable.Loadable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Data provider for {@link BlockStateDataMapLoader} */
public abstract class BlockStateDataMapProvider<D> extends GenericDataProvider {
  private final Loadable<D> dataLoader;
  private final Map<Block,DataMap> entries = new HashMap<>();
  public BlockStateDataMapProvider(PackOutput output, Target type, String folder, Loadable<D> dataLoader) {
    super(output, type, folder);
    this.dataLoader = dataLoader;
  }

  public BlockStateDataMapProvider(PackOutput output, Target type, BlockStateDataMapLoader<D> registry) {
    this(output, type, registry.getFolder(), registry.getDataLoader());
  }

  /** Adds all entries to this provider */
  protected abstract void addEntries();

  @Override
  public CompletableFuture<?> run(CachedOutput cached) {
    addEntries();
    return allOf(entries.values().stream().map(entry -> saveJson(cached, BuiltInRegistries.BLOCK.getKey(entry.owner), entry.toJson())));
  }

  /** Creates a new builder for a block */
  protected DataMap block(Block block) {
    return entries.computeIfAbsent(block, DataMap::new);
  }

  /** Record holding a single entry in the variants list */
  private record Variant<D>(D data, StateVariantStringBuilder variant) {}

  /** Represents a single file to be generated */
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  protected class DataMap {
    private final Block owner;
    private final List<Variant<D>> variants = new ArrayList<>();

    /** Creates a new builder instance */
    protected VariantBuilder addVariant(D data) {
      VariantBuilder builder = new VariantBuilder();
      variants.add(new Variant<>(data, builder));
      return builder;
    }

    /** Serializes this to JSON */
    private JsonObject toJson() {
      JsonObject variants = new JsonObject();
      for (Variant<D> variant : this.variants) {
        variants.add(variant.variant.toString(), dataLoader.serialize(variant.data));
      }
      JsonObject map = new JsonObject();
      map.add("variants", variants);
      return variants;
    }

    /** Variant builder that returns to the data map when finished */
    protected class VariantBuilder extends StateVariantStringBuilder {
      public VariantBuilder() {
        super(owner);
      }

      /** Returns to the outer datamap */
      public DataMap finish() {
        return DataMap.this;
      }
    }
  }
}
