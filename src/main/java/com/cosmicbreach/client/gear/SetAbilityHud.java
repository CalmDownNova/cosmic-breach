package com.cosmicbreach.client.gear;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientConfig;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetState;
import com.cosmicbreach.item.CombatWeaponItem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * The set ability's pip on the HUD: a small ring in the set's colour just right of the dash pips (under the
 * crosshair when no combat weapon is in hand, since the ability works with anything). The ring fills
 * clockwise while the cooldown runs; ready, it shows a solid core, and it flashes once as it comes ready.
 * Only while the worn set allows the ability.
 */
public final class SetAbilityHud {
    public static final ResourceLocation LAYER = CosmicBreach.id("set_ability_hud");

    private static final float PIP_Y = 11.0f;
    private static final float DASH_SPACING = 4.5f;
    private static final float GAP = 7.0f;
    private static final float RADIUS = 2.4f;
    private static final float WIDTH = 0.9f;
    private static final int TRACK = 0x40000000;
    private static final int FLASH_TICKS = 8;

    private static long readySince = Long.MIN_VALUE;
    private static boolean wasReady = true;

    private SetAbilityHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(com.cosmicbreach.client.combat.CombatHud.LAYER, LAYER, SetAbilityHud::render);
    }

    /** What the pip shows now: null when hidden, else the fill (0 to 1) and whether it is ready. */
    public record Pip(float fill, boolean ready, float flash, int color) {}

    public static Pip pip() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        ArmorSet set = ArmorSets.withAbility(player);
        if (set == null) {
            return null;
        }
        long now = mc.level.getGameTime();
        SetState state = ArmorSets.state(player);
        boolean ready = state.ready(now);
        if (ready && !wasReady) {
            readySince = now;
        }
        wasReady = ready;
        float flash = readySince == Long.MIN_VALUE ? 0f : Math.max(0f, 1f - (now - readySince) / (float) FLASH_TICKS);
        return new Pip(state.cooldownFill(now), ready, flash, set.color());
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || mc.options.hideGui || !ClientConfig.showCombatHud()
                || mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            return;
        }
        Pip pip = pip();
        if (pip == null) {
            return;
        }
        float cx = (graphics.guiWidth() - 15) / 2 + 7.5f;
        float cy = (graphics.guiHeight() - 15) / 2 + 7.5f;
        float x = cx;
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat != null && CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
            int dashes = combat.machine().maxDashCharges();
            x = cx + (dashes - 1) * DASH_SPACING / 2f + GAP;
        }
        float y = cy + PIP_Y;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer out = graphics.bufferSource().getBuffer(RenderType.gui());
        int rgb = pip.color() & 0xFFFFFF;
        arc(out, pose, x, y, RADIUS, WIDTH + 1.1f, 0f, 360f, TRACK);
        if (pip.ready()) {
            arc(out, pose, x, y, RADIUS, WIDTH, 0f, 360f, 0xE6000000 | rgb);
            disc(out, pose, x, y, RADIUS * 0.45f, 0xFF000000 | rgb);
            if (pip.flash() > 0f) {
                int a = (int) (pip.flash() * 200) << 24;
                arc(out, pose, x, y, RADIUS + 1.6f * (1f - pip.flash()) + 0.6f, 0.8f, 0f, 360f, a | 0xFFF4DC);
            }
        } else {
            arc(out, pose, x, y, RADIUS, WIDTH, 0f, 360f * pip.fill(), 0x99000000 | rgb);
        }
        graphics.flush();
    }

    /** A band {@code width} wide on {@code radius}, from {@code from} to {@code to} degrees (0 up, clockwise). */
    private static void arc(VertexConsumer out, Matrix4f pose, float cx, float cy, float radius, float width, float from, float to,
                            int argb) {
        float sweep = to - from;
        if (sweep <= 0.01f) {
            return;
        }
        float inner = radius - width / 2f;
        float outer = radius + width / 2f;
        int segments = Math.max(1, (int) Math.ceil(sweep / 12f));
        for (int s = 0; s < segments; s++) {
            float a0 = from + sweep * s / segments;
            float a1 = from + sweep * (s + 1) / segments;
            vertex(out, pose, cx, cy, inner, a0, argb);
            vertex(out, pose, cx, cy, inner, a1, argb);
            vertex(out, pose, cx, cy, outer, a1, argb);
            vertex(out, pose, cx, cy, outer, a0, argb);
        }
    }

    private static void disc(VertexConsumer out, Matrix4f pose, float cx, float cy, float radius, int argb) {
        int segments = 12;
        for (int s = 0; s < segments; s++) {
            float a0 = 360f * s / segments;
            float a1 = 360f * (s + 1) / segments;
            vertex(out, pose, cx, cy, 0f, a0, argb);
            vertex(out, pose, cx, cy, 0f, a1, argb);
            vertex(out, pose, cx, cy, radius, a1, argb);
            vertex(out, pose, cx, cy, radius, a0, argb);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float cx, float cy, float radius, float degrees, int argb) {
        double r = Math.toRadians(degrees);
        out.addVertex(pose, cx + radius * (float) Math.sin(r), cy - radius * (float) Math.cos(r), 0f).setColor(argb);
    }
}
