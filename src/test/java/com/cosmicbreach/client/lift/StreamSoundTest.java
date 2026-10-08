package com.cosmicbreach.client.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rising-air loop of each air vent: when a loop the engine never started, or dropped, is queued again. */
class StreamSoundTest {
    @Test
    void aLoopTheEngineDoesNotHaveIsGivenUpOnAfterAWhileAndQueuedAgain() {
        // the engine skips a sound (no free channel, master volume at zero, a mod cancelling it) or drops it (a level change stops every
        // sound), and the vent was then silent until the player walked 48 blocks away and back
        assertTrue(StreamSound.lost(100, 100 - StreamSound.GRACE, false), "not active a grace period after it was started");
        assertTrue(StreamSound.lost(1000, 100, false), "nor long after");
    }

    @Test
    void aLoopTheEngineHasIsKept() {
        assertFalse(StreamSound.lost(100, 100 - StreamSound.GRACE, true));
        assertFalse(StreamSound.lost(10_000, 100, true), "however long it has played");
    }

    @Test
    void aLoopJustStartedIsNotJudgedYet() {
        assertFalse(StreamSound.lost(100, 100, false), "the same tick");
        assertFalse(StreamSound.lost(100, 100 - StreamSound.GRACE + 1, false), "a tick short of the grace period");
        assertFalse(StreamSound.lost(100, 130, false), "a loop dated after now (the clock was set back) is not judged either");
    }
}
