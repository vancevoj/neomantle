package slimeknights.mantle.datagen.tag;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.BuiltinRegistryTagProvider;
import slimeknights.mantle.datagen.MantleTags;

import java.util.concurrent.CompletableFuture;

/** Tag provider for Mantle menu tags */
@Internal
public class MantleMenuTagProvider extends BuiltinRegistryTagProvider<MenuType<?>> {
  public MantleMenuTagProvider(PackOutput packOutput, CompletableFuture<Provider> lookupProvider) {
    super(packOutput, BuiltInRegistries.MENU, lookupProvider, Mantle.modId);
  }

  @Override
  protected void addTags(Provider provider) {
    tag(MantleTags.MenuTypes.REPLACEABLE).add(
      // generic inventories are safe
      // anything with a notable UI component where you might lose progress (e.g. crafting table) is left out
      MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
      MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6,
      MenuType.SHULKER_BOX,
      MenuType.GENERIC_3x3, MenuType.HOPPER,
      MenuType.FURNACE, MenuType.BLAST_FURNACE, MenuType.SMOKER,
      MenuType.BREWING_STAND
    );
  }

  @Override
  public String getName() {
    return "Mantle Menu Tag Provider";
  }
}
