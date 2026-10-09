package com.cosmicbreach.client.jelly;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.jelly.DriftJelly;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The drift jelly's GeckoLib model ({@code geo/entity/drift_jelly.geo.json}, from {@code tools/art/gen_drift_jelly.py}): a
 * stepped bell over a translucent body with a lit core, a ring of flaps, two frills and eight jointed tendrils.
 */
public class JellyModel extends DefaultedEntityGeoModel<DriftJelly> {
    static final ResourceLocation TEXTURE = CosmicBreach.id("textures/entity/drift_jelly.png");

    public JellyModel() {
        super(CosmicBreach.id("drift_jelly"), false);
    }

    @Override
    public ResourceLocation getTextureResource(DriftJelly jelly) {
        return TEXTURE;
    }
}
