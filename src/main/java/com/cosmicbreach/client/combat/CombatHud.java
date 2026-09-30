package com.cosmicbreach.client.combat;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.WeaponDef;
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
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

/**
 * The combat HUD, drawn round the crosshair while a combat weapon is in hand: a thin gold arc from
 * -120 to +120 degrees (0 is straight up) filled by Resonance, the dash charges as small diamonds in
 * the arc's opening under the crosshair, and while charging a ring that fills up to the charged
 * move's minimum and turns bright at full charge. Everything sits on a faint dark track so it reads
 * on bright sky and on grass alike. Sizes are GUI units (3 pixels each at 1280 by 720). F1 hides it
 * with the rest of the HUD.
 */
public final class CombatHud {
    public static final ResourceLocation LAYER = CosmicBreach.id("combat_hud");

    private static final float ARC_RADIUS = 10.5f;
    private static final float ARC_WIDTH = 1.0f;
    private static final float ARC_FROM = -120f;
    private static final float ARC_SWEEP = 240f;
    private static final float RING_RADIUS = 7.0f;
    private static final float RING_WIDTH = 1.0f;
    private static final float RING_FULL_WIDTH = 1.3f;
    private static final float TRACK_WIDTH = 2.0f;
    private static final float PIP_Y = 11.0f;
    private static final float PIP_SPACING = 4.5f;
    private static final float PIP_SIZE = 1.5f;
    private static final float PIP_BACK = 2.2f;
    private static final float DEGREES_PER_SEGMENT = 4f;

    /** Colours, ARGB. */
    private static final int TRACK = 0x40000000;
    private static final int GOLD = 0xE6FFC857;
    private static final int GOLD_FULL = 0xFFFFE9B0;
    private static final int GOLD_RECHARGING = 0xB3FFC857;
    private static final int RING_FILLING = 0xB3FFF0C8;
    private static final int RING_READY = 0xE6FFD77A;
    private static final int RING_FULL = 0xFFFFF6DC;

    /** The charge the state machine assumes for a charged move that doesn't say (its DEFAULT_CHARGE). */
    private static final MoveDef.Charge DEFAULT_CHARGE = new MoveDef.Charge(12, 24);

    private CombatHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, CombatHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || mc.options.hideGui || !ClientConfig.showCombatHud()
                || !CombatWeaponItem.isCombatWeapon(player.getMainHandItem())
                || mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            return;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat == null) {
            return;
        }
        CombatHudModel model = model(combat.machine());

        // The vanilla crosshair is a 15 by 15 sprite at ((width - 15) / 2, (height - 15) / 2).
        float cx = (graphics.guiWidth() - 15) / 2 + 7.5f;
        float cy = (graphics.guiHeight() - 15) / 2 + 7.5f;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer out = graphics.bufferSource().getBuffer(RenderType.gui());

        arc(out, pose, cx, cy, ARC_RADIUS, TRACK_WIDTH, ARC_FROM, ARC_FROM + ARC_SWEEP, TRACK);
        if (model.resonanceFill() > 0f) {
            arc(out, pose, cx, cy, ARC_RADIUS, ARC_WIDTH, ARC_FROM, ARC_FROM + ARC_SWEEP * model.resonanceFill(),
                    model.resonanceFull() ? GOLD_FULL : GOLD);
        }

        if (model.charge() != CombatHudModel.ChargeStage.NONE) {
            arc(out, pose, cx, cy, RING_RADIUS, TRACK_WIDTH, 0f, 360f, TRACK);
            int colour = switch (model.charge()) {
                case FULL -> RING_FULL;
                case READY -> RING_READY;
                default -> RING_FILLING;
            };
            float width = model.charge() == CombatHudModel.ChargeStage.FULL ? RING_FULL_WIDTH : RING_WIDTH;
            arc(out, pose, cx, cy, RING_RADIUS, width, 0f, 360f * model.chargeFill(), colour);
        }

        int pips = model.maxDashCharges();
        float left = cx - (pips - 1) * PIP_SPACING / 2f;
        for (int i = 0; i < pips; i++) {
            float x = left + i * PIP_SPACING;
            float y = cy + PIP_Y;
            diamond(out, pose, x, y, PIP_BACK, TRACK);
            switch (model.pip(i)) {
                case FULL -> diamond(out, pose, x, y, PIP_SIZE, GOLD);
                case RECHARGING -> diamond(out, pose, x, y, PIP_SIZE * model.rechargeFill(), GOLD_RECHARGING);
                case EMPTY -> {
                }
            }
        }
        graphics.flush();
    }

    private static CombatHudModel model(CombatStateMachine machine) {
        MoveDef.Charge charge = DEFAULT_CHARGE;
        WeaponDef weapon = machine.weapon();
        if (weapon != null && weapon.charged().isPresent()) {
            MoveDef charged = CombatData.client().move(weapon.charged().get());
            if (charged != null && charged.charge().isPresent()) {
                charge = charged.charge().get();
            }
        }
        return CombatHudModel.of(machine.resonance(), machine.maxResonance(), machine.dashCharges(),
                machine.maxDashCharges(), machine.dashRechargeProgress(),
                machine.phase() == CombatStateMachine.Phase.CHARGING, machine.attackHeldTicks(), charge.min(), charge.full());
    }

    /**
     * A band {@code width} wide centred on {@code radius}, from {@code fromDeg} to {@code toDeg}
     * (0 is up, clockwise). Each quad goes round counterclockwise on screen, like GuiGraphics.fill's,
     * so back-face culling keeps it.
     */
    private static void arc(VertexConsumer out, Matrix4f pose, float cx, float cy, float radius, float width,
                            float fromDeg, float toDeg, int argb) {
        float sweep = toDeg - fromDeg;
        if (sweep <= 0.01f || width <= 0f) {
            return;
        }
        float inner = radius - width / 2f;
        float outer = radius + width / 2f;
        int segments = Math.max(1, (int) Math.ceil(sweep / DEGREES_PER_SEGMENT));
        for (int s = 0; s < segments; s++) {
            float a0 = fromDeg + sweep * s / segments;
            float a1 = fromDeg + sweep * (s + 1) / segments;
            vertex(out, pose, cx, cy, inner, a0, argb);
            vertex(out, pose, cx, cy, inner, a1, argb);
            vertex(out, pose, cx, cy, outer, a1, argb);
            vertex(out, pose, cx, cy, outer, a0, argb);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float cx, float cy, float radius, float degrees, int argb) {
        double r = Math.toRadians(degrees);
        out.addVertex(pose, cx + radius * (float) Math.sin(r), cy - radius * (float) Math.cos(r), 0f).setColor(argb);
    }

    /** A diamond with corners {@code size} from its centre: top, left, bottom, right (counterclockwise on screen). */
    private static void diamond(VertexConsumer out, Matrix4f pose, float x, float y, float size, int argb) {
        if (size <= 0.05f) {
            return;
        }
        out.addVertex(pose, x, y - size, 0f).setColor(argb);
        out.addVertex(pose, x - size, y, 0f).setColor(argb);
        out.addVertex(pose, x, y + size, 0f).setColor(argb);
        out.addVertex(pose, x + size, y, 0f).setColor(argb);
    }
}
