package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.leviathan.LeviathanClient;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.guardian.GuardianPart;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanOrbit;
import com.cosmicbreach.guardian.leviathan.LeviathanPaths;
import com.cosmicbreach.guardian.leviathan.LeviathanRegistry;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.Moorage;
import com.cosmicbreach.guardian.leviathan.Polyline;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ShedScale;
import com.cosmicbreach.guardian.leviathan.SongPull;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A whole Thalassine Leviathan fight played by script through the real inputs (movement, jump, attack, dash and parry
 * keys, the look direction), timed: part {@code leviathan-fight}. The player is the scenario's level 24 in the Driftweave
 * with the Comet Maul. It plays like a sharp player: it waits at the inner edge of the platform the orbit passes closest
 * and swings whenever a part of the body is in reach, steps out of a Breach Dive's wake and hits the body going past,
 * dashes out of the Song of Pulling and keeps clear of the mouth, parries a Tail Flick on its glint and hits the drooping
 * tail, knocks shed scales out of the air, hits the head in a Break, and in each Moorage walks a grown bridge onto the
 * coil, holds on by a song gland and hits it, and walks back before the coil tears free (or once the next health line
 * holds). A fall into the bowl is climbed back by an updraft. When low it drinks (instant health by command, counted).
 * Logs the fight's length, its parts and the uptime.
 */
final class LeviathanFightBot {
    /** The longest fight it plays, in ticks (10 minutes). */
    private static final int TIMEOUT = 12000;
    private static final int SWING_EVERY = 9;
    /** Where the Maul's swings reach: 3 blocks out from the chest (the first swing's arc), in a band 3 tall. */
    private static final double REACH = 3.0;
    private static final double BAND = 1.5;
    private static final double CHEST = 0.55 * 1.8;
    /** How far in from a platform's rim it stands. */
    private static final double EDGE = 1.0;
    /** How far along the inner edge it moves to follow the body (either side of straight in). */
    private static final double TRACK = Math.toRadians(35.0);

    private final LeviathanScenario scenario;

    // what the bot remembers between ticks
    private int tick;
    private int nextSwing;
    private final List<KeyMapping> release = new ArrayList<>();
    private int home = -1;
    private final Deque<Vec3> route = new ArrayDeque<>();
    private int routeTo = -1;
    private long diveKey = Long.MIN_VALUE;
    private @Nullable Vec3 dodge;
    private long parryKey = Long.MIN_VALUE;
    private int pressedDashAt = -100;
    private int bridge = -1;
    private boolean leaving;
    private boolean headingOut;
    private boolean climbing;
    private int healedAt = -100;
    // what it measures
    private long fightStart = -1;
    private long killAt = -1;
    private final long[] moorIn = {-1, -1};
    private final long[] moorOut = {-1, -1};
    private int moorSeen;
    private int fightTicks;
    private int orbitTicks;
    private int swingTicks;
    private int orbitSwingTicks;
    private int heals;
    private int parries;
    private int dodges;
    private int dashes;
    private int falls;
    private float orbitDamage;
    private float moorDamage;
    private float lastHealth = -1f;
    private @Nullable State lastState;
    private int[] counts = new int[9];
    private int breaks;
    private double maxGauge;
    private final List<String> homes = new ArrayList<>();

    LeviathanFightBot(LeviathanScenario scenario) {
        this.scenario = scenario;
    }

    /** One part of the body as the server has it: 0 the head, 1 to 4 the segments, 5 the tail, 6 to 9 the glands. */
    private record Part(int role, AABB box) {
        Vec3 centre() {
            return box.getCenter();
        }
    }

    /** What the server says this tick. */
    private record Snap(State state, ThalassineLeviathan.Moor moor, LeviathanTactics.Attack action, long actionTick, boolean broken,
                        boolean drooping, boolean glands, float health, float max, long now, SongPull.Cone cone, @Nullable Polyline dive,
                        List<Part> parts, List<AABB> scales, List<Vec3> glandPoints, double wave, long moorTick, float myHealth, float myMax,
                        int[] counts, int breaks, double gauge) {
        @Nullable Part part(int role) {
            for (Part p : parts) {
                if (p.role() == role) {
                    return p;
                }
            }
            return null;
        }
    }

    void steps(Steps steps, Minecraft mc) {
        scenario.equip(steps, mc);
        steps.command("gamerule naturalRegeneration true")
                .run("onto the ledge outside the entrance", () -> {
                    Vec3 ledge = layout().ledgeTop();
                    Vec3 c = layout().centre();
                    ColossusScenario.tp(mc, ledge.x, ledge.y, ledge.z, c.x, ledge.y + 1.6, c.z);
                })
                .waitTicks(10);
        LeviathanMechanics.walk(steps, mc, "along the walkway to platform 0 (entering wakes it)", () -> layout().platform(0).topCentre(), 2.5, 600);
        steps.waitUntil("it wakes", 100, () -> !LeviathanScenario.is(State.DORMANT))
                .run("forget the damage so far", () -> {
                    scenario.playerDamage.clear();
                    scenario.dealt.clear();
                })
                .waitUntil("the fight (played by script, at most 10 minutes)", TIMEOUT + 600, () -> tick(mc))
                .run("release every key", () -> releaseAll())
                .log("platforms", () -> String.join("; ", homes))
                .log("fight", this::report)
                .run("note it", () -> scenario.summary.add(report()))
                .check("the Leviathan fell", () -> killAt > 0)
                .check("even near-perfect play needs at least about a minute and a half", () -> killAt - fightStart >= 1700);
    }

    private String report() {
        double total = (killAt - fightStart) / 20.0;
        double p1 = span(fightStart, moorIn[0]);
        double m1 = span(moorIn[0], moorOut[0] > 0 ? moorOut[0] : killAt);
        double p2 = span(moorOut[0], moorIn[1]);
        double m2 = span(moorIn[1], moorOut[1] > 0 ? moorOut[1] : killAt);
        double after = span(moorOut[1], killAt);
        return String.format(Locale.ROOT, "scripted fight: %.0f s from the end of the intro to the kill (phase 1 %.0f s, Moorage 1 %.0f s, "
                        + "phase 2 %.0f s, Moorage 2 %.0f s, after %.0f s); swinging on %.0f%% of the fight's ticks (%.0f%% of the orbit's); "
                        + "dealt %.0f in the orbit and %.0f in the Moorages; %d Break(s); dives %d (%d stepped out of), songs %d (%d bite(s), "
                        + "%d dash(es)), flicks %d (%d parry press(es), %d parried), sheds %d, shudders %d, gland hits %d; took %.0f damage, %d heal(s), "
                        + "%d fall(s) into the bowl; the Break gauge peaked at %.0f%%",
                total, p1, m1, p2, m2, after, 100.0 * swingTicks / Math.max(1, fightTicks), 100.0 * orbitSwingTicks / Math.max(1, orbitTicks),
                orbitDamage, moorDamage, breaks, counts[0], dodges, counts[1], counts[2], dashes, counts[3], parries, counts[4], counts[5],
                counts[7], counts[8], taken(), heals, falls, 100.0 * maxGauge);
    }

    private static double span(long from, long to) {
        return from > 0 && to > 0 && to >= from ? (to - from) / 20.0 : 0.0;
    }

    private float taken() {
        float sum = 0f;
        for (float f : scenario.playerDamage) {
            sum += f;
        }
        return sum;
    }

    // ------------------------------------------------------------------ one tick of play

    /** Plays one tick; true once the fight is over (or timed out). */
    private boolean tick(Minecraft mc) {
        tick++;
        for (KeyMapping key : release) {
            KeyMapping.set(key.getKey(), false);
        }
        release.clear();
        Snap s = snap();
        if (s == null) {
            if (killAt < 0) {
                killAt = mc.level.getGameTime();
            }
            releaseAll();
            return true;
        }
        measure(s);
        if (s.state() == State.DYING || tick > TIMEOUT) {
            if (s.state() == State.DYING && killAt < 0) {
                killAt = s.now();
            }
            releaseAll();
            return true;
        }
        heal(mc, s);
        if (tick % 100 == 0) {
            // a trace to read the fight back by (the client log)
            Vec3 me = mc.player.position();
            com.cosmicbreach.CosmicBreach.LOGGER.info(String.format(Locale.ROOT,
                    "[fightbot] t=%d %s/%s %s@%d health %.0f me (%.1f %.1f %.1f) r %.1f on platform %d home %d climbing %s swinging %d",
                    tick, s.state(), s.moor(), s.action(), s.actionTick(), s.health(), me.x, me.y, me.z, radius(me), platformUnder(me), home,
                    climbing, swingTicks));
        }
        if (fallen(mc)) {
            climb(mc);
            return false;
        }
        switch (s.state()) {
            case FIGHT -> fight(mc, s);
            case MOORAGE -> moorage(mc, s);
            default -> position(mc, s);
        }
        return false;
    }

    private void measure(Snap s) {
        if (fightStart < 0 && s.state() == State.FIGHT) {
            fightStart = s.now();
        }
        if (lastHealth >= 0f && s.health() < lastHealth) {
            float d = lastHealth - s.health();
            if (s.state() == State.MOORAGE || lastState == State.MOORAGE) {
                moorDamage += d;
            } else {
                orbitDamage += d;
            }
        }
        lastHealth = s.health();
        if (s.state() == State.MOORAGE && lastState != State.MOORAGE && moorSeen < 2) {
            moorIn[moorSeen++] = s.now();
        }
        if (s.state() == State.FIGHT && lastState == State.MOORAGE && moorSeen > 0) {
            moorOut[moorSeen - 1] = s.now();
            bridge = -1;
            leaving = false;
            headingOut = false;
            home = -1;
        }
        lastState = s.state();
        counts = s.counts();
        breaks = s.breaks();
        maxGauge = Math.max(maxGauge, s.gauge());
        if (fightStart >= 0) {
            fightTicks++;
            if (s.state() == State.FIGHT) {
                orbitTicks++;
            }
        }
    }

    // ------------------------------------------------------------------ the orbit

    private void fight(Minecraft mc, Snap s) {
        if (dive(mc, s) || song(mc, s) || flick(mc, s) || scales(mc, s)) {
            return;
        }
        if (strike(mc, s)) {
            return;
        }
        position(mc, s);
    }

    /** Where it waits: the inner edge of its home platform, shifted along the edge toward the nearest part of the body. */
    private void position(Minecraft mc, Snap s) {
        Vec3 me = mc.player.position();
        if (home < 0) {
            home = chooseHome(me, s.wave());
        }
        Part head = s.part(0);
        Vec3 look = head != null ? head.centre() : layout().centre();
        RiftLayout.Platform p = layout().platform(home);
        if (platformUnder(me) != home) {
            travel(mc, home, look);
            return;
        }
        route.clear();
        routeTo = -1;
        Part near = nearestPart(me, s, false);
        Vec3 toward = s.broken() && head != null ? head.centre() : near != null && flat(near.centre(), me) < 14.0 ? near.centre() : null;
        Vec3 spot = toward != null ? edgePoint(p, toward) : standPoint(p, EDGE);
        walkTo(mc, spot, 0.35, look);
    }

    /** Swings at the best part of the body in reach (the head in a Break, else the part it takes most from); true if it did. */
    private boolean strike(Minecraft mc, Snap s) {
        Vec3 chest = mc.player.position().add(0, CHEST, 0);
        Part best = null;
        double bestTaken = -1;
        for (Part part : s.parts()) {
            if (part.role() >= ThalassineLeviathan.ROLE_GLAND && !s.glands()) {
                continue;
            }
            if (!inReach(chest, part.box()) || !inSight(mc, part.box())) {
                continue;
            }
            double taken = taken(part.role());
            if (taken > bestTaken) {
                bestTaken = taken;
                best = part;
            }
        }
        if (best == null) {
            return false;
        }
        double gap = rimGap(mc.player.position());
        if (gap >= 0 && gap < 0.6 && s.state() == State.FIGHT) {
            // the Maul's lunges carry it toward the rim: a step back in before the next swing
            int k = platformUnder(mc.player.position());
            RiftLayout.Platform p = layout().platform(k);
            walkTo(mc, new Vec3(p.x(), p.top() + 1.0, p.z()), 0.3, nearestPoint(chest, best.box()));
            return true;
        }
        release(mc.options.keyUp);
        ColossusScenario.lookAt(mc, nearestPoint(chest, best.box()));
        swing(mc, s);
        return true;
    }

    private static double taken(int role) {
        return switch (role) {
            case 0 -> LeviathanMoves.HEAD_TAKEN;
            case 1, 2, 3, 4 -> LeviathanMoves.BODY_TAKEN;
            case 5 -> LeviathanMoves.TAIL_TAKEN;
            default -> LeviathanMoves.GLAND_TAKEN;
        };
    }

    /** A Breach Dive at me: out of its wake (square to the path, staying on the platform); then the body going past is free hits. */
    private boolean dive(Minecraft mc, Snap s) {
        if (s.action() != LeviathanTactics.Attack.DIVE || s.dive() == null) {
            dodge = null;
            return false;
        }
        long key = s.now() - s.actionTick();
        if (key != diveKey) {
            diveKey = key;
            dodge = dodgePoint(mc.player.position(), s.dive());
            if (dodge != null) {
                dodges++;
            }
        }
        if (dodge == null) {
            return false;
        }
        Part head = s.part(0);
        Vec3 look = head != null ? head.centre() : layout().centre();
        if (flat(mc.player.position(), dodge) > 0.4) {
            // a part already in reach while stepping out is a free hit
            if (!strike(mc, s)) {
                walkTo(mc, dodge, 0.35, look);
            }
            return true;
        }
        if (!strike(mc, s)) {
            idle(mc, look);
        }
        return true;
    }

    /**
     * The nearest spot on this platform at least 3.6 blocks from the dive's path (its middle; so its box is out of the
     * head's 2.7 reach with room to spare), or null if the path already misses.
     */
    private @Nullable Vec3 dodgePoint(Vec3 feet, Polyline path) {
        double clear = LeviathanMoves.DIVE_REACH + 0.9;
        if (pathDistance(path, feet.add(0, 0.9, 0)) >= clear) {
            return null;
        }
        int k = platformUnder(feet);
        if (k < 0) {
            k = nearestPlatform(feet);
        }
        RiftLayout.Platform p = layout().platform(k);
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (double dx = -p.radius(); dx <= p.radius(); dx += 0.5) {
            for (double dz = -p.radius(); dz <= p.radius(); dz += 0.5) {
                if (dx * dx + dz * dz > (p.radius() - 1.3) * (p.radius() - 1.3)) {
                    continue;
                }
                Vec3 at = new Vec3(p.x() + dx, p.top() + 1.0, p.z() + dz);
                if (nearBoulder(k, at) || pathDistance(path, at.add(0, 0.9, 0)) < clear) {
                    continue;
                }
                double d = flat(at, feet);
                if (d < bestD) {
                    bestD = d;
                    best = at;
                }
            }
        }
        return best;
    }

    private static double pathDistance(Polyline path, Vec3 p) {
        double best = Double.MAX_VALUE;
        for (double d = 0; d <= path.length(); d += 0.5) {
            best = Math.min(best, path.at(d).distanceTo(p));
        }
        return best;
    }

    /**
     * The Song of Pulling while I'm in its cone: into cover behind a boulder on my platform (rock between the mouth and me
     * blocks it); with no cover, dash out of the pull when a backstep keeps me on the platform, and keep clear of the mouth.
     */
    private boolean song(Minecraft mc, Snap s) {
        if (s.action() != LeviathanTactics.Attack.SONG || s.actionTick() >= LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL) {
            return false;
        }
        long t = s.actionTick();
        Vec3 me = mc.player.position();
        Vec3 middle = mc.player.getBoundingBox().getCenter();
        Vec3 mouth = s.cone().mouth();
        if (!SongPull.inCone(s.cone(), middle) && mouth.distanceTo(middle) > LeviathanMoves.BITE_REACH + 3.0) {
            return false;
        }
        boolean pulling = t >= LeviathanMoves.SONG_TELL - 2;
        long now = mc.level.getGameTime();
        boolean exempt = LeviathanClient.exempt(now + 1);
        boolean close = mouth.distanceTo(middle) < LeviathanMoves.BITE_REACH + 2.0;
        boolean hidden = rockBetween(mc, mouth, middle) && rockBetween(mc, mouth, mc.player.getEyePosition());
        Vec3 spot = coverSpot(me, mouth);
        if (spot != null && !(pulling && !hidden && !exempt) && !close) {
            if (flat(me, spot) > 0.35) {
                walkTo(mc, spot, 0.3, mouth);
            } else if (!strike(mc, s)) {
                idle(mc, mouth);
            }
            return true;
        }
        if (pulling && (!exempt || close) && !hidden) {
            ColossusScenario.lookAt(mc, new Vec3(mouth.x, mc.player.getEyeY(), mouth.z));
            boolean safe = backIsSafe(mc, 4.0);
            if (!exempt && safe && t >= LeviathanMoves.SONG_TELL - 1 && tick - pressedDashAt > 3) {
                pressedDashAt = tick;
                press(ModKeyMappings.DASH);
                dashes++;
            } else if (backIsSafe(mc, 1.2)) {
                hold(mc.options.keyDown);
            }
            return true;
        }
        if (!strike(mc, s)) {
            idle(mc, mouth);
        }
        return true;
    }

    /** True if a block stands between {@code from} and {@code to}. */
    private static boolean rockBetween(Minecraft mc, Vec3 from, Vec3 to) {
        return mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player)).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    /** The spot behind a boulder on my platform, as seen from the mouth (on the platform), nearest me; or null. */
    private static @Nullable Vec3 coverSpot(Vec3 me, Vec3 mouth) {
        int k = platformUnder(me);
        if (k < 0) {
            return null;
        }
        RiftLayout.Platform p = layout().platform(k);
        Vec3 best = null;
        for (Vec3 b : layout().boulders(k)) {
            Vec3 away = new Vec3(b.x - mouth.x, 0, b.z - mouth.z).normalize();
            Vec3 at = new Vec3(b.x, p.top() + 1.0, b.z).add(away.scale(RiftLayout.BOULDER + 0.9));
            if (Math.hypot(at.x - p.x(), at.z - p.z()) > p.radius() - 1.2 || nearBoulder(k, at)) {
                continue;
            }
            if (best == null || flat(at, me) < flat(best, me)) {
                best = at;
            }
        }
        return best;
    }

    /** A waypoint beside the boulder when the straight way to {@code spot} runs into it, else {@code spot}. */
    private static Vec3 aroundBoulder(Vec3 me, Vec3 spot) {
        int k = platformUnder(me);
        if (k < 0) {
            return spot;
        }
        for (Vec3 b : obstacles(k)) {
            double radius = b.y;
            Vec3 d = new Vec3(spot.x - me.x, 0, spot.z - me.z);
            double len = d.length();
            if (len < 1e-3) {
                continue;
            }
            Vec3 u = d.scale(1.0 / len);
            double along = Mth.clamp((b.x - me.x) * u.x + (b.z - me.z) * u.z, 0.0, len);
            double off = Math.hypot(me.x + u.x * along - b.x, me.z + u.z * along - b.z);
            if (off < radius + 0.6 && along > 0.2 && along < len - 0.2) {
                Vec3 side = new Vec3(-u.z, 0, u.x);
                Vec3 left = new Vec3(b.x, spot.y, b.z).add(side.scale(radius + 1.1));
                Vec3 right = new Vec3(b.x, spot.y, b.z).subtract(side.scale(radius + 1.1));
                RiftLayout.Platform p = layout().platform(k);
                boolean leftOn = Math.hypot(left.x - p.x(), left.z - p.z()) < p.radius() - 1.0;
                boolean rightOn = Math.hypot(right.x - p.x(), right.z - p.z()) < p.radius() - 1.0;
                if (leftOn && (!rightOn || flat(left, me) < flat(right, me))) {
                    return left;
                }
                return rightOn ? right : spot;
            }
        }
        return spot;
    }

    /** A Tail Flick at me: hold my swings through its tell, parry on the gold glint; the drooping tail is free hits after. */
    private boolean flick(Minecraft mc, Snap s) {
        if (s.action() != LeviathanTactics.Attack.FLICK || s.actionTick() >= LeviathanMoves.FLICK_TELL) {
            return false;
        }
        Part tail = s.part(ThalassineLeviathan.FOLLOWERS);
        if (tail == null || !near(mc.player.getBoundingBox(), tail.centre(), LeviathanMoves.FLICK_REACH + 1.0)) {
            return false;
        }
        release(mc.options.keyUp);
        ColossusScenario.lookAt(mc, tail.centre());
        long key = s.now() - s.actionTick();
        if (s.actionTick() >= LeviathanMoves.FLICK_GLINT + 1 && parryKey != key) {
            parryKey = key;
            press(ModKeyMappings.PARRY);
            parries++;
        }
        return true;
    }

    /** A shed scale in reach: knock it out of the air. */
    private boolean scales(Minecraft mc, Snap s) {
        Vec3 chest = mc.player.position().add(0, CHEST, 0);
        AABB best = null;
        double bestD = Double.MAX_VALUE;
        for (AABB scale : s.scales()) {
            double d = scale.getCenter().distanceTo(chest);
            if (inReach(chest, scale) && d < bestD) {
                bestD = d;
                best = scale;
            }
        }
        if (best == null) {
            return false;
        }
        release(mc.options.keyUp);
        ColossusScenario.lookAt(mc, best.getCenter());
        swing(mc, s);
        return true;
    }

    // ------------------------------------------------------------------ the Moorage

    private void moorage(Minecraft mc, Snap s) {
        switch (s.moor()) {
            case SWIM_IN -> {
                // it swims on round the orbit to coil beside my platform: hit it going by, and wait at the edge
                if (!strike(mc, s)) {
                    position(mc, s);
                }
            }
            case COILED -> coiled(mc, s);
            default -> {
                // tearing free: off the coil and its bridge if still there, then back to the orbit's edge
                leaving = false;
                headingOut = false;
                Vec3 me = mc.player.position();
                if (bridge >= 0 && platformUnder(me) != bridge && radius(me) < RiftLayout.CLEAR_RADIUS) {
                    walkTo(mc, standPoint(layout().platform(bridge), EDGE + 0.5), 0.4, layout().centre());
                    return;
                }
                position(mc, s);
            }
        }
    }

    private void coiled(Minecraft mc, Snap s) {
        RiftLayout lay = layout();
        Vec3 c = lay.centre();
        Vec3 me = mc.player.position();
        int t = (int) s.moorTick();
        if (bridge < 0) {
            Part mid = s.part(2);
            bridge = bridgePlatform(me, mid != null ? LeviathanOrbit.angleOf(c, mid.centre()) : 0.0);
        }
        RiftLayout.Platform bp = lay.platform(bridge);
        double coilTop = Math.floor(c.y + LeviathanMoves.COIL_TOP);
        Vec3 foot = new Vec3(c.x + LeviathanMoves.COIL_RADIUS * Math.cos(bp.angle()), coilTop, c.z + LeviathanMoves.COIL_RADIUS * Math.sin(bp.angle()));
        Vec3 edge = standPoint(bp, EDGE + 0.5);
        boolean onCoil = onCoil(mc);
        boolean onBridge = !onCoil && onMoorageBridge(me, bp);
        boolean onPlatform = platformUnder(me) == bridge;
        Part gland = nearestGland(s, me);
        boolean holding = false;
        for (Vec3 g : s.glandPoints()) {
            holding |= g.distanceTo(me) <= LeviathanMoves.HOLD_ON - 0.4;
        }
        boolean rippleSoon = t % Moorage.SHUDDER_EVERY >= Moorage.SHUDDER_EVERY - Moorage.SHUDDER_TELL - 6 && t + 30 < Moorage.COILED;
        boolean closed = !s.glands();
        int back = (int) ((flat(me, foot) + flat(foot, edge)) / 0.2) + 30;
        if (!leaving && (onCoil || onBridge) && (t + back >= Moorage.COILED || closed)) {
            leaving = true;
        }
        if (leaving) {
            if (onCoil && rippleSoon && holding && !headingOut) {
                idle(mc, foot); // hold on by the gland through the shudder, then go
                return;
            }
            if (!headingOut && onCoil && flat(me, foot) > 1.2) {
                walkTo(mc, coilStep(me, foot), 0.3, foot);
                return;
            }
            headingOut = true; // at the bridge's foot: out along it, not back
            if (onCoil || onBridge) {
                walkTo(mc, edge, 0.4, c);
            } else {
                stand(mc, bridge, EDGE + 0.5, c);
            }
            return;
        }
        if (!onCoil && !onBridge) {
            if (!onPlatform) {
                stand(mc, bridge, EDGE + 0.5, foot);
                return;
            }
            if (t < Moorage.BRIDGE_GROW + 2 || t + 60 >= Moorage.COILED || closed) {
                walkTo(mc, edge, 0.4, foot);
                return;
            }
            walkTo(mc, radius(me) > radius(edge) + 0.5 ? edge : foot, 0.3, foot);
            return;
        }
        if (onBridge) {
            walkTo(mc, foot, 0.3, foot);
            return;
        }
        if (gland == null) {
            idle(mc, c);
            return;
        }
        if (flat(me, gland.centre()) > 1.2 && !(rippleSoon && holding)) {
            walkTo(mc, coilStep(me, gland.centre()), 0.3, gland.centre());
            return;
        }
        if (!strike(mc, s)) {
            idle(mc, gland.centre());
        }
    }

    /** True if {@code me} is on the Moorage bridge out from platform {@code p} (between its rim and the coil). */
    private static boolean onMoorageBridge(Vec3 me, RiftLayout.Platform p) {
        Vec3 c = layout().centre();
        double r = radius(me);
        double along = (me.x - c.x) * Math.cos(p.angle()) + (me.z - c.z) * Math.sin(p.angle());
        double across = Math.abs(-(me.x - c.x) * Math.sin(p.angle()) + (me.z - c.z) * Math.cos(p.angle()));
        return along > 0 && across < 2.2 && r > LeviathanMoves.COIL_RADIUS + 1.0 && r < RiftLayout.PLATFORM_RING - p.radius() + 1.5;
    }

    /** A step toward {@code target} along the coil's ring (so a long way round doesn't cut across the ring's inside). */
    private Vec3 coilStep(Vec3 me, Vec3 target) {
        Vec3 c = layout().centre();
        double a = LeviathanOrbit.angleOf(c, me);
        double b = LeviathanOrbit.angleOf(c, target);
        double d = Mth.wrapDegrees(Math.toDegrees(b - a));
        if (Math.abs(d) < 18.0) {
            return target;
        }
        double step = a + Math.toRadians(Math.signum(d) * 15.0);
        return new Vec3(c.x + LeviathanMoves.COIL_RADIUS * Math.cos(step), target.y, c.z + LeviathanMoves.COIL_RADIUS * Math.sin(step));
    }

    /**
     * The platform whose bridge it takes: of the three nearest the coil's middle (the ones that grow bridges), the one whose
     * bridge surely lands on the coil and is quickest to reach.
     */
    private int bridgePlatform(Vec3 me, double coilMid) {
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        List<RiftLayout.Platform> three = layout().nearestPlatforms(coilMid, 3);
        for (RiftLayout.Platform p : three) {
            double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(p.angle() - coilMid)));
            if (off > 40.0 && p != three.get(0)) {
                continue;
            }
            double cost = hops(nearestPlatform(me), p.index()) * 30.0 + off * 0.2;
            if (cost < bestCost) {
                bestCost = cost;
                best = p.index();
            }
        }
        return best;
    }

    private @Nullable Part nearestGland(Snap s, Vec3 me) {
        Part best = null;
        for (Part p : s.parts()) {
            if (p.role() >= ThalassineLeviathan.ROLE_GLAND && (best == null || flat(p.centre(), me) < flat(best.centre(), me))) {
                best = p;
            }
        }
        return best;
    }

    private boolean onCoil(Minecraft mc) {
        Vec3 me = mc.player.position();
        double coilTop = Math.floor(layout().centre().y + LeviathanMoves.COIL_TOP);
        if (mc.level.getBlockState(mc.player.blockPosition().below()).is(LeviathanRegistry.LEVIATHAN_COIL.get())) {
            return true;
        }
        return Math.abs(me.y - coilTop) < 0.9 && Math.abs(radius(me) - LeviathanMoves.COIL_RADIUS) < 1.8;
    }

    // ------------------------------------------------------------------ the bowl

    /** True while it is down in the bowl, or riding an updraft back up (until it stands on a platform again). */
    private boolean fallen(Minecraft mc) {
        double y = mc.player.getY();
        double cy = layout().centre().y;
        if (!climbing && y < cy - 9.0) {
            climbing = true;
            falls++;
            route.clear();
            routeTo = -1;
            bridge = -1;
            leaving = false;
        }
        if (climbing && mc.player.onGround() && y > cy - 6.0) {
            climbing = false;
            home = -1;
        }
        return climbing;
    }

    /** To the nearest updraft, and ride it up (it pushes riders off onto its platform at the top). */
    private void climb(Minecraft mc) {
        Vec3 me = mc.player.position();
        RiftLayout.Updraft best = null;
        double bestD = Double.MAX_VALUE;
        for (RiftLayout.Updraft u : layout().updrafts()) {
            double d = Math.hypot(u.x() + 0.5 - me.x, u.z() + 0.5 - me.z);
            if (d < bestD) {
                bestD = d;
                best = u;
            }
        }
        if (best == null) {
            return;
        }
        Vec3 column = new Vec3(best.x() + 0.5, me.y, best.z() + 0.5);
        RiftLayout.Platform landing = layout().platform(nearestPlatform(column));
        if (me.y >= landing.top() + 1.2) {
            // risen over its landing: step off onto it (the top of the column nudges riders that way too)
            walkTo(mc, landing.topCentre(), 0.8, landing.topCentre());
        } else if (bestD > 0.45) {
            walkTo(mc, column, 0.25, column.add(0, 10, 0));
        } else {
            release(mc.options.keyUp);
            release(mc.options.keySprint);
            ColossusScenario.lookAt(mc, landing.topCentre());
        }
    }

    // ------------------------------------------------------------------ platforms and routes

    private static RiftLayout layout() {
        return LeviathanScenario.layout();
    }

    /** On platform {@code p}'s line in toward the axis, {@code fromEdge} in from its rim (found by probing the rock). */
    private static Vec3 standPoint(RiftLayout.Platform p, double fromEdge) {
        Vec3 c = layout().centre();
        double in = Math.atan2(c.z - p.z(), c.x - p.x());
        double r = rim(p, in) - fromEdge;
        return new Vec3(p.x() + r * Math.cos(in), p.top() + 1.0, p.z() + r * Math.sin(in));
    }

    /** How far from platform {@code p}'s middle its top reaches toward {@code angle} (the first gap in the top blocks). */
    private static double rim(RiftLayout.Platform p, double angle) {
        Minecraft mc = Minecraft.getInstance();
        for (double d = 0.5; d <= p.radius() + 2.0; d += 0.25) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(p.x() + d * Math.cos(angle), p.top(), p.z() + d * Math.sin(angle));
            if (!mc.level.getBlockState(pos).isFaceSturdy(mc.level, pos, net.minecraft.core.Direction.UP)) {
                return d - 0.25;
            }
        }
        return p.radius();
    }

    /** How far {@code me} stands in from the rim of the platform under it, along the line from the platform's middle; or -1. */
    private static double rimGap(Vec3 me) {
        int k = platformUnder(me);
        if (k < 0) {
            return -1;
        }
        RiftLayout.Platform p = layout().platform(k);
        double d = Math.hypot(me.x - p.x(), me.z - p.z());
        return rim(p, Math.atan2(me.z - p.z(), me.x - p.x())) - d;
    }

    /** The point of platform {@code p}'s inner edge nearest {@code toward}, within {@link #TRACK} of straight in. */
    private static Vec3 edgePoint(RiftLayout.Platform p, Vec3 toward) {
        Vec3 c = layout().centre();
        double in = Math.atan2(c.z - p.z(), c.x - p.x());
        double a = Math.atan2(toward.z - p.z(), toward.x - p.x());
        double off = Mth.clamp(Mth.wrapDegrees(Math.toDegrees(a - in)), -Math.toDegrees(TRACK), Math.toDegrees(TRACK));
        double b = in + Math.toRadians(off);
        double r = rim(p, b) - EDGE;
        return new Vec3(p.x() + r * Math.cos(b), p.top() + 1.0, p.z() + r * Math.sin(b));
    }

    /**
     * Its home: the platform whose inner edge the orbit's parts come most within reach of, less the walk there (a hop
     * between platforms costs as much as 15 hundredths of a radian of reach).
     */
    private int chooseHome(Vec3 me, double wave) {
        int from = nearestPlatform(me);
        int best = from;
        double bestScore = -Double.MAX_VALUE;
        StringBuilder line = new StringBuilder("home from platform " + from + ":");
        for (RiftLayout.Platform p : layout().platforms()) {
            int reach = reachScore(p, wave);
            double score = reach - 15.0 * hops(from, p.index());
            line.append(String.format(Locale.ROOT, " %d (r %d, top %+d, reach %d)", p.index(), p.radius(), p.top() - (int) layout().centre().y, reach));
            if (score > bestScore) {
                bestScore = score;
                best = p.index();
            }
        }
        homes.add(line.append(" -> ").append(best).toString());
        return best;
    }

    /** How many hundredths of a radian of the orbit near {@code p} bring the head within reach of its inner edge. */
    private static int reachScore(RiftLayout.Platform p, double wave) {
        Vec3 c = layout().centre();
        double hw = LeviathanMoves.PART_WIDTH[0] / 2.0;
        double hh = LeviathanMoves.PART_HEIGHT[0] / 2.0;
        int n = 0;
        for (double off : new double[] {-TRACK, 0.0, TRACK}) {
            Vec3 toward = new Vec3(p.x() + Math.cos(p.angle() + Math.PI + off), 0, p.z() + Math.sin(p.angle() + Math.PI + off));
            Vec3 chest = edgePoint(p, toward).add(0, CHEST, 0);
            for (double a = p.angle() - 0.6; a <= p.angle() + 0.6; a += 0.01) {
                Vec3 o = LeviathanOrbit.point(c, a, wave);
                if (inReach(chest, new AABB(o.x - hw, o.y - hh, o.z - hw, o.x + hw, o.y + hh, o.z + hw))) {
                    n++;
                }
            }
        }
        return n / 3;
    }

    private static int hops(int from, int to) {
        int d = Math.floorMod(to - from, RiftLayout.PLATFORMS);
        return Math.min(d, RiftLayout.PLATFORMS - d);
    }

    /** The platform whose top it stands on (within its rim, near its height), or -1. */
    private static int platformUnder(Vec3 me) {
        for (RiftLayout.Platform p : layout().platforms()) {
            if (Math.hypot(me.x - p.x(), me.z - p.z()) <= p.radius() + 0.3 && Math.abs(me.y - (p.top() + 1.0)) < 2.5) {
                return p.index();
            }
        }
        return -1;
    }

    private static int nearestPlatform(Vec3 me) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (RiftLayout.Platform p : layout().platforms()) {
            double d = Math.hypot(me.x - p.x(), me.z - p.z());
            if (d < bestD) {
                bestD = d;
                best = p.index();
            }
        }
        return best;
    }

    private static boolean nearBoulder(int k, Vec3 at) {
        for (Vec3 b : obstacles(k)) {
            if (Math.hypot(at.x - b.x, at.z - b.z) < b.y + 0.8) {
                return true;
            }
        }
        return false;
    }

    /** What stands on platform {@code k} to walk round: its boulders and (platform 0) the bell and its lit posts, as (x, radius, z). */
    private static List<Vec3> obstacles(int k) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 b : layout().boulders(k)) {
            out.add(new Vec3(b.x, RiftLayout.BOULDER, b.z));
        }
        if (k == 0) {
            Vec3 bell = Vec3.atCenterOf(layout().bell());
            out.add(new Vec3(bell.x, 0.9, bell.z));
            for (Vec3 post : layout().bellPosts()) {
                out.add(new Vec3(post.x, 0.8, post.z));
            }
        }
        return out;
    }

    /** To platform {@code k}'s inner edge, round the ring of platforms by their bridges when it stands on another. */
    private void stand(Minecraft mc, int k, double fromEdge, Vec3 look) {
        if (platformUnder(mc.player.position()) != k) {
            travel(mc, k, look);
            return;
        }
        route.clear();
        routeTo = -1;
        walkTo(mc, standPoint(layout().platform(k), fromEdge), 0.35, look);
    }

    private void travel(Minecraft mc, int to, Vec3 look) {
        Vec3 me = mc.player.position();
        if (routeTo != to || route.isEmpty()) {
            planRoute(me, to);
        }
        Vec3 next = route.peek();
        if (next != null && walkTo(mc, next, 0.6, look)) {
            route.poll();
        }
    }

    /** Waypoints round the ring: across each bridge from one platform's rim to the next's, then to the target's inner edge. */
    private void planRoute(Vec3 me, int to) {
        route.clear();
        RiftLayout lay = layout();
        int from = nearestPlatform(me);
        int n = RiftLayout.PLATFORMS;
        int forward = Math.floorMod(to - from, n);
        int dir = forward <= n / 2 ? 1 : -1;
        int count = dir == 1 ? forward : n - forward;
        int k = from;
        for (int i = 0; i < count; i++) {
            int next = Math.floorMod(k + dir, n);
            RiftLayout.Platform p = lay.platform(k);
            RiftLayout.Platform q = lay.platform(next);
            Vec3 u = new Vec3(q.x() - p.x(), 0, q.z() - p.z()).normalize();
            route.add(new Vec3(p.x() + u.x * (p.radius() - 1.5), p.top() + 1.0, p.z() + u.z * (p.radius() - 1.5)));
            route.add(new Vec3(q.x() - u.x * (q.radius() - 1.5), q.top() + 1.0, q.z() - u.z * (q.radius() - 1.5)));
            k = next;
        }
        route.add(standPoint(lay.platform(to), EDGE));
        routeTo = to;
    }

    // ------------------------------------------------------------------ geometry

    /** True if a swing from {@code chest} reaches {@code box}: within {@link #REACH} flat, overlapping the band. */
    private static boolean inReach(Vec3 chest, AABB box) {
        double nx = Mth.clamp(chest.x, box.minX, box.maxX);
        double nz = Mth.clamp(chest.z, box.minZ, box.maxZ);
        return Math.hypot(nx - chest.x, nz - chest.z) <= REACH && box.maxY >= chest.y - BAND && box.minY <= chest.y + BAND;
    }

    /** True if nothing solid stands between the eye and the part's eye height over its middle (as the server's hit check sees it). */
    private static boolean inSight(Minecraft mc, AABB box) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 partEye = new Vec3((box.minX + box.maxX) / 2.0, box.minY + (box.maxY - box.minY) * 0.85, (box.minZ + box.maxZ) / 2.0);
        return mc.level.clip(new net.minecraft.world.level.ClipContext(eye, partEye, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static Vec3 nearestPoint(Vec3 from, AABB box) {
        return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY), Mth.clamp(from.z, box.minZ, box.maxZ));
    }

    private static @Nullable Part nearestPart(Vec3 me, Snap s, boolean glands) {
        Part best = null;
        double bestD = Double.MAX_VALUE;
        for (Part p : s.parts()) {
            if (p.role() >= ThalassineLeviathan.ROLE_GLAND && !glands) {
                continue;
            }
            double d = flat(p.centre(), me);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean near(AABB box, Vec3 p, double reach) {
        return nearestPoint(p, box).distanceTo(p) <= reach;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static double radius(Vec3 p) {
        Vec3 c = layout().centre();
        return Math.hypot(p.x - c.x, p.z - c.z);
    }

    /** True if {@code blocks} back (away from where it faces) is still on its platform, a block in from the rim. */
    private static boolean backIsSafe(Minecraft mc, double blocks) {
        Vec3 me = mc.player.position();
        int k = platformUnder(me);
        if (k < 0) {
            return false;
        }
        RiftLayout.Platform p = layout().platform(k);
        Vec3 back = Vec3.directionFromRotation(0, mc.player.getYRot()).scale(-blocks);
        return Math.hypot(me.x + back.x - p.x(), me.z + back.z - p.z()) < p.radius() - 1.0;
    }

    // ------------------------------------------------------------------ inputs

    private void heal(Minecraft mc, Snap s) {
        if (s.myHealth() < 0.4f * s.myMax() && tick - healedAt > 40) {
            healedAt = tick;
            heals++;
            mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 1 true");
        }
    }

    /** Walks (looking where it goes, sprinting when far, jumping at a step) toward {@code goal}; true once within {@code within}. */
    private boolean walkTo(Minecraft mc, Vec3 goal, double within, Vec3 look) {
        Vec3 me = mc.player.position();
        double d = flat(me, goal);
        if (d <= within) {
            release(mc.options.keyUp);
            release(mc.options.keySprint);
            ColossusScenario.lookAt(mc, look);
            return true;
        }
        Vec3 via = aroundBoulder(me, goal);
        ColossusScenario.lookAt(mc, new Vec3(via.x, mc.player.getEyeY(), via.z));
        hold(mc.options.keyUp);
        if (d > 3.0) {
            hold(mc.options.keySprint);
        }
        if (mc.player.horizontalCollision && mc.player.onGround() && (platformUnder(me) < 0 || rimGap(me) > 3.5)) {
            hold(mc.options.keyJump);
        }
        return false;
    }

    private void idle(Minecraft mc, Vec3 look) {
        release(mc.options.keyUp);
        ColossusScenario.lookAt(mc, look);
    }

    /** A press of attack every {@value #SWING_EVERY} ticks (the Maul's combo carries on while pressed in time). */
    private void swing(Minecraft mc, Snap s) {
        swingTicks++;
        if (s.state() == State.FIGHT) {
            orbitSwingTicks++;
        }
        if (tick >= nextSwing && mc.player.onGround() && !PlayerCombat.of(mc.player).machine().isDashing()) {
            nextSwing = tick + SWING_EVERY;
            press(mc.options.keyAttack);
        }
    }

    private void press(KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
        release.add(key);
    }

    private void hold(KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        release.add(key);
    }

    private static void release(KeyMapping key) {
        KeyMapping.set(key.getKey(), false);
    }

    private static void releaseAll() {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump,
                mc.options.keySprint, mc.options.keyAttack, ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            KeyMapping.set(key.getKey(), false);
        }
    }

    // ------------------------------------------------------------------ the server's view

    private static @Nullable Snap snap() {
        return ServerQuery.ask(p -> {
            ThalassineLeviathan l = LeviathanScenario.lev(p);
            if (l == null || l.isRemoved()) {
                return null;
            }
            long now = l.level().getGameTime();
            List<Part> parts = new ArrayList<>();
            parts.add(new Part(0, l.getBoundingBox()));
            for (GuardianPart g : p.serverLevel().getEntitiesOfClass(GuardianPart.class, l.getBoundingBox().inflate(72.0),
                    g -> g.owner() == l && g.isAlive())) {
                parts.add(new Part(g.role(), g.getBoundingBox()));
            }
            List<AABB> scales = new ArrayList<>();
            for (ShedScale scale : p.serverLevel().getEntitiesOfClass(ShedScale.class, p.getBoundingBox().inflate(16.0), ShedScale::isAlive)) {
                scales.add(scale.getBoundingBox());
            }
            List<Vec3> glandPoints = new ArrayList<>();
            if (l.state() == State.MOORAGE && l.moorStage() == ThalassineLeviathan.Moor.COILED) {
                for (int i = 0; i < ThalassineLeviathan.GLANDS; i++) {
                    glandPoints.add(l.glandPoint(i));
                }
            }
            return new Snap(l.state(), l.moorStage(), l.action(), now - l.actionStart(), l.isBroken(), l.drooping(), l.glandsExposed(),
                    l.getHealth(), l.getMaxHealth(), now, l.songCone(), l.divePath(), parts, scales, glandPoints, l.orbitWave(),
                    now - l.moorStart(), p.getHealth(), p.getMaxHealth(), l.counts(), l.breaks(), l.gaugeFraction());
        });
    }
}
