package com.cosmicbreach.guardian.colossus;

import net.minecraft.world.phys.Vec3;

/**
 * The Prism Colossus's numbers (Prism Colossus design v1) and the paths its fists fly, pure and shared by the
 * server (which hits with them) and the client (which draws them): both sides compute a fist from the same synced
 * inputs, so the fist a player sees is where the hit lands.
 *
 * <p>Its fists leave the wrists for every reaching attack: a gold ring around each wrist holds a tether of light to
 * its fist. A slamming fist rises over its target, hovers while the ring fills and slams down on tick
 * {@value #SLAM_TELL}; a sweeping fist draws back to one edge of the band and sweeps across it. Ticks count from
 * the attack's start. Telegraphs are never shorter than {@value #MIN_TELEGRAPH} ticks.
 */
public final class ColossusMoves {
    public static final int MIN_TELEGRAPH = 12;

    // ------------------------------------------------------------------ Prism Slam
    /** The slam's telegraph: the fist rises and the gold ring fills; it lands on this tick. */
    public static final int SLAM_TELL = 24;
    /** The ring follows its target until this tick, then holds. */
    public static final int SLAM_TRACK = 12;
    /** The gold glint, the parry cue. */
    public static final int SLAM_GLINT = 20;
    /** The fist has flown from the wrist to over its ring by this tick. */
    public static final int SLAM_RISE = 8;
    /** Ticks the fist rests in the floor after an unparried slam before it flies home. */
    public static final int SLAM_REST = 8;
    /** Ticks the fist stays stuck in the floor after a parry: free hits. */
    public static final int SLAM_STUCK = 30;
    /** Ticks a fist takes to fly home to its wrist. */
    public static final int FIST_RETURN = 12;
    public static final double SLAM_RADIUS = 3.0;
    public static final double SLAM_DAMAGE = 16.0;
    public static final double SLAM_IMPACT = 30.0;
    /** The double slam's second ring starts this many ticks after the first. */
    public static final int DOUBLE_SLAM_DELAY = 12;
    /** How high over its ring a slamming fist hovers, and how high it winds up before the glint. */
    public static final double HOVER = 4.5;
    public static final double HOVER_HIGH = 6.5;
    /** A fist in the floor: its middle this far above the floor. */
    public static final double FIST_REST = 0.7;

    // ------------------------------------------------------------------ Facet Sweep
    public static final int SWEEP_TELL = 30;
    /** Ticks the sweep takes from one edge of the band to the other. */
    public static final int SWEEP_SWING = 6;
    public static final int SWEEP_RECOVER = 14;
    /** The drawn-back fist reaches the band's edge by this tick. */
    public static final int SWEEP_DRAW = 14;
    public static final double SWEEP_INNER = 3.0;
    public static final double SWEEP_OUTER = 9.0;
    public static final double SWEEP_ARC = 200.0;
    /** The sweeping fist's distance from the centre and height. */
    public static final double SWEEP_FIST_RADIUS = 6.0;
    public static final double SWEEP_FIST_HEIGHT = 1.0;
    public static final double SWEEP_DAMAGE = 12.0;
    public static final double SWEEP_KNOCKBACK = 1.0;
    public static final double SWEEP_IMPACT = 15.0;

    // ------------------------------------------------------------------ Refraction
    /** 4 s (2 s before 1.2.1): time to reach a lit crystal at the rim and turn it 1 to 3 times. */
    public static final int REFRACTION_CHARGE = 80;
    public static final int REFRACTION_FIRE = 40;
    public static final int REFRACTION_RECOVER = 10;
    /** Degrees a tick it turns during a charge to face its lit crystals (half a turn in under a second). */
    public static final float REFRACTION_TURN_SPEED = 9.0f;
    /**
     * The fired beam races along its path at this many blocks a tick, so the light is seen leaving the eye and bouncing
     * from crystal to crystal (a full three-crystal path, about 60 blocks, takes 7 ticks). Players are hurt only where it
     * has reached; a beam turned back hits the body when it arrives.
     */
    public static final double BEAM_SPEED = 9.0;
    /** A player in the beam takes a pulse at most this often. */
    public static final int BEAM_PULSE = 10;
    public static final double BEAM_DAMAGE = 6.0;
    public static final double BEAM_KNOCKBACK = 0.6;
    public static final double BEAM_RADIUS = 0.45;
    /** A beam turned back into the core: 50 (40 before 1.2.1)... */
    public static final double CORE_HIT_DAMAGE = 50.0;
    /** ...and a whole Break gauge, always a Break... */
    public static final double CORE_HIT_GAUGE = ColossusMoves.BREAK_POISE;
    /** ...once the beam has burned into the body this long (so it is seen striking before the Colossus slumps). */
    public static final int CORE_HIT_BREAK_DELAY = 10;

    // ------------------------------------------------------------------ Prism Burst (phase 2)
    public static final int BURST_TELL = 16;
    public static final int BURST_RECOVER = 12;
    public static final double BURST_RADIUS = 3.0;
    public static final double BURST_DAMAGE = 8.0;
    public static final double BURST_KNOCKBACK = 1.5;
    /** It bursts when a player stays within {@link #BURST_RADIUS} this long. */
    public static final int BURST_HUG_TICKS = 60;

    // ------------------------------------------------------------------ cooldowns (from each attack's start)
    public static final int SLAM_COOLDOWN = 80;
    public static final int SWEEP_COOLDOWN = 200;
    public static final int REFRACTION_COOLDOWN = 280;
    public static final int BURST_COOLDOWN = 160;
    public static final double SLAM_WEIGHT = 3.0;
    public static final double SWEEP_WEIGHT = 4.0;
    /** The first Refraction waits this long into the fight (after the intro). */
    public static final int FIRST_REFRACTION = 200;
    /** Idle ticks between attacks, turning toward the target. */
    public static final int GAP = 16;

    // ------------------------------------------------------------------ the fight's other beats
    public static final int INTRO = 200;
    public static final int FRACTURE = 60;
    /** Bursting into shards, then shards landing. */
    public static final int SHATTER = 30;
    /** The shards flying home and the Colossus re-forming. */
    public static final int REFORM = 50;
    /** The pillar dimming to a stump. */
    public static final int DYING = 60;
    /** Nobody in the arena this long resets the fight. */
    public static final int RESET_ABSENT = 600;
    /** Degrees a tick it turns toward its target. */
    public static final float TURN_SPEED = 4.0f;

    // ------------------------------------------------------------------ the body's stats
    /**
     * 420, not the GDD's 280: a bot with perfect parries killed 280 in 38 to 51 s at level 12, against a
     * design target of about 2.5 minutes for a first-time player. At 420 the bot needs about a minute.
     */
    public static final double BASE_HEALTH = 420.0;
    public static final double ARMOR = 6.0;
    public static final double TOUGHNESS = 2.0;
    public static final double BREAK_POISE = 150.0;
    public static final double BREAK_DAMAGE_TAKEN = 1.5;
    public static final double SHARD_HEALTH = 40.0;
    /** Parrying the slam puts twice its Impact into the gauge (the engine's parry rule): 60. */
    public static final double PARRY_GAUGE = 2.0 * SLAM_IMPACT;

    // ------------------------------------------------------------------ where the wrists are (entity frame)
    /**
     * A hanging fist's middle (its wrist, for flight paths): this far to the side, this high over the floor, this far
     * ahead (gen_colossus.py: the arm bent at the elbow, the forearm 20 degrees forward, measured on the built model).
     */
    public static final double WRIST_SIDE = 2.625;
    public static final double WRIST_HEIGHT = 1.704;
    public static final double WRIST_AHEAD = 0.743;

    private ColossusMoves() {
    }

    /** The wrist of the right ({@code right} true) or left arm for a Colossus at {@code centre} facing {@code yaw}. */
    public static Vec3 wrist(Vec3 centre, float yaw, boolean right) {
        Vec3 f = CrownArena.forward(yaw);
        // (-f.z, 0, f.x) is the entity's right (facing south, +Z, its right is west, -X)
        Vec3 side = new Vec3(-f.z, 0.0, f.x).scale(right ? 1.0 : -1.0);
        return centre.add(side.scale(WRIST_SIDE)).add(f.scale(WRIST_AHEAD)).add(0.0, WRIST_HEIGHT, 0.0);
    }

    /**
     * A slamming fist at tick {@code t} (fractional on the client) of its slam: it flies from {@code wrist} to over
     * {@code ring} (the ring's centre on the floor) by {@link #SLAM_RISE}, winds up to {@link #HOVER_HIGH} by the
     * glint, drops to the floor by {@link #SLAM_TELL} and rests there.
     */
    public static Vec3 slamFist(double t, Vec3 wrist, Vec3 ring) {
        Vec3 hover = ring.add(0.0, HOVER, 0.0);
        if (t <= 0) {
            return wrist;
        }
        if (t < SLAM_RISE) {
            double u = smooth(t / SLAM_RISE);
            Vec3 p = lerp(wrist, hover, u);
            return p.add(0.0, 2.5 * Math.sin(Math.PI * u), 0.0); // an arc up and over
        }
        if (t < SLAM_GLINT) {
            double u = smooth((t - SLAM_RISE) / (SLAM_GLINT - SLAM_RISE));
            return ring.add(0.0, HOVER + (HOVER_HIGH - HOVER) * u, 0.0);
        }
        if (t < SLAM_TELL) {
            double u = (t - SLAM_GLINT) / (SLAM_TELL - SLAM_GLINT);
            return ring.add(0.0, FIST_REST + (HOVER_HIGH - FIST_REST) * (1.0 - u * u), 0.0);
        }
        return ring.add(0.0, FIST_REST, 0.0);
    }

    /** A fist flying home from {@code from} to {@code wrist}, {@code t} ticks into its {@link #FIST_RETURN}. */
    public static Vec3 returningFist(double t, Vec3 from, Vec3 wrist) {
        if (t >= FIST_RETURN) {
            return wrist;
        }
        double u = smooth(Math.max(0.0, t) / FIST_RETURN);
        return lerp(from, wrist, u).add(0.0, 2.0 * Math.sin(Math.PI * u), 0.0);
    }

    /** The sweeping fist's angle (degrees from the facing, positive clockwise) at tick {@code t} of the sweep. */
    public static double sweepAngle(double t) {
        double half = SWEEP_ARC / 2.0;
        if (t < SWEEP_TELL) {
            return -half;
        }
        double u = Math.min(1.0, (t - SWEEP_TELL) / SWEEP_SWING);
        return -half + SWEEP_ARC * u;
    }

    /**
     * The sweeping fist at tick {@code t}: out from the wrist to the band's first edge by {@link #SWEEP_DRAW}, held
     * there (drawn back) until {@link #SWEEP_TELL}, then across the arc at {@link #SWEEP_FIST_RADIUS}.
     */
    public static Vec3 sweepFist(double t, Vec3 wrist, Vec3 centre, float facing) {
        Vec3 edge = onArc(centre, facing, sweepAngle(0), SWEEP_FIST_RADIUS, SWEEP_FIST_HEIGHT);
        if (t < SWEEP_DRAW) {
            double u = smooth(Math.max(0.0, t) / SWEEP_DRAW);
            return lerp(wrist, edge, u).add(0.0, 1.2 * Math.sin(Math.PI * u), 0.0);
        }
        if (t < SWEEP_TELL) {
            return edge;
        }
        return onArc(centre, facing, sweepAngle(t), SWEEP_FIST_RADIUS, SWEEP_FIST_HEIGHT);
    }

    /** The tick of the sweep on which it passes {@code angle} (degrees from the facing), from the tell's end. */
    public static int sweepTickAt(double angle) {
        double half = SWEEP_ARC / 2.0;
        double u = (Math.max(-half, Math.min(half, angle)) + half) / SWEEP_ARC;
        return SWEEP_TELL + (int) Math.min(SWEEP_SWING - 1, Math.floor(u * SWEEP_SWING));
    }

    /** A point at {@code angle} degrees from {@code facing} (clockwise), {@code radius} out and {@code height} up. */
    public static Vec3 onArc(Vec3 centre, float facing, double angle, double radius, double height) {
        Vec3 dir = CrownArena.forward((float) (facing + angle));
        return centre.add(dir.scale(radius)).add(0.0, height, 0.0);
    }

    /** The length of a whole slam, rest and return included, parried or not. */
    public static int slamLength(boolean parried) {
        return SLAM_TELL + (parried ? SLAM_STUCK : SLAM_REST) + FIST_RETURN;
    }

    /** The double slam: the second fist lands {@link #DOUBLE_SLAM_DELAY} after the first. */
    public static int doubleSlamLength(boolean secondParried) {
        return DOUBLE_SLAM_DELAY + slamLength(secondParried);
    }

    public static int sweepLength() {
        return SWEEP_TELL + SWEEP_SWING + SWEEP_RECOVER;
    }

    /** How far along its path the beam reaches {@code t} ticks (fractional on the client) into its fire. */
    public static double beamReach(double t) {
        return (Math.max(0.0, t) + 1.0) * BEAM_SPEED;
    }

    /** The tick of the fire on which the beam first reaches {@code length} along its path. */
    public static int beamArrival(double length) {
        return Math.max(0, (int) Math.ceil(length / BEAM_SPEED) - 1);
    }

    /** The distance along a beam's points to point {@code i} (0 for the first). */
    public static double along(java.util.List<Vec3> pts, int i) {
        double d = 0.0;
        for (int j = 1; j <= i && j < pts.size(); j++) {
            d += pts.get(j).distanceTo(pts.get(j - 1));
        }
        return d;
    }

    public static int refractionLength() {
        return REFRACTION_CHARGE + REFRACTION_FIRE + REFRACTION_RECOVER;
    }

    public static int burstLength() {
        return BURST_TELL + BURST_RECOVER;
    }

    // ------------------------------------------------------------------ when its voice may speak (1.1)

    /** Ticks of quiet a Colossus line keeps before the readable part of the next telegraph (short: the part is already the last of it). */
    public static final int VOICE_MARGIN = 2;

    /**
     * One attack as its voice sees it: the ticks from its start to its strike (its telegraph), the tick the player starts reading it
     * (its last {@value #MIN_TELEGRAPH} ticks, the design's floor for a telegraph: the ring filling and the glint; before it the fist is
     * only flying out), and the fewest ticks it takes to end (a parried slam is longer). A line never sounds over the readable part. The
     * Refraction is read from the last of its charge on and all through its fire: its beams are drawn and turned back then.
     */
    public record Shape(int telegraph, int minLength, int readableFrom) {}

    public static final Shape SLAM_SHAPE = new Shape(SLAM_TELL, slamLength(false), SLAM_TELL - MIN_TELEGRAPH);
    public static final Shape DOUBLE_SLAM_SHAPE = new Shape(DOUBLE_SLAM_DELAY + SLAM_TELL, doubleSlamLength(false), SLAM_TELL - MIN_TELEGRAPH);
    public static final Shape SWEEP_SHAPE = new Shape(SWEEP_TELL, sweepLength(), SWEEP_TELL - MIN_TELEGRAPH);
    public static final Shape REFRACTION_SHAPE = new Shape(REFRACTION_CHARGE + REFRACTION_FIRE, refractionLength(), REFRACTION_CHARGE - MIN_TELEGRAPH);
    public static final Shape BURST_SHAPE = new Shape(BURST_TELL, burstLength(), BURST_TELL - MIN_TELEGRAPH);

    /**
     * Ticks from now until the readable part of the next attack's telegraph begins, at the earliest, from the boss's own schedule:
     * {@code untilGap} is when the gap after the current attack (or Break) ends; each attack it could choose starts then or when its
     * cooldown ends, the later, and is read from its own {@code readableFrom} after that. The slam (or the double slam) is always a
     * candidate; the sweep only while someone stands in its band, the Refraction while its crystals stand, the burst while someone
     * hugs it (the open flags).
     */
    public static long nextReadableIn(long untilGap, long slamLeft, long sweepLeft, boolean sweepOpen, long refractionLeft,
            boolean refractionOpen, long burstLeft, boolean burstOpen) {
        long soonest = Math.max(untilGap, slamLeft) + SLAM_SHAPE.readableFrom();
        if (sweepOpen) {
            soonest = Math.min(soonest, Math.max(untilGap, sweepLeft) + SWEEP_SHAPE.readableFrom());
        }
        if (refractionOpen) {
            soonest = Math.min(soonest, Math.max(untilGap, refractionLeft) + REFRACTION_SHAPE.readableFrom());
        }
        if (burstOpen) {
            soonest = Math.min(soonest, Math.max(untilGap, burstLeft) + BURST_SHAPE.readableFrom());
        }
        return soonest;
    }

    /**
     * How long it is quiet, {@code t} ticks into an attack of {@code shape}: until its own readable part begins, none inside it, and
     * after the strike until the next one ({@code nextIn}, see {@link #nextReadableIn}).
     */
    public static int quietTicks(Shape shape, long t, long nextIn) {
        if (t < shape.readableFrom()) {
            return (int) (shape.readableFrom() - t);
        }
        if (t < shape.telegraph()) {
            return 0;
        }
        return (int) Math.max(0L, nextIn);
    }

    /** Between attacks: ticks until the next readable part, {@code nextIn}. */
    public static int idleQuietTicks(long nextIn) {
        return (int) Math.max(0L, nextIn);
    }

    static double smooth(double u) {
        double x = Math.max(0.0, Math.min(1.0, u));
        return x * x * (3.0 - 2.0 * x);
    }

    static Vec3 lerp(Vec3 a, Vec3 b, double u) {
        return new Vec3(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, a.z + (b.z - a.z) * u);
    }
}
