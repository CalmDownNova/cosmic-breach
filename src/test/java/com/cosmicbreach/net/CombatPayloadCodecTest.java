package com.cosmicbreach.net;

import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.CombatDataTestAccess;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every payload survives the wire (singleplayer skips encoding, so only a server would find a bug). */
class CombatPayloadCodecTest {

    @Test
    void dataSyncRoundTripsMeridian() {
        CombatData data = CombatDataTestAccess.meridian();
        CombatDataSyncPayload sent = CombatDataSyncPayload.of(data);
        ByteBuf buf = Unpooled.buffer();
        CombatDataSyncPayload.STREAM_CODEC.encode(buf, sent);
        CombatDataSyncPayload received = CombatDataSyncPayload.STREAM_CODEC.decode(buf);
        assertEquals(sent.moves(), received.moves());
        assertEquals(sent.weapons(), received.weapons());
        assertFalse(buf.isReadable(), "nothing left over");
    }

    @Test
    void smallPayloadsRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        CombatInputPayload input = CombatInputPayload.of(CombatAction.PARRY);
        CombatInputPayload.STREAM_CODEC.encode(buf, input);
        assertEquals(CombatAction.PARRY, CombatInputPayload.STREAM_CODEC.decode(buf).decoded());

        CombatSyncPayload sync = new CombatSyncPayload(42.5f, 2, 87);
        CombatSyncPayload.STREAM_CODEC.encode(buf, sync);
        assertEquals(sync, CombatSyncPayload.STREAM_CODEC.decode(buf));

        MoveStartedPayload started = new MoveStartedPayload(1234, ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l3"));
        MoveStartedPayload.STREAM_CODEC.encode(buf, started);
        assertEquals(started, MoveStartedPayload.STREAM_CODEC.decode(buf));

        for (CombatFxPayload.Kind kind : CombatFxPayload.Kind.values()) {
            CombatFxPayload fx = new CombatFxPayload(99, kind);
            CombatFxPayload.STREAM_CODEC.encode(buf, fx);
            assertEquals(fx, CombatFxPayload.STREAM_CODEC.decode(buf));
        }
        // The parry's meeting point and a plunge's fall ride along as floats.
        CombatFxPayload parry = new CombatFxPayload(7, CombatFxPayload.Kind.PARRY, new Vec3(0.25, 1.125, -0.5), 10f);
        CombatFxPayload.STREAM_CODEC.encode(buf, parry);
        assertEquals(parry, CombatFxPayload.STREAM_CODEC.decode(buf));
        CombatFxPayload landing = new CombatFxPayload(7, CombatFxPayload.Kind.PLUNGE_LANDED, Vec3.ZERO, 5.75f);
        CombatFxPayload.STREAM_CODEC.encode(buf, landing);
        assertEquals(landing, CombatFxPayload.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }

    @Test
    void hitFxRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HitFxPayload hit = new HitFxPayload(5, 6, new Vec3(10.25, 64.5, -3.75), new Vec3(0, 0, 1), 14f, true, 4);
        HitFxPayload.STREAM_CODEC.encode(buf, hit);
        HitFxPayload back = HitFxPayload.STREAM_CODEC.decode(buf);
        assertEquals(hit.attackerId(), back.attackerId());
        assertEquals(hit.targetId(), back.targetId());
        assertEquals(hit.position(), back.position());
        assertTrue(hit.direction().distanceTo(back.direction()) < 1e-6);
        assertEquals(hit.impact(), back.impact());
        assertEquals(hit.crit(), back.crit());
        assertEquals(hit.hitstop(), back.hitstop());
    }
}
