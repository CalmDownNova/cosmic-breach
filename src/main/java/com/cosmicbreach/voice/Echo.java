package com.cosmicbreach.voice;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Starfall's voice (A1): the Choir's last hum, a guide who speaks to one player at a few key moments. The API
 * for the rest of the mod is one call:
 *
 * <pre>{@code Echo.say(player, EchoLine.DEEP);}</pre>
 *
 * It plays the line to that player only (their client, {@code SoundSource.VOICE}, not positional), once ever per
 * player ({@link #HEARD}, saved and kept through death), and never over another line (the client queues them,
 * {@link EchoQueue}). A guardian speaks through its type instead: {@code GuardianType.echo()} is said to every
 * participant of its first kill, after the rewards ({@code GuardianRewards}).
 *
 * <p>Wired here: {@link EchoLine#FIRST_SHARD} on picking up a Starfall Shard and {@link EchoLine#ARRIVAL} on
 * entering Aetheria; {@code BreachRings.useShard} says {@link EchoLine#RING_OPEN}. Sounds (made by
 * {@code tools/sound/voice.py}) and their subtitles live in this class's register and
 * {@code assets/cosmicbreach_voice/lang}. Debug: {@code /cosmicbreach echo status|reset|play <line>}
 * ({@link EchoCommands}).
 */
public final class Echo {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);

    private static final Map<EchoLine, DeferredHolder<SoundEvent, SoundEvent>> SOUND = new EnumMap<>(EchoLine.class);

    static {
        for (EchoLine line : EchoLine.values()) {
            SOUND.put(line, SOUNDS.register(line.soundPath(),
                    () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(line.soundPath()))));
        }
    }

    /** On players (server): the lines they have been told. */
    public static final Supplier<AttachmentType<EchoMemory>> HEARD = ATTACHMENTS.register("echo_heard",
            () -> AttachmentType.builder(() -> EchoMemory.NEW).serialize(EchoMemory.CODEC).copyOnDeath().build());

    /** Tells a client to say a line (it queues it, {@link EchoQueue}). */
    public record Say(EchoLine line) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(CosmicBreach.id("echo_say"));
        public static final StreamCodec<ByteBuf, Say> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
                id -> new Say(EchoLine.byId(id).orElseThrow(() -> new IllegalArgumentException("unknown Echo line " + id))),
                say -> say.line().id());

        @Override
        public Type<Say> type() {
            return TYPE;
        }
    }

    private Echo() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").playToClient(Say.TYPE,
                Say.STREAM_CODEC, (payload, context) -> com.cosmicbreach.client.voice.EchoClient.onSay(payload.line())));
        game.addListener(ItemEntityPickupEvent.Post.class, Echo::onPickup);
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getTo() == AetheriaWorld.LEVEL && event.getEntity() instanceof ServerPlayer player) {
                say(player, EchoLine.ARRIVAL);
            }
        });
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && AetheriaWorld.is(player.level())) {
                say(player, EchoLine.ARRIVAL);
            }
        });
        game.addListener(RegisterCommandsEvent.class, event -> EchoCommands.register(event.getDispatcher()));
    }

    /**
     * Says {@code line} to {@code player}: their client plays it once nothing else of the Echo is playing and the
     * line's lead-in has passed. Only the first time (and not once the line has become pointless,
     * {@link EchoLine#supersededBy()}). True if it was sent now.
     */
    public static boolean say(ServerPlayer player, EchoLine line) {
        EchoMemory memory = player.getData(HEARD);
        if (!memory.shouldSay(line)) {
            return false;
        }
        player.setData(HEARD, memory.with(line));
        PacketDistributor.sendToPlayer(player, new Say(line));
        CosmicBreach.LOGGER.debug("[cosmicbreach] Echo: {} to {}", line.id(), player.getGameProfile().getName());
        return true;
    }

    /** Plays {@code line} to {@code player} now, heard before or not, and leaves their memory alone (debug). */
    public static void play(ServerPlayer player, EchoLine line) {
        PacketDistributor.sendToPlayer(player, new Say(line));
    }

    public static boolean heard(ServerPlayer player, EchoLine line) {
        return player.getData(HEARD).heard(line);
    }

    /** Forgets every line {@code player} has heard (debug). */
    public static void forget(ServerPlayer player) {
        player.setData(HEARD, EchoMemory.NEW);
    }

    public static SoundEvent sound(EchoLine line) {
        return SOUND.get(line).get();
    }

    private static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getOriginalStack().is(OnboardingRegistry.STARFALL_SHARD.get())) {
            say(player, EchoLine.FIRST_SHARD);
        }
    }
}
