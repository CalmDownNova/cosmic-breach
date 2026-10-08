package com.cosmicbreach.mount;

import com.cosmicbreach.world.AiLod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static com.cosmicbreach.mount.MountGear.Kind.MANTA;
import static com.cosmicbreach.mount.MountGear.Kind.STAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeping a tamed mount (1.1 design section 4): its floor, where it is safe, its rescue, the hits it shrugs off. */
class MountCareRulesTest {
    @Test
    void eachMountHasTheFloorOfItsOwnLayer() {
        assertEquals(160.0, MountCareRules.safeFloor(MANTA), "under the Drift a stingray can never climb");
        assertEquals(320.0, MountCareRules.safeFloor(STAG), "below its islands a stag is in the gap");
    }

    @Test
    void aStagIsSafeStandingOnItsIslandsAndAStingrayInTheDriftsAir() {
        assertTrue(MountCareRules.safeHere(STAG, 340, true, false));
        assertFalse(MountCareRules.safeHere(STAG, 340, false, false), "in the air");
        assertFalse(MountCareRules.safeHere(STAG, 250, true, true), "on an asteroid below its gap");
        assertTrue(MountCareRules.safeHere(MANTA, 200, false, true));
        assertFalse(MountCareRules.safeHere(MANTA, 162, false, true), "too close to the gap below");
        assertFalse(MountCareRules.safeHere(MANTA, 330, false, false), "above the Drift it only glides");
    }

    @Test
    void onlyAnUnriddenTamedMountInAetheriaBelowItsFloorIsRescued() {
        assertTrue(MountCareRules.needsRescue(true, false, true, MANTA, 150));
        assertFalse(MountCareRules.needsRescue(false, false, true, MANTA, 150), "wild");
        assertFalse(MountCareRules.needsRescue(true, true, true, MANTA, 150), "its rider decides");
        assertFalse(MountCareRules.needsRescue(true, false, false, MANTA, 150), "another dimension");
        assertFalse(MountCareRules.needsRescue(true, false, true, MANTA, 170), "still in the Drift");
        assertTrue(MountCareRules.needsRescue(true, false, true, STAG, 310), "a stag fallen into its gap");
    }

    @Test
    void aMountItsRiderParkedBelowItsLayerStaysThere() {
        // ridden down into the Deep and left there: parked, not lost
        assertTrue(MountCareRules.ridBelow(MANTA, 120));
        assertFalse(MountCareRules.needsRescue(true, false, true, MANTA, 120, MountCareRules.ridBelow(MANTA, 120)));
        // got off in the Drift, then fell below on its own: still rescued
        assertFalse(MountCareRules.ridBelow(MANTA, 200));
        assertTrue(MountCareRules.needsRescue(true, false, true, MANTA, 120, MountCareRules.ridBelow(MANTA, 200)));
        // the same for a stag ridden down past its gap
        assertTrue(MountCareRules.ridBelow(STAG, 250));
        assertFalse(MountCareRules.needsRescue(true, false, true, STAG, 250, true));
        assertFalse(MountCareRules.ridBelow(STAG, 340));
    }

    @Test
    void theRescueClimbsTheWayItFellThenCrossesWithoutOvershooting() {
        assertEquals(new Vec3(0, MountCareRules.RESCUE_SPEED, 0), MountCareRules.rescueVelocity(new Vec3(10, 120, 10), new Vec3(14, 200, 10)));
        Vec3 across = MountCareRules.rescueVelocity(new Vec3(10, 200, 10), new Vec3(14, 200, 10));
        assertEquals(MountCareRules.RESCUE_SPEED, across.length(), 1e-9);
        assertTrue(across.x > 0 && Math.abs(across.y) < 1e-9);
        assertEquals(0.1, MountCareRules.rescueVelocity(new Vec3(14, 200, 10.1), new Vec3(14, 200, 10)).length(), 1e-9);
        assertEquals(Vec3.ZERO, MountCareRules.rescueVelocity(new Vec3(14, 200, 10), new Vec3(14, 200, 10)));
    }

    @Test
    void itShrugsOffItsOwnersHitsAndTheWeather() {
        assertTrue(MountCareRules.shrugsOff(true, false, false));
        assertTrue(MountCareRules.shrugsOff(false, false, true));
        assertFalse(MountCareRules.shrugsOff(false, false, false), "an ordinary hit still hurts");
    }

    @Test
    void aPlayersHitIsShruggedOffExactlyWhenTheMountIsShelteredFromThem() {
        // quality review Important 2 and its re-review Minor 1: where PvP is off a friend's vanilla sword, sweep or arrow must not
        // hurt a mount, parked or ridden; where it is on another player's hit is fair game, as the mod's own weapons treat it.
        // Who is sheltered is TargetShield's rule (its own tests): PvP off or the same team, or a rider they may not hurt
        assertTrue(MountCareRules.shrugsOff(false, true, false), "sheltered from this player");
        assertFalse(MountCareRules.shrugsOff(false, false, false), "an enemy's hit where PvP is on, or a mob's");
    }

    @Test
    void aMountRemembersWhereItIsSafeAtEveryRateTheAiLodTicksIt() {
        // far from every player a mount ticks only every 2nd or 5th tick, on the ticks where (tickCount + id) divides by the
        // rate: a plain "tickCount % 20" never lands on one of those for most ids, so its safe spot would never be refreshed
        for (int every : new int[] {1, AiLod.HALF_EVERY, AiLod.FAR_EVERY}) {
            for (int id = 0; id < 60; id++) {
                int remembered = 0;
                for (int tick = 0; tick < 400; tick++) {
                    if (AiLod.ticksNow(tick, id, every) && MountCareRules.rememberNow(tick, id)) {
                        remembered++;
                    }
                }
                assertTrue(remembered >= 400 / MountCareRules.SAFE_EVERY - 1,
                        "id " + id + " ticking every " + every + " remembered " + remembered + " times in 400 ticks");
            }
        }
        assertEquals(20, MountCareRules.SAFE_EVERY);
    }

    @Test
    void itTriesTheSideItIsAlreadyOnFirstThenWidensAllTheWayRound() {
        double base = 0.5;
        assertEquals(base, MountCareRules.comeAngle(base, 0), 1e-12, "the direction it is already in");
        assertEquals(base - Math.PI / 4, MountCareRules.comeAngle(base, 1), 1e-12);
        assertEquals(base + Math.PI / 4, MountCareRules.comeAngle(base, 2), 1e-12);
        assertEquals(base - Math.PI / 2, MountCareRules.comeAngle(base, 3), 1e-12);
        assertEquals(base + Math.PI / 2, MountCareRules.comeAngle(base, 4), 1e-12);
        assertEquals(base - Math.PI, MountCareRules.comeAngle(base, MountCareRules.COME_DIRECTIONS - 1), 1e-12, "the far side last");
        Set<Long> degrees = new HashSet<>();
        for (int i = 0; i < MountCareRules.COME_DIRECTIONS; i++) {
            degrees.add(Math.floorMod(Math.round(Math.toDegrees(MountCareRules.comeAngle(base, i) - base)), 360L));
        }
        assertEquals(8, degrees.size(), "eight different directions: all the way round the player");
    }

    @Test
    void aMountWithNoRememberedSpotMayTakeItsOwnersSideIfThatIsSafeForItsKind() {
        // a mount saved before 1.1 never recorded where it was safe: below its layer it falls back on its owner, but only
        // if the owner stands where such a mount is safe (a stingray in the Drift's air, a stag on the ground above the gap)
        assertTrue(MountCareRules.ownerSpotWorks(MANTA, 200, true, true, 10), "an owner on a Drift asteroid");
        assertTrue(MountCareRules.ownerSpotWorks(MANTA, 250, false, true, 40), "an owner flying in the Drift");
        assertFalse(MountCareRules.ownerSpotWorks(MANTA, 150, true, false, 10), "an owner below the Drift is no safer");
        assertFalse(MountCareRules.ownerSpotWorks(MANTA, 330, true, false, 10), "above the Drift it only glides");
        assertFalse(MountCareRules.ownerSpotWorks(MANTA, 162, true, true, 10), "too close to the gap below");
        assertTrue(MountCareRules.ownerSpotWorks(STAG, 340, true, false, 10), "an owner on level 1 ground");
        assertFalse(MountCareRules.ownerSpotWorks(STAG, 340, false, false, 10), "in the air");
        assertFalse(MountCareRules.ownerSpotWorks(STAG, 250, true, true, 10), "on level 2 ground a stag is still in its gap");
        assertTrue(MountCareRules.ownerSpotWorks(MANTA, 200, true, true, MountCareRules.OWNER_RANGE));
        assertFalse(MountCareRules.ownerSpotWorks(MANTA, 200, true, true, MountCareRules.OWNER_RANGE + 1), "an owner too far to be near");
    }

    @Test
    void itIsSetDownBesideItsOwnerOnTheSideItComesFrom() {
        Vec3 owner = new Vec3(10, 200, 10);
        Vec3 spot = MountCareRules.ownerSpot(owner, new Vec3(10, 150, 60));
        assertEquals(10.0, spot.x, 1e-9);
        assertEquals(10 + MountCareRules.OWNER_SIDE, spot.z, 1e-9, "toward the mount, not on top of its owner");
        assertEquals(200.6, spot.y, 1e-9, "a little up");
        Vec3 diagonal = MountCareRules.ownerSpot(owner, new Vec3(-30, 100, -30));
        assertEquals(MountCareRules.OWNER_SIDE, Math.hypot(diagonal.x - owner.x, diagonal.z - owner.z), 1e-9);
        assertTrue(diagonal.x < owner.x && diagonal.z < owner.z);
        Vec3 below = MountCareRules.ownerSpot(owner, new Vec3(10, 100, 10));
        assertEquals(MountCareRules.OWNER_SIDE, Math.hypot(below.x - owner.x, below.z - owner.z), 1e-9,
                "straight below, it still comes down beside them and not into them");
    }

    @Test
    void theRescueCrossesOverARocksEdgeNotThroughItsSide() {
        Vec3 safe = new Vec3(14, 200, 10);
        double c = MountCareRules.CLEARANCE;
        assertEquals(new Vec3(0, MountCareRules.RESCUE_SPEED, 0), MountCareRules.rescueVelocity(new Vec3(10, 200.2, 10), safe, c),
                "still level with the rock's top: keep climbing");
        Vec3 over = MountCareRules.rescueVelocity(new Vec3(10, 200.6, 10), safe, c);
        assertEquals(MountCareRules.RESCUE_SPEED, over.length(), 1e-9);
        assertTrue(over.x > 0 && over.y < 0, "half a block over the top it crosses, sinking toward the spot");
        assertEquals(MountCareRules.rescueVelocity(new Vec3(10, 120, 10), safe), MountCareRules.rescueVelocity(new Vec3(10, 120, 10), safe, 0.0),
                "no clearance is the old rule");
    }

    @Test
    void justTamedItWaitsWellInsideItsOwnersReach() {
        // the stingray's box is 1.9 wide: its near edge is COME_DISTANCE - 0.95 from its owner, inside the 3 block reach
        assertTrue(MountCareRules.COME_DISTANCE - 0.95 < 2.0);
        assertTrue(MountCareRules.COME_TICKS >= 100);
    }
}
