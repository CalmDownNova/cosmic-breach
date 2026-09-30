package com.cosmicbreach.guardian.colossus;

/**
 * The Crown Spire's shape (Prism Colossus design v1, "The lair"), pure: what goes at every block of the lair, for
 * the structure piece that builds it chunk by chunk during world generation and for the debug command that builds
 * one at once.
 *
 * <p>A hollow spire rises from its island (its root piercing down through the island, its crown 110 to 140 blocks
 * above the root's tip), six Spire Quartz veins spiralling up its stone and buttress roots at its foot, and flares
 * into the crown: a floor of radius 18 in Starfall Stone bricks with muted brass inlay rings, a rim of pale crown
 * quartz with battlements (clear glass in their gaps), twelve crown quartz points leaning out round the edge, tall
 * and short in turn (the crown's silhouette from outside), the six crown crystals at radius 14 and the stump of the
 * central pillar where the Colossus stands. The Colossus owns turquoise; the crown is pale. In the hollow core a
 * 3 by 3 column of rising light carries players from the spire's foot to the crown and sets them down beside the
 * Prism Altar; a well of falling light on the far side, flush with the floor, a brass border round it and a lamp
 * post at each corner, lowers them again. Two doors open at the foot, one below each column, each framed by a gate
 * of crown quartz: pillars on brass plinths with glowing capitals, a lintel, and a stepped prism with a light at its
 * heart.
 *
 * <p>The arena's centre is the block corner {@code (x, z)}; {@code floorY} is the first block of air above the
 * floor (so the floor's top blocks are at {@code floorY - 1}); {@code baseY} is the first block of air over the
 * island at the spire's foot; {@code rootY} is the root's lowest block.
 */
public record CrownSpireLayout(int x, int z, int floorY, int baseY, int rootY) {
    /** What one block is. */
    public enum Kind {
        /** Leave the world as it is. */
        KEEP,
        /** Clear to air (the hollow core, the air over the arena). */
        AIR,
        STONE,
        BRICKS,
        POLISHED,
        QUARTZ,
        GOLD,
        GLASS,
        CRYSTAL,
        PILLAR,
        RISING,
        RISING_TOP,
        FALLING,
        ALTAR,
        /** Pale crown quartz: the rim, the battlements, the crown's points, the gates, the well's posts. */
        CROWN_QUARTZ,
        /** A lamp: a lit crown pillar block. */
        LAMP
    }

    public static final double ARENA_RADIUS = CrownArena.FLOOR_RADIUS + 0.5;
    public static final double WALL_OUTER = 19.5;
    public static final double CROWN_RADIUS = 20.5;
    public static final int WALL_HEIGHT = 3;
    /** The battlements' merlons stand one above the rim's glass. */
    public static final int MERLON_HEIGHT = 4;
    /** A merlon's half-width in degrees (two blocks at the rim), centred on every multiple of 15 degrees. */
    public static final double MERLON_HALF_DEG = 3.2;
    /** How far above the floor the tall and the short crown points reach. */
    public static final int POINT_TALL = 12;
    public static final int POINT_SHORT = 7;
    /** The Spire Quartz veins: degrees they turn per block of height, and how wide they are in degrees. */
    public static final double VEIN_TWIST = 3.0;
    public static final double VEIN_WIDTH = 10.0;
    /** The buttress roots at the foot: how far out they reach at the ground and how high they climb. */
    public static final double BUTTRESS_REACH = 3.5;
    public static final int BUTTRESS_HEIGHT = 9;
    /** Each door's gate: how far out from the centre it stands, its opening's half-width, pillar width and height. */
    public static final double GATE_ALONG = 18.5;
    public static final int GATE_HALF = 2;
    public static final int GATE_PILLAR = 2;
    public static final int GATE_OPENING = 5;
    /** Air is cleared this high over the arena (stray spire tips and the like). */
    public static final int CLEAR_HEIGHT = 18;
    public static final int CAPITAL_DEPTH = 12;
    public static final double SHAFT_TOP = 11.5;
    public static final double SHAFT_BOTTOM = 12.5;
    public static final double WALL_THICKNESS = 2.5;
    public static final int FLARE_HEIGHT = 14;
    public static final double FLARE = 5.0;
    /** The lift columns stand this far from the centre. */
    public static final double LIFT_RADIUS = 8.0;
    /** The rising column's bearing (degrees clockwise from east): between crystals 0 and 1. */
    public static final double RISE_ANGLE = 30.0;
    public static final double ALTAR_RADIUS = 11.5;
    public static final int CROWN_POINTS = 12;
    public static final int DOOR_WIDTH = 3;
    public static final int DOOR_HEIGHT = 4;
    /** How far out the whole lair reaches from the centre (the crown points). */
    public static final int REACH = 25;

    // ------------------------------------------------------------------ fixed places

    /** The rising column's centre block, as {x, z}. */
    public int[] riseColumn() {
        return columnAt(RISE_ANGLE);
    }

    /** The falling well's centre block, as {x, z}. */
    public int[] fallColumn() {
        return columnAt(RISE_ANGLE + 180.0);
    }

    private int[] columnAt(double degrees) {
        double a = Math.toRadians(degrees);
        return new int[] {(int) Math.floor(x + LIFT_RADIUS * Math.cos(a)), (int) Math.floor(z + LIFT_RADIUS * Math.sin(a))};
    }

    /** The altar's block, as {x, y, z}: beside where the lift sets players down, a step clockwise of its path. */
    public int[] altar() {
        double a = Math.toRadians(RISE_ANGLE);
        double sx = -Math.sin(a) * ALTAR_SIDE;
        double sz = Math.cos(a) * ALTAR_SIDE;
        return new int[] {(int) Math.floor(x + ALTAR_RADIUS * Math.cos(a) + sx), floorY, (int) Math.floor(z + ALTAR_RADIUS * Math.sin(a) + sz)};
    }

    /** How far the altar stands to the side of the lift's path. */
    public static final double ALTAR_SIDE = 2.2;

    /** Where to stand outside the rising column's door, looking in: {x, y, z} of the block (on its walkway). */
    public int[] riseDoorOutside() {
        double a = Math.toRadians(RISE_ANGLE);
        double r = outerRadius(baseY - 2) + 3.0;
        return new int[] {(int) Math.floor(x + r * Math.cos(a)), baseY, (int) Math.floor(z + r * Math.sin(a))};
    }

    /** The unit step pointing out from the centre along the rising column's bearing, rounded to a side: {dx, dz}. */
    public int[] exitStep() {
        double a = Math.toRadians(RISE_ANGLE);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return Math.abs(c) >= Math.abs(s) ? new int[] {c > 0 ? 1 : -1, 0} : new int[] {0, s > 0 ? 1 : -1};
    }

    public CrownArena arena() {
        return new CrownArena(x, floorY, z);
    }

    // ------------------------------------------------------------------ radii

    /** The spire's outer radius at height {@code y} (shaft, flare and capital; the crown is separate). */
    public double outerRadius(int y) {
        int capitalBottom = floorY - 3 - CAPITAL_DEPTH;
        if (y >= floorY - 3) {
            return CROWN_RADIUS - 0.5;
        }
        if (y >= capitalBottom) {
            double u = (y - capitalBottom) / (double) CAPITAL_DEPTH; // 0 at the shaft, 1 under the crown
            return SHAFT_TOP + (CROWN_RADIUS - 0.5 - SHAFT_TOP) * u * u;
        }
        double span = Math.max(1, capitalBottom - baseY);
        double u = Math.max(0.0, Math.min(1.0, (y - baseY) / span));
        double shaft = SHAFT_BOTTOM + (SHAFT_TOP - SHAFT_BOTTOM) * u;
        double flareU = Math.max(0.0, 1.0 - (y - (baseY - 2)) / (double) FLARE_HEIGHT);
        return shaft + FLARE * flareU * flareU;
    }

    /** The root's radius below the spire's foot (a cone to a point at {@link #rootY}). */
    public double rootRadius(int y) {
        int top = baseY - 1;
        if (y > top || y < rootY) {
            return -1.0;
        }
        double u = (y - rootY + 1) / (double) Math.max(1, top - rootY + 1);
        return outerRadius(baseY - 2) * Math.pow(u, 0.75);
    }

    /** The hollow core's radius at {@code y} (0 where there is none). */
    public double innerRadius(int y) {
        if (y < baseY || y > floorY - 3) {
            return 0.0;
        }
        return Math.max(0.0, outerRadius(y) - WALL_THICKNESS - (y < baseY + FLARE_HEIGHT ? flareExtra(y) : 0.0));
    }

    private double flareExtra(int y) {
        double flareU = Math.max(0.0, 1.0 - (y - (baseY - 2)) / (double) FLARE_HEIGHT);
        return FLARE * flareU * flareU;
    }

    // ------------------------------------------------------------------ the block at a position

    /** What goes at block {@code (bx, by, bz)}. */
    public Kind kind(int bx, int by, int bz) {
        double dx = bx + 0.5 - x;
        double dz = bz + 0.5 - z;
        double r = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.toDegrees(Math.atan2(dz, dx));

        // the lift columns run from the foot up through the floor
        int[] rise = riseColumn();
        if (Math.abs(bx - rise[0]) <= 1 && Math.abs(bz - rise[1]) <= 1 && by >= baseY && by <= floorY - 1) {
            return by == floorY - 1 ? Kind.RISING_TOP : Kind.RISING;
        }
        int[] fall = fallColumn();
        if (Math.abs(bx - fall[0]) <= 1 && Math.abs(bz - fall[1]) <= 1 && by >= baseY && by <= floorY - 1) {
            return Kind.FALLING;
        }

        // the crown: floor, rim wall, crystal points, and what stands on the floor
        if (by >= floorY - 2 && by <= floorY + CLEAR_HEIGHT && r <= REACH) {
            Kind crown = crown(bx, by, bz, r, angle);
            if (crown != null) {
                return crown;
            }
        }
        if (by >= floorY - 4 && by <= floorY + POINT_TALL && r > CROWN_RADIUS - 4.0 && r <= REACH) {
            Kind point = crownPoint(by, r, angle);
            if (point != null) {
                return point;
            }
        }
        if (by >= floorY - 2) {
            return by <= floorY + CLEAR_HEIGHT && r <= WALL_OUTER ? Kind.AIR : Kind.KEEP;
        }

        // the doors' walkways: a straight path 3 wide, flush with the spire's floor, out over the island
        Kind path = walkway(dx, dz, by);
        if (path != null) {
            return path;
        }
        // the gates across them
        Kind gate = gate(bx, by, bz);
        if (gate != null) {
            return gate;
        }
        // the buttress roots at the foot
        Kind buttress = buttress(r, angle, by);
        if (buttress != null) {
            return buttress;
        }

        // the spire's body: shell, hollow core, doors, veins
        double outer = outerRadius(by);
        if (by >= baseY - 1) {
            if (r > outer) {
                return Kind.KEEP;
            }
            if (by == baseY - 1) {
                if (doorOffset(angle) >= 0 && r >= innerRadius(baseY) - 0.5) {
                    return Kind.BRICKS; // the passage's floor
                }
                return r > outer - WALL_THICKNESS ? Kind.STONE : ((int) Math.floor(r) % 4 == 0 ? Kind.POLISHED : Kind.BRICKS);
            }
            double inner = innerRadius(by);
            if (r < inner) {
                return Kind.AIR;
            }
            if (door(angle, by) && r >= inner - 0.5) {
                return Kind.AIR;
            }
            Kind lining = doorLining(angle, by, r);
            if (lining != null) {
                return lining;
            }
            return shell(by, angle, r, outer);
        }

        // below the foot: the root, down through the island and out beneath it
        double root = rootRadius(by);
        if (root > 0 && r <= root) {
            return vein(by, angle) ? Kind.QUARTZ : Kind.STONE;
        }
        return Kind.KEEP;
    }

    private Kind crown(int bx, int by, int bz, double r, double angle) {
        if (by <= floorY - 1) {
            if (r > CROWN_RADIUS) {
                return null;
            }
            if (by == floorY - 2) {
                return Kind.POLISHED;
            }
            if (r > ARENA_RADIUS) {
                return Kind.BRICKS;
            }
            int[] fall = fallColumn();
            if (by == floorY - 1 && Math.max(Math.abs(bx - fall[0]), Math.abs(bz - fall[1])) == 2) {
                return Kind.GOLD; // a brass border round the falling well, flush with the floor
            }
            return goldRing(r) ? Kind.GOLD : Kind.BRICKS;
        }
        // on the floor: the rim, a band of crown quartz with battlements on it (clear glass fills their gaps)
        if (r > ARENA_RADIUS && r <= WALL_OUTER && by < floorY + MERLON_HEIGHT) {
            if (by == floorY || merlon(angle)) {
                return Kind.CROWN_QUARTZ;
            }
            return by < floorY + WALL_HEIGHT ? Kind.GLASS : null;
        }
        CrownArena arena = arena();
        for (int k = 0; r > 12.0 && r < 16.5 && k < Refraction.CRYSTALS; k++) {
            net.minecraft.core.BlockPos base = arena.crystalBase(k);
            if (bx - base.getX() >= 0 && bx - base.getX() < 2 && bz - base.getZ() >= 0 && bz - base.getZ() < 2
                    && by - floorY >= 0 && by - floorY < CrownArena.CRYSTAL_HEIGHT) {
                return Kind.CRYSTAL;
            }
        }
        if (by == floorY) {
            if (bx >= x - 2 && bx <= x + 1 && bz >= z - 2 && bz <= z + 1 && !((bx == x - 2 || bx == x + 1) && (bz == z - 2 || bz == z + 1))) {
                return Kind.PILLAR;
            }
            int[] altar = altar();
            if (bx == altar[0] && bz == altar[2]) {
                return Kind.ALTAR;
            }
        }
        // a lamp post at each corner of the falling well: crown quartz with a lamp on it
        int[] fall = fallColumn();
        if (Math.abs(bx - fall[0]) == 2 && Math.abs(bz - fall[1]) == 2 && by <= floorY + 1) {
            return by == floorY ? Kind.CROWN_QUARTZ : Kind.LAMP;
        }
        return null;
    }

    /** A merlon of the rim's battlements: two blocks wide, centred on every multiple of 15 degrees (each crown point's
     * bearing and halfway between), three blocks of glass between. */
    static boolean merlon(double angle) {
        double m = ((angle % 15.0) + 15.0) % 15.0;
        return m < MERLON_HALF_DEG || m > 15.0 - MERLON_HALF_DEG;
    }

    private static boolean goldRing(double r) {
        return (r >= 5.6 && r < 6.4) || (r >= 10.6 && r < 11.4) || (r >= 15.6 && r < 16.4);
    }

    /**
     * A crown point: a faceted spike of crown quartz (a diamond in cross-section) leaning out from under the crown's
     * edge, one every 30 degrees, tall and short in turn; never on the arena's floor or in its rim.
     */
    private Kind crownPoint(int by, double r, double angle) {
        double step = 360.0 / CROWN_POINTS;
        long index = Math.round((angle - step / 2.0) / step);
        double nearest = index * step + step / 2.0;
        boolean tall = Math.floorMod(index, 2) == 0;
        int bottom = floorY - 4;
        int top = floorY + (tall ? POINT_TALL : POINT_SHORT);
        double t = (by - bottom) / (double) (top - bottom);
        if (t < 0.0 || t > 1.0 || (by >= floorY && r <= WALL_OUTER)) {
            return null;
        }
        double da = Math.toRadians(angle - nearest);
        double tangential = r * Math.sin(da);
        double radial = r * Math.cos(da);
        double axisR = 19.6 + (tall ? 4.2 : 3.0) * t;
        double thick = (tall ? 2.7 : 2.2) * (1.0 - t) + 0.35;
        return Math.abs(radial - axisR) + Math.abs(tangential) <= thick ? Kind.CROWN_QUARTZ : null;
    }

    /** The side a door at {@code bearing} faces, square to the grid: {dx, dz}. */
    private static int[] facing(double bearing) {
        double a = Math.toRadians(bearing);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return Math.abs(c) >= Math.abs(s) ? new int[] {c > 0 ? 1 : -1, 0} : new int[] {0, s > 0 ? 1 : -1};
    }

    /**
     * A door's gate, square to the grid (facing the side the door opens to) and standing across its walkway just
     * out from the foot: two pillars of crown quartz, two wide and two deep, on brass plinths with a lamp on each; a
     * brass band and a quartz lintel over a five-wide, five-high opening; over that a stepped prism of crown quartz
     * with a lamp at its heart. Null outside every gate.
     */
    private Kind gate(int bx, int by, int bz) {
        int h = by - baseY;
        if (h < -4 || h > GATE_OPENING + 5) {
            return null;
        }
        for (double bearing : new double[] {RISE_ANGLE, RISE_ANGLE + 180.0}) {
            int[] f = facing(bearing);
            double a = Math.toRadians(bearing);
            double px = x + GATE_ALONG * Math.cos(a);
            double pz = z + GATE_ALONG * Math.sin(a);
            boolean alongX = f[0] != 0;
            int out = alongX ? f[0] : f[1];
            int n0 = (int) Math.floor(alongX ? px : pz);
            int l0 = (int) Math.floor(alongX ? pz : px);
            int dn = ((alongX ? bx : bz) - n0) * out;
            int dl = Math.abs((alongX ? bz : bx) - l0);
            if (dn < 0 || dn > 1 || dl > GATE_HALF + GATE_PILLAR) {
                continue;
            }
            boolean pillar = dl > GATE_HALF;
            if (h < 0) {
                return h == -1 && !pillar ? Kind.BRICKS : Kind.STONE;
            }
            if (h < GATE_OPENING) {
                return pillar ? (h == 0 ? Kind.GOLD : Kind.CROWN_QUARTZ) : Kind.AIR;
            }
            if (h == GATE_OPENING) {
                return pillar ? Kind.CROWN_QUARTZ : Kind.GOLD;
            }
            if (h == GATE_OPENING + 1) {
                return Kind.CROWN_QUARTZ;
            }
            // over the lintel, on its outer face: the lamps over the pillars and the stepped prism between them
            int step = h - GATE_OPENING - 2; // 0 to 3
            if (dn != 1) {
                return null;
            }
            if (step == 0 && dl == GATE_HALF + GATE_PILLAR) {
                return Kind.LAMP;
            }
            if (dl <= GATE_HALF + 1 - step) {
                return step == 0 && dl == 0 ? Kind.LAMP : Kind.CROWN_QUARTZ;
            }
            return null;
        }
        return null;
    }

    /**
     * A buttress root at the foot, one every 60 degrees (between the doors, which open at 30 and 210): stone with a
     * Spire Quartz spine, reaching {@value #BUTTRESS_REACH} blocks out at the ground and narrowing up into the flare.
     */
    private Kind buttress(double r, double angle, int by) {
        if (by < baseY - 4 || by > baseY + BUTTRESS_HEIGHT) {
            return null;
        }
        double nearest = Math.round(angle / 60.0) * 60.0;
        double lateral = Math.abs(r * Math.sin(Math.toRadians(angle - nearest)));
        double h = Math.max(0, by - (baseY - 1));
        double reach = BUTTRESS_REACH * Math.pow(1.0 - h / (BUTTRESS_HEIGHT + 1.0), 1.5);
        double width = 1.3 * (1.0 - 0.5 * h / BUTTRESS_HEIGHT) + 0.4;
        double outer = outerRadius(Math.max(by, baseY - 1));
        if (lateral > width || r > outer + reach || r < outer - 1.0) {
            return null;
        }
        return lateral < 0.5 ? Kind.QUARTZ : Kind.STONE;
    }

    private boolean door(double angle, int by) {
        if (by < baseY || by >= baseY + DOOR_HEIGHT) {
            return false;
        }
        double da = doorOffset(angle);
        return da >= 0 && da <= Math.toDegrees(Math.atan2(DOOR_WIDTH / 2.0 + (by == baseY + DOOR_HEIGHT - 1 ? -0.5 : 0.0), 13.0));
    }

    /** How far (degrees) {@code angle} is from the nearer door's bearing, when that is within a door's width; else -1. */
    private static double doorOffset(double angle) {
        double half = Math.toDegrees(Math.atan2(DOOR_WIDTH / 2.0, 13.0));
        for (double bearing : new double[] {RISE_ANGLE, RISE_ANGLE + 180.0}) {
            double da = Math.abs(wrap(angle - bearing));
            if (da <= half) {
                return da;
            }
        }
        return -1.0;
    }

    /**
     * The lining of a door's passage through the shell, so behind the lit gate it reads as built, not dug: walls a
     * block and a bit thick and a ceiling over it in Starfall bricks, a rib of crown quartz every third block along.
     */
    private Kind doorLining(double angle, int by, double r) {
        if (by < baseY || by > baseY + DOOR_HEIGHT) {
            return null;
        }
        double margin = Math.toDegrees(1.3 / Math.max(r, 1.0));
        for (double bearing : new double[] {RISE_ANGLE, RISE_ANGLE + 180.0}) {
            double da = Math.abs(wrap(angle - bearing));
            double open = Math.toDegrees(Math.atan2(DOOR_WIDTH / 2.0 + (by == baseY + DOOR_HEIGHT - 1 ? -0.5 : 0.0), 13.0));
            double full = Math.toDegrees(Math.atan2(DOOR_WIDTH / 2.0, 13.0));
            boolean wall = by < baseY + DOOR_HEIGHT && da > open && da <= full + margin;
            boolean ceiling = by == baseY + DOOR_HEIGHT && da <= full + margin;
            if (wall || ceiling) {
                return Math.floorMod((int) Math.floor(r), 3) == 0 ? Kind.CROWN_QUARTZ : Kind.BRICKS;
            }
        }
        return null;
    }

    /** How far out past the base the walkways run. */
    public static final double WALKWAY = 7.0;

    /**
     * A door's walkway at offset {@code (dx, dz)} from the centre: bricks at the floor's level, air over it up to the
     * door's height, stone under it; null elsewhere. Straight, 3 wide, from inside the door to past the base.
     */
    private Kind walkway(double dx, double dz, int by) {
        if (by < baseY - 5 || by >= baseY + DOOR_HEIGHT) {
            return null;
        }
        double outer = outerRadius(baseY - 2);
        for (double bearing : new double[] {RISE_ANGLE, RISE_ANGLE + 180.0}) {
            double a = Math.toRadians(bearing);
            double along = dx * Math.cos(a) + dz * Math.sin(a);
            double lateral = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a));
            if (lateral > DOOR_WIDTH / 2.0 + 0.01 || along < outer - 5.0 || along > outer + WALKWAY) {
                continue;
            }
            if (by == baseY - 1) {
                return Kind.BRICKS;
            }
            if (by >= baseY) {
                return along >= outer - 5.0 ? Kind.AIR : null;
            }
            return along >= outer - 1.0 ? Kind.STONE : null;
        }
        return null;
    }

    private Kind shell(int by, double angle, double r, double outer) {
        if (vein(by, angle)) {
            return Kind.QUARTZ;
        }
        // a ring of polished stone every 12 blocks, and brick bands where the flare meets the shaft
        if (Math.floorMod(by - baseY, 12) == 0 && r > outer - 1.0) {
            return Kind.POLISHED;
        }
        if (by >= floorY - 3 - CAPITAL_DEPTH && r > outer - 1.2) {
            return Kind.BRICKS;
        }
        return Kind.STONE;
    }

    /** Spire Quartz veins spiralling up the stone, six of them, each running up into a tall crown point. */
    private boolean vein(int by, double angle) {
        double phase = angle - 360.0 / CROWN_POINTS / 2.0 + (floorY - 4 - by) * VEIN_TWIST;
        double m = ((phase % 60.0) + 60.0) % 60.0;
        return m < VEIN_WIDTH / 2.0 || m > 60.0 - VEIN_WIDTH / 2.0;
    }

    private static double wrap(double degrees) {
        double a = degrees % 360.0;
        if (a > 180.0) {
            a -= 360.0;
        } else if (a <= -180.0) {
            a += 360.0;
        }
        return a;
    }

    /** The whole lair's box: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] bounds() {
        return new int[] {x - REACH, rootY, z - REACH, x + REACH, floorY + CLEAR_HEIGHT, z + REACH};
    }
}
