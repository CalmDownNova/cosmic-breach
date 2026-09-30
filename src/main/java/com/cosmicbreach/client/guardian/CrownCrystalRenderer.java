package com.cosmicbreach.client.guardian;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.CrownCrystalBlock;
import com.cosmicbreach.guardian.colossus.CrownCrystalBlockEntity;
import com.cosmicbreach.guardian.colossus.Refraction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A crown crystal's pointer: a flat arrow of light on its top pointing at its target (another crystal, or the
 * Colossus's core), so a player can read where a beam will bounce. Pale turquoise at rest, gold while the crystal
 * is lit (it can be turned), white when it points at the core.
 */
public class CrownCrystalRenderer implements BlockEntityRenderer<CrownCrystalBlockEntity> {
    public CrownCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrownCrystalBlockEntity crystal, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
                       int overlay) {
        CrownArena arena = crystal.arena();
        if (crystal.arenaCentre().equals(net.minecraft.core.BlockPos.ZERO)) {
            return;
        }
        boolean lit = crystal.getBlockState().getValue(CrownCrystalBlock.LIT);
        int target = crystal.target();
        Vec3 from = arena.crystalPoint(crystal.index());
        Vec3 to = target == Refraction.CORE ? arena.centre() : arena.crystalPoint(target);
        Vec3 dir = new Vec3(to.x - from.x, 0, to.z - from.z);
        if (dir.lengthSqr() < 1e-6) {
            return;
        }
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        // local to the block entity: the crystal's axis is at (1, 0, 1) from its controller's corner
        Vec3 top = new Vec3(1.0, CrownArena.CRYSTAL_HEIGHT + 0.03, 1.0);
        float[] c = target == Refraction.CORE ? new float[] {1f, 1f, 1f} : lit ? new float[] {1f, 0.76f, 0.23f} : new float[] {0.6f, 0.95f, 0.9f};
        float alpha = lit ? 0.95f : 0.55f;
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer out = buffers.getBuffer(ShardDraw.solid());
        Vec3 tip = top.add(dir.scale(0.95));
        Vec3 back = top.subtract(dir.scale(0.55));
        Vec3 wing = side.scale(0.42);
        Vec3 neck = top.add(dir.scale(0.25));
        // an arrowhead (two triangles as a quad each) and a shaft
        ShardDraw.vertex(out, pose, tip, 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, neck.add(wing), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, neck, 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, neck.subtract(wing), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        Vec3 shaft = side.scale(0.13);
        ShardDraw.vertex(out, pose, neck.add(shaft), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, back.add(shaft), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, back.subtract(shaft), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
        ShardDraw.vertex(out, pose, neck.subtract(shaft), 0.5f, 0.5f, c[0], c[1], c[2], alpha);
    }

    @Override
    public AABB getRenderBoundingBox(CrownCrystalBlockEntity crystal) {
        return new AABB(crystal.getBlockPos()).expandTowards(2, CrownArena.CRYSTAL_HEIGHT + 1, 2).inflate(0.5);
    }
}
