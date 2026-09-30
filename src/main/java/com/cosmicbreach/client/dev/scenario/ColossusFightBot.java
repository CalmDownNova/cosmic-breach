package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.Telegraphs;
import com.cosmicbreach.guardian.colossus.ColossusMoves;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.guardian.colossus.Refraction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A whole Prism Colossus fight played by script through the real inputs (movement, attack, dash and parry keys, the
 * look direction), timed: part {@code colossus-fight}. The player is the scenario's level 12 with the Vanguard and
 * Meridian. It fights like an eager player: it stays in melee and swings whenever nothing threatens it, parries a gold
 * slam on its glint, sidesteps a plain one, backsteps from a Prism Burst, runs to a lit crown crystal and turns it
 * until the beam ends in the core, steps off burning lines, hits the open core in a Break and hunts the shards. When
 * low it drinks (an instant health effect by command, counted). Logs the fight's length, its parts and the uptime.
 */
final class ColossusFightBot {
    /** The fight's longest allowed length, in ticks (7.5 minutes). */
    private static final int TIMEOUT = 9000;
    private static final double MELEE = 2.75;
    private static final int SWING_EVERY = 9;
    /** The bot keeps within this distance of the crown's centre (its floor is 18 in radius, the rim wall beyond). */
    private static final double SAFE_RADIUS = CrownArena.FLOOR_RADIUS - 2.0;

    private final ColossusScenario scenario;
    private final List<String> summary;

    // what the bot remembers between ticks
    private int tick;
    private int nextSwing;
    private long handledSlam = Long.MIN_VALUE;
    private int backsteppedAt = -100;
    private int dashedAt = -100;
    private int pressedDashAt = -100;
    private int dashes;
    private int healedAt = -100;
    private final List<KeyMapping> release = new ArrayList<>();
    // what it measures
    private long fightStart = -1;
    private long phaseTwoAt = -1;
    private long shatterAt = -1;
    private long killAt = -1;
    private int fightTicks;
    private int swingTicks;
    private int heals;
    private int parryPresses;
    private int dodges;
    private int turns;
    private int turnsLanded;
    private final List<String> refractions = new ArrayList<>();
    private int breaks;
    private int coreHits;
    private int shatters;
    private float taken;
    private final java.util.Map<PrismColossus.Action, Integer> attacks = new java.util.EnumMap<>(PrismColossus.Action.class);
    private long lastActionStart = Long.MIN_VALUE;
    /** Where and when the player first stood outside the arena mid-fight (a bot that falls off can't finish). */
    private String leftArena = "";
    private boolean resetMidFight;

    ColossusFightBot(ColossusScenario scenario, List<String> summary) {
        this.scenario = scenario;
        this.summary = summary;
    }

    /** What the server says this tick. */
    private record Snap(PrismColossus.State state, int phase, PrismColossus.Action action, long actionTick, boolean broken, boolean glowing,
                        float yaw, float health, float maxHealth, PrismColossus.FistMode[] mode, long[] slamTick, boolean[] glint, Vec3[] ring,
                        int[] lit, boolean[] litToCore, List<Vec3[]> segments, List<Vec3> shards, float myHealth, float myMax, long now,
                        int breaks, int coreHits, int shatters, int crystalTurns, int[] stepsToCore) {
    }

    void steps(Steps steps, Minecraft mc) {
        scenario.equip(steps, mc);
        steps.command("gamerule naturalRegeneration true")
                .run("onto the crown, 9 blocks out", () -> {
                    CrownArena a = ColossusScenario.arena();
                    ColossusScenario.tp(mc, a.x() + 0.5, a.floorY(), a.z() + 9.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("it wakes", 100, () -> ServerQuery.ask(p -> ColossusScenario.colossusOf(p).state() == PrismColossus.State.INTRO))
                .run("reset the counters", () -> scenario.playerDamage().clear())
                .waitUntil("the fight (played by script, at most 7.5 minutes)", TIMEOUT + 400, () -> tick(mc))
                .run("release every key", () -> releaseAll(mc))
                .log("fight", this::report)
                .run("note it", () -> summary.add(report()))
                .run("the Colossus fell", () -> {
                    if (killAt <= 0) {
                        throw new Steps.Failure((resetMidFight ? "the Colossus reset to dormant mid-fight" : "the Colossus did not fall")
                                + (leftArena.isEmpty() ? "" : "; " + leftArena));
                    }
                });
    }

    private String report() {
        double total = (killAt - fightStart) / 20.0;
        double p1 = (phaseTwoAt - fightStart) / 20.0;
        double p2 = (shatterAt - phaseTwoAt) / 20.0;
        double shards = (killAt - shatterAt) / 20.0;
        return String.format(Locale.ROOT, "scripted fight: %.0f s from the end of the intro to the kill (phase 1 %.0f s, phase 2 %.0f s, "
                        + "shards %.0f s); uptime %.0f%% of the fight's ticks swinging; %d Break(s), %d beam(s) into the core, %d Shatter(s), "
                        + "%d parry press(es), %d dodge(s), %d dash(es) to crystals, %d tick(s) swinging at crystals, %d turn(s) landed "
                + "(turns needed at each Refraction's start: %s); took %.0f damage, %d heal(s); attacks %s%s",
                total, p1, p2, shards, 100.0 * swingTicks / Math.max(1, fightTicks), breaks, coreHits, shatters, parryPresses, dodges, dashes,
                turns, turnsLanded, refractions, taken, heals, attacks, leftArena.isEmpty() ? "" : "; " + leftArena);
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
            if (fightStart >= 0 && killAt < 0) {
                killAt = mc.level.getGameTime();
            }
            return killAt > 0 || tick > TIMEOUT;
        }
        breaks = s.breaks();
        turnsLanded = s.crystalTurns();
        if (s.action() == PrismColossus.Action.REFRACTION && s.actionTick() == 1) {
            refractions.add(java.util.Arrays.toString(s.stepsToCore()));
        }
        coreHits = s.coreHits();
        shatters = s.shatters();
        taken = 0f;
        for (float f : scenario.playerDamage()) {
            taken += f;
        }
        if (fightStart < 0 && s.state() == PrismColossus.State.FIGHT) {
            fightStart = s.now();
        }
        CrownArena arena = ColossusScenario.arena();
        Vec3 me = mc.player.position();
        if (fightStart >= 0 && leftArena.isEmpty() && !arena.contains(me)) {
            leftArena = String.format(Locale.ROOT, "the player left the arena at tick %d (%s, phase %d): %.1f blocks from the centre, "
                    + "%.1f above the floor%s", tick, s.state(), s.phase(), arena.distance(me.x, me.z), me.y - arena.floorY(),
                    com.cosmicbreach.combat.PlayerCombat.of(mc.player).machine().isDashing() ? ", dashing" : "");
        }
        if (fightStart >= 0 && s.state() == PrismColossus.State.DORMANT) {
            resetMidFight = true;
            stop(mc);
            return true;
        }
        if (phaseTwoAt < 0 && s.phase() == 2) {
            phaseTwoAt = s.now();
        }
        if (shatterAt < 0 && s.state() == PrismColossus.State.SHATTERED) {
            shatterAt = s.now();
        }
        if (s.state() == PrismColossus.State.DYING) {
            if (killAt < 0) {
                killAt = s.now();
            }
            stop(mc);
            return true;
        }
        if (tick > TIMEOUT) {
            stop(mc);
            return true;
        }
        if (fightStart >= 0) {
            fightTicks++;
        }
        long start = s.now() - s.actionTick();
        if (s.action() != PrismColossus.Action.NONE && start != lastActionStart) {
            lastActionStart = start;
            attacks.merge(s.action(), 1, Integer::sum);
        }
        heal(mc, s);
        switch (s.state()) {
            case FIGHT -> fight(mc, s);
            case SHATTERED -> shards(mc, s);
            default -> hold(mc, s);
        }
        return false;
    }

    private void fight(Minecraft mc, Snap s) {
        CrownArena a = ColossusScenario.arena();
        Vec3 me = mc.player.position();
        Vec3 centre = new Vec3(a.x(), a.floorY(), a.z());
        Vec3 out = flat(me.subtract(centre));
        double d = Math.hypot(me.x - a.x(), me.z - a.z());
        AABB box = mc.player.getBoundingBox();
        // 1. a slam coming down on me
        for (int f = 0; f < 2; f++) {
            if (s.mode()[f] != PrismColossus.FistMode.SLAM || !Telegraphs.inCircle(box.inflate(0.3), s.ring()[f], ColossusMoves.SLAM_RADIUS)) {
                continue;
            }
            long t = s.slamTick()[f];
            long key = s.now() - t + f;
            if (t >= 12 && t < 21) {
                idle(mc, centre.add(0, 2, 0)); // let the current swing end, so the answer isn't refused
                return;
            }
            if (t >= 21 && handledSlam != key) {
                handledSlam = key;
                if (s.glint()[f]) {
                    press(mc, ModKeyMappings.PARRY);
                    parryPresses++;
                } else {
                    hold(mc, mc.options.keyLeft);
                    press(mc, ModKeyMappings.DASH);
                    dodges++;
                }
                return;
            }
        }
        // 2. a Prism Burst building while I hug it
        if (s.glowing() && d < ColossusMoves.BURST_RADIUS + 1.5) {
            if (tick - backsteppedAt > 20) {
                backsteppedAt = tick;
                ColossusScenario.lookAt(mc, centre.add(0, 2, 0));
                press(mc, ModKeyMappings.DASH);
                dodges++;
            } else {
                walkTo(mc, centre.add(out.scale(5.5)), centre.add(0, 2, 0));
            }
            return;
        }
        // 3. the Facet Sweep while I stand in its band: get inside 3
        if (s.action() == PrismColossus.Action.SWEEP && d > ColossusMoves.SWEEP_INNER - 0.4 && d < ColossusMoves.SWEEP_OUTER + 0.6) {
            if (s.actionTick() >= ColossusMoves.SWEEP_TELL - 2 && d > 4.5) {
                ColossusScenario.lookAt(mc, centre.add(0, 1.6, 0));
                hold(mc, mc.options.keyUp);
                press(mc, ModKeyMappings.DASH);
                dodges++;
                return;
            }
            walkTo(mc, centre.add(out.scale(2.3)), centre.add(0, 2, 0));
            return;
        }
        // 4. Refraction: while it charges, turn a lit crystal back toward the core; while it burns, stay off the lines
        if (s.action() == PrismColossus.Action.REFRACTION) {
            if (s.actionTick() < ColossusMoves.REFRACTION_CHARGE - 1) {
                int k = crystalToTurn(s, me);
                if (k >= 0) {
                    Vec3 crystal = a.crystalPoint(k);
                    Vec3 inward = flat(centre.subtract(crystal));
                    Vec3 stand = new Vec3(crystal.x, a.floorY(), crystal.z).add(inward.scale(2.6));
                    Vec3 aim = new Vec3(crystal.x, a.floorY() + 1.6, crystal.z);
                    if (flatDistance(me, crystal) > 3.3) {
                        walkTo(mc, stand, aim);
                    } else {
                        release(mc, mc.options.keyUp);
                        ColossusScenario.lookAt(mc, aim);
                        swing(mc, true);
                        turns++;
                    }
                    return;
                }
            }
            for (Vec3[] seg : s.segments()) {
                if (Telegraphs.onLine(box, seg[0], seg[1], ColossusMoves.BEAM_RADIUS + 0.7)) {
                    Vec3 away = Telegraphs.outOfLine(me, seg[0], seg[1]);
                    walkTo(mc, me.add(away.scale(2.5)), centre.add(0, 2, 0));
                    return;
                }
            }
        }
        // 5. a Break: the core, low and in front
        if (s.broken()) {
            Vec3 core = a.core(s.yaw(), true);
            Vec3 stand = new Vec3(core.x, a.floorY(), core.z).add(flat(me.subtract(core)).scale(1.8));
            if (flatDistance(me, stand) > 0.8 && flatDistance(me, core) > 2.6) {
                walkTo(mc, stand, core);
            } else {
                release(mc, mc.options.keyUp);
                ColossusScenario.lookAt(mc, core);
                swing(mc, true);
            }
            return;
        }
        // 6. otherwise: in close and swinging (a fist resting in the floor is free hits too)
        Vec3 target = centre.add(0, 2.2, 0);
        for (int f = 0; f < 2; f++) {
            if (s.mode()[f] == PrismColossus.FistMode.REST && flatDistance(me, s.ring()[f]) < 3.0) {
                target = s.ring()[f].add(0, ColossusMoves.FIST_REST, 0);
            }
        }
        if (d > MELEE + 0.4) {
            walkTo(mc, centre.add(out.scale(MELEE)), target);
        } else {
            release(mc, mc.options.keyUp);
            ColossusScenario.lookAt(mc, target);
            swing(mc, true);
        }
    }

    /**
     * The lit crystal whose beam doesn't end in the core yet that is quickest to turn back: its distance (at about half a
     * block a tick, dashing) plus 10 ticks a turn still needed; or -1.
     */
    private static int crystalToTurn(Snap s, Vec3 me) {
        CrownArena a = ColossusScenario.arena();
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < s.lit().length; i++) {
            if (s.litToCore()[i]) {
                continue;
            }
            double cost = flatDistance(me, a.crystalPoint(s.lit()[i])) / 0.5 + 10.0 * Math.max(1, s.stepsToCore()[i]);
            if (cost < bestCost) {
                bestCost = cost;
                best = s.lit()[i];
            }
        }
        return best;
    }

    private void shards(Minecraft mc, Snap s) {
        Vec3 me = mc.player.position();
        CrownArena a = ColossusScenario.arena();
        Vec3 best = null;
        for (Vec3 shard : s.shards()) {
            if (a.distance(shard.x, shard.z) > SAFE_RADIUS || Math.abs(shard.y - a.floorY()) > 3.0) {
                continue; // over the rim or in the air: it falls and is sent back in
            }
            if (best == null || me.distanceToSqr(shard) < me.distanceToSqr(best)) {
                best = shard;
            }
        }
        if (best == null) {
            hold(mc, s);
            return;
        }
        Vec3 aim = best.add(0, 0.35, 0);
        if (flatDistance(me, best) > 2.3) {
            walkTo(mc, best, aim);
        } else {
            release(mc, mc.options.keyUp);
            ColossusScenario.lookAt(mc, aim);
            swing(mc, true);
        }
    }

    /** Between the fight's beats (the intro, the Fracture, the re-forming): wait in melee range. */
    private void hold(Minecraft mc, Snap s) {
        CrownArena a = ColossusScenario.arena();
        Vec3 centre = new Vec3(a.x(), a.floorY(), a.z());
        Vec3 me = mc.player.position();
        Vec3 out = flat(me.subtract(centre));
        if (Math.hypot(me.x - a.x(), me.z - a.z()) > MELEE + 1.0) {
            walkTo(mc, centre.add(out.scale(MELEE + 0.5)), centre.add(0, 2, 0));
        } else {
            idle(mc, centre.add(0, 2, 0));
        }
    }

    private void heal(Minecraft mc, Snap s) {
        if (s.myHealth() < 0.4f * s.myMax() && tick - healedAt > 40) {
            healedAt = tick;
            heals++;
            mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 1 true");
        }
    }

    // ------------------------------------------------------------------ inputs

    private void walkTo(Minecraft mc, Vec3 goal, Vec3 look) {
        Vec3 me = mc.player.position();
        CrownArena a = ColossusScenario.arena();
        double r = a.distance(goal.x, goal.z);
        if (r > SAFE_RADIUS) { // keep the goal on the floor, well inside the rim wall
            double k = SAFE_RADIUS / r;
            goal = new Vec3(a.x() + (goal.x - a.x()) * k, goal.y, a.z() + (goal.z - a.z()) * k);
        }
        double d = flatDistance(me, goal);
        if (d < 0.4) {
            release(mc, mc.options.keyUp);
            release(mc, mc.options.keySprint);
            ColossusScenario.lookAt(mc, look);
            return;
        }
        ColossusScenario.lookAt(mc, new Vec3(goal.x, me.y + mc.player.getEyeHeight(), goal.z));
        KeyMapping.set(mc.options.keyUp.getKey(), true);
        KeyMapping.set(mc.options.keySprint.getKey(), d > 3.0);
        // a long way to go (a lit crystal across the arena): dash along it, again next tick if a swing refused it
        boolean dashing = com.cosmicbreach.combat.PlayerCombat.of(mc.player).machine().isDashing();
        if (dashing) {
            dashedAt = tick;
        } else if (d > 6.0 && tick - dashedAt > 9 && tick - pressedDashAt > 1 && a.distance(goal.x, goal.z) < SAFE_RADIUS - 3.0) {
            pressedDashAt = tick;
            press(mc, ModKeyMappings.DASH);
            dashes++;
        }
    }

    private void idle(Minecraft mc, Vec3 look) {
        release(mc, mc.options.keyUp);
        ColossusScenario.lookAt(mc, look);
    }

    /** A press of attack every {@value #SWING_EVERY} ticks. */
    private void swing(Minecraft mc, boolean counts) {
        if (counts) {
            swingTicks++;
        }
        if (tick >= nextSwing) {
            nextSwing = tick + SWING_EVERY;
            press(mc, mc.options.keyAttack);
        }
    }

    private void press(Minecraft mc, KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
        release.add(key);
    }

    private void hold(Minecraft mc, KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        release.add(key);
    }

    private void release(Minecraft mc, KeyMapping key) {
        KeyMapping.set(key.getKey(), false);
    }

    private void stop(Minecraft mc) {
        releaseAll(mc);
    }

    private void releaseAll(Minecraft mc) {
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keyLeft, mc.options.keySprint, mc.options.keyAttack,
                ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            KeyMapping.set(key.getKey(), false);
        }
    }

    // ------------------------------------------------------------------ the server's view

    private static @Nullable Snap snap() {
        return ServerQuery.ask(p -> {
            PrismColossus c = ColossusScenario.colossusOrNull(p);
            if (c == null || c.isRemoved()) {
                return null;
            }
            long now = c.level().getGameTime();
            PrismColossus.FistMode[] mode = {c.fistMode(true), c.fistMode(false)};
            long[] slamTick = {now - c.fistStart(true), now - c.fistStart(false)};
            boolean[] glint = {c.fistGlints(true), c.fistGlints(false)};
            Vec3[] ring = {c.fistParam(true), c.fistParam(false)};
            int[] lit = c.litCrystals();
            List<int[]> paths = c.refractionPaths();
            boolean[] toCore = new boolean[lit.length];
            List<Vec3[]> segments = new ArrayList<>();
            CrownArena a = c.arena();
            for (int i = 0; i < paths.size() && i < lit.length; i++) {
                int[] path = paths.get(i);
                toCore[i] = Refraction.endsAtCore(path);
                Vec3 prev = a.eye(c.getYRot());
                for (int node : path) {
                    Vec3 next = a.node(node, c.getYRot(), c.isBroken());
                    segments.add(new Vec3[] {prev, next});
                    prev = next;
                }
            }
            int[] steps = new int[lit.length];
            for (int i = 0; i < lit.length; i++) {
                steps[i] = p.level().getBlockEntity(a.crystalBase(lit[i])) instanceof com.cosmicbreach.guardian.colossus.CrownCrystalBlockEntity be
                        ? Refraction.stepsToCore(lit[i], be.target()) : -1;
            }
            List<Vec3> shards = new ArrayList<>();
            for (UUID id : c.shardIds()) {
                if (p.serverLevel().getEntity(id) instanceof PrismShard shard && shard.isAlive()) {
                    shards.add(shard.position());
                }
            }
            return new Snap(c.state(), c.phase(), c.action(), now - c.actionStart(), c.isBroken(), c.glowing(), c.getYRot(), c.getHealth(),
                    (float) c.maxHealth(), mode, slamTick, glint, ring, lit, toCore, segments, shards, p.getHealth(), p.getMaxHealth(), now,
                    c.breaks(), c.coreHits(), c.shatters(), c.crystalTurns(), steps);
        });
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static double flatDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    @SuppressWarnings("unused")
    private static ServerPlayer none() {
        return null;
    }
}
