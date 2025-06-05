package slimeknights.mantle.datagen;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.Mantle;

/** List of all tags used directly by mantle */
public class MantleTags {
  public static void init() {
    Fluids.init();
  }

  public static class Items {
    /** Tag of empty glass bottles that would contain a splash potion */
    public static final TagKey<Item> SPLASH_BOTTLE = common("bottles/splash");
    /** Tag of empty glass bottles that would contain a lingering potion */
    public static final TagKey<Item> LINGERING_BOTTLE = common("bottles/lingering");

    /** Adds a common domain tag */
    private static TagKey<Item> common(String name) {
      return TagKey.create(Registries.ITEM, Mantle.commonResource(name));
    }
  }

  public static class Fluids {
    private static void init() {}

    /**
     * This tag represents vanilla water, but is not used by vanilla logic.
     * Means it's not going to be filled with random mod entries that are not water making it safe for recipes
     */
    public static final TagKey<Fluid> WATER = tag("water");
    /**
     * This tag represents vanilla lava, but is not used by vanilla logic.
     * Means it's not going to be filled with random mod entries that are not water making it safe for recipes
     */
    public static final TagKey<Fluid> LAVA = tag("lava");

    // common fluids with Mantle compat
    /** Anything classified as a soup, notably used for tooltips */
    public static final TagKey<Fluid> SOUP = tag("soup");
    /** Fluid inside honey bottles, at 250mb per bottle */
    public static final TagKey<Fluid> HONEY = common("honey");
    /** Fluid inside beetroot soup bowls, at 250mb per bowl */
    public static final TagKey<Fluid> BEETROOT_SOUP = common("beetroot_soup");
    /** Fluid inside mushroom stew bowls, at 250mb per bowl */
    public static final TagKey<Fluid> MUSHROOM_STEW = common("mushroom_stew");
    /** Fluid inside rabbit stew bowls, at 250mb per bowl */
    public static final TagKey<Fluid> RABBIT_STEW = common("rabbit_stew");
    /** Fluid inside potion bottles, at 250mb per bottle */
    public static final TagKey<Fluid> POTION = common("potion");


    /** Adds a mantle domain tag */
    private static TagKey<Fluid> tag(String name) {
      return TagKey.create(Registries.FLUID, Mantle.getResource(name));
    }

    /** Adds a common domain tag */
    private static TagKey<Fluid> common(String name) {
      return TagKey.create(Registries.FLUID, Mantle.commonResource(name));
    }
  }
}
