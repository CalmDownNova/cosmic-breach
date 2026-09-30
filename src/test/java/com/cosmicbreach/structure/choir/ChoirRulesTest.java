package com.cosmicbreach.structure.choir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ring's geometry: petals from radius 2.5 to 6.5, seams between them, the turn, and the timing helpers. */
class ChoirRulesTest {
    @Test
    void eachPetalCentreIsItsSlot() {
        for (int slot = 0; slot < 8; slot++) {
            double a = ChoirRules.slotAngle(slot);
            for (double r = 2.6; r <= 6.4; r += 0.5) {
                assertEquals(slot, ChoirRules.slotAt(Math.cos(a) * r, Math.sin(a) * r), "slot " + slot + " at r " + r);
            }
        }
    }

    @Test
    void seamsTheInnerDiscAndOutsideAreNoPad() {
        assertEquals(-1, ChoirRules.slotAt(0, 0));
        assertEquals(-1, ChoirRules.slotAt(1.5, 1.0), "the inner disc");
        assertEquals(-1, ChoirRules.slotAt(7.0, 0), "outside the ring");
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            assertEquals(-1, ChoirRules.slotAt(Math.cos(a) * 4.5, Math.sin(a) * 4.5), "the seam at " + (22.5 + 45 * k));
        }
    }

    @Test
    void theSeamIsHalfABlockWide() {
        double a = Math.toRadians(22.5);
        double r = 5.0;
        // a point 0.3 blocks across the seam line, into slot 0's side
        double x = Math.cos(a) * r + Math.sin(a) * 0.3;
        double z = Math.sin(a) * r - Math.cos(a) * 0.3;
        assertEquals(0, ChoirRules.slotAt(x, z));
        double x2 = Math.cos(a) * r + Math.sin(a) * 0.2;
        double z2 = Math.sin(a) * r - Math.cos(a) * 0.2;
        assertEquals(-1, ChoirRules.slotAt(x2, z2));
    }

    @Test
    void neighboursAreCloseAndAcrossIsFar() {
        assertEquals(1, ChoirRules.ringDistance(0, 1));
        assertEquals(1, ChoirRules.ringDistance(7, 0), "the ring wraps: F#5 sits beside D4");
        assertEquals(4, ChoirRules.ringDistance(1, 5));
        assertEquals(3, ChoirRules.ringDistance(0, 5));
        assertEquals(ChoirRules.BEAT, ChoirRules.gapFor(2));
        assertEquals(2 * ChoirRules.BEAT, ChoirRules.gapFor(3), "a leap across the ring gets two beats");
    }

    @Test
    void theTurnMovesEveryPadOneSlot() {
        for (int pad = 0; pad < 8; pad++) {
            double a = ChoirRules.slotAngle(ChoirRules.slotOf(pad, 3));
            assertEquals(pad, ChoirRules.padAt(Math.cos(a) * 4.5, Math.sin(a) * 4.5, 3));
        }
    }

    @Test
    void beatsAndGrace() {
        assertEquals(12, ChoirRules.nextBeat(1));
        assertEquals(12, ChoirRules.nextBeat(12));
        assertEquals(0, ChoirRules.nextBeat(0));
        assertTrue(ChoirRules.ordinal(2).equals("third"));
    }

    /** Every petal is walkable: a neighbour's nearest point is less than a block and a half away from the inner edge. */
    @Test
    void neighboursMeetAtTheInnerDisc() {
        double a0 = ChoirRules.slotAngle(0) + Math.toRadians(15);
        double a1 = ChoirRules.slotAngle(1) - Math.toRadians(15);
        double r = 2.8;
        double gap = Math.hypot(Math.cos(a0) * r - Math.cos(a1) * r, Math.sin(a0) * r - Math.sin(a1) * r);
        assertEquals(0, ChoirRules.slotAt(Math.cos(a0) * r, Math.sin(a0) * r));
        assertEquals(1, ChoirRules.slotAt(Math.cos(a1) * r, Math.sin(a1) * r));
        assertTrue(gap < 1.5, "gap " + gap);
    }
}
