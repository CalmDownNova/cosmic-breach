package com.cosmicbreach.familiar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything about a familiar, kept on its Familiar Lantern (the data component {@code cosmicbreach:familiar}): what it
 * is, its bond id (the one living familiar summoned from this lantern carries it), its mode, the share of health it
 * went into the lantern with and when ({@link FamiliarRules#restored}), and when the lantern relights after a death
 * (0: never went dark). Nothing about it is saved anywhere else.
 */
public record FamiliarBond(FamiliarKind kind, UUID id, FamiliarMode mode, float health, long restedAt, long darkUntil) {
    public static final Codec<FamiliarBond> CODEC = RecordCodecBuilder.create(i -> i.group(
            FamiliarKind.CODEC.fieldOf("kind").forGetter(FamiliarBond::kind),
            UUIDUtil.CODEC.fieldOf("id").forGetter(FamiliarBond::id),
            FamiliarMode.CODEC.optionalFieldOf("mode", FamiliarMode.GUARD).forGetter(FamiliarBond::mode),
            Codec.FLOAT.optionalFieldOf("health", 1.0f).forGetter(FamiliarBond::health),
            Codec.LONG.optionalFieldOf("rested_at", 0L).forGetter(FamiliarBond::restedAt),
            Codec.LONG.optionalFieldOf("dark_until", 0L).forGetter(FamiliarBond::darkUntil)
    ).apply(i, FamiliarBond::new));

    public static final StreamCodec<ByteBuf, FamiliarBond> STREAM_CODEC = StreamCodec.composite(
            FamiliarKind.STREAM_CODEC, FamiliarBond::kind,
            UUIDUtil.STREAM_CODEC, FamiliarBond::id,
            FamiliarMode.STREAM_CODEC, FamiliarBond::mode,
            ByteBufCodecs.FLOAT, FamiliarBond::health,
            ByteBufCodecs.VAR_LONG, FamiliarBond::restedAt,
            ByteBufCodecs.VAR_LONG, FamiliarBond::darkUntil,
            FamiliarBond::new);

    /** A new hatchling of {@code kind}: its own bond id, in Guard, whole. */
    public static FamiliarBond hatch(FamiliarKind kind, UUID id) {
        return new FamiliarBond(kind, id, FamiliarMode.GUARD, 1.0f, 0L, 0L);
    }

    public FamiliarBond withMode(FamiliarMode m) {
        return new FamiliarBond(kind, id, m, health, restedAt, darkUntil);
    }

    /** Back into the lantern at {@code now} with {@code share} of its health. */
    public FamiliarBond rested(float share, long now) {
        return new FamiliarBond(kind, id, mode, Math.max(0f, Math.min(1f, share)), now, darkUntil);
    }

    /** It died at {@code now}: dark for 60 s, and whole when it relights. */
    public FamiliarBond died(long now) {
        return new FamiliarBond(kind, id, mode, 1.0f, LanternState.darkUntil(now), LanternState.darkUntil(now));
    }

    /** The share of health it comes out with at {@code now}. */
    public float healthAt(long now) {
        return FamiliarRules.restored(health, now - restedAt);
    }

    public LanternState state(boolean out, long now) {
        return LanternState.of(darkUntil, out, now);
    }
}
