package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The voice director's rules (Aetheria 1.1 Voice Script v1, "Director rules"): one line at a time, the global gap from one
 * end to the next start, the kill and phase lines cutting in, priority then script order, freshness, once a fight or a
 * cooldown that outlives the fight, the boss's gate and placed lines, takes by the living masks, and the kill closing the
 * fight.
 */
class VoiceDirectorTest {
    static final GearCheck.Reference REF = new GearCheck.Reference(1, 15, 4, 8, 10, 14, 16);
    static final Context SOLO = Context.of(1, false, 3);

    /** A line with one take of {@code length} ticks whose words fill it. */
    static VoiceLine line(int order, String id, String trigger, int priority, int cooldown, int length, String condition) {
        VoiceLine.Variant take = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/test_" + id),
                "subtitles.test." + id, length, 0, length, List.of());
        return new VoiceLine("colossus", id, Trigger.parse(trigger), Condition.parseAll(condition), priority, cooldown, "Words.",
                Map.of(VoiceLine.ALL, take), order);
    }

    static BossCatalog catalog(int gapSeconds, int waitSeconds, VoiceLine... lines) {
        return new BossCatalog("colossus", gapSeconds * 20, waitSeconds * 20, 0xFFFFFF, REF, List.of(lines));
    }

    static VoiceDirector director(BossCatalog c) {
        return new VoiceDirector(c, new HashMap<>());
    }

    /** A gate with fixed answers. */
    static VoiceDirector.Gate gate(boolean mayStart, int quiet, String living, int masks) {
        return new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return mayStart;
            }

            @Override
            public int quietTicks(long now) {
                return quiet;
            }

            @Override
            public String living() {
                return living;
            }

            @Override
            public int masks() {
                return masks;
            }
        };
    }

    /** Lines started from {@code from} to {@code to}: {id, tick, cut}. */
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

    static List<Object[]> run(VoiceDirector d, long from, long to) {
        return run(d, from, to, VoiceDirector.Gate.OPEN);
    }

    static Trigger t(String text) {
        return Trigger.parse(text);
    }

    @Test
    void oneAtATimeAndTheGlobalGapRunsFromTheEndForLesserLines() {
        VoiceDirector d = director(catalog(10, 30, line(0, "open", "fight_start", 90, 0, 60, "none"),
                line(1, "bow", "weapon:bow", 50, 0, 40, "none"), line(2, "phase", "hp_threshold:50", 95, 0, 30, "none")));
        d.trigger(t("fight_start"), SOLO, 0, true);
        d.trigger(t("weapon:bow"), SOLO, 0, false);
        List<Object[]> said = run(d, 0, 400);
        assertEquals("open", said.get(0)[0]);
        assertEquals(0L, said.get(0)[1]);
        assertEquals("bow", said.get(1)[0]);
        assertEquals(60L + 200L, said.get(1)[1], "a line under 90 waits the gap after the last line ended");
        d.trigger(t("hp_threshold:50"), SOLO, 310, true);
        List<Object[]> phase = run(d, 310, 400);
        assertEquals(300L, (long) said.get(1)[1] + 40, "the bow line ends at 300");
        assertEquals(310L, phase.get(0)[1], "90 and up ignore the gap once nothing plays");
    }

    @Test
    void ninetyAndUpNeverOverlapUnlessTheyMayCut() {
        VoiceDirector d = director(catalog(0, 30, line(0, "open", "fight_start", 90, 0, 100, "none"),
                line(1, "urgent", "player_fell", 90, 0, 20, "none")));
        d.trigger(t("fight_start"), SOLO, 0, true);
        run(d, 0, 0);
        d.trigger(t("player_fell"), SOLO, 10, true);
        List<Object[]> said = run(d, 10, 200);
        assertEquals(100L, said.get(0)[1], "a 90 waits for the playing line's end");
        assertEquals(false, said.get(0)[2]);
    }

    @Test
    void theKillCutsAnythingAndClosesTheFight() {
        VoiceDirector d = director(catalog(10, 30, line(0, "open", "fight_start", 90, 0, 200, "none"),
                line(1, "low", "player_low_health", 60, 0, 40, "none"), line(2, "kill", "boss_kill", 100, 0, 50, "none")));
        d.trigger(t("fight_start"), SOLO, 0, true);
        run(d, 0, 4);
        d.trigger(t("player_low_health"), SOLO, 5, false);
        assertTrue(run(d, 5, 19).isEmpty(), "the opener plays on");
        d.trigger(t("boss_kill"), SOLO, 20, true);
        List<Object[]> said = run(d, 20, 600);
        assertEquals(1, said.size(), "the kill dropped the waiting line");
        assertEquals("kill", said.get(0)[0]);
        assertEquals(20L, said.get(0)[1]);
        assertEquals(true, said.get(0)[2], "a cut");
        assertTrue(d.closed());
        assertNull(d.trigger(t("player_low_health"), SOLO, 100, false), "nothing new after the kill");
    }

    @Test
    void aPhaseLineCutsOnlyAMinorLineWithWordsLeft() {
        VoiceLine minor = line(0, "minor", "player_low_health", 55, 0, 100, "none");
        VoiceLine phase = line(1, "phase", "hp_threshold:50", 95, 0, 30, "none");
        VoiceDirector d = director(catalog(0, 30, minor, phase));
        d.trigger(t("player_low_health"), SOLO, 0, false);
        run(d, 0, 0);
        d.trigger(t("hp_threshold:50"), SOLO, 10, true);
        List<Object[]> cut = run(d, 10, 10);
        assertEquals("phase", cut.get(0)[0]);
        assertEquals(true, cut.get(0)[2], "90 ticks of words left: cut");

        VoiceDirector late = director(catalog(0, 30, minor, phase));
        late.trigger(t("player_low_health"), SOLO, 0, false);
        run(late, 0, 0);
        late.trigger(t("hp_threshold:50"), SOLO, 80, true);
        List<Object[]> waited = run(late, 80, 200);
        assertEquals(100L, waited.get(0)[1], "20 ticks left: it waits for the end");
        assertEquals(false, waited.get(0)[2]);

        VoiceLine notMinor = line(0, "taunt", "player_low_health", 60, 0, 100, "none");
        VoiceDirector d60 = director(catalog(0, 30, notMinor, phase));
        d60.trigger(t("player_low_health"), SOLO, 0, false);
        run(d60, 0, 0);
        d60.trigger(t("hp_threshold:50"), SOLO, 10, true);
        assertEquals(100L, run(d60, 10, 200).get(0)[1], "60 is not below 60: no cut");
    }

    @Test
    void theHighestPriorityThenTheScriptOrderAndEachTriggerQueuesOneLine() {
        VoiceDirector d = director(catalog(0, 30, line(0, "alone_now", "player_death", 75, 0, 20, "players:group, last_alive"),
                line(1, "player_down", "player_death", 70, 0, 20, "none"), line(2, "one_less", "player_death", 70, 0, 20, "none")));
        Context group = Context.of(3, false, 0);
        assertEquals("player_down", d.trigger(t("player_death"), group, 0, false).id(), "not the last alive: the first 70 row");
        assertEquals("one_less", d.trigger(t("player_death"), group, 1, false).id(), "a second death while it waits: the next row");
        assertEquals("alone_now", d.trigger(t("player_death"), group.withLastAlive(true), 2, false).id(), "75 wins when it fits");
        List<Object[]> said = run(d, 2, 200);
        assertEquals(List.of("alone_now", "player_down", "one_less"), said.stream().map(s -> (String) s[0]).toList());
        assertNull(d.trigger(t("player_death"), group, 300, false), "each once a fight");
    }

    @Test
    void staleLinesAreDroppedAndTheOpenerStartsOnItsTickOrNever() {
        VoiceDirector d = director(catalog(10, 4, line(0, "talk", "player_fell", 50, 0, 40, "none"),
                line(1, "bow", "weapon:bow", 50, 0, 20, "none"), line(2, "open", "fight_start", 90, 0, 20, "none"),
                line(3, "gear", "gear:over", 50, 0, 20, "none")));
        d.trigger(t("player_fell"), SOLO, 0, false);
        run(d, 0, 0);
        d.trigger(t("weapon:bow"), SOLO, 41, false);
        d.trigger(t("gear:over"), SOLO, 41, false);
        List<Object[]> said = run(d, 41, 600);
        assertEquals(1, said.size(), "the bow line waited past its 4 s and was dropped; the gear verdict waits 20 s");
        assertEquals("gear", said.get(0)[0]);
        assertEquals(240L, said.get(0)[1]);

        VoiceDirector busy = director(catalog(0, 4, line(0, "talk", "player_fell", 95, 0, 40, "none"),
                line(1, "open", "fight_start", 90, 0, 20, "none")));
        busy.trigger(t("player_fell"), SOLO, 0, false);
        run(busy, 0, 0);
        busy.trigger(t("fight_start"), SOLO, 10, true);
        assertTrue(run(busy, 10, 200).isEmpty(), "an opener that cannot start on its tick is never played late");
    }

    @Test
    void onceAFightButACooldownLineComesBackAfterItsCooldownFromItsEnd() {
        Map<String, Long> lair = new HashMap<>();
        BossCatalog c = catalog(0, 30, line(0, "again", "taunt", 95, 30, 40, "on:nova_return"), line(1, "once", "player_fell", 50, 0, 20, "none"));
        VoiceDirector d = new VoiceDirector(c, lair);
        Context nova = SOLO.withEvent("nova_return");
        d.trigger(t("taunt"), nova, 0, true);
        assertEquals(1, run(d, 0, 0).size());
        assertNull(d.trigger(t("taunt"), nova, 600, true), "600 ticks after its end at 40: still cooling");
        assertEquals("again", d.trigger(t("taunt"), nova, 640, true).id(), "30 s after its end");
        run(d, 640, 699);
        d.trigger(t("player_fell"), SOLO, 700, false);
        assertEquals("once", run(d, 700, 800).get(0)[0]);
        assertNull(d.trigger(t("player_fell"), SOLO, 900, false), "once a fight");

        VoiceDirector nextFight = new VoiceDirector(c, lair);
        assertEquals("once", nextFight.trigger(t("player_fell"), SOLO, 1000, false).id(), "a new fight says it again");
        assertNull(nextFight.trigger(t("taunt"), nova, 1000, true), "the lair remembers the cooldown");
    }

    @Test
    void theGateHoldsLinesUntilTheBossAllowsAndAQuietWindowFitsTheWords() {
        VoiceDirector d = director(catalog(0, 12, line(0, "gap", "attack_gap", 25, 0, 48, "none")));
        d.trigger(t("attack_gap"), SOLO, 0, false);
        assertTrue(run(d, 0, 50, gate(true, VoiceDirector.QUIET_MARGIN + 47, "all", 0)).isEmpty(), "48 ticks of words need the margin more quiet");
        assertEquals(51L, run(d, 51, 51, gate(true, VoiceDirector.QUIET_MARGIN + 48, "all", 0)).get(0)[1]);

        VoiceDirector beat = director(catalog(0, 8, line(0, "breathe", "player_low_health", 60, 0, 20, "none")));
        beat.trigger(t("player_low_health"), SOLO, 0, false);
        assertTrue(run(beat, 0, 10, gate(false, 1000, "all", 0)).isEmpty(), "off the beat");
        assertEquals(11L, run(beat, 11, 11, gate(true, 1000, "all", 0)).get(0)[1]);
    }

    @Test
    void aPlacedLineSkipsTheGateOnItsOwnTickOnly() {
        VoiceDirector d = director(catalog(0, 8, line(0, "kill", "boss_kill", 100, 0, 20, "none")));
        d.trigger(t("boss_kill"), SOLO, 5, true);
        assertEquals(5L, run(d, 5, 5, gate(false, 0, "all", 0)).get(0)[1], "on its tick, past a closed gate");

        VoiceDirector late = director(catalog(10, 8, line(0, "busy", "player_fell", 95, 0, 40, "none"),
                line(1, "rise", "taunt", 60, 0, 20, "on:break")));
        late.trigger(t("player_fell"), SOLO, 0, false);
        run(late, 0, 0);
        late.trigger(t("taunt"), SOLO.withEvent("break"), 30, true);
        assertTrue(run(late, 30, 240, gate(false, 1000, "all", 0)).isEmpty(), "once its tick has passed the gate applies");
    }

    @Test
    void theTakeFollowsTheLivingMasksAndMaskLinesNeedTheMasks() {
        VoiceLine.Variant twelve = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/u_12"), "s.12", 40, 2, 30, List.of());
        VoiceLine.Variant thirteen = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/u_13"), "s.13", 44, 2, 30, List.of());
        VoiceLine gone = new VoiceLine("unsung", "one_gone", t("hp_threshold:67"), List.of(), 95, 0, "Who will sing its part now?",
                Map.of("12", twelve, "13", thirteen), 0);
        VoiceLine breathe = line(1, "breathe", "player_low_health", 60, 0, 20, "health<=25%, masks:3");
        VoiceDirector d = new VoiceDirector(new BossCatalog("unsung", 0, 160, 0xFFFFFF, REF, List.of(gone, breathe)), new HashMap<>());
        d.trigger(t("hp_threshold:67"), Context.of(1, false, 2), 0, true);
        VoiceDirector.Start s = d.next(0, gate(true, 1000, "13", 2));
        assertEquals("13", s.variantKey());
        assertEquals(44, s.variant().lengthTicks());
        d.trigger(t("player_low_health"), SOLO.withHealth(0.2), 100, false);
        assertTrue(run(d, 100, 300, gate(true, 1000, "13", 2)).isEmpty(), "a mask broke while it waited");
        assertNull(d.trigger(t("player_low_health"), Context.of(1, false, 2).withHealth(0.2), 400, false), "masks:3 fails with two");
    }

    @Test
    void quietMeansNothingPlaysOrWaitsAndTheGapHasRun() {
        VoiceDirector d = director(catalog(10, 30, line(0, "open", "fight_start", 90, 0, 60, "none")));
        assertTrue(d.quiet(0));
        d.trigger(t("fight_start"), SOLO, 0, true);
        assertFalse(d.quiet(0));
        run(d, 0, 0);
        assertFalse(d.quiet(100));
        assertFalse(d.quiet(259));
        assertTrue(d.quiet(260));
    }

    /** A line whose words start {@code lead} ticks into its take and end at {@code wordsEnd}. */
    static VoiceLine placedLine(int order, String id, String trigger, int priority, int length, int lead, int wordsEnd, String condition) {
        VoiceLine.Variant take = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/test_" + id),
                "subtitles.test." + id, length, lead, wordsEnd, List.of());
        return new VoiceLine("leviathan", id, Trigger.parse(trigger), Condition.parseAll(condition), priority, 0, "Words.",
                Map.of(VoiceLine.ALL, take), order);
    }

    @Test
    void aPlacedLineStartsItsTakeEarlyEnoughThatTheWordsLandOnTheCue() {
        VoiceDirector d = director(catalog(0, 30, placedLine(0, "kill", "boss_kill", 100, 69, 3, 56, "none")));
        d.trigger(t("boss_kill"), SOLO, 100, 105L);
        List<Object[]> said = run(d, 100, 110);
        assertEquals(1, said.size());
        assertEquals(102L, said.get(0)[1], "the take starts 3 ticks before the cue, so its first word is on tick 105");
        assertEquals(false, said.get(0)[2], "nothing to cut");
        assertEquals(105L + 53L, d.playing().wordsEnd(), "the words end 53 ticks after the cue");

        VoiceDirector late = director(catalog(0, 30, placedLine(0, "kill", "boss_kill", 100, 69, 3, 56, "none")));
        late.trigger(t("boss_kill"), SOLO, 100, 101L);
        assertEquals(100L, run(late, 100, 110).get(0)[1], "the lead is shorter than the take's: it starts at once, its words a little late");
    }

    @Test
    void aMinorLineMayNotTakeTheSlotAPlacedLineIsAboutToStartIn() {
        VoiceDirector d = director(catalog(0, 30, placedLine(0, "open", "fight_start", 90, 80, 3, 70, "none"),
                line(1, "bow", "weapon:bow", 50, 0, 60, "none")));
        d.trigger(t("weapon:bow"), SOLO, 100, false);
        d.trigger(t("fight_start"), SOLO, 100, 105L);
        List<Object[]> said = run(d, 100, 300);
        assertEquals("open", said.get(0)[0], "the opener holds its slot though the bow line could start first");
        assertEquals(102L, said.get(0)[1]);
        assertEquals("bow", said.get(1)[0]);
        assertEquals(102L + 80L, said.get(1)[1], "the bow line follows when the opener's take has ended (gap 0)");
    }

    @Test
    void anOpenerThatMissesItsTickIsDroppedEvenIfTheTakeIsLongerThanTheLead() {
        VoiceDirector d = director(catalog(0, 30, placedLine(0, "talk", "player_fell", 95, 40, 0, 40, "none"),
                placedLine(1, "open", "fight_start", 90, 80, 3, 70, "none")));
        d.trigger(t("player_fell"), SOLO, 100, false);
        run(d, 100, 100);
        d.trigger(t("fight_start"), SOLO, 100, 105L);
        assertTrue(run(d, 101, 400).isEmpty(), "the other line plays to 140; the opener's tick 102 has passed");
        assertTrue(d.pending().isEmpty());
    }

    @Test
    void aLongLineNeedsALongWindowAndAGapLineFitsTheGuaranteedGap() {
        VoiceLine longLine = placedLine(0, "moor", "hp_threshold:50", 95, 89, 2, 53, "none");
        VoiceLine gapLine = placedLine(1, "cousins", "taunt", 30, 83, 3, 43, "on:break");
        VoiceDirector inGap = director(catalog(0, 12, longLine, gapLine));
        inGap.trigger(t("hp_threshold:50"), SOLO, 0, false);
        inGap.trigger(t("taunt"), SOLO.withEvent("break"), 0, false);
        List<Object[]> said = run(inGap, 0, 20, gate(true, 50, "all", 0));
        assertEquals(1, said.size(), "50 ticks fit the gap line (words end at 43) and not the long one (53)");
        assertEquals("cousins", said.get(0)[0]);

        VoiceDirector coil = director(catalog(0, 12, longLine, gapLine));
        coil.trigger(t("hp_threshold:50"), SOLO, 0, false);
        List<Object[]> long64 = run(coil, 0, 5, gate(true, 64, "all", 0));
        assertEquals("moor", long64.get(0)[0], "a coil's 64 quiet ticks fit it");
    }

    @Test
    void nothingStartsInAGapTooShortForItsWordsAndTheLineIsDroppedAtItsWait() {
        VoiceDirector d = director(catalog(0, 12, placedLine(0, "cousins", "taunt", 30, 83, 3, 43, "on:break")));
        d.trigger(t("taunt"), SOLO.withEvent("break"), 0, false);
        assertTrue(run(d, 0, 241, gate(true, 42, "all", 0)).isEmpty(), "words end at 43; 42 quiet ticks cover nothing");
        assertTrue(d.pending().isEmpty(), "dropped after its 12 s");
    }
}
