package com.cosmicbreach.mount;

import org.junit.jupiter.api.Test;

import static com.cosmicbreach.mount.CrystalFate.Fate.FREE_HERE;
import static com.cosmicbreach.mount.CrystalFate.Fate.HAND_OVER;
import static com.cosmicbreach.mount.CrystalFate.Fate.HOLD;
import static com.cosmicbreach.mount.CrystalFate.Fate.LOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What becomes of a mount whose Stable Crystal is lost: the item falls out of the world, or is killed or destroyed (quality
 * review, Important 1, and its re-review: a player who dies in the void must get the crystal back, so an owner who is not
 * about is owed it instead of the item being held under the world).
 */
class CrystalFateTest {
    @Test
    void aCrystalThatFallsOutOfTheWorldIsHandedToItsOwnerAboutOrNot() {
        assertEquals(HAND_OVER, CrystalFate.whenFallen(true),
                "about, it goes into their pack; dead on the death screen or away, it is owed to them until they respawn or log in");
    }

    @Test
    void onlyAnItemWithNoOwnerOnRecordIsHeldWhereItIs() {
        assertEquals(HOLD, CrystalFate.whenFallen(false), "never a tamed mount's: it is lifted back above the line where the world deletes items");
    }

    @Test
    void aKilledCrystalGoesToItsOwnerIfTheyAreAboutElseSetsItsMountDownWhereItWas() {
        assertEquals(HAND_OVER, CrystalFate.whenKilled(true, true, true));
        assertEquals(HAND_OVER, CrystalFate.whenKilled(true, false, true), "the owner does not need room: it goes into their pack");
        assertEquals(FREE_HERE, CrystalFate.whenKilled(false, true, true));
    }

    @Test
    void withNoRoomAKilledCrystalIsOwedToItsOwner() {
        assertEquals(HAND_OVER, CrystalFate.whenKilled(false, false, true), "kept for them rather than the mount lost");
    }

    @Test
    void theOneWayAMountIsLostIsAKilledCrystalWithNoOwnerAboutNoRoomAndNoOwnerOnRecord() {
        assertEquals(LOST, CrystalFate.whenKilled(false, false, false));
        for (boolean here : new boolean[] {true, false}) {
            for (boolean room : new boolean[] {true, false}) {
                assertTrue(CrystalFate.whenKilled(here, room, true) != LOST, "with an owner on record it is never lost");
            }
        }
        assertTrue(CrystalFate.whenFallen(true) != LOST && CrystalFate.whenFallen(false) != LOST, "a fall never loses it");
    }

    @Test
    void itIsOutOfTheWorldOnceItIsWellBelowItsFloor() {
        assertFalse(CrystalFate.fellOut(5.0, 0), "on the Deep's rock");
        assertFalse(CrystalFate.fellOut(-1.0, 0), "just under the floor: a player there is already dying, an item may still land");
        assertFalse(CrystalFate.fellOut(-CrystalFate.FALL_MARGIN, 0));
        assertTrue(CrystalFate.fellOut(-CrystalFate.FALL_MARGIN - 0.5, 0));
        assertFalse(CrystalFate.fellOut(-70.0, -64), "another floor, another line: 6 under it is still inside the margin");
        assertTrue(CrystalFate.fellOut(-73.0, -64));
        assertTrue(CrystalFate.FALL_MARGIN < 64, "it acts well before the world discards it 64 blocks under the floor");
    }

    @Test
    void aFullCrystalStaysOutOfTheItemsThatCarryOtherItemsWhereTheGuardCannotReachIt() {
        assertFalse(CrystalFate.fitsInsideItems(true),
                "a shulker box is an ordinary item: it can fall out of the world or despawn, and its mounts would go with it");
        assertTrue(CrystalFate.fitsInsideItems(false), "an empty crystal has nothing to lose and stores like any item");
    }

    @Test
    void aHeldItemIsLiftedToJustAboveTheLineWhereTheWorldDeletesItems() {
        for (int floor : new int[] {0, -64}) {
            double held = CrystalFate.holdHeight(floor);
            assertFalse(CrystalFate.fellOut(held, floor), "not out of the world again, so it is not handled every tick");
            assertTrue(held > floor - 64, "and well above the line where the world deletes it (64 under the floor)");
        }
    }
}
