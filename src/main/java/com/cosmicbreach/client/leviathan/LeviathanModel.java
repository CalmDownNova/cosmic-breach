package com.cosmicbreach.client.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Leviathan's GeckoLib model ({@code geo/entity/thalassine_leviathan.geo.json}, from
 * {@code tools/art/gen_leviathan.py}) placed bone by bone: the head and each of the five followers is a bone of its own
 * (pivot at its middle, facing -Z as built), put at its body point and turned along the body, so the model follows the
 * head's recorded path exactly as the server's hit parts do. What keyframes can't do is here too: the jaw opens through
 * the Song of Pulling, the tail curls back through a flick's telegraph and whips, the fan droops after a parry.
 */
public class LeviathanModel extends DefaultedEntityGeoModel<ThalassineLeviathan> {
    static final ResourceLocation BASE = CosmicBreach.id("textures/entity/thalassine_leviathan.png");
    static final ResourceLocation DORMANT = CosmicBreach.id("textures/entity/thalassine_leviathan_dormant.png");
    static final String[] BONES = {"head", "seg1", "seg2", "seg3", "seg4", "tail"};

    public LeviathanModel() {
        super(CosmicBreach.id("thalassine_leviathan"), false);
    }

    @Override
    public ResourceLocation getTextureResource(ThalassineLeviathan l) {
        return l.state() == ThalassineLeviathan.State.DORMANT ? DORMANT : BASE;
    }

    @Override
    public void setCustomAnimations(ThalassineLeviathan l, long instanceId, AnimationState<ThalassineLeviathan> state) {
        super.setCustomAnimations(l, instanceId, state);
        float partial = state.getPartialTick();
        Vec3 origin = new Vec3(Mth.lerp(partial, l.xo, l.getX()), Mth.lerp(partial, l.yo, l.getY()), Mth.lerp(partial, l.zo, l.getZ()));
        for (int i = 0; i < BONES.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(BONES[i]);
            if (bone == null) {
                continue;
            }
            Vec3 local = toModel(l.renderPoint(i, partial).subtract(origin));
            bone.setPosX((float) (-local.x * 16.0));
            bone.setPosY((float) (local.y * 16.0));
            bone.setPosZ((float) (local.z * 16.0));
            Vec3 d = toModelDirection(l.renderDirection(i, partial));
            bone.setRotX((float) Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
            bone.setRotY((float) Math.atan2(-d.x, -d.z));
            bone.setRotZ(0f);
        }
        double time = l.level().getGameTime() + partial;
        double t = time - l.actionStart();
        // the jaw: open through the song's telegraph and pull, shut after a bite
        GeoBone jaw = getAnimationProcessor().getBone("jaw");
        if (jaw != null) {
            float open = 0f;
            if (l.action() == LeviathanTactics.Attack.SONG) {
                double end = LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL;
                open = (float) (Math.min(1.0, t / 12.0) * Math.min(1.0, Math.max(0.0, (end - t) / 8.0)));
                open *= 0.85f + 0.15f * (float) Math.sin(time * 0.9);
            }
            jaw.setRotX(jaw.getRotX() + open * 0.62f);
        }
        // the tail: curls back through a flick's telegraph, whips, droops when parried
        GeoBone fan = getAnimationProcessor().getBone("fan");
        if (fan != null && l.action() == LeviathanTactics.Attack.FLICK) {
            float curl;
            if (t < LeviathanMoves.FLICK_TELL) {
                double f = t / LeviathanMoves.FLICK_TELL;
                curl = (float) (-0.9 * f * f);
            } else if (l.drooping()) {
                curl = 0.55f;
            } else {
                double f = Math.min(1.0, (t - LeviathanMoves.FLICK_TELL) / 3.0);
                curl = (float) (-0.9 + 1.7 * f - 0.8 * Math.max(0.0, (t - LeviathanMoves.FLICK_TELL - 3) / 10.0));
            }
            fan.setRotX(fan.getRotX() + curl);
        }
    }

    /** A world offset from the entity to the model's own space: the renderer turns the model by 180 degrees (its body yaw is held at 0) and scales it. */
    static Vec3 toModel(Vec3 offset) {
        return new Vec3(-offset.x / LeviathanRenderer.SCALE, offset.y / LeviathanRenderer.SCALE, -offset.z / LeviathanRenderer.SCALE);
    }

    static Vec3 toModelDirection(Vec3 d) {
        return new Vec3(-d.x, d.y, -d.z);
    }
}
