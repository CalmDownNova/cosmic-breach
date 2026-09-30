package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.WeaponDef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Server to client: every loaded move and weapon, on login and after {@code /reload}. */
public record CombatDataSyncPayload(Map<ResourceLocation, MoveDef> moves, Map<ResourceLocation, WeaponDef> weapons)
        implements CustomPacketPayload {
    public static final Codec<CombatDataSyncPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, MoveDef.CODEC).fieldOf("moves").forGetter(CombatDataSyncPayload::moves),
            Codec.unboundedMap(ResourceLocation.CODEC, WeaponDef.CODEC).fieldOf("weapons").forGetter(CombatDataSyncPayload::weapons)
    ).apply(i, CombatDataSyncPayload::new));
    public static final Type<CombatDataSyncPayload> TYPE = new Type<>(CosmicBreach.id("combat_data"));
    public static final StreamCodec<ByteBuf, CombatDataSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public static CombatDataSyncPayload of(CombatData data) {
        return new CombatDataSyncPayload(data.moves(), data.weapons());
    }

    @Override
    public Type<CombatDataSyncPayload> type() {
        return TYPE;
    }
}
