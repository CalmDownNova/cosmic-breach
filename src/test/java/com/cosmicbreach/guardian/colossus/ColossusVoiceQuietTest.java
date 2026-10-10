package com.cosmicbreach.guardian.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * When the Colossus's voice may speak (A5 and its follow-up): a line may start whenever it will end, with a short margin, before
 * the readable part of the next attack's telegraph begins: the last {@link ColossusMoves#MIN_TELEGRAPH} ticks before the strike (the
 * design's own floor for a telegraph), which a line never covers. {@link ColossusMoves#quietTicks} and
 * {@link ColossusMoves#nextReadableIn} are how long it is quiet at the very least, from the boss's own attack schedule.
 */
class ColossusVoiceQuietTest {
    @Test
    void theShapesAreTheAttacksOwnNumbers() {
        assertEquals(new ColossusMoves.Shape(24, 44, 12), ColossusMoves.SLAM_SHAPE, "the ring is read for the last 12 of its 24 ticks");
        assertEquals(new ColossusMoves.Shape(36, 56, 12), ColossusMoves.DOUBLE_SLAM_SHAPE, "its second ring tells until 12 + 24");
        assertEquals(new ColossusMoves.Shape(30, 50, 18), ColossusMoves.SWEEP_SHAPE);
        assertEquals(new ColossusMoves.Shape(120, 130, 68), ColossusMoves.REFRACTION_SHAPE, "the beams are read from the last 12 ticks of the charge and all through the fire");
        assertEquals(new ColossusMoves.Shape(16, 28, 4), ColossusMoves.BURST_SHAPE);
    }

    @Test
    void thereIsNoQuietInsideTheReadablePartOfATelegraph() {
        for (int t = 12; t < ColossusMoves.SLAM_TELL; t++) {
            assertEquals(0, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, t, 99), "slam tick " + t);
        }
        for (int t = 12; t < 36; t++) {
            assertEquals(0, ColossusMoves.quietTicks(ColossusMoves.DOUBLE_SLAM_SHAPE, t, 99), "double slam tick " + t);
        }
        for (int t = 68; t < 120; t++) {
            assertEquals(0, ColossusMoves.quietTicks(ColossusMoves.REFRACTION_SHAPE, t, 99), "refraction tick " + t);
        }
    }

    @Test
    void theEarlyPartOfATelegraphIsQuietUntilItsReadablePart() {
        assertEquals(12, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, 0, 99), "the fist is still flying out: 12 ticks before the ring");
        assertEquals(1, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, 11, 99));
    }

    @Test
    void afterTheStrikeItIsTheTimeToTheNextReadablePart() {
        assertEquals(60, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, 24, 60));
        assertEquals(0, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, 24, 0));
        assertEquals(0, ColossusMoves.quietTicks(ColossusMoves.SLAM_SHAPE, 24, -5));
        assertEquals(30, ColossusMoves.idleQuietTicks(30));
        assertEquals(0, ColossusMoves.idleQuietTicks(0));
    }

    @Test
    void theNextReadablePartIsTheSoonestOneOfTheAttacksItCouldChooseNow() {
        // the gap ends in 20; a slam is off cooldown in 50 (readable 12 after it starts: 62), the sweep in 0 and open (20 + 18 = 38),
        // the Refraction in 100 (100), the burst in 10 but nobody hugs it
        assertEquals(38, ColossusMoves.nextReadableIn(20, 50, 0, true, 100, true, 10, false));
    }

    @Test
    void anAttackStillOnCooldownLengthensTheWaitAndOnlyOpenAttacksCount() {
        // slam: 12 ticks of fist and ring before it is readable
        assertEquals(30 + 12, ColossusMoves.nextReadableIn(30, 0, 0, false, 0, false, 0, false), "the gap ends in 30, then 12 more");
        assertEquals(50 + 12, ColossusMoves.nextReadableIn(30, 50, 0, false, 0, false, 0, false), "a slam on cooldown for 50");
        assertEquals(30 + 18, ColossusMoves.nextReadableIn(30, 99, 0, true, 0, false, 0, false), "the sweep is open and ready: 18 after it starts");
        assertEquals(30 + 68, ColossusMoves.nextReadableIn(30, 99, 99, false, 0, true, 0, false), "the Refraction is read from the last 12 ticks of its charge");
        assertEquals(30 + 4, ColossusMoves.nextReadableIn(30, 99, 99, false, 99, false, 0, true), "the burst from its fifth tick");
        assertEquals(99 + 12, ColossusMoves.nextReadableIn(30, 99, 0, false, 5, false, 5, false), "attacks it cannot choose do not count");
    }

    @Test
    void theFractureLineHasLittleRoomBeforeTheFirstPhase2Telegraph() {
        // its words run 59 ticks from tick 48 (its sound is over at 42 and the voice adds a margin); the first attack after the
        // Fracture starts at tick 76, a slam is readable from 88: 40 quiet ticks are left, so it waits for a later lull
        assertEquals(40, ColossusMoves.nextReadableIn(ColossusMoves.FRACTURE + ColossusMoves.GAP - 48, 0, 0, false, 99, false, 99, false));
        assertTrue(40 < 62 + ColossusMoves.VOICE_MARGIN);
    }
}
