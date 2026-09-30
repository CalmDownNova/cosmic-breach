package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.heliarch.HaloShed;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HeliarchMoves;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Heliarch's GeckoLib model ({@code geo/entity/hollow_heliarch.geo.json}, from {@code tools/art/gen_heliarch.py}):
 * every piece is a free bone placed each frame where {@link HeliarchPose} puts it, so the model is exactly where the
 * server's hits are. The plates turn with their flight: closed they face out of the shell, open they stand as rays
 * round the ring, thrown they spin like boomerangs, torn free they turn upright to stand as monoliths. The hands are
 * palm down at rest, flat for the slam, dragging for the sweep, facing each other round the lance, raised for the
 * shed. In phase 2 there is no model: the eclipse and the tendrils are drawn by {@link HeliarchFx}.
 */
public class HeliarchModel extends DefaultedEntityGeoModel<HollowHeliarch> {
    static final ResourceLocation TEXTURE = CosmicBreach.id("textures/entity/hollow_heliarch.png");
    public static final float SCALE = 4.0f;

    public HeliarchModel() {
        super(CosmicBreach.id("hollow_heliarch"), false);
    }

    @Override
    public ResourceLocation getTextureResource(HollowHeliarch h) {
        return TEXTURE;
    }

    @Override
    public void setCustomAnimations(HollowHeliarch h, long instanceId, AnimationState<HollowHeliarch> state) {
        super.setCustomAnimations(h, instanceId, state);
        float partial = state.getPartialTick();
        double t = h.level().getGameTime() + partial;
        float bodyYaw = Mth.rotLerp(partial, h.yBodyRotO, h.yBodyRot);
        double facing = HeliarchArena.compassOf(bodyYaw);
        HeliarchPose.Input in = h.poseInput();
        Vec3 origin = new Vec3(Mth.lerp(partial, h.xo, h.getX()), Mth.lerp(partial, h.yo, h.getY()), Mth.lerp(partial, h.zo, h.getZ()));
        State st = h.state();
        double s = t - h.stateStart();

        // the core: a sun in phase 1 (in phase 2 the eclipse is drawn as light and shadow)
        boolean sun = st == State.INTRO || st == State.REGENT || st == State.HOLLOWING && s < 60;
        GeoBone core = getAnimationProcessor().getBone("core");
        if (core != null) {
            core.setHidden(!sun);
            if (sun) {
                place(core, HeliarchPose.core(in, t).subtract(origin), bodyYaw);
                float grow = st == State.INTRO ? (float) HeliarchPose.smooth((s - 40) / 60.0) : st == State.HOLLOWING
                        ? (float) (1.0 - HeliarchPose.smooth((s - 40) / 20.0)) : 1f;
                grow = Math.max(0.01f, grow) * 0.8f;
                core.setScaleX(grow);
                core.setScaleY(grow);
                core.setScaleZ(grow);
            }
        }

        // the plates
        for (int k = 0; k < 6; k++) {
            GeoBone plate = getAnimationProcessor().getBone("plate_" + k);
            if (plate == null) {
                continue;
            }
            Vec3 at = HeliarchPose.plate(in, facing, k, t);
            if (at == null) {
                plate.setHidden(true);
                continue;
            }
            plate.setHidden(false);
            place(plate, at.subtract(origin), bodyYaw);
            orient(plate, plateFrame(h, in, facing, k, t, at), bodyYaw);
        }

        // the hands
        for (boolean right : new boolean[] {true, false}) {
            String side = right ? "right" : "left";
            GeoBone hand = getAnimationProcessor().getBone("hand_" + side);
            if (hand == null) {
                continue;
            }
            Vec3 at = HeliarchPose.hand(in, facing, right, t);
            if (at == null) {
                hand.setHidden(true);
                hand.setChildrenHidden(true);
                continue;
            }
            hand.setHidden(false);
            hand.setChildrenHidden(false);
            place(hand, at.subtract(origin), bodyYaw);
            Frame f = handFrame(h, in, facing, right, t, at);
            orient(hand, f, bodyYaw);
            float scale = st == State.INTRO ? (float) Math.max(0.01, HeliarchPose.smooth((s - HeliarchPose.INTRO_HANDS) / 50.0)) : 1f;
            if (st == State.HOLLOWING) {
                scale = (float) Math.max(0.01, 1.0 - HeliarchPose.smooth((s - 20) / 30.0));
            }
            hand.setScaleX(scale);
            hand.setScaleY(scale);
            hand.setScaleZ(scale);
            float curl = (float) Math.toRadians(f.curl);
            for (int i = 0; i < 4; i++) {
                GeoBone a = getAnimationProcessor().getBone("finger_" + side + "_" + i);
                GeoBone b = getAnimationProcessor().getBone("finger_" + side + "_" + i + "_tip");
                float spread = (float) Math.toRadians((i - 1.5) * f.spread);
                if (a != null) {
                    a.setRotX(-curl);
                    a.setRotY(spread);
                }
                if (b != null) {
                    b.setRotX(-curl * 1.1f);
                }
            }
            GeoBone thumb = getAnimationProcessor().getBone("thumb_" + side);
            if (thumb != null) {
                thumb.setRotX(-curl * 0.6f);
                // the thumb opens with the spread (clear of the fingers, so a flat hand reads as a hand)
                thumb.setRotY((float) Math.toRadians((right ? -1 : 1) * f.spread * 0.8));
            }
        }
    }

    // ------------------------------------------------------------------ frames

    /** Where a piece's own axes point in the world: its ray or fingers ({@code up}), its face ({@code front}). */
    record Frame(Vec3 up, Vec3 front, double curl, double spread) {
        Frame(Vec3 up, Vec3 front) {
            this(up, front, 0, 0);
        }
    }

    private static Frame plateFrame(HollowHeliarch h, HeliarchPose.Input in, double facing, int k, double t, Vec3 at) {
        Vec3 up = new Vec3(0, 1, 0);
        double o = HeliarchPose.openness(in, t);
        Frame crown = new Frame(HeliarchPose.ray(k, t, o), HeliarchPose.face(k, t, o));
        Frame open = new Frame(HeliarchPose.ray(k, t, 1.0), HeliarchPose.face(k, t, 1.0));
        Frame closed = new Frame(HeliarchPose.ray(k, t, 0.0), HeliarchPose.face(k, t, 0.0));
        double s = t - in.stateStart();
        switch (in.state()) {
            case INTRO -> {
                double u = HeliarchPose.smooth((s - HeliarchPose.INTRO_DEBRIS) / (HeliarchPose.INTRO_ASSEMBLED - HeliarchPose.INTRO_DEBRIS));
                double tumble = (1.0 - u) * (s * 0.09 + k);
                Frame debris = new Frame(new Vec3(Math.sin(tumble), Math.cos(tumble), 0.3).normalize(),
                        new Vec3(Math.cos(tumble * 0.7), 0.2, Math.sin(tumble * 0.7)).normalize());
                return slerp(debris, closed, u);
            }
            case REGENT -> {
                if (in.action() == Action.HALO_SHED) {
                    double a = t - in.actionStart();
                    if (a >= HaloShed.FLASH && a < HaloShed.TOTAL) {
                        // thrown: it spins round its face like a boomerang, face up so it reads from the floor
                        double spin = Math.toRadians((a - HaloShed.FLASH) * 34.0);
                        Frame thrown = new Frame(new Vec3(Math.cos(spin), 0, Math.sin(spin)), up);
                        double into = HeliarchPose.smooth((a - HaloShed.FLASH) / 6.0)
                                * (1.0 - HeliarchPose.smooth((a - (HaloShed.TOTAL - 6)) / 6.0));
                        return slerp(open, thrown, into);
                    }
                    return open;
                }
                return crown;
            }
            case HOLLOWING -> {
                // torn free, it turns upright to stand as a monolith facing the throne
                Vec3 inward = HeliarchArena.CENTRE.subtract(HeliarchPose.monolithMiddle(k)).multiply(1, 0, 1).normalize();
                Frame stand = new Frame(up, inward);
                double u = HeliarchPose.smooth((s - HeliarchPose.TEAR) / (HeliarchMoves.HOLLOWING_SLAM - HeliarchPose.TEAR));
                return slerp(open, stand, u);
            }
            default -> {
                return open;
            }
        }
    }

    /** A hand's frame: {@code up} is the back of the hand (the palm faces the other way), {@code front} its fingers. */
    private static Frame handFrame(HollowHeliarch h, HeliarchPose.Input in, double facing, boolean right, double t, Vec3 at) {
        Vec3 f = HeliarchPose.forward(facing);
        Vec3 r = HeliarchPose.right(facing);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 inward = r.scale(right ? -1 : 1);
        // at rest: raised and open, palm toward its target, fingers up and a little out, spread
        Vec3 outward = inward.scale(-1);
        Frame rest = new Frame(f.scale(-1), up.scale(0.94).add(outward.scale(0.34)).normalize(), 16, 12);
        if (in.state() == State.REGENT) {
            double a = t - in.actionStart();
            if (in.breakStart() != Long.MIN_VALUE && t >= in.breakStart() && t < in.breakStart() + HeliarchMoves.BREAK_TICKS + 20) {
                // Broken: fallen open on its back, palm to the sky, fingers curling up and spread, thumb out (flat and
                // palm down, seen from the floor, they read as benches)
                return new Frame(up.scale(-1), f, 48, 24);
            }
            switch (in.action()) {
                case SUNDERFALL -> {
                    if (right == in.sunderRight()) {
                        Vec3 toward = in.actionPos().subtract(HeliarchPose.core(in, t)).multiply(1, 0, 1);
                        Vec3 fingers = toward.lengthSqr() < 1e-4 ? f : toward.normalize();
                        if (a < HeliarchMoves.SUNDER_TELL) {
                            // rising: the palm turns down over the ring, the fingers open and spread
                            return slerp(rest, new Frame(up, fingers, 4, 16), HeliarchPose.smooth(a / 12.0));
                        }
                        if (in.sunderParried()) {
                            // struck back: the hand rears up, fingers flung skyward, palm toward the one who parried
                            return new Frame(fingers.scale(-1), up, 22, 18);
                        }
                        return new Frame(up, fingers, 2, 18);
                    }
                    return rest;
                }
                case CORONA_SWEEP -> {
                    if (right) {
                        double w = a - HeliarchMoves.SWEEP_TELL;
                        double ang = w < 0 ? in.actionAngle() : com.cosmicbreach.guardian.heliarch.CoronaSweep.wallAngle(in.actionAngle(), w);
                        // a clenched fist dragged along the floor, knuckles leading the fire
                        return new Frame(up, HeliarchArena.dir(ang + 90.0), 95, 4);
                    }
                    // the other hand raised high, palm out, conducting
                    return new Frame(f.scale(-1), up, 20, 10);
                }
                case SOLAR_LANCE -> {
                    // palms facing each other round the line
                    return new Frame(inward.scale(-1), f, 8, 4);
                }
                case HALO_SHED -> {
                    // raised, palms up
                    return new Frame(up.scale(-1), f, 16, 14);
                }
                default -> {
                    return rest;
                }
            }
        }
        return rest;
    }

    // ------------------------------------------------------------------ placing bones

    /** A world offset from the entity into the model's own space (blocks before the renderer's scale). */
    static Vec3 toModel(Vec3 offset, float bodyYaw) {
        double theta = Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;
        return new Vec3(x / SCALE, offset.y / SCALE, z / SCALE);
    }

    private static Vector3f dirToModel(Vec3 d, float bodyYaw) {
        double theta = Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        return new Vector3f((float) (d.x * cos - d.z * sin), (float) d.y, (float) (d.x * sin + d.z * cos));
    }

    private static void place(GeoBone bone, Vec3 offset, float bodyYaw) {
        Vec3 local = toModel(offset, bodyYaw);
        bone.setPosX((float) (-local.x * 16.0));
        bone.setPosY((float) (local.y * 16.0));
        bone.setPosZ((float) (local.z * 16.0));
    }

    /**
     * Turns {@code bone} so its +Y points along the frame's {@code up} and its -Z (its face, its palm's back is +Y for
     * a hand) along {@code front}, as GeckoLib applies rotations (Z, then Y, then X).
     */
    private static void orient(GeoBone bone, Frame frame, float bodyYaw) {
        Matrix3f m = basis(frame, bodyYaw);
        if (m == null) {
            return;
        }
        Vector3f e = m.getEulerAnglesZYX(new Vector3f());
        bone.setRotX(e.x);
        bone.setRotY(e.y);
        bone.setRotZ(e.z);
    }

    private static @Nullable Matrix3f basis(Frame frame, float bodyYaw) {
        Vector3f y = dirToModel(frame.up(), bodyYaw);
        Vector3f front = dirToModel(frame.front(), bodyYaw);
        if (y.lengthSquared() < 1e-8f || front.lengthSquared() < 1e-8f) {
            return null;
        }
        y.normalize();
        Vector3f z = new Vector3f(front).negate();
        z.sub(new Vector3f(y).mul(z.dot(y)));
        if (z.lengthSquared() < 1e-8f) {
            z = Math.abs(y.y) < 0.9f ? new Vector3f(0, 1, 0).cross(y) : new Vector3f(1, 0, 0).cross(y);
        }
        z.normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        return new Matrix3f(x, y, z);
    }

    /** Between two frames by {@code u}, turning the shortest way. */
    private static Frame slerp(Frame a, Frame b, double u) {
        if (u <= 0) {
            return a;
        }
        if (u >= 1) {
            return b;
        }
        Matrix3f ma = worldBasis(a);
        Matrix3f mb = worldBasis(b);
        if (ma == null || mb == null) {
            return u < 0.5 ? a : b;
        }
        Quaternionf qa = new Quaternionf().setFromNormalized(ma);
        Quaternionf qb = new Quaternionf().setFromNormalized(mb);
        Quaternionf q = qa.slerp(qb, (float) u, new Quaternionf());
        Matrix3f m = new Matrix3f().set(q);
        Vector3f y = m.getColumn(1, new Vector3f());
        Vector3f z = m.getColumn(2, new Vector3f());
        return new Frame(new Vec3(y.x, y.y, y.z), new Vec3(-z.x, -z.y, -z.z), a.curl() + (b.curl() - a.curl()) * u,
                a.spread() + (b.spread() - a.spread()) * u);
    }

    private static @Nullable Matrix3f worldBasis(Frame f) {
        Vector3f y = new Vector3f((float) f.up().x, (float) f.up().y, (float) f.up().z);
        Vector3f front = new Vector3f((float) f.front().x, (float) f.front().y, (float) f.front().z);
        if (y.lengthSquared() < 1e-8f || front.lengthSquared() < 1e-8f) {
            return null;
        }
        y.normalize();
        Vector3f z = new Vector3f(front).negate();
        z.sub(new Vector3f(y).mul(z.dot(y)));
        if (z.lengthSquared() < 1e-8f) {
            z = Math.abs(y.y) < 0.9f ? new Vector3f(0, 1, 0).cross(y) : new Vector3f(1, 0, 0).cross(y);
        }
        z.normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        return new Matrix3f(x, y, z);
    }
}
