package com.cosmicbreach.progression;

import com.cosmicbreach.progression.XpSource.LayerTier;
import com.cosmicbreach.progression.net.AllocatePointsPayload;
import com.cosmicbreach.progression.net.LevelUpPayload;
import com.cosmicbreach.progression.net.XpGainedPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** GDD section 3.2's source table, and the progression payloads on the wire. */
class XpSourceTest {

    @Test
    void theSourceTable() {
        int[][] expected = {
                {25, 75, 180},
                {250, 600, 1_200},
                {150, 400, 900},
                {800, 2_000, 4_000},
                {3_000, 9_000, 25_000},
                {600, 1_800, 5_000}};
        XpSource[] sources = {XpSource.TRASH_MOB, XpSource.ELITE, XpSource.STRUCTURE_FOUND, XpSource.PUZZLE_SOLVED,
                XpSource.GUARDIAN_FIRST_KILL, XpSource.GUARDIAN_REPEAT_KILL};
        for (int i = 0; i < sources.length; i++) {
            assertEquals(expected[i][0], sources[i].at(LayerTier.REACH), sources[i] + " in the Reach");
            assertEquals(expected[i][1], sources[i].at(LayerTier.DRIFT), sources[i] + " in the Drift");
            assertEquals(expected[i][2], sources[i].at(LayerTier.DEEP), sources[i] + " in the Deep");
        }
        assertEquals(40_000, XpSource.HELIARCH_FIRST_KILL);
        assertEquals(8_000, XpSource.HELIARCH_REPEAT_KILL);
    }

    @Test
    void aShardlingIsReachTrash() {
        assertEquals(25, XpSource.TRASH_MOB.at(LayerTier.REACH));
    }

    @Test
    void payloadsRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        AllocatePointsPayload allocate = new AllocatePointsPayload(new Allocation(1, 20, 0, 3));
        AllocatePointsPayload.STREAM_CODEC.encode(buf, allocate);
        assertEquals(allocate, AllocatePointsPayload.STREAM_CODEC.decode(buf));
        LevelUpPayload levelUp = new LevelUpPayload(1234, 10, 9);
        LevelUpPayload.STREAM_CODEC.encode(buf, levelUp);
        assertEquals(levelUp, LevelUpPayload.STREAM_CODEC.decode(buf));
        XpGainedPayload gained = new XpGainedPayload(6_002);
        XpGainedPayload.STREAM_CODEC.encode(buf, gained);
        assertEquals(gained, XpGainedPayload.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }
}
