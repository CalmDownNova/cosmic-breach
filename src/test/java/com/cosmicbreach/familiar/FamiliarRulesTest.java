package com.cosmicbreach.familiar;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The familiars' numbers and rules (GDD 8.2). */
class FamiliarRulesTest {
    private static final double EPS = 1e-9;

    @Test
    void healthIsTenPlusHalfTheOwnersResilienceAndTheGravikinHasHalfAgainMore() {
        assertEquals(10.0, FamiliarRules.maxHealth(FamiliarKind.EMBERWISP, 0), EPS);
        assertEquals(20.0, FamiliarRules.maxHealth(FamiliarKind.EMBERWISP, 20), EPS);
        assertEquals(30.0, FamiliarRules.maxHealth(FamiliarKind.PRISM_MOTH, 40), EPS);
        assertEquals(15.0, FamiliarRules.maxHealth(FamiliarKind.GRAVIKIN, 0), EPS);
        assertEquals(30.0, FamiliarRules.maxHealth(FamiliarKind.GRAVIKIN, 20), EPS);
        assertEquals(45.0, FamiliarRules.maxHealth(FamiliarKind.GRAVIKIN, 40), EPS);
        assertEquals(10.0, FamiliarRules.maxHealth(FamiliarKind.EMBERWISP, -5), EPS, "never under the base");
    }

    @Test
    void damageIsTwoPlusATenthOfTheOwnersArcane() {
        assertEquals(2.0, FamiliarRules.damage(0), EPS);
        assertEquals(3.5, FamiliarRules.damage(15), EPS);
        assertEquals(4.0, FamiliarRules.damage(20), EPS);
        assertEquals(6.0, FamiliarRules.damage(40), EPS);
    }

    @Test
    void theLanternIsLitOutOrDarkForSixtySecondsAfterADeath() {
        long death = 1000;
        long until = LanternState.darkUntil(death);
        assertEquals(death + 1200, until);
        assertEquals(LanternState.DARK, LanternState.of(until, false, death));
        assertEquals(LanternState.DARK, LanternState.of(until, true, until - 1), "dark wins over out");
        assertEquals(LanternState.LIT, LanternState.of(until, false, until));
        assertEquals(LanternState.OUT, LanternState.of(until, true, until));
        assertEquals(LanternState.LIT, LanternState.of(0, false, 5));
        assertEquals(60, LanternState.secondsLeft(until, death));
        assertEquals(1, LanternState.secondsLeft(until, until - 1));
        assertEquals(0, LanternState.secondsLeft(until, until));
    }

    @Test
    void theBondKeepsTheModeTheHealthAndTheDarkness() {
        UUID id = new UUID(1, 2);
        FamiliarBond b = FamiliarBond.hatch(FamiliarKind.GRAVIKIN, id);
        assertEquals(FamiliarMode.GUARD, b.mode(), "a hatchling starts in Guard");
        assertEquals(1.0f, b.healthAt(0));
        assertEquals(LanternState.LIT, b.state(false, 0));
        FamiliarBond hurt = b.withMode(FamiliarMode.ATTACK).rested(0.25f, 100);
        assertEquals(FamiliarMode.ATTACK, hurt.mode());
        assertEquals(0.25f, hurt.healthAt(100), 1e-6);
        assertEquals(0.75f, hurt.healthAt(100 + 600), 1e-6, "half the way back after 30 s");
        assertEquals(1.0f, hurt.healthAt(100 + 5000), 1e-6);
        FamiliarBond dead = hurt.died(2000);
        assertEquals(LanternState.DARK, dead.state(false, 2000 + 1199));
        assertEquals(LanternState.LIT, dead.state(false, 2000 + 1200));
        assertEquals(1.0f, dead.healthAt(2000 + 1200), 1e-6, "whole when it relights");
        assertEquals(id, dead.id());
        assertEquals(FamiliarMode.ATTACK, dead.mode());
    }

    @Test
    void aRestedFamiliarHealsEvenlyAndWholeInSixtySeconds() {
        assertEquals(0.5f, FamiliarRules.restored(0.5f, 0), 1e-6);
        assertEquals(0.75f, FamiliarRules.restored(0.5f, 300), 1e-6);
        assertEquals(1.0f, FamiliarRules.restored(0.0f, 1200), 1e-6);
        assertEquals(1.0f, FamiliarRules.restored(0.9f, 100_000), 1e-6);
        assertEquals(0.3f, FamiliarRules.restored(0.3f, -50), 1e-6, "time never runs backwards");
    }

    @Test
    void aTapCyclesAttackGuardPassive() {
        assertEquals(FamiliarMode.GUARD, FamiliarMode.ATTACK.next());
        assertEquals(FamiliarMode.PASSIVE, FamiliarMode.GUARD.next());
        assertEquals(FamiliarMode.ATTACK, FamiliarMode.PASSIVE.next());
        assertTrue(FamiliarMode.ATTACK.fights());
        assertTrue(FamiliarMode.GUARD.fights());
        assertFalse(FamiliarMode.PASSIVE.fights());
    }

    @Test
    void eachFamiliarsTimers() {
        assertEquals(60, FamiliarRules.SCORCH_EVERY, "Scorch every 3 s");
        assertEquals(60, FamiliarRules.KINDLED_TICKS);
        assertEquals(400, FamiliarRules.TAUNT_EVERY, "a taunt every 20 s");
        assertEquals(80, FamiliarRules.TAUNT_TICKS);
        assertEquals(40, FamiliarRules.REFRACT_EVERY, "Refract every 2 s");
        assertEquals(600, FamiliarRules.CLEANSE_EVERY, "a cleanse every 30 s");
        assertEquals(12000, FamiliarRules.HATCH_TICKS, "10 minutes in the brazier");
        assertEquals(16.0, FamiliarRules.TELEPORT_RANGE, EPS);
    }

    @Test
    void aCadenceIsReadyAtOnceThenEveryPeriodAndWaitsForSomethingToUseItOn() {
        Cadence c = new Cadence(60);
        assertTrue(c.ready(0));
        assertEquals(0, c.left(0));
        c.fire(10);
        assertFalse(c.ready(69));
        assertEquals(1, c.left(69));
        assertTrue(c.ready(70));
        assertTrue(c.ready(500), "waits ready until used");
        c.fire(500);
        assertEquals(500, c.last());
        assertEquals(60, c.left(500));
        c.reset();
        assertTrue(c.ready(501));
        assertEquals(Long.MIN_VALUE, c.last());
    }

    @Test
    void refractStacksToThreeAndOnlyThreeGiveTheAbilityThirtyPercent() {
        assertEquals(1, RefractStacks.after(0));
        assertEquals(2, RefractStacks.after(1));
        assertEquals(3, RefractStacks.after(2));
        assertEquals(3, RefractStacks.after(3), "capped at 3");
        assertEquals(1.0, RefractStacks.multiplier(0), EPS);
        assertEquals(1.0, RefractStacks.multiplier(2), EPS);
        assertEquals(1.3, RefractStacks.multiplier(3), EPS);
        assertFalse(RefractStacks.consumes(2));
        assertTrue(RefractStacks.consumes(3));
        // three stacks from the first one take two more of the moth's 2 s turns
        assertEquals(2 * FamiliarRules.REFRACT_EVERY, 80);
        assertTrue(RefractStacks.TICKS > 2 * FamiliarRules.REFRACT_EVERY, "the stacks outlast the time to build three");
    }

    @Test
    void kindledIsTenPercent() {
        assertEquals(1.1, FamiliarRules.kindledMultiplier(true), EPS);
        assertEquals(1.0, FamiliarRules.kindledMultiplier(false), EPS);
        assertEquals(0.8, FamiliarRules.slowedSpeed(), EPS);
    }

    @Test
    void theRecentTargetIsTheNewestStillValidWithinFiveSeconds() {
        RecentTargets r = new RecentTargets();
        assertTrue(r.latest(0, 100, id -> true).isEmpty());
        r.hit(7, 10);
        r.hit(8, 20);
        assertEquals(8, r.latest(30, 100, id -> true).getAsInt());
        assertEquals(7, r.latest(30, 100, id -> id != 8).getAsInt(), "the newest valid one");
        assertEquals(8, r.latest(120, 100, id -> true).getAsInt());
        assertTrue(r.latest(121, 100, id -> id == 7).isEmpty(), "7 was hit too long ago");
        r.hit(7, 200);
        assertEquals(7, r.latest(200, 100, id -> true).getAsInt(), "hit again: to the front");
        assertEquals(2, r.size());
        for (int i = 0; i < 10; i++) {
            r.hit(100 + i, 300 + i);
        }
        assertEquals(RecentTargets.SIZE, r.size(), "holds four");
        assertEquals(109, r.latest(309, 100, id -> true).getAsInt());
        assertEquals(Long.MIN_VALUE, r.lastHit(7));
    }

    @Test
    void theCleanseTakesRiftFirstThenWhatLastsLongest() {
        assertEquals(-1, FamiliarRules.pickCleanse(List.of()));
        assertEquals(1, FamiliarRules.pickCleanse(List.of(new FamiliarRules.Status(false, 400), new FamiliarRules.Status(true, 20))));
        assertEquals(0, FamiliarRules.pickCleanse(List.of(new FamiliarRules.Status(false, 400), new FamiliarRules.Status(false, 60))));
        assertEquals(1, FamiliarRules.pickCleanse(List.of(new FamiliarRules.Status(false, 400), new FamiliarRules.Status(false, -1))),
                "no end counts as the longest");
    }

    @Test
    void theTauntPullsMonstersInAttackWhatIsOnUsInGuardAndNeverABoss() {
        FamiliarRules.Near monster = new FamiliarRules.Near(false, true, false, 5.0);
        FamiliarRules.Near chasing = new FamiliarRules.Near(false, false, true, 7.9);
        FamiliarRules.Near far = new FamiliarRules.Near(false, true, true, 8.1);
        FamiliarRules.Near boss = new FamiliarRules.Near(true, true, true, 3.0);
        assertTrue(FamiliarRules.taunts(FamiliarMode.ATTACK, monster));
        assertTrue(FamiliarRules.taunts(FamiliarMode.ATTACK, chasing));
        assertFalse(FamiliarRules.taunts(FamiliarMode.ATTACK, far), "8 blocks at most");
        assertFalse(FamiliarRules.taunts(FamiliarMode.ATTACK, boss), "bosses take threat instead");
        assertFalse(FamiliarRules.taunts(FamiliarMode.GUARD, monster), "Guard leaves a monster that isn't after us");
        assertTrue(FamiliarRules.taunts(FamiliarMode.GUARD, chasing));
        assertFalse(FamiliarRules.taunts(FamiliarMode.PASSIVE, chasing));
        assertTrue(FamiliarRules.tauntsBoss(FamiliarMode.GUARD, 8.0));
        assertFalse(FamiliarRules.tauntsBoss(FamiliarMode.ATTACK, 8.5));
        assertFalse(FamiliarRules.tauntsBoss(FamiliarMode.PASSIVE, 2.0));
        assertEquals(150.0, FamiliarRules.TAUNT_THREAT, EPS);
        assertEquals(com.cosmicbreach.guardian.heliarch.HeliarchMoves.GRAVIKIN_TAUNT, FamiliarRules.TAUNT_THREAT, EPS,
                "the Heliarch counts the taunt as 150");
    }

    @Test
    void hatchingTakesTenMinutesOfLoadedTime() {
        assertFalse(FamiliarRules.hatched(11999, FamiliarRules.HATCH_TICKS));
        assertTrue(FamiliarRules.hatched(12000, FamiliarRules.HATCH_TICKS));
        assertTrue(FamiliarRules.hatched(60, 60), "a shortened hatch for tests");
    }

    @Test
    void theWispOrbitsTiltedUnderTheEyesInFrontAndTheMothStaysBehindTheHead() {
        float yaw = 0f; // facing +Z
        double front = Double.NaN;
        double back = Double.NaN;
        for (int t = 0; t < 80; t++) {
            Vec3 p = FamiliarPaths.wispOrbit(t, yaw);
            assertEquals(FamiliarPaths.WISP_RADIUS, Math.hypot(p.x, p.z), 1e-9, "a circle");
            if (p.z > 1.14) {
                front = p.y;
            }
            if (p.z < -1.14) {
                back = p.y;
            }
            if (p.z > 0) {
                assertTrue(p.y < 1.45, "in front it passes under the eyes' line: " + p);
            }
        }
        assertTrue(front < back - 0.4, "lower in front than behind: " + front + " vs " + back);
        for (int t = 0; t < 120; t++) {
            Vec3 m = FamiliarPaths.mothLoop(t, 90f); // facing -X: behind is +X
            assertTrue(m.x > 0.2, "behind the head: " + m);
            assertTrue(m.y > 1.7 && m.y < 2.3);
        }
        Vec3 perch = FamiliarPaths.perch(0f);
        assertTrue(perch.x < 0 && perch.z < 0, "behind and to the right");
    }

    @Test
    void aHopLeavesAndLandsOnItsSurfacesAndPeaksOverTheHigherOne() {
        Vec3 a = new Vec3(0, 64, 0);
        Vec3 b = new Vec3(4, 65, 0);
        double apex = FamiliarPaths.hopApex(4.1);
        assertEquals(a, FamiliarPaths.hop(a, b, apex, 0.0));
        assertEquals(b, FamiliarPaths.hop(a, b, apex, 1.0));
        Vec3 mid = FamiliarPaths.hop(a, b, apex, 0.5);
        assertEquals(65 + apex, mid.y, 1e-9);
        assertEquals(15, FamiliarPaths.hopTicks(4.1));
        assertEquals(8, FamiliarPaths.hopTicks(0.3));
        assertEquals(20, FamiliarPaths.hopTicks(30));
        Vec3 s = FamiliarPaths.step(Vec3.ZERO, new Vec3(10, 0, 0), 0.5, 1.2);
        assertEquals(1.2, s.length(), 1e-9, "capped");
        assertEquals(0.5, FamiliarPaths.step(Vec3.ZERO, new Vec3(1, 0, 0), 0.5, 1.2).x, 1e-9);
    }
}
