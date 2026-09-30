package com.cosmicbreach.client.combat;

/**
 * What the combat HUD shows, from the machine's numbers: the Resonance fill, one pip per dash
 * charge and the charge ring. Pure, so the rules are tested apart from the drawing.
 *
 * @param resonanceFill Resonance / max, 0 to 1
 * @param resonanceFull Resonance is at its max
 * @param dashCharges   charges ready
 * @param maxDashCharges pips to draw
 * @param rechargeFill  progress of the charge coming back, 0 to 1
 * @param charge        where a held charge is, {@link ChargeStage#NONE} when not charging
 * @param chargeFill    the ring's fill: the hold so far over the charged move's minimum, 0 to 1
 */
public record CombatHudModel(float resonanceFill, boolean resonanceFull, int dashCharges, int maxDashCharges,
                             float rechargeFill, ChargeStage charge, float chargeFill) {
    public enum ChargeStage { NONE, FILLING, READY, FULL }

    public enum Pip { FULL, RECHARGING, EMPTY }

    /**
     * @param heldTicks  ticks the attack button has been held (the machine counts from the press)
     * @param chargeMin  hold needed before a release fires the charged move
     * @param chargeFull hold for the strongest release
     */
    public static CombatHudModel of(double resonance, int maxResonance, int dashCharges, int maxDashCharges,
                                    double rechargeProgress, boolean charging, int heldTicks, int chargeMin, int chargeFull) {
        float fill = maxResonance <= 0 ? 0f : (float) clamp(resonance / maxResonance);
        boolean full = maxResonance > 0 && resonance >= maxResonance - 1e-6;
        int max = Math.max(0, maxDashCharges);
        int charges = Math.max(0, Math.min(max, dashCharges));
        float recharge = charges < max ? (float) clamp(rechargeProgress) : 0f;
        ChargeStage stage = ChargeStage.NONE;
        float ring = 0f;
        if (charging) {
            ring = chargeMin <= 0 ? 1f : (float) clamp(heldTicks / (double) chargeMin);
            if (heldTicks >= Math.max(chargeMin, chargeFull)) {
                stage = ChargeStage.FULL;
            } else if (heldTicks >= chargeMin) {
                stage = ChargeStage.READY;
            } else {
                stage = ChargeStage.FILLING;
            }
        }
        return new CombatHudModel(fill, full, charges, max, recharge, stage, ring);
    }

    /** The pip at {@code index}: ready, the one coming back, or waiting behind it. */
    public Pip pip(int index) {
        if (index < dashCharges) {
            return Pip.FULL;
        }
        return index == dashCharges && dashCharges < maxDashCharges ? Pip.RECHARGING : Pip.EMPTY;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
