package com.cosmicbreach.client.combat;

import org.junit.jupiter.api.Test;

import static com.cosmicbreach.client.combat.ButtonEdges.Edge.NONE;
import static com.cosmicbreach.client.combat.ButtonEdges.Edge.PRESS;
import static com.cosmicbreach.client.combat.ButtonEdges.Edge.RELEASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonEdgesTest {

    @Test
    void aTapIsOnePressAndOneRelease() {
        ButtonEdges b = new ButtonEdges();
        assertEquals(NONE, b.update(false, true));
        assertEquals(PRESS, b.update(true, true));
        assertTrue(b.isPressed());
        assertEquals(NONE, b.update(true, true), "holding is not another press");
        assertEquals(RELEASE, b.update(false, true));
        assertFalse(b.isPressed());
        assertEquals(NONE, b.update(false, true));
    }

    @Test
    void aPressThatIsNotAllowedNeverCountsEvenWhenItBecomesAllowed() {
        ButtonEdges b = new ButtonEdges();
        assertEquals(NONE, b.update(true, false), "no weapon in hand, or a screen open");
        assertEquals(NONE, b.update(true, true), "still the same press");
        assertEquals(NONE, b.update(false, true), "nothing went out, so nothing to release");
        assertEquals(PRESS, b.update(true, true), "a fresh press counts");
    }

    @Test
    void theReleaseAlwaysFollowsAPressThatWentOut() {
        ButtonEdges b = new ButtonEdges();
        assertEquals(PRESS, b.update(true, true));
        assertEquals(NONE, b.update(true, false), "swapped away from the weapon while holding");
        assertEquals(RELEASE, b.update(false, false), "the release still goes out");
    }

    @Test
    void resetNeedsAFreshPress() {
        ButtonEdges b = new ButtonEdges();
        assertEquals(PRESS, b.update(true, true));
        b.reset(true);
        assertFalse(b.isPressed());
        assertEquals(NONE, b.update(true, true), "still held from before the reset");
        assertEquals(NONE, b.update(false, true), "the old press is forgotten, so no release");
        assertEquals(PRESS, b.update(true, true));
    }
}
