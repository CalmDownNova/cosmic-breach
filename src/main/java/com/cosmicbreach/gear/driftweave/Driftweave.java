package com.cosmicbreach.gear.driftweave;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetBehavior;
import com.cosmicbreach.gear.set.SetState;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import org.jetbrains.annotations.Nullable;

/**
 * The Driftweave (GDD 5.1): T2 light plates on layered cloth, cut for the Drift, for Agility and Power. More
 * dashes, and a personal slice of low gravity.
 *
 * <ul>
 *   <li>Each piece: +2 Agility, +1 Power; armor 3/7/5/3, toughness 1 each.</li>
 *   <li>2 pieces: +1 dash charge, dashes 20% longer (the engine's {@link CombatHooks}).</li>
 *   <li>4 pieces, Slipstream: a perfect dodge leaves an Afterimage that repeats the next 3 attacks
 *       ({@link Slipstream}).</li>
 *   <li>Set ability, Drift ({@link Drift}).</li>
 * </ul>
 *
 * The cloth shimmers with the dash charges ready: the set's meter holds the server's count, synced to everyone
 * who sees the wearer.
 */
public final class Driftweave implements SetBehavior {
    public static final int COLOR = 0x9FE6FF;
    private static final Map<Stat, Integer> PER_PIECE = Map.of(Stat.AGILITY, 2, Stat.POWER, 1);
    /** The shimmer's meter never runs out by itself; the set rewrites it whenever the count changes. */
    private static final long FOREVER = 1L << 50;

    public static final ArmorSet SET = ArmorSets.register(new ArmorSet(CosmicBreach.id("driftweave"), 2,
            List.of(new ArmorSet.Piece(ArmorItem.Type.HELMET, "driftweave_hood", 3, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.CHESTPLATE, "driftweave_coat", 7, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.LEGGINGS, "driftweave_leggings", 5, PER_PIECE),
                    new ArmorSet.Piece(ArmorItem.Type.BOOTS, "driftweave_boots", 3, PER_PIECE)),
            1.0f, 0.0f, 33, COLOR, new Driftweave(), new Drift()));

    private Driftweave() {
    }

    /** What the Driftweave adds to the engine, on both sides. */
    public static final CombatHooks.Hook HOOK = new CombatHooks.Hook() {
        @Override
        public int dashChargeBonus(Player player) {
            return ArmorSets.pieces(player, SET) >= ArmorSet.TWO_PIECES ? DriftweaveRules.DASH_CHARGES : 0;
        }

        @Override
        public double dashDistanceScale(Player player) {
            return ArmorSets.pieces(player, SET) >= ArmorSet.TWO_PIECES ? DriftweaveRules.DASH_DISTANCE : 1.0;
        }

        @Override
        public boolean freeAirDashes(Player player) {
            return Drift.active(player);
        }
    };

    /** Drift's aerial attacks +20%: any hit of a plunge, or of an attack made off the ground. */
    public static final HitModifiers.Modifier AERIAL = new HitModifiers.Modifier() {
        @Override
        public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
            if (attacker == null || move == null || !Drift.active(attacker)) {
                return 1.0;
            }
            return DriftweaveRules.aerial(move.def().kind() == MoveKind.PLUNGE, attacker.onGround()) ? DriftweaveRules.AERIAL_DAMAGE : 1.0;
        }
    };

    @Override
    public void tick(ServerPlayer player, int pieces, long now) {
        // the shimmer: the dash charges ready, rewritten only when they change
        PlayerCombat combat = PlayerCombat.existing(player);
        int charges = combat == null ? 0 : combat.machine().dashCharges();
        SetState state = ArmorSets.state(player);
        if (state.meter(SET.id(), now) != charges || !state.ownedBy(SET.id())) {
            ArmorSets.setState(player, state.withMeter(SET.id(), charges, now + FOREVER));
        }
    }

    @Override
    public void onCombatEvent(ServerPlayer player, int pieces, PlayerCombat combat, CombatEvent event) {
        if (pieces >= ArmorSet.FULL_SET) {
            Slipstream.onCombatEvent(player, combat, event);
        }
    }
}
