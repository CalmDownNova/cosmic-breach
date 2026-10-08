package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A boss's moments placed ahead of time: a placed one is raised {@code LOOKAHEAD} ticks before its cue, a free one on its tick. */
class TriggerScheduleTest {
    static Trigger t(String text) {
        return Trigger.parse(text);
    }

    @Test
    void aPlacedMomentIsRaisedALookaheadBeforeItsCueAndAFreeOneOnItsTick() {
        TriggerSchedule s = new TriggerSchedule();
        s.placed(t("hp_threshold:50"), "", 100);
        s.free(t("taunt"), "break", 100);
        assertTrue(s.due(100 - TriggerSchedule.LOOKAHEAD - 1).isEmpty());
        List<TriggerSchedule.Due> early = s.due(100 - TriggerSchedule.LOOKAHEAD);
        assertEquals(1, early.size());
        assertEquals("hp_threshold:50", early.get(0).trigger().key());
        assertEquals(100L, early.get(0).cue(), "the cue is the tick of the first word");
        assertTrue(s.due(99).isEmpty(), "each is raised once");
        List<TriggerSchedule.Due> free = s.due(100);
        assertEquals(1, free.size());
        assertEquals("break", free.get(0).event());
        assertEquals(VoiceDirector.NOT_PLACED, free.get(0).cue());
        assertTrue(s.pending().isEmpty());
    }

    @Test
    void aMomentWhoseCueIsAlreadyCloseIsRaisedAtOnceAndSeveralComeInOrder() {
        TriggerSchedule s = new TriggerSchedule();
        s.placed(t("boss_kill"), "", 205);
        s.placed(t("fight_start"), "", 300);
        List<TriggerSchedule.Due> now = s.due(200);
        assertEquals(1, now.size());
        assertEquals("boss_kill", now.get(0).trigger().key());
        assertEquals(1, s.pending().size());
        s.clear();
        assertTrue(s.pending().isEmpty());
    }
}
