package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.CombatAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/** Client to server: one combat input edge ({@link CombatAction} as a byte). */
public record CombatInputPayload(byte action, byte held) implements CustomPacketPayload {
    public static final Type<CombatInputPayload> TYPE = new Type<>(CosmicBreach.id("combat_input"));
    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, CombatInputPayload::action,
            ByteBufCodecs.BYTE, CombatInputPayload::held,
            CombatInputPayload::new);

    public static CombatInputPayload of(CombatAction action) {
        return new CombatInputPayload(action.id(), (byte) -1);
    }

    /** An attack release with how many ticks the player's client counted the button down (the server decides tap or hold by it). */
    public static CombatInputPayload release(int heldTicks) {
        return new CombatInputPayload(CombatAction.ATTACK_RELEASE.id(), (byte) Math.max(0, Math.min(127, heldTicks)));
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
