package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.guardian.BreakGauge;

/**
 * The Heliarch's Break gauge (GDD 7.3): {@value HeliarchMoves#BREAK_POISE} poise per phase. Impact from hits on the
 * core and hands and from parries (a parried Sunderfall gives 60) adds up, draining after
 * {@value HeliarchMoves#GAUGE_WINDOW} ticks; filling it Breaks the Heliarch for {@value HeliarchMoves#BREAK_TICKS}
 * ticks with its core at x1.5. Nothing builds during a Break, and the gauge empties when a new phase starts (a Break
 * running then ends). Pure.
 */
public final class PhaseGauge {
    private final BreakGauge gauge = new BreakGauge(HeliarchMoves.BREAK_POISE, HeliarchMoves.GAUGE_WINDOW);
    private int phase;
    private long breakStart = Long.MIN_VALUE;
    private int breaks;

    /** Impact {@code impact} at {@code now}; true if it Broke the Heliarch. */
    public boolean add(long now, double impact) {
        if (impact <= 0 || broken(now)) {
            return false;
        }
        if (gauge.add(now, impact)) {
            breakStart = now;
            breaks++;
            return true;
        }
        return false;
    }

    /** The phase is {@code p} now: a new phase empties the gauge and ends a Break. */
    public void phase(int p) {
        if (p != phase) {
            phase = p;
            gauge.clear();
            breakStart = Long.MIN_VALUE;
        }
    }

    public int phase() {
        return phase;
    }

    public boolean broken(long now) {
        return breakStart != Long.MIN_VALUE && now >= breakStart && now < breakStart + HeliarchMoves.BREAK_TICKS;
    }

    public long breakStart() {
        return breakStart;
    }

    /** Ends a Break early (the fight moved on). */
    public void endBreak() {
        breakStart = Long.MIN_VALUE;
    }

    /** How full it is, 0 to 1 (full while Broken). */
    public double fraction(long now) {
        return broken(now) ? 1.0 : gauge.fraction(now);
    }

    /** The core's damage taken for the gauge now: x1.5 while Broken. */
    public double taken(long now) {
        return broken(now) ? HeliarchMoves.BREAK_TAKEN : 1.0;
    }

    public int breaks() {
        return breaks;
    }

    public void clear() {
        gauge.clear();
        breakStart = Long.MIN_VALUE;
        breaks = 0;
        phase = 0;
    }
}
