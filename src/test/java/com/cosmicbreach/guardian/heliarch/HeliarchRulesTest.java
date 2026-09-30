package com.cosmicbreach.guardian.heliarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.GuardianHealth;
import com.cosmicbreach.guardian.heliarch.HeliarchPicker.Attack;
import com.cosmicbreach.structure.sanctum.SanctumArena.Ring;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The Hollow Heliarch's rules (GDD 7.3), each number checked against the design. */
class HeliarchRulesTest {
    private static AABB playerAt(double x, double z) {
        return new AABB(x - 0.3, HeliarchArena.FLOOR, z - 0.3, x + 0.3, HeliarchArena.FLOOR + 1.8, z + 0.3);
    }

    // ------------------------------------------------------------------ health

    @Test
    void healthScalesByPlayersCountedAtTheSummonCappedAtFour() {
        assertEquals(1200.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 1), 1e-9);
        assertEquals(1920.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 2), 1e-9);
        assertEquals(2640.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 3), 1e-9);
        assertEquals(3360.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 4), 1e-9);
        assertEquals(3360.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 7), 1e-9, "capped at four");
        assertEquals(1200.0, GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, 0), 1e-9, "at least one");
        assertEquals(10.0, HeliarchMoves.ARMOR);
        assertEquals(6.0, HeliarchMoves.TOUGHNESS);
    }

    // ------------------------------------------------------------------ the picker

    @Test
    void thePickerHonoursCooldownsAndNeverRepeats() {
        HeliarchPicker p = new HeliarchPicker();
        p.start(0);
        Random random = new Random(5);
        HeliarchPicker.Context ctx = new HeliarchPicker.Context(1, 0, 12.0, false);
        Attack last = null;
        Map<Attack, Long> lastUse = new EnumMap<>(Attack.class);
        int made = 0;
        for (long now = 1; now < 20_000; now += 7) {
            Attack a = p.next(ctx, now, random::nextDouble);
            if (a == null) {
                continue;
            }
            made++;
            if (a != Attack.HALO_SHED) {
                assertNotEquals(last, a, "never the same attack twice in a row");
            }
            Long before = lastUse.get(a);
            if (before != null) {
                long gap = now - before;
                long cd = switch (a) {
                    case SUNDERFALL -> HeliarchMoves.SUNDER_COOLDOWN;
                    case SOLAR_LANCE -> HeliarchMoves.LANCE_COOLDOWN;
                    case CORONA_SWEEP -> HeliarchMoves.SWEEP_COOLDOWN;
                    case HALO_SHED -> HeliarchMoves.SHED_EVERY;
                    case CORONA_FLARE -> HeliarchMoves.FLARE_COOLDOWN;
                };
                assertTrue(gap >= cd, a + " came back after " + gap + " ticks, its cooldown is " + cd);
            }
            lastUse.put(a, now);
            last = a;
        }
        assertTrue(made > 100);
        assertTrue(lastUse.containsKey(Attack.HALO_SHED));
        assertFalse(lastUse.containsKey(Attack.CORONA_SWEEP), "no one in the band: no sweep");
    }

    @Test
    void theShedGoesFirstEveryThirtySeconds() {
        HeliarchPicker p = new HeliarchPicker();
        p.start(1000);
        HeliarchPicker.Context ctx = new HeliarchPicker.Context(2, 2, 12.0, false);
        assertNotEquals(Attack.HALO_SHED, p.next(ctx, 1000, () -> 0.1));
        assertFalse(p.ready(Attack.HALO_SHED, 1599));
        assertTrue(p.ready(Attack.HALO_SHED, 1600));
        assertEquals(Attack.HALO_SHED, p.next(ctx, 1600, () -> 0.1), "priority when due");
        assertFalse(p.ready(Attack.HALO_SHED, 2199));
        assertTrue(p.ready(Attack.HALO_SHED, 2200));
    }

    @Test
    void theSweepWantsAnyoneInTheBandAndTheLanceWantsMidRangeOrALonePlayer() {
        assertTrue(HeliarchPicker.sweepWanted(1, 1), "the one player in the band");
        assertTrue(HeliarchPicker.sweepWanted(3, 1), "anyone in the band (G9c)");
        assertFalse(HeliarchPicker.sweepWanted(3, 0));
        assertTrue(HeliarchPicker.lanceWanted(1, 3.0), "alone, the lance at any range (G9c)");
        assertTrue(HeliarchPicker.lanceWanted(2, 12.0));
        assertFalse(HeliarchPicker.lanceWanted(2, 3.0), "two or more: no lance at point blank");
        assertTrue(HeliarchPicker.options(new HeliarchPicker.Context(2, 2, 3, false)).stream().anyMatch(o -> o.attack() == Attack.CORONA_SWEEP));
        assertEquals(100, HeliarchMoves.SUNDER_COOLDOWN, "Sunderfall 5 s");
        assertEquals(160, HeliarchMoves.LANCE_COOLDOWN, "Solar Lance 8 s");
        assertEquals(240, HeliarchMoves.SWEEP_COOLDOWN, "Corona Sweep 12 s");
        assertEquals(200, HeliarchMoves.FLARE_COOLDOWN, "Corona Flare 10 s");
    }

    @Test
    void aLonePlayerHuggingTheCoreMeetsTheLanceAndTheFlare() {
        HeliarchPicker.Context hugging = new HeliarchPicker.Context(1, 0, 3.0, true);
        List<Attack> offered = HeliarchPicker.options(hugging).stream().map(o -> o.attack()).toList();
        assertTrue(offered.contains(Attack.SOLAR_LANCE), "alone, the Lance at point blank");
        assertTrue(offered.contains(Attack.CORONA_FLARE), "three seconds of hugging calls the Flare");
        assertTrue(offered.contains(Attack.SUNDERFALL));
        assertFalse(HeliarchPicker.options(new HeliarchPicker.Context(1, 0, 3.0, false)).stream().anyMatch(o -> o.attack() == Attack.CORONA_FLARE),
                "no Flare before the hug has lasted");
        HeliarchPicker p = new HeliarchPicker();
        p.start(0);
        assertEquals(Attack.CORONA_FLARE, p.next(hugging, 0, () -> 0.5), "the push-out goes first once it's called");
        Map<Attack, Integer> seen = new EnumMap<>(Attack.class);
        for (long now = 7; now < 1200; now += 7) {
            Attack a = p.next(hugging, now, () -> 0.5);
            if (a != null) {
                seen.merge(a, 1, Integer::sum);
            }
        }
        assertTrue(seen.getOrDefault(Attack.SOLAR_LANCE, 0) >= 3, "the Lance keeps coming: " + seen);
        assertTrue(seen.getOrDefault(Attack.SUNDERFALL, 0) >= 3, "and Sunderfall: " + seen);
        assertTrue(seen.getOrDefault(Attack.CORONA_FLARE, 0) >= 4, "the Flare every 10 s: " + seen);
        assertEquals(1, seen.getOrDefault(Attack.HALO_SHED, 0), "the shed at 30 s: " + seen);
        // a group hugging the throne before anyone has hugged 3 s: only Sunderfall, so it may repeat
        HeliarchPicker q = new HeliarchPicker();
        q.start(0);
        HeliarchPicker.Context group = new HeliarchPicker.Context(2, 0, 3.0, false);
        assertEquals(Attack.SUNDERFALL, q.next(group, 0, () -> 0.5));
        assertEquals(Attack.SUNDERFALL, q.next(group, HeliarchMoves.SUNDER_COOLDOWN, () -> 0.5), "its only choice, so it may repeat");
    }

    @Test
    void theFlareCatchesWhoHugsItAndThrowsThemOut() {
        Vec3 near = HeliarchArena.at(90, 3.0);
        Vec3 edge = HeliarchArena.at(10, 6.2);
        Vec3 beyond = HeliarchArena.at(10, 6.5);
        assertTrue(CoronaFlare.inside(near.x, near.z));
        assertTrue(CoronaFlare.inside(edge.x, edge.z), "a player's edge counts");
        assertFalse(CoronaFlare.inside(beyond.x, beyond.z));
        assertTrue(CoronaFlare.strikes(near.x, near.z, 1.0), "a jump doesn't clear it");
        assertFalse(CoronaFlare.strikes(near.x, near.z, 9.0));
        int t = 0;
        for (int i = 0; i < HeliarchMoves.FLARE_HUG - 1; i++) {
            t = CoronaFlare.hug(t, true);
        }
        assertFalse(CoronaFlare.hugged(t), "59 ticks");
        t = CoronaFlare.hug(t, true);
        assertTrue(CoronaFlare.hugged(t), "60 ticks");
        assertEquals(0, CoronaFlare.hug(t, false), "stepping out starts it again");
        Vec3 out = CoronaFlare.outward(near.x, near.z);
        assertEquals(1.0, out.length(), 1e-9);
        assertTrue(out.dot(HeliarchArena.dir(90)) > 0.999, "straight out from the throne");
        assertEquals(1.0, CoronaFlare.outward(HeliarchArena.CX, HeliarchArena.CZ).length(), 1e-9, "from dead centre, somewhere");
        assertTrue(HeliarchMoves.FLARE_TELL >= HeliarchMoves.MIN_TELL);
        assertEquals(12.0, HeliarchMoves.FLARE_DAMAGE);
        assertEquals(2.0, HeliarchMoves.FLARE_KNOCKBACK);
    }

    @Test
    void phaseTwosEclipseHangsOverThePillarsUntilItsHeartIsLaidBare() {
        long m = Long.MIN_VALUE;
        HeliarchPose.Input high = new HeliarchPose.Input(HollowHeliarch.State.HOLLOW, 0, HollowHeliarch.Action.NONE, 0, Vec3.ZERO, 0f, true, 0,
                m, m, m, false, false, HollowHeliarch.Action.NONE, 0);
        assertEquals(HeliarchArena.HIGH_HEIGHT, HeliarchPose.coreHeight(high, 500), 0.2);
        assertTrue(HeliarchPose.coreHeight(high, 500) > 14.0 + 2.0, "over the pillars' capitals (14 over the floor)");
        assertEquals(HeliarchArena.ECLIPSE_BIG, HeliarchPose.eclipseScale(high, 500), 1e-9, "two and a half times the sun it was");
        HeliarchPose.Input broken = new HeliarchPose.Input(HollowHeliarch.State.HOLLOW, 0, HollowHeliarch.Action.NONE, 0, Vec3.ZERO, 0f, true, 0,
                480, m, m, false, false, HollowHeliarch.Action.NONE, 0);
        assertEquals(HeliarchArena.LOW_HEIGHT, HeliarchPose.coreHeight(broken, 500), 0.2, "a Break drags it down in reach");
        assertEquals(1.0, HeliarchPose.eclipseScale(broken, 500), 1e-9, "and shrinks it to its heart");
        HeliarchPose.Input stunned = new HeliarchPose.Input(HollowHeliarch.State.COLLAPSE, 0, HollowHeliarch.Action.NONE, 0, Vec3.ZERO, 0f, true,
                0, m, 500 + HeliarchMoves.NOVA_STUN - 60, m, false, false, HollowHeliarch.Action.NONE, 0);
        assertEquals(HeliarchArena.LOW_HEIGHT, HeliarchPose.coreHeight(stunned, 500), 0.2, "Nova's stun too");
        HeliarchPose.Input channel = new HeliarchPose.Input(HollowHeliarch.State.HOLLOW, 0, HollowHeliarch.Action.NOVA, 400, Vec3.ZERO, 0f, true,
                0, m, m, 400, false, false, HollowHeliarch.Action.NONE, 0);
        assertEquals(HeliarchArena.HIGH_HEIGHT, HeliarchPose.coreHeight(channel, 500), 0.2, "through the channel it stays high: its tendrils feed it");
        assertEquals(1.0, HeliarchMoves.TENDRIL_SHARE, "a blow on a tendril reaches the eclipse");
    }

    @Test
    void novaGathersItsRootsSoItsShieldCanAlwaysBeStruck() {
        TendrilRules.Tendril t = TendrilRules.create(0)[0];
        assertTrue(t.hit(HeliarchMoves.TENDRIL_HEALTH, 100), "cut");
        assertTrue(t.silenced(150));
        t.regrow();
        assertFalse(t.silenced(150), "Nova draws it back up");
        assertEquals(HeliarchMoves.TENDRIL_HEALTH, t.health(), 1e-9, "whole");
    }

    @Test
    void theClosedCrownTurnsItsBladesSoTheCoreShowsBetweenThem() {
        for (int k = 0; k < 6; k++) {
            Vec3 ray = HeliarchPose.ray(k, 100, 0.0);
            Vec3 face = HeliarchPose.face(k, 100, 0.0);
            assertEquals(0.0, ray.dot(face), 1e-9, "the face stays square to the blade");
            assertEquals(1.0, face.length(), 1e-9);
            double lam = Math.toRadians(HeliarchPose.CROWN_CLOSED_LEAN);
            Vec3 square = HeliarchArena.dir(HeliarchPose.crownAngle(k, 100)).scale(Math.cos(lam)).subtract(0, Math.sin(lam), 0);
            assertEquals(HeliarchPose.CROWN_CLOSED_TWIST, Math.toDegrees(Math.acos(Math.min(1.0, face.dot(square)))), 1e-6, "turned fan-wise");
        }
        double lam = Math.toRadians(HeliarchPose.CROWN_OPEN_LEAN);
        Vec3 open = HeliarchPose.face(2, 100, 1.0);
        Vec3 square = HeliarchArena.dir(HeliarchPose.crownAngle(2, 100)).scale(Math.cos(lam)).subtract(0, Math.sin(lam), 0);
        assertEquals(1.0, open.dot(square), 1e-9, "open, the blades face straight out");
    }

    @Test
    void theSweepIsJumpedOrLeft() {
        double start = 90.0;
        // a player in the band, due east: the wall starts on them
        Vec3 east = HeliarchArena.at(90.0, 13.0);
        assertTrue(CoronaSweep.strikes(east.x, east.z, 0.0, start, -1, 0));
        assertFalse(CoronaSweep.strikes(east.x, east.z, 1.0, start, -1, 0), "a jump clears it");
        // just past due south: a quarter of the way round (the wall runs clockwise, 18 degrees a tick)
        Vec3 south = HeliarchArena.at(181.0, 13.0);
        assertFalse(CoronaSweep.strikes(south.x, south.z, 0.0, start, -1, 5));
        assertTrue(CoronaSweep.strikes(south.x, south.z, 0.0, start, 5, 6));
        // inside 10 or outside 16 is safe
        Vec3 in = HeliarchArena.at(180.0, 9.0);
        Vec3 out = HeliarchArena.at(180.0, 17.0);
        assertFalse(CoronaSweep.strikes(in.x, in.z, 0.0, start, -1, HeliarchMoves.SWEEP_TICKS));
        assertFalse(CoronaSweep.strikes(out.x, out.z, 0.0, start, -1, HeliarchMoves.SWEEP_TICKS));
        // every angle is passed exactly once in the whole sweep
        for (int a = 0; a < 360; a += 7) {
            int passes = 0;
            for (int t = 0; t <= HeliarchMoves.SWEEP_TICKS; t++) {
                if (CoronaSweep.passes(a, start, t - 1, t)) {
                    passes++;
                }
            }
            assertEquals(1, passes, "angle " + a);
        }
    }

    // ------------------------------------------------------------------ Halo Shed

    @Test
    void haloShedTimingAndItsExposedCore() {
        assertEquals(20, HaloShed.FLASH, "20 ticks of flashing plates");
        assertEquals(16, HaloShed.TRAILS, "16 ticks of red trails");
        assertFalse(HaloShed.exposed(19));
        assertTrue(HaloShed.exposed(20));
        assertTrue(HaloShed.exposed(115));
        assertFalse(HaloShed.exposed(116));
        int exposed = 0;
        for (int t = 0; t < HaloShed.TOTAL; t++) {
            exposed += HaloShed.exposed(t) ? 1 : 0;
        }
        assertTrue(exposed >= 90 && exposed <= 100, "out about 5 s: " + exposed);
        assertEquals(1.25, HeliarchMoves.SHED_EXPOSED);
        // trails show for exactly 16 ticks before the plates come back
        assertFalse(HaloShed.trails(HaloShed.RETURN_START - HaloShed.TRAILS - 1));
        assertTrue(HaloShed.trails(HaloShed.RETURN_START - HaloShed.TRAILS));
        assertFalse(HaloShed.returning(HaloShed.RETURN_START - 1));
        assertTrue(HaloShed.returning(HaloShed.RETURN_START));
    }

    @Test
    void thePlatesHangAtTheRimAndComeBackAlongTheirTrails() {
        Vec3 slot = HeliarchArena.core(HeliarchArena.CORE_HEIGHT + 2.0);
        for (int k = 0; k < 6; k++) {
            Vec3 hang = HaloShed.position(0.0, k, 60, slot);
            assertEquals(HaloShed.RIM_R, HeliarchArena.radiusOf(hang.x, hang.z), 1e-6);
            // over heads on the way out
            Vec3 mid = HaloShed.position(0.0, k, HaloShed.FLASH + HaloShed.OUT / 2.0, slot);
            assertTrue(mid.y - HeliarchArena.FLOOR > 4.0, "overhead on the way out");
            // the way back is the trail
            Vec3[] line = HaloShed.returnLine(0.0, k);
            Vec3 a = HaloShed.position(0.0, k, HaloShed.RETURN_START, slot);
            assertTrue(a.distanceTo(line[0]) < 1e-6);
            Vec3 half = HaloShed.position(0.0, k, HaloShed.RETURN_START + HaloShed.RETURN / 2.0, slot);
            assertEquals(HaloShed.LOW, half.y - HeliarchArena.FLOOR, 1e-6, "chest high");
            assertEquals(HaloShed.returnAngle(0.0, k), HeliarchArena.angleOf(half.x, half.z), 1e-6);
            assertTrue(HaloShed.position(0.0, k, HaloShed.TOTAL, slot).distanceTo(slot) < 1e-6, "back in the halo");
        }
        // a player on a trail is struck; between two trails is safe
        Vec3 on = HeliarchArena.at(HaloShed.returnAngle(0.0, 2), 15.0);
        Vec3 between = HeliarchArena.at(HaloShed.returnAngle(0.0, 2) + 30.0, 15.0);
        boolean hitOn = false;
        boolean hitBetween = false;
        Vec3 prev = HaloShed.position(0.0, 2, HaloShed.RETURN_START, slot);
        for (int t = HaloShed.RETURN_START + 1; t <= HaloShed.RETURN_END; t++) {
            Vec3 now = HaloShed.position(0.0, 2, t, slot);
            hitOn |= HaloShed.strikes(playerAt(on.x, on.z), prev, now);
            hitBetween |= HaloShed.strikes(playerAt(between.x, between.z), prev, now);
            prev = now;
        }
        assertTrue(hitOn);
        assertFalse(hitBetween);
    }

    // ------------------------------------------------------------------ the Break gauge

    @Test
    void theBreakGaugeHoldsFourHundredPerPhase() {
        PhaseGauge g = new PhaseGauge();
        g.phase(1);
        assertEquals(60.0, 2 * HeliarchMoves.SUNDER_IMPACT, "a parried Sunderfall gives the gauge 60");
        long now = 100;
        for (int parry = 0; parry < 6; parry++) {
            assertFalse(g.add(now, 2 * HeliarchMoves.SUNDER_IMPACT));
            now += 20;
        }
        assertEquals(0.9, g.fraction(now), 1e-9);
        assertTrue(g.add(now, 40), "400 Breaks it");
        assertTrue(g.broken(now));
        assertEquals(1.5, g.taken(now));
        assertFalse(g.add(now + 5, 400), "nothing builds during a Break");
        assertTrue(g.broken(now + HeliarchMoves.BREAK_TICKS - 1));
        assertFalse(g.broken(now + HeliarchMoves.BREAK_TICKS), "100 ticks");
        assertEquals(1.0, g.taken(now + HeliarchMoves.BREAK_TICKS));
        assertEquals(0.0, g.fraction(now + HeliarchMoves.BREAK_TICKS), 1e-9, "a Break empties it");
        // Impact drains out after the window
        g.add(now + 200, 300);
        assertEquals(0.75, g.fraction(now + 200), 1e-9);
        assertEquals(0.0, g.fraction(now + 200 + HeliarchMoves.GAUGE_WINDOW), 1e-9);
        // a new phase empties it
        g.add(now + 1000, 300);
        g.phase(2);
        assertEquals(0.0, g.fraction(now + 1000), 1e-9);
        assertEquals(1, g.breaks());
    }

    // ------------------------------------------------------------------ cover and the monoliths

    @Test
    void theMonolithsStandInTheMidRingThreeWideAwayFromTheTendrils() {
        assertEquals(6, HeliarchArena.monoliths().size());
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            for (int[] c : m.columns()) {
                assertEquals(1, SanctumLayout.ring(c[0], c[1]), "monolith " + m.index() + " in the mid ring");
                assertEquals(-1, SanctumLayout.pillarAt(c[0], c[1]));
            }
            double[] r = m.rect();
            assertEquals(3.0, Math.max(r[2] - r[0], r[3] - r[1]), 1e-9);
            assertEquals(1.0, Math.min(r[2] - r[0], r[3] - r[1]), 1e-9);
            for (int i = 0; i < 4; i++) {
                Vec3 a = HeliarchArena.tendrilAnchor(i);
                assertTrue(a.distanceTo(m.middle()) > 5.0, "tendril " + i + " clear of monolith " + m.index());
            }
        }
        for (int i = 0; i < 4; i++) {
            Vec3 a = HeliarchArena.tendrilAnchor(i);
            assertEquals(0, SanctumLayout.ring((int) Math.floor(a.x), (int) Math.floor(a.z)), "tendrils rise from the dais, which never falls");
        }
    }

    @Test
    void aMonolithHidesWhoeverStandsBehindItUntilItShatters() {
        int[] pips = {3, 3, 3, 3, 3, 3};
        List<EclipseCover.Blocker> blockers = EclipseCover.blockers(pips);
        Vec3 behind = HeliarchArena.monoliths().get(0).middle().add(0, 0, -2.5);
        Vec3 open = HeliarchArena.at(30.0, 14.0);
        Vec3 inFront = HeliarchArena.monoliths().get(0).middle().add(0, 0, 2.0);
        assertTrue(EclipseCover.covered(behind.x, behind.z, blockers));
        assertFalse(EclipseCover.covered(open.x, open.z, blockers));
        assertFalse(EclipseCover.covered(inFront.x, inFront.z, blockers), "on the core's side of it");
        pips[0] = 0;
        assertFalse(EclipseCover.covered(behind.x, behind.z, EclipseCover.blockers(pips)), "a shattered monolith hides no one");
        // the pillars block sight too
        double[] c = HeliarchArena.pillarCircle(3);
        Vec3 behindPillar = new Vec3(c[0], 0, c[1]).add(HeliarchArena.dir(HeliarchArena.angleOf(c[0], c[1])).scale(4.0));
        assertTrue(EclipseCover.covered(behindPillar.x, behindPillar.z, blockers));
    }

    @Test
    void eachBeamTakesOnePipFromEveryMonolithItPasses() {
        for (int dir : new int[] {1, -1}) {
            double start = 10.0;
            Set<Integer> reached = new HashSet<>();
            int t0 = -1;
            for (int t = 0; t <= HeliarchMoves.BEAM_SWEEP; t++) {
                for (int m : EclipseCover.monolithsReached(start, dir, t0, t)) {
                    assertTrue(reached.add(m), "monolith " + m + " reached twice by one beam");
                }
                t0 = t;
            }
            assertTrue(reached.size() >= 3 && reached.size() <= 4, "a half turn passes three or four of six: " + reached);
        }
        // the beam's middle crosses 180 degrees in 60 ticks
        assertEquals(10.0, EclipseCover.beamAngle(10.0, 1, 0), 1e-9);
        assertEquals(190.0, EclipseCover.beamAngle(10.0, 1, HeliarchMoves.BEAM_SWEEP), 1e-9);
        assertEquals(190.0, EclipseCover.beamAngle(10.0, -1, HeliarchMoves.BEAM_SWEEP), 1e-9);
        // a player in the open in the wedge burns, behind a monolith doesn't
        int[] pips = {3, 3, 3, 3, 3, 3};
        Vec3 behind = HeliarchArena.monoliths().get(1).middle().add(HeliarchArena.dir(60.0).scale(2.5));
        double t = HeliarchMoves.BEAM_SWEEP * (60.0 - 10.0) / HeliarchMoves.BEAM_ARC;
        assertTrue(EclipseCover.inWedge(behind.x, behind.z, 10.0, 1, t));
        assertFalse(EclipseCover.beamHits(behind.x, behind.z, 10.0, 1, t, EclipseCover.blockers(pips)));
        Vec3 exposed = HeliarchArena.at(60.0, 6.0);
        assertTrue(EclipseCover.beamHits(exposed.x, exposed.z, 10.0, 1, t, EclipseCover.blockers(pips)));
        // cover runs out: eighteen pips at three or four a beam last five or six beams, 100 to 120 s at one every 20 s
        int pips18 = HeliarchArena.MONOLITHS * HeliarchArena.PIPS;
        assertEquals(18, pips18);
        assertEquals(400, HeliarchMoves.BEAM_COOLDOWN, "a beam every 20 s");
        assertTrue(Math.ceil(pips18 / 4.0) * HeliarchMoves.BEAM_COOLDOWN / 20.0 >= 100.0);
        // a beam aimed at a target passes it in its middle third
        for (int d : new int[] {1, -1}) {
            double s = EclipseCover.startFor(200.0, d, 0.0);
            assertEquals(200.0, EclipseCover.beamAngle(s, d, HeliarchMoves.BEAM_SWEEP / 2.0), 1e-9);
        }
    }

    // ------------------------------------------------------------------ Nova

    @Test
    void novasShieldIsScaledLikeHealthAndBreakingItStunsTheHeliarch() {
        assertEquals(250.0, NovaRules.shieldFor(1), 1e-9);
        assertEquals(700.0, NovaRules.shieldFor(4), 1e-9);
        NovaRules n = new NovaRules();
        assertTrue(n.due(0, 0.4));
        assertFalse(n.due(0, 0.41));
        n.begin(1000, 1);
        assertFalse(n.due(1001, 0.4), "one channel at a time");
        n.absorb(125);
        assertEquals(0.5, n.shieldFraction(), 1e-9);
        assertEquals(NovaRules.Outcome.CHANNELLING, n.tick(1200));
        n.absorb(200);
        assertEquals(NovaRules.Outcome.BROKEN, n.tick(1201));
        assertTrue(n.stunned(1201));
        assertTrue(n.stunned(1201 + HeliarchMoves.NOVA_STUN - 1));
        assertFalse(n.stunned(1201 + HeliarchMoves.NOVA_STUN), "stunned 160 ticks");
        assertEquals(1.5, n.taken(1250), "heart exposed x1.5");
        assertTrue(n.broken());
        assertFalse(n.due(5000, 0.4), "a broken Nova never comes back");
        // 12.5 damage a second for 20 s breaks it alone
        assertEquals(12.5, HeliarchMoves.SHIELD_BASE / (HeliarchMoves.NOVA_CHANNEL / 20.0), 1e-9);
    }

    @Test
    void aFailedNovaDetonatesAndComesBackThirtySecondsLater() {
        NovaRules n = new NovaRules();
        n.begin(0, 2);
        n.absorb(100);
        assertEquals(NovaRules.Outcome.CHANNELLING, n.tick(399));
        assertEquals(NovaRules.Outcome.DETONATED, n.tick(400));
        assertEquals(60.0, NovaRules.detonation(false), 1e-9);
        assertEquals(24.0, NovaRules.detonation(true), 1e-9, "60% less behind a monolith");
        assertFalse(n.due(999, 0.4));
        assertTrue(n.due(1000, 0.4), "back 30 s later");
        assertEquals(1, n.detonations());
        n.begin(1000, 2);
        assertEquals(1.0, n.shieldFraction(), 1e-9, "a new channel has a whole shield");
    }

    // ------------------------------------------------------------------ the Collapse

    @Test
    void theCollapseDropsTheRimAndOuterRingInTwoMinutesAndTheMidRingInFour() {
        CollapseSchedule north = new CollapseSchedule(-1);
        assertEquals(1, north.first(), "north halls: the stair's segments 7 and 0 go last");
        assertEquals(5, new CollapseSchedule(1).first(), "south halls: the stair's 3 and 4 go last");
        assertEquals(24, north.cracks().size());
        List<CollapseSchedule.Crack> rim = new ArrayList<>();
        for (CollapseSchedule.Crack c : north.cracks()) {
            assertEquals(HeliarchMoves.SHAKE_TICKS, c.fall() - c.crack(), "every segment shakes 100 ticks");
            if (c.ring() == Ring.RIM) {
                rim.add(c);
            }
        }
        for (int i = 0; i < rim.size(); i++) {
            assertEquals(i * 300, rim.get(i).crack(), "a rim segment every 15 s");
            assertEquals((1 + i) % 8, rim.get(i).segment(), "clockwise");
        }
        assertEquals(Ring.RIM, north.cracks().get(0).ring());
        assertEquals(28.5, north.radiusLeft(0), 1e-9);
        assertEquals(24.5, north.radiusLeft(100), 1e-9);
        assertEquals(CollapseSchedule.State.SHAKING, north.state(Ring.RIM, 1, 50));
        assertEquals(CollapseSchedule.State.FALLEN, north.state(Ring.RIM, 1, 100));
        assertEquals(CollapseSchedule.State.STANDING, north.state(Ring.RIM, 2, 100));
        assertEquals(16.5, north.radiusLeft(2400), 1e-9, "two minutes in, the arena ends at radius 16");
        for (int s = 0; s < 8; s++) {
            assertEquals(CollapseSchedule.State.FALLEN, north.state(Ring.OUTER, s, 2400));
            assertEquals(CollapseSchedule.State.STANDING, north.state(Ring.MID, s, 2399));
        }
        assertEquals(8.5, north.radiusLeft(4800), 1e-9, "four minutes in, only the dais is left");
        assertTrue(north.done(4800));
        assertEquals(CollapseSchedule.State.STANDING, north.state(Ring.DAIS, 0, 100_000));
        // the stair's two segments are the last of the rim
        assertEquals(7, rim.get(6).segment());
        assertEquals(0, rim.get(7).segment());
        assertEquals(1, north.falling(99, 100).size());
    }

    @Test
    void noWarningIsEverUnderTwelveTicks() {
        for (int t : HeliarchMoves.tells()) {
            assertTrue(t >= HeliarchMoves.MIN_TELL, "a warning of " + t + " ticks");
        }
        assertEquals(12, HeliarchMoves.tell(3));
        assertEquals(40, HeliarchMoves.tell(40));
    }

    // ------------------------------------------------------------------ soft enrage

    @Test
    void theSoftEnrageAddsTenPercentEveryThirtySecondsPastEightMinutes() {
        assertEquals(1.0, SoftEnrage.multiplier(0), 1e-9);
        assertEquals(1.0, SoftEnrage.multiplier(8 * 60 * 20 - 1), 1e-9);
        assertEquals(1.1, SoftEnrage.multiplier(8 * 60 * 20), 1e-9);
        assertEquals(1.1, SoftEnrage.multiplier(8 * 60 * 20 + 599), 1e-9);
        assertEquals(1.2, SoftEnrage.multiplier(8 * 60 * 20 + 600), 1e-9);
        assertEquals(2.0, SoftEnrage.multiplier(8 * 60 * 20 + 9 * 600), 1e-9, "no cap");
    }

    // ------------------------------------------------------------------ threat

    @Test
    void itSwitchesTargetOnlyWhenAnotherPassesTheTargetByThirtyPercent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ThreatTable t = new ThreatTable();
        List<ThreatTable.Candidate> both = List.of(new ThreatTable.Candidate(a, 100), new ThreatTable.Candidate(b, 25));
        assertEquals(b, t.check(both).orElseThrow(), "no threat yet: the nearest");
        t.addDamage(b, 100);
        t.addDamage(a, 120);
        assertEquals(b, t.check(both).orElseThrow(), "120 is not 30% past 100");
        t.addDamage(a, 11);
        assertEquals(a, t.check(both).orElseThrow(), "131 is");
        assertEquals(1, t.switches());
        t.taunt(b, HeliarchMoves.GRAVIKIN_TAUNT);
        assertEquals(250.0, t.threat(b), 1e-9, "a Gravikin's taunt counts 150");
        assertEquals(b, t.check(both).orElseThrow());
        assertEquals(a, t.check(List.of(new ThreatTable.Candidate(a, 9))).orElseThrow(), "its target left: the next");
        assertTrue(t.check(List.of()).isEmpty());
        assertEquals(40, HeliarchMoves.THREAT_CHECK, "checked every 40 ticks");
    }

    // ------------------------------------------------------------------ tendrils

    @Test
    void cuttingATendrilSilencesItAndItGrowsBackWhole() {
        TendrilRules.Tendril[] ts = TendrilRules.create(0);
        assertEquals(4, ts.length);
        TendrilRules.Tendril t = ts[0];
        assertFalse(t.hit(29, 100));
        assertTrue(t.hit(2, 101), "30 health");
        assertTrue(t.silenced(101));
        assertTrue(t.silenced(101 + HeliarchMoves.TENDRIL_SILENCE - 1));
        assertFalse(t.silenced(101 + HeliarchMoves.TENDRIL_SILENCE), "10 s");
        assertFalse(t.hit(100, 150), "a silenced tendril can't be cut again");
        assertEquals(30.0, t.health(), 1e-9);
        assertFalse(t.lashDue(200));
        assertTrue(t.nextLash() >= 101 + HeliarchMoves.TENDRIL_SILENCE);
        assertEquals(15.0, HeliarchMoves.TENDRIL_RESONANCE);
        // each lashes every 6 s, the four staggered
        Set<Long> firsts = new HashSet<>();
        for (TendrilRules.Tendril x : ts) {
            firsts.add(x.nextLash());
        }
        assertEquals(4, firsts.size());
        TendrilRules.Tendril u = ts[1];
        u.lashed(500);
        assertFalse(u.lashDue(619));
        assertTrue(u.lashDue(620));
        // the lash line: 1.5 wide, 12 long
        Vec3 anchor = HeliarchArena.tendrilAnchor(0);
        Vec3 end = TendrilRules.lashEnd(anchor, anchor.add(20, 0, 0));
        assertEquals(12.0, end.distanceTo(anchor), 1e-9);
        assertTrue(TendrilRules.onLash(anchor.x + 6, anchor.z + 0.9, 0, anchor, end));
        assertFalse(TendrilRules.onLash(anchor.x + 6, anchor.z + 1.2, 0, anchor, end));
        assertFalse(TendrilRules.onLash(anchor.x + 13.5, anchor.z, 0, anchor, end));
        assertFalse(TendrilRules.onLash(anchor.x + 6, anchor.z, 2.0, anchor, end), "not over a high jump");
    }

    // ------------------------------------------------------------------ Solar Rain

    @Test
    void solarRainFallsOnEachPlayerAndElsewhereOnStandingFloor() {
        Random random = new Random(9);
        List<Vec3> players = List.of(HeliarchArena.at(0, 5), HeliarchArena.at(100, 12));
        for (int i = 0; i < 50; i++) {
            List<Vec3> c = SolarRain.pick(players, 16.0, random::nextDouble, (x, z) -> HeliarchArena.radiusOf(x, z) <= 16.0);
            assertEquals(6, c.size());
            assertTrue(c.get(0).distanceTo(players.get(0)) < 1e-6);
            assertTrue(c.get(1).distanceTo(players.get(1)) < 1e-6);
            for (int k = 2; k < c.size(); k++) {
                assertTrue(HeliarchArena.radiusOf(c.get(k).x, c.get(k).z) <= 16.0);
                for (int j = 0; j < k; j++) {
                    assertTrue(Math.hypot(c.get(k).x - c.get(j).x, c.get(k).z - c.get(j).z) >= SolarRain.SPREAD - 1e-9);
                }
            }
        }
        Vec3 c0 = HeliarchArena.at(45, 10);
        assertTrue(SolarRain.inCircle(c0.x + 2.1, c0.z, 0, c0));
        assertFalse(SolarRain.inCircle(c0.x + 2.5, c0.z, 0, c0));
    }

    // ------------------------------------------------------------------ loot

    @Test
    void aFirstKillGivesTheWholeTableAndRepeatsRollTheirChances() {
        List<net.minecraft.resources.ResourceLocation> charms = HeliarchLoot.CHARMS;
        HeliarchLoot.Reward first = HeliarchLoot.roll(true, charms, () -> 0.99);
        assertEquals(1, first.count(HeliarchLoot.LAST_LIGHT));
        assertEquals(1, first.count(HeliarchLoot.CROWN));
        assertEquals(1, first.count(HeliarchLoot.SOLAR_HEART));
        assertEquals(0, first.count(HeliarchLoot.UMBRA_CANTOR));
        assertEquals(12, first.count(HeliarchLoot.ECLIPSIUM));
        assertEquals(8, HeliarchLoot.roll(true, charms, () -> 0.0).count(HeliarchLoot.ECLIPSIUM));
        assertEquals(40_000, first.xp());
        assertEquals(2, first.statPoints());
        assertTrue(first.firstKill());
        Random random = new Random(21);
        int n = 20_000;
        int cantor = 0;
        int crown = 0;
        int charm = 0;
        int minIngots = 99;
        int maxIngots = 0;
        for (int i = 0; i < n; i++) {
            HeliarchLoot.Reward r = HeliarchLoot.roll(false, charms, random::nextDouble);
            assertEquals(0, r.count(HeliarchLoot.LAST_LIGHT), "Last Light never drops again");
            assertEquals(1, r.count(HeliarchLoot.SOLAR_HEART));
            assertEquals(8_000, r.xp());
            assertEquals(0, r.statPoints());
            cantor += r.count(HeliarchLoot.UMBRA_CANTOR);
            crown += r.count(HeliarchLoot.CROWN);
            for (net.minecraft.resources.ResourceLocation c : charms) {
                charm += r.count(c);
            }
            int ing = r.count(HeliarchLoot.ECLIPSIUM);
            minIngots = Math.min(minIngots, ing);
            maxIngots = Math.max(maxIngots, ing);
        }
        assertEquals(0.15, cantor / (double) n, 0.01);
        assertEquals(0.10, crown / (double) n, 0.01);
        assertEquals(0.25, charm / (double) n, 0.012);
        assertEquals(4, minIngots);
        assertEquals(8, maxIngots);
        assertNull(HeliarchLoot.roll(false, List.of(), () -> 0.0).drops().stream()
                .filter(d -> charms.contains(d.item())).findAny().orElse(null), "no charm when none exist");
        assertNotNull(HeliarchLoot.roll(false, charms, () -> 0.0).drops().stream()
                .filter(d -> charms.contains(d.item())).findAny().orElse(null));
    }
}
