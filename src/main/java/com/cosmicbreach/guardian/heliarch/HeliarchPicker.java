package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.guardian.AttackPicker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * How the Regent (phase 1) chooses its next attack (GDD 7.3, with G9c's changes), on the guardians' {@link AttackPicker}:
 * by weight, each with its cooldown from its start, never the same one twice in a row. Sunderfall (5 s) always may;
 * Solar Lance (8 s) wants its target at mid range, or any range when fewer than two fight; Corona Sweep (12 s) comes
 * when anyone stands in its band. Halo Shed comes every 30 s and goes first when due; Corona Flare (10 s) answers a
 * player who has hugged the core for 3 s, next after the shed. When Sunderfall is its only choice (a group hugging the
 * throne before the Flare is due) it may repeat, so the Regent never stands idle for want of variety. Pure.
 */
public final class HeliarchPicker {
    public enum Attack { SUNDERFALL, CORONA_SWEEP, SOLAR_LANCE, HALO_SHED, CORONA_FLARE }

    /**
     * What the Heliarch sees when it chooses: players in the fight, players in the sweep's band, its target's distance,
     * and whether anyone has hugged the core long enough to call the Flare.
     */
    public record Context(int players, int inBand, double targetDistance, boolean hugged) {
    }

    private final AttackPicker<Attack> picker = new AttackPicker<>();

    /** The shed's first turn: 30 s after phase 1 starts. */
    public void start(long now) {
        picker.clear();
        picker.holdUntil(Attack.HALO_SHED, now + HeliarchMoves.SHED_EVERY);
    }

    /** True if the sweep's condition holds: anyone in the band. */
    public static boolean sweepWanted(int players, int inBand) {
        return inBand >= 1;
    }

    /** True if the lance's condition holds: its target at mid range, or any range when fewer than two fight. */
    public static boolean lanceWanted(int players, double targetDistance) {
        return players < 2 || targetDistance >= HeliarchMoves.LANCE_MIN_RANGE;
    }

    /** The attacks it could make now, their weights and cooldowns (conditions applied). */
    public static List<AttackPicker.Option<Attack>> options(Context c) {
        List<AttackPicker.Option<Attack>> out = new ArrayList<>();
        out.add(AttackPicker.Option.<Attack>weighted(Attack.HALO_SHED, 1.0, HeliarchMoves.SHED_EVERY).asPriority());
        if (c.hugged()) {
            out.add(AttackPicker.Option.<Attack>weighted(Attack.CORONA_FLARE, 1.0, HeliarchMoves.FLARE_COOLDOWN).asPriority());
        }
        boolean lance = lanceWanted(c.players(), c.targetDistance());
        boolean sweep = sweepWanted(c.players(), c.inBand());
        AttackPicker.Option<Attack> sunder = AttackPicker.Option.weighted(Attack.SUNDERFALL, 3.0, HeliarchMoves.SUNDER_COOLDOWN);
        out.add(lance || sweep || c.hugged() ? sunder : sunder.asRepeatable());
        if (lance) {
            out.add(AttackPicker.Option.weighted(Attack.SOLAR_LANCE, 2.0, HeliarchMoves.LANCE_COOLDOWN));
        }
        if (sweep) {
            out.add(AttackPicker.Option.weighted(Attack.CORONA_SWEEP, 4.0, HeliarchMoves.SWEEP_COOLDOWN));
        }
        return out;
    }

    /** The next attack at {@code now}, or null if none is ready; its cooldown starts. */
    public Attack next(Context c, long now, DoubleSupplier random) {
        List<AttackPicker.Option<Attack>> options = options(c);
        Attack a = picker.pick(options, now, random);
        if (a != null) {
            for (AttackPicker.Option<Attack> o : options) {
                if (o.attack() == a) {
                    picker.used(a, o.cooldownTicks(), now);
                }
            }
        }
        return a;
    }

    /** Forces {@code a} next (debug and tests): its cooldown starts as if chosen. */
    public void force(Attack a, long now) {
        int cd = switch (a) {
            case SUNDERFALL -> HeliarchMoves.SUNDER_COOLDOWN;
            case CORONA_SWEEP -> HeliarchMoves.SWEEP_COOLDOWN;
            case SOLAR_LANCE -> HeliarchMoves.LANCE_COOLDOWN;
            case HALO_SHED -> HeliarchMoves.SHED_EVERY;
            case CORONA_FLARE -> HeliarchMoves.FLARE_COOLDOWN;
        };
        picker.used(a, cd, now);
    }

    public boolean ready(Attack a, long now) {
        return picker.ready(a, now);
    }

    public long cooldownLeft(Attack a, long now) {
        return picker.cooldownLeft(a, now);
    }

    public Attack last() {
        return picker.last();
    }

    public AttackPicker<Attack> raw() {
        return picker;
    }
}
