package com.cosmicbreach.mount;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * Debug commands for the mounts (op level 2), under {@code /cosmicbreach debug mount}:
 * <ul>
 *   <li>{@code spawn stag [count]}: a herd of wild stags in an arc 10 blocks ahead (3 by default);</li>
 *   <li>{@code spawn manta}: a wild manta 8 blocks ahead, 3 up;</li>
 *   <li>{@code trust <0..5>}: the nearest wild stag's trust;</li>
 *   <li>{@code tame}: tames the nearest mount to the caller;</li>
 *   <li>{@code calm}: the nearest manta's retreat ends;</li>
 *   <li>{@code info}: the nearest stag's and manta's state.</li>
 * </ul>
 */
public final class MountCommands {
    private MountCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("mount")
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("stag")
                                                .executes(c -> spawnStags(c, 3))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 8))
                                                        .executes(c -> spawnStags(c, IntegerArgumentType.getInteger(c, "count")))))
                                        .then(Commands.literal("manta").executes(MountCommands::spawnManta)))
                                .then(Commands.literal("trust")
                                        .then(Commands.argument("trust", IntegerArgumentType.integer(0, StagRules.TAME_AT))
                                                .executes(c -> trust(c, IntegerArgumentType.getInteger(c, "trust")))))
                                .then(Commands.literal("tame").executes(MountCommands::tame))
                                .then(Commands.literal("calm").executes(MountCommands::calm))
                                .then(Commands.literal("info").executes(MountCommands::info)))));
    }

    private static int spawnStags(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        UUID herd = Mounts.newHerd();
        Vec3 ahead = Vec3.directionFromRotation(0f, player.getYRot());
        int made = 0;
        for (int i = 0; i < count; i++) {
            double side = (i - (count - 1) / 2.0) * 2.4;
            Vec3 at = player.position().add(ahead.scale(10.0)).add(ahead.yRot((float) Math.PI / 2).scale(side));
            LumenStag s = Mounts.LUMEN_STAG.get().create(player.serverLevel());
            if (s == null) {
                continue;
            }
            // on the ground there: the column's top if it is near the player's height (terraces), else the player's
            int top = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z));
            double y = Math.abs(top - player.getY()) <= 8 ? top : player.getY();
            s.moveTo(at.x, y, at.z, player.getYRot() + 180f, 0f);
            s.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(s.blockPosition()),
                    MobSpawnType.COMMAND, new LumenStag.HerdData(herd));
            s.setHerd(herd);
            s.setPersistenceRequired();
            player.serverLevel().addFreshEntity(s);
            made++;
        }
        int n = made;
        c.getSource().sendSuccess(() -> Component.literal("spawned " + n + " stags, herd " + herd), false);
        return n;
    }

    private static int spawnManta(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Vec3 at = player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(8.0)).add(0, 3.0, 0);
        DriftManta m = Mounts.DRIFT_MANTA.get().create(player.serverLevel());
        if (m == null) {
            return 0;
        }
        m.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
        m.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
        m.setPersistenceRequired();
        player.serverLevel().addFreshEntity(m);
        c.getSource().sendSuccess(() -> Component.literal("spawned a manta at " + m.blockPosition().toShortString()), false);
        return 1;
    }

    private static int trust(CommandContext<CommandSourceStack> c, int value) throws CommandSyntaxException {
        LumenStag s = Mounts.nearest(c.getSource().getPlayerOrException(), LumenStag.class);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("no stag near"));
            return 0;
        }
        s.setTrust(value);
        return 1;
    }

    private static int tame(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        LumenStag s = Mounts.nearest(player, LumenStag.class);
        DriftManta m = Mounts.nearest(player, DriftManta.class);
        CelestialMount mount = s == null ? m : m == null ? s : s.distanceToSqr(player) <= m.distanceToSqr(player) ? s : m;
        if (mount == null) {
            c.getSource().sendFailure(Component.literal("no mount near"));
            return 0;
        }
        if (mount instanceof LumenStag stag) {
            stag.setTrust(StagRules.TAME_AT);
        }
        mount.tameTo(player);
        return 1;
    }

    private static int calm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DriftManta m = Mounts.nearest(c.getSource().getPlayerOrException(), DriftManta.class);
        if (m == null) {
            return 0;
        }
        m.calm();
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        LumenStag s = Mounts.nearest(player, LumenStag.class);
        DriftManta m = Mounts.nearest(player, DriftManta.class);
        if (s != null) {
            String text = String.format(Locale.ROOT, "stag trust %d tamed %s bolting %s herd %s armor %.0f saddled %s tack %s",
                    s.trust(), s.isTamed(), s.bolting(), s.herd(), s.getArmorValue() * 1.0, s.isSaddled(), s.tackItem());
            c.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        if (m != null) {
            String text = String.format(Locale.ROOT, "manta phase %s clean %d notes %s tamed %s armor %.0f saddled %s tack %s drift %s",
                    m.call().phase(), m.call().clean(), Arrays.toString(m.call().notes()), m.isTamed(), m.getArmorValue() * 1.0,
                    m.isSaddled(), m.tackItem(), m.inDrift());
            c.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        return s == null && m == null ? 0 : 1;
    }
}
