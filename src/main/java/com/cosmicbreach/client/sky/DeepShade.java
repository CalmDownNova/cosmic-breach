package com.cosmicbreach.client.sky;

import com.cosmicbreach.world.light.DeepLight;
import org.joml.Vector3f;

/**
 * The Deep's darkness in the lightmap (W3c). While the camera is in the Deep, the lightmap is drawn as if the sky gave
 * only {@link DeepLight#DEEP_SKY_SHARE} of its light, by the rule the Hollow Stalker judges ground by
 * ({@link DeepLight}):
 * <ul>
 *   <li>sky light level L draws as level L times the share, so open ground under the noon sky (15) draws as light 6,
 *       which a Stalker counts as 6;</li>
 *   <li>the dimension's ambient glow (its {@code ambient_light}, 0.1, which vanilla lays under both rows of the
 *       lightmap and which keeps the Reach's shade and the eclipse night from going black) takes the same share, since
 *       in a sky world it is the sky's light too. Without this the glow alone would keep unlit ground in the Deep at
 *       more than half brightness.</li>
 * </ul>
 * Block light's own curve is untouched: torches and ability light read exactly as bright as anywhere else and stand
 * out against the dark. Through Shear band B the share follows the sky's own eased Deep weight ({@link SkyLayers}), so
 * the ground darkens with the sky and the fog over the same seconds. The Eclipse Surge's dimming
 * ({@link SkyWeather#setSkyDim}), the time of day (the eclipse night), and vanilla's night vision, Darkness and gamma
 * all still apply on top. Only the client's drawing changes; the level's light values, and so mob spawning and every
 * vanilla rule, stay as they are.
 */
public final class DeepShade {
    /** For checks: false draws the Deep with the sky's full light (the look before W3c). */
    public static volatile boolean enabledForTest = true;

    /** Vanilla's sky colour lerps this far towards white ({@code LightTexture.updateLightTexture}). */
    private static final float SKY_WHITE = 0.35f;
    /** Vanilla lerps every cell this far towards grey 0.75 before weather and effects. */
    private static final float GREY_LERP = 0.04f;

    private DeepShade() {
    }

    /** The share of the sky's light the lightmap draws with for the camera's eased Deep weight (0..1). */
    public static float share(double deepWeight) {
        return enabledForTest ? (float) DeepLight.shareAt(deepWeight) : 1f;
    }

    /**
     * Vanilla's brightness curve ({@code LightTexture.getBrightness}) for a level that may be fractional:
     * {@code lerp(ambient, f / (4 - 3f), 1)} with {@code f = level / 15}.
     */
    public static float brightness(float ambient, float level) {
        float f = Math.max(0f, Math.min(15f, level)) / 15f;
        float curve = f / (4f - 3f * f);
        return curve + ambient * (1f - curve);
    }

    /** Vanilla's warm colour for block light of brightness {@code b} (already times the flicker), into {@code out}. */
    public static Vector3f blockColour(float b, Vector3f out) {
        return out.set(b, b * ((b * 0.6f + 0.4f) * 0.6f + 0.4f), b * (b * b * 0.6f + 0.4f));
    }

    /**
     * Shades one lightmap cell as vanilla built it: {@code colors} holds block light's warm colour (brightness at
     * {@code blockLevel} times {@code flicker}) plus the sky's colour times {@code skyLight} (the sky's brightness at
     * {@code skyLevel} times the time of day), lerped 4% to grey and dimmed by a boss's darkened world
     * ({@code darkenWorld}, 0..1). Takes away what the shade removes: the sky's term drawn at level times
     * {@code share} with the ambient glow times {@code share}, and the ambient glow's part of the block term.
     *
     * @param skyDarken the level's sky darkening (1 at noon, 0.2 at night), which tints the sky's colour
     */
    public static void apply(Vector3f colors, float ambient, int blockLevel, int skyLevel, float skyLight, float skyDarken,
                             float flicker, float share, float darkenWorld) {
        if (share >= 1f) {
            return;
        }
        float dimmed = ambient * share;
        float full = brightness(ambient, skyLevel);
        // the time of day (and a lightning flash) as vanilla applied it: skyLight = full * daylight
        float daylight = full > 0f ? skyLight / full : 0f;
        float sky = (full - brightness(dimmed, skyLevel * share)) * daylight;
        float tint = skyDarken + (1f - skyDarken) * SKY_WHITE;
        Vector3f block = blockColour(brightness(ambient, blockLevel) * flicker, new Vector3f())
                .sub(blockColour(brightness(dimmed, blockLevel) * flicker, new Vector3f()));
        float keep = 1f - GREY_LERP;
        float d = Math.max(0f, Math.min(1f, darkenWorld));
        colors.sub((sky * tint + block.x) * keep * (1f - 0.3f * d), (sky * tint + block.y) * keep * (1f - 0.4f * d),
                (sky + block.z) * keep * (1f - 0.4f * d));
    }
}
