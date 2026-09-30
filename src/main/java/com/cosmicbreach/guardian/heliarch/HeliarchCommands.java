package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.structure.sanctum.SanctumThroneBlock;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.weather.WeatherData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debug commands for the Heliarch (op level 2), under {@code /cosmicbreach debug heliarch}: {@code summon} (as if the
 * caller set a Heart on the throne), {@code info}, {@code attack <name>}, {@code hold <ticks>}, {@code health <hp>},
 * {@code break}, {@code gauge <impact>}, {@code hollow}, {@code nova}, {@code shield} (breaks a Nova's shield), {@code collapse},
 * {@code ahead <ticks>} (the Collapse's clock), {@code kill} (its death and rewards), {@code reset} (it withdraws),
 * {@code taunt <threat>}, {@code seal on|off} (the world's seal).
 */
public final class HeliarchCommands {
    private HeliarchCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> attack = Commands.literal("attack");
        for (HollowHeliarch.Action a : HollowHeliarch.Action.values()) {
            if (a == HollowHeliarch.Action.NONE || a == HollowHeliarch.Action.NOVA) {
                continue;
            }
            attack.then(Commands.literal(a.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> push(c, h -> h.forceNext(a))));
        }
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("heliarch")
                                .then(Commands.literal("summon").executes(HeliarchCommands::summon))
                                .then(Commands.literal("info").executes(HeliarchCommands::info))
                                .then(attack)
                                .then(Commands.literal("hold").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> push(c, h -> h.holdAttacks(IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(Commands.literal("health").then(Commands.argument("hp", FloatArgumentType.floatArg(0f, 5000f))
                                        .executes(c -> push(c, h -> h.debugHealth(FloatArgumentType.getFloat(c, "hp"))))))
                                .then(Commands.literal("break").executes(c -> push(c, HollowHeliarch::debugBreak)))
                                .then(Commands.literal("gauge").then(Commands.argument("impact", FloatArgumentType.floatArg(0f, 1000f))
                                        .executes(c -> push(c, h -> h.debugGauge(FloatArgumentType.getFloat(c, "impact"))))))
                                .then(Commands.literal("hollow").executes(c -> push(c, HollowHeliarch::debugHollow)))
                                .then(Commands.literal("nova").executes(c -> push(c, HollowHeliarch::debugNova)))
                                .then(Commands.literal("shield").executes(c -> push(c, HollowHeliarch::debugBreakShield)))
                                .then(Commands.literal("collapse").executes(c -> push(c, HollowHeliarch::debugCollapse)))
                                .then(Commands.literal("ahead").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 100_000))
                                        .executes(c -> push(c, h -> h.debugCollapseAhead(IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(Commands.literal("kill").executes(c -> push(c, h -> h.debugHealth(0f))))
                                .then(Commands.literal("reset").executes(c -> push(c, h -> h.discard())))
                                .then(Commands.literal("taunt").then(Commands.argument("threat", FloatArgumentType.floatArg(0f, 100_000f))
                                        .executes(c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            return push(c, h -> h.threat().taunt(p.getUUID(), FloatArgumentType.getFloat(c, "threat")));
                                        })))
                                .then(Commands.literal("seal")
                                        .then(Commands.literal("on").executes(c -> seal(c, true)))
                                        .then(Commands.literal("off").executes(c -> seal(c, false)))))));
    }

    private static ServerLevel aetheria(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer().getLevel(AetheriaWorld.LEVEL);
    }

    private static int summon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = aetheria(c);
        if (level == null) {
            c.getSource().sendFailure(Component.literal("no Aetheria"));
            return 0;
        }
        BlockState throne = level.getBlockState(SanctumArena.THRONE);
        if (throne.is(SanctumRegistry.SANCTUM_THRONE.get())) {
            level.setBlock(SanctumArena.THRONE, throne.setValue(SanctumThroneBlock.HEART, true), Block.UPDATE_ALL);
        }
        boolean ok = HollowHeliarch.summon(level, SanctumArena.THRONE, p);
        if (ok) {
            HeliarchData.get(level).clearSummoner(); // no Heart was set, so none goes back if this fight fails
        }
        c.getSource().sendSuccess(() -> Component.literal(ok ? "the Hollow Heliarch rises" : "it does not answer"), false);
        return ok ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ServerLevel level = aetheria(c);
        HollowHeliarch h = level == null ? null : Heliarchs.active(level);
        String s = h == null ? "no Heliarch fight" : h.summary();
        c.getSource().sendSuccess(() -> Component.literal(s), false);
        return h == null ? 0 : 1;
    }

    private static int push(CommandContext<CommandSourceStack> c, Consumer<HollowHeliarch> action) {
        ServerLevel level = aetheria(c);
        HollowHeliarch h = level == null ? null : Heliarchs.active(level);
        if (h == null) {
            c.getSource().sendFailure(Component.literal("no Heliarch fight"));
            return 0;
        }
        action.accept(h);
        return 1;
    }

    private static int seal(CommandContext<CommandSourceStack> c, boolean on) {
        ServerLevel level = aetheria(c);
        if (level == null) {
            return 0;
        }
        WeatherData data = WeatherData.get(level);
        data.schedule().setHeliarchFallen(on);
        data.setDirty();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            HeliarchNet.seal(p, on, false);
        }
        c.getSource().sendSuccess(() -> Component.literal("the Breach is " + (on ? "sealed" : "open")), false);
        return 1;
    }
}
