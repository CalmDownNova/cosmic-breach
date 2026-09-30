package com.cosmicbreach.combat.server;

/**
 * Decides when the server sends a player its authoritative Resonance, dash charges and ability
 * cooldown: when something changed that the client can't predict, and every 10 ticks regardless.
 * The client predicts Resonance drift and the cooldown counting down, so those alone don't count
 * as changes. Pure, no world access.
 */
public final class SyncThrottle {
    public static final int INTERVAL_TICKS = 10;
    /** Resonance moving by this much since the last send counts as a change. */
    public static final double RESONANCE_STEP = 1.0;

    private boolean sentOnce;
    private int ticksSinceSend;
    private double resonance;
    private int dashCharges;
    private int cooldown;

    /** Called once per tick with the current numbers; true means send them now. */
    public boolean update(double resonance, int dashCharges, int cooldown) {
        ticksSinceSend++;
        int predictedCooldown = Math.max(0, this.cooldown - ticksSinceSend);
        boolean changed = !sentOnce
                || dashCharges != this.dashCharges
                || Math.abs(resonance - this.resonance) >= RESONANCE_STEP
                || cooldown != predictedCooldown;
        if (!changed && ticksSinceSend < INTERVAL_TICKS) {
            return false;
        }
        sentOnce = true;
        ticksSinceSend = 0;
        this.resonance = resonance;
        this.dashCharges = dashCharges;
        this.cooldown = cooldown;
        return true;
    }
}
