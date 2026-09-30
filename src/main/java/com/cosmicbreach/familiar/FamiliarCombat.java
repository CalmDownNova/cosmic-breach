package com.cosmicbreach.familiar;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the familiars meet the combat engine. Kindled: an Emberwisp out when its owner dodges perfectly or parries
 * gives them {@value FamiliarRules#KINDLED_TICKS} ticks of +10% damage, a Buffs term of every engine hit they deal.
 * Refract: an ability hit on a target with three stacks deals +30%, and consumes them once it lands
 * ({@link #onStrike}).
 */
final class FamiliarCombat implements CombatHooks.Hook, HitModifiers.Modifier {
    @Override
    public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
        if (!(event instanceof CombatEvent.PerfectDodge) && !(event instanceof CombatEvent.ParrySucceeded)) {
            return;
        }
        FamiliarEntity wisp = FamiliarSessions.active(player);
        if (wisp == null || wisp.isRemoved() || wisp.kind() != FamiliarKind.EMBERWISP) {
            return;
        }
        player.addEffect(new MobEffectInstance(FamiliarRegistry.KINDLED, FamiliarRules.KINDLED_TICKS, 0, false, true, true));
        player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), FamiliarRegistry.KINDLE.get(), SoundSource.PLAYERS,
                0.9f, 1.0f);
        FamiliarNet.fx(player, FamiliarFxPayload.KINDLED, player.getId(), wisp.getId(), player.position(), 0);
        wisp.act(FamiliarEntity.ACT_FLARE);
    }

    @Override
    public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
        return FamiliarRules.kindledMultiplier(attacker != null && attacker.hasEffect(FamiliarRegistry.KINDLED));
    }

    @Override
    public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move, @Nullable LivingEntity target) {
        double m = damageMultiplier(attacker, move);
        if (move != null && move.def().kind() == MoveKind.ABILITY && target != null) {
            m *= RefractStacks.multiplier(Refract.stacks(target));
        }
        return m;
    }

    /** After an engine hit landed: an ability consumes three Refract stacks. */
    static void onStrike(ServerPlayer player, MoveInstance move, LivingEntity target, Vec3 point) {
        if (move.def().kind() != MoveKind.ABILITY || !RefractStacks.consumes(Refract.stacks(target))) {
            return;
        }
        Refract.consume(target);
        target.level().playSound(null, point.x, point.y, point.z, FamiliarRegistry.REFRACT_BREAK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        FamiliarNet.fx(target, FamiliarFxPayload.REFRACT_BREAK, target.getId(), player.getId(), point, 0);
        FamiliarNet.fx(target, FamiliarFxPayload.REFRACT, target.getId(), -1, target.position(), 0);
    }
}
