package com.cosmicbreach.progression;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.progression.net.LevelUpPayload;
import com.cosmicbreach.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and changes a player's {@link Attunement} on the server, with everything a change brings:
 * the attributes (allocated points as base values; move speed and max health from the effective
 * stats), the level-up chime, burst and message, and the free Reverie Draught at level 10. Setting
 * the attachment syncs it to the player's client.
 */
public final class Attunements {
    /** +0.4% move speed per effective Agility point, on the player's movement speed. */
    public static final ResourceLocation AGILITY_SPEED = CosmicBreach.id("agility_speed");
    /** +0.4 max health per effective Resilience point. */
    public static final ResourceLocation RESILIENCE_HEALTH = CosmicBreach.id("resilience_health");

    private Attunements() {
    }

    /** The player's Attunement, on either side (the client has its own player's copy). */
    public static Attunement of(Player player) {
        return player.getData(ProgressionRegistry.ATTUNEMENT);
    }

    /**
     * Stores a new state, syncs it and applies it to the attributes. Levels gained play their
     * feedback. The free Reverie Draught goes into the inventory once level 10 is reached (a dead
     * player gets it on respawn, see {@link #refresh}).
     */
    public static void set(ServerPlayer player, Attunement next) {
        Attunement before = of(player);
        if (next.freeDraughtDue() && player.isAlive()) {
            next = next.withFreeDraughtGiven();
            giveFreeDraught(player);
        }
        player.setData(ProgressionRegistry.ATTUNEMENT, next);
        apply(player);
        int gained = next.level() - before.level();
        if (gained > 0) {
            levelUpFeedback(player, next.level(), gained);
        }
    }

    /** On login and respawn: the attributes from the saved state, and a free draught still owed. */
    public static void refresh(ServerPlayer player) {
        Attunement state = of(player);
        if (state.freeDraughtDue() && player.isAlive()) {
            set(player, state);
        } else {
            apply(player);
        }
    }

    /**
     * Brings the attributes in line with the state and the effective stats: the four base values are
     * the spent points, and the move speed and max health modifiers follow the effective Agility and
     * Resilience (gear included). Only what differs is touched, so calling it every tick is cheap.
     */
    public static void apply(ServerPlayer player) {
        Attunement state = of(player);
        for (Stat stat : Stat.values()) {
            AttributeInstance instance = player.getAttribute(ModAttributes.of(stat));
            if (instance != null) {
                instance.setBaseValue(state.spent().get(stat));
            }
        }
        StatBlock stats = ProgressionStats.of(player);
        modifier(player, Attributes.MOVEMENT_SPEED, AGILITY_SPEED, CombatMath.moveSpeedBonus(stats),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.MAX_HEALTH, RESILIENCE_HEALTH, CombatMath.maxHealthBonus(stats),
                AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Sets a saved modifier to {@code amount} (removed at 0). Saved, not transient, so a player who
     * logs in with more than 20 health keeps it: the modifiers load before the health does.
     */
    private static void modifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == operation) {
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    private static void levelUpFeedback(ServerPlayer player, int level, int gained) {
        player.level().playSound(null, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                ProgressionRegistry.LEVEL_UP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new LevelUpPayload(player.getId(), level, gained));
    }

    private static void giveFreeDraught(ServerPlayer player) {
        ItemStack stack = new ItemStack(ProgressionRegistry.REVERIE_DRAUGHT.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("message.cosmicbreach.attunement.free_draught")
                .withStyle(ChatFormatting.GOLD));
    }
}
