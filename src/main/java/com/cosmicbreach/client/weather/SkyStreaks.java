package com.cosmicbreach.client.weather;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.sky.SkyFrame;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.LayerWeather;
import com.cosmicbreach.world.weather.WeatherKind;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Meteor Shower in the sky (a {@link com.cosmicbreach.client.sky.SkyWeather} layer): short red streaks that
 * burn across the dome away from a radiant low on the shower's heading. They start with the warning, a few at
 * first and more as it runs on, and keep falling through the shower. Drawn only for a shower in the camera's
 * own layer.
 */
final class SkyStreaks {
    /** How far out the streaks are drawn (inside the sky's 50-block dome, well within the far plane). */
    private static final float DISTANCE = 40f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static @Nullable VertexBuffer buffer;
    private static int drawn;

    /** One streak: its start direction, the direction it burns towards, its age and life in ticks. */
    private record Streak(Vector3f start, Vector3f tangent, float speed, float length, float width, float brightness,
                          int life, int[] age) {}

    private SkyStreaks() {
    }

    /** Streaks drawn in the last frame (for checks). */
    static int drawn() {
        return drawn;
    }

    static int live() {
        return STREAKS.size();
    }

    static void clear() {
        STREAKS.clear();
    }

    static void tick(Minecraft mc) {
        STREAKS.removeIf(s -> ++s.age()[0] >= s.life());
        LayerWeather w = CosmicWeather.client(Layer.at(mc.gameRenderer.getMainCamera().getPosition().y));
        double perSecond;
        if (w.is(WeatherKind.SHOWER, Phase.WARNING)) {
            perSecond = 5.0 + 13.0 * w.progress(mc.level.getGameTime());
        } else if (w.is(WeatherKind.SHOWER, Phase.ACTIVE)) {
            perSecond = 18.0;
        } else {
            return;
        }
        double expected = perSecond / 20.0;
        while (expected > 0 && STREAKS.size() < 96) {
            if (RANDOM.nextDouble() < expected) {
                STREAKS.add(spawn(w.heading()));
            }
            expected -= 1.0;
        }
    }

    private static Streak spawn(float heading) {
        // the radiant: low over the heading; streaks run away from it
        Vector3f radiant = new Vector3f((float) Math.cos(heading), 0.35f, (float) Math.sin(heading)).normalize();
        Vector3f start;
        do {
            double yaw = heading + (RANDOM.nextDouble() - 0.5) * Math.PI * 1.3;
            double elevation = Math.toRadians(18 + RANDOM.nextDouble() * 52);
            start = new Vector3f((float) (Math.cos(yaw) * Math.cos(elevation)), (float) Math.sin(elevation),
                    (float) (Math.sin(yaw) * Math.cos(elevation)));
        } while (start.dot(radiant) > 0.97f);
        // the great circle away from the radiant, turned a little downwards
        Vector3f tangent = new Vector3f(start).mul(start.dot(radiant)).sub(radiant).normalize();
        tangent.add(0f, -0.25f, 0f);
        tangent.sub(new Vector3f(start).mul(tangent.dot(start))).normalize();
        float speed = (float) Math.toRadians(30 + RANDOM.nextDouble() * 30) / 20f; // radians a tick
        float length = (float) Math.toRadians(10 + RANDOM.nextDouble() * 12);
        float width = 0.35f + RANDOM.nextFloat() * 0.3f;
        float brightness = 0.65f + RANDOM.nextFloat() * 0.35f;
        int life = 12 + RANDOM.nextInt(12);
        return new Streak(start, tangent, speed, length, width, brightness, life, new int[] {0});
    }

    /** The {@link com.cosmicbreach.client.sky.SkyWeather.Layer}: blend is ONE, ONE on entry and left so. */
    static void render(Matrix4f view, Matrix4f projection, Camera camera, float partialTick, SkyFrame frame, PoseStack scratch) {
        drawn = 0;
        if (STREAKS.isEmpty()) {
            return;
        }
        ShaderInstance shader = GameRenderer.getPositionTexColorShader();
        if (shader == null) {
            return;
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        // two passes: a red halo blended over the sky (red reads on a daylight blue, where added light would
        // only whiten it), then the white-hot core added on top
        RenderSystem.setShaderTexture(0, FxRenderTypes.BEAM);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawn = pass(view, projection, shader, partialTick, true);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        pass(view, projection, shader, partialTick, false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }

    private static int pass(Matrix4f view, Matrix4f projection, ShaderInstance shader, float partialTick, boolean halo) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int n = 0;
        for (Streak s : STREAKS) {
            float age = s.age()[0] + partialTick;
            float t = age / s.life();
            float fade = Math.min(1f, age / 3f) * (1f - t * t);
            if (fade <= 0.01f) {
                continue;
            }
            float head = s.speed() * age;
            float tail = Math.max(0f, head - s.length());
            Vector3f a = along(s, tail).mul(DISTANCE);
            Vector3f b = along(s, head).mul(DISTANCE);
            Vector3f side = new Vector3f(b).sub(a).cross(new Vector3f(a).add(b)).normalize();
            float glow = fade * s.brightness();
            if (halo) {
                quad(builder, a, b, side, s.width() * 2.4f, 0.86f, 0.1f, 0.02f, Math.min(1f, glow * 1.1f));
            } else {
                quad(builder, a, b, side, s.width() * 0.8f, 1.0f, 0.72f, 0.45f, glow);
            }
            n++;
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            return 0;
        }
        buffer.bind();
        buffer.upload(mesh);
        buffer.drawWithShader(view, projection, shader);
        VertexBuffer.unbind();
        return n;
    }

    private static Vector3f along(Streak s, float angle) {
        return new Vector3f(s.start()).mul((float) Math.cos(angle)).add(new Vector3f(s.tangent()).mul((float) Math.sin(angle)));
    }

    /** A strip from tail {@code a} (faded out) to head {@code b} (full), {@code width} across; v runs along it. */
    private static void quad(BufferBuilder out, Vector3f a, Vector3f b, Vector3f side, float width, float r, float g,
                             float bl, float alpha) {
        float tw = width * 0.35f;
        out.addVertex(a.x - side.x * tw, a.y - side.y * tw, a.z - side.z * tw).setUv(0f, 0.2f).setColor(r, g, bl, 0f);
        out.addVertex(b.x - side.x * width, b.y - side.y * width, b.z - side.z * width).setUv(0f, 0.85f).setColor(r, g, bl, alpha);
        out.addVertex(b.x + side.x * width, b.y + side.y * width, b.z + side.z * width).setUv(1f, 0.85f).setColor(r, g, bl, alpha);
        out.addVertex(a.x + side.x * tw, a.y + side.y * tw, a.z + side.z * tw).setUv(1f, 0.2f).setColor(r, g, bl, 0f);
    }
}
