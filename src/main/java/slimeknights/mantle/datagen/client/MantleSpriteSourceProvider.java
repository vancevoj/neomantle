package slimeknights.mantle.datagen.client;

import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.AtlasIds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.data.SpriteSourceProvider;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.mantle.Mantle;

import java.util.concurrent.CompletableFuture;

/** Adds sprite sources from Mantle */
@Internal
public class MantleSpriteSourceProvider extends SpriteSourceProvider {
  public MantleSpriteSourceProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(output, lookupProvider, Mantle.modId);
  }

  @Override
  protected void gather() {
    // fluid textures must stay on the block atlas, vanilla fluid models only accept block atlas sprites
    atlas(AtlasIds.BLOCKS).addSource(new DirectoryLister("fluid", "fluid" + '/'));
  }
}
