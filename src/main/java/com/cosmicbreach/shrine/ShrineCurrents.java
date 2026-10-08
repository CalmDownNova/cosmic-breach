package com.cosmicbreach.shrine;

import com.cosmicbreach.lift.AscentCurrent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where the rising current beside a shrine stands (1.1 design section 5), pure over a world of solid blocks: the
 * nearest column, {@value #NEAR} to {@value #FAR} blocks from the shrine, of open air (it and its four neighbours) from
 * {@value #HEADROOM} above the shrine's floor down through the gap below ({@code passY}), whose way across to a spot beside the
 * shrine (the first of {@code landings} that works: in front of it if the way is clear, else beside it) is clear at the height a
 * rider crosses it and never over the shrine itself, and whose catch stays clear
 * of the landing ({@link AscentCurrent.Current#landingClear}: nobody is ever set down in a current); preferring one that comes down onto a floor in the level below (the first solid block under the gap, no
 * lower than {@code lowest}).
 */
public final class ShrineCurrents {
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    public static final int NEAR = 3;
    public static final int FAR = 14;
    public static final int HEADROOM = 3;
    private static final int OPEN = Integer.MIN_VALUE;

    private ShrineCurrents() {
    }

    /**
     * @param shrine  the shrine's block (players stand at its y in front of it)
     * @param landings where players may be set down beside it, best first ({@link ShrineSpots#standSpots}): each is tried, for each column
     * @param passY   the lowest Y of the gap the current climbs through
     * @param lowest  the lowest Y it may reach down to
     * @param keepX   the middle of a space it must stay out of, with keepZ and keepR (boss 2's sphere); NaN for none
     */
    public static Optional<AscentCurrent.Current> find(Solid solid, BlockPos shrine, List<Vec3> landings, int passY, int lowest,
                                                       double keepX, double keepZ, double keepR) {
        record Candidate(int x, int z, double d) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -FAR; dx <= FAR; dx++) {
            for (int dz = -FAR; dz <= FAR; dz++) {
                double d = Math.hypot(dx, dz);
                int x = shrine.getX() + dx;
                int z = shrine.getZ() + dz;
                if (d < NEAR || d > FAR || (!Double.isNaN(keepX) && Math.hypot(x + 0.5 - keepX, z + 0.5 - keepZ) < keepR)) {
                    continue;
                }
                candidates.add(new Candidate(x, z, d));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::d).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
        int top = shrine.getY();
        AscentCurrent.Current open = null;
        for (Candidate c : candidates) {
            int floor = floorBelow(solid, c.x(), c.z(), top + HEADROOM, passY, lowest);
            if (floor == BLOCKED) {
                continue;
            }
            int foot = floor == OPEN ? lowest : floor + 1;
            if (!clearAround(solid, c.x(), c.z(), foot + 2, top + HEADROOM)) {
                continue;
            }
            AscentCurrent.Current current = null;
            for (Vec3 landing : landings) {
                AscentCurrent.Current tried = new AscentCurrent.Current(c.x() + 0.5, c.z() + 0.5, foot, landing.y, landing.x, landing.z);
                if (tried.landingClear() && wayAcross(solid, c.x() + 0.5, c.z() + 0.5, landing, shrine)) {
                    current = tried;
                    break;
                }
            }
            if (current == null) {
                continue;
            }
            if (floor != OPEN) {
                return Optional.of(current);
            }
            if (open == null) {
                open = current;
            }
        }
        return Optional.ofNullable(open);
    }

    private static final int BLOCKED = Integer.MAX_VALUE;

    /** The first solid block going down the column from {@code from}: BLOCKED if at or above {@code passY}, OPEN if none down to {@code lowest}. */
    private static int floorBelow(Solid solid, int x, int z, int from, int passY, int lowest) {
        for (int y = from; y >= lowest; y--) {
            if (solid.at(x, y, z)) {
                return y >= passY ? BLOCKED : y;
            }
        }
        return OPEN;
    }

    private static boolean clearAround(Solid solid, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (solid.at(x + 1, y, z) || solid.at(x - 1, y, z) || solid.at(x, y, z + 1) || solid.at(x, y, z - 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clear for a rider crossing at the landing's floor plus 1.5, head up to 4.3 above it with the overshoot of the climb (blocks
     * y + 1 to y + 4 of the landing's floor), never over the shrine's own column.
     */
    private static boolean wayAcross(Solid solid, double fromX, double fromZ, Vec3 landing, BlockPos shrine) {
        double dx = landing.x - fromX;
        double dz = landing.z - fromZ;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 0.25));
        for (int i = 0; i <= steps; i++) {
            double px = fromX + dx * i / steps;
            double pz = fromZ + dz * i / steps;
            if (Math.hypot(px - (shrine.getX() + 0.5), pz - (shrine.getZ() + 0.5)) < 0.9) {
                return false;
            }
            int bx = (int) Math.floor(px);
            int bz = (int) Math.floor(pz);
            int floor = (int) Math.floor(landing.y + 1e-6);
            for (int y = floor + 1; y <= floor + 4; y++) {
                if (solid.at(bx, y, bz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
