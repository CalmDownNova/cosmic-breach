package com.cosmicbreach.guardian.leviathan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Breach Dive at its faster speed: hits are swept along each step, never a point check. */
class DiveSweepTest {
    private static final Vec3 C = new Vec3(0.5, 100.0, 0.5);

    @Test
    void aStepCatchesATargetItWouldHaveSkippedWithAPointCheck() {
        AABB thin = new AABB(0.6, -0.4, -0.4, 1.4, 0.4, 0.4);          // between the step's two ends
        Vec3 from = new Vec3(-0.3, 0, 0);
        Vec3 to = new Vec3(2.0, 0, 0);
        double reach = 0.1;
        assertFalse(DiveSweep.near(thin, from, reach));
        assertFalse(DiveSweep.near(new AABB(0.9, -0.4, -0.4, 1.1, 0.4, 0.4), from, reach));
        assertFalse(DiveSweep.near(new AABB(0.9, -0.4, -0.4, 1.1, 0.4, 0.4), to, reach));
        assertTrue(DiveSweep.reaches(new AABB(0.9, -0.4, -0.4, 1.1, 0.4, 0.4), from, to, reach), "the step passes through it");
        assertFalse(DiveSweep.reaches(new AABB(0.9, 3.0, -0.4, 1.1, 4.0, 0.4), from, to, reach), "a miss stays a miss");
    }

    @Test
    void everyTickOfTheDivePathAtFullSpeedSweepsAPlayerStandingInIt() {
        LeviathanPaths.Swim s = LeviathanPaths.dive(C, 0.3, 0.0, C.add(30, 0, 12), 38.0);
        double speed = LeviathanMoves.DIVE_SPEED;
        assertTrue(speed < 2 * (LeviathanMoves.DIVE_REACH - 0.2), "a step is shorter than the reach's diameter, so no point is skipped even unswept");
        // a thin box on the path, anywhere: stepping the path at speed per tick, one step's sweep always reaches it
        for (double at = 5; at < s.path().length() - 5; at += 3.7) {
            Vec3 p = s.path().at(at);
            AABB box = new AABB(p.x - 0.3, p.y - 0.9, p.z - 0.3, p.x + 0.3, p.y + 0.9, p.z + 0.3);
            List<Boolean> hit = new ArrayList<>();
            for (double a = 0; a < s.path().length(); a += speed) {
                hit.add(DiveSweep.reaches(box, s.path().at(a), s.path().at(Math.min(s.path().length(), a + speed)), 0.5));
            }
            assertTrue(hit.contains(true), "reached at " + at);
        }
    }

    @Test
    void theDiveStillTakesAboutHalfAsLongSoItNeverOutrunsItsOwnTell() {
        LeviathanPaths.Swim s = LeviathanPaths.dive(C, 0.3, 0.0, C.add(30, 0, 12), 38.0);
        double ticks = s.path().length() / LeviathanMoves.DIVE_SPEED;
        assertTrue(ticks > 30 && ticks < 120, "ticks " + ticks);
        assertEquals(1.3, LeviathanMoves.DIVE_SPEED, 1e-9);
    }
}
