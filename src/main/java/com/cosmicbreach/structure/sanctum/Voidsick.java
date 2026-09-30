package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.combat.server.CombatHooks;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

/**
 * Voidsick (GDD 7.3): the Breach threw you back onto the arena and your weapon's ability won't answer for
 * {@value RescueRule#VOIDSICK_TICKS} ticks. Like Silence, it puts the ability's cost out of reach through
 * {@link #HOOK} on both sides of the combat machine (the effect is synced to its player).
 */
public class Voidsick extends MobEffect {
    public static final int COLOR = 0x5B2C8F;

    public static final CombatHooks.Hook HOOK = new CombatHooks.Hook() {
        @Override
        public double abilityCostScale(Player player) {
            return player.hasEffect(SanctumRegistry.VOIDSICK) ? 1.0e6 : 1.0;
        }
    };

    public Voidsick() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
