package com.cosmicbreach.world.light;

import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.ShearBand;
import net.minecraft.world.level.Level;

/**
 * How much of the sky's light reaches a height in Aetheria (W3c; GDD 2.2: the Deep has "low sky light (the layers
 * above shade it)", "the only dark place in Aetheria"). One rule for both sides: the Hollow Stalker judges ground by it
 * ({@link com.cosmicbreach.entity.stalker.StalkerRules#effectiveLight}) and the client draws sky light by it (the
 * lightmap in {@code client.sky.AetheriaSkyEffects}), so what looks dark is dark to the Stalker.
 *
 * <ul>
 *   <li>Above Shear band B (the Reach and the Drift) the sky gives all its light: scale 1.</li>
 *   <li>Below the band, in the Deep, {@value #DEEP_SKY_SHARE} of it: open ground under the noon sky (sky light 15)
 *       counts as 6, which is dark to a Stalker (lit starts at 8).</li>
 *   <li>Through the band (Y {@value #BAND_LOW} to {@value #BAND_HIGH}) a smoothstep between the two, the same curve the
 *       sky and the fog cross-fade on ({@code client.sky.SkyLayers}), so falling through the band is losing the sun.</li>
 * </ul>
 * Only how sky light is judged and drawn changes: the level's stored light, vanilla's mob spawning and every other
 * mechanic keep the real values. Block light (torches, lichen, abilities) is never scaled.
 */
public final class DeepLight {
    /** The share of sky light that reaches the Deep under the layers above. */
    public static final double DEEP_SKY_SHARE = 0.4;
    /** Shear band B's bottom ({@link ShearBand#B}): at and below it the Deep's share holds. */
    public static final int BAND_LOW = 145;
    /** The first Y above Shear band B: at and above it the sky gives its full light. */
    public static final int BAND_HIGH = 160;

    private DeepLight() {
    }

    /** How deep {@code y} is: 0 above Shear band B, 1 below it, a smoothstep through it. */
    public static double deepness(double y) {
        double t = Math.max(0.0, Math.min(1.0, (y - BAND_LOW) / (double) (BAND_HIGH - BAND_LOW)));
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }

    /** The share of sky light at a given deepness (0 above the band, 1 in the Deep; clamped). */
    public static double shareAt(double deepness) {
        double d = Math.max(0.0, Math.min(1.0, deepness));
        return 1.0 - (1.0 - DEEP_SKY_SHARE) * d;
    }

    /** The share of sky light that reaches height {@code y} in Aetheria. */
    public static double skyScale(double y) {
        return shareAt(deepness(y));
    }

    /** The share of sky light at height {@code y} in {@code level}: all of it outside Aetheria. */
    public static double skyScale(Level level, double y) {
        return AetheriaWorld.is(level) ? skyScale(y) : 1.0;
    }

    /** A sky light level under a share, rounded down (15 at the Deep's share is 6). */
    public static int shade(int skyLight, double scale) {
        if (scale >= 1.0) {
            return skyLight;
        }
        return (int) Math.floor(Math.max(0, skyLight) * Math.max(0.0, scale) + 1e-9);
    }
}
