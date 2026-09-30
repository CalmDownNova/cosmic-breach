package com.cosmicbreach.client.weather;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.LayerWeather;
import com.cosmicbreach.world.weather.SolarFlare;
import com.cosmicbreach.world.weather.WeatherKind;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Weather on the screen's edges, readable at a glance:
 * <ul>
 *   <li>A Solar Flare on an exposed player: the edges glow Solenne's orange, brightest just after each Scorch
 *       tick, and every living thing out in the open (the player included) gives off rising embers. Shade
 *       clears it within half a second.</li>
 *   <li>An Eclipse Surge in the Deep: the edges darken to violet black as the warning runs, and stay dark
 *       while the Surge lasts.</li>
 * </ul>
 */
final class WeatherOverlay {
    private static final ResourceLocation VIGNETTE = CosmicBreach.id("textures/gui/weather_vignette.png");
    private static final ResourceLocation LAYER = CosmicBreach.id("weather_overlay");
    private static final RandomSource RANDOM = RandomSource.create();
    private static float exposure;
    private static float exposureO;
    private static float dark;
    private static float darkO;

    private WeatherOverlay() {
    }

    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, WeatherOverlay::render);
    }

    /** How strongly the local player's screen shows exposure, 0 to 1 (for checks). */
    static float exposure() {
        return exposure;
    }

    static void clear() {
        exposure = 0f;
        exposureO = 0f;
        dark = 0f;
        darkO = 0f;
    }

    static void tick(Minecraft mc) {
        exposureO = exposure;
        darkO = dark;
        boolean exposed = mc.player != null && !mc.player.isSpectator() && SolarFlare.exposed(mc.player);
        exposure = exposed ? Math.min(1f, exposure + 0.12f) : Math.max(0f, exposure - 0.1f);
        float darkTarget = 0f;
        if (mc.player != null && Layer.at(mc.player.getY()) == Layer.DEEP) {
            LayerWeather deep = CosmicWeather.client(Layer.DEEP);
            if (deep.is(WeatherKind.SURGE, Phase.WARNING)) {
                darkTarget = (float) deep.progress(mc.level.getGameTime());
            } else if (deep.is(WeatherKind.SURGE, Phase.ACTIVE)) {
                darkTarget = 1f;
            }
        }
        dark = darkTarget > dark ? Math.min(darkTarget, dark + 0.02f) : Math.max(darkTarget, dark - 0.02f);
        if (!SolarFlare.active(mc.level) || !FxParticles.ready()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.distanceToSqr(cam) > 40 * 40
                    || living.getType().is(SolarFlare.SOLAR_IMMUNE) || RANDOM.nextInt(2) != 0 || !SolarFlare.exposed(living)) {
                continue;
            }
            Vec3 at = living.position().add((RANDOM.nextDouble() - 0.5) * living.getBbWidth(),
                    RANDOM.nextDouble() * living.getBbHeight(), (RANDOM.nextDouble() - 0.5) * living.getBbWidth());
            if (FxBudget.count(1, at, false) > 0) {
                FxBudget.spawn(FxParticles.spark(mc.level, at).velocity(new Vec3(0, 0.05 + RANDOM.nextDouble() * 0.05, 0))
                        .color(1.0f, 0.55f, 0.18f).size(0.06f, 0.02f).life(12 + RANDOM.nextInt(8)).drag(0.96f).gravity(-0.05f));
            }
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float e = exposureO + (exposure - exposureO) * partial;
        float d = darkO + (dark - darkO) * partial;
        if (e <= 0.01f && d <= 0.01f) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        if (d > 0.01f) {
            // the Surge: the edges sink into violet black
            RenderSystem.defaultBlendFunc();
            graphics.setColor(0.05f, 0.0f, 0.1f, 0.8f * d);
            graphics.blit(VIGNETTE, 0, 0, 0, 0f, 0f, w, h, w, h);
        }
        if (e > 0.01f) {
            long time = mc.level == null ? 0 : mc.level.getGameTime();
            // brighter just after each Scorch tick (once a second, on whole seconds of game time)
            float sinceBurn = ((time % SolarFlare.SCORCH_INTERVAL_TICKS) + partial) / SolarFlare.SCORCH_INTERVAL_TICKS;
            float strength = e * (0.5f + 0.35f * (1f - sinceBurn) * (1f - sinceBurn));
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            graphics.setColor(1.0f, 0.5f, 0.16f, strength);
            graphics.blit(VIGNETTE, 0, 0, 0, 0f, 0f, w, h, w, h);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }
}
