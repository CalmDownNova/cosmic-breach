package com.cosmicbreach.guardian.heliarch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;
import net.minecraft.world.phys.Vec3;

/**
 * Solar Rain (GDD 7.3), through the Collapse: every {@value HeliarchMoves#RAIN_EVERY} ticks six gold circles (radius 2)
 * on the floor, and {@value HeliarchMoves#RAIN_TELL} ticks later six impacts (14 each). One circle falls on each
 * player first; the rest on floor still standing, apart from each other. Pure.
 */
public final class SolarRain {
    /** Circles at least this far apart (players' circles excepted). */
    public static final double SPREAD = 4.0;

    private SolarRain() {
    }

    /**
     * The circles' middles (at floor level) for one volley: on each of {@code players} first (up to
     * {@link HeliarchMoves#RAIN_COUNT}), then random points within {@code radius} where {@code standing} says there is
     * floor.
     */
    public static List<Vec3> pick(List<Vec3> players, double radius, DoubleSupplier random, BiPredicate<Double, Double> standing) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 p : players) {
            if (out.size() >= HeliarchMoves.RAIN_COUNT) {
                break;
            }
            out.add(new Vec3(p.x, HeliarchArena.FLOOR, p.z));
        }
        int tries = 0;
        while (out.size() < HeliarchMoves.RAIN_COUNT && tries++ < 200) {
            double a = random.getAsDouble() * 360.0;
            double r = Math.sqrt(random.getAsDouble()) * radius;
            Vec3 c = HeliarchArena.at(a, r);
            if (!standing.test(c.x, c.z)) {
                continue;
            }
            boolean apart = true;
            for (Vec3 o : out) {
                if (Math.hypot(o.x - c.x, o.z - c.z) < SPREAD) {
                    apart = false;
                    break;
                }
            }
            if (apart) {
                out.add(c);
            }
        }
        return out;
    }

    /** True if a player whose middle is at (x, z), feet {@code feet} over the floor, is in the circle at {@code c}. */
    public static boolean inCircle(double x, double z, double feet, Vec3 c) {
        return feet < 2.5 && Math.hypot(x - c.x, z - c.z) <= HeliarchMoves.RAIN_RADIUS + 0.3;
    }
}
