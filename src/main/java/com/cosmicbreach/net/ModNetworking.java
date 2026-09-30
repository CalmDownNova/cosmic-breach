package com.cosmicbreach.net;

import com.cosmicbreach.client.net.ClientPayloadHandlers;
import com.cosmicbreach.combat.server.CombatServerEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers the combat payloads (all handled on the main thread) and has the send helpers.
 *
 * <p>Client handlers live in the client-only {@link ClientPayloadHandlers}. Each is registered as a
 * lambda whose body calls it, never as a method reference: the lambda body only runs when a packet
 * reaches a client, so a dedicated server never loads the client class.
 */
public final class ModNetworking {
    public static final String PROTOCOL_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(CombatInputPayload.TYPE, CombatInputPayload.STREAM_CODEC, CombatServerEvents::onInput);
        registrar.playToClient(CombatSyncPayload.TYPE, CombatSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.combatSync(payload, context));
        registrar.playToClient(MoveStartedPayload.TYPE, MoveStartedPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.moveStarted(payload, context));
        registrar.playToClient(HitFxPayload.TYPE, HitFxPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.hitFx(payload, context));
        registrar.playToClient(CombatFxPayload.TYPE, CombatFxPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.combatFx(payload, context));
        registrar.playToClient(CombatDataSyncPayload.TYPE, CombatDataSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.combatData(payload, context));
        registrar.playToClient(MoveEffectPayload.TYPE, MoveEffectPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.moveEffect(payload, context));
    }

    /** To everyone tracking {@code entity}, and the entity itself if it is a player. */
    public static void sendToTrackersAndSelf(Entity entity, CustomPacketPayload payload) {
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        }
    }

    /** To everyone tracking {@code entity} but not the entity itself. */
    public static void sendToTrackers(Entity entity, CustomPacketPayload payload) {
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        }
    }

    /** To everyone tracking {@code entity}, the entity if it is a player, and {@code also}; each once. */
    public static void sendToTrackersSelfAnd(Entity entity, CustomPacketPayload payload, @Nullable ServerPlayer also) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Set<ServerPlayer> to = new LinkedHashSet<>(level.getChunkSource().chunkMap.getPlayersWatching(entity));
        if (entity instanceof ServerPlayer self) {
            to.add(self);
        }
        if (also != null) {
            to.add(also);
        }
        for (ServerPlayer player : to) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
