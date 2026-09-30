package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.structure.StructureCommands;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Sanctum's debug commands (op level 2):
 *
 * <ul>
 *   <li>{@code /cosmicbreach debug place sanctum}: builds the whole Sanctum at its place in Aetheria, as worldgen would
 *       (for test worlds, which generate no structures), and says where it stands and how long it took.</li>
 *   <li>{@code /cosmicbreach debug goto sanctum [edge|gate|hall|seal|arena|throne|rim]}: onto the Deep platform where
 *       the causeway starts, looking at the Sanctum (the default), or to a spot inside it.</li>
 *   <li>{@code /cosmicbreach debug sanctum info|reset|lock west|lock east}: its state; forget everything and build it
 *       afresh; light a lock as its puzzle would.</li>
 * </ul>
 */
public final class SanctumCommands {
    private static volatile long lastBuildMillis = -1;

    private SanctumCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> go = Commands.literal("sanctum").executes(c -> go(c, "edge"));
        for (String where : new String[] {"edge", "gate", "hall", "seal", "arena", "throne", "rim"}) {
            go.then(Commands.literal(where).executes(c -> go(c, where)));
        }
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("place").then(Commands.literal("sanctum").executes(SanctumCommands::place)))
                        .then(Commands.literal("goto").then(go))
                        .then(Commands.literal("sanctum")
                                .then(Commands.literal("info").executes(SanctumCommands::info))
                                .then(Commands.literal("reset").executes(SanctumCommands::reset))
                                .then(Commands.literal("lock")
                                        .then(Commands.literal("west").executes(c -> lock(c, Wing.WEST)))
                                        .then(Commands.literal("east").executes(c -> lock(c, Wing.EAST)))))));
    }

    private static ServerLevel aetheria(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(AetheriaWorld.LEVEL);
        if (level == null) {
            throw new IllegalStateException("Aetheria is missing from this world (datapack not loaded?)");
        }
        return level;
    }

    /** Builds the whole Sanctum at its place. Returns the milliseconds it took. */
    public static long build(ServerLevel level) {
        long t0 = System.nanoTime();
        StructureCommands.build(level, SanctumRegistry.BREACH_SANCTUM, new SanctumPiece(Sanctums.layout(level)));
        lastBuildMillis = (System.nanoTime() - t0) / 1_000_000;
        return lastBuildMillis;
    }

    public static long lastBuildMillis() {
        return lastBuildMillis;
    }

    private static int place(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = aetheria(source);
        long ms = build(level);
        SanctumLayout l = Sanctums.layout(level);
        int[] e = l.end();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.place.sanctum", l.side() < 0 ? "north" : "south",
                e[0], e[1], e[2], String.format(Locale.ROOT, "%.0f", l.causewayLength()), ms), false);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> context, String where) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = aetheria(source);
        SanctumLayout l = Sanctums.layout(level);
        double[] at;
        Vec3 look;
        Vec3 throne = new Vec3(0.5, SanctumLayout.ARENA_Y + 1, 0.5);
        switch (where) {
            case "gate" -> {
                at = l.local(0, SanctumLayout.COURT_D0 + 5);
                double[] g = l.gateCentre();
                look = new Vec3(g[0], g[1], g[2]);
            }
            case "hall" -> {
                at = l.local(0, SanctumLayout.WING_D);
                look = vec(l.local(0, SanctumLayout.SEAL_D)).add(0, 3, 0);
            }
            case "seal" -> {
                at = l.local(0, SanctumLayout.SEAL_D + 3);
                look = vec(l.local(0, SanctumLayout.SEAL_D)).add(0, 3, 0);
            }
            case "arena" -> {
                at = new double[] {0.5, SanctumLayout.ARENA_Y, l.side() * 26 + 0.5};
                look = throne;
            }
            case "throne" -> {
                at = new double[] {0.5, SanctumLayout.ARENA_Y, l.side() * 3 + 0.5};
                look = throne;
            }
            case "rim" -> {
                at = new double[] {0.5, SanctumLayout.ARENA_Y, -l.side() * 26 + 0.5};
                look = throne;
            }
            default -> {
                int[] e = l.end();
                at = new double[] {e[0] + 0.5, e[1], e[2] + 0.5};
                double[] g = l.gateCentre();
                look = new Vec3(g[0], g[1], g[2]);
            }
        }
        Vec3 eye = new Vec3(at[0], at[1] + 1.62, at[2]);
        Vec3 d = look.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        player.teleportTo(level, at[0], at[1], at[2], Set.of(), yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.goto", "sanctum " + where, (int) Math.floor(at[0]),
                (int) Math.floor(at[1]), (int) Math.floor(at[2])), false);
        return 1;
    }

    private static Vec3 vec(double[] p) {
        return new Vec3(p[0], p[1], p[2]);
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = aetheria(source);
        SanctumData data = SanctumData.get(level);
        LockRule locks = data.locks();
        SanctumLayout l = Sanctums.layout(level);
        String text = String.format(Locale.ROOT, "halls %s, causeway %.0f blocks (slope %.2f), locks west %s east %s, stair %s, "
                        + "admitted %d; gate %s; throne %s; rescues %s", l.side() < 0 ? "north" : "south", l.causewayLength(),
                l.causewaySlope(), locks.west(), locks.east(), locks.stair(), data.admitted().size(), SanctumGate.counters(),
                SanctumThrone.counters(), FallRescue.counters());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = aetheria(source);
        SanctumData.get(level).clear();
        long ms = build(level);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.sanctum.reset", ms), false);
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> context, Wing wing) {
        CommandSourceStack source = context.getSource();
        SanctumLocks.light(aetheria(source), wing);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.sanctum.lock", wing.name().toLowerCase(Locale.ROOT)), false);
        return 1;
    }
}
