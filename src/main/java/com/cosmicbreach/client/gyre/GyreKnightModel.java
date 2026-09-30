package com.cosmicbreach.client.gyre;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreModes;
import com.cosmicbreach.entity.gyre.GyreOrbit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Gyre Knight's GeckoLib model ({@code geo/entity/gyre_knight.geo.json}, from {@code tools/art/gen_gyre_knight.py}):
 * the empty suit, the gyroscope core whose three gimbal rings spin on three axes (faster as its mode heats up, still when
 * stunned), and the three blades, each a bone of its own placed wherever {@link GyreKnight#renderBlade} puts it and
 * pointed along {@link GyreKnight#renderBladeDirection} (their tips lead; built pointing -Z).
 */
public class GyreKnightModel extends DefaultedEntityGeoModel<GyreKnight> {
    static final ResourceLocation TEXTURE = CosmicBreach.id("textures/entity/gyre_knight.png");

    public GyreKnightModel() {
        super(CosmicBreach.id("gyre_knight"), false);
    }

    @Override
    public ResourceLocation getTextureResource(GyreKnight k) {
        return TEXTURE;
    }

    @Override
    public void setCustomAnimations(GyreKnight k, long instanceId, AnimationState<GyreKnight> state) {
        super.setCustomAnimations(k, instanceId, state);
        float partial = state.getPartialTick();
        double time = k.level().getGameTime() + partial;
        float bodyYaw = Mth.rotLerp(partial, k.yBodyRotO, k.yBodyRot);
        Vec3 origin = k.getPosition(partial);
        // the gimbals: three rings on three axes
        double rate = switch (k.mode()) {
            case STUNNED -> 0.0;
            case SWEEP -> 0.32;
            case LANCE, RECALL -> 0.22;
            case SHIELD -> 0.12;
        };
        double spin = time * rate;
        bone("gimbal0").ifPresent(b -> b.setRotY((float) spin));
        bone("gimbal1").ifPresent(b -> b.setRotX((float) (spin * 1.3)));
        bone("gimbal2").ifPresent(b -> b.setRotZ((float) (spin * 0.8)));
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            GeoBone blade = getAnimationProcessor().getBone("blade" + i);
            if (blade == null) {
                continue;
            }
            Vec3 local = toModel(k.renderBlade(i, partial).subtract(origin), bodyYaw);
            blade.setPosX((float) (-local.x * 16.0));
            blade.setPosY((float) (local.y * 16.0));
            blade.setPosZ((float) (local.z * 16.0));
            Vec3 d = toModel(k.renderBladeDirection(i, partial), bodyYaw);
            double len = d.length();
            d = len < 1e-6 ? new Vec3(0, 0, -1) : d.scale(1.0 / len);
            blade.setRotX((float) Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
            blade.setRotY((float) Math.atan2(-d.x, -d.z));
            // the blade spins on its own axis as it flies
            GyreKnight.Blade b = k.blade(i);
            blade.setRotZ(b == GyreKnight.Blade.FLY || b == GyreKnight.Blade.BROKEN ? (float) (time * 0.9) : 0f);
        }
    }

    /** A world offset from the entity to the model's own space (the renderer turns the model by 180 - its body yaw). */
    static Vec3 toModel(Vec3 offset, float bodyYaw) {
        double theta = Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;
        return new Vec3(x, offset.y, z);
    }

    private java.util.Optional<GeoBone> bone(String name) {
        return java.util.Optional.ofNullable(getAnimationProcessor().getBone(name));
    }

    /** The gimbal spin for tests and previews. */
    static double ringRadius(GyreKnight k, double t) {
        return GyreModes.ringRadius(k.mode(), t, k.approachEnd());
    }
}
