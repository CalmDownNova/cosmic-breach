package com.cosmicbreach.client.combat;

import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A weapon with a second blade in the off hand ({@link WeaponDef.OffHand}, the Binary Edges): what each hand
 * draws. While such a weapon is in the main hand the off hand shows its blade instead of whatever is in the
 * off-hand slot (the weapons are two-handed: the off hand is otherwise ignored), in third person, in the
 * animated first person and in vanilla's first-person hand ({@code ItemInHandLayerMixin},
 * {@code ItemInHandRendererOffHandMixin}). The weapon's own client code says when the off blade is away (thrown)
 * or both blades are out of the hands (released into an orbit), and the hands then draw nothing.
 */
public final class TwinBlades {
    private static final List<Predicate<Player>> AWAY = new CopyOnWriteArrayList<>();
    private static final List<Predicate<Player>> RELEASED = new CopyOnWriteArrayList<>();
    /** One stack per off-hand item, drawn by every player's off hand (it is never in an inventory). */
    private static final Map<ResourceLocation, ItemStack> STACKS = new ConcurrentHashMap<>();

    private TwinBlades() {
    }

    /** {@code rule} says when a player's off blade is away from its hand (a thrown blade). */
    public static void addAwayRule(Predicate<Player> rule) {
        AWAY.add(rule);
    }

    /** {@code rule} says when both of a player's blades are out of its hands (flung into an orbit). */
    public static void addReleasedRule(Predicate<Player> rule) {
        RELEASED.add(rule);
    }

    /** The off-hand blade of the weapon in {@code entity}'s main hand, or null if it has none. */
    public static @Nullable WeaponDef.OffHand offHandOf(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
        return weapon == null ? null : weapon.offHand().orElse(null);
    }

    /** True if {@code player}'s off blade is away from its hand. */
    public static boolean offAway(Player player) {
        for (Predicate<Player> rule : AWAY) {
            if (rule.test(player)) {
                return true;
            }
        }
        return false;
    }

    /** True if both of {@code player}'s blades are out of its hands. */
    public static boolean released(Player player) {
        if (offHandOf(player) == null) {
            return false;
        }
        for (Predicate<Player> rule : RELEASED) {
            if (rule.test(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What {@code entity}'s off hand draws in place of {@code original}: its weapon's off blade, nothing while
     * that blade is away, or {@code original} for any other weapon.
     */
    public static ItemStack offHandStack(LivingEntity entity, ItemStack original) {
        WeaponDef.OffHand off = offHandOf(entity);
        if (off == null) {
            return original;
        }
        Player player = (Player) entity;
        if (offAway(player) || released(player)) {
            return ItemStack.EMPTY;
        }
        return stackOf(off.item());
    }

    /** What {@code entity}'s main hand draws: nothing while its blades are released, else {@code original}. */
    public static ItemStack mainHandStack(LivingEntity entity, ItemStack original) {
        return entity instanceof Player player && released(player) ? ItemStack.EMPTY : original;
    }

    /** True if {@code stack} is the drawn off-hand blade of {@code entity}'s weapon. */
    public static boolean isOffBlade(LivingEntity entity, ItemStack stack) {
        WeaponDef.OffHand off = offHandOf(entity);
        return off != null && stack == STACKS.get(off.item());
    }

    /** The animation {@code player}'s main hand plays for {@code animation}: its solo version while the off blade is away. */
    public static ResourceLocation animationFor(Player player, ResourceLocation animation) {
        WeaponDef.OffHand off = offHandOf(player);
        return off != null && offAway(player) ? off.soloAnimation(animation) : animation;
    }

    private static ItemStack stackOf(ResourceLocation id) {
        return STACKS.computeIfAbsent(id, key -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }
}
