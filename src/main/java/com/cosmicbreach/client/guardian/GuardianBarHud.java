package com.cosmicbreach.client.guardian;

import com.cosmicbreach.guardian.GuardianBarPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * What a guardian's boss bar adds on the client (client thread): under vanilla's bar a thin gold line, the Break
 * gauge, that flashes white while the guardian is Broken, and a countdown drawn as the bar itself (the server sets
 * the bar's progress to the time left). Also says whether a guardian's music should play ({@link GuardianMusic}).
 */
public final class GuardianBarHud {
    private static final int WIDTH = 182;
    private static final int GAUGE_Y = 6;
    private static final int GAUGE_H = 2;
    private static final int GOLD = 0xFFFFC23A;
    private static final int PALE_GOLD = 0xFFFFE7A6;
    private static final int BACK = 0x90101820;

    private static final Map<UUID, GuardianBarPayload> BARS = new HashMap<>();

    private GuardianBarHud() {
    }

    static void update(GuardianBarPayload payload) {
        if (payload.gone()) {
            BARS.remove(payload.bar());
        } else {
            BARS.put(payload.bar(), payload);
        }
    }

    /** The boss loop the guardian bars this client sees want (the first that names one), or null. */
    public static @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation musicWanted() {
        for (GuardianBarPayload p : BARS.values()) {
            if (!p.music().isEmpty()) {
                return net.minecraft.resources.ResourceLocation.tryParse(p.music());
            }
        }
        return null;
    }

    /** The last gauge this client got for any guardian bar (for checks), or -1. */
    public static float anyGauge() {
        for (GuardianBarPayload p : BARS.values()) {
            return p.gauge();
        }
        return -1f;
    }

    public static int count() {
        return BARS.size();
    }

    static void clear() {
        BARS.clear();
    }

    /** Draws the gauge under one of our bars, before vanilla draws the bar. */
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        GuardianBarPayload p = BARS.get(event.getBossEvent().getId());
        if (p == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY() + GAUGE_Y;
        g.fill(x, y, x + WIDTH, y + GAUGE_H, BACK);
        if (p.broken()) {
            boolean on = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() / 3) % 2 == 0;
            g.fill(x, y, x + WIDTH, y + GAUGE_H, on ? 0xFFFFFFFF : PALE_GOLD);
        } else {
            int fill = Math.round(WIDTH * Math.max(0f, Math.min(1f, p.gauge())));
            if (fill > 0) {
                g.fill(x, y, x + fill, y + GAUGE_H, GOLD);
                g.fill(x + fill - 1, y, x + fill, y + GAUGE_H, PALE_GOLD);
            }
        }
        event.setIncrement(event.getIncrement() + GAUGE_H + 2);
    }
}
