package com.cosmicbreach.client.sky;

import com.cosmicbreach.world.VesperClock;

/**
 * The art direction of Aetheria's sky as numbers: where Solenne, Thalassa and Vesper are, what colour the
 * sky and fog are, per layer and time of day. Pure (no Minecraft types), unit tested, and dumped by
 * {@code SkyModelDump} (src/test) for the offline preview ({@code tools/sky/preview.py}).
 *
 * <p><b>The day.</b> Aetheria shares the Overworld's clock. Solenne rises in the east and crosses the sky on
 * a path tilted 22 degrees to the south (so noon is 68 degrees up, not overhead), as fast as the vanilla sun.
 * Thalassa hangs low in the west exactly on that path. At dusk Solenne slides behind it and stays there all
 * night: the eclipse. The planet's air lights up as a halo, the rings glow, the corona peeks out round the
 * edge, and the sky turns deep blue-violet, never black. Just before dawn the halo fades and Solenne rises in
 * the east again. The sky's palettes follow vanilla's sky brightness curve, so the sky dims with the world's
 * light.
 *
 * <p><b>The layers.</b> Each layer has its palette (day, sunset, night), nebula, and sizes. Descending,
 * Thalassa rises and grows (8 degrees up and 20 across in the Reach, 20 up and 28 across in the Deep); the
 * Reach is bright daylight blue with a cream horizon (the fog colour), the Drift silver-cyan, the Deep dark
 * indigo with its nebula glowing through. The layer weights come from the camera's height, smoothed by
 * {@link SkyLayers}.
 */
public final class SkyModel {
    public static final int REACH = 0;
    public static final int DRIFT = 1;
    public static final int DEEP = 2;
    public static final int DAY = 0;
    public static final int SUNSET = 1;
    public static final int NIGHT = 2;

    /** Solenne's path is tilted this far to the south. */
    public static final double TILT = Math.toRadians(22.0);
    /** The celestial pole: 58 degrees up in the north. The stars, clusters and nebula turn around it once a day. */
    public static final double POLE_ELEVATION = Math.toRadians(58.0);
    /** Solenne's disc radius (the disc is 1.3 times vanilla's across; with the corona it reads about 3 times). */
    public static final double SUN_RADIUS = Math.toRadians(5.6);
    /** Where the sun stops hiding behind Thalassa and jumps to its dawn path, below the eastern horizon. */
    static final double DAWN_SWITCH = Math.PI * 1.42;

    /** Thalassa per layer: elevation, angular radius (degrees). */
    private static final double[] PLANET_ELEVATION = {8.0, 16.0, 20.0};
    private static final double[] PLANET_RADIUS = {10.0, 13.0, 14.0};

    /** Fog per layer, as fractions of the render distance (GDD 2.3). */
    public static final double[] FOG_START = {0.55, 0.45, 0.25};
    public static final double[] FOG_END = {1.0, 1.0, 0.8};

    private SkyModel() {
    }

    // ================================================================ inputs

    /** Weather's say in the sky (W3b sets these; 0 is calm). */
    public static final class Weather {
        /** Solar Flare warning: Solenne brightens and the sky's edge whitens, 0..1. */
        public double solenneFlare;
        /** Eclipse Surge warning: Vesper stops pulsing and its beam dies, 0..1. */
        public double vesperSilence;
        /** Eclipse Surge: the sky dims (its colours, the fog's, and the sky light's share of the lightmap), 0..1. */
        public double skyDim;
        /** A fight that places Solenne (the Hollow Heliarch's eclipse, G9p): where, a unit direction, and how far, 0..1. */
        public final double[] solenneAt = {0.0, 1.0, 0.0};
        public double solenneAtWeight;
    }

    // ================================================================ the model

    /**
     * Fills {@code out} for the given clock ({@code dayTime} with the partial tick added, any value; the
     * level's game time for Vesper), smoothed layer weights (Reach, Drift, Deep), the render distance in
     * blocks and animation seconds.
     */
    public static void compute(SkyFrame out, double dayTime, long gameTime, float partialTick, double[] layerWeights,
                               double renderBlocks, double seconds, Weather weather) {
        double wr = layerWeights[REACH];
        double wd = layerWeights[DRIFT];
        double wp = layerWeights[DEEP];
        double sum = Math.max(wr + wd + wp, 1e-9);
        out.layers[REACH] = wr / sum;
        out.layers[DRIFT] = wd / sum;
        out.layers[DEEP] = wp / sum;
        out.seconds = seconds;
        double[] lw = out.layers;

        // ------------------------------------------------------------ time of day
        double a = timeOfDay(dayTime);
        double theta = a * Math.PI * 2.0;
        double light = daylight(a);
        out.daylight = light;
        double sunset = Math.pow(Math.sin(Math.PI * light), 2.0);
        out.times[DAY] = light * (1.0 - sunset);
        out.times[SUNSET] = sunset;
        out.times[NIGHT] = (1.0 - light) * (1.0 - sunset);
        double[] tw = out.times;

        // ------------------------------------------------------------ colours
        for (int i = 0; i < 3; i++) {
            out.zenith[i] = blend(SkyPalette.ZENITH, lw, tw, i);
            out.mid[i] = blend(SkyPalette.MID, lw, tw, i);
            out.haze[i] = blend(SkyPalette.HAZE, lw, tw, i);
            out.horizon[i] = blend(SkyPalette.HORIZON, lw, tw, i);
            out.low[i] = blend(SkyPalette.LOW, lw, tw, i);
            out.nadir[i] = blend(SkyPalette.NADIR, lw, tw, i);
        }
        double flare = clamp01(weather.solenneFlare);
        if (flare > 0) {
            // the sky bleaches before a flare: its edge to white first, then up towards the zenith, warming to gold
            double[] hot = {1.0, 0.97, 0.84};
            for (int i = 0; i < 3; i++) {
                out.haze[i] = (float) lerp(out.haze[i], i < 2 ? 1.0 : hot[2], 0.7 * flare);
                out.horizon[i] = (float) lerp(out.horizon[i], i < 2 ? 1.0 : hot[2], 0.6 * flare);
                out.mid[i] = (float) lerp(out.mid[i], hot[i], 0.5 * flare);
                out.zenith[i] = (float) lerp(out.zenith[i], hot[i] * 0.9, 0.3 * flare);
                out.low[i] = (float) lerp(out.low[i], hot[i], 0.35 * flare);
            }
        }

        double dim = clamp01(weather.skyDim);
        if (dim > 0) {
            // an Eclipse Surge darkens the whole dome
            float keep = (float) (1.0 - 0.55 * dim);
            for (int i = 0; i < 3; i++) {
                out.zenith[i] *= keep;
                out.mid[i] *= keep;
                out.haze[i] *= keep;
                out.horizon[i] *= keep;
                out.low[i] *= keep;
                out.nadir[i] *= keep;
            }
        }

        // ------------------------------------------------------------ the celestial sphere
        double spin = (floorMod(dayTime, 24000.0) / 24000.0) * Math.PI * 2.0;
        double[] pole = pole();
        rotation(pole, -spin, out.skyRot);
        out.nebula[0] = (float) mix(SkyPalette.NEBULA_LIGHT, lw, tw);
        out.nebula[1] = (float) mix(SkyPalette.NEBULA_DUST, lw, tw);
        out.nebula[2] = (float) mix(SkyPalette.NEBULA_BELOW, lw, tw);
        out.nebula[3] = (float) mix(SkyPalette.NEBULA_VEIL, lw, tw);
        pickNebula(lw, out);
        out.stars[0] = (float) mix(SkyPalette.STARS, lw, tw);
        out.stars[1] = 0.35f;
        out.stars[2] = (float) (lw[REACH] * 0.35 + lw[DRIFT] * 0.6 + lw[DEEP] * 0.8);
        if (dim > 0) {
            // and its nebula and stars fade with it
            out.nebula[0] *= (float) (1.0 - 0.6 * dim);
            out.nebula[1] *= (float) (1.0 - 0.6 * dim);
            out.stars[0] *= (float) (1.0 - 0.4 * dim);
        }

        // ------------------------------------------------------------ Thalassa and Solenne
        double planetElev = Math.toRadians(dot3(PLANET_ELEVATION, lw));
        double planetRadius = Math.toRadians(dot3(PLANET_RADIUS, lw));
        double thetaT = thetaForElevation(planetElev);
        double[] planet = sunDirection(thetaT);
        copy(planet, out.planetDir);
        out.planetRadius = (float) planetRadius;
        // the axis leans towards the viewer and to the side, so the rings open as a wide ellipse
        double[] axis = normalize(new double[] {0.28, 0.9, 0.34});
        copy(axis, out.ringAxis);

        boolean stalled = theta >= thetaT && theta <= DAWN_SWITCH;
        double thetaSun = stalled ? thetaT : theta;
        double[] sun = sunDirection(thetaSun);
        copy(sun, out.sunDir);
        out.sunRadius = (float) SUN_RADIUS;
        double sep = angle(sun, planet);
        double cover = stalled ? 1.0 : overlapFraction(sep, SUN_RADIUS, planetRadius);
        out.eclipse = (float) cover;
        double horizonMask = smoothstep(-0.035, 0.012, sun[1]);
        out.sunVisible = (float) ((1.0 - cover) * horizonMask);

        // the night: how far through the eclipse (0 at totality, 1 at the dawn switch)
        double night = stalled ? clamp01((theta - thetaT) / (DAWN_SWITCH - thetaT)) : 0.0;
        double dawnFade = stalled ? 1.0 - smoothstep(0.86, 1.0, night) : 0.0;
        double approach = smoothstep(planetRadius + Math.toRadians(14.0), planetRadius - SUN_RADIUS * 0.5, sep);
        double backlight = stalled ? lerp(1.0, 0.72, smoothstep(0.0, 0.35, night)) * dawnFade : approach;

        double sunStrength = 1.0 + 0.8 * flare;
        out.discStrength = (float) (mix3(SkyPalette.DISC, lw) * sunStrength);
        double coronaDay = mix3(SkyPalette.CORONA, lw) * horizonMask * (1.0 + 1.6 * flare);
        double coronaNight = mix3(SkyPalette.CORONA, lw) * lerp(0.9, 0.6, smoothstep(0.0, 0.4, night)) * dawnFade;
        out.coronaStrength = (float) (stalled ? coronaNight : Math.max(coronaDay, coronaNight * approach));

        // glare: warm and wide when the sun is low; a faint glow round Thalassa through the night
        double high = smoothstep(0.0, 0.45, sun[1]);
        out.glow[0] = 1.0f;
        out.glow[1] = (float) lerp(0.68, 0.95, high);
        out.glow[2] = (float) lerp(0.40, 0.84, high);
        double glare = mix3(SkyPalette.GLARE, lw) * (1.0 + 3.0 * flare);
        double glareStrength = stalled ? 0.12 * dawnFade * mix3(SkyPalette.GLARE, lw) / 0.34 : glare * out.sunVisible * lerp(1.35, 1.0, high);
        out.glow[3] = (float) glareStrength;

        // light on the planet: the real sun by day; behind it through the night (its near side dark)
        double direct;
        if (stalled) {
            direct = smoothstep(0.25, 0.0, night);
        } else if (theta > DAWN_SWITCH && theta < Math.PI * 1.5) {
            direct = smoothstep(DAWN_SWITCH, Math.PI * 1.5, theta);
        } else {
            direct = 1.0;
        }
        copy(sun, out.planetLightDir);
        double planetLight = mix3(SkyPalette.PLANET_LIGHT, lw) * direct;
        out.planetLight[0] = (float) planetLight;
        out.planetLight[1] = (float) (planetLight * lerp(0.86, 0.97, high));
        out.planetLight[2] = (float) (planetLight * lerp(0.72, 0.9, high));
        out.planetLight[3] = (float) (backlight * (lw[DEEP] > 0 ? 1.0 - 0.4 * lw[DEEP] : 1.0));
        out.planetParams[0] = (float) lerp(mix3(SkyPalette.PLANET_OPACITY_DAY, lw), 1.0, 1.0 - light);
        out.planetParams[1] = (float) mix(SkyPalette.PLANET_INSCATTER, lw, tw);
        out.planetParams[2] = 1.0f;
        out.planetParams[3] = 1.0f;
        out.planetAmbient[0] = (float) lerp(0.05, 0.018, 1.0 - light);
        out.planetAmbient[1] = (float) lerp(0.06, 0.024, 1.0 - light);
        out.planetAmbient[2] = (float) lerp(0.08, 0.05, 1.0 - light);
        out.bandDrift = (float) (seconds * 0.0015 % 1.0);

        // ------------------------------------------------------------ Vesper
        double[] vesperC = vesperCelestial();
        double[] vesper = apply(out.skyRot, vesperC);
        copy(vesper, out.vesperDir);
        double[] u = normalize(cross(vesper, new double[] {0.0, 1.0, 0.0}));
        double[] v = cross(vesper, u);
        copy(u, out.beamU);
        copy(v, out.beamV);
        out.beamAngle = (float) VesperClock.sweepRadians(gameTime, partialTick);
        double silence = clamp01(weather.vesperSilence);
        double pulse = VesperClock.pulse(gameTime, partialTick) * (1.0 - silence);
        out.vesperPulse = (float) pulse;
        double dark = 1.0 - light;
        double vesperBase = lerp(0.55, 1.0, Math.max(dark, lw[DEEP]));
        out.vesperCore = (float) (vesperBase * (0.55 + 0.45 * pulse) * (1.0 - 0.5 * silence));
        out.vesperBeam = (float) (lerp(0.16, 0.5, Math.max(dark, lw[DEEP] * 0.8)) * (0.7 + 0.3 * pulse) * (1.0 - silence));

        // ------------------------------------------------------------ aurora: over the Reach from sunset into the dusk
        double t = floorMod(dayTime, 24000.0);
        double aurora = smoothstep(11900.0, 12900.0, t) * (1.0 - smoothstep(14400.0, 15600.0, t));
        out.aurora = (float) (aurora * lw[REACH] * 0.9);

        // ------------------------------------------------------------ fog
        out.fogStart = (float) dot3(FOG_START, lw);
        out.fogEnd = (float) dot3(FOG_END, lw);
        out.fogVertical = (float) fogVertical(lw, renderBlocks);

        // ------------------------------------------------------------ a fight that places Solenne (G9p)
        double place = clamp01(weather.solenneAtWeight);
        if (place > 0) {
            double[] at = normalize(new double[] {lerp(out.sunDir[0], weather.solenneAt[0], place),
                    lerp(out.sunDir[1], weather.solenneAt[1], place), lerp(out.sunDir[2], weather.solenneAt[2], place)});
            copy(at, out.sunDir);
            out.sunVisible = (float) lerp(out.sunVisible, 1.0, place);
        }
    }

    /**
     * The sky's colour at elevation sine {@code h} (the dome shader's {@code sky_gradient}, without the glare),
     * into {@code out}. The fog takes it for the look direction, so terrain far below fades into the lower sky's
     * violet rather than the horizon's cream, as vanilla tints its fog towards a sunrise.
     */
    public static void skyColour(SkyFrame f, double h, float[] out) {
        for (int i = 0; i < 3; i++) {
            double c;
            if (h >= 0) {
                c = lerp(f.horizon[i], f.haze[i], smoothstep(0.0, 0.045, h));
                c = lerp(c, f.mid[i], smoothstep(0.03, 0.4, h));
                c = lerp(c, f.zenith[i], smoothstep(0.32, 1.0, h));
            } else {
                c = lerp(f.horizon[i], f.low[i], smoothstep(0.0, 0.2, -h));
                c = lerp(c, f.nadir[i], smoothstep(0.2, 1.0, -h));
            }
            out[i] = (float) c;
        }
    }

    /** The fog's colour for a look direction whose vertical component is {@code lookY}: the sky there, softened. */
    public static void fogColour(SkyFrame f, double lookY, float[] out) {
        skyColour(f, lookY * 0.8, out);
    }

    // ================================================================ the pieces (package-private for tests)

    /** Vanilla's celestial angle for a day time (DimensionType.timeOfDay without a fixed time): 0 at noon, 0.25 at sunset. */
    static double timeOfDay(double dayTime) {
        double d0 = frac(dayTime / 24000.0 - 0.25);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (d0 * 2.0 + d1) / 3.0;
    }

    /** Vanilla's sky brightness curve without its floor: 1 by day, 0 at night. */
    static double daylight(double timeOfDay) {
        return clamp01(Math.cos(timeOfDay * Math.PI * 2.0) * 2.0 + 0.2);
    }

    /** Where Solenne is at celestial angle {@code theta} (0 noon, pi/2 sunset in the west, 3pi/2 sunrise in the east). */
    static double[] sunDirection(double theta) {
        double s = Math.sin(theta);
        double c = Math.cos(theta);
        return new double[] {-s, c * Math.cos(TILT), c * Math.sin(TILT)};
    }

    /** The afternoon point of the sun's path at {@code elevation} (radians). */
    static double thetaForElevation(double elevation) {
        return Math.acos(clamp(Math.sin(elevation) / Math.cos(TILT), -1.0, 1.0));
    }

    /** Fraction of a disc of radius {@code r} covered by a disc of radius {@code big} whose centre is {@code d} away. */
    static double overlapFraction(double d, double r, double big) {
        if (d >= r + big) {
            return 0.0;
        }
        if (d <= big - r) {
            return 1.0;
        }
        if (d <= r - big) {
            return (big * big) / (r * r);
        }
        double r2 = r * r;
        double b2 = big * big;
        double alpha = Math.acos(clamp((d * d + r2 - b2) / (2.0 * d * r), -1.0, 1.0));
        double beta = Math.acos(clamp((d * d + b2 - r2) / (2.0 * d * big), -1.0, 1.0));
        double area = r2 * (alpha - Math.sin(2.0 * alpha) / 2.0) + b2 * (beta - Math.sin(2.0 * beta) / 2.0);
        return clamp01(area / (Math.PI * r2));
    }

    /** Which two nebula atlases to show (the two heaviest layers) and the cross-fade between them. */
    static void pickNebula(double[] lw, SkyFrame out) {
        int first = 0;
        for (int i = 1; i < 3; i++) {
            if (lw[i] > lw[first]) {
                first = i;
            }
        }
        int second = first == 0 ? 1 : 0;
        for (int i = 0; i < 3; i++) {
            if (i != first && lw[i] > lw[second]) {
                second = i;
            }
        }
        int a = Math.min(first, second);
        int b = Math.max(first, second);
        out.nebulaA = a;
        out.nebulaB = b;
        double total = lw[a] + lw[b];
        out.nebulaMix = total <= 1e-9 ? 0f : (float) (lw[b] / total);
    }

    /**
     * How much vertical distance counts in the fog (1 = vanilla's cylinder): small enough that the layers below
     * show through the Breach. From the Reach the Deep's glow 300 blocks down sits just where the fog begins;
     * from the Drift, the Deep 240 down; in the Deep the fog closes in on every side.
     */
    static double fogVertical(double[] lw, double renderBlocks) {
        double reach = clamp(FOG_START[REACH] * renderBlocks / 330.0, 0.1, 1.0);
        double drift = clamp(FOG_START[DRIFT] * renderBlocks / 240.0, 0.1, 1.0);
        double deep = 0.75;
        return reach * lw[REACH] + drift * lw[DRIFT] + deep * lw[DEEP];
    }

    /** The fog shape index for fogVertical: 2 + steps of 0.05 (the shader reads k = (index - 1) * 0.05). */
    public static int fogShapeIndex(double vertical) {
        int steps = (int) Math.round(clamp(vertical, 0.05, 1.0) / 0.05);
        return steps + 1;
    }

    /** The celestial pole (unit). */
    static double[] pole() {
        return new double[] {0.0, Math.sin(POLE_ELEVATION), -Math.cos(POLE_ELEVATION)};
    }

    /** Vesper in the celestial frame: 14 degrees from the pole, so it circles it and never sets. */
    static double[] vesperCelestial() {
        double[] p = pole();
        double k = Math.toRadians(14.0);
        return normalize(new double[] {Math.sin(k), p[1] * Math.cos(k), p[2] * Math.cos(k)});
    }

    // ================================================================ small maths

    private static float blend(int[][] table, double[] lw, double[] tw, int channel) {
        double v = 0.0;
        for (int l = 0; l < 3; l++) {
            if (lw[l] <= 0.0) {
                continue;
            }
            for (int t = 0; t < 3; t++) {
                if (tw[t] <= 0.0) {
                    continue;
                }
                int rgb = table[l][t];
                int c = (rgb >> (16 - 8 * channel)) & 0xFF;
                v += lw[l] * tw[t] * (c / 255.0);
            }
        }
        return (float) v;
    }

    private static double mix(double[][] table, double[] lw, double[] tw) {
        double v = 0.0;
        for (int l = 0; l < 3; l++) {
            for (int t = 0; t < 3; t++) {
                v += lw[l] * tw[t] * table[l][t];
            }
        }
        return v;
    }

    private static double mix3(double[] perLayer, double[] lw) {
        return dot3(perLayer, lw);
    }

    private static double dot3(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Row-major rotation by {@code angle} around unit {@code axis} (Rodrigues). */
    static void rotation(double[] axis, double angle, float[] m) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double t = 1.0 - c;
        double x = axis[0];
        double y = axis[1];
        double z = axis[2];
        m[0] = (float) (t * x * x + c);
        m[1] = (float) (t * x * y - s * z);
        m[2] = (float) (t * x * z + s * y);
        m[3] = (float) (t * x * y + s * z);
        m[4] = (float) (t * y * y + c);
        m[5] = (float) (t * y * z - s * x);
        m[6] = (float) (t * x * z - s * y);
        m[7] = (float) (t * y * z + s * x);
        m[8] = (float) (t * z * z + c);
    }

    static double[] apply(float[] m, double[] v) {
        return new double[] {
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    static double angle(double[] a, double[] b) {
        return Math.acos(clamp(a[0] * b[0] + a[1] * b[1] + a[2] * b[2], -1.0, 1.0));
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / n, v[1] / n, v[2] / n};
    }

    private static void copy(double[] from, float[] to) {
        to[0] = (float) from[0];
        to[1] = (float) from[1];
        to[2] = (float) from[2];
    }

    static double smoothstep(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static double floorMod(double v, double m) {
        return v - Math.floor(v / m) * m;
    }
}
