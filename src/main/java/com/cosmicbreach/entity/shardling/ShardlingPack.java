package com.cosmicbreach.entity.shardling;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pack brain (GDD section 7.1), shared by every Shardling of one pack. The pack's leader (the
 * longest-serving member) re-plans every {@value #PLAN_INTERVAL} ticks: one <b>Taunter</b> holds
 * {@value #TAUNT_DISTANCE} blocks in front of the target (the GDD's 4 to 6) and now and then feints, a
 * quick dart in and back out, then runs back into its band; the rest are <b>Flankers</b> that circle
 * round to slots 120 to 180 degrees behind the target, 4 to 5 blocks out. Nobody cuts past the target
 * to reach its spot: a member whose straight walk would pass close steps round the circle instead.
 * Each plan may start one attack, and only with one of the target's {@link AttackTokens}, so
 * however many packs hunt a player, at most two Shardlings attack it at once. A Flanker that lands a
 * hit while the Taunter holds the front becomes the Taunter-elect: it runs round to the front and takes
 * the role when it gets there, and only then does the old Taunter fall back to a flank. (Handing the
 * role over on the spot left nobody in front: a big pack lands a hit from behind every second or so, so
 * the role kept jumping to whoever had just hit, behind the player.) If the leader dies, the next member
 * leads.
 *
 * <p>Pure: the Shardlings feed it their positions and state as plain numbers and follow its orders.
 * Angles are measured round the target from its facing: 0 is straight in front, 180 straight behind.
 * Yaw is Minecraft's: 0 faces +Z, and the facing is {@code (-sin yaw, cos yaw)}.
 */
public final class ShardlingPack {
    public enum Role { TAUNTER, FLANKER }

    public enum Attack { NONE, LUNGE, SPIT }

    /** The hunted player: where it stands and where it faces. */
    public record Target(int id, double x, double z, float yaw) {
    }

    /**
     * One member as the plan sees it. {@code free}: not attacking or staggered (it can move and feint);
     * {@code ready}: free and rested, so it may start an attack; {@code sees}: it has line of sight
     * to the target.
     */
    public record Member(int id, double x, double z, boolean free, boolean ready, boolean sees) {
    }

    /** How a member moves to its order: a walk close by, a run when far or backing off, a dart on a feint. */
    public enum Pace { WALK, RUN, DART }

    /** Where a member should be now and how fast to go. {@code feint}: the Taunter's dart in. */
    public record Order(Role role, double x, double z, boolean feint, Pace pace) {
    }

    public static final int MAX_SIZE = 5;
    public static final int PLAN_INTERVAL = 10;
    /** A plan this old is overdue: any member may make the next one (the leader may not be ticking). */
    public static final int STALE_AFTER = 30;

    public static final double TAUNT_DISTANCE = 5.0;
    public static final double TAUNT_MIN = 4.0;
    public static final double TAUNT_MAX = 6.0;
    /** A feint darts in this close, then the next plan sends the Taunter back out. */
    public static final double FEINT_DISTANCE = 2.8;
    /**
     * Every this many plans (2.5 s) the Taunter feints, when it is free and holding its spot. Every third
     * plan kept it darting in and back two thirds of the time, so it read as hanging inside its band.
     */
    public static final int FEINT_EVERY = 5;
    /** How near its spot the Taunter must be to feint: it feints from where it holds, not on the way. */
    public static final double FEINT_READY = 1.0;

    public static final double FLANK_ANGLE_MIN = 120.0;
    public static final double FLANK_ANGLE_MAX = 180.0;
    public static final double FLANK_RADIUS_MIN = 4.0;
    public static final double FLANK_RADIUS_MAX = 5.0;
    /** Flank slots sit this far inside the 120 degree edge of the arc (so from 130 round to -130). */
    public static final double SLOT_MARGIN = 10.0;
    /** Slot radii alternate between these, both inside 4 to 5. */
    public static final double FLANK_RADIUS_NEAR = 4.3;
    public static final double FLANK_RADIUS_FAR = 4.7;
    /**
     * A walk to a spot may not pass closer to the target than this. A member whose straight walk would,
     * and whose spot is more than {@value #CIRCLE_STEP} degrees round the circle, goes to the point that
     * many degrees round toward it instead, at the spot's distance: it circles.
     */
    public static final double CLEARANCE = 3.5;
    public static final double CIRCLE_STEP = 50.0;
    /** A member further than this from where it is sent runs there. */
    public static final double RUN_BEYOND = 3.0;

    /** A Splinter Lunge carries 5 blocks: it is only started from this close (and not from on top). */
    public static final double LUNGE_MIN = 1.5;
    public static final double LUNGE_MAX = 5.5;
    /** Shard Spit is for a target beyond 6 blocks, up to this far. */
    public static final double SPIT_MIN = 6.0;
    public static final double SPIT_MAX = 16.0;
    /** A Flanker this far round (or more) counts as behind the target for picking who attacks. */
    public static final double BEHIND = 110.0;
    /** The Taunter holds the front while within this of its spot; only then can a Flanker's hit start a handover. */
    public static final double FRONT_HELD = 1.5;
    /** The Taunter-elect heads for the front this many degrees off straight ahead, beside the Taunter holding it... */
    public static final double ELECT_ANGLE = 25.0;
    /** ...and takes the role once it is this close to the Taunter's spot. */
    public static final double HANDOVER = 2.5;
    /** An elect that hasn't got there in this many plans (5 s; it takes 2 to 3) is a plain Flanker again. */
    public static final int ELECT_PLANS = 10;

    private final List<Integer> members = new ArrayList<>();
    private final Map<Integer, Role> roles = new HashMap<>();
    private final Map<Integer, Order> orders = new HashMap<>();
    private final Map<Integer, Attack> attacks = new HashMap<>();
    private int taunter = -1;
    /** The Flanker that earned the front with a hit and is on its way there, or -1. */
    private int elect = -1;
    /** The plan count when the elect earned the front. */
    private int electedAt;
    /** The Taunter was within {@link #FRONT_HELD} of its spot at the last plan. */
    private boolean frontHeld;
    private int target = -1;
    private long lastPlan = Long.MIN_VALUE / 4;
    private int plans;

    // ------------------------------------------------------------------ membership

    /** Adds a member (in join order: the first is the leader). False if it is full or already in. */
    public boolean join(int id) {
        if (members.contains(id) || members.size() >= MAX_SIZE) {
            return false;
        }
        members.add(id);
        return true;
    }

    /** A member died or left: it gives back its token, and if it led, the next member leads. */
    public void leave(int id, AttackTokens tokens) {
        members.remove((Integer) id);
        roles.remove(id);
        orders.remove(id);
        attacks.remove(id);
        tokens.release(id);
        if (taunter == id) {
            taunter = -1;
            frontHeld = false;
        }
        if (elect == id) {
            elect = -1;
        }
    }

    public boolean contains(int id) {
        return members.contains(id);
    }

    public List<Integer> members() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** The member that plans, or -1 for an empty pack. */
    public int leader() {
        return members.isEmpty() ? -1 : members.get(0);
    }

    // ------------------------------------------------------------------ planning

    /** True when {@code id} should plan now: the leader every 10 ticks, anyone once a plan is overdue. */
    public boolean planDue(int id, long now) {
        if (!members.contains(id)) {
            return false;
        }
        long since = now - lastPlan;
        return since >= STALE_AFTER || (since >= PLAN_INTERVAL && id == leader());
    }

    /**
     * Makes a plan: roles, where each member goes, and at most one new attack. {@code views} are the
     * members that could be found this tick; the others keep their roles but get no orders. A null
     * target stands the pack down and gives back its tokens, as does a change of target.
     */
    public void plan(long now, @Nullable Target hunted, List<Member> views, AttackTokens tokens) {
        lastPlan = now;
        plans++;
        // An attack not taken since the last plan was never started (its member staggered first).
        for (Integer id : List.copyOf(attacks.keySet())) {
            tokens.release(id);
        }
        attacks.clear();
        orders.clear();
        if (hunted == null) {
            releaseAll(tokens);
            target = -1;
            return;
        }
        if (hunted.id() != target) {
            releaseAll(tokens);
            target = hunted.id();
        }
        List<Member> present = new ArrayList<>();
        for (Member view : views) {
            if (members.contains(view.id())) {
                present.add(view);
            }
        }
        if (present.isEmpty()) {
            return;
        }
        assignRoles(hunted, present);
        boolean feint = placeTaunter(hunted, present);
        placeElect(hunted, present);
        placeFlankers(hunted, present);
        grantAttack(hunted, present, tokens, feint);
    }

    private void releaseAll(AttackTokens tokens) {
        for (int id : members) {
            tokens.release(id);
        }
    }

    private void assignRoles(Target hunted, List<Member> present) {
        Set<Integer> ids = new HashSet<>();
        present.forEach(m -> ids.add(m.id()));
        double[] front = pointAt(hunted, TAUNT_DISTANCE, 0.0);
        if (!ids.contains(elect)) {
            elect = -1;
        }
        if (!ids.contains(taunter)) {
            taunter = present.stream()
                    .min(Comparator.<Member>comparingDouble(m -> dist(m.x(), m.z(), front[0], front[1])).thenComparingInt(Member::id))
                    .map(Member::id).orElse(-1);
            frontHeld = false;
        }
        if (elect != -1 && elect != taunter) {
            Member e = present.stream().filter(m -> m.id() == elect).findFirst().orElseThrow();
            if (dist(e.x(), e.z(), front[0], front[1]) <= HANDOVER) {
                taunter = elect; // it has arrived: the old Taunter becomes a Flanker and falls back
                frontHeld = false;
                elect = -1;
            } else if (plans - electedAt > ELECT_PLANS) {
                elect = -1; // something is in its way: it goes back to flanking
            }
        }
        if (elect == taunter) {
            elect = -1;
        }
        roles.clear();
        for (Member m : present) {
            roles.put(m.id(), m.id() == taunter ? Role.TAUNTER : Role.FLANKER);
        }
    }

    /**
     * The Taunter's spot in front, or a dart in on a feint plan: every {@value #FEINT_EVERY}th plan while it
     * holds its spot and isn't attacking (resting after an attack is when it taunts most). After a dart,
     * a lunge or a role swap it is inside its band or round the side: it runs back out to the spot,
     * circling round the target rather than past it. Returns whether it feints.
     */
    private boolean placeTaunter(Target hunted, List<Member> present) {
        Member t = present.stream().filter(m -> m.id() == taunter).findFirst().orElse(null);
        if (t == null) {
            return false;
        }
        double[] spot = pointAt(hunted, TAUNT_DISTANCE, 0.0);
        double fromSpot = dist(t.x(), t.z(), spot[0], spot[1]);
        frontHeld = fromSpot <= FRONT_HELD;
        boolean inPlace = fromSpot <= FEINT_READY;
        if (t.free() && inPlace && plans % FEINT_EVERY == 0) {
            double[] dart = pointAt(hunted, FEINT_DISTANCE, 0.0);
            orders.put(t.id(), new Order(Role.TAUNTER, dart[0], dart[1], true, Pace.DART));
            return true;
        }
        double[] at = route(hunted, t, 0.0, TAUNT_DISTANCE);
        boolean inside = dist(t.x(), t.z(), hunted.x(), hunted.z()) < TAUNT_MIN;
        orders.put(t.id(), new Order(Role.TAUNTER, at[0], at[1], false, inside ? Pace.RUN : paceTo(t, at)));
        return false;
    }

    /**
     * Flank slots spread evenly from 130 degrees round the back to -130, 4.3 and 4.7 blocks out in turn.
     * Flankers take them in the order they already stand round the target, so their paths don't cross;
     * one far round the circle from its slot circles to it ({@link #route}).
     */
    private void placeFlankers(Target hunted, List<Member> present) {
        List<Member> flankers = new ArrayList<>();
        for (Member m : present) {
            if (m.id() != taunter && m.id() != elect) {
                flankers.add(m);
            }
        }
        flankers.sort(Comparator.<Member>comparingDouble(m -> around(hunted, m.x(), m.z())).thenComparingInt(Member::id));
        int k = flankers.size();
        double from = FLANK_ANGLE_MIN + SLOT_MARGIN;
        double to = 360.0 - FLANK_ANGLE_MIN - SLOT_MARGIN;
        for (int j = 0; j < k; j++) {
            Member m = flankers.get(j);
            double slot = from + (to - from) * (j + 0.5) / k;
            double radius = j % 2 == 0 ? FLANK_RADIUS_NEAR : FLANK_RADIUS_FAR;
            double[] at = route(hunted, m, slot, radius);
            orders.put(m.id(), new Order(Role.FLANKER, at[0], at[1], false, paceTo(m, at)));
        }
    }

    /**
     * The Taunter-elect runs round to the front, {@value #ELECT_ANGLE} degrees to the side it comes from
     * (so it arrives beside the Taunter holding the spot, not into it), at the Taunter's distance. It keeps
     * to that circle, {@value #CIRCLE_STEP} degrees at a time, until it is that close to its spot: from
     * the back, the straight walk {@link #route} allows would take it past the target's side at 3.3 blocks.
     */
    private void placeElect(Target hunted, List<Member> present) {
        Member e = present.stream().filter(m -> m.id() == elect).findFirst().orElse(null);
        if (e == null) {
            return;
        }
        double side = angleOf(hunted, e.x(), e.z()) >= 0.0 ? ELECT_ANGLE : -ELECT_ANGLE;
        double current = around(hunted, e.x(), e.z());
        double delta = wrap180(side - current);
        double[] at = pointAt(hunted, TAUNT_DISTANCE, Math.abs(delta) > CIRCLE_STEP ? current + Math.signum(delta) * CIRCLE_STEP : side);
        orders.put(e.id(), new Order(Role.FLANKER, at[0], at[1], false, Pace.RUN));
    }

    /**
     * Where to send a member bound for the spot {@code radius} blocks out at {@code angle} round the
     * target: the spot itself, unless it is more than {@value #CIRCLE_STEP} degrees round the circle
     * and the straight walk there would pass within {@value #CLEARANCE} blocks of the target; then the
     * point {@value #CIRCLE_STEP} degrees round toward it, at the spot's distance. A member right behind
     * the target goes round by its left (positive angles).
     */
    static double[] route(Target t, Member m, double angle, double radius) {
        double[] spot = pointAt(t, radius, angle);
        double current = around(t, m.x(), m.z());
        double delta = wrap180(angle - current);
        if (Math.abs(delta) > CIRCLE_STEP && closestApproach(m.x(), m.z(), spot[0], spot[1], t) < CLEARANCE) {
            return pointAt(t, radius, current + Math.signum(delta) * CIRCLE_STEP);
        }
        return spot;
    }

    private static Pace paceTo(Member m, double[] at) {
        return dist(m.x(), m.z(), at[0], at[1]) > RUN_BEYOND ? Pace.RUN : Pace.WALK;
    }

    /** How close the straight walk from (x1, z1) to (x2, z2) passes to the target. */
    static double closestApproach(double x1, double z1, double x2, double z2, Target t) {
        double vx = x2 - x1;
        double vz = z2 - z1;
        double len2 = vx * vx + vz * vz;
        double s = len2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((t.x() - x1) * vx + (t.z() - z1) * vz) / len2));
        return Math.hypot(x1 + vx * s - t.x(), z1 + vz * s - t.z());
    }

    /**
     * At most one new attack a plan: a Splinter Lunge from within {@value #LUNGE_MAX} blocks, a Shard
     * Spit from beyond {@value #SPIT_MIN}. Flankers behind the target go first, then the Taunter (not
     * while it feints), then anyone else in lunge range, then spitters; nearest first.
     */
    private void grantAttack(Target hunted, List<Member> present, AttackTokens tokens, boolean feint) {
        Member best = null;
        Attack bestAttack = Attack.NONE;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Member m : present) {
            if (!m.ready() || !m.sees() || tokens.holds(m.id()) || m.id() == elect) {
                continue; // the elect is busy getting to the front
            }
            double d = dist(m.x(), m.z(), hunted.x(), hunted.z());
            Attack attack = attackFor(d);
            if (attack == Attack.NONE) {
                continue;
            }
            boolean isTaunter = m.id() == taunter;
            if (isTaunter && feint) {
                continue;
            }
            int rank;
            if (attack == Attack.SPIT) {
                rank = 3;
            } else if (!isTaunter && Math.abs(angleOf(hunted, m.x(), m.z())) >= BEHIND) {
                rank = 0;
            } else {
                rank = isTaunter ? 1 : 2;
            }
            if (rank < bestRank || (rank == bestRank && d < bestDistance)) {
                best = m;
                bestAttack = attack;
                bestRank = rank;
                bestDistance = d;
            }
        }
        if (best != null && tokens.tryAcquire(hunted.id(), best.id())) {
            attacks.put(best.id(), bestAttack);
        }
    }

    /** Which attack fits a target {@code distance} blocks away. */
    public static Attack attackFor(double distance) {
        if (distance >= LUNGE_MIN && distance <= LUNGE_MAX) {
            return Attack.LUNGE;
        }
        if (distance > SPIT_MIN && distance <= SPIT_MAX) {
            return Attack.SPIT;
        }
        return Attack.NONE;
    }

    // ------------------------------------------------------------------ what the members read

    /** This member's order from the last plan, or null (no target, or it wasn't found). */
    public @Nullable Order order(int id) {
        return orders.get(id);
    }

    /** This member's role; Flanker until a plan says otherwise. */
    public Role role(int id) {
        return roles.getOrDefault(id, id == taunter ? Role.TAUNTER : Role.FLANKER);
    }

    /** The Taunter's id, or -1. */
    public int taunter() {
        return taunter;
    }

    /** The attack the last plan gave this member (it already holds the token), once: taking it clears it. */
    public Attack takeAttack(int id) {
        Attack attack = attacks.remove(id);
        return attack == null ? Attack.NONE : attack;
    }

    /** The target's entity id, or -1. */
    public int target() {
        return target;
    }

    public int plans() {
        return plans;
    }

    public long lastPlan() {
        return lastPlan;
    }

    /**
     * A member's attack connected. A Flanker's hit, while the Taunter holds the front and no other
     * handover is under way, makes it the Taunter-elect: it takes the role when it reaches the front,
     * or gives up after {@value #ELECT_PLANS} plans.
     */
    public void onHitLanded(int id) {
        if (!members.contains(id) || id == taunter || elect != -1 || !frontHeld) {
            return;
        }
        elect = id;
        electedAt = plans;
    }

    /** The Flanker on its way to take over the front, or -1. */
    public int elect() {
        return elect;
    }

    /** Whether the Taunter held its spot at the last plan. */
    public boolean frontHeld() {
        return frontHeld;
    }

    // ------------------------------------------------------------------ geometry

    /** Degrees round the target from its facing to the point, in (-180, 180]: 0 in front, 180 behind. */
    public static double angleOf(Target t, double x, double z) {
        double yaw = Math.toRadians(t.yaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double dx = x - t.x();
        double dz = z - t.z();
        double angle = Math.toDegrees(Math.atan2(fx * dz - fz * dx, dx * fx + dz * fz));
        return angle <= -180.0 ? angle + 360.0 : angle;
    }

    /** The same angle as {@link #angleOf}, but in [0, 360), so the back of the target is continuous. */
    public static double around(Target t, double x, double z) {
        double angle = angleOf(t, x, z);
        return angle < 0.0 ? angle + 360.0 : angle;
    }

    /** The point {@code radius} blocks from the target at {@code degrees} round from its facing. */
    public static double[] pointAt(Target t, double radius, double degrees) {
        double yaw = Math.toRadians(t.yaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double a = Math.toRadians(degrees);
        double c = Math.cos(a);
        double s = Math.sin(a);
        // f turned by a: f cos a + g sin a, where g = (-fz, fx) is f turned a quarter toward positive angles
        return new double[] {t.x() + radius * (fx * c - fz * s), t.z() + radius * (fz * c + fx * s)};
    }

    public static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d <= -180.0) {
            d += 360.0;
        } else if (d > 180.0) {
            d -= 360.0;
        }
        return d;
    }

    private static double dist(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }
}
