package com.cosmicbreach.entity.gyre;

import net.minecraft.world.phys.Vec3;

/**
 * Where the Gyre Knight's three blades orbit (GDD 7.1: "three blades orbit it on tilted rings"), pure: blade {@code i}
 * rides a ring tilted {@value #TILT_DEGREES} degrees from level, the three rings leaning 120 degrees apart round the
 * core, a third of a turn apart on them. The blade points out from the core (its edge leads). Both sides compute the
 * same positions from the synced mode and its start.
 */
public final class GyreOrbit {
    public static final double TILT_DEGREES = 24.0;
    public static final int BLADES = 3;
    /** A blade's length, from its root to its tip, centred on the orbit radius. */
    public static final double BLADE_LENGTH = 1.4;
    public static final double BLADE_THICKNESS = 0.35;

    private GyreOrbit() {
    }

    /** The unit normal of blade {@code i}'s ring. */
    public static Vec3 normal(int i) {
        double lean = Math.toRadians(TILT_DEGREES);
        double az = i * Math.PI * 2.0 / BLADES;
        return new Vec3(Math.sin(lean) * Math.cos(az), Math.cos(lean), Math.sin(lean) * Math.sin(az));
    }

    /** Blade {@code i}'s middle, from the core, at orbit radius {@code r} after the rings turned {@code spin} radians. */
    public static Vec3 offset(int i, double r, double spin) {
        Vec3 n = normal(i);
        Vec3 u = new Vec3(0, 0, 1).cross(n).normalize();
        Vec3 v = n.cross(u).normalize();
        double a = spin + i * Math.PI * 2.0 / BLADES;
        return u.scale(r * Math.cos(a)).add(v.scale(r * Math.sin(a)));
    }

    /**
     * Which way blade {@code i} points on its ring: along the ring (edge first, a spinning guard) when the rings are
     * tight, out from the core (spokes) when they are wide, blended between.
     */
    public static Vec3 direction(int i, double r, double spin) {
        Vec3 radial = offset(i, 1.0, spin);
        Vec3 tangent = offset(i, 1.0, spin + 0.01).subtract(radial).normalize();
        double f = Math.max(0.0, Math.min(1.0, (r - 1.6) / 1.8));
        Vec3 d = tangent.scale(1.0 - f).add(radial.scale(f));
        return d.lengthSqr() < 1e-9 ? radial : d.normalize();
    }

    /** The blade's root and tip (from the core) for a blade whose middle is at {@code offset}. */
    public static Vec3[] edge(Vec3 offset) {
        double len = offset.length();
        Vec3 d = len < 1e-6 ? new Vec3(1, 0, 0) : offset.scale(1.0 / len);
        return new Vec3[] {d.scale(Math.max(0.2, len - BLADE_LENGTH / 2.0)), d.scale(len + BLADE_LENGTH / 2.0)};
    }
}
