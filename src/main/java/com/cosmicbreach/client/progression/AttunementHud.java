package com.cosmicbreach.client.progression;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.Attunement;
import com.cosmicbreach.progression.Attunements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The Attunement HUD, quiet like the combat HUD: a thin gold line along the bottom edge of the hotbar
 * that shows Attunement XP toward the next level, only while XP comes in (it holds 3 s after the last
 * gain, then fades), filling through every level gained; and on a level-up a small "Attunement N"
 * above the crosshair with the key to spend the points, fading after 3.5 s. F1 hides both.
 */
public final class AttunementHud {
    public static final ResourceLocation LAYER = CosmicBreach.id("attunement_hud");

    static final int XP_HOLD_TICKS = 60;
    static final int XP_FADE_TICKS = 12;
    static final int MESSAGE_TICKS = 70;
    private static final int MESSAGE_FADE_IN = 5;
    private static final int MESSAGE_FADE_OUT = 20;
    /** Share of the gap to the real progress the line closes per tick. */
    private static final double FILL_EASE = 0.25;
    /** The least it moves in a tick (a fraction of a level), so it arrives. */
    private static final double MIN_STEP = 0.01;
    /** The line's half width: the hotbar's. */
    private static final int HALF_WIDTH = 91;
    /** The message sits this far above the crosshair: clear of the combat HUD, the chat and the hotbar's neighbours. */
    private static final int MESSAGE_ABOVE_CENTRE = 52;

    private static final int GOLD = 0xFFC857;
    private static final int GOLD_HEAD = 0xFFF6DC;
    private static final int PALE = 0xD8DEE8;

    private static int xpLeft;
    /** What the line shows, in levels: 9.25 is a quarter of the way from level 9 to 10. Negative: nothing yet. */
    private static double shown = -1;
    private static double shownBefore = -1;
    private static int messageLevel;
    private static int messageAge = -1;

    private AttunementHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, AttunementHud::render);
    }

    /** XP came in: show the line (it animates from what it showed before to the synced state). */
    public static void showXpGain() {
        xpLeft = XP_HOLD_TICKS + XP_FADE_TICKS;
    }

    public static void showLevelUp(int level) {
        messageLevel = level;
        messageAge = 0;
        showXpGain();
    }

    /** True while the XP line is on screen. */
    public static boolean xpLineVisible() {
        return xpLeft > 0;
    }

    /** The level the message shows, or 0 when no message is on screen. */
    public static int messageLevel() {
        return messageAge >= 0 ? messageLevel : 0;
    }

    public static void reset() {
        xpLeft = 0;
        shown = -1;
        shownBefore = -1;
        messageAge = -1;
    }

    static void tick(Minecraft mc) {
        shownBefore = shown;
        if (mc.player == null) {
            reset();
            return;
        }
        double target = progress(Attunements.of(mc.player));
        if (xpLeft <= 0 || shown < 0 || shown > target) {
            shown = target; // hidden: follow the state, so the next gain animates from here
            shownBefore = target;
        } else {
            if (target - shown > 1.0) {
                shown = target - 1.0; // many levels at once: one full sweep and a wrap, not a flicker per level
                shownBefore = shown;
            }
            double gap = target - shown;
            shown += Math.max(Math.min(gap, MIN_STEP), gap * FILL_EASE);
        }
        if (xpLeft > 0) {
            xpLeft--;
        }
        if (messageAge >= 0 && ++messageAge >= MESSAGE_TICKS) {
            messageAge = -1;
        }
    }

    /** Level plus the share of the way to the next (a full bar, not a wrap, at the cap). */
    static double progress(Attunement state) {
        double fill = state.isMaxLevel() ? 1.0 : state.xp() / (double) state.xpToNext();
        return state.level() + Math.min(0.999, fill);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (xpLeft > 0) {
            xpLine(graphics, partial);
        }
        if (messageAge >= 0) {
            message(graphics, mc, partial);
        }
    }

    private static void xpLine(GuiGraphics graphics, float partial) {
        float alpha = Mth.clamp((xpLeft - partial) / XP_FADE_TICKS, 0f, 1f);
        if (alpha <= 0.01f) {
            return;
        }
        int centre = graphics.guiWidth() / 2;
        int left = centre - HALF_WIDTH;
        int right = centre + HALF_WIDTH;
        int y = graphics.guiHeight() - 1;
        graphics.fill(left, y, right, y + 1, argb(alpha * 0.5f, 0x000000));
        double at = Mth.lerp(partial, shownBefore, shown);
        float fill = (float) Mth.clamp(at - Math.floor(at), 0.0, 1.0);
        int end = left + Math.round((right - left) * fill);
        if (end > left) {
            graphics.fill(left, y, end, y + 1, argb(alpha * 0.9f, GOLD));
            graphics.fill(end - 1, y, end, y + 1, argb(alpha, GOLD_HEAD));
        }
    }

    private static void message(GuiGraphics graphics, Minecraft mc, float partial) {
        float t = messageAge + partial;
        float alpha = Math.min(1f, t / MESSAGE_FADE_IN) * Mth.clamp((MESSAGE_TICKS - t) / MESSAGE_FADE_OUT, 0f, 1f);
        if (alpha < 0.03f) {
            return; // the font draws alpha under 4/255 as opaque
        }
        Font font = mc.font;
        int centre = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 - MESSAGE_ABOVE_CENTRE;
        Component title = Component.translatable("hud.cosmicbreach.attunement.level_up", messageLevel);
        int width = font.width(title);
        graphics.drawString(font, title, centre - width / 2, y, argb(alpha, GOLD), true);
        rule(graphics, centre, y + 11, width / 2 + 10, alpha);
        int unspent = Attunements.of(mc.player).unspent();
        if (unspent > 0) {
            Component hint = Component.translatable(unspent == 1 ? "hud.cosmicbreach.attunement.point_to_spend"
                    : "hud.cosmicbreach.attunement.points_to_spend", unspent, ProgressionKeys.ATTUNEMENT.getTranslatedKeyMessage());
            graphics.drawString(font, hint, centre - font.width(hint) / 2, y + 15, argb(alpha * 0.85f, PALE), true);
        }
    }

    /** A 1-pixel gold rule that fades toward both ends. */
    private static void rule(GuiGraphics graphics, int centre, int y, int half, float alpha) {
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            int inner = half * i / steps;
            int outer = half * (i + 1) / steps;
            int colour = argb(alpha * 0.8f * (1f - i / (float) steps), GOLD);
            graphics.fill(centre - outer, y, centre - inner, y + 1, colour);
            graphics.fill(centre + inner, y, centre + outer, y + 1, colour);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
