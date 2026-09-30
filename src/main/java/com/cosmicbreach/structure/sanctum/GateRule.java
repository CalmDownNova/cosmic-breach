package com.cosmicbreach.structure.sanctum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gate's rules (GDD 6.1), pure: who it lets through and when its doors stand open.
 *
 * <ul>
 *   <li>It opens for any player attuned to the Sanctum (the Unsung's first kill).</li>
 *   <li>When an attuned player arrives at it ({@link #OPEN_RADIUS}), everyone within {@link #PARTY_RADIUS} of them at
 *       that moment is their party and is admitted for good: the Gate lets them through from then on, alone or not,
 *       so a party that went home comes back in.</li>
 *   <li>Its doors stand open while anyone it lets through is near, and close {@link #CLOSE_DELAY} ticks after the
 *       last one left, never on someone standing in the doorway. While open, the veil in the doorway still stops
 *       everyone it would not let through.</li>
 * </ul>
 */
public final class GateRule {
    public static final double PARTY_RADIUS = 16.0;
    public static final double OPEN_RADIUS = 6.0;
    /** How near a player it won't let through must come to be told so. */
    public static final double REFUSE_RADIUS = 4.0;
    public static final int CLOSE_DELAY = 40;
    /** A refused player is told again no sooner than this. */
    public static final int REFUSE_EVERY = 100;

    private GateRule() {
    }

    /** True if the Gate lets this player through. */
    public static boolean passes(boolean attuned, boolean admitted) {
        return attuned || admitted;
    }

    /** The party of an attuned opener at {@code opener}: every player within 16 blocks of them (the opener too). */
    public static <T> List<T> party(double[] opener, Map<T, double[]> players) {
        List<T> out = new ArrayList<>();
        for (Map.Entry<T, double[]> e : players.entrySet()) {
            double[] p = e.getValue();
            double dx = p[0] - opener[0];
            double dy = p[1] - opener[1];
            double dz = p[2] - opener[2];
            if (dx * dx + dy * dy + dz * dz <= PARTY_RADIUS * PARTY_RADIUS) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** One player near the Gate this tick. */
    public record Near(UUID id, boolean attuned, boolean admitted, double[] pos) {
    }

    /** What changed this tick. */
    public record Update(boolean open, boolean opened, boolean closed, Set<UUID> admitted, Set<UUID> refused) {
    }

    /**
     * The Gate's memory between ticks: whether it stands open, when it may close, which attuned players were already
     * at it (so each arrival opens it for a party once) and when each refused player was last told.
     */
    public static final class Gate {
        private boolean open;
        private long closeAt;
        private final Set<UUID> attunedHere = new HashSet<>();
        private final java.util.HashMap<UUID, Long> toldAt = new java.util.HashMap<>();

        public boolean open() {
            return open;
        }

        /** Forces the state (the world's blocks are the truth after a load). */
        public void setOpen(boolean open) {
            this.open = open;
        }

        /**
         * One tick. {@code near}: players within {@link #OPEN_RADIUS} of the Gate; {@code everyone}: every player in
         * the level with their position (for a party); {@code doorway}: someone stands in the doorway; {@code close}:
         * players it won't let through within {@link #REFUSE_RADIUS}.
         */
        public Update tick(long now, List<Near> near, Map<UUID, double[]> everyone, boolean doorway, List<UUID> close) {
            Set<UUID> admitted = new LinkedHashSet<>();
            Set<UUID> attunedNow = new HashSet<>();
            boolean anyPass = false;
            for (Near n : near) {
                if (n.attuned()) {
                    attunedNow.add(n.id());
                    if (!attunedHere.contains(n.id())) {
                        // an arrival: this attuned player's party comes in with them
                        admitted.addAll(party(n.pos(), everyone));
                        admitted.add(n.id());
                    }
                }
                anyPass |= passes(n.attuned(), n.admitted() || admitted.contains(n.id()));
            }
            attunedHere.clear();
            attunedHere.addAll(attunedNow);
            boolean opened = false;
            boolean closed = false;
            if (anyPass) {
                closeAt = now + CLOSE_DELAY;
                if (!open) {
                    open = true;
                    opened = true;
                }
            } else if (open && now >= closeAt && !doorway) {
                open = false;
                closed = true;
            }
            Set<UUID> refused = new LinkedHashSet<>();
            for (UUID id : close) {
                if (admitted.contains(id)) {
                    continue;
                }
                Long last = toldAt.get(id);
                if (last == null || now - last >= REFUSE_EVERY) {
                    toldAt.put(id, now);
                    refused.add(id);
                }
            }
            return new Update(open, opened, closed, admitted, refused);
        }
    }
}
