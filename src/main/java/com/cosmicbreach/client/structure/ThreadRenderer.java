package com.cosmicbreach.client.structure;

import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlockEntity;
import com.cosmicbreach.structure.trap.KineticRules;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Kinetic Tripwire's thread (GDD 6.4): near-invisible, drawn only to a careful player (sneaking or walking, not
 * sprinting or dashing) within {@link KineticRules#REVEAL_RADIUS} blocks, as a hair-thin pale line that fades
 * in as you come closer. Records which threads it showed this frame, so the client can play the thread's
 * shimmer when one appears ({@link StructuresClient}).
 */
public class ThreadRenderer implements BlockEntityRenderer<KineticEmitterBlockEntity> {
    /** Threads shown recently: emitter position to the game time it was last drawn. */
    static final Map<BlockPos, Long> SHOWN = new HashMap<>();

    /** The game time the thread of the emitter at {@code pos} was last shown, or null (tests). */
    public static Long shownAt(BlockPos pos) {
        return SHOWN.get(pos);
    }

    @Override
    public void render(KineticEmitterBlockEntity emitter, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || emitter.length() <= 0 || emitter.getLevel() == null) {
            return;
        }
        Direction along = emitter.getBlockState().getValue(KineticEmitterBlock.FACING);
        double closeness = revealed(emitter, along, player);
        if (closeness <= 0) {
            return;
        }
        SHOWN.put(emitter.getBlockPos(), emitter.getLevel().getGameTime());
        Vec3 from = new Vec3(0.5 + along.getStepX() * 0.5, 0.35, 0.5 + along.getStepZ() * 0.5);
        Vec3 to = from.add(along.getStepX() * emitter.length(), 0, along.getStepZ() * emitter.length());
        int alpha = (int) (40 + 150 * closeness);
        Beams.draw(pose, buffers, from, to, 0xCFF8FF, 0.008f, 0.03f, alpha, 0);
    }

    /** 0 if the thread is hidden from {@code player}, else up to 1 as they come closer. */
    static double revealed(KineticEmitterBlockEntity emitter, Direction along, LocalPlayer player) {
        double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
        if (!KineticRules.careful(player.isSprinting(), speed) && !com.cosmicbreach.accessory.TrapSight.anySpeed(player)) {
            return 0;
        }
        BlockPos p = emitter.getBlockPos();
        Vec3 a = Vec3.atBottomCenterOf(p).add(along.getStepX(), 0.35, along.getStepZ());
        Vec3 b = a.add(along.getStepX() * (emitter.length() - 1), 0, along.getStepZ() * (emitter.length() - 1));
        Vec3 feet = player.position();
        double t = segmentParam(a, b, feet);
        Vec3 nearest = a.add(b.subtract(a).scale(t));
        double dist = nearest.distanceTo(feet.add(0, 0.35, 0));
        double reach = com.cosmicbreach.accessory.TrapSight.radius(player, KineticRules.REVEAL_RADIUS); // 8 with the Sunshard Compass
        return dist > reach ? 0 : 1.0 - dist / reach;
    }

    private static double segmentParam(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        return len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
    }

    @Override
    public boolean shouldRenderOffScreen(KineticEmitterBlockEntity emitter) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(KineticEmitterBlockEntity emitter) {
        Direction along = emitter.getBlockState().getValue(KineticEmitterBlock.FACING);
        BlockPos p = emitter.getBlockPos();
        return new AABB(p).expandTowards(along.getStepX() * (emitter.length() + 1), 1, along.getStepZ() * (emitter.length() + 1));
    }
}
