package com.cosmicbreach.guardian.heliarch;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Halo Shed (GDD 7.3), every 30 s in phase 1: the six plates flash in the halo for {@value #FLASH} ticks, arc high
 * over the floor out to the rim (harmless: they pass overhead), hang there turning a sixth of a turn clockwise while
 * the core burns exposed (x1.25), show their way back as red trails for {@value #TRAILS} ticks, then boomerang in
 * straight and low through the rings toward the core (14 each), and climb back into the halo. Between the six return
 * lines there is room to stand. Pure: the flight is a function of the shed's start, the halo's facing when it began
 * and where each plate sits in the halo.
 */
public final class HaloShed {
    public static final int FLASH = 20;
    public static final int OUT = 12;
    public static final int OUT_END = FLASH + OUT;
    public static final int TRAILS = 16;
    public static final int RETURN_START = 104;
    public static final int RETURN = 12;
    public static final int RETURN_END = RETURN_START + RETURN;
    public static final int TOTAL = 120;
    /** How far round the rim the plates drift while they are out. */
    public static final double ORBIT = 30.0;
    public static final double RIM_R = 26.0;
    /** The return lines end this close to the throne (then the plates climb, over any head). */
    public static final double INNER_R = 5.0;
    /** A plate's middle through the return: chest high. */
    public static final double LOW = 1.2;
    /** While out, the plates hang this high, sinking to {@link #LOW} as the trails show. */
    public static final double HANG = 2.4;
    /** A plate's reach from its middle (it is a big thing). */
    public static final double PLATE_REACH = 1.6;
    /** How high the plates arc on the way out. */
    public static final double ARC = 8.0;

    private HaloShed() {
    }

    /** True while the plates are out and the core takes x1.25 (from when they leave to when they are back). */
    public static boolean exposed(double t) {
        return t >= FLASH && t < RETURN_END;
    }

    /** True while the red trails of the way back show. */
    public static boolean trails(double t) {
        return t >= RETURN_START - TRAILS && t < RETURN_END;
    }

    /** True while the plates boomerang back (the only part that hurts). */
    public static boolean returning(double t) {
        return t >= RETURN_START && t < RETURN_END;
    }

    /** The compass angle plate {@code k} leaves toward: spread round the rim from the halo's facing. */
    public static double outAngle(double facingCompass, int k) {
        return norm(facingCompass + 60.0 * k);
    }

    /** The compass angle plate {@code k} hangs at, {@code t} ticks in (it drifts clockwise while out). */
    public static double rimAngle(double facingCompass, int k, double t) {
        double u = clamp((t - OUT_END) / (RETURN_START - OUT_END));
        return norm(outAngle(facingCompass, k) + ORBIT * u);
    }

    /** The angle of plate {@code k}'s way back: where it hangs when the return starts. */
    public static double returnAngle(double facingCompass, int k) {
        return norm(outAngle(facingCompass, k) + ORBIT);
    }

    /** Where plate {@code k}'s return starts and ends (the red trail), at chest height. */
    public static Vec3[] returnLine(double facingCompass, int k) {
        double a = returnAngle(facingCompass, k);
        Vec3 from = HeliarchArena.at(a, RIM_R).add(0, LOW, 0);
        Vec3 to = HeliarchArena.at(a, INNER_R).add(0, LOW, 0);
        return new Vec3[] {from, to};
    }

    /**
     * Where plate {@code k} is {@code t} ticks into the shed: {@code slot} is where it sits in the halo (it leaves from
     * there and comes back to it).
     */
    public static Vec3 position(double facingCompass, int k, double t, Vec3 slot) {
        if (t < FLASH || t >= TOTAL) {
            return slot;
        }
        if (t < OUT_END) {
            double u = smooth((t - FLASH) / OUT);
            Vec3 rim = HeliarchArena.at(outAngle(facingCompass, k), RIM_R).add(0, HANG, 0);
            Vec3 p = slot.add(rim.subtract(slot).scale(u));
            return p.add(0, ARC * Math.sin(Math.PI * u), 0);
        }
        if (t < RETURN_START) {
            double sink = smooth((t - (RETURN_START - TRAILS)) / TRAILS);
            double bob = 0.25 * Math.sin((t - OUT_END) * 0.35 + k);
            return HeliarchArena.at(rimAngle(facingCompass, k, t), RIM_R).add(0, HANG + (LOW - HANG) * sink + bob * (1 - sink), 0);
        }
        Vec3[] line = returnLine(facingCompass, k);
        if (t < RETURN_END) {
            double u = (t - RETURN_START) / RETURN;
            return line[0].add(line[1].subtract(line[0]).scale(u));
        }
        double u = smooth((t - RETURN_END) / (TOTAL - RETURN_END));
        return line[1].add(slot.subtract(line[1]).scale(u));
    }

    /**
     * True if a returning plate that moved from {@code a} to {@code b} this tick struck {@code box} (the plate's reach
     * swept along its path, so a fast plate never skips a player).
     */
    public static boolean strikes(AABB box, Vec3 a, Vec3 b) {
        AABB grown = box.inflate(PLATE_REACH);
        return grown.contains(a) || grown.contains(b) || grown.clip(a, b).isPresent();
    }

    private static double clamp(double u) {
        return Math.max(0.0, Math.min(1.0, u));
    }

    private static double smooth(double u) {
        u = clamp(u);
        return u * u * (3 - 2 * u);
    }

    private static double norm(double a) {
        a %= 360.0;
        return a < 0 ? a + 360.0 : a;
    }
}
