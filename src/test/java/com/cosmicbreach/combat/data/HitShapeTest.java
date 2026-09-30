package com.cosmicbreach.combat.data;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometry of the three hit shapes. Yaw 0 faces +Z (south), yaw 90 faces -X. */
class HitShapeTest {
    /** A player standing at y 64 swings from its chest. */
    private static final Vec3 CHEST = new Vec3(0, 65, 0);

    /** A 0.6 wide, 1.8 tall mob standing at (x, 64, z). */
    private static AABB mob(double x, double z) {
        return new AABB(x - 0.3, 64, z - 0.3, x + 0.3, 65.8, z + 0.3);
    }

    @Test
    void arcHitsInFrontAndMissesBehind() {
        HitShape.Arc arc = new HitShape.Arc(3.5, 110, 2.5);
        assertTrue(arc.hits(CHEST, 0f, mob(0, 2)));
        assertFalse(arc.hits(CHEST, 0f, mob(0, -2)));
    }

    @Test
    void arcRespectsItsAngle() {
        HitShape.Arc arc = new HitShape.Arc(3.5, 110, 2.5);
        assertTrue(arc.hits(CHEST, 0f, mob(1.4, 2)));    // about 35 degrees off forward
        assertFalse(arc.hits(CHEST, 0f, mob(2.5, 0.2))); // about 85 degrees off forward
    }

    @Test
    void arcRespectsItsRadius() {
        HitShape.Arc arc = new HitShape.Arc(3.5, 110, 2.5);
        assertTrue(arc.hits(CHEST, 0f, mob(0, 3.7)));    // near face at 3.4
        assertFalse(arc.hits(CHEST, 0f, mob(0, 4.2)));   // near face at 3.9
    }

    @Test
    void arcIgnoresTargetsAboveItsBand() {
        HitShape.Arc arc = new HitShape.Arc(3.5, 110, 2.5);
        assertFalse(arc.hits(CHEST, 0f, new AABB(-0.3, 68, 1.7, 0.3, 69.8, 2.3)));
    }

    @Test
    void arcFollowsYaw() {
        HitShape.Arc arc = new HitShape.Arc(3.5, 110, 2.5);
        assertTrue(arc.hits(CHEST, 90f, mob(-2, 0)));
        assertFalse(arc.hits(CHEST, 90f, mob(2, 0)));
    }

    @Test
    void fullCircleArcHitsEverythingInRange() {
        HitShape.Arc ring = new HitShape.Arc(3.0, 360, 2.5);
        assertTrue(ring.hits(CHEST, 0f, mob(0, -2)));
        assertTrue(ring.hits(CHEST, 0f, mob(2, 0)));
    }

    @Test
    void lineRespectsLengthAndWidth() {
        HitShape.Line line = new HitShape.Line(4.0, 1.2, 2.5);
        assertTrue(line.hits(CHEST, 0f, mob(0, 3)));
        assertTrue(line.hits(CHEST, 0f, mob(0.85, 2)));  // spans x 0.55 to 1.15, overlaps the 0.6 half width
        assertFalse(line.hits(CHEST, 0f, mob(1.5, 2)));  // spans x 1.2 to 1.8
        assertFalse(line.hits(CHEST, 0f, mob(0, 4.6)));  // starts at z 4.3, past the 4.0 length
        assertFalse(line.hits(CHEST, 0f, mob(0, -1)));   // behind
    }

    @Test
    void lineWorksOnADiagonal() {
        HitShape.Line line = new HitShape.Line(4.0, 1.2, 2.5);
        assertTrue(line.hits(CHEST, -45f, mob(2, 2)));
        assertFalse(line.hits(CHEST, -45f, mob(-2, 2)));
    }

    @Test
    void sphereUsesDistanceAndOffset() {
        HitShape.Sphere around = new HitShape.Sphere(2.5, 0);
        assertTrue(around.hits(CHEST, 0f, mob(0, 2)));
        assertFalse(around.hits(CHEST, 0f, mob(0, 3.2)));
        HitShape.Sphere ahead = new HitShape.Sphere(1.0, 3.0);
        assertTrue(ahead.hits(CHEST, 0f, mob(0, 3)));
        assertFalse(ahead.hits(CHEST, 0f, mob(0, 0.5)));
    }
}
