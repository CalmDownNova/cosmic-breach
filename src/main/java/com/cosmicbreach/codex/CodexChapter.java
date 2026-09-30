package com.cosmicbreach.codex;

import java.util.Locale;
import java.util.Optional;

/**
 * What a player has lived through, as far as the Starfall Codex cares (GDD 9.4, the chapters that unlock with
 * progress). Pages name these by {@link #key()}: in their frontmatter ({@code unlock: drift}) and in the
 * {@code <Gate when="drift">} tag. Each is one bit of {@link CodexProgress}; the order is the save format, so new
 * ones go at the end.
 */
public enum CodexChapter {
    /** Has arrived in Aetheria at least once: the Reach, and everything learnt there. */
    ARRIVED,
    /** Attuned to the Drift (the Prism Colossus's first kill). */
    DRIFT,
    /** Attuned to the Deep (the Thalassine Leviathan's first kill). */
    DEEP,
    /** Attuned to the Breach Sanctum (the Unsung's first kill). */
    SANCTUM,
    /** Has stood in the Prism Colossus's fight. */
    MET_COLOSSUS,
    /** Has stood in the Thalassine Leviathan's fight. */
    MET_LEVIATHAN,
    /** Has stood in the Unsung's fight. */
    MET_UNSUNG,
    /** Has seen the Hollow Heliarch summoned. */
    MET_HELIARCH,
    /** Has sealed the Breach (the Hollow Heliarch's first kill). */
    SEALED;

    /** The page-facing name: {@code drift}, {@code met_colossus}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public long bit() {
        return 1L << ordinal();
    }

    public static Optional<CodexChapter> byKey(String key) {
        for (CodexChapter c : values()) {
            if (c.key().equals(key.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
