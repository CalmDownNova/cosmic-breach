package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The effects drawn as a few shaped quads instead of particles (GDD section 11: big effects are
 * quads): slash ribbons, the Zenith pillar, the Meridian Line, glow flashes. Each ticks with the
 * game and draws every frame in {@code AFTER_PARTICLES}, interpolated with the partial tick, with
 * {@link FxRenderTypes#additive}. Also carries timed jobs (a delayed start, a trail that follows an
 * entity for a few ticks). Client thread only.
 */
public final class WorldFx {
    /** One effect. */
    public interface Effect {
        /** A game tick passed; false once the effect is over. */
        boolean tick();

        /** Draws the effect for this frame. */
        default void render(Frame frame) {
        }
    }

    /** What a frame's effects draw with: camera-relative positions, the time, the buffers. */
    public record Frame(Camera camera, Vec3 cameraPos, float partialTick, double now, MultiBufferSource buffers) {
        /** A world position relative to the camera, as the renderer wants it. */
        public Vec3 relative(Vec3 world) {
            return world.subtract(cameraPos);
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Effect> ADDED = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 17));
    private static boolean ticking;

    private WorldFx() {
    }

    public static void add(Effect effect) {
        (ticking ? ADDED : EFFECTS).add(effect);
    }

    /** Runs {@code action} in the {@code ticks}-th game tick from now (1 or less: the next tick). */
    public static void after(int ticks, Runnable action) {
        add(new Effect() {
            private int left = ticks;

            @Override
            public boolean tick() {
                if (--left <= 0) {
                    action.run();
                    return false;
                }
                return true;
            }
        });
    }

    /** Calls {@code each} with the entity once a tick for {@code ticks} ticks, while it is there. */
    public static void follow(Entity entity, int ticks, Consumer<Entity> each) {
        add(new Effect() {
            private int left = ticks;

            @Override
            public boolean tick() {
                if (entity.isRemoved() || left-- <= 0) {
                    return false;
                }
                each.accept(entity);
                return true;
            }
        });
    }

    public static int count() {
        return EFFECTS.size() + ADDED.size();
    }

    /** Once per client tick while the level runs. */
    static void tick() {
        ticking = true;
        try {
            EFFECTS.removeIf(effect -> !effect.tick());
        } finally {
            ticking = false;
        }
        EFFECTS.addAll(ADDED);
        ADDED.clear();
    }

    static void clear() {
        EFFECTS.clear();
        ADDED.clear();
    }

    /** Draws every effect; also puts back the blend our particles left behind. */
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        BladeTracker.levelProjection(event.getProjectionMatrix());
        if (!EFFECTS.isEmpty()) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Camera camera = event.getCamera();
            Frame frame = new Frame(camera, camera.getPosition(), partialTick, FxClock.now(partialTick), BUFFERS);
            for (Effect effect : EFFECTS) {
                effect.render(frame);
            }
            BUFFERS.endBatch();
        }
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ drawing helpers

    /** One vertex of an additive quad (position relative to the camera). */
    public static void vertex(VertexConsumer out, Vec3 p, float u, float v, float r, float g, float b, float a) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(r, g, b, a);
    }

    /** A quad facing the camera round its centre {@code at} (camera-relative), {@code half} across. */
    public static void billboard(VertexConsumer out, Camera camera, Vec3 at, float half, float r, float g, float b, float a) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vec3 l = new Vec3(left.x() * half, left.y() * half, left.z() * half);
        Vec3 u = new Vec3(up.x() * half, up.y() * half, up.z() * half);
        vertex(out, at.subtract(l).subtract(u), 1f, 1f, r, g, b, a);
        vertex(out, at.subtract(l).add(u), 1f, 0f, r, g, b, a);
        vertex(out, at.add(l).add(u), 0f, 0f, r, g, b, a);
        vertex(out, at.add(l).subtract(u), 0f, 1f, r, g, b, a);
    }

    /** A square lying flat, {@code half} from its centre {@code at} (camera-relative) to each side. */
    public static void flat(VertexConsumer out, Vec3 at, float half, float r, float g, float b, float a) {
        vertex(out, at.add(-half, 0, -half), 0f, 0f, r, g, b, a);
        vertex(out, at.add(-half, 0, half), 0f, 1f, r, g, b, a);
        vertex(out, at.add(half, 0, half), 1f, 1f, r, g, b, a);
        vertex(out, at.add(half, 0, -half), 1f, 0f, r, g, b, a);
    }

    /** Red, green and blue of 0xRRGGBB as 0 to 1. */
    public static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    /** True if the camera shows the local player from its own eyes. */
    public static boolean firstPersonOf(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }
}
