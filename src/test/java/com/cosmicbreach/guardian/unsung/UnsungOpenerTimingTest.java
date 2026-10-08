package com.cosmicbreach.guardian.unsung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The opener's first word is a beat after the Alto's lift, and waits for the wake sounds: the intro takes one more turn if need be. */
class UnsungOpenerTimingTest {
    static final int OVER = 86;

    @Test
    void theFirstWordComesAfterTheWakeSoundsWhateverTheAwakening() {
        int moved = 0;
        for (long awake = 1000; awake < 1000 + 8 * 96; awake++) {
            long start = UnsungSong.fightStartAfterWake(awake, OVER);
            long firstWord = start - (long) UnsungMoves.INTRO_BEATS * UnsungMoves.BEAT + UnsungMoves.BEAT;
            assertTrue(firstWord >= awake + OVER, "awake " + awake + ": first word at " + (firstWord - awake));
            assertEquals(0, start % UnsungMoves.TURN_TICKS, "still on a line of the song");
            long plain = UnsungSong.fightStart(awake);
            assertTrue(start == plain || start == plain + UnsungMoves.TURN_TICKS, "at most one more turn");
            if (start != plain) {
                moved++;
            }
        }
        assertTrue(moved > 0 && moved < 8 * 96, "some awakenings wait a turn, not all");
    }

    @Test
    void anAwakeningWithTimeToSpareIsLeftAlone() {
        for (long awake = 1000; awake < 1000 + 8 * 96; awake++) {
            long plain = UnsungSong.fightStart(awake);
            long firstWord = plain - (long) UnsungMoves.INTRO_BEATS * UnsungMoves.BEAT + UnsungMoves.BEAT;
            if (firstWord >= awake + OVER) {
                assertEquals(plain, UnsungSong.fightStartAfterWake(awake, OVER));
            }
        }
    }
}
