package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A long teleport into fresh Drift terrain on a wide view, then straight out of the game. The server must stop cleanly: a
 * stop with chunk generation still queued can hang in vanilla 1.21.1 (A2 Report, Concern 1), and the denser belts made that
 * stop more likely to be met. The player is sent several thousand blocks away, to the thick of a belt where the terrain is
 * dearest to generate, at render distance 12, held there, and the scenario ends: the harness quits at once. Three things vary:
 *
 * <ul>
 *   <li>how long the player stays: a fixed number of ticks (the quit meets generation still going), or until the client has
 *       drawn the whole view (the quit meets a settled view, which is what stalled the structure scenario and what ordinary
 *       play looks like: {@code quit-teleport-settled} is the normal-play variant);</li>
 *   <li>how many far teleports come one after another (with more than one the player leaves each area before it has finished
 *       generating);</li>
 *   <li>whether the view distance is shrunk just before the quit, as the structure scenario did.</li>
 * </ul>
 *
 * Where the player is sent can be a vanilla dimension instead ({@link Place}): the same quit in the Nether or a normal Overworld says
 * whether a stall belongs to Aetheria's terrain or to the game's own stop.
 *
 * A stop that hangs fails the run: the harness's watchdog rewrites the result as a FAIL before it halts the game (ExitStall),
 * and {@code scripts/autotest.sh} prints STALL and exits 1 (A2 quality review, Important 1). Run a variant a few times, through
 * the client gate: {@code bash scripts/client-gate.sh run <who> client scripts/autotest.sh quit-teleport-settled}.
 */
public final class QuitAfterTeleportScenario implements Scenario {
    /** Where the player is sent: the thick of a belt in the Drift, or a control in a vanilla dimension. */
    public enum Place {
        DRIFT, NETHER, OVERWORLD
    }

    /** Where each hop's search for a belt starts, in blocks: far from the Breach and from each other, so each is fresh terrain. */
    private static final int[][] STARTS = {{6000, 6000}, {-9000, 6000}, {6000, -9000}};
    private static final int WIDE_VIEW = 12;
    /** How long the player stays at a hop before the next, in ticks: well short of the time a ring takes to generate. */
    private static final int BETWEEN_HOPS = 10;

    private final int holdTicks;
    private final int hops;
    private final boolean waitForView;
    private final int shrinkTo;
    private final Place place;

    /**
     * @param holdTicks how long the player stays at the last destination before the scenario ends (20 ticks are a second);
     *     ignored when {@code waitForView}
     * @param hops how many far teleports to make, one after another ({@link #STARTS} holds three)
     * @param waitForView true to stay until the client has drawn its whole view instead of a fixed time
     * @param shrinkTo the view distance to set just before the end, or 0 to leave it
     */
    public QuitAfterTeleportScenario(int holdTicks, int hops, boolean waitForView, int shrinkTo) {
        this(holdTicks, hops, waitForView, shrinkTo, Place.DRIFT);
    }

    /** As above, sent to {@code place}. */
    public QuitAfterTeleportScenario(int holdTicks, int hops, boolean waitForView, int shrinkTo, Place place) {
        if (holdTicks < 1 || hops < 1 || hops > STARTS.length || shrinkTo < 0) {
            throw new IllegalArgumentException("hold at least a tick and make 1 to " + STARTS.length + " hops: " + holdTicks + ", " + hops);
        }
        this.holdTicks = holdTicks;
        this.hops = hops;
        this.waitForView = waitForView;
        this.shrinkTo = shrinkTo;
        this.place = place;
    }

    @Override
    public int timeBudgetSeconds() {
        return 240;
    }

    /** The default terrain (with structures) for the Overworld control, the usual flat world otherwise. */
    @Override
    public boolean normalWorld() {
        return place == Place.OVERWORLD;
    }

    private ResourceKey<Level> level() {
        return switch (place) {
            case DRIFT -> AetheriaWorld.LEVEL;
            case NETHER -> Level.NETHER;
            case OVERWORLD -> Level.OVERWORLD;
        };
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos[] target = new BlockPos[1];
        steps.command("gamerule sendCommandFeedback false")
                .command("gamemode spectator")
                .command("execute in " + level().location() + " run tp @s 0 " + (place == Place.DRIFT ? 400 : 100) + " 0")
                .waitUntil("in the dimension", 600, () -> mc.level != null && mc.level.dimension() == level())
                .run("a wide view", () -> mc.options.renderDistance().set(WIDE_VIEW))
                .waitTicks(60);
        for (int hop = 0; hop < hops; hop++) {
            int[] start = STARTS[hop];
            boolean last = hop + 1 == hops;
            steps.run("pick a destination far away", () -> target[0] = place == Place.DRIFT
                            ? ServerQuery.ask(p -> beltCore(AetheriaSpots.terrain(p.serverLevel()), start[0], start[1]))
                            : new BlockPos(start[0], 100, start[1]))
                    .log("destination", () -> (place == Place.DRIFT ? "belt core at " : "far point at ") + target[0].toShortString() + ", "
                            + (int) Math.hypot(target[0].getX(), target[0].getZ()) + " blocks from the origin")
                    .run("teleport there", () -> mc.player.connection.sendCommand("tp @s " + target[0].getX() + " " + target[0].getY() + " " + target[0].getZ()));
            if (last && waitForView) {
                steps.waitTicks(20).waitUntil("the view is drawn", 2400, CryptKit.settled(mc, 2000));
            } else {
                steps.waitTicks(last ? holdTicks : BETWEEN_HOPS);
            }
        }
        if (shrinkTo > 0) {
            steps.run("a smaller view", () -> mc.options.renderDistance().set(shrinkTo));
        }
        steps.log("the chunks as the game quits", () -> chunksLoaded() + ", view distance " + (shrinkTo > 0 ? shrinkTo : WIDE_VIEW));
    }

    /** What the server says it holds, or that it did not answer: a busy server must not fail the scenario this asks about. */
    private static String chunksLoaded() {
        try {
            int loaded = ServerQuery.<Integer>ask(p -> p.serverLevel().getChunkSource().getLoadedChunksCount());
            return String.format(Locale.ROOT, "%d loaded", loaded);
        } catch (Steps.Failure e) {
            return "the server did not answer in 5 s";
        }
    }

    /** The first point from (x0, z0) on, searching in a square, where the belt strength is 0.7 or more. */
    static BlockPos beltCore(AetheriaTerrain terrain, int x0, int z0) {
        for (int dz = 0; dz < 3000; dz += 48) {
            for (int dx = 0; dx < 3000; dx += 48) {
                if (terrain.drift.belt(x0 + dx, z0 + dz) >= 0.7) {
                    return new BlockPos(x0 + dx, 232, z0 + dz);
                }
            }
        }
        return new BlockPos(x0, 232, z0);
    }
}
