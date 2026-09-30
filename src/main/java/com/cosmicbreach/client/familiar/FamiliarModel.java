package com.cosmicbreach.client.familiar;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.familiar.FamiliarEntity;
import com.cosmicbreach.familiar.FamiliarKind;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * A familiar's GeckoLib model ({@code geo/entity/<kind>.geo.json} and its animations, from
 * {@code tools/art/gen_familiars.py}), with the turning parts driven by time so they never loop visibly: the
 * Emberwisp's two crowns of rays turn against each other, the Gravikin's three pebbles orbit its chest.
 */
public class FamiliarModel<T extends FamiliarEntity> extends DefaultedEntityGeoModel<T> {
    private final FamiliarKind kind;

    public FamiliarModel(FamiliarKind kind) {
        super(CosmicBreach.id(kind.id()), false);
        this.kind = kind;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        double time = animatable.level().getGameTime() + state.getPartialTick() + animatable.getId() * 7.0;
        switch (kind) {
            case EMBERWISP -> {
                spin("rays_outer", (float) (time * 0.05));
                spin("rays_inner", (float) (-time * 0.08));
            }
            case GRAVIKIN -> spin("orbit", (float) (time * 0.09));
            case PRISM_MOTH -> {
            }
        }
    }

    private void spin(String name, float radians) {
        GeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setRotY(radians);
        }
    }
}
