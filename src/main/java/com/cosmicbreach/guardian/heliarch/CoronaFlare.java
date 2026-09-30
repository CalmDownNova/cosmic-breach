package com.cosmicbreach.guardian.heliarch;

import net.minecraft.world.phys.Vec3;

/**
 * Corona Flare (G9c), the Regent's push-out for a player who hugs the core: who stands inside its ring (radius
 * {@value HeliarchMoves#FLARE_RADIUS} round the throne's column, a player's edge counts), how long each has hugged, and
 * which way the flare throws them. Pure.
 */
public final class CoronaFlare {
    /** A player this high over the floor is above the flare. */
    public static final double REACH_UP = 8.0;

    private CoronaFlare() {
    }

    /** True if a player standing at (x, z) is inside the flare's ring. */
    public static boolean inside(double x, double z) {
        return HeliarchArena.radiusOf(x, z) <= HeliarchMoves.FLARE_RADIUS + 0.3;
    }

    /** True if the flare burns a player at (x, z) whose feet are {@code feet} over the floor. */
    public static boolean strikes(double x, double z, double feet) {
        return inside(x, z) && feet < REACH_UP;
    }

    /** A player's hug count after one more tick: one more inside the ring, back to zero outside it. */
    public static int hug(int ticks, boolean inside) {
        return inside ? ticks + 1 : 0;
    }

    /** True once a player has stayed inside long enough to call the flare. */
    public static boolean hugged(int ticks) {
        return ticks >= HeliarchMoves.FLARE_HUG;
    }

    /** The way the flare throws a player at (x, z): straight out from the throne's column (east from dead centre). */
    public static Vec3 outward(double x, double z) {
        Vec3 d = new Vec3(x - HeliarchArena.CX, 0.0, z - HeliarchArena.CZ);
        return d.lengthSqr() < 1e-6 ? new Vec3(1.0, 0.0, 0.0) : d.normalize();
    }
}
