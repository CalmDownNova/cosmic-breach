package com.cosmicbreach.client.shrine;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.shrine.ShrineBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a shrine (GeckoLib, turned by its facing) with its glowmask added unshaded and full bright, as every guardian's
 * glow is ({@link GuardianGlowLayer}). Seen from 128 blocks; its render box covers a model up to three blocks tall and
 * a block beyond its cell each way.
 */
public final class ShrineRenderer extends GeoBlockRenderer<ShrineBlockEntity> {
    public ShrineRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShrineModel());
        addRenderLayer(new GuardianGlowLayer<>(this,
                shrine -> CosmicBreach.id("textures/block/shrine_" + shrine.kind().id() + "_glowmask.png"), shrine -> 0xFFFFFF));
    }

    @Override
    public AABB getRenderBoundingBox(ShrineBlockEntity shrine) {
        BlockPos p = shrine.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 3, p.getZ() + 2);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
