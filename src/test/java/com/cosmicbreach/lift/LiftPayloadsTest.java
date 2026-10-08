package com.cosmicbreach.lift;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The lift's payloads survive the wire (singleplayer skips encoding, so only a server would find a bug). */
class LiftPayloadsTest {
    @Test
    void theListOfRidersSurvivesTheWire() {
        for (List<Integer> ids : List.of(List.<Integer>of(), List.of(7), List.of(3, 300, 70_000, 2_000_000_000))) {
            ByteBuf buf = Unpooled.buffer();
            LiftRiders.STREAM_CODEC.encode(buf, new LiftRiders(ids));
            assertEquals(new LiftRiders(ids), LiftRiders.STREAM_CODEC.decode(buf), "riders " + ids);
            assertFalse(buf.isReadable(), "nothing left over");
        }
    }

    @Test
    void theZonesSurviveTheWire() {
        LiftZones zones = new LiftZones(List.of(new LiftZones.Rift(new BlockPos(400, 229, -900), 1234L), new LiftZones.Rift(new BlockPos(-731, 229, 17), -987_654_321_012L)),
                List.of(new AscentCurrent.Current(100.5, -50.5, 221.0, 341.0, 106.5, 52.25), new AscentCurrent.Current(-7.5, 8.5, 1.0, 225.0, -1.5, 3.0)));
        ByteBuf buf = Unpooled.buffer();
        LiftZones.STREAM_CODEC.encode(buf, zones);
        assertEquals(zones, LiftZones.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable(), "nothing left over");
        LiftZones none = new LiftZones(List.of(), List.of());
        ByteBuf empty = Unpooled.buffer();
        LiftZones.STREAM_CODEC.encode(empty, none);
        assertEquals(none, LiftZones.STREAM_CODEC.decode(empty));
    }

    @Test
    void anEmptyListOfRidersIsOneByteOnTheWire() {
        ByteBuf buf = Unpooled.buffer();
        LiftRiders.STREAM_CODEC.encode(buf, new LiftRiders(List.of()));
        assertEquals(1, buf.readableBytes(), "it is sent when the last rider lands, and is tiny");
    }
}
