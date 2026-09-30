package com.cosmicbreach.familiar;

import com.cosmicbreach.net.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The familiars' packets: the familiar key up, their moments down ({@link FamiliarFxPayload}). */
public final class FamiliarNet {
    private FamiliarNet() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToServer(FamiliarKeyPayload.TYPE, FamiliarKeyPayload.STREAM_CODEC, FamiliarNet::onKey);
        registrar.playToClient(FamiliarFxPayload.TYPE, FamiliarFxPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.familiar.FamiliarFx.handle(payload, context));
    }

    private static void onKey(FamiliarKeyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            if (payload.action() == FamiliarKeyPayload.TOGGLE) {
                FamiliarSessions.toggle(player, null);
            } else if (payload.action() == FamiliarKeyPayload.CYCLE) {
                FamiliarSessions.cycle(player);
            }
        }
    }

    /** A moment about {@code around} to everyone who sees it (and to it, if it is a player). */
    static void fx(Entity around, int kind, int a, int b, Vec3 at, int value) {
        ModNetworking.sendToTrackersAndSelf(around, new FamiliarFxPayload(kind, a, b, at, value));
    }

    /** A moment at a place to everyone within 48 blocks of it. */
    static void fxAt(ServerLevel level, Vec3 at, int kind, int a, int value) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 48.0, new FamiliarFxPayload(kind, a, -1, at, value));
    }
}
