package com.cosmicbreach.familiar;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The three combat familiars (GDD 8.2). Each vault leans to one: a Reliquary's Star Egg holds an Emberwisp, an
 * Observatory's a Gravikin, a Crypt's a Prism Moth, so the familiars come in the order the layers do.
 */
public enum FamiliarKind implements StringRepresentable {
    /** A small sun-sprite orbiting its owner: Scorch every 3 s, Kindled on perfect dodges and parries. */
    EMBERWISP("emberwisp", 0xFFB040, 1.0),
    /** A pebble golem hopping between surfaces: the taunt, the slow, +50% health. */
    GRAVIKIN("gravikin", 0x8C9CFF, FamiliarRules.GRAVIKIN_HEALTH),
    /** A glass-winged moth throwing rainbow glints: Refract every 2 s, a cleanse every 30 s. */
    PRISM_MOTH("prism_moth", 0xDDF4FF, 1.0);

    public static final Codec<FamiliarKind> CODEC = StringRepresentable.fromEnum(FamiliarKind::values);
    public static final StreamCodec<ByteBuf, FamiliarKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)],
            Enum::ordinal);

    private final String id;
    private final int color;
    private final double healthScale;

    FamiliarKind(String id, int color, double healthScale) {
        this.id = id;
        this.color = color;
        this.healthScale = healthScale;
    }

    public String id() {
        return id;
    }

    /** Its light, 0xRRGGBB (the lantern's glow, the summoning burst). */
    public int color() {
        return color;
    }

    /** Its health against the base rule: the Gravikin's x1.5. */
    public double healthScale() {
        return healthScale;
    }

    /** The translation key of its name. */
    public String nameKey() {
        return "entity.cosmicbreach." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The kind with this id, or null. */
    public static FamiliarKind byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (FamiliarKind k : values()) {
            if (k.id.equals(s)) {
                return k;
            }
        }
        return null;
    }
}
