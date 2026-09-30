package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.TwinBlades;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The combat effects, as recipes over {@link WorldFx} shapes and {@link FxParticle}s. Every count goes
 * through {@link FxBudget}. Slash trails follow the blade as it is drawn ({@link SlashTrail},
 * {@link BladeTracker}), and so do the charge's glow and full Resonance's sparks. Colours: white-gold
 * and gold for Meridian's light, turquoise for the perfect dodge (the crystal of the palette), a cool
 * white for dash streaks. No smoke anywhere.
 */
public final class CombatEffects {
    public static final int WHITE = 0xFFFFFF;
    public static final int WHITE_GOLD = 0xFFF4D6;
    public static final int PALE_GOLD = 0xFFE3A0;
    public static final int GOLD = 0xFFC857;
    public static final int TURQUOISE = 0x60E8D8;
    public static final int PALE_TURQUOISE = 0xC4FFF6;
    public static final int DASH_WHITE = 0xDDEEFF;

    /** Zenith's pillar stands this far in front of the attacker, where the launched targets rise. */
    static final double PILLAR_AHEAD = 1.5;
    static final double PILLAR_HEIGHT = 6.0;
    static final float PILLAR_HALF_WIDTH = 0.42f;

    private static final RandomSource RANDOM = RandomSource.create();
    /** Told of every dash drawn (a weapon's own dash effects: the Binary Edges' afterimages). */
    private static final List<Consumer<Entity>> DASH_LISTENERS = new CopyOnWriteArrayList<>();

    private CombatEffects() {
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    // ------------------------------------------------------------------ moves

    /**
     * A move started on {@code player}: its slash trail, which follows the drawn blade through the
     * move's swing ({@link SlashTrail}). The local player's moves come from its own machine; everyone
     * else's from {@link #remoteMoveStarted}.
     */
    public static void moveStarted(Player player, MoveDef def) {
        if (!ready()) {
            return;
        }
        def.slash().ifPresent(slash -> trails(player, def, slash));
        ClientMoveEffects.started(player, def);
    }

    /**
     * A move's trails: the main blade's in the slash's colour, and with a second blade in the off hand
     * ({@link TwinBlades}) whichever the slash names, the off blade's in its own colour. While the off blade is
     * away its strokes fall to the main blade (its solo animation swings that one).
     */
    private static void trails(Player player, MoveDef def, MoveDef.Slash slash) {
        ResourceLocation played = TwinBlades.animationFor(player, def.animation());
        boolean offInHand = TwinBlades.offHandOf(player) != null && !TwinBlades.offAway(player);
        boolean main = slash.blades().main() || !offInHand;
        boolean off = slash.blades().off() && offInHand;
        if (main) {
            WorldFx.add(new SlashTrail(player, def, played, false, slash.color()));
        }
        if (off) {
            WorldFx.add(new SlashTrail(player, def, played, true, slash.offColorOrMain()));
        }
    }

    /**
     * A move's first active tick on {@code attacker}: Meridian Line's line of light or Zenith's pillar,
     * from where the attacker stands and faces then.
     */
    public static void moveActive(Entity attacker, MoveDef def) {
        if (!ready()) {
            return;
        }
        float yaw = attacker.getYRot();
        def.wave().ifPresent(wave -> groundLine(attacker, wave.length(), yaw,
                def.slash().map(MoveDef.Slash::color).orElse(WHITE_GOLD)));
        def.launch().ifPresent(launch -> pillar(attacker, yaw));
        if (attacker instanceof Player player) {
            ClientMoveEffects.active(player, def);
        }
    }

    /**
     * Another player started a move (its {@code MoveStartedPayload}): its trail now (it follows the
     * blade, whose animation starts now too) and the rest with its active ticks, {@code startup} ticks
     * from now. A plunge's effects come with its landing instead.
     */
    public static void remoteMoveStarted(Player player, MoveDef def) {
        moveStarted(player, def);
        if (def.kind() == MoveKind.PLUNGE) {
            return;
        }
        WorldFx.after(def.timing().startup(), () -> {
            if (!player.isRemoved()) {
                moveActive(player, def);
            }
        });
    }

    // ------------------------------------------------------------------ hits

    /**
     * A landed hit at {@code at}: sparks thrown along {@code direction} (away from the attacker), more,
     * faster and bigger with Impact, a small flash, and on a crit a star burst.
     */
    public static void hit(Vec3 at, Vec3 direction, double impact, boolean crit) {
        if (!ready()) {
            return;
        }
        ClientLevel level = level();
        double power = 0.6 + impact / 20.0;
        boolean big = crit || impact >= 18.0;
        Vec3 along = flatten(direction);
        int sparks = FxBudget.count((int) Math.round(5 + impact * 0.7), at, big);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = cone(along, 62.0).add(0, 0.15 + RANDOM.nextDouble() * 0.35, 0).normalize()
                    .scale((0.22 + RANDOM.nextDouble() * 0.36) * power);
            FxBudget.spawn(FxParticles.spark(level, jitter(at, 0.08)).velocity(v)
                    .color(mix(WHITE_GOLD, GOLD, RANDOM.nextFloat()))
                    .size((float) (0.03 * Math.sqrt(power)), 0.012f).life(6 + RANDOM.nextInt(5))
                    .gravity(0.55f).drag(0.84f).physics().streak(1.7f, 0.1f));
        }
        if (FxBudget.count(1, at, big) > 0) {
            FxBudget.spawn(FxParticles.glint(level, at).size(fit(at, (float) (0.2 + impact * 0.012)), 0.04f).life(4)
                    .color(WHITE_GOLD).fade(0f, 1.4f).spin(0.05f));
            GlowEffect.flash(at, (float) (0.3 + impact * 0.018), WHITE_GOLD, 0.7f, 4);
        }
        if (crit) {
            critBurst(level, at, impact);
        }
    }

    /** A crit: a bigger flash, a gold ring and a burst of star glints. */
    private static void critBurst(ClientLevel level, Vec3 at, double impact) {
        GlowEffect.flash(at, 1.0f, WHITE_GOLD, 0.9f, 6);
        FxBudget.spawn(FxParticles.glint(level, at).size(fit(at, 0.6f), 0.08f).life(7).color(WHITE).fade(0f, 1.3f).spin(0.14f));
        GlowEffect.ring(at, 0.15f, 1.1f, GOLD, 1.0f, 8);
        int stars = FxBudget.count(9, at, true);
        for (int i = 0; i < stars; i++) {
            Vec3 v = randomUnit().scale(0.2 + RANDOM.nextDouble() * 0.12);
            FxBudget.spawn(FxParticles.glint(level, jitter(at, 0.1)).velocity(v).drag(0.74f)
                    .size(0.15f, 0.02f).life(8 + RANDOM.nextInt(3)).color(PALE_GOLD).spin(0.2f));
        }
    }

    // ------------------------------------------------------------------ landing, defence, movement

    /** A plunge landing at {@code feet} after falling {@code fall} blocks: a ground ring and sparks, sized by the fall. */
    public static void plungeLanding(Vec3 feet, double fall) {
        if (!ready()) {
            return;
        }
        ClientLevel level = level();
        float size = (float) Mth.clamp(1.4 + fall * 0.3, 1.4, 5.0);
        Vec3 ground = feet.add(0, 0.06, 0);
        boolean big = fall >= 4.0;
        GlowEffect.groundRing(feet, 0.3f, size, PALE_GOLD, 1.0f, 11);
        GlowEffect.groundRing(feet.add(0, 0.01, 0), 0.2f, size * 0.6f, WHITE_GOLD, 0.8f, 7);
        GlowEffect.ground(feet, size * 0.75f, GOLD, 0.7f, 9);
        int sparks = FxBudget.count((int) Mth.clamp(10 + fall * 2.5, 10, 40), ground, big);
        for (int i = 0; i < sparks; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 v = out.scale((0.3 + RANDOM.nextDouble() * 0.35) * (1.0 + Math.min(fall, 12.0) * 0.04))
                    .add(0, 0.12 + RANDOM.nextDouble() * 0.25, 0);
            FxBudget.spawn(FxParticles.spark(level, ground.add(out.scale(0.4))).velocity(v)
                    .color(mix(WHITE_GOLD, GOLD, RANDOM.nextFloat())).size(0.035f, 0.012f).life(7 + RANDOM.nextInt(6))
                    .gravity(0.8f).drag(0.86f).physics().streak(1.6f, 0.1f));
        }
    }

    /** A parry at the point where the blades met: a bright flash and an expanding gold ring. */
    public static void parry(Vec3 point) {
        if (!ready()) {
            return;
        }
        ClientLevel level = level();
        GlowEffect.flash(point, 1.3f, WHITE, 1.0f, 6);
        GlowEffect.ring(point, 0.25f, 1.8f, GOLD, 1.0f, 9);
        if (FxBudget.count(1, point, true) > 0) {
            FxBudget.spawn(FxParticles.glint(level, point).size(fit(point, 0.85f), 0.15f).life(7).color(WHITE)
                    .fade(0f, 1.2f).spin(0.12f));
        }
        int sparks = FxBudget.count(14, point, true);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = randomUnit().scale(0.26 + RANDOM.nextDouble() * 0.2);
            FxBudget.spawn(FxParticles.spark(level, point).velocity(v).color(mix(WHITE, GOLD, RANDOM.nextFloat() * 0.7f))
                    .size(0.03f, 0.01f).life(6 + RANDOM.nextInt(4)).gravity(0.35f).drag(0.82f).streak(1.8f, 0.1f));
        }
    }

    /**
     * A perfect dodge: a quick burst of turquoise glints round the player. Seen from the player's own
     * eyes it sits in front of the camera instead, where it can be seen.
     */
    public static void perfectDodge(Entity entity) {
        if (!ready()) {
            return;
        }
        ClientLevel level = level();
        Vec3 centre;
        double spread;
        boolean firstPerson = WorldFx.firstPersonOf(entity);
        if (firstPerson) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = camera.getLookVector();
            centre = camera.getPosition().add(look.x() * 1.4, look.y() * 1.4 - 0.3, look.z() * 1.4);
            spread = 0.6;
        } else {
            centre = entity.position().add(0, entity.getBbHeight() * 0.55, 0);
            spread = 0.75;
        }
        GlowEffect.flash(centre, 1.3f, PALE_TURQUOISE, 0.6f, 6);
        GlowEffect.ring(centre, 0.3f, 1.5f, TURQUOISE, 0.9f, 8);
        int glints = FxBudget.count(14, centre, true);
        for (int i = 0; i < glints; i++) {
            Vec3 out = randomUnit();
            FxBudget.spawn(FxParticles.glint(level, centre.add(out.scale(spread * (0.8 + RANDOM.nextDouble() * 0.5))))
                    .velocity(out.scale(0.07)).drag(0.85f).size(firstPerson ? 0.16f : 0.26f, 0.0f).life(9 + RANDOM.nextInt(4))
                    .color(mix(PALE_TURQUOISE, TURQUOISE, RANDOM.nextFloat() * 0.7f)).fade(0.1f, 1.0f).spin(0.18f));
        }
    }

    /** {@code listener} hears of every dash drawn, the local player's and everyone else's. */
    public static void addDashListener(Consumer<Entity> listener) {
        DASH_LISTENERS.add(listener);
    }

    /** A dash: a few thin streaks left hanging along the path for the dash's ticks. */
    public static void dash(Entity entity) {
        if (!ready()) {
            return;
        }
        for (Consumer<Entity> listener : DASH_LISTENERS) {
            listener.accept(entity);
        }
        boolean firstPerson = WorldFx.firstPersonOf(entity);
        Vec3[] last = {entity.position()};
        WorldFx.follow(entity, BodyMotion.DASH_TICKS, e -> {
            Vec3 now = e.position();
            Vec3 moved = now.subtract(last[0]);
            last[0] = now;
            if (moved.lengthSqr() < 0.01) {
                return;
            }
            Vec3 dir = moved.normalize();
            Vec3 across = new Vec3(-dir.z, 0, dir.x);
            int streaks = FxBudget.count(3, now, false);
            for (int i = 0; i < streaks; i++) {
                double height = firstPerson ? 0.1 + RANDOM.nextDouble() * 0.75 : 0.15 + RANDOM.nextDouble() * 1.4;
                Vec3 at = now.add(0, height, 0).add(across.scale((RANDOM.nextDouble() - 0.5) * 0.9))
                        .subtract(dir.scale(RANDOM.nextDouble() * 0.6));
                FxBudget.spawn(FxParticles.spark(level(), at).streakAlong(dir, (float) (0.9 + RANDOM.nextDouble() * 0.6))
                        .velocity(dir.scale(0.04)).size(0.045f, 0.01f).life(6 + RANDOM.nextInt(3)).color(DASH_WHITE));
            }
        });
    }

    // ------------------------------------------------------------------ Zenith and Meridian Line

    private static void pillar(Entity attacker, float yaw) {
        ClientLevel level = level();
        Vec3 base = attacker.position().add(BodyMotion.forward(yaw).scale(PILLAR_AHEAD));
        int[] ticks = {0};
        // Seen from the attacker's own eyes the pillar stands right ahead, in front of the target it launches:
        // thinner and fainter there, so it marks the launch without hiding what rises through it.
        boolean own = WorldFx.firstPersonOf(attacker);
        float halfWidth = own ? PILLAR_HALF_WIDTH * 0.4f : PILLAR_HALF_WIDTH;
        float strength = own ? 0.55f : 1.0f;
        WorldFx.add(new PillarEffect(base, PILLAR_HEIGHT, halfWidth, strength, WHITE_GOLD, () -> {
            if (ticks[0]++ >= 9) {
                return;
            }
            int sparks = FxBudget.count(4, base, false);
            for (int i = 0; i < sparks; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                double r = RANDOM.nextDouble() * 0.3;
                Vec3 at = base.add(Math.cos(angle) * r, RANDOM.nextDouble() * 1.8, Math.sin(angle) * r);
                Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.03, 0.35 + RANDOM.nextDouble() * 0.3, (RANDOM.nextDouble() - 0.5) * 0.03);
                FxBudget.spawn(FxParticles.spark(level, at).velocity(v).drag(0.9f).color(mix(WHITE_GOLD, PALE_GOLD, RANDOM.nextFloat()))
                        .size(0.03f, 0.01f).life(8 + RANDOM.nextInt(5)).streak(1.5f, 0.1f));
            }
        }));
        GlowEffect.ground(base, 1.3f, GOLD, 0.8f, PillarEffect.LIFE_TICKS);
        GlowEffect.flash(base.add(0, 1.0, 0), 1.1f, WHITE_GOLD, 0.6f, 5);
    }

    private static void groundLine(Entity attacker, double length, float yaw, int color) {
        Vec3 start = attacker.position().add(0, 0.03, 0);
        Vec3 dir = BodyMotion.forward(yaw);
        WorldFx.add(new GroundLineEffect(start, dir, length, color, () -> lineMotes(start, dir, length, color)));
    }

    /** The line shatters: motes rise off its length and drift away. */
    private static void lineMotes(Vec3 start, Vec3 dir, double length, int color) {
        if (!ready()) {
            return;
        }
        ClientLevel level = level();
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        int motes = FxBudget.count((int) Math.round(length * 4), start.add(dir.scale(length * 0.5)), false);
        for (int i = 0; i < motes; i++) {
            Vec3 at = start.add(dir.scale(RANDOM.nextDouble() * length)).add(across.scale((RANDOM.nextDouble() - 0.5) * 0.2))
                    .add(0, 0.04, 0);
            Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.03, 0.025 + RANDOM.nextDouble() * 0.05, (RANDOM.nextDouble() - 0.5) * 0.03);
            FxBudget.spawn(FxParticles.glint(level, at).velocity(v).drag(0.96f).size(0.12f, 0.0f)
                    .life(14 + RANDOM.nextInt(9)).color(mix(color, WHITE, 0.5f)).fade(0.1f, 0.9f).spin(0.15f));
        }
    }

    // ------------------------------------------------------------------ the blade

    /** Where a player's blade is, on its centre line ({@link #bladePoint(Player, double, double)}). */
    static Vec3 bladePoint(Player player, double along) {
        return bladePoint(player, along, 0.0);
    }

    /**
     * Where a player's blade is drawn, from the guard ({@code along} 0) to the tip (1), {@code edge}
     * texels from its centre line toward its upper edge ({@link BladeTracker#halfWidth} is the
     * edge itself): read off the blade as it was last drawn ({@link BladeTracker}), in the world or in
     * vanilla's first-person hand. Only a blade not drawn lately (a player out of view, the hand hidden)
     * falls back to an estimate.
     */
    static Vec3 bladePoint(Player player, double along, double edge) {
        BladeTracker.BladePose pose = BladeTracker.fresh(player);
        if (pose != null) {
            return BladeTracker.toWorld(pose, pose.point(along, edge));
        }
        return estimatedBladePoint(player, along);
    }

    /** True if the player's blade is drawn over the world (vanilla's first-person hand): effects on it go past its edge. */
    static boolean bladeDrawnOver(Player player) {
        BladeTracker.BladePose pose = BladeTracker.fresh(player);
        return pose != null ? pose.space() == BladeTracker.Space.HAND : WorldFx.firstPersonOf(player);
    }

    /**
     * An estimate for a blade not drawn lately: seen through the local player's eyes, where vanilla draws
     * the held item (measured once off a screenshot at the hand's 70 degree field of view, as directions
     * one block ahead, scaled to the world's); otherwise forward of the right hand.
     */
    private static Vec3 estimatedBladePoint(Player player, double along) {
        Minecraft mc = Minecraft.getInstance();
        if (WorldFx.firstPersonOf(player)) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vector3f f = camera.getLookVector();
            Vector3f u = camera.getUpVector();
            Vector3f l = camera.getLeftVector();
            double k = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0) / Math.tan(Math.toRadians(35.0));
            double right = Mth.lerp(along, 0.78, 1.22) * k;
            double up = Mth.lerp(along, -0.55, 0.39) * k;
            return camera.getPosition().add(f.x() - l.x() * right + u.x() * up,
                    f.y() - l.y() * right + u.y() * up,
                    f.z() - l.z() * right + u.z() * up);
        }
        Vec3 forward = BodyMotion.forward(player.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 hand = player.position().add(0, 0.75, 0).add(right.scale(0.36)).add(forward.scale(0.15));
        return hand.add(forward.scale(0.85 * along)).add(0, 0.35 * along, 0);
    }

    /** The charge reached its minimum (or its full strength): a glint on the blade's tip, in the charge's colour. */
    public static void chargeGlint(Player player, boolean full) {
        if (!ready()) {
            return;
        }
        boolean own = WorldFx.firstPersonOf(player);
        int color = Chargers.color(player.getId());
        Vec3 tip = bladePoint(player, 0.95, bladeDrawnOver(player) ? BladeTracker.halfWidth(player) + 1.0 : 0.0);
        float size = own ? (full ? 0.16f : 0.12f) : (full ? 0.34f : 0.26f);
        int tint = color < 0 ? (full ? WHITE : WHITE_GOLD) : mix(color, WHITE, full ? 0.75f : 0.4f);
        FxBudget.spawn(FxParticles.glint(level(), tip).size(size, 0.02f).life(full ? 8 : 6)
                .color(tint).fade(0f, 1.3f).spin(0.16f));
    }

    /**
     * While charging: motes drawn in to the blade, more of them as the charge builds, in the charge's
     * colour ({@code color}; -1 for Meridian's gold). A coloured charge draws embers: sparks that stream in.
     */
    public static void chargeMote(Player player, double progress, int color) {
        if (!ready()) {
            return;
        }
        boolean own = WorldFx.firstPersonOf(player);
        double edge = bladeDrawnOver(player) ? BladeTracker.halfWidth(player) + 0.5 : 0.0;
        int wanted = 1 + (RANDOM.nextDouble() < progress ? 1 : 0);
        Vec3 target = bladePoint(player, 0.3 + RANDOM.nextDouble() * 0.6, edge);
        int motes = FxBudget.count(wanted, target, false);
        for (int i = 0; i < motes; i++) {
            Vec3 aim = bladePoint(player, 0.3 + RANDOM.nextDouble() * 0.6, edge);
            Vec3 offset = randomUnit().scale((own ? 0.45 : 0.7) + RANDOM.nextDouble() * 0.35);
            int life = 7;
            if (color < 0) {
                FxBudget.spawn(FxParticles.glint(level(), aim.add(offset)).velocity(offset.scale(-1.0 / life))
                        .size(own ? 0.06f : 0.1f, 0.02f).life(life).color(mix(PALE_GOLD, WHITE, RANDOM.nextFloat() * 0.5f))
                        .fade(0.25f, 0.5f).spin(0.2f));
            } else {
                FxBudget.spawn(FxParticles.spark(level(), aim.add(offset)).velocity(offset.scale(-1.0 / life))
                        .size(own ? 0.025f : 0.04f, 0.012f).life(life).color(mix(color, WHITE, RANDOM.nextFloat() * 0.45f))
                        .fade(0.2f, 0.6f).streak(1.4f, 0.06f));
            }
        }
    }

    /** Full Resonance: now and then a spark drifts up off the blade (from its edge, in first person). */
    public static void bladeSpark(Player player) {
        if (!ready()) {
            return;
        }
        float halfWidth = BladeTracker.halfWidth(player);
        double edge = bladeDrawnOver(player) ? halfWidth + 0.5 + RANDOM.nextDouble() * 2.0
                : (RANDOM.nextDouble() * 2.0 - 1.0) * halfWidth;
        Vec3 at = bladePoint(player, 0.15 + RANDOM.nextDouble() * 0.85, edge);
        if (FxBudget.count(1, at, false) == 0) {
            return;
        }
        Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.02, 0.018 + RANDOM.nextDouble() * 0.025, (RANDOM.nextDouble() - 0.5) * 0.02);
        FxBudget.spawn(FxParticles.glint(level(), at).velocity(v).drag(0.97f).size(0.05f, 0.0f).life(12 + RANDOM.nextInt(6))
                .color(mix(PALE_GOLD, GOLD, RANDOM.nextFloat())).fade(0.15f, 1.0f).spin(0.12f));
    }

    // ------------------------------------------------------------------ helpers

    /** {@code size}, but at most 0.4 of the distance from the camera (a flash in the player's face stays small). */
    private static float fit(Vec3 at, float size) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(at);
        return (float) Math.min(size, Math.max(0.05, distance * 0.4));
    }

    private static Vec3 flatten(Vec3 direction) {
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        return flat.lengthSqr() < 1e-6 ? randomUnit() : flat.normalize();
    }

    /** A random direction within {@code spreadDeg} of {@code dir} (unit). */
    private static Vec3 cone(Vec3 dir, double spreadDeg) {
        Vec3 any = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(any).normalize();
        Vec3 p2 = dir.cross(p1);
        double around = RANDOM.nextDouble() * Math.PI * 2.0;
        double away = Math.toRadians(spreadDeg) * Math.sqrt(RANDOM.nextDouble());
        return dir.scale(Math.cos(away)).add(p1.scale(Math.sin(away) * Math.cos(around)))
                .add(p2.scale(Math.sin(away) * Math.sin(around)));
    }

    private static Vec3 randomUnit() {
        Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }

    private static Vec3 jitter(Vec3 at, double amount) {
        return at.add((RANDOM.nextDouble() - 0.5) * amount, (RANDOM.nextDouble() - 0.5) * amount, (RANDOM.nextDouble() - 0.5) * amount);
    }

    /** {@code a} to {@code b} (0xRRGGBB) by {@code t}. */
    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
