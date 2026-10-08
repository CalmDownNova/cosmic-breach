package com.cosmicbreach.lift;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who the rescue lift's trail is drawn behind: the rule each client applies, and what a player arriving in the Drift is told. */
class LiftRidersTest {
    @Test
    void aPlayerCarriedUpIsListed() {
        assertTrue(LiftRiders.carried(true, false));
    }

    @Test
    void aPlayerWhoSneaksToSinkIsNotListedSoTheyTrailNothingGoingDown() {
        assertFalse(LiftRiders.carried(true, true), "the lift holds them but lowers them: no trail climbs behind them");
    }

    @Test
    void aPlayerTheLiftHasNotCaughtIsNeverListed() {
        assertFalse(LiftRiders.carried(false, false));
        assertFalse(LiftRiders.carried(false, true));
    }

    @Test
    void aPlayerWhoArrivesIsToldWhoIsCarriedEvenWhenNobodyIs() {
        // a client that left the Drift while someone was carried still holds that list; the empty one is what clears it (it was once
        // skipped, and the teammate then trailed a rescue ribbon wherever they walked, for the rest of the session)
        LiftRiders told = LiftRiders.forArrival(true, List.of());
        assertNotNull(told, "told even when the list is empty");
        assertTrue(told.ids().isEmpty());
    }

    @Test
    void aPlayerWhoArrivesIsToldTheRidersThereAre() {
        assertEquals(new LiftRiders(List.of(3, 9)), LiftRiders.forArrival(true, List.of(3, 9)));
    }

    @Test
    void aPlayerAlreadyInTheDriftIsNotToldAgainWhatTheyHearOfWhenItChanges() {
        assertNull(LiftRiders.forArrival(false, List.of(3, 9)));
        assertNull(LiftRiders.forArrival(false, List.of()));
    }
}
