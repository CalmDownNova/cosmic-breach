package com.cosmicbreach.progression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The progression's test commands under {@code /cosmicbreach debug} (op level 2; Brigadier merges
 * them into the tree {@code CosmicBreachCommand} registers):
 * <ul>
 *   <li>{@code xp <amount>}: Attunement XP, exactly like a reward (levels, feedback, the free draught).</li>
 *   <li>{@code level <1..50>}: straight to a level with no XP toward the next.</li>
 *   <li>{@code stats <power> <agility> <arcane> <resilience>}: exactly this allocation (0 to 30 each),
 *       adding bonus points if the level hasn't earned that many.</li>
 * </ul>
 */
public final class ProgressionCommands {
    private ProgressionCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("xp")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(ProgressionCommands::xp)))
                        .then(Commands.literal("level")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, Attunement.MAX_LEVEL))
                                        .executes(ProgressionCommands::level)))
                        .then(Commands.literal("stats")
                                .then(stat("power").then(stat("agility").then(stat("arcane").then(stat("resilience")
                                        .executes(ProgressionCommands::stats))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> stat(String name) {
        return Commands.argument(name, IntegerArgumentType.integer(0, Attunement.STAT_CAP));
    }

    private static int xp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        AttunementXp.award(player, amount);
        report(context.getSource(), "commands.cosmicbreach.debug.xp", player, player.getDisplayName(), amount);
        return 1;
    }

    private static int level(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Attunements.set(player, Attunements.of(player).atLevel(IntegerArgumentType.getInteger(context, "level")));
        report(context.getSource(), "commands.cosmicbreach.debug.level", player, player.getDisplayName());
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Allocation allocation = new Allocation(IntegerArgumentType.getInteger(context, "power"),
                IntegerArgumentType.getInteger(context, "agility"), IntegerArgumentType.getInteger(context, "arcane"),
                IntegerArgumentType.getInteger(context, "resilience"));
        Attunements.set(player, Attunements.of(player).withSpent(allocation));
        report(context.getSource(), "commands.cosmicbreach.debug.stats", player, player.getDisplayName());
        return 1;
    }

    /** One line with the command's own words, then the state it left: level, XP, points and allocation. */
    private static void report(CommandSourceStack source, String key, ServerPlayer player, Object... args) {
        Attunement state = Attunements.of(player);
        Allocation spent = state.spent();
        source.sendSuccess(() -> Component.translatable(key, args).append(" ").append(Component.translatable(
                "commands.cosmicbreach.debug.attunement_state", state.level(), state.xp(), state.xpToNext(),
                state.unspent(), spent.power(), spent.agility(), spent.arcane(), spent.resilience())), false);
    }
}
