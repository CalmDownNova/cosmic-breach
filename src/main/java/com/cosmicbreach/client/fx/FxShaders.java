package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The effects' core shader, {@code cosmicbreach:fx_additive} ({@code assets/cosmicbreach/shaders/core/}):
 * textured light for additive blending with no alpha cut-off (vanilla's position_tex_color drops
 * everything under 10% alpha, which puts a hard edge on every fade) and a fog fade.
 */
public final class FxShaders {
    private static @Nullable ShaderInstance additive;

    private FxShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CosmicBreach.id("fx_additive"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> additive = shader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not load the cosmicbreach:fx_additive shader", e);
        }
    }

    /** The additive effect shader; vanilla's textured one until ours has loaded. */
    public static ShaderInstance additive() {
        ShaderInstance shader = additive;
        return shader != null ? shader : GameRenderer.getPositionTexColorShader();
    }
}
