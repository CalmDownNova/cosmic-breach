package com.cosmicbreach.client.entity;

import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.entity.shardling.Shardling;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where a Shardling's spine tips, throat and mouth were when it was last drawn, in the world and as
 * animated: the telegraphs' glints sit on them. {@link ShardlingRenderer} keeps the pose GeckoLib
 * draws each of these bones' cubes with (it maps the model's baked coordinates, in blocks, to the
 * camera-relative frame) and hands them over after the frame; the points themselves come from the
 * baked cubes, so they follow the generated model if it changes. (GeckoLib's own tracked world
 * matrices are only right at a bone's pivot: its translate helper adds an identity matrix.)
 * A Shardling that isn't being drawn has no points, and needs no glints either. Client thread.
 */
public final class ShardlingPoints {
    static final String[] SPINES = {"spine_1", "spine_2", "spine_3", "spine_4", "spine_5", "spine_6"};
    static final String HEAD = "head";
    /** A glint sits this far from a spine's root to its point: on the crystal, not past it. */
    private static final float ALONG_SPINE = 0.9f;
    /** Points older than this many ticks are stale (the Shardling is out of sight). */
    private static final long FRESH_TICKS = 2;

    public record Points(Vec3[] spineTips, Vec3 throat, Vec3 mouth, long tick) {
    }

    /** The same points in the model's own coordinates (blocks, GeckoLib's axes), worked out once per model. */
    private record Local(Vector3f[] spineTips, Vector3f throat, Vector3f mouth) {
    }

    private static final Map<Shardling, Points> LAST = new WeakHashMap<>();
    private static @Nullable BakedGeoModel localFor;
    private static @Nullable Local local;

    private ShardlingPoints() {
    }

    /** True for the bones whose cube pose the renderer should keep. */
    static boolean tracks(String bone) {
        if (bone.equals(HEAD)) {
            return true;
        }
        for (String spine : SPINES) {
            if (spine.equals(bone)) {
                return true;
            }
        }
        return false;
    }

    /** After drawing {@code shardling}: {@code poses} are its bones' cube poses this frame, relative to {@code camera}. */
    static void record(Shardling shardling, BakedGeoModel model, Map<String, Matrix4f> poses, Vec3 camera) {
        Local points = localPoints(model);
        if (points == null) {
            return;
        }
        Vec3[] tips = new Vec3[SPINES.length];
        for (int i = 0; i < SPINES.length; i++) {
            Matrix4f pose = poses.get(SPINES[i]);
            if (pose == null) {
                return;
            }
            tips[i] = world(pose, points.spineTips()[i], camera);
        }
        Matrix4f head = poses.get(HEAD);
        if (head == null) {
            return;
        }
        LAST.put(shardling, new Points(tips, world(head, points.throat(), camera), world(head, points.mouth(), camera), FxClock.ticks()));
    }

    private static Vec3 world(Matrix4f pose, Vector3f local, Vec3 camera) {
        Vector3f p = pose.transformPosition(new Vector3f(local));
        return new Vec3(camera.x + p.x, camera.y + p.y, camera.z + p.z);
    }

    /**
     * Spine tips: 90% of the way from each spine bone's pivot to the top face of its last cube (the
     * point). Throat: the underside of the head's second cube (the snout's jaw). Mouth: the front of
     * the third (the nose). The head faces -Z.
     */
    private static @Nullable Local localPoints(BakedGeoModel model) {
        if (model == localFor) {
            return local;
        }
        localFor = model;
        local = null;
        Vector3f[] tips = new Vector3f[SPINES.length];
        for (int i = 0; i < SPINES.length; i++) {
            GeoBone bone = model.getBone(SPINES[i]).orElse(null);
            if (bone == null || bone.getCubes().isEmpty()) {
                return null;
            }
            Vector3f pivot = new Vector3f(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
            Vector3f top = face(bone.getCubes().get(bone.getCubes().size() - 1), 1, true);
            tips[i] = pivot.lerp(top, ALONG_SPINE, new Vector3f());
        }
        GeoBone head = model.getBone(HEAD).orElse(null);
        if (head == null) {
            return null;
        }
        List<GeoCube> cubes = head.getCubes();
        Vector3f pivot = new Vector3f(head.getPivotX() / 16f, head.getPivotY() / 16f, head.getPivotZ() / 16f);
        Vector3f throat = cubes.size() > 1 ? face(cubes.get(1), 1, false) : pivot;
        Vector3f mouth = cubes.size() > 2 ? face(cubes.get(2), 2, false) : pivot;
        local = new Local(tips, throat, mouth);
        return local;
    }

    /** The centre of a cube's face: the vertices at the highest ({@code max}) or lowest value on {@code axis}. */
    private static Vector3f face(GeoCube cube, int axis, boolean max) {
        float best = max ? -Float.MAX_VALUE : Float.MAX_VALUE;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                float v = vertex.position().get(axis);
                best = max ? Math.max(best, v) : Math.min(best, v);
            }
        }
        Vector3f sum = new Vector3f();
        int n = 0;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                if (Math.abs(vertex.position().get(axis) - best) < 1e-4f) {
                    sum.add(vertex.position());
                    n++;
                }
            }
        }
        return n == 0 ? sum : sum.div(n);
    }

    /** The points from the last frame this Shardling was drawn in, if that was just now. */
    public static @Nullable Points of(Shardling shardling) {
        Points points = LAST.get(shardling);
        return points != null && FxClock.ticks() - points.tick() <= FRESH_TICKS ? points : null;
    }

    static void clear() {
        LAST.clear();
    }
}
