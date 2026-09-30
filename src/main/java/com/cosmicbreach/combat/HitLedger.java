package com.cosmicbreach.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-use hit bookkeeping: move serial, then target id, then how often and when it was last hit.
 * A target can be hit {@code hits} times by one use of a move, {@code hitInterval} ticks apart
 * (by default once). Pure, no world access.
 */
public final class HitLedger {
    /** Uses kept at once; only the newest few can still be hitting (one move plus stragglers). */
    private static final int MAX_USES = 8;

    private static final class Entry {
        int hits;
        long lastTick;
    }

    private final LinkedHashMap<Integer, Map<Integer, Entry>> uses = new LinkedHashMap<>();

    /**
     * Records a hit of use {@code serial} on {@code targetId} at {@code tick} if the move allows one
     * now, and says whether it did. {@code maxHits} is at least 1; an interval under 1 means one tick.
     */
    public boolean tryHit(int serial, int targetId, long tick, int maxHits, int interval) {
        Map<Integer, Entry> targets = uses.get(serial);
        if (targets == null) {
            targets = new HashMap<>();
            uses.put(serial, targets);
            trim();
        }
        Entry entry = targets.get(targetId);
        if (entry == null) {
            entry = new Entry();
            entry.hits = 1;
            entry.lastTick = tick;
            targets.put(targetId, entry);
            return true;
        }
        if (entry.hits >= Math.max(1, maxHits) || tick - entry.lastTick < Math.max(1, interval)) {
            return false;
        }
        entry.hits++;
        entry.lastTick = tick;
        return true;
    }

    /**
     * Records up to {@code count} hits of use {@code serial} on {@code targetId} at {@code tick}, never more
     * than {@code maxHits} in all, and says how many it recorded. For hits spread over a move's active ticks
     * ({@code MoveDef.Hit#spread}), where one tick may carry several and the move's schedule sets the pace.
     */
    public int tryHits(int serial, int targetId, long tick, int maxHits, int count) {
        if (count <= 0) {
            return 0;
        }
        Map<Integer, Entry> targets = uses.get(serial);
        if (targets == null) {
            targets = new HashMap<>();
            uses.put(serial, targets);
            trim();
        }
        Entry entry = targets.get(targetId);
        int already = entry == null ? 0 : entry.hits;
        int granted = Math.min(count, Math.max(1, maxHits) - already);
        if (granted <= 0) {
            return 0;
        }
        if (entry == null) {
            entry = new Entry();
            targets.put(targetId, entry);
        }
        entry.hits += granted;
        entry.lastTick = tick;
        return granted;
    }

    /** How many times use {@code serial} has hit {@code targetId}. */
    public int hitsOn(int serial, int targetId) {
        Map<Integer, Entry> targets = uses.get(serial);
        Entry entry = targets == null ? null : targets.get(targetId);
        return entry == null ? 0 : entry.hits;
    }

    /** The use is over; its bookkeeping can go. */
    public void forget(int serial) {
        uses.remove(serial);
    }

    public void clear() {
        uses.clear();
    }

    private void trim() {
        Iterator<Integer> oldest = uses.keySet().iterator();
        while (uses.size() > MAX_USES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
