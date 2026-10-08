package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * A line starts its words only after the boss's own loud sound (its wake, a phase) has finished, plus the margin; one that
 * would then run past its window is skipped, never forced in (and an attack never waits for one).
 */
class VoiceDirectorEffectsTest {
    static final Context SOLO = Context.of(1, false, 3);

    static VoiceLine line(int order, String id, String trigger, int priority, int length, int lead, int wordsEnd) {
        VoiceLine.Variant take = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/test_" + id),
                "subtitles.test." + id, length, lead, wordsEnd, List.of());
        return new VoiceLine("leviathan", id, Trigger.parse(trigger), List.of(), priority, 0, "Words.", Map.of(VoiceLine.ALL, take), order);
    }

    static VoiceDirector director(VoiceLine... lines) {
        return new VoiceDirector(new BossCatalog("leviathan", 0, 240, 0xFFFFFF, new GearCheck.Reference(1, 15, 4, 8, 10, 14, 16), List.of(lines)),
                new HashMap<>());
    }

    /** A gate whose boss's own sound is over at tick {@code clear} (words may start from it). */
    static VoiceDirector.Gate gate(long clear, int quiet) {
        return new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return true;
            }

            @Override
            public int quietTicks(long now) {
                return quiet;
            }

            @Override
            public String living() {
                return VoiceLine.ALL;
            }

            @Override
            public int masks() {
                return 0;
            }

            @Override
            public long wordsMayStartAt(long now) {
                return clear;
            }
        };
    }

    static List<Object[]> run(VoiceDirector d, long from, long to, VoiceDirector.Gate gate) {
        List<Object[]> out = new ArrayList<>();
        for (long t = from; t <= to; t++) {
            VoiceDirector.Start s = d.next(t, gate);
            if (s != null) {
                out.add(new Object[] {s.line().id(), t, s.cut()});
            }
        }
        return out;
    }

    @Test
    void aPlacedOpenerHoldsBackUntilTheWakeSoundIsOverAndItsWordsStartThen() {
        VoiceDirector d = director(line(0, "open", "fight_start", 90, 135, 3, 99));
        d.trigger(Trigger.parse("fight_start"), SOLO, 34, 40L);
        List<Object[]> said = run(d, 34, 300, gate(106, Integer.MAX_VALUE));
        assertEquals(1, said.size());
        assertEquals(103L, said.get(0)[1], "the take starts 3 ticks before the first word at 106");
    }

    @Test
    void anOpenerWhoseWordsWouldEndPastItsWindowIsSkippedNotPlayedLate() {
        VoiceDirector d = director(line(0, "open", "fight_start", 90, 135, 3, 99));
        d.trigger(Trigger.parse("fight_start"), SOLO, 34, 40L, 200L);
        assertTrue(run(d, 34, 300, gate(106, Integer.MAX_VALUE)).isEmpty(), "106 + 96 words is 202, past its 200");
        assertTrue(d.pending().isEmpty(), "dropped, not waiting");
        VoiceDirector fits = director(line(0, "open", "fight_start", 90, 135, 3, 99));
        fits.trigger(Trigger.parse("fight_start"), SOLO, 34, 40L, 202L);
        assertEquals(1, run(fits, 34, 300, gate(106, Integer.MAX_VALUE)).size());
    }

    @Test
    void theKillLineDoesNotWaitForASoundItStartsOnItsCue() {
        // a Colossus cut down within a second of its Shatter: the Shatter's sound is over at tick 150, the kill's words are due at 105
        // and must end by 160 (the rewards and the guide follow), so the kill speaks on its cue, over the tail of the sound
        VoiceDirector d = director(line(0, "kill", "boss_kill", 100, 69, 3, 56));
        d.trigger(Trigger.parse("boss_kill"), SOLO, 100, 105L);
        List<Object[]> said = run(d, 100, 300, gate(150, Integer.MAX_VALUE));
        assertEquals(1, said.size());
        assertEquals(102L, said.get(0)[1], "its first word on its cue at 105, the take 3 ticks earlier");
    }

    @Test
    void aBossMayAskForAShorterMarginBeforeWhatItMustNotCover() {
        // words end at tick 40 of the take: with the usual margin of 6 the quiet window must be 46, with the Colossus's 2 it is 42
        VoiceDirector.Gate quiet42 = new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return true;
            }

            @Override
            public int quietTicks(long now) {
                return 42;
            }

            @Override
            public int marginTicks() {
                return 2;
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
        VoiceDirector d = director(line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        d.trigger(Trigger.parse("weapon:bow"), SOLO, 100, false);
        assertEquals(1, run(d, 100, 100, quiet42).size(), "42 quiet ticks cover 40 of words and a margin of 2");
        VoiceDirector usual = director(line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        usual.trigger(Trigger.parse("weapon:bow"), SOLO, 100, false);
        assertTrue(run(usual, 100, 100, gate(Long.MIN_VALUE, 42)).isEmpty(), "with the usual margin of 6 they do not");
    }

    @Test
    void aFreeLineWaitsForTheSoundToo() {
        VoiceDirector d = director(line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        d.trigger(Trigger.parse("weapon:bow"), SOLO, 100, false);
        List<Object[]> said = run(d, 100, 400, gate(150, Integer.MAX_VALUE));
        assertEquals(147L, said.get(0)[1], "its first word at 150, the take 3 ticks earlier");
    }

    @Test
    void aLineThatWouldWaitPastItsFreshnessIsDroppedLikeAnyOther() {
        VoiceDirector d = director(line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        d.trigger(Trigger.parse("weapon:bow"), SOLO, 100, false);
        assertTrue(run(d, 100, 1000, gate(900, Integer.MAX_VALUE)).isEmpty());
    }

    @Test
    void noSoundMeansNoWait() {
        VoiceDirector d = director(line(0, "open", "fight_start", 90, 135, 3, 99));
        d.trigger(Trigger.parse("fight_start"), SOLO, 34, 40L);
        assertEquals(37L, run(d, 34, 300, VoiceDirector.Gate.OPEN).get(0)[1]);
    }

    @Test
    void theKillClosesTheFightEvenWhenNoLineAnswersIt() {
        VoiceDirector d = director(line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        d.trigger(Trigger.parse("weapon:bow"), SOLO, 100, false);
        assertNull(d.trigger(Trigger.parse("boss_kill"), SOLO, 101, true));
        assertTrue(d.closed());
        assertTrue(d.pending().isEmpty(), "what waited for its turn is dropped");
        assertTrue(run(d, 101, 400, VoiceDirector.Gate.OPEN).isEmpty());
    }
}
