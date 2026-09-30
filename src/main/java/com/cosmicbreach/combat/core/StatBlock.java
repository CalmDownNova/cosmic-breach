package com.cosmicbreach.combat.core;

/**
 * Effective attribute points (allocated plus gear): the values of the players' four attribute
 * instances, see {@code progression.ProgressionStats}.
 */
public record StatBlock(int power, int agility, int arcane, int resilience) {
    public static final StatBlock ZERO = new StatBlock(0, 0, 0, 0);

    public int get(Stat stat) {
        return switch (stat) {
            case POWER -> power;
            case AGILITY -> agility;
            case ARCANE -> arcane;
            case RESILIENCE -> resilience;
        };
    }
}
