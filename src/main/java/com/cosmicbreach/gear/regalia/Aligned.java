package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.GearSets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Aligned (GDD 5.1): an enemy standing in a Hymn of Alignment takes 10% more damage from abilities: the weapons'
 * abilities and their second presses (the engine's hits of an ability move, through {@link HitModifiers}), their
 * echoes, and set abilities that strike (Meteor Call's meteor). Its icon shows in the effect list, and clients draw
 * its glyph over every enemy inside a ring. Renewed every tick in the ring, so it ends just after leaving it.
 */
public class Aligned extends MobEffect {
    public static final int COLOR = 0xFFD98A;

    public Aligned() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** The engine's hits (and the ghosts' that ask the same question): an ability move on an Aligned target. */
    public static final HitModifiers.Modifier MODIFIER = new HitModifiers.Modifier() {
        @Override
        public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move, @Nullable LivingEntity target) {
            return move != null && target != null && move.def().kind() == MoveKind.ABILITY && target.hasEffect(GearSets.ALIGNED)
                    ? RegaliaRules.ALIGNED_ABILITY_DAMAGE : 1.0;
        }
    };

    /** Set abilities' own blasts: the meteor. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().is(GearRegistry.METEOR_DAMAGE) && event.getEntity().hasEffect(GearSets.ALIGNED)) {
            event.setAmount((float) (event.getAmount() * RegaliaRules.ALIGNED_ABILITY_DAMAGE));
        }
    }
}
