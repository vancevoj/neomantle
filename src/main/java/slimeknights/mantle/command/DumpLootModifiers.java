package slimeknights.mantle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.neoforge.resource.NeoForgeReloadListeners;
import slimeknights.mantle.Mantle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command to dump global loot modifiers.
 * <p>
 * Since 26.3 NeoForge has no global loot modifier index file, every file under {@code loot_modifiers} is loaded and ordered by priority.
 * This command dumps the loaded modifiers in the order they apply, along with their priority.
 */
public class DumpLootModifiers {
  /** Resource location of the dumped loot modifier ordering */
  protected static final Identifier GLOBAL_LOOT_MODIFIERS = Identifier.fromNamespaceAndPath("neoforge", "loot_modifier_order.json");
  /** Path for saving the loot modifiers */
  private static final String LOOT_MODIFIER_PATH = GLOBAL_LOOT_MODIFIERS.getNamespace() + "/" + GLOBAL_LOOT_MODIFIERS.getPath();

  // loot modifiers
  private static final Component LOOT_MODIFIER_SUCCESS_LOG = Component.translatable("command.mantle.dump_loot_modifiers.success_log");
  // 1.21: TranslatableContents args must be Component/Number/Boolean/String, so pass the location as a string
  protected static final SimpleCommandExceptionType ERROR_READING_LOOT_MODIFIERS = new SimpleCommandExceptionType(Component.translatable("command.mantle.dump_loot_modifiers.read_error", GLOBAL_LOOT_MODIFIERS.toString()));

  /**
   * Registers this sub command with the root command
   * @param subCommand  Command builder
   */
  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand) {
    subCommand.requires(Commands.hasPermission(MantleCommand.PERMISSION_EDIT_SPAWN))
              .then(Commands.literal("save").executes(source -> run(source, true)))
              .then(Commands.literal("log").executes(source -> run(source, false)));
  }


  /** Runs the command, dumping the loot modifiers */
  private static int run(CommandContext<CommandSourceStack> context, boolean saveFile) throws CommandSyntaxException {
    LootModifierManager manager;
    try {
      manager = context.getSource().getServer().getServerResources().managers().getListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
    } catch (RuntimeException ex) {
      Mantle.logger.error("Failed to fetch the global loot modifier manager", ex);
      throw ERROR_READING_LOOT_MODIFIERS.create();
    }

    // modifiers are already sorted in the order they apply
    JsonArray entries = new JsonArray();
    for (IGlobalLootModifier modifier : manager.getSortedModifiers()) {
      Identifier id = manager.getId(modifier);
      JsonObject entry = new JsonObject();
      entry.addProperty("id", id == null ? "<unknown>" : id.toString());
      entry.addProperty("priority", modifier.priority());
      entries.add(entry);
    }
    JsonObject json = new JsonObject();
    json.add("entries", entries);

    // if requested, save
    if (saveFile) {
      // save file
      Path path = DumpAllTagsCommand.getOutputFile(context).toPath().resolve(LOOT_MODIFIER_PATH);
      try {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
          writer.write(DumpTagCommand.GSON.toJson(json));
        }
      } catch (IOException ex) {
        Mantle.logger.error("Couldn't save global loot manager to {}", path, ex);
      }
      context.getSource().sendSuccess(() -> Component.translatable("command.mantle.dump_loot_modifiers.success_save", GeneratePackHelper.getOutputComponent(path)), true);
    } else {
      // print to console
      context.getSource().sendSuccess(() -> LOOT_MODIFIER_SUCCESS_LOG, true);
      Mantle.logger.info("Dump of global loot modifiers in application order:\n{}", DumpTagCommand.GSON.toJson(json));
    }
    // return a number to finish
    return entries.size();
  }
}
