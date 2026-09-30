package com.cosmicbreach.mount;

/**
 * The Lumen Stag's numbers (GDD 8.1), pure.
 *
 * <p><b>Trust.</b> A wild stag's trust runs from 0 to {@value #TAME_AT}. A hand-fed Starbloom adds 1 or 2
 * ({@link #feed}); at {@value #TAME_AT} it is tamed. Sprinting within {@value #LOSS_RADIUS} blocks of a herd, or
 * hitting any of it, takes {@value #LOSS} from every untamed stag of that herd ({@link #lose}); a sprint costs it once
 * per {@value #SPRINT_LOSS_COOLDOWN} ticks, a hit every time. A herd bolts from anyone sprinting within
 * {@value #BOLT_RADIUS} blocks ({@link #bolts}). A player who walks up standing (not sneaking) makes a wild stag step
 * away inside {@value #WARY_RADIUS} blocks ({@link #wary}): the herd is fed by those who approach sneaking. Trust shows
 * only in the antlers ({@link #antlerGlow}).
 *
 * <p><b>Jumps.</b> Ridden, it jumps {@code 2.5}, {@code 3.5} and {@code 4.0} blocks, the second and third in mid-air
 * (the Comet Bridle adds a fourth of {@code 4.0}). A jump sets the vertical speed to what rises exactly that high under
 * vanilla's motion for a ridden mount ({@link #jumpVelocity}: each tick the body moves by its speed, then the speed loses
 * gravity and keeps 98% of the rest; and on the rider's client, which moves it, the speed is also scaled by
 * {@value #CLIENT_DRAG} at the start of every tick after the first), measured from where the jump starts.
 *
 * <p><b>Glide.</b> Holding jump while falling it sinks {@value #GLIDE_SINK} blocks a tick while moving
 * {@value #GLIDE_SPEED} forward (5.5 to 1); the Halo Reins cut the sink by 30% ({@link #glideSink}).
 */
public final class StagRules {
    public static final double HEALTH = 30.0;
    public static final double SPEED = 0.30;
    /** Vanilla's gravity for living entities, blocks per tick per tick, and the vertical drag. */
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    /**
     * On the rider's client a ridden mount's speed is scaled by this at the start of each tick, before it moves
     * (vanilla's LivingEntity.aiStep for an entity whose AI that side does not run). The jumps count it; the glide keeps
     * its own speed from tick to tick, so it is untouched by it.
     */
    public static final double CLIENT_DRAG = 0.98;
    /** Heights of the jumps, in order; the first leaves the ground. */
    public static final double[] JUMP_HEIGHTS = {2.5, 3.5, 4.0};
    /** The Comet Bridle's fourth jump. */
    public static final double BRIDLE_JUMP_HEIGHT = 4.0;
    public static final double GLIDE_SINK = 0.1;
    public static final double GLIDE_SPEED = 0.55;
    /** The Halo Reins: glide sink -30%. */
    public static final double HALO_SINK_SCALE = 0.7;
    /** How much of the gap to the glide's forward velocity closes each tick. */
    public static final double GLIDE_STEER = 0.35;
    /** A fall only counts toward fall damage while it drops faster than this a tick (a glide never does). */
    public static final double SAFE_SINK = 0.15;

    public static final int TAME_AT = 5;
    public static final int LOSS = 2;
    public static final double BOLT_RADIUS = 16.0;
    public static final double LOSS_RADIUS = 8.0;
    public static final double WARY_RADIUS = 4.0;
    /** A sprint costs a herd trust at most once in this many ticks. */
    public static final int SPRINT_LOSS_COOLDOWN = 60;
    /** How long a herd runs when it bolts. */
    public static final int BOLT_TICKS = 60;
    /** A stag chews a Starbloom this long and takes no other meanwhile. */
    public static final int CHEW_TICKS = 20;
    /** Stags this close with the same herd id count as one herd. */
    public static final double HERD_RADIUS = 48.0;
    /** A drop deeper than this is a cliff: an unridden stag never steps over one, nor spawns beside one. */
    public static final int CLIFF = 4;

    private StagRules() {
    }

    // ------------------------------------------------------------------ trust

    /** Trust after a feed: +1, or +2 when {@code bonus}, never past {@value #TAME_AT}. */
    public static int feed(int trust, boolean bonus) {
        return Math.min(TAME_AT, Math.max(0, trust) + (bonus ? 2 : 1));
    }

    /** Trust after a scare (a sprint near the herd, a hit on any of it). */
    public static int lose(int trust) {
        return Math.max(0, trust - LOSS);
    }

    public static boolean tames(int trust) {
        return trust >= TAME_AT;
    }

    /** True if a player {@code distance} blocks away makes the herd bolt. */
    public static boolean bolts(double distance, boolean sprinting) {
        return sprinting && distance <= BOLT_RADIUS;
    }

    /** True if a player {@code distance} blocks away costs the herd trust (subject to the cooldown). */
    public static boolean spooks(double distance, boolean sprinting) {
        return sprinting && distance <= LOSS_RADIUS;
    }

    /** True if a sprint may cost trust at {@code now}, the last one having cost it at {@code last}. */
    public static boolean sprintLossReady(long now, long last) {
        return now - last >= SPRINT_LOSS_COOLDOWN;
    }

    /** True if a player {@code distance} blocks away makes a wild stag step away: standing, and close. */
    public static boolean wary(double distance, boolean sneaking) {
        return !sneaking && distance <= WARY_RADIUS;
    }

    /**
     * How bright the crystal antlers glow, 0 to 1: a faint light at no trust, brighter with each point, full once
     * tamed. The only trust meter there is.
     */
    public static float antlerGlow(int trust, boolean tamed) {
        if (tamed) {
            return 1.0f;
        }
        int t = Math.max(0, Math.min(TAME_AT, trust));
        return 0.12f + 0.88f * t / TAME_AT;
    }

    // ------------------------------------------------------------------ jumps

    /** Jumps before landing: three, four with the Comet Bridle. */
    public static int jumps(boolean bridle) {
        return bridle ? JUMP_HEIGHTS.length + 1 : JUMP_HEIGHTS.length;
    }

    /** Height of jump {@code index} (0 leaves the ground). */
    public static double jumpHeight(int index) {
        if (index < 0) {
            return 0.0;
        }
        return index < JUMP_HEIGHTS.length ? JUMP_HEIGHTS[index] : BRIDLE_JUMP_HEIGHT;
    }

    /**
     * How high a body started upward at {@code v0} rises under vanilla's motion: each tick it moves by its speed, then
     * the speed becomes (speed - gravity) x drag; from the second tick on the speed is first scaled by
     * {@code preScale} (a ridden mount on its rider's client: {@value #CLIENT_DRAG}; 1 for a body its side runs itself)
     * and zeroed under 0.003, as vanilla does; the rise ends on the first tick the speed is no longer upward.
     */
    public static double apex(double v0, double gravity, double preScale) {
        if (!(v0 > 0.0)) {
            return 0.0;
        }
        double y = v0;
        double v = (v0 - gravity) * DRAG;
        for (int i = 0; i < 2000; i++) {
            v *= preScale;
            if (Math.abs(v) < 0.003) {
                v = 0.0;
            }
            if (v <= 0.0) {
                break;
            }
            y += v;
            v = (v - gravity) * DRAG;
        }
        return y;
    }

    /** The rise of a ridden mount's jump started at {@code v0} (moved by its rider's client). */
    public static double apex(double v0, double gravity) {
        return apex(v0, gravity, CLIENT_DRAG);
    }

    /** The starting upward speed with which a ridden mount rises exactly {@code height} blocks (bisection on {@link #apex}). */
    public static double jumpVelocity(double height, double gravity) {
        if (!(height > 0.0) || !(gravity > 0.0)) {
            return 0.0;
        }
        double lo = 0.0;
        double hi = 0.05;
        while (apex(hi, gravity) < height && hi < 50.0) {
            hi *= 2.0;
        }
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            if (apex(mid, gravity) < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /** The upward speed of jump {@code index} at vanilla gravity. */
    public static double jumpVelocity(int index) {
        return jumpVelocity(jumpHeight(index), GRAVITY);
    }

    /**
     * What a press of jump does now. {@code used}: jumps spent since the stag left the ground; returns the index of
     * the jump to make, or -1 for none (on the ground it is always the first; in the air the next, while any is left).
     */
    public static int nextJump(boolean onGround, int used, boolean bridle) {
        if (onGround) {
            return 0;
        }
        return used < jumps(bridle) ? Math.max(1, used) : -1;
    }

    // ------------------------------------------------------------------ glide

    /** Blocks a tick the glide sinks. */
    public static double glideSink(boolean haloReins) {
        return haloReins ? GLIDE_SINK * HALO_SINK_SCALE : GLIDE_SINK;
    }

    /** True while the rider's held jump makes a falling stag glide. */
    public static boolean glides(boolean jumpHeld, boolean onGround, double verticalSpeed, boolean inFluid) {
        return jumpHeld && !onGround && !inFluid && verticalSpeed <= 0.0;
    }

    /**
     * One tick of the glide: the horizontal velocity {@code (vx, vz)} closes {@value #GLIDE_STEER} of its gap to
     * {@value #GLIDE_SPEED} blocks a tick along the heading {@code yawDegrees} (Minecraft's: 0 faces +z, 90 faces -x),
     * and the vertical is the sink. Returns {@code {vx, vy, vz}}.
     */
    public static double[] glide(double vx, double vz, float yawDegrees, boolean haloReins) {
        double yaw = Math.toRadians(yawDegrees);
        double tx = -Math.sin(yaw) * GLIDE_SPEED;
        double tz = Math.cos(yaw) * GLIDE_SPEED;
        return new double[] {vx + (tx - vx) * GLIDE_STEER, -glideSink(haloReins), vz + (tz - vz) * GLIDE_STEER};
    }

    /** True if a move of {@code dy} blocks keeps the fall distance at zero (rising, or sinking no faster than a glide). */
    public static boolean fallHarmless(double dy) {
        return dy > -SAFE_SINK;
    }
}
