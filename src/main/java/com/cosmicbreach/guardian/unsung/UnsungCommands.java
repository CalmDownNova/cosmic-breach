package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Debug commands for the Unsung (op level 2):
 * <ul>
 *   <li>{@code /cosmicbreach debug lair unsung}: builds a Silent Nave near the caller (on a Rift Abyss pillar in
 *       Aetheria; elsewhere floating 40 blocks ahead, its door toward the caller) with its sleeping choir.</li>
 *   <li>{@code /cosmicbreach debug unsung info|awaken|reset|cooldown|break|harmonize|hold <ticks>|singer <voice>|
 *       health <voice> <hp>|shatter <voice>}: the nearest choir's state, or a push past a part of the fight.</li>
 * </ul>
 */
public final class UnsungCommands {
    private static @Nullable NaveLayout lastBuilt;

    private UnsungCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> singer = Commands.literal("singer");
        LiteralArgumentBuilder<CommandSourceStack> health = Commands.literal("health");
        LiteralArgumentBuilder<CommandSourceStack> shatter = Commands.literal("shatter");
        for (Voice v : Voice.values()) {
            shatter.then(Commands.literal(v.id()).executes(c -> push(c, u -> u.debugShatter(v))));
            singer.then(Commands.literal(v.id()).executes(c -> push(c, u -> u.forceNextSinger(v))));
            health.then(Commands.literal(v.id()).then(Commands.argument("hp", FloatArgumentType.floatArg(1f, 5000f))
                    .executes(c -> push(c, u -> u.debugHealth(v, FloatArgumentType.getFloat(c, "hp"))))));
        }
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("lair")
                                .then(Commands.literal("unsung").executes(UnsungCommands::buildLair)))
                        .then(Commands.literal("unsung")
                                .then(Commands.literal("info").executes(UnsungCommands::info))
                                .then(Commands.literal("awaken").executes(c -> push(c, u -> u.awaken(player(c), true))))
                                .then(Commands.literal("reset").executes(c -> push(c, u -> u.reset((ServerLevel) u.level(), u.level().getGameTime()))))
                                .then(Commands.literal("cooldown").executes(UnsungCommands::clearCooldown))
                                .then(Commands.literal("break").executes(c -> push(c, Unsung::debugBreak)))
                                .then(Commands.literal("harmonize").executes(c -> push(c, Unsung::debugHarmonizeNext)))
                                .then(Commands.literal("hold").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> push(c, u -> u.holdAttacks(IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(singer)
                                .then(shatter)
                                .then(health))));
    }

    /** The nave the last {@code debug lair unsung} built (dev tests). */
    public static @Nullable NaveLayout lastBuilt() {
        return lastBuilt;
    }

    private static @Nullable ServerPlayer player(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer();
    }

    // ------------------------------------------------------------------ building a lair

    /**
     * Builds the Nave into the world (chunks loaded first), with its altar and sleeping choir; {@code crypts} are the
     * Hollow Crypts sharing its pillar, which it leaves whole and cuts a way into.
     */
    public static void build(ServerLevel level, NaveLayout layout, java.util.List<com.cosmicbreach.structure.crypt.CryptNaveLink> crypts) {
        int[] b = layout.bounds();
        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++) {
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        NaveBuilder.build(level, layout, new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), crypts);
        int[] a = layout.altar();
        BlockPos altarPos = new BlockPos(a[0], a[1], a[2]);
        BlockPos centre = layout.arena().centreBlock();
        if (level.getBlockEntity(altarPos) instanceof GuardianAltarBlockEntity altar) {
            altar.setup(centre);
            GuardianLairs.get(level).register(altarPos, GuardianTypes.UNSUNG, centre);
            if (altar.current(level) == null) {
                altar.spawnGuardian(level);
            }
        }
    }

    private static int buildLair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        NaveLayout layout = debugLayout(level, player);
        build(level, layout, java.util.List.of());
        lastBuilt = layout;
        BlockPos centre = layout.arena().centreBlock();
        int[] door = layout.world(NaveLayout.DOOR_WALL + 1.5, 0.0);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.lair", "unsung",
                centre.getX(), centre.getY(), centre.getZ(), door[0], layout.floorY(), door[1]), true);
        return 1;
    }

    /** A place near the player: a pillar of the Rift Abyss in Aetheria, else 40 blocks ahead, floating. */
    public static NaveLayout debugLayout(ServerLevel level, ServerPlayer player) {
        if (AetheriaWorld.is(level)) {
            AetheriaTerrain terrain = AetheriaTerrain.of(level.getChunkSource().randomState());
            ChunkPos here = new ChunkPos(player.blockPosition());
            int[][] rings = {{0, 0}, {2, 0}, {0, 2}, {-2, 0}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {4, 0}, {0, 4}, {-4, 0}, {0, -4}};
            for (int[] off : rings) {
                Optional<NaveLayout> site = SilentNaveStructure.site(terrain, level.getSeed(), new ChunkPos(here.x + off[0], here.z + off[1]));
                if (site.isPresent()) {
                    return site.get();
                }
            }
        }
        Vec3 ahead = Vec3.directionFromRotation(0f, player.getYRot()).scale(40.0);
        int x = (int) Math.floor(player.getX() + ahead.x);
        int z = (int) Math.floor(player.getZ() + ahead.z);
        level.getChunk(x >> 4, z >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floorY = Math.min(level.getMaxBuildHeight() - NaveLayout.ROOF_MAX - 4, ground + NaveLayout.KEEL_DEPTH);
        // the door toward the player
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        int facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 0 : 2) : (dz > 0 ? 1 : 3);
        return new NaveLayout(x, z, floorY, facing, level.getSeed());
    }

    // ------------------------------------------------------------------ the choir

    static @Nullable Unsung nearest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return null;
        }
        Unsung best = null;
        double bestD = 160.0 * 160.0;
        for (Unsung u : player.serverLevel().getEntitiesOfClass(Unsung.class, player.getBoundingBox().inflate(160.0))) {
            double d = u.distanceToSqr(player);
            if (d < bestD) {
                best = u;
                bestD = d;
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.unsung.none"));
        }
        return best;
    }

    private static int push(CommandContext<CommandSourceStack> context, Consumer<Unsung> what) {
        Unsung u = nearest(context.getSource());
        if (u == null) {
            return 0;
        }
        what.accept(u);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        Unsung u = nearest(context.getSource());
        if (u == null) {
            return 0;
        }
        long now = u.level().getGameTime();
        StringBuilder masks = new StringBuilder();
        u.masks().forEach((v, m) -> masks.append(String.format(Locale.ROOT, "%s %.0f/%.0f %s; ", v.id(), m.getHealth(), m.getMaxHealth(), m.mode())));
        String text = String.format(Locale.ROOT, "state %s beat %d singer %s next %s gauge %.2f broken %s warning %s lit %s health %.0f/%.0f; %s"
                        + "participants %d, breaks %d, harmonizes %d, parries %d",
                u.state(), UnsungSong.fightBeat(now, u.fightStart()), u.singer(), u.nextSinger(), u.gaugeFraction(), u.broken(now),
                u.warning(), java.util.Arrays.toString(u.litCircles()), u.totalHealth(), u.totalMaxHealth(), masks,
                u.participants().size(), u.breaks(), u.harmonizes(), u.parries());
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int clearCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int cleared = 0;
        for (var entry : GuardianLairs.get(level).all().entrySet()) {
            if (entry.getValue().guardian().equals(GuardianTypes.UNSUNG.name())
                    && level.getBlockEntity(entry.getKey()) instanceof GuardianAltarBlockEntity altar) {
                altar.clearCooldown();
                cleared++;
            }
        }
        int n = cleared;
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.colossus.cooldown", n), false);
        return cleared;
    }
}
