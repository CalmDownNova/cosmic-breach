package com.cosmicbreach.client.combat;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings ({@code config/cosmicbreach-client.toml}, also in the mod list's config screen). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SHOW_COMBAT_HUD;
    private static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    private static final double SCREEN_SHAKE_DEFAULT = 0.6;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SHOW_COMBAT_HUD = builder
                .comment("Resonance, dash charges and the charge ring round the crosshair while a combat weapon is in hand.")
                .translation("cosmicbreach.configuration.showCombatHud")
                .define("showCombatHud", true);
        SCREEN_SHAKE = builder
                .comment("How strongly the camera shakes on heavy hits: 0 is off, 1 is full.")
                .translation("cosmicbreach.configuration.screenShake")
                .defineInRange("screenShake", SCREEN_SHAKE_DEFAULT, 0.0, 1.0);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean showCombatHud() {
        return !SPEC.isLoaded() || SHOW_COMBAT_HUD.get();
    }

    public static double screenShake() {
        return SPEC.isLoaded() ? SCREEN_SHAKE.get() : SCREEN_SHAKE_DEFAULT;
    }
}
