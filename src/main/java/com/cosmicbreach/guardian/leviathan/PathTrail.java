package com.cosmicbreach.guardian.leviathan;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The path the Leviathan's head has swum, kept so its body follows it (Thalassine Leviathan design v1, "Movement": a
 * head part and trailing segments that follow the head's recorded path, like the hydra's necks). The head's middle is
 * recorded whenever it has moved at least {@code step} from the last point kept; {@link #behind} walks back from the
 * head along the kept points by arc length, so each segment sits a fixed distance of path behind the head whatever the
 * speed: stalls add no points, a dive's curve is followed exactly. When the record runs out (just spawned) it carries on
 * straight along its last direction. Pure; one per Leviathan (server).
 */
public final class PathTrail {
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final double step;
    /** Index of the newest point, and how many are kept. */
    private int newest = -1;
    private int count;

    /** Keeps up to {@code capacity} points at least {@code step} blocks apart (capacity x step must exceed the body). */
    public PathTrail(int capacity, double step) {
        if (capacity < 2 || step <= 0) {
            throw new IllegalArgumentException("capacity " + capacity + ", step " + step);
        }
        this.xs = new double[capacity];
        this.ys = new double[capacity];
        this.zs = new double[capacity];
        this.step = step;
    }

    public int size() {
        return count;
    }

    public void clear() {
        newest = -1;
        count = 0;
    }

    /** Forgets the path and lays down {@code points}, the first nearest the head (a body lying along a known curve). */
    public void reset(List<Vec3> pointsFromHead) {
        clear();
        for (int i = pointsFromHead.size() - 1; i >= 0; i--) {
            push(pointsFromHead.get(i));
        }
    }

    /** Forgets the path and lays the body out straight behind {@code head}, along {@code back} (a unit vector). */
    public void resetStraight(Vec3 head, Vec3 back, double length) {
        clear();
        int n = (int) Math.ceil(length / step) + 1;
        for (int i = n; i >= 0; i--) {
            push(head.add(back.scale(i * step)));
        }
    }

    /** The head is at {@code head} now: kept if it moved at least a step from the newest point. */
    public void record(Vec3 head) {
        if (count == 0) {
            push(head);
            return;
        }
        double dx = head.x - xs[newest];
        double dy = head.y - ys[newest];
        double dz = head.z - zs[newest];
        if (dx * dx + dy * dy + dz * dz >= step * step) {
            push(head);
        }
    }

    private void push(Vec3 p) {
        newest = (newest + 1) % xs.length;
        xs[newest] = p.x;
        ys[newest] = p.y;
        zs[newest] = p.z;
        count = Math.min(count + 1, xs.length);
    }

    /**
     * The point {@code distance} blocks of path behind {@code head} (the head's middle now, which may be ahead of the
     * newest kept point): back along the kept points, interpolated, and straight on past the oldest.
     */
    public Vec3 behind(Vec3 head, double distance) {
        if (count == 0 || distance <= 0) {
            return head;
        }
        double px = head.x;
        double py = head.y;
        double pz = head.z;
        double left = distance;
        double lastDx = 0;
        double lastDy = 0;
        double lastDz = -1;
        for (int k = 0; k < count; k++) {
            int i = Math.floorMod(newest - k, xs.length);
            double dx = xs[i] - px;
            double dy = ys[i] - py;
            double dz = zs[i] - pz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-9) {
                if (len >= left) {
                    double f = left / len;
                    return new Vec3(px + dx * f, py + dy * f, pz + dz * f);
                }
                lastDx = dx / len;
                lastDy = dy / len;
                lastDz = dz / len;
                left -= len;
            }
            px = xs[i];
            py = ys[i];
            pz = zs[i];
        }
        return new Vec3(px + lastDx * left, py + lastDy * left, pz + lastDz * left);
    }
}
