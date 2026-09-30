package com.cosmicbreach.client.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.colossus.PrismShard;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A Prism Shard: one GeckoLib model ({@code prism_shard}), a crystal beast of the Colossus's own turquoise drawn
 * twice its built size (a block and a half to the tips of its crest), its crest, the point of its head and the slit
 * of its face burning red, green or blue (its glowmask, added full bright by {@link GuardianGlowLayer}).
 */
public class PrismShardRenderer extends GeoEntityRenderer<PrismShard> {
    /** How much bigger than built the shard is drawn. */
    public static final float SCALE = 2.0f;
    private static final ResourceLocation TEXTURE = CosmicBreach.id("textures/entity/prism_shard.png");
    private static final ResourceLocation[] GLOWS = {
            CosmicBreach.id("textures/entity/prism_shard_red_glowmask.png"),
            CosmicBreach.id("textures/entity/prism_shard_green_glowmask.png"),
            CosmicBreach.id("textures/entity/prism_shard_blue_glowmask.png")};

    public PrismShardRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(CosmicBreach.id("prism_shard"), true) {
            @Override
            public ResourceLocation getTextureResource(PrismShard shard) {
                return TEXTURE;
            }
        });
        addRenderLayer(new GuardianGlowLayer<>(this, shard -> GLOWS[Math.floorMod(shard.color(), 3)], shard -> 0xFFFFFF));
        withScale(SCALE);
        this.shadowRadius = 0.8f;
    }
}
