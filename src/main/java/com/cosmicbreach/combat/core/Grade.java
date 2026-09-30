package com.cosmicbreach.combat.core;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Scaling grades: how strongly a weapon converts a stat into damage (GDD section 3.4). */
public enum Grade implements StringRepresentable {
    S(1.00),
    A(0.80),
    B(0.55),
    C(0.30),
    D(0.15);

    public static final Codec<Grade> CODEC = StringRepresentable.fromEnum(Grade::values);

    public final double coefficient;

    Grade(double coefficient) {
        this.coefficient = coefficient;
    }

    @Override
    public String getSerializedName() {
        return name();
    }
}
