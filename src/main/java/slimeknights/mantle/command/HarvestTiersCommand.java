package slimeknights.mantle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.Mantle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command to dump the harvest tier ordering.
 * <p>
 * NeoForge 1.21 removed {@code TierSortingRegistry}, so there is no longer a global tier ordering for mods to extend.
 * The harvest tiers are now the vanilla tool materials, each denying drops from blocks in its {@link ToolMaterial#incorrectBlocksForDrops()} tag,
 * so this command reports those in harvest level order.
 */
public class HarvestTiersCommand {
  /** Path to save the tier ordering, matching the old Forge tier ordering file */
  private static final String HARVEST_TIER_PATH = "minecraft/item_tier_ordering.json";
  /** Message when logging the tiers */
  private static final Component SUCCESS_LOG = Component.translatable("command.mantle.harvest_tiers.success_log");
  /** Vanilla tool materials in harvest level order */
  private static final List<Tier> TIERS = List.of(
    new Tier("wood", ToolMaterial.WOOD),
    new Tier("gold", ToolMaterial.GOLD),
    new Tier("stone", ToolMaterial.STONE),
    new Tier("copper", ToolMaterial.COPPER),
    new Tier("iron", ToolMaterial.IRON),
    new Tier("diamond", ToolMaterial.DIAMOND),
    new Tier("netherite", ToolMaterial.NETHERITE)
  );

  /**
   * Registers this sub command with the root command
   * @param subCommand  Command builder
   */
  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand) {
    subCommand.requires(Commands.hasPermission(MantleCommand.PERMISSION_EDIT_SPAWN))
              .then(Commands.literal("save").executes(context -> run(context, true)))
              .then(Commands.literal("log").executes(context -> run(context, false)))
              .then(Commands.literal("list").executes(HarvestTiersCommand::list));
  }

  /** Gets a clickable component for a block tag, suggesting the command to dump it */
  private static Component getTagComponent(TagKey<Block> tag) {
    Identifier id = tag.location();
    return Component.literal(id.toString()).withStyle(style -> style.withUnderlined(true)
      .withClickEvent(new ClickEvent.SuggestCommand("/mantle tags entries save " + Registries.BLOCK.identifier() + " " + id)));
  }

  /** Lists all tiers in chat */
  private static int list(CommandContext<CommandSourceStack> context) {
    MutableComponent output = Component.translatable("command.mantle.harvest_tiers.success_list");
    for (Tier tier : TIERS) {
      output.append("\n* ").append(Component.translatable("command.mantle.harvest_tiers.tag", tier.name, getTagComponent(tier.material.incorrectBlocksForDrops())));
    }
    context.getSource().sendSuccess(() -> output, true);
    return TIERS.size();
  }

  /** Saves or logs the tier ordering */
  private static int run(CommandContext<CommandSourceStack> context, boolean saveFile) {
    JsonArray order = new JsonArray();
    for (Tier tier : TIERS) {
      order.add(tier.name);
    }
    JsonObject json = new JsonObject();
    json.add("order", order);

    if (saveFile) {
      Path path = DumpAllTagsCommand.getOutputFile(context).toPath().resolve(HARVEST_TIER_PATH);
      try {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
          writer.write(DumpTagCommand.GSON.toJson(json));
        }
      } catch (IOException ex) {
        Mantle.logger.error("Couldn't save harvests tiers to {}", path, ex);
      }
      context.getSource().sendSuccess(() -> Component.translatable("command.mantle.harvest_tiers.success_save", GeneratePackHelper.getOutputComponent(path)), true);
    } else {
      context.getSource().sendSuccess(() -> SUCCESS_LOG, true);
      Mantle.logger.info("Dump of harvests tiers:\n{}", DumpTagCommand.GSON.toJson(json));
    }
    return TIERS.size();
  }

  /** Named vanilla tool material */
  private record Tier(String name, ToolMaterial material) {}
}
