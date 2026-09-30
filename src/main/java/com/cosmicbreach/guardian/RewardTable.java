package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.ResourceLocation;

/**
 * A guardian's rewards (the tables in each guardian's design doc), per participant: a first kill gives one set,
 * every later kill another. Each line is an item with a first-kill count and a repeat chance and count range.
 * Pure: {@link #roll} turns the table into one player's {@link Reward} with the random numbers it is given.
 *
 * <p>XP goes through {@code progression.AttunementXp}, stat points through {@code AttunementXp.awardStatPoints},
 * and the first-kill advancement through the guardian's own code.
 */
public record RewardTable(List<Line> lines, int firstXp, int repeatXp, int firstStatPoints) {
    /**
     * One item: {@code firstCount} on a first kill (0 for none); on a repeat kill, with {@code repeatChance}, a count
     * from {@code repeatMin} to {@code repeatMax}.
     */
    public record Line(ResourceLocation item, int firstCount, double repeatChance, int repeatMin, int repeatMax) {
        public static Line firstThenChance(ResourceLocation item, int firstCount, double repeatChance) {
            return new Line(item, firstCount, repeatChance, 1, 1);
        }

        public static Line repeatOnly(ResourceLocation item, double chance, int min, int max) {
            return new Line(item, 0, chance, min, max);
        }
    }

    /** An item and how many. */
    public record Drop(ResourceLocation item, int count) {
    }

    /** What one participant gets for one kill. */
    public record Reward(List<Drop> drops, int xp, int statPoints, boolean firstKill) {
    }

    /** One participant's reward: {@code random} gives numbers in [0, 1). */
    public Reward roll(boolean firstKill, DoubleSupplier random) {
        List<Drop> drops = new ArrayList<>();
        for (Line line : lines) {
            if (firstKill) {
                if (line.firstCount() > 0) {
                    drops.add(new Drop(line.item(), line.firstCount()));
                }
                continue;
            }
            if (line.repeatChance() <= 0 || random.getAsDouble() >= line.repeatChance()) {
                continue;
            }
            int span = Math.max(0, line.repeatMax() - line.repeatMin());
            int count = line.repeatMin() + (span == 0 ? 0 : (int) Math.floor(random.getAsDouble() * (span + 1)));
            if (count > 0) {
                drops.add(new Drop(line.item(), Math.min(line.repeatMax(), count)));
            }
        }
        return new Reward(List.copyOf(drops), firstKill ? firstXp : repeatXp, firstKill ? firstStatPoints : 0, firstKill);
    }
}
