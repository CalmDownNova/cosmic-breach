package com.cosmicbreach.satchel;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.net.ModNetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Satchel's packets: open (the key), a request from its screen, and the contents back to the open screen. */
public final class SatchelNet {
    private SatchelNet() {
    }

    /** Client to server: the open key (B). */
    public record OpenPayload() implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(CosmicBreach.id("satchel_open"));
        public static final StreamCodec<ByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.unit(new OpenPayload());

        @Override
        public Type<OpenPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToServer(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, SatchelNet::onOpen);
        registrar.playToServer(SatchelActionPayload.TYPE, SatchelActionPayload.STREAM_CODEC, SatchelNet::onAction);
        registrar.playToClient(SatchelContentsPayload.TYPE, SatchelContentsPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.satchel.SatchelClient.contents(payload, context));
    }

    private static void onOpen(OpenPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            SatchelLocator.find(player).ifPresent(found -> SatchelMenu.open(player, found.ref()));
        }
    }

    private static void onAction(SatchelActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SatchelMenu menu
                && menu.containerId == payload.containerId()) {
            menu.act(player, payload.action(), payload.item(), payload.amount());
        }
    }

    /** Client to server: a request from the open Satchel screen. */
    public record SatchelActionPayload(int containerId, int action, ResourceLocation item, int amount) implements CustomPacketPayload {
        public static final Type<SatchelActionPayload> TYPE = new Type<>(CosmicBreach.id("satchel_action"));
        public static final StreamCodec<ByteBuf, SatchelActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SatchelActionPayload::containerId,
                ByteBufCodecs.VAR_INT, SatchelActionPayload::action,
                ResourceLocation.STREAM_CODEC, SatchelActionPayload::item,
                ByteBufCodecs.VAR_INT, SatchelActionPayload::amount,
                SatchelActionPayload::new);

        @Override
        public Type<SatchelActionPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: what the open Satchel holds. */
    public record SatchelContentsPayload(int containerId, SatchelContents contents) implements CustomPacketPayload {
        public static final Type<SatchelContentsPayload> TYPE = new Type<>(CosmicBreach.id("satchel_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SatchelContentsPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SatchelContentsPayload::containerId,
                SatchelContents.STREAM_CODEC, SatchelContentsPayload::contents,
                SatchelContentsPayload::new);

        @Override
        public Type<SatchelContentsPayload> type() {
            return TYPE;
        }
    }
}
