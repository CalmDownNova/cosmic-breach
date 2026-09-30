package com.cosmicbreach.entity.shardling;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Attack tokens (GDD section 7.1): at most {@value #PER_TARGET} Shardlings may attack the same player at
 * once, whichever pack they belong to. A Shardling takes a token before its telegraph starts and gives
 * it back when the attack ends, when it staggers, dies or leaves the level, and when its pack changes
 * target. Each holder holds at most one token. Keyed by entity ids; pure, no world access.
 */
public final class AttackTokens {
    public static final int PER_TARGET = 2;

    private final Map<Integer, Integer> targetOf = new HashMap<>();
    private final Map<Integer, Set<Integer>> holdersOf = new HashMap<>();

    /**
     * Gives {@code holder} a token for {@code target} if one is free. A holder that already has this
     * target's token keeps it (true); one holding another target's token gives that back first.
     */
    public boolean tryAcquire(int target, int holder) {
        Integer current = targetOf.get(holder);
        if (current != null && current == target) {
            return true;
        }
        Set<Integer> holders = holdersOf.get(target);
        if (holders != null && holders.size() >= PER_TARGET) {
            return false;
        }
        release(holder);
        holdersOf.computeIfAbsent(target, t -> new LinkedHashSet<>()).add(holder);
        targetOf.put(holder, target);
        return true;
    }

    /** Gives back whatever token {@code holder} has. Returns true if it had one. */
    public boolean release(int holder) {
        Integer target = targetOf.remove(holder);
        if (target == null) {
            return false;
        }
        Set<Integer> holders = holdersOf.get(target);
        if (holders != null) {
            holders.remove(holder);
            if (holders.isEmpty()) {
                holdersOf.remove(target);
            }
        }
        return true;
    }

    public boolean holds(int holder) {
        return targetOf.containsKey(holder);
    }

    /** The target whose token {@code holder} has, or -1. */
    public int targetOf(int holder) {
        Integer target = targetOf.get(holder);
        return target == null ? -1 : target;
    }

    /** How many tokens for {@code target} are out. */
    public int held(int target) {
        Set<Integer> holders = holdersOf.get(target);
        return holders == null ? 0 : holders.size();
    }

    /** Who holds {@code target}'s tokens (a snapshot). */
    public Set<Integer> holders(int target) {
        Set<Integer> holders = holdersOf.get(target);
        return holders == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(holders));
    }

    public void clear() {
        targetOf.clear();
        holdersOf.clear();
    }
}
