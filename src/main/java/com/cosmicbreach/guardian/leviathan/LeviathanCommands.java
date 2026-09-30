package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics.Attack;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Debug commands for the Leviathan (op level 2):
 * <ul>
 *   <li>{@code /cosmicbreach debug lair leviathan}: builds a Leviathan Rift about 95 blocks ahead of the caller (in
 *       the Drift's band in Aetheria, else 100 over the ground), with its sleeping Leviathan, and says where its
 *       entrance ledge is.</li>
 *   <li>{@code /cosmicbreach debug leviathan info|awaken|reset|cooldown|break|kill|attack <name>|hold <ticks>|health <hp>}:
 *       the nearest Leviathan's state, or a push past a part of the fight.</li>
 * </ul>
 */
public final class LeviathanCommands {
    private static @Nullable RiftLayout lastBuilt;
    private static long lastBuildMillis;

    private LeviathanCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("lair")
                                .then(Commands.literal("leviathan").executes(LeviathanCommands::buildLair)))
                        .then(Commands.literal("leviathan")
                                .then(Commands.literal("info").executes(LeviathanCommands::info))
                                .then(Commands.literal("awaken").executes(c -> push(c, l -> l.awaken(player(c), true))))
                                .then(Commands.literal("reset").executes(c -> push(c, l -> l.reset((ServerLevel) l.level(), l.level().getGameTime()))))
                                .then(Commands.literal("cooldown").executes(LeviathanCommands::clearCooldown))
                                .then(Commands.literal("break").executes(c -> push(c, ThalassineLeviathan::debugBreak)))
                                .then(Commands.literal("kill").executes(c -> push(c, ThalassineLeviathan::debugKill)))
                                .then(Commands.literal("hold")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1_000_000))
                                                .executes(c -> push(c, l -> l.holdAttacks(IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(Commands.literal("health")
                                        .then(Commands.argument("hp", FloatArgumentType.floatArg(0f, 4000f))
                                                .executes(c -> push(c, l -> l.debugHealth(FloatArgumentType.getFloat(c, "hp"))))))
                                .then(Commands.literal("attack")
                                        .then(Commands.literal("dive").executes(c -> push(c, l -> l.forceNext(Attack.DIVE))))
                                        .then(Commands.literal("song").executes(c -> push(c, l -> l.forceNext(Attack.SONG))))
                                        .then(Commands.literal("flick").executes(c -> push(c, l -> l.forceNext(Attack.FLICK))))
                                        .then(Commands.literal("shed").executes(c -> push(c, l -> l.forceNext(Attack.SHED))))))));
    }

    /** The Rift the last {@code debug lair leviathan} built (dev tests). */
    public static @Nullable RiftLayout lastBuilt() {
        return lastBuilt;
    }

    public static long lastBuildMillis() {
        return lastBuildMillis;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        try {
            return c.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int buildLair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 ahead = Vec3.directionFromRotation(0f, player.getYRot()).scale(95.0);
        int x = (int) Math.floor(player.getX() + ahead.x);
        int z = (int) Math.floor(player.getZ() + ahead.z);
        int y = RiftLayout.CENTRE_Y;
        if (!AetheriaWorld.is(level)) {
            level.getChunk(x >> 4, z >> 4);
            y = Math.min(level.getMaxBuildHeight() - RiftLayout.RY - 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 100);
        }
        RiftLayout layout = LeviathanRiftStructure.layout(level.getSeed(), new BlockPos(x, y, z));
        long t0 = System.nanoTime();
        int[] b = layout.bounds();
        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++) {
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        RiftBuilder.build(level, layout, new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
        BlockPos bell = layout.bell();
        if (level.getBlockEntity(bell) instanceof GuardianAltarBlockEntity altar) {
            altar.setup(layout.centreBlock());
            GuardianLairs.get(level).register(bell, GuardianTypes.LEVIATHAN, layout.centreBlock());
            if (altar.current(level) == null) {
                altar.spawnGuardian(level);
            }
        }
        lastBuildMillis = (System.nanoTime() - t0) / 1_000_000;
        lastBuilt = layout;
        Vec3 ledge = layout.ledgeTop();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.lair.leviathan", layout.x(), layout.y(), layout.z(),
                (int) Math.floor(ledge.x), (int) Math.floor(ledge.y), (int) Math.floor(ledge.z)), true);
        return 1;
    }

    private static @Nullable ThalassineLeviathan nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ThalassineLeviathan best = null;
        double bestD = 200.0 * 200.0;
        for (ThalassineLeviathan l : player.serverLevel().getEntitiesOfClass(ThalassineLeviathan.class, player.getBoundingBox().inflate(200.0))) {
            double d = l.distanceToSqr(player);
            if (d < bestD) {
                best = l;
                bestD = d;
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.leviathan.none"));
        }
        return best;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ThalassineLeviathan l = nearest(context.getSource());
        if (l == null) {
            return 0;
        }
        String text = String.format(Locale.ROOT, "state %s moor %s phase %d action %s health %.1f/%.1f gauge %.2f broken %s participants %d counts %s",
                l.state(), l.moorStage(), l.phase(), l.action(), l.getHealth(), l.getMaxHealth(), l.gaugeFraction(), l.isBroken(),
                l.participants().size(), Arrays.toString(l.counts()));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int clearCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int cleared = 0;
        for (var entry : GuardianLairs.get(level).all().entrySet()) {
            if (level.getBlockEntity(entry.getKey()) instanceof GuardianAltarBlockEntity altar) {
                altar.clearCooldown();
                cleared++;
            }
        }
        int n = cleared;
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.colossus.cooldown", n), false);
        return cleared;
    }

    private static int push(CommandContext<CommandSourceStack> context, Consumer<ThalassineLeviathan> what) throws CommandSyntaxException {
        ThalassineLeviathan l = nearest(context.getSource());
        if (l == null) {
            return 0;
        }
        what.accept(l);
        return 1;
    }
}
