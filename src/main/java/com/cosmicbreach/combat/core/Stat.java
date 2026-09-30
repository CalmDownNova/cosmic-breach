package com.cosmicbreach.combat.core;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The four attributes (GDD section 3.3). */
public enum Stat implements StringRepresentable {
    POWER("power"),
    AGILITY("agility"),
    ARCANE("arcane"),
    RESILIENCE("resilience");

    public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

    private final String serializedName;

    Stat(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
