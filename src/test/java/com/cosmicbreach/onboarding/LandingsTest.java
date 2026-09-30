package com.cosmicbreach.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LandingsTest {
    @Test
    void nothingSpawnsWithin24BlocksOfALandingsMiddle() {
        // a Landing is saved by its ring's origin; its middle is the corner the four Breach blocks share
        Landings landings = Landings.load(landingsTag(List.of(new BlockPos(100, 360, 50))));
        assertTrue(landings.quiet(101.0, 361, 51.0));
        assertTrue(landings.quiet(101.0 + 23.5, 361, 51.0));
        assertFalse(landings.quiet(101.0 + 24.5, 361, 51.0));
        assertFalse(landings.quiet(101.0 - 24.5, 361, 51.0));
        assertFalse(landings.quiet(101.0, 361 + 30, 51.0));
    }

    @Test
    void aLandingWithin32BlocksIsShared() {
        Landings landings = Landings.load(landingsTag(List.of(new BlockPos(0, 360, 0), new BlockPos(200, 350, 0))));
        assertEquals(new BlockPos(0, 360, 0), landings.nearest(new BlockPos(20, 340, 20), Landings.SHARED).orElseThrow());
        assertFalse(landings.nearest(new BlockPos(30, 340, 30), Landings.SHARED).isPresent());
        assertEquals(new BlockPos(200, 350, 0), landings.nearest(new BlockPos(190, 0, 5), Landings.SHARED).orElseThrow());
    }

    private static CompoundTag landingsTag(List<BlockPos> centres) {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (BlockPos c : centres) {
            list.add(net.minecraft.nbt.LongTag.valueOf(c.asLong()));
        }
        tag.put("landings", list);
        return tag;
    }
}
