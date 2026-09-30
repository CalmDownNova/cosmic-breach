package com.cosmicbreach.client.anim;

/**
 * The client's game clock for animations, in ticks: counted up at the start of every client tick in
 * which entities tick (not while the game is paused), before any entity ticks. A frame drawn after
 * tick n with partial tick p is at time n + p.
 */
final class AnimationTime {
    private static long ticks;

    private AnimationTime() {
    }

    /** Called at the start of every client tick; {@code running} is false while the game is paused. */
    static void onClientTick(boolean running) {
        if (running) {
            ticks++;
        }
    }

    static long ticks() {
        return ticks;
    }
}
