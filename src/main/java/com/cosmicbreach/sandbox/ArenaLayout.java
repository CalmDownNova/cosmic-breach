package com.cosmicbreach.sandbox;

/**
 * The sky arena's shape, relative to its centre block (the floor block under the middle): a disc of
 * polished tuff {@value #RADIUS} blocks in radius with a smooth quartz compass pattern (a star in the middle,
 * rings at {@value #INNER_RING} and {@value #OUTER_RING}, four spokes) and a smooth quartz border, a
 * knee-high smooth quartz rim with lanterns on it, and {@value #PILLARS} amethyst pillars to hide behind.
 * The air above the floor is cleared. Pure; {@link ArenaBuilder} places the blocks.
 */
public final class ArenaLayout {
    public enum Kind {
        /** Leave the block as it is. */
        KEEP,
        /** Make it air. */
        AIR,
        /**
         * The floor's field: polished tuff, a calm mid-tone. The first arena's white calcite hid the
         * Shardlings' pale bodies and swallowed every white flash; on tuff the creatures, the gold
         * telegraph, the red shard timers and the flashes all stand out, and the white pattern keeps
         * it daylit.
         */
        FLOOR,
        /** Smooth quartz in the floor. */
        PATTERN,
        /** The smooth quartz rim round the edge. */
        RIM,
        /** A lantern standing on the rim. */
        LANTERN,
        /** Amethyst block. */
        PILLAR,
        /** An amethyst cluster growing up out of a pillar's top. */
        CLUSTER
    }

    public static final int RADIUS = 22;
    /** Air is cleared this high above the floor. */
    public static final int CLEAR_HEIGHT = 8;
    public static final int INNER_RING = 8;
    public static final int OUTER_RING = 15;
    public static final int PILLARS = 5;
    /** Pillars stand round this circle... */
    public static final double PILLAR_RING = 12.5;
    /**
     * ...starting at this many degrees, then every 72 round (angles are {@code atan2(dz, dx)}): from 9
     * none of them stands on the lines along the axes to the middle, where the packs run in.
     */
    public static final double PILLAR_START = 9.0;
    private static final int[] PILLAR_HEIGHTS = {4, 3, 5, 3, 4};
    /** Lanterns stand on the rim every this many degrees. */
    public static final double LANTERN_EVERY = 30.0;

    private ArenaLayout() {
    }

    /** What goes at {@code (dx, dy, dz)} from the centre; dy 0 is the floor. */
    public static Kind at(int dx, int dy, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > RADIUS + 0.5 || dy < 0 || dy > CLEAR_HEIGHT) {
            return Kind.KEEP;
        }
        boolean edge = r > RADIUS - 0.5;
        if (dy == 0) {
            return edge || pattern(dx, dz, r) ? Kind.PATTERN : Kind.FLOOR;
        }
        if (edge) {
            if (dy == 1) {
                return Kind.RIM;
            }
            if (dy == 2 && lanternAt(dx, dz)) {
                return Kind.LANTERN;
            }
            return Kind.AIR;
        }
        int pillar = pillarAt(dx, dz);
        if (pillar >= 0) {
            int height = PILLAR_HEIGHTS[pillar];
            if (dy <= height) {
                return Kind.PILLAR;
            }
            if (dy == height + 1) {
                return Kind.CLUSTER;
            }
        }
        return Kind.AIR;
    }

    /** The compass pattern: a small star in the middle, two rings, four spokes between them. */
    private static boolean pattern(int dx, int dz, double r) {
        if (Math.abs(dx) + Math.abs(dz) <= 2) {
            return true;
        }
        if (Math.abs(r - INNER_RING) < 0.5 || Math.abs(r - OUTER_RING) < 0.5) {
            return true;
        }
        return (dx == 0 || dz == 0) && r >= 3 && r <= OUTER_RING;
    }

    /** The pillar (0 to 4) whose 2 by 2 footprint covers this column, or -1. */
    public static int pillarAt(int dx, int dz) {
        for (int i = 0; i < PILLARS; i++) {
            int[] corner = pillarCorner(i);
            if (dx >= corner[0] && dx <= corner[0] + 1 && dz >= corner[1] && dz <= corner[1] + 1) {
                return i;
            }
        }
        return -1;
    }

    /** The low x, low z column of pillar {@code i}'s 2 by 2 footprint. */
    public static int[] pillarCorner(int i) {
        double angle = Math.toRadians(PILLAR_START + 72.0 * i);
        return new int[] {(int) Math.floor(Math.cos(angle) * PILLAR_RING), (int) Math.floor(Math.sin(angle) * PILLAR_RING)};
    }

    public static int pillarHeight(int i) {
        return PILLAR_HEIGHTS[i];
    }

    /** True for the rim column nearest each lantern angle (only one column per angle). */
    private static boolean lanternAt(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        double nearest = Math.round(angle / LANTERN_EVERY) * LANTERN_EVERY;
        double wanted = Math.toRadians(nearest);
        int x = (int) Math.round(Math.cos(wanted) * RADIUS);
        int z = (int) Math.round(Math.sin(wanted) * RADIUS);
        return dx == x && dz == z;
    }
}
