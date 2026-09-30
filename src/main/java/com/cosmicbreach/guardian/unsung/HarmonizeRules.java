package com.cosmicbreach.guardian.unsung;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Harmonize's circles (Unsung design v1): how many of the eight circles of silence light, which ones, and who they
 * shelter on the downbeat. Pure.
 *
 * <ul>
 *   <li>Three circles light while two or three masks sing, two when one is left; one more per two players beyond the
 *       second (so four players get four), never more than eight.</li>
 *   <li>The lit circles are spread round the ring: never two side by side, so from anywhere on the floor one is near.</li>
 *   <li>A player whose middle stands within a lit circle's radius (plus half a player's width) is inside it. Alone,
 *       any lit circle shelters you; with more than one player each lit circle shelters at most two, the two nearest
 *       its centre.</li>
 * </ul>
 */
public final class HarmonizeRules {
    public static final int CIRCLES = 8;
    public static final int LIT = 3;
    public static final int LIT_LAST_MASK = 2;
    /** Players each lit circle shelters when more than one player fights. */
    public static final int CAPACITY = 2;
    /** Half a player's width: a player whose edge is in the circle counts as inside. */
    public static final double PLAYER_HALF = 0.3;

    /** A player's place on the floor. */
    public record Spot(UUID id, double x, double z) {
    }

    /** A lit circle's centre and radius. */
    public record Circle(int index, double x, double z, double radius) {
        boolean holds(Spot s) {
            return distance(s) <= radius + PLAYER_HALF;
        }

        double distance(Spot s) {
            return Math.hypot(s.x() - x, s.z() - z);
        }
    }

    private HarmonizeRules() {
    }

    /** How many circles light for {@code livingMasks} masks and {@code players} players in the fight. */
    public static int litCount(int livingMasks, int players) {
        int base = livingMasks <= 1 ? LIT_LAST_MASK : LIT;
        int extra = Math.max(0, players - 2) / 2;
        return Math.min(CIRCLES, base + extra);
    }

    /**
     * Which {@code count} of the eight circles light, spread round the ring (no two neighbours while that is possible),
     * starting from a random circle; {@code random} gives numbers in [0, 1). Sorted.
     */
    public static int[] choose(int count, DoubleSupplier random) {
        int n = Math.max(0, Math.min(CIRCLES, count));
        int start = (int) Math.floor(clamp01(random.getAsDouble()) * CIRCLES);
        List<Integer> picked = new ArrayList<>();
        if (n <= CIRCLES / 2) {
            // gaps of at least two: split the eight steps round the ring into n gaps of 2 or more, the spare steps at random
            int[] gaps = new int[n];
            java.util.Arrays.fill(gaps, 2);
            int spare = CIRCLES - 2 * n;
            for (int i = 0; i < spare && n > 0; i++) {
                gaps[(int) Math.floor(clamp01(random.getAsDouble()) * n)]++;
            }
            int at = start;
            for (int i = 0; i < n; i++) {
                picked.add(Math.floorMod(at, CIRCLES));
                at += gaps[i];
            }
        } else {
            // more than half: every other circle, then fill the gaps at random
            for (int i = 0; i < CIRCLES / 2; i++) {
                picked.add(Math.floorMod(start + 2 * i, CIRCLES));
            }
            List<Integer> rest = new ArrayList<>();
            for (int i = 0; i < CIRCLES; i++) {
                if (!picked.contains(i)) {
                    rest.add(i);
                }
            }
            while (picked.size() < n) {
                picked.add(rest.remove((int) Math.floor(clamp01(random.getAsDouble()) * rest.size())));
            }
        }
        return picked.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /**
     * Who the lit circles shelter on the downbeat: alone, anyone inside a lit circle; with more players, at most
     * {@value #CAPACITY} per circle, the nearest its centre.
     */
    public static Set<UUID> sheltered(List<Spot> players, List<Circle> lit) {
        Set<UUID> safe = new HashSet<>();
        int capacity = players.size() <= 1 ? Integer.MAX_VALUE : CAPACITY;
        for (Circle c : lit) {
            List<Spot> inside = new ArrayList<>();
            for (Spot s : players) {
                if (c.holds(s)) {
                    inside.add(s);
                }
            }
            inside.sort(Comparator.comparingDouble(c::distance));
            for (int i = 0; i < inside.size() && i < capacity; i++) {
                safe.add(inside.get(i).id());
            }
        }
        return safe;
    }

    private static double clamp01(double u) {
        return Math.max(0.0, Math.min(0.999999, u));
    }
}
