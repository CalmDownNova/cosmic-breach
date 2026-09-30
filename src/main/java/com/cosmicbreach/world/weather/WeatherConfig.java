package com.cosmicbreach.world.weather;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Cosmic weather's server config ({@code serverconfig/cosmicbreach-weather.toml} in each world): how often
 * each event comes, in minutes of play in Aetheria, drawn evenly between a minimum and a maximum (GDD 2.5).
 * The Eclipse Surge's interval doubles after the Heliarch's first death.
 */
public final class WeatherConfig {
    public static final String FILE = "cosmicbreach-weather.toml";
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue METEORITES;
    private static final ModConfigSpec.IntValue[] MIN = new ModConfigSpec.IntValue[WeatherKind.values().length];
    private static final ModConfigSpec.IntValue[] MAX = new ModConfigSpec.IntValue[WeatherKind.values().length];
    private static final int[][] DEFAULTS = {{25, 35}, {40, 60}, {20, 30}, {60, 90}};

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Cosmic weather in Aetheria. Each event starts a set time after the last one in its layer",
                "ended, drawn evenly between the minimum and maximum minutes (counted only while someone is in Aetheria).").push("weather");
        ENABLED = b.comment("Natural weather events. Commands can still start them when this is off.").translation("cosmicbreach.configuration.weather.enabled")
                .define("enabled", true);
        METEORITES = b.comment("Meteor impacts leave a Meteorite block on the ground they hit.").translation("cosmicbreach.configuration.weather.meteoritesLeaveBlocks")
                .define("meteoritesLeaveBlocks", true);
        for (WeatherKind kind : WeatherKind.values()) {
            int[] d = DEFAULTS[kind.ordinal()];
            MIN[kind.ordinal()] = b.comment("Fewest minutes between " + kind.id() + " events.")
                    .translation("cosmicbreach.configuration.weather." + kind.id() + "MinMinutes").defineInRange(kind.id() + "MinMinutes", d[0], 1, 1440);
            MAX[kind.ordinal()] = b.comment("Most minutes between " + kind.id() + " events.")
                    .translation("cosmicbreach.configuration.weather." + kind.id() + "MaxMinutes").defineInRange(kind.id() + "MaxMinutes", d[1], 1, 1440);
        }
        b.pop();
        SPEC = b.build();
    }

    private WeatherConfig() {
    }

    public static boolean enabled() {
        return read(ENABLED, true);
    }

    public static boolean meteoritesLeaveBlocks() {
        return read(METEORITES, true);
    }

    /** The configured range for {@code kind}, in minutes: {min, max} (max never below min). */
    public static int[] minutes(WeatherKind kind) {
        int[] d = DEFAULTS[kind.ordinal()];
        int min = read(MIN[kind.ordinal()], d[0]);
        int max = read(MAX[kind.ordinal()], d[1]);
        return new int[] {min, Math.max(min, max)};
    }

    /** The schedule's intervals from this config: minutes to ticks, the Surge's doubled after the Heliarch. */
    public static int intervalTicks(WeatherSchedule.Slot slot, boolean heliarchFallen, RandomSource random) {
        int[] m = minutes(slot.kind);
        double minutes = m[0] + random.nextDouble() * (m[1] - m[0]);
        if (slot.kind == WeatherKind.SURGE && heliarchFallen) {
            minutes *= 2.0;
        }
        return (int) Math.round(minutes * 60 * 20);
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
