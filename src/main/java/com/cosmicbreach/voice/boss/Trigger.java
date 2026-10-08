package com.cosmicbreach.voice.boss;

import java.util.List;
import java.util.Locale;

/**
 * What makes a boss speak: the voice script's fixed trigger vocabulary (Aetheria 1.1 Voice Script v1). {@code kind} is one
 * of {@link #KINDS}; {@code arg} is what follows the colon, or empty: {@code hp_threshold:<percent>} (0 to 100),
 * {@code weapon:<category>} ({@link WeaponCategory}), {@code armor:<category>} ({@link ArmorCategory}) and
 * {@code gear:<over|under|even>} ({@link GearCheck.Verdict}). Pure.
 */
public record Trigger(String kind, String arg) {
    public static final String FIGHT_START = "fight_start";
    public static final String HP_THRESHOLD = "hp_threshold";
    public static final String PLAYER_DEATH = "player_death";
    public static final String PLAYER_LOW_HEALTH = "player_low_health";
    public static final String PLAYER_FELL = "player_fell";
    public static final String PLAYER_LEFT = "player_left";
    public static final String PLAYER_RETURNED = "player_returned";
    public static final String WEAPON = "weapon";
    public static final String ARMOR = "armor";
    public static final String GEAR = "gear";
    public static final String ATTACK_GAP = "attack_gap";
    public static final String BOSS_KILL = "boss_kill";
    public static final String TAUNT = "taunt";

    public static final List<String> KINDS = List.of(FIGHT_START, HP_THRESHOLD, PLAYER_DEATH, PLAYER_LOW_HEALTH, PLAYER_FELL,
            PLAYER_LEFT, PLAYER_RETURNED, WEAPON, ARMOR, GEAR, ATTACK_GAP, BOSS_KILL, TAUNT);

    /** Reads a trigger as the script writes it; a word outside the vocabulary is an error, so a typo never ships silent. */
    public static Trigger parse(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        int colon = s.indexOf(':');
        String kind = colon < 0 ? s : s.substring(0, colon);
        String arg = colon < 0 ? "" : s.substring(colon + 1);
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("unknown trigger \"" + text + "\"");
        }
        switch (kind) {
            case HP_THRESHOLD -> {
                int percent;
                try {
                    percent = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("hp_threshold needs a percent: \"" + text + "\"");
                }
                if (percent < 0 || percent > 100) {
                    throw new IllegalArgumentException("hp_threshold is 0 to 100: \"" + text + "\"");
                }
            }
            case WEAPON -> {
                if (WeaponCategory.byId(arg) == null) {
                    throw new IllegalArgumentException("unknown weapon category: \"" + text + "\"");
                }
            }
            case ARMOR -> {
                if (ArmorCategory.byId(arg) == null) {
                    throw new IllegalArgumentException("unknown armor category: \"" + text + "\"");
                }
            }
            case GEAR -> {
                if (GearCheck.Verdict.byId(arg) == null) {
                    throw new IllegalArgumentException("unknown gear verdict: \"" + text + "\"");
                }
            }
            default -> {
                if (!arg.isEmpty()) {
                    throw new IllegalArgumentException(kind + " takes no argument: \"" + text + "\"");
                }
            }
        }
        return new Trigger(kind, arg);
    }

    /** As the script writes it: {@code fight_start}, {@code hp_threshold:50}. */
    public String key() {
        return arg.isEmpty() ? kind : kind + ":" + arg;
    }

    /** The percent of an hp_threshold trigger, or -1. */
    public int percent() {
        return kind.equals(HP_THRESHOLD) ? Integer.parseInt(arg) : -1;
    }

    public boolean is(String kind) {
        return this.kind.equals(kind);
    }

    @Override
    public String toString() {
        return key();
    }
}
