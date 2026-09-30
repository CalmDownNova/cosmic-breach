package com.cosmicbreach.progression;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The players who damaged a creature, an attachment on the creature (server only, never saved), and
 * the rule for who shares its kill (GDD section 3.2): everyone who dealt it damage, wherever they are
 * now, and everyone who stood within {@value #RADIUS} blocks when it died. Each gets the full amount.
 */
public final class KillCredit {
    /** Standing this close to a kill counts as taking part in it, in blocks. */
    public static final double RADIUS = 24.0;
    /** More damagers than this on one creature are not tracked (a guard against runaway growth). */
    public static final int MAX_TRACKED = 64;

    private final Set<UUID> damagers = new LinkedHashSet<>();

    public void recordDamage(UUID player) {
        if (damagers.size() < MAX_TRACKED || damagers.contains(player)) {
            damagers.add(player);
        }
    }

    public Set<UUID> damagers() {
        return Collections.unmodifiableSet(damagers);
    }

    /** A player near the kill: how far it stood, and whether it can take part at all (alive, not spectating). */
    public record Bystander(UUID id, double distance, boolean eligible) {
    }

    /**
     * Everyone who shares a kill, each once: the damagers first (in the order they first hit), then
     * eligible bystanders within {@link #RADIUS}.
     */
    public static Set<UUID> participants(Collection<UUID> damagers, Collection<Bystander> bystanders) {
        Set<UUID> result = new LinkedHashSet<>(damagers);
        for (Bystander bystander : bystanders) {
            if (bystander.eligible() && bystander.distance() <= RADIUS) {
                result.add(bystander.id());
            }
        }
        return result;
    }
}
