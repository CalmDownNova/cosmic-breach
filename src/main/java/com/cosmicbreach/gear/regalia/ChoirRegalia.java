package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetBehavior;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;

/**
 * The Choir Regalia (GDD 5.1): T3 ceremonial armor of the Choir, for Arcane and Resilience. Abilities echo, and
 * the whole party casts faster inside your hymn.
 *
 * <ul>
 *   <li>Each piece: +2 Arcane, +1 Resilience; armor 3/8/6/3, toughness 2.5 each.</li>
 *   <li>2 pieces: out of combat Resonance drifts to 50% of max instead of 30%; abilities cost 10% less.</li>
 *   <li>4 pieces, Harmonics: every third ability cast within 10 s echoes 10 ticks later at 60% power, free
 *       ({@link Harmonics}).</li>
 *   <li>Set ability, the Hymn of Alignment ({@link HymnOfAlignment}).</li>
 * </ul>
 *
 * The set's meter holds the casts Harmonics has counted (2: the next one echoes), so the halo spins faster when an
 * echo is primed.
 */
public final class ChoirRegalia implements SetBehavior {
    public static final int COLOR = 0xFFD98A;
    private static final Map<Stat, Integer> PER_PIECE = Map.of(Stat.ARCANE, 2, Stat.RESILIENCE, 1);

    public static final ArmorSet SET = ArmorSets.register(new ArmorSet(CosmicBreach.id("choir_regalia"), 3,
            List.of(new ArmorSet.Piece(ArmorItem.Type.HELMET, "choir_regalia_circlet", 3, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.CHESTPLATE, "choir_regalia_vestment", 8, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.LEGGINGS, "choir_regalia_tassets", 6, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.BOOTS, "choir_regalia_sabatons", 3, PER_PIECE)),
            2.5f, 0.0f, 37, COLOR, new ChoirRegalia(), new HymnOfAlignment()));

    private ChoirRegalia() {
    }

    /** What the Regalia and the Hymn add to the engine, on both sides (the Hymn's for every player inside a ring). */
    public static final CombatHooks.Hook HOOK = new CombatHooks.Hook() {
        @Override
        public double abilityCostScale(Player player) {
            return ArmorSets.pieces(player, SET) >= ArmorSet.TWO_PIECES ? RegaliaRules.ABILITY_COST : 1.0;
        }

        @Override
        public double resonanceDriftTarget(Player player) {
            return ArmorSets.pieces(player, SET) >= ArmorSet.TWO_PIECES ? RegaliaRules.DRIFT_TARGET : Double.NaN;
        }

        @Override
        public double hasteBonus(Player player) {
            return HymnRings.inside(player) ? RegaliaRules.HYMN_HASTE : 0.0;
        }

        @Override
        public double resonanceGain(Player player) {
            return HymnRings.inside(player) ? RegaliaRules.HYMN_RESONANCE_GAIN : 1.0;
        }
    };

    @Override
    public void tick(ServerPlayer player, int pieces, long now) {
        Harmonics.tick(player, pieces, now);
    }

    @Override
    public void onCombatEvent(ServerPlayer player, int pieces, PlayerCombat combat, CombatEvent event) {
        if (pieces >= ArmorSet.FULL_SET && event instanceof CombatEvent.MoveStarted started) {
            Harmonics.onMoveStarted(player, combat, started.move());
        }
    }
}
