package com.cosmicbreach.structure.sanctum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.sanctum.GateRule.Near;
import com.cosmicbreach.structure.sanctum.GateRule.Update;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Sanctum's rules: the Gate's party rule, the Eclipse Locks and the Throne Stair, the fall rescue. */
class SanctumRulesTest {
    private static final UUID OPENER = UUID.nameUUIDFromBytes("opener".getBytes());
    private static final UUID FRIEND = UUID.nameUUIDFromBytes("friend".getBytes());
    private static final UUID FAR = UUID.nameUUIDFromBytes("far".getBytes());

    private static double[] at(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    @Test
    void theGateLetsTheAttunedAndTheirPartyThrough() {
        assertTrue(GateRule.passes(true, false));
        assertTrue(GateRule.passes(false, true));
        assertFalse(GateRule.passes(false, false));
        Map<UUID, double[]> everyone = new LinkedHashMap<>();
        everyone.put(OPENER, at(0, 84, 0));
        everyone.put(FRIEND, at(10, 84, 12));   // 15.6 away
        everyone.put(FAR, at(0, 84, 16.5));      // 16.5 away
        List<UUID> party = GateRule.party(everyone.get(OPENER), everyone);
        assertEquals(List.of(OPENER, FRIEND), party, "within 16 blocks of the opener, the opener included");
    }

    @Test
    void anArrivalAdmitsThePartyOnceAndTheDoorsFollowWhoIsNear() {
        GateRule.Gate gate = new GateRule.Gate();
        Map<UUID, double[]> everyone = new LinkedHashMap<>();
        everyone.put(OPENER, at(0, 84, 3));
        everyone.put(FRIEND, at(0, 84, 12));
        everyone.put(FAR, at(0, 84, 40));
        // a stranger alone is refused, told once in a while, and the doors stay shut
        Update u = gate.tick(0, List.of(new Near(FAR, false, false, at(0, 84, 2))), everyone, false, List.of(FAR));
        assertFalse(u.open());
        assertEquals(Set.of(FAR), u.refused());
        u = gate.tick(10, List.of(new Near(FAR, false, false, at(0, 84, 2))), everyone, false, List.of(FAR));
        assertTrue(u.refused().isEmpty(), "not told again at once");
        u = gate.tick(10 + GateRule.REFUSE_EVERY, List.of(), everyone, false, List.of(FAR));
        assertEquals(Set.of(FAR), u.refused(), "told again later");
        // the attuned opener arrives: it opens, and admits whoever is within 16 blocks right then
        u = gate.tick(200, List.of(new Near(OPENER, true, false, at(0, 84, 3))), everyone, false, List.of());
        assertTrue(u.open() && u.opened());
        assertEquals(Set.of(OPENER, FRIEND), u.admitted());
        // staying at the Gate is not a second arrival
        u = gate.tick(201, List.of(new Near(OPENER, true, false, at(0, 84, 3))), everyone, false, List.of());
        assertTrue(u.admitted().isEmpty() && !u.opened());
        // everyone leaves: it closes after the delay, but never on someone in the doorway
        u = gate.tick(202, List.of(), everyone, false, List.of());
        assertTrue(u.open(), "not at once");
        u = gate.tick(201 + GateRule.CLOSE_DELAY, List.of(), everyone, true, List.of());
        assertTrue(u.open(), "someone stands in the doorway");
        u = gate.tick(202 + GateRule.CLOSE_DELAY, List.of(), everyone, false, List.of());
        assertTrue(u.closed() && !u.open());
        // the party returns without the opener: admitted players open it again
        u = gate.tick(500, List.of(new Near(FRIEND, false, true, at(0, 84, 2))), everyone, false, List.of());
        assertTrue(u.opened(), "it stays open for a returning party");
        assertTrue(u.admitted().isEmpty());
        // the opener leaves and comes back: a new arrival, a new party (the stranger has come close now)
        gate.tick(600, List.of(), everyone, false, List.of());
        everyone.put(FAR, at(0, 84, 10));
        u = gate.tick(601, List.of(new Near(OPENER, true, false, at(0, 84, 3))), everyone, false, List.of());
        assertTrue(u.admitted().contains(FAR), "whoever is with the opener when it opens");
        // a refused player who is admitted this very tick is not told off
        GateRule.Gate g2 = new GateRule.Gate();
        u = g2.tick(0, List.of(new Near(OPENER, true, false, at(0, 84, 3)), new Near(FRIEND, false, false, at(0, 84, 4))), everyone,
                false, List.of(FRIEND));
        assertTrue(u.refused().isEmpty() && u.admitted().contains(FRIEND));
    }

    @Test
    void eachWingLightsItsLockAndBothOpenTheStairOnce() {
        LockRule s = LockRule.SEALED;
        assertEquals(0, s.count());
        assertFalse(s.opensStair(Wing.WEST));
        s = s.light(Wing.WEST);
        assertTrue(s.lit(Wing.WEST) && !s.lit(Wing.EAST) && !s.stair());
        s = s.light(Wing.WEST);
        assertEquals(1, s.count(), "solving a wing again lights nothing new");
        assertTrue(s.opensStair(Wing.EAST), "the second lock opens the stair");
        s = s.light(Wing.EAST);
        assertTrue(s.stair() && s.count() == 2);
        assertFalse(s.opensStair(Wing.EAST), "only once");
        assertFalse(s.opensStair(Wing.WEST));
        // either order
        LockRule t = LockRule.SEALED.light(Wing.EAST);
        assertFalse(t.stair());
        assertTrue(t.light(Wing.WEST).stair());
    }

    @Test
    void aFallFromTheArenaIsThrownBackOntoTheRimForHalfTheHealth() {
        // only below the line, within the catch cylinder, falling, not creative
        assertTrue(RescueRule.shouldRescue(20, RescueRule.CATCH_Y - 0.1, 25, false, false));
        assertFalse(RescueRule.shouldRescue(20, RescueRule.CATCH_Y + 0.1, 25, false, false), "not yet");
        assertFalse(RescueRule.shouldRescue(20, 30, 25, true, false), "standing on something");
        assertFalse(RescueRule.shouldRescue(20, 30, 25, false, true), "creative");
        assertFalse(RescueRule.shouldRescue(40, 30, 30, false, false), "outside the catch cylinder (50 out)");
        assertTrue(RescueRule.shouldRescue(0, -60, -47, false, false), "anywhere below, all the way to the void");
        assertEquals(10f, RescueRule.healthAfter(20f));
        assertEquals(0.25f, RescueRule.healthAfter(0.5f));
        assertTrue(RescueRule.healthAfter(Float.MIN_VALUE) >= 0f);
        // first choice: the rim, at the angle of the fall
        List<double[]> c = RescueRule.candidates(40.5, 0.5); // fell off the east edge
        double[] first = c.get(0);
        assertEquals(3, SanctumLayout.ring((int) Math.floor(first[0]), (int) Math.floor(first[1])), "onto the rim");
        assertEquals(26.5, Math.hypot(first[0] - 0.5, first[1] - 0.5), 1e-9);
        assertEquals(90.0, Math.toDegrees(SanctumLayout.angle(first[0] - 0.5, first[1] - 0.5)), 1e-6, "where they fell");
        for (double[] p : c) {
            assertTrue(SanctumLayout.pillarAt((int) Math.floor(p[0]), (int) Math.floor(p[1])) < 0, "never into a pillar");
            assertTrue(SanctumLayout.ring((int) Math.floor(p[0]), (int) Math.floor(p[1])) >= 0, "always on the disc");
        }
        // the rim's segment there has fallen: the nearest standing rim segment next
        int fellSeg = SanctumLayout.segment(40, 0);
        Set<Integer> gone = new HashSet<>(Set.of(fellSeg));
        double[] next = RescueRule.pick(c, (x, z) -> standing(x, z, gone, -1));
        int seg = SanctumLayout.segment((int) Math.floor(next[0]), (int) Math.floor(next[1]));
        assertEquals(3, SanctumLayout.ring((int) Math.floor(next[0]), (int) Math.floor(next[1])));
        assertEquals(1, Math.min(Math.floorMod(seg - fellSeg, 8), Math.floorMod(fellSeg - seg, 8)), "a neighbouring segment");
        // the whole rim has fallen: the outer ring
        double[] inner = RescueRule.pick(c, (x, z) -> standing(x, z, Set.of(), 3));
        assertEquals(2, SanctumLayout.ring((int) Math.floor(inner[0]), (int) Math.floor(inner[1])));
        // nothing is left: nowhere
        assertNull(RescueRule.pick(c, (x, z) -> false));
    }

    /** A test arena: segments in {@code rimGone} of the rim have fallen, and every ring from {@code ringsGone} out. */
    private static boolean standing(double x, double z, Set<Integer> rimGone, int ringsGone) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int ring = SanctumLayout.ring(bx, bz);
        if (ring < 0 || (ringsGone >= 0 && ring >= ringsGone)) {
            return false;
        }
        return !(ring == 3 && rimGone.contains(SanctumLayout.segment(bx, bz)));
    }
}
