package com.cosmicbreach.combat.core;

/** Timing rules shared by every weapon (GDD section 4.1). All values are ticks (50 ms) unless named otherwise. */
public final class CombatRules {
    /** An attack pressed in the last N ticks of recovery starts on the first free tick. */
    public static final int BUFFER_WINDOW = 5;
    /** Ticks after recovery ends to continue a chain before it resets to the first hit. */
    public static final int COMBO_WINDOW = 8;
    /** Moves of the light chain that work while riding (GDD 8.1: L1 and L2). */
    public static final int MOUNTED_CHAIN = 2;
    /** Recovery tick index (0-based) from which a dash or parry cancels: the 3rd tick. */
    public static final int DASH_CANCEL_FROM = 2;
    /** Recovery tick index from which an ability cancels: the 1st tick. */
    public static final int ABILITY_CANCEL_FROM = 0;
    /** Held this long, a light attack flows into the charge instead of recovering. */
    public static final int CHARGE_HOLD_THRESHOLD = 6;
    /** A fresh attack press waits this many ticks (0.2 s): released inside it, a light attack fires on release; still held, the charge starts with no swing. */
    public static final int HOLD_WINDOW = 4;
    /**
     * The server waits this many ticks longer than {@link #HOLD_WINDOW} before it commits a hold to a charge, so the release of
     * a tap that a laggy link delivered a little late still finds the decision open.
     */
    public static final int HOLD_GRACE = 2;
    /** A charge the server started straight from a hold turns into the tap it was, if the release reports a short hold within this many ticks. */
    public static final int MAX_LATE_TAP = 6;
    public static final int DASH_TICKS = 8;
    /** A dash attack comes out of the last N ticks of a dash, or the N ticks after it. */
    public static final int DASH_ATTACK_WINDOW = 4;
    /** Perfect dodge and perfect parry: the first N ticks of the window. */
    public static final int PERFECT_WINDOW = 2;
    public static final int RIPOSTE_WINDOW = 20;
    public static final int GUARANTEED_CRIT_WINDOW = 20;
    /** No damage dealt or taken for this long counts as out of combat. */
    public static final int OUT_OF_COMBAT_TICKS = 100;
    /** Resonance drifts 5 a second toward {@link #DRIFT_TARGET} of max when out of combat. */
    public static final double DRIFT_PER_TICK = 0.25;
    public static final double DRIFT_TARGET = 0.30;
    public static final int PARRY_WHIFF_RECOVERY = 10;
    /** The first N ticks of a parry whiff can't be cancelled into a dash. */
    public static final int PARRY_NO_DASH = 6;
    /** Aim at least this far down (degrees, Minecraft pitch) while airborne and an attack becomes a plunge. */
    public static final float PLUNGE_PITCH_DEG = 60.0f;
    public static final int MAX_LATENCY_GRACE = 2;
    public static final int PERFECT_DODGE_RESONANCE = 15;
    public static final int PARRY_RESONANCE = 25;
    public static final double RIPOSTE_MULTIPLIER = 1.5;
    /** Resonance from one swing counts at most this many targets. */
    public static final int MAX_RESONANCE_TARGETS = 3;
    /** Movement speed while holding a charge. */
    public static final float CHARGING_MOVE_MULTIPLIER = 0.4f;

    private CombatRules() {
    }

    /**
     * Latency grace in ticks from the server's round-trip estimate in milliseconds: the whole round trip, rounded, capped
     * at {@link #MAX_LATENCY_GRACE}. A reaction reaches the server a full round trip after the moment its player saw (the
     * attack's news travels out, the answer travels back), so half the round trip under-counts it.
     */
    public static int latencyGraceTicks(int latencyMs) {
        return Math.max(0, Math.min(MAX_LATENCY_GRACE, Math.round(latencyMs / 50.0f)));
    }
}
