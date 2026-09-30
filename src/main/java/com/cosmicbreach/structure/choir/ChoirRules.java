package com.cosmicbreach.structure.choir;

import com.cosmicbreach.world.VesperClock;

/**
 * The Choir Floor's numbers (GDD 6.3), all in whole ticks of Vesper's clock (12 a beat, 100 BPM). Pure.
 *
 * <p><b>The ring.</b> Eight pads, one note each of the pentatonic scale in D (the sound set's key: D4 E4 F#4 A4
 * B4 D5 E5 F#5), laid round the Conductor like petals: pad {@code p} spans the band from {@link #R_IN} to
 * {@link #R_OUT} blocks around the Conductor's axis, {@value #SLOT_DEGREES} degrees wide, centred on its slot's
 * angle, with a {@link #DIVIDER}-block seam between neighbours that belongs to no pad. The glyphs sit near radius 5.
 * Inside the ring, round the statue, is a clear disc to walk on; from its edge a player reaches every pad at a
 * walk: a neighbour across the seam in half a beat, two pads over in a beat, three or four (across the Conductor)
 * in two. The ring can turn: at rotation {@code r}, pad {@code p} lies
 * in slot {@code (p + r) mod 8} (slot 0 is east, slots run counter-clockwise seen from above, toward +z).
 *
 * <p><b>Rhythm.</b> A phrase's notes fall on beats ({@link #BEAT} ticks apart), except one eighth-note pair
 * ({@link #EIGHTH} ticks) in round 2; a leap of three or four pads is given two beats. {@link #gapFor} is the
 * rule, and it keeps every phrase walkable.
 */
public final class ChoirRules {
    public static final int PADS = 8;
    public static final int ROUNDS = 3;
    public static final int BEAT = VesperClock.TICKS_PER_BEAT;
    public static final int EIGHTH = BEAT / 2;
    /** A step counts within this many ticks of its beat (GDD 6.3), plus latency grace. */
    public static final int WINDOW = 3;
    /** The server config's Relaxed window. */
    public static final int RELAXED_WINDOW = 5;
    /** Most latency grace a step gets, in ticks (a 200 ms round trip). */
    public static final int MAX_GRACE = 4;
    public static final float DISCORD_DAMAGE = 4.0f;
    /** The Discord's push away from the Conductor, blocks per tick. */
    public static final double DISCORD_KNOCKBACK = 0.45;
    /** Mistakes in one round before the floor rests. */
    public static final int MISTAKES_TO_REST = 3;
    /** The rest, 10 s. */
    public static final int REST_TICKS = 200;
    /** The home note every phrase ends on: the tonic, D4, the first pad. */
    public static final int HOME = 0;
    /** Beats counted in before the answer (the ring turns during these in round 3). */
    public static final int COUNT_IN_BEATS = 4;
    /** Beats between a phase's end and the next call. */
    public static final int LEAD_BEATS = 2;
    /** With no player on the floor for this long, an awake floor falls idle (it keeps its rounds). */
    public static final int IDLE_TICKS = 100;

    /** The notes, pad by pad (Hz): D major pentatonic from D4. */
    public static final double[] HZ = {293.66, 329.63, 369.99, 440.0, 493.88, 587.33, 659.26, 739.99};
    public static final String[] NOTE_NAMES = {"D4", "E4", "F#4", "A4", "B4", "D5", "E5", "F#5"};

    // ------------------------------------------------------------------ the ring's geometry (blocks)

    public static final double R_IN = 2.0;
    public static final double R_OUT = 6.5;
    /** The ring's nominal radius (GDD 6.3: radius 5): where the glyphs sit. */
    public static final double RADIUS = 4.7;
    public static final double SLOT_DEGREES = 45.0;
    /** Width of the seam between two pads (blocks, measured across it). */
    public static final double DIVIDER = 0.5;
    /** The floor round the Conductor that counts as "on the floor": players here hear Discords. */
    public static final double FLOOR_RADIUS = 7.5;

    private ChoirRules() {
    }

    /** Pads apart round the ring (0 to 4). */
    public static int ringDistance(int a, int b) {
        int d = Math.floorMod(a - b, PADS);
        return Math.min(d, PADS - d);
    }

    /** The ticks a note must follow the previous one by, for a move of {@code distance} pads (not a pair). */
    public static int gapFor(int distance) {
        return distance >= 3 ? 2 * BEAT : BEAT;
    }

    /** Slot of pad {@code pad} at rotation {@code rotation}. */
    public static int slotOf(int pad, int rotation) {
        return Math.floorMod(pad + rotation, PADS);
    }

    /** Pad in slot {@code slot} at rotation {@code rotation}. */
    public static int padIn(int slot, int rotation) {
        return Math.floorMod(slot - rotation, PADS);
    }

    /** Angle of slot {@code slot}'s centre line, radians (0 is +x, toward +z). */
    public static double slotAngle(int slot) {
        return Math.toRadians(slot * SLOT_DEGREES);
    }

    /**
     * The slot a point {@code (dx, dz)} blocks from the Conductor's axis stands in, or -1 on no pad (inside the
     * inner disc, outside the ring, or on a seam).
     */
    public static int slotAt(double dx, double dz) {
        double r = Math.hypot(dx, dz);
        if (r < R_IN || r > R_OUT) {
            return -1;
        }
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        int slot = Math.floorMod((int) Math.round(deg / SLOT_DEGREES), PADS);
        double off = Math.abs(Math.IEEEremainder(deg - slot * SLOT_DEGREES, 360.0));
        // distance from the seam line, across it, in blocks
        double toSeam = r * Math.sin(Math.toRadians(SLOT_DEGREES / 2 - off));
        return toSeam < DIVIDER / 2 ? -1 : slot;
    }

    /** The pad under a point at rotation {@code rotation}, or -1. */
    public static int padAt(double dx, double dz, int rotation) {
        int slot = slotAt(dx, dz);
        return slot < 0 ? -1 : padIn(slot, rotation);
    }

    /** Latency grace for a player, in ticks, from the server's round-trip estimate in milliseconds. */
    public static int graceTicks(int latencyMs) {
        return Math.max(0, Math.min(MAX_GRACE, Math.round(latencyMs / 50.0f)));
    }

    /** The first beat tick at or after {@code tick}. */
    public static long nextBeat(long tick) {
        return Math.floorDiv(tick + BEAT - 1, BEAT) * BEAT;
    }

    /** "first" .. "eighth": a pad's name in its subtitle ("Chime, third pad"). */
    public static String ordinal(int pad) {
        return switch (pad) {
            case 0 -> "first";
            case 1 -> "second";
            case 2 -> "third";
            case 3 -> "fourth";
            case 4 -> "fifth";
            case 5 -> "sixth";
            case 6 -> "seventh";
            default -> "eighth";
        };
    }
}
