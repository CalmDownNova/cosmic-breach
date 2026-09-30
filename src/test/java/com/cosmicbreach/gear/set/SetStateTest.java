package com.cosmicbreach.gear.set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** The set state: a meter that runs out, the ability's cooldown and its fill, and the wire format. */
class SetStateTest {
    @Test
    void theMeterRunsOut() {
        SetState state = SetState.NONE.withMeter(7.5f, 160);
        assertEquals(7.5f, state.meter(100), 1e-6);
        assertEquals(7.5f, state.meter(159), 1e-6);
        assertEquals(0f, state.meter(160), 1e-6, "gone at its end");
        assertEquals(0f, state.withoutMeter().meter(100), 1e-6);
        assertEquals(0L, SetState.NONE.withMeter(0f, 500).meterUntil(), "an empty meter keeps no deadline");
    }

    @Test
    void cooldownCountsDownAndFills() {
        SetState state = SetState.NONE.withCooldown(1000, 600);
        assertFalse(state.ready(1000));
        assertEquals(600, state.cooldownLeft(1000));
        assertEquals(0f, state.cooldownFill(1000), 1e-6);
        assertEquals(0.5f, state.cooldownFill(1300), 1e-6);
        assertTrue(state.ready(1600));
        assertEquals(1f, state.cooldownFill(1600), 1e-6);
        assertTrue(SetState.NONE.ready(0), "never cast: ready");
    }

    @Test
    void theMeterAndTheCooldownAreIndependent() {
        SetState state = SetState.NONE.withCooldown(0, 600).withMeter(12f, 60).withActive(90);
        assertEquals(600, state.cooldownLeft(0));
        assertEquals(12f, state.meter(10), 1e-6);
        assertTrue(state.active(89));
        assertFalse(state.active(90));
        assertEquals(600, state.withoutMeter().cooldownLeft(0));
    }

    @Test
    void survivesTheWire() {
        SetState state = new SetState(13.25f, 4242L, 99999L, 413, 1234L);
        ByteBuf buf = Unpooled.buffer();
        SetState.STREAM_CODEC.encode(buf, state);
        assertEquals(state, SetState.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }

    @Test
    void hasteShortensTheCooldown() {
        assertEquals(600, ArmorSets.cooldownTicks(600, 0));
        // Arcane 30: Haste 45, x100/145
        assertEquals(414, ArmorSets.cooldownTicks(600, 30));
        // the Choir Regalia's Hymn: Arcane 8 (Haste 12) and +30 inside the ring, x100/142
        assertEquals(564, ArmorSets.cooldownTicks(800, 8, 30.0));
        assertEquals(715, ArmorSets.cooldownTicks(800, 8), "outside it, x100/112");
    }

    @Test
    void aSetReadsOnlyItsOwnMeterAndWindow() {
        var vanguard = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cosmicbreach", "starfall_vanguard");
        var driftweave = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cosmicbreach", "driftweave");
        SetState charges = SetState.NONE.withCooldown(0, 560).withMeter(driftweave, 3f, 1_000_000L);
        assertEquals(3f, charges.meter(driftweave, 10), 1e-6);
        assertEquals(0f, charges.meter(vanguard, 10), 1e-6, "the Driftweave's charges are never the Vanguard's Heat");
        SetState legacy = SetState.NONE.withMeter(7f, 100);
        assertEquals(7f, legacy.meter(vanguard, 10), 1e-6, "a meter without an owner is anyone's");
        SetState drift = charges.withActive(driftweave, 100);
        assertTrue(drift.active(driftweave, 50));
        assertFalse(drift.active(vanguard, 50), "Drift is not a Meteor Call");
        assertEquals(3f, drift.meter(driftweave, 50), 1e-6, "its own meter stays");
        SetState meteor = drift.withActive(vanguard, 90);
        assertEquals(0f, meteor.meter(vanguard, 50), 1e-6, "another set's meter goes when a set takes the state");
        assertEquals(560, meteor.cooldownLeft(0), "the cooldown is shared");
        assertTrue(meteor.active(vanguard, 50));
    }

    @Test
    void theOwnerSurvivesTheWire() {
        var driftweave = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cosmicbreach", "driftweave");
        SetState state = SetState.NONE.withMeter(driftweave, 2f, 99L).withActive(driftweave, 77L);
        ByteBuf buf = Unpooled.buffer();
        SetState.STREAM_CODEC.encode(buf, state);
        assertEquals(state, SetState.STREAM_CODEC.decode(buf));
        assertEquals("cosmicbreach:driftweave", state.owner());
    }
}
