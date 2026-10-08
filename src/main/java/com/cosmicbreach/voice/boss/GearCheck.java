package com.cosmicbreach.voice.boss;

import com.cosmicbreach.combat.core.ArmorMath;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.registry.ModAttributes;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Over, under or evenly geared, for {@code gear:<verdict>} lines (Aetheria 1.1 Design section 7, the voice audit's rule),
 * against the boss's reference loadout from its catalog. Each fighter scores the sum of three steps:
 * <ul>
 *   <li>offense: their weapon's tier minus the reference tier (this mod's weapons at their gear tier; vanilla iron,
 *       diamond and netherite swords and axes count 1, 2 and 3, a bow or crossbow 1, a trident or mace 2, anything else
 *       0), with the weapon that has dealt most of their damage this fight, or the one in hand before they hit;</li>
 *   <li>defense: the boss's typical hit through vanilla's armor formula and their Resilience, against the same hit on
 *       the reference gear: under 0.75 of it is +1, over 1.35 of it is -1;</li>
 *   <li>level: below the reference band's bottom minus 4 is -1, above its top plus 6 is +1.</li>
 * </ul>
 * A fighter scoring 2 or more is over, -2 or less under. The party (the script's rule) is over if at least half its
 * fighters are over and none is under, under if at least half are under, and even otherwise. Pure but for {@link #of}.
 */
public final class GearCheck {
    /** Resilience: half a percent of damage reduction a point. */
    public static final double RESILIENCE_SHARE = 0.005;

    public enum Verdict {
        OVER("over"),
        EVEN("even"),
        UNDER("under");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static @Nullable Verdict byId(String id) {
            for (Verdict v : values()) {
                if (v.id.equals(id)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** A boss's reference loadout (its catalog's "reference"). */
    public record Reference(int weaponTier, double armor, double toughness, double resilience, int levelMin, int levelMax,
            double typicalHit) {}

    /** What one fighter brings. */
    public record Loadout(int weaponTier, double armor, double toughness, double resilience, int level) {}

    private GearCheck() {
    }

    public static int score(Loadout l, Reference r) {
        int offense = l.weaponTier() - r.weaponTier();
        double mine = ArmorMath.taken(r.typicalHit(), l.armor(), l.toughness(), RESILIENCE_SHARE * l.resilience());
        double ref = ArmorMath.taken(r.typicalHit(), r.armor(), r.toughness(), RESILIENCE_SHARE * r.resilience());
        double ratio = mine / ref;
        int defense = ratio < 0.75 ? 1 : ratio > 1.35 ? -1 : 0;
        int level = l.level() < r.levelMin() - 4 ? -1 : l.level() > r.levelMax() + 6 ? 1 : 0;
        return offense + defense + level;
    }

    public static Verdict verdict(int score) {
        return score >= 2 ? Verdict.OVER : score <= -2 ? Verdict.UNDER : Verdict.EVEN;
    }

    /** The party's verdict from each fighter's. */
    public static Verdict party(List<Verdict> each) {
        int over = 0;
        int under = 0;
        for (Verdict v : each) {
            if (v == Verdict.OVER) {
                over++;
            } else if (v == Verdict.UNDER) {
                under++;
            }
        }
        if (each.isEmpty()) {
            return Verdict.EVEN;
        }
        if (under == 0 && over * 2 >= each.size()) {
            return Verdict.OVER;
        }
        return under * 2 >= each.size() ? Verdict.UNDER : Verdict.EVEN;
    }

    /** A vanilla item's tier by its id, pure. */
    public static int vanillaTier(String itemId) {
        if (itemId.equals("minecraft:bow") || itemId.equals("minecraft:crossbow")) {
            return 1;
        }
        if (itemId.equals("minecraft:trident") || itemId.equals("minecraft:mace")) {
            return 2;
        }
        if (!itemId.endsWith("_sword") && !itemId.endsWith("_axe")) {
            return 0;
        }
        if (itemId.startsWith("minecraft:netherite_")) {
            return 3;
        }
        if (itemId.startsWith("minecraft:diamond_")) {
            return 2;
        }
        return itemId.startsWith("minecraft:iron_") ? 1 : 0;
    }

    public static int weaponTier(ItemStack stack) {
        if (stack.getItem() instanceof CombatWeaponItem item) {
            WeaponDef def = item.weapon(false);
            return GearTier.of(stack, def == null ? 1 : def.tier());
        }
        return stack.isEmpty() ? 0 : vanillaTier(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** {@code p}'s loadout; {@code weaponTier} is the tier that has dealt most of their damage, or -1 for the hand's. */
    public static Loadout of(Player p, int weaponTier) {
        int tier = weaponTier >= 0 ? weaponTier : weaponTier(p.getMainHandItem());
        return new Loadout(tier, p.getArmorValue(), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                p.getAttributeValue(ModAttributes.RESILIENCE), Attunements.of(p).level());
    }
}
