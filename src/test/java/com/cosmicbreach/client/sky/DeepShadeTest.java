package com.cosmicbreach.client.sky;

import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Deep's shade in the lightmap (W3c): the sky's light (its levels and its ambient glow) at the Deep's share. */
class DeepShadeTest {
    private static final float AMBIENT = 0.1f;
    private static final float FLICKER = 1.5f;

    @AfterEach
    void restore() {
        DeepShade.enabledForTest = true;
    }

    /** One lightmap cell as vanilla's LightTexture builds it, before weather, night vision and gamma. */
    private static Vector3f vanilla(float ambient, float skyLevel, int blockLevel, float daylight, float skyDarken, float darkenWorld) {
        Vector3f c = DeepShade.blockColour(DeepShade.brightness(ambient, blockLevel) * FLICKER, new Vector3f());
        float sky = DeepShade.brightness(ambient, skyLevel) * daylight;
        Vector3f tint = new Vector3f(skyDarken, skyDarken, 1f).lerp(new Vector3f(1f, 1f, 1f), 0.35f);
        c.add(tint.mul(sky));
        c.lerp(new Vector3f(0.75f, 0.75f, 0.75f), 0.04f);
        c.lerp(new Vector3f(c).mul(0.7f, 0.6f, 0.6f), darkenWorld);
        return c;
    }

    private static Vector3f shaded(int skyLevel, int blockLevel, float daylight, float skyDarken, float share, float darkenWorld) {
        Vector3f cell = vanilla(AMBIENT, skyLevel, blockLevel, daylight, skyDarken, darkenWorld);
        DeepShade.apply(cell, AMBIENT, blockLevel, skyLevel, DeepShade.brightness(AMBIENT, skyLevel) * daylight, skyDarken, FLICKER,
                share, darkenWorld);
        return cell;
    }

    @Test
    void theCurveIsVanillas() {
        assertEquals(1f, DeepShade.brightness(AMBIENT, 15), 1e-6f);
        assertEquals(AMBIENT, DeepShade.brightness(AMBIENT, 0), 1e-6f);
        assertEquals(0f, DeepShade.brightness(0f, 0), 1e-6f);
        float f = 6f / 15f;
        assertEquals(f / (4f - 3f * f), DeepShade.brightness(0f, 6), 1e-6f);
        assertEquals(0.2285714f, DeepShade.brightness(AMBIENT, 6), 1e-6f, "lerp(ambient, curve, 1)");
        Vector3f warm = DeepShade.blockColour(1.5f, new Vector3f());
        assertEquals(1.5f, warm.x, 1e-6f);
        assertEquals(1.5f * ((0.9f + 0.4f) * 0.6f + 0.4f), warm.y, 1e-6f);
        assertEquals(1.5f * (2.25f * 0.6f + 0.4f), warm.z, 1e-6f);
    }

    @Test
    void theShareFollowsTheDeepWeight() {
        assertEquals(1f, DeepShade.share(0.0), 1e-6f);
        assertEquals(0.4f, DeepShade.share(1.0), 1e-6f);
        assertEquals(0.7f, DeepShade.share(0.5), 1e-6f);
        DeepShade.enabledForTest = false;
        assertEquals(1f, DeepShade.share(1.0), 1e-6f, "off for a before-and-after check");
    }

    @Test
    void shadingACellIsDrawingItWithTheSkyAtItsShare() {
        float[][] times = {{1f, 1f}, {0.2f, 0.24f}, {0.6f, 0.62f}}; // skyDarken, daylight: noon, night, dusk
        for (float share : new float[] {0.4f, 0.7f, 0.95f}) {
            for (float[] t : times) {
                for (float darken : new float[] {0f, 0.5f}) {
                    for (int sky = 0; sky < 16; sky++) {
                        for (int block = 0; block < 16; block++) {
                            Vector3f cell = shaded(sky, block, t[1], t[0], share, darken);
                            Vector3f want = vanilla(AMBIENT * share, sky * share, block, t[1], t[0], darken);
                            String at = "share " + share + ", sky " + sky + ", block " + block + ", daylight " + t[1];
                            assertEquals(want.x, cell.x, 1e-5f, at);
                            assertEquals(want.y, cell.y, 1e-5f, at);
                            assertEquals(want.z, cell.z, 1e-5f, at);
                        }
                    }
                }
            }
        }
    }

    @Test
    void torchesAndAbilitiesKeepTheirLight() {
        for (int sky : new int[] {0, 15}) {
            Vector3f torch = shaded(sky, 14, 1f, 1f, 0.4f, 0f);
            assertTrue(torch.x >= 1f && torch.y >= 1f && torch.z >= 1f, "a torch's light still saturates, sky " + sky + ": " + torch);
        }
        Vector3f ability = shaded(0, 12, 1f, 1f, 0.4f, 0f);
        Vector3f before = vanilla(AMBIENT, 0, 12, 1f, 1f, 0f);
        // before gamma; vanilla's default gamma lifts both to within 5% of each other
        assertTrue(ability.x > 0.85f * before.x, "an ability's light 12 keeps its strength: " + ability + " against " + before);
        Vector3f lichenLit = shaded(15, 6, 1f, 1f, 0.4f, 0f);
        Vector3f open = shaded(15, 0, 1f, 1f, 0.4f, 0f);
        assertTrue(lichenLit.y > 1.5f * open.y, "ground by lichen stands out from the open dark: " + lichenLit + " against " + open);
    }

    @Test
    void theDeepIsDarkerAndNightDarkerStill() {
        Vector3f reachNoon = vanilla(AMBIENT, 15, 0, 1f, 1f, 0f);
        Vector3f deepNoon = shaded(15, 0, 1f, 1f, 0.4f, 0f);
        Vector3f deepNight = shaded(15, 0, 0.24f, 0.2f, 0.4f, 0f);
        Vector3f cave = vanilla(AMBIENT, 0, 0, 1f, 1f, 0f);
        Vector3f deepCave = shaded(0, 0, 1f, 1f, 0.4f, 0f);
        assertTrue(deepNoon.y < 0.3f * reachNoon.y, "open ground in the Deep at noon " + deepNoon + " against the Reach's " + reachNoon);
        assertTrue(deepNight.y < deepNoon.y, "night in the Deep is darker still");
        assertTrue(deepCave.y < 0.6f * cave.y, "unlit rock in the Deep is darker than unlit rock above: " + deepCave + " against " + cave);
        Vector3f untouched = vanilla(AMBIENT, 15, 0, 1f, 1f, 0f);
        DeepShade.apply(untouched, AMBIENT, 0, 15, 1f, 1f, FLICKER, 1f, 0f);
        assertEquals(reachNoon, untouched, "the Reach and the Drift (share 1) are left as they are");
    }
}
