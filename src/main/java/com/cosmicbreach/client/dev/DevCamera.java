package com.cosmicbreach.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * Dev only: a camera anywhere, for screenshots: a marker entity that exists only in this client's
 * level (no server round trip, nothing drawn), placed exactly and looked through. Unlike an armor
 * stand summoned by command it moves at once and never lags a tick behind.
 */
public final class DevCamera {
    private final Marker marker;

    private DevCamera(Marker marker) {
        this.marker = marker;
    }

    public static DevCamera create(ClientLevel level) {
        Marker marker = new Marker(EntityType.MARKER, level);
        level.addEntity(marker);
        return new DevCamera(marker);
    }

    /** Puts the eye at {@code eye} looking at {@code target}, with no interpolation. */
    public void place(Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        marker.moveTo(eye.x, eye.y, eye.z, yaw, pitch);
        marker.setOldPosAndRot();
    }

    /** Looks through this camera. */
    public void use() {
        Minecraft.getInstance().setCameraEntity(marker);
    }

    public Entity entity() {
        return marker;
    }

    public void remove() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == marker && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        marker.remove(Entity.RemovalReason.DISCARDED);
    }
}
