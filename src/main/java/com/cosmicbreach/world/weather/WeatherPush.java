package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Moving things with the Drift's flows: a Gravity Tide's current ({@value GravityTide#CURRENT} a tick along its
 * heading) plus the standing currents ({@value DriftCurrents#PUSH} a tick inside a tube). The flow moves an
 * entity by exactly that much each tick, before its own tick, on top of its own movement (walking with a
 * current is faster, against it slower; nothing piles up as speed), and never into a block (it slides along
 * one instead). The client moves its own player (its movement is the client's to send); the server moves
 * mobs, items, experience, falling blocks and TNT. Players that fly or spectate, riders and ridden mounts
 * are left alone.
 */
public final class WeatherPush {
    private WeatherPush() {
    }

    static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (!AetheriaWorld.is(level) || Layer.at(entity.getY()) != Layer.DRIFT || !movable(entity, level.isClientSide())) {
            return;
        }
        double[] flow = flow(level, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        if (flow[0] != 0.0 || flow[1] != 0.0) {
            move(entity, flow[0], flow[1]);
        }
    }

    /** The flow at a point in the Drift, blocks per tick as {x, z} (either side). */
    public static double[] flow(Level level, double x, double y, double z) {
        double tide = GravityTide.heading(level);
        double fx = 0.0;
        double fz = 0.0;
        if (!Double.isNaN(tide)) {
            fx += Math.cos(tide) * GravityTide.CURRENT;
            fz += Math.sin(tide) * GravityTide.CURRENT;
        }
        if (!CosmicWeather.synced(level)) {
            return new double[] {fx, fz}; // a client that hasn't heard the salt yet knows no currents
        }
        double[] current = DriftCurrents.push(CosmicWeather.currentSalt(level), x, y, z, tide);
        return new double[] {fx + current[0], fz + current[1]};
    }

    private static boolean movable(Entity entity, boolean clientSide) {
        if (entity.isPassenger() || entity.noPhysics || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof Player player) {
            // each client moves its own player; the server leaves players to them
            return clientSide && player.isLocalPlayer() && !player.isSpectator() && !player.getAbilities().flying;
        }
        if (clientSide || entity.getControllingPassenger() instanceof Player) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt;
    }

    /** Moves {@code entity} by (dx, dz) if the way is clear, else along whichever axis is. */
    static void move(Entity entity, double dx, double dz) {
        Level level = entity.level();
        AABB box = entity.getBoundingBox();
        if (level.noCollision(entity, box.move(dx, 0.0, dz))) {
            entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
            return;
        }
        if (dx != 0.0 && level.noCollision(entity, box.move(dx, 0.0, 0.0))) {
            entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ());
            box = entity.getBoundingBox();
        }
        if (dz != 0.0 && level.noCollision(entity, box.move(0.0, 0.0, dz))) {
            entity.setPos(entity.getX(), entity.getY(), entity.getZ() + dz);
        }
    }
}
