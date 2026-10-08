package com.cosmicbreach.world;

import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Drops that would fall into the void go to the killer instead (1.1): a mob a player kills in Aetheria with nothing solid
 * under it for {@value #VOID_DEPTH} blocks (a Gyre Knight cut down over a lane, a Drift Manta over open sky) puts its drops
 * in the killer's inventory (what does not fit lands at their feet) and its experience straight into them. With ground
 * below, drops fall as ever. Projectiles and familiars count as their owner's kill (a damage source's causing entity).
 * Only mobs are handled: a player's own drops are never moved, so this never meets the shrines' keeping of a player's pack.
 */
public final class VoidSafeDrops {
    /** Further down than this, nobody is fetching what fell. */
    public static final int VOID_DEPTH = 64;

    private VoidSafeDrops() {
    }

    public static void register(IEventBus game) {
        game.addListener(LivingDropsEvent.class, VoidSafeDrops::onDrops);
        game.addListener(LivingExperienceDropEvent.class, VoidSafeDrops::onExperience);
    }

    /** Pure: whether a kill's drops go to the killer ({@code groundDepth}: blocks down to solid ground, -1 for none). */
    public static boolean toKiller(boolean inAetheria, boolean byPlayer, boolean victimIsPlayer, int groundDepth) {
        return inAetheria && byPlayer && !victimIsPlayer && groundDepth < 0;
    }

    /** Pure: the first depth from 0 to {@code limit} (both counted) at which {@code solid} holds, or -1 if there is none. */
    static int firstSolid(IntPredicate solid, int limit) {
        for (int d = 0; d <= limit; d++) {
            if (solid.test(d)) {
                return d;
            }
        }
        return -1;
    }

    /** Blocks from {@code pos} down to the first block with a collision shape, or -1 if none within {@value #VOID_DEPTH}. */
    public static int groundDepth(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int limit = Math.min(VOID_DEPTH, pos.getY() - level.getMinBuildHeight());
        return firstSolid(d -> {
            p.set(pos.getX(), pos.getY() - d, pos.getZ());
            return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
        }, limit);
    }

    private static boolean voidBound(LivingEntity dead) {
        return toKiller(AetheriaWorld.is(dead.level()), true, dead instanceof Player, groundDepth(dead.level(), dead.blockPosition()));
    }

    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !voidBound(event.getEntity())) {
            return;
        }
        for (ItemEntity item : event.getDrops()) {
            ItemHandlerHelper.giveItemToPlayer(killer, item.getItem().copy());
        }
        event.getDrops().clear();
    }

    private static void onExperience(LivingExperienceDropEvent event) {
        if (!(event.getAttackingPlayer() instanceof ServerPlayer killer) || event.getDroppedExperience() <= 0 || !voidBound(event.getEntity())) {
            return;
        }
        killer.giveExperiencePoints(event.getDroppedExperience());
        event.setDroppedExperience(0);
    }
}
