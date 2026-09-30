package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.guardian.unsung.UnsungRegistry;
import com.cosmicbreach.relic.Relics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Silence on enemies (GDD 7.3: a chord "silences enemies that aren't bosses (no abilities)"). It is the mod's one
 * Silence, the Unsung's {@code cosmicbreach:silenced} effect: on a player it already keeps the weapon's ability from
 * answering (its combat hook), and here it takes a mob's abilities away while it lasts:
 * <ul>
 *   <li>no shots, spells or summons: anything a silenced mob sends into the world with itself as the owner (arrows,
 *       fireballs, potions, tridents, Shardling needles, evoker fangs, vexes) fizzles as it appears;</li>
 *   <li>a creeper's fuse won't catch (it cools while silenced); an enderman can't teleport;</li>
 *   <li>the Hollow Stalker neither Shadow Steps nor Grasps (its own code asks {@link #silenced}).</li>
 * </ul>
 * Plain melee is not an ability and stays. Bosses ({@link #isBoss}) are never silenced.
 */
public final class Silence {
    private Silence() {
    }

    public static void register(IEventBus game) {
        game.addListener(EntityJoinLevelEvent.class, Silence::onJoin);
        game.addListener(EntityTickEvent.Pre.class, Silence::onEntityTick);
        game.addListener(EntityTeleportEvent.EnderEntity.class, event -> {
            if (!event.getEntity().level().isClientSide() && silenced(event.getEntityLiving())) {
                event.setCanceled(true);
            }
        });
    }

    /** True while {@code entity} is silenced (either side, for the effect is synced to its own player). */
    public static boolean silenced(LivingEntity entity) {
        return entity.hasEffect(UnsungRegistry.SILENCED);
    }

    /** Silences {@code entity} for {@code ticks} (a boss never; a longer Silence it already has stays). */
    public static void silence(LivingEntity entity, int ticks) {
        if (isBoss(entity)) {
            return;
        }
        MobEffectInstance current = entity.getEffect(UnsungRegistry.SILENCED);
        if (current == null || current.getDuration() < ticks - 5) {
            entity.addEffect(new MobEffectInstance(UnsungRegistry.SILENCED, ticks, 0, false, true, true));
        }
    }

    /**
     * True for a boss: tagged {@code c:bosses} (the Wither, the Ender Dragon, other mods' bosses), or one of the mod's
     * guardians and their parts (every entity of the guardian package: the Colossus and its shards, the Leviathan,
     * the Unsung's masks, the Heliarch).
     */
    public static boolean isBoss(Entity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES) || entity.getClass().getName().startsWith("com.cosmicbreach.guardian.");
    }

    /** A silenced mob's shot, spell or summon never arrives. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof TraceableEntity traceable)) {
            return;
        }
        Entity owner = traceable.getOwner();
        if (owner instanceof LivingEntity caster && !(caster instanceof Player) && silenced(caster)) {
            event.setCanceled(true);
            if (event.getLevel() instanceof ServerLevel level) {
                fizzle(level, event.getEntity().position());
            }
        }
    }

    /** A silenced creeper's fuse cools instead of burning (flint and steel still lights it). */
    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Creeper creeper && !creeper.level().isClientSide() && !creeper.isIgnited() && silenced(creeper)) {
            creeper.setSwellDir(-1);
        }
    }

    /** Where a silenced shot died: a puff of violet smoke and a hush. */
    public static void fizzle(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.01);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.02);
        ServerCombatSounds.forEveryone(level, at, Relics.UC_FIZZLE, 0.8f, 1.0f);
    }
}
