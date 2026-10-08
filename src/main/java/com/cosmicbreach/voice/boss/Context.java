package com.cosmicbreach.voice.boss;

/**
 * What was true when a trigger fired, for its lines' conditions ({@link Condition}): the fight's (players at the wake, a
 * returning party, the masks still whole) and the moment's (the fighter's health, how long the arena has stood empty or
 * the fighter was away, the last one standing, the item that hit, the set worn, the boss's event, a fighter riding, the
 * target out of reach). Pure.
 */
public record Context(int players, boolean returning, int masks, double health, boolean allGone, int awaySeconds,
        boolean lastAlive, String item, String armorSet, String event, boolean mounted, boolean targetFar) {

    /** The fight's facts, nothing of the moment yet. */
    public static Context of(int players, boolean returning, int masks) {
        return new Context(players, returning, masks, 1.0, false, 0, false, "", "", "", false, false);
    }

    /**
     * A moment that meets every condition of {@code line}, for checks and the debug command that fires a line's own moment:
     * a lone fighter unless the line wants a group, three masks unless it wants fewer, and whatever else its conditions name.
     */
    public static Context meeting(VoiceLine line) {
        int players = 1;
        boolean returning = false;
        int masks = 3;
        for (Condition k : line.conditions()) {
            switch (k.kind()) {
                case "players" -> players = k.arg().equals("solo") ? 1 : 3;
                case "returning" -> returning = true;
                case "masks" -> masks = Integer.parseInt(k.arg());
                default -> {
                }
            }
        }
        Context c = of(players, returning, masks);
        for (Condition k : line.conditions()) {
            switch (k.kind()) {
                case "health" -> c = c.withHealth(Integer.parseInt(k.arg()) / 100.0);
                case "away" -> c = c.withAway(Integer.parseInt(k.arg()), c.allGone());
                case "all_gone" -> c = c.withAway(c.awaySeconds(), true);
                case "last_alive" -> c = c.withLastAlive(true);
                case "item" -> c = c.withItem("cosmicbreach:" + k.arg());
                case "set" -> c = c.withArmorSet(k.arg());
                case "on" -> c = c.withEvent(k.arg());
                case "while" -> c = c.withMounted(true);
                case "target_far" -> c = c.withTargetFar(true);
                default -> {
                }
            }
        }
        return c;
    }

    public Context withHealth(double share) {
        return new Context(players, returning, masks, share, allGone, awaySeconds, lastAlive, item, armorSet, event, mounted, targetFar);
    }

    public Context withAway(int seconds, boolean gone) {
        return new Context(players, returning, masks, health, gone, seconds, lastAlive, item, armorSet, event, mounted, targetFar);
    }

    public Context withLastAlive(boolean last) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, last, item, armorSet, event, mounted, targetFar);
    }

    public Context withItem(String id) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, lastAlive, id, armorSet, event, mounted, targetFar);
    }

    public Context withArmorSet(String set) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, lastAlive, item, set, event, mounted, targetFar);
    }

    public Context withEvent(String name) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, lastAlive, item, armorSet, name, mounted, targetFar);
    }

    public Context withMounted(boolean riding) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, lastAlive, item, armorSet, event, riding, targetFar);
    }

    public Context withTargetFar(boolean far) {
        return new Context(players, returning, masks, health, allGone, awaySeconds, lastAlive, item, armorSet, event, mounted, far);
    }
}
