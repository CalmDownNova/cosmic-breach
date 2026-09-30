package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.unsung.ChoirArena;
import com.cosmicbreach.guardian.unsung.SongNote;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungMask;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.Voice;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * A whole Unsung fight played by script through the real inputs (movement, attack, jump, crouch, dash and parry keys,
 * the look direction), timed: part {@code unsung-fight}. The player is the mechanics part's: level 34 in the Choir
 * Regalia with the Comet Maul. It fights like a near-perfect player who knows the song: it keeps beside the singing
 * mask and swings; it dashes through a Homing Note on its beat, jumps the Sweeping Wave on its beat, crouches in the
 * Ground Ripples, parries the Bass Drop on its glint, walks into a lit circle through each Harmonize warning, and in a
 * Break pounds the nearest fallen mask. Every input that must meet a beat is timed by the integrated server's tick.
 * When low it drinks (an instant health effect by command, counted). Logs the fight's length, each voice's share,
 * the uptime and the answers.
 */
final class UnsungFightBot {
    /** The fight's longest allowed length, in ticks (10 minutes). */
    private static final int TIMEOUT = 12_000;
    private static final int SWING_EVERY = 10;

    private final UnsungScenario scenario;
    private final List<String> summary;
    private final UnsungMechanics hands;

    private int tick;
    private int nextSwing;
    private final List<KeyMapping> release = new ArrayList<>();
    private long fightStart = -1;
    private long killAt = -1;
    private final Map<Integer, Long> livingSince = new java.util.HashMap<>();
    private int fightTicks;
    private int swingTicks;
    private int heals;
    private int healedAt = -100;
    private int dashes;
    private int jumps;
    private int parryPresses;
    private int braces;
    private long dashedFor = Long.MIN_VALUE;
    private long jumpedFor = Long.MIN_VALUE;
    private long parriedFor = Long.MIN_VALUE;
    private long bracedFor = Long.MIN_VALUE;
    private final Map<Integer, Long> phaseStart = new java.util.TreeMap<>();

    UnsungFightBot(UnsungScenario scenario, List<String> summary) {
        this.scenario = scenario;
        this.summary = summary;
        this.hands = new UnsungMechanics(scenario, summary);
    }

    /** What the server says this tick. */
    private record Snap(long now, Unsung.State state, long fightStart, Voice singer, int broken, boolean breakOn, boolean warning, int[] lit,
                        Map<Voice, Vec3> faces, Map<Voice, Boolean> shattered, List<double[]> notes, long waveRelease, long rippleLand,
                        Vec3 rippleCentre, long dropLand, Vec3 dropPoint, float myHealth, float myMax, int breaks, int harmonizes,
                        int harmonizeHits, int parries, float total) {
    }

    void steps(Steps steps, Minecraft mc) {
        hands.listen();
        hands.equip(steps, mc);
        steps.run("onto the choir floor, 10 blocks out", () -> {
                    Vec3 at = UnsungScenario.fromLocal(10.5, 0.0, 0.0);
                    UnsungScenario.tp(mc, at.x, UnsungScenario.arena().floorY(), at.z, UnsungScenario.arena().x(), UnsungScenario.arena().floorY() + 3,
                            UnsungScenario.arena().z());
                })
                .waitUntil("it wakes", 100, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.INTRO))
                .waitUntil("the fight (played by script, at most 10 minutes)", TIMEOUT + 600, () -> tick(mc))
                .run("release every key", () -> releaseAll(mc))
                .log("fight", this::report)
                .run("note it", () -> summary.add(report()))
                .check("the Unsung fell", () -> killAt > 0);
    }

    private String report() {
        double total = (killAt - fightStart) / 20.0;
        StringBuilder phases = new StringBuilder();
        long prev = fightStart;
        int n = 3;
        for (Map.Entry<Integer, Long> e : phaseStart.entrySet()) {
            phases.append(String.format(Locale.ROOT, "%d voices %.0f s, ", n, (e.getValue() - prev) / 20.0));
            prev = e.getValue();
            n--;
        }
        phases.append(String.format(Locale.ROOT, "%d voice%s %.0f s", n, n == 1 ? "" : "s", (killAt - prev) / 20.0));
        double taken = 0;
        int[] byKind = new int[6];
        double[] dmgByKind = new double[6];
        for (double[] t : hands.taken) {
            taken += t[1];
            byKind[(int) t[0]]++;
            dmgByKind[(int) t[0]] += t[1];
        }
        StringBuilder hits = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            if (byKind[k] > 0) {
                hits.append(String.format(Locale.ROOT, "%s %d (%.0f) ", UnsungMechanics.KINDS[k], byKind[k], dmgByKind[k]));
            }
        }
        return String.format(Locale.ROOT, "scripted fight: %.0f s (%.1f min) from the first line to the kill (%s); uptime %.0f%% of the fight's ticks "
                        + "swinging; %d Break(s), %d parried Drop(s), %d Harmonize(s) (%d caught); %d dash(es) through notes, %d jump(s), %d brace(s), "
                        + "%d parry press(es); took %.0f damage (%s), %d heal(s)",
                total, total / 60.0, phases, 100.0 * swingTicks / Math.max(1, fightTicks), last.breaks(), last.parries(), last.harmonizes(),
                last.harmonizeHits(), dashes, jumps, braces, parryPresses, taken, hits.toString().trim(), heals);
    }

    private Snap last;

    private Snap snap() {
        return ServerQuery.ask(p -> {
            Unsung u = UnsungScenario.choirOrNull(p);
            if (u == null) {
                return null;
            }
            Map<Voice, Vec3> faces = new EnumMap<>(Voice.class);
            Map<Voice, Boolean> shattered = new EnumMap<>(Voice.class);
            u.masks().forEach((v, m) -> {
                faces.put(v, m.face());
                shattered.put(v, m.shattered());
            });
            long now = u.level().getGameTime();
            List<double[]> notes = new ArrayList<>();
            for (SongNote n : p.serverLevel().getEntitiesOfClass(SongNote.class, p.getBoundingBox().inflate(30.0))) {
                Vec3 c = n.centre();
                notes.add(new double[] {c.x, c.y, c.z, now >= n.flies() ? 1 : 0, n.lands() - now});
            }
            return new Snap(now, u.state(), u.fightStart(), u.singer(), u.brokenBits(), u.broken(now), u.warning(), u.litCircles(), faces, shattered,
                    notes, u.waveRelease(), u.rippleLand(), u.rippleCentre(), u.dropLand(), u.dropPoint(), p.getHealth(), p.getMaxHealth(), u.breaks(),
                    u.harmonizes(), u.harmonizeHits(), u.parries(), u.totalHealth());
        });
    }

    // ------------------------------------------------------------------ one tick of play

    private boolean tick(Minecraft mc) {
        tick++;
        for (KeyMapping key : release) {
            KeyMapping.set(key.getKey(), false);
        }
        release.clear();
        Snap s = snap();
        if (s == null) {
            if (fightStart >= 0 && killAt < 0) {
                killAt = UnsungScenario.serverTick(mc);
            }
            releaseAll(mc);
            return killAt > 0 || tick > TIMEOUT;
        }
        last = s;
        if (fightStart < 0 && s.state() == Unsung.State.FIGHT) {
            fightStart = s.fightStart();
        }
        int broken = Integer.bitCount(s.broken());
        if (broken > 0 && !phaseStart.containsKey(broken)) {
            phaseStart.put(broken, s.now());
        }
        if (s.state() == Unsung.State.DYING) {
            if (killAt < 0) {
                killAt = s.now();
            }
            releaseAll(mc);
            return false;
        }
        if (tick > TIMEOUT) {
            releaseAll(mc);
            return true;
        }
        if (s.state() != Unsung.State.FIGHT) {
            return false;
        }
        fightTicks++;
        heal(mc, s);
        fight(mc, s);
        return false;
    }

    private void fight(Minecraft mc, Snap s) {
        ChoirArena a = UnsungScenario.arena();
        Vec3 me = mc.player.position();
        // 1. Harmonize: into the nearest lit circle, and stay there
        if (s.warning() && s.lit().length > 0) {
            Vec3 best = null;
            for (int k : s.lit()) {
                Vec3 c = a.circleCentre(k);
                if (best == null || c.distanceToSqr(me) < best.distanceToSqr(me)) {
                    best = c;
                }
            }
            if (answerBeats(mc, s, me)) {
                return;
            }
            walkTo(mc, best, best.add(0, 2, 0), 0.6);
            return;
        }
        // 2. the answers on the beat
        if (answerBeats(mc, s, me)) {
            return;
        }
        // 3. a Break: the nearest fallen mask
        Voice target = s.singer();
        if (s.breakOn()) {
            double bestD = Double.MAX_VALUE;
            for (Map.Entry<Voice, Vec3> e : s.faces().entrySet()) {
                if (!s.shattered().get(e.getKey()) && e.getValue().distanceToSqr(me) < bestD) {
                    bestD = e.getValue().distanceToSqr(me);
                    target = e.getKey();
                }
            }
        }
        if (target == null || s.shattered().getOrDefault(target, true)) {
            idle(mc, a.centre().add(0, 2, 0));
            return;
        }
        Vec3 face = s.faces().get(target);
        Vec3 floorPt = new Vec3(face.x, a.floorY(), face.z);
        Vec3 out = UnsungScenario.outward(floorPt);
        Vec3 stand = floorPt.add(out.scale(s.breakOn() ? 1.6 : 1.9));
        Vec3 aim = new Vec3(face.x, Math.min(face.y - 1.4, a.floorY() + 2.3), face.z);
        if (walkTo(mc, stand, aim, 0.8)) {
            swing(mc, true);
        } else if (Math.hypot(stand.x - me.x, stand.z - me.z) < 2.4) {
            swing(mc, true); // close enough to reach while stepping
        }
    }

    /** The inputs that must land on a beat; true if one was pressed this tick. */
    private boolean answerBeats(Minecraft mc, Snap s, Vec3 me) {
        long now = UnsungScenario.serverTick(mc);
        // the Bass Drop: parry it on its glint when the ring is under me
        if (s.dropLand() > now && s.dropLand() - now <= 3 && parriedFor != s.dropLand()
                && Math.hypot(s.dropPoint().x - me.x, s.dropPoint().z - me.z) <= UnsungMoves.DROP_RADIUS + 0.6) {
            parriedFor = s.dropLand();
            press(mc, ModKeyMappings.PARRY);
            parryPresses++;
            return true;
        }
        // the Sweeping Wave: jump a tick before it leaves the dais
        if (s.waveRelease() > now && s.waveRelease() - now <= 1 && jumpedFor != s.waveRelease()) {
            jumpedFor = s.waveRelease();
            press(mc, mc.options.keyJump);
            jumps++;
            return true;
        }
        // Ground Ripples under me: crouch and stand firm
        if (s.rippleLand() > now && s.rippleLand() - now <= UnsungMoves.RIPPLE_TELL && bracedFor != s.rippleLand()
                && Math.hypot(s.rippleCentre().x - me.x, s.rippleCentre().z - me.z) <= UnsungMoves.RIPPLE_RADIUS + 0.6) {
            bracedFor = s.rippleLand();
            braces++;
        }
        if (bracedFor != Long.MIN_VALUE && now <= bracedFor + 1) {
            KeyMapping.set(mc.options.keyShift.getKey(), true);
            KeyMapping.set(mc.options.keyUp.getKey(), false);
            return true;
        }
        KeyMapping.set(mc.options.keyShift.getKey(), false);
        // a Homing Note about to burst on me: dash through it
        for (double[] n : s.notes()) {
            if (n[3] > 0 && n[4] <= 2 && n[4] >= 0 && Math.hypot(n[0] - me.x, n[2] - me.z) < 2.8) {
                long lands = now + (long) n[4];
                if (dashedFor != lands) {
                    dashedFor = lands;
                    UnsungScenario.lookAt(mc, new Vec3(n[0], mc.player.getEyeY(), n[2]));
                    hold(mc, mc.options.keyUp);
                    press(mc, ModKeyMappings.DASH);
                    dashes++;
                    return true;
                }
            }
        }
        return false;
    }

    private void heal(Minecraft mc, Snap s) {
        if (s.myHealth() < 0.4f * s.myMax() && tick - healedAt > 40) {
            healedAt = tick;
            heals++;
            mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 1 true");
        }
    }

    // ------------------------------------------------------------------ inputs

    private boolean walkTo(Minecraft mc, Vec3 goal, Vec3 look, double near) {
        Vec3 me = mc.player.position();
        double d = Math.hypot(goal.x - me.x, goal.z - me.z);
        if (d < near) {
            KeyMapping.set(mc.options.keyUp.getKey(), false);
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            UnsungScenario.lookAt(mc, look);
            return true;
        }
        UnsungScenario.lookAt(mc, new Vec3(goal.x, mc.player.getEyeY(), goal.z));
        KeyMapping.set(mc.options.keyUp.getKey(), true);
        KeyMapping.set(mc.options.keySprint.getKey(), d > 4.0);
        return false;
    }

    private void idle(Minecraft mc, Vec3 look) {
        KeyMapping.set(mc.options.keyUp.getKey(), false);
        UnsungScenario.lookAt(mc, look);
    }

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

    private void releaseAll(Minecraft mc) {
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keySprint, mc.options.keyAttack, mc.options.keyJump,
                mc.options.keyShift, ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            KeyMapping.set(key.getKey(), false);
        }
        release.clear();
    }

    @SuppressWarnings("unused")
    private UnsungMask unused() {
        return null;
    }
}
