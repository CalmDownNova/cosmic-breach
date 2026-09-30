package com.cosmicbreach.client.dev;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Dev only: a player that exists only in this client's level, the way the client knows any other
 * player (a {@link RemotePlayer}), for tests of what other players look like: it gets the same
 * animation layer and plays through the same calls. It stands where it is put, faces where it is
 * told, and walks when told to, moving the way the network moves remote players (position
 * interpolation), so vanilla's walk animation sees it walk. The server never hears of it.
 */
public final class Mannequin {
    /** An id DefaultPlayerSkin gives wide-armed Steve (the arms the animations were solved for). */
    public static final UUID STEVE = new UUID(0L, 15L);
    private static @Nullable Mannequin walking;
    private static boolean listening;

    private final RemotePlayer body;
    private Vec3 step = Vec3.ZERO;

    private Mannequin(RemotePlayer body) {
        this.body = body;
    }

    /** A mannequin standing at {@code at} facing {@code yaw} (0 south, 90 west), holding {@code held}. */
    public static Mannequin spawn(ClientLevel level, Vec3 at, float yaw, ItemStack held) {
        RemotePlayer body = new RemotePlayer(level, new GameProfile(STEVE, "Mannequin"));
        body.moveTo(at.x, at.y, at.z, yaw, 0f);
        body.setOldPosAndRot();
        body.setOnGround(true);
        body.setItemSlot(EquipmentSlot.MAINHAND, held);
        Mannequin mannequin = new Mannequin(body);
        mannequin.face(yaw);
        level.addEntity(body);
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> {
                Mannequin m = walking;
                if (m != null && !m.body.isRemoved()) {
                    Vec3 next = m.body.position().add(m.step);
                    m.body.lerpTo(next.x, next.y, next.z, m.body.getYRot(), 0f, 1);
                }
            });
        }
        return mannequin;
    }

    public RemotePlayer body() {
        return body;
    }

    /** Turns head and body to {@code yaw} at once, with no interpolation. */
    public void face(float yaw) {
        body.setYRot(yaw);
        body.yRotO = yaw;
        body.setXRot(0f);
        body.xRotO = 0f;
        body.setYHeadRot(yaw);
        body.yHeadRotO = yaw;
        body.setYBodyRot(yaw);
        body.yBodyRotO = yaw;
    }

    /** Walks {@code perTick} blocks every tick until {@link #stand}. */
    public void walk(Vec3 perTick) {
        step = perTick;
        walking = this;
    }

    public void stand() {
        step = Vec3.ZERO;
        if (walking == this) {
            walking = null;
        }
    }

    /** Puts it back to {@code at}, standing. */
    public void moveTo(Vec3 at) {
        stand();
        body.moveTo(at.x, at.y, at.z, body.getYRot(), 0f);
        body.setOldPosAndRot();
        face(body.getYRot());
    }

    public void remove() {
        stand();
        body.remove(Entity.RemovalReason.DISCARDED);
    }
}
