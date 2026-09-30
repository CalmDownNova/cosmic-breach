package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.combat.server.CombatHooks;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

/**
 * Silence (Unsung design v1): Harmonize caught you outside a circle of silence, and your weapon's ability won't answer
 * for {@value UnsungMoves#SILENCE_TICKS} ticks. The effect is synced to its player, so both sides of the combat
 * machine refuse the ability alike (its cost goes out of reach through {@link #HOOK}); the 30 Resonance it drains are
 * taken on the server.
 */
public class Silenced extends MobEffect {
    public static final int COLOR = 0x9A8CC8;

    /** Abilities cost far more than any Resonance while Silenced. */
    public static final CombatHooks.Hook HOOK = new CombatHooks.Hook() {
        @Override
        public double abilityCostScale(Player player) {
            return player.hasEffect(UnsungRegistry.SILENCED) ? 1.0e6 : 1.0;
        }
    };

    public Silenced() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
