package com.cosmicbreach.accessory;

import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.progression.ProgressionStats;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Halo of Nine (GDD 5.2): three shards orbit the wearer (radius 1.6, one turn a second, {@link AccessoryRules#shardOffset}).
 * A projectile about to strike them breaks the shard nearest to it instead and is gone; a trident's blow is stopped
 * instead, so vanilla bounces it off and its thrower can pick it up. The shard grows back 120 ticks later. At Arcane 20 or more a shard cuts whatever enemy it passes through: 3 damage, then
 * that shard rests 10 ticks. The shards' state is a synced attachment ({@link HaloState}), so every client draws them.
 * Server only.
 */
final class HaloOfNine {
    /** When each wearer's shards last cut, per shard. */
    private static final Map<UUID, long[]> LAST_CUT = new ConcurrentHashMap<>();

    private HaloOfNine() {
    }

    static void forget(UUID player) {
        LAST_CUT.remove(player);
    }

    static void clear() {
        LAST_CUT.clear();
    }

    /** Where shard {@code i} of {@code player}'s halo is at {@code time}. */
    static Vec3 shardAt(Entity player, int i, double time) {
        double[] o = AccessoryRules.shardOffset(i, time);
        return new Vec3(player.getX() + o[0], player.getY() + AccessoryRules.HALO_HEIGHT, player.getZ() + o[1]);
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean wearing = player.isAlive() && Worn.wears(player, Accessory.HALO_OF_NINE);
        HaloState state = player.getData(AccessoryRegistry.HALO);
        if (state.worn() != wearing) {
            state = new HaloState(wearing, state.regrow0(), state.regrow1(), state.regrow2());
            player.setData(AccessoryRegistry.HALO, state);
        }
        if (!wearing) {
            return;
        }
        long now = player.level().getGameTime();
        for (int i = 0; i < AccessoryRules.HALO_SHARDS; i++) {
            if (state.regrowAt(i) == now) {
                Vec3 middle = player.position().add(0, AccessoryRules.HALO_HEIGHT, 0);
                player.level().playSound(null, middle.x, middle.y, middle.z, AccessoryRegistry.HALO_REGROW.get(), SoundSource.PLAYERS,
                        0.7f, 1.0f + 0.1f * i);
                ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.SHARD_REGROWN, player.getId(),
                        middle, 0f, i));
            }
        }
        if (AccessoryRules.shardsCut(ProgressionStats.of(player).arcane())) {
            cut(player, state, now);
        }
    }

    /** Each shard that is there and rested cuts every enemy it passes through. */
    private static void cut(ServerPlayer player, HaloState state, long now) {
        ServerLevel level = player.serverLevel();
        long[] last = LAST_CUT.computeIfAbsent(player.getUUID(), id -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
        Holder<DamageType> type = null;
        for (int i = 0; i < AccessoryRules.HALO_SHARDS; i++) {
            if (!state.alive(i, now) || !AccessoryRules.cutReady(last[i], now)) {
                continue;
            }
            Vec3 at = shardAt(player, i, now);
            AABB reach = new AABB(at, at).inflate(AccessoryRules.HALO_CUT_REACH + 1.0);
            boolean cut = false;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach,
                    t -> HitResolver.isValidTarget(player, t) && AccessoryHooks.isEnemy(player, t))) {
                if (!target.getBoundingBox().inflate(AccessoryRules.HALO_CUT_REACH).contains(at)) {
                    continue;
                }
                if (type == null) {
                    type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(AccessoryRegistry.HALO_DAMAGE);
                }
                target.invulnerableTime = 0;
                if (target.hurt(new DamageSource(type, player, player), AccessoryRules.HALO_CUT_DAMAGE)) {
                    cut = true;
                }
            }
            if (cut) {
                last[i] = now;
                level.playSound(null, at.x, at.y, at.z, AccessoryRegistry.HALO_CUT.get(), SoundSource.PLAYERS, 0.6f, 1.0f + 0.08f * i);
                ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.SHARD_CUT, player.getId(), at, 0f, i));
            }
        }
    }

    /** A projectile about to strike a wearer with a shard left: the shard takes it (a trident: its blow, below). */
    static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile instanceof ThrownTrident || projectile.level().isClientSide() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (block(player, projectile)) {
            event.setCanceled(true);
        }
    }

    /**
     * A projectile's blow that no impact announced (a trident's, which bounces off when its blow does nothing; a mod's
     * own projectile): a shard takes it all the same.
     */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        if (block(player, projectile)) {
            event.setCanceled(true);
        }
    }

    /** True if a shard of {@code player}'s halo broke on {@code projectile} (which is then gone). */
    static boolean block(ServerPlayer player, Projectile projectile) {
        if (projectile.isRemoved() || !player.isAlive() || !Worn.wears(player, Accessory.HALO_OF_NINE)) {
            return false;
        }
        Entity owner = projectile.getOwner();
        if (owner == player || owner instanceof Player other && !player.canHarmPlayer(other)) {
            return false; // its own shots, and friends' that couldn't hurt anyway
        }
        long now = player.level().getGameTime();
        HaloState state = player.getData(AccessoryRegistry.HALO);
        int shard = nearestAlive(player, state, projectile.position(), now);
        if (shard < 0) {
            return false;
        }
        player.setData(AccessoryRegistry.HALO, state.broken(shard, now));
        Vec3 at = projectile.position();
        if (!(projectile instanceof ThrownTrident)) {
            projectile.discard(); // a trident is left to vanilla: its blow did nothing, so it bounces off to be picked up
        }
        player.level().playSound(null, at.x, at.y, at.z, AccessoryRegistry.HALO_BLOCK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.SHARD_BROKEN, player.getId(), at, 0f, shard));
        return true;
    }

    /** The shard still there nearest to {@code from}, or -1 for none. */
    private static int nearestAlive(ServerPlayer player, HaloState state, Vec3 from, long now) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < AccessoryRules.HALO_SHARDS; i++) {
            if (!state.alive(i, now)) {
                continue;
            }
            double d = shardAt(player, i, now).distanceToSqr(from);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** How many shards {@code player}'s halo has now (the scenario reads it). */
    static int shards(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        HaloState state = player.getData(AccessoryRegistry.HALO);
        return state.worn() ? state.count(player.level().getGameTime()) : 0;
    }
}
