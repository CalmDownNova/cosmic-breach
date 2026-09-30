package com.cosmicbreach.gear.set;

import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.progression.ProgressionStats;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Every armor set, and what a player wears of them. Both sides. */
public final class ArmorSets {
    public static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private static final Map<ResourceLocation, ArmorSet> SETS = new LinkedHashMap<>();

    private ArmorSets() {
    }

    public static ArmorSet register(ArmorSet set) {
        if (SETS.putIfAbsent(set.id(), set) != null) {
            throw new IllegalStateException("armor set " + set.id() + " registered twice");
        }
        return set;
    }

    public static Collection<ArmorSet> all() {
        return Collections.unmodifiableCollection(SETS.values());
    }

    public static @Nullable ArmorSet byId(ResourceLocation id) {
        return SETS.get(id);
    }

    /** The set a stack is a piece of, or null. */
    public static @Nullable ArmorSet setOf(ItemStack stack) {
        return stack.getItem() instanceof SetArmorItem piece ? piece.set() : null;
    }

    /** How many pieces of {@code set} the entity wears. */
    public static int pieces(LivingEntity entity, ArmorSet set) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (setOf(entity.getItemBySlot(slot)) == set) {
                n++;
            }
        }
        return n;
    }

    /** Every set the entity wears a piece of, with how many pieces, in slot order. */
    public static Map<ArmorSet, Integer> worn(LivingEntity entity) {
        Map<ArmorSet, Integer> worn = new LinkedHashMap<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ArmorSet set = setOf(entity.getItemBySlot(slot));
            if (set != null) {
                worn.merge(set, 1, Integer::sum);
            }
        }
        return worn;
    }

    /** The set whose ability the entity can cast now (enough pieces worn), or null. */
    public static @Nullable ArmorSet withAbility(LivingEntity entity) {
        for (Map.Entry<ArmorSet, Integer> e : worn(entity).entrySet()) {
            SetAbility ability = e.getKey().ability().orElse(null);
            if (ability != null && e.getValue() >= ability.piecesRequired()) {
                return e.getKey();
            }
        }
        return null;
    }

    public static SetState state(Player player) {
        return player.getData(GearRegistry.SET_STATE);
    }

    public static void setState(Player player, SetState state) {
        if (!state.equals(state(player))) {
            player.setData(GearRegistry.SET_STATE, state);
        }
    }

    /**
     * The ability's cooldown for this player: base x 100 / (100 + Haste), Haste = 1.5 x Arcane plus gear and buffs
     * (GDD 3.4; the Hymn of Alignment's +30 comes through {@link CombatHooks#hasteBonus}).
     */
    public static int cooldownTicks(Player player, SetAbility ability) {
        return cooldownTicks(ability.baseCooldown(), ProgressionStats.of(player).arcane(), CombatHooks.hasteBonus(player));
    }

    public static int cooldownTicks(int baseTicks, int arcane) {
        return cooldownTicks(baseTicks, arcane, 0.0);
    }

    public static int cooldownTicks(int baseTicks, int arcane, double gearHaste) {
        return (int) Math.ceil(CombatMath.cooldownTicks(baseTicks,
                CombatMath.haste(new StatBlock(0, 0, arcane, 0), gearHaste)));
    }

    /** Every worn set's poise bonus for the player right now. */
    public static double poiseBonus(Player player) {
        double bonus = 0.0;
        for (Map.Entry<ArmorSet, Integer> e : worn(player).entrySet()) {
            bonus += e.getKey().behavior().poiseBonus(player, e.getValue());
        }
        return bonus;
    }
}
