package com.cosmicbreach.onboarding;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Starfall commands (op level 2):
 *
 * <ul>
 *   <li>{@code /cosmicbreach starfall now}: a Starfall for you now (the schedule is untouched).</li>
 *   <li>{@code /cosmicbreach starfall status}: when your next one is due, and what you have.</li>
 *   <li>{@code /cosmicbreach starfall reset}: forget your way in (schedule, Codex, ring), as a new player.</li>
 * </ul>
 */
public final class OnboardingCommands {
    private OnboardingCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("starfall")
                        .then(Commands.literal("now").executes(OnboardingCommands::now))
                        .then(Commands.literal("status").executes(OnboardingCommands::status))
                        .then(Commands.literal("reset").executes(OnboardingCommands::reset))));
    }

    private static int now(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!Starfalls.fallFor(player)) {
            context.getSource().sendFailure(Component.translatable("commands.cosmicbreach.starfall.nowhere"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.starfall.now"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OnboardingState s = player.getData(OnboardingRegistry.STATE);
        long now = player.server.overworld().getDayTime();
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.starfall.status",
                s.nextFall(), s.nextFall() < 0 ? "-" : Long.toString(Math.max(0, s.nextFall() - now)), s.firstFallen(),
                s.codexGiven(), s.codexOpened(), s.ring().map(r -> r.toShortString()).orElse("none")), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.setData(OnboardingRegistry.STATE, OnboardingState.NEW);
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.starfall.reset"), false);
        return 1;
    }
}
