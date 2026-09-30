package com.cosmicbreach.client.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * The sky's hooks for weather (W3b), client side. Each value is a 0..1 intensity the weather task eases in
 * and out itself; the sky reads them every frame.
 *
 * <ul>
 *   <li>{@link #setSolenneFlare}: the Solar Flare's warning. Solenne's disc and corona brighten, the glare
 *       widens, the sky's edge whitens (haze and horizon pale towards white).</li>
 *   <li>{@link #setVesperSilence}: the Eclipse Surge's warning. Vesper stops pulsing and its beam fades out
 *       (the {@link com.cosmicbreach.world.VesperClock} itself never stops; server logic keeps its beat).</li>
 *   <li>{@link #setSkyDim}: the Eclipse Surge. The dome and the fog darken and the sky light's share of the
 *       lightmap drops, so only block light (torches, lichen) keeps the ground lit.</li>
 *   <li>{@link #addLayer}: anything else drawn in the sky pass, after the bodies and before the aurora,
 *       such as the Meteor Shower's red streaks or the Drift's dust streams at a distance. A layer gets the
 *       view rotation (camera at the origin, world axes), the projection and the frame's {@link SkyFrame}.
 *       Draw within 90 blocks of the camera (the far plane is 128 at render distance 2); depth writes are
 *       off and every fragment passes the depth test; blend is on, ONE/ONE, when a layer is called. Restore
 *       any other GL state you change.</li>
 * </ul>
 * Only while our sky draws: under an Iris shader pack the pack draws the sky and layers are not called.
 */
public final class SkyWeather {
    /** Something weather draws in the sky. */
    @FunctionalInterface
    public interface Layer {
        void render(Matrix4f viewRotation, Matrix4f projection, Camera camera, float partialTick, SkyFrame frame, PoseStack scratch);
    }

    private static final List<Layer> LAYERS = new CopyOnWriteArrayList<>();

    private SkyWeather() {
    }

    public static void setSolenneFlare(float intensity) {
        SkyState.WEATHER.solenneFlare = Math.max(0f, Math.min(1f, intensity));
    }

    public static void setVesperSilence(float intensity) {
        SkyState.WEATHER.vesperSilence = Math.max(0f, Math.min(1f, intensity));
    }

    /** The Eclipse Surge's dimming: the dome, the fog and the sky light's share of the lightmap darken, 0..1. */
    public static void setSkyDim(float intensity) {
        SkyState.WEATHER.skyDim = Math.max(0f, Math.min(1f, intensity));
    }

    /**
     * A fight's say in where Solenne stands (G9p): its disc, glare and anything drawn on it move toward the unit
     * direction (x, y, z) by {@code weight} 0..1 (0 gives the sky back).
     */
    public static void setSolenneAt(double x, double y, double z, float weight) {
        double n = Math.sqrt(x * x + y * y + z * z);
        if (n > 1e-9) {
            SkyState.WEATHER.solenneAt[0] = x / n;
            SkyState.WEATHER.solenneAt[1] = y / n;
            SkyState.WEATHER.solenneAt[2] = z / n;
        }
        SkyState.WEATHER.solenneAtWeight = Math.max(0f, Math.min(1f, weight));
    }

    public static void addLayer(Layer layer) {
        LAYERS.add(layer);
    }

    public static void removeLayer(Layer layer) {
        LAYERS.remove(layer);
    }

    static List<Layer> layers() {
        return LAYERS;
    }
}
