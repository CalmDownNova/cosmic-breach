package com.cosmicbreach.world;

import com.cosmicbreach.entity.gyre.GyreKnights;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.entity.stalker.Stalkers;
import com.cosmicbreach.mount.CelestialMount;
import com.cosmicbreach.mount.Mounts;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * AI level of detail for Aetheria's creatures (GDD 9.4, the mob budget: 1.5 ms a tick for 150 loaded mobs): a
 * Shardling, Gyre Knight, Hollow Stalker, Lumen Stag or Drift Manta ticks every tick within {@value #FULL} blocks of a
 * player, every 2nd tick out to {@value #HALF}, and every 5th beyond. A skipped tick is a whole tick (its AI and its
 * movement): the creature simply holds still that tick, far from anyone who could see it hesitate. Never skipped:
 * anything fighting (a target, or hurt lately), falling, swimming, ridden, leashed, or new to the world, so a fight,
 * a fall and a spawn always run at full rate; so does a mount climbing back from the void (1.1), which would otherwise
 * take five times as long when it is far from its owner, as it is when it has fallen a long way down. Ticks are
 * staggered by entity id so the skipped work spreads evenly.
 */
public final class AiLod {
    /** Full rate within this many blocks of a player. */
    public static final double FULL = 32.0;
    /** Every 2nd tick out to this; every 5th beyond. */
    public static final double HALF = 64.0;
    public static final int HALF_EVERY = 2;
    public static final int FAR_EVERY = 5;
    /** A creature is left at full rate for its first ticks in the world (it settles, finds the ground). */
    public static final int SETTLE_TICKS = 40;

    private static Set<EntityType<?>> types;
    private static volatile long skipped;

    private AiLod() {
    }

    public static void register(IEventBus game) {
        game.addListener(EventPriority.HIGH, EntityTickEvent.Pre.class, AiLod::onEntityTick);
    }

    /** How often a creature this far from the nearest player ticks: 1 (every tick), 2 or 5. Pure. */
    public static int every(double nearestPlayer) {
        if (nearestPlayer <= FULL) {
            return 1;
        }
        return nearestPlayer <= HALF ? HALF_EVERY : FAR_EVERY;
    }

    /** True if a creature ticking every {@code every} ticks ticks on this one (staggered by its id). Pure. */
    public static boolean ticksNow(int tickCount, int id, int every) {
        return every <= 1 || Math.floorMod(tickCount + id, every) == 0;
    }

    /** Ticks skipped so far (for the stress scenario's report). */
    public static long skipped() {
        return skipped;
    }

    private static Set<EntityType<?>> types() {
        if (types == null) {
            types = Set.of(ModEntities.SHARDLING.get(), GyreKnights.GYRE_KNIGHT.get(), Stalkers.HOLLOW_STALKER.get(),
                    Mounts.LUMEN_STAG.get(), Mounts.DRIFT_MANTA.get());
        }
        return types;
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide() || !(e instanceof Mob mob) || !types().contains(e.getType()) || busy(mob)) {
            return;
        }
        Player nearest = e.level().getNearestPlayer(e.getX(), e.getY(), e.getZ(), HALF + 1.0, false);
        double d = nearest == null ? Double.MAX_VALUE : nearest.distanceTo(e);
        if (!ticksNow(e.tickCount, e.getId(), every(d))) {
            event.setCanceled(true);
            skipped++;
        }
    }

    /** Fighting, falling, swimming, ridden, leashed or new: always at full rate. */
    private static boolean busy(Mob mob) {
        return mob.tickCount < SETTLE_TICKS || mob.getTarget() != null || mob.hurtTime > 0 || mob.isVehicle() || mob.isPassenger()
                || mob.isLeashed() || mob.isInWater() || mob.isInLava() || !mob.isNoGravity() && !mob.onGround() && !flies(mob)
                || mob.isDeadOrDying() || mob instanceof CelestialMount mount && mount.isRescuing();
    }

    private static boolean flies(Mob mob) {
        return mob.getType() == GyreKnights.GYRE_KNIGHT.get() || mob.getType() == Mounts.DRIFT_MANTA.get();
    }
}
