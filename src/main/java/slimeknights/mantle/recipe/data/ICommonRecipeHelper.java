package slimeknights.mantle.recipe.data;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;
import slimeknights.mantle.registration.object.BuildingBlockObject;
import slimeknights.mantle.registration.object.MetalItemObject;
import slimeknights.mantle.registration.object.WallBuildingBlockObject;
import slimeknights.mantle.registration.object.WoodBlockObject;

import java.util.List;
import java.util.Optional;

/**
 * Crafting helper for common recipe types, like stairs, slabs, and packing.
 */
@SuppressWarnings("unused") // API
public interface ICommonRecipeHelper extends IRecipeHelper {
  /* Criteria helpers: private with unique names so they never clash with RecipeProvider#has (protected instance method)
   * or RecipeProvider.inventoryTrigger (private static) when this interface is mixed into a RecipeProvider subclass.
   * Subclasses that need the public criteria call the inherited vanilla methods directly. */

  /** Creates an inventory change criterion for the given item predicates */
  private Criterion<InventoryChangeTrigger.TriggerInstance> criterion(ItemPredicate... predicates) {
    return CriteriaTriggers.INVENTORY_CHANGED.createCriterion(
      new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(predicates)));
  }

  /** Criterion for having the given item */
  private Criterion<InventoryChangeTrigger.TriggerInstance> hasItem(HolderGetter<Item> items, ItemLike item) {
    return criterion(ItemPredicate.Builder.item().of(items, item).build());
  }

  /** Criterion for having an item in the given tag */
  private Criterion<InventoryChangeTrigger.TriggerInstance> hasTag(HolderGetter<Item> items, TagKey<Item> tag) {
    return criterion(ItemPredicate.Builder.item().of(items, tag).build());
  }

  /** Gets the item lookup from the recipe output, needed by the vanilla recipe builders since 1.21.2 */
  private static HolderGetter<Item> items(RecipeOutput consumer) {
    return consumer.lookup(Registries.ITEM);
  }

  /** Gets the recipe key for the given ID */
  private static ResourceKey<Recipe<?>> key(Identifier id) {
    return ResourceKey.create(Registries.RECIPE, id);
  }


  /* Metals */

  /**
   * Registers a recipe packing a small item into a large one
   * @param consumer   Recipe consumer
   * @param category   Recipe category
   * @param large      Large item
   * @param small      Small item
   * @param largeName  Large name
   * @param smallName  Small name
   * @param folder     Recipe folder
   */
  default void packingRecipe(RecipeOutput consumer, RecipeCategory category, String largeName, ItemLike large, String smallName, ItemLike small, String folder) {
    HolderGetter<Item> items = items(consumer);
    // ingot to block
    Identifier largeId = id(large);
    ShapedRecipeBuilder.shaped(items, category, large)
                       .define('#', small)
                       .pattern("###")
                       .pattern("###")
                       .pattern("###")
                       .unlockedBy("has_item", hasItem(items, small))
                       .group(largeId.toString())
                       .save(consumer, key(wrap(largeId, folder, String.format("_from_%ss", smallName))));
    // block to ingot
    Identifier smallId = id(small);
    ShapelessRecipeBuilder.shapeless(items, category, small, 9)
                          .requires(large)
                          .unlockedBy("has_item", hasItem(items, large))
                          .group(smallId.toString())
                          .save(consumer, key(wrap(smallId, folder, String.format("_from_%s", largeName))));
  }

  /**
   * Registers a recipe packing a small item into a large one
   * @param consumer   Recipe consumer
   * @param largeItem  Large item
   * @param smallItem  Small item
   * @param smallTag   Tag for small item
   * @param largeName  Large name
   * @param smallName  Small name
   * @param folder     Recipe folder
   */
  default void packingRecipe(RecipeOutput consumer, RecipeCategory category, String largeName, ItemLike largeItem, String smallName, ItemLike smallItem, TagKey<Item> smallTag, String folder) {
    HolderGetter<Item> items = items(consumer);
    // ingot to block
    // note our item is in the center, any mod allowed around the edges
    Identifier largeId = id(largeItem);
    ShapedRecipeBuilder.shaped(items, category, largeItem)
                       .define('#', smallTag)
                       .define('*', smallItem)
                       .pattern("###")
                       .pattern("#*#")
                       .pattern("###")
                       .unlockedBy("has_item", hasItem(items, smallItem))
                       .group(largeId.toString())
                       .save(consumer, key(wrap(largeId, folder, String.format("_from_%ss", smallName))));
    // block to ingot
    Identifier smallId = id(smallItem);
    ShapelessRecipeBuilder.shapeless(items, category, smallItem, 9)
                          .requires(largeItem)
                          .unlockedBy("has_item", hasItem(items, largeItem))
                          .group(smallId.toString())
                          .save(consumer, key(wrap(smallId, folder, String.format("_from_%s", largeName))));
  }

  /**
   * Adds recipes to convert a block to ingot, ingot to block, and for nuggets
   * @param consumer  Recipe consumer
   * @param metal     Metal object
   * @param folder    Folder for recipes
   */
  default void metalCrafting(RecipeOutput consumer, MetalItemObject metal, String folder) {
    ItemLike ingot = metal.getIngot();
    packingRecipe(consumer, RecipeCategory.MISC, "block", metal.get(), "ingot", ingot, metal.getIngotTag(), folder);
    packingRecipe(consumer, RecipeCategory.MISC, "ingot", ingot, "nugget", metal.getNugget(), metal.getNuggetTag(), folder);
  }


  /* Building blocks */

  /**
   * Registers generic saveing block recipes for slabs and stairs
   * @param consumer  Recipe consumer
   * @param building  Building object instance
   */
  default void slabStairsCrafting(RecipeOutput consumer, BuildingBlockObject building, String folder, boolean addStonecutter) {
    HolderGetter<Item> items = items(consumer);
    Item item = building.asItem();
    Identifier itemId = id(item);
    Criterion<InventoryChangeTrigger.TriggerInstance> hasBlock = hasItem(items, item);
    // slab
    ItemLike slab = building.getSlab();
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, slab, 6)
                       .define('B', item)
                       .pattern("BBB")
                       .unlockedBy("has_item", hasBlock)
                       .group(id(slab).toString())
                       .save(consumer, key(wrap(itemId, folder, "_slab")));
    // stairs
    ItemLike stairs = building.getStairs();
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, stairs, 4)
                       .define('B', item)
                       .pattern("B  ")
                       .pattern("BB ")
                       .pattern("BBB")
                       .unlockedBy("has_item", hasBlock)
                       .group(id(stairs).toString())
                       .save(consumer, key(wrap(itemId, folder, "_stairs")));

    // only add stonecutter if relevant
    if (addStonecutter) {
      Ingredient ingredient = Ingredient.of(item);
      SingleItemRecipeBuilder.stonecutting(ingredient, RecipeCategory.BUILDING_BLOCKS, slab, 2)
                             .unlockedBy("has_item", hasBlock)
                             .save(consumer, key(wrap(itemId, folder, "_slab_stonecutter")));
      SingleItemRecipeBuilder.stonecutting(ingredient, RecipeCategory.BUILDING_BLOCKS, stairs, 1)
                             .unlockedBy("has_item", hasBlock)
                             .save(consumer, key(wrap(itemId, folder, "_stairs_stonecutter")));
    }
  }

  /**
   * Registers generic saveing block recipes for slabs, stairs, and walls
   * @param consumer  Recipe consumer
   * @param building  Building object instance
   */
  default void stairSlabWallCrafting(RecipeOutput consumer, WallBuildingBlockObject building, String folder, boolean addStonecutter) {
    HolderGetter<Item> items = items(consumer);
    slabStairsCrafting(consumer, building, folder, addStonecutter);
    // wall
    Item item = building.asItem();
    Identifier itemId = id(item);
    Criterion<InventoryChangeTrigger.TriggerInstance> hasBlock = hasItem(items, item);
    ItemLike wall = building.getWall();
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, wall, 6)
                       .define('B', item)
                       .pattern("BBB")
                       .pattern("BBB")
                       .unlockedBy("has_item", hasBlock)
                       .group(id(wall).toString())
                       .save(consumer, key(wrap(itemId, folder, "_wall")));
    // only add stonecutter if relevant
    if (addStonecutter) {
      Ingredient ingredient = Ingredient.of(item);
      SingleItemRecipeBuilder.stonecutting(ingredient, RecipeCategory.BUILDING_BLOCKS, wall, 1)
                             .unlockedBy("has_item", hasBlock)
                             .save(consumer, key(wrap(itemId, folder, "_wall_stonecutter")));
    }
  }

  /**
   * Registers recipes relevant to wood
   * @param consumer  Recipe consumer
   * @param wood      Wood types
   * @param folder    Wood folder
   */
  default void woodCrafting(RecipeOutput consumer, WoodBlockObject wood, String folder) {
    HolderGetter<Item> items = items(consumer);
    Criterion<InventoryChangeTrigger.TriggerInstance> hasPlanks = hasItem(items, wood);

    // planks
    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, wood, 4).requires(wood.getLogItemTag())
                          .group("planks")
                          .unlockedBy("has_log", hasTag(items, wood.getLogItemTag()))
                          .save(consumer, key(location(folder + "planks")));
    // slab
    ItemLike slab = wood.getSlab();
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, slab, 6)
                       .define('#', wood)
                       .pattern("###")
                       .unlockedBy("has_planks", hasPlanks)
                       .group("wooden_slab")
                       .save(consumer, key(location(folder + "slab")));
    // stairs
    ItemLike stairs = wood.getStairs();
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, stairs, 4)
                       .define('#', wood)
                       .pattern("#  ")
                       .pattern("## ")
                       .pattern("###")
                       .unlockedBy("has_planks", hasPlanks)
                       .group("wooden_stairs")
                       .save(consumer, key(location(folder + "stairs")));

    // log to stripped
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, wood.getWood(), 3)
                       .define('#', wood.getLog())
                       .pattern("##").pattern("##")
                       .group("bark")
                       .unlockedBy("has_log", hasItem(items, wood.getLog()))
                       .save(consumer, key(location(folder + "log_to_wood")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.BUILDING_BLOCKS, wood.getStrippedWood(), 3)
                       .define('#', wood.getStrippedLog())
                       .pattern("##").pattern("##")
                       .group("bark")
                       .unlockedBy("has_log", hasItem(items, wood.getStrippedLog()))
                       .save(consumer, key(location(folder + "stripped_log_to_wood")));
    // doors
    ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, wood.getFence(), 3)
                       .define('#', Tags.Items.RODS_WOODEN).define('W', wood)
                       .pattern("W#W").pattern("W#W")
                       .group("wooden_fence")
                       .unlockedBy("has_planks", hasPlanks)
                       .save(consumer, key(location(folder + "fence")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, wood.getFenceGate())
                       .define('#', Items.STICK).define('W', wood)
                       .pattern("#W#").pattern("#W#")
                       .group("wooden_fence_gate")
                       .unlockedBy("has_planks", hasPlanks)
                       .save(consumer, key(location(folder + "fence_gate")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, wood.getDoor(), 3)
                       .define('#', wood)
                       .pattern("##").pattern("##").pattern("##")
                       .group("wooden_door")
                       .unlockedBy("has_planks", hasPlanks)
                       .save(consumer, key(location(folder + "door")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, wood.getTrapdoor(), 2)
                       .define('#', wood)
                       .pattern("###").pattern("###")
                       .group("wooden_trapdoor")
                       .unlockedBy("has_planks", hasPlanks)
                       .save(consumer, key(location(folder + "trapdoor")));
    // buttons
    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.REDSTONE, wood.getButton())
                          .requires(wood)
                          .group("wooden_button")
                          .unlockedBy("has_planks", hasPlanks)
                          .save(consumer, key(location(folder + "button")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, wood.getPressurePlate())
                       .define('#', wood)
                       .pattern("##")
                       .group("wooden_pressure_plate")
                       .unlockedBy("has_planks", hasPlanks)
                       .save(consumer, key(location(folder + "pressure_plate")));
    // signs
    ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, wood.getSign(), 3)
                       .group("sign")
                       .define('#', wood).define('X', Tags.Items.RODS_WOODEN)
                       .pattern("###").pattern("###").pattern(" X ")
                       .unlockedBy("has_planks", hasItem(items, wood))
                       .save(consumer, key(location(folder + "sign")));
    ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, wood.getHangingSign(), 6)
                       .group("hanging_sign")
                       .define('#', wood.getStrippedLog())
                       .define('X', Items.IRON_CHAIN)
                       .pattern("X X").pattern("###").pattern("###")
                       .unlockedBy("has_stripped_logs", hasItem(items, wood.getStrippedLog()))
                       .save(consumer, key(location(folder + "hanging_sign")));
  }
}
