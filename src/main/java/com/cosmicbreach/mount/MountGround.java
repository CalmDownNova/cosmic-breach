package com.cosmicbreach.mount;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a mount is put down or sent (quality review, Important 3): a flier needs room, one with gravity needs a floor under
 * it, or it falls, climbs back and falls again for ever. A floor is vanilla's own test for where a rider may step off
 * ({@link DismountHelper}): a floor within a block, the mount's box clear, nothing dangerous.
 */
final class MountGround {
    /** How far up and down a mount with gravity looks for a floor from a spot that has none, blocks. */
    static final int LEDGE_UP = 2;
    static final int LEDGE_DOWN = 3;

    /** The columns tried for a floor near a spot: its own, then the eight round it, two blocks out (a mount is wider than a player). */
    private static final int[][] COLUMNS = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};

    private MountGround() {
    }

    /**
     * The blocks to try for a mount set down at {@code at}, best first: the block itself, the one above, the four beside it,
     * two above; for a mount with gravity then up to {@value #LEDGE_DOWN} blocks down, for a ledge below.
     */
    static List<BlockPos> candidates(BlockPos at, boolean needsGround) {
        List<BlockPos> out = new ArrayList<>(List.of(at, at.above(), at.north(), at.south(), at.east(), at.west(), at.above(2)));
        if (needsGround) {
            for (int d = 1; d <= LEDGE_DOWN; d++) {
                out.add(at.below(d));
            }
        }
        return out;
    }

    /** Where {@code mount} stands when set down near {@code at}: room for a flier, a floor under it for one that falls; null if there is none. */
    static @Nullable Vec3 releaseSpot(ServerLevel level, CelestialMount mount, BlockPos at) {
        boolean ground = mount.needsGround();
        for (BlockPos p : candidates(at, ground)) {
            if (ground) {
                Vec3 stand = DismountHelper.findSafeDismountLocation(mount.getType(), level, p, true);
                if (stand != null) {
                    return stand;
                }
            } else {
                Vec3 v = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (level.noCollision(mount.getType().getSpawnAABB(v.x, v.y, v.z))) {
                    return v;
                }
            }
        }
        return null;
    }

    /** True if {@code mount}'s box fits at {@code at} (its feet) with ground right under it. */
    static boolean standsAt(Level level, Entity mount, Vec3 at) {
        AABB box = mount.getBoundingBox().move(at.subtract(mount.position()));
        return level.noCollision(mount, box) && !level.noCollision(mount, box.move(0, -0.25, 0));
    }

    /**
     * A place a mount of {@code type} can stand near {@code start}: the column of that block from {@code up} above to
     * {@code down} below, then the columns round it; null if there is none.
     */
    static @Nullable Vec3 groundNear(Level level, EntityType<?> type, BlockPos start, int up, int down) {
        for (int[] c : COLUMNS) {
            for (int dy = up; dy >= -down; dy--) {
                Vec3 stand = DismountHelper.findSafeDismountLocation(type, level, start.offset(c[0], dy, c[1]), true);
                if (stand != null) {
                    return stand;
                }
            }
        }
        return null;
    }

    /**
     * Where a mount of {@code type} that fell is set down beside its owner standing at {@code owner}: two blocks from them on
     * the side it comes from if that has a floor, else the next side round them, else the owner's own block (they stand on
     * ground). Null if even that has no room for it.
     */
    static @Nullable Vec3 standingBeside(Level level, EntityType<?> type, Vec3 owner, Vec3 mount) {
        double base = Math.atan2(mount.z - owner.z, mount.x - owner.x);
        for (int i = 0; i < MountCareRules.COME_DIRECTIONS; i++) {
            double a = MountCareRules.comeAngle(base, i);
            BlockPos side = BlockPos.containing(owner.x + Math.cos(a) * MountCareRules.OWNER_SIDE, owner.y, owner.z + Math.sin(a) * MountCareRules.OWNER_SIDE);
            Vec3 stand = DismountHelper.findSafeDismountLocation(type, level, side, true);
            if (stand != null) {
                return stand;
            }
        }
        return DismountHelper.findSafeDismountLocation(type, level, BlockPos.containing(owner), true);
    }
}
