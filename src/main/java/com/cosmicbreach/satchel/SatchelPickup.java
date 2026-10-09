package com.cosmicbreach.satchel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * A player with a Satchel in force collects material items into it instead of the inventory: as many as fit under the
 * cap, with pickup on for that type. The item entity is reduced by exactly the amount the Satchel took; what does not
 * fit stays in the entity for the normal pickup. Server only, and only for an entity that is ready to be picked up.
 */
final class SatchelPickup {
    private SatchelPickup() {
    }

    static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        ItemEntity entity = event.getItemEntity();
        if (entity.hasPickUpDelay() || entity.isRemoved() || event.canPickup() == TriState.FALSE) {
            return;
        }
        if (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) { // reserved for another player
            return;
        }
        ItemStack stack = entity.getItem();
        if (!SatchelFilter.isMaterial(stack)) {
            return;
        }
        int before = stack.getCount();
        int taken = take(player, stack);
        if (taken <= 0) {
            return;
        }
        player.take(entity, taken);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
                (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 1.4f + 2.0f);
        if (taken >= before) {
            entity.discard();
            event.setCanPickup(TriState.FALSE);
        } else {
            // Vanilla read this very stack object before the event and adds it to the inventory next: shrink it in place, so it
            // adds only what the satchel did not take (replacing the entity's stack would leave vanilla adding the whole count).
            stack.shrink(taken);
        }
    }

    /** Deposits what fits of {@code stack} (not changed here) into the Satchel in force; returns how many it took. */
    static int take(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = SatchelFilter.id(stack);
        int[] taken = {0};
        SatchelLocator.update(player, c -> {
            if (!c.pickupOn(id)) {
                return c;
            }
            SatchelContents.Move move = c.deposit(id, stack.getCount());
            taken[0] = move.moved();
            return move.contents();
        });
        return taken[0];
    }
}
