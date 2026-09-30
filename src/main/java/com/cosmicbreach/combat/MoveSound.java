package com.cosmicbreach.combat;

import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sound a move makes on its first active tick, picked from its data: light swings for the quick
 * hits, a heavier swing from {@value #HEAVY_IMPACT} Impact up, the charged strike for a charged move,
 * the ability's own sound (Zenith, Meridian's only ability) and nothing for a plunge (it whistles when
 * it starts and booms when it lands). The attacker's client plays it from prediction; the server plays
 * it for everyone else. A move's data may name its own ({@link #swingOverride}). Pure except for
 * {@link #sound()}, {@link #swingOverride} and {@link #resolve}, which look sound events up.
 */
public enum MoveSound {
    NONE(0f, 1f),
    SWING_LIGHT(0.8f, 1.05f),
    SWING_HEAVY(0.95f, 0.95f),
    CHARGE_RELEASE(1.0f, 1.0f),
    ZENITH(1.0f, 1.0f);

    /** Swings with at least this much Impact sound heavy. */
    public static final double HEAVY_IMPACT = 10.0;
    /** Swing pitches vary by up to this much either way, so repeats don't sound identical. */
    public static final float PITCH_SPREAD = 0.06f;

    private final float volume;
    private final float pitch;

    MoveSound(float volume, float pitch) {
        this.volume = volume;
        this.pitch = pitch;
    }

    public static MoveSound forMove(MoveDef def) {
        return switch (def.kind()) {
            case LIGHT, DASH_ATTACK -> def.hit().impact() >= HEAVY_IMPACT ? SWING_HEAVY : SWING_LIGHT;
            case CHARGED -> CHARGE_RELEASE;
            case ABILITY -> ZENITH;
            case PLUNGE -> NONE;
        };
    }

    public float volume() {
        return volume;
    }

    /** The pitch for a roll in [0, 1): swings spread a little, the one-off sounds stay put. */
    public float pitch(float roll) {
        return this == SWING_LIGHT || this == SWING_HEAVY ? pitch + (roll * 2f - 1f) * PITCH_SPREAD : pitch;
    }

    /**
     * The sound a move's data names for its first active tick ({@code sound.swing}), if any: it replaces
     * the one {@link #forMove} picks.
     */
    public static @Nullable Holder<SoundEvent> swingOverride(MoveDef def) {
        return def.traits().sound().flatMap(MoveTraits.Sounds::swing).map(MoveSound::resolve).orElse(null);
    }

    /** Pitch of a charged release's own swing at its lightest and at its fullest charge: fuller is deeper. */
    public static final float CHARGE_PITCH_LOW = 1.08f;
    public static final float CHARGE_PITCH_FULL = 0.86f;

    /**
     * The pitch a move's own swing sound plays at: a charged release follows its charge (from
     * {@value #CHARGE_PITCH_LOW} at the minimum hold to {@value #CHARGE_PITCH_FULL} at full); anything else 1.
     */
    public static float swingPitch(MoveDef def, double mv) {
        if (def.kind() != MoveKind.CHARGED || def.hit().mvCap() <= def.hit().mv()) {
            return 1.0f;
        }
        double t = Math.max(0.0, Math.min(1.0, (mv - def.hit().mv()) / (def.hit().mvCap() - def.hit().mv())));
        return (float) (CHARGE_PITCH_LOW + (CHARGE_PITCH_FULL - CHARGE_PITCH_LOW) * t);
    }

    /** A sound event by id: the registered one, or a direct event for an id nobody registered. */
    public static Holder<SoundEvent> resolve(ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.getHolder(id).<Holder<SoundEvent>>map(h -> h)
                .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(id)));
    }

    public @Nullable Holder<SoundEvent> sound() {
        return switch (this) {
            case NONE -> null;
            case SWING_LIGHT -> ModSounds.SWING_LIGHT;
            case SWING_HEAVY -> ModSounds.SWING_HEAVY;
            case CHARGE_RELEASE -> ModSounds.CHARGE_RELEASE;
            case ZENITH -> ModSounds.ZENITH;
        };
    }
}
