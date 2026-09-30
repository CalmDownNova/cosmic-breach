package com.cosmicbreach.guardian.leviathan;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Song of Pulling (Thalassine Leviathan design v1): after a {@value LeviathanMoves#SONG_TELL}-tick telegraph (the
 * mouth opens toward a target and rings of sound pulse out in a {@value LeviathanMoves#SONG_CONE}-degree cone,
 * {@value LeviathanMoves#SONG_RANGE} blocks long) it pulls everyone in the cone toward its mouth for
 * {@value LeviathanMoves#SONG_PULL} ticks, {@value LeviathanMoves#PULL_MIN} blocks a tick rising to
 * {@value LeviathanMoves#PULL_MAX}. An asteroid (any solid block) between the mouth and a player blocks it; a dash
 * breaks it for {@value LeviathanMoves#DASH_BREAK} ticks; an elytra flier is pulled twice as hard. Reaching the mouth
 * means a bite and a throw. Pure: each client moves its own player by {@link #step}, the server bites with
 * {@link #bites}; both from the same synced mouth and axis.
 */
public final class SongPull {
    /** The cone: the mouth, its unit axis. */
    public record Cone(Vec3 mouth, Vec3 axis) {
        public Cone {
            double len = axis.length();
            axis = len < 1e-9 ? new Vec3(0, 0, 1) : axis.scale(1.0 / len);
        }
    }

    private SongPull() {
    }

    /** The pull's strength (blocks a tick) {@code pullTick} ticks into the pull (0 at its first tick). */
    public static double strength(int pullTick) {
        double f = Math.max(0.0, Math.min(1.0, pullTick / (double) LeviathanMoves.SONG_RAMP));
        return LeviathanMoves.PULL_MIN + (LeviathanMoves.PULL_MAX - LeviathanMoves.PULL_MIN) * f;
    }

    /** True while {@code songTick} (ticks since the song began) is in the pull, after the telegraph. */
    public static boolean pulling(long songTick) {
        return songTick >= LeviathanMoves.SONG_TELL && songTick < LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL;
    }

    /** True if {@code p} is inside the cone: within its range and half its angle of the axis. */
    public static boolean inCone(Cone cone, Vec3 p) {
        Vec3 d = p.subtract(cone.mouth());
        double dist = d.length();
        if (dist > LeviathanMoves.SONG_RANGE) {
            return false;
        }
        if (dist < 1e-6) {
            return true;
        }
        double cos = d.dot(cone.axis()) / dist;
        return cos >= Math.cos(Math.toRadians(LeviathanMoves.SONG_CONE / 2.0));
    }

    /**
     * How far a player whose middle is at {@code p} is moved this tick: toward the mouth by the strength (doubled for a
     * flier), never past the bite; nothing outside the cone, behind a block ({@code blocked}) or just after a dash
     * ({@code exempt}).
     */
    public static Vec3 step(Cone cone, Vec3 p, double strength, boolean flier, boolean exempt, boolean blocked) {
        if (exempt || blocked || !inCone(cone, p)) {
            return Vec3.ZERO;
        }
        Vec3 d = cone.mouth().subtract(p);
        double dist = d.length();
        double room = dist - LeviathanMoves.BITE_REACH * 0.5;
        if (room <= 0) {
            return Vec3.ZERO;
        }
        double s = Math.min(room, strength * (flier ? LeviathanMoves.FLIER_PULL : 1.0));
        return d.scale(s / dist);
    }

    /** True if a player's box has reached the mouth (a bite). */
    public static boolean bites(Vec3 mouth, AABB box) {
        double nx = Math.max(box.minX, Math.min(mouth.x, box.maxX));
        double ny = Math.max(box.minY, Math.min(mouth.y, box.maxY));
        double nz = Math.max(box.minZ, Math.min(mouth.z, box.maxZ));
        double dx = nx - mouth.x;
        double dy = ny - mouth.y;
        double dz = nz - mouth.z;
        return dx * dx + dy * dy + dz * dz <= LeviathanMoves.BITE_REACH * LeviathanMoves.BITE_REACH;
    }
}
