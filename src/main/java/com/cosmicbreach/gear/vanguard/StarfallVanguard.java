package com.cosmicbreach.gear.vanguard;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetBehavior;
import com.cosmicbreach.gear.set.SetState;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;

/**
 * The Starfall Vanguard (GDD 5.1): T1 meteor-forged heavy plate for Power and Resilience. Falling becomes a
 * weapon.
 *
 * <ul>
 *   <li>Each piece: +2 Power, +1 Resilience; armor 2/6/5/2, toughness 1 and knockback resistance 0.05 each.</li>
 *   <li>2 pieces, Heavy Landing: fall damage halved, and a landing from 4+ blocks releases a shockwave
 *       ({@link Shockwave}).</li>
 *   <li>4 pieces, Meteoric Momentum: the fall damage avoided (the half Heavy Landing takes away, or all of it
 *       when a plunge lands) is stored as Heat, up to 20, and added to the next hit within 60 ticks; +10 poise
 *       while charging an attack.</li>
 *   <li>Set ability, Meteor Call ({@link MeteorCall}).</li>
 * </ul>
 */
public final class StarfallVanguard implements SetBehavior {
    public static final int COLOR = 0xFF9A3C;
    private static final Map<Stat, Integer> PER_PIECE = Map.of(Stat.POWER, 2, Stat.RESILIENCE, 1);

    public static final ArmorSet SET = ArmorSets.register(new ArmorSet(CosmicBreach.id("starfall_vanguard"), 1,
            List.of(new ArmorSet.Piece(ArmorItem.Type.HELMET, "starfall_vanguard_helm", 2, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.CHESTPLATE, "starfall_vanguard_chestplate", 6, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.LEGGINGS, "starfall_vanguard_greaves", 5, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.BOOTS, "starfall_vanguard_boots", 2, PER_PIECE)),
            1.0f, 0.05f, 20, COLOR, new StarfallVanguard(), new MeteorCall()));

    private StarfallVanguard() {
    }

    /** Heat stored for the next hit (4 pieces), 0 if none or it ran out. */
    public static float heat(Player player, long now) {
        return ArmorSets.state(player).meter(SET.id(), now);
    }

    // ------------------------------------------------------------------ Heavy Landing

    @Override
    public void onLanding(ServerPlayer player, int pieces, float fallDistance, float damageMultiplier, boolean cancelled) {
        if (pieces < ArmorSet.TWO_PIECES || player.isSpectator()) {
            return;
        }
        if (fallDistance >= VanguardRules.SHOCKWAVE_MIN_FALL) {
            Shockwave.release(player, fallDistance);
        }
        if (cancelled && pieces >= ArmorSet.FULL_SET) {
            // a plunge (or anything else) already took the whole fall's damage away: all of it becomes Heat
            double avoided = VanguardRules.vanillaFallDamage(fallDistance, damageMultiplier,
                    player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
            storeHeat(player, avoided);
        }
    }

    @Override
    public float onHurt(ServerPlayer player, int pieces, DamageSource source, float amount) {
        if (pieces < ArmorSet.TWO_PIECES || !source.is(DamageTypeTags.IS_FALL)) {
            return amount;
        }
        float kept = (float) VanguardRules.heavyLanding(amount);
        if (pieces >= ArmorSet.FULL_SET) {
            storeHeat(player, amount - kept);
        }
        return kept;
    }

    // ------------------------------------------------------------------ Meteoric Momentum

    private static void storeHeat(ServerPlayer player, double avoided) {
        if (avoided <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        SetState state = ArmorSets.state(player);
        double heat = VanguardRules.storeHeat(state.meter(SET.id(), now), avoided);
        ArmorSets.setState(player, state.withMeter(SET.id(), (float) heat, now + VanguardRules.HEAT_TICKS));
    }

    /** The next hit within 60 ticks carries the stored Heat (melee and the engine's hits, not the set's own blasts). */
    @Override
    public float onHitDealt(ServerPlayer player, int pieces, LivingEntity target, DamageSource source, float amount) {
        if (pieces < ArmorSet.FULL_SET || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return amount;
        }
        long now = player.level().getGameTime();
        SetState state = ArmorSets.state(player);
        float heat = state.meter(SET.id(), now);
        if (heat <= 0f) {
            return amount;
        }
        ArmorSets.setState(player, state.withoutMeter());
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    4 + (int) (heat / 4), 0.25, 0.3, 0.25, 0.0);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8 + (int) heat, 0.3, 0.4, 0.3, 0.05);
        }
        return amount + heat;
    }

    @Override
    public void tick(ServerPlayer player, int pieces, long now) {
        if (pieces < ArmorSet.FULL_SET) {
            SetState state = ArmorSets.state(player);
            if (state.meter() > 0f && state.ownedBy(SET.id())) {
                ArmorSets.setState(player, state.withoutMeter()); // took a piece off: the Heat goes
            }
        }
    }

    @Override
    public double poiseBonus(Player player, int pieces) {
        if (pieces < ArmorSet.FULL_SET) {
            return 0.0;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        return combat != null && combat.machine().phase() == CombatStateMachine.Phase.CHARGING ? VanguardRules.CHARGING_POISE : 0.0;
    }
}
