package com.cosmicbreach.structure.sanctum;

import java.util.ArrayList;
import java.util.List;

/**
 * The Breach Sanctum's shape (GDD 6.1 and 7.3), as a pure function of a block position: what {@link #kind} stands
 * there. No level, no registry: the builder turns kinds into blocks, the tests read it directly.
 *
 * <p>Two parts, both at the Breach's axis:
 * <ul>
 *   <li><b>The arena</b>, always at the world's origin: a disc of radius 28 whose floor's top is at {@link #ARENA_Y}
 *       (players stand at Y 64), four rings (the dais to 8, the mid ring to 16, the outer ring to 24, the rim to 28,
 *       see {@link SanctumArena}), each ring cut into eight segments clockwise from north, a keel under each ring one
 *       step deeper toward the middle (so every segment is a clean wedge the Collapse can drop), eight Choir Pillars
 *       at radius 22 (one in the middle of each outer segment), the Regent's Throne on the dais at X 0, Z 0, and an
 *       ember pendant hanging under it.</li>
 *   <li><b>The halls</b>, on one side of the arena ({@link #side}: -1 north, +1 south, whichever faces the Deep
 *       platform the causeway reaches; the west wing always lies west): the Throne Stair climbing 20 steps from the
 *       arena's rim to the halls' floor ({@link #HALL_Y}), sealed at its head by the Throne Seal between the two
 *       Eclipse Locks; the Antechamber; a corridor west to the Lens of Solenne (a 7 by 7 Lens Array under a dome) and
 *       one east to the Choir of the Unsung (a Choir Floor under a dome), each with its vault; the Gate at the far
 *       end; the forecourt outside it between two towers; and the causeway from the forecourt out to the Deep
 *       platform at {@link #end}.</li>
 * </ul>
 *
 * <p>Local coordinates: {@code x} is the world's X; {@code d} runs from the arena toward the Gate
 * ({@code d = side * z}), so the halls lie at d 49 to 101. Everything the halls hold is written in (x, d) and
 * turned into the world with {@link #z}.
 */
public final class SanctumLayout {
    // ------------------------------------------------------------------ the arena (world coordinates)

    /** Where players stand on the arena: the floor's blocks are at Y 63. */
    public static final int ARENA_Y = 64;
    public static final int FLOOR_Y = ARENA_Y - 1;
    /** Outer edges of the four rings, measured to block centres from the throne's column. */
    public static final double DAIS_R = 8.5;
    public static final double MID_R = 16.5;
    public static final double OUTER_R = 24.5;
    public static final double RIM_R = 28.5;
    public static final int SEGMENTS = 8;
    public static final double PILLAR_R = 22.0;
    /** A pillar's footprint: blocks within this squared distance of its centre column (13 blocks). */
    public static final double PILLAR_FOOT_SQ = 4.5;
    /** The pillars' ember capitals. */
    public static final int PILLAR_TOP = ARENA_Y + 14;
    /** The pendant under the dais reaches down to here. */
    public static final int PENDANT_Y = 40;
    public static final int[] PILLAR_X = new int[SEGMENTS];
    public static final int[] PILLAR_Z = new int[SEGMENTS];

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            double a = Math.toRadians((i + 0.5) * 45.0);
            PILLAR_X[i] = (int) Math.round(PILLAR_R * Math.sin(a));
            PILLAR_Z[i] = (int) Math.round(-PILLAR_R * Math.cos(a));
        }
    }

    // ------------------------------------------------------------------ the halls (local coordinates)

    /** Where players stand in the halls: their floor's blocks are at Y 83. */
    public static final int HALL_Y = 84;
    public static final int HALL_FLOOR = HALL_Y - 1;
    /** The Throne Stair: 20 steps, step k at d = 29 + k and Y 64 + k, 7 wide; the landing at d 49. */
    public static final int STAIR_D0 = 29;
    public static final int STAIR_STEPS = HALL_Y - ARENA_Y;
    public static final int STAIR_HALF = 3;
    public static final int LANDING_D = STAIR_D0 + STAIR_STEPS;
    /** The Antechamber: walls at |x| 10, d 50 (the Throne Seal's wall) and d 88 (the Gate's). */
    public static final int SEAL_D = 50;
    public static final int GATE_D = 88;
    public static final int ANTE_HALF = 10;
    public static final int ANTE_CEIL = 96;
    /** The seal's doorway: |x| up to 3, Y 84 to 90. */
    public static final int SEAL_TOP = HALL_Y + 6;
    /** The Gate's doorway: |x| up to 2, Y 84 to 89. */
    public static final int GATE_HALF = 2;
    public static final int GATE_TOP = HALL_Y + 5;
    /** The locks, in the seal's wall facing the hall, at x -6 (west) and 6 (east). */
    public static final int LOCK_X = 6;
    public static final int LOCK_Y = HALL_Y + 3;
    /** The wings' axis. */
    public static final int WING_D = 70;
    /** The Lens of Solenne (west): a round room, inside to 13.5 from its axis, its wall to 15.5. */
    public static final int LENS_X = -38;
    public static final double LENS_IN = 13.5;
    public static final double LENS_OUT = 15.5;
    /** Headroom over the Lens Array's pedestal row (the Sun Aperture sits in the ceiling at Y 93). */
    public static final int LENS_CEILING = 9;
    public static final int LENS_CEIL_Y = HALL_Y + LENS_CEILING;
    /** The Choir of the Unsung (east): inside to 11.5, its wall to 13.5, domed from Y 90. */
    public static final int CHOIR_X = 35;
    public static final double CHOIR_IN = 11.5;
    public static final double CHOIR_OUT = 13.5;
    public static final int CHOIR_WALL_TOP = HALL_Y + 6;
    /** The corridors to the wings: west x -25 to -11, east 11 to 24; d 67 to 73 (5 wide, 5 tall inside). */
    public static final int CORRIDOR_WEST0 = -25;
    public static final int CORRIDOR_EAST1 = 24;
    public static final int CORRIDOR_TOP = HALL_Y + 5;
    /** The forecourt outside the Gate: d 89 to 101, |x| up to 13; the causeway leaves from its outer edge. */
    public static final int COURT_D0 = GATE_D + 1;
    public static final int COURT_D1 = 101;
    public static final int COURT_HALF = 13;
    /** The causeway: 5 blocks wide (2.5 each side of its line), its floor ramping to the platform's height. */
    public static final double CAUSEWAY_HALF = 2.6;
    /** Ember posts along the causeway, this far apart. */
    public static final double POST_EVERY = 12.0;

    /** What goes at a block. */
    public enum Kind {
        /** Leave the world as it is. */
        KEEP,
        /** Clear. */
        AIR,
        IVORY,
        BRICKS,
        GILT,
        EMBER,
        /** Rift Glass lit from within: the Sanctum's lamps and windows. */
        LAMP,
        UMBRAL,
        PILLAR,
        /** A step of the Throne Stair, rising toward the halls. */
        STAIR,
        /** A bottom slab (the causeway's ramp). */
        SLAB,
        THRONE,
        GATE,
        SEAL,
        LOCK_WEST,
        LOCK_EAST,
        VAULT_WEST,
        VAULT_EAST
    }

    /** The two wings. */
    public enum Wing {
        /** The Lens of Solenne. */
        WEST,
        /** The Choir of the Unsung. */
        EAST
    }

    private final int side;
    private final int endX;
    private final int endY;
    private final int endZ;
    private final long seed;
    /** The causeway's line from the forecourt's edge (F) to the platform (E), world coordinates. */
    private final double fx;
    private final double fz;
    private final double ux;
    private final double uz;
    private final double run;

    /**
     * @param side -1 when the halls lie north of the arena, +1 south
     * @param endX the causeway's end on the Deep platform (feet level), world coordinates
     * @param seed the world's seed, for the two puzzles
     */
    public SanctumLayout(int side, int endX, int endY, int endZ, long seed) {
        if (side != -1 && side != 1) {
            throw new IllegalArgumentException("side is -1 or 1, not " + side);
        }
        this.side = side;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.seed = seed;
        this.fx = 0.5;
        this.fz = z(COURT_D1) + 0.5;
        double dx = endX + 0.5 - fx;
        double dz = endZ + 0.5 - fz;
        double len = Math.hypot(dx, dz);
        this.run = Math.max(1e-6, len);
        this.ux = len < 1e-6 ? 0.0 : dx / len;
        this.uz = len < 1e-6 ? side : dz / len;
    }

    public int side() {
        return side;
    }

    public long seed() {
        return seed;
    }

    /** The causeway's end on the Deep platform: feet level. */
    public int[] end() {
        return new int[] {endX, endY, endZ};
    }

    /** The causeway's length, in blocks. */
    public double causewayLength() {
        return run;
    }

    /** The causeway ramp's rise per block (absolute). */
    public double causewaySlope() {
        return Math.abs(endY - HALL_Y) / run;
    }

    /** World z of local d. */
    public int z(int d) {
        return side * d;
    }

    /** The puzzles' seeds, fixed per world. */
    public long lensSeed() {
        return mix(seed ^ 0x4C454E53L);
    }

    public long choirSeed() {
        return mix(seed ^ 0x43484F49L);
    }

    // ------------------------------------------------------------------ places, world coordinates {x, y, z}

    /** The Regent's Throne: on the dais at the arena's middle. */
    public static int[] throne() {
        return new int[] {0, ARENA_Y, 0};
    }

    /** The Lens Array's core: the floor block under its middle pedestal. */
    public int[] lensCore() {
        return new int[] {LENS_X, HALL_FLOOR, z(WING_D)};
    }

    /** Where the Choir Floor's Conductor stands (its floor at Y 83). */
    public int[] conductor() {
        return new int[] {CHOIR_X, HALL_Y, z(WING_D)};
    }

    /** A wing's vault: across its room from the corridor. */
    public int[] vault(Wing wing) {
        return wing == Wing.WEST ? new int[] {LENS_X - 12, HALL_Y, z(WING_D)} : new int[] {CHOIR_X + 10, HALL_Y, z(WING_D)};
    }

    /** A wing's Eclipse Lock, in the seal's wall. */
    public int[] lock(Wing wing) {
        return new int[] {wing == Wing.WEST ? -LOCK_X : LOCK_X, LOCK_Y, z(SEAL_D)};
    }

    /** Every block of the Throne Seal (|x| up to 3, Y 84 to 90). */
    public List<int[]> sealBlocks() {
        List<int[]> out = new ArrayList<>();
        for (int y = HALL_Y; y <= SEAL_TOP; y++) {
            for (int x = -STAIR_HALF; x <= STAIR_HALF; x++) {
                out.add(new int[] {x, y, z(SEAL_D)});
            }
        }
        return out;
    }

    /** Every block of the Gate (|x| up to 2, Y 84 to 89). */
    public List<int[]> gateBlocks() {
        List<int[]> out = new ArrayList<>();
        for (int y = HALL_Y; y <= GATE_TOP; y++) {
            for (int x = -GATE_HALF; x <= GATE_HALF; x++) {
                out.add(new int[] {x, y, z(GATE_D)});
            }
        }
        return out;
    }

    /** The middle of the Gate's doorway (block centre coordinates). */
    public double[] gateCentre() {
        return new double[] {0.5, HALL_Y + 3.0, z(GATE_D) + 0.5};
    }

    /** True if (x, z) lies inside the Antechamber's walls (the halls' side of the Gate). */
    public boolean insideHalls(double x, double y, double z) {
        double d = side * (z - 0.5) + 0.5;
        return Math.abs(x - 0.5) < ANTE_HALF - 0.5 && d >= SEAL_D + 1 && d < GATE_D && y >= HALL_Y - 1 && y < ANTE_CEIL;
    }

    /** The head of the Throne Stair: the landing's middle, feet level. */
    public int[] stairHead() {
        return new int[] {0, HALL_Y, z(LANDING_D)};
    }

    // ------------------------------------------------------------------ bounds

    /** {minX, minY, minZ, maxX, maxY, maxZ} of everything built, the causeway included. */
    public int[] bounds() {
        int minX = Math.min(LENS_X - 16, Math.min(0, endX) - 4);
        int maxX = Math.max(CHOIR_X + 14, Math.max(0, endX) + 4);
        int hallsNear = z(STAIR_D0 - 1);
        int hallsFar = z(COURT_D1 + 1);
        int minZ = Math.min(Math.min(-30, Math.min(hallsNear, hallsFar)), endZ - 4);
        int maxZ = Math.max(Math.max(30, Math.max(hallsNear, hallsFar)), endZ + 4);
        int maxY = Math.max(ANTE_CEIL + 12, endY + 6);
        return new int[] {minX, PENDANT_Y, minZ, maxX, maxY, maxZ};
    }

    /** The chunk the structure starts in: the one holding the middle of its bounds. */
    public int[] startChunk() {
        int[] b = bounds();
        return new int[] {Math.floorDiv((b[0] + b[3]) / 2, 16), Math.floorDiv((b[2] + b[5]) / 2, 16)};
    }

    /** True if every chunk the bounds touch lies within 8 chunks of the start chunk (so each one references it). */
    public boolean fitsReferenceRange() {
        int[] b = bounds();
        int[] s = startChunk();
        return Math.floorDiv(b[0], 16) >= s[0] - 8 && Math.floorDiv(b[3], 16) <= s[0] + 8
                && Math.floorDiv(b[2], 16) >= s[1] - 8 && Math.floorDiv(b[5], 16) <= s[1] + 8;
    }

    /** True if the column (x, z) may hold anything (a quick test before {@link #kind}). */
    public boolean touches(int x, int z) {
        if ((double) x * x + (double) z * z <= 30.0 * 30.0) {
            return true;
        }
        int d = side * z;
        if (d >= STAIR_D0 - 1 && d <= COURT_D1 + 1 && x >= LENS_X - 16 && x <= CHOIR_X + 14) {
            return true;
        }
        return causewayOffset(x, z) <= CAUSEWAY_HALF + 0.5;
    }

    // ------------------------------------------------------------------ the kind at a block

    public Kind kind(int x, int y, int z) {
        double r2 = (double) x * x + (double) z * z;
        if (r2 <= 29.0 * 29.0 && y >= PENDANT_Y && y <= PILLAR_TOP) {
            Kind k = arena(x, y, z, r2);
            if (k != null) {
                return k;
            }
        }
        int d = side * z;
        if (Math.abs(x) <= STAIR_HALF + 1 && d >= STAIR_D0 && d <= LANDING_D && y >= ARENA_Y - 4 && y <= HALL_Y + 6) {
            Kind k = stair(x, y, d);
            if (k != null) {
                return k;
            }
        }
        Kind c = causeway(x, y, z);
        if (c != null) {
            return c;
        }
        if (d >= SEAL_D && d <= COURT_D1 && x >= LENS_X - 16 && x <= CHOIR_X + 14 && y >= HALL_FLOOR - 24 && y <= ANTE_CEIL + 12) {
            Kind k = halls(x, y, d);
            if (k != null) {
                return k;
            }
        }
        return Kind.KEEP;
    }

    // ------------------------------------------------------------------ the arena

    /** The ring a column belongs to: 0 dais, 1 mid, 2 outer, 3 rim, -1 off the disc. */
    public static int ring(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r <= DAIS_R) {
            return 0;
        }
        if (r <= MID_R) {
            return 1;
        }
        if (r <= OUTER_R) {
            return 2;
        }
        return r <= RIM_R ? 3 : -1;
    }

    /** The ring's outer edge. */
    public static double ringOuter(int ring) {
        return switch (ring) {
            case 0 -> DAIS_R;
            case 1 -> MID_R;
            case 2 -> OUTER_R;
            default -> RIM_R;
        };
    }

    /** The segment a column belongs to: 0 to 7 clockwise from north (segment 0 spans north to north-east). */
    public static int segment(int x, int z) {
        return segmentOfAngle(angle(x, z));
    }

    public static int segmentOfAngle(double angle) {
        int s = (int) Math.floor(angle / (Math.PI / 4));
        return Math.floorMod(s, SEGMENTS);
    }

    /** The angle of (x, z) around the throne, clockwise from north, in [0, 2 pi). */
    public static double angle(double x, double z) {
        double a = Math.atan2(x, -z);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** The lowest Y of a ring's keel: each ring one step deeper toward the middle. */
    public static int keelBottom(int ring) {
        return switch (ring) {
            case 0 -> FLOOR_Y - 7;
            case 1 -> FLOOR_Y - 4;
            case 2 -> FLOOR_Y - 2;
            default -> FLOOR_Y - 1;
        };
    }

    /** The pillar standing at column (x, z), or -1. */
    public static int pillarAt(int x, int z) {
        for (int i = 0; i < SEGMENTS; i++) {
            int dx = x - PILLAR_X[i];
            int dz = z - PILLAR_Z[i];
            if (dx * dx + dz * dz <= PILLAR_FOOT_SQ) {
                return i;
            }
        }
        return -1;
    }

    /** True if the block belongs to the arena's disc (floor or keel, not the pendant or a pillar). */
    public static boolean discBlock(int x, int y, int z) {
        int ring = ring(x, z);
        return ring >= 0 && y <= FLOOR_Y && y >= keelBottom(ring);
    }

    private Kind arena(int x, int y, int z, double r2) {
        int p = pillarAt(x, z);
        if (p >= 0 && y >= ARENA_Y && y <= PILLAR_TOP) {
            if (y == PILLAR_TOP) {
                return Kind.EMBER;
            }
            return y == ARENA_Y || y == PILLAR_TOP - 1 ? Kind.GILT : Kind.PILLAR;
        }
        if (x == 0 && z == 0 && y == ARENA_Y) {
            return Kind.THRONE;
        }
        int ring = ring(x, z);
        if (ring < 0) {
            return null;
        }
        double r = Math.sqrt(r2);
        if (y == FLOOR_Y) {
            return floor(x, z, r, ring);
        }
        if (y < FLOOR_Y) {
            if (y >= keelBottom(ring)) {
                // the keel's outer face of each ring shows ivory, its underside the dark stone and a few embers
                if (y == keelBottom(ring) && hash(x, y, z) % 19 == 0) {
                    return Kind.EMBER;
                }
                return r > ringOuter(ring) - 1.0 && y == FLOOR_Y - 1 ? Kind.BRICKS : Kind.UMBRAL;
            }
            // the pendant: a cone of dark stone under the dais, an ember core and tip
            int top = keelBottom(0) - 1;
            if (y <= top && y >= PENDANT_Y && r <= 4.6) {
                double reach = 4.6 * (y - PENDANT_Y + 1) / (double) (top - PENDANT_Y + 1);
                if (r <= reach) {
                    return r <= 1.0 || y <= PENDANT_Y + 1 ? Kind.EMBER : Kind.UMBRAL;
                }
            }
            return null;
        }
        return Kind.AIR;
    }

    /** The arena floor's pattern: gold rays on the dais, ember lines at the rings' edges, gold seams between segments. */
    private static Kind floor(int x, int z, double r, int ring) {
        if (ring < 3 && r > ringOuter(ring) - 1.0) {
            return Kind.EMBER;
        }
        if (ring == 3 && r > RIM_R - 0.9) {
            return Kind.GILT;
        }
        if (ring > 0 && seam(x, z, r)) {
            return Kind.GILT;
        }
        return switch (ring) {
            case 0 -> r <= 1.5 || Math.floorMod((int) Math.floor(angle(x, z) / (Math.PI / 8)), 2) == 0 ? Kind.GILT : Kind.IVORY;
            case 1 -> Kind.IVORY;
            case 2 -> Kind.BRICKS;
            default -> Kind.UMBRAL;
        };
    }

    /** True on the thin radial lines between segments. */
    static boolean seam(int x, int z, double r) {
        double a = angle(x, z);
        double step = Math.PI / 4;
        double off = Math.abs(a - Math.round(a / step) * step);
        return off * r < 0.55;
    }

    // ------------------------------------------------------------------ the Throne Stair

    private static Kind stair(int x, int y, int d) {
        int ax = Math.abs(x);
        if (d < LANDING_D) {
            int k = d - STAIR_D0;
            int top = ARENA_Y + k;
            if (ax <= STAIR_HALF) {
                if (y == top) {
                    return Kind.STAIR;
                }
                if (y < top && y >= top - 3) {
                    return y == top - 1 ? Kind.BRICKS : Kind.UMBRAL;
                }
                return y > top && y <= top + 5 ? Kind.AIR : null;
            }
            // the balustrade, an ember lamp every fifth step
            if (y == top + 1) {
                return k % 5 == 0 ? Kind.EMBER : Kind.IVORY;
            }
            if (y <= top && y >= top - 3) {
                return y == top ? Kind.GILT : Kind.UMBRAL;
            }
            return null;
        }
        // the landing
        if (y == HALL_FLOOR) {
            return ax > STAIR_HALF ? Kind.GILT : Kind.IVORY;
        }
        if (y < HALL_FLOOR && y >= HALL_FLOOR - 3) {
            return Kind.UMBRAL;
        }
        if (ax > STAIR_HALF) {
            return y == HALL_Y ? Kind.IVORY : null;
        }
        return y > HALL_FLOOR && y <= HALL_Y + 5 ? Kind.AIR : null;
    }

    // ------------------------------------------------------------------ the halls (local x, d)

    private Kind halls(int x, int y, int d) {
        if (y < HALL_FLOOR) {
            return keel(x, y, d);
        }
        Kind k = corridor(x, y, d);
        if (k != null) {
            return k;
        }
        k = antechamber(x, y, d);
        if (k != null) {
            return k;
        }
        k = lensRoom(x, y, d);
        if (k != null) {
            return k;
        }
        k = choirRoom(x, y, d);
        if (k != null) {
            return k;
        }
        k = gatehouse(x, y, d);
        if (k != null) {
            return k;
        }
        return forecourt(x, y, d);
    }

    /** How deep the halls' keel reaches under column (x, d): 0 outside their floor. */
    int hallDepth(int x, int d) {
        int best = 0;
        int ax = Math.abs(x);
        if (ax <= ANTE_HALF && d >= SEAL_D && d <= GATE_D) {
            best = Math.max(best, 3 + (ANTE_HALF - ax) / 2);
        }
        if (Math.abs(d - WING_D) <= 3 && ((x >= CORRIDOR_WEST0 && x <= -ANTE_HALF) || (x >= ANTE_HALF && x <= CORRIDOR_EAST1))) {
            best = Math.max(best, 3);
        }
        double rl = Math.hypot(x - LENS_X, d - WING_D);
        if (rl <= LENS_OUT) {
            best = Math.max(best, 3 + (int) ((LENS_OUT - rl) * 0.6));
        }
        double rc = Math.hypot(x - CHOIR_X, d - WING_D);
        if (rc <= CHOIR_OUT) {
            best = Math.max(best, 3 + (int) ((CHOIR_OUT - rc) * 0.6));
        }
        if (ax <= COURT_HALF && d >= COURT_D0 && d <= COURT_D1) {
            best = Math.max(best, d >= COURT_D1 - 4 ? 2 : 3);
        }
        return best;
    }

    private Kind keel(int x, int y, int d) {
        int depth = hallDepth(x, d);
        if (depth > 0 && y >= HALL_FLOOR - depth) {
            if (y == HALL_FLOOR - depth && hash(x, d, 7) % 23 == 0) {
                return Kind.EMBER;
            }
            return Kind.UMBRAL;
        }
        // an ember-tipped pendant under each wing's dome
        for (int w = 0; w < 2; w++) {
            int cx = w == 0 ? LENS_X : CHOIR_X;
            double r = Math.hypot(x - cx, d - WING_D);
            int top = HALL_FLOOR - hallDepth(cx, WING_D) - 1;
            int bottom = top - 12;
            if (y <= top && y >= bottom && r <= 3.6) {
                double reach = 3.6 * (y - bottom + 1) / (double) (top - bottom + 1);
                if (r <= reach) {
                    return r <= 0.8 || y == bottom ? Kind.EMBER : Kind.UMBRAL;
                }
            }
        }
        return null;
    }

    private static Kind corridor(int x, int y, int d) {
        boolean west = x >= CORRIDOR_WEST0 && x <= -ANTE_HALF;
        boolean east = x >= ANTE_HALF && x <= CORRIDOR_EAST1;
        int off = Math.abs(d - WING_D);
        if (!(west || east) || off > 3 || y > CORRIDOR_TOP + 1) {
            return null;
        }
        if (y == HALL_FLOOR) {
            return off == 0 ? Kind.GILT : off == 3 ? Kind.BRICKS : Kind.IVORY;
        }
        if (off == 3 || y >= CORRIDOR_TOP) {
            if (y == CORRIDOR_TOP + 1) {
                return off == 3 ? Kind.GILT : Kind.BRICKS;
            }
            // a lamp in each side wall halfway along
            int mid = west ? (CORRIDOR_WEST0 - ANTE_HALF) / 2 : (ANTE_HALF + CORRIDOR_EAST1) / 2;
            if (off == 3 && x == mid && y == HALL_Y + 2) {
                return Kind.LAMP;
            }
            return y == HALL_Y ? Kind.GILT : Kind.BRICKS;
        }
        return Kind.AIR;
    }

    private static Kind antechamber(int x, int y, int d) {
        int ax = Math.abs(x);
        if (ax > ANTE_HALF || d < SEAL_D || d > GATE_D || y > ANTE_CEIL + 2) {
            return null;
        }
        boolean wall = ax == ANTE_HALF || d == SEAL_D || d == GATE_D;
        if (y == HALL_FLOOR) {
            if (wall) {
                return Kind.BRICKS;
            }
            if (ax <= 1) {
                return ax == 0 && Math.floorMod(d - SEAL_D, 6) == 3 ? Kind.EMBER : Kind.GILT;
            }
            return ax == ANTE_HALF - 1 ? Kind.GILT : Kind.IVORY;
        }
        if (y == ANTE_CEIL + 2) {
            // the parapet: merlons along the roof's edge
            return wall && Math.floorMod(x + d, 2) == 0 ? Kind.BRICKS : null;
        }
        if (y == ANTE_CEIL + 1) {
            return wall ? Kind.GILT : Kind.BRICKS;
        }
        if (wall) {
            if (d == SEAL_D) {
                if (ax <= STAIR_HALF && y >= HALL_Y && y <= SEAL_TOP) {
                    return Kind.SEAL;
                }
                if (ax == LOCK_X && y == LOCK_Y) {
                    return x < 0 ? Kind.LOCK_WEST : Kind.LOCK_EAST;
                }
                if (ax >= LOCK_X - 1 && ax <= LOCK_X + 1 && y >= LOCK_Y - 1 && y <= LOCK_Y + 1) {
                    return Kind.GILT;
                }
                if (ax == STAIR_HALF + 1 && y <= SEAL_TOP + 1) {
                    return Kind.GILT;
                }
                if (ax <= STAIR_HALF + 1 && y == SEAL_TOP + 1) {
                    return Kind.GILT;
                }
            }
            if (d == GATE_D) {
                if (ax <= GATE_HALF && y >= HALL_Y && y <= GATE_TOP) {
                    return Kind.GATE;
                }
                if (ax <= GATE_HALF + 1 && y <= GATE_TOP + 1) {
                    return Kind.GILT;
                }
            }
            if (ax == ANTE_HALF && Math.abs(d - WING_D) <= 2 && y >= HALL_Y && y < CORRIDOR_TOP) {
                return Kind.AIR;
            }
            if (ax == ANTE_HALF && y >= HALL_Y + 3 && y <= HALL_Y + 8 && isWindow(d)) {
                return Kind.LAMP;
            }
            return y == HALL_Y || y == ANTE_CEIL - 1 || y == ANTE_CEIL ? Kind.GILT : Kind.BRICKS;
        }
        if (y == ANTE_CEIL) {
            return Math.floorMod(d - SEAL_D, 6) == 0 ? Kind.GILT : Kind.IVORY;
        }
        if (y == ANTE_CEIL - 1 && x == 0 && Math.floorMod(d - SEAL_D, 12) == 6) {
            return Kind.LAMP;
        }
        if (ax == 7 && isColumn(d)) {
            return y == HALL_Y || y == ANTE_CEIL - 1 ? Kind.GILT : Kind.PILLAR;
        }
        return Kind.AIR;
    }

    private static boolean isWindow(int d) {
        int k = d - SEAL_D;
        return k == 5 || k == 11 || k == 27 || k == 33;
    }

    private static boolean isColumn(int d) {
        int k = d - SEAL_D;
        return k == 8 || k == 14 || k == 26 || k == 32;
    }

    private static Kind lensRoom(int x, int y, int d) {
        double r = Math.hypot(x - LENS_X, d - WING_D);
        if (r > LENS_OUT + 0.01) {
            return null;
        }
        boolean wall = r > LENS_IN;
        if (y == HALL_FLOOR) {
            if (wall) {
                return Kind.BRICKS;
            }
            // gold rings under the walkway, ivory under the grid
            return r > 11.5 && r <= 12.5 ? Kind.GILT : Kind.IVORY;
        }
        if (y > LENS_CEIL_Y) {
            return dome(x - LENS_X, y - LENS_CEIL_Y, d - WING_D, LENS_OUT, 7.5);
        }
        if (wall) {
            double a = Math.atan2(d - WING_D, x - LENS_X);
            if (y == HALL_Y || y == LENS_CEIL_Y || y == LENS_CEIL_Y - 1) {
                return Kind.GILT;
            }
            if (y >= HALL_Y + 3 && y <= HALL_Y + 6 && windowAngle(a, 8, 0.0)) {
                return Kind.LAMP;
            }
            return Kind.BRICKS;
        }
        if (y == LENS_CEIL_Y) {
            // the ceiling over the grid (the Sun Aperture replaces one block of it), lamps round its edge
            if (r > 10.5 && r <= 11.5 && Math.floorMod(Math.round(Math.toDegrees(Math.atan2(d - WING_D, x - LENS_X))), 45) == 0) {
                return Kind.LAMP;
            }
            return r > 12.5 ? Kind.GILT : Kind.IVORY;
        }
        if (x == LENS_X - 12 && d == WING_D && y == HALL_Y) {
            return Kind.VAULT_WEST;
        }
        if (x == LENS_X - 12 && Math.abs(d - WING_D) == 1 && y <= HALL_Y + 1) {
            return Kind.GILT;
        }
        return Kind.AIR;
    }

    private static Kind choirRoom(int x, int y, int d) {
        double r = Math.hypot(x - CHOIR_X, d - WING_D);
        if (r > CHOIR_OUT + 0.01) {
            return null;
        }
        boolean wall = r > CHOIR_IN;
        if (y == HALL_FLOOR) {
            if (wall) {
                return Kind.BRICKS;
            }
            return r > 8.5 && r <= 9.5 ? Kind.GILT : Kind.IVORY;
        }
        if (y > CHOIR_WALL_TOP) {
            int h = y - CHOIR_WALL_TOP;
            double inner = (r / CHOIR_IN) * (r / CHOIR_IN) + (h / 8.0) * (h / 8.0);
            if (inner < 1.0) {
                return Kind.AIR;
            }
            return dome(x - CHOIR_X, h, d - WING_D, CHOIR_OUT, 10.0);
        }
        if (wall) {
            double a = Math.atan2(d - WING_D, x - CHOIR_X);
            if (y == HALL_Y || y == CHOIR_WALL_TOP) {
                return Kind.GILT;
            }
            if (y >= HALL_Y + 2 && y <= HALL_Y + 4 && windowAngle(a, 12, Math.PI / 12)) {
                return Kind.LAMP;
            }
            return Kind.BRICKS;
        }
        if (x == CHOIR_X + 10 && d == WING_D && y == HALL_Y) {
            return Kind.VAULT_EAST;
        }
        if (x == CHOIR_X + 10 && Math.abs(d - WING_D) == 1 && y <= HALL_Y + 1) {
            return Kind.GILT;
        }
        return Kind.AIR;
    }

    /**
     * A dome's shell over a round room: solid under the ellipsoid of radius {@code radius} and height {@code height}
     * ({@code h} counted from its base), gold ribs every 45 degrees, lamps between them, an ember finial.
     */
    private static Kind dome(int dx, int h, int dd, double radius, double height) {
        double r = Math.hypot(dx, dd);
        double e = (r / radius) * (r / radius) + (h / height) * (h / height);
        if (e > 1.0) {
            if (r <= 0.5 && h <= height + 2) {
                return Kind.EMBER;
            }
            return null;
        }
        double a = Math.atan2(dd, dx);
        double k = a / (Math.PI / 4);
        double off = Math.abs(k - Math.round(k)) * r;
        if (off < 0.6) {
            return Kind.GILT;
        }
        return Kind.BRICKS;
    }

    /** True within a narrow arc at each of {@code n} evenly spaced angles, except toward the corridor and the vault. */
    private static boolean windowAngle(double a, int n, double phase) {
        double step = 2 * Math.PI / n;
        double k = (a - phase) / step;
        double off = Math.abs(k - Math.round(k)) * step;
        if (off > 0.07) {
            return false;
        }
        // not toward the corridor (east for the west room, west for the east room) nor the vault opposite
        double c = Math.abs(Math.cos(a));
        return c < 0.93;
    }

    private static Kind gatehouse(int x, int y, int d) {
        int ax = Math.abs(x);
        if (d < COURT_D0 || d > COURT_D0 + 3) {
            return null;
        }
        // two towers flanking the Gate, an ember crown on each
        if (ax >= 4 && ax <= 8) {
            int top = ANTE_CEIL + 8;
            boolean edge = ax == 4 || ax == 8 || d == COURT_D0 || d == COURT_D0 + 3;
            if (y == HALL_FLOOR) {
                return Kind.BRICKS;
            }
            if (y > HALL_FLOOR && y <= top) {
                if (!edge) {
                    return y == top ? Kind.BRICKS : Kind.UMBRAL;
                }
                if (y == HALL_Y || y == ANTE_CEIL || y == top) {
                    return Kind.GILT;
                }
                if (ax == 6 && d == COURT_D0 + 3 && y >= HALL_Y + 6 && y <= HALL_Y + 10) {
                    return Kind.LAMP;
                }
                return Kind.BRICKS;
            }
            boolean crown = ax == 6 && (d == COURT_D0 + 1 || d == COURT_D0 + 2);
            if (crown && y <= top + 2) {
                return Kind.EMBER;
            }
            return edge && y == top + 1 && Math.floorMod(x + d, 2) == 0 ? Kind.GILT : null;
        }
        // the Gate's frame, and an eclipse over it: a dark disc in an ember corona in a gold ring
        if (d == COURT_D0 && ax <= 3 && y >= HALL_Y) {
            if (y <= GATE_TOP) {
                return ax == 3 ? Kind.GILT : null;
            }
            if (y == GATE_TOP + 1) {
                return Kind.GILT;
            }
            int dy = y - (GATE_TOP + 5);
            int rr = ax * ax + dy * dy;
            if (dy <= 3) {
                if (rr <= 4) {
                    return Kind.UMBRAL;
                }
                if (rr <= 9) {
                    return Kind.EMBER;
                }
                if (rr <= 13) {
                    return Kind.GILT;
                }
            }
        }
        return null;
    }

    private static Kind forecourt(int x, int y, int d) {
        int ax = Math.abs(x);
        if (ax > COURT_HALF || d < COURT_D0 || d > COURT_D1) {
            return null;
        }
        boolean edge = ax == COURT_HALF || d == COURT_D1;
        if (y == HALL_FLOOR) {
            if (edge) {
                return Kind.GILT;
            }
            if (ax == 0 && d == COURT_D0 + 6) {
                return Kind.EMBER;
            }
            return ax <= 1 ? Kind.GILT : Kind.IVORY;
        }
        if (edge && y == HALL_Y) {
            return (ax == COURT_HALF && Math.floorMod(d - COURT_D0, 4) == 0) || (d == COURT_D1 && Math.floorMod(x, 4) == 0)
                    ? Kind.EMBER : Kind.IVORY;
        }
        // two braziers
        if (ax == 9 && d == COURT_D0 + 7) {
            return y == HALL_Y ? Kind.GILT : y == HALL_Y + 1 ? Kind.EMBER : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ the causeway (world coordinates)

    /** How far column (x, z) lies from the causeway's line, sideways; infinite off its ends. */
    double causewayOffset(int x, int z) {
        double px = x + 0.5 - fx;
        double pz = z + 0.5 - fz;
        double along = px * ux + pz * uz;
        if (along < 0.0 || along > run + 0.5) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(px * uz - pz * ux);
    }

    /** Feet height of the causeway's ramp at column (x, z), in half blocks (a Y times two), or -1 off the causeway. */
    public int causewayFeet2(int x, int z) {
        double px = x + 0.5 - fx;
        double pz = z + 0.5 - fz;
        double along = px * ux + pz * uz;
        if (along < 0.0 || along > run + 0.5 || Math.abs(px * uz - pz * ux) > CAUSEWAY_HALF) {
            return -1;
        }
        double t = Math.min(1.0, along / run);
        double feet = HALL_Y + (endY - HALL_Y) * t;
        return (int) Math.round(feet * 2.0);
    }

    private Kind causeway(int x, int y, int z) {
        double px = x + 0.5 - fx;
        double pz = z + 0.5 - fz;
        double along = px * ux + pz * uz;
        if (along < 0.0 || along > run + 0.5) {
            return null;
        }
        double cross = px * uz - pz * ux;
        double off = Math.abs(cross);
        if (off > CAUSEWAY_HALF) {
            return null;
        }
        double t = Math.min(1.0, along / run);
        int feet2 = (int) Math.round((HALL_Y + (endY - HALL_Y) * t) * 2.0);
        boolean half = (feet2 & 1) == 1;
        int top = half ? feet2 >> 1 : (feet2 >> 1) - 1;
        boolean edge = off > 1.6;
        if (y == top) {
            return half ? Kind.SLAB : edge ? Kind.GILT : Kind.IVORY;
        }
        if (half && y == top - 1) {
            return edge ? Kind.GILT : Kind.IVORY;
        }
        if (y < top && y >= top - 3) {
            return Kind.UMBRAL;
        }
        if (y > top && y <= top + 4) {
            // ember posts at the edge, alternating sides; a pair where the causeway meets the platform
            if (off > 2.0) {
                boolean left = cross > 0;
                int n = (int) Math.floor(along / POST_EVERY);
                boolean post = along > 4.0 && along - n * POST_EVERY < 1.0 && (n % 2 == 0) == left;
                boolean waystone = along > run - 1.5;
                if (post || waystone) {
                    int h = y - top;
                    if (h == 1 || (waystone && h == 2)) {
                        return Kind.IVORY;
                    }
                    if (h == (waystone ? 3 : 2)) {
                        return Kind.EMBER;
                    }
                }
            }
            return Kind.AIR;
        }
        return null;
    }

    // ------------------------------------------------------------------ routes (for tests: block centres, feet level)

    /** From the causeway's end on the platform over the causeway, across the forecourt, through the Gate, into the hall. */
    public List<double[]> approach() {
        List<double[]> out = new ArrayList<>();
        double len = run;
        int steps = Math.max(1, (int) Math.ceil(len / 8.0));
        for (int i = steps; i >= 0; i--) {
            double along = len * i / steps;
            double x = fx + ux * along;
            double z = fz + uz * along;
            double y = HALL_Y + (endY - HALL_Y) * (along / len);
            out.add(new double[] {x, y, z});
        }
        out.add(local(0, COURT_D0 + 5));
        out.add(local(0, COURT_D0 + 1));
        out.add(local(0, GATE_D - 2));
        out.add(local(0, WING_D + 6));
        return out;
    }

    /** From the Antechamber's middle down the west corridor and round the Lens Array to its vault. */
    public List<double[]> toWing(Wing wing) {
        List<double[]> out = new ArrayList<>();
        out.add(local(0, WING_D));
        if (wing == Wing.WEST) {
            // round the grid on the walkway, well outside its sockets, to the vault's front
            out.add(local(-ANTE_HALF + 1, WING_D));
            out.add(local(CORRIDOR_WEST0 + 1, WING_D));
            for (int deg = 0; deg <= 150; deg += 15) {
                double a = Math.toRadians(deg);
                out.add(local(LENS_X + 11.5 * Math.cos(a), WING_D + 11.5 * Math.sin(a)));
            }
            out.add(local(LENS_X - 10.5, WING_D));
        } else {
            // round the Choir's ring without stepping on it (that would wake it), to the vault's front
            out.add(local(ANTE_HALF - 1, WING_D));
            out.add(local(CORRIDOR_EAST1 - 1, WING_D));
            for (int deg = 180; deg >= 30; deg -= 15) {
                double a = Math.toRadians(deg);
                out.add(local(CHOIR_X + 9.9 * Math.cos(a), WING_D + 9.9 * Math.sin(a)));
            }
            out.add(local(CHOIR_X + 8.6, WING_D));
        }
        return out;
    }

    /** Back from a wing's vault to the Antechamber's middle. */
    public List<double[]> fromWing(Wing wing) {
        List<double[]> there = toWing(wing);
        List<double[]> out = new ArrayList<>();
        for (int i = there.size() - 1; i >= 0; i--) {
            out.add(there.get(i));
        }
        return out;
    }

    /** From the Antechamber's middle through the (open) seal, down the Throne Stair and onto the arena's rim. */
    public List<double[]> descent() {
        List<double[]> out = new ArrayList<>();
        out.add(local(0, WING_D));
        out.add(local(0, SEAL_D + 2));
        out.add(local(0, LANDING_D));
        double[] foot = local(0, STAIR_D0 - 1);
        foot[1] = ARENA_Y;
        out.add(foot);
        double[] rim = local(0, 26);
        rim[1] = ARENA_Y;
        out.add(rim);
        return out;
    }

    /** A world point {x, feet y, z} at local (x, d) on the halls' floor. */
    public double[] local(double x, double d) {
        return new double[] {x + 0.5, HALL_Y, side * d + 0.5};
    }

    // ------------------------------------------------------------------ helpers

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static int hash(int x, int y, int z) {
        long h = mix(x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L);
        return (int) Math.floorMod(h, 1_000_003L);
    }
}
