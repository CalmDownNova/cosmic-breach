package com.cosmicbreach.guardian;

import com.cosmicbreach.progression.AttunementXp;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hands one participant their share of a guardian kill (the per-player rewards of GDD 7.2 and 7.3): their own
 * items into their inventory (what does not fit lands at their feet, for them alone), Attunement XP and stat points
 * through G1's API, and on a first kill the guardian's advancement, the attunement to the next layer and its line from
 * the Starfall's voice ({@link GuardianType#echo()}). Server side. Who gets paid when is {@link GuardianPayouts}.
 */
public final class GuardianRewards {
    /** Ticks the dropped rewards wait before anyone may pick them up (they land first). */
    public static final int PICKUP_DELAY = 10;

    private GuardianRewards() {
    }

    /** True if {@code player} has already killed this guardian (holds its advancement). */
    public static boolean killedBefore(ServerPlayer player, GuardianType type) {
        return hasAdvancement(player, type.advancement());
    }

    /**
     * Gives {@code player} their reward for a kill of {@code type}: the items straight into their inventory. Returns what
     * was given, for logs and tests. The player must be online and alive; {@link GuardianPayouts} holds rewards until then.
     */
    public static List<ItemStack> give(ServerPlayer player, GuardianType type, RewardTable.Reward reward) {
        List<ItemStack> given = new ArrayList<>();
        for (RewardTable.Drop drop : reward.drops()) {
            Item item = BuiltInRegistries.ITEM.get(drop.item());
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item, drop.count());
            given.add(stack.copy());
            deliver(player, stack);
        }
        if (reward.xp() > 0) {
            AttunementXp.award(player, reward.xp());
        }
        if (reward.statPoints() > 0) {
            AttunementXp.awardStatPoints(player, reward.statPoints());
        }
        if (reward.firstKill()) {
            grantAdvancement(player, type.advancement());
            if (type.attunes() != null) {
                LayerAttunement.grant(player, type.attunes());
            }
            if (type.echo() != null) {
                Echo.say(player, type.echo());
            }
        }
        return given;
    }

    /**
     * Puts {@code stack} into {@code player}'s inventory; whatever does not fit lands at their feet, and only they may
     * pick it up. The stack passed in is not changed.
     */
    public static void deliver(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = stack.copy();
        player.getInventory().add(rest);
        if (rest.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.25, player.getZ(), rest, 0.0, 0.15, 0.0);
        entity.setTarget(player.getUUID());
        entity.setPickUpDelay(PICKUP_DELAY);
        entity.setExtendedLifetime();
        player.level().addFreshEntity(entity);
    }

    public static boolean hasAdvancement(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Completes an advancement (every remaining criterion). True if anything changed. */
    public static boolean grantAdvancement(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        boolean changed = false;
        for (String criterion : remaining) {
            changed |= player.getAdvancements().award(holder, criterion);
        }
        return changed;
    }
}
