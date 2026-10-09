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
        // in the Deep, far terrain fades to one fog colour while it stands against the sky both above and below the
        // horizon (the layer's masses hang all around the camera): the bands next to the horizon close in on it, so a
        // distant mass below eye level melts into the air rather than showing as a flat cut-out (the nadir stays dark)
        flatten(FRAME.low, FRAME.horizon, weights[2]);
        flatten(FRAME.haze, FRAME.horizon, weights[2] * 0.75);
        flatten(FRAME.mid, FRAME.horizon, weights[2] * 0.75);
        // the Deep's zones colour the air: the sky's lower bands fully, the zenith by half (Aetheria 1.2, ZoneFog)
        ZoneFog.update(level, mc.gameRenderer.getMainCamera().getBlockPosition(), weights[2]);
        for (float[] band : new float[][] {FRAME.horizon, FRAME.haze, FRAME.mid, FRAME.low, FRAME.nadir}) {
            ZoneFog.tint(band, weights[2]);
        }
        ZoneFog.tint(FRAME.zenith, weights[2] * 0.5);
        frames++;
        return FRAME;
    }

    private static void flatten(float[] band, float[] toward, double amount) {
        for (int i = 0; i < 3; i++) {
            band[i] += (float) ((toward[i] - band[i]) * amount);
        }
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
