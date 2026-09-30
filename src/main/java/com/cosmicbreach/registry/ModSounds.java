package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every sound event, named as in {@code tools/sound/manifest.json} (which the sound build writes).
 * {@code assets/cosmicbreach/sounds.json} groups each event's variants and names its subtitle.
 * Registered on both sides: the server plays most of them for the players around.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWING_LIGHT = register("combat/swing_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING_HEAVY = register("combat/swing_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT = register("combat/hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_CRIT = register("combat/hit_crit");
    /** Loops seamlessly (not streamed); the client plays it as a looping sound on a charging player. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_LOOP = register("combat/charge_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_READY = register("combat/charge_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_RELEASE = register("combat/charge_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUNGE_WHISTLE = register("combat/plunge_whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUNGE_IMPACT = register("combat/plunge_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZENITH = register("combat/zenith");
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH = register("combat/dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY = register("combat/parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> PERFECT_DODGE = register("combat/perfect_dodge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESONANCE_FULL = register("combat/resonance_full");
    /** A parry that caught nothing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIFF = register("combat/whiff");

    // The Comet Maul's set: named by its moves' data (sound.swing, hit, slam, charge) and its effects.
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_SWING = register("maul/swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_SLAM = register("maul/slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_HIT = register("maul/hit");
    /** Loops seamlessly while a Maul charge is held; its pitch rises with the charge. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_CHARGE = register("maul/charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_WELL = register("maul/well");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAUL_COLLAPSE = register("maul/collapse");

    // The Binary Edges' set: named by their moves' data (sound.swing, hit, slam) and their effects.
    /** A paired metallic "tsching": the quick hooks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_SWING = register("edges/swing");
    /** Both blades crossing: two "tsching"s a hair apart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_CROSS = register("edges/cross");
    /** The Gyre's whirl of rings. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_GYRE = register("edges/gyre");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_HIT = register("edges/hit");
    /** The Tether's throw: a spinning whirr. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_THROW = register("edges/throw");
    /** The thrown blade biting into what it hit. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_STICK = register("edges/stick");
    /** The chain of light pulling the blade home. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_RETURN = register("edges/return");
    /** The blink's "shff". */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_BLINK = register("edges/blink");
    /** The blink's arrival chime. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_CHIME = register("edges/chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_BLINK_SLASH = register("edges/blink_slash");
    /** The Binary Orbit's release: both blades flung out whirring. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_ORBIT = register("edges/orbit");
    /** The Twin Meteor's landing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDGES_METEOR = register("edges/meteor");

    // The Shardling's set: registered here, played by the Shardling itself.
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_STEP = register("shardling/step");
    /** The Splinter Lunge telegraph (its subtitle says so, for players who can't hear it). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_TELL = register("shardling/tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_LUNGE = register("shardling/lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_HURT = register("shardling/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_DEATH = register("shardling/death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_SPIT = register("shardling/spit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_NEEDLE_HIT = register("shardling/needle_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_SHARD_TICK = register("shardling/shard_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARDLING_SHARD_BURST = register("shardling/shard_burst");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
