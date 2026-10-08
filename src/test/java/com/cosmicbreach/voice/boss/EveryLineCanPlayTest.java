package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shipped catalogs against the director (A5.2): every line can be reached, with a moment built from its own conditions,
 * and starts within its boss's wait once the fight is quiet; and the script's own director rules hold on the real numbers
 * (the global gaps, the waits, a kill line per boss, one opener per fight). A typo in a condition, a trigger nobody fires
 * or a take too long for its quiet window fails here, before a playtest.
 */
class EveryLineCanPlayTest {
    /** A moment that meets every condition of {@code line}. */
    static Context momentFor(VoiceLine line) {
        return Context.meeting(line);
    }

    /** A gate with no limits at all, for the living masks {@code living}. */
    static VoiceDirector.Gate quiet(String living, int masks) {
        return new VoiceDirector.Gate() {
            @Override
            public boolean mayStart(long now) {
                return true;
            }

            @Override
            public int quietTicks(long now) {
                return Integer.MAX_VALUE;
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

    /** The triggers that reach {@code line}: its own, and for a weapon line the categories its {@code also:} adds. */
    static List<Trigger> triggersOf(VoiceLine line) {
        List<Trigger> out = new ArrayList<>(List.of(line.trigger()));
        for (Condition k : line.conditions()) {
            if (k.kind().equals("also")) {
                out.add(Trigger.parse("weapon:" + k.arg()));
            }
        }
        return out;
    }

    @Test
    void everyLineIsReachedAndStartsAtOnceInAQuietFight() {
        List<String> problems = new ArrayList<>();
        int takes = 0;
        for (String boss : BossCatalog.BOSSES) {
            BossCatalog full = BossCatalog.of(boss);
            for (VoiceLine line : full.lines()) {
                for (Trigger t : triggersOf(line)) {
                    for (String living : line.variants().keySet()) {
                        BossCatalog alone = new BossCatalog(boss, full.globalGapTicks(), full.waitTicks(), full.captionColor(), full.reference(),
                                List.of(line));
                        VoiceDirector d = new VoiceDirector(alone, new HashMap<>());
                        Context c = momentFor(line);
                        String where = boss + "/" + line.id() + " (" + t.key() + ", " + living + ")";
                        if (d.trigger(t, c, 1000, false) == null) {
                            problems.add(where + ": not queued for its own trigger and conditions");
                            continue;
                        }
                        VoiceDirector.Start s = d.next(1000, quiet(living, c.masks()));
                        if (s == null) {
                            problems.add(where + ": queued but never started");
                        } else {
                            takes++;
                            assertEquals(line, s.line());
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(takes >= 75, "every take of every line was started once: " + takes);
    }

    /**
     * With the whole catalog (not the line alone), repeating a line's moment gets it said: lines that share a trigger take
     * their turns (a second death gets the second death line), and no line is shadowed for good by another.
     */
    @Test
    void inTheWholeCatalogEveryLineGetsItsTurn() {
        List<String> problems = new ArrayList<>();
        for (String boss : BossCatalog.BOSSES) {
            BossCatalog full = BossCatalog.of(boss);
            for (VoiceLine line : full.lines()) {
                VoiceDirector d = new VoiceDirector(full, new HashMap<>());
                long now = 1000;
                boolean said = false;
                for (int turn = 0; turn < full.lines().size() + 1 && !said; turn++) {
                    Context c = momentFor(line);
                    d.trigger(line.trigger(), c, now, false);
                    VoiceDirector.Start s = d.next(now, quiet(line.variants().keySet().iterator().next(), c.masks()));
                    said = s != null && s.line() == line;
                    now += 100_000;
                }
                if (!said) {
                    problems.add(boss + "/" + line.id() + ": never the line said for its own moment, however often it comes");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theScriptsNumbersHold() {
        assertEquals(25 * 20, BossCatalog.of("colossus").globalGapTicks());
        assertEquals(15 * 20, BossCatalog.of("leviathan").globalGapTicks());
        assertEquals(20 * 20, BossCatalog.of("unsung").globalGapTicks());
        assertEquals(12 * 20, BossCatalog.of("heliarch").globalGapTicks());
        assertEquals(12 * 20, BossCatalog.of("leviathan").waitTicks(), "the Leviathan waits up to 12 s for a quiet window");
        assertEquals(8 * 20, BossCatalog.of("unsung").waitTicks(), "the Unsung up to 8 s for a beat");
        assertEquals(8 * 20, BossCatalog.of("colossus").waitTicks(), "the Colossus waits up to 8 s for a lull (4 s in the script: too few lines found one)");
        assertEquals(4 * 20, BossCatalog.of("heliarch").waitTicks());
        Map<String, Integer> counts = Map.of("colossus", 14, "leviathan", 14, "unsung", 13, "heliarch", 30);
        for (String boss : BossCatalog.BOSSES) {
            BossCatalog c = BossCatalog.of(boss);
            assertEquals(counts.get(boss), c.lines().size(), boss + " has its scripted lines");
            long kills = c.lines().stream().filter(l -> l.trigger().is(Trigger.BOSS_KILL)).count();
            assertEquals(1, kills, boss + " has one kill line");
            assertTrue(c.lines().stream().filter(l -> l.trigger().is(Trigger.FIGHT_START)).allMatch(l -> l.priority() >= VoiceDirector.URGENT),
                    boss + ": openers ignore the gap");
            assertTrue(c.lines().stream().filter(l -> l.trigger().is(Trigger.FIGHT_START)).count() >= 3,
                    boss + " opens differently for one player, a group and a returning party");
        }
    }

    /** The five Leviathan lines the production tool flags as needing more than a gap: they play only in the intro, a Moorage, a Break or the sink. */
    static final List<String> LONG = List.of("open_solo", "open_group", "open_again", "moor_first", "kill");

    @Test
    void theLeviathansOtherLinesFitHerGuaranteedGap() {
        int checked = 0;
        for (VoiceLine l : BossCatalog.of("leviathan").lines()) {
            boolean isLong = LONG.contains(l.id());
            for (VoiceLine.Variant v : l.variants().values()) {
                int needs = v.speechTicks() + VoiceDirector.QUIET_MARGIN;
                if (isLong) {
                    assertTrue(needs > com.cosmicbreach.guardian.leviathan.LeviathanMoves.GAP, l.id() + " is flagged long, so it does not fit a gap");
                } else {
                    assertTrue(needs <= com.cosmicbreach.guardian.leviathan.LeviathanMoves.GAP,
                            l.id() + " needs " + needs + " quiet ticks, her gap after an attack is " + com.cosmicbreach.guardian.leviathan.LeviathanMoves.GAP);
                    checked++;
                }
            }
        }
        assertEquals(9, checked, "nine lines fit a gap");
    }
}
