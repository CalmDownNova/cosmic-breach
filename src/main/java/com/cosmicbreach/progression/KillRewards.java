package com.cosmicbreach.progression;

import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Creatures that give a fixed amount of Attunement XP to everyone who took part in the kill. Register
 * a creature once at mod construction ({@link Progression#register} has the Shardling); a creature
 * whose reward depends on the player (guardians: first kill or repeat) calls
 * {@link AttunementXp#awardKill(net.minecraft.world.entity.LivingEntity, java.util.function.ToIntFunction)} itself.
 */
public final class KillRewards {
    private static final Map<Supplier<? extends EntityType<?>>, Integer> REWARDS = new LinkedHashMap<>();

    private KillRewards() {
    }

    public static synchronized void register(Supplier<? extends EntityType<?>> type, int xp) {
        REWARDS.put(type, xp);
    }

    /** The XP a kill of this type gives each participant, or 0. */
    public static synchronized int xpFor(EntityType<?> type) {
        for (Map.Entry<Supplier<? extends EntityType<?>>, Integer> entry : REWARDS.entrySet()) {
            if (entry.getKey().get() == type) {
                return entry.getValue();
            }
        }
        return 0;
    }
}
