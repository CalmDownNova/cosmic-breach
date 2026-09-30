package com.cosmicbreach.guardian.heliarch;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Heliarch's aggro (GDD 7.3): threat is the damage a player has dealt it (and its parts) plus taunts (a Gravikin's
 * counts as {@value HeliarchMoves#GRAVIKIN_TAUNT}). It keeps its target until someone else's threat passes the
 * target's by 30%, checked every {@value HeliarchMoves#THREAT_CHECK} ticks by the caller. With no threat at all it
 * takes the nearest. Pure.
 */
public final class ThreatTable {
    /** A player it may target now, and how far they are (squared). */
    public record Candidate(UUID id, double distanceSqr) {
    }

    private final Map<UUID, Double> threat = new HashMap<>();
    private UUID current;
    private int switches;

    /** {@code player} dealt {@code amount} damage. */
    public void addDamage(UUID player, double amount) {
        if (amount > 0) {
            threat.merge(player, amount, Double::sum);
        }
    }

    /** A taunt for {@code player} (a Gravikin's familiar, for instance). */
    public void taunt(UUID player, double amount) {
        addDamage(player, amount);
    }

    public double threat(UUID player) {
        return threat.getOrDefault(player, 0.0);
    }

    public UUID current() {
        return current;
    }

    /** Times it changed its mind (for checks). */
    public int switches() {
        return switches;
    }

    /**
     * The target after a check among {@code candidates}: the current one unless it left, or someone's threat passes
     * its by {@link HeliarchMoves#THREAT_SWITCH}; the most threatening when it has none (the nearest on a tie or with
     * no threat at all). Empty if there is no one.
     */
    public Optional<UUID> check(Collection<Candidate> candidates) {
        Candidate top = null;
        double topThreat = -1;
        Candidate stillThere = null;
        for (Candidate c : candidates) {
            double t = threat(c.id());
            if (top == null || t > topThreat + 1e-9 || (Math.abs(t - topThreat) <= 1e-9 && c.distanceSqr() < top.distanceSqr())) {
                top = c;
                topThreat = t;
            }
            if (c.id().equals(current)) {
                stillThere = c;
            }
        }
        if (top == null) {
            current = null;
            return Optional.empty();
        }
        UUID next;
        if (stillThere == null) {
            next = top.id();
        } else if (!top.id().equals(current) && topThreat > threat(current) * HeliarchMoves.THREAT_SWITCH && topThreat > 0) {
            next = top.id();
        } else {
            next = current;
        }
        if (current != null && !next.equals(current)) {
            switches++;
        }
        current = next;
        return Optional.of(next);
    }

    /** Forgets a player (they left the fight). */
    public void forget(UUID player) {
        threat.remove(player);
        if (player.equals(current)) {
            current = null;
        }
    }

    public void clear() {
        threat.clear();
        current = null;
        switches = 0;
    }
}
