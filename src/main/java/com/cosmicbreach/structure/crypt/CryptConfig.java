package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.structure.choir.ChoirRules;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Crypt's server config ({@code serverconfig/cosmicbreach-crypt.toml} in each world): the Choir Floor's
 * Relaxed timing (GDD 6.3: a step counts within 5 ticks of its beat instead of 3). {@code /cosmicbreach debug choir
 * relaxed on|off} overrides it until the server stops.
 */
public final class CryptConfig {
    public static final String FILE = "cosmicbreach-crypt.toml";
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue RELAXED;
    private static volatile @Nullable Boolean override;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The Choir Floor, the rhythm puzzle of the Hollow Crypt and the Breach Sanctum.").push("choir_floor");
        RELAXED = b.comment("Relaxed timing: a step counts within 5 ticks of its beat instead of 3 (plus latency grace).")
                .translation("cosmicbreach.configuration.choir_floor.relaxed").define("relaxed", false);
        b.pop();
        SPEC = b.build();
    }

    private CryptConfig() {
    }

    public static boolean relaxed() {
        Boolean o = override;
        if (o != null) {
            return o;
        }
        try {
            return RELAXED.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** The Choir Floor's window in ticks. */
    public static int window() {
        return relaxed() ? ChoirRules.RELAXED_WINDOW : ChoirRules.WINDOW;
    }

    /** Overrides Relaxed until the server stops (debug); null goes back to the config. */
    public static void override(@Nullable Boolean relaxed) {
        override = relaxed;
    }
}
