package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are on the Sanctum floor for the Heliarch's fight (GDD 7.3), as pure geometry in world coordinates: the
 * core's place over the throne, the six monolith spots in the mid ring, the four tendril anchors, the pillars' and the
 * monoliths' footprints for cover. The floor's top is at Y 64; the throne's column is block (0, 0), its middle
 * (0.5, 0.5).
 *
 * <p>Angles here are compass angles in degrees, clockwise from north (the Sanctum's own convention, {@link
 * SanctumLayout#angle}): 0 north (-Z), 90 east (+X). A Minecraft yaw is the compass angle plus 180.
 */
public final class HeliarchArena {
    public static final double CX = 0.5;
    public static final double CZ = 0.5;
    public static final double FLOOR = SanctumLayout.ARENA_Y;
    public static final Vec3 CENTRE = new Vec3(CX, FLOOR, CZ);
    /** The core's middle floats this high over the floor (its lowest edge within a sword's reach of a player beside it). */
    public static final double CORE_HEIGHT = 3.9;
    /** Broken, stunned or dying, it sinks this low. */
    public static final double LOW_HEIGHT = 2.0;
    /**
     * In phase 2 the eclipse hangs this high, over the pillars' capitals (14 over the floor), until its heart is laid
     * bare (a Break, Nova's stun): then it is dragged down to {@link #LOW_HEIGHT}.
     */
    public static final double HIGH_HEIGHT = 18.0;
    /** High in phase 2 the eclipse is this many times the sun it was; laid bare it shrinks back to its heart. */
    public static final double ECLIPSE_BIG = 2.5;
    /** The core's box. */
    public static final float CORE_SIZE = 3.6f;
    /** The rim: where the halo's plates fly to, and the arena's edge. */
    public static final double RIM = SanctumLayout.RIM_R;
    /** Players count as in the fight within this horizontal distance of the throne. */
    public static final double FIGHT_RADIUS = 31.0;
    public static final double FIGHT_BOTTOM = FLOOR - 12.0;
    public static final double FIGHT_TOP = FLOOR + 36.0;

    /** Monolith spots: compass angles, all at {@link #MONOLITH_RADIUS}. */
    public static final double[] MONOLITH_ANGLES = {0.0, 60.0, 120.0, 180.0, 240.0, 300.0};
    public static final double MONOLITH_RADIUS = 12.0;
    public static final int MONOLITH_WIDTH = 3;
    public static final int MONOLITH_HEIGHT = 4;
    public static final int MONOLITHS = 6;
    public static final int PIPS = 3;

    /** Tendril anchors: floor cracks in the dais round the core (the dais never falls), clear of the monoliths. */
    /** The tendrils' cracks on the dais: uneven in angle and distance (an even four read as a trident from above). */
    public static final double[] TENDRIL_ANGLES = {20.0, 105.0, 200.0, 305.0};
    public static final double[] TENDRIL_RADII = {6.4, 5.4, 6.9, 5.9};

    /** A monolith's blocks: {@code width} columns along X or Z, {@link #MONOLITH_HEIGHT} tall from the floor. */
    public record Monolith(int index, int x, int z, boolean alongX) {
        /** Its footprint's lowest corner and size, in blocks: {x0, z0, x1, z1} (x1, z1 exclusive). */
        public double[] rect() {
            if (alongX) {
                return new double[] {x - 1, z, x + 2, z + 1};
            }
            return new double[] {x, z - 1, x + 1, z + 2};
        }

        /** The middle of its footprint at floor level. */
        public Vec3 middle() {
            double[] r = rect();
            return new Vec3((r[0] + r[2]) / 2.0, FLOOR, (r[1] + r[3]) / 2.0);
        }

        /** Its compass angle from the throne. */
        public double angle() {
            return MONOLITH_ANGLES[index];
        }

        /** The block columns it stands on: {x, z} for each of its three. */
        public List<int[]> columns() {
            List<int[]> out = new ArrayList<>();
            for (int i = -1; i <= 1; i++) {
                out.add(alongX ? new int[] {x + i, z} : new int[] {x, z + i});
            }
            return out;
        }
    }

    private static final List<Monolith> MONOLITH_LIST;

    static {
        List<Monolith> list = new ArrayList<>();
        for (int i = 0; i < MONOLITHS; i++) {
            double a = Math.toRadians(MONOLITH_ANGLES[i]);
            double x = CX + MONOLITH_RADIUS * Math.sin(a);
            double z = CZ - MONOLITH_RADIUS * Math.cos(a);
            // the slab runs along whichever axis is nearer the ring's tangent
            boolean alongX = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a));
            list.add(new Monolith(i, (int) Math.floor(x), (int) Math.floor(z), alongX));
        }
        MONOLITH_LIST = Collections.unmodifiableList(list);
    }

    private HeliarchArena() {
    }

    public static List<Monolith> monoliths() {
        return MONOLITH_LIST;
    }

    /** The core's middle at {@code height} over the floor. */
    public static Vec3 core(double height) {
        return new Vec3(CX, FLOOR + height, CZ);
    }

    /** The flat unit vector toward a compass angle. */
    public static Vec3 dir(double compassDegrees) {
        double a = Math.toRadians(compassDegrees);
        return new Vec3(Math.sin(a), 0.0, -Math.cos(a));
    }

    /** The point at {@code radius} from the throne toward a compass angle, at floor level. */
    public static Vec3 at(double compassDegrees, double radius) {
        return CENTRE.add(dir(compassDegrees).scale(radius));
    }

    /** The compass angle of (x, z) seen from the throne, in [0, 360). */
    public static double angleOf(double x, double z) {
        double a = Math.toDegrees(Math.atan2(x - CX, -(z - CZ)));
        return a < 0 ? a + 360.0 : a;
    }

    /** The horizontal distance of (x, z) from the throne's middle. */
    public static double radiusOf(double x, double z) {
        return Math.hypot(x - CX, z - CZ);
    }

    /** A Minecraft yaw for a compass angle. */
    public static float yawOf(double compassDegrees) {
        return (float) wrap(compassDegrees + 180.0);
    }

    /** A compass angle for a Minecraft yaw. */
    public static double compassOf(float yaw) {
        double a = (yaw - 180.0) % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    /** Tendril {@code i}'s anchor at floor level. */
    public static Vec3 tendrilAnchor(int i) {
        return at(TENDRIL_ANGLES[i], TENDRIL_RADII[i]);
    }

    /** Pillar {@code i}'s footprint as a circle: {x, z, radius}. */
    public static double[] pillarCircle(int i) {
        return new double[] {SanctumLayout.PILLAR_X[i] + 0.5, SanctumLayout.PILLAR_Z[i] + 0.5, Math.sqrt(SanctumLayout.PILLAR_FOOT_SQ) + 0.5};
    }

    /** True if (x, y, z) is where the fight is: over the disc, a little beyond its rim, from the Breach below to high above. */
    public static boolean inFight(double x, double y, double z) {
        return radiusOf(x, z) <= FIGHT_RADIUS && y >= FIGHT_BOTTOM && y <= FIGHT_TOP;
    }

    /** An angle brought into (-180, 180]. */
    public static double wrap(double degrees) {
        double a = degrees % 360.0;
        if (a > 180.0) {
            a -= 360.0;
        } else if (a <= -180.0) {
            a += 360.0;
        }
        return a;
    }
}
