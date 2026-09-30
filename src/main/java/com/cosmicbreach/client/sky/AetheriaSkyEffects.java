package com.cosmicbreach.client.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Aetheria's {@link DimensionSpecialEffects} ({@code cosmicbreach:aetheria}): our own sky, no clouds (the
 * vanilla layer at Y 192 would slice through the Drift), otherwise Overworld-like, so that under an Iris
 * shader pack (when both hooks step aside) the pack sees a normal sky with a sun.
 */
public final class AetheriaSkyEffects extends DimensionSpecialEffects {
    public AetheriaSkyEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (ShaderPacks.inUse()) {
            return false;
        }
        // like vanilla: no sky in thick fog, lava, powder snow, under water, or blinded; the clear colour shows
        FogType fluid = camera.getFluidInCamera();
        if (isFoggy || fluid != FogType.NONE || blinded(camera)) {
            return true;
        }
        AetheriaSkyRenderer.render(partialTick, modelViewMatrix, camera, projectionMatrix);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY,
                                double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        // true skips vanilla's clouds; under a shader pack the pack decides (and our cloud height is NaN anyway)
        return !ShaderPacks.inUse();
    }

    /**
     * The Deep's shade and weather in the lightmap. In the Deep the sky gives only its share of light
     * ({@link DeepShade}, the rule the Hollow Stalker sees by), eased with the sky's Deep weight through Shear band B.
     * A Solar Flare ({@link SkyWeather#setSolenneFlare}) turns sky-lit ground a harsher gold. The Eclipse Surge
     * ({@link SkyWeather#setSkyDim}): the sky light's share drops, while block light keeps its strength, so torches
     * and lichen are what light the ground.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        float ambient = level.dimensionType().ambientLight();
        float share = DeepShade.share(SkyState.frame().layers[2]);
        if (share < 1f) {
            DeepShade.apply(colors, ambient, pixelX, pixelY, skyLight, skyDarken, blockLightRedFlicker, share,
                    Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(partialTicks));
        }
        float flare = (float) SkyState.WEATHER.solenneFlare;
        if (flare > 0f) {
            // Solenne flaring: sky-lit ground turns a harsher gold
            float k = 0.25f * flare * pixelY / 15f;
            colors.set(Math.min(1f, colors.x * (1f + 0.4f * k)), Math.min(1f, colors.y * (1f + 0.15f * k)), colors.z * (1f - k));
        }
        float dim = (float) SkyState.WEATHER.skyDim;
        if (dim > 0f) {
            // block light as drawn here (in the Deep with the ambient glow at its share)
            Vector3f block = DeepShade.blockColour(DeepShade.brightness(ambient * share, pixelX) * blockLightRedFlicker, new Vector3f());
            float keep = 1f - 0.6f * dim;
            colors.set(Math.max(colors.x * keep, block.x), Math.max(colors.y * keep, block.y), Math.max(colors.z * keep, block.z));
        }
        float fight = (float) fightDim.getAsDouble();
        if (fight > 0f && !(pixelX == 15 && pixelY == 15)) {
            // a fight's darkness over everything lit (the Heliarch's eclipse); full-bright things keep their light
            colors.mul(1f - Math.min(1f, fight));
        }
    }

    /**
     * A fight's darkening of everything the lightmap lights, 0..1 (the Hollow Heliarch's eclipse sets it, G9p). The
     * full-bright entry (block and sky light 15, what glowing things and the boss itself draw with) keeps its light.
     */
    public static java.util.function.DoubleSupplier fightDim = () -> 0.0;

    static boolean blinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
