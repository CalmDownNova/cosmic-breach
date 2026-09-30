package com.cosmicbreach.client.fx;

import net.minecraft.client.Minecraft;

/**
 * The effects' time: counts the client ticks in which the level runs (so it stops with the game on
 * pause, like the particles), and with the frame's partial tick gives a smooth time for animation.
 * Something started in tick N is {@code partialTick} ticks old in the frames drawn after tick N.
 */
public final class FxClock {
    private static long ticks;

    private FxClock() {
    }

    /** Called at the start of every client tick. */
    static void tick(Minecraft mc) {
        if (running(mc)) {
            ticks++;
        }
    }

    /** True while the level ticks: a level, not paused, not frozen by {@code /tick}. */
    public static boolean running(Minecraft mc) {
        return mc.level != null && !mc.isPaused() && mc.level.tickRateManager().runsNormally();
    }

    public static long ticks() {
        return ticks;
    }

    /** Now, in ticks, for a frame {@code partialTick} past the last tick. */
    public static double now(float partialTick) {
        return ticks + partialTick;
    }
}
