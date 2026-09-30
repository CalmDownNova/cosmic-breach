package com.cosmicbreach.client.sky;

/**
 * Everything the sky renderer and the fog need for one frame, filled by {@link SkyModel#compute}. Colours
 * are sRGB 0..1, directions unit vectors in world axes (+X east, +Y up, -Z north), angles radians.
 * Plain fields, reused every frame, no Minecraft types (so {@code SkyModelDump} (src/test) can print it offline).
 */
public final class SkyFrame {
    // ---------------------------------------------------------------- where and when
    /** Layer weights after smoothing: Reach, Drift, Deep (sum 1). */
    public final double[] layers = new double[3];
    /** 0 night, 1 full day: vanilla's sky brightness curve, so the sky dims with the world's light. */
    public double daylight;
    /** Weights of the day, sunset and night palettes (sum 1). */
    public final double[] times = new double[3];

    // ---------------------------------------------------------------- sky gradient (fog = horizon)
    public final float[] zenith = new float[3];
    public final float[] mid = new float[3];
    public final float[] haze = new float[3];
    public final float[] horizon = new float[3];
    public final float[] low = new float[3];
    public final float[] nadir = new float[3];

    // ---------------------------------------------------------------- the celestial sphere
    /** Celestial frame to world (row-major 3 by 3): turns once a day around the pole. */
    public final float[] skyRot = new float[9];
    /** Two nebula atlases to cross-fade (layer indices) and how far from the first to the second. */
    public int nebulaA;
    public int nebulaB;
    public float nebulaMix;
    /** Nebula light gain, dust gain, gain below the horizon, daytime veil. */
    public final float[] nebula = new float[4];
    /** Star brightness, twinkle depth, brightness below the horizon. */
    public final float[] stars = new float[3];

    // ---------------------------------------------------------------- Solenne
    public final float[] sunDir = new float[3];
    /** Angular radius of the disc. */
    public float sunRadius;
    /** How much of the disc shows: behind Thalassa or under the horizon it is 0. */
    public float sunVisible;
    /** Fraction of the disc Thalassa covers. */
    public float eclipse;
    public float discStrength;
    public float coronaStrength;
    /** Glare colour and strength (strength already scaled by what shows of the sun). */
    public final float[] glow = new float[4];

    // ---------------------------------------------------------------- Thalassa
    public final float[] planetDir = new float[3];
    public float planetRadius;
    public final float[] ringAxis = new float[3];
    public final float[] planetLightDir = new float[3];
    /** Light on the planet: rgb, a = backlight (the eclipse halo). */
    public final float[] planetLight = new float[4];
    /** Night side opacity, day sky over the disc, glare over the disc, halo strength. */
    public final float[] planetParams = new float[4];
    public final float[] planetAmbient = new float[3];
    /** Band drift, in turns. */
    public float bandDrift;

    // ---------------------------------------------------------------- Vesper
    public final float[] vesperDir = new float[3];
    /** The beam's heading around Vesper (radians) and the tangent frame it turns in. */
    public float beamAngle;
    public final float[] beamU = new float[3];
    public final float[] beamV = new float[3];
    public float vesperCore;
    public float vesperBeam;
    /** The beat's flicker, 0..1 (already silenced by weather). */
    public float vesperPulse;

    // ---------------------------------------------------------------- aurora
    public float aurora;

    // ---------------------------------------------------------------- fog (fractions of the render distance)
    public float fogStart;
    public float fogEnd;
    /** How much vertical distance counts, 0.05 to 1 (1 is vanilla's cylinder). */
    public float fogVertical;

    /** Seconds of animation time, for twinkle, streamers and ribbons. */
    public double seconds;
}
