package com.cosmicbreach.combat;

/**
 * An entity with its own poise: the Impact it takes within 3 s before it staggers. Other mobs use
 * {@code 10 + maxHealth / 2}, players {@code CombatMath.playerPoise}.
 */
public interface PoiseSource {
    double poise();
}
