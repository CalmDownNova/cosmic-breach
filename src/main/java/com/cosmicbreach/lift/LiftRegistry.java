package com.cosmicbreach.lift;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The lifts' sound ({@code tools/sound/lift.py}): the rising air, a seamless loop heard from 48 blocks. */
public final class LiftRegistry {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> STREAM = SOUNDS.register("lift/stream",
            () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id("lift/stream")));

    private LiftRegistry() {
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
