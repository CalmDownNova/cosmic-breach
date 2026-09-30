package com.cosmicbreach.client.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Colossus's GeckoLib model ({@code geo/entity/prism_colossus.geo.json}, from {@code tools/art/gen_colossus.py})
 * with what keyframes can't do: a fist that has left its wrist is drawn as its free twin ({@code free_fist_*}, a
 * child of the root, with its own gold band) wherever the shared flight path puts it, turned along the line from its
 * shoulder so the knuckles lead and the band trails toward the arm, the arm reaching toward it; the upper body hides
 * while the Colossus is shattered. Textures follow its state: dim as a dormant statue, cracked in phase 2 (the Prism
 * Burst's white-hot light is added over the model by {@link ColossusRenderer}).
 */
public class ColossusModel extends DefaultedEntityGeoModel<PrismColossus> {
    static final ResourceLocation BASE = CosmicBreach.id("textures/entity/prism_colossus.png");
    static final ResourceLocation DORMANT = CosmicBreach.id("textures/entity/prism_colossus_dormant.png");
    static final ResourceLocation CRACKED = CosmicBreach.id("textures/entity/prism_colossus_cracked.png");
    static final ResourceLocation WHITE = CosmicBreach.id("textures/entity/prism_colossus_white.png");

    public ColossusModel() {
        super(CosmicBreach.id("prism_colossus"), false);
    }

    @Override
    public ResourceLocation getTextureResource(PrismColossus c) {
        if (c.state() == PrismColossus.State.DORMANT) {
            return DORMANT;
        }
        return c.phaseTwo() ? CRACKED : BASE;
    }

    @Override
    public void setCustomAnimations(PrismColossus c, long instanceId, AnimationState<PrismColossus> state) {
        super.setCustomAnimations(c, instanceId, state);
        boolean hidden = c.hidden();
        bone("torso").ifPresent(b -> {
            b.setHidden(hidden);
            b.setChildrenHidden(hidden);
        });
        if (hidden) {
            return;
        }
        float partial = state.getPartialTick();
        double time = c.level().getGameTime() + partial;
        float bodyYaw = Mth.rotLerp(partial, c.yBodyRotO, c.yBodyRot);
        for (boolean right : new boolean[] {true, false}) {
            String side = right ? "right" : "left";
            boolean away = c.fistMode(right) != PrismColossus.FistMode.ATTACHED;
            bone("fist_" + side).ifPresent(b -> b.setHidden(away));
            GeoBone free = getAnimationProcessor().getBone("free_fist_" + side);
            if (free == null) {
                continue;
            }
            free.setHidden(!away);
            if (!away) {
                continue;
            }
            Vec3 world = c.fistPosition(right, time);
            Vec3 local = toModel(world.subtract(c.position()), bodyYaw);
            free.setPosX((float) (-local.x * 16.0));
            free.setPosY((float) (local.y * 16.0));
            free.setPosZ((float) (local.z * 16.0));
            GeoBone shoulder = getAnimationProcessor().getBone("shoulder_" + side);
            if (shoulder != null) {
                float[] angles = aim(shoulder, local);
                if (angles != null) {
                    shoulder.setRotX(angles[0]);
                    shoulder.setRotY(0f);
                    shoulder.setRotZ(angles[1]);
                    free.setRotX(angles[0]);
                    free.setRotY(0f);
                    free.setRotZ(angles[1]);
                    // the arm straightens as it reaches (at rest it bends at the elbow)
                    for (String part : new String[] {"arm_upper_", "arm_lower_"}) {
                        bone(part + side).ifPresent(b -> {
                            b.setRotX(0f);
                            b.setRotY(0f);
                            b.setRotZ(0f);
                        });
                    }
                }
            }
        }
    }

    /** A world offset from the entity to the model's own space (blocks before the renderer's scale). */
    static Vec3 toModel(Vec3 offset, float bodyYaw) {
        double theta = Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // undo the renderer's Y rotation by (180 - yaw), then its scale
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;
        return new Vec3(x / PrismColossus.SCALE, offset.y / PrismColossus.SCALE, z / PrismColossus.SCALE);
    }

    /**
     * The rotation {X, Z} (radians) that swings a hanging part (its rest direction straight down) to point from the
     * shoulder at {@code target} (model space, blocks), as GeckoLib applies them (Z, Y, X in the matrix): for the arm
     * reaching out and for the fist in flight at its end. Null when the target is on the pivot.
     */
    private static float @org.jetbrains.annotations.Nullable [] aim(GeoBone shoulder, Vec3 target) {
        Vec3 pivot = new Vec3(shoulder.getPivotX() / 16.0, shoulder.getPivotY() / 16.0, shoulder.getPivotZ() / 16.0);
        Vec3 d = target.subtract(pivot);
        if (d.lengthSqr() < 1e-6) {
            return null;
        }
        d = d.normalize();
        float a = (float) -Math.asin(Mth.clamp(d.z, -1.0, 1.0));
        float b = (float) Math.atan2(d.x, -d.y);
        return new float[] {a, b};
    }

    private java.util.Optional<GeoBone> bone(String name) {
        return java.util.Optional.ofNullable(getAnimationProcessor().getBone(name));
    }
}
