package com.cosmicbreach.shrine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** The four shrines, one per guardian (1.1 design section 9), named as the lair registry names their guardians. */
public enum ShrineKind implements StringRepresentable {
    COLOSSUS("colossus", 1),
    LEVIATHAN("leviathan", 2),
    UNSUNG("unsung", 3),
    HELIARCH("heliarch", 4);

    public static final Codec<ShrineKind> CODEC = StringRepresentable.fromEnum(ShrineKind::values);
    private final String id;
    private final int boss;

    ShrineKind(String id, int boss) {
        this.id = id;
        this.boss = boss;
    }

    public String id() {
        return id;
    }

    /** Which boss (1 to 4) this shrine belongs to. */
    public int boss() {
        return boss;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The shrine of a lair whose guardian the lair registry names {@code guardian}, or null. */
    public static @Nullable ShrineKind ofGuardian(String guardian) {
        for (ShrineKind k : values()) {
            if (k.id.equals(guardian)) {
                return k;
            }
        }
        return null;
    }
}
