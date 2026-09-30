package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Steps;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Helpers the crypt's scenarios share: the server's state, moving and aiming the player, waiting for a settled view. */
final class CryptKit {
    private CryptKit() {
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    static <T> T server(Function<MinecraftServer, T> call) {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv == null) {
            throw new Steps.Failure("no integrated server");
        }
        try {
            return srv.submit(() -> call.apply(srv)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Steps.Failure f) {
                throw f;
            }
            throw new Steps.Failure("server call failed: " + cause);
        }
    }

    /** Teleports the player (by command) to {@code at}, looking at {@code lookAt}. */
    static void tp(Minecraft mc, Vec3 at, Vec3 lookAt) {
        Vec3 d = lookAt.subtract(at.add(0, 1.62, 0));
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", at.x, at.y, at.z, yaw, pitch));
    }

    /** Turns the player to face {@code target} horizontally (pitch {@code pitch}). */
    static void face(Minecraft mc, Vec3 target, float pitch) {
        Vec3 p = mc.player.position();
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(target.x - p.x), target.z - p.z)));
        var pl = mc.player;
        pl.setYRot(yaw);
        pl.setXRot(pitch);
        pl.yRotO = yaw;
        pl.xRotO = pitch;
        pl.setYHeadRot(yaw);
        pl.yHeadRotO = yaw;
    }

    /** Aims the eye at {@code target}. */
    static void aim(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
        var p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
    }

    static void set(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    static void releaseAll(Minecraft mc) {
        set(mc.options.keyUp, false);
        set(mc.options.keyDown, false);
        set(mc.options.keySprint, false);
        set(mc.options.keyJump, false);
        set(mc.options.keyShift, false);
        set(mc.options.keyLeft, false);
        set(mc.options.keyRight, false);
        mc.player.setSprinting(false);
    }

    private static final int SETTLE_TICKS = 30;

    /** True once the view has drawn every section and stopped changing for a while (or {@code maxTicks} passed). */
    static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = mc.screen == null && mc.level != null && mc.player != null;
            int rendered = ready ? mc.levelRenderer.countRenderedSections() : -1;
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= SETTLE_TICKS || (state[0] >= maxTicks && ready);
        };
    }

    /** Heals the player to full on the server. */
    static void heal() {
        server(srv -> {
            ServerPlayer p = player(srv);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            return null;
        });
    }

    static float health() {
        return server(srv -> player(srv).getHealth());
    }
}
