package com.cosmicbreach.onboarding;

import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * Where a Starfall lands (GDD 1.3): the highest valid spot {@code minDistance} to {@code maxDistance} blocks
 * from the player in a random direction; if that direction has none, the next of {@link #DIRECTIONS}
 * directions round the compass; if none has any, the highest of a few spots {@link #NEAR_MIN} to
 * {@link #NEAR_MAX} blocks away. Pure: what counts as valid (a loaded chunk, solid dry ground the crater may
 * dig, no fluid, leaves or block entities around) is the {@link Surface}'s business.
 */
public final class LandingSite {
    public static final int DIRECTIONS = 8;
    /** Blocks between samples along a direction. */
    public static final int STEP = 4;
    public static final int NEAR_MIN = 6;
    public static final int NEAR_MAX = 16;
    public static final int NEAR_TRIES = 24;
    /** Round a landing, the ground may rise or fall at most this much: no craters cut into cliffs. */
    public static final int MAX_RISE = 2;

    /** The ground at a column. */
    @FunctionalInterface
    public interface Surface {
        int NONE = Integer.MIN_VALUE;

        /** The y of the ground block a shard may land on at (x, z), or {@link #NONE}. */
        int landingY(int x, int z);
    }

    /** A landing spot: the ground block the crater is dug into. */
    public record Spot(int x, int y, int z) {
        public double distanceTo(double px, double pz) {
            return Math.hypot(x + 0.5 - px, z + 0.5 - pz);
        }
    }

    private LandingSite() {
    }

    /**
     * True if the ground round a landing at height {@code y} is level enough for a crater: every rim sample
     * within {@link #MAX_RISE} of it ({@link Surface#NONE} samples, water say, fail it).
     */
    public static boolean levelEnough(int y, int[] rim) {
        for (int h : rim) {
            if (h == Surface.NONE || Math.abs(h - y) > MAX_RISE) {
                return false;
            }
        }
        return true;
    }

    /** The landing spot for a player standing at ({@code px}, {@code pz}), or empty if nothing is valid. */
    public static Optional<Spot> choose(double px, double pz, int minDistance, int maxDistance, Surface surface, RandomSource random) {
        int min = Math.max(1, Math.min(minDistance, maxDistance));
        int max = Math.max(min, maxDistance);
        double first = random.nextDouble() * Math.PI * 2.0;
        for (int k = 0; k < DIRECTIONS; k++) {
            double angle = first + k * Math.PI * 2.0 / DIRECTIONS;
            Spot best = null;
            for (int d = min; d <= max; d += STEP) {
                Spot s = sample(px, pz, angle, d, surface);
                if (s != null && s.distanceTo(px, pz) >= min - 0.5 && s.distanceTo(px, pz) <= max + 0.5
                        && (best == null || s.y > best.y)) {
                    best = s;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        Spot best = null;
        for (int i = 0; i < NEAR_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int d = NEAR_MIN + random.nextInt(NEAR_MAX - NEAR_MIN + 1);
            Spot s = sample(px, pz, angle, d, surface);
            if (s != null && s.distanceTo(px, pz) <= NEAR_MAX + 0.5 && (best == null || s.y > best.y)) {
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Spot sample(double px, double pz, double angle, int distance, Surface surface) {
        int x = (int) Math.floor(px + Math.cos(angle) * distance);
        int z = (int) Math.floor(pz + Math.sin(angle) * distance);
        int y = surface.landingY(x, z);
        return y == Surface.NONE ? null : new Spot(x, y, z);
    }
}
