package com.cosmicbreach.world;

import com.cosmicbreach.CosmicBreach;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Aetheria's sound (GDD 2.4): the ambient beds each biome loops (Reach: high wind and crystal chimes; Drift:
 * a low drone and distant whale song; Deep: sub-bass and whispers), the three layer tracks the biomes' music
 * entries play (100 BPM, pentatonic), and the Arrival cue, played once per player the first time they enter
 * Aetheria. The files are synthesized by {@code tools/sound}; {@code assets/cosmicbreach/sounds.json} gives
 * each event its file and subtitle; the biome JSON (written by {@code tools/world/write_worldgen.py}) names
 * the beds and tracks.
 *
 * <p>The Arrival cue is remembered per player in {@link #HEARD_ARRIVAL} (saved, kept through death). The
 * onboarding task can play it at its own moment with {@link #playArrival}; entering Aetheria any other way
 * the first time plays it too.
 */
public final class AetheriaAudio {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BED_REACH = sound("ambient/reach");
    public static final DeferredHolder<SoundEvent, SoundEvent> BED_DRIFT = sound("ambient/drift");
    public static final DeferredHolder<SoundEvent, SoundEvent> BED_DEEP = sound("ambient/deep");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_REACH = sound("music/reach");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DRIFT = sound("music/drift");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DEEP = sound("music/deep");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ARRIVAL = sound("music/arrival");

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);
    /** On players (server): the Arrival cue has played for them. */
    public static final Supplier<AttachmentType<Boolean>> HEARD_ARRIVAL = ATTACHMENTS.register("heard_arrival",
            () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).copyOnDeath().build());

    /** Tells a client to play the Arrival cue now (through its music manager, so tracks never overlap). */
    public record ArrivalCuePayload() implements CustomPacketPayload {
        public static final ArrivalCuePayload INSTANCE = new ArrivalCuePayload();
        public static final Type<ArrivalCuePayload> TYPE = new Type<>(CosmicBreach.id("arrival_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArrivalCuePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<ArrivalCuePayload> type() {
            return TYPE;
        }
    }

    private AetheriaAudio() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, AetheriaAudio::registerPayloads);
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> onArrived(event.getEntity()));
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> onArrived(event.getEntity()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // a lambda calling the client class, so a dedicated server never loads it
        event.registrar("1").playToClient(ArrivalCuePayload.TYPE, ArrivalCuePayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.sky.AetheriaMusic.onArrivalCue());
    }

    private static void onArrived(Player player) {
        if (player instanceof ServerPlayer serverPlayer && AetheriaWorld.is(serverPlayer.level())
                && !serverPlayer.getData(HEARD_ARRIVAL)) {
            playArrival(serverPlayer);
        }
    }

    /** Plays the Arrival cue for {@code player} and marks it heard. */
    public static void playArrival(ServerPlayer player) {
        player.setData(HEARD_ARRIVAL, true);
        PacketDistributor.sendToPlayer(player, ArrivalCuePayload.INSTANCE);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
