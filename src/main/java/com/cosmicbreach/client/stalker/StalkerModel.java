package com.cosmicbreach.client.stalker;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.stalker.HollowStalker;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Hollow Stalker's GeckoLib model ({@code geo/entity/hollow_stalker.geo.json}, from {@code tools/art/gen_stalker.py}):
 * long thin limbs of void under a porcelain mask; the head turns to its look.
 */
public class StalkerModel extends DefaultedEntityGeoModel<HollowStalker> {
    static final ResourceLocation TEXTURE = CosmicBreach.id("textures/entity/hollow_stalker.png");

    public StalkerModel() {
        super(CosmicBreach.id("hollow_stalker"), true);
    }

    @Override
    public ResourceLocation getTextureResource(HollowStalker stalker) {
        return TEXTURE;
    }
}
