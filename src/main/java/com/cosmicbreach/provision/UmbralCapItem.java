package com.cosmicbreach.provision;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * The Umbral Cap as an item (1.1). A food that is also a block item eats only when it cannot be planted: vanilla's
 * {@code BlockItem.useOn} places first. Nearly every floor of the Deep is cap soil (the boss floor of level 4 included), so a
 * hungry player who right clicked toward the floor, mid fight, planted the cap and healed nothing.
 *
 * <p>Here a hungry player who is not sneaking eats on a right click, whatever the cursor is on. Sneak to plant it on basalt;
 * a player who is not hungry plants it as before (and eats it when it cannot be planted, as every block food does).
 */
public class UmbralCapItem extends ItemNameBlockItem {
    public UmbralCapItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Eat first (instead of planting) when the player is hungry and not sneaking. */
    public static boolean eatsFirst(boolean sneaking, boolean hungry) {
        return hungry && !sneaking;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && eatsFirst(player.isSecondaryUseActive(), player.getFoodData().needsFood())) {
            // what BlockItem.useOn does when the cap cannot be planted: the item's own use, which starts the eating
            InteractionResult eating = use(context.getLevel(), player, context.getHand()).getResult();
            return eating == InteractionResult.CONSUME ? InteractionResult.CONSUME_PARTIAL : eating;
        }
        return super.useOn(context);
    }
}
