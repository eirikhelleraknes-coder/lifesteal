package com.lifesteal.command;

import com.lifesteal.item.LifestealItems;
import com.lifesteal.manager.HeartManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers the /ls command with sub-commands:
 *
 *  /ls withdraw           – gives the caller a Heart Item
 *  /ls gift <player>      – gives <player> a Heart Item
 *  /ls revive <name>      – (op only) revives a banned player
 */
public final class LifestealCommands {

    private LifestealCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {

        dispatcher.register(
                CommandManager.literal("ls")
                        // /ls withdraw
                        .then(CommandManager.literal("withdraw")
                                .executes(LifestealCommands::withdraw))
                        // /ls gift <player>
                        .then(CommandManager.literal("gift")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(LifestealCommands::gift)))
                        // /ls revive <name>  (op-only)
                        .then(CommandManager.literal("revive")
                                .requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(LifestealCommands::revive)))
        );
    }

    // -------------------------------------------------------------------------

    private static int withdraw(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        player.giveItemStack(LifestealItems.createHeartItem());
        player.sendMessage(
                Text.literal("You received a Heart Item!").formatted(Formatting.GREEN),
                false);
        return 1;
    }

    private static int gift(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(ctx, "player");
        } catch (Exception e) {
            source.sendError(Text.literal("Player not found."));
            return 0;
        }

        target.giveItemStack(LifestealItems.createHeartItem());
        target.sendMessage(
                Text.literal("You received a Heart Item!").formatted(Formatting.GREEN),
                false);
        source.sendFeedback(
                () -> Text.literal("Gifted a Heart Item to " + target.getName().getString())
                        .formatted(Formatting.GREEN),
                true);
        return 1;
    }

    private static int revive(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");

        boolean ok = HeartManager.revivePlayer(source.getServer(), name);
        if (ok) {
            source.sendFeedback(
                    () -> Text.literal("Revived " + name + "!").formatted(Formatting.GREEN),
                    true);
            return 1;
        } else {
            source.sendError(Text.literal(name + " is not banned or was not found."));
            return 0;
        }
    }
}