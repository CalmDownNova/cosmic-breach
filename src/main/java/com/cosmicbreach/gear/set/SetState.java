package com.cosmicbreach.gear.set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's armor set state, one attachment for every set (the player wears at most one full set):
 *
 * <ul>
 *   <li>{@code meter}: the worn set's own gauge until {@code meterUntil} (game time). The Starfall Vanguard's
 *       Heat, the Driftweave's dash charges ready (its cloth shimmers with them), the Choir Regalia's ability casts
 *       counted toward an echo (its halo spins faster when one is primed).</li>
 *   <li>{@code cooldownUntil} and {@code cooldownTicks}: the set ability's cooldown, when it ends and how long
 *       it was in all (for the HUD pip's fill). Shared by every set, so swapping sets never resets it.</li>
 *   <li>{@code activeUntil}: while a set ability's effect lasts (the meteor is falling, Drift runs, the Hymn
 *       rings).</li>
 *   <li>{@code owner}: the id of the set the meter and the active window belong to ("" for any, as the first
 *       set wrote them), so one set never reads another's gauge after a swap: ask with
 *       {@link #meter(ResourceLocation, long)} and {@link #active(ResourceLocation, long)}.</li>
 * </ul>
 *
 * Immutable: change it with the {@code with} methods and {@code setData}, which syncs it to the player and
 * everyone tracking them (the armor's glow reads the meter).
 */
public record SetState(float meter, long meterUntil, long cooldownUntil, int cooldownTicks, long activeUntil, String owner) {
    public static final SetState NONE = new SetState(0f, 0L, 0L, 0, 0L, "");

    public static final Codec<SetState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("meter", 0f).forGetter(SetState::meter),
            Codec.LONG.optionalFieldOf("meter_until", 0L).forGetter(SetState::meterUntil),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(SetState::cooldownUntil),
            Codec.INT.optionalFieldOf("cooldown_ticks", 0).forGetter(SetState::cooldownTicks),
            Codec.LONG.optionalFieldOf("active_until", 0L).forGetter(SetState::activeUntil),
            Codec.STRING.optionalFieldOf("owner", "").forGetter(SetState::owner)
    ).apply(i, SetState::new));

    public static final StreamCodec<ByteBuf, SetState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SetState::meter,
            ByteBufCodecs.VAR_LONG, SetState::meterUntil,
            ByteBufCodecs.VAR_LONG, SetState::cooldownUntil,
            ByteBufCodecs.VAR_INT, SetState::cooldownTicks,
            ByteBufCodecs.VAR_LONG, SetState::activeUntil,
            ByteBufCodecs.STRING_UTF8, SetState::owner,
            SetState::new);

    public SetState {
        owner = owner == null ? "" : owner;
    }

    /** Without an owner: the meter and the active window belong to whichever set reads them. */
    public SetState(float meter, long meterUntil, long cooldownUntil, int cooldownTicks, long activeUntil) {
        this(meter, meterUntil, cooldownUntil, cooldownTicks, activeUntil, "");
    }

    /** True if the meter and the active window are {@code set}'s (or nobody's in particular). */
    public boolean ownedBy(ResourceLocation set) {
        return owner.isEmpty() || owner.equals(set.toString());
    }

    /** The meter at {@code now}: 0 once it has run out. */
    public float meter(long now) {
        return now < meterUntil ? meter : 0f;
    }

    /** {@code set}'s meter at {@code now}: 0 once it has run out or if another set wrote it. */
    public float meter(ResourceLocation set, long now) {
        return ownedBy(set) ? meter(now) : 0f;
    }

    /** The meter set to {@code value}, lasting until {@code until}. */
    public SetState withMeter(float value, long until) {
        return new SetState(Math.max(0f, value), value > 0f ? until : 0L, cooldownUntil, cooldownTicks, activeUntil, owner);
    }

    /** {@code set}'s meter set to {@code value}, lasting until {@code until}; the set owns the meter from now on. */
    public SetState withMeter(ResourceLocation set, float value, long until) {
        return new SetState(Math.max(0f, value), value > 0f ? until : 0L, cooldownUntil, cooldownTicks,
                ownedBy(set) ? activeUntil : 0L, set.toString());
    }

    public SetState withoutMeter() {
        return new SetState(0f, 0L, cooldownUntil, cooldownTicks, activeUntil, owner);
    }

    public boolean ready(long now) {
        return now >= cooldownUntil;
    }

    public int cooldownLeft(long now) {
        return (int) Math.max(0L, cooldownUntil - now);
    }

    /** How far the cooldown has come, 0 just cast to 1 ready. */
    public float cooldownFill(long now) {
        if (ready(now) || cooldownTicks <= 0) {
            return 1f;
        }
        return 1f - (float) cooldownLeft(now) / cooldownTicks;
    }

    /** The ability cast at {@code now}: ready again after {@code ticks}. */
    public SetState withCooldown(long now, int ticks) {
        return new SetState(meter, meterUntil, now + Math.max(0, ticks), Math.max(0, ticks), activeUntil, owner);
    }

    public boolean active(long now) {
        return now < activeUntil;
    }

    /** True while {@code set}'s ability effect lasts (and not another set's). */
    public boolean active(ResourceLocation set, long now) {
        return ownedBy(set) && active(now);
    }

    public SetState withActive(long until) {
        return new SetState(meter, meterUntil, cooldownUntil, cooldownTicks, until, owner);
    }

    /** {@code set}'s ability effect lasting until {@code until}; the set owns the state from now on (another's meter goes). */
    public SetState withActive(ResourceLocation set, long until) {
        boolean same = ownedBy(set);
        return new SetState(same ? meter : 0f, same ? meterUntil : 0L, cooldownUntil, cooldownTicks, until, set.toString());
    }
}
