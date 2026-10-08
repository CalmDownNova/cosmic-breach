package com.cosmicbreach.voice.boss;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The one message the voice sends: say take {@code variant} of line {@code line} of {@code boss}'s catalog now, fading out
 * the line playing first if {@code cut}. The client finds the rest in the catalog.
 */
public final class BossVoiceNet {
    private BossVoiceNet() {
    }

    public record Say(String boss, String line, String variant, boolean cut) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(CosmicBreach.id("boss_voice_say"));
        public static final StreamCodec<ByteBuf, Say> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Say::boss,
                ByteBufCodecs.STRING_UTF8, Say::line,
                ByteBufCodecs.STRING_UTF8, Say::variant,
                ByteBufCodecs.BOOL, Say::cut,
                Say::new);

        @Override
        public Type<Say> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        // a lambda calling the client class, so a dedicated server never loads it
        event.registrar("1").playToClient(Say.TYPE, Say.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.voice.BossVoiceClient.onSay(payload));
    }

    public static void say(Collection<ServerPlayer> to, String boss, String line, String variant, boolean cut) {
        Say say = new Say(boss, line, variant, cut);
        for (ServerPlayer p : to) {
            PacketDistributor.sendToPlayer(p, say);
        }
    }
}
