package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.leviathan.Moorage;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Leviathan's opening line at her first Moorage (A5 follow-up): her song fills the intro, so the opener comes where she has
 * a window long enough, swimming in to the coil and in the first pause of the coil, raised free (once a fight, no attack waits
 * for it), and her Moorage line takes the next quiet window of the coil.
 */
class LeviathanOpenerWindowTest {
    static final Context SOLO = Context.of(1, false, 3);

    @Test
    void theSwimInIsQuietFromTheSwellOnUntilTheFirstRipple() {
        assertEquals(0, Moorage.swimInQuietTicks(10, 90), "the swell sounds in the first 30 ticks");
        assertEquals(144, Moorage.swimInQuietTicks(30, 80), "80 ticks of swimming left, then 64 of coil before the first ripple");
        assertEquals(114, Moorage.swimInQuietTicks(60, 50));
    }

    @Test
    void theCoilIsQuietBetweenItsRipples() {
        assertEquals(64, Moorage.coilQuietTicks(0), "the first ripple is told at tick 64");
        assertEquals(1, Moorage.coilQuietTicks(63));
        assertEquals(0, Moorage.coilQuietTicks(64));
        assertEquals(0, Moorage.coilQuietTicks(80), "the shudder");
        assertEquals(63, Moorage.coilQuietTicks(81), "then 63 quiet ticks to the next ripple");
    }

    @Test
    void theOpenersFitTheSwimInOnlyIfItIsLongEnough() {
        // opener words end at 99, 89 and 71 ticks into their takes; the swim in must leave that and the margin
        assertTrue(Moorage.swimInFits(110, 99));
        assertTrue(Moorage.swimInFits(71, 99), "30 ticks of swell, 41 of swimming, 64 of coil: 105 quiet ticks");
        assertFalse(Moorage.swimInFits(70, 99));
        assertTrue(Moorage.swimInFits(61, 89));
        assertTrue(Moorage.swimInFits(43, 71));
        assertFalse(Moorage.swimInFits(42, 71));
    }

    /** The first Moorage's quiet ticks as the boss reports them: the swell, the swim in to tick {@code coilAt}, then the coil. */
    static int quiet(long t, int coilAt) {
        if (t < coilAt) {
            return Moorage.swimInQuietTicks(t, coilAt - t);
        }
        return Moorage.coilQuietTicks((int) (t - coilAt));
    }

    static VoiceDirector.Gate gate(int coilAt) {
        return new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return true;
            }

            @Override
            public int quietTicks(long now) {
                return quiet(now, coilAt);
            }

            @Override
            public String living() {
                return VoiceLine.ALL;
            }

            @Override
            public int masks() {
                return 0;
            }
        };
    }

    @Test
    void theOpenerPlaysInTheSwimInAndTheMoorageLineInTheNextWindowOfTheCoil() {
        VoiceDirector d = VoiceDirectorEffectsTest.director(
                VoiceDirectorEffectsTest.line(0, "open", "fight_start", 90, 135, 3, 99),
                VoiceDirectorEffectsTest.line(1, "moor", "hp_threshold:50", 95, 89, 2, 53));
        int coilAt = 110;
        // the Moorage begins at tick 0: the opener is raised free; the Moorage line is raised when the coil settles
        d.trigger(Trigger.parse("fight_start"), SOLO, 0, false);
        List<Object[]> said = new java.util.ArrayList<>();
        for (long t = 0; t < 400; t++) {
            if (t == coilAt) {
                d.trigger(Trigger.parse("hp_threshold:50"), SOLO, t, false);
            }
            VoiceDirector.Start s = d.next(t, gate(coilAt));
            if (s != null) {
                said.add(new Object[] {s.line().id(), t, s.variant().speechStartTicks(), s.variant().speechTicks()});
            }
        }
        assertEquals(2, said.size());
        assertEquals("open", said.get(0)[0]);
        assertEquals(30L, said.get(0)[1], "as soon as the swell is over and the window covers its words");
        assertEquals("moor", said.get(1)[0]);
        long start = (Long) said.get(1)[1];
        assertTrue(start >= 30 + 135, "after the opener's take is over");
        // its words must end before the first ripple (the coil's tick 64) or the one after it (144), with the margin
        for (long t = start; t <= start + 53; t++) {
            assertTrue(t < coilAt || Moorage.coilQuietTicks((int) (t - coilAt)) > 0, "no word over a ripple at tick " + t);
        }
    }

    @Test
    void anOpenerTheSwimInCannotHostIsDroppedAfterItsWaitAndNeverPlaysLate() {
        // a swim in of 60 ticks leaves 94 quiet ticks: the opener (105 needed) never fits; it is raised free and is dropped after 240
        VoiceDirector d = VoiceDirectorEffectsTest.director(VoiceDirectorEffectsTest.line(0, "open", "fight_start", 90, 135, 3, 99));
        d.trigger(Trigger.parse("fight_start"), SOLO, 0, false);
        for (long t = 0; t < 400; t++) {
            assertEquals(null, d.next(t, gate(60)), "tick " + t);
        }
        assertTrue(d.pending().isEmpty(), "dropped, not waiting for some later window");
    }

    @Test
    void aFreeOpenerHasItsFreshnessLikeAnyOtherFreeLine() {
        VoiceDirector never = new VoiceDirector(new BossCatalog("leviathan", 0, 240, 0xFFFFFF, new GearCheck.Reference(1, 15, 4, 8, 10, 14, 16),
                List.of(VoiceDirectorEffectsTest.line(0, "open", "fight_start", 90, 135, 3, 99))), new HashMap<>());
        never.trigger(Trigger.parse("fight_start"), SOLO, 1000, false);
        assertEquals(null, never.next(1100, VoiceDirectorEffectsTest.gate(Long.MIN_VALUE, 10)));
        assertEquals(1, never.pending().size(), "still waiting inside its wait");
        never.next(1241, VoiceDirectorEffectsTest.gate(Long.MIN_VALUE, 10));
        assertTrue(never.pending().isEmpty(), "dropped once its 240 ticks are over");
    }
}
