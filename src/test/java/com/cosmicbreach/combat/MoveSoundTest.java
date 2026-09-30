package com.cosmicbreach.combat;

import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.CombatDataTestAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSoundTest {

    private static MoveSound of(CombatData data, String move) {
        return MoveSound.forMove(data.move(CombatDataTestAccess.id("meridian/" + move)));
    }

    @Test
    void meridiansMovesSoundTheirWeight() {
        CombatData data = CombatDataTestAccess.meridian();
        assertEquals(MoveSound.SWING_LIGHT, of(data, "l1"));
        assertEquals(MoveSound.SWING_LIGHT, of(data, "l2"));
        assertEquals(MoveSound.SWING_HEAVY, of(data, "l3"), "Impact 14");
        assertEquals(MoveSound.SWING_HEAVY, of(data, "pass"), "Impact 10");
        assertEquals(MoveSound.CHARGE_RELEASE, of(data, "line"));
        assertEquals(MoveSound.ZENITH, of(data, "zenith"));
        assertEquals(MoveSound.NONE, of(data, "falling_star"), "a plunge whistles at its start and booms at its landing");
    }

    @Test
    void swingsVaryTheirPitchALittleAndTheRestNot() {
        for (MoveSound sound : new MoveSound[] {MoveSound.SWING_LIGHT, MoveSound.SWING_HEAVY}) {
            float low = sound.pitch(0f);
            float high = sound.pitch(0.999f);
            assertTrue(high - low > 0.1f && high - low <= 2 * MoveSound.PITCH_SPREAD + 1e-6f, sound + " spreads its pitch");
        }
        assertEquals(MoveSound.ZENITH.pitch(0f), MoveSound.ZENITH.pitch(0.9f));
        assertEquals(MoveSound.CHARGE_RELEASE.pitch(0f), MoveSound.CHARGE_RELEASE.pitch(0.9f));
    }
}
