package com.cosmicbreach.client.guardian;

import com.cosmicbreach.guardian.colossus.RefractionPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The Refraction beams the server last sent, per Colossus (client thread). */
public final class RefractionView {
    /**
     * One Colossus's beams: charging (red lines) or firing, the paths' points and nodes (crystal indices, the core last if
     * turned back), which end in the core, when this began, and when a crystal last turned (the game tick a charge's paths
     * changed, for the flash on the crystal; {@link Long#MIN_VALUE} if never).
     */
    public record Beams(byte mode, long start, List<List<Vec3>> paths, List<List<Integer>> nodes, List<Boolean> core, long turnedAt) {
        /** The lit crystal of path {@code i} (its first node), or -1. */
        public int lit(int i) {
            return i < nodes.size() && !nodes.get(i).isEmpty() ? nodes.get(i).get(0) : -1;
        }
    }

    private static final Map<Integer, Beams> BY_COLOSSUS = new HashMap<>();

    private RefractionView() {
    }

    static void update(RefractionPayload payload) {
        if (payload.mode() == RefractionPayload.OFF) {
            BY_COLOSSUS.remove(payload.colossus());
            return;
        }
        Beams before = BY_COLOSSUS.get(payload.colossus());
        long turnedAt = before == null ? Long.MIN_VALUE : before.turnedAt();
        if (before != null && payload.mode() == RefractionPayload.CHARGING && before.mode() == RefractionPayload.CHARGING
                && !before.nodes().equals(payload.nodes())) {
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            turnedAt = level == null ? Long.MIN_VALUE : level.getGameTime();
        }
        BY_COLOSSUS.put(payload.colossus(), new Beams(payload.mode(), payload.start(), payload.paths(), payload.nodes(), payload.core(), turnedAt));
    }

    public static @Nullable Beams of(int colossus) {
        return BY_COLOSSUS.get(colossus);
    }

    static void clear() {
        BY_COLOSSUS.clear();
    }
}
