package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.guardian.AttackPicker;
import java.util.ArrayList;
import java.util.List;

/**
 * How the Leviathan chooses its attacks from the orbit (Thalassine Leviathan design v1, "Choosing attacks"): weighted,
 * never the same twice in a row. Breach Dive every 12 s (10 in phase 2), when its target is ahead along the orbit (so the
 * dive never cuts across the core); the Tail Flick first whenever a player stands within
 * {@value LeviathanMoves#FLICK_TRIGGER} blocks of the tail (6 s cooldown); the Song of Pulling every 16 s, only with a
 * target in front of the head; Scale Shed joins in phase 2. The dive and the flick are timed or answer a condition, so
 * they go first when ready; the song and the shed share the rest by weight. Pure: {@link #options} feeds an
 * {@link AttackPicker}.
 */
public final class LeviathanTactics {
    public enum Attack { NONE, DIVE, SONG, FLICK, SHED }

    public static final double SONG_WEIGHT = 1.0;
    public static final double SHED_WEIGHT = 1.0;

    /**
     * What it sees when choosing: its phase, how far ahead along the orbit its target stands (radians, NaN for no target),
     * whether the target is in front of its head (inside the song's cone), and whether a player stands by its tail.
     */
    public record Situation(boolean phaseTwo, double targetAhead, boolean targetInFront, boolean tailNear) {
    }

    private LeviathanTactics() {
    }

    /** The attacks it could start now, for the picker. */
    public static List<AttackPicker.Option<Attack>> options(Situation s) {
        List<AttackPicker.Option<Attack>> out = new ArrayList<>();
        if (s.tailNear()) {
            out.add(AttackPicker.Option.weighted(Attack.FLICK, 0, LeviathanMoves.FLICK_COOLDOWN).asPriority());
        }
        if (!Double.isNaN(s.targetAhead()) && LeviathanPaths.diveWindow(s.targetAhead())) {
            out.add(AttackPicker.Option.weighted(Attack.DIVE, 0, LeviathanMoves.diveCooldown(s.phaseTwo())).asPriority());
        }
        if (s.targetInFront()) {
            out.add(AttackPicker.Option.weighted(Attack.SONG, SONG_WEIGHT, LeviathanMoves.SONG_COOLDOWN));
        }
        if (s.phaseTwo()) {
            out.add(AttackPicker.Option.weighted(Attack.SHED, SHED_WEIGHT, LeviathanMoves.SHED_COOLDOWN));
        }
        return out;
    }

    /** The cooldown an attack starts when chosen. */
    public static int cooldown(Attack attack, boolean phaseTwo) {
        return switch (attack) {
            case DIVE -> LeviathanMoves.diveCooldown(phaseTwo);
            case SONG -> LeviathanMoves.SONG_COOLDOWN;
            case FLICK -> LeviathanMoves.FLICK_COOLDOWN;
            case SHED -> LeviathanMoves.SHED_COOLDOWN;
            case NONE -> 0;
        };
    }
}
