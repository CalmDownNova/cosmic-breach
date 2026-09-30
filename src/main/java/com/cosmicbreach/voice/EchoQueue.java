package com.cosmicbreach.voice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * When each Echo line the server says starts, on the client (times in client ticks). Pure, so the rules are
 * tested without a game:
 *
 * <ul>
 *   <li>One line at a time: the voice never overlaps itself. Lines wait in the order they were said, with
 *       {@link #GAP} ticks of quiet between one line's end and the next one's start.</li>
 *   <li>A line waits its {@link EchoLine#leadTicks() lead-in} after being said.</li>
 *   <li>Nothing starts while the screen is white from falling up or a world is loading ({@code blocked}); a line
 *       that follows a fall-up ({@link EchoLine#afterFallUp()}, the arrival) starts its lead-in only once the white
 *       has cleared.</li>
 *   <li>A line already playing or waiting is not queued again.</li>
 * </ul>
 */
public final class EchoQueue {
    /** Quiet between two lines, in ticks. */
    public static final int GAP = 10;

    private record Waiting(EchoLine line, long saidAt) {}

    private final ArrayDeque<Waiting> waiting = new ArrayDeque<>();
    private @Nullable EchoLine playing;
    private long endedAt = Long.MIN_VALUE / 4;
    private long clearSince = Long.MIN_VALUE / 4;
    private boolean blocked;

    /** {@code line} was said at {@code now}. False if it is already playing or waiting. */
    public boolean offer(EchoLine line, long now) {
        if (line == playing || waiting.stream().anyMatch(w -> w.line() == line)) {
            return false;
        }
        waiting.add(new Waiting(line, now));
        return true;
    }

    /**
     * Called every tick: the line to start now (it becomes the one playing), or null. {@code blocked}: the screen is
     * white from falling up, or a world is loading.
     */
    public @Nullable EchoLine next(long now, boolean blocked) {
        if (blocked) {
            this.blocked = true;
            return null;
        }
        if (this.blocked || clearSince == Long.MIN_VALUE / 4) {
            this.blocked = false;
            clearSince = now;
        }
        Waiting w = waiting.peek();
        if (playing != null || w == null || now - endedAt < GAP) {
            return null;
        }
        long from = w.line().afterFallUp() ? Math.max(w.saidAt(), clearSince) : w.saidAt();
        if (now < from + w.line().leadTicks()) {
            return null;
        }
        waiting.poll();
        playing = w.line();
        return playing;
    }

    /** The line playing has ended (or was cut off) at {@code now}. */
    public void finished(long now) {
        playing = null;
        endedAt = now;
    }

    public @Nullable EchoLine playing() {
        return playing;
    }

    public List<EchoLine> waiting() {
        List<EchoLine> out = new ArrayList<>();
        waiting.forEach(w -> out.add(w.line()));
        return out;
    }

    /** Forgets everything (leaving a world). */
    public void clear() {
        waiting.clear();
        playing = null;
        endedAt = Long.MIN_VALUE / 4;
        clearSince = Long.MIN_VALUE / 4;
        blocked = false;
    }
}
