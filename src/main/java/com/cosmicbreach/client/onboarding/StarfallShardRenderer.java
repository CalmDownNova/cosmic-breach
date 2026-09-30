package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.onboarding.StarfallShardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The fallen shard's light pillar: vanilla's beacon beam in the shard's pale gold, from just above the
 * crystal to the top of the sky, for five minutes after it fell. Like the beacon's renderer it draws to 256
 * blocks ({@link #getViewDistance}, measured flat, as {@code BeaconRenderer.shouldRender} does), is never
 * culled with the block's own section ({@link #shouldRenderOffScreen}), and has a render box as tall as the
 * beam so frustum culling keeps it while any of it is on screen. Past {@value #NEAR} blocks it widens with distance
 * (up to {@value #MAX_WIDTH_SCALE} times), so it keeps the width it has there on screen instead of thinning to a line
 * at the 48 to 96 blocks a Starfall lands from its player.
 */
public class StarfallShardRenderer implements BlockEntityRenderer<StarfallShardBlockEntity> {
    /** Pale gold, a little warmer than a white beacon. */
    public static final int COLOR = 0xFFFFEBB4;
    public static final int VIEW_DISTANCE = 256;
    /** The beam's and the glow's radii up close (a beacon's are 0.2 and 0.25). */
    public static final float BEAM_RADIUS = 0.3f;
    public static final float GLOW_RADIUS = 0.5f;
    /** Past this flat distance the pillar widens in proportion. */
    public static final double NEAR = 24.0;
    /** It widens no more than this. */
    public static final float MAX_WIDTH_SCALE = 8.0f;

    @Override
    public void render(StarfallShardBlockEntity shard, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (shard.getLevel() == null) {
            return;
        }
        long time = shard.getLevel().getGameTime();
        if (!shard.pillarAt(time)) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos p = shard.getBlockPos();
        float grow = widthScale(Math.hypot(camera.x - (p.getX() + 0.5), camera.z - (p.getZ() + 0.5)));
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, time, 1,
                BeaconRenderer.MAX_RENDER_Y, COLOR, BEAM_RADIUS * grow, GLOW_RADIUS * grow);
    }

    /** How many times its close width the pillar draws at this flat distance: 1 within {@link #NEAR}, then in proportion, capped. Pure. */
    public static float widthScale(double distance) {
        return (float) Math.min(MAX_WIDTH_SCALE, Math.max(1.0, distance / NEAR));
    }

    @Override
    public boolean shouldRenderOffScreen(StarfallShardBlockEntity shard) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRender(StarfallShardBlockEntity shard, Vec3 camera) {
        return Vec3.atCenterOf(shard.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(camera.multiply(1.0, 0.0, 1.0), VIEW_DISTANCE);
    }

    @Override
    public AABB getRenderBoundingBox(StarfallShardBlockEntity shard) {
        var p = shard.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, BeaconRenderer.MAX_RENDER_Y, p.getZ() + 1.0);
    }
}
