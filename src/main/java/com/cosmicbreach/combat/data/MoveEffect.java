package com.cosmicbreach.combat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Something a move does beyond its hitbox, by id, with its numbers: {@code {"id": "cosmicbreach:gravity_well",
 * "params": {"radius": 6, "pull": 0.12}}}. The id picks a handler registered on the server (what happens:
 * pulls, zones, extra hits) and one on the client (what it looks like); either side may have none. An
 * ability is then its move's data plus a small handler. Parameters are plain numbers; a handler reads
 * each with its own default.
 */
public record MoveEffect(ResourceLocation id, Map<String, Double> params) {
    public static final Codec<MoveEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(MoveEffect::id),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(MoveEffect::params)
    ).apply(i, MoveEffect::new));

    public MoveEffect {
        params = Map.copyOf(params);
    }

    /** The parameter {@code name}, or {@code fallback} when the data leaves it out. */
    public double param(String name, double fallback) {
        Double value = params.get(name);
        return value == null ? fallback : value;
    }

    /** {@link #param} rounded to a whole number (tick counts, particle counts). */
    public int intParam(String name, int fallback) {
        Double value = params.get(name);
        return value == null ? fallback : (int) Math.round(value);
    }
}
