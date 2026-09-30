package com.cosmicbreach.structure.lens;

/**
 * The Lens Array's rules as numbers (GDD 6.2), shared by the tracer, the generator and the solver. Pure: no
 * Minecraft types, so the whole puzzle core is unit tested.
 *
 * <p><b>The grid.</b> {@code n} by {@code n} pedestals, cell {@code (x, z)} at index {@code z * n + x}, x to the
 * east and z to the south (Minecraft's axes). Headings are {@link #NORTH}, {@link #EAST}, {@link #SOUTH},
 * {@link #WEST} (0 to 3, clockwise). Beams run in straight lines from cell centre to cell centre.
 *
 * <p><b>Ports.</b> Round the grid, one step outside each border cell, stand the wall's sockets: port
 * {@code side * n + i} is outside cell {@code i} of that side (north and south count along x, east and west
 * along z). A port holds nothing (the beam ends on the wall), a receptor crystal wanting one colour, or the
 * Warden Eye.
 *
 * <p><b>A cell</b> is one int: kind in bits 0 to 2, turn in bits 3 and 4, colour in bits 5 and 6, loose in bit 7.
 * <ul>
 *   <li>A mirror's turn {@code t} counts 45 degree clockwise steps from a plate lying east to west: 0 "-", 1 "\",
 *       2 "|", 3 "/". Mirrors are silvered on both faces. A beam along the plate passes it, a beam into a
 *       diagonal plate turns 90 degrees, a beam square onto the plate is reflected straight back (it retraces
 *       its path, so the tracer ends it there).</li>
 *   <li>A splitter's turn is the heading it accepts: a beam with that heading leaves as two, turned 90 degrees
 *       either way; from any other side the prism absorbs it. One use turns it 90 degrees.</li>
 *   <li>The source (the focus under the Sun Aperture) emits white light with the heading of its turn.</li>
 *   <li>A filter tints whatever passes it, in any direction.</li>
 *   <li>An Umbral block absorbs.</li>
 * </ul>
 */
public final class Lens {
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};

    public static final int EMPTY = 0;
    public static final int SOURCE = 1;
    public static final int MIRROR = 2;
    public static final int SPLITTER = 3;
    public static final int FILTER = 4;
    public static final int UMBRAL = 5;

    public static final int WHITE = 0;
    public static final int GOLD = 1;
    public static final int TEAL = 2;
    public static final int MAGENTA = 3;
    public static final String[] COLOR_NAMES = {"white", "gold", "teal", "magenta"};

    /** Port contents. */
    public static final int PORT_WALL = 0;
    public static final int PORT_EYE = 1;
    /** A receptor wanting colour {@code c} is {@code PORT_RECEPTOR + c}. */
    public static final int PORT_RECEPTOR = 2;

    /** The most segments one trace may draw (GDD 6.2). */
    public static final int MAX_SEGMENTS = 64;

    /**
     * {@code MIRROR_OUT[t][heading]}: the heading a beam leaves a mirror of turn {@code t} with. Equal to the
     * incoming heading means it passed along the plate; the opposite heading means it hit the plate square.
     */
    static final int[][] MIRROR_OUT = {
            {SOUTH, EAST, NORTH, WEST},   // 0 "-": north and south bounce back, east and west pass
            {WEST, SOUTH, EAST, NORTH},   // 1 "\": north to west, east to south, south to east, west to north
            {NORTH, WEST, SOUTH, EAST},   // 2 "|": north and south pass, east and west bounce back
            {EAST, NORTH, WEST, SOUTH},   // 3 "/": north to east, east to north, south to west, west to south
    };

    private Lens() {
    }

    // ------------------------------------------------------------------ headings

    public static int cw(int d) {
        return (d + 1) & 3;
    }

    public static int ccw(int d) {
        return (d + 3) & 3;
    }

    public static int opposite(int d) {
        return (d + 2) & 3;
    }

    // ------------------------------------------------------------------ cells

    public static int cell(int kind, int turn, int color, boolean loose) {
        return (kind & 7) | (turn & 3) << 3 | (color & 3) << 5 | (loose ? 1 << 7 : 0);
    }

    public static int mirror(int turn, boolean loose) {
        return cell(MIRROR, turn, 0, loose);
    }

    public static int splitter(int accepts) {
        return cell(SPLITTER, accepts, 0, false);
    }

    public static int filter(int color, boolean loose) {
        return cell(FILTER, 0, color, loose);
    }

    public static int source(int heading) {
        return cell(SOURCE, heading, 0, false);
    }

    public static int umbral() {
        return cell(UMBRAL, 0, 0, false);
    }

    public static int kind(int cell) {
        return cell & 7;
    }

    public static int turn(int cell) {
        return cell >> 3 & 3;
    }

    public static int color(int cell) {
        return cell >> 5 & 3;
    }

    public static boolean loose(int cell) {
        return (cell & 1 << 7) != 0;
    }

    public static int withTurn(int cell, int turn) {
        return cell & ~(3 << 3) | (turn & 3) << 3;
    }

    /** True for the pieces a player turns: mirrors and splitters. */
    public static boolean rotatable(int cell) {
        int k = kind(cell);
        return k == MIRROR || k == SPLITTER;
    }

    /** The heading a beam leaves a mirror of turn {@code t} with, arriving with {@code heading}. */
    public static int mirrorOut(int t, int heading) {
        return MIRROR_OUT[t & 3][heading];
    }

    /** The mirror turn that sends a beam arriving with {@code in} out with {@code out} (a right angle). */
    public static int mirrorTurnFor(int in, int out) {
        for (int t = 1; t <= 3; t += 2) {
            if (MIRROR_OUT[t][in] == out) {
                return t;
            }
        }
        throw new IllegalArgumentException("no mirror turns " + in + " into " + out);
    }

    /** Presses between two turns of a four-step piece, either way round. */
    public static int turnDistance(int from, int to) {
        int d = (to - from) & 3;
        return Math.min(d, 4 - d);
    }

    // ------------------------------------------------------------------ ports

    /** The port a beam reaches leaving cell (x, z) with {@code heading}, if that step leaves the grid, else -1. */
    public static int portLeaving(int n, int x, int z, int heading) {
        int nx = x + DX[heading];
        int nz = z + DZ[heading];
        if (nx >= 0 && nx < n && nz >= 0 && nz < n) {
            return -1;
        }
        return switch (heading) {
            case NORTH -> NORTH * n + x;
            case EAST -> EAST * n + z;
            case SOUTH -> SOUTH * n + x;
            default -> WEST * n + z;
        };
    }

    public static int portSide(int n, int port) {
        return port / n;
    }

    /** The port's position in grid units (outside the grid by one cell). */
    public static int portX(int n, int port) {
        int side = port / n;
        int i = port % n;
        return switch (side) {
            case NORTH, SOUTH -> i;
            case EAST -> n;
            default -> -1;
        };
    }

    public static int portZ(int n, int port) {
        int side = port / n;
        int i = port % n;
        return switch (side) {
            case NORTH -> -1;
            case SOUTH -> n;
            default -> i;
        };
    }

    /** The border cell just inside a port. */
    public static int portCell(int n, int port) {
        int x = Math.max(0, Math.min(n - 1, portX(n, port)));
        int z = Math.max(0, Math.min(n - 1, portZ(n, port)));
        return z * n + x;
    }

    /** The heading a beam needs, leaving the port's border cell, to reach the port. */
    public static int portHeading(int n, int port) {
        return port / n;
    }

    public static boolean isReceptor(int port) {
        return port >= PORT_RECEPTOR;
    }

    public static int receptorColor(int port) {
        return port - PORT_RECEPTOR;
    }
}
