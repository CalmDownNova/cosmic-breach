package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Points per attribute: what a player has spent, or what one visit to the screen adds. */
public record Allocation(int power, int agility, int arcane, int resilience) {
    public static final Allocation ZERO = new Allocation(0, 0, 0, 0);

    public static final Codec<Allocation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("power", 0).forGetter(Allocation::power),
            Codec.INT.optionalFieldOf("agility", 0).forGetter(Allocation::agility),
            Codec.INT.optionalFieldOf("arcane", 0).forGetter(Allocation::arcane),
            Codec.INT.optionalFieldOf("resilience", 0).forGetter(Allocation::resilience)
    ).apply(i, Allocation::new));

    public static final StreamCodec<ByteBuf, Allocation> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Allocation::power,
            ByteBufCodecs.VAR_INT, Allocation::agility,
            ByteBufCodecs.VAR_INT, Allocation::arcane,
            ByteBufCodecs.VAR_INT, Allocation::resilience,
            Allocation::new);

    public static Allocation of(Stat stat, int points) {
        return ZERO.with(stat, points);
    }

    public int get(Stat stat) {
        return switch (stat) {
            case POWER -> power;
            case AGILITY -> agility;
            case ARCANE -> arcane;
            case RESILIENCE -> resilience;
        };
    }

    public Allocation with(Stat stat, int points) {
        return switch (stat) {
            case POWER -> new Allocation(points, agility, arcane, resilience);
            case AGILITY -> new Allocation(power, points, arcane, resilience);
            case ARCANE -> new Allocation(power, agility, points, resilience);
            case RESILIENCE -> new Allocation(power, agility, arcane, points);
        };
    }

    public Allocation plus(Allocation other) {
        return new Allocation(power + other.power, agility + other.agility, arcane + other.arcane,
                resilience + other.resilience);
    }

    public int total() {
        return power + agility + arcane + resilience;
    }

    public boolean isZero() {
        return power == 0 && agility == 0 && arcane == 0 && resilience == 0;
    }

    /** Every component clamped to 0..{@code max}. */
    public Allocation clamped(int max) {
        return new Allocation(clamp(power, max), clamp(agility, max), clamp(arcane, max), clamp(resilience, max));
    }

    public StatBlock toStatBlock() {
        return new StatBlock(power, agility, arcane, resilience);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
