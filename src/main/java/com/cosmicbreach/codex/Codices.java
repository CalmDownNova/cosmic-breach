package com.cosmicbreach.codex;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.onboarding.Codex;
import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.LayerAttunement;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Starfall Codex's memory of each player (F1, GDD 9.4): which chapters have opened for them
 * ({@link CodexProgress}, a synced, saved attachment), kept up to date from what the world knows (layer
 * attunements, guardian kills, first arrival) plus what only happens once in a fight (meeting a guardian, seeing
 * the Heliarch summoned: {@link #met}, called by the guardians' boss bars). New chapters make the book stir. Also
 * the two things the book does in the world: the lair mote ({@link LairMote}) and the Lens Array hint its page
 * offers ({@link #hint}, asked by the client with {@link HintPayload}).
 */
public final class Codices {
    public static final ResourceLocation BREACH_SEALED = CosmicBreach.id("guardian/breach_sealed");
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);
    public static final Codec<CodexProgress> CODEC = Codec.LONG.xmap(CodexProgress::new, CodexProgress::bits);
    public static final StreamCodec<ByteBuf, CodexProgress> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(CodexProgress::new, CodexProgress::bits);

    /** Saved with the player, kept through death, synced to that player's client only. */
    public static final Supplier<AttachmentType<CodexProgress>> PROGRESS = ATTACHMENTS.register("codex_progress",
            () -> AttachmentType.builder(() -> CodexProgress.NONE)
                    .serialize(CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, STREAM_CODEC)
                    .build());

    /** A new chapter's stir waits this long, so it never talks over an arrival or a guardian's last breath. */
    static final int STIR_DELAY = 100;
    /** Everyone's chapters are looked at again this often (a backstop for changes no event reports). */
    static final int SWEEP_TICKS = 200;

    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<UUID, Integer> STIR_AT = new HashMap<>();

    private Codices() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Codices::registerPayloads);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> mark(e.getEntity()));
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> mark(e.getEntity()));
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> mark(e.getEntity()));
        game.addListener(AdvancementEvent.AdvancementEarnEvent.class, e -> mark(e.getEntity()));
        game.addListener(ServerTickEvent.Post.class, e -> onServerTick(e.getServer()));
        game.addListener(LevelTickEvent.Post.class, e -> {
            if (e.getLevel() instanceof ServerLevel level && AetheriaWorld.is(level)) {
                LairMote.tick(level);
            }
        });
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            DIRTY.remove(e.getEntity().getUUID());
            STIR_AT.remove(e.getEntity().getUUID());
            LairMote.forget(e.getEntity().getUUID());
        });
        game.addListener(ServerStoppedEvent.class, e -> {
            DIRTY.clear();
            STIR_AT.clear();
            LairMote.reset();
        });
        game.addListener(RegisterCommandsEvent.class, e -> CodexCommands.register(e.getDispatcher()));
    }

    /** A player's chapters, on either side (the client has its own copy). */
    public static CodexProgress of(Player player) {
        return player.getData(PROGRESS);
    }

    /** {@code player} has lived through {@code chapter} (met a guardian, saw the Heliarch summoned). */
    public static void met(ServerPlayer player, CodexChapter chapter) {
        store(player, of(player).with(chapter));
    }

    /** Looks at the world again for {@code player}'s chapters (next server tick). */
    public static void mark(Player player) {
        if (player instanceof ServerPlayer) {
            DIRTY.add(player.getUUID());
        }
    }

    /** Brings {@code player}'s chapters up to date now. */
    public static void refresh(ServerPlayer player) {
        store(player, CodexProgress.derive(of(player), facts(player)));
    }

    /** Sets the chapters outright (the debug command). */
    public static void set(ServerPlayer player, CodexProgress progress) {
        store(player, progress);
    }

    static CodexProgress.Facts facts(ServerPlayer player) {
        return new CodexProgress.Facts(AetheriaWorld.is(player.level()), LayerAttunement.has(player, Layer.DRIFT),
                LayerAttunement.has(player, Layer.DEEP), LayerAttunement.hasSanctum(player),
                GuardianRewards.killedBefore(player, GuardianTypes.COLOSSUS), GuardianRewards.killedBefore(player, GuardianTypes.LEVIATHAN),
                GuardianRewards.killedBefore(player, GuardianTypes.UNSUNG), GuardianRewards.hasAdvancement(player, BREACH_SEALED));
    }

    private static void store(ServerPlayer player, CodexProgress next) {
        CodexProgress before = of(player);
        if (next.bits() == before.bits()) {
            return;
        }
        player.setData(PROGRESS, next);
        if (!next.newSince(before).isEmpty()) {
            STIR_AT.put(player.getUUID(), player.server.getTickCount() + STIR_DELAY);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % SWEEP_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                DIRTY.add(p.getUUID());
            }
        }
        if (!DIRTY.isEmpty()) {
            for (UUID id : DIRTY.toArray(new UUID[0])) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    refresh(p);
                }
            }
            DIRTY.clear();
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = STIR_AT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            if (now < e.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            // the book only stirs for someone carrying it
            if (p != null && Codex.carries(p)) {
                p.displayClientMessage(Component.translatable("cosmicbreach.codex.new_pages"), true);
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.1f);
            }
        }
    }

    // ------------------------------------------------------------------ the Lens Array's hint

    /** What the Codex's hint link did (for tests and the command). */
    public enum HintResult { NO_ARRAY, NOT_YET, LIT, NOTHING_LEFT }

    /**
     * The Lens Array page's hint (GDD 6.2): if the nearest unsolved array within 32 blocks has waited long enough,
     * one mirror of its answer lights; otherwise the player is told how long the book still needs.
     */
    public static HintResult hint(ServerPlayer player) {
        LensCoreBlockEntity core = LensArrays.nearest(player);
        if (core == null || core.phase() != LensCoreBlockEntity.ACTIVE || !(core.getLevel() instanceof ServerLevel level)) {
            player.displayClientMessage(Component.translatable("cosmicbreach.codex.hint.none"), true);
            return HintResult.NO_ARRAY;
        }
        if (core.hintOffered()) {
            if (core.takeHint(level)) {
                return HintResult.LIT;
            }
            player.displayClientMessage(Component.translatable("cosmicbreach.codex.hint.nothing"), true);
            return HintResult.NOTHING_LEFT;
        }
        long wait = core.ticksToHint(level.getGameTime());
        if (wait < 0) {
            player.displayClientMessage(Component.translatable("cosmicbreach.codex.hint.lit"), true);
            return HintResult.NOTHING_LEFT;
        }
        long minutes = Math.max(1, (wait + 20 * 60 - 1) / (20 * 60));
        player.displayClientMessage(Component.translatable("cosmicbreach.codex.hint.wait", minutes), true);
        return HintResult.NOT_YET;
    }

    /** Sent by the Codex's Lens Array page when its hint link is clicked. */
    public record HintPayload() implements CustomPacketPayload {
        public static final HintPayload INSTANCE = new HintPayload();
        public static final Type<HintPayload> TYPE = new Type<>(CosmicBreach.id("codex_hint"));
        public static final StreamCodec<ByteBuf, HintPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<HintPayload> type() {
            return TYPE;
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(HintPayload.TYPE, HintPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                hint(player);
            }
        });
    }
}
