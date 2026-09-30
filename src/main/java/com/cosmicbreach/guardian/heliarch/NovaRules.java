package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.guardian.GuardianHealth;

/**
 * Nova, the hard DPS check (GDD 7.3): at 40% health the Heliarch channels for {@value HeliarchMoves#NOVA_CHANNEL}
 * ticks behind a Corona Shield (250, scaled like its health). Every hit on it goes into the shield. Break the shield
 * before the channel ends and it is stunned for {@value HeliarchMoves#NOVA_STUN} ticks with its heart exposed (x1.5);
 * let the channel end and Nova detonates (60 in the open, 60% less behind cover), and the channel comes back
 * {@value HeliarchMoves#NOVA_RETURN} ticks later. Its health cannot pass the 40% line until a Nova has been broken.
 * Pure: one channel's state, driven by the entity.
 */
public final class NovaRules {
    public enum Outcome { NONE, CHANNELLING, BROKEN, DETONATED }

    private long start = Long.MIN_VALUE;
    private double shield;
    private double shieldMax;
    private long stunnedUntil = Long.MIN_VALUE;
    private long nextChannel = Long.MIN_VALUE;
    private boolean broken;
    private int detonations;

    /** The shield for {@code players} players at the summon. */
    public static double shieldFor(int players) {
        return HeliarchMoves.SHIELD_BASE * GuardianHealth.factor(players);
    }

    /** A detonation's damage to one player: 60, or 24 behind cover (before armor and the soft enrage). */
    public static double detonation(boolean covered) {
        return HeliarchMoves.NOVA_DAMAGE * (covered ? HeliarchMoves.NOVA_COVERED : 1.0);
    }

    /** Starts a channel at {@code now}. */
    public void begin(long now, int players) {
        start = now;
        shieldMax = shieldFor(players);
        shield = shieldMax;
    }

    /** True while a channel runs. */
    public boolean channelling() {
        return start != Long.MIN_VALUE;
    }

    /** Ticks into the channel. */
    public long ticksIn(long now) {
        return channelling() ? now - start : -1;
    }

    public long start() {
        return start;
    }

    /** The shield left, 0 to 1 of its full size. */
    public double shieldFraction() {
        return shieldMax <= 0 ? 0.0 : Math.max(0.0, shield / shieldMax);
    }

    public double shield() {
        return shield;
    }

    public double shieldMax() {
        return shieldMax;
    }

    /** A hit of {@code amount} on the shield. */
    public void absorb(double amount) {
        if (channelling() && amount > 0) {
            shield = Math.max(0.0, shield - amount);
        }
    }

    /**
     * Where the channel stands at {@code now}: BROKEN when the shield is gone (the stun starts), DETONATED when the
     * time ran out first (the next channel is set), CHANNELLING otherwise; NONE with no channel. BROKEN and
     * DETONATED end the channel.
     */
    public Outcome tick(long now) {
        if (!channelling()) {
            return Outcome.NONE;
        }
        if (shield <= 0.0) {
            start = Long.MIN_VALUE;
            broken = true;
            stunnedUntil = now + HeliarchMoves.NOVA_STUN;
            return Outcome.BROKEN;
        }
        if (now - start >= HeliarchMoves.NOVA_CHANNEL) {
            start = Long.MIN_VALUE;
            detonations++;
            nextChannel = now + HeliarchMoves.NOVA_RETURN;
            return Outcome.DETONATED;
        }
        return Outcome.CHANNELLING;
    }

    /** True while stunned with the heart exposed. */
    public boolean stunned(long now) {
        return now < stunnedUntil;
    }

    public long stunnedUntil() {
        return stunnedUntil;
    }

    /** True once a Nova was broken: the fight may go on below 40%. */
    public boolean broken() {
        return broken;
    }

    /** True when a channel should start at {@code now}: health at the line, no channel, not broken, not waiting. */
    public boolean due(long now, double healthFraction) {
        return !broken && !channelling() && healthFraction <= HeliarchMoves.NOVA_AT + 1e-6 && now >= nextChannel;
    }

    /** The next channel may start at once (debug: skips the 30 s wait after a detonation). */
    public void skipWait() {
        nextChannel = Long.MIN_VALUE;
    }

    public long nextChannel() {
        return nextChannel;
    }

    public int detonations() {
        return detonations;
    }

    /** The damage multiplier on the core now: x1.5 while stunned. */
    public double taken(long now) {
        return stunned(now) ? HeliarchMoves.NOVA_EXPOSED : 1.0;
    }
}
