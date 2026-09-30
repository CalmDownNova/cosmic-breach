package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Afterimages: a player's body left glowing where it was, in the pose it had, fading out (the Binary Edges'
 * dashes and the blink's start). A request is captured at the next frame: the player is drawn once more
 * through its own renderer (armour, held blades and all) into a recorder instead of the screen, which keeps
 * every quad where it was in the world; the ghost then redraws those quads additively in one colour, fainter
 * each tick. Capturing tracks no blade ({@link BladeTracker#suppress}) and draws no name tag.
 */
public final class Afterimages {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Quads kept per afterimage at most (a player with armour and two blades draws far fewer). */
    private static final int MAX_QUADS = 6000;

    private record Request(Player player, Vec3 at, int color, float alpha, int life) {
    }

    private static final List<Request> PENDING = new ArrayList<>();
    private static boolean capturing;
    private static boolean reportedError;

    private Afterimages() {
    }

    /**
     * Leaves an afterimage of {@code player} in its current pose at {@code at} (its feet; null: where it is now),
     * {@code color}, {@code alpha} at its brightest, fading over {@code life} ticks.
     */
    public static void leave(Player player, Vec3 at, int color, float alpha, int life) {
        if (PENDING.size() < 16) {
            PENDING.add(new Request(player, at, color, alpha, life));
        }
    }

    /** True while an afterimage is being captured (a second draw of a player that must leave no trace). */
    public static boolean capturing() {
        return capturing;
    }

    /** From the level's drawing: captures the afterimages asked for since the last frame. */
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PENDING.isEmpty()) {
            return;
        }
        List<Request> requests = new ArrayList<>(PENDING);
        PENDING.clear();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        for (Request request : requests) {
            if (request.player().isRemoved() || request.player().isInvisible()) {
                continue;
            }
            try {
                float[] quads = capture(request.player(), request.at(), camera.getPosition(), partialTick);
                if (quads.length > 0) {
                    WorldFx.add(new Ghost(quads, request.color(), request.alpha(), request.life()));
                }
            } catch (RuntimeException e) {
                if (!reportedError) {
                    reportedError = true;
                    LOGGER.error("[cosmicbreach] could not capture a visual effect", e);
                }
            }
        }
    }

    /** No name tag on a player drawn for an afterimage. */
    static void onNameTag(RenderNameTagEvent event) {
        if (capturing) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** The player's quads as drawn at {@code feet}, in world coordinates, 12 floats a quad. */
    private static float[] capture(Player player, Vec3 feet, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super Player> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        Vec3 at = feet != null ? feet : player.getPosition(partialTick);
        PoseStack pose = new PoseStack();
        pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        Recorder recorder = new Recorder(camera);
        MultiBufferSource source = type -> recorder;
        capturing = true;
        BladeTracker.suppress(true);
        try {
            renderer.render(player, Mth.rotLerp(partialTick, player.yRotO, player.getYRot()), partialTick, pose, source,
                    LightTexture.FULL_BRIGHT);
        } finally {
            BladeTracker.suppress(false);
            capturing = false;
        }
        return recorder.quads();
    }

    /** Takes in drawn vertices, keeps their positions (camera-relative in, world out). */
    private static final class Recorder implements VertexConsumer {
        private final Vec3 camera;
        private final float[] data = new float[MAX_QUADS * 12];
        private int vertices;

        Recorder(Vec3 camera) {
            this.camera = camera;
        }

        float[] quads() {
            int whole = (vertices / 4) * 12;
            float[] out = new float[whole];
            System.arraycopy(data, 0, out, 0, whole);
            return out;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (vertices < MAX_QUADS * 4) {
                int i = vertices * 3;
                data[i] = (float) (x + camera.x);
                data[i + 1] = (float) (y + camera.y);
                data[i + 2] = (float) (z + camera.z);
                vertices++;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    /**
     * One afterimage: the captured quads, redrawn in one glowing colour, fading. A ghost near the camera (the
     * local player's own, left where its eyes still are) fades out, so it never fills the view.
     */
    private static final class Ghost implements WorldFx.Effect {
        private static final double NEAR = 1.2;
        private static final double CLEAR = 2.6;
        private final float[] quads;
        private final float[] color;
        private final float alpha;
        private final int life;
        private final Vec3 centre;
        private final double born = FxClock.ticks();

        Ghost(float[] quads, int color, float alpha, int life) {
            this.quads = quads;
            this.color = WorldFx.rgb(color);
            this.alpha = alpha;
            this.life = Math.max(1, life);
            double x = 0;
            double y = 0;
            double z = 0;
            int n = quads.length / 3;
            for (int i = 0; i < quads.length; i += 3) {
                x += quads[i];
                y += quads[i + 1];
                z += quads[i + 2];
            }
            this.centre = n == 0 ? Vec3.ZERO : new Vec3(x / n, y / n, z / n);
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - born < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double t = (f.now() - born) / life;
            if (t < 0 || t >= 1) {
                return;
            }
            Vec3 cam = f.cameraPos();
            double near = Math.max(0.0, Math.min(1.0, (centre.distanceTo(cam) - NEAR) / (CLEAR - NEAR)));
            float a = (float) (alpha * (1.0 - t) * (1.0 - t * 0.5) * near * near);
            if (a <= 0.003f) {
                return;
            }
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            for (int i = 0; i + 2 < quads.length; i += 3) {
                out.addVertex((float) (quads[i] - cam.x), (float) (quads[i + 1] - cam.y), (float) (quads[i + 2] - cam.z))
                        .setUv(0.5f, 0.5f).setColor(color[0], color[1], color[2], a);
            }
        }
    }
}
