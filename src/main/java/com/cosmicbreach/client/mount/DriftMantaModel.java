package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.mount.DriftManta;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Drift Manta's GeckoLib model ({@code geo/entity/drift_manta.geo.json}, from {@code tools/art/gen_mounts.py}):
 * the whole body pitches with its climb and banks into its turns (the {@code tilt} bone), the wings ripple in their
 * animations, and the gear bones show what it wears.
 */
public class DriftMantaModel extends DefaultedEntityGeoModel<DriftManta> {
    public DriftMantaModel() {
        super(CosmicBreach.id("drift_manta"), false);
    }

    @Override
    public void setCustomAnimations(DriftManta manta, long instanceId, AnimationState<DriftManta> state) {
        super.setCustomAnimations(manta, instanceId, state);
        GeoBone tilt = getAnimationProcessor().getBone("tilt");
        if (tilt != null) {
            float partial = state.getPartialTick();
            tilt.setRotX(Mth.lerp(partial, manta.tiltO, manta.tilt) * Mth.DEG_TO_RAD);
            tilt.setRotZ(Mth.lerp(partial, manta.bankO, manta.bank) * Mth.DEG_TO_RAD);
        }
        MountGearBones.apply(getAnimationProcessor(), manta);
    }
}
