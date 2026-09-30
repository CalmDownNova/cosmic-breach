package com.cosmicbreach.voice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug commands for the Starfall's voice (op level 2), on the calling player:
 * <ul>
 *   <li>{@code /cosmicbreach echo status}: the lines they have heard.</li>
 *   <li>{@code /cosmicbreach echo reset}: forgets them, so every line plays again.</li>
 *   <li>{@code /cosmicbreach echo play <line>}: plays a line now (queued like any other), heard or not, without
 *       remembering it.</li>
 * </ul>
 */
public final class EchoCommands {
    private EchoCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("echo")
                        .then(Commands.literal("status").executes(EchoCommands::status))
                        .then(Commands.literal("reset").executes(EchoCommands::reset))
                        .then(Commands.literal("play")
                                .then(Commands.argument("line", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(EchoLine.values()).map(EchoLine::id), b))
                                        .executes(EchoCommands::play)))));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String heard = Arrays.stream(EchoLine.values()).filter(l -> Echo.heard(player, l)).map(EchoLine::id)
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.echo.status",
                heard.isEmpty() ? "-" : heard), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Echo.forget(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.echo.reset"), false);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "line");
        var line = EchoLine.byId(id);
        if (line.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.cosmicbreach.echo.unknown", id));
            return 0;
        }
        Echo.play(context.getSource().getPlayerOrException(), line.get());
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.echo.play", id), false);
        return 1;
    }
}
