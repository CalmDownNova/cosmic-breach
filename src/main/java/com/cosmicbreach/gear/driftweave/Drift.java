package com.cosmicbreach.gear.driftweave;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetAbility;
import com.cosmicbreach.net.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Drift (GDD 5.1), the Driftweave's set ability: 28 s cooldown, no Resonance. For 100 ticks the wearer falls at
 * 0.4x gravity (its own, on top of wherever it is: in the Drift that makes 0.16x), jumps 1.5 times as high, dashes
 * in the air for free and hits 20% harder off the ground ({@link Driftweave#AERIAL}).
 *
 * <p>The gravity and the jump are transient attribute modifiers, which the game syncs to the client that moves the
 * player. Every server tick {@link #onPlayerTick} puts them on while Drift runs and takes them off when it ends
 * (also after the set came off, a relog or anything else), so they never outlive it. The free air dashes read
 * the set state's active window on both sides.
 */
public final class Drift implements SetAbility {
    public static final ResourceLocation GRAVITY_ID = CosmicBreach.id("drift_gravity");
    public static final ResourceLocation JUMP_ID = CosmicBreach.id("drift_jump");

    @Override
    public int baseCooldown() {
        return DriftweaveRules.DRIFT_COOLDOWN;
    }

    @Override
    public boolean cast(ServerPlayer player, long now) {
        ArmorSets.setState(player, ArmorSets.state(player).withActive(Driftweave.SET.id(), now + DriftweaveRules.DRIFT_TICKS));
        apply(player, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GearSets.DRIFT_START.get(), SoundSource.PLAYERS,
                1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, SetFxPayload.at(SetFxPayload.Kind.DRIFT_START, player.getId(), player.position(),
                DriftweaveRules.DRIFT_TICKS));
        return true;
    }

    /** True while {@code player}'s Drift runs (either side). */
    public static boolean active(@Nullable Player player) {
        return player != null && ArmorSets.state(player).active(Driftweave.SET.id(), player.level().getGameTime());
    }

    /** Every server tick, for every player: the modifiers on while Drift runs, off (with its end) once it stops. */
    public static void onPlayerTick(ServerPlayer player) {
        boolean active = active(player) && player.isAlive();
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        boolean on = gravity != null && gravity.hasModifier(GRAVITY_ID);
        if (active != on) {
            apply(player, active);
            if (!active) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GearSets.DRIFT_END.get(),
                        SoundSource.PLAYERS, 0.9f, 1.0f);
                ModNetworking.sendToTrackersAndSelf(player, SetFxPayload.at(SetFxPayload.Kind.DRIFT_END, player.getId(),
                        player.position(), 0));
            }
        }
    }

    private static void apply(ServerPlayer player, boolean on) {
        set(player.getAttribute(Attributes.GRAVITY), GRAVITY_ID, on ? DriftweaveRules.DRIFT_GRAVITY - 1.0 : 0.0);
        set(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP_ID, on ? DriftweaveRules.driftJumpMultiplier() - 1.0 : 0.0);
        if (on) {
            player.resetFallDistance();
        }
    }

    private static void set(@Nullable AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) {
            return;
        }
        if (amount == 0.0) {
            if (attribute.hasModifier(id)) {
                attribute.removeModifier(id);
            }
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Where Drift's star dust starts (the feet), for the effects. */
    static Vec3 feet(ServerPlayer player) {
        return player.position();
    }
}
