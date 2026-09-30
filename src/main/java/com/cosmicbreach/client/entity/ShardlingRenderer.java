package com.cosmicbreach.client.entity;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.shardling.Shardling;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * The Shardling through GeckoLib: {@code geo/entity/shardling.geo.json} and its animations, the
 * spines and eyes lit by {@code shardling_glowmask.png}, the head turning to look. It is not drawn
 * once dead (it shattered into its shards). While drawing it keeps the poses of the spine and head
 * bones, and afterwards hands them to {@link ShardlingPoints} for the telegraph glints.
 */
public class ShardlingRenderer extends GeoEntityRenderer<Shardling> {
    static final ResourceLocation TELL_TEXTURE = CosmicBreach.id("textures/entity/shardling_tell.png");
    static final ResourceLocation SPIT_TEXTURE = CosmicBreach.id("textures/entity/shardling_spit.png");
    private final Map<String, Matrix4f> poses = new HashMap<>();

    public ShardlingRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(CosmicBreach.id("shardling"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(Shardling.SCALE);
        this.shadowRadius = 0.35f * Shardling.SCALE;
    }

    /**
     * The telegraphs are in the texture as well as the glints: through the lunge's telegraph the crest
     * spines are gold (and glow gold, since the glow layer takes its colours from this texture), through
     * the spit's the eyes and jaw line glow red.
     */
    @Override
    public ResourceLocation getTextureLocation(Shardling shardling) {
        return switch (shardling.phase()) {
            case TELL -> TELL_TEXTURE;
            case SPIT_TELL -> SPIT_TEXTURE;
            default -> super.getTextureLocation(shardling);
        };
    }

    @Override
    public void render(Shardling entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        if (entity.isDeadOrDying()) {
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void preRender(PoseStack poseStack, Shardling animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            poses.clear();
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** The pose here maps the bone's baked cube coordinates to the camera-relative frame: keep it for the points. */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        if (ShardlingPoints.tracks(bone.getName()) && !poses.containsKey(bone.getName())) {
            poses.put(bone.getName(), new Matrix4f(poseStack.last().pose()));
        }
    }

    @Override
    public void renderFinal(PoseStack poseStack, Shardling animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
        ShardlingPoints.record(animatable, model, poses, entityRenderDispatcher.camera.getPosition());
        poses.clear();
    }
}
