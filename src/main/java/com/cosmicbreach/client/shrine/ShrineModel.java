package com.cosmicbreach.client.shrine;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.shrine.ShrineBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** A shrine's GeckoLib model, texture and idle animation, by kind: {@code shrine_<kind>} under geo, textures and animations /block. */
public final class ShrineModel extends GeoModel<ShrineBlockEntity> {
    @Override
    public ResourceLocation getModelResource(ShrineBlockEntity shrine) {
        return CosmicBreach.id("geo/block/shrine_" + shrine.kind().id() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShrineBlockEntity shrine) {
        return CosmicBreach.id("textures/block/shrine_" + shrine.kind().id() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShrineBlockEntity shrine) {
        return CosmicBreach.id("animations/block/shrine_" + shrine.kind().id() + ".animation.json");
    }
}
