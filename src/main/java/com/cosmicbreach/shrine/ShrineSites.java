package com.cosmicbreach.shrine;

import com.cosmicbreach.guardian.colossus.CrownSpireLayout;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where each guardian's shrine stands (1.1 design section 9), pure: at the approach to its arena, in sight of the way
 * in, outside every attack's reach and off the path. Everything comes from the lairs' own layouts, so a lair built
 * before 1.1 gets the same spot as a new one. The placer then settles each spot on real ground (within two blocks
 * across and a few up or down, {@link ShrinePlacer#settle}).
 */
public final class ShrineSites {
    /** A shrine's block and the way its face looks. */
    public record Site(ShrineKind kind, BlockPos pos, Direction facing) {
    }

    /** An island's top at a column (the terrain model's), or empty off any island. */
    @FunctionalInterface
    public interface IslandTop {
        OptionalDouble at(double x, double z);
    }

    /** Boss 1: how far out the rim is searched, how far to the side of the walkway, how far in from the rim. */
    public static final int CROWN_SEARCH = 64;
    public static final double CROWN_SIDE = 4.0;
    public static final double CROWN_INSET = 3.0;
    /** Boss 2: outward of the ledge's middle and to the side of the walkway. */
    public static final double RIFT_OUT = 1.0;
    public static final double RIFT_SIDE = 3.0;
    /**
     * Boss 3: the landing's middle row (u 50.5) and outer column (v 3.5), given as column centres because
     * {@code NaveLayout.world} floors (a whole number would land on different columns for different facings).
     */
    public static final double NAVE_U = NaveLayout.DOOR_WALL + 2.0;
    public static final double NAVE_V = 3.5;
    /** Boss 4: in the antechamber, x and d (d runs from the arena toward the Gate). */
    public static final int SANCTUM_X = -5;
    public static final int SANCTUM_D = 55;

    private ShrineSites() {
    }

    /**
     * Boss 1: on the island beside the rising door's walkway, out along its bearing to three blocks short of the rim
     * (never nearer the door than the walkway's length), facing the door; stepped back toward the door, a block at a
     * time, if that spot would be off the island. Empty only if even one block out is off it.
     */
    public static Optional<Site> crown(CrownSpireLayout l, IslandTop island) {
        int[] door = l.riseDoorOutside();
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        double ux = Math.cos(a);
        double uz = Math.sin(a);
        double ox = door[0] + 0.5 - uz * CROWN_SIDE;
        double oz = door[2] + 0.5 + ux * CROWN_SIDE;
        double rim = -1.0;
        for (int t = 1; t <= CROWN_SEARCH; t++) {
            if (!onIsland(l, island, ox + ux * t, oz + uz * t)) {
                rim = t;
                break;
            }
        }
        double t = rim < 0 ? CrownSpireLayout.WALKWAY + 4.0 : Math.max(CrownSpireLayout.WALKWAY, rim - CROWN_INSET);
        for (; t >= 1.0; t -= 1.0) {
            double x = ox + ux * t;
            double z = oz + uz * t;
            if (onIsland(l, island, x, z)) {
                double top = island.at(x, z).getAsDouble();
                return Optional.of(new Site(ShrineKind.COLOSSUS, BlockPos.containing(x, Math.floor(top) + 1.0, z),
                        Direction.getNearest(-ux, 0.0, -uz)));
            }
        }
        return Optional.empty();
    }

    /** True on the spire's own island: an island top no more than 8 below the spire's foot. */
    private static boolean onIsland(CrownSpireLayout l, IslandTop island, double x, double z) {
        OptionalDouble top = island.at(x, z);
        return top.isPresent() && top.getAsDouble() >= l.baseY() - 8;
    }

    /** Boss 2: on the entrance ledge outside the shell's gap, beside the walkway, facing the gap. */
    public static Site rift(RiftLayout l) {
        Vec3 ledge = l.ledgeTop();
        double ex = Math.cos(l.entranceAngle());
        double ez = Math.sin(l.entranceAngle());
        double x = ledge.x + ex * RIFT_OUT - ez * RIFT_SIDE;
        double z = ledge.z + ez * RIFT_OUT + ex * RIFT_SIDE;
        return new Site(ShrineKind.LEVIATHAN, BlockPos.containing(x, ledge.y, z), Direction.getNearest(-ex, 0.0, -ez));
    }

    /** Boss 3: on the landing outside the nave's door, beside the doorway, facing out along the nave's axis. */
    public static Site nave(NaveLayout l) {
        int[] w = l.world(NAVE_U, NAVE_V);
        int[] a = l.axis();
        return new Site(ShrineKind.UNSUNG, new BlockPos(w[0], l.floorY(), w[1]), Direction.getNearest((double) a[0], 0.0, (double) a[1]));
    }

    /** The nave of a lair: its apse centre and floor from the arena centre, its facing from where its Hymnal Altar stands. */
    public static NaveLayout naveOf(BlockPos arenaCentre, BlockPos altar) {
        int dx = altar.getX() - arenaCentre.getX();
        int dz = altar.getZ() - arenaCentre.getZ();
        int facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 0 : 2) : (dz > 0 ? 1 : 3);
        return new NaveLayout(arenaCentre.getX(), arenaCentre.getZ(), arenaCentre.getY(), facing, 0L);
    }

    /** Boss 4: in the Sanctum's antechamber, between the Throne Seal and the first columns, west of the aisle, facing it. */
    public static Site sanctum(SanctumLayout l) {
        return new Site(ShrineKind.HELIARCH, new BlockPos(SANCTUM_X, SanctumLayout.HALL_Y, l.z(SANCTUM_D)), Direction.EAST);
    }
}
