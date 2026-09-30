package com.cosmicbreach.combat;

/**
 * An entity that plays its own stagger when its poise breaks. Entities without it get the generic
 * stagger instead: Slowness V and no outgoing damage for the stagger's length.
 */
public interface Staggerable {
    void onStagger(int ticks);
}
