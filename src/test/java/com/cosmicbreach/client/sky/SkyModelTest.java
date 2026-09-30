package com.cosmicbreach.client.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyModelTest {
    private static final double[] REACH = {1, 0, 0};
    private static final double[] DRIFT = {0, 1, 0};
    private static final double[] DEEP = {0, 0, 1};

    private static SkyFrame frame(double dayTime, double[] layers) {
        return frame(dayTime, layers, new SkyModel.Weather());
    }

    private static SkyFrame frame(double dayTime, double[] layers, SkyModel.Weather weather) {
        SkyFrame f = new SkyFrame();
        SkyModel.compute(f, dayTime, (long) dayTime, 0f, layers, 192.0, 10.0, weather);
        return f;
    }

    private static double elevation(float[] d) {
        return Math.toDegrees(Math.asin(d[1]));
    }

    @Test
    void theClockIsVanillas() {
        assertEquals(0.0, SkyModel.timeOfDay(6000), 1e-9, "noon");
        assertEquals(0.5, SkyModel.timeOfDay(18000), 1e-9, "midnight");
        assertEquals(1.0, SkyModel.daylight(SkyModel.timeOfDay(6000)), 1e-9);
        assertEquals(0.0, SkyModel.daylight(SkyModel.timeOfDay(18000)), 1e-9);
    }

    @Test
    void solenneRisesInTheEastPeaksHighInTheSouthAndSetsInTheWest() {
        SkyFrame noon = frame(6000, REACH);
        assertEquals(68.0, elevation(noon.sunDir), 0.01, "noon: 68 degrees up");
        assertTrue(noon.sunDir[2] > 0.3, "towards the south");
        double[] sunset = SkyModel.sunDirection(Math.PI / 2);
        assertArrayEquals(new double[] {-1, 0, 0}, sunset, 1e-9, "the path meets the horizon due west");
        double[] sunrise = SkyModel.sunDirection(Math.PI * 1.5);
        assertArrayEquals(new double[] {1, 0, 0}, sunrise, 1e-9, "and due east");
        SkyFrame dawn = frame(23210, REACH);
        assertTrue(dawn.sunDir[0] > 0.99 && Math.abs(dawn.sunDir[1]) < 0.02, "dawn (23210, vanilla's sunrise): on the eastern horizon");
    }

    @Test
    void thalassaSitsLowInTheWestOnTheSunsPath() {
        SkyFrame f = frame(6000, REACH);
        assertEquals(8.0, elevation(f.planetDir), 0.01);
        assertTrue(f.planetDir[0] < -0.95, "west");
        assertEquals(20.0, Math.toDegrees(f.planetRadius) * 2, 0.01, "20 degrees across");
        double theta = SkyModel.thetaForElevation(Math.toRadians(8.0));
        assertArrayEquals(SkyModel.sunDirection(theta), new double[] {f.planetDir[0], f.planetDir[1], f.planetDir[2]}, 1e-6);
        SkyFrame deep = frame(6000, DEEP);
        assertTrue(deep.planetRadius > f.planetRadius && elevation(deep.planetDir) > elevation(f.planetDir),
                "descending, Thalassa rises and grows");
    }

    @Test
    void atNightSolenneHidesBehindThalassaAndTheHaloShines() {
        SkyFrame noon = frame(6000, REACH);
        assertEquals(0.0, noon.eclipse, 1e-9);
        assertEquals(1.0, noon.sunVisible, 1e-6);
        for (double t : new double[] {12600, 15000, 18000, 21000}) {
            SkyFrame night = frame(t, REACH);
            assertEquals(1.0, night.eclipse, 1e-9, "eclipsed at " + t);
            assertEquals(0.0, night.sunVisible, 1e-9);
            assertTrue(night.planetLight[3] > 0.5, "backlit halo at " + t);
        }
        SkyFrame dusk = frame(11500, REACH);
        assertTrue(dusk.eclipse > 0.0 && dusk.eclipse < 1.0, "at dusk it slides behind: partly covered, " + dusk.eclipse);
        SkyFrame beforeDawn = frame(22600, REACH);
        assertEquals(0.0, beforeDawn.planetLight[3], 1e-6, "the halo has faded before Solenne rises in the east");
        assertEquals(0.0, beforeDawn.sunVisible, 1e-6, "and Solenne is still under the horizon");
        assertTrue(frame(21800, REACH).planetLight[3] < frame(21000, REACH).planetLight[3], "the halo fades towards dawn");
    }

    @Test
    void theReachIsDaylightBlueAndTheNightNeverBlack() {
        SkyFrame noon = frame(6000, REACH);
        assertTrue(noon.zenith[2] > 0.8 && noon.zenith[2] > noon.zenith[0] + 0.4, "noon zenith: bright blue");
        assertTrue(noon.mid[2] > 0.9, "the mid sky is bright");
        SkyFrame night = frame(18000, REACH);
        float brightest = Math.max(night.zenith[0], Math.max(night.zenith[1], night.zenith[2]));
        assertTrue(brightest > 0.15, "night zenith is not black: " + brightest);
        assertTrue(night.zenith[2] > night.zenith[0] && night.zenith[0] > night.zenith[1] * 0.9, "and blue-violet");
        SkyFrame deep = frame(6000, DEEP);
        float deepest = Math.max(deep.zenith[0], Math.max(deep.zenith[1], deep.zenith[2]));
        assertTrue(deepest < 0.3 && deep.zenith[2] > deep.zenith[1], "the Deep is dark indigo even at noon");
        assertTrue(deep.nebula[0] > 0.8, "and its nebula glows through");
        assertTrue(noon.nebula[0] < 0.2 && noon.nebula[3] > 0.5, "by day the Reach's nebula is a veil, not light");
    }

    @Test
    void fogColoursAndDistancesPerLayer() {
        assertRgb(0xF3E6C4, frame(6000, REACH).horizon);
        assertRgb(0xA8D8E8, frame(6000, DRIFT).horizon);
        assertRgb(0x25123F, frame(6000, DEEP).horizon);
        SkyFrame r = frame(6000, REACH);
        assertEquals(0.55, r.fogStart, 1e-6);
        assertEquals(1.0, r.fogEnd, 1e-6);
        assertEquals(0.45, frame(6000, DRIFT).fogStart, 1e-6);
        SkyFrame d = frame(6000, DEEP);
        assertEquals(0.25, d.fogStart, 1e-6);
        assertEquals(0.8, d.fogEnd, 1e-6);
    }

    @Test
    void fogTakesTheSkysColourWhereTheCameraLooks() {
        SkyFrame f = frame(6000, REACH);
        float[] level = new float[3];
        SkyModel.fogColour(f, 0.0, level);
        assertRgb(0xF3E6C4, level);
        float[] down = new float[3];
        SkyModel.fogColour(f, -0.9, down);
        assertTrue(down[2] > down[0] && down[0] < 0.6, "looking down, the fog is the lower sky's violet");
        float[] up = new float[3];
        SkyModel.fogColour(f, 0.8, up);
        assertTrue(up[2] > up[0] + 0.3, "looking up, the sky's blue");
    }

    @Test
    void verticalFogLetsTheReachSeeTheDeep() {
        double k = SkyModel.fogVertical(REACH, 192.0);
        assertEquals(0.55 * 192 / 330, k, 1e-9);
        // the Deep's glow 300 blocks below counts as less than the fog's start
        assertTrue(300 * 0.05 * (SkyModel.fogShapeIndex(k) - 1) < 0.55 * 192);
        assertEquals(21, SkyModel.fogShapeIndex(1.0), "1 is vanilla's cylinder");
        assertEquals(2, SkyModel.fogShapeIndex(0.01), "never below 0.05");
        assertEquals(7, SkyModel.fogShapeIndex(0.32));
        assertEquals(0.75, SkyModel.fogVertical(DEEP, 192.0), 1e-9, "the Deep closes in all round");
    }

    @Test
    void layersCrossFadeThroughTheShearBands() {
        assertArrayEquals(new double[] {1, 0, 0}, SkyLayers.targetWeights(400), 1e-9);
        assertArrayEquals(new double[] {0.5, 0.5, 0}, SkyLayers.targetWeights(310), 1e-9);
        assertArrayEquals(new double[] {0, 1, 0}, SkyLayers.targetWeights(250), 1e-9);
        assertArrayEquals(new double[] {0, 0.5, 0.5}, SkyLayers.targetWeights(152.5), 1e-9);
        assertArrayEquals(new double[] {0, 0, 1}, SkyLayers.targetWeights(50), 1e-9);
    }

    @Test
    void aTeleportFadesOverThreeSeconds() {
        SkyLayers layers = new SkyLayers();
        layers.update(400, 0.0);
        double t = 0;
        double half = -1;
        while (layers.shown()[1] < 1.0 - 1e-9 && t < 10) {
            layers.update(250, 0.05);
            t += 0.05;
            if (half < 0 && layers.shown()[1] >= 0.5) {
                half = t;
            }
        }
        assertEquals(3.0, t, 0.051, "a whole layer change takes 3 s");
        assertEquals(1.5, half, 0.051);
        layers.reset();
        layers.update(50, 1.0);
        assertArrayEquals(new double[] {0, 0, 1}, layers.shown(), 1e-9, "after a reset (a new level) it jumps");
    }

    @Test
    void eclipseCoverage() {
        double r = Math.toRadians(5);
        double big = Math.toRadians(10);
        assertEquals(0.0, SkyModel.overlapFraction(r + big + 1e-6, r, big), 1e-12);
        assertEquals(1.0, SkyModel.overlapFraction(big - r - 1e-6, r, big), 1e-12);
        double halfway = SkyModel.overlapFraction(big, r, big);
        assertTrue(halfway > 0.4 && halfway < 0.5, "centre on the rim: a little under half, " + halfway);
        double prev = 1.0;
        for (double d = big - r; d <= big + r; d += r / 20) {
            double c = SkyModel.overlapFraction(d, r, big);
            assertTrue(c <= prev + 1e-12, "falls as the sun moves out");
            prev = c;
        }
    }

    @Test
    void vesperCirclesThePoleAndFollowsTheBeat() {
        double[] pole = SkyModel.pole();
        for (double t = 0; t < 24000; t += 1500) {
            SkyFrame f = frame(t, REACH);
            double dot = f.vesperDir[0] * pole[0] + f.vesperDir[1] * pole[1] + f.vesperDir[2] * pole[2];
            assertEquals(14.0, Math.toDegrees(Math.acos(Math.min(1.0, dot))), 0.01);
            assertTrue(f.vesperDir[1] > 0.5, "always well above the horizon");
        }
        SkyFrame onBeat = frame(1200, REACH);
        SkyFrame offBeat = frame(1206, REACH);
        assertTrue(onBeat.vesperCore > offBeat.vesperCore, "brightest on the beat");
    }

    @Test
    void auroraAtSunsetOverTheReachOnly() {
        assertEquals(0.0, frame(6000, REACH).aurora, 1e-9);
        assertTrue(frame(13500, REACH).aurora > 0.8);
        assertEquals(0.0, frame(13500, DEEP).aurora, 1e-9);
        assertEquals(0.0, frame(20000, REACH).aurora, 1e-9);
    }

    @Test
    void weatherHooks() {
        SkyModel.Weather calm = new SkyModel.Weather();
        SkyModel.Weather surge = new SkyModel.Weather();
        surge.vesperSilence = 1.0;
        SkyFrame silent = frame(1200, REACH, surge);
        assertEquals(0.0, silent.vesperPulse, 1e-9);
        assertEquals(0.0, silent.vesperBeam, 1e-9);
        SkyModel.Weather flare = new SkyModel.Weather();
        flare.solenneFlare = 1.0;
        SkyFrame bright = frame(6000, REACH, flare);
        SkyFrame normal = frame(6000, REACH, calm);
        assertTrue(bright.discStrength > normal.discStrength && bright.glow[3] > normal.glow[3]);
        assertTrue(bright.haze[0] > normal.haze[0] && bright.haze[1] > normal.haze[1], "the sky's edge whitens");
    }

    @Test
    void theStarFieldHasItsCounts() {
        assertEquals(SkyStars.BRIGHT + SkyStars.FAINT, SkyStars.field().quads());
        assertEquals(SkyStars.CLUSTERS * (SkyStars.CLUSTER_MEMBERS + 2), SkyStars.clusters().quads());
        assertEquals(3 * 56, SkyAurora.ribbons().quads());
    }

    private static void assertRgb(int hex, float[] rgb) {
        assertEquals(((hex >> 16) & 0xFF) / 255.0, rgb[0], 0.002);
        assertEquals(((hex >> 8) & 0xFF) / 255.0, rgb[1], 0.002);
        assertEquals((hex & 0xFF) / 255.0, rgb[2], 0.002);
    }
}
