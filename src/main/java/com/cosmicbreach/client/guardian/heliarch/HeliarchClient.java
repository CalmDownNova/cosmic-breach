package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.heliarch.HeliarchEffects;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.cosmicbreach.guardian.heliarch.HeliarchLine;
import com.cosmicbreach.guardian.heliarch.HeliarchRegistry;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.ReliquaryBlockEntity;
import com.cosmicbreach.mixin.client.GuiSubtitleAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Heliarch on the client: its renderers, its telegraphs and effects ({@link HeliarchFx}), the sky's eclipse and the
 * seal over the Breach ({@link HeliarchSky}), and its voice: a line is heard at the listener by everyone in the fight
 * (the regent's voice fills the arena), with its words kept up as the subtitle for as long as it lasts.
 */
public final class HeliarchClient {
    private static @Nullable Voice speaking;
    private static final List<HeliarchLine> SPOKEN = new ArrayList<>();

    private HeliarchClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(HeliarchRegistry.HOLLOW_HELIARCH.get(), HeliarchRenderer::new);
            event.registerEntityRenderer(HeliarchRegistry.STAR_SEED.get(), StarSeedRenderer::new);
            event.registerBlockEntityRenderer(HeliarchRegistry.RELIQUARY_ENTITY.get(), ReliquaryRenderer::new);
        });
        HeliarchFx fx = new HeliarchFx();
        HeliarchEffects.install(fx);
        ReliquaryBlockEntity.localPlayer = () -> Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        game.addListener(RenderLevelStageEvent.class, HeliarchFx::render);
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                CosmicBreach.id("heliarch_overlay"), HeliarchClient::renderOverlay));
        game.addListener(EventPriority.LOWEST, false, ClientTickEvent.Post.class, event -> tick());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
        HeliarchSky.register(game);
    }

    // ------------------------------------------------------------------ messages

    public static void onSpeak(int line) {
        if (line < 0 || line >= HeliarchLine.values().length) {
            return;
        }
        HeliarchLine l = HeliarchLine.values()[line];
        Minecraft mc = Minecraft.getInstance();
        if (speaking != null) {
            mc.getSoundManager().stop(speaking);
        }
        speaking = new Voice(l);
        mc.getSoundManager().play(speaking);
        SPOKEN.add(l);
        keepSubtitle(mc, speaking);
    }

    public static void onRain(List<Vec3> circles, long land) {
        HeliarchFx.rain(circles, land);
    }

    public static void onFall(int ring, int segment, boolean restore) {
        HeliarchFx.fall(ring, segment, restore);
    }

    public static void onSeal(boolean sealed, boolean forming) {
        HeliarchSky.setSealed(sealed, forming);
    }

    // ------------------------------------------------------------------ the voice

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        HeliarchSky.tick();
        if (speaking == null) {
            return;
        }
        speaking.age++;
        if (speaking.age > speaking.line.lengthTicks() || mc.level == null) {
            speaking = null;
            return;
        }
        keepSubtitle(mc, speaking);
    }

    /** Refreshes the line's subtitle just ahead of the camera (vanilla drops a subtitle 3 s after its sound starts). */
    private static void keepSubtitle(Minecraft mc, Voice voice) {
        if (!mc.options.showSubtitles().get()) {
            return;
        }
        WeighedSoundEvents events = mc.getSoundManager().getSoundEvent(voice.getLocation());
        if (events == null || events.getSubtitle() == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 at = camera.getPosition().add(new Vec3(camera.getLookVector()).scale(2.0));
        SoundInstance here = new SimpleSoundInstance(voice.getLocation(), SoundSource.HOSTILE, 1.0f, 1.0f,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
        ((GuiSubtitleAccessor) mc.gui).cosmicbreach$subtitleOverlay().onPlaySound(here, events, Float.POSITIVE_INFINITY);
    }

    /** The line being spoken now, or null (checks). */
    public static @Nullable HeliarchLine speaking() {
        return speaking == null ? null : speaking.line;
    }

    /** Every line heard since the game started (checks). */
    public static List<HeliarchLine> spoken() {
        return List.copyOf(SPOKEN);
    }

    private static void reset() {
        if (speaking != null) {
            Minecraft.getInstance().getSoundManager().stop(speaking);
            speaking = null;
        }
        HeliarchFx.clear();
        HeliarchSky.clear();
    }

    /** A line at the listener: the regent's voice is everywhere in the arena at once. */
    private static final class Voice extends AbstractTickableSoundInstance {
        final HeliarchLine line;
        int age;

        Voice(HeliarchLine line) {
            super(line.sound(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.line = line;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 1.0f;
        }

        @Override
        public void tick() {
        }
    }

    // ------------------------------------------------------------------ the screen's edges

    private static final net.minecraft.resources.ResourceLocation VIGNETTE = CosmicBreach.id("textures/gui/weather_vignette.png");
    private static float overlayDark;

    /** How dark the screen's edges are now for the Eclipse Beam's warning, 0 to 1 (checks). */
    public static float overlayDark() {
        return overlayDark;
    }

    /**
     * The Eclipse Beam's warning darkens the screen's edges (violet-black, deepest as the beam comes); Nova's last five
     * seconds burn them gold; a detonation or the death flashes the whole screen white.
     */
    private static void renderOverlay(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        HollowHeliarch h = HeliarchFx.heliarch();
        float dark = 0f;
        float gold = 0f;
        if (h != null && mc.level != null) {
            double a = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false) - h.actionStart();
            if (h.action() == HollowHeliarch.Action.ECLIPSE_BEAM) {
                int tell = com.cosmicbreach.guardian.heliarch.HeliarchMoves.BEAM_TELL;
                int sweep = com.cosmicbreach.guardian.heliarch.HeliarchMoves.BEAM_SWEEP;
                dark = (float) (a < tell ? 0.85 * HeliarchPose.smooth(a / tell) : 0.85 - 0.5 * HeliarchPose.smooth((a - tell) / sweep));
            } else if (h.action() == HollowHeliarch.Action.NOVA) {
                int channel = com.cosmicbreach.guardian.heliarch.HeliarchMoves.NOVA_CHANNEL;
                gold = (float) HeliarchPose.smooth((a - (channel - 100)) / 100.0) * 0.7f;
            }
        }
        overlayDark = dark;
        float flash = HeliarchSky.flashNow();
        if ((dark <= 0.01f && gold <= 0.01f && flash <= 0.01f) || mc.options.hideGui) {
            return;
        }
        int w = graphics.guiWidth();
        int hh = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        if (dark > 0.01f) {
            RenderSystem.defaultBlendFunc();
            graphics.setColor(0.06f, 0.0f, 0.12f, dark);
            graphics.blit(VIGNETTE, 0, 0, 0, 0f, 0f, w, hh, w, hh);
        }
        if (gold > 0.01f) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            graphics.setColor(1.0f, 0.7f, 0.25f, gold);
            graphics.blit(VIGNETTE, 0, 0, 0, 0f, 0f, w, hh, w, hh);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        if (flash > 0.01f) {
            graphics.fill(0, 0, w, hh, ((int) (Math.min(1f, flash) * 230) << 24) | 0xFFF8E8);
        }
        RenderSystem.depthMask(true);
    }

    /** How many frames each kind of telegraph and effect was drawn (checks). */
    public static java.util.Map<String, Integer> drawnFrames() {
        return java.util.Map.copyOf(HeliarchFx.DRAWN);
    }

    /** The Heliarch this client sees, or null. */
    public static @Nullable HollowHeliarch heliarch() {
        return HeliarchFx.heliarch();
    }
}
