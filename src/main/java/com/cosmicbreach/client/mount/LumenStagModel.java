package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.mount.LumenStag;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Lumen Stag's GeckoLib model ({@code geo/entity/lumen_stag.geo.json}, from {@code tools/art/gen_mounts.py}): the
 * head turns to look; the gear bones show what it wears ({@link MountGearBones}).
 */
public class LumenStagModel extends DefaultedEntityGeoModel<LumenStag> {
    public LumenStagModel() {
        super(CosmicBreach.id("lumen_stag"), true);
    }

    @Override
    public void setCustomAnimations(LumenStag stag, long instanceId, AnimationState<LumenStag> state) {
        super.setCustomAnimations(stag, instanceId, state);
        MountGearBones.apply(getAnimationProcessor(), stag);
        // its own rider looking out in first person sees over the head, not through a crown of lit antlers
        Minecraft mc = Minecraft.getInstance();
        boolean riderView = mc.player != null && mc.player.getVehicle() == stag && mc.options.getCameraType().isFirstPerson()
                && mc.getCameraEntity() == mc.player;
        for (String name : new String[] {"antler_left", "antler_right"}) {
            GeoBone bone = getAnimationProcessor().getBone(name);
            if (bone != null) {
                bone.setHidden(riderView);
                bone.setChildrenHidden(riderView);
            }
        }
    }
}
