package com.cosmicbreach.structure.choir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Who is playing a Choir Floor this session: everyone who has stepped onto a pad since it woke. A Discord for a missed
 * beat hurts only them; someone who came in to watch and never stepped on a pad is never hurt. The set empties when the
 * floor falls idle or is solved. Pure.
 */
public final class ChoirPlayers {
    private final Set<UUID> playing = new HashSet<>();

    /** {@code player} stepped onto a pad. */
    public void stepped(UUID player) {
        playing.add(player);
    }

    public boolean playing(UUID player) {
        return playing.contains(player);
    }

    public int size() {
        return playing.size();
    }

    public void reset() {
        playing.clear();
    }

    /** Of the players on the floor, the ones a missed beat hurts: those who have stepped onto a pad this session. */
    public <T> List<T> hurtByMissedBeat(List<T> onFloor, Function<T, UUID> id) {
        List<T> out = new ArrayList<>();
        for (T p : onFloor) {
            if (playing.contains(id.apply(p))) {
                out.add(p);
            }
        }
        return out;
    }
}
