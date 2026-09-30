package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Falling up, on the client: the pull (the local player rises faster and faster for the server's lift
 * ticks) and the white: it fades in over the pull, holds while the world changes (also over the loading
 * screen), and clears over two seconds once the new place is there.
 */
public final class FallUpClient {
    public static final ResourceLocation LAYER = CosmicBreach.id("fall_up_white");
    private static final int CLEAR_TICKS = 40;
    private static final int SETTLE_TICKS = 8;
    private static final int GIVE_UP_TICKS = 120;

    private enum Phase { IDLE, LIFT, HOLD, CLEAR }

    private static Phase phase = Phase.IDLE;
    private static int total;
    private static int age;
    private static int held;
    private static int settled;
    private static float white;
    private static ResourceKey<Level> from;

    private FallUpClient() {
    }

    static void start(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        phase = Phase.LIFT;
        total = Math.max(1, ticks);
        age = 0;
        held = 0;
        settled = 0;
        from = mc.level.dimension();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        switch (phase) {
            case IDLE -> white = 0f;
            case LIFT -> {
                age++;
                white = Math.min(1f, (float) age / total);
                if (mc.player != null) {
                    Vec3 v = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(v.x * 0.5, Math.min(1.1, 0.35 + 0.035 * age), v.z * 0.5);
                    mc.player.resetFallDistance();
                }
                if (age >= total) {
                    phase = Phase.HOLD;
                }
            }
            case HOLD -> {
                white = 1f;
                held++;
                boolean moved = mc.level != null && mc.level.dimension() != from && !(mc.screen instanceof ReceivingLevelScreen);
                if (moved) {
                    settled++;
                }
                if (settled >= SETTLE_TICKS || held > GIVE_UP_TICKS) {
                    phase = Phase.CLEAR;
                }
            }
            case CLEAR -> {
                white -= 1f / CLEAR_TICKS;
                if (white <= 0f) {
                    white = 0f;
                    phase = Phase.IDLE;
                }
            }
        }
    }

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, FallUpClient::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (white > 0.002f) {
            fill(graphics);
        }
    }

    /** The white also covers the loading screen between the two worlds. */
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (white > 0.002f && event.getScreen() instanceof ReceivingLevelScreen) {
            fill(event.getGuiGraphics());
        }
    }

    private static void fill(GuiGraphics graphics) {
        int a = Math.round(Math.min(1f, white) * 255f);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | 0xFFFBF2);
    }

    /** How white the screen is now, 0 to 1 (checks). */
    public static float white() {
        return white;
    }

    /** True from the pull until the white has cleared (checks). */
    public static boolean active() {
        return phase != Phase.IDLE;
    }
}
