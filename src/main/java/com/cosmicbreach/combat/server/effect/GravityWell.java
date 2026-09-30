package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Comet Maul's ability (GDD 4.2): the maul is planted {@code offset} blocks ahead when the startup
 * ends, and for {@code pull_ticks} active ticks every enemy that isn't a boss within {@code radius} of
 * the plant is pulled toward it at {@code pull} blocks a tick; the last active tick is the Collapse, a
 * sphere of {@code collapse_radius} at the move's motion value and Impact. The player is rooted (the move's
 * {@code root}); a dash from the well's 12th tick (the move's cancel window) frees it and fires the Collapse
 * early at {@code early_scale} of its motion value and Impact. Any other end (a stagger, a weapon swap)
 * lets the well fade without a Collapse. The move's own hitbox is not used: the Collapse is this.
 *
 * <p>Tells the clients: {@link #PLANTED} (at the plant, value the radius, ticks the pull), {@link #COLLAPSE}
 * (value the Collapse radius, ticks its strength in percent), {@link #FADED}.
 *
 * <p>For other weapons' effects: {@link #live} lists the wells pulling now, and a {@link PullBoost} registered with
 * {@link #boost} multiplies a well's pull (the Choir Astrolabe's Singularity: a Pocket Star inside a well doubles it).
 */
public final class GravityWell implements ServerMoveEffect {
    public static final ResourceLocation ID = CosmicBreach.id("gravity_well");
    public static final int PLANTED = 0;
    public static final int COLLAPSE = 1;
    public static final int FADED = 2;

    public static final double RADIUS = 6.0;
    public static final double PULL = 0.12;
    public static final int PULL_TICKS = 30;
    public static final double OFFSET = 1.0;
    public static final double COLLAPSE_RADIUS = 3.0;
    public static final double EARLY_SCALE = 0.6;
    /** A pulled body stops this close to the centre (horizontally), instead of jittering across it. */
    private static final double ARRIVED = 0.6;
    /** The Collapse's sphere sits this far above the plant, around the bodies piled there. */
    private static final double COLLAPSE_HEIGHT = 1.0;

    private static final class Well {
        final int serial;
        final Vec3 centre;
        boolean collapsed;

        Well(int serial, Vec3 centre) {
            this.serial = serial;
            this.centre = centre;
        }
    }

    private final Map<ServerPlayer, Well> wells = new WeakHashMap<>();

    /** A well pulling now: where, how far it reaches, whose. */
    public record Live(net.minecraft.server.level.ServerLevel level, Vec3 centre, double radius, ServerPlayer owner) {
    }

    /** Something that makes a well pull harder: the multiplier for a well at {@code centre} of {@code radius} (1 for none). */
    @FunctionalInterface
    public interface PullBoost {
        double scale(net.minecraft.server.level.ServerLevel level, Vec3 centre, double radius);
    }

    private static final java.util.List<PullBoost> BOOSTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<Live> LIVE = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void boost(PullBoost boost) {
        BOOSTS.add(boost);
    }

    /** The wells pulling now (their pull ticks, before the Collapse), on the server. */
    public static java.util.List<Live> live() {
        LIVE.removeIf(l -> l.owner().isRemoved()); // a well whose owner left (or a world closed mid-pull)
        return java.util.List.copyOf(LIVE);
    }

    /**
     * Lists a well another effect opened (the Gravity Loop's, where a plunge lands), so a Pocket Star inside it makes a
     * Singularity too. Take it off with {@link #removeLive} when it stops pulling.
     */
    public static void addLive(Live well) {
        LIVE.add(well);
    }

    public static void removeLive(Live well) {
        LIVE.remove(well);
    }

    /** The product of every boost for a well at {@code centre}. */
    public static double pullScale(net.minecraft.server.level.ServerLevel level, Vec3 centre, double radius) {
        double scale = 1.0;
        for (PullBoost b : BOOSTS) {
            scale *= b.scale(level, centre, radius);
        }
        return scale;
    }

    @Override
    public boolean dealsItsOwnHits() {
        return true;
    }

    @Override
    public void activeTick(Use use, int activeTick) {
        Well well = wells.get(use.player());
        if (well == null || well.serial != use.move().serial()) {
            well = plant(use);
        }
        int pullTicks = use.intParam("pull_ticks", PULL_TICKS);
        if (activeTick < pullTicks) {
            pull(use, well);
        } else if (!well.collapsed) {
            unlist(use.player());
            collapse(use, well, 1.0);
        }
    }

    private static void unlist(ServerPlayer owner) {
        LIVE.removeIf(l -> l.owner() == owner);
    }

    @Override
    public void cancelled(Use use, MoveTraits.CancelBy by, MoveTraits.Stage stage, int tick) {
        Well well = wells.get(use.player());
        if (by == MoveTraits.CancelBy.DASH && well != null && well.serial == use.move().serial() && !well.collapsed) {
            unlist(use.player());
            collapse(use, well, use.param("early_scale", EARLY_SCALE));
        }
    }

    @Override
    public void ended(Use use, boolean cancelled) {
        Well well = wells.remove(use.player());
        unlist(use.player());
        if (well != null && well.serial == use.move().serial() && !well.collapsed) {
            use.send(FADED, well.centre, 0f, 0);
        }
    }

    /** The well's centre for a player standing at {@code feet} facing {@code yaw}: the plant, on the ground. */
    public static Vec3 centre(Vec3 feet, float yaw, double offset) {
        return feet.add(HitShape.forward(yaw).scale(offset));
    }

    private Well plant(Use use) {
        ServerPlayer player = use.player();
        double radius = use.param("radius", RADIUS);
        int pullTicks = use.intParam("pull_ticks", PULL_TICKS);
        Well well = new Well(use.move().serial(), centre(player.position(), player.getYRot(), use.param("offset", OFFSET)));
        wells.put(player, well);
        unlist(player);
        LIVE.add(new Live(player.serverLevel(), well.centre, radius, player));
        use.send(PLANTED, well.centre, (float) radius, pullTicks); // its sound is the move's swing (sound.swing)
        return well;
    }

    /** One tick of pull: every enemy in range that isn't a boss, a step toward the centre. */
    private static void pull(Use use, Well well) {
        ServerPlayer player = use.player();
        double radius = use.param("radius", RADIUS);
        double pull = use.param("pull", PULL) * pullScale(player.serverLevel(), well.centre, radius);
        AABB area = new AABB(well.centre, well.centre).inflate(radius);
        for (LivingEntity target : HitResolver.candidates(player, area)) {
            if (target.getType().is(Tags.EntityTypes.BOSSES) || target.position().distanceTo(well.centre) > radius
                    || com.cosmicbreach.combat.server.TargetShield.riddenByAnotherPlayer(target, player)) {
                continue; // a mount another player rides is moved by that player's client, never pushed from here
            }
            Vec3 to = well.centre.subtract(target.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            if (flat <= ARRIVED) {
                continue;
            }
            Vec3 step = new Vec3(to.x / flat, 0, to.z / flat).scale(Math.min(pull, flat - ARRIVED));
            if (target instanceof Player) {
                // a player's own client moves it: pull through its motion instead
                target.setDeltaMovement(target.getDeltaMovement().add(step));
                target.hurtMarked = true;
            } else {
                target.move(MoverType.SELF, step);
            }
        }
    }

    private static void collapse(Use use, Well well, double scale) {
        well.collapsed = true;
        ServerPlayer player = use.player();
        double collapseRadius = use.param("collapse_radius", COLLAPSE_RADIUS);
        double impact = use.move().def().hit().impact() * scale;
        int hit = HitResolver.effectHit(player, use.combat(), use.move(), new HitShape.Sphere(collapseRadius, 0.0),
                well.centre.add(0, COLLAPSE_HEIGHT, 0), player.getYRot(), use.move().mv() * scale, impact);
        if (hit > 0) {
            use.combat().machine().onHitLanded(use.move(), hit);
        }
        use.send(COLLAPSE, well.centre, (float) collapseRadius, (int) Math.round(scale * 100));
        ServerCombatSounds.forEveryone(player.level(), well.centre, ModSounds.MAUL_COLLAPSE, 1.0f, scale < 1.0 ? 1.12f : 1.0f);
    }
}
