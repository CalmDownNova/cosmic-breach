package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.server.TargetShield;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.MeteorShower;
import com.cosmicbreach.world.weather.SolarFlare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Keeping the mod's mounts (1.1 design section 4): a tamed mount takes no harm from its owner or the weather, never drifts
 * while parked ({@code WeatherPush}), is never taken by a gap ({@code ShearBands}), climbs back to its last safe spot when
 * it ends up below its layer ({@link #tick}), and can be stowed in a Stable Crystal ({@link Stable}).
 */
public final class MountCare {
    /** The mod's mounts ({@code data/cosmicbreach/tags/entity_type/celestial_mounts.json}). */
    public static final TagKey<EntityType<?>> CELESTIAL_MOUNTS = TagKey.create(Registries.ENTITY_TYPE, CosmicBreach.id("celestial_mounts"));

    private MountCare() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        Stable.register(modBus);
        Stable.registerGame(game);
        game.addListener(LivingIncomingDamageEvent.class, MountCare::onIncomingDamage);
    }

    /**
     * True if {@code entity} is one of the mod's mounts with an owner: in the mounts tag, or a {@link CelestialMount} all the
     * same (a later mount left out of the tag would otherwise lose the drift and gap protections without a word).
     */
    public static boolean tamedMount(Entity entity) {
        return (entity.getType().is(CELESTIAL_MOUNTS) || entity instanceof CelestialMount)
                && entity instanceof OwnableEntity o && o.getOwnerUUID() != null;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CelestialMount mount) || mount.level().isClientSide || !mount.isTamed()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        boolean fromOwner = attacker != null && attacker.getUUID().equals(mount.getOwnerUUID());
        // the Flare's scorch counts as weather only under a burning Flare: the gear's own Scorch status has the same damage type
        boolean weather = source.is(MeteorShower.METEOR) || source.is(SolarFlare.SCORCH) && SolarFlare.exposed(mount);
        // another player's hit follows the server's PvP setting and teams, as the mod's own weapons do: a mount of someone they
        // may not hurt, or carrying someone they may not hurt, is sheltered, parked or ridden
        boolean sheltered = attacker instanceof ServerPlayer player && TargetShield.shelters(player, mount);
        if (MountCareRules.shrugsOff(fromOwner, sheltered, weather)) {
            event.setCanceled(true);
        }
    }

    /** One server tick of a tamed mount's care, from {@link CelestialMount#tick}. */
    static void tick(CelestialMount mount) {
        if (!mount.isTamed() || !AetheriaWorld.is(mount.level())) {
            mount.endRescue();
            return;
        }
        MountGear.Kind kind = mount.kind();
        if (!mount.isRescuing() && MountCareRules.rememberNow(mount.tickCount, mount.getId())
                && MountCareRules.safeHere(kind, mount.getY(), mount.onGround(), Layer.at(mount.getY()) == Layer.DRIFT)) {
            mount.rememberSafe(mount.position());
            mount.clearPlaced(); // it has a safe spot of its own now: ordinary care from here on
        }
        Vec3 safe = mount.lastSafe();
        if (safe == null) {
            safe = ownersSide(mount, kind);
            if (safe == null) {
                return;
            }
        }
        if (!mount.isRescuing()) {
            if (MountCareRules.needsRescue(true, mount.isVehicle(), true, kind, mount.getY())) {
                mount.startRescue();
            }
            return;
        }
        if (mount.isVehicle()) {
            mount.endRescue();
            return;
        }
        if (mount.position().distanceTo(safe) <= MountCareRules.HOME) {
            arrive(mount, safe);
            return;
        }
        mount.rescueStep(safe);
    }

    /**
     * Within reach of its spot. A mount that falls must have ground there (the world may have changed since it stood on it):
     * if not, the nearest ground takes the spot's place and the climb goes on to it, or the spot is forgotten. Left in the
     * air it would fall, climb back and fall again for ever.
     */
    private static void arrive(CelestialMount mount, Vec3 safe) {
        if (!mount.needsGround() || MountGround.standsAt(mount.level(), mount, safe)) {
            mount.endRescue();
            return;
        }
        Vec3 ground = MountGround.groundNear(mount.level(), mount.getType(), BlockPos.containing(safe), MountGround.LEDGE_UP, MountGround.LEDGE_DOWN);
        if (ground == null || ground.distanceToSqr(safe) < 0.25) {
            mount.forgetSafe(); // nowhere to stand: it is left as it is (its owner's side may take it)
        } else {
            mount.rememberSafe(ground); // the next tick carries on toward it
        }
    }

    /**
     * A mount below its layer that never recorded a safe spot (one from a world saved before 1.1, say) takes its owner's
     * side as one, if the owner is online in this level, near, and standing where such a mount is safe; the normal climb
     * then runs. Null if it may not. No chunk is loaded to find the spot: the owner is right here.
     */
    private static @Nullable Vec3 ownersSide(CelestialMount mount, MountGear.Kind kind) {
        // a mount a crystal set down stays where it was put: the fallback is for mounts from worlds saved before 1.1
        if (mount.placedByCrystal() || !MountCareRules.needsRescue(true, mount.isVehicle(), true, kind, mount.getY())
                || !(mount.getOwner() instanceof Player owner)) {
            return null;
        }
        if (owner.level() != mount.level() || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }
        double near = Math.hypot(owner.getX() - mount.getX(), owner.getZ() - mount.getZ());
        if (!MountCareRules.ownerSpotWorks(kind, owner.getY(), owner.onGround(), Layer.at(owner.getY()) == Layer.DRIFT, near)) {
            return null;
        }
        Vec3 spot = mount.needsGround()
                ? MountGround.standingBeside(owner.level(), mount.getType(), owner.position(), mount.position())
                : MountCareRules.ownerSpot(owner.position(), mount.position());
        if (spot == null) {
            return null; // no ground beside its owner for it to stand on
        }
        mount.rememberSafe(spot);
        return spot;
    }
}
