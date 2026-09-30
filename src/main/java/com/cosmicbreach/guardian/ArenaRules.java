package com.cosmicbreach.guardian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The anti-cheese rules every guardian arena shares (Prism Colossus design v1, "Rules and anti-cheese"; the
 * Heliarch's in GDD 7.3 are the same kind): blocks a player places inside the arena during a fight crumble after
 * {@value #CRUMBLE_TICKS} ticks, and projectiles fired from farther than the guardian allows ({@code maxRange}, 24
 * blocks for the Colossus) burn up before they touch it. Server side.
 */
public final class ArenaRules {
    public static final int CRUMBLE_TICKS = 40;
    private static final String FIRED_FROM = "cosmicbreach_fired_from";

    private ArenaRules() {
    }

    public static void register(IEventBus game) {
        game.addListener(BlockEvent.EntityPlaceEvent.class, ArenaRules::onPlace);
        game.addListener(EntityJoinLevelEvent.class, ArenaRules::onJoin);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        GuardianFights.Fight fight = GuardianFights.at(level, Vec3.atCenterOf(pos));
        if (fight != null) {
            fight.blockPlaced(pos.immutable());
        }
    }

    /** Remembers where each projectile was fired from while a fight is on. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile) || event.loadedFromDisk()
                || GuardianFights.all().isEmpty()) {
            return;
        }
        Entity owner = projectile.getOwner();
        Vec3 from = owner != null ? owner.position() : projectile.position();
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", from.x);
        tag.putDouble("y", from.y);
        tag.putDouble("z", from.z);
        projectile.getPersistentData().put(FIRED_FROM, tag);
    }

    /**
     * True if {@code source} is a projectile fired from farther than {@code maxRange} blocks (horizontally) from
     * {@code centre}: it burns up (removed, with a puff) and must not hurt or turn anything.
     */
    public static boolean burnsUp(DamageSource source, Vec3 centre, double maxRange) {
        return source.getDirectEntity() instanceof Projectile projectile && burnsUp(projectile, centre, maxRange);
    }

    public static boolean burnsUp(Projectile projectile, Vec3 centre, double maxRange) {
        CompoundTag tag = projectile.getPersistentData().getCompound(FIRED_FROM);
        if (tag.isEmpty()) {
            return false;
        }
        double dx = tag.getDouble("x") - centre.x;
        double dz = tag.getDouble("z") - centre.z;
        if (dx * dx + dz * dz <= maxRange * maxRange) {
            return false;
        }
        if (projectile.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.1, 0.1, 0.1, 0.02);
            level.sendParticles(ParticleTypes.SMOKE, projectile.getX(), projectile.getY(), projectile.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
            level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE,
                    0.6f, 1.6f);
        }
        projectile.discard();
        return true;
    }
}
