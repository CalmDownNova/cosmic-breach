package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.StructureConfig;
import com.cosmicbreach.structure.StructureRegistry;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A loose mirror or filter lifted off a Lens Array's grid. Use it on an empty pedestal of the same room to set it
 * down ({@link LensArrays#onRightClickBlock}). It belongs to its room: carried out of range, into another
 * dimension, thrown away or dropped on death, it goes back on the grid, and a stale one (its room already took
 * it back) crumbles.
 */
public class LensPieceItem extends Item {
    private final LensPiece.Kind kind;

    public LensPieceItem(Properties properties, LensPiece.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public LensPiece.Kind kind() {
        return kind;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || level.getGameTime() % 10 != 0) {
            return;
        }
        LensPiece piece = stack.get(StructureRegistry.LENS_PIECE.get());
        if (piece == null) {
            stack.setCount(0);
            return;
        }
        boolean far = piece.dimension() != level.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(piece.core())) > StructureConfig.CARRY_RANGE * StructureConfig.CARRY_RANGE;
        ServerLevel home = server.getServer().getLevel(piece.dimension());
        boolean loaded = home != null && home.isLoaded(piece.core());
        LensCoreBlockEntity core = loaded && home.getBlockEntity(piece.core()) instanceof LensCoreBlockEntity c ? c : null;
        if (far) {
            if (core != null) {
                core.returnPiece(home, piece.token());
            }
            player.displayClientMessage(Component.translatable("cosmicbreach.lens.piece_returned"), true);
            stack.setCount(0);
        } else if (core != null && !core.owns(piece)) {
            stack.setCount(0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cosmicbreach.loose_piece.tooltip"));
    }
}
