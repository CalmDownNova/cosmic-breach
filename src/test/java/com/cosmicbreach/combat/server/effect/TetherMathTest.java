package com.cosmicbreach.combat.server.effect;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a blink to the Binary Edges' thrown blade arrives, and its travel step (GDD 4.2's Tether). */
class TetherMathTest {
    private static final double E = 1e-9;

    @Test
    void besideAnEnemyOnTheThrowersSide() {
        // a zombie (0.6 wide) 8 blocks south, the blade in its side: the feet stop out from its side, facing it
        AABB zombie = new AABB(-0.3, 64, 7.7, 0.3, 65.95, 8.3);
        Vec3 at = TetherMath.arrival(new Vec3(0, 64, 0), new Vec3(0.0, 64.8, 7.8), zombie, null);
        assertEquals(0.0, at.x, E);
        assertEquals(64.0, at.y, E, "on the enemy's feet level");
        assertEquals(8.0 - 0.3 - TetherMath.ENEMY_GAP, at.z, E);
    }

    @Test
    void inFrontOfAWallWithTheBladeAtChestHeight() {
        Vec3 blade = new Vec3(0.2, 65.3, 6.0);
        Vec3 at = TetherMath.arrival(new Vec3(0, 64, 0), blade, null, Direction.NORTH);
        assertEquals(0.2, at.x, E);
        assertEquals(6.0 - TetherMath.WALL_GAP, at.z, E, "out from the face it hit, toward the thrower");
        assertEquals(65.3 - TetherMath.CHEST, at.y, E);
    }

    @Test
    void onTopOfTheGroundItWentInto() {
        Vec3 blade = new Vec3(3.0, 64.0, 4.0);
        Vec3 at = TetherMath.arrival(new Vec3(0, 70, 0), blade, null, Direction.UP);
        assertEquals(64.02, at.y, E, "standing on it");
        assertEquals(0.3, at.subtract(blade).multiply(1, 0, 1).length(), 1e-6, "a step short, on the thrower's side");
    }

    @Test
    void theTravelStepCoversWhatIsLeftInEvenShares() {
        Vec3 now = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(0, 66, 6.0);
        Vec3 step = TetherMath.blinkStep(now, to, 6);
        assertEquals(1.0, step.z, E);
        assertEquals(2.0 / 6.0, step.y, E);
        assertEquals(to, now.add(TetherMath.blinkStep(now, to, 1)), "the last tick lands on it");
        assertEquals(to.subtract(now), TetherMath.blinkStep(now, to, 0), "never divides by zero");
    }
}
