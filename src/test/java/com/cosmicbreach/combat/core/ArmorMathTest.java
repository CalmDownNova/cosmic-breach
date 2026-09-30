package com.cosmicbreach.combat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.colossus.ColossusMoves;
import com.cosmicbreach.guardian.heliarch.HeliarchMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import org.junit.jupiter.api.Test;

/**
 * The balance rule for enemy damage (F1): each guardian's design table says what a hit comes to against that
 * layer's reference gear, and vanilla's armor formula, applied to the hit's damage in code, has to land within the
 * table's "about" (1 point) of it. The Unsung's first numbers failed this (its table had assumed armor takes half of
 * any hit; against armor 20 vanilla takes 70% of a 10-point one): they were raised to meet its table.
 */
class ArmorMathTest {
    private static final double ABOUT = 1.0;

    @Test
    void vanillasFormula() {
        // a 10-point hit on armor 20, no toughness: max(4, 20 - 5) = 15 of 25 taken off
        assertEquals(4.0, ArmorMath.afterArmor(10, 20, 0), 1e-9);
        // armor 0 takes nothing
        assertEquals(10.0, ArmorMath.afterArmor(10, 0, 0), 1e-9);
        // never more than 80% off
        assertEquals(0.2, ArmorMath.afterArmor(1, 30, 20), 1e-9);
        // big hits pierce more: a fifth of the armor always counts
        assertEquals(1000.0 * (1 - 4.0 / 25), ArmorMath.afterArmor(1000, 20, 0), 1e-9);
    }

    @Test
    void theInverseLandsOnItsTarget() {
        for (double target : new double[] {1, 3, 5, 7, 9, 12, 20}) {
            double d = ArmorMath.beforeArmorFor(target, 20, 8, 0.12);
            assertEquals(target, ArmorMath.taken(d, 20, 8, 0.12), 1e-6);
        }
    }

    private static void about(String what, double damage, double expected, double armor, double toughness, double reduction) {
        double got = ArmorMath.taken(damage, armor, toughness, reduction);
        assertTrue(Math.abs(got - expected) <= ABOUT, what + ": " + damage + " lands as " + got + ", the table says about " + expected);
    }

    @Test
    void thePrismColossusAgainstTheVanguard() {
        // armor 15, toughness 4, Resilience 8 (4%)
        about("Prism Slam", ColossusMoves.SLAM_DAMAGE, 9.4, 15, 4, 0.04);
        about("Facet Sweep", ColossusMoves.SWEEP_DAMAGE, 7, 15, 4, 0.04);
        about("Refraction", ColossusMoves.BEAM_DAMAGE, 3.5, 15, 4, 0.04);
        about("Prism Burst", ColossusMoves.BURST_DAMAGE, 4.5, 15, 4, 0.04);
    }

    @Test
    void theLeviathanAgainstTierTwo() {
        // armor 17, toughness 6, Resilience 16 (8%)
        about("Breach Dive", LeviathanMoves.DIVE_DAMAGE, 10, 17, 6, 0.08);
        about("the bite", LeviathanMoves.BITE_DAMAGE, 7, 17, 6, 0.08);
        about("Tail Flick", LeviathanMoves.FLICK_DAMAGE, 6, 17, 6, 0.08);
        about("Scale Shed", LeviathanMoves.SCALE_DAMAGE, 4, 17, 6, 0.08);
        about("Moorage shudder", LeviathanMoves.SHUDDER_DAMAGE, 3, 17, 6, 0.08);
    }

    @Test
    void theUnsungAgainstTierThree() {
        // armor 20, toughness 8, Resilience 24 (12%)
        about("Homing Notes", UnsungMoves.NOTE_DAMAGE, 5, 20, 8, 0.12);
        about("Sweeping Wave", UnsungMoves.WAVE_DAMAGE, 7, 20, 8, 0.12);
        about("Ground Ripples", UnsungMoves.RIPPLE_DAMAGE, 3, 20, 8, 0.12);
        about("Bass Drop", UnsungMoves.DROP_DAMAGE, 9, 20, 8, 0.12);
        about("Harmonize", UnsungMoves.HARMONIZE_DAMAGE, 9, 20, 8, 0.12);
    }

    @Test
    void theHeliarchAgainstTierThreeAtResilienceThirty() {
        // armor 20, toughness 10, Resilience 30 (15%)
        about("Sunderfall", HeliarchMoves.SUNDER_DAMAGE, 8.4, 20, 10, 0.15);
        about("Corona Sweep", HeliarchMoves.SWEEP_DAMAGE, 6.4, 20, 10, 0.15);
        about("Solar Lance", HeliarchMoves.LANCE_DAMAGE, 5.5, 20, 10, 0.15);
        about("Halo Shed", HeliarchMoves.SHED_DAMAGE, 3.9, 20, 10, 0.15);
        about("Eclipse Beam", HeliarchMoves.BEAM_DAMAGE, 2.5, 20, 10, 0.15);
        about("Tendril Lash", HeliarchMoves.LASH_DAMAGE, 4.6, 20, 10, 0.15);
        about("Star Seeds", HeliarchMoves.SEED_DAMAGE, 3.1, 20, 10, 0.15);
        about("Solar Rain", HeliarchMoves.RAIN_DAMAGE, 3.9, 20, 10, 0.15);
    }
}
