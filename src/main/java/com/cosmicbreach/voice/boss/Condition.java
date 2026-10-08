package com.cosmicbreach.voice.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One token of a line's Condition cell (the voice script's list): when the line suits the moment its trigger fired.
 * {@code also:<category>} widens a weapon line's trigger instead ({@link VoiceLine#answers}). Tokens outside the list are
 * errors, so a typo in the script never ships silent. Pure.
 */
public record Condition(String kind, String arg) {
    /** The boss events a taunt may wait for ({@code on:<event>}). */
    public static final List<String> EVENTS = List.of("reform", "break", "nova_return", "nova_broken", "nova_detonated",
            "corona_flare", "soft_enrage", "harmonize_all_safe");
    /** The mod's armor sets by the script's names ({@code set:<name>}). */
    public static final List<String> SETS = List.of("vanguard", "driftweave", "regalia");

    /** A whole cell: {@code none}, or tokens separated by commas. */
    public static List<Condition> parseAll(String cell) {
        String s = cell.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) {
            return List.of();
        }
        List<Condition> out = new ArrayList<>();
        for (String token : s.split(",")) {
            out.add(parse(token));
        }
        return List.copyOf(out);
    }

    public static Condition parse(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "players:solo" -> {
                return new Condition("players", "solo");
            }
            case "players:group" -> {
                return new Condition("players", "group");
            }
            case "first", "returning", "all_gone", "last_alive", "target_far" -> {
                return new Condition(t, "");
            }
            case "while:mounted" -> {
                return new Condition("while", "mounted");
            }
            default -> {
            }
        }
        if (t.startsWith("masks:")) {
            return new Condition("masks", String.valueOf(number(t, t.substring(6), 1, 3)));
        }
        if (t.startsWith("health<=") && t.endsWith("%")) {
            return new Condition("health", String.valueOf(number(t, t.substring(8, t.length() - 1), 1, 100)));
        }
        if (t.startsWith("away>=") && t.endsWith("s")) {
            return new Condition("away", String.valueOf(number(t, t.substring(6, t.length() - 1), 0, 3600)));
        }
        if (t.startsWith("also:") && WeaponCategory.byId(t.substring(5)) != null) {
            return new Condition("also", t.substring(5));
        }
        if (t.startsWith("item:") && t.substring(5).matches("[a-z0-9_./]+")) {
            return new Condition("item", t.substring(5));
        }
        if (t.startsWith("set:") && SETS.contains(t.substring(4))) {
            return new Condition("set", t.substring(4));
        }
        if (t.startsWith("on:") && EVENTS.contains(t.substring(3))) {
            return new Condition("on", t.substring(3));
        }
        throw new IllegalArgumentException("unknown condition \"" + token.trim() + "\"");
    }

    private static int number(String token, String digits, int min, int max) {
        try {
            int n = Integer.parseInt(digits);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("bad number in condition \"" + token + "\" (" + min + " to " + max + ")");
    }

    /** True if the moment {@code c} meets this condition. */
    public boolean test(Context c) {
        return switch (kind) {
            case "players" -> arg.equals("solo") ? c.players() == 1 : c.players() >= 2;
            case "first" -> !c.returning();
            case "returning" -> c.returning();
            case "masks" -> c.masks() == Integer.parseInt(arg);
            case "health" -> c.health() <= Integer.parseInt(arg) / 100.0 + 1e-9;
            case "all_gone" -> c.allGone();
            case "away" -> c.awaySeconds() >= Integer.parseInt(arg);
            case "last_alive" -> c.lastAlive();
            case "also" -> true;
            case "item" -> c.item().equals("cosmicbreach:" + arg);
            case "set" -> c.armorSet().equals(arg);
            case "on" -> c.event().equals(arg);
            case "while" -> c.mounted();
            case "target_far" -> c.targetFar();
            default -> false;
        };
    }

    @Override
    public String toString() {
        return switch (kind) {
            case "first", "returning", "all_gone", "last_alive", "target_far" -> kind;
            case "health" -> "health<=" + arg + "%";
            case "away" -> "away>=" + arg + "s";
            default -> kind + ":" + arg;
        };
    }
}
