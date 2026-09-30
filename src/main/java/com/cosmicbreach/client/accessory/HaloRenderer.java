package com.cosmicbreach.client.accessory;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.accessory.Accessories;
import com.cosmicbreach.accessory.AccessoryRegistry;
import com.cosmicbreach.accessory.AccessoryRules;
import com.cosmicbreach.accessory.HaloState;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Halo of Nine's shards on every player who wears it (their {@link HaloState} is synced to whoever sees them): each
 * shard still there is a small crystal (two crossed planes of {@code textures/fx/halo_shard.png}, turning) with a soft
 * gold glow, on its orbit round the wearer ({@link AccessoryRules#shardOffset}: radius 1.6, one turn a second), at the
 * game time plus the partial tick so they move smoothly. From the wearer's own eyes they are fainter. Client only.
 */
public final class HaloRenderer {
    static final ResourceLocation SHARD = CosmicBreach.id("textures/fx/halo_shard.png");
    private static final double RANGE = 64.0;
    private static final float WIDTH = 0.2f;
    private static final float HEIGHT = 0.42f;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 15));
    /** Shards drawn in the last frame (the scenario reads it). */
    private static int drawn;

    private record Shard(Vec3 at, float spin, float alpha) {
    }

    private HaloRenderer() {
    }

    public static int drawnLastFrame() {
        return drawn;
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            drawn = 0;
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        long now = level.getGameTime();
        double time = now + partial;
        List<Shard> shards = new ArrayList<>();
        for (Player player : level.players()) {
            HaloState state = player.getData(AccessoryRegistry.HALO);
            if (!state.worn() || player.isInvisible() || player.position().distanceToSqr(eye) > RANGE * RANGE) {
                continue;
            }
            boolean own = WorldFx.firstPersonOf(player);
            Vec3 body = player.getPosition(partial);
            for (int i = 0; i < AccessoryRules.HALO_SHARDS; i++) {
                if (!state.alive(i, now)) {
                    continue;
                }
                double[] o = AccessoryRules.shardOffset(i, time);
                Vec3 at = body.add(o[0], AccessoryRules.HALO_HEIGHT, o[1]);
                shards.add(new Shard(at, (float) (time * 0.35 + i * 2.1), own ? 0.55f : 1.0f));
            }
        }
        drawn = shards.size();
        if (shards.isEmpty()) {
            return;
        }
        VertexConsumer body = BUFFERS.getBuffer(FxRenderTypes.shade(SHARD));
        for (Shard s : shards) {
            Vec3 rel = s.at.subtract(eye);
            crossed(body, rel, s.spin, s.alpha);
        }
        BUFFERS.endBatch();
        VertexConsumer glow = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        float[] c = WorldFx.rgb(AccessoryFx.GOLD);
        for (Shard s : shards) {
            WorldFx.billboard(glow, camera, s.at.subtract(eye), 0.32f, c[0], c[1], c[2], 0.55f * s.alpha);
        }
        BUFFERS.endBatch();
        RenderSystem.defaultBlendFunc();
    }

    /** Two upright planes crossed at right angles, turned {@code spin} radians about the vertical. */
    private static void crossed(VertexConsumer out, Vec3 at, float spin, float alpha) {
        for (int k = 0; k < 2; k++) {
            double a = spin + k * Math.PI / 2;
            double dx = Math.cos(a) * WIDTH / 2;
            double dz = Math.sin(a) * WIDTH / 2;
            double h = HEIGHT / 2;
            WorldFx.vertex(out, at.add(-dx, -h, -dz), 0f, 1f, 1f, 1f, 1f, alpha);
            WorldFx.vertex(out, at.add(-dx, h, -dz), 0f, 0f, 1f, 1f, 1f, alpha);
            WorldFx.vertex(out, at.add(dx, h, dz), 1f, 0f, 1f, 1f, 1f, alpha);
            WorldFx.vertex(out, at.add(dx, -h, dz), 1f, 1f, 1f, 1f, 1f, alpha);
        }
    }

    /** Where shard {@code i} of {@code player}'s halo is now, as the server places it (the scenario compares). */
    static Vec3 serverShard(Player player, int i) {
        return Accessories.shardAt(player, i, player.level().getGameTime());
    }
}
