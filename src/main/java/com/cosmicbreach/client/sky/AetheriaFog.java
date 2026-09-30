package com.cosmicbreach.client.sky;

import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Aetheria's fog (GDD 2.3), per layer and cross-faded through the Shear bands with the sky ({@link SkyState}):
 * the Reach #F3E6C4 from 55% to 100% of the render distance, the Drift #A8D8E8 from 45%, the Deep #25123F
 * from 25% to 80%. The colour is the sky's own colour where the camera looks (the horizon's when looking level:
 * the GDD colours are the daytime horizons), so it follows the time of day and distant terrain melts into the
 * sky behind it, above or below.
 *
 * <p><b>Seeing down the Breach.</b> Vanilla's terrain fog is a cylinder whose height counts as much as its
 * radius, so from a Reach island the Deep, 200 to 340 blocks below, is past the fog at any common render
 * distance. Here vertical distance counts less: the fog distance is {@code max(horizontal, |dy| * k)}. The
 * horizontal edge of the loaded world is still fogged exactly as before; only looking up or down reaches
 * further. {@code k} (0.05 to 1, see {@link SkyModel#fogVertical}) goes to the shaders through the fog shape
 * uniform: indices 2 and up mean "cylinder with height factor (index - 1) * 0.05", read by our copy of
 * vanilla's {@code shaders/include/fog.glsl} and put in place by {@code ShaderInstanceFogMixin}. Indices 0
 * and 1 behave exactly as vanilla; outside Aetheria, under water, blinded or under a shader pack the index
 * is never raised. Vanilla also never draws sections further above or below the camera than the render
 * distance; {@code SectionOcclusionGraphMixin} lets the graph reach the whole height in Aetheria
 * ({@link #verticalSections}).
 */
public final class AetheriaFog {
    /** The fog shape index for vanilla's shaders this frame, 0 when ours is not active. */
    private static volatile int shapeIndex;
    /** Aetheria is 480 blocks tall: 30 sections. */
    private static final int AETHERIA_SECTIONS = 30;
    /** Tests can turn the vertical reach and factor off (vanilla's) to compare. */
    public static volatile boolean vanillaVerticalForTest;
    /** The client is in Aetheria (read from the section graph's thread). */
    private static volatile boolean inAetheria;
    /** The last fog colour set in Aetheria (for checks). */
    private static final float[] lastColour = new float[3];
    /** The last terrain fog distances set in Aetheria, in blocks (for checks). */
    private static volatile float lastStart;
    private static volatile float lastEnd;
    /** Terrain fog set up outside Aetheria, counted (so a check can wait for a whole frame rendered elsewhere). */
    private static volatile int framesOutside;

    private AetheriaFog() {
    }

    /** Called by the mixin with the shape index vanilla is about to upload; our index replaces the cylinder's. */
    public static int shapeIndexFor(int vanilla) {
        int ours = shapeIndex;
        return ours != 0 && vanilla == FogShape.CYLINDER.getIndex() ? ours : vanilla;
    }

    /**
     * How many sections up and down the render graph may reach from the camera: vanilla stops at the render
     * distance (12 chunks is 192 blocks), which hides the Deep from the Reach entirely; in Aetheria it reaches
     * the whole height. Called by {@code SectionOcclusionGraphMixin}, possibly off the render thread.
     */
    public static int verticalSections(int viewDistance) {
        return inAetheria && !vanillaVerticalForTest ? Math.max(viewDistance, AETHERIA_SECTIONS) : viewDistance;
    }

    /** The index in effect (for checks). */
    public static int activeShapeIndex() {
        return shapeIndex;
    }

    /** How many terrain fog set-ups have run outside Aetheria (one a frame); checks wait for it to move. */
    public static int framesOutside() {
        return framesOutside;
    }

    /** The last fog colour set in Aetheria. */
    public static float[] lastColour() {
        return lastColour.clone();
    }

    /** The last terrain fog start and end set in Aetheria, in blocks. */
    public static float[] lastDistances() {
        return new float[] {lastStart, lastEnd};
    }

    static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !AetheriaWorld.is(level)) {
            shapeIndex = 0;
            inAetheria = false;
            return;
        }
        inAetheria = true;
        SkyFrame f = SkyState.update(level, event.getCamera().getPosition().y, (float) event.getPartialTick());
        if (event.getCamera().getFluidInCamera() != FogType.NONE || AetheriaSkyEffects.blinded(event.getCamera())) {
            return;
        }
        SkyModel.fogColour(f, event.getCamera().getLookVector().y(), lastColour);
        event.setRed(lastColour[0]);
        event.setGreen(lastColour[1]);
        event.setBlue(lastColour[2]);
    }

    static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            shapeIndex = 0;
            return;
        }
        if (mc.level == null || !AetheriaWorld.is(mc.level)) {
            shapeIndex = 0;
            framesOutside++;
            return;
        }
        if (event.getType() != FogType.NONE || AetheriaSkyEffects.blinded(event.getCamera()) || mc.gui.getBossOverlay().shouldCreateWorldFog()) {
            shapeIndex = 0;
            return;
        }
        SkyFrame f = SkyState.FRAME;
        float far = event.getFarPlaneDistance();
        lastStart = far * f.fogStart;
        lastEnd = far * f.fogEnd;
        event.setNearPlaneDistance(lastStart);
        event.setFarPlaneDistance(lastEnd);
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true);
        shapeIndex = ShaderPacks.inUse() || vanillaVerticalForTest ? 0 : SkyModel.fogShapeIndex(f.fogVertical);
    }
}
