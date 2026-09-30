package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to one player: its authoritative Resonance, dash charges and ability cooldown (ticks).
 * Sent when they change in a way the client can't predict, and every 10 ticks.
 */
public record CombatSyncPayload(float resonance, int dashCharges, int abilityCooldown) implements CustomPacketPayload {
    public static final Type<CombatSyncPayload> TYPE = new Type<>(CosmicBreach.id("combat_sync"));
    public static final StreamCodec<ByteBuf, CombatSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CombatSyncPayload::resonance,
            ByteBufCodecs.VAR_INT, CombatSyncPayload::dashCharges,
            ByteBufCodecs.VAR_INT, CombatSyncPayload::abilityCooldown,
            CombatSyncPayload::new);

    @Override
    public Type<CombatSyncPayload> type() {
        return TYPE;
    }
}
