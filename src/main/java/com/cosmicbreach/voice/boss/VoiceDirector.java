package com.cosmicbreach.voice.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Which line a boss says, and when, for one fight: the voice script's director rules (Aetheria 1.1 Voice Script v1).
 * Pure; times are server ticks.
 *
 * <ol>
 *   <li><b>One line at a time.</b> Nothing starts over a playing line, except as rule 3 allows.</li>
 *   <li><b>The global gap</b> runs from one line's end to the next start; lines of priority {@value #URGENT} and up
 *       ignore it.</li>
 *   <li><b>Preemption.</b> The kill line ({@value #KILL}) cuts any playing line. A line of {@value #PHASE} waits for the
 *       playing line to end, except that it cuts one below {@value #MINOR} whose words still have more than
 *       {@value #CUT_REMAINING} ticks to run. Nothing else interrupts. A cut is faded by the client.</li>
 *   <li><b>Choice.</b> A trigger queues the best line for it that fits the moment and may still play: the highest
 *       priority, then the first in the script's order. Among queued lines that can start, the same order decides.</li>
 *   <li><b>Freshness.</b> A queued line that has not started within the boss's wait (4 s; the Leviathan 12 s, the Unsung
 *       8 s) is dropped; a gear verdict waits up to {@value #GEAR_WAIT} ticks; an opener starts on its cue or never.</li>
 *   <li><b>Repeats.</b> A line plays once a fight unless it has a cooldown; a cooldown line may come back once its
 *       cooldown has run from its last end. Cooldowns are kept per lair ({@code memory}), so a reset and a quick retry
 *       do not replay one early.</li>
 *   <li><b>The boss's own gate</b> ({@link Gate}): a line starts only when the boss allows it (the Unsung on a beat that
 *       is not a downbeat) and only into a quiet window long enough for its words and {@value #QUIET_MARGIN} ticks (the
 *       Leviathan between attacks, the Unsung clear of a Harmonize warning). A line never holds the boss back: its next
 *       attack comes when it comes, and a line that does not fit before it is skipped. A line the boss placed itself (a
 *       phase, the opener, the kill, an event it fires on its own tick) skips the gate on its cue.</li>
 *   <li><b>Placement.</b> A placed line has a cue: the tick its first word should sound. Its take starts
 *       {@code speechStartTicks} earlier (a file leads into its words by 1 to 6 ticks), so the words land on the cue; the
 *       boss raises it a few ticks ahead ({@code BossVoices.LEAD}). A placed line due soon keeps its slot: nothing of
 *       lesser priority may start and still be playing then.</li>
 *   <li><b>Sounds first.</b> A line's first word sounds only after the boss's own wake or phase sound has finished
 *       ({@link Gate#wordsMayStartAt}), so nothing sits under it. A line may carry the last tick its words may end on (an
 *       opener before the first attack's swell, before the fight starts): if waiting for a sound would push it past that,
 *       it is skipped, never forced in and never allowed to hold anything back. The kill line is the exception: it speaks on
 *       its cue whatever has just sounded.</li>
 *   <li><b>The kill closes the fight:</b> everything else queued is dropped and nothing new is taken.</li>
 * </ol>
 */
public final class VoiceDirector {
    public static final int URGENT = 90;
    public static final int PHASE = 95;
    public static final int KILL = 100;
    public static final int MINOR = 60;
    public static final int CUT_REMAINING = 30;
    /**
     * Quiet ticks wanted after a line's last word (0.3 s). The production table counts 10 and the Leviathan's guaranteed gap
     * is 50 ticks: with 10, five of her eight gap lines (their words end at 36 to 43 ticks) could never start in it. Six
     * keeps every one of them inside the gap, with a quarter of a second of quiet before the next attack's swell.
     */
    public static final int QUIET_MARGIN = 6;
    public static final int GEAR_WAIT = 400;
    /** The cue of a trigger the boss did not place: its line starts when the rules allow. */
    public static final long NOT_PLACED = Long.MIN_VALUE;
    /** A line with no window of its own but its freshness. */
    public static final long NO_LIMIT = Long.MAX_VALUE;

    /** What the boss allows right now (one per fight, read every tick). */
    public interface Gate {
        /** True if a line may start on this tick (the Unsung: a beat that is not a downbeat). */
        boolean mayStart(long now);

        /** Ticks from now with nothing the voice must not cover (an attack's swell, a Harmonize warning). */
        int quietTicks(long now);

        /**
         * The first tick a line's first word may sound: after the boss's own loud sound (its wake, a phase) has finished, plus
         * a margin. {@code Long.MIN_VALUE}: nothing to wait for.
         */
        default long wordsMayStartAt(long now) {
            return Long.MIN_VALUE;
        }

        /** Quiet ticks wanted after a line's last word, in the boss's quiet window ({@value VoiceDirector#QUIET_MARGIN} unless it says). */
        default int marginTicks() {
            return QUIET_MARGIN;
        }

        /** The living masks, for the take of a line with one per set ({@code "13"}), else {@link VoiceLine#ALL}. */
        String living();

        /** Masks still whole (0 for a boss without masks). */
        int masks();

        /** No limits: a boss that may speak at any moment. */
        Gate OPEN = new Gate() {
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
                return VoiceLine.ALL;
            }

            @Override
            public int masks() {
                return 0;
            }
        };
    }

    /**
     * A line waiting its turn; {@code cue} is the tick its first word should sound, or {@link #NOT_PLACED}; {@code latestEnd}
     * the last tick its words may end on ({@link #NO_LIMIT}): a line that would end later is skipped.
     */
    public record Pending(VoiceLine line, Context context, long firedAt, long expiresAt, long cue, long latestEnd) {
        public boolean placed() {
            return cue != NOT_PLACED;
        }
    }

    /** A line to start now: the take, whether it cuts the one playing, and whether the boss placed it on this tick. */
    public record Start(VoiceLine line, String variantKey, VoiceLine.Variant variant, boolean cut, boolean onCue) {}

    /** The line playing: from {@code start} to {@code end}, its words until {@code wordsEnd}. */
    public record Playing(VoiceLine line, long start, long end, long wordsEnd) {}

    private final BossCatalog catalog;
    private final Map<String, Long> memory;
    private final Set<String> played = new HashSet<>();
    private final List<Pending> pending = new ArrayList<>();
    private @Nullable Playing playing;
    private long lastEnd = Long.MIN_VALUE / 4;
    private boolean closed;

    /** {@code memory}: the lair's record of when each cooldown line last ended (shared by its fights). */
    public VoiceDirector(BossCatalog catalog, Map<String, Long> memory) {
        this.catalog = catalog;
        this.memory = memory;
    }

    /**
     * {@code fired} at {@code now} with {@code context}: queues the best line for it and returns it, or null. {@code placed}:
     * the boss chose this tick itself, so the line may start on it past the boss's gate (its first word is due now).
     */
    public @Nullable VoiceLine trigger(Trigger fired, Context context, long now, boolean placed) {
        return trigger(fired, context, now, placed ? now : NOT_PLACED);
    }

    /**
     * The same for a line the boss places: its first word is due on tick {@code cue} (at or after {@code now}); the take
     * starts as many ticks before as it leads into its words, and goes past the boss's gate.
     */
    public @Nullable VoiceLine trigger(Trigger fired, Context context, long now, long cue) {
        return trigger(fired, context, now, cue, NO_LIMIT);
    }

    /** The same, with the last tick the words may end on: past it the line is skipped. */
    public @Nullable VoiceLine trigger(Trigger fired, Context context, long now, long cue, long latestEnd) {
        if (closed) {
            return null;
        }
        boolean kill = fired.is(Trigger.BOSS_KILL);
        if (kill) {
            // the kill closes the fight whether or not a line answers it: nothing queued may start over the death
            pending.clear();
            closed = true;
        }
        VoiceLine best = null;
        for (VoiceLine l : catalog.lines()) {
            if (!l.answers(fired) || !l.fits(context) || !mayPlay(l, now) || queued(l)) {
                continue;
            }
            if (best == null || l.priority() > best.priority()) {
                best = l;
            }
        }
        if (best == null) {
            return null;
        }
        long wait = fired.is(Trigger.GEAR) ? Math.max(GEAR_WAIT, catalog.waitTicks()) : catalog.waitTicks();
        long from = cue == NOT_PLACED ? now : Math.max(now, cue);
        // a placed opener has no freshness of its own: it starts on its tick (moved later by a sound it waits for) or it is dropped;
        // one raised free (the Leviathan's, at her first Moorage) waits for a window like any free line, within the boss's wait
        long expires = fired.is(Trigger.FIGHT_START) && cue != NOT_PLACED ? Long.MAX_VALUE / 2 : from + wait;
        pending.add(new Pending(best, context, now, expires, cue, latestEnd));
        return best;
    }

    private boolean queued(VoiceLine l) {
        for (Pending p : pending) {
            if (p.line() == l) {
                return true;
            }
        }
        return false;
    }

    /** Not said yet this fight, or a cooldown line whose cooldown has run since it last ended. */
    private boolean mayPlay(VoiceLine l, long now) {
        if (!l.repeats()) {
            return !played.contains(l.id());
        }
        Long end = memory.get(l.id());
        return end == null || now - end >= l.cooldownSeconds() * 20L;
    }

    /**
     * The first tick {@code line}'s first word may sound for the boss's own sounds: none for the kill line, which speaks on its cue
     * (it must end before the rewards and the guide follow, so it cannot wait for a Shatter's sound that came just before the kill).
     */
    private static long soundsOver(VoiceLine line, Gate gate, long now) {
        return line.trigger().is(Trigger.BOSS_KILL) ? Long.MIN_VALUE : gate.wordsMayStartAt(now);
    }

    /**
     * The tick a placed line's take starts on: its words land on the cue, or once the boss's own sound is over if that is
     * later; never before the trigger.
     */
    private static long dueTick(Pending p, VoiceLine.Variant take, long wordsFrom) {
        return Math.max(Math.max(p.cue(), wordsFrom) - take.speechStartTicks(), p.firedAt());
    }

    /** Called every tick after the triggers: the line to start now (it is then playing), or null. */
    public @Nullable Start next(long now, Gate gate) {
        pending.removeIf(p -> now > p.expiresAt());
        // a placed line due soon keeps its slot: nothing of lesser priority may start and still be playing then
        long reservedAt = Long.MAX_VALUE;
        int reservedPriority = 0;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending p = it.next();
            VoiceLine.Variant take = p.line().variant(gate.living());
            if (!p.placed() || take == null) {
                continue;
            }
            long due = dueTick(p, take, soundsOver(p.line(), gate, now));
            if (due + take.speechTicks() > p.latestEnd()) {
                it.remove(); // its words would end past its window once the sound is over: skipped, never forced in
            } else if (now > due && p.line().trigger().is(Trigger.FIGHT_START)) {
                it.remove(); // an opener starts on its tick or never
            } else if (now < due && (due < reservedAt || due == reservedAt && p.line().priority() > reservedPriority)) {
                reservedAt = due;
                reservedPriority = p.line().priority();
            }
        }
        Pending best = null;
        VoiceLine.Variant bestTake = null;
        boolean bestCut = false;
        boolean bestOnCue = false;
        for (Pending p : pending) {
            VoiceLine l = p.line();
            VoiceLine.Variant take = l.variant(gate.living());
            if (take == null || !mayPlay(l, now) || l.masksNeeded() > 0 && gate.masks() != l.masksNeeded()) {
                continue;
            }
            long due = p.placed() ? dueTick(p, take, soundsOver(p.line(), gate, now)) : now;
            if (now < due || now + take.speechStartTicks() < soundsOver(l, gate, now)) {
                continue;
            }
            if (l.priority() <= reservedPriority && now + take.lengthTicks() > reservedAt) {
                continue;
            }
            boolean cut = false;
            if (speaking(now)) {
                if (l.priority() >= KILL) {
                    cut = true;
                } else if (l.priority() >= PHASE && playing.line().priority() < MINOR && playing.wordsEnd() - now > CUT_REMAINING) {
                    cut = true;
                } else {
                    continue;
                }
            }
            if (l.priority() < URGENT && now < lastEnd + catalog.globalGapTicks()) {
                continue;
            }
            boolean onCue = p.placed() && now == due;
            if (!onCue && (!gate.mayStart(now) || gate.quietTicks(now) < take.speechTicks() + gate.marginTicks())) {
                continue;
            }
            if (best == null || l.priority() > best.line().priority()
                    || l.priority() == best.line().priority() && l.order() < best.line().order()) {
                best = p;
                bestTake = take;
                bestCut = cut;
                bestOnCue = onCue;
            }
        }
        if (best == null) {
            return null;
        }
        VoiceLine line = best.line();
        pending.removeIf(p -> p.line() == line);
        played.add(line.id());
        long end = now + bestTake.lengthTicks();
        if (line.repeats()) {
            memory.put(line.id(), end);
        }
        playing = new Playing(line, now, end, now + bestTake.speechTicks());
        lastEnd = end;
        return new Start(line, line.variantKey(gate.living()), bestTake, bestCut, bestOnCue);
    }

    /** Debug: the global gap has run (what was said stays said, and what plays keeps playing). */
    public void endGap() {
        lastEnd = Math.min(lastEnd, Long.MIN_VALUE / 4);
    }

    /** True while a line plays. */
    public boolean speaking(long now) {
        return playing != null && now < playing.end();
    }

    /** True when nothing plays or waits and the global gap has run: a moment for a check the script defers (armor, gear). */
    public boolean quiet(long now) {
        return !closed && pending.isEmpty() && !speaking(now) && now >= lastEnd + catalog.globalGapTicks();
    }

    public boolean closed() {
        return closed;
    }

    public @Nullable Playing playing() {
        return playing;
    }

    public List<Pending> pending() {
        return List.copyOf(pending);
    }

    public Set<String> played() {
        return Set.copyOf(played);
    }
}
