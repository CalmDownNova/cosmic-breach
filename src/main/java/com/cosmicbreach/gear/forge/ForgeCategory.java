package com.cosmicbreach.gear.forge;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Astral Forge screen's tabs (1.1 design section 10). Each Forge recipe names its tab in its data
 * ({@code "category": "armor"}); a recipe without one, or with a name this version doesn't know, goes to
 * {@link #OTHER}, the way vanilla's crafting recipes fall back to {@code misc}. The screen shows one tab for each
 * category that has a recipe, in this order, so new content only has to name its category.
 */
public enum ForgeCategory implements StringRepresentable {
    WEAPONS("weapons"),
    ARMOR("armor"),
    TOOLS("tools"),
    MOUNT_GEAR("mount_gear"),
    OTHER("other");

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The recipe field {@code "category"}: {@link #OTHER} when it is missing, and also for a name this version doesn't
     * know (with a warning in the log, since a recipe in the wrong tab doesn't point at its cause); always written out by
     * datagen.
     */
    public static final MapCodec<ForgeCategory> FIELD = Codec.STRING.fieldOf("category").orElse(OTHER.name)
            .xmap(name -> named(name, unknown -> LOGGER.warn("Unknown Forge recipe category \"{}\", using \"{}\" instead", unknown, OTHER.name)),
                    ForgeCategory::getSerializedName);
    /** An id this version doesn't know (from a newer one) reads as the last tab, Other. */
    private static final IntFunction<ForgeCategory> BY_ID =
            ByIdMap.continuous(ForgeCategory::ordinal, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
    public static final StreamCodec<ByteBuf, ForgeCategory> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ForgeCategory::ordinal);

    private final String name;

    ForgeCategory(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The tab's name in the lang files: {@code gui.cosmicbreach.forge.tab.<name>}. */
    public String translationKey() {
        return "gui.cosmicbreach.forge.tab." + name;
    }

    /** The category with this data name, or null. */
    public static @Nullable ForgeCategory byName(String name) {
        for (ForgeCategory category : values()) {
            if (category.name.equals(name)) {
                return category;
            }
        }
        return null;
    }

    /** The category with this data name, or {@link #OTHER}, telling {@code onUnknown} the name if this version doesn't know it. */
    static ForgeCategory named(String name, Consumer<String> onUnknown) {
        ForgeCategory category = byName(name);
        if (category == null) {
            onUnknown.accept(name);
            return OTHER;
        }
        return category;
    }
}
