package com.cosmicbreach.client.sky;

/**
 * Aetheria's sky colours and gains, per layer (Reach, Drift, Deep) and time (day, sunset, night). Tables
 * are {@code [layer][time]}. The horizon colour is also the fog colour: distant terrain melts into the sky.
 * Daytime horizons are the GDD's fog colours (Reach #F3E6C4, Drift #A8D8E8, Deep #25123F).
 *
 * <p>Look rule (GDD 2.2): the Reach is bright daylight, blue above a warm sunlit haze, with the nebula and
 * the bodies as a pale overlay; darkness belongs to the Deep, where the nebula glows through dark indigo.
 * Nights are the eclipse: deep blue-violet, never black.
 */
final class SkyPalette {
    private SkyPalette() {
    }

    //                                   day        sunset     night
    static final int[][] ZENITH = {
            {0x3D7FDE, 0x34478F, 0x131338},
            {0x5A98D4, 0x33458A, 0x10143A},
            {0x1D1146, 0x1E1044, 0x140C34}};
    static final int[][] MID = {
            {0x78B3F0, 0x7A77BE, 0x1E1C4E},
            {0x84BCE2, 0x6F7FB8, 0x19204C},
            {0x22134C, 0x26124C, 0x180E3A}};
    static final int[][] HAZE = {
            {0xD4E7F8, 0xF2B78F, 0x2F2B64},
            {0xA0D2E6, 0xD8B4A8, 0x25305E},
            {0x26144A, 0x2C1446, 0x1C1038}};
    static final int[][] HORIZON = {
            {0xF3E6C4, 0xFFD29A, 0x3B3572},
            {0xA8D8E8, 0xE2C6B4, 0x2C3868},
            {0x25123F, 0x2A133E, 0x1E0F36}};
    static final int[][] LOW = {
            {0xB7C8EE, 0xC98EA6, 0x252055},
            {0x86AECC, 0x8C8AB4, 0x1C224E},
            {0x190B30, 0x1A0B2E, 0x140A2A}};
    static final int[][] NADIR = {
            {0x4C3F9C, 0x3A2A72, 0x1C103F},
            {0x2E2C6C, 0x2A2464, 0x160E36},
            {0x0B0519, 0x0B0519, 0x080414}};

    /** Nebula light gain, dust gain, and gain below the horizon. */
    static final double[][] NEBULA_LIGHT = {{0.12, 0.36, 0.9}, {0.18, 0.45, 0.95}, {0.85, 0.9, 1.0}};
    static final double[][] NEBULA_DUST = {{0.04, 0.18, 0.55}, {0.08, 0.25, 0.6}, {0.55, 0.6, 0.7}};
    static final double[][] NEBULA_BELOW = {{0.55, 0.65, 0.8}, {0.7, 0.75, 0.85}, {0.9, 0.9, 0.9}};
    /** By day the nebula's clouds are sunlit veils in their own hue (0 at night: then they are light). */
    static final double[][] NEBULA_VEIL = {{0.9, 0.45, 0.0}, {0.7, 0.35, 0.0}, {0.0, 0.0, 0.0}};
    /** Star brightness. */
    static final double[][] STARS = {{0.1, 0.45, 1.0}, {0.25, 0.6, 1.0}, {0.85, 0.9, 1.0}};
    /** How much of the day sky lies over Thalassa's disc. */
    static final double[][] PLANET_INSCATTER = {{0.26, 0.24, 0.04}, {0.24, 0.2, 0.04}, {0.08, 0.06, 0.03}};

    /** Per layer: Solenne's disc, its corona, its glare, the light it throws on Thalassa, the planet's night side opacity by day. */
    static final double[] DISC = {1.0, 0.95, 0.35};
    static final double[] CORONA = {0.55, 0.5, 0.35};
    static final double[] GLARE = {0.34, 0.3, 0.08};
    static final double[] PLANET_LIGHT = {1.0, 0.95, 0.6};
    static final double[] PLANET_OPACITY_DAY = {0.5, 0.55, 0.85};
}
