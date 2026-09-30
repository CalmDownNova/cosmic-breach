package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.entity.shardling.ShardlingPack.Attack;
import com.cosmicbreach.entity.shardling.ShardlingPack.Member;
import com.cosmicbreach.entity.shardling.ShardlingPack.Order;
import com.cosmicbreach.entity.shardling.ShardlingPack.Role;
import com.cosmicbreach.entity.shardling.ShardlingPack.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardlingPackTest {
    private static final int PLAYER = 1000;

    // ------------------------------------------------------------------ helpers

    /** A pack with the given member ids and a movable copy of where each one stands. */
    private static final class World {
        final ShardlingPack pack = new ShardlingPack();
        final AttackTokens tokens;
        final Map<Integer, double[]> at = new HashMap<>();
        final Map<Integer, Boolean> ready = new HashMap<>();
        final Map<Integer, Boolean> free = new HashMap<>();
        long now;

        World(AttackTokens tokens) {
            this.tokens = tokens;
        }

        World add(int id, double x, double z) {
            assertTrue(pack.join(id));
            at.put(id, new double[] {x, z});
            ready.put(id, true);
            free.put(id, true);
            return this;
        }

        List<Member> views() {
            List<Member> views = new ArrayList<>();
            for (int id : pack.members()) {
                double[] p = at.get(id);
                views.add(new Member(id, p[0], p[1], free.get(id), ready.get(id), true));
            }
            return views;
        }

        void plan(Target target) {
            pack.plan(now, target, views(), tokens);
            now += ShardlingPack.PLAN_INTERVAL;
        }

        /** Every member walks to its order before the next plan. */
        void follow() {
            for (int id : pack.members()) {
                Order order = pack.order(id);
                if (order != null) {
                    at.put(id, new double[] {order.x(), order.z()});
                }
            }
        }
    }

    private static World world() {
        return new World(new AttackTokens());
    }

    private static double distance(Target t, double x, double z) {
        return Math.hypot(x - t.x(), z - t.z());
    }

    // ------------------------------------------------------------------ geometry

    @Test
    void angleAndPointAgree() {
        for (float yaw : new float[] {0f, 90f, -90f, 180f, 33f, -147f}) {
            Target t = new Target(PLAYER, 3.5, -7.25, yaw);
            for (double angle : new double[] {0, 30, 90, 135, 180, -45, -120, -179}) {
                double[] p = ShardlingPack.pointAt(t, 4.5, angle);
                assertEquals(4.5, distance(t, p[0], p[1]), 1e-9);
                assertEquals(0.0, ShardlingPack.wrap180(ShardlingPack.angleOf(t, p[0], p[1]) - angle), 1e-6,
                        "yaw " + yaw + " angle " + angle);
            }
            // straight ahead is along Minecraft's facing: yaw 0 faces +Z, yaw 90 faces -X
            double[] ahead = ShardlingPack.pointAt(t, 1.0, 0.0);
            double yawRad = Math.toRadians(yaw);
            assertEquals(t.x() - Math.sin(yawRad), ahead[0], 1e-9);
            assertEquals(t.z() + Math.cos(yawRad), ahead[1], 1e-9);
        }
    }

    // ------------------------------------------------------------------ leader and plans

    @Test
    void theLeaderPlansEveryTenTicksAndAnyoneWhenOverdue() {
        World w = world().add(1, 0, 10).add(2, 1, 10).add(3, 2, 10);
        assertEquals(1, w.pack.leader());
        assertTrue(w.pack.planDue(1, 0));
        w.pack.plan(0, new Target(PLAYER, 0, 0, 0f), w.views(), w.tokens);
        assertFalse(w.pack.planDue(1, 5), "the leader waits 10 ticks between plans");
        assertFalse(w.pack.planDue(2, 10), "only the leader plans on time");
        assertTrue(w.pack.planDue(1, 10));
        assertFalse(w.pack.planDue(2, 29));
        assertTrue(w.pack.planDue(2, 30), "an overdue plan can come from anyone");
        assertFalse(w.pack.planDue(9, 30), "not from outside the pack");
    }

    @Test
    void whenTheLeaderDiesTheNextMemberLeads() {
        World w = world().add(1, 0, 10).add(2, 1, 10).add(3, 2, 10);
        w.pack.leave(1, w.tokens);
        assertEquals(2, w.pack.leader());
        assertTrue(w.pack.planDue(2, 0));
        w.pack.leave(2, w.tokens);
        w.pack.leave(3, w.tokens);
        assertEquals(-1, w.pack.leader());
        assertTrue(w.pack.isEmpty());
    }

    @Test
    void aPackHoldsFiveAtMost() {
        ShardlingPack pack = new ShardlingPack();
        for (int id = 1; id <= ShardlingPack.MAX_SIZE; id++) {
            assertTrue(pack.join(id));
        }
        assertFalse(pack.join(99));
        assertFalse(pack.join(1), "no one joins twice");
    }

    // ------------------------------------------------------------------ roles and positions

    @Test
    void oneTaunterAndTheRestFlankers() {
        for (int size = 1; size <= 5; size++) {
            World w = world();
            for (int id = 1; id <= size; id++) {
                w.add(id, id * 2.0 - 5.0, 12.0);
            }
            w.plan(new Target(PLAYER, 0, 0, 0f));
            long taunters = w.pack.members().stream().filter(id -> w.pack.role(id) == Role.TAUNTER).count();
            assertEquals(1, taunters, "pack of " + size);
            for (int id : w.pack.members()) {
                assertNotNull(w.pack.order(id), "everyone gets an order");
            }
        }
    }

    @Test
    void theTaunterHoldsFourToSixBlocksInFront() {
        for (float yaw : new float[] {0f, 90f, -135f, 33f}) {
            Target t = new Target(PLAYER, 10, -4, yaw);
            double[] start = ShardlingPack.pointAt(t, 12.0, 10.0);
            World w = world().add(1, start[0], start[1]).add(2, start[0] + 1, start[1]);
            w.plan(t);
            int taunter = w.pack.taunter();
            Order order = w.pack.order(taunter);
            assertEquals(Role.TAUNTER, order.role());
            double d = distance(t, order.x(), order.z());
            assertTrue(d >= ShardlingPack.TAUNT_MIN && d <= ShardlingPack.TAUNT_MAX, "distance " + d);
            assertEquals(0.0, ShardlingPack.angleOf(t, order.x(), order.z()), 1e-6, "straight in front");
        }
    }

    @Test
    void flankersSettle120To180DegreesBehindAtFourToFiveBlocks() {
        for (int size = 2; size <= 5; size++) {
            for (float yaw : new float[] {0f, 120f, -70f}) {
                Target t = new Target(PLAYER, 0, 0, yaw);
                World w = world();
                // They arrive together from the front and the sides, some close, some far.
                for (int id = 1; id <= size; id++) {
                    double[] p = ShardlingPack.pointAt(t, 6.0 + id * 2.0, -60.0 + id * 25.0);
                    w.add(id, p[0], p[1]);
                }
                for (int i = 0; i < 12; i++) {
                    w.plan(t);
                    w.follow();
                }
                int flankers = 0;
                for (int id : w.pack.members()) {
                    if (w.pack.role(id) != Role.FLANKER) {
                        continue;
                    }
                    flankers++;
                    double[] p = w.at.get(id);
                    double angle = Math.abs(ShardlingPack.angleOf(t, p[0], p[1]));
                    double d = distance(t, p[0], p[1]);
                    String where = "pack " + size + " yaw " + yaw + " flanker " + id + ": " + angle + " degrees, " + d + " blocks";
                    assertTrue(angle >= ShardlingPack.FLANK_ANGLE_MIN && angle <= ShardlingPack.FLANK_ANGLE_MAX, where);
                    assertTrue(d >= ShardlingPack.FLANK_RADIUS_MIN && d <= ShardlingPack.FLANK_RADIUS_MAX, where);
                }
                assertEquals(size - 1, flankers);
            }
        }
    }

    @Test
    void twoFlankersTakeBothSides() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        World w = world().add(1, 0, 5).add(2, 3, 6).add(3, -3, 6);
        for (int i = 0; i < 10; i++) {
            w.plan(t);
            w.follow();
        }
        List<Double> sides = new ArrayList<>();
        for (int id : w.pack.members()) {
            if (w.pack.role(id) == Role.FLANKER) {
                double[] p = w.at.get(id);
                sides.add(Math.signum(ShardlingPack.angleOf(t, p[0], p[1])));
            }
        }
        assertEquals(2, sides.size());
        assertEquals(0.0, sides.get(0) + sides.get(1), "one on each side");
    }

    @Test
    void aFlankerCirclesRoundRatherThanCuttingPastTheTarget() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        // The Taunter already holds the front; the Flanker starts in front too, a little to one side.
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        double[] flank = ShardlingPack.pointAt(t, 5.0, 25.0);
        World w = world().add(1, front[0], front[1]).add(2, flank[0], flank[1]);
        for (int i = 0; i < 8; i++) {
            w.plan(t);
            double[] from = w.at.get(2).clone();
            Order order = w.pack.order(2);
            assertEquals(Role.FLANKER, order.role());
            // The straight walk to each waypoint keeps well clear of the target.
            double closest = closestApproach(from, new double[] {order.x(), order.z()}, t);
            assertTrue(closest >= 3.5, "the walk passes " + closest + " blocks from the target");
            w.follow();
        }
        double[] end = w.at.get(2);
        assertTrue(Math.abs(ShardlingPack.angleOf(t, end[0], end[1])) >= ShardlingPack.FLANK_ANGLE_MIN);
    }

    private static double closestApproach(double[] a, double[] b, Target t) {
        double vx = b[0] - a[0];
        double vz = b[1] - a[1];
        double len2 = vx * vx + vz * vz;
        double s = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((t.x() - a[0]) * vx + (t.z() - a[1]) * vz) / len2));
        return Math.hypot(a[0] + vx * s - t.x(), a[1] + vz * s - t.z());
    }

    @Test
    void theTaunterFeintsEveryFifthPlanWhenHoldingItsSpot() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, ShardlingPack.TAUNT_DISTANCE, 0.0);
        // Far out of attack range from the flank, so no attack decisions get in the way.
        World w = world().add(1, front[0], front[1]);
        w.ready.put(1, false);
        w.free.put(1, false);
        List<Boolean> feints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            w.plan(t);
            feints.add(w.pack.order(1).feint());
        }
        assertTrue(feints.stream().noneMatch(f -> f), "a Taunter in an attack doesn't feint");
        w.free.put(1, true); // resting after an attack: free, not ready; it feints then
        feints.clear();
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            w.plan(t);
            Order order = w.pack.order(1);
            feints.add(order.feint());
            distances.add(distance(t, order.x(), order.z()));
        }
        assertEquals(10 / ShardlingPack.FEINT_EVERY, feints.stream().filter(f -> f).count(), "feints " + feints);
        for (int i = 0; i < feints.size(); i++) {
            double expected = feints.get(i) ? ShardlingPack.FEINT_DISTANCE : ShardlingPack.TAUNT_DISTANCE;
            assertEquals(expected, distances.get(i), 1e-9);
        }
    }

    @Test
    void noFeintOnTheWayToItsSpot() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] near = ShardlingPack.pointAt(t, ShardlingPack.TAUNT_DISTANCE + 1.5, 10.0); // in the band, not on its spot
        World w = world().add(1, near[0], near[1]);
        w.tokens.tryAcquire(PLAYER + 1, 1);
        for (int i = 0; i < 12; i++) {
            w.plan(t);
            assertFalse(w.pack.order(1).feint(), "plan " + i + ": it feints only while holding its spot");
        }
    }

    @Test
    void theTaunterRunsBackIntoItsBandAfterADart() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] dart = ShardlingPack.pointAt(t, ShardlingPack.FEINT_DISTANCE, 0.0);
        World w = world().add(1, dart[0], dart[1]); // it just darted in to 2.8 blocks
        w.tokens.tryAcquire(PLAYER + 1, 1); // busy with another token: no attack gets in the way
        w.plan(t);
        Order order = w.pack.order(1);
        assertFalse(order.feint());
        assertEquals(ShardlingPack.TAUNT_DISTANCE, distance(t, order.x(), order.z()), 1e-9, "back to its spot");
        assertEquals(ShardlingPack.Pace.RUN, order.pace(), "at a run, not a stroll");
    }

    @Test
    void aFlankerThatHitsFromBehindCirclesToTheFrontAndTakesItThere() {
        for (float yaw : new float[] {0f, 90f, -120f}) {
            Target t = new Target(PLAYER, 3, -2, yaw);
            double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
            double[] hit = ShardlingPack.pointAt(t, 1.1, 170.0); // where a Flanker's lunge from behind ends
            World w = world().add(1, front[0], front[1]).add(2, hit[0], hit[1]);
            w.tokens.tryAcquire(PLAYER + 1, 1);
            w.tokens.tryAcquire(PLAYER + 1, 2);
            w.free.put(1, false); // no feints to keep track of
            w.plan(t);
            w.pack.onHitLanded(2);
            assertEquals(2, w.pack.elect(), "yaw " + yaw + ": the hitter is on its way to the front");
            List<Double> distances = new ArrayList<>();
            double[] at = w.at.get(2);
            int handedOver = -1;
            for (int i = 0; i < 10 && handedOver < 0; i++) {
                w.plan(t);
                if (w.pack.taunter() == 2) {
                    handedOver = i;
                    break;
                }
                assertEquals(1, w.pack.taunter(), "yaw " + yaw + ": the old Taunter holds the front until then");
                Order order = w.pack.order(2);
                double d = distance(t, order.x(), order.z());
                distances.add(d);
                if (i > 0) {
                    double pass = ShardlingPack.closestApproach(at[0], at[1], order.x(), order.z(), t);
                    assertTrue(pass >= ShardlingPack.CLEARANCE, "yaw " + yaw + ": the walk passes " + pass + " from the target");
                }
                at = new double[] {order.x(), order.z()};
                w.follow();
            }
            assertTrue(handedOver > 0, "yaw " + yaw + ": the front changed hands when it arrived");
            for (double d : distances) {
                assertTrue(d >= ShardlingPack.TAUNT_MIN && d <= ShardlingPack.TAUNT_MAX, "yaw " + yaw + ": a waypoint " + d + " out");
            }
            assertEquals(-1, w.pack.elect());
            assertEquals(Role.FLANKER, w.pack.role(1), "the old Taunter falls back");
            Order order = w.pack.order(2);
            assertEquals(Role.TAUNTER, order.role());
            assertEquals(0.0, ShardlingPack.angleOf(t, order.x(), order.z()), 1e-6, "yaw " + yaw + ": the new Taunter takes the spot");
            assertEquals(ShardlingPack.TAUNT_DISTANCE, distance(t, order.x(), order.z()), 1e-6);
        }
    }

    @Test
    void theElectKeepsWideOfTheTargetWhileItRunsRound() {
        for (float yaw : new float[] {0f, 90f, -120f}) {
            Target t = new Target(PLAYER, 3, -2, yaw);
            double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
            double[] hit = ShardlingPack.pointAt(t, 1.1, 170.0);
            World w = world().add(1, front[0], front[1]).add(2, hit[0], hit[1]);
            w.tokens.tryAcquire(PLAYER + 1, 1); // tokens elsewhere: nobody attacks
            w.tokens.tryAcquire(PLAYER + 1, 2);
            w.free.put(1, false); // no feints
            w.plan(t);
            w.pack.onHitLanded(2);
            boolean out = false;
            for (int i = 0; i < 12 && w.pack.taunter() != 2; i++) {
                w.plan(t);
                Order order = w.pack.order(2);
                double[] at = w.at.get(2);
                out |= distance(t, at[0], at[1]) >= ShardlingPack.TAUNT_MIN; // counted once it is out of the hit
                if (out) {
                    double pass = ShardlingPack.closestApproach(at[0], at[1], order.x(), order.z(), t);
                    assertTrue(pass >= 4.0, "yaw " + yaw + ", plan " + i + ": its run passes " + pass + " from the target");
                }
                // it runs about 2.6 blocks between plans, so it is never quite where its last order sent it
                double dx = order.x() - at[0];
                double dz = order.z() - at[1];
                double step = Math.min(1.0, 2.6 / Math.max(Math.hypot(dx, dz), 1e-9));
                w.at.put(2, new double[] {at[0] + dx * step, at[1] + dz * step});
            }
            assertEquals(2, w.pack.taunter(), "yaw " + yaw + ": it got to the front");
        }
    }

    @Test
    void theFrontIsNeverLeftEmptyWhileItChangesHands() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        World w = world().add(1, front[0], front[1]);
        for (int i = 0; i < 4; i++) {
            double[] back = ShardlingPack.pointAt(t, 4.5, 135.0 + 30.0 * i);
            w.add(2 + i, back[0], back[1]);
        }
        for (int id = 1; id <= 5; id++) {
            w.tokens.tryAcquire(PLAYER + id % 2 + 1, id); // tokens elsewhere: nobody attacks
            w.free.put(id, false);
        }
        w.plan(t);
        w.pack.onHitLanded(3);
        for (int i = 0; i < 12; i++) {
            w.plan(t);
            int taunter = w.pack.taunter();
            Order order = w.pack.order(taunter);
            double[] where = w.at.get(taunter);
            boolean holding = distance(t, where[0], where[1]) >= ShardlingPack.TAUNT_MIN
                    && Math.abs(ShardlingPack.angleOf(t, where[0], where[1])) <= 30.0;
            assertTrue(holding, "plan " + i + ": the Taunter #" + taunter + " is not in front");
            assertEquals(0.0, ShardlingPack.angleOf(t, order.x(), order.z()), 1e-6);
            w.follow();
        }
        assertEquals(3, w.pack.taunter(), "the hitter holds the front now");
    }

    @Test
    void aSecondHitWhileTheFrontChangesHandsChangesNothing() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        double[] back = ShardlingPack.pointAt(t, 4.5, 150.0);
        double[] other = ShardlingPack.pointAt(t, 4.5, -150.0);
        World w = world().add(1, front[0], front[1]).add(2, back[0], back[1]).add(3, other[0], other[1]);
        w.plan(t);
        w.pack.onHitLanded(2);
        w.pack.onHitLanded(3);
        assertEquals(2, w.pack.elect(), "one handover at a time");
        assertEquals(1, w.pack.taunter());
    }

    @Test
    void anElectThatCannotGetToTheFrontGivesUpAndFlanksAgain() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        double[] back = ShardlingPack.pointAt(t, 4.5, 150.0);
        World w = world().add(1, front[0], front[1]).add(2, back[0], back[1]);
        w.tokens.tryAcquire(PLAYER + 1, 1); // tokens elsewhere: nobody attacks
        w.tokens.tryAcquire(PLAYER + 1, 2);
        w.free.put(1, false); // no feints
        w.plan(t);
        w.pack.onHitLanded(2);
        for (int i = 0; i < ShardlingPack.ELECT_PLANS; i++) {
            w.plan(t); // it never moves: something is in its way
            assertEquals(2, w.pack.elect(), "plan " + i + ": still on its way");
            assertEquals(1, w.pack.taunter());
        }
        w.plan(t);
        assertEquals(-1, w.pack.elect(), "it gave up");
        assertEquals(1, w.pack.taunter(), "the Taunter kept the front all along");
        Order order = w.pack.order(2);
        assertEquals(Role.FLANKER, order.role());
        assertTrue(Math.abs(ShardlingPack.angleOf(t, order.x(), order.z())) >= ShardlingPack.FLANK_ANGLE_MIN, "it is sent back to a flank");
    }

    @Test
    void noHandoverUnlessTheTaunterHoldsTheFront() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] lunging = ShardlingPack.pointAt(t, 1.2, 0.0); // the Taunter's own lunge took it to the player
        double[] back = ShardlingPack.pointAt(t, 4.5, 150.0);
        World w = world().add(1, lunging[0], lunging[1]).add(2, back[0], back[1]);
        w.tokens.tryAcquire(PLAYER + 1, 1);
        w.tokens.tryAcquire(PLAYER + 1, 2);
        w.plan(t);
        assertFalse(w.pack.frontHeld());
        w.pack.onHitLanded(2);
        assertEquals(-1, w.pack.elect(), "nobody holds the front to hand over");
        assertEquals(1, w.pack.taunter());
    }

    @Test
    void membersRunWhenFarAndWalkTheLastStretch() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] far = ShardlingPack.pointAt(t, 14.0, 0.0);
        double[] near = ShardlingPack.pointAt(t, 5.8, 0.0);
        World w = world().add(1, far[0], far[1]);
        w.tokens.tryAcquire(PLAYER + 1, 1);
        w.plan(t);
        assertEquals(ShardlingPack.Pace.RUN, w.pack.order(1).pace());
        w.at.put(1, near);
        w.plan(t);
        assertEquals(ShardlingPack.Pace.WALK, w.pack.order(1).pace(), "0.8 from its spot, outside the band's inner edge");
    }

    // ------------------------------------------------------------------ attacks and tokens

    @Test
    void lungeFromCloseSpitFromBeyondSix() {
        assertEquals(Attack.LUNGE, ShardlingPack.attackFor(4.5));
        assertEquals(Attack.LUNGE, ShardlingPack.attackFor(5.5));
        assertEquals(Attack.NONE, ShardlingPack.attackFor(5.9), "between the two: close in first");
        assertEquals(Attack.NONE, ShardlingPack.attackFor(6.0), "the spit is for beyond 6 blocks");
        assertEquals(Attack.SPIT, ShardlingPack.attackFor(6.1));
        assertEquals(Attack.SPIT, ShardlingPack.attackFor(12.0));
        assertEquals(Attack.NONE, ShardlingPack.attackFor(1.0), "too close to leap");
        assertEquals(Attack.NONE, ShardlingPack.attackFor(20.0), "too far to spit");
    }

    @Test
    void aFlankerBehindAttacksBeforeTheTaunter() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        double[] back = ShardlingPack.pointAt(t, 4.5, 150.0);
        World w = world().add(1, front[0], front[1]).add(2, back[0], back[1]);
        w.plan(t);
        assertEquals(1, w.pack.taunter());
        assertEquals(Attack.LUNGE, w.pack.takeAttack(2));
        assertEquals(Attack.NONE, w.pack.takeAttack(1), "one new attack per plan");
        assertEquals(Attack.NONE, w.pack.takeAttack(2), "taking it clears it");
        assertTrue(w.tokens.holds(2));
    }

    @Test
    void aFarMemberSpits() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        World w = world().add(1, 0, 10);
        w.plan(t);
        assertEquals(Attack.SPIT, w.pack.takeAttack(1));
    }

    @Test
    void noAttackWithoutSightOrWhileBusy() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        ShardlingPack pack = new ShardlingPack();
        AttackTokens tokens = new AttackTokens();
        pack.join(1);
        pack.join(2);
        pack.plan(0, t, List.of(new Member(1, 0, 4.5, true, true, false), new Member(2, 0, -4.5, true, false, true)), tokens);
        assertEquals(Attack.NONE, pack.takeAttack(1), "no line of sight");
        assertEquals(Attack.NONE, pack.takeAttack(2), "not ready");
        assertEquals(0, tokens.held(PLAYER));
    }

    @Test
    void neverMoreThanTwoAttackersOnOnePlayerAcrossPacks() {
        AttackTokens tokens = new AttackTokens();
        Target t = new Target(PLAYER, 0, 0, 45f);
        World a = new World(tokens);
        World b = new World(tokens);
        for (int i = 0; i < 5; i++) {
            double[] p = ShardlingPack.pointAt(t, 4.5, i * 72.0);
            a.add(1 + i, p[0], p[1]);
            double[] q = ShardlingPack.pointAt(t, 5.0, 36.0 + i * 72.0);
            b.add(11 + i, q[0], q[1]);
        }
        // Each attack lasts 32 ticks (12 tell, 4 active, 16 recovery); plans come every 10.
        Map<Integer, Long> attackingUntil = new HashMap<>();
        int maxHeld = 0;
        int attacksStarted = 0;
        for (long now = 0; now < 2000; now += ShardlingPack.PLAN_INTERVAL) {
            for (World w : List.of(a, b)) {
                for (int id : w.pack.members()) {
                    Long until = attackingUntil.get(id);
                    if (until != null && now >= until) {
                        attackingUntil.remove(id);
                        tokens.release(id); // the attack ended
                    }
                    w.ready.put(id, !attackingUntil.containsKey(id));
                    w.free.put(id, !attackingUntil.containsKey(id));
                }
                w.pack.plan(now, t, w.views(), tokens);
                int started = 0;
                for (int id : w.pack.members()) {
                    if (w.pack.takeAttack(id) != Attack.NONE) {
                        started++;
                        attackingUntil.put(id, now + 32);
                    }
                }
                assertTrue(started <= 1, "one new attack per plan per pack");
                attacksStarted += started;
                maxHeld = Math.max(maxHeld, tokens.held(PLAYER));
                long attacking = attackingUntil.size();
                assertTrue(attacking <= AttackTokens.PER_TARGET, "attacking at once: " + attacking);
            }
        }
        assertEquals(AttackTokens.PER_TARGET, maxHeld, "the pressure does reach two");
        assertTrue(attacksStarted > 50, "attacks keep coming: " + attacksStarted);
    }

    @Test
    void tokensComeBackOnRetargetLeaveAndStandDown() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        World w = world().add(1, 0, 4.5).add(2, 0, -4.5).add(3, 4.5, 0);
        for (int i = 0; i < 2; i++) {
            w.plan(t);
            for (int id : w.pack.members()) {
                w.pack.takeAttack(id); // whoever was picked starts its attack
            }
        }
        assertEquals(2, w.tokens.held(PLAYER), "two attacks granted over two plans");

        w.plan(new Target(PLAYER + 1, 0, 0, 0f));
        assertEquals(0, w.tokens.held(PLAYER), "a new target gives the old one's tokens back");

        World v = world().add(1, 0, 4.5).add(2, 0, -4.5);
        v.plan(t);
        int holder = v.tokens.holders(PLAYER).iterator().next();
        v.pack.takeAttack(holder);
        v.pack.leave(holder, v.tokens);
        assertEquals(0, v.tokens.held(PLAYER), "a member that dies gives its token back");

        World u = world().add(1, 0, 4.5);
        u.plan(t);
        u.pack.takeAttack(1);
        assertTrue(u.tokens.holds(1));
        u.pack.plan(u.now, null, u.views(), u.tokens);
        assertFalse(u.tokens.holds(1), "no target: the pack stands down");
        assertEquals(-1, u.pack.target());
    }

    @Test
    void anAttackNotTakenBeforeTheNextPlanIsCancelled() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        World w = world().add(1, 0, 4.5);
        w.plan(t);
        assertTrue(w.tokens.holds(1));
        w.ready.put(1, false); // it staggered before it could start
        w.free.put(1, false);
        w.plan(t);
        assertFalse(w.tokens.holds(1));
        assertEquals(Attack.NONE, w.pack.takeAttack(1));
    }

    @Test
    void onlyAFlankersHitStartsAHandover() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        double[] front = ShardlingPack.pointAt(t, 5.0, 0.0);
        double[] back = ShardlingPack.pointAt(t, 4.5, 150.0);
        World w = world().add(1, front[0], front[1]).add(2, back[0], back[1]).add(3, -back[0], back[1]);
        w.plan(t);
        assertEquals(1, w.pack.taunter());
        assertTrue(w.pack.frontHeld());
        w.pack.onHitLanded(1);
        assertEquals(-1, w.pack.elect(), "the Taunter's own hit changes nothing");
        w.pack.onHitLanded(2);
        assertEquals(2, w.pack.elect());
        assertEquals(Role.FLANKER, w.pack.role(2), "it stays a Flanker until it gets there");
        assertEquals(Role.TAUNTER, w.pack.role(1));
    }

    @Test
    void aDeadTaunterIsReplacedByTheMemberNearestTheFront() {
        Target t = new Target(PLAYER, 0, 0, 0f);
        World w = world().add(1, 0, 5).add(2, 1, 7).add(3, 0, -5);
        w.plan(t);
        assertEquals(1, w.pack.taunter());
        w.pack.leave(1, w.tokens);
        w.plan(t);
        assertEquals(2, w.pack.taunter());
    }
}
