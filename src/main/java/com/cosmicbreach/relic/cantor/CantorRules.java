package com.cosmicbreach.relic.cantor;

import java.util.List;

/**
 * The Umbra Cantor's numbers and rules (GDD 7.3), pure so they are unit-tested.
 *
 * <ul>
 *   <li><b>Notes.</b> The charged shot (a {@value #DRAW_TICKS}-tick draw) leaves a resonant note where it lands, for
 *       {@value #NOTE_LIFE} ticks. The notes a player places walk up D major pentatonic (D, E, F#, A, B, then round
 *       again), one tone each; a phrase starts over on D whenever the player has no loose note left. A player keeps at
 *       most {@value #MAX_NOTES} loose notes; a new one fades the oldest.</li>
 *   <li><b>Chords.</b> When a note lands within {@value #CHORD_SPAN} blocks of two loose notes that are themselves
 *       within {@value #CHORD_SPAN} of each other, the three form a chord (of the pairs that could, the widest
 *       triangle, then the newest). A chord rings for {@value #CHORD_LIFE} ticks from the moment it forms, its notes
 *       held as its corners: the triangle between them (seen from above, with {@value #EDGE_MARGIN} block round its
 *       edges, from {@value #BAND_BELOW} below its lowest corner to {@value #BAND_ABOVE} above its highest) silences
 *       every enemy in it that isn't a boss (no abilities) and pulses {@value #PULSE_DAMAGE} damage every
 *       {@value #PULSE_TICKS} ticks (at its unlock tier and zero stats; the Cantor's scaling applies, crits don't).
 *       Its sound is a triad: D, F#, A.</li>
 *   <li><b>Cadence</b> (30 Resonance, 7 s): three quick shots in 12 ticks, on the ability's active ticks 0, 5 and 10,
 *       fanned so they land as a triangle (left, far middle, right). Each leaves a note, so a Cadence into a group
 *       strikes a chord by itself.</li>
 * </ul>
 */
public final class CantorRules {
    /** Ticks the charged shot's draw is held (from the press). */
    public static final int DRAW_TICKS = 20;
    public static final int NOTE_LIFE = 100;
    public static final double CHORD_SPAN = 8.0;
    public static final int CHORD_LIFE = 100;
    public static final int PULSE_TICKS = 20;
    public static final double PULSE_DAMAGE = 4.0;
    public static final double EDGE_MARGIN = 1.0;
    public static final double BAND_BELOW = 1.5;
    public static final double BAND_ABOVE = 2.5;
    public static final int MAX_NOTES = 6;
    /** D major pentatonic: semitones from D5 (D, E, F#, A, B). */
    public static final int[] SCALE = {0, 2, 4, 7, 9};
    /** The chord's triad: D, F#, A (semitones from D5). */
    public static final int[] TRIAD = {0, 4, 7};
    public static final int CADENCE_SHOTS = 3;
    /** Active ticks between Cadence's shots: 0, 5, 10, so three shots within 12 ticks. */
    public static final int CADENCE_INTERVAL = 5;

    /** A loose note: an id, where it sits, when it was placed (game time). */
    public record Note(int id, double x, double y, double z, long placed) {
    }

    private CantorRules() {
    }

    /**
     * The index of the next note in its phrase: a phrase starts over on D when the player has no loose note left (they
     * all faded, or the last chord took them), else it walks on from the {@code placed} notes before it.
     */
    public static int nextIndex(int placed, int looseNotes) {
        return looseNotes <= 0 ? 0 : Math.max(0, placed);
    }

    /** The tone of the {@code index}-th note of a phrase (from 0): semitones from D5, up the scale and round again. */
    public static int tone(int index) {
        return SCALE[Math.floorMod(index, SCALE.length)];
    }

    /** The playback pitch that turns a D5 tone into {@code semitones} from D5 (vanilla's 0.5 to 2 range). */
    public static float pitch(int semitones) {
        return (float) Math.max(0.5, Math.min(2.0, Math.pow(2.0, semitones / 12.0)));
    }

    /** True if {@code semitones} from D5 is in D major pentatonic. */
    public static boolean inScale(int semitones) {
        int s = Math.floorMod(semitones, 12);
        for (int n : SCALE) {
            if (n == s) {
                return true;
            }
        }
        return false;
    }

    public static double distance(Note a, Note b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** True if every two of the three notes are within {@link #CHORD_SPAN} of each other. */
    public static boolean chord(Note a, Note b, Note c) {
        return distance(a, b) <= CHORD_SPAN + 1e-9 && distance(b, c) <= CHORD_SPAN + 1e-9 && distance(a, c) <= CHORD_SPAN + 1e-9;
    }

    /** The triangle's area seen from above (x, z). */
    public static double area(Note a, Note b, Note c) {
        return Math.abs((b.x() - a.x()) * (c.z() - a.z()) - (c.x() - a.x()) * (b.z() - a.z())) / 2.0;
    }

    /**
     * The two loose notes that strike a chord with the note just {@code placed}, as indices into {@code loose} (which
     * doesn't hold {@code placed}), or null if no pair does. Of the pairs that could, the widest triangle wins, then
     * the newest pair (the later of its two notes, then the other).
     */
    public static int[] pick(Note placed, List<Note> loose) {
        int[] best = null;
        double bestArea = -1.0;
        long bestNewest = Long.MIN_VALUE;
        long bestOther = Long.MIN_VALUE;
        for (int i = 0; i < loose.size(); i++) {
            for (int j = i + 1; j < loose.size(); j++) {
                Note a = loose.get(i);
                Note b = loose.get(j);
                if (!chord(placed, a, b)) {
                    continue;
                }
                double area = area(placed, a, b);
                long newest = Math.max(a.placed(), b.placed());
                long other = Math.min(a.placed(), b.placed());
                boolean better = area > bestArea + 1e-9
                        || (Math.abs(area - bestArea) <= 1e-9 && (newest > bestNewest || (newest == bestNewest && other > bestOther)));
                if (better) {
                    best = new int[] {i, j};
                    bestArea = area;
                    bestNewest = newest;
                    bestOther = other;
                }
            }
        }
        return best;
    }

    /** The loose note to fade before a new one is added ({@code loose} full): the oldest; -1 while there is room. */
    public static int evict(List<Note> loose) {
        if (loose.size() < MAX_NOTES) {
            return -1;
        }
        int oldest = 0;
        for (int i = 1; i < loose.size(); i++) {
            if (loose.get(i).placed() < loose.get(oldest).placed()) {
                oldest = i;
            }
        }
        return oldest;
    }

    /** True if a note placed at {@code placed} has run out at {@code now}. */
    public static boolean expired(long placed, long now) {
        return now - placed >= NOTE_LIFE;
    }

    /**
     * True if the point is in the chord with corners {@code (xs[i], ys[i], zs[i])}: seen from above inside the triangle
     * or within {@link #EDGE_MARGIN} of an edge (so three notes in a row still make a strip), and between
     * {@link #BAND_BELOW} under the lowest corner and {@link #BAND_ABOVE} over the highest.
     */
    public static boolean inside(double[] xs, double[] ys, double[] zs, double x, double y, double z) {
        double low = Math.min(ys[0], Math.min(ys[1], ys[2])) - BAND_BELOW;
        double high = Math.max(ys[0], Math.max(ys[1], ys[2])) + BAND_ABOVE;
        if (y < low || y > high) {
            return false;
        }
        if (inTriangle(xs[0], zs[0], xs[1], zs[1], xs[2], zs[2], x, z)) {
            return true;
        }
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            if (segmentDistance(xs[i], zs[i], xs[j], zs[j], x, z) <= EDGE_MARGIN + 1e-9) {
                return true;
            }
        }
        return false;
    }

    /** True if (px, pz) is inside the triangle or on its edge (either winding). */
    static boolean inTriangle(double ax, double az, double bx, double bz, double cx, double cz, double px, double pz) {
        double d1 = cross(ax, az, bx, bz, px, pz);
        double d2 = cross(bx, bz, cx, cz, px, pz);
        double d3 = cross(cx, cz, ax, az, px, pz);
        boolean negative = d1 < -1e-12 || d2 < -1e-12 || d3 < -1e-12;
        boolean positive = d1 > 1e-12 || d2 > 1e-12 || d3 > 1e-12;
        return !(negative && positive);
    }

    private static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    /** Distance from (px, pz) to the segment from (ax, az) to (bx, bz). */
    static double segmentDistance(double ax, double az, double bx, double bz, double px, double pz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (pz - az) * dz) / len2));
        double qx = ax + t * dx - px;
        double qz = az + t * dz - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    /** True on the ticks a chord {@code age} ticks old pulses: 20, 40, 60, 80 and 100, five in its life. */
    public static boolean pulses(int age) {
        return age > 0 && age <= CHORD_LIFE && age % PULSE_TICKS == 0;
    }

    /** One pulse's damage: {@link #PULSE_DAMAGE} times the Cantor's grade scaling and its tier multiplier (no crits). */
    public static double pulseDamage(double scaling, double tierMultiplier) {
        return PULSE_DAMAGE * scaling * tierMultiplier;
    }

    /** True on the Cadence's active ticks that loose a shot: 0, 5 and 10. */
    public static boolean cadenceShot(int activeTick) {
        return activeTick >= 0 && activeTick % CADENCE_INTERVAL == 0 && activeTick / CADENCE_INTERVAL < CADENCE_SHOTS;
    }

    /** Which of the Cadence's shots an active tick looses (0, 1, 2), or -1. */
    public static int cadenceIndex(int activeTick) {
        return cadenceShot(activeTick) ? activeTick / CADENCE_INTERVAL : -1;
    }

    /**
     * Where the Cadence's shot {@code index} aims, off the crosshair, in degrees {yaw to the right, up}: left, the
     * middle a little higher (so it lands further), right. Three notes that land as a triangle, not in a row.
     */
    public static double[] cadenceOffset(int index, double spread, double lift) {
        return switch (index) {
            case 0 -> new double[] {-spread, 0.0};
            case 1 -> new double[] {0.0, lift};
            default -> new double[] {spread, 0.0};
        };
    }
}
