package com.cosmicbreach.combat;

/**
 * An entity that keeps its own gauge for Impact instead of the engine's stagger (a guardian's Break gauge):
 * {@link com.cosmicbreach.combat.server.PoiseTracker} hands it every Impact it takes (hits and parries) and never
 * staggers it. Server only.
 */
public interface ImpactSink {
    /** Impact taken now. */
    void takeImpact(double impact);
}
