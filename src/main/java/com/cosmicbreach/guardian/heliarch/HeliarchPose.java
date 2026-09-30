package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import net.minecraft.world.phys.Vec3;

/**
 * Where the Heliarch's pieces are at a moment, from its synced state: the core, the six plates of its halo and its two
 * hands. The server moves its hit parts and deals its hits with these; the client draws the model with the same
 * numbers, so every piece is exactly where it hits. Pure (vectors only).
 *
 * <p>The frame: {@code facing} is a compass angle (clockwise from north); forward is toward it, right is a quarter turn
 * clockwise. The <b>halo</b> is a crown of six blades round the core, turning slowly about it: <b>closed</b> the blades
 * stand up round the core as a bud (the core hidden inside, its light leaking at the seams); <b>open</b>, while it
 * attacks, they lean out flat into a sunburst round the blazing core. A crown reads as a halo from every side of the
 * arena, where a ring standing on edge would be a line from the side.
 */
public final class HeliarchPose {
    /** Where the blades' inner lips sit round the core, closed and open (out from its axis, and up from its middle). */
    public static final double CROWN_CLOSED_R = 3.0;
    public static final double CROWN_CLOSED_H = -2.7;
    public static final double CROWN_OPEN_R = 3.4;
    public static final double CROWN_OPEN_H = 0.2;
    /** How far the blades lean out from upright, closed and open, in degrees. */
    public static final double CROWN_CLOSED_LEAN = 62.0;
    /**
     * Closed, the blades turn this far about their own length, fan-wise, so the core's light shows between them (a
     * shut crown hid it: G9p).
     */
    public static final double CROWN_CLOSED_TWIST = 24.0;
    public static final double CROWN_OPEN_LEAN = 70.0;
    /** From a blade's middle to its inner lip, in blocks (its model is drawn four times its built size). */
    public static final double PLATE_LIP = 2.875;
    /** The crown turns this many degrees a tick. */
    public static final double SPIN = 0.4;
    public static final int OPEN_TICKS = 8;

    /** The hands at rest: beside the crown, a little forward, raised; open, palms toward its target. */
    public static final double HAND_SIDE = 9.5;
    public static final double HAND_FWD = 1.2;
    public static final double HAND_DOWN = 1.2;
    /** A slamming hand's palm rests this high over the floor. */
    public static final double HAND_FLOOR = 0.75;
    /** A Sunderfall's hand rises this high over its ring. */
    public static final double HAND_RISE = 11.0;
    public static final double SWEEP_HAND_R = 13.0;

    /** Intro: the plates rise from the rim as debris, then fly in; the hands form. */
    public static final int INTRO_DEBRIS = 60;
    public static final int INTRO_ASSEMBLED = 140;
    public static final int INTRO_HANDS = 100;
    public static final int INTRO_HANDS_DONE = 160;
    /** Hollowing: the plates shake loose, then fly to their monoliths and slam at {@link HeliarchMoves#HOLLOWING_SLAM}. */
    public static final int TEAR = 30;

    /** What the pose depends on: the entity's synced state, and which hand a Sunderfall uses. */
    public record Input(State state, long stateStart, Action action, long actionStart, Vec3 actionPos, float actionAngle, boolean open,
                        long openSince, long breakStart, long stunUntil, long novaStart, boolean sunderRight, boolean sunderParried,
                        Action brokenAction, long brokenActionStart) {
        boolean broken(double t) {
            return breakStart != Long.MIN_VALUE && t >= breakStart && t < breakStart + HeliarchMoves.BREAK_TICKS + 20;
        }
    }

    private HeliarchPose() {
    }

    // ------------------------------------------------------------------ the core

    /** The core's height over the floor. */
    public static double coreHeight(Input in, double t) {
        double s = t - in.stateStart();
        double h = switch (in.state()) {
            case INTRO -> lerp(0.5, HeliarchArena.CORE_HEIGHT, smooth((s - 40) / 80.0));
            case REGENT -> lerp(HeliarchArena.CORE_HEIGHT, HeliarchArena.LOW_HEIGHT, low(in, t));
            // hollowing, it climbs over the pillars, and there the eclipse hangs until its heart is laid bare
            case HOLLOWING -> lerp(HeliarchArena.CORE_HEIGHT, HeliarchArena.HIGH_HEIGHT, smooth(s / 60.0));
            case HOLLOW, COLLAPSE -> lerp(HeliarchArena.HIGH_HEIGHT, HeliarchArena.LOW_HEIGHT, low(in, t));
            case DYING -> lerp(lerp(HeliarchArena.HIGH_HEIGHT, HeliarchArena.LOW_HEIGHT, low(in, in.stateStart())),
                    HeliarchArena.LOW_HEIGHT, smooth(s / 40.0));
        };
        return h + 0.12 * Math.sin(t * 0.08);
    }

    /** The eclipse's size over the sun's (phase 2): large while it hangs high, back to its heart's size laid bare. */
    public static double eclipseScale(Input in, double t) {
        double s = t - in.stateStart();
        return switch (in.state()) {
            case HOLLOWING -> lerp(1.0, HeliarchArena.ECLIPSE_BIG, smooth((s - 40) / 50.0));
            case HOLLOW, COLLAPSE -> lerp(HeliarchArena.ECLIPSE_BIG, 1.0, low(in, t));
            case DYING -> lerp(lerp(HeliarchArena.ECLIPSE_BIG, 1.0, low(in, in.stateStart())), 1.0, smooth(s / 40.0));
            default -> 1.0;
        };
    }

    /** How far it has sunk for a Break or a Nova's stun, 0 to 1. */
    public static double low(Input in, double t) {
        double best = 0.0;
        if (in.breakStart() != Long.MIN_VALUE) {
            best = Math.max(best, dip(t - in.breakStart(), HeliarchMoves.BREAK_TICKS));
        }
        if (in.stunUntil() != Long.MIN_VALUE) {
            best = Math.max(best, dip(t - (in.stunUntil() - HeliarchMoves.NOVA_STUN), HeliarchMoves.NOVA_STUN));
        }
        return best;
    }

    /** Down over 10 ticks, held for {@code length}, back up over 20. */
    private static double dip(double s, int length) {
        if (s < 0 || s > length + 20) {
            return 0.0;
        }
        if (s < 10) {
            return smooth(s / 10.0);
        }
        return s < length ? 1.0 : 1.0 - smooth((s - length) / 20.0);
    }

    public static Vec3 core(Input in, double t) {
        return HeliarchArena.core(coreHeight(in, t));
    }

    /** How open the halo is, 0 (a closed shell) to 1 (the ring). */
    public static double openness(Input in, double t) {
        if (in.state() == State.INTRO) {
            return 0.0;
        }
        double u = clamp((t - in.openSince()) / OPEN_TICKS);
        return smooth(in.open() ? u : 1.0 - u);
    }

    // ------------------------------------------------------------------ frames

    public static Vec3 forward(double facing) {
        return HeliarchArena.dir(facing);
    }

    public static Vec3 right(double facing) {
        return HeliarchArena.dir(facing + 90.0);
    }

    /** Blade {@code k}'s compass angle round the core at {@code t}. */
    public static double crownAngle(int k, double t) {
        return 60.0 * k + SPIN * t;
    }

    /** The blades' lean at openness {@code open}, in radians. */
    public static double lean(double open) {
        return Math.toRadians(lerp(CROWN_CLOSED_LEAN, CROWN_OPEN_LEAN, open));
    }

    /** Blade {@code k}'s ray (its length, from its lip out to its point) at openness {@code open}. */
    public static Vec3 ray(int k, double t, double open) {
        double lam = lean(open);
        return new Vec3(0, Math.cos(lam), 0).add(HeliarchArena.dir(crownAngle(k, t)).scale(Math.sin(lam)));
    }

    /** Blade {@code k}'s face (outward: out and a little down when open) at openness {@code open}. */
    public static Vec3 face(int k, double t, double open) {
        double lam = lean(open);
        Vec3 f = HeliarchArena.dir(crownAngle(k, t)).scale(Math.cos(lam)).subtract(0, Math.sin(lam), 0);
        double tw = Math.toRadians(lerp(CROWN_CLOSED_TWIST, 0.0, open));
        if (Math.abs(tw) < 1e-9) {
            return f;
        }
        // turned about the blade's own length (the face stays square to it)
        Vec3 r = ray(k, t, open);
        return f.scale(Math.cos(tw)).add(r.cross(f).scale(Math.sin(tw)));
    }

    /** Where blade {@code k}'s middle sits in the crown round {@code core} at openness {@code open}. */
    public static Vec3 crownSlot(Vec3 core, int k, double t, double open) {
        Vec3 dir = HeliarchArena.dir(crownAngle(k, t));
        double r0 = lerp(CROWN_CLOSED_R, CROWN_OPEN_R, open);
        double h0 = lerp(CROWN_CLOSED_H, CROWN_OPEN_H, open);
        return core.add(dir.scale(r0)).add(0, h0, 0).add(ray(k, t, open).scale(PLATE_LIP));
    }

    /** Where blade {@code k} sits in the open crown (a Halo Shed leaves from and comes back to it). */
    public static Vec3 ringSlot(Vec3 core, double facing, int k, double t) {
        return crownSlot(core, k, t, 1.0);
    }

    /** Where blade {@code k} sits in the halo now: between the closed bud and the open crown by the openness. */
    public static Vec3 haloSlot(Input in, double facing, int k, double t) {
        return crownSlot(core(in, t), k, t, openness(in, t));
    }

    // ------------------------------------------------------------------ the plates

    /** Where the intro's debris for plate {@code k} lies: low under the rim. */
    public static Vec3 debris(int k) {
        return HeliarchArena.at(30.0 + 60.0 * k, 25.0).add(0, -2.5, 0);
    }

    /** Plate {@code k}'s middle at {@code t}; null when it is no plate any more (a monolith stands there). */
    public static Vec3 plate(Input in, double facing, int k, double t) {
        double s = t - in.stateStart();
        switch (in.state()) {
            case INTRO -> {
                Vec3 slot = haloSlot(in, facing, k, t);
                Vec3 d = debris(k);
                if (s < INTRO_DEBRIS) {
                    // the debris stirs and lifts out of the Breach to the rim's level
                    return d.add(0, 3.5 * smooth(s / INTRO_DEBRIS) + 0.2 * Math.sin(s * 0.9 + k), 0);
                }
                Vec3 lifted = d.add(0, 3.5, 0);
                double u = smooth((s - INTRO_DEBRIS) / (INTRO_ASSEMBLED - INTRO_DEBRIS));
                return lerp(lifted, slot, u).add(0, 6.0 * Math.sin(Math.PI * u), 0);
            }
            case REGENT -> {
                if (in.action() == Action.HALO_SHED) {
                    double shedT = t - in.actionStart();
                    Vec3 slot = ringSlot(core(in, t), facing, k, t);
                    return HaloShed.position(in.actionAngle(), k, shedT, slot);
                }
                return haloSlot(in, facing, k, t);
            }
            case HOLLOWING -> {
                Vec3 slot = ringSlot(core(in, t), facing, k, t);
                if (s < TEAR) {
                    double shake = 0.08 + 0.25 * s / TEAR;
                    return slot.add(shake * Math.sin(s * 2.3 + k), shake * Math.sin(s * 3.1 + 2 * k), shake * Math.cos(s * 2.7 + k));
                }
                Vec3 dest = monolithMiddle(k);
                if (s < HeliarchMoves.HOLLOWING_SLAM) {
                    Vec3 from = ringSlot(core(in, in.stateStart() + TEAR), facing, k, in.stateStart() + TEAR);
                    double u = (s - TEAR) / (HeliarchMoves.HOLLOWING_SLAM - TEAR);
                    double e = u * u; // they drop faster and faster: a slam
                    return lerp(from, dest, e).add(0, 5.0 * Math.sin(Math.PI * Math.min(1.0, u * 1.4)) * (1 - u), 0);
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** Where plate {@code k} stands as a monolith: the middle of its blocks. */
    public static Vec3 monolithMiddle(int k) {
        return HeliarchArena.monoliths().get(k).middle().add(0, HeliarchArena.MONOLITH_HEIGHT / 2.0, 0);
    }

    // ------------------------------------------------------------------ the hands

    /** A hand at rest beside the core. */
    public static Vec3 handRest(Vec3 core, double facing, boolean right, double t) {
        double side = right ? HAND_SIDE : -HAND_SIDE;
        double bob = 0.18 * Math.sin(t * 0.07 + (right ? 0.0 : 1.7));
        return core.add(right(facing).scale(side)).add(forward(facing).scale(HAND_FWD)).add(0, HAND_DOWN + bob, 0);
    }

    /** A hand lying slack on the floor beside the core (a Break, a death). */
    public static Vec3 handSlack(double facing, boolean right) {
        return HeliarchArena.CENTRE.add(right(facing).scale(right ? 7.5 : -7.5)).add(forward(facing).scale(1.5)).add(0, HAND_FLOOR + 0.1, 0);
    }

    /** Hand {@code right}'s middle at {@code t}, or null while there is no hand (before it forms, after the Hollowing). */
    public static Vec3 hand(Input in, double facing, boolean right, double t) {
        double s = t - in.stateStart();
        Vec3 core = core(in, t);
        return switch (in.state()) {
            case INTRO -> s < INTRO_HANDS ? null : handRest(core, facing, right, t);
            case REGENT -> {
                if (in.broken(t)) {
                    Vec3 from = actionHand(in, in.brokenAction(), in.brokenActionStart(), facing, right, in.breakStart(), core);
                    double bs = t - in.breakStart();
                    Vec3 slack = handSlack(facing, right);
                    if (bs < HeliarchMoves.BREAK_TICKS) {
                        yield lerp(from, slack, smooth(bs / 10.0));
                    }
                    yield lerp(slack, handRest(core, facing, right, t), smooth((bs - HeliarchMoves.BREAK_TICKS) / 20.0));
                }
                yield actionHand(in, in.action(), in.actionStart(), facing, right, t, core);
            }
            case HOLLOWING -> s < HeliarchMoves.HOLLOWING_SLAM ? handRest(core, facing, right, t).add(0, 0.05 * s, 0) : null;
            default -> null;
        };
    }

    private static Vec3 actionHand(Input in, Action action, long start, double facing, boolean right, double t, Vec3 core) {
        Vec3 rest = handRest(core, facing, right, t);
        double a = t - start;
        switch (action) {
            case SUNDERFALL -> {
                if (right != in.sunderRight()) {
                    return rest;
                }
                return sunderHand(rest, in.actionPos(), a, in.sunderParried());
            }
            case CORONA_SWEEP -> {
                if (!right) {
                    Vec3 up = core.add(0, 6.0, 0).add(right(facing).scale(-8.0));
                    return blend(rest, up, a, 12, 50, 10);
                }
                double startAngle = in.actionAngle();
                Vec3 first = HeliarchArena.at(startAngle, SWEEP_HAND_R).add(0, HAND_FLOOR, 0);
                if (a < HeliarchMoves.SWEEP_TELL) {
                    double u = smooth(a / 20.0);
                    return lerp(rest, first.add(0, 1.5 * (1 - smooth((a - 20) / 10.0)), 0), u);
                }
                double w = a - HeliarchMoves.SWEEP_TELL;
                if (w <= HeliarchMoves.SWEEP_TICKS) {
                    return HeliarchArena.at(CoronaSweep.wallAngle(startAngle, w), SWEEP_HAND_R).add(0, HAND_FLOOR, 0);
                }
                Vec3 last = HeliarchArena.at(CoronaSweep.wallAngle(startAngle, HeliarchMoves.SWEEP_TICKS), SWEEP_HAND_R).add(0, HAND_FLOOR, 0);
                return lerp(last, rest, smooth((w - HeliarchMoves.SWEEP_TICKS) / 12.0));
            }
            case SOLAR_LANCE -> {
                Vec3 frame = core.add(forward(facing).scale(4.2)).add(right(facing).scale(right ? 3.4 : -3.4)).add(0, 0.2, 0);
                int end = HeliarchMoves.LANCE_TRACK + HeliarchMoves.LANCE_LOCK + HeliarchMoves.LANCE_BURN;
                return blend(rest, frame, a, 8, end, 10);
            }
            case HALO_SHED -> {
                Vec3 up = core.add(0, 5.0, 0).add(right(facing).scale(right ? 9.5 : -9.5)).add(forward(facing).scale(0.6));
                return blend(rest, up, a, 12, HaloShed.RETURN_END, 10);
            }
            default -> {
                return rest;
            }
        }
    }

    /**
     * A Sunderfall's hand {@code a} ticks in: up from rest over its ring (16 ticks), hovering with the glint, down in
     * the last four ticks (faster and faster), resting where it landed, then home.
     */
    public static Vec3 sunderHand(Vec3 rest, Vec3 ring, double a, boolean parried) {
        Vec3 over = ring.add(0, HAND_RISE, 0);
        Vec3 floor = ring.add(0, HAND_FLOOR, 0);
        if (a < 16) {
            double u = smooth(a / 16.0);
            return lerp(rest, over, u).add(0, 2.0 * Math.sin(Math.PI * u), 0);
        }
        if (a < HeliarchMoves.SUNDER_GLINT) {
            return over.add(0, 0.8 * smooth((a - 16) / 4.0), 0);
        }
        if (a < HeliarchMoves.SUNDER_TELL) {
            double u = (a - HeliarchMoves.SUNDER_GLINT) / (double) (HeliarchMoves.SUNDER_TELL - HeliarchMoves.SUNDER_GLINT);
            return lerp(over.add(0, 0.8, 0), floor, u * u);
        }
        int rest2 = parried ? HeliarchMoves.SUNDER_PARRIED_REST : HeliarchMoves.SUNDER_REST;
        double lie = a - HeliarchMoves.SUNDER_TELL;
        if (lie < rest2) {
            return floor.add(0, parried ? 0.25 * Math.abs(Math.sin(lie * 0.6)) * Math.exp(-lie / 12.0) : 0.0, 0);
        }
        return lerp(floor, rest, smooth((lie - rest2) / HeliarchMoves.SUNDER_RETURN));
    }

    /** The total length of a Sunderfall, parried or not. */
    public static int sunderLength(boolean parried) {
        return HeliarchMoves.SUNDER_TELL + (parried ? HeliarchMoves.SUNDER_PARRIED_REST : HeliarchMoves.SUNDER_REST) + HeliarchMoves.SUNDER_RETURN;
    }

    /** From {@code a} to {@code b} over {@code in} ticks, held until {@code until}, back over {@code out}. */
    private static Vec3 blend(Vec3 a, Vec3 b, double s, int in, int until, int out) {
        if (s < until) {
            return lerp(a, b, smooth(s / in));
        }
        return lerp(b, a, smooth((s - until) / out));
    }

    // ------------------------------------------------------------------ helpers

    public static double smooth(double u) {
        u = clamp(u);
        return u * u * (3 - 2 * u);
    }

    public static double clamp(double u) {
        return Math.max(0.0, Math.min(1.0, u));
    }

    public static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double u) {
        return a.add(b.subtract(a).scale(u));
    }
}
