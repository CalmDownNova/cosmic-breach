package com.cosmicbreach.structure;

import com.cosmicbreach.combat.server.BlockStrikes;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.trap.KineticTripwires;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The first dungeons of Aetheria (W5, GDD 6.1, 6.2, 6.4), wired up:
 *
 * <ol>
 *   <li>The Lens Array ({@code structure.lens}: the pure puzzle; {@code structure.array}: its blocks, the room's
 *       core and the rules at {@link LensArrays}).</li>
 *   <li>The vaults ({@code structure.vault}): sealed until the puzzle is solved, then once per player.</li>
 *   <li>The Kinetic Tripwires and gravity lifts ({@code structure.trap}), and the guard posts.</li>
 *   <li>The Spire Reliquary and the Gyre Observatory ({@code structure.gen}).</li>
 * </ol>
 * Config: {@link StructureConfig}. Commands: {@link StructureCommands}. Client side: {@code client.structure}.
 */
public final class Structures {
    private Structures() {
    }

    public static void register(IEventBus modBus, IEventBus game, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, StructureConfig.SPEC, StructureConfig.FILE);
        StructureRegistry.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Structures::onCreativeTab);

        game.addListener(PlayerInteractEvent.RightClickBlock.class, LensArrays::onRightClickBlock);
        game.addListener(PlayerInteractEvent.LeftClickBlock.class, LensArrays::onLeftClickBlock);
        game.addListener(BlockEvent.EntityPlaceEvent.class, LensArrays::onPlace);
        game.addListener(BlockEvent.BreakEvent.class, LensArrays::onBreak);
        game.addListener(ItemTossEvent.class, LensArrays::onToss);
        game.addListener(LivingDropsEvent.class, LensArrays::onDrops);
        game.addListener(LevelEvent.Unload.class, LensArrays::onLevelUnload);
        game.addListener(PlayerTickEvent.Post.class, KineticTripwires::onPlayerTick);
        game.addListener(ServerTickEvent.Post.class, KineticTripwires::onServerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> KineticTripwires.forget(event.getEntity().getUUID()));
        game.addListener(ServerStoppedEvent.class, event -> {
            LensArrays.reset();
            KineticTripwires.reset();
        });
        game.addListener(RegisterCommandsEvent.class, event -> StructureCommands.register(event.getDispatcher()));
        BlockStrikes.register(LensArrays::onStrike);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModBlocks.BLOCKS_TAB.getKey()) {
            event.accept(StructureRegistry.SUNSTONE.get());
            event.accept(StructureRegistry.NEBULITE_LAMP.get());
            event.accept(StructureRegistry.KINETIC_EMITTER.get());
        }
    }
}
