package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Thalassine Leviathan and its Rift (task G5), the common entry point: registrations, the dive path's payload, the
 * Rift's soft falls and the debug commands. Client side: {@code client.leviathan.LeviathanClient}.
 */
public final class Leviathans {
    /**
     * Falls that land inside a Rift count this share of their height: the lower shell catches anyone who drops (about 50
     * blocks in 0.4x gravity) and it is a climb back, not a death (Thalassine Leviathan design v1, "Falling").
     */
    public static final float RIFT_FALL = 0.3f;

    private Leviathans() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        LeviathanRegistry.register(modBus);
        com.cosmicbreach.guardian.GuardianPayouts.register(com.cosmicbreach.guardian.GuardianTypes.LEVIATHAN, LeviathanLoot.TABLE, null);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Leviathans::registerPayloads);
        game.addListener(LivingFallEvent.class, Leviathans::onFall);
        game.addListener(RegisterCommandsEvent.class, event -> LeviathanCommands.register(event.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        // a lambda calling the client class, so a dedicated server never loads it
        registrar.playToClient(LeviathanPathPayload.TYPE, LeviathanPathPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.leviathan.LeviathanClient.path(payload));
    }

    private static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level && AetheriaWorld.is(level) && event.getDistance() > 3.0f
                && insideRift(level, entity.getX(), entity.getY(), entity.getZ())) {
            event.setDistance(event.getDistance() * RIFT_FALL);
        }
    }

    /** True if a point is inside a known Leviathan Rift's sphere. */
    public static boolean insideRift(ServerLevel level, double x, double y, double z) {
        for (Map.Entry<BlockPos, GuardianLairs.Lair> e : GuardianLairs.get(level).all().entrySet()) {
            if (!e.getValue().guardian().equals("leviathan")) {
                continue;
            }
            BlockPos c = e.getValue().arenaCentre();
            double dx = (x - c.getX() - 0.5) / RiftLayout.RX;
            double dy = (y - c.getY() - 0.5) / RiftLayout.RY;
            double dz = (z - c.getZ() - 0.5) / RiftLayout.RX;
            if (dx * dx + dy * dy + dz * dz < 1.0) {
                return true;
            }
        }
        return false;
    }
}
