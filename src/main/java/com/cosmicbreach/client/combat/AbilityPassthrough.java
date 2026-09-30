package com.cosmicbreach.client.combat;

import com.cosmicbreach.registry.ModBlockTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Right click with a combat weapon fires its ability, except on a block a right click is for: with
 * the crosshair on such a block within reach, vanilla handles the click (the door opens, the chest
 * shows its inventory) and the ability doesn't fire. Such a block has a menu (its block entity is a
 * {@link MenuProvider}, or the block offers one, like a crafting table) or is in
 * {@code #cosmicbreach:ability_passthrough} (doors, trapdoors, gates, buttons, levers, beds, bells).
 * Sneaking skips block interactions in vanilla, so a sneaking right click fires the ability anyway.
 *
 * <p>Both the vanilla click ({@link CombatInput#onInteraction}) and the ability's press edge (which
 * feeds the local machine and the server alike) ask this, from the same crosshair, in the same tick.
 */
public final class AbilityPassthrough {
    private AbilityPassthrough() {
    }

    /** True if a right click now is for the block under the crosshair. */
    public static boolean targetsInteractiveBlock(Minecraft mc, LocalPlayer player) {
        if (player.isSecondaryUseActive() || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        return isInteractive(mc.level, pos, mc.level.getBlockState(pos));
    }

    public static boolean isInteractive(Level level, BlockPos pos, BlockState state) {
        return state.is(ModBlockTags.ABILITY_PASSTHROUGH)
                || level.getBlockEntity(pos) instanceof MenuProvider
                || state.getMenuProvider(level, pos) != null;
    }
}
