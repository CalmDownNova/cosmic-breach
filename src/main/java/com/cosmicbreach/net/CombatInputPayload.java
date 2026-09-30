package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.CombatAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/** Client to server: one combat input edge ({@link CombatAction} as a byte). */
public record CombatInputPayload(byte action) implements CustomPacketPayload {
    public static final Type<CombatInputPayload> TYPE = new Type<>(CosmicBreach.id("combat_input"));
    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(CombatInputPayload::new, CombatInputPayload::action);

    public static CombatInputPayload of(CombatAction action) {
        return new CombatInputPayload(action.id());
    }

    /** The action, or null for a byte no action has. */
    public @Nullable CombatAction decoded() {
        return CombatAction.byId(action);
    }

    @Override
    public Type<CombatInputPayload> type() {
        return TYPE;
    }
}
