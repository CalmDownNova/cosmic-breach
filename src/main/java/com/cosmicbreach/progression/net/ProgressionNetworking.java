package com.cosmicbreach.progression.net;

import com.cosmicbreach.client.progression.ProgressionClientHandlers;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.progression.Attunement;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.progression.ProgressionRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * The progression payloads (main thread). The Attunement state itself travels as a synced
 * attachment. Client handlers are reached through lambdas only, as in {@link ModNetworking}, so a
 * dedicated server never loads the client class.
 */
public final class ProgressionNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ProgressionNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToServer(AllocatePointsPayload.TYPE, AllocatePointsPayload.STREAM_CODEC, ProgressionNetworking::onAllocate);
        registrar.playToClient(LevelUpPayload.TYPE, LevelUpPayload.STREAM_CODEC,
                (payload, context) -> ProgressionClientHandlers.levelUp(payload, context));
        registrar.playToClient(XpGainedPayload.TYPE, XpGainedPayload.STREAM_CODEC,
                (payload, context) -> ProgressionClientHandlers.xpGained(payload, context));
    }

    /** Spends points if the whole request is allowed; otherwise nothing changes and the client is corrected. */
    private static void onAllocate(AllocatePointsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Attunement before = Attunements.of(player);
        Attunement after = before.allocate(payload.add());
        if (after == null) {
            LOGGER.debug("[cosmicbreach] refused allocation {} from {} ({} points to spend, {} spent)",
                    payload.add(), player.getGameProfile().getName(), before.unspent(), before.spent());
            player.syncData(ProgressionRegistry.ATTUNEMENT);
            return;
        }
        Attunements.set(player, after);
    }
}
