package com.cosmicbreach.client.mount;

import com.cosmicbreach.mount.CelestialMount;
import com.cosmicbreach.mount.MountGear;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The mounts' gear on their models: every bone named {@code gear_<gear id>[_part]} shows only while that gear is worn
 * (the saddle by the saddled flag, the barding by the body slot, the tack by the synced tack slot).
 */
final class MountGearBones {
    private static final MountGear[] GEAR = MountGear.values();

    private MountGearBones() {
    }

    static <T extends CelestialMount> void apply(AnimationProcessor<T> processor, CelestialMount mount) {
        for (GeoBone bone : processor.getRegisteredBones()) {
            String name = bone.getName();
            if (!name.startsWith("gear_")) {
                continue;
            }
            boolean show = false;
            String rest = name.substring(5);
            for (MountGear g : GEAR) {
                if (rest.startsWith(g.id())) {
                    show = worn(g, mount);
                    break;
                }
            }
            bone.setHidden(!show);
            bone.setChildrenHidden(!show);
        }
    }

    static boolean worn(MountGear gear, CelestialMount mount) {
        return switch (gear.slot()) {
            case SADDLE -> mount.isSaddled();
            case ARMOR -> MountGear.of(mount.getBodyArmorItem()) == gear;
            case TACK -> mount.wears(gear);
        };
    }
}
