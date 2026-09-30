package com.cosmicbreach.guardian;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who takes part in a guardian fight (Prism Colossus design v1, "Rules and anti-cheese"): players inside the arena
 * who dealt the guardian (or its parts, or its shards) damage, or whom it targeted. Each is counted once, in the
 * order they joined; each gets their own rewards. Hitting it from outside the arena doesn't count. Pure.
 */
public final class Participants {
    private final Set<UUID> players = new LinkedHashSet<>();

    /** {@code player} dealt damage; counts if they stood inside the arena. True if they are newly counted. */
    public boolean dealtDamage(UUID player, boolean insideArena) {
        return insideArena && players.add(player);
    }

    /** The guardian chose {@code player} as its target (only players inside the arena are targeted). */
    public boolean targeted(UUID player) {
        return players.add(player);
    }

    public boolean contains(UUID player) {
        return players.contains(player);
    }

    public Set<UUID> all() {
        return Collections.unmodifiableSet(players);
    }

    public int size() {
        return players.size();
    }

    public void clear() {
        players.clear();
    }
}
