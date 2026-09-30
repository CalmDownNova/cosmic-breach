package com.cosmicbreach.combat.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What slot of a weapon's moveset a move fills. */
public enum MoveKind implements StringRepresentable {
    /** A hit of the light chain. Can flow into the charge if the button stays held. */
    LIGHT("light"),
    /** Released from a held charge; its motion value scales with the hold. */
    CHARGED("charged"),
    /** Attack while airborne aiming down: active until landing, damage grows with the fall. */
    PLUNGE("plunge"),
    /** Attack at the end of a dash or just after it. */
    DASH_ATTACK("dash_attack"),
    /** The weapon's right-click ability; costs Resonance and has a cooldown. */
    ABILITY("ability");

    public static final Codec<MoveKind> CODEC = StringRepresentable.fromEnum(MoveKind::values);

    private final String serializedName;

    MoveKind(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
