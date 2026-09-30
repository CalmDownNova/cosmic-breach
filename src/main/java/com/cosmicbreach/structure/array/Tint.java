package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.lens.Lens;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** A beam colour as a block state value: receptors want one, filters give one (never white). */
public enum Tint implements StringRepresentable {
    WHITE(Lens.WHITE),
    GOLD(Lens.GOLD),
    TEAL(Lens.TEAL),
    MAGENTA(Lens.MAGENTA);

    public final int color;

    Tint(int color) {
        this.color = color;
    }

    public static Tint of(int color) {
        return values()[color & 3];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
