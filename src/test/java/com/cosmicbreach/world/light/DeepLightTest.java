package com.cosmicbreach.world.light;

import com.cosmicbreach.client.sky.SkyLayers;
import com.cosmicbreach.entity.stalker.StalkerRules;
import com.cosmicbreach.world.ShearBand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Deep's share of sky light (W3c): one rule for the Stalker and the client's lightmap. */
class DeepLightTest {
    @Test
    void theBandIsShearBandB() {
        assertEquals(ShearBand.B.minY, DeepLight.BAND_LOW);
        assertEquals(ShearBand.B.maxY, DeepLight.BAND_HIGH);
        assertEquals(StalkerRules.DEEP_SKY_SHARE, DeepLight.DEEP_SKY_SHARE, "the Stalker and the lightmap share one constant");
    }

    @Test
    void fullSkyAboveTheBandTheDeepsShareBelowIt() {
        for (double y : new double[] {479, 350, 320, 300, 250, 170, 160}) {
            assertEquals(1.0, DeepLight.skyScale(y), 1e-12, "above band B at Y " + y);
        }
        for (double y : new double[] {145, 144, 143, 100, 64, 8, 0}) {
            assertEquals(0.4, DeepLight.skyScale(y), 1e-12, "in the Deep at Y " + y);
        }
        assertEquals(0.7, DeepLight.skyScale(152.5), 1e-12, "halfway through the band, halfway between");
    }

    @Test
    void theBandIsASmoothDescent() {
        double last = DeepLight.skyScale(170);
        double steepest = 0;
        for (double y = 170; y >= 135; y -= 0.25) {
            double s = DeepLight.skyScale(y);
            assertTrue(s <= last + 1e-12, "never brightens going down, Y " + y);
            steepest = Math.max(steepest, last - s);
            last = s;
        }
        // a smoothstep: no step bigger than its steepest slope (1.5 / 15 per block) allows, and flat at both edges
        assertTrue(steepest <= 0.6 * 1.5 / 15 * 0.25 + 1e-9, "no jump: " + steepest);
        assertTrue(DeepLight.skyScale(159.75) > 0.999, "eases in at the band's top");
        assertTrue(DeepLight.skyScale(145.25) < 0.401, "eases out at the band's bottom");
    }

    @Test
    void theRuleIsTheSkysOwnCrossFade() {
        // the client draws with the sky's eased Deep weight; its target is this rule's deepness
        for (double y = 130; y <= 175; y += 0.5) {
            assertEquals(SkyLayers.targetWeights(y)[2], DeepLight.deepness(y), 1e-12, "Y " + y);
            assertEquals(DeepLight.skyScale(y), DeepLight.shareAt(DeepLight.deepness(y)), 1e-12);
        }
        assertEquals(1.0, DeepLight.shareAt(-3), 1e-12, "clamped");
        assertEquals(0.4, DeepLight.shareAt(7), 1e-12, "clamped");
    }

    @Test
    void levelsRoundDownLikeTheStalkerCounts() {
        assertEquals(6, DeepLight.shade(15, 0.4));
        assertEquals(5, DeepLight.shade(14, 0.4));
        assertEquals(4, DeepLight.shade(10, 0.4));
        assertEquals(0, DeepLight.shade(0, 0.4));
        assertEquals(15, DeepLight.shade(15, 1.0));
        assertEquals(10, DeepLight.shade(15, 0.7));
        assertEquals(0, DeepLight.shade(-2, 0.4));
    }

    @Test
    void theStalkerSeesByTheSameRule() {
        assertEquals(6, StalkerRules.effectiveLight(0, 15, 0, DeepLight.skyScale(100)), "the Deep at noon: dark");
        assertTrue(StalkerRules.dark(StalkerRules.effectiveLight(0, 15, 0, DeepLight.skyScale(100)), 0));
        assertEquals(15, StalkerRules.effectiveLight(0, 15, 0, DeepLight.skyScale(200)), "the Drift at noon: lit");
        assertEquals(10, StalkerRules.effectiveLight(0, 15, 0, DeepLight.skyScale(152.5)), "mid band");
        assertEquals(1, StalkerRules.effectiveLight(0, 15, 11, DeepLight.skyScale(100)), "the Deep at midnight");
        assertEquals(12, StalkerRules.effectiveLight(12, 15, 0, DeepLight.skyScale(100)), "a torch is a torch anywhere");
        for (int sky = 0; sky <= 15; sky++) {
            for (int darken = 0; darken <= 11; darken++) {
                assertEquals(StalkerRules.effectiveLight(3, sky, darken, true),
                        StalkerRules.effectiveLight(3, sky, darken, DeepLight.skyScale(64)), "the old Deep rule, sky " + sky);
                assertEquals(StalkerRules.effectiveLight(3, sky, darken, false),
                        StalkerRules.effectiveLight(3, sky, darken, DeepLight.skyScale(300)), "the old open rule, sky " + sky);
            }
        }
    }
}
