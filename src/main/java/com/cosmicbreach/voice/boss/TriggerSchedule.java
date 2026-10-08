package com.cosmicbreach.voice.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The moments a boss places ahead of time (a phase line a beat after its crack, a Moorage's line once the swell has
 * passed). A placed one has a cue, the tick its first word should sound, and is raised {@value #LOOKAHEAD} ticks before it,
 * so the director can start the take early enough that the words land on the cue; a free one is raised on its tick and
 * plays whenever the rules allow. Pure.
 */
public final class TriggerSchedule {
    /** Ticks before a cue a placed moment is raised: more than the longest lead into any take's words. */
    public static final int LOOKAHEAD = 8;

    /** A moment that has come due: raised now. {@code cue} is {@link VoiceDirector#NOT_PLACED} for a free one. */
    public record Due(Trigger trigger, String event, long cue, long latestEnd) {}

    private record Entry(Trigger trigger, String event, long at, long cue, long latestEnd) {}

    private final List<Entry> entries = new ArrayList<>();

    /** Raises {@code trigger} with its first word due on tick {@code cue}. */
    public void placed(Trigger trigger, String event, long cue) {
        placed(trigger, event, cue, VoiceDirector.NO_LIMIT);
    }

    /** The same, with the last tick the words may end on. */
    public void placed(Trigger trigger, String event, long cue, long latestEnd) {
        entries.add(new Entry(trigger, event, cue - LOOKAHEAD, cue, latestEnd));
    }

    /** Raises {@code trigger} on tick {@code at}, to play when it may. */
    public void free(Trigger trigger, String event, long at) {
        entries.add(new Entry(trigger, event, at, VoiceDirector.NOT_PLACED, VoiceDirector.NO_LIMIT));
    }

    /** What has come due by {@code now}, in the order it was scheduled; each is raised once. */
    public List<Due> due(long now) {
        List<Due> out = new ArrayList<>();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext();) {
            Entry e = it.next();
            if (e.at() <= now) {
                it.remove();
                out.add(new Due(e.trigger(), e.event(), e.cue(), e.latestEnd()));
            }
        }
        return out;
    }

    public List<Due> pending() {
        List<Due> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(new Due(e.trigger(), e.event(), e.cue(), e.latestEnd()));
        }
        return out;
    }

    public void clear() {
        entries.clear();
    }
}
