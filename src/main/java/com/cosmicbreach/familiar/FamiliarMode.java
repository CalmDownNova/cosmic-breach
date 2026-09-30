package com.cosmicbreach.familiar;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * How a familiar fights, cycled by a tap of the familiar key: Attack, Guard, Passive.
 * <ul>
 *   <li>Attack: it goes for its owner's target, anything that hurt its owner and any monster near its owner; its
 *       specials (Scorch, the taunt and the slow, Refract) run.</li>
 *   <li>Guard: it stays at its owner's side and fights only what its owner is fighting, near them; specials run.</li>
 *   <li>Passive: it never starts or joins a fight (no attacks, no Scorch, no taunt, no slow, no Refract), but what it
 *       does for its owner alone stays: Kindled and the Prism Moth's cleanse.</li>
 * </ul>
 */
public enum FamiliarMode implements StringRepresentable {
    ATTACK("attack"),
    GUARD("guard"),
    PASSIVE("passive");

    public static final Codec<FamiliarMode> CODEC = StringRepresentable.fromEnum(FamiliarMode::values);
    public static final StreamCodec<ByteBuf, FamiliarMode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)],
            Enum::ordinal);

    private final String id;

    FamiliarMode(String id) {
        this.id = id;
    }

    /** The next mode a tap of the key turns to: Attack, Guard, Passive, Attack. */
    public FamiliarMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** True if it fights and uses its specials on enemies (Attack and Guard). */
    public boolean fights() {
        return this != PASSIVE;
    }

    public String id() {
        return id;
    }

    public String nameKey() {
        return "cosmicbreach.familiar.mode." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static FamiliarMode byOrdinal(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
