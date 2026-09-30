package com.cosmicbreach.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The progression system's server hooks: attributes kept in line with each player's state and gear
 * every tick, Resilience's damage reduction after armor, who damaged what, and kill rewards.
 */
public final class ProgressionEvents {
    private ProgressionEvents() {
    }

    public static void register(IEventBus game) {
        game.addListener(PlayerTickEvent.Post.class, ProgressionEvents::onPlayerTick);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, ProgressionEvents::onLoggedIn);
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, ProgressionEvents::onRespawn);
        game.addListener(LivingDamageEvent.Pre.class, ProgressionEvents::onDamagePre);
        game.addListener(LivingDamageEvent.Post.class, ProgressionEvents::onDamagePost);
        game.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, ProgressionEvents::onDeath);
    }

    /** Gear can change the effective stats at any moment, so the derived modifiers follow every tick. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Attunements.apply(player);
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Attunements.refresh(player);
        }
    }

    /** Death costs nothing: the state was copied, and the bonus health comes back full. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Attunements.refresh(player);
            if (!event.isEndConquered()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /**
     * Resilience takes 0.5% a point off the damage left after armor, Resistance and protection (this
     * event comes after all three, before absorption). The void and /kill stay lethal.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            return;
        }
        float damage = event.getNewDamage();
        float reduced = DerivedStats.reduce(damage, ProgressionStats.of(player));
        if (reduced != damage) {
            event.setNewDamage(reduced);
        }
    }

    /** Remembers every player who damaged a creature, for its kill's participants. */
    private static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            victim.getData(ProgressionRegistry.KILL_CREDIT).recordDamage(attacker.getUUID());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) {
            return;
        }
        int xp = KillRewards.xpFor(victim.getType());
        if (xp > 0) {
            AttunementXp.awardKill(victim, xp);
        }
    }
}
