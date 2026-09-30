package com.cosmicbreach.guardian.unsung;

import java.util.ArrayList;
import java.util.List;

/**
 * The Silent Nave's shape (Unsung design v1, silhouette G8p), pure: which block goes where for a nave whose apse is
 * centred on the block corner {@code (cx, cz)} with its choir floor's first air at {@code floorY}, and whose nave runs
 * from the apse toward {@code facing} (0 east +X, 1 south +Z, 2 west -X, 3 north -Z) to its door. A ruined cathedral of
 * Umbral Basalt: a long nave with basalt columns under a steep gabled roof, its lancet windows of lichen-lit glass
 * glowing from outside (their light stays under what Hollow Stalkers shun, so they still follow players in), flying
 * buttresses with pinnacles along it, a west front between two spired towers with a rose window over the door, a lantern
 * spire on the ridge, a cracked lectern at the apse's entrance (the Hymnal Altar), and the round apse, the choir floor
 * ({@link ChoirArena}): a disc of radius 16 under a dome ringed with pinnacles, its lichen-lit windows, the raised dais
 * and the eight circles of silence. Under it all a keel of basalt tapers down into the Deep, with pendants under the
 * piers and towers. Rubble and holes in the nave's roof come from {@code seed}; the apse is whole and dark.
 *
 * <p>Local axes: {@code u} runs from the apse's centre toward the door, {@code v} across it (positive to the right,
 * looking at the door).
 */
public record NaveLayout(int cx, int cz, int floorY, int facing, long seed) {
    public static final double APSE_WALL_IN = ChoirArena.FLOOR_RADIUS;
    public static final double APSE_WALL_OUT = APSE_WALL_IN + 2.0;
    /** Buttresses reach this far out round the apse. */
    public static final double BUTTRESS_OUT = APSE_WALL_OUT + 2.0;
    /** The apse wall's top over the floor; the dome rises from it. */
    public static final int APSE_WALL_TOP = 12;
    public static final int DOME_RISE = 8;
    public static final double NAVE_HALF = 6.5;
    public static final double NAVE_WALL = NAVE_HALF + 2.0;
    public static final double NAVE_START = 12.0;
    public static final double NAVE_END = 46.5;
    public static final double DOOR_WALL = NAVE_END + 2.0;
    public static final double LANDING = DOOR_WALL + 3.0;
    public static final int NAVE_WALL_TOP = 11;
    public static final int NAVE_RISE = 5;
    /** The altar's place along the nave: just past the apse's arch. */
    public static final int ALTAR_U = 19;
    /** How deep the keel reaches under the apse's centre. */
    public static final int KEEL_DEPTH = 30;
    public static final int ROOF_MAX = APSE_WALL_TOP + DOME_RISE + 2;
    /** The nave's roof ridge over the floor: the gable falls one block a block to the eaves. */
    public static final int GABLE_TOP = 21;
    /** The west towers: their footprint (local), the top of their walls and of their spires. */
    public static final double TOWER_U0 = NAVE_END - 4.5;
    public static final double TOWER_U1 = DOOR_WALL + 0.5;
    public static final double TOWER_V0 = NAVE_WALL - 0.5;
    public static final double TOWER_V1 = NAVE_WALL + 4.5;
    public static final int TOWER_TOP = 24;
    public static final int SPIRE_TOP = 32;
    /**
     * Nothing of the Nave reaches over this Y: the Deep's band tops out at 140 and Shear band B runs from 145. When the
     * floor is high the towers and their spires shorten to fit; the floor never moves for them.
     */
    public static final int TOP_Y = 143;
    /** The flying buttresses' piers stand this far out. */
    public static final double PIER_IN = NAVE_WALL + 2.5;
    public static final double PIER_OUT = NAVE_WALL + 4.0;
    /** The rose window over the door: its middle's height over the floor and its radius. */
    public static final double ROSE_H = 11.5;
    public static final double ROSE_R = 5.0;
    /** The lantern spire on the ridge, just past the apse. */
    public static final double FLECHE_U = 20.0;
    public static final int FLECHE_TOP = 30;

    /** What each block of the nave is. */
    public enum Kind {
        /** Leave the world as it is. */
        KEEP,
        AIR,
        FLOOR, FLOOR_BAND, DAIS, DAIS_EDGE, STEP, CIRCLE,
        WALL, WALL_BASE, COLUMN, RIB, ROOF, ROOF_RIB,
        WINDOW, LICHEN_WINDOW_MAGENTA, LICHEN_WINDOW_TEAL,
        /** The nave's, the towers' and the rose's windows: lit lichen glass the fight does not dim. */
        NAVE_WINDOW_MAGENTA, NAVE_WINDOW_TEAL,
        /** The gable's slope (stairs rising to the ridge), the ridge, a spire's body, a pinnacle's post. */
        ROOF_SLOPE, RIDGE, SPIRE, PINNACLE,
        ALTAR, KEEL, KEEL_BAND, RUBBLE, RUBBLE_SLAB
    }

    /** A patch of Neon Lichen: the air block it grows in and the side it clings to (0 +X, 1 +Z, 2 -X, 3 -Z). */
    public record Lichen(int x, int y, int z, int side, boolean magenta) {
    }

    public ChoirArena arena() {
        return new ChoirArena(cx, floorY, cz);
    }

    /**
     * The tallest block over the floor: the spires' full height, or less so their tips stay at or under {@link #TOP_Y},
     * but never below the dome (a lair built for a check higher than any site keeps its body and loses only spire).
     */
    public int heightCap() {
        return Math.min(SPIRE_TOP, Math.max(ROOF_MAX + 1, TOP_Y - floorY));
    }

    /** The west towers' walls: full height, or lower so a spire still crowns them under the cap. */
    public int towerTop() {
        return Math.max(16, Math.min(TOWER_TOP, heightCap() - 5));
    }

    // ------------------------------------------------------------------ axes

    /** The unit vector toward the door: {x, z}. */
    public int[] axis() {
        return switch (Math.floorMod(facing, 4)) {
            case 0 -> new int[] {1, 0};
            case 1 -> new int[] {0, 1};
            case 2 -> new int[] {-1, 0};
            default -> new int[] {0, -1};
        };
    }

    /** Local {u, v} of a block column's centre. */
    public double[] local(int x, int z) {
        int[] a = axis();
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        double u = dx * a[0] + dz * a[1];
        double v = -dx * a[1] + dz * a[0];
        return new double[] {u, v};
    }

    /** The block column at local {@code (u, v)} (block centres). */
    public int[] world(double u, double v) {
        int[] a = axis();
        double x = cx + u * a[0] - v * a[1];
        double z = cz + u * a[1] + v * a[0];
        return new int[] {(int) Math.floor(x), (int) Math.floor(z)};
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of everything the nave builds. */
    public int[] bounds() {
        int r = (int) Math.ceil(BUTTRESS_OUT) + 1;
        int[] a = axis();
        int farX = (int) Math.floor(cx + LANDING * a[0]);
        int farZ = (int) Math.floor(cz + LANDING * a[1]);
        int side = (int) Math.ceil(TOWER_V1 + 1.0);
        int minX = Math.min(cx - r, farX - side);
        int maxX = Math.max(cx + r, farX + side);
        int minZ = Math.min(cz - r, farZ - side);
        int maxZ = Math.max(cz + r, farZ + side);
        return new int[] {minX, floorY - KEEL_DEPTH - 2, minZ, maxX, floorY + heightCap() + 1, maxZ};
    }

    /** The altar's block, and the side its reader stands on (toward the door). */
    public int[] altar() {
        int[] w = world(ALTAR_U + 0.5, 0.0);
        return new int[] {w[0], floorY, w[1]};
    }

    // ------------------------------------------------------------------ the blocks

    public Kind kind(int x, int y, int z) {
        int[] alt = altar();
        if (x == alt[0] && y == alt[1] && z == alt[2]) {
            return Kind.ALTAR;
        }
        double[] l = local(x, z);
        double u = l[0];
        double v = l[1];
        double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
        int h = y - floorY;
        if (h > heightCap()) {
            return Kind.KEEP;
        }
        boolean inApse = d <= BUTTRESS_OUT;
        boolean inNave = u >= NAVE_START && u <= LANDING && Math.abs(v) <= TOWER_V1 + 0.5;
        if (!inApse && !inNave) {
            return Kind.KEEP;
        }
        if (h < -1) {
            return keel(u, v, d, h, inApse);
        }
        Kind apse = inApse ? apse(x, z, u, v, d, h) : Kind.KEEP;
        Kind nave = inNave ? nave(x, z, u, v, h) : Kind.KEEP;
        return merge(apse, nave, d);
    }

    /** Where the apse and the nave overlap (the arch), the nave's opening wins over the apse's wall. */
    private Kind merge(Kind apse, Kind nave, double d) {
        if (nave == Kind.KEEP) {
            return apse;
        }
        if (apse == Kind.KEEP) {
            return nave;
        }
        if (d <= APSE_WALL_IN) {
            return apse; // the choir floor and its dome are the apse's own
        }
        if (nave == Kind.AIR || nave == Kind.FLOOR || nave == Kind.FLOOR_BAND) {
            return nave;
        }
        return apse == Kind.AIR ? nave : apse;
    }

    private Kind apse(int x, int z, double u, double v, double d, int h) {
        ChoirArena a = arena();
        if (d <= APSE_WALL_IN) {
            if (h == -1) {
                if (a.circleColumn(x, z) >= 0) {
                    return Kind.CIRCLE;
                }
                boolean band = (d > 7.3 && d <= 8.1) || (d > 12.9 && d <= 13.7) || d > APSE_WALL_IN - 0.9;
                return band ? Kind.FLOOR_BAND : Kind.FLOOR;
            }
            if (h == 0 && d <= ChoirArena.DAIS_RADIUS) {
                return d > ChoirArena.DAIS_RADIUS - 0.8 ? Kind.DAIS_EDGE : Kind.DAIS;
            }
            if (h == 0 && d <= ChoirArena.STEP_RADIUS) {
                return Kind.STEP;
            }
            double inner = domeInner(d);
            if (h > inner) {
                return h <= inner + 2 ? (rib(x, z, d) ? Kind.ROOF_RIB : Kind.ROOF) : Kind.KEEP;
            }
            return Kind.AIR;
        }
        if (d <= APSE_WALL_OUT) {
            if (h == -1) {
                return Kind.WALL_BASE;
            }
            double inner = domeInner(d);
            if (h > inner + 2) {
                return Kind.KEEP;
            }
            if (h > APSE_WALL_TOP) {
                return rib(x, z, d) ? Kind.ROOF_RIB : Kind.ROOF;
            }
            int win = apseWindow(x, z, h);
            if (win >= 0) {
                // lichen glass through the wall, so the windows glow from outside too (the fight dims them all)
                return win % 2 == 0 ? Kind.LICHEN_WINDOW_MAGENTA : Kind.LICHEN_WINDOW_TEAL;
            }
            return h <= 0 ? Kind.WALL_BASE : Kind.WALL;
        }
        // buttresses: radial fins between the windows, stepping down outward
        double ang = Math.toDegrees(Math.atan2(z + 0.5 - cz, x + 0.5 - cx));
        double near = Math.round(ang / 45.0) * 45.0;
        double off = Math.abs(Math.toRadians(ang - near)) * d;
        if (off <= 0.6 && h >= -1 && h <= 9 - (int) Math.round((d - APSE_WALL_OUT) * 2.5) && !openingAt(u, v)) {
            return h <= 0 ? Kind.WALL_BASE : Kind.WALL;
        }
        // a pinnacle on each buttress, against the dome's foot
        if (off <= 0.6 && d <= APSE_WALL_OUT + 1.2 && h >= 10 && h <= 14 && !openingAt(u, v)) {
            return Kind.PINNACLE;
        }
        return Kind.KEEP;
    }

    /** The dome's inner surface over the floor at distance {@code d}. */
    static double domeInner(double d) {
        double s = Math.min(1.0, d / APSE_WALL_OUT);
        return APSE_WALL_TOP + DOME_RISE * (1.0 - s * s);
    }

    /** Eight ribs run up the dome, and one ring round its top. */
    private boolean rib(int x, int z, double d) {
        double ang = Math.toDegrees(Math.atan2(z + 0.5 - cz, x + 0.5 - cx));
        double near = Math.round(ang / 45.0) * 45.0;
        return Math.abs(Math.toRadians(ang - near)) * d <= 0.6 || (d > 5.0 && d <= 6.0);
    }

    /** True where the nave's arch opens the apse wall. */
    private boolean openingAt(double u, double v) {
        return u > 0 && Math.abs(v) <= NAVE_WALL + 0.5;
    }

    /**
     * The apse window (0 to 7) holding the wall block at {@code (x, z)}, height {@code h}, or -1: tall lancets 3 wide
     * between the buttresses, from the floor's second block to a pointed top, none where the nave's arch opens.
     */
    public int apseWindow(int x, int z, int h) {
        double ang = Math.toDegrees(Math.atan2(z + 0.5 - cz, x + 0.5 - cx));
        int k = (int) Math.floorMod(Math.round((ang - 22.5) / 45.0), 8L);
        double centre = 22.5 + 45.0 * k;
        double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
        double off = Math.abs(Math.toRadians(wrap(ang - centre))) * d;
        double[] l = local(x, z);
        if (openingAt(l[0], l[1]) || Math.abs(Math.toDegrees(Math.atan2(l[1], l[0]))) < 38.0) {
            return -1;
        }
        double top = 9.0 - off * 1.3;
        return off <= 1.6 && h >= 2 && h <= top ? k : -1;
    }

    private static double wrap(double deg) {
        double a = deg % 360.0;
        if (a > 180.0) {
            a -= 360.0;
        } else if (a <= -180.0) {
            a += 360.0;
        }
        return a;
    }

    private Kind nave(int x, int z, double u, double v, int h) {
        double av = Math.abs(v);
        if (u > DOOR_WALL) {
            // the landing outside the door
            if (av <= 3.5 && u <= LANDING) {
                return h == -1 ? Kind.FLOOR_BAND : h >= 0 && h <= 3 ? Kind.AIR : Kind.KEEP;
            }
            return Kind.KEEP;
        }
        if (towerAt(u, av)) {
            return tower(u, av, h);
        }
        double inner = naveInner(av);
        int top = gable(av);
        if (u > NAVE_END && av <= NAVE_WALL) {
            return front(v, av, h, top);
        }
        if (av <= NAVE_HALF) {
            if (h == -1) {
                return av <= 1.5 ? Kind.FLOOR_BAND : Kind.FLOOR;
            }
            if (h > inner) {
                if (h > top) {
                    return fleche(u, v, h);
                }
                if (roofHole(u, v)) {
                    return Kind.AIR;
                }
                if (h <= inner + 2) {
                    return ribBay(u) ? Kind.ROOF_RIB : Kind.ROOF;
                }
                return h < top ? Kind.ROOF : av < 1.0 ? Kind.RIDGE : Kind.ROOF_SLOPE;
            }
            if (column(u, v)) {
                return Kind.COLUMN;
            }
            if (ribBay(u) && h >= inner - 1 && av > 3.5) {
                return Kind.RIB;
            }
            Kind rubble = rubble(u, v, h);
            return rubble != null ? rubble : Kind.AIR;
        }
        if (av <= NAVE_WALL) {
            if (h == -1) {
                return Kind.WALL_BASE;
            }
            if (h > top) {
                return Kind.KEEP;
            }
            if (h > NAVE_WALL_TOP) {
                return h == top ? Kind.ROOF_SLOPE : Kind.ROOF;
            }
            if (naveWindow(u, h)) {
                return windowHue(u);
            }
            if (wallBreach(u, v, h)) {
                return Kind.AIR;
            }
            return h <= 0 ? Kind.WALL_BASE : Kind.WALL;
        }
        // outside the walls: the eaves, and a flying buttress outside each column
        if (av <= NAVE_WALL + 1.0 && h == top && u >= NAVE_START + 6.0) {
            return Kind.ROOF_SLOPE;
        }
        if (ribBay(u)) {
            if (av >= PIER_IN && av <= PIER_OUT) {
                if (h >= -1 && h <= 13) {
                    return h <= 0 ? Kind.WALL_BASE : Kind.WALL;
                }
                if (h == 14 || (h <= 17 && av > PIER_OUT - 0.6)) {
                    return Kind.PINNACLE;
                }
            }
            // the flying arch, from the pier's top up to the wall's
            if (av > NAVE_WALL + 0.5 && av < PIER_IN && (h == 13 || h == (av < NAVE_WALL + 1.5 ? 14 : 12))) {
                return Kind.RIB;
            }
        }
        return Kind.KEEP;
    }

    /** The gable roof's top block over the floor at {@code |v|}: the ridge at 21, one lower each block out. */
    static int gable(double av) {
        return GABLE_TOP - (int) Math.floor(av);
    }

    /** The nave's windows alternate magenta and teal. */
    private static Kind windowHue(double u) {
        return Math.floorMod(Math.round((u - 21.5) / 6.0), 2) == 0 ? Kind.NAVE_WINDOW_MAGENTA : Kind.NAVE_WINDOW_TEAL;
    }

    /** The west front: its door, the rose window over it, a coping over the roof line and a finial on the apex. */
    private Kind front(double v, double av, int h, int top) {
        if (h == -1) {
            return Kind.WALL_BASE;
        }
        boolean door = av <= 2.0 && h >= 0 && h <= 5 - Math.max(0, (int) Math.round(av * 1.2 - 0.6));
        if (door) {
            return Kind.AIR;
        }
        if (h > top + 1) {
            return av < 1.0 && h <= top + 3 ? Kind.PINNACLE : Kind.KEEP;
        }
        double rr = Math.hypot(v, h + 0.5 - ROSE_H);
        if (rr <= ROSE_R) {
            if (rr > ROSE_R - 0.6) {
                return Kind.RIB;
            }
            if (rr <= 1.3) {
                return Kind.NAVE_WINDOW_TEAL;
            }
            double ang = Math.atan2(h + 0.5 - ROSE_H, v);
            double k = Math.round(ang / (Math.PI / 4.0));
            return Math.abs(Math.sin(ang - k * Math.PI / 4.0)) * rr < 0.4 ? Kind.RIB : Kind.NAVE_WINDOW_MAGENTA;
        }
        return h > top ? Kind.RIB : Kind.WALL;
    }

    /** The two west towers flank the front. */
    static boolean towerAt(double u, double av) {
        return u > TOWER_U0 && u < TOWER_U1 && av > TOWER_V0 && av < TOWER_V1;
    }

    /** A west tower: basalt pillars at its corners, glowing lancets high up, a band, then a stepped spire and its post. */
    private Kind tower(double u, double av, int h) {
        double du = u - (TOWER_U0 + TOWER_U1) / 2.0;
        double dv = av - (TOWER_V0 + TOWER_V1) / 2.0;
        int top = towerTop();
        if (h <= top) {
            if (h <= 0) {
                return Kind.WALL_BASE;
            }
            boolean edgeU = Math.abs(du) > 2.9;
            boolean edgeV = Math.abs(dv) > 1.9;
            if (edgeU && edgeV) {
                return Kind.COLUMN;
            }
            if (h == top - 12 || h == top) {
                return Kind.RIB;
            }
            boolean outer = edgeV && dv > 0 && Math.abs(du) < 1.0;
            boolean west = du > 2.9 && Math.abs(dv) < 0.6;
            if (h >= top - 9 && h <= top - 3 && (outer || west)) {
                return Kind.NAVE_WINDOW_TEAL;
            }
            return Kind.WALL;
        }
        double half = 3.2 - 0.55 * (h - top);
        if (half >= 0.5) {
            return Math.abs(du) <= half && Math.abs(dv) <= Math.min(half, 2.0) ? Kind.SPIRE : Kind.KEEP;
        }
        return Math.abs(du) < 0.6 && Math.abs(dv) < 0.6 ? Kind.PINNACLE : Kind.KEEP;
    }

    /** The lantern spire on the ridge: a glowing lantern, a band, a post. */
    private static Kind fleche(double u, double v, int h) {
        if (Math.abs(v) > 1.0 || Math.abs(u - FLECHE_U) > 1.0) {
            return Kind.KEEP;
        }
        if (h <= GABLE_TOP + 2) {
            return Kind.NAVE_WINDOW_MAGENTA;
        }
        if (h == GABLE_TOP + 3) {
            return Kind.RIB;
        }
        // one post, not four: from afar a spire, not chimneys
        return h <= FLECHE_TOP && u > FLECHE_U && v > 0 ? Kind.PINNACLE : Kind.KEEP;
    }

    /** The nave's vault over the floor at {@code |v|}: a pointed barrel. */
    static double naveInner(double av) {
        return NAVE_WALL_TOP + NAVE_RISE * Math.max(0.0, 1.0 - av / (NAVE_WALL + 0.5));
    }

    /** The columns' bays: every 6 blocks along the nave from 18. */
    private static boolean ribBay(double u) {
        double k = (u - 18.5) / 6.0;
        return u >= 18.0 && u <= NAVE_END - 1.0 && Math.abs(k - Math.round(k)) * 6.0 < 0.5;
    }

    private static boolean column(double u, double v) {
        return ribBay(u) && Math.abs(Math.abs(v) - 5.5) < 0.5;
    }

    private static boolean naveWindow(double u, int h) {
        double k = (u - 21.5) / 6.0;
        double off = Math.abs(k - Math.round(k)) * 6.0;
        return u >= 20.0 && u <= NAVE_END - 2.0 && off <= 1.0 && h >= 2 && h <= 8 - (off > 0.5 ? 1 : 0);
    }

    // ------------------------------------------------------------------ the ruin

    private long hash(long a, long b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    private double unit(long a, long b) {
        return (hash(a, b) >>> 11) * 0x1.0p-53;
    }

    /** Two or three holes in the nave's roof, where it fell in. */
    private boolean roofHole(double u, double v) {
        for (int i = 0; i < 3; i++) {
            if (unit(i, 1) < 0.25) {
                continue;
            }
            double hu = 22.0 + unit(i, 2) * (NAVE_END - 26.0);
            double hv = (unit(i, 3) - 0.5) * 8.0;
            double r = 1.2 + unit(i, 4) * 1.4;
            if (Math.hypot(u - hu, v - hv) <= r) {
                return true;
            }
        }
        return false;
    }

    /** What fell from the roof lies under each hole. */
    private Kind rubble(double u, double v, int h) {
        for (int i = 0; i < 3; i++) {
            if (unit(i, 1) < 0.25) {
                continue;
            }
            double hu = 22.0 + unit(i, 2) * (NAVE_END - 26.0);
            double hv = (unit(i, 3) - 0.5) * 8.0;
            double dist = Math.hypot(u - hu, v - hv);
            if (Math.abs(v) > NAVE_HALF - 0.5 || Math.abs(v) < 1.6) {
                continue;
            }
            if (h == 0 && dist <= 1.6) {
                return unit((long) Math.floor(u * 7), (long) Math.floor(v * 7)) < 0.55 ? Kind.RUBBLE : Kind.RUBBLE_SLAB;
            }
            if (h == 1 && dist <= 0.7) {
                return Kind.RUBBLE_SLAB;
            }
        }
        return null;
    }

    /** A crack in the nave's wall high up, on one side. */
    private boolean wallBreach(double u, double v, int h) {
        double bu = 24.0 + unit(7, 1) * 14.0;
        boolean side = (unit(7, 2) < 0.5) == (v > 0);
        return side && h >= 8 && Math.abs(u - bu) <= 1.0 + (h - 8) * 0.6;
    }

    // ------------------------------------------------------------------ the keel

    private Kind keel(double u, double v, double d, int h, boolean inApse) {
        double av = Math.abs(v);
        if (towerAt(u, av) && av > NAVE_WALL + 0.5) {
            // a pendant under each tower, tapering to a point
            double off = Math.max(Math.abs(u - (TOWER_U0 + TOWER_U1) / 2.0), Math.abs(av - (TOWER_V0 + TOWER_V1) / 2.0));
            return -h - 1 <= 7 - (int) Math.round(off * 1.8) ? Kind.KEEL : Kind.KEEP;
        }
        if (u >= NAVE_START && ribBay(u) && av >= PIER_IN && av <= PIER_OUT) {
            // and one under each pier
            return -h - 1 <= (av > PIER_OUT - 0.6 ? 4 : 3) ? Kind.KEEL : Kind.KEEP;
        }
        double depth;
        if (inApse && d <= APSE_WALL_OUT + 0.5) {
            double s = d / (APSE_WALL_OUT + 0.5);
            depth = 3 + (KEEL_DEPTH - 3) * Math.pow(1.0 - s, 1.6);
        } else if (u >= NAVE_START && u <= LANDING && Math.abs(v) <= NAVE_WALL + 0.5) {
            double s = Math.abs(v) / (NAVE_WALL + 0.5);
            double along = Math.max(0.0, 1.0 - (u - NAVE_START) / (LANDING - NAVE_START + 8.0));
            depth = 3 + (6 + 10 * along) * Math.pow(1.0 - s, 1.3);
        } else {
            return Kind.KEEP;
        }
        if (-h - 1 > depth) {
            return Kind.KEEP;
        }
        return Math.floorMod(h, 5) == 0 ? Kind.KEEL_BAND : Kind.KEEL;
    }

    // ------------------------------------------------------------------ lichen

    /** The Neon Lichen of the nave: on the inside of each window, magenta and teal in turn. */
    public List<Lichen> lichen() {
        List<Lichen> out = new ArrayList<>();
        int[] a = axis();
        int i = 0;
        for (double u = 21.5; u <= NAVE_END - 2.0; u += 6.0) {
            for (int side = -1; side <= 1; side += 2) {
                double v = side * (NAVE_HALF - 0.5);
                int[] w = world(u, v);
                // the lichen clings to the wall toward +v on the right, -v on the left
                int dx = -a[1] * side;
                int dz = a[0] * side;
                int face = dx > 0 ? 0 : dz > 0 ? 1 : dx < 0 ? 2 : 3;
                for (int h = 2; h <= 7; h++) {
                    if ((h + i) % 3 != 0) {
                        out.add(new Lichen(w[0], floorY + h, w[1], face, (i & 1) == 0));
                    }
                }
                i++;
            }
        }
        return out;
    }
}
