package com.cosmicbreach.entity.gyre;

import com.cosmicbreach.entity.gyre.GyreModes.Mode;
import com.cosmicbreach.guardian.AttackPicker;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Gyre Knight's mode cycle and its blades' orbit. */
class GyreModesTest {
    private static List<Mode> kinds(double distance, double health, int blades) {
        return GyreModes.options(distance, health, blades).stream().map(AttackPicker.Option::attack).toList();
    }

    @Test
    void itPicksByRangeAndRecallsOnlyBelowHalfHealth() {
        assertEquals(List.of(Mode.SWEEP), kinds(3.0, 1.0, 3), "close: sweep");
        assertEquals(List.of(Mode.SWEEP, Mode.LANCE), kinds(8.0, 1.0, 3));
        assertEquals(List.of(Mode.LANCE), kinds(14.0, 1.0, 3), "far: lances");
        assertEquals(List.of(Mode.LANCE, Mode.RECALL), kinds(14.0, 0.4, 3), "below half: Recall Crash joins");
        assertEquals(List.of(), kinds(8.0, 0.4, 0), "no blades on the rings: nothing");
    }

    @Test
    void theCycleReturnsToTheShieldAndNeverRepeatsAModeBackToBack() {
        AttackPicker<Mode> picker = new AttackPicker<>();
        Mode last = null;
        long now = 0;
        int switches = 0;
        java.util.Random r = new java.util.Random(3);
        while (now < 12_000) {
            now += GyreModes.SHIELD_MIN;
            List<AttackPicker.Option<Mode>> options = GyreModes.options(2 + r.nextDouble() * 16, r.nextDouble(), 3);
            Mode m = picker.pick(options, now, r::nextDouble);
            if (m == null) {
                continue;
            }
            assertTrue(m != last, "at " + now);
            int cooldown = options.stream().filter(o -> o.attack() == m).findFirst().orElseThrow().cooldownTicks();
            picker.used(m, cooldown, now);
            last = m;
            long t = 0;
            while (!GyreModes.done(m, t, 20)) {
                t++;
            }
            now += t;
            switches++;
        }
        assertTrue(switches > 40, "switched " + switches);
    }

    @Test
    void eachModesTimelineMatchesTheTable() {
        assertEquals(16, GyreModes.SWEEP_TELL);
        assertEquals(30, GyreModes.SWEEP_ACTIVE);
        assertFalse(GyreModes.sweepCutting(20 + 15, 20));
        assertTrue(GyreModes.sweepCutting(20 + 16, 20));
        assertTrue(GyreModes.sweepCutting(20 + 45, 20));
        assertFalse(GyreModes.sweepCutting(20 + 46, 20));
        assertEquals(10, GyreModes.fireTick(0));
        assertEquals(18, GyreModes.fireTick(1));
        assertEquals(26, GyreModes.fireTick(2), "one blade every 8 ticks after a 10 tick line");
        assertTrue(GyreModes.done(Mode.RECALL, GyreModes.RECALL_OUT + 14 + GyreModes.RECALL_RIP + GyreModes.RECALL_RECOVER, -1));
        assertEquals(60, GyreModes.STUN);
        assertTrue(GyreModes.done(Mode.STUNNED, 60, -1) && !GyreModes.done(Mode.STUNNED, 59, -1));
        assertTrue(GyreModes.RECALL_TELL >= GyreModes.MIN_TELEGRAPH && GyreModes.SWEEP_TELL >= GyreModes.MIN_TELEGRAPH);
    }

    @Test
    void theRingsAndTheHumTellTheMode() {
        assertEquals(1.5, GyreModes.ringRadius(Mode.SHIELD, 50, -1), 1e-9);
        assertEquals(1.5, GyreModes.ringRadius(Mode.SWEEP, 10, 12), 1e-9, "tight while it swoops");
        assertEquals(4.0, GyreModes.ringRadius(Mode.SWEEP, 12 + 16, 12), 1e-9, "wide once the tell is over");
        assertTrue(GyreModes.ringRadius(Mode.SWEEP, 12 + 8, 12) > 2.0);
        float low = GyreModes.humPitch(Mode.SHIELD, 0, -1);
        float rising = GyreModes.humPitch(Mode.SWEEP, 12 + 8, 12);
        float whine = GyreModes.humPitch(Mode.SWEEP, 12 + 15, 12);
        assertTrue(low < rising && rising < whine, "a rising whine into the sweep");
        assertTrue(GyreModes.humPitch(Mode.LANCE, 3, -1) > low);
        assertTrue(GyreModes.humPitch(Mode.STUNNED, 3, -1) < low);
        // the spin speeds up smoothly: no jump at the tell's start or end
        double a = GyreModes.spin(Mode.SWEEP, 12, 12);
        double b = GyreModes.spin(Mode.SWEEP, 12.001, 12);
        assertTrue(Math.abs(b - a) < 0.01);
        double c = GyreModes.spin(Mode.SWEEP, 28, 12);
        double d = GyreModes.spin(Mode.SWEEP, 28.001, 12);
        assertEquals(GyreModes.SWEEP_SPIN * 0.001, d - c, 1e-6);
        assertEquals(Math.PI * 4, GyreModes.spin(Mode.SWEEP, 12 + 16 + 30, 12) - GyreModes.spin(Mode.SWEEP, 12 + 16, 12), 1e-9,
                "two turns over the 30 cutting ticks");
    }

    @Test
    void threeBladesOrbitOnTiltedRingsAThirdOfATurnApart() {
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            Vec3 n = GyreOrbit.normal(i);
            assertEquals(GyreOrbit.TILT_DEGREES, Math.toDegrees(Math.acos(n.y)), 1e-6);
            for (double spin = 0; spin < 6.3; spin += 0.7) {
                Vec3 o = GyreOrbit.offset(i, 4.0, spin);
                assertEquals(4.0, o.length(), 1e-9);
                assertEquals(0.0, o.dot(n), 1e-9, "on its ring");
            }
        }
        Vec3 a = GyreOrbit.offset(0, 1, 0);
        Vec3 b = GyreOrbit.offset(1, 1, 0);
        assertTrue(a.distanceTo(b) > 1.2, "spread round the core");
        Vec3[] edge = GyreOrbit.edge(new Vec3(4, 0, 0));
        assertEquals(4 + GyreOrbit.BLADE_LENGTH / 2, edge[1].x, 1e-9);
        // a Recall Crash's blade passes through where the target stood on its way to the core
        Vec3 spot = new Vec3(10, 0, 3);
        Vec3 mark = new Vec3(5, 0, 0);
        Vec3 core = new Vec3(0, 5, 0);
        double a1 = spot.distanceTo(mark);
        double total = a1 + mark.distanceTo(core);
        assertEquals(0.0, GyreKnight.ripPoint(spot, mark, core, a1 / total).distanceTo(mark), 1e-9);
        assertEquals(0.0, GyreKnight.ripPoint(spot, mark, core, 1.0).distanceTo(core), 1e-9);
    }
}
