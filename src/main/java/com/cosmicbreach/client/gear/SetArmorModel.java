package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.SetArmorItem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

/**
 * An armor set's GeckoLib model, found by the set's id: {@code geo/armor/<id>.geo.json},
 * {@code textures/armor/<id>.png} and {@code animations/armor/<id>.animation.json}. A set whose parts move with
 * the wearer registers a {@link BoneDriver} (the Vanguard's crest), which poses them every frame after GeckoLib's
 * own animation pass.
 */
public class SetArmorModel extends GeoModel<SetArmorItem> {
    /** Poses a set's moving bones from the wearer, once a frame. */
    public interface BoneDriver {
        void pose(SetArmorModel model, Entity wearer, float partialTick);
    }

    private static final Map<ResourceLocation, BoneDriver> DRIVERS = new ConcurrentHashMap<>();

    private final ArmorSet set;
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public SetArmorModel(ArmorSet set) {
        this.set = set;
        String ns = set.id().getNamespace();
        String name = set.id().getPath();
        this.model = ResourceLocation.fromNamespaceAndPath(ns, "geo/armor/" + name + ".geo.json");
        this.texture = texture(set);
        this.animation = ResourceLocation.fromNamespaceAndPath(ns, "animations/armor/" + name + ".animation.json");
    }

    public static void driver(ResourceLocation setId, BoneDriver driver) {
        DRIVERS.put(setId, driver);
    }

    public static ResourceLocation texture(ArmorSet set) {
        return ResourceLocation.fromNamespaceAndPath(set.id().getNamespace(), "textures/armor/" + set.id().getPath() + ".png");
    }

    public static ResourceLocation glowTexture(ArmorSet set) {
        return ResourceLocation.fromNamespaceAndPath(set.id().getNamespace(),
                "textures/armor/" + set.id().getPath() + "_glowmask.png");
    }

    public ArmorSet set() {
        return set;
    }

    @Override
    public ResourceLocation getModelResource(SetArmorItem animatable) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(SetArmorItem animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(SetArmorItem animatable) {
        return animation;
    }

    @Override
    public void setCustomAnimations(SetArmorItem animatable, long instanceId, AnimationState<SetArmorItem> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        BoneDriver driver = DRIVERS.get(set.id());
        Entity wearer = state.getData(DataTickets.ENTITY);
        if (driver != null && wearer != null) {
            driver.pose(this, wearer, state.getPartialTick());
        }
    }
}
