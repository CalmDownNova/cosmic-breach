package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.Telegraphs;
import com.cosmicbreach.guardian.heliarch.CoronaFlare;
import com.cosmicbreach.guardian.heliarch.EclipseCover;
import com.cosmicbreach.guardian.heliarch.HaloShed;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HeliarchMoves;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.cosmicbreach.guardian.heliarch.Heliarchs;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.guardian.heliarch.NovaRules;
import com.cosmicbreach.guardian.heliarch.StarSeed;
import com.cosmicbreach.guardian.heliarch.TendrilRules;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A whole Hollow Heliarch fight played by script through the real inputs (movement, attack, jump, dash and parry keys,
 * the look direction), every answer timed on the server's tick: part {@code heliarch-fight}. The player is the
 * scenario's level 36 in the Choir Regalia with Meridian at tier III, and it plays like a near-perfect player: it
 * summons with the Heart, keeps under the core and strikes it whenever it can be hurt, parries every Sunderfall on its
 * glint and every lash that comes at it, backsteps through a Corona Flare on its last ticks (the dash's i-frames), stands
 * between the Halo Shed's return lines, steps out of the Eclipse Beam's
 * sweep (or behind a monolith), breaks the Corona Shield, cuts Star Seeds out of the air and steps out of Solar Rain.
 * When low it drinks (an instant health effect by command, counted). Logs the fight's length, its phases and what it
 * met.
 */
final class HeliarchFightBot {
    /** The fight's longest allowed length, in ticks (10 minutes). */
    private static final int TIMEOUT = 12_000;
    private static final int SWING_EVERY = 9;
    /** Its place: this far from the throne's column, under the core's rim, where a sword reaches the core. */
    private static final double STAND = 3.0;
    private static final double REACH = 3.0;

    private final HeliarchScenario scenario;
    private final List<String> results;

    // what the bot remembers between ticks
    private int tick;
    private int nextSwing;
    private final List<KeyMapping> release = new ArrayList<>();
    private long handledSunder = Long.MIN_VALUE;
    private final long[] handledLash = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private long handledLance = Long.MIN_VALUE;
    private long handledFlare = Long.MIN_VALUE;
    private long handledJump = Long.MIN_VALUE;
    private int dashedAt = -100;
    private int healedAt = -100;
    private double standAngle = 180.0;
    private boolean dead;
    // what it measures (server ticks)
    private long regentAt = -1;
    private long hollowingAt = -1;
    private long hollowAt = -1;
    private long novaAt = -1;
    private long collapseAt = -1;
    private long killAt = -1;
    private int fightTicks;
    private int swingTicks;
    private int closedTicks;
    private int heals;
    private int deaths;
    private int sunderPresses;
    private int lashPresses;
    private int dodges;
    private int dashes;
    private int jumps;
    private int seedSwings;
    private int beamMoves;
    private int flareDashes;
    private String end = "";
    private final Map<Action, Integer> attacks = new EnumMap<>(Action.class);
    private long lastActionStart = Long.MIN_VALUE;

    HeliarchFightBot(HeliarchScenario scenario) {
        this.scenario = scenario;
        this.results = scenario.results();
    }

    /** What the server says this tick. */
    private record Snap(State state, Action action, long now, long actionTick, double actionAngle, Vec3 actionPos, int beamDir,
                        Vec3 core, boolean coreOpen, boolean stunned, boolean channelling, double shield, long channelTick,
                        long[] lashTick, Vec3[] lashEnd, int[] pips, int pillarsDown, @Nullable List<Vec3> rain, long rainLeft,
                        List<Vec3> seeds, boolean lanceMine, @Nullable Vec3[] lance, Vec3[] hands, double health, double maxHealth,
                        float myHealth, float myMax, boolean[] silent) {
    }

    void steps(Steps steps, Minecraft mc) {
        new HeliarchMechanics(scenario).equip(steps, mc);
        steps.command("execute in cosmicbreach:aetheria run spawnpoint @s 0 " + SanctumLayout.ARENA_Y + " 4")
                .command("item replace entity @s hotbar.3 with cosmicbreach:dying_star_heart")
                .run("before the throne", () -> CryptKit.tp(mc, new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5 + HeliarchScenario.side() * 2.6),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 0.6, 0.5)))
                .waitTicks(10)
                .run("hold the Heart", () -> select(mc, 3))
                .waitTicks(3)
                .run("aim at the throne's seat", () -> CryptKit.aim(mc, new Vec3(0.5, SanctumLayout.ARENA_Y + 0.5, 0.5)))
                .press(mc.options.keyUse)
                .waitUntil("the Heliarch rises from the Heart", 60, HeliarchScenario::fighting)
                .run("hold the weapon", () -> select(mc, 0))
                .waitUntil("the fight (played by script, at most 10 minutes)", TIMEOUT + 400, () -> tick(mc))
                .run("release every key", () -> releaseAll(mc))
                .log("fight", this::report)
                .run("note it", () -> results.add(report()))
                .check("the Heliarch fell, and the fight passed through every phase", () -> killAt > 0 && regentAt > 0 && hollowingAt > 0
                        && hollowAt > 0 && novaAt > 0 && collapseAt > 0);
    }

    private String report() {
        return String.format(Locale.ROOT, "scripted fight: %.0f s from phase 1 to the kill (phase 1 %.0f s, the Hollowing %.0f s, phase 2 to "
                        + "Nova %.0f s, Nova to the Collapse %.0f s, the Collapse %.0f s); striking %.0f%% of its ticks, %d ticks waiting on a "
                        + "closed halo; %d Sunderfall parry press(es), %d lash parry press(es), %d Flare backstep(s), %d dodge(s), %d dash(es), "
                        + "%d jump(s), %d beam move(s), %d swing(s) at Star Seeds; %d heal(s), %d death(s); attacks seen: %s; %s",
                span(regentAt, killAt), span(regentAt, hollowingAt), span(hollowingAt, hollowAt), span(hollowAt, novaAt),
                span(novaAt, collapseAt), span(collapseAt, killAt), 100.0 * swingTicks / Math.max(1, fightTicks), closedTicks,
                sunderPresses, lashPresses, flareDashes, dodges, dashes, jumps, beamMoves, seedSwings, heals, deaths, attacksSeen(), end);
    }

    /** How many of each attack it met, every attack named (zero included). */
    private String attacksSeen() {
        List<String> out = new ArrayList<>();
        for (Action a : Action.values()) {
            if (a != Action.NONE) {
                out.add(a.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + attacks.getOrDefault(a, 0));
            }
        }
        return String.join(", ", out);
    }

    private static double span(long from, long to) {
        return from < 0 || to < 0 ? -1 : (to - from) / 20.0;
    }

    // ------------------------------------------------------------------ one tick of play

    /** Plays one tick; true once the fight is over (or timed out). */
    private boolean tick(Minecraft mc) {
        tick++;
        for (KeyMapping key : release) {
            KeyMapping.set(key.getKey(), false);
        }
        release.clear();
        if (mc.player == null) {
            return false;
        }
        if (!mc.player.isAlive()) {
            if (!dead) {
                dead = true;
                deaths++;
                releaseAll(mc);
            }
            if (mc.screen != null) {
                mc.player.respawn();
            }
            return tick > TIMEOUT;
        }
        if (dead) {
            dead = false;
            select(mc, 0);
        }
        Snap s = snap();
        if (s == null) {
            if (tick > TIMEOUT || regentAt >= 0) {
                releaseAll(mc);
                return true;
            }
            return false;
        }
        if (regentAt < 0 && s.state() == State.REGENT) {
            regentAt = s.now();
        }
        if (hollowingAt < 0 && s.state() == State.HOLLOWING) {
            hollowingAt = s.now();
        }
        if (hollowAt < 0 && s.state() == State.HOLLOW) {
            hollowAt = s.now();
        }
        if (novaAt < 0 && s.action() == Action.NOVA) {
            novaAt = s.now();
        }
        if (collapseAt < 0 && s.state() == State.COLLAPSE) {
            collapseAt = s.now();
        }
        if (s.state() == State.DYING) {
            killAt = s.now();
            end = HeliarchScenario.ask(h -> String.format(Locale.ROOT, "hits it took %s; %s; hits on me %s", h.summary(),
                    h.nova().detonations() > 0 ? "Nova detonated " + h.nova().detonations() + " time(s)" : "Nova never detonated", h.tallies()));
            releaseAll(mc);
            return true;
        }
        if (tick > TIMEOUT) {
            releaseAll(mc);
            return true;
        }
        long start = s.now() - s.actionTick();
        if (s.action() != Action.NONE && start != lastActionStart) {
            lastActionStart = start;
            attacks.merge(s.action(), 1, Integer::sum);
        }
        heal(mc, s);
        switch (s.state()) {
            case REGENT, HOLLOW, COLLAPSE -> fight(mc, s);
            default -> hold(mc, s);
        }
        return false;
    }

    private void fight(Minecraft mc, Snap s) {
        fightTicks++;
        Vec3 me = mc.player.position();
        AABB box = mc.player.getBoundingBox();
        double feet = me.y - HeliarchArena.FLOOR;
        // 1. Solar Rain about to land on me: out of every circle
        if (s.rain() != null && s.rainLeft() > 0 && inRain(me, s.rain(), 0.7)) {
            walkTo(mc, safeFrom(s.rain(), me), s.core(), true);
            dodges += s.rainLeft() == HeliarchMoves.RAIN_TELL - 1 ? 1 : 0;
            return;
        }
        // 2. a lash coming at me: parry it as it lands (its swing let go first, so the parry isn't refused)
        for (int i = 0; i < 4; i++) {
            long t = s.lashTick()[i];
            if (t < 0 || t >= HeliarchMoves.LASH_TELL
                    || !TendrilRules.onLash(me.x, me.z, feet, HeliarchArena.tendrilAnchor(i), s.lashEnd()[i])) {
                continue;
            }
            long key = s.now() - t;
            if (t >= HeliarchMoves.LASH_TELL - 3 && handledLash[i] != key) {
                handledLash[i] = key;
                press(ModKeyMappings.PARRY);
                lashPresses++;
                return;
            }
            if (t >= HeliarchMoves.LASH_TELL - 12) {
                idle(mc, s.core());
                return;
            }
        }
        // 2b. a Corona Flare while I'm inside its ring: swing while the core is open, stop so the dash isn't refused,
        // then a backstep on its last ticks (timed on the server's tick) so the dash's i-frames take the burst
        if (s.action() == Action.CORONA_FLARE && s.actionTick() < HeliarchMoves.FLARE_TELL && CoronaFlare.inside(me.x, me.z)) {
            long key = s.now() - s.actionTick();
            if (s.actionTick() >= HeliarchMoves.FLARE_TELL - 2 && handledFlare != key) {
                handledFlare = key;
                release(mc.options.keyUp);
                CryptKit.aim(mc, s.core());
                press(ModKeyMappings.DASH);
                flareDashes++;
                return;
            }
            if (s.actionTick() >= HeliarchMoves.FLARE_TELL - 11) {
                idle(mc, s.core());
                return;
            }
        }
        // 3. a Sunderfall's ring under me: parry on the gold glint
        if (s.action() == Action.SUNDERFALL && Telegraphs.inCircle(box.inflate(0.3), s.actionPos(), HeliarchMoves.SUNDER_RADIUS)) {
            long t = s.actionTick();
            long key = s.now() - t;
            if (t > HeliarchMoves.SUNDER_GLINT && t < HeliarchMoves.SUNDER_TELL && handledSunder != key) {
                handledSunder = key;
                press(ModKeyMappings.PARRY);
                sunderPresses++;
                return;
            }
            if (t >= HeliarchMoves.SUNDER_GLINT - 10 && t < HeliarchMoves.SUNDER_TELL + 2) {
                idle(mc, s.core());
                return;
            }
        }
        // 4. the Solar Lance locked on me: a dash aside
        if (s.action() == Action.SOLAR_LANCE && s.lanceMine() && s.lance() != null) {
            long t = s.actionTick();
            long key = s.now() - t;
            if (t >= HeliarchMoves.LANCE_TRACK && t < HeliarchMoves.LANCE_TRACK + HeliarchMoves.LANCE_LOCK + HeliarchMoves.LANCE_BURN
                    && Telegraphs.onLine(box, s.lance()[0], s.lance()[1], HeliarchMoves.LANCE_RADIUS + 0.8)) {
                CryptKit.face(mc, s.core(), 0f);
                hold(mc.options.keyLeft);
                if (handledLance != key) {
                    handledLance = key;
                    press(ModKeyMappings.DASH);
                    dodges++;
                }
                return;
            }
        }
        // 5. in the Corona Sweep's band: jump the wall as it comes, and get back under the core
        double r = HeliarchArena.radiusOf(me.x, me.z);
        if (s.action() == Action.CORONA_SWEEP && r > HeliarchMoves.SWEEP_INNER - 0.6 && r < HeliarchMoves.SWEEP_OUTER + 0.6) {
            double along = ((HeliarchArena.angleOf(me.x, me.z) - s.actionAngle()) % 360.0 + 360.0) % 360.0;
            double hit = HeliarchMoves.SWEEP_TELL + along / (360.0 / HeliarchMoves.SWEEP_TICKS);
            long key = s.now() - s.actionTick();
            if (s.actionTick() >= hit - 3 && s.actionTick() <= hit && handledJump != key) {
                handledJump = key;
                press(mc.options.keyJump);
                jumps++;
                return;
            }
            if (s.actionTick() < hit - 6) {
                walkTo(mc, spot(standAngle), s.core(), true);
                return;
            }
        }
        // 6. the Eclipse Beam: somewhere its sweep won't reach, under the core if it can be
        if (s.action() == Action.ECLIPSE_BEAM && s.actionTick() < HeliarchMoves.BEAM_TELL + HeliarchMoves.BEAM_SWEEP) {
            Vec3 safe = beamSafe(s, me);
            if (flat(me, safe) > 0.5) {
                if (s.actionTick() < 2) {
                    beamMoves++;
                }
                walkTo(mc, safe, s.core(), flat(me, safe) > 4.0);
                return;
            }
            standAngle = HeliarchArena.angleOf(safe.x, safe.z);
        }
        // 7. Nova: a shield it won't break in time sends it behind a monolith before the blast
        if (s.channelling() && HeliarchMoves.NOVA_CHANNEL - s.channelTick() < 70 && s.shield() > 0.25) {
            Vec3 cover = cover(s, me);
            if (cover != null) {
                walkTo(mc, cover, s.core(), true);
                return;
            }
        }
        // 8. the Halo Shed: between two of the return lines
        if (s.action() == Action.HALO_SHED) {
            standAngle = betweenLines(s.actionAngle(), HeliarchArena.angleOf(me.x, me.z));
        }
        // 9. a Star Seed near: cut it down
        Vec3 eye = mc.player.getEyePosition();
        Vec3 seed = null;
        for (Vec3 p : s.seeds()) {
            if (p.distanceTo(eye) < REACH + 0.6 && (seed == null || p.distanceTo(eye) < seed.distanceTo(eye))) {
                seed = p;
            }
        }
        if (seed != null) {
            release(mc.options.keyUp);
            CryptKit.aim(mc, seed);
            if (swing(true)) {
                seedSwings++;
            }
            return;
        }
        // 10. in phase 2 while the eclipse hangs over the pillars: at the nearest tendril, cutting at its root (it bleeds
        // the eclipse, and through Nova's channel it drains the shield)
        if ((s.state() == State.HOLLOW || s.state() == State.COLLAPSE) && s.core().y - HeliarchArena.FLOOR > 6.0) {
            int ti = nearestTendril(s, me);
            if (ti >= 0) {
                Vec3 anchor = HeliarchArena.tendrilAnchor(ti);
                Vec3 at = HeliarchArena.at(HeliarchArena.angleOf(anchor.x, anchor.z), 3.8);
                if (flat(me, at) > 0.6) {
                    walkTo(mc, at, anchor.add(0, 1.4, 0), false);
                    return;
                }
                release(mc.options.keyUp);
                release(mc.options.keySprint);
                CryptKit.aim(mc, anchor.add(0, 1.4, 0));
                swing(true);
                return;
            }
        }
        // 11. under the core, striking whenever it can be hurt (phase 1, or its heart laid bare)
        Vec3 spot = spot(standAngle);
        if (flat(me, spot) > 0.6) {
            walkTo(mc, spot, s.core(), false);
            return;
        }
        release(mc.options.keyUp);
        release(mc.options.keySprint);
        Vec3 target = target(s, eye);
        if (target == null) {
            idle(mc, s.core());
            closedTicks++;
            return;
        }
        CryptKit.aim(mc, target);
        swing(true);
    }

    /** The tendril still standing nearest to {@code me}, or -1. */
    private static int nearestTendril(Snap s, Vec3 me) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (s.silent()[i]) {
                continue;
            }
            double d = flat(me, HeliarchArena.tendrilAnchor(i));
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** What to strike: the core, or with the halo closed a hand within reach (half damage), or nothing. */
    private static @Nullable Vec3 target(Snap s, Vec3 eye) {
        if (s.state() == State.REGENT && !s.coreOpen()) {
            for (Vec3 hand : s.hands()) {
                if (hand != null && hand.distanceTo(eye) < REACH + HollowHeliarch.HAND_SIZE / 2.0) {
                    return hand;
                }
            }
            return null;
        }
        return s.core();
    }

    /** Between the fight's beats (the intro, the Hollowing): under the core, waiting. */
    private void hold(Minecraft mc, Snap s) {
        Vec3 me = mc.player.position();
        Vec3 spot = spot(standAngle);
        if (flat(me, spot) > 0.6) {
            walkTo(mc, spot, s.core(), false);
        } else {
            idle(mc, s.core());
        }
    }

    private void heal(Minecraft mc, Snap s) {
        if (s.myHealth() < 0.4f * s.myMax() && tick - healedAt > 40) {
            healedAt = tick;
            heals++;
            mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 1 true");
        }
    }

    // ------------------------------------------------------------------ where to stand

    private static Vec3 spot(double compass) {
        return HeliarchArena.at(compass, STAND);
    }

    /** The angle between two of the shed's return lines (they come back at the out angles plus half a gap) nearest mine. */
    private static double betweenLines(double facing, double mine) {
        double best = mine;
        double bestGap = Double.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            double a = HaloShed.outAngle(facing, k);
            double gap = Math.abs(HeliarchArena.wrap(a - mine));
            if (gap < bestGap) {
                bestGap = gap;
                best = a;
            }
        }
        return best;
    }

    /**
     * Where the beam can't burn it: a point under the core whose angle the wedge never crosses (with a margin), or
     * behind a standing monolith; the nearest such.
     */
    private static Vec3 beamSafe(Snap s, Vec3 me) {
        List<EclipseCover.Blocker> blockers = EclipseCover.blockers(s.pips(), s.pillarsDown());
        List<Vec3> candidates = new ArrayList<>();
        for (int k = 0; k < 36; k++) {
            candidates.add(spot(10.0 * k));
        }
        Vec3 c = cover(s, me);
        if (c != null) {
            candidates.add(c);
        }
        Vec3 best = candidates.get(0);
        double bestCost = Double.MAX_VALUE;
        for (Vec3 p : candidates) {
            if (!beamSafeAt(p, s, blockers)) {
                continue;
            }
            // walking under the throne's column is blocked by the throne: round the rim of the dais instead
            double cost = flat(me, p) + (crossesThrone(me, p) ? 4.0 : 0.0);
            if (cost < bestCost) {
                bestCost = cost;
                best = p;
            }
        }
        return best;
    }

    private static boolean beamSafeAt(Vec3 p, Snap s, List<EclipseCover.Blocker> blockers) {
        if (EclipseCover.covered(p.x, p.z, blockers)) {
            return true;
        }
        double angle = HeliarchArena.angleOf(p.x, p.z);
        for (int t = 0; t <= HeliarchMoves.BEAM_SWEEP; t++) {
            double beam = EclipseCover.beamAngle(s.actionAngle(), s.beamDir(), t);
            if (Math.abs(HeliarchArena.wrap(angle - beam)) <= HeliarchMoves.BEAM_WIDTH / 2.0 + 16.0) {
                return false;
            }
        }
        return true;
    }

    /** Behind the nearest monolith still standing (on the far side from the throne), or null. */
    private static @Nullable Vec3 cover(Snap s, Vec3 me) {
        Vec3 best = null;
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            if (s.pips()[m.index()] <= 0) {
                continue;
            }
            Vec3 mid = m.middle();
            Vec3 behind = mid.add(HeliarchArena.dir(HeliarchArena.angleOf(mid.x, mid.z)).scale(2.4));
            behind = new Vec3(behind.x, HeliarchArena.FLOOR, behind.z);
            if (best == null || flat(me, behind) < flat(me, best)) {
                best = behind;
            }
        }
        return best;
    }

    private static boolean inRain(Vec3 me, List<Vec3> circles, double margin) {
        for (Vec3 c : circles) {
            if (Math.hypot(me.x - c.x, me.z - c.z) < HeliarchMoves.RAIN_RADIUS + 0.3 + margin) {
                return true;
            }
        }
        return false;
    }

    /** A point a few blocks off, on the dais, as far from every circle as it can find. */
    private static Vec3 safeFrom(List<Vec3> circles, Vec3 me) {
        Vec3 best = me.add(3, 0, 0);
        double bestScore = -1;
        for (int k = 0; k < 16; k++) {
            Vec3 to = me.add(HeliarchArena.dir(22.5 * k).scale(4.0));
            if (HeliarchArena.radiusOf(to.x, to.z) > 7.5 || HeliarchArena.radiusOf(to.x, to.z) < 1.8) {
                continue;
            }
            double score = Double.MAX_VALUE;
            for (Vec3 c : circles) {
                score = Math.min(score, Math.hypot(to.x - c.x, to.z - c.z));
            }
            if (score > bestScore) {
                bestScore = score;
                best = to;
            }
        }
        return best;
    }

    private static boolean crossesThrone(Vec3 a, Vec3 b) {
        Vec3 c = new Vec3(HeliarchArena.CX, a.y, HeliarchArena.CZ);
        Vec3 near = Telegraphs.closestOnSegment(c, new Vec3(a.x, a.y, a.z), new Vec3(b.x, a.y, b.z));
        return Math.hypot(near.x - c.x, near.z - c.z) < 1.6;
    }

    // ------------------------------------------------------------------ inputs

    /** Toward {@code goal}: sprinting when it's far, a dash when it's urgent; round the throne rather than into it. */
    private void walkTo(Minecraft mc, Vec3 goal, Vec3 look, boolean urgent) {
        Vec3 me = mc.player.position();
        if (crossesThrone(me, goal)) {
            double a = HeliarchArena.angleOf(me.x, me.z);
            double b = HeliarchArena.angleOf(goal.x, goal.z);
            double mid = a + HeliarchArena.wrap(b - a) / 2.0;
            if (Math.abs(HeliarchArena.wrap(b - a)) > 170) {
                mid = a + 90.0;
            }
            goal = spot(mid);
        }
        double d = flat(me, goal);
        if (d < 0.35) {
            idle(mc, look);
            return;
        }
        CryptKit.face(mc, new Vec3(goal.x, me.y, goal.z), 0f);
        KeyMapping.set(mc.options.keyUp.getKey(), true);
        KeyMapping.set(mc.options.keySprint.getKey(), d > 2.5);
        if ((urgent || d > 5.0) && d > 1.5 && tick - dashedAt > 12) {
            dashedAt = tick;
            press(ModKeyMappings.DASH);
            dashes++;
        }
    }

    private void idle(Minecraft mc, Vec3 look) {
        release(mc.options.keyUp);
        release(mc.options.keySprint);
        CryptKit.aim(mc, look);
    }

    /** A press of attack every {@value #SWING_EVERY} ticks; true if it pressed now. */
    private boolean swing(boolean counts) {
        if (counts) {
            swingTicks++;
        }
        if (tick >= nextSwing) {
            nextSwing = tick + SWING_EVERY;
            press(Minecraft.getInstance().options.keyAttack);
            return true;
        }
        return false;
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

    private static void releaseAll(Minecraft mc) {
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keyLeft, mc.options.keySprint, mc.options.keyAttack,
                mc.options.keyJump, ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            KeyMapping.set(key.getKey(), false);
        }
    }

    private static void select(Minecraft mc, int slot) {
        KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
    }

    // ------------------------------------------------------------------ the server's view

    private static @Nullable Snap snap() {
        return ServerQuery.ask(p -> {
            HollowHeliarch h = Heliarchs.active(p.serverLevel());
            if (h == null || h.isRemoved()) {
                return null;
            }
            long now = h.level().getGameTime();
            long[] lash = new long[4];
            Vec3[] ends = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                long ls = h.lashStart(i);
                lash[i] = ls == Long.MIN_VALUE ? -1 : now - ls;
                ends[i] = h.lashEnd(i);
            }
            List<Vec3> seeds = new ArrayList<>();
            for (StarSeed seed : p.serverLevel().getEntitiesOfClass(StarSeed.class, p.getBoundingBox().inflate(10.0))) {
                if (seed.isAlive()) {
                    seeds.add(seed.position().add(0, seed.getBbHeight() / 2.0, 0));
                }
            }
            List<Vec3> rain = h.rainCircles();
            HeliarchPose.Input in = h.poseInput();
            Vec3[] hands = {HeliarchPose.hand(in, h.facing(), true, now), HeliarchPose.hand(in, h.facing(), false, now)};
            NovaRules n = h.nova();
            boolean[] silent = new boolean[4];
            for (int i = 0; i < 4; i++) {
                silent[i] = h.tendrilSilent(i);
            }
            return new Snap(h.state(), h.action(), now, now - h.actionStart(), h.actionAngle(), h.actionPos(), h.beamDir(), h.core(now),
                    h.coreOpen(now), n.stunned(now), n.channelling(), n.shieldFraction(), n.channelling() ? n.ticksIn(now) : -1, lash, ends,
                    h.pips(), h.pillarsDown(), rain == null ? null : List.copyOf(rain), h.rainLand() - now, seeds,
                    h.lanceTargetId() == p.getId(), h.action() == Action.SOLAR_LANCE ? h.lanceLine(now) : null, hands, h.health(),
                    h.maxHealth(), p.getHealth(), p.getMaxHealth(), silent);
        });
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }
}
