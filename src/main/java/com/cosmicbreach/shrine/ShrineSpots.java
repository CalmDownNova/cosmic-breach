package com.cosmicbreach.shrine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.Vec3;

/** Where a player stands beside a shrine: in front of it first, then its front corners, its sides, further out, behind. */
public final class ShrineSpots {
    private ShrineSpots() {
    }

    public static Optional<Vec3> standSpot(CollisionGetter level, BlockPos shrine, Direction facing) {
        return standSpots(level, shrine, facing).stream().findFirst();
    }

    /** Every spot where a player can stand beside the shrine, best first: in front of it, its front corners, its sides, further out, behind. */
    public static List<Vec3> standSpots(CollisionGetter level, BlockPos shrine, Direction facing) {
        List<Vec3> out = new ArrayList<>();
        BlockPos front = shrine.relative(facing);
        List<BlockPos> around = List.of(front, front.relative(facing.getClockWise()), front.relative(facing.getCounterClockWise()),
                shrine.relative(facing.getClockWise()), shrine.relative(facing.getCounterClockWise()), shrine.relative(facing, 2),
                shrine.relative(facing.getOpposite()));
        for (BlockPos p : around) {
            for (int dy : new int[] {0, 1, -1}) {
                Vec3 v = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, p.above(dy), true);
                if (v != null) {
                    out.add(v);
                    break;
                }
            }
        }
        return out;
    }
}
