package com.cosmicbreach.mount;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A mount kept in a Stable Crystal: its entity type and everything it saves (health, saddle, barding, tack, name, owner,
 * its last safe spot), plus what the tooltip shows. Component values are shared between copies of a stack, so the tag is
 * this record's own copy and {@link #data()} hands out copies: nothing can change what a crystal holds by editing one.
 */
public record StowedMount(ResourceLocation type, CompoundTag data, float health, float maxHealth, Optional<String> name) {
    public StowedMount {
        data = data.copy();
    }

    /** A copy of everything the mount saved. */
    @Override
    public CompoundTag data() {
        return data.copy();
    }

    /** Who owns the mount (as saved), or null. */
    public @Nullable UUID ownerId() {
        return data.hasUUID("Owner") ? data.getUUID("Owner") : null;
    }

    /** Where it last stood safe (in Aetheria), or null if it never did (a mount saved before 1.1). */
    public @Nullable Vec3 safeSpot() {
        return data.contains("CareSafeY") ? new Vec3(data.getDouble("CareSafeX"), data.getDouble("CareSafeY"), data.getDouble("CareSafeZ")) : null;
    }

    public static final Codec<StowedMount> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(StowedMount::type),
            CompoundTag.CODEC.fieldOf("data").forGetter(StowedMount::data),
            Codec.FLOAT.fieldOf("health").forGetter(StowedMount::health),
            Codec.FLOAT.fieldOf("max_health").forGetter(StowedMount::maxHealth),
            Codec.STRING.optionalFieldOf("name").forGetter(StowedMount::name)).apply(i, StowedMount::new));
    public static final StreamCodec<ByteBuf, StowedMount> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, StowedMount::type,
            ByteBufCodecs.COMPOUND_TAG, StowedMount::data,
            ByteBufCodecs.FLOAT, StowedMount::health,
            ByteBufCodecs.FLOAT, StowedMount::maxHealth,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), StowedMount::name,
            StowedMount::new);
}
