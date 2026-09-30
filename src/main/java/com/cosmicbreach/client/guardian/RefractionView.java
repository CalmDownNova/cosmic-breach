package com.cosmicbreach.client.guardian;

import com.cosmicbreach.guardian.colossus.RefractionPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The Refraction beams the server last sent, per Colossus (client thread). */
public final class RefractionView {
    /** One Colossus's beams: charging (red lines) or firing, the paths, which end in the core, and when this began. */
    public record Beams(byte mode, long start, List<List<Vec3>> paths, List<Boolean> core) {
    }

    private static final Map<Integer, Beams> BY_COLOSSUS = new HashMap<>();

    private RefractionView() {
    }

    static void update(RefractionPayload payload) {
        if (payload.mode() == RefractionPayload.OFF) {
            BY_COLOSSUS.remove(payload.colossus());
        } else {
            BY_COLOSSUS.put(payload.colossus(), new Beams(payload.mode(), payload.start(), payload.paths(), payload.core()));
        }
    }

    public static @Nullable Beams of(int colossus) {
        return BY_COLOSSUS.get(colossus);
    }

    static void clear() {
        BY_COLOSSUS.clear();
    }
}
