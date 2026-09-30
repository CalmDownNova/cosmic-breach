package com.cosmicbreach.onboarding;

import com.cosmicbreach.world.AetheriaWorld;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * The welcome (GDD 1.3): for the first 60 s after a player arrives through a Breach, Aetheria's mobs ignore
 * them unless they attack; and nothing spawns naturally within 24 blocks of a Landing ({@link Landings#QUIET}).
 * Shardling packs consult {@link #shelters} when they pick their prey; other mobs are kept off by cancelling
 * their target change.
 */
public final class ArrivalGrace {
    public static final int TICKS = 60 * 20;
    private static final Map<UUID, Integer> UNTIL = new ConcurrentHashMap<>();

    private ArrivalGrace() {
    }

    /** {@code player} just arrived in Aetheria through a Breach. */
    public static void start(ServerPlayer player) {
        UNTIL.put(player.getUUID(), player.server.getTickCount() + TICKS);
    }

    /** True while mobs in Aetheria should leave {@code player} alone. Server side. */
    public static boolean shelters(Player player) {
        if (!(player instanceof ServerPlayer server) || !AetheriaWorld.is(server.level())) {
            return false;
        }
        Integer until = UNTIL.get(server.getUUID());
        return until != null && server.server.getTickCount() < until;
    }

    /** The grace ends the moment the player attacks. */
    static void end(Player player) {
        UNTIL.remove(player.getUUID());
    }

    static void forget(UUID player) {
        UNTIL.remove(player);
    }

    static void reset() {
        UNTIL.clear();
    }

    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob && event.getNewAboutToBeSetTarget() instanceof Player player && shelters(player)) {
            event.setCanceled(true);
        }
    }

    static void onAttack(AttackEntityEvent event) {
        end(event.getEntity());
    }

    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player && event.getEntity() != player) {
            end(player);
        }
    }

    /** No natural spawns near a Landing: the arrival is never met by a pack. */
    static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (AetheriaWorld.is(level)) {
            var pos = event.getPos();
            if (Landings.get(level).quiet(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        }
    }
}
