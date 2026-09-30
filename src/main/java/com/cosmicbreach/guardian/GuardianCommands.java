package com.cosmicbreach.guardian;

import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.CrownSpireBuilder;
import com.cosmicbreach.guardian.colossus.CrownSpireLayout;
import com.cosmicbreach.guardian.colossus.CrownSpireStructure;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.Refraction;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * Debug commands for the guardians (op level 2):
 * <ul>
 *   <li>{@code /cosmicbreach debug lair colossus}: builds a Crown Spire near the caller (on a Shattered Spires
 *       island in Aetheria; elsewhere 30 blocks ahead) with its dormant Colossus, and says where its door is.</li>
 *   <li>{@code /cosmicbreach debug colossus info|awaken|reset|cooldown|attack <name>|hold <ticks>|health <hp>}:
 *       the nearest Colossus's state, or a push past a part of the fight.</li>
 * </ul>
 * {@code debug locate} lists the nearest lair of each guardian ({@link #locateLines}).
 */
public final class GuardianCommands {
    private static @Nullable CrownSpireLayout lastBuilt;

    private GuardianCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("lair")
                                .then(Commands.literal("colossus").executes(GuardianCommands::buildColossusLair)))
                        .then(Commands.literal("colossus")
                                .then(Commands.literal("info").executes(c -> info(c)))
                                .then(Commands.literal("awaken").executes(c -> awaken(c)))
                                .then(Commands.literal("reset").executes(c -> reset(c)))
                                .then(Commands.literal("cooldown").executes(c -> clearCooldown(c)))
                                .then(Commands.literal("break").executes(c -> push(c, PrismColossus::debugBreak)))
                                .then(Commands.literal("phase2").executes(c -> push(c, PrismColossus::debugPhaseTwo)))
                                .then(Commands.literal("shatter").executes(c -> push(c, PrismColossus::debugShatter)))
                                .then(Commands.literal("hold")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> hold(c))))
                                .then(Commands.literal("health")
                                        .then(Commands.argument("hp", FloatArgumentType.floatArg(1f, 2000f)).executes(c -> health(c))))
                                .then(Commands.literal("attack")
                                        .then(Commands.literal("slam").executes(c -> attack(c, PrismColossus.Action.SLAM)))
                                        .then(Commands.literal("double").executes(c -> attack(c, PrismColossus.Action.DOUBLE_SLAM)))
                                        .then(Commands.literal("sweep").executes(c -> attack(c, PrismColossus.Action.SWEEP)))
                                        .then(Commands.literal("refraction").executes(c -> attack(c, PrismColossus.Action.REFRACTION)))
                                        .then(Commands.literal("burst").executes(c -> attack(c, PrismColossus.Action.BURST)))))));
    }

    /** The lair the last {@code debug lair} built (dev tests). */
    public static @Nullable CrownSpireLayout lastBuilt() {
        return lastBuilt;
    }

    // ------------------------------------------------------------------ building a lair

    private static int buildColossusLair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        CrownSpireLayout layout = debugLayout(level, player);
        int[] b = layout.bounds();
        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++) {
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BoundingBox all = new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        CrownSpireBuilder.build(level, layout, all);
        int[] a = layout.altar();
        BlockPos altarPos = new BlockPos(a[0], a[1], a[2]);
        if (level.getBlockEntity(altarPos) instanceof GuardianAltarBlockEntity altar) {
            altar.setup(layout.arena().centreBlock());
            GuardianLairs.get(level).register(altarPos, GuardianTypes.COLOSSUS, layout.arena().centreBlock());
            if (altar.current(level) == null) {
                altar.spawnGuardian(level);
            }
        }
        lastBuilt = layout;
        int[] door = layout.riseDoorOutside();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.lair", "colossus",
                layout.x(), layout.floorY(), layout.z(), door[0], door[1], door[2]), true);
        return 1;
    }

    /** A spot for a lair near the player: the island search in Aetheria, else 30 blocks ahead on the ground. */
    static CrownSpireLayout debugLayout(ServerLevel level, ServerPlayer player) {
        if (AetheriaWorld.is(level)) {
            AetheriaTerrain terrain = AetheriaTerrain.of(level.getChunkSource().randomState());
            ChunkPos here = new ChunkPos(player.blockPosition());
            int[][] rings = {{0, 0}, {3, 0}, {0, 3}, {-3, 0}, {0, -3}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {6, 0}, {0, 6}, {-6, 0}, {0, -6}};
            for (int[] off : rings) {
                Optional<CrownSpireLayout> site = CrownSpireStructure.site(terrain, level.getSeed(),
                        new ChunkPos(here.x + off[0], here.z + off[1]), player.getBlockX(), player.getBlockZ(), 34.0);
                if (site.isPresent()) {
                    return site.get();
                }
            }
        }
        Vec3 ahead = Vec3.directionFromRotation(0f, player.getYRot()).scale(30.0);
        int x = (int) Math.floor(player.getX() + ahead.x);
        int z = (int) Math.floor(player.getZ() + ahead.z);
        level.getChunk(x >> 4, z >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floorY = Math.min(level.getMaxBuildHeight() - CrownSpireLayout.CLEAR_HEIGHT - 2, ground + 80);
        return new CrownSpireLayout(x, z, floorY, ground, ground - 6);
    }

    // ------------------------------------------------------------------ the Colossus

    private static @Nullable PrismColossus nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PrismColossus best = null;
        double bestD = 128.0 * 128.0;
        for (PrismColossus c : player.serverLevel().getEntitiesOfClass(PrismColossus.class, player.getBoundingBox().inflate(128.0))) {
            double d = c.distanceToSqr(player);
            if (d < bestD) {
                best = c;
                bestD = d;
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.colossus.none"));
        }
        return best;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        String lit = Arrays.stream(c.litCrystals()).mapToObj(Integer::toString).collect(Collectors.joining(","));
        String paths = c.refractionPaths().stream().map(p -> Arrays.stream(p).mapToObj(n -> n == Refraction.CORE ? "core" : Integer.toString(n))
                .collect(Collectors.joining(">"))).collect(Collectors.joining(" "));
        String text = String.format(Locale.ROOT, "state %s phase %d action %s health %.1f/%.1f gauge %.2f broken %s participants %d lit [%s] paths [%s]",
                c.state(), c.phase(), c.action(), c.getHealth(), c.maxHealth(), c.gaugeFraction(), c.isBroken(),
                c.participants().size(), lit, paths);
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int awaken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        c.awaken(context.getSource().getPlayerOrException(), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        c.reset((ServerLevel) c.level(), c.level().getGameTime());
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

    private static int hold(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        c.holdAttacks(IntegerArgumentType.getInteger(context, "ticks"));
        return 1;
    }

    private static int health(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        c.debugHealth(FloatArgumentType.getFloat(context, "hp"));
        return 1;
    }

    private static int push(CommandContext<CommandSourceStack> context, java.util.function.Consumer<PrismColossus> what)
            throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        what.accept(c);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> context, PrismColossus.Action action) throws CommandSyntaxException {
        PrismColossus c = nearest(context.getSource());
        if (c == null) {
            return 0;
        }
        c.forceNext(action);
        return 1;
    }

    // ------------------------------------------------------------------ locate

    /** The lines {@code /cosmicbreach debug locate} adds: the nearest lair of each guardian. */
    public static void locateLines(CommandSourceStack source, ServerLevel level, BlockPos from) {
        for (GuardianType type : GuardianType.all()) {
            Optional<BlockPos> lair = GuardianLairs.nearest(level, from, type);
            String where = lair.map(p -> p.toShortString() + " (" + (int) Math.sqrt(p.distSqr(from.atY(p.getY()))) + " blocks)")
                    .orElse("none within about " + GuardianLairs.SEARCH_BLOCKS + " blocks");
            source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.locate.lair", type.name(), where), false);
        }
    }

    /** For tests: the arena of the last built lair. */
    public static @Nullable CrownArena lastArena() {
        return lastBuilt == null ? null : lastBuilt.arena();
    }
}
