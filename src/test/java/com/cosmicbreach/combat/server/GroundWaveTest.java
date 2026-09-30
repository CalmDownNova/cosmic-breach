package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.data.HitShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Meridian Line's ground wave: 8 blocks in 4 ticks, a moving segment, once per target. */
class GroundWaveTest {
    private static final Vec3 FEET = new Vec3(0.5, 64, 0.5);
    private static final float SOUTH = 0f; // forward is +Z

    private static GroundWave wave() {
        return new GroundWave(FEET, SOUTH, 8.0, 1.2, 100, null, null, 1.2);
    }

    /** A zombie-sized box (0.6 by 1.95) standing on the ground {@code ahead} blocks south of the feet. */
    private static AABB zombieAt(double ahead) {
        return new AABB(FEET.x - 0.3, FEET.y, FEET.z + ahead - 0.3, FEET.x + 0.3, FEET.y + 1.95, FEET.z + ahead + 0.3);
    }

    private static List<GroundWave.Step> allSteps(GroundWave wave) {
        List<GroundWave.Step> steps = new ArrayList<>();
        GroundWave.Step step;
        while ((step = wave.advance()) != null) {
            steps.add(step);
        }
        return steps;
    }

    @Test
    void fourContiguousSegmentsCoverTheWholeLength() {
        List<GroundWave.Step> steps = allSteps(wave());
        assertEquals(GroundWave.STEPS, steps.size());
        double reached = 0;
        for (GroundWave.Step step : steps) {
            assertEquals(reached, step.from(), 1e-9, "no gap before step " + step.index());
            assertEquals(2.0, step.to() - step.from(), 1e-9);
            assertEquals(2.0, step.shape().length(), 1e-9);
            reached = step.to();
        }
        assertEquals(8.0, reached, 1e-9);
    }

    @Test
    void itMovesOnlyOnTheTicksAfterItWasMade() {
        GroundWave wave = wave();
        assertFalse(wave.dueAt(100), "not in the tick of the first active frame");
        assertTrue(wave.dueAt(101));
        allSteps(wave);
        assertTrue(wave.finished());
        assertFalse(wave.dueAt(200));
        assertNull(wave.advance());
    }

    @Test
    void eachStepHitsWhatIsInItsSliceOnly() {
        GroundWave wave = wave();
        AABB near = zombieAt(1.0);
        AABB mid = zombieAt(5.0);
        AABB far = zombieAt(9.5);
        List<GroundWave.Step> steps = allSteps(wave);
        assertTrue(hits(steps.get(0), near), "1 block is in the first slice, 0 to 2");
        assertFalse(hits(steps.get(1), near));
        assertFalse(hits(steps.get(0), mid));
        assertFalse(hits(steps.get(1), mid));
        assertTrue(hits(steps.get(2), mid), "5 blocks is in the third slice, 4 to 6");
        assertFalse(hits(steps.get(3), mid));
        for (GroundWave.Step step : steps) {
            assertFalse(hits(step, far), "past the end of the wave");
        }
    }

    @Test
    void theWaveStaysOnTheGround() {
        GroundWave.Step first = wave().advance();
        assertNotNull(first);
        AABB suspended = zombieAt(1.0).move(0, 3.5, 0);
        assertFalse(hits(first, suspended), "a target launched 3.5 blocks up is above the wave");
    }

    @Test
    void itFollowsTheAttackersYaw() {
        GroundWave east = new GroundWave(FEET, -90f, 8.0, 1.2, 0, null, null, 1.2);
        GroundWave.Step step = east.advance();
        assertNotNull(step);
        AABB eastBox = new AABB(FEET.x + 0.7, FEET.y, FEET.z - 0.3, FEET.x + 1.3, FEET.y + 1.95, FEET.z + 0.3);
        assertTrue(step.shape().hits(step.origin(), east.yaw(), eastBox));
        assertFalse(step.shape().hits(step.origin(), east.yaw(), zombieAt(1.0)));
    }

    @Test
    void aTargetIsHitOncePerWave() {
        GroundWave wave = wave();
        assertTrue(wave.markHit(42));
        assertFalse(wave.markHit(42));
        assertTrue(wave.markHit(7));
        assertTrue(wave().markHit(42), "a new wave can hit it again");
    }

    @Test
    void segmentMathMatchesTheSteps() {
        assertEquals(0.0, GroundWave.segmentFrom(8, 1), 1e-9);
        assertEquals(6.0, GroundWave.segmentFrom(8, 4), 1e-9);
        assertEquals(8.0, GroundWave.segmentTo(8, 4), 1e-9);
    }

    @Test
    void stepsStartAlongTheWavesPathAtTheBand() {
        GroundWave wave = wave();
        wave.advance();
        GroundWave.Step second = wave.advance();
        assertNotNull(second);
        Vec3 expected = FEET.add(HitShape.forward(SOUTH).scale(2.0)).add(0, GroundWave.BAND_CENTRE, 0);
        assertTrue(second.origin().distanceTo(expected) < 1e-9, "second slice starts 2 blocks out, got " + second.origin());
    }

    private static boolean hits(GroundWave.Step step, AABB box) {
        return step.shape().hits(step.origin(), SOUTH, box);
    }
}
