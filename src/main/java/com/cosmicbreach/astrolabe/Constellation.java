package com.cosmicbreach.astrolabe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Constellation's marks (GDD 4.2): while the charge is held, each enemy the aim sweeps across (the crosshair's ray
 * through its box, or within {@value #CONE} degrees of its middle) within {@value #RANGE} blocks is marked with a
 * small star, up to {@value #MAX}; the release links them all with one beam, 1.8 per target. The beam's hits run
 * along the chain, one target a tick (the arpeggio), the last ones together on the last active tick. Pure.
 */
public final class Constellation {
    public static final int MAX = 5;
    public static final double RANGE = 24.0;
    public static final double CONE = 4.0;
    /** The crosshair's ray counts as on an enemy this far outside its box. */
    public static final double RAY_SLACK = 0.25;

    private Constellation() {
    }

    /** An enemy the aim could mark: its entity id and box. */
    public record Candidate(int id, AABB box) {
    }

    /**
     * The next enemy to mark, by id, or -1: of those not yet marked within {@code range} of the eye, one the crosshair's
     * ray passes through (the nearest along it), else the one nearest the aim within {@code cone} degrees; none once
     * {@value #MAX} are marked.
     */
    public static int pick(Vec3 eye, Vec3 look, List<Candidate> candidates, Collection<Integer> marked, double range,
                           double cone) {
        if (marked.size() >= MAX) {
            return -1;
        }
        Vec3 end = eye.add(look.normalize().scale(range));
        int onRay = -1;
        double onRayDistance = Double.MAX_VALUE;
        int near = -1;
        double nearAngle = Double.MAX_VALUE;
        for (Candidate c : candidates) {
            if (marked.contains(c.id())) {
                continue;
            }
            Vec3 centre = c.box().getCenter();
            if (centre.distanceTo(eye) > range) {
                continue;
            }
            AABB grown = c.box().inflate(RAY_SLACK);
            Optional<Vec3> hit = grown.clip(eye, end);
            if (hit.isPresent() || grown.contains(eye)) {
                double along = hit.map(h -> h.distanceTo(eye)).orElse(0.0);
                if (along < onRayDistance) {
                    onRay = c.id();
                    onRayDistance = along;
                }
                continue;
            }
            double a = Homing.angle(look, centre.subtract(eye));
            if (a <= cone && a < nearAngle) {
                near = c.id();
                nearAngle = a;
            }
        }
        return onRay >= 0 ? onRay : near;
    }

    /** The beam's active tick on which the {@code index}-th mark (from 0) is struck, of {@code activeTicks}. */
    public static int hitTick(int index, int activeTicks) {
        return Math.max(0, Math.min(index, Math.max(1, activeTicks) - 1));
    }
}
