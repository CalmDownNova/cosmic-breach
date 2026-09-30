package com.cosmicbreach.familiar;

import com.cosmicbreach.guardian.GuardianPart;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.relic.cantor.Silence;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Gravikin's taunt (GDD 8.2 and 7.3): every enemy within {@value FamiliarRules#TAUNT_RADIUS} blocks targets the
 * Gravikin for {@value FamiliarRules#TAUNT_TICKS} ticks, and whatever else tries to change their mind meanwhile is
 * overruled ({@link LivingChangeTargetEvent}). Bosses ({@code c:bosses} and the guardians) aren't pulled; instead the
 * taunt counts as {@value FamiliarRules#TAUNT_THREAT} threat for the Gravikin's owner, which the Hollow Heliarch's
 * {@code ThreatTable} reads. When the taunt runs out, a creature still on the Gravikin goes back to what it was after
 * (a monster that was after nothing turns on the owner, who set the Gravikin on it).
 */
public final class Taunts {
    /** A creature held on a Gravikin till {@code until}, and what it was after before ({@code before}, an entity id, -1 for nothing). */
    private record Pull(ResourceKey<Level> level, int gravikin, long until, int before) {
    }

    private static final Map<UUID, Pull> PULLED = new HashMap<>();

    private Taunts() {
    }

    /**
     * {@code gravikin} taunts for {@code owner} in {@code mode} at {@code now}: returns how many answered (creatures
     * pulled plus bosses given threat). Nothing is spent when none did.
     */
    static int taunt(Gravikin gravikin, ServerPlayer owner, FamiliarMode mode, long now) {
        ServerLevel level = (ServerLevel) gravikin.level();
        Vec3 centre = gravikin.position().add(0, gravikin.getBbHeight() * 0.5, 0);
        int answered = 0;
        Set<Entity> bosses = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, gravikin.getBoundingBox().inflate(FamiliarRules.TAUNT_RADIUS + 1.0),
                e -> e.isAlive() && e != owner && !(e instanceof FamiliarEntity) && !ownedBy(e, owner))) {
            double d = Math.sqrt(e.getBoundingBox().distanceToSqr(centre));
            Entity body = e instanceof GuardianPart part && part.owner() != null ? part.owner() : e;
            if (Silence.isBoss(body)) {
                if (FamiliarRules.tauntsBoss(mode, d) && bosses.add(body)) {
                    if (body instanceof HollowHeliarch heliarch) {
                        heliarch.threat().taunt(owner.getUUID(), FamiliarRules.TAUNT_THREAT);
                        answered++;
                    }
                }
                continue;
            }
            if (!(e instanceof Mob mob)) {
                continue; // players are never taunted
            }
            boolean onUs = mob.getTarget() == owner || mob.getTarget() == gravikin || FamiliarSessions.attackedLately(owner, mob, now);
            if (FamiliarRules.taunts(mode, new FamiliarRules.Near(false, mob instanceof Enemy, onUs, d))) {
                Pull old = PULLED.get(mob.getUUID());
                LivingEntity was = mob.getTarget();
                int before = old != null ? old.before() : was != null && was != gravikin ? was.getId() : mob instanceof Enemy ? owner.getId() : -1;
                PULLED.put(mob.getUUID(), new Pull(level.dimension(), gravikin.getId(), now + FamiliarRules.TAUNT_TICKS, before));
                mob.setTarget(gravikin);
                answered++;
            }
        }
        return answered;
    }

    private static boolean ownedBy(Entity e, ServerPlayer owner) {
        return e instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID());
    }

    /** True if {@code mob} is held by a taunt now. */
    public static boolean pulled(Mob mob) {
        Pull p = PULLED.get(mob.getUUID());
        return p != null && mob.level().getGameTime() < p.until();
    }

    /** The Gravikin holding {@code mob}, or null. */
    private static Gravikin holder(Mob mob, Pull p) {
        return mob.level().getEntity(p.gravikin()) instanceof Gravikin g && g.isAlive() ? g : null;
    }

    /** Nothing else may take a taunted creature's attention while the taunt lasts. */
    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET) {
            return;
        }
        Pull p = PULLED.get(mob.getUUID());
        if (p == null || mob.level().getGameTime() >= p.until()) {
            return;
        }
        Gravikin g = holder(mob, p);
        if (g != null && event.getNewAboutToBeSetTarget() != g) {
            event.setNewAboutToBeSetTarget(g);
        }
    }

    /** Holds taunted creatures on their Gravikin, and lets them go when the taunt ends or the Gravikin is gone. */
    static void onServerTick(ServerTickEvent.Post event) {
        if (PULLED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Pull>> it = PULLED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pull> entry = it.next();
            Pull p = entry.getValue();
            ServerLevel level = server.getLevel(p.level());
            Entity e = level == null ? null : level.getEntity(entry.getKey());
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            Gravikin g = holder(mob, p);
            if (g == null || level.getGameTime() >= p.until()) {
                it.remove();
                if (g != null && mob.getTarget() == g) {
                    // back to what it was after (a target goal left alone would put it straight back on the Gravikin)
                    LivingEntity before = p.before() >= 0 && level.getEntity(p.before()) instanceof LivingEntity b && b.isAlive() ? b : null;
                    mob.setTarget(before);
                }
                continue;
            }
            if (mob.getTarget() != g) {
                mob.setTarget(g);
            }
        }
    }

    static void clear() {
        PULLED.clear();
    }
}
