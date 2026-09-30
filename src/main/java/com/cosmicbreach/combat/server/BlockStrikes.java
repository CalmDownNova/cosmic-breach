package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.data.HitShape;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks a strike can move (GDD 6.2: "combat is a puzzle verb"): every hit shape {@link HitResolver} tests on an
 * active tick, plunge landing, ground wave step or move effect is also offered to the listeners here, with the
 * move's Impact, so a Lens Array's Umbral blocks can be knocked by a 20+ Impact hit. Listeners are registered by
 * the code that owns the blocks; the engine never depends on it.
 */
public final class BlockStrikes {
    /** One kind of block that answers strikes. */
    public interface Listener {
        /**
         * A strike by {@code player}'s move {@code serial} (unique per move instance) with {@code shape} placed at
         * {@code origin} facing {@code yaw}, carrying {@code impact}.
         */
        void strike(ServerPlayer player, HitShape shape, Vec3 origin, float yaw, double impact, int serial);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private BlockStrikes() {
    }

    public static void register(Listener listener) {
        LISTENERS.add(listener);
    }

    /** Offers one strike to every listener. */
    public static void strike(ServerPlayer player, HitShape shape, Vec3 origin, float yaw, double impact, int serial) {
        for (Listener l : LISTENERS) {
            l.strike(player, shape, origin, yaw, impact, serial);
        }
    }
}
