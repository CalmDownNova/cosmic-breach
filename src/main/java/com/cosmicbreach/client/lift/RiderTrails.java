package com.cosmicbreach.client.lift;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/**
 * The luminous trails behind the players the rescue lift carries (1.1 design section 6), as a client keeps them, with no screen in
 * them so that a pause, a long ride and leaving the Drift can be tried in a test: which players the server says are carried, where each
 * one's feet were in the last {@value StreamLook#TRAIL_TICKS} ticks, and the trail to draw at a frame between ticks. {@link LiftClient}
 * feeds it each tick and {@link StreamRenderer} draws what it hands back.
 */
final class RiderTrails {
    /** The last ticks of a player the lift carries: where their feet were (oldest first, the rider last) and how many ticks ago. */
    record Trail(List<Vec3> points, List<Double> ages) {
    }

    private record Point(Vec3 at, long tick) {
    }

    private Set<Integer> listed = Set.of();
    private final Map<Integer, Deque<Point>> history = new HashMap<>();

    /** The server's list of who the lift carries now. */
    void list(Collection<Integer> ids) {
        listed = Set.copyOf(ids);
    }

    /** The entity ids the server last said are carried. */
    Set<Integer> listed() {
        return listed;
    }

    /** Forgets the list and every trail: this client has left the Drift (or its world), and nobody is carried as far as it knows. */
    void clear() {
        listed = Set.of();
        history.clear();
    }

    /**
     * Once a client tick: notes where each carried player's feet are, and forgets what is older than a trail. At most one point a tick
     * of the game's clock: a paused game keeps ticking the client while the level's clock stands still (so does {@code /tick freeze}),
     * and a point for every client tick would grow without bound and never be trimmed.
     */
    void note(long gameTime, Map<Integer, Vec3> carried) {
        for (Map.Entry<Integer, Vec3> e : carried.entrySet()) {
            Deque<Point> points = history.computeIfAbsent(e.getKey(), k -> new ArrayDeque<>());
            if (points.isEmpty() || points.peekLast().tick() < gameTime) {
                points.addLast(new Point(e.getValue(), gameTime));
            }
        }
        history.values().forEach(points -> {
            while (!points.isEmpty() && gameTime - points.peekFirst().tick() > StreamLook.TRAIL_TICKS) {
                points.removeFirst();
            }
        });
        history.values().removeIf(Deque::isEmpty);
    }

    /**
     * The trails to draw at a frame {@code partial} of the way from game tick {@code gameTime} to the next: each rider's last ticks (oldest
     * first) and, for those carried now ({@code heads}, their smoothly moving feet), that position as the last point.
     */
    List<Trail> drawn(long gameTime, double partial, Map<Integer, Vec3> heads) {
        List<Trail> out = new ArrayList<>();
        for (Map.Entry<Integer, Deque<Point>> en : history.entrySet()) {
            List<Vec3> points = new ArrayList<>();
            List<Double> ages = new ArrayList<>();
            for (Point p : en.getValue()) {
                if (!notReached(p.tick(), gameTime)) {
                    points.add(p.at());
                    ages.add(trailAge(p.tick(), gameTime, partial));
                }
            }
            Vec3 head = heads.get(en.getKey());
            if (head != null) {
                points.add(head);
                ages.add(0.0);
            }
            out.add(new Trail(points, ages));
        }
        return out;
    }

    /**
     * How old, in ticks, a point recorded at the end of client tick {@code recordedTick} (the game time then) looks at a frame
     * {@code partial} of the way from the last tick to the next. The rider drawn then is the state after the previous tick plus
     * {@code partial} of the way to the one after this tick, so the point of the previous tick is {@code partial} ticks behind them.
     */
    static double trailAge(long recordedTick, long gameTime, double partial) {
        return gameTime + partial - 1.0 - recordedTick;
    }

    /**
     * True for a point recorded this very tick: the rider's state at the end of it, which the rider drawn between ticks has not reached
     * yet. It is left out of the trail (kept, it lay above the rider and made the trail double back under them).
     */
    static boolean notReached(long recordedTick, long gameTime) {
        return recordedTick >= gameTime;
    }
}
