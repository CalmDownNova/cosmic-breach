package com.cosmicbreach.client.sky;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sky's core shaders ({@code assets/cosmicbreach/shaders/core/sky_*}): the dome with its nebula, the
 * stars, the glows (Solenne, Vesper), Thalassa, the aurora. All share {@code include/sky_common.glsl}.
 * Null until the resources have loaded; {@link AetheriaSkyRenderer} draws nothing until then.
 */
public final class SkyShaders {
    static @Nullable ShaderInstance dome;
    static @Nullable ShaderInstance stars;
    static @Nullable ShaderInstance glow;
    static @Nullable ShaderInstance body;
    static @Nullable ShaderInstance aurora;

    private SkyShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        load(event, "sky_dome", DefaultVertexFormat.POSITION, s -> dome = s);
        load(event, "sky_stars", DefaultVertexFormat.POSITION_TEX_COLOR, s -> stars = s);
        load(event, "sky_glow", DefaultVertexFormat.POSITION_TEX_COLOR, s -> glow = s);
        load(event, "sky_body", DefaultVertexFormat.POSITION, s -> body = s);
        load(event, "sky_aurora", DefaultVertexFormat.POSITION_TEX_COLOR, s -> aurora = s);
    }

    static boolean ready() {
        return dome != null && stars != null && glow != null && body != null && aurora != null;
    }

    private static void load(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> onLoad) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CosmicBreach.id(name), format), onLoad);
        } catch (IOException e) {
            throw new UncheckedIOException("could not load the cosmicbreach:" + name + " shader", e);
        }
    }
}
