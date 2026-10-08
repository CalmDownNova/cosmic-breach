package com.cosmicbreach.shrine;

import com.cosmicbreach.lift.AscentCurrent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the shrines' saved data keeps (1.1 design sections 5 and 9): the shrines, and each rising current found beside one. */
class ShrineDataTest {
    @Test
    void aSavedCurrentComesBackExactlyAndAnOldSaveWithoutOneLoadsEmpty() {
        ShrineData data = new ShrineData();
        data.put("colossus@1,2,3", new ShrineData.Placed(ShrineKind.COLOSSUS, new BlockPos(6, 341, 0), Direction.WEST));
        AscentCurrent.Current c = new AscentCurrent.Current(100.5, -50.5, 221.0, 341.0, 106.5, 52.25);
        data.putCurrent("colossus@1,2,3", c);
        CompoundTag tag = data.save(new CompoundTag(), null);
        ShrineData back = ShrineData.load(tag, null);
        assertEquals(c, back.current("colossus@1,2,3").orElseThrow());
        assertEquals(1, back.currents().size());
        assertEquals(1, back.all().size());

        CompoundTag old = new CompoundTag();
        old.put("Shrines", tag.getList("Shrines", 10)); // a world saved before the currents existed
        ShrineData loaded = ShrineData.load(old, null);
        assertEquals(1, loaded.all().size());
        assertTrue(loaded.currents().isEmpty(), "the placer finds them at the next start");
    }
}
