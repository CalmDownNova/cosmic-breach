package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** The Breach's core shader, {@code cosmicbreach:breach_sky}: compiled at resource load, never on first use. */
public final class BreachShaders {
    private static @Nullable ShaderInstance sky;

    private BreachShaders() {
    }

    static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CosmicBreach.id("breach_sky"),
                    DefaultVertexFormat.POSITION_TEX), shader -> sky = shader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not load the cosmicbreach:breach_sky shader", e);
        }
    }

    /** The Breach's sky shader; vanilla's textured one until ours has loaded. */
    public static ShaderInstance sky() {
        ShaderInstance shader = sky;
        return shader != null ? shader : GameRenderer.getPositionTexShader();
    }

    public static boolean ready() {
        return sky != null;
    }
}
