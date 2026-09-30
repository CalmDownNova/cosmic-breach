package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.combat.data.MoveDef;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A slash's trail, drawn off the blade itself: the path the drawn blade took, read frame by frame by
 * {@link BladeTracker}, over the move's swing ({@link TrailMath#window}: from the end of its wind-up
 * through its follow-through). So it sits wherever the sword really swings, and a hit-stop that holds
 * the blade holds its trail too. Layers, with {@code textures/fx/slash.png} for their soft profile
 * across (the fade along is the trail's own):
 * <ul>
 *   <li>the body: a band along the tip's path turned to face the camera, widest and brightest at the
 *       blade and tapering to nothing behind it, like a comet;</li>
 *   <li>the sweep: the band the blade passed through, in the swing's own plane, from partway up the blade
 *       to beyond its tip, brightest along the tip's path: the crescent when the swing is seen face on.
 *       It fades as the swing turns edge on to the camera, where it would only be a line;</li>
 *   <li>the edge: a thin near-white core along the tip's path, ending crisp at the blade;</li>
 *   <li>a small flare where the tip is, while it moves fast.</li>
 * </ul>
 *
 * <p>Seen by everyone else (and from outside) it is built in the world, the sweep reaching well past the
 * tip and the body and core never thinner on screen than a set angle, so a slash reads from across an
 * arena. Through the attacker's own eyes the framed blade turns with the camera, so the trail is built
 * on the screen: from the blade's positions in the camera's frame, cut where the path passes behind
 * the eye, sized in view angles, faded before the view's edges (so it ends inside the view unless the
 * blade itself left it), and a stroke that runs nearly straight across the view, like a chop coming
 * down its middle, is bowed into a crescent. The move's other slash hints (from, to, roll, radius)
 * describe the arc for anything that draws one without the blade; this uses the colour and width.
 */
final class SlashTrail implements WorldFx.Effect {
    // Seen from outside, in blocks (the body and core also never thinner than their angle, in radians).
    private static final double WORLD_INNER = 0.35;
    private static final double WORLD_OUTER = 0.45;
    private static final float WORLD_SWEEP_ALPHA = 0.6f;
    private static final double WORLD_BODY = 0.18;
    private static final double WORLD_BODY_ANGLE = 0.02;
    private static final float WORLD_BODY_ALPHA = 0.75f;
    private static final double WORLD_CORE = 0.032;
    private static final double WORLD_CORE_ANGLE = 0.004;

    // Through the attacker's eyes, in view units (the tangent of the angle off the view's centre).
    private static final double VIEW_INNER = 0.4;
    private static final double VIEW_OUTER = 0.2;
    private static final float VIEW_SWEEP_ALPHA = 0.55f;
    private static final double VIEW_BODY = 0.07;
    private static final float VIEW_BODY_ALPHA = 0.72f;
    private static final double VIEW_CORE = 0.011;
    /** Blade positions closer to the eye than this (blocks ahead) are cut: the path went over or behind the head. */
    private static final double VIEW_NEAREST = 0.3;

    /** Texture u: the sweep samples the trail part (soft across), the body and core the crisp end (flat, soft edges). */
    private static final float SWEEP_U = 0.95f;
    private static final float BODY_U = 0.975f;
    private static final double SPACING = 0.06;
    private static final int MAX_AGE_TICKS = 80;

    private final Player player;
    private final ResourceLocation animation;
    /** The off hand's blade (a weapon with two), else the main hand's. */
    private final boolean off;
    private final double from;
    private final double to;
    private final double width;
    private final float[] color;
    private final float[] coreColor;
    private final double created;
    private double fadeStart = Double.NaN;

    SlashTrail(Player player, MoveDef def) {
        this(player, def, def.animation(), false, def.slash().map(MoveDef.Slash::color).orElse(CombatEffects.WHITE_GOLD));
    }

    /**
     * The trail of one blade through {@code def}'s swing, as {@code animation} plays it (a weapon may play
     * another animation for the move, see {@code TwinBlades}), in {@code rgb}: the off hand's blade if {@code off}.
     */
    SlashTrail(Player player, MoveDef def, ResourceLocation animation, boolean off, int rgb) {
        this.player = player;
        this.animation = animation;
        this.off = off;
        double[] window = TrailMath.window(def.timing().startup(), def.timing().active());
        this.from = window[0];
        this.to = window[1];
        this.width = def.slash().map(MoveDef.Slash::width).orElse(1.0f);
        this.color = WorldFx.rgb(rgb);
        float white = 0.7f;
        this.coreColor = new float[] {color[0] + (1f - color[0]) * white, color[1] + (1f - color[1]) * white,
                color[2] + (1f - color[2]) * white};
        this.created = FxClock.ticks();
    }

    @Override
    public boolean tick() {
        double now = FxClock.ticks();
        if (player.isRemoved()) {
            return false;
        }
        if (Double.isNaN(fadeStart) && swingOver()) {
            fadeStart = now;
        }
        if (!Double.isNaN(fadeStart)) {
            return now - fadeStart < TrailMath.FADE_TICKS;
        }
        return now - created < MAX_AGE_TICKS;
    }

    /** The player's layer moved on to something else, or past this swing's follow-through. */
    private boolean swingOver() {
        if (!(player instanceof AbstractClientPlayer client)) {
            return true;
        }
        PlayerAnimations.State state = PlayerAnimations.state(client);
        return !animation.equals(state.animation()) || state.time() > to;
    }

    // ------------------------------------------------------------------ drawing

    /** One point of the trail: where the tip and the guard were, and when (animation time). */
    private record Sample(Vec3 tip, Vec3 guard, double time) {
        Sample lerp(Sample o, double t) {
            return new Sample(tip.add(o.tip.subtract(tip).scale(t)), guard.add(o.guard.subtract(guard).scale(t)),
                    time + (o.time - time) * t);
        }
    }

    @Override
    public void render(WorldFx.Frame f) {
        List<BladeTracker.BladePose> poses = swingPoses();
        if (poses.size() < 2) {
            return;
        }
        BladeTracker.BladePose newest = poses.get(poses.size() - 1);
        double head = newest.animationTime();
        double fade = Double.isNaN(fadeStart) ? 0.0 : Math.min(1.0, Math.max(0.0, (f.now() - fadeStart) / TrailMath.FADE_TICKS));
        double length = TrailMath.TRAIL_TICKS * (1.0 - fade);
        if (length < 0.05) {
            return;
        }
        double fadeLength = TrailMath.fadeLength(length, head, from);
        double tail = head - length;
        int first = 0;
        for (int i = poses.size() - 1; i >= 0; i--) {
            if (poses.get(i).animationTime() < tail) {
                first = i;
                break;
            }
        }
        List<BladeTracker.BladePose> used = poses.subList(first, poses.size());
        if (newest.space() == BladeTracker.Space.WORLD) {
            List<Sample> samples = new ArrayList<>();
            for (BladeTracker.BladePose pose : used) {
                samples.add(new Sample(pose.tip().subtract(f.cameraPos()), pose.guard().subtract(f.cameraPos()), pose.animationTime()));
            }
            renderWorld(f, samples, head, fadeLength, fade);
        } else {
            List<Sample> samples = new ArrayList<>();
            for (BladeTracker.BladePose pose : used) {
                Vec3 tip = pose.space() == BladeTracker.Space.HAND ? BladeTracker.handToView(pose.tip()) : pose.tip();
                Vec3 guard = pose.space() == BladeTracker.Space.HAND ? BladeTracker.handToView(pose.guard()) : pose.guard();
                samples.add(new Sample(tip, guard, pose.animationTime()));
            }
            renderView(f, samples, head, fadeLength, fade);
        }
    }

    /** Smoothed samples, about {@link #SPACING} apart. */
    private static List<Sample> smooth(List<Sample> samples) {
        List<Vec3> tips = new ArrayList<>();
        for (Sample s : samples) {
            tips.add(s.tip);
        }
        TrailMath.Curve curve = TrailMath.smooth(tips, SPACING);
        List<Sample> out = new ArrayList<>();
        for (int j = 0; j < curve.points().size(); j++) {
            double at = curve.at().get(j);
            int i = Math.min((int) Math.floor(at), samples.size() - 2);
            double u = at - i;
            Vec3 guard = TrailMath.centripetal(samples.get(Math.max(0, i - 1)).guard, samples.get(i).guard, samples.get(i + 1).guard,
                    samples.get(Math.min(samples.size() - 1, i + 2)).guard, u);
            double time = samples.get(i).time + (samples.get(i + 1).time - samples.get(i).time) * u;
            out.add(new Sample(curve.points().get(j), guard, time));
        }
        return out;
    }

    // ------------------------------------------------------------------ in the world

    /** Built in the world; the samples are camera-relative. */
    private void renderWorld(WorldFx.Frame f, List<Sample> raw, double head, double fadeLength, double fade) {
        List<Sample> samples = smooth(raw);
        int n = samples.size();
        Vec3[] tip = new Vec3[n];
        Vec3[] guard = new Vec3[n];
        double[] strength = new double[n];
        double[] reach = new double[n];
        for (int j = 0; j < n; j++) {
            Sample s = samples.get(j);
            tip[j] = s.tip;
            guard[j] = s.guard;
            double age = Math.max(0.0, head - s.time);
            strength[j] = TrailMath.ageStrength(age, fadeLength) * (1.0 - fade);
            reach[j] = Math.max(0.0, 1.0 - age / fadeLength);
        }
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.SLASH));

        // The sweep, in the swing's plane.
        Vec3[] inner = new Vec3[n];
        Vec3[] outer = new Vec3[n];
        float[] sweepAlpha = new float[n];
        for (int j = 0; j < n; j++) {
            Vec3 blade = tip[j].subtract(guard[j]);
            double narrow = 0.5 + 0.5 * reach[j];
            inner[j] = tip[j].subtract(blade.scale((1.0 - WORLD_INNER) * narrow));
            outer[j] = tip[j].add(blade.scale(WORLD_OUTER * width * narrow));
            sweepAlpha[j] = (float) (WORLD_SWEEP_ALPHA * strength[j] * faceOn(tip, guard, j));
        }
        sweep(out, inner, tip, outer, sweepAlpha);

        // The body and the core, facing the camera, along the tip's path (a hair past the tip).
        Vec3[] edge = new Vec3[n];
        double[] body = new double[n];
        double[] core = new double[n];
        float[] bodyAlpha = new float[n];
        float[] coreAlpha = new float[n];
        for (int j = 0; j < n; j++) {
            edge[j] = tip[j].add(tip[j].subtract(guard[j]).scale(0.04));
            double distance = edge[j].length();
            body[j] = Math.max(WORLD_BODY, distance * WORLD_BODY_ANGLE) * Math.pow(reach[j], 0.6);
            core[j] = Math.max(WORLD_CORE, distance * WORLD_CORE_ANGLE) * (0.3 + 0.7 * reach[j]);
            bodyAlpha[j] = (float) (WORLD_BODY_ALPHA * strength[j]);
            coreAlpha[j] = (float) strength[j];
        }
        facingStrip(out, edge, body, bodyAlpha, color, false);
        facingStrip(out, edge, core, coreAlpha, coreColor, true);
        flare(f, edge, Math.max(0.14, edge[n - 1].length() * 0.02), fade, 0.4);
    }

    /** How face on the swing's plane is to the camera at sample {@code j}: 1 face on, 0 edge on. */
    private static double faceOn(Vec3[] tip, Vec3[] guard, int j) {
        int n = tip.length;
        Vec3 motion = tip[Math.min(n - 1, j + 1)].subtract(tip[Math.max(0, j - 1)]);
        Vec3 normal = tip[j].subtract(guard[j]).cross(motion);
        double nl = normal.length();
        double pl = tip[j].length();
        if (nl < 1e-9 || pl < 1e-9) {
            return 0.0;
        }
        double c = Math.abs(normal.dot(tip[j])) / (nl * pl);
        double t = Math.max(0.0, Math.min(1.0, (c - 0.08) / (0.45 - 0.08)));
        return t * t * (3.0 - 2.0 * t);
    }

    // ------------------------------------------------------------------ on the screen, through the attacker's eyes

    /** Built on the screen; the samples are in the camera's frame (x right, y up, -z ahead). */
    private void renderView(WorldFx.Frame f, List<Sample> raw, double head, double fadeLength, double fade) {
        // Cut where the path went over or behind the eye, keeping where it crossed back into view.
        List<Sample> cut = new ArrayList<>();
        for (int i = raw.size() - 1; i >= 0; i--) {
            Sample s = raw.get(i);
            if (-s.tip.z >= VIEW_NEAREST) {
                cut.add(0, s);
                continue;
            }
            if (!cut.isEmpty()) {
                Sample next = cut.get(0);
                double t = (VIEW_NEAREST + s.tip.z) / (s.tip.z - next.tip.z);
                cut.add(0, s.lerp(next, Math.max(0.0, Math.min(1.0, t))));
            }
            break;
        }
        if (cut.size() < 2) {
            return;
        }
        List<Sample> samples = smooth(cut);
        int n = samples.size();
        Minecraft mc = Minecraft.getInstance();
        double tanV = BladeTracker.worldFovTan();
        double tanH = tanV * mc.getWindow().getWidth() / Math.max(1.0, mc.getWindow().getHeight());

        // Screen positions, depth, strength and the fade before the view's edges.
        double[] sx = new double[n];
        double[] sy = new double[n];
        double[] depth = new double[n];
        double[] strength = new double[n];
        double[] reach = new double[n];
        double[] edge = new double[n];
        for (int j = 0; j < n; j++) {
            Sample s = samples.get(j);
            depth[j] = Math.max(1e-3, -s.tip.z);
            sx[j] = s.tip.x / depth[j];
            sy[j] = s.tip.y / depth[j];
            double age = Math.max(0.0, head - s.time);
            strength[j] = TrailMath.ageStrength(age, fadeLength) * (1.0 - fade);
            reach[j] = Math.max(0.0, 1.0 - age / fadeLength);
            edge[j] = Math.max(Math.abs(sx[j]) / tanH, Math.abs(sy[j]) / tanV);
        }
        double[] visible = new double[n];
        for (int j = 0; j < n; j++) {
            visible[j] = TrailMath.edgeFade(edge[j], edge[n - 1]);
        }

        // Bow a nearly straight stroke into a crescent, over its visible part.
        int start = 0;
        while (start < n - 2 && visible[start] * strength[start] < 0.01) {
            start++;
        }
        List<TrailMath.Point2> stroke = new ArrayList<>();
        for (int j = start; j < n; j++) {
            stroke.add(new TrailMath.Point2(sx[j], sy[j]));
        }
        double[] bow = TrailMath.bow(stroke);
        double chordX = sx[n - 1] - sx[start];
        double chordY = sy[n - 1] - sy[start];
        double chord = Math.hypot(chordX, chordY);
        double nx = chord < 1e-9 ? 0.0 : -chordY / chord;
        double ny = chord < 1e-9 ? 0.0 : chordX / chord;
        double[] ox = new double[n];
        double[] oy = new double[n];
        for (int j = start; j < n; j++) {
            ox[j] = nx * bow[j - start];
            oy[j] = ny * bow[j - start];
        }

        Quaternionf toWorld = new Quaternionf(f.camera().rotation());
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.SLASH));

        // The sweep, in the swing's plane, moved with the bow.
        Vec3[] tipView = new Vec3[n];
        Vec3[] guardView = new Vec3[n];
        for (int j = 0; j < n; j++) {
            tipView[j] = samples.get(j).tip;
            guardView[j] = samples.get(j).guard;
        }
        Vec3[] inner = new Vec3[n];
        Vec3[] mid = new Vec3[n];
        Vec3[] outer = new Vec3[n];
        float[] sweepAlpha = new float[n];
        for (int j = 0; j < n; j++) {
            Vec3 blade = tipView[j].subtract(guardView[j]);
            double narrow = 0.5 + 0.5 * reach[j];
            inner[j] = bent(toWorld, tipView[j].subtract(blade.scale((1.0 - VIEW_INNER) * narrow)), ox[j], oy[j]);
            mid[j] = bent(toWorld, tipView[j], ox[j], oy[j]);
            outer[j] = bent(toWorld, tipView[j].add(blade.scale(VIEW_OUTER * width * narrow)), ox[j], oy[j]);
            sweepAlpha[j] = (float) (VIEW_SWEEP_ALPHA * strength[j] * visible[j] * faceOn(tipView, guardView, j));
        }
        sweep(out, inner, mid, outer, sweepAlpha);

        // The body and the core, on the screen.
        double[] body = new double[n];
        double[] core = new double[n];
        float[] bodyAlpha = new float[n];
        float[] coreAlpha = new float[n];
        for (int j = 0; j < n; j++) {
            double shown = visible[j] * strength[j];
            body[j] = VIEW_BODY * Math.pow(reach[j], 0.6) * Math.sqrt(visible[j]);
            core[j] = VIEW_CORE * (0.3 + 0.7 * reach[j]);
            bodyAlpha[j] = (float) (VIEW_BODY_ALPHA * shown);
            coreAlpha[j] = (float) shown;
        }
        screenStrip(out, toWorld, sx, sy, ox, oy, depth, body, bodyAlpha, color, false);
        screenStrip(out, toWorld, sx, sy, ox, oy, depth, core, coreAlpha, coreColor, true);
        Vec3[] path = new Vec3[n];
        for (int j = 0; j < n; j++) {
            path[j] = mid[j];
        }
        flare(f, path, 0.05 * depth[n - 1], fade, 0.35 * depth[n - 1]);
    }

    /** A camera-frame point moved on the screen by (ox, oy) at its own depth, as a camera-relative world point. */
    private static Vec3 bent(Quaternionf toWorld, Vec3 view, double ox, double oy) {
        double d = Math.max(1e-3, -view.z);
        return rotate(toWorld, view.x + ox * d, view.y + oy * d, view.z);
    }

    private static Vec3 rotate(Quaternionf toWorld, double x, double y, double z) {
        Vector3f v = toWorld.transform(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * A strip along the screen path (sx + ox, sy + oy) at the given depths, {@code half[j]} view units to
     * each side, square to the path on the screen.
     */
    private static void screenStrip(VertexConsumer out, Quaternionf toWorld, double[] sx, double[] sy, double[] ox, double[] oy,
                                    double[] depth, double[] half, float[] alpha, float[] c, boolean core) {
        int n = sx.length;
        Vec3 prevLow = null;
        Vec3 prevHigh = null;
        float prevA = 0f;
        float prevU = BODY_U;
        for (int j = 0; j < n; j++) {
            int a = Math.max(0, j - 1);
            int b = Math.min(n - 1, j + 1);
            double tx = (sx[b] + ox[b]) - (sx[a] + ox[a]);
            double ty = (sy[b] + oy[b]) - (sy[a] + oy[a]);
            double tl = Math.hypot(tx, ty);
            if (tl < 1e-9) {
                continue;
            }
            double px = -ty / tl * half[j];
            double py = tx / tl * half[j];
            double x = sx[j] + ox[j];
            double y = sy[j] + oy[j];
            double d = depth[j];
            Vec3 low = rotate(toWorld, (x - px) * d, (y - py) * d, -d);
            Vec3 high = rotate(toWorld, (x + px) * d, (y + py) * d, -d);
            float u = core && j == n - 1 ? 1f : BODY_U;
            if (prevLow != null && (alpha[j] > 0.003f || prevA > 0.003f)) {
                WorldFx.vertex(out, prevLow, prevU, 1f, c[0], c[1], c[2], prevA);
                WorldFx.vertex(out, prevHigh, prevU, 0f, c[0], c[1], c[2], prevA);
                WorldFx.vertex(out, high, u, 0f, c[0], c[1], c[2], alpha[j]);
                WorldFx.vertex(out, low, u, 1f, c[0], c[1], c[2], alpha[j]);
            }
            prevLow = low;
            prevHigh = high;
            prevA = alpha[j];
            prevU = u;
        }
    }

    // ------------------------------------------------------------------ shared

    /**
     * The sweep between {@code inner} and {@code outer} (camera-relative), two quads a step: the inner one
     * fading in from the blade to the tip's path at {@code mid} (the texture's middle, its brightest), the
     * outer one fading out past it.
     */
    private void sweep(VertexConsumer out, Vec3[] inner, Vec3[] mid, Vec3[] outer, float[] alpha) {
        for (int j = 1; j < mid.length; j++) {
            float a0 = alpha[j - 1];
            float a1 = alpha[j];
            if (a0 <= 0.003f && a1 <= 0.003f) {
                continue;
            }
            WorldFx.vertex(out, inner[j - 1], SWEEP_U, 1f, color[0], color[1], color[2], a0);
            WorldFx.vertex(out, mid[j - 1], SWEEP_U, 0.5f, color[0], color[1], color[2], a0);
            WorldFx.vertex(out, mid[j], SWEEP_U, 0.5f, color[0], color[1], color[2], a1);
            WorldFx.vertex(out, inner[j], SWEEP_U, 1f, color[0], color[1], color[2], a1);

            WorldFx.vertex(out, mid[j - 1], SWEEP_U, 0.5f, color[0], color[1], color[2], a0);
            WorldFx.vertex(out, outer[j - 1], SWEEP_U, 0.08f, color[0], color[1], color[2], a0);
            WorldFx.vertex(out, outer[j], SWEEP_U, 0.08f, color[0], color[1], color[2], a1);
            WorldFx.vertex(out, mid[j], SWEEP_U, 0.5f, color[0], color[1], color[2], a1);
        }
    }

    /** A strip along {@code points} (camera-relative) turned to face the camera, {@code half[j]} blocks to each side. */
    private static void facingStrip(VertexConsumer out, Vec3[] points, double[] half, float[] alpha, float[] c, boolean core) {
        int n = points.length;
        Vec3 prevLow = null;
        Vec3 prevHigh = null;
        float prevA = 0f;
        float prevU = BODY_U;
        for (int j = 0; j < n; j++) {
            Vec3 tangent = points[Math.min(n - 1, j + 1)].subtract(points[Math.max(0, j - 1)]);
            Vec3 across = tangent.cross(points[j]);
            double length = across.length();
            if (length < 1e-9) {
                continue;
            }
            Vec3 offset = across.scale(half[j] / length);
            Vec3 low = points[j].subtract(offset);
            Vec3 high = points[j].add(offset);
            float u = core && j == n - 1 ? 1f : BODY_U;
            if (prevLow != null && (alpha[j] > 0.003f || prevA > 0.003f)) {
                WorldFx.vertex(out, prevLow, prevU, 1f, c[0], c[1], c[2], prevA);
                WorldFx.vertex(out, prevHigh, prevU, 0f, c[0], c[1], c[2], prevA);
                WorldFx.vertex(out, high, u, 0f, c[0], c[1], c[2], alpha[j]);
                WorldFx.vertex(out, low, u, 1f, c[0], c[1], c[2], alpha[j]);
            }
            prevLow = low;
            prevHigh = high;
            prevA = alpha[j];
            prevU = u;
        }
    }

    /** A flare on the tip (the last of {@code path}) while it moves faster than {@code fullSpeed} a few frames. */
    private void flare(WorldFx.Frame f, Vec3[] path, double size, double fade, double fullSpeed) {
        int n = path.length;
        Vec3 last = path[n - 1];
        double speed = last.distanceTo(path[Math.max(0, n - 4)]);
        float strength = (float) (Math.min(1.0, speed / Math.max(1e-3, fullSpeed)) * 0.85 * (1.0 - fade));
        if (strength > 0.02f) {
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.billboard(glow, f.camera(), last, (float) size, coreColor[0], coreColor[1], coreColor[2], strength);
        }
    }

    /** This swing's poses, oldest first: this animation, inside the window, one space, one per animation time. */
    private List<BladeTracker.BladePose> swingPoses() {
        List<BladeTracker.BladePose> all = BladeTracker.poses(player, off);
        List<BladeTracker.BladePose> kept = new ArrayList<>();
        BladeTracker.Space space = null;
        for (int i = all.size() - 1; i >= 0; i--) {
            BladeTracker.BladePose pose = all.get(i);
            if (!animation.equals(pose.animation()) || pose.gameTime() < created - 1.0) {
                continue;
            }
            if (space == null) {
                space = pose.space();
            }
            if (pose.space() == space) {
                kept.add(0, pose);
            }
        }
        List<BladeTracker.BladePose> swing = new ArrayList<>();
        for (BladeTracker.BladePose pose : kept) {
            double time = pose.animationTime();
            if (time < from - 1e-3 || time > to + 0.5) {
                continue;
            }
            if (!swing.isEmpty() && time <= swing.get(swing.size() - 1).animationTime() + 1e-4) {
                swing.set(swing.size() - 1, pose); // held (hit-stop) or drawn twice at one time: the latest
                continue;
            }
            swing.add(pose);
        }
        return swing;
    }
}
