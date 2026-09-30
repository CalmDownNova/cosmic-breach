package com.cosmicbreach.client.sky;

import com.cosmicbreach.client.fx.FxClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The sky's state on this client: the smoothed layer weights, the weather hooks, and this frame's
 * {@link SkyFrame}. {@link #update} runs once a frame, at the fog colour event (the first thing a frame
 * computes in the level pass, before the sky draws), so the sky, the fog colour and the fog distances all
 * use the same numbers.
 */
public final class SkyState {
    static final SkyFrame FRAME = new SkyFrame();
    static final SkyLayers LAYERS = new SkyLayers();
    static final SkyModel.Weather WEATHER = new SkyModel.Weather();

    private static @Nullable ClientLevel lastLevel;
    private static long lastNanos;
    private static long frames;

    private SkyState() {
    }

    /** Recomputes the frame for a camera at height {@code cameraY}. */
    static SkyFrame update(ClientLevel level, double cameraY, float partialTick) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.0 : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        if (level != lastLevel) {
            LAYERS.reset();
            lastLevel = level;
        }
        double[] weights = LAYERS.update(cameraY, Math.min(dt, 0.25));
        Minecraft mc = Minecraft.getInstance();
        double renderBlocks = mc.options.getEffectiveRenderDistance() * 16.0;
        SkyModel.compute(FRAME, level.getDayTime(), level.getGameTime(), partialTick, weights, renderBlocks,
                FxClock.now(partialTick) / 20.0, WEATHER);
        frames++;
        return FRAME;
    }

    /** This frame's numbers (read only). */
    public static SkyFrame frame() {
        return FRAME;
    }

    /** How many frames have been computed (for checks: it grows while Aetheria is on screen). */
    public static long frames() {
        return frames;
    }
}
