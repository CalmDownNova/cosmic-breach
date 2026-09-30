package com.cosmicbreach.client.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.colossus.ColossusMoves;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Prism Colossus through GeckoLib, drawn twice its built size, then its light added over it full bright
 * ({@link GuardianGlowLayer}): the core and the eye white-hot, the gold veins and rings catching light, the cracks of
 * phase 2; and while a Prism Burst builds, white light over the whole body, growing to white-hot. While drawing it
 * keeps where a few bones' pivots landed in the world (the eye, the chest core, the wrist rings), for the effects
 * that sit on them: the eye's charge, the core's halo and motes, the tethers of light to a flying fist.
 */
public class ColossusRenderer extends GeoEntityRenderer<PrismColossus> {
    static final ResourceLocation GLOW = CosmicBreach.id("textures/entity/prism_colossus_glowmask.png");
    static final ResourceLocation GLOW_DORMANT = CosmicBreach.id("textures/entity/prism_colossus_dormant_glowmask.png");
    static final ResourceLocation GLOW_CRACKED = CosmicBreach.id("textures/entity/prism_colossus_cracked_glowmask.png");
    static final String[] TRACKED = {"eye", "chest_core", "wrist_ring_right", "wrist_ring_left"};

    /** Where the tracked bones were when the Colossus was last drawn (camera-relative matrices turned to world points). */
    public record Points(Map<String, Vec3> at, long frame) {
    }

    private static final Map<PrismColossus, Points> LAST = new WeakHashMap<>();
    private static long frame;
    private final Map<String, Matrix4f> poses = new HashMap<>();

    public ColossusRenderer(EntityRendererProvider.Context context) {
        super(context, new ColossusModel());
        addRenderLayer(new GuardianGlowLayer<>(this, ColossusRenderer::glowTexture, c -> 0xFFFFFF));
        addRenderLayer(new GuardianGlowLayer<>(this, c -> c.glowing() ? ColossusModel.WHITE : null, ColossusRenderer::burstLight));
        withScale(PrismColossus.SCALE);
        this.shadowRadius = 2.2f;
    }

    /**
     * No red hurt tint: a giant hit several times a second would stay red through a whole Break. Its hits show in
     * the sparks, the sounds and the bar instead.
     */
    @Override
    public int getPackedOverlay(PrismColossus animatable, float u, float partialTick) {
        return OverlayTexture.NO_OVERLAY;
    }

    @Override
    public void preRender(PoseStack poseStack, PrismColossus animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            poses.clear();
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        for (String name : TRACKED) {
            if (name.equals(bone.getName()) && !poses.containsKey(name)) {
                poses.put(name, new Matrix4f(poseStack.last().pose()));
            }
        }
    }

    @Override
    public void renderFinal(PoseStack poseStack, PrismColossus animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        Map<String, Vec3> at = new HashMap<>();
        for (Map.Entry<String, Matrix4f> e : poses.entrySet()) {
            GeoBone bone = model.getBone(e.getKey()).orElse(null);
            if (bone == null) {
                continue;
            }
            Vector3f pivot = new Vector3f(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
            Vector3f p = e.getValue().transformPosition(pivot);
            at.put(e.getKey(), new Vec3(camera.x + p.x, camera.y + p.y, camera.z + p.z));
        }
        LAST.put(animatable, new Points(at, ++frame));
        poses.clear();
    }

    private static ResourceLocation glowTexture(PrismColossus c) {
        if (c.state() == PrismColossus.State.DORMANT) {
            return GLOW_DORMANT;
        }
        return c.phaseTwo() ? GLOW_CRACKED : GLOW;
    }

    /**
     * The Prism Burst's light over the body (grey level of the white texture added): from a third to nearly all of
     * white across the tell, with a quickening flicker, so the whole Colossus goes white-hot before it bursts.
     */
    static int burstLight(PrismColossus c) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double t = c.level().getGameTime() + partial - c.actionStart();
        double u = Math.max(0.0, Math.min(1.0, t / ColossusMoves.BURST_TELL));
        double flicker = 0.06 * Math.sin(t * (1.2 + 1.6 * u));
        int level = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, 0.35 + 0.6 * u + flicker)));
        return (level << 16) | (level << 8) | level;
    }

    /** The world point of a tracked bone of {@code colossus} from its last drawing, or null. */
    public static @Nullable Vec3 point(PrismColossus colossus, String bone) {
        Points p = LAST.get(colossus);
        return p == null ? null : p.at().get(bone);
    }
}
