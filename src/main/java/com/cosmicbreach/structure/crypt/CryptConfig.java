package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.structure.choir.ChoirRules;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Crypt's server config ({@code serverconfig/cosmicbreach-crypt.toml} in each world): the Choir Floor's
 * pace: "any pace" (the default since 1.2.3: the answer only has to be in the right order, nothing is timed), or
 * timed, where Relaxed (GDD 6.3) lets a step count within 5 ticks of its beat instead of 3. {@code /cosmicbreach
 * debug choir relaxed on|off} overrides Relaxed until the server stops.
 */
public final class CryptConfig {
    public static final String FILE = "cosmicbreach-crypt.toml";
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ANY_PACE;
    private static final ModConfigSpec.BooleanValue RELAXED;
    private static volatile @Nullable Boolean override;
    private static volatile @Nullable Boolean anyPaceOverride;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The Choir Floor, the rhythm puzzle of the Hollow Crypt and the Breach Sanctum.").push("choir_floor");
        ANY_PACE = b.comment("Any pace: the answer only has to be in the right order; nothing is timed. Off brings back the timed",
                        "puzzle, judged on the beat (see relaxed).")
                .translation("cosmicbreach.configuration.choir_floor.any_pace").define("any_pace", true);
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

    /** True if Choir Floors take their answers at any pace (order only). */
    public static boolean anyPace() {
        Boolean o = anyPaceOverride;
        if (o != null) {
            return o;
        }
        try {
            return ANY_PACE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Overrides "any pace" until the server stops (scenarios); null goes back to the config. */
    public static void overrideAnyPace(@Nullable Boolean anyPace) {
        anyPaceOverride = anyPace;
    }

    /** The window a Choir Floor judges with: {@link ChoirRules#ANY_PACE}, or the timed window. */
    public static int choirWindow() {
        return anyPace() ? ChoirRules.ANY_PACE : window();
    }

    /** The timed window in ticks (also the Drift Manta's call). */
    public static int window() {
        return relaxed() ? ChoirRules.RELAXED_WINDOW : ChoirRules.WINDOW;
    }

    /** Overrides Relaxed until the server stops (debug); null goes back to the config. */
    public static void override(@Nullable Boolean relaxed) {
        override = relaxed;
    }
}
