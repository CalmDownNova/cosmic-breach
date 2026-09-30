package com.cosmicbreach.guardian.unsung;

import java.util.EnumSet;
import java.util.Set;

/**
 * The three masks of the lost Choir (Unsung design v1), in the order the song passes between them: Alto, Tenor,
 * Bass, then the Alto again. Each has its colour (the cracks of its porcelain, the light in its eyes and mouth, its
 * attacks) and its own line in the music.
 */
public enum Voice {
    /** Pale gold, high: the melody. Homing Notes. */
    ALTO(0xFFE3A0, 0xFFC96A),
    /** Silver and teal, middle: the countermelody. The Sweeping Wave. */
    TENOR(0xCFF4FF, 0x3FE0D0),
    /** Indigo and magenta, low: the ground. Ground Ripples and the gold Bass Drop. */
    BASS(0xB37BFF, 0xFF3FB8);

    /** The mask's light: its cracks, eyes and mouth (0xRRGGBB). */
    public final int glow;
    /** Its second colour: the edge of its telegraphs and the shroud's sheen. */
    public final int accent;

    Voice(int glow, int accent) {
        this.glow = glow;
        this.accent = accent;
    }

    /** Lower case, for ids and sounds ({@code alto}). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The voice with this ordinal, or null for -1 (as synced). */
    public static Voice of(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

    /** All three. */
    public static Set<Voice> all() {
        return EnumSet.allOf(Voice.class);
    }

    /** The living voices from a bit set (bit {@code ordinal} set = broken). */
    public static Set<Voice> livingFromBroken(int brokenBits) {
        Set<Voice> out = EnumSet.noneOf(Voice.class);
        for (Voice v : values()) {
            if ((brokenBits & (1 << v.ordinal())) == 0) {
                out.add(v);
            }
        }
        return out;
    }
}
