package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.guardian.unsung.SongNote;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Homing Note (Unsung design v1): a pale-gold light that swells at the Alto's mouth through the beat it forms, then
 * flies visible the whole way, trailing a thin streak, with a ring of sound turning round it. Half a beat before it
 * bursts the ring closes in and the light goes white-hot, so the beat it lands on can be seen coming. Drawn as light
 * (additive) over a gold halo with ordinary blending, so it reads in the dark apse and against the lit windows alike.
 */
public class SongNoteRenderer extends EntityRenderer<SongNote> {
    static final ResourceLocation RING = CosmicBreach.id("textures/fx/note_ring.png");
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Map<SongNote, ArrayDeque<Vec3>> TRAILS = new WeakHashMap<>();

    public SongNoteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(SongNote note) {
        return ShardDraw.GLOW;
    }

    @Override
    public boolean shouldRender(SongNote note, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(SongNote note, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Camera camera = entityRenderDispatcher.camera;
        double now = note.level().getGameTime() + partialTick;
        double age = now - note.formed();
        Vec3 world = note.getPosition(partialTick).add(0, note.getBbHeight() / 2.0, 0);
        Vec3 cam = camera.getPosition();
        Vec3 at = world.subtract(cam);
        float[] gold = ShardDraw.rgb(note.voice().glow);
        float[] deep = ShardDraw.rgb(note.voice().accent);
        double form = Math.max(0.0, Math.min(1.0, age / UnsungMoves.NOTE_FORM));
        double toLand = note.lands() - now;
        double hot = toLand < UnsungMoves.HALF_BEAT ? 1.0 - Math.max(0.0, toLand) / UnsungMoves.HALF_BEAT : 0.0;
        float size = (float) (0.28 + 0.42 * form + 0.15 * hot);
        // the trail: where it has been, fading
        ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(note, n -> new ArrayDeque<>());
        if (trail.isEmpty() || trail.peekLast().distanceToSqr(world) > 0.01) {
            trail.addLast(world);
            while (trail.size() > 10) {
                trail.pollFirst();
            }
        }
        if (age >= UnsungMoves.NOTE_FORM && trail.size() > 1) {
            Vec3 prev = null;
            int i = 0;
            int n = trail.size();
            for (Vec3 p : trail) {
                if (prev != null) {
                    float a = 0.5f * i / n;
                    TelegraphDraw.ribbon(buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, prev.subtract(cam), p.subtract(cam),
                            0.08 + 0.22 * i / n, gold, a);
                }
                prev = p;
                i++;
            }
        }
        // a gold halo that holds on bright floors, then the light
        TelegraphDraw.glow(buffers.getBuffer(ShardDraw.translucent(ShardDraw.GLOW, true)), camera, at, size * 2.2f, deep, 0.45f);
        // (a buffer is fetched right before each use: the entity buffers share one for every custom type)
        TelegraphDraw.glow(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, size * 2.0f, gold, 1.0f);
        TelegraphDraw.glow(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, size * 1.1f, gold, 1.0f);
        float core = (float) (0.75 + 0.25 * hot);
        TelegraphDraw.glow(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, size * 0.55f, new float[] {1f, 1f, 1f}, core);
        // the ring of sound turning round it, closing in on the burst
        double ringSize = size * (1.6 - 0.8 * hot);
        double spin = now * 0.18;
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        Vec3 right = left.scale(Math.cos(spin)).add(up.scale(Math.sin(spin * 0.7) * 0.35)).normalize().scale(ringSize);
        Vec3 top = up.scale(Math.cos(spin * 0.7)).subtract(left.scale(Math.sin(spin) * 0.25)).normalize().scale(ringSize * 0.55);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(RING)), IDENTITY, at, right, top, gold[0], gold[1], gold[2],
                (float) (0.55 + 0.45 * hot) * (float) form);
    }
}
