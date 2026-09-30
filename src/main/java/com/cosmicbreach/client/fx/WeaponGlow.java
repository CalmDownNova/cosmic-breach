package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The weapon shows Resonance (GDD pillar 4, "the world is the meter"): the item property
 * {@code cosmicbreach:resonance} is the local player's Resonance fraction on the stack in that
 * player's main hand and 0 everywhere else, and Meridian's model switches to its glow stages at
 * 0.34, 0.67 and 1.0 ({@code models/item/meridian.json}; the Comet Maul's the same way). At full Resonance the blade sheds a spark
 * every few ticks, and reaching full plays its sound.
 */
public final class WeaponGlow {
    public static final ResourceLocation PROPERTY = CosmicBreach.id("resonance");
    private static final int SPARK_EVERY_TICKS = 2;

    private static boolean wasFull;
    private static int ticks;

    private WeaponGlow() {
    }

    /** From client setup, on the main thread: every combat weapon gets the property (its model picks the stages). */
    public static void registerItemProperties() {
        for (var holder : ModItems.ITEMS.getEntries()) {
            if (holder.get() instanceof CombatWeaponItem weapon) {
                ItemProperties.register(weapon, PROPERTY, (ClampedItemPropertyFunction) WeaponGlow::resonance);
            }
        }
    }

    /** The property: the local player's Resonance fraction while this stack is in its main hand, else 0. */
    static float resonance(ItemStack stack, @Nullable net.minecraft.client.multiplayer.ClientLevel level,
                           @Nullable LivingEntity entity, int seed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity != player || player.getMainHandItem() != stack) {
            return 0f;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        return combat == null ? 0f : fraction(combat.machine());
    }

    /** Resonance over its maximum, exactly 1 when full. */
    static float fraction(CombatStateMachine machine) {
        int max = machine.maxResonance();
        if (max <= 0) {
            return 0f;
        }
        double resonance = machine.resonance();
        return resonance >= max - 1e-6 ? 1f : (float) Math.max(0.0, Math.min(1.0, resonance / max));
    }

    /** Every tick for the local player: the sound on reaching full, and the blade's sparks while full. */
    public static void tick(LocalPlayer player, CombatStateMachine machine) {
        boolean full = fraction(machine) >= 1f;
        if (full && !wasFull) {
            CombatAudio.playOwn(player, ModSounds.RESONANCE_FULL, 0.9f, 1.0f);
        }
        wasFull = full;
        ticks++;
        if (full && CombatWeaponItem.isCombatWeapon(player.getMainHandItem()) && ticks % SPARK_EVERY_TICKS == 0) {
            CombatEffects.bladeSpark(player);
        }
    }

    /** A new local player: take its Resonance as it is, without a sound. */
    public static void reset(boolean full) {
        wasFull = full;
    }
}
