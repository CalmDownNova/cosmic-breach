package com.cosmicbreach.guardian.leviathan;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Who is farming the Leviathan from one spot (the player who stands on a ledge and swings as it goes by). A player counts
 * once their run of hits from within {@value #RADIUS} blocks of the same spot spans {@value #TICKS} ticks (4 s) or more,
 * the latest is recent and they are still standing there: every hit in the run lies within the radius of the latest hit
 * and the oldest is at least {@value #TICKS} ticks before it. The Breach Dive then prefers such a player over the usual target.
 * Pure: the Leviathan records hits and asks.
 */
public final class LedgeFarming {
    public static final double RADIUS = 3.0;
    public static final int TICKS = 80;
    /**
     * A farmer's latest hit is no older than this (30 s: it only reaches a ledge as it goes by, a lap of its orbit apart,
     * so a farmer's hits come in bursts).
     */
    public static final int RECENT = 600;
    /** Hits older than this are forgotten. */
    private static final int MEMORY = 1200;

    private record Hit(long tick, Vec3 at) {
    }

    private final Map<UUID, ArrayDeque<Hit>> hits = new HashMap<>();

    /** {@code player} damaged it at {@code now}, standing at {@code at}. */
    public void hit(UUID player, long now, Vec3 at) {
        ArrayDeque<Hit> list = hits.computeIfAbsent(player, id -> new ArrayDeque<>());
        list.addLast(new Hit(now, at));
        while (!list.isEmpty() && now - list.peekFirst().tick() > MEMORY) {
            list.removeFirst();
        }
    }

    /** True if {@code player}, now at {@code current}, is farming it at {@code now}. */
    public boolean farming(UUID player, long now, Vec3 current) {
        ArrayDeque<Hit> list = hits.get(player);
        if (list == null || list.isEmpty()) {
            return false;
        }
        Hit latest = list.peekLast();
        if (now - latest.tick() > RECENT || !within(latest.at(), current)) {
            return false;
        }
        // walk back from the latest hit while the player stayed put: the unbroken run of hits near this spot
        long oldest = latest.tick();
        var it = list.descendingIterator();
        while (it.hasNext()) {
            Hit h = it.next();
            if (!within(latest.at(), h.at())) {
                break;
            }
            oldest = h.tick();
        }
        return latest.tick() - oldest >= TICKS;
    }

    /** The farmer among {@code players} (those with the longest stay first); null if none is farming. */
    public UUID farmer(Collection<UUID> players, long now, java.util.function.Function<UUID, Vec3> position) {
        UUID best = null;
        long bestSpan = -1;
        for (UUID id : players) {
            Vec3 at = position.apply(id);
            if (at == null || !farming(id, now, at)) {
                continue;
            }
            long span = now - oldestOfRun(id);
            if (span > bestSpan) {
                best = id;
                bestSpan = span;
            }
        }
        return best;
    }

    private long oldestOfRun(UUID player) {
        ArrayDeque<Hit> list = hits.get(player);
        Hit latest = list.peekLast();
        long oldest = latest.tick();
        var it = list.descendingIterator();
        while (it.hasNext()) {
            Hit h = it.next();
            if (!within(latest.at(), h.at())) {
                break;
            }
            oldest = h.tick();
        }
        return oldest;
    }

    public void forget(UUID player) {
        hits.remove(player);
    }

    public void clear() {
        hits.clear();
    }

    private static boolean within(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) <= RADIUS * RADIUS;
    }
}
