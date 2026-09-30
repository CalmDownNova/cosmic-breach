package com.cosmicbreach.astrolabe;

import com.cosmicbreach.status.RiftStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Choir Astrolabe's pure rules (GDD 4.2): homing, the Constellation's marks, the Pocket Star's pulses and the
 * Supernova's scaling, the Singularity, the tunes; and the Rift status the Stalker's Rend applies (GDD 8.2).
 */
class AstrolabeRulesTest {
    private static final Vec3 ORIGIN = new Vec3(0, 65, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    private static Vec3 at(double yawDeg, double distance) {
        return ORIGIN.add(Homing.fan(SOUTH, yawDeg).scale(distance));
    }

    // ------------------------------------------------------------------ homing

    @Test
    void aBoltLocksOntoTheEnemyNearestItsHeadingWithinTenDegrees() {
        List<Vec3> targets = List.of(at(8, 10), at(3, 12), at(15, 6));
        assertEquals(1, Homing.acquire(ORIGIN, SOUTH, targets, Homing.CONE, Homing.RANGE), "3 degrees beats 8");
        assertEquals(-1, Homing.acquire(ORIGIN, SOUTH, List.of(at(12, 5)), Homing.CONE, Homing.RANGE), "12 is outside the cone");
        assertEquals(-1, Homing.acquire(ORIGIN, SOUTH, List.of(at(2, 30)), Homing.CONE, Homing.RANGE), "beyond 24 blocks");
        assertEquals(0, Homing.acquire(ORIGIN, SOUTH, List.of(at(5, 5), at(-5, 9)), Homing.CONE, Homing.RANGE), "a tie to the nearer");
    }

    @Test
    void itTurnsAtMostEightDegreesATickAndKeepsItsSpeed() {
        Vec3 v = SOUTH.scale(2.0);
        Vec3 turned = Homing.steer(v, Homing.fan(SOUTH, 30), Homing.TURN);
        assertEquals(8.0, Homing.angle(v, turned), 1e-6);
        assertEquals(2.0, turned.length(), 1e-9);
        Vec3 onto = Homing.steer(v, Homing.fan(SOUTH, 5), Homing.TURN);
        assertEquals(5.0, Homing.angle(v, onto), 1e-6, "within the turn it points straight at it");
        Vec3 back = Homing.steer(v, SOUTH.scale(-1), Homing.TURN);
        assertEquals(8.0, Homing.angle(v, back), 1e-6, "even a target straight behind turns it only 8");
    }

    @Test
    void aTriadsSideBoltsHomeOntoOneEnemyAhead() {
        // three bolts 7.5 degrees apart, an enemy 10 blocks straight ahead: every bolt reaches it
        Vec3 enemy = ORIGIN.add(SOUTH.scale(10));
        AABB box = new AABB(enemy.x - 0.3, enemy.y - 0.9, enemy.z - 0.3, enemy.x + 0.3, enemy.y + 0.9, enemy.z + 0.3);
        for (double fan : new double[] {-7.5, 0.0, 7.5}) {
            Vec3 pos = ORIGIN;
            Vec3 vel = Homing.fan(SOUTH, fan).scale(Homing.SPEED);
            boolean hit = false;
            for (int tick = 0; tick < 12 && !hit; tick++) {
                if (Homing.acquire(pos, vel, List.of(enemy), Homing.CONE, Homing.RANGE) == 0
                        || Homing.keeps(pos, vel, enemy, Homing.KEEP, Homing.RANGE)) {
                    vel = Homing.steer(vel, enemy.subtract(pos), Homing.TURN);
                }
                Vec3 next = pos.add(vel);
                hit = box.inflate(0.15).clip(pos, next).isPresent();
                pos = next;
            }
            assertTrue(hit, "the bolt fanned " + fan + " degrees hits");
        }
    }

    @Test
    void aBoltAimedWideOfEveryoneFliesStraight() {
        Vec3 enemy = at(25, 10);
        assertEquals(-1, Homing.acquire(ORIGIN, SOUTH, List.of(enemy), Homing.CONE, Homing.RANGE));
    }

    // ------------------------------------------------------------------ the Constellation

    private static Constellation.Candidate enemy(int id, double yawDeg, double distance) {
        Vec3 c = at(yawDeg, distance);
        return new Constellation.Candidate(id, new AABB(c.x - 0.3, c.y - 0.9, c.z - 0.3, c.x + 0.3, c.y + 0.9, c.z + 0.3));
    }

    @Test
    void sweepingTheAimMarksUpToFiveEnemiesOnceEach() {
        List<Constellation.Candidate> enemies = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            enemies.add(enemy(i, -45 + 15 * i, 10));
        }
        List<Integer> marked = new ArrayList<>();
        for (double yaw = -50; yaw <= 50; yaw += 1.0) {
            int id = Constellation.pick(ORIGIN, Homing.fan(SOUTH, yaw), enemies, marked, Constellation.RANGE, Constellation.CONE);
            if (id >= 0) {
                assertFalse(marked.contains(id));
                marked.add(id);
            }
        }
        assertEquals(List.of(0, 1, 2, 3, 4), marked, "the first five the sweep crossed, in order, then no more");
    }

    @Test
    void theCrosshairsRayWinsOverANearMiss() {
        List<Constellation.Candidate> enemies = List.of(enemy(1, 2, 20), enemy(2, 0, 14));
        assertEquals(2, Constellation.pick(ORIGIN, SOUTH, enemies, Set.of(), Constellation.RANGE, Constellation.CONE),
                "the one the ray passes through, even behind a near miss");
        assertEquals(-1, Constellation.pick(ORIGIN, SOUTH, List.of(enemy(3, 0, 30)), Set.of(), Constellation.RANGE,
                Constellation.CONE), "beyond 24 blocks");
    }

    @Test
    void theBeamStrikesOneTargetATickAlongTheChain() {
        assertEquals(0, Constellation.hitTick(0, 4));
        assertEquals(2, Constellation.hitTick(2, 4));
        assertEquals(3, Constellation.hitTick(3, 4));
        assertEquals(3, Constellation.hitTick(4, 4), "the fifth with the fourth on the last active tick");
    }

    // ------------------------------------------------------------------ the Pocket Star

    @Test
    void theStarPulsesEveryTenTicksThroughItsLife() {
        int pulses = 0;
        for (int age = 0; age <= 100; age++) {
            if (PocketStarRules.pulsesAt(age)) {
                pulses++;
            }
        }
        assertEquals(8, pulses, "10, 20, ... 80");
        assertFalse(PocketStarRules.pulsesAt(0));
        assertTrue(PocketStarRules.pulsesAt(80));
        assertFalse(PocketStarRules.pulsesAt(90));
    }

    @Test
    void theSupernovaIsStrongerTheEarlierItComes() {
        assertEquals(4.0, PocketStarRules.novaMv(0, 80, 1.0, 3.0, false), 1e-9, "at once: 1 + 3");
        assertEquals(2.5, PocketStarRules.novaMv(40, 80, 1.0, 3.0, false), 1e-9, "half way: 1 + 1.5");
        assertEquals(1.75, PocketStarRules.novaMv(60, 80, 1.0, 3.0, false), 1e-9);
        assertEquals(1.0, PocketStarRules.novaMv(80, 80, 1.0, 3.0, false), 1e-9, "burned out: 1");
        assertEquals(3.75, PocketStarRules.novaMv(40, 80, 1.0, 3.0, true), 1e-9, "a Singularity: +50%");
    }

    @Test
    void theSingularityDoublesTheWellsPull() {
        Vec3 well = new Vec3(10, 64, 10);
        assertTrue(PocketStarRules.inside(new Vec3(12, 65, 13), well, 6.0));
        assertFalse(PocketStarRules.inside(new Vec3(17, 64, 10), well, 6.0));
        assertEquals(2.0, PocketStarRules.pullScale(true));
        assertEquals(1.0, PocketStarRules.pullScale(false));
    }

    @Test
    void theStarGoesWhereYouAimUpToSixteenBlocks() {
        Vec3 look = SOUTH;
        assertEquals(ORIGIN.add(0, 0, 16), PocketStarRules.placement(ORIGIN, look, null, 16));
        Vec3 wall = ORIGIN.add(0, 0, 9);
        assertEquals(8.2, PocketStarRules.placement(ORIGIN, look, wall, 16).distanceTo(ORIGIN), 1e-9, "just short of the wall");
        assertEquals(5, PocketStarRules.groundLights().length);
    }

    // ------------------------------------------------------------------ tunes

    @Test
    void everyNoteIsPentatonicInDAndPlayable() {
        for (int bar = 0; bar < AstrolabeTunes.BARS_IN_PHRASE; bar++) {
            List<Integer> notes = new ArrayList<>(List.of(AstrolabeTunes.first(bar), AstrolabeTunes.second(bar),
                    AstrolabeTunes.starfall(bar)));
            for (int n : AstrolabeTunes.chord(bar)) {
                notes.add(n);
            }
            for (int n : notes) {
                assertTrue(AstrolabeTunes.inScale(n), "bar " + bar + " note " + n);
                float p = AstrolabeTunes.pitch(n);
                assertTrue(p >= 0.5f && p <= 2.0f);
                assertEquals(Math.pow(2, n / 12.0), p, 1e-5, "exactly that note, not clamped");
            }
            assertEquals(3, AstrolabeTunes.chord(bar).length, "a three-note chord");
        }
        List<Integer> more = new ArrayList<>();
        for (int n : AstrolabeTunes.PARALLAX) {
            more.add(n);
        }
        for (int i = 0; i < 5; i++) {
            more.add(AstrolabeTunes.arpeggio(i));
        }
        for (int n : more) {
            assertTrue(AstrolabeTunes.inScale(n));
            assertEquals(Math.pow(2, n / 12.0), AstrolabeTunes.pitch(n), 1e-5, "note " + n + " not clamped");
        }
        assertFalse(AstrolabeTunes.inScale(1), "E flat is not in it");
    }

    // ------------------------------------------------------------------ Rift

    @Test
    void riftStacksToThreeAtFifteenPercentEach() {
        assertEquals(1, RiftStacks.after(0));
        assertEquals(3, RiftStacks.after(2));
        assertEquals(3, RiftStacks.after(3), "no more than 3");
        assertEquals(0.85, RiftStacks.armorScale(1), 1e-9);
        assertEquals(0.55, RiftStacks.armorScale(3), 1e-9);
        assertEquals(100, RiftStacks.TICKS);
    }
}
