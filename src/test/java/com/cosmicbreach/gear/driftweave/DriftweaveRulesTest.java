package com.cosmicbreach.gear.driftweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Driftweave's numbers (GDD 5.1) and Drift's jump. */
class DriftweaveRulesTest {
    @Test
    void theTableMatchesTheDesign() {
        assertEquals(1, DriftweaveRules.DASH_CHARGES);
        assertEquals(1.2, DriftweaveRules.DASH_DISTANCE, 1e-12);
        assertEquals(60, DriftweaveRules.AFTERIMAGE_TICKS);
        assertEquals(3, DriftweaveRules.AFTERIMAGE_REPEATS);
        assertEquals(0.4, DriftweaveRules.AFTERIMAGE_DAMAGE, 1e-12);
        assertEquals(560, DriftweaveRules.DRIFT_COOLDOWN);
        assertEquals(100, DriftweaveRules.DRIFT_TICKS);
        assertEquals(0.4, DriftweaveRules.DRIFT_GRAVITY, 1e-12);
        assertEquals(1.2, DriftweaveRules.AERIAL_DAMAGE, 1e-12);
    }

    @Test
    void aVanillaJumpRisesItsKnownHeight() {
        assertEquals(1.2522, DriftweaveRules.jumpApex(0.42, 0.08), 5e-4, "vanilla's jump tops out at 1.2522 blocks");
    }

    @Test
    void driftsJumpRisesHalfAgainAsHighAsTheLowGravityAlone() {
        double gravity = DriftweaveRules.VANILLA_GRAVITY * DriftweaveRules.DRIFT_GRAVITY;
        double alone = DriftweaveRules.jumpApex(DriftweaveRules.VANILLA_JUMP, gravity);
        double drift = DriftweaveRules.jumpApex(DriftweaveRules.VANILLA_JUMP * DriftweaveRules.driftJumpMultiplier(), gravity);
        assertEquals(1.5, drift / alone, 2e-3);
        assertEquals(Math.sqrt(1.5), DriftweaveRules.driftJumpMultiplier(), 0.05,
                "near sqrt(1.5) (height goes with speed squared); the air drag over a long low-gravity rise asks a little more");
        assertTrue(alone > 2.5, "0.4x gravity alone already lifts a jump past two and a half blocks: " + alone);
    }

    @Test
    void anAfterimageHitsForFortyPercent() {
        assertEquals(2.4, DriftweaveRules.afterimageDamage(6.0), 1e-12);
    }

    @Test
    void aerialMeansAPlungeOrOffTheGround() {
        assertTrue(DriftweaveRules.aerial(true, true), "a plunge lands on the ground but is the aerial moveset");
        assertTrue(DriftweaveRules.aerial(false, false));
        assertFalse(DriftweaveRules.aerial(false, true));
    }
}
