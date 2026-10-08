package com.cosmicbreach.entity.gyre;

import com.cosmicbreach.guardian.AttackPicker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Gyre Knight's orbit modes (GDD 7.1), pure: their timelines, how it picks the next, and what the rings and the hum
 * show, so the mode is always readable (the hum's pitch and the rings' radius tell you what comes next).
 *
 * <ul>
 *   <li><b>Shield Orbit</b> (default): rings tight ({@value #SHIELD_RADIUS}), a steady low hum; the blades turn close
 *       and block projectiles from its front half. Get behind it.</li>
 *   <li><b>Sweep Orbit</b>, when near: it swoops level with its target, then {@value #SWEEP_TELL} ticks of rings
 *       expanding with a rising whine and gold glints on the blades; the blades reach {@value #SWEEP_RADIUS} and turn
 *       twice over {@value #SWEEP_ACTIVE} ticks, {@value #SWEEP_DAMAGE} a blade. Parry a blade to break it for 5 s.</li>
 *   <li><b>Lance Volley</b>, at range: each blade shows a red aim line for {@value #BLADE_TELL} ticks and fires, one
 *       every {@value #BLADE_GAP} ticks, {@value #LANCE_DAMAGE} each. Dash across the lines. While blades are out its
 *       core is exposed (x{@value #CORE_EXPOSED}).</li>
 *   <li><b>Recall Crash</b>, below half health: the blades fly out beyond its target, then {@value #RECALL_TELL} ticks
 *       of red lines from each blade back to the core through where the target stood; they rip back along them,
 *       {@value #RECALL_DAMAGE} each. Leave the lines.</li>
 *   <li><b>Stunned</b>: its poise (50) broke; for {@value #STUN} ticks it sinks slowly (1.1) to beside its target at
 *       their level, or onto the rock under it, never into the gap; core exposed.</li>
 *   <li><b>Dive</b> (1.1), every {@value #VOLLEYS_MIN} to {@value #VOLLEYS_MAX} Lance Volleys at a player on foot: a
 *       {@value #DIVE_TELL} tick tell (the rings swell, the blades glow gold, a rising call), a drop to beside the
 *       target at their level, one cut ({@value #DIVE_DAMAGE}, parryable), then {@value #DIVE_HANG} ticks hanging within
 *       sword reach of them (it follows them if the cut shoves them or they step away), core exposed, before it climbs
 *       back. A rider never draws it: mounted fighting stays stronger.</li>
 * </ul>
 * Ticks count from the mode's start.
 */
public final class GyreModes {
    public enum Mode { SHIELD, SWEEP, LANCE, RECALL, STUNNED, DIVE }

    public static final int MIN_TELEGRAPH = 10;
    public static final double SHIELD_RADIUS = 1.5;
    public static final double SWEEP_RADIUS = 4.0;
    public static final double LANCE_RADIUS = 2.5;
    public static final double CORE_EXPOSED = 1.5;
    /** Ticks it keeps its Shield Orbit between other modes. */
    public static final int SHIELD_MIN = 40;

    // Sweep
    public static final int SWEEP_APPROACH = 30;
    public static final int SWEEP_TELL = 16;
    public static final int SWEEP_ACTIVE = 30;
    public static final int SWEEP_RECOVER = 12;
    public static final double SWEEP_DAMAGE = 8.0;
    /** A parry deals twice this to its poise. */
    public static final double SWEEP_IMPACT = 15.0;
    public static final double SWEEP_RANGE = 10.0;
    public static final int SWEEP_COOLDOWN = 140;
    /** A parried blade is broken this long. */
    public static final int BLADE_BROKEN = 100;

    // Lance Volley
    public static final int BLADE_TELL = 10;
    public static final int BLADE_GAP = 8;
    public static final double LANCE_SPEED = 1.6;
    public static final double LANCE_RANGE = 26.0;
    public static final double LANCE_DAMAGE = 9.0;
    public static final double LANCE_IMPACT = 12.0;
    public static final int LANCE_STICK = 10;
    public static final double RETURN_SPEED = 1.1;
    public static final double LANCE_MIN_RANGE = 4.0;
    public static final int LANCE_COOLDOWN = 150;
    /** The volley ends by this tick whatever the blades do. */
    public static final int LANCE_MAX = 110;

    // Recall Crash
    public static final int RECALL_OUT = 12;
    public static final int RECALL_TELL = 14;
    public static final int RECALL_RIP = 6;
    public static final int RECALL_RECOVER = 10;
    public static final double RECALL_DAMAGE = 10.0;
    public static final double RECALL_IMPACT = 14.0;
    /** How far past where the target stood the blades fly out. */
    public static final double RECALL_BEYOND = 6.0;
    public static final int RECALL_COOLDOWN = 200;

    public static final int STUN = 60;
    /** A stunned Knight sinks at most this fast, and drifts sideways at most this fast (blocks a tick). */
    public static final double STUN_SINK = 0.35;
    public static final double STUN_DRIFT = 0.15;

    // Dive (1.1): a readable melee commit, so a player on foot gets a fair window
    public static final int DIVE_TELL = 20;
    public static final int DIVE_DESCENT_MAX = 30;
    public static final int DIVE_STRIKE = 6;
    public static final int DIVE_HANG = 40;
    public static final double DIVE_SPEED = 0.55;
    /** Where it hangs: this far from its target's feet, toward where it came from, level with them (inside a sword's 3). */
    public static final double DIVE_STAND_OFF = 2.2;
    public static final double DIVE_REACH = 3.2;
    public static final double DIVE_RADIUS = 2.6;
    public static final double DIVE_DAMAGE = 8.0;
    public static final double DIVE_IMPACT = 15.0;
    public static final double DIVE_RANGE = 24.0;
    /** The share of the gap to its stand-off that a hanging Knight closes each tick. */
    public static final double HANG_EASE = 0.5;
    public static final int VOLLEYS_MIN = 2;
    public static final int VOLLEYS_MAX = 3;
    /** The strike's cue (a glint on the blades and a rising whine) sounds this many ticks of flight before the cut: time to parry. */
    public static final int DIVE_CUE_TICKS = 5;
    /** A descent that has moved less than {@value #DIVE_STUCK_PROGRESS} blocks over {@value #DIVE_STUCK_TICKS} ticks is against rock: it cuts where it is. */
    public static final int DIVE_STUCK_TICKS = 5;
    public static final double DIVE_STUCK_PROGRESS = 0.1;

    /** Spin rates, radians a tick. */
    public static final double SHIELD_SPIN = Math.PI * 2.0 / 40.0;
    public static final double SWEEP_SPIN = Math.PI * 4.0 / SWEEP_ACTIVE;
    public static final double LANCE_SPIN = Math.PI * 2.0 / 28.0;

    private GyreModes() {
    }

    /** The tick blade {@code i} fires in a Lance Volley (its red line shows for the {@value #BLADE_TELL} before). */
    public static int fireTick(int i) {
        return BLADE_TELL + BLADE_GAP * i;
    }

    /** The modes it could switch to now, for the picker: by range, and Recall Crash only below half health. */
    public static List<AttackPicker.Option<Mode>> options(double distance, double healthFraction, int bladesReady) {
        List<AttackPicker.Option<Mode>> out = new ArrayList<>();
        if (bladesReady <= 0) {
            return out;
        }
        if (distance <= SWEEP_RANGE) {
            out.add(AttackPicker.Option.weighted(Mode.SWEEP, 1.0, SWEEP_COOLDOWN));
        }
        if (distance >= LANCE_MIN_RANGE) {
            out.add(AttackPicker.Option.weighted(Mode.LANCE, distance > SWEEP_RANGE ? 1.4 : 0.8, LANCE_COOLDOWN));
        }
        if (healthFraction < 0.5) {
            out.add(AttackPicker.Option.weighted(Mode.RECALL, 1.2, RECALL_COOLDOWN));
        }
        return out;
    }

    /** True if it is time to dive: enough volleys since the last dive, at a player on foot within range. */
    public static boolean diveDue(int volleys, int divesAfter, boolean targetOnFoot, double distance) {
        return targetOnFoot && volleys >= divesAfter && distance <= DIVE_RANGE;
    }

    /**
     * {@link #diveDue(int, int, boolean, double)} and a clear line from the Knight to the spot beside its target: with rock
     * in the way the dive waits (the caller keeps its count, so it is still owed at the next pick).
     */
    public static boolean diveDue(int volleys, int divesAfter, boolean targetOnFoot, double distance, boolean lineClear) {
        return lineClear && diveDue(volleys, divesAfter, targetOnFoot, distance);
    }

    /** True once, when {@code remaining} blocks of the descent are left (about {@value #DIVE_CUE_TICKS} ticks at its speed): the strike's cue. */
    public static boolean strikeCueDue(double remaining, boolean alreadyGiven) {
        return !alreadyGiven && remaining <= DIVE_SPEED * DIVE_CUE_TICKS;
    }

    /** True if a descent that moved {@code moved} blocks over the last {@value #DIVE_STUCK_TICKS} ticks is stuck against rock. */
    public static boolean diveStuck(double moved) {
        return moved < DIVE_STUCK_PROGRESS;
    }

    /** True on the dive's cutting ticks ({@code t} ticks in, its descent ended at {@code approachEnd}). */
    public static boolean diveStriking(long t, long approachEnd) {
        return approachEnd >= 0 && t >= approachEnd && t < approachEnd + DIVE_STRIKE;
    }

    /** True while it hangs within reach after the cut. */
    public static boolean diveHanging(long t, long approachEnd) {
        return approachEnd >= 0 && t >= approachEnd + DIVE_STRIKE && t < approachEnd + DIVE_STRIKE + DIVE_HANG;
    }

    /** A point {@link #DIVE_STAND_OFF} from {@code targetFeet}, toward {@code from} across the ground, at the target's level. */
    public static Vec3 standOff(Vec3 targetFeet, Vec3 from) {
        Vec3 flat = new Vec3(from.x - targetFeet.x, 0, from.z - targetFeet.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
        return targetFeet.add(flat.scale(DIVE_STAND_OFF));
    }

    /** Where a stunned Knight settles: beside its target at their level (never higher than it is), or where it is if alone. */
    public static Vec3 stunAnchor(Vec3 feet, @Nullable Vec3 targetFeet) {
        if (targetFeet == null) {
            return feet;
        }
        Vec3 spot = standOff(targetFeet, feet);
        return new Vec3(spot.x, Math.min(spot.y, feet.y), spot.z);
    }

    /**
     * A hanging Knight's motion this tick: easing toward {@link #standOff} its target's feet as they stand now, at most
     * {@link #DIVE_SPEED} a tick. The cut shoves a player like any hit (vanilla knockback, which in the Drift's low gravity
     * carries them several blocks), and a player may dodge or walk off: the hang keeps the Knight within sword reach of them,
     * level with their feet, for its whole {@value #DIVE_HANG} ticks.
     */
    public static Vec3 hangVelocity(Vec3 feet, Vec3 targetFeet) {
        Vec3 d = standOff(targetFeet, feet).subtract(feet).scale(HANG_EASE);
        double len = d.length();
        return len > DIVE_SPEED ? d.scale(DIVE_SPEED / len) : d;
    }

    /** A stunned Knight's motion this tick: easing toward its anchor, sinking at most {@link #STUN_SINK}, never rising. */
    public static Vec3 stunVelocity(Vec3 feet, Vec3 anchor) {
        Vec3 d = anchor.subtract(feet);
        Vec3 side = new Vec3(d.x, 0, d.z).scale(0.2);
        if (side.length() > STUN_DRIFT) {
            side = side.scale(STUN_DRIFT / side.length());
        }
        return new Vec3(side.x, Math.max(-STUN_SINK, Math.min(0.0, d.y * 0.2)), side.z);
    }

    /** True once a mode has run its course ({@code t} ticks in; a sweep's approach ended at {@code approachEnd}). */
    public static boolean done(Mode mode, long t, long approachEnd) {
        return switch (mode) {
            case SHIELD -> false;
            case SWEEP -> approachEnd >= 0 && t >= approachEnd + SWEEP_TELL + SWEEP_ACTIVE + SWEEP_RECOVER;
            case LANCE -> t >= LANCE_MAX;
            case RECALL -> t >= RECALL_OUT + RECALL_TELL + RECALL_RIP + RECALL_RECOVER;
            case STUNNED -> t >= STUN;
            case DIVE -> approachEnd >= 0 && t >= approachEnd + DIVE_STRIKE + DIVE_HANG;
        };
    }

    /** True while a sweep's blades cut ({@code t} ticks in, its approach ended at {@code approachEnd}). */
    public static boolean sweepCutting(long t, long approachEnd) {
        return approachEnd >= 0 && t >= approachEnd + SWEEP_TELL && t < approachEnd + SWEEP_TELL + SWEEP_ACTIVE;
    }

    /** The blades' orbit radius, what the rings show. */
    public static double ringRadius(Mode mode, double t, double approachEnd) {
        return switch (mode) {
            case SHIELD -> SHIELD_RADIUS;
            case SWEEP -> {
                if (approachEnd < 0 || t < approachEnd) {
                    yield SHIELD_RADIUS;
                }
                double u = t - approachEnd;
                if (u < SWEEP_TELL) {
                    double f = u / SWEEP_TELL;
                    yield SHIELD_RADIUS + (SWEEP_RADIUS - SHIELD_RADIUS) * f * f * (3 - 2 * f);
                }
                if (u < SWEEP_TELL + SWEEP_ACTIVE) {
                    yield SWEEP_RADIUS;
                }
                double f = Math.min(1.0, (u - SWEEP_TELL - SWEEP_ACTIVE) / SWEEP_RECOVER);
                yield SWEEP_RADIUS + (SHIELD_RADIUS - SWEEP_RADIUS) * f;
            }
            case LANCE -> LANCE_RADIUS;
            case RECALL -> SHIELD_RADIUS;
            case STUNNED -> 0.9;
            case DIVE -> {
                if (t < DIVE_TELL) {
                    double f = t / DIVE_TELL;
                    yield SHIELD_RADIUS + (DIVE_RADIUS - SHIELD_RADIUS) * f * f * (3 - 2 * f);
                }
                if (approachEnd < 0 || t < approachEnd) {
                    yield DIVE_RADIUS;
                }
                double u = t - approachEnd;
                yield u < DIVE_STRIKE ? DIVE_RADIUS + (SHIELD_RADIUS - DIVE_RADIUS) * (u / DIVE_STRIKE) : SHIELD_RADIUS;
            }
        };
    }

    /** The hum's pitch: low and steady at rest, a rising whine into a sweep, higher and pulsing for the lances. */
    public static float humPitch(Mode mode, double t, double approachEnd) {
        return switch (mode) {
            case SHIELD -> 0.75f;
            case SWEEP -> {
                if (approachEnd < 0 || t < approachEnd) {
                    yield 0.85f;
                }
                double u = t - approachEnd;
                if (u < SWEEP_TELL) {
                    yield (float) (0.85 + 0.9 * (u / SWEEP_TELL));
                }
                yield u < SWEEP_TELL + SWEEP_ACTIVE ? 1.6f : 1.0f;
            }
            case LANCE -> 1.2f + (Math.floorMod((long) t - BLADE_TELL, (long) BLADE_GAP) < 2 ? 0.15f : 0f);
            case RECALL -> (float) (t < RECALL_OUT ? 1.1 : 1.1 + 0.5 * Math.min(1.0, (t - RECALL_OUT) / RECALL_TELL));
            case STUNNED -> 0.5f;
            case DIVE -> {
                if (t < DIVE_TELL) {
                    yield (float) (0.85 + 0.75 * (t / DIVE_TELL));
                }
                if (approachEnd < 0 || t < approachEnd) {
                    yield 1.6f;
                }
                yield t < approachEnd + DIVE_STRIKE ? 1.2f : 0.6f;
            }
        };
    }

    /** How far the blades have turned {@code t} ticks into a mode (radians; the spin speeds up into a sweep). */
    public static double spin(Mode mode, double t, double approachEnd) {
        return switch (mode) {
            case SHIELD, RECALL, DIVE -> SHIELD_SPIN * t;
            case SWEEP -> {
                double end = approachEnd < 0 ? t : Math.min(t, approachEnd);
                double a = SHIELD_SPIN * end;
                if (approachEnd < 0 || t <= approachEnd) {
                    yield a;
                }
                double u = t - approachEnd;
                double tell = Math.min(u, SWEEP_TELL);
                a += SHIELD_SPIN * tell + (SWEEP_SPIN - SHIELD_SPIN) * tell * tell / (2.0 * SWEEP_TELL);
                if (u > SWEEP_TELL) {
                    a += SWEEP_SPIN * (u - SWEEP_TELL);
                }
                yield a;
            }
            case LANCE -> LANCE_SPIN * t;
            case STUNNED -> 0.0;
        };
    }
}
