package com.cosmicbreach.client.progression;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.progression.Allocation;
import com.cosmicbreach.progression.Attunement;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.progression.DerivedStats;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.progression.net.AllocatePointsPayload;
import com.cosmicbreach.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The allocation screen (Attunement key, K by default): level, XP toward the next level and points to
 * spend; the four attributes with + buttons, and - buttons for points added during this visit; what
 * each attribute gives right now (the numbers the game uses, gear included) with the changes a
 * pending allocation would make shown in bright gold; Confirm sends the allocation, which the server
 * checks before it counts. A dark translucent panel with gold accents over the running game (it
 * doesn't pause). K or Esc closes it; unconfirmed points are simply not spent.
 */
public final class AttunementScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PAD = 12;
    private static final int HEADER_H = 46;
    private static final int ROW_H = 36;
    private static final int FOOTER_H = 28;
    private static final int VALUE_RIGHT = 86;
    private static final int GEAR_X = 89;
    private static final int MINUS_X = 106;
    private static final int PLUS_X = 119;
    private static final int TEXT_X = 140;
    private static final int LINE_H = 10;
    private static final int BUTTON = 11;
    /** Ticks to wait for the server's answer to a Confirm before showing the synced state again. */
    private static final int AWAIT_TICKS = 40;

    private static final int PANEL_TOP = 0xF2141822;
    private static final int PANEL_BOTTOM = 0xF40B0D12;
    private static final int BORDER = 0x66FFC857;
    private static final int GOLD = 0xFFFFC857;
    private static final int GOLD_BRIGHT = 0xFFFFE9B0;
    private static final int PALE_GOLD = 0xFFFFE3A0;
    private static final int WHITE = 0xFFF4F6FA;
    private static final int TEXT = 0xFFB4BCC8;
    private static final int DIM = 0xFF7C8492;
    private static final int TURQUOISE = 0xFF60E8D8;
    private static final int TRACK = 0x33FFFFFF;
    private static final int SEPARATOR = 0x18FFFFFF;

    private final Map<Stat, Integer> pending = new EnumMap<>(Stat.class);
    private final Map<Stat, GoldButton> plus = new EnumMap<>(Stat.class);
    private final Map<Stat, GoldButton> minus = new EnumMap<>(Stat.class);
    private @Nullable GoldButton confirm;
    /** What a Confirm spent, shown until the server's synced state arrives. */
    private @Nullable Allocation awaiting;
    private @Nullable Attunement awaitingFrom;
    private int awaitingTicks;
    private int left;
    private int top;
    private int panelH;

    public AttunementScreen() {
        super(Component.translatable("screen.cosmicbreach.attunement.title"));
        for (Stat stat : Stat.values()) {
            pending.put(stat, 0);
        }
    }

    /** Opens the screen (the Attunement key, and later the Codex). */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new AttunementScreen());
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        panelH = HEADER_H + Stat.values().length * ROW_H + FOOTER_H;
        left = (width - PANEL_W) / 2;
        top = Math.max(2, (height - panelH) / 2);
        plus.clear();
        minus.clear();
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat stat = stats[i];
            int y = rowTop(i) + 2;
            minus.put(stat, addRenderableWidget(new GoldButton(left + MINUS_X, y, BUTTON, BUTTON,
                    Component.translatable("screen.cosmicbreach.attunement.remove", statName(stat)), GoldButton.Glyph.MINUS,
                    () -> change(stat, -1))));
            plus.put(stat, addRenderableWidget(new GoldButton(left + PLUS_X, y, BUTTON, BUTTON,
                    Component.translatable("screen.cosmicbreach.attunement.add", statName(stat)), GoldButton.Glyph.PLUS,
                    () -> change(stat, 1))));
        }
        int confirmW = 90;
        confirm = addRenderableWidget(new GoldButton(left + (PANEL_W - confirmW) / 2, top + panelH - 21, confirmW, 14,
                Component.translatable("screen.cosmicbreach.attunement.confirm"), GoldButton.Glyph.TEXT, this::confirm));
        refreshButtons();
    }

    private int rowTop(int index) {
        return top + HEADER_H + index * ROW_H;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the server keeps running, so a Confirm is answered at once
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ProgressionKeys.ATTUNEMENT.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        if (awaiting != null && (state() != awaitingFrom || ++awaitingTicks > AWAIT_TICKS)) {
            awaiting = null;
            awaitingFrom = null;
        }
        refreshButtons();
    }

    // ------------------------------------------------------------------ state

    private Attunement state() {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        return player == null ? Attunement.START : Attunements.of(player);
    }

    /** The spent points as shown: what a Confirm just sent, until the server's answer arrives. */
    private Allocation spentShown() {
        return awaiting != null ? awaiting : state().spent();
    }

    public int pending(Stat stat) {
        return pending.get(stat);
    }

    private int pendingTotal() {
        int total = 0;
        for (int points : pending.values()) {
            total += points;
        }
        return total;
    }

    /** Points still free to add in this visit. */
    public int available() {
        Attunement state = state();
        int inFlight = spentShown().total() - state.spent().total();
        return state.unspent() - inFlight - pendingTotal();
    }

    private int allocatedAfter(Stat stat) {
        return spentShown().get(stat) + pending(stat);
    }

    private void change(Stat stat, int delta) {
        int now = pending(stat);
        if (delta > 0 && (available() <= 0 || allocatedAfter(stat) >= Attunement.STAT_CAP)) {
            return;
        }
        if (delta < 0 && now <= 0) {
            return;
        }
        pending.put(stat, now + delta);
        refreshButtons();
    }

    private void confirm() {
        Allocation add = new Allocation(pending(Stat.POWER), pending(Stat.AGILITY), pending(Stat.ARCANE), pending(Stat.RESILIENCE));
        if (add.isZero()) {
            return;
        }
        PacketDistributor.sendToServer(new AllocatePointsPayload(add));
        awaiting = spentShown().plus(add);
        awaitingFrom = state();
        awaitingTicks = 0;
        for (Stat stat : Stat.values()) {
            pending.put(stat, 0);
        }
        refreshButtons();
    }

    private void refreshButtons() {
        int available = available();
        for (Stat stat : Stat.values()) {
            GoldButton add = plus.get(stat);
            GoldButton remove = minus.get(stat);
            if (add != null) {
                add.active = available > 0 && allocatedAfter(stat) < Attunement.STAT_CAP;
            }
            if (remove != null) {
                remove.visible = pending(stat) > 0;
                remove.active = remove.visible;
            }
        }
        if (confirm != null) {
            confirm.active = pendingTotal() > 0;
        }
    }

    /** The + button for {@code stat} (tests click it through {@link #mouseClicked}). */
    public AbstractWidget plusButton(Stat stat) {
        return plus.get(stat);
    }

    /** The - button for {@code stat}; only visible while this visit added points to it. */
    public AbstractWidget minusButton(Stat stat) {
        return minus.get(stat);
    }

    public AbstractWidget confirmButton() {
        return confirm;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x50000000, 0x88000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        panel(graphics);
        header(graphics);
        StatBlock current = ProgressionStats.of(player);
        StatBlock preview = preview(player, current);
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            row(graphics, i, stats[i], player, current, preview);
        }
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** The effective stats after the shown allocation: allocated points plus what gear adds now, at most 40. */
    private StatBlock preview(LocalPlayer player, StatBlock current) {
        Allocation allocatedNow = state().spent();
        int[] points = new int[4];
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat stat = stats[i];
            int gear = current.get(stat) - allocatedNow.get(stat);
            points[i] = Math.max(0, Math.min((int) ModAttributes.MAX_POINTS, allocatedAfter(stat) + gear));
        }
        return new StatBlock(points[0], points[1], points[2], points[3]);
    }

    private void panel(GuiGraphics graphics) {
        int right = left + PANEL_W;
        int bottom = top + panelH;
        graphics.fillGradient(left, top, right, bottom, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(left, top, PANEL_W, panelH, BORDER);
        int corner = 8;
        graphics.fill(left, top, left + corner, top + 1, GOLD);
        graphics.fill(left, top, left + 1, top + corner, GOLD);
        graphics.fill(right - corner, top, right, top + 1, GOLD);
        graphics.fill(right - 1, top, right, top + corner, GOLD);
        graphics.fill(left, bottom - 1, left + corner, bottom, GOLD);
        graphics.fill(left, bottom - corner, left + 1, bottom, GOLD);
        graphics.fill(right - corner, bottom - 1, right, bottom, GOLD);
        graphics.fill(right - 1, bottom - corner, right, bottom, GOLD);
        for (int i = 1; i < Stat.values().length; i++) {
            graphics.fill(left + PAD, rowTop(i) - 2, right - PAD, rowTop(i) - 1, SEPARATOR);
        }
        graphics.fill(left + PAD, top + panelH - FOOTER_H, right - PAD, top + panelH - FOOTER_H + 1, SEPARATOR);
    }

    private void header(GuiGraphics graphics) {
        Font font = this.font;
        int centre = left + PANEL_W / 2;
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centre - titleWidth / 2, top + 8, GOLD, false);
        rule(graphics, centre, top + 18, titleWidth / 2 + 24);

        Attunement state = state();
        Component level = Component.translatable(state.isMaxLevel() ? "screen.cosmicbreach.attunement.level_max"
                : "screen.cosmicbreach.attunement.level", state.level());
        graphics.drawString(font, level, left + PAD, top + 25, PALE_GOLD, false);

        Component xp = state.isMaxLevel() ? Component.translatable("screen.cosmicbreach.attunement.xp_max")
                : Component.translatable("screen.cosmicbreach.attunement.xp", grouped(state.xp()), grouped(state.xpToNext()));
        graphics.drawString(font, xp, centre - font.width(xp) / 2, top + 25, TEXT, false);

        int available = available();
        Component points = Component.translatable("screen.cosmicbreach.attunement.points", available);
        graphics.drawString(font, points, left + PANEL_W - PAD - font.width(points), top + 25, available > 0 ? GOLD : DIM, false);

        int barLeft = left + PAD;
        int barRight = left + PANEL_W - PAD;
        int barY = top + 36;
        graphics.fill(barLeft, barY, barRight, barY + 2, TRACK);
        float fill = state.isMaxLevel() ? 1f : state.xp() / (float) state.xpToNext();
        int end = barLeft + Math.round((barRight - barLeft) * fill);
        if (end > barLeft) {
            graphics.fill(barLeft, barY, end, barY + 2, GOLD);
        }
    }

    private void row(GuiGraphics graphics, int index, Stat stat, LocalPlayer player, StatBlock current, StatBlock preview) {
        Font font = this.font;
        int y = rowTop(index);
        graphics.drawString(font, statName(stat), left + PAD, y + 4, PALE_GOLD, false);

        int allocated = allocatedAfter(stat);
        String value = Integer.toString(allocated);
        int valueColour = pending(stat) > 0 ? GOLD_BRIGHT : WHITE;
        graphics.drawString(font, value, left + VALUE_RIGHT - font.width(value), y + 4, valueColour, false);
        int gear = current.get(stat) - state().spent().get(stat);
        if (gear != 0) {
            Component gearText = Component.translatable("screen.cosmicbreach.attunement.gear", (gear > 0 ? "+" : "") + gear);
            graphics.drawString(font, gearText, left + GEAR_X, y + 4, TURQUOISE, false);
        }

        List<Component> now = lines(stat, current, player);
        List<Component> after = lines(stat, preview, player);
        for (int i = 0; i < after.size(); i++) {
            Component line = after.get(i);
            boolean changed = i >= now.size() || !now.get(i).getString().equals(line.getString());
            graphics.drawString(font, line, left + TEXT_X, y + 4 + i * LINE_H, changed ? GOLD_BRIGHT : TEXT, false);
        }
    }

    /** What {@code stat} gives at these effective points, one short line each. */
    private List<Component> lines(Stat stat, StatBlock s, LocalPlayer player) {
        DerivedStats d = DerivedStats.of(s);
        List<Component> out = new ArrayList<>(3);
        switch (stat) {
            case POWER -> {
                out.add(line("power.crit", fixed(d.critMultiplier(), 2)));
                gradeLine(out, stat, s, player, true);
            }
            case AGILITY -> {
                out.add(line("agility.crit", trimmed(d.critChance() * 100), trimmed(d.moveSpeedBonus() * 100)));
                out.add(line("agility.dash", d.dashCharges(), fixed(d.dashRechargeSeconds(), 2), d.dashIframes()));
                gradeLine(out, stat, s, player, false);
            }
            case ARCANE -> {
                out.add(line("arcane.resonance", d.maxResonance(), fixed(d.cooldownFactor(), 2)));
                WeaponDef weapon = heldWeapon(player);
                double ability = DerivedStats.abilityArcaneBonus(weapon == null ? Map.of() : weapon.grades(), s);
                out.add(line("arcane.ability", trimmed(ability * 100)));
            }
            case RESILIENCE -> {
                out.add(line("resilience.health", trimmed(d.bonusHealth()), trimmed(d.damageReduction() * 100)));
                out.add(line("resilience.defense", trimmed(d.poise()), d.parryWindow()));
            }
        }
        return out;
    }

    /** The held weapon's damage from this stat's grade, or a general line for Power when it has none. */
    private void gradeLine(List<Component> out, Stat stat, StatBlock s, LocalPlayer player, boolean generalIfNone) {
        WeaponDef weapon = heldWeapon(player);
        Grade grade = weapon == null ? null : weapon.grades().get(stat);
        if (grade != null) {
            ItemStack stack = player.getMainHandItem();
            out.add(line("grade", stack.getHoverName(), grade.name(),
                    trimmed(DerivedStats.gradeBonus(weapon.grades(), stat, s) * 100)));
        } else if (generalIfNone) {
            out.add(line("power.general"));
        }
    }

    private static @Nullable WeaponDef heldWeapon(LocalPlayer player) {
        return CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
    }

    private void rule(GuiGraphics graphics, int centre, int y, int half) {
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            int inner = half * i / steps;
            int outer = half * (i + 1) / steps;
            int alpha = Math.round(200 * (1f - i / (float) steps));
            int colour = (alpha << 24) | (GOLD & 0xFFFFFF);
            graphics.fill(centre - outer, y, centre - inner, y + 1, colour);
            graphics.fill(centre + inner, y, centre + outer, y + 1, colour);
        }
    }

    private static Component statName(Stat stat) {
        return Component.translatable("cosmicbreach.stat." + stat.getSerializedName());
    }

    private static Component line(String key, Object... args) {
        return Component.translatable("screen.cosmicbreach.attunement." + key, args);
    }

    private static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** One decimal, without a trailing .0: 25, 12.5, 17. */
    private static String trimmed(double value) {
        String one = fixed(value, 1);
        return one.endsWith(".0") ? one.substring(0, one.length() - 2) : one;
    }

    private static String grouped(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    // ------------------------------------------------------------------ the buttons

    /** A small dark button with a gold frame: a crisp + or -, or a short label. */
    static final class GoldButton extends AbstractButton {
        enum Glyph { PLUS, MINUS, TEXT }

        private final Glyph glyph;
        private final Runnable action;

        GoldButton(int x, int y, int width, int height, Component message, Glyph glyph, Runnable action) {
            super(x, y, width, height, message);
            this.glyph = glyph;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int border = !active ? 0x44FFC857 : hot ? 0xFFFFE9B0 : 0xB3FFC857;
            int fill = !active ? 0x66101218 : hot ? 0xE0382C14 : 0xCC1A1C24;
            int ink = !active ? 0x66FFC857 : hot ? 0xFFFFF6DC : GOLD;
            int x = getX();
            int y = getY();
            graphics.fill(x, y, x + width, y + height, fill);
            graphics.renderOutline(x, y, width, height, border);
            int cx = x + width / 2;
            int cy = y + height / 2;
            switch (glyph) {
                case PLUS -> {
                    graphics.fill(cx - 3, cy, cx + 4, cy + 1, ink);
                    graphics.fill(cx, cy - 3, cx + 1, cy + 4, ink);
                }
                case MINUS -> graphics.fill(cx - 3, cy, cx + 4, cy + 1, ink);
                case TEXT -> {
                    Font font = Minecraft.getInstance().font;
                    graphics.drawString(font, getMessage(), cx - font.width(getMessage()) / 2, y + (height - 8) / 2, ink, false);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
