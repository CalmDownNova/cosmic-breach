package com.cosmicbreach.onboarding;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The way in's server config ({@code serverconfig/cosmicbreach-onboarding.toml} in each world, GDD 1.3):
 * Starfalls on or off, the first-sunset window, and how far from the player a shard may land.
 */
public final class OnboardingConfig {
    public static final String FILE = "cosmicbreach-onboarding.toml";
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue WINDOW_START;
    private static final ModConfigSpec.IntValue WINDOW_END;
    private static final ModConfigSpec.IntValue MIN_DISTANCE;
    private static final ModConfigSpec.IntValue MAX_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Starfalls: a shard falls near each player at their first sunset in the Overworld and once each",
                "later night, the catalyst that opens a Breach Ring.").push("starfall");
        ENABLED = b.comment("Starfalls happen. Commands can still call one when this is off.").translation("cosmicbreach.configuration.starfall.enabled").define("enabled", true);
        WINDOW_START = b.comment("The first Starfall's window opens at this time of day (ticks, 12000 is sunset).")
                .translation("cosmicbreach.configuration.starfall.firstWindowStart").defineInRange("firstWindowStart", 12_000, 0, 23_999);
        WINDOW_END = b.comment("And closes at this time of day (beds work from 12542).")
                .translation("cosmicbreach.configuration.starfall.firstWindowEnd").defineInRange("firstWindowEnd", 12_500, 1, 24_000);
        MIN_DISTANCE = b.comment("Nearest a shard lands to its player, in blocks.").translation("cosmicbreach.configuration.starfall.minDistance").defineInRange("minDistance", 48, 4, 256);
        MAX_DISTANCE = b.comment("Farthest a shard lands from its player, in blocks.").translation("cosmicbreach.configuration.starfall.maxDistance").defineInRange("maxDistance", 96, 4, 256);
        b.pop();
        SPEC = b.build();
    }

    private OnboardingConfig() {
    }

    public static boolean enabled() {
        return read(ENABLED, true);
    }

    public static int windowStart() {
        return read(WINDOW_START, 12_000);
    }

    public static int windowEnd() {
        return Math.max(windowStart() + 1, read(WINDOW_END, 12_500));
    }

    public static int minDistance() {
        return read(MIN_DISTANCE, 48);
    }

    public static int maxDistance() {
        return Math.max(minDistance(), read(MAX_DISTANCE, 96));
    }

    private static boolean read(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
