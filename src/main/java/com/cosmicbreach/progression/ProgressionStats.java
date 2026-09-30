package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.registry.ModAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

/**
 * A player's effective stats: its four attribute values (allocated points plus gear, capped at 40),
 * as whole points. Works on both sides, since the attributes are synced to every client.
 */
public final class ProgressionStats {
    private ProgressionStats() {
    }

    public static StatBlock of(Player player) {
        return new StatBlock(points(player, Stat.POWER), points(player, Stat.AGILITY), points(player, Stat.ARCANE),
                points(player, Stat.RESILIENCE));
    }

    public static int points(Player player, Stat stat) {
        AttributeInstance instance = player.getAttribute(ModAttributes.of(stat));
        return instance == null ? 0 : wholePoints(instance.getValue());
    }

    /** An attribute value as whole points: rounded down (so gear adds whole points), 0 to 40. */
    public static int wholePoints(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return (int) Math.max(0, Math.min(ModAttributes.MAX_POINTS, Math.floor(value + 1e-6)));
    }
}
