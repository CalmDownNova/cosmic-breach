package com.cosmicbreach.client.sky;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * The Deep's zones in its air (Aetheria 1.2). Aetheria's fog colour comes from the sky ({@link AetheriaFog}), not from
 * biomes; in the Deep this tints the sky's gradient itself ({@link SkyState}) by the camera's biome, so the fog read from
 * it carries the zone's colour and distant terrain still melts into the sky behind it (tinting the fog alone left far
 * terrain as flat cut-outs against an untinted sky): each channel times that biome's fog colour over the Rift
 * Abyss's ({@link #REFERENCE}), so the Spans look exactly as before, the Lichen Gardens' fog lifts toward violet, the
 * Hanging Wood's darkens toward teal and the Shattered Field's warms toward magenta; outside the Spans the fog is also
 * pulled {@link #PULL} of the way to the biome's own fog colour, so a zone's air reads as its own colour. The tint
 * eases over about a second and a half, so crossing a zone's border fades rather than snaps, and it counts only as much
 * as the camera is in the Deep (the sky's own layer weight), so the Shear bands cross-fade as before.
 */
public final class ZoneFog {
    /** The Rift Abyss's fog colour (the Deep's colour in the GDD): the tint is relative to it. */
    public static final int REFERENCE = 0x25123F;
    private static final double EASE_SECONDS = 0.6;
    private static final float[] tint = {1f, 1f, 1f};
    /** The biome's own fog colour, eased, and how far toward it the fog is pulled (0 for the reference). */
    private static final float[] own = {0f, 0f, 0f};
    private static float pull;
    /** How far a zone's fog is pulled toward its biome's own fog colour, on top of the tint. */
    public static final float PULL = 0.4f;
    private static long lastNanos;

    private ZoneFog() {
    }

    /** The tint a biome fog colour gives, per channel (1 for the reference). */
    public static float[] target(int biomeFog, float[] out) {
        for (int i = 0; i < 3; i++) {
            int shift = 16 - 8 * i;
            double c = ((biomeFog >> shift) & 0xFF) / 255.0;
            double r = ((REFERENCE >> shift) & 0xFF) / 255.0;
            out[i] = (float) Math.max(0.35, Math.min(2.5, (c + 0.03) / (r + 0.03)));
        }
        return out;
    }

    /** Eases the tint toward the camera's biome at {@code pos}; once a frame, before {@link #tint(float[], double)}. */
    static void update(ClientLevel level, BlockPos pos, double deep) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 1.0 : Math.min(1.0, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (deep > 0.001 && !level.hasChunkAt(pos)) {
            // the camera's chunk is not here yet (just after a teleport or a layer change): an unloaded chunk reads as the
            // fallback biome, whose fog colour would tint the air for a moment, so the tint holds until the chunk arrives
            return;
        }
        float[] want = {1f, 1f, 1f};
        int fog = REFERENCE;
        if (deep > 0.001) {
            fog = level.getBiome(pos).value().getFogColor();
            target(fog, want);
        }
        float wantPull = fog == REFERENCE ? 0f : PULL;
        double k = 1.0 - Math.exp(-dt / EASE_SECONDS);
        pull += (float) ((wantPull - pull) * k);
        for (int i = 0; i < 3; i++) {
            tint[i] += (float) ((want[i] - tint[i]) * k);
            own[i] += (float) ((((fog >> (16 - 8 * i)) & 0xFF) / 255f - own[i]) * k);
        }
    }

    /**
     * Tints one sky colour (any band of the sky's gradient, so the fog that is read from it matches) by the eased zone
     * tint, {@code weight} its strength (the camera's Deep weight, less for the zenith).
     */
    static void tint(float[] colour, double weight) {
        for (int i = 0; i < 3; i++) {
            float t = (float) (1.0 + (tint[i] - 1.0) * weight);
            float c = Math.min(1f, colour[i] * t);
            colour[i] = c + (own[i] - c) * pull * (float) weight;
        }
    }

    /** The tint now (for checks). */
    public static float[] tint() {
        return tint.clone();
    }
}
