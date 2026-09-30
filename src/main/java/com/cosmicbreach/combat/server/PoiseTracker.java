package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.PoiseSource;
import com.cosmicbreach.combat.Staggerable;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.net.CombatFxPayload;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.registry.ModAttachments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Poise and stagger (GDD section 3.4), an attachment on any living entity that took Impact. Impact
 * adds up over a rolling 60 ticks; reaching the entity's poise staggers it and resets the count.
 * A {@link Staggerable} plays its own stagger; anything else gets Slowness V and deals no damage
 * until the stagger ends; a player's move, charge, parry or dash also stops and its inputs lock for
 * the stagger ({@link com.cosmicbreach.combat.core.CombatStateMachine#onStaggered}). A player's poise
 * includes the hyper armor of the move it is in. After a player's stagger, {@link #PLAYER_STAGGER_GRACE_TICKS}
 * of grace stop the next stagger from chaining. Server only.
 */
public final class PoiseTracker {
    public static final int WINDOW_TICKS = 60;
    public static final int STAGGER_TICKS = 20;
    public static final int PLAYER_STAGGER_TICKS = 10;
    /**
     * After a player's stagger ends, Impact is ignored for this long, so a pack can't chain one stagger
     * into the next (a stun-lock). Mobs get no such grace: staggering them again is the heavy weapons' reward.
     */
    public static final int PLAYER_STAGGER_GRACE_TICKS = 20;
    private static final int SLOWNESS_V = 4;

    private final ImpactWindow window = new ImpactWindow(WINDOW_TICKS);
    private long staggeredUntil = Long.MIN_VALUE;
    private long immuneUntil = Long.MIN_VALUE;

    /** Impact to stagger: its own if it says, players by the GDD formula, other mobs 10 + max health / 2. */
    public static double poiseOf(LivingEntity entity) {
        if (entity instanceof PoiseSource source) {
            return source.poise();
        }
        if (entity instanceof Player player) {
            PlayerCombat combat = PlayerCombat.existing(player);
            double hyperArmor = combat == null ? 0.0 : combat.machine().poiseBonus();
            return CombatMath.playerPoise(ProgressionStats.of(player), CombatHooks.poiseBonus(player)) + hyperArmor;
        }
        return 10.0 + entity.getMaxHealth() / 2.0;
    }

    /** Adds Impact taken now. Returns true if it staggered the entity. */
    public static boolean addImpact(LivingEntity target, double impact) {
        if (impact <= 0 || target.level().isClientSide() || !target.isAlive()) {
            return false;
        }
        if (target instanceof com.cosmicbreach.combat.ImpactSink sink) {
            sink.takeImpact(impact); // a guardian's own Break gauge
            return false;
        }
        long now = target.level().getGameTime();
        PoiseTracker tracker = target.getData(ModAttachments.POISE);
        if (now < tracker.immuneUntil) {
            return false;
        }
        if (!tracker.window.add(now, impact, poiseOf(target))) {
            return false;
        }
        if (target instanceof Staggerable staggerable) {
            staggerable.onStagger(STAGGER_TICKS);
        } else {
            int ticks = target instanceof Player ? PLAYER_STAGGER_TICKS : STAGGER_TICKS;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, SLOWNESS_V, false, false));
            tracker.staggeredUntil = now + ticks;
            if (target instanceof Player player) {
                tracker.immuneUntil = now + ticks + PLAYER_STAGGER_GRACE_TICKS;
                PlayerCombat combat = PlayerCombat.existing(player);
                if (combat != null) {
                    combat.machine().onStaggered(ticks);
                }
            }
        }
        ModNetworking.sendToTrackersAndSelf(target, new CombatFxPayload(target.getId(), CombatFxPayload.Kind.STAGGER));
        return true;
    }

    /** True while the generic stagger holds this entity: its outgoing damage is cancelled. */
    public static boolean isStaggered(LivingEntity entity) {
        PoiseTracker tracker = entity.getExistingDataOrNull(ModAttachments.POISE);
        return tracker != null && entity.level().getGameTime() < tracker.staggeredUntil;
    }

    /** Impact that still counts toward the next stagger. */
    public static double impactTaken(LivingEntity entity) {
        PoiseTracker tracker = entity.getExistingDataOrNull(ModAttachments.POISE);
        return tracker == null ? 0 : tracker.window.total(entity.level().getGameTime());
    }
}
