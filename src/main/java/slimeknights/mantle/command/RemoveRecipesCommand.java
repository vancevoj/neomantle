package slimeknights.mantle.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Either;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static slimeknights.mantle.util.JsonHelper.DEFAULT_GSON;

/**
 * Command to disable recipes based on various presets or by ID.
 * @see RemoveDataCommand
 */
public class RemoveRecipesCommand {
  // success
  /** Translation key for successfully removing recipes */
  private static final String KEY_SUCCESS = Mantle.makeDescriptionId("command", "remove_recipes");

  // failure
  /** Error on invalid item or tag ID */
  private static final DynamicCommandExceptionType ITEM_NOT_FOUND = new DynamicCommandExceptionType(id -> Mantle.makeComponent("command", "item.not_found", String.valueOf(id)));
  /** Error on invalid preset */
  private static final DynamicCommandExceptionType PRESET_NOT_FOUND = new DynamicCommandExceptionType(id -> Mantle.makeComponent("command", "remove_recipes.preset_not_found", String.valueOf(id)));

  // presets
  /** Loadable for saving a list of recipe types */
  public static final Loadable<List<RecipeType<?>>> RECIPE_TYPES = Loadables.RECIPE_TYPE.list(ArrayLoadable.COMPACT);
  /** Folder containing all preset JSONs */
  public static FileToIdConverter PRESETS = FileToIdConverter.json("mantle/remove_recipes");

  /** Suggestion builder for recipe IDs, recipes are a reloadable registry since 26.3 */
  private static final SuggestionProvider<CommandSourceStack> SUGGESTS_RECIPES = (context, builder)
    -> SharedSuggestionProvider.suggestResource(context.getSource().getServer().getRecipeManager().getRecipes().stream().map(recipe -> recipe.id().identifier()), builder);
  /** Suggests presets for the command */
  private static final SuggestionProvider<CommandSourceStack> SUGGEST_PRESETS = SourcesCommand.suggestFolder(PRESETS);

  /**
   * Registers this sub command with the root command
   *
   * @param subCommand Command builder
   * @param context    Context to fetch the recipe type argument
   */
  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand, CommandBuildContext context) {
    subCommand
      .then(Commands.literal("preset")
        .then(Commands.argument("preset", IdentifierArgument.id()).suggests(SUGGEST_PRESETS)
          .executes(RemoveRecipesCommand::runPreset)))
      .then(Commands.literal("result")
        .then(Commands.argument("recipe_type", ResourceArgument.resource(context, Registries.RECIPE_TYPE))
          .then(Commands.argument("result", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
            .executes(RemoveRecipesCommand::runByResult)
            .then(Commands.argument("input", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
              .executes(RemoveRecipesCommand::runResultInput)))))
      .then(Commands.literal("input")
        .then(Commands.argument("recipe_type", ResourceArgument.resource(context, Registries.RECIPE_TYPE))
          .then(Commands.argument("input", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
            .executes(RemoveRecipesCommand::runByInput))))
      .then(Commands.literal("id")
        .then(Commands.argument("recipe", IdentifierArgument.id()).suggests(SUGGESTS_RECIPES)
          .executes(RemoveRecipesCommand::byId)));
  }

  /** Gets the item predicate */
  @SuppressWarnings("deprecation")
  private static Predicate<Item> getPredicate(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
    Either<ResourceKey<Item>, TagKey<Item>> items = ResourceOrTagKeyArgument.getResourceOrTagKey(context, name, Registries.ITEM, ITEM_NOT_FOUND).unwrap();
    return items.map(
      key -> item -> item.builtInRegistryHolder().is(key),
      tag -> item -> item.builtInRegistryHolder().is(tag));
  }

  /** Runs the command for provided arguments */
  private static int runByResult(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long startTime = System.nanoTime();
    Holder<RecipeType<?>> recipeType = ResourceArgument.getResource(context, "recipe_type", Registries.RECIPE_TYPE);
    return run(context, List.of(recipeType.value()), getPredicate(context, "result"), null, startTime);
  }

  /** Runs the command for provided arguments */
  private static int runByInput(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long startTime = System.nanoTime();
    Holder<RecipeType<?>> recipeType = ResourceArgument.getResource(context, "recipe_type", Registries.RECIPE_TYPE);
    return run(context, List.of(recipeType.value()), null, getPredicate(context, "input"), startTime);
  }

  /** Runs the command for provided arguments */
  private static int runResultInput(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long startTime = System.nanoTime();
    Holder<RecipeType<?>> recipeType = ResourceArgument.getResource(context, "recipe_type", Registries.RECIPE_TYPE);
    return run(context, List.of(recipeType.value()), getPredicate(context, "result"), getPredicate(context, "input"), startTime);
  }

  /** Runs the command using a JSON preset */
  private static int runPreset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long startTime = System.nanoTime();
    Identifier preset = IdentifierArgument.getId(context, "preset");

    // load in the preset JSON
    Identifier presetLocation = PRESETS.idToFile(preset);
    ServerLevel level = context.getSource().getLevel();
    Optional<Resource> resource = level.getServer().getResourceManager().getResource(presetLocation);
    if (resource.isPresent()) {
      JsonObject json = JsonHelper.getJson(resource.get(), presetLocation);
      if (json != null) {
        try {
          // parse the preset
          IJsonPredicate<Item> remove = ItemPredicate.LOADER.getOrDefault(json, "result");
          IJsonPredicate<Item> input = ItemPredicate.LOADER.getOrDefault(json, "input");
          List<RecipeType<?>> recipeTypes = RECIPE_TYPES.getIfPresent(json, "recipe_type");

          // run command
          return run(context, recipeTypes,
            // map any to null to allow quick evaluation
            remove == ItemPredicate.ANY ? null : remove::matches,
            input == ItemPredicate.ANY ? null : input::matches,
            startTime);
        } catch (RuntimeException e) {
          Mantle.logger.error("Failed to parse preset {} from {} in pack '{}'", preset, presetLocation, resource.get().sourcePackId(), e);
        }
      }
    } else {
      Mantle.logger.error("Failed to locate preset {} from {}", preset, presetLocation);
    }
    throw PRESET_NOT_FOUND.create(preset);
  }

  /** Finds all advancements relevant to the given list of recipes and places their IDs in {@code advancements}. */
  private static void findAdvancements(ServerLevel level, List<Identifier> recipes, List<Identifier> advancements) {
    Set<Identifier> isRelevant = new HashSet<>(recipes);
    for (AdvancementHolder advancement : level.getServer().getAdvancements().getAllAdvancements()) {
      for (ResourceKey<Recipe<?>> reward : advancement.value().rewards().recipes()) {
        if (isRelevant.contains(reward.identifier())) {
          advancements.add(advancement.id());
        }
      }
    }
  }

  /** Gets the JSON to remove a recipe */
  private static String getRemoveJson(HolderLookup.Provider registries) {
    JsonObject json = new JsonObject();
    ICondition.writeConditions(registries, json, NeverCondition.INSTANCE);
    return DEFAULT_GSON.toJson(json);
  }

  /** Saves the JSON string at the given ID */
  private static boolean saveJson(Path data, String folder, Identifier id, String json) {
    Path path = data.resolve(id.getNamespace() + '/' + folder + '/' + id.getPath() + ".json");
    try {
      Files.createDirectories(path.getParent());
      try (BufferedWriter writer = Files.newBufferedWriter(path)) {
        writer.write(json);
        return true;
      }
    } catch(IOException e){
      Mantle.logger.error("Couldn't save {} {}", folder, id, e);
      return false;
    }
  }

  /** Saves the JSON string at the given ID */
  private static int saveJsons(Path data, String folder, List<Identifier> ids, String json) {
    int successes = 0;
    for (Identifier id : ids) {
      if (saveJson(data, folder, id, json)) {
        successes += 1;
      }
    }
    return successes;
  }


  /**
   * Checks if any result of the recipe matches the requested item predicate.
   * Since 1.21.2 recipes have no generic result getter, so this uses the recipe displays used by the recipe book and JEI.
   */
  private static boolean matchesResult(Recipe<?> recipe, Predicate<Item> removeResult, ContextMap displayContext) {
    for (RecipeDisplay display : recipe.display()) {
      for (ItemStack stack : display.result().resolveForStacks(displayContext)) {
        if (!stack.isEmpty() && removeResult.test(stack.getItem())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if any ingredient of the recipe matches the requested item predicate.
   * Since 1.21.2 recipes have no generic ingredient list, so this uses the placement info used by the recipe book.
   */
  private static boolean matchesInput(Recipe<?> recipe, Predicate<Item> removeInput) {
    for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
      if (ingredient.items().anyMatch(item -> removeInput.test(item.value()))) {
        return true;
      }
    }
    return false;
  }

  /** Runs the command */
  @SuppressWarnings("unchecked")  // not like we are using the generics at all
  private static <I extends RecipeInput, T extends Recipe<I>> int run(CommandContext<CommandSourceStack> context, List<RecipeType<?>> recipeTypes, @Nullable Predicate<Item> removeResult, @Nullable Predicate<Item> removeInput, long startTime) {
    // iterate all recipes for the type storing recipes that craft the tag
    ServerLevel level = context.getSource().getLevel();
    RegistryAccess access = level.registryAccess();
    RecipeMap recipeMap = level.getServer().getRecipeManager().recipeMap();
    ContextMap displayContext = SlotDisplayContext.fromLevel(level);
    List<Identifier> recipes = new ArrayList<>();
    for (RecipeType<?> recipeType : recipeTypes) {
      for (RecipeHolder<T> holder : recipeMap.byType((RecipeType<T>) recipeType)) {
        Recipe<I> recipe = holder.value();
        // result must match or not be requested, at least one ingredient must match or not be requested
        if ((removeResult == null || matchesResult(recipe, removeResult, displayContext)) && (removeInput == null || matchesInput(recipe, removeInput))) {
          recipes.add(holder.id().identifier());
        }
      }
    }

    // find advancements that grant those recipes
    List<Identifier> advancements = new ArrayList<>();
    findAdvancements(level, recipes, advancements);

    // determine the path for the resulting datapack
    Path pack = GeneratePackHelper.getDatapackPath(level.getServer());
    GeneratePackHelper.saveMcmeta(pack);

    // create the object for removing recipes
    String jsonString = getRemoveJson(access);

    // save all the removals
    Path data = pack.resolve(PackType.SERVER_DATA.getDirectory());
    int recipeSuccesses = saveJsons(data, "recipe", recipes, jsonString);
    int advancementSuccesses = saveJsons(data, "advancement", advancements, jsonString);

    // send success
    float time = (System.nanoTime() - startTime) / 1000000f;
    context.getSource().sendSuccess(() -> Component.translatable(KEY_SUCCESS, recipeSuccesses, time, GeneratePackHelper.getOutputComponent(pack)), true);
    return recipeSuccesses + advancementSuccesses;
  }

  /** Removes a recipe by ID */
  private static int byId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    long startTime = System.nanoTime();
    Identifier id = IdentifierArgument.getId(context, "recipe");

    // determine the path for the resulting datapack
    Path pack = GeneratePackHelper.getDatapackPath(context.getSource().getServer());
    GeneratePackHelper.saveMcmeta(pack);

    // find advancements that grant those recipes
    List<Identifier> advancements = new ArrayList<>();
    findAdvancements(context.getSource().getLevel(), List.of(id), advancements);

    // create the object for removing recipes
    Path data = pack.resolve(PackType.SERVER_DATA.getDirectory());
    Path path = data.resolve(id.getNamespace() + "/recipe/" + id.getPath() + ".json");
    if (!GeneratePackHelper.saveConditionRemove(context.getSource().getServer().registryAccess(), path)) {
      throw GeneratePackHelper.FAILED_SAVE.create(id.toString());
    }

    // create the object for removing advancements
    String jsonString = getRemoveJson(context.getSource().getServer().registryAccess());
    int advancementSuccesses = saveJsons(data, "advancement", advancements, jsonString);

    // send success
    float time = (System.nanoTime() - startTime) / 1000000f;
    context.getSource().sendSuccess(() -> Component.translatable(KEY_SUCCESS, 1, time, GeneratePackHelper.getOutputComponent(pack)), true);
    return 1 + advancementSuccesses;
  }
}
