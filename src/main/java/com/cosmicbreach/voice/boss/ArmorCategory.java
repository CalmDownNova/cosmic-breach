package com.cosmicbreach.voice.boss;

import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a fighter wears, for {@code armor:<category>} lines: all four pieces of one of this mod's sets, some of this mod's
 * pieces, only other armor, or nothing in any slot. A full set also has its script name for {@code set:<name>}.
 */
public enum ArmorCategory {
    MOD_FULL_SET("mod_full_set"),
    MOD_PARTIAL("mod_partial"),
    VANILLA("vanilla"),
    NONE("none");

    /** The script's names for the mod's sets, by set id path. */
    public static final Map<String, String> SET_NAMES = Map.of("starfall_vanguard", "vanguard", "driftweave", "driftweave",
            "choir_regalia", "regalia");

    /** A fighter's category, and the set's script name when it is a full set (else empty). */
    public record Worn(ArmorCategory category, String set) {}

    private final String id;

    ArmorCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static @Nullable ArmorCategory byId(String id) {
        for (ArmorCategory c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Pure: the most pieces of one mod set worn, all mod pieces worn, other armor pieces worn. */
    public static ArmorCategory of(int bestSetPieces, int modPieces, int otherArmorPieces) {
        if (bestSetPieces >= ArmorSet.FULL_SET) {
            return MOD_FULL_SET;
        }
        if (modPieces > 0) {
            return MOD_PARTIAL;
        }
        return otherArmorPieces > 0 ? VANILLA : NONE;
    }

    public static Worn of(Player player) {
        int best = 0;
        int mod = 0;
        String set = "";
        for (Map.Entry<ArmorSet, Integer> e : ArmorSets.worn(player).entrySet()) {
            mod += e.getValue();
            if (e.getValue() > best) {
                best = e.getValue();
                set = SET_NAMES.getOrDefault(e.getKey().id().getPath(), e.getKey().id().getPath());
            }
        }
        int other = 0;
        for (EquipmentSlot slot : ArmorSets.ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && ArmorSets.setOf(stack) == null && stack.getItem() instanceof ArmorItem) {
                other++;
            }
        }
        ArmorCategory category = of(best, mod, other);
        return new Worn(category, category == MOD_FULL_SET ? set : "");
    }
}
