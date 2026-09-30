package com.cosmicbreach.structure.trap;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who is riding an updraft right now, on the client: the game tick each creature last touched one. The client
 * plays the lift's rush when its own player steps in ({@code client.structure.StructuresClient}).
 */
public final class UpdraftRiders {
    private static final Map<LivingEntity, Long> TOUCHED = new WeakHashMap<>();

    private UpdraftRiders() {
    }

    static synchronized void touch(LivingEntity entity, long gameTime) {
        TOUCHED.put(entity, gameTime);
    }

    /** True if {@code entity} was in an updraft on tick {@code gameTime} or the one before. */
    public static synchronized boolean riding(LivingEntity entity, long gameTime) {
        Long t = TOUCHED.get(entity);
        return t != null && gameTime - t <= 1;
    }
}
