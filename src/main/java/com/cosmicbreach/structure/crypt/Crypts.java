package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.choir.ChoirFloors;
import com.cosmicbreach.structure.crypt.trap.CryptTraps;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Hollow Crypt (W6, GDD 6.1, 6.3, 6.4), wired up:
 *
 * <ol>
 *   <li>The Choir Floor ({@code structure.choir}: the pure play in {@link com.cosmicbreach.structure.choir.ChoirSession},
 *       the tunes in {@link com.cosmicbreach.structure.choir.ChoirGenerator}, the judge; the room at
 *       {@link com.cosmicbreach.structure.choir.ChoirRoom}, its Conductor, and {@link ChoirFloors}).</li>
 *   <li>The traps ({@code structure.crypt.trap}): Void Rift tiles over Void Pockets, Crushing Gravity Plates, Starfall
 *       Chutes (and W5's Kinetic Tripwires), each with the careful-movement tells.</li>
 *   <li>The crypt ({@link CryptLayout}, {@link CryptPiece}, {@link HollowCryptStructure}) and its vault.</li>
 * </ol>
 * Config: {@link CryptConfig}. Commands: {@link CryptCommands}. Client side: {@code client.crypt}.
 */
public final class Crypts {
    private Crypts() {
    }

    public static void register(IEventBus modBus, IEventBus game, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CryptConfig.SPEC, CryptConfig.FILE);
        CryptRegistry.register(modBus);
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class, event -> {
            event.modify(StructureRegistry.VAULT_ENTITY.get(), CryptRegistry.CRYPT_VAULT.get());
            event.modify(StructureRegistry.KINETIC_EMITTER_ENTITY.get(), CryptRegistry.UMBRAL_EMITTER.get());
        });
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Crypts::onCreativeTab);

        game.addListener(PlayerTickEvent.Post.class, CryptTraps::onPlayerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CryptTraps.forget(player);
            }
        });
        game.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
                ChoirFloors.unload(level);
            }
        });
        game.addListener(ServerStoppedEvent.class, event -> {
            ChoirFloors.reset();
            CryptTraps.reset();
            CryptConfig.override(null);
        });
        game.addListener(RegisterCommandsEvent.class, event -> CryptCommands.register(event.getDispatcher()));
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModBlocks.BLOCKS_TAB.getKey()) {
            event.accept(CryptRegistry.RESONANCE_FLOOR.get());
            event.accept(CryptRegistry.POCKET_SEAL.get());
            event.accept(CryptRegistry.UMBRAL_EMITTER.get());
        }
    }
}
