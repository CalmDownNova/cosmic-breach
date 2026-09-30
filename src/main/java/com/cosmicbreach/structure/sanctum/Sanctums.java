package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Breach Sanctum (W7, GDD 6.1 and 7.3), wired up:
 *
 * <ol>
 *   <li>Its shape ({@link SanctumLayout}, where it reaches the Deep: {@link SanctumSite}), the structure that builds it
 *       once per world ({@link SanctumStructure}, {@link SanctumPlacement}, {@link SanctumPiece},
 *       {@link SanctumBuilder}).</li>
 *   <li>The Gate ({@link GateRule}, {@link SanctumGate}, its veil {@link SanctumGateBlock} and {@link SanctumPasses}).</li>
 *   <li>The wings' puzzles lighting the Eclipse Locks and opening the Throne Stair ({@link LockRule},
 *       {@link SanctumLocks}).</li>
 *   <li>The arena for the Hollow Heliarch ({@link SanctumArena}), the throne and its summon hook
 *       ({@link SanctumThrone}), the fall rescue ({@link RescueRule}, {@link FallRescue}) and Voidsick.</li>
 * </ol>
 * State: {@link SanctumData}. Commands: {@link SanctumCommands}. Client side: {@code client.sanctum}.
 */
public final class Sanctums {
    private static final Map<ServerLevel, SanctumLayout> LAYOUTS = new WeakHashMap<>();

    private Sanctums() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        SanctumRegistry.register(modBus);
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class,
                event -> event.modify(StructureRegistry.VAULT_ENTITY.get(), SanctumRegistry.SANCTUM_VAULT.get()));
        modBus.addListener(RegisterPayloadHandlersEvent.class, SanctumNet::register);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(SanctumRegistry.DYING_STAR_HEART.get());
                event.accept(SanctumRegistry.EVENT_HORIZON_LENS.get());
                event.accept(SanctumRegistry.HOURGLASS_OF_VESPER.get());
            }
        });
        CombatHooks.register(Voidsick.HOOK);
        SanctumLocks.register();

        game.addListener(PlayerTickEvent.Post.class, FallRescue::onPlayerTick);
        game.addListener(ServerTickEvent.Post.class, Sanctums::onServerTick);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p) {
                SanctumGate.resync(p);
            }
        });
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p && event.getTo() == AetheriaWorld.LEVEL) {
                SanctumGate.resync(p);
            }
        });
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SanctumGate.forget(event.getEntity()));
        game.addListener(ServerStoppedEvent.class, event -> {
            synchronized (LAYOUTS) {
                LAYOUTS.clear();
            }
            SanctumGate.reset();
            SanctumThrone.reset();
            FallRescue.reset();
        });
        game.addListener(RegisterCommandsEvent.class, event -> SanctumCommands.register(event.getDispatcher()));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        SanctumThrone.tick(server);
        ServerLevel level = server.getLevel(AetheriaWorld.LEVEL);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        long t = level.getGameTime();
        if (t % 2 == 0) {
            SanctumGate.tick(level);
        }
        if (t % 10 == 5) {
            SanctumLocks.tick(level);
        }
    }

    /** The Sanctum of Aetheria (its layout from the terrain model and the world's seed, as worldgen builds it). */
    public static SanctumLayout layout(ServerLevel aetheria) {
        synchronized (LAYOUTS) {
            return LAYOUTS.computeIfAbsent(aetheria, l -> SanctumSite.of(AetheriaTerrain.of(l.getChunkSource().randomState())).layout(l.getSeed()));
        }
    }

    /** A line under the title, like the Shear bands' ("The song won't carry you yet."). */
    public static void subtitle(ServerPlayer p, Component text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(text));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }
}
