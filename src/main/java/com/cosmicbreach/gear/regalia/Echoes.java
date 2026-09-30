package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.combat.HitLedger;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.combat.server.effect.ServerMoveEffect;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.combat.server.effect.TetherBlades;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.GhostHits;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.net.ModNetworking;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Harmonics' echoes (GDD 5.1): 10 ticks after the third cast, a ghost of the caster replays the ability from where
 * it was cast, facing the way it was cast, at 60% power (damage and Impact), free: the move's timeline again from
 * its first tick, its hitbox on its active ticks, a launch where it launches, and its effects that have an echo
 * here: the Comet Maul's Gravity Well (a well planted at the same spot that pulls and collapses) and the Binary
 * Edges' Tether (a ghost blade thrown along the cast's aim that strikes the first enemy in its path; no blink).
 * An effect with no echo is left out (and logged once); if it dealt the move's damage itself, the echo deals none.
 * Echo hits use their own damage type, {@code cosmicbreach:echo}, and count as ability hits (Aligned enemies take
 * 10% more). Server only; clients are told to draw the ghost, the well and the blade ({@link SetFxPayload}).
 */
public final class Echoes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();
    /** A pulled body stops this close to the well's centre. */
    private static final double ARRIVED = 0.6;
    private static final double COLLAPSE_HEIGHT = 1.0;
    /** The ghost blade's reach sideways from its path. */
    private static final double BLADE_RADIUS = 0.5;

    private static final class Echo {
        final UUID owner;
        final ServerLevel level;
        final MoveInstance move;
        final WeaponDef weapon;
        final int tierSteps;
        final Vec3 feet;
        final float yaw;
        final Vec3 eye;
        final Vec3 look;
        final long start;
        final HitLedger ledger = new HitLedger();
        Vec3 wellCentre;
        boolean collapsed;
        Vec3 blade;
        double bladeTravelled;
        boolean bladeDone;

        Echo(ServerPlayer player, MoveInstance move, WeaponDef weapon, int tierSteps, long start) {
            this.owner = player.getUUID();
            this.level = player.serverLevel();
            this.move = move;
            this.weapon = weapon;
            this.tierSteps = tierSteps;
            this.feet = player.position();
            this.yaw = player.getYRot();
            this.eye = player.getEyePosition();
            this.look = player.getLookAngle();
            this.start = start;
        }

        int length() {
            MoveDef.Timing t = move.def().timing();
            return t.startup() + t.active() + t.recovery();
        }
    }

    private static final List<Echo> ECHOES = new ArrayList<>();

    private Echoes() {
    }

    /** The cast {@code move} (just started) echoes at {@code start}. */
    static void schedule(ServerPlayer player, PlayerCombat combat, WeaponDef weapon, MoveInstance move, long start) {
        int tierSteps = GearTier.stepsAbove(player.getMainHandItem(), weapon.tier());
        MoveInstance echo = new MoveInstance(move.serial(), move.id(), move.def(), move.mv(), false);
        ECHOES.add(new Echo(player, echo, weapon, tierSteps, start));
    }

    /** Echoes still running or waiting (tests and scenarios). */
    public static int pending() {
        return ECHOES.size();
    }

    /** Every server tick: each echo's timeline. */
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Echo> it = ECHOES.iterator();
        while (it.hasNext()) {
            Echo echo = it.next();
            ServerPlayer owner = echo.level.getServer().getPlayerList().getPlayer(echo.owner);
            long now = echo.level.getGameTime();
            if (owner == null || owner.level() != echo.level || !owner.isAlive()) {
                it.remove();
                continue;
            }
            long elapsed = now - echo.start;
            if (elapsed < 0) {
                continue;
            }
            if (elapsed == 0) {
                begin(owner, echo);
            }
            MoveDef.Timing timing = echo.move.def().timing();
            long activeTick = elapsed - timing.startup();
            if (activeTick >= 0 && activeTick < timing.active()) {
                active(owner, echo, (int) activeTick, now);
            }
            if (echo.blade != null && !echo.bladeDone) {
                flyBlade(owner, echo);
            }
            if (elapsed >= echo.length() && (echo.blade == null || echo.bladeDone)) {
                it.remove();
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ECHOES.clear();
    }

    private static GhostHits.Source source(ServerPlayer owner, Echo echo) {
        return new GhostHits.Source(owner, echo.weapon, echo.tierSteps, echo.feet, echo.yaw, RegaliaRules.ECHO_POWER,
                RegaliaRules.ECHO_POWER, GearSets.ECHO_DAMAGE);
    }

    private static void begin(ServerPlayer owner, Echo echo) {
        echo.level.playSound(null, echo.feet.x, echo.feet.y + 1.0, echo.feet.z, GearSets.ECHO.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(owner, new SetFxPayload(SetFxPayload.Kind.ECHO, owner.getId(), echo.feet, echo.yaw,
                echo.look, echo.move.id(), echo.length(), (float) RegaliaRules.ECHO_POWER));
    }

    private static void active(ServerPlayer owner, Echo echo, int activeTick, long now) {
        MoveDef def = echo.move.def();
        boolean ownHits = false;
        for (MoveEffect effect : def.traits().effects()) {
            ServerMoveEffect handler = ServerMoveEffects.get(effect.id());
            if (handler != null && handler.dealsItsOwnHits()) {
                ownHits = true;
            }
            if (effect.id().equals(GravityWell.ID)) {
                well(owner, echo, effect, activeTick);
            } else if (effect.id().equals(TetherBlades.ID)) {
                if (activeTick == 0) {
                    throwBlade(owner, echo, effect);
                }
            } else if (handler != null && REPORTED.add(effect.id())) {
                LOGGER.debug("[cosmicbreach] no echo for move effect {}: the echo leaves it out", effect.id());
            }
        }
        if (!ownHits) {
            GhostHits.activeTick(source(owner, echo), echo.move, activeTick, echo.ledger, echo.move.serial(), now,
                    (point, direction, target, impact) -> struck(owner, point, direction, impact));
        }
    }

    // ------------------------------------------------------------------ the Gravity Well's echo

    private static void well(ServerPlayer owner, Echo echo, MoveEffect effect, int activeTick) {
        double radius = effect.param("radius", GravityWell.RADIUS);
        int pullTicks = effect.intParam("pull_ticks", GravityWell.PULL_TICKS);
        if (echo.wellCentre == null) {
            echo.wellCentre = GravityWell.centre(echo.feet, echo.yaw, effect.param("offset", GravityWell.OFFSET));
            ModNetworking.sendToTrackersAndSelf(owner, new SetFxPayload(SetFxPayload.Kind.ECHO_WELL, owner.getId(), echo.wellCentre,
                    echo.yaw, Vec3.ZERO, echo.move.id(), pullTicks, (float) radius));
        }
        if (activeTick < pullTicks) {
            pull(owner, echo.wellCentre, radius, effect.param("pull", GravityWell.PULL));
        } else if (!echo.collapsed) {
            echo.collapsed = true;
            double collapseRadius = effect.param("collapse_radius", GravityWell.COLLAPSE_RADIUS);
            GhostHits.burst(source(owner, echo), echo.move, new HitShape.Sphere(collapseRadius, 0.0),
                    echo.wellCentre.add(0, COLLAPSE_HEIGHT, 0), echo.move.mv(), echo.move.def().hit().impact(),
                    (point, direction, target, impact) -> struck(owner, point, direction, impact));
            ModNetworking.sendToTrackersAndSelf(owner, new SetFxPayload(SetFxPayload.Kind.ECHO_COLLAPSE, owner.getId(), echo.wellCentre,
                    echo.yaw, Vec3.ZERO, echo.move.id(), 0, (float) collapseRadius));
        }
    }

    /** One tick of the echo's pull: every enemy in range that isn't a boss, a step toward the centre (as the well's). */
    private static void pull(ServerPlayer owner, Vec3 centre, double radius, double pull) {
        AABB area = new AABB(centre, centre).inflate(radius);
        for (LivingEntity target : HitResolver.candidates(owner, area)) {
            if (target.getType().is(Tags.EntityTypes.BOSSES) || target.position().distanceTo(centre) > radius
                    || com.cosmicbreach.combat.server.TargetShield.riddenByAnotherPlayer(target, owner)) {
                continue; // a mount another player rides is moved by that player's client, never pushed from here
            }
            Vec3 to = centre.subtract(target.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            if (flat <= ARRIVED) {
                continue;
            }
            Vec3 step = new Vec3(to.x / flat, 0, to.z / flat).scale(Math.min(pull, flat - ARRIVED));
            if (target instanceof Player) {
                target.setDeltaMovement(target.getDeltaMovement().add(step));
                target.hurtMarked = true;
            } else {
                target.move(MoverType.SELF, step);
            }
        }
    }

    // ------------------------------------------------------------------ the Tether's echo

    private static void throwBlade(ServerPlayer owner, Echo echo, MoveEffect effect) {
        float yaw = echo.yaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        echo.blade = echo.eye.add(echo.look.scale(0.4)).add(left.scale(0.28)).add(0, -0.3, 0);
        echo.bladeTravelled = 0.0;
        double range = effect.param("range", 16.0);
        double speed = effect.param("speed", 2.0);
        ModNetworking.sendToTrackersAndSelf(owner, new SetFxPayload(SetFxPayload.Kind.ECHO_THROW, owner.getId(), echo.blade, echo.yaw,
                echo.look, echo.move.id(), (int) Math.ceil(range / speed), (float) range));
    }

    /** One tick of the ghost blade: it flies on, and strikes the first enemy in its path (or stops at a block). */
    private static void flyBlade(ServerPlayer owner, Echo echo) {
        MoveEffect effect = echo.move.def().traits().effects().stream().filter(e -> e.id().equals(TetherBlades.ID)).findFirst().orElse(null);
        double range = effect == null ? 16.0 : effect.param("range", 16.0);
        double speed = effect == null ? 2.0 : effect.param("speed", 2.0);
        double step = Math.min(speed, range - echo.bladeTravelled);
        if (step <= 1e-3) {
            echo.bladeDone = true;
            return;
        }
        Vec3 from = echo.blade;
        Vec3 to = from.add(echo.look.scale(step));
        BlockHitResult block = echo.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (block.getType() != HitResult.Type.MISS) {
            to = block.getLocation();
            echo.bladeDone = true;
        }
        AABB sweep = new AABB(from, to).inflate(BLADE_RADIUS);
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity target : HitResolver.candidates(owner, sweep)) {
            var hit = target.getBoundingBox().inflate(BLADE_RADIUS).clip(from, to);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(from);
                if (d < best) {
                    best = d;
                    first = target;
                }
            }
        }
        if (first != null) {
            GhostHits.single(source(owner, echo), echo.move, first, echo.move.mv(), from, echo.look,
                    (point, direction, target, impact) -> struck(owner, point, direction, impact));
            echo.bladeDone = true;
            return;
        }
        echo.bladeTravelled += step;
        echo.blade = to;
        if (echo.bladeTravelled >= range - 0.05) {
            echo.bladeDone = true;
        }
    }

    private static void struck(ServerPlayer owner, Vec3 point, Vec3 direction, double impact) {
        ModNetworking.sendToTrackersAndSelf(owner, new SetFxPayload(SetFxPayload.Kind.ECHO_STRIKE, owner.getId(), point, 0f,
                direction, SetFxPayload.NO_MOVE, 0, (float) impact));
    }
}
