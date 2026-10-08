package com.cosmicbreach.client.voice;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.unsung.Voice;
import com.cosmicbreach.voice.boss.BossCatalog;
import com.cosmicbreach.voice.boss.VoiceLine;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * A boss line's caption (1.1): its words in quotes, centred low on the screen, wrapped to {@value #MAX_WIDTH} pixels on a
 * faint dark box, in the boss's colour; a relayed sentence of the Unsung in pieces, each in the colour of the mask that
 * speaks it. It sits above the action bar (68 up) and the held item's name (59 up), clear of the hotbar, the boss bars at
 * the top and vanilla's subtitles in the corner. Hidden with the HUD (F1).
 */
public final class BossCaptionLayer {
    public static final ResourceLocation ID = CosmicBreach.id("boss_caption");
    static final int MAX_WIDTH = 300;
    /** The caption's last row ends this many GUI pixels above the screen's bottom. */
    static final int ABOVE_BOTTOM = 84;

    private BossCaptionLayer() {
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        VoiceLine line = BossVoiceClient.caption();
        VoiceLine.Variant take = BossVoiceClient.captionTake();
        if (line == null || take == null || mc.options.hideGui) {
            return;
        }
        float alpha = BossVoiceClient.captionAlpha(delta.getGameTimeDeltaPartialTick(false));
        if (alpha < 0.03f) {
            return;
        }
        List<FormattedCharSequence> rows = mc.font.split(text(line, take, BossCatalog.of(line.boss()).captionColor()), MAX_WIDTH);
        int width = 0;
        for (FormattedCharSequence row : rows) {
            width = Math.max(width, mc.font.width(row));
        }
        int rowHeight = mc.font.lineHeight + 1;
        int bottom = graphics.guiHeight() - ABOVE_BOTTOM;
        int top = bottom - rows.size() * rowHeight;
        int centre = graphics.guiWidth() / 2;
        graphics.fill(centre - width / 2 - 4, top - 3, centre + width / 2 + 4, bottom + 1, (int) (alpha * 0x90) << 24);
        int textAlpha = Math.max(4, (int) (alpha * 255));
        for (int r = 0; r < rows.size(); r++) {
            FormattedCharSequence row = rows.get(r);
            graphics.drawString(mc.font, row, centre - mc.font.width(row) / 2, top + r * rowHeight, (textAlpha << 24) | 0xFFFFFF, true);
        }
    }

    /** The caption: the words in quotes in the boss's colour, or a relayed sentence's pieces each in its mask's colour. */
    public static Component text(VoiceLine line, VoiceLine.Variant take, int color) {
        if (take.fragments().isEmpty()) {
            return Component.translatableWithFallback(take.subtitle(), "\"" + line.words() + "\"").withColor(color);
        }
        MutableComponent out = Component.literal("\"").withColor(color);
        for (int i = 0; i < take.fragments().size(); i++) {
            VoiceLine.Fragment f = take.fragments().get(i);
            Voice mask = Voice.values()[Math.max(0, Math.min(2, f.mask() - 1))];
            out.append(Component.literal((i > 0 ? " " : "") + f.text()).withColor(mask.glow));
        }
        return out.append(Component.literal("\"").withColor(color));
    }
}
