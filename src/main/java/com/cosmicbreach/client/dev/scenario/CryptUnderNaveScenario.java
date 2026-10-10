package com.cosmicbreach.client.dev.scenario;

import static com.cosmicbreach.client.dev.scenario.CryptKit.player;
import static com.cosmicbreach.client.dev.scenario.CryptKit.server;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.guardian.unsung.UnsungCommands;
import com.cosmicbreach.structure.StructureCommands;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.structure.crypt.CryptCommands;
import com.cosmicbreach.structure.crypt.CryptLayout;
import com.cosmicbreach.structure.crypt.CryptNaveLink;
import com.cosmicbreach.structure.crypt.CryptPiece;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Hollow Crypt under the Silent Nave (they may share a pillar): built the way worldgen builds them, the crypt first
 * and the nave over it, with the nave leaving the crypt whole. A way is searched over the real blocks from the nave's
 * landing, outside its door, to the top of the crypt's entrance stair, and then walked with real key input, and on down
 * the stair into the crypt. The crypt's Conductor is checked to still be there, so the nave's keel has not buried it.
 */
public final class CryptUnderNaveScenario implements Scenario {
    private static final long SEED = 12L;

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    private static final class State {
        CryptPiece crypt;
        NaveLayout nave;
        CryptNaveLink link;
        final List<String> results = new ArrayList<>();
        List<Vec3> route = new ArrayList<>();
        int waypoint;
        int stuck;
        Vec3 last;
        long walkStart;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0 400 0")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .command("cosmicbreach debug goto deep")
                .waitUntil("the Deep is drawn", 1600, CryptKit.settled(mc, 1200))
                .run("build a crypt, then the nave over it", () -> {
                    List<String> out = server(srv -> {
                        ServerLevel level = srv.getLevel(AetheriaWorld.LEVEL);
                        s.nave = UnsungCommands.debugLayout(level, player(srv));
                        int[] door = s.nave.world(NaveLayout.DOOR_WALL - 0.5, 0.0);
                        BlockPos origin = new BlockPos(door[0] - CryptLayout.SIZE / 2, s.nave.floorY() - 9, door[1] - CryptLayout.SIZE / 2);
                        s.crypt = new CryptPiece(origin, SEED);
                        StructureCommands.build(level, CryptCommands.CRYPT, s.crypt);
                        s.link = CryptNaveLink.of(s.nave, origin, s.crypt.plan());
                        UnsungCommands.build(level, s.nave, List.of(s.link));
                        List<String> lines = new ArrayList<>();
                        lines.add("nave floor " + s.nave.floorY() + " facing " + s.nave.facing() + ", crypt origin " + origin.toShortString()
                                + ", entrance cell " + s.crypt.plan().entranceX + "," + s.crypt.plan().entranceZ
                                + ", lane " + s.link.laneColumns().size() / 3 + " steps toward " + s.link.direction());
                        BlockPos top = s.link.top();
                        BlockState step = level.getBlockState(top.below());
                        lines.add("stair's top step: " + step.getBlock().getDescriptionId());
                        for (int dy = 0; dy < CryptNaveLink.HEAD; dy++) {
                            if (!level.getBlockState(top.above(dy)).isAir()) {
                                throw new Steps.Failure("the stair's top is not clear at +" + dy + ": " + level.getBlockState(top.above(dy)));
                            }
                        }
                        if (!(level.getBlockEntity(s.crypt.conductor()) instanceof ConductorBlockEntity)) {
                            throw new Steps.Failure("the crypt's Conductor is gone from " + s.crypt.conductor() + ": the nave buried the crypt");
                        }
                        lines.add("the crypt's Conductor stands at " + s.crypt.conductor().toShortString());
                        return lines;
                    });
                    s.results.addAll(out);
                })
                .run("find a way over the real blocks", () -> {
                    int[] landing = s.nave.world(NaveLayout.DOOR_WALL + 1.5, 0.0);
                    BlockPos start = new BlockPos(landing[0], s.nave.floorY(), landing[1]);
                    BlockPos goal = s.link.top();
                    List<Vec3> path = server(srv -> walkPath(srv.getLevel(AetheriaWorld.LEVEL), start, goal));
                    if (path == null) {
                        throw new Steps.Failure("no way on foot from the nave's landing " + start.toShortString() + " to the crypt's stair " + goal.toShortString());
                    }
                    s.route = new ArrayList<>(path);
                    List<Vec3> down = CryptScenario.route(s.crypt);
                    s.route.addAll(down.subList(1, 3));
                    s.waypoint = 1;
                    s.results.add("a way of " + path.size() + " blocks from the landing to the stair's top");
                })
                .run("stand at the nave's landing", () -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                    CryptKit.tp(mc, s.route.get(0), s.route.get(1).add(0, 1.2, 0));
                })
                .waitTicks(20)
                .waitUntil("the view is drawn", 600, CryptKit.settled(mc, 400))
                .screenshot("crypt_under_nave_landing")
                .run("remember", () -> s.walkStart = mc.level.getGameTime())
                .waitUntil("walked from the landing to the bottom of the crypt's stair", 4000, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitUntil("the view is drawn", 600, CryptKit.settled(mc, 400))
                .screenshot("crypt_under_nave_stair_bottom")
                .run("result", () -> {
                    Vec3 p = mc.player.position();
                    if (p.y > s.crypt.floorY(0) + 3) {
                        throw new Steps.Failure("not down in the crypt: y " + p.y + ", the crypt's first floor is at " + s.crypt.floorY(0));
                    }
                    s.results.add("walked from the nave's landing into the crypt in " + (mc.level.getGameTime() - s.walkStart) + " ticks, "
                            + s.stuck + " ticks nudged, ending at " + p);
                });
        steps.log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    /** Steers along the route one tick; true at the end. */
    private static boolean follow(Minecraft mc, State s) {
        if (s.waypoint >= s.route.size()) {
            return true;
        }
        Vec3 w = s.route.get(s.waypoint);
        Vec3 p = mc.player.position();
        double d = Math.hypot(w.x - p.x, w.z - p.z);
        if (d < 0.45 && Math.abs(w.y - p.y) < 1.6) {
            s.waypoint++;
            return s.waypoint >= s.route.size();
        }
        CryptKit.face(mc, w.add(0, 1.2, 0), 15f);
        CryptKit.set(mc.options.keyUp, true);
        CryptKit.set(mc.options.keySprint, false);
        mc.player.setSprinting(false);
        if (s.last != null && s.last.distanceTo(p) < 0.02) {
            s.stuck++;
            CryptKit.set(mc.options.keyJump, s.stuck % 10 == 0);
            if (s.stuck > 200) {
                throw new Steps.Failure("stuck at " + p + " on the way to waypoint " + s.waypoint + " " + w);
            }
        } else {
            CryptKit.set(mc.options.keyJump, false);
        }
        s.last = p;
        return false;
    }

    // ------------------------------------------------------------------ the search over real blocks

    private static boolean passable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    private static boolean stand(ServerLevel level, BlockPos p) {
        return passable(level, p) && passable(level, p.above()) && !passable(level, p.below());
    }

    /** The cells a walker stands in from {@code start} to {@code goal} (4-way steps, up one or down three), or null. */
    private static List<Vec3> walkPath(ServerLevel level, BlockPos start, BlockPos goal) {
        if (!stand(level, start)) {
            throw new Steps.Failure("nothing to stand on at the nave's landing " + start.toShortString());
        }
        Map<BlockPos, BlockPos> from = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        from.put(start, start);
        queue.add(start);
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (p.getX() == goal.getX() && p.getZ() == goal.getZ() && Math.abs(p.getY() - goal.getY()) <= 1) {
                List<Vec3> out = new ArrayList<>();
                for (BlockPos q = p; !q.equals(start); q = from.get(q)) {
                    out.add(new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5));
                }
                out.add(new Vec3(start.getX() + 0.5, start.getY(), start.getZ() + 0.5));
                Collections.reverse(out);
                return out;
            }
            for (int[] d : dirs) {
                int nx = p.getX() + d[0];
                int nz = p.getZ() + d[1];
                if (Math.abs(nx - start.getX()) > 110 || Math.abs(nz - start.getZ()) > 110) {
                    continue;
                }
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos n = new BlockPos(nx, p.getY() + dy, nz);
                    if (dy == 1 && !passable(level, p.above(2))) {
                        continue;
                    }
                    if (stand(level, n)) {
                        if (from.putIfAbsent(n, p) == null) {
                            queue.add(n);
                        }
                        break;
                    }
                }
            }
        }
        return null;
    }
}
