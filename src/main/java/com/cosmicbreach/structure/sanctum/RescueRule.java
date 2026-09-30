package com.cosmicbreach.structure.sanctum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Falling off the arena (GDD 7.3), pure: the Breach doesn't kill a player who falls from the Sanctum's floor. Below
 * {@link #CATCH_Y} anywhere within {@link #CATCH_RADIUS} of the throne's column, a falling player is thrown back onto
 * the rim for half their current health and {@link #VOIDSICK_TICKS} of Voidsick. Always on, fight or no fight;
 * creative and spectator players are left alone.
 *
 * <p>Where to: the rim at the angle they fell from, else the rim's nearest standing segment (the Collapse drops them),
 * else the outer ring's, the mid ring's, the dais's; each spot away from the pillars.
 */
public final class RescueRule {
    public static final double CATCH_RADIUS = 48.0;
    public static final int CATCH_Y = SanctumLayout.ARENA_Y - 12;
    public static final int VOIDSICK_TICKS = 60;
    /** Where on each ring a thrown-back player lands, from the throne's column: clear of the pillars at 22. */
    static final double[] LAND_R = {4.0, 12.5, 18.0, 26.5};

    private RescueRule() {
    }

    /** True if a player at (x, y, z) is falling into the Breach from the arena and should be thrown back. */
    public static boolean shouldRescue(double x, double y, double z, boolean onGround, boolean creativeOrSpectator) {
        if (creativeOrSpectator || onGround || y >= CATCH_Y) {
            return false;
        }
        double dx = x - 0.5;
        double dz = z - 0.5;
        return dx * dx + dz * dz <= CATCH_RADIUS * CATCH_RADIUS;
    }

    /** Health left after the throw: half of what they had. Never kills. */
    public static float healthAfter(float health) {
        return health * 0.5f;
    }

    /**
     * Landing spots in the order they are tried ({x, z} block centre coordinates): each ring from the rim inward; on
     * each, the fall's own segment (at the fall's angle), then its neighbours alternating, nearest first (at their
     * middles).
     */
    public static List<double[]> candidates(double x, double z) {
        double angle = SanctumLayout.angle(x - 0.5, z - 0.5);
        int seg = SanctumLayout.segmentOfAngle(angle);
        List<double[]> out = new ArrayList<>();
        for (int ring = 3; ring >= 0; ring--) {
            double r = LAND_R[ring];
            out.add(at(r, angle));
            for (int k = 1; k <= 4; k++) {
                for (int sign : new int[] {1, -1}) {
                    if (k == 4 && sign < 0) {
                        continue;
                    }
                    int s = Math.floorMod(seg + sign * k, SanctumLayout.SEGMENTS);
                    out.add(at(r, (s + 0.5) * Math.PI / 4));
                }
            }
        }
        return out;
    }

    /** The first candidate whose floor still stands, or null if the whole arena is gone. */
    public static double[] pick(List<double[]> candidates, BiPredicate<Double, Double> standing) {
        for (double[] c : candidates) {
            if (standing.test(c[0], c[1])) {
                return c;
            }
        }
        return null;
    }

    private static double[] at(double r, double angle) {
        return new double[] {0.5 + r * Math.sin(angle), 0.5 - r * Math.cos(angle)};
    }
}
