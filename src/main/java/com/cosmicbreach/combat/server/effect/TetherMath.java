package com.cosmicbreach.combat.server.effect;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a blink to the thrown blade arrives (GDD 4.2's Tether), from where it starts: both sides compute it
 * the same way, the client to move its player there, the server to know where the player is going. Pure.
 */
public final class TetherMath {
    /** Arriving at a blade in an enemy, the feet stop this far out from its side, toward where the blink came from. */
    public static final double ENEMY_GAP = 1.1;
    /** Arriving at a blade in a wall, the feet stop this far out from it. */
    public static final double WALL_GAP = 0.75;
    /** A blade in a wall sits at about chest height of the arrival: the feet this far below it. */
    public static final double CHEST = 0.95;

    private TetherMath() {
    }

    /**
     * The feet's arrival point for a blink from {@code feet} to a blade at {@code blade}: beside the enemy it is
     * stuck in ({@code enemy}, its box), on the thrower's side; in front of the block face it is stuck in
     * ({@code face}, the face it hit), with the blade at chest height; on top of a block it went into from above.
     */
    public static Vec3 arrival(Vec3 feet, Vec3 blade, @Nullable AABB enemy, @Nullable Direction face) {
        Vec3 back = flatBack(feet, blade);
        if (enemy != null) {
            Vec3 centre = enemy.getCenter();
            double out = Math.max(enemy.getXsize(), enemy.getZsize()) / 2.0 + ENEMY_GAP;
            return new Vec3(centre.x + back.x * out, enemy.minY, centre.z + back.z * out);
        }
        if (face == Direction.UP) {
            return blade.add(back.scale(0.3)).add(0, 0.02, 0); // thrown down into the ground: land on it
        }
        if (face == Direction.DOWN) {
            return blade.add(back.scale(0.3)).subtract(0, 1.9, 0); // a ceiling: hang under it and drop
        }
        Vec3 out = face == null ? back : new Vec3(face.getStepX(), 0, face.getStepZ());
        if (out.lengthSqr() < 1e-6) {
            out = back;
        }
        return blade.add(out.scale(WALL_GAP)).subtract(0, CHEST, 0);
    }

    /** The horizontal unit vector from the blade back toward where the blink starts (south if right above it). */
    static Vec3 flatBack(Vec3 feet, Vec3 blade) {
        Vec3 d = new Vec3(feet.x - blade.x, 0, feet.z - blade.z);
        double length = d.length();
        return length < 1e-6 ? new Vec3(0, 0, 1) : d.scale(1.0 / length);
    }

    /** The feet's velocity for one tick of a blink with {@code ticksLeft} ticks to go from {@code now} to {@code to}. */
    public static Vec3 blinkStep(Vec3 now, Vec3 to, int ticksLeft) {
        return to.subtract(now).scale(1.0 / Math.max(1, ticksLeft));
    }
}
