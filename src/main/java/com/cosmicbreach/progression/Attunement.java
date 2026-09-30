package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * One player's Attunement (GDD sections 3.2 and 3.3), separate from vanilla experience: the level
 * (1 to 50), the XP gathered toward the next level, stat points awarded from outside the levels
 * (guardians, the Heliarch), the points spent per attribute, and whether the free Reverie Draught
 * was handed over. Immutable: every change makes a new value, so setting it on the player syncs it.
 *
 * <p>Points are never stored, they follow from the rest: one per level above 1, plus the bonus
 * points; whatever isn't spent is there to spend. The constructor keeps every value valid (a level
 * in range, XP below the next level's need, at most 30 points per attribute, never more spent than
 * earned), so a damaged save or a lowered debug level can't leave a player in an impossible state.
 */
public record Attunement(int level, int xp, int bonusPoints, Allocation spent, boolean freeDraughtGiven) {
    public static final int MAX_LEVEL = 50;
    /** The most points one attribute can take from the player; gear can add up to 40 in all. */
    public static final int STAT_CAP = 30;
    /** The first Reverie Draught is free at this level. */
    public static final int FREE_DRAUGHT_LEVEL = 10;

    public static final Attunement START = new Attunement(1, 0, 0, Allocation.ZERO, false);

    public static final Codec<Attunement> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("level", 1).forGetter(Attunement::level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(Attunement::xp),
            Codec.INT.optionalFieldOf("bonus_points", 0).forGetter(Attunement::bonusPoints),
            Allocation.CODEC.optionalFieldOf("spent", Allocation.ZERO).forGetter(Attunement::spent),
            Codec.BOOL.optionalFieldOf("free_draught_given", false).forGetter(Attunement::freeDraughtGiven)
    ).apply(i, Attunement::new));

    public static final StreamCodec<ByteBuf, Attunement> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Attunement::level,
            ByteBufCodecs.VAR_INT, Attunement::xp,
            ByteBufCodecs.VAR_INT, Attunement::bonusPoints,
            Allocation.STREAM_CODEC, Attunement::spent,
            ByteBufCodecs.BOOL, Attunement::freeDraughtGiven,
            Attunement::new);

    public Attunement {
        level = Math.max(1, Math.min(MAX_LEVEL, level));
        int need = xpToNext(level);
        xp = need == 0 ? 0 : Math.max(0, Math.min(need - 1, xp));
        bonusPoints = Math.max(0, bonusPoints);
        spent = spent == null ? Allocation.ZERO : spent.clamped(STAT_CAP);
        if (spent.total() > (level - 1) + bonusPoints) {
            spent = Allocation.ZERO; // more spent than earned: everything goes back to be spent again
        }
    }

    /** Attunement XP to go from {@code level} to the next: round(50 x L^1.5 + 50); 0 at the cap. */
    public static int xpToNext(int level) {
        return level >= MAX_LEVEL ? 0 : CombatMath.xpToNext(Math.max(1, level));
    }

    /** Total Attunement XP from a fresh start to reach {@code level}. */
    public static long totalXpToReach(int level) {
        long total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL + 1); l++) {
            total += xpToNext(l);
        }
        return total;
    }

    public int xpToNext() {
        return xpToNext(level);
    }

    public boolean isMaxLevel() {
        return level >= MAX_LEVEL;
    }

    /** Every Attunement XP this player has gathered (death takes none of it). */
    public long totalXp() {
        return totalXpToReach(level) + xp;
    }

    /** One point per level above 1, plus the points awarded from outside. */
    public int earnedPoints() {
        return (level - 1) + bonusPoints;
    }

    public int unspent() {
        return earnedPoints() - spent.total();
    }

    /** {@code amount} more XP, with every level it completes (XP past the cap is dropped). */
    public Attunement withXp(long amount) {
        if (amount <= 0 || isMaxLevel()) {
            return this;
        }
        int newLevel = level;
        long carried = xp + amount;
        while (newLevel < MAX_LEVEL && carried >= xpToNext(newLevel)) {
            carried -= xpToNext(newLevel);
            newLevel++;
        }
        return new Attunement(newLevel, newLevel >= MAX_LEVEL ? 0 : (int) carried, bonusPoints, spent, freeDraughtGiven);
    }

    /** {@code points} more stat points to spend, from outside the levels. */
    public Attunement withBonusPoints(int points) {
        if (points <= 0) {
            return this;
        }
        return new Attunement(level, xp, (int) Math.min(Integer.MAX_VALUE, (long) bonusPoints + points), spent, freeDraughtGiven);
    }

    /**
     * Spends {@code add} on top of what is spent, or returns null if it isn't allowed: a negative
     * part, nothing at all, more than the unspent points, or an attribute past {@value #STAT_CAP}.
     */
    public @Nullable Attunement allocate(Allocation add) {
        for (Stat stat : Stat.values()) {
            int part = add.get(stat);
            if (part < 0 || part > STAT_CAP || spent.get(stat) + part > STAT_CAP) {
                return null;
            }
        }
        int total = add.total();
        if (total <= 0 || total > unspent()) {
            return null;
        }
        return new Attunement(level, xp, bonusPoints, spent.plus(add), freeDraughtGiven);
    }

    /** A Reverie Draught: every spent point comes back. */
    public Attunement respec() {
        return new Attunement(level, xp, bonusPoints, Allocation.ZERO, freeDraughtGiven);
    }

    /** For tests: straight to {@code newLevel} with no XP toward the next; points spent past the new total come back. */
    public Attunement atLevel(int newLevel) {
        return new Attunement(newLevel, 0, bonusPoints, spent, freeDraughtGiven);
    }

    /**
     * For tests: exactly this allocation (each part 0 to 30). If it spends more than the player has
     * earned, the difference is added to the bonus points so the state stays valid.
     */
    public Attunement withSpent(Allocation allocation) {
        Allocation clamped = allocation.clamped(STAT_CAP);
        int missing = Math.max(0, clamped.total() - earnedPoints());
        return new Attunement(level, xp, bonusPoints + missing, clamped, freeDraughtGiven);
    }

    /** True when the free Reverie Draught should be handed over now. */
    public boolean freeDraughtDue() {
        return level >= FREE_DRAUGHT_LEVEL && !freeDraughtGiven;
    }

    public Attunement withFreeDraughtGiven() {
        return new Attunement(level, xp, bonusPoints, spent, true);
    }
}
