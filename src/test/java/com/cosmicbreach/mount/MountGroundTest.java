package com.cosmicbreach.mount;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a mount is put down (quality review, Important 3): a flier needs room, one with gravity needs a floor under it. */
class MountGroundTest {
    private static final BlockPos AT = new BlockPos(10, 100, 10);

    @Test
    void aFlierTriesTheBlockThenTheOnesRoundIt() {
        assertEquals(List.of(AT, AT.above(), AT.north(), AT.south(), AT.east(), AT.west(), AT.above(2)), MountGround.candidates(AT, false));
    }

    @Test
    void oneWithGravityTriesTheSameThenLooksDownForAFloor() {
        List<BlockPos> ground = MountGround.candidates(AT, true);
        assertEquals(MountGround.candidates(AT, false), ground.subList(0, 7), "the same places first");
        assertEquals(List.of(AT.below(), AT.below(2), AT.below(3)), ground.subList(7, ground.size()), "then a ledge a few blocks down");
        assertEquals(ground.size(), new HashSet<>(ground).size(), "no place twice");
        assertTrue(ground.size() <= 12, "a short search");
    }

    @Test
    void theClimbBackOnlyLooksSoFarForGround() {
        assertTrue(MountGround.LEDGE_DOWN <= 3 && MountGround.LEDGE_UP <= 2, "a short distance: it must not hop islands");
    }
}
