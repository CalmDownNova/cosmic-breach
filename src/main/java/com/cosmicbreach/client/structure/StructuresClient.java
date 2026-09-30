package com.cosmicbreach.client.structure;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.UpdraftRiders;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The structures' client side (W5): the Lens Array's beams and hint ({@link LensBeamRenderer}), the receptors'
 * hum ({@link ReceptorHum}), the tripwires' threads ({@link ThreadRenderer}) and their shimmer as one appears, the
 * lift's rush as you step into an updraft, and the vault's glints while it still holds your share.
 */
public final class StructuresClient {
    private static boolean riding;
    private static final Set<BlockPos> SHOWN_BEFORE = new HashSet<>();

    private StructuresClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(StructureRegistry.LENS_CORE_ENTITY.get(), context -> new LensBeamRenderer());
            event.registerBlockEntityRenderer(StructureRegistry.KINETIC_EMITTER_ENTITY.get(), context -> new ThreadRenderer());
        });
        game.addListener(ClientTickEvent.Post.class, event -> tick());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ReceptorHum.reset();
            ThreadRenderer.SHOWN.clear();
            SHOWN_BEFORE.clear();
        });
        VaultBlockEntity.localPlayer = () -> Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ReceptorHum.tick(mc);
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        boolean ridingNow = UpdraftRiders.riding(mc.player, now);
        if (ridingNow && !riding) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), StructureRegistry.UPDRAFT_RUSH.get(),
                    SoundSource.BLOCKS, 0.8f, 1.0f, false);
        }
        riding = ridingNow;
        // a thread's shimmer, the moment it shows
        Set<BlockPos> shown = new HashSet<>();
        ThreadRenderer.SHOWN.forEach((pos, t) -> {
            if (now - t <= 2) {
                shown.add(pos);
            }
        });
        for (BlockPos pos : shown) {
            if (!SHOWN_BEFORE.contains(pos)) {
                mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, StructureRegistry.THREAD.get(),
                        SoundSource.BLOCKS, 0.6f, 1.0f, false);
            }
        }
        SHOWN_BEFORE.clear();
        SHOWN_BEFORE.addAll(shown);
        ThreadRenderer.SHOWN.values().removeIf(t -> now - t > 40);
    }
}
