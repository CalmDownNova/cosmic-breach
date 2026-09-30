package com.cosmicbreach.structure;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The structures' server config ({@code serverconfig/cosmicbreach-structures.toml} in each world): how near a
 * player wakes a Lens Array, how long before its hint lights, and how long the Warden Eye rests between calls.
 */
public final class StructureConfig {
    public static final String FILE = "cosmicbreach-structures.toml";
    public static final ModConfigSpec SPEC;
    /** Loose pieces carried farther than this from their array go back on its grid. */
    public static final double CARRY_RANGE = 48.0;

    private static final ModConfigSpec.IntValue WAKE_RADIUS;
    private static final ModConfigSpec.IntValue HINT_SECONDS;
    private static final ModConfigSpec.IntValue EYE_COOLDOWN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The Lens Array, the beam puzzle of the Spire Reliquary and the Gyre Observatory.").push("lens_array");
        WAKE_RADIUS = b.comment("A room's puzzle is made from its seed the first time a player comes this close (blocks).")
                .translation("cosmicbreach.configuration.lens_array.wakeRadius").defineInRange("wakeRadius", 24, 8, 64);
        HINT_SECONDS = b.comment("Unsolved this long, the Codex's Lens Array page offers to light one mirror of the answer (seconds).")
                .translation("cosmicbreach.configuration.lens_array.hintSeconds").defineInRange("hintSeconds", 300, 10, 3600);
        EYE_COOLDOWN = b.comment("After the Warden Eye calls its Shardlings, it rests this long (seconds).")
                .translation("cosmicbreach.configuration.lens_array.eyeCooldownSeconds").defineInRange("eyeCooldownSeconds", 30, 1, 600);
        b.pop();
        SPEC = b.build();
    }

    private StructureConfig() {
    }

    public static int wakeRadius() {
        return read(WAKE_RADIUS, 24);
    }

    public static long hintTicks() {
        return read(HINT_SECONDS, 300) * 20L;
    }

    public static long eyeCooldownTicks() {
        return read(EYE_COOLDOWN, 30) * 20L;
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
