package com.cosmicbreach.status;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The combat statuses of GDD 8.2 that live outside the gear: Rift. (Scorch is the gear's, {@code GearRegistry.SCORCH}.) */
public final class Statuses {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);

    public static final DeferredHolder<MobEffect, Rift> RIFT = EFFECTS.register("rift", Rift::new);

    private Statuses() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
