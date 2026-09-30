package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.MoveSound;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.net.HitFxPayload;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.registry.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turns a move's active tick, a plunge landing or a ground wave step into hits: finds valid targets
 * inside the shape, deals the damage, knocks back, adds Impact, launches, and tells the clients.
 * Server only; the attacker is always a player with a {@link PlayerCombat}.
 */
public final class HitResolver {
    /** Swings start at the chest: this fraction of the attacker's height above its feet. */
    public static final double CHEST = 0.55;
    /** Plunge landings hit from just above the feet. */
    public static final double LANDING_HEIGHT = 0.5;
    public static final double KNOCKBACK_BASE = 0.4;
    public static final double KNOCKBACK_PER_IMPACT = 0.02;
    /** Horizontal speed a launched target keeps, so it stays in front for the follow-up. */
    public static final double LAUNCH_HORIZONTAL_KEEP = 0.25;
    /** Hit-stop for a crit is at least this (GDD section 4.1). */
    public static final int CRIT_HITSTOP = 4;

    private HitResolver() {
    }

    /** {@link #activeTick(ServerPlayer, PlayerCombat, MoveInstance, int, long)} on the move's first active tick. */
    public static int activeTick(ServerPlayer player, PlayerCombat combat, MoveInstance move, long tick) {
        return activeTick(player, combat, move, 0, tick);
    }

    /**
     * Active tick {@code activeTick} of {@code move}: everything valid inside its hitbox. Hits spread over the
     * active ticks ({@link MoveDef.Hit#spread}) deal this tick's share to each target inside, each hit pushing
     * {@link #knockback(double, int)}. Returns how many hits landed.
     */
    public static int activeTick(ServerPlayer player, PlayerCombat combat, MoveInstance move, int activeTick, long tick) {
        WeaponDef weapon = combat.machine().weapon();
        if (weapon == null) {
            return 0;
        }
        MoveDef def = move.def();
        MoveDef.Hit hit = def.hit();
        Vec3 origin = chest(player);
        float yaw = player.getYRot();
        Vec3 forward = HitShape.forward(yaw);
        AABB area = player.getBoundingBox().inflate(def.hitbox().reach() + 1.0 + MAX_LAG_SLACK);
        HitSound sound = new HitSound(def);
        int now = hit.spread() ? hit.hitsAt(activeTick, def.timing().active()) : 1;
        if (now <= 0) {
            return 0;
        }
        BlockStrikes.strike(player, def.hitbox(), origin, yaw, hit.impact(), move.serial());
        int count = 0;
        for (LivingEntity target : candidates(player, area)) {
            if (!def.hitbox().hits(origin, yaw, seenBox(player, target)) || !player.hasLineOfSight(target)) {
                continue;
            }
            int granted;
            if (hit.spread()) {
                granted = combat.hits().tryHits(move.serial(), target.getId(), tick, hit.hits(), now);
            } else {
                granted = combat.hits().tryHit(move.serial(), target.getId(), tick, hit.hits(), hit.hitInterval()) ? 1 : 0;
            }
            for (int i = 0; i < granted && target.isAlive(); i++) {
                if (strike(player, combat, target, move, weapon, move.mv(), hit.impact(), knockback(hit.impact(), hit.spread() ? hit.hits() : 1),
                        origin, forward, def.launch().orElse(null), sound)) {
                    count++;
                }
            }
        }
        sound.play(player, hit.impact());
        return count;
    }

    /**
     * A hit of {@code move} on one {@code target} that something other than the move's hitbox decided (the
     * Binary Edges' thrown blade striking what it flew into): at motion value {@code mv} and {@code impact},
     * pushed along {@code direction}, sparks at {@code from}'s nearest point. True if it did damage.
     */
    public static boolean strikeTarget(ServerPlayer player, PlayerCombat combat, MoveInstance move, LivingEntity target,
                                       double mv, double impact, Vec3 from, Vec3 direction) {
        WeaponDef weapon = combat.machine().weapon();
        if (weapon == null || !isValidTarget(player, target)) {
            return false;
        }
        HitSound sound = new HitSound(move.def());
        boolean struck = strike(player, combat, target, move, weapon, mv, impact, knockback(impact), from, direction, null, sound);
        sound.play(player, impact);
        return struck;
    }

    /**
     * {@link #strikeTarget(ServerPlayer, PlayerCombat, MoveInstance, LivingEntity, double, double, Vec3, Vec3)} for a hit
     * that lands after its move (the Choir Astrolabe's bolts): the weapon and its stack as they were when it was made,
     * so putting the weapon away doesn't change the bolt in flight, and no hit-stop for the attacker.
     */
    public static boolean strikeRanged(ServerPlayer player, PlayerCombat combat, MoveInstance move, WeaponDef weapon,
                                       ItemStack weaponStack, LivingEntity target, double mv, double impact, Vec3 from,
                                       Vec3 direction) {
        if (!isValidTarget(player, target)) {
            return false;
        }
        HitSound sound = new HitSound(move.def());
        boolean struck = strike(player, combat, target, move, weapon, weaponStack, true, mv, impact, knockback(impact), from,
                direction, null, sound);
        sound.play(player, impact);
        return struck;
    }

    /**
     * {@link #effectHit(ServerPlayer, PlayerCombat, MoveInstance, HitShape, Vec3, float, double, double)} for an effect
     * that outlives its move (the Pocket Star's pulses and Supernova): with the weapon and stack it was cast with, no
     * hit-stop, and every target hit handed to {@code each} (may be null).
     */
    public static int effectHitRanged(ServerPlayer player, PlayerCombat combat, MoveInstance move, WeaponDef weapon,
                                      ItemStack weaponStack, HitShape shape, Vec3 origin, float yaw, double mv, double impact,
                                      @Nullable java.util.function.Consumer<LivingEntity> each) {
        AABB area = new AABB(origin, origin).inflate(shape.reach() + 1.0);
        BlockStrikes.strike(player, shape, origin, yaw, impact, move.serial());
        HitSound sound = new HitSound(move.def());
        int count = 0;
        for (LivingEntity target : candidates(player, area)) {
            if (!shape.hits(origin, yaw, target.getBoundingBox()) || !clearBetween(player, origin, target)) {
                continue;
            }
            Vec3 away = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
            Vec3 direction = away.lengthSqr() < 1e-6 ? HitShape.forward(yaw) : away.normalize();
            if (strike(player, combat, target, move, weapon, weaponStack, true, mv, impact, knockback(impact), origin, direction,
                    null, sound)) {
                count++;
                if (each != null) {
                    each.accept(target);
                }
            }
        }
        sound.play(player, impact);
        return count;
    }

    /** A plunge landed after falling {@code fallBlocks}: a burst around the feet. */
    public static int plungeLanding(ServerPlayer player, PlayerCombat combat, MoveInstance move, double fallBlocks, long tick) {
        WeaponDef weapon = combat.machine().weapon();
        if (weapon == null) {
            return 0;
        }
        MoveDef def = move.def();
        MoveDef.Hit hit = def.hit();
        double mv = plungeMv(hit, fallBlocks, HitModifiers.uncapsPlunge(player));
        Vec3 origin = player.position().add(0, LANDING_HEIGHT, 0);
        float yaw = player.getYRot();
        BlockStrikes.strike(player, def.hitbox(), origin, yaw, hit.impact(), move.serial());
        AABB area = player.getBoundingBox().inflate(def.hitbox().reach() + 1.0);
        HitSound sound = new HitSound(def);
        int count = 0;
        for (LivingEntity target : candidates(player, area)) {
            if (!def.hitbox().hits(origin, yaw, target.getBoundingBox()) || !player.hasLineOfSight(target)) {
                continue;
            }
            if (!combat.hits().tryHit(move.serial(), target.getId(), tick, hit.hits(), hit.hitInterval())) {
                continue;
            }
            Vec3 away = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
            Vec3 direction = away.lengthSqr() < 1e-6 ? HitShape.forward(yaw) : away.normalize();
            if (strike(player, combat, target, move, weapon, mv, hit.impact(), knockback(hit.impact()), origin, direction, null, sound)) {
                count++;
            }
        }
        sound.play(player, hit.impact());
        return count;
    }

    /**
     * A plunge's motion value after falling {@code fallBlocks} (GDD 4.1: damage grows with the height fallen), up to the
     * move's cap unless {@code uncapped} (the Gravity Loop: no height cap).
     */
    public static double plungeMv(MoveDef.Hit hit, double fallBlocks, boolean uncapped) {
        return CombatMath.plungeMv(hit.mv(), hit.mvPerBlock(), uncapped ? Double.POSITIVE_INFINITY : hit.mvCap(), fallBlocks);
    }

    /** One step of a ground wave: everything valid in its slice that this wave hasn't hit yet. */
    public static int waveStep(ServerPlayer player, PlayerCombat combat, GroundWave wave, GroundWave.Step step) {
        MoveInstance move = wave.move();
        WeaponDef weapon = wave.weapon();
        if (move == null || weapon == null) {
            return 0;
        }
        Vec3 forward = HitShape.forward(wave.yaw());
        Vec3 end = step.origin().add(forward.scale(step.to() - step.from()));
        double half = wave.width() / 2.0 + 0.5;
        AABB area = new AABB(step.origin(), end).inflate(half, GroundWave.BAND_HEIGHT / 2.0 + 0.5, half);
        BlockStrikes.strike(player, step.shape(), step.origin(), wave.yaw(), move.def().hit().impact(), move.serial());
        HitSound sound = new HitSound(move.def());
        int count = 0;
        for (LivingEntity target : candidates(player, area)) {
            if (!step.shape().hits(step.origin(), wave.yaw(), target.getBoundingBox()) || !player.hasLineOfSight(target)) {
                continue;
            }
            if (!wave.markHit(target.getId())) {
                continue;
            }
            if (strike(player, combat, target, move, weapon, wave.mv(), move.def().hit().impact(), knockback(move.def().hit().impact()),
                    step.origin(), forward, null, sound)) {
                count++;
            }
        }
        sound.play(player, move.def().hit().impact());
        return count;
    }

    /**
     * A move effect's own hit (the Gravity Well's Collapse): everything valid in {@code shape} around
     * {@code origin}, once each, at motion value {@code mv} and {@code impact}, knocked away from the
     * origin. Line of sight is checked from the origin. Returns how many were hit.
     */
    public static int effectHit(ServerPlayer player, PlayerCombat combat, MoveInstance move, HitShape shape, Vec3 origin,
                                float yaw, double mv, double impact) {
        WeaponDef weapon = combat.machine().weapon();
        if (weapon == null) {
            return 0;
        }
        AABB area = new AABB(origin, origin).inflate(shape.reach() + 1.0);
        BlockStrikes.strike(player, shape, origin, yaw, impact, move.serial());
        HitSound sound = new HitSound(move.def());
        int count = 0;
        for (LivingEntity target : candidates(player, area)) {
            if (!shape.hits(origin, yaw, target.getBoundingBox()) || !clearBetween(player, origin, target)) {
                continue;
            }
            Vec3 away = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
            Vec3 direction = away.lengthSqr() < 1e-6 ? HitShape.forward(yaw) : away.normalize();
            if (strike(player, combat, target, move, weapon, mv, impact, knockback(impact), origin, direction, null, sound)) {
                count++;
            }
        }
        sound.play(player, impact);
        return count;
    }

    /** No block between {@code from} and the target's middle. */
    private static boolean clearBetween(ServerPlayer player, Vec3 from, LivingEntity target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Living entities in {@code area} this player may hit. */
    /** The most a target's box grows for its attacker's latency, in blocks. */
    public static final double MAX_LAG_SLACK = 1.0;

    /**
     * How far a target may have moved between what its attacker saw and where the server tests the swing: its speed
     * (blocks a tick) times the attacker's round trip, capped at {@link #MAX_LAG_SLACK}. Zero for a player with no latency
     * (the host), so a local game plays exactly as before. Pure.
     */
    public static double lagSlack(double speedPerTick, int latencyMs) {
        if (!(speedPerTick > 0) || latencyMs <= 0) {
            return 0.0;
        }
        return Math.min(MAX_LAG_SLACK, speedPerTick * (latencyMs / 50.0));
    }

    /** {@code target}'s box as {@code attacker} could have seen it: widened sideways by {@link #lagSlack}. */
    static AABB seenBox(ServerPlayer attacker, LivingEntity target) {
        AABB box = target.getBoundingBox();
        int latency = attacker.connection == null ? 0 : attacker.connection.latency();
        double slack = lagSlack(Math.hypot(target.getX() - target.xo, target.getZ() - target.zo), latency);
        return slack <= 0.0 ? box : box.inflate(slack, 0.0, slack);
    }

    public static List<LivingEntity> candidates(ServerPlayer player, AABB area) {
        return player.level().getEntitiesOfClass(LivingEntity.class, area, target -> isValidTarget(player, target));
    }

    /**
     * Not the player or its mount, alive, not a spectator, not its own pet, not another player's pet or mount (or anything
     * carrying a player) that PvP or a team protects ({@link TargetShield}), and a player only where PvP is allowed.
     */
    public static boolean isValidTarget(ServerPlayer player, LivingEntity target) {
        if (target == player || target == player.getVehicle() || target.isSpectator() || !target.isAlive() || !target.isAttackable()) {
            return false;
        }
        if (target instanceof OwnableEntity owned && player.getUUID().equals(owned.getOwnerUUID())) {
            return false;
        }
        if (TargetShield.shelters(player, target)) {
            return false;
        }
        return !(target instanceof Player other) || player.canHarmPlayer(other);
    }

    /** {@link #damage(WeaponDef, MoveDef, double, boolean, boolean, StatBlock)} at zero stats. */
    public static double damage(WeaponDef weapon, MoveDef move, double mv, boolean crit, boolean riposte) {
        return damage(weapon, move, mv, crit, riposte, StatBlock.ZERO);
    }

    /**
     * GDD section 3.4 at the weapon's unlock tier: Base x MV x scaling x Crit x Riposte, with the
     * attacker's effective stats in the grade scaling (abilities also scale with Arcane) and the crit
     * multiplier (Power).
     */
    public static double damage(WeaponDef weapon, MoveDef move, double mv, boolean crit, boolean riposte, StatBlock stats) {
        return damage(weapon, move, mv, crit, riposte, stats, 1.0);
    }

    /** {@link #damage(WeaponDef, MoveDef, double, boolean, boolean, StatBlock)} times {@code buffs} ({@link HitModifiers}). */
    public static double damage(WeaponDef weapon, MoveDef move, double mv, boolean crit, boolean riposte, StatBlock stats,
                                double buffs) {
        return damage(weapon, move, mv, crit, riposte, stats, buffs, 0);
    }

    /**
     * The full GDD 3.4 formula: {@code tierSteps} reforges above the weapon's unlock tier multiply its base by
     * 1.15 each ({@link GearTier}).
     */
    public static double damage(WeaponDef weapon, MoveDef move, double mv, boolean crit, boolean riposte, StatBlock stats,
                                double buffs, int tierSteps) {
        return damage(weapon, move, mv, crit, riposte, stats, buffs, tierSteps, 0.0);
    }

    /**
     * {@link #damage(WeaponDef, MoveDef, double, boolean, boolean, StatBlock, double, int)} with {@code critGear} added to
     * a crit's multiplier (GDD 3.4's gear term: the Perihelion Loop's +25% after a dash), still capped at 2.5.
     */
    public static double damage(WeaponDef weapon, MoveDef move, double mv, boolean crit, boolean riposte, StatBlock stats,
                                double buffs, int tierSteps, double critGear) {
        double scaling = move.kind() == MoveKind.ABILITY
                ? CombatMath.abilityScaling(weapon.grades(), stats)
                : CombatMath.scaling(weapon.grades(), stats);
        double critMultiplier = crit ? CombatMath.critMultiplier(stats, critGear) : 1.0;
        double riposteMultiplier = riposte ? CombatRules.RIPOSTE_MULTIPLIER : 1.0;
        return CombatMath.hitDamage(weapon.baseDamage(), tierSteps, mv, scaling, critMultiplier, riposteMultiplier, buffs);
    }

    /** Knockback strength for a hit with this Impact. */
    public static double knockback(double impact) {
        return KNOCKBACK_BASE + impact * KNOCKBACK_PER_IMPACT;
    }

    /**
     * Knockback of each of {@code hits} hits of {@code impact} spread over one move: together they push as far
     * as one hit of all their Impact would, so a whirl keeps its target in reach instead of batting it away.
     */
    public static double knockback(double impact, int hits) {
        int n = Math.max(1, hits);
        return knockback(impact * n) / n;
    }

    private static Vec3 chest(ServerPlayer player) {
        return player.position().add(0, CHEST * player.getBbHeight(), 0);
    }

    /** The crit chance of a hit by a player with {@code stats} on {@code target}, with every {@link HitModifiers} bonus. */
    public static double critChance(@Nullable ServerPlayer player, StatBlock stats, @Nullable LivingEntity target) {
        return CombatMath.critChance(stats, HitModifiers.critChanceBonus(player, target));
    }

    /** Deals one hit. Returns false if the target took no damage (invulnerable, cancelled, blocked). */
    private static boolean strike(ServerPlayer player, PlayerCombat combat, LivingEntity target, MoveInstance move,
                                  WeaponDef weapon, double mv, double impact, double push, Vec3 from, Vec3 direction,
                                  @Nullable MoveDef.Launch launch, HitSound sound) {
        return strike(player, combat, target, move, weapon, player.getMainHandItem(), false, mv, impact, push, from, direction,
                launch, sound);
    }

    /**
     * Deals one hit. {@code weaponStack} is the weapon that made it (its reforges count); a {@code ranged} hit (a bolt
     * that lands after its move is over) holds the attacker's animation for no ticks, crit or not.
     */
    private static boolean strike(ServerPlayer player, PlayerCombat combat, LivingEntity target, MoveInstance move,
                                  WeaponDef weapon, ItemStack weaponStack, boolean ranged, double mv, double impact,
                                  double push, Vec3 from, Vec3 direction, @Nullable MoveDef.Launch launch, HitSound sound) {
        MoveDef.Hit hit = move.def().hit();
        StatBlock stats = combat.machine().stats();
        boolean crit = move.critGuaranteed() || player.getRandom().nextDouble() < critChance(player, stats, target);
        boolean riposte = combat.machine().consumeRiposte();
        int tierSteps = GearTier.stepsAbove(weaponStack, weapon.tier());
        double critGear = crit ? HitModifiers.critMultiplierBonus(player, target) : 0.0;
        float amount = (float) damage(weapon, move.def(), mv, crit, riposte, stats,
                HitModifiers.damageMultiplier(player, move, target), tierSteps, critGear);

        target.invulnerableTime = 0; // the engine paces its own hits
        Vec3 before = target.getDeltaMovement();
        if (!target.hurt(player.damageSources().playerAttack(player), amount)) {
            return false;
        }
        // Replace vanilla's knockback toward the attacker's position with one along the swing.
        target.setDeltaMovement(before);
        target.knockback(push, -direction.x, -direction.z);
        player.setLastHurtMob(target);
        if (target.isAlive()) {
            PoiseTracker.addImpact(target, impact);
            if (launch != null && PoiseTracker.poiseOf(target) <= launch.maxPoise()) {
                launch(target, launch);
            }
        }

        int hitstop = ranged ? 0 : crit ? Math.max(hit.hitstop(), CRIT_HITSTOP) : hit.hitstop();
        Vec3 point = nearestPoint(target.getBoundingBox(), from);
        ModNetworking.sendToTrackersSelfAnd(target, new HitFxPayload(player.getId(), target.getId(),
                point, direction, (float) impact, crit, hitstop), player);
        sound.landed(point, crit);
        for (StrikeListener listener : LISTENERS) {
            listener.struck(player, move, target, point);
        }
        return true;
    }

    /** Told of every hit the engine lands (after the damage): an ability's light where it hits, for one. */
    @FunctionalInterface
    public interface StrikeListener {
        void struck(ServerPlayer player, MoveInstance move, LivingEntity target, Vec3 point);
    }

    private static final List<StrikeListener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onStrike(StrikeListener listener) {
        LISTENERS.add(listener);
    }

    /**
     * One hit sound per swing tick, however many targets it struck: at the first one, and the crit
     * sound if any of them was a crit. Played for everyone by {@link ServerCombatSounds#hit}.
     */
    private static final class HitSound {
        private final @Nullable Holder<SoundEvent> own;
        private @Nullable Vec3 at;
        private boolean crit;

        HitSound(MoveDef def) {
            this.own = def.traits().sound().flatMap(MoveTraits.Sounds::hit).map(MoveSound::resolve).orElse(null);
        }

        void landed(Vec3 point, boolean wasCrit) {
            if (at == null) {
                at = point;
            }
            crit |= wasCrit;
        }

        void play(ServerPlayer player, double impact) {
            if (at != null) {
                ServerCombatSounds.hit(player.level(), at, impact, crit, player.getRandom(), own);
            }
        }
    }

    /**
     * Zenith: straight up to the launch height, then Suspended (players only get the launch). Public for hits the
     * engine doesn't resolve itself (a set's echo of the ability).
     */
    public static void launch(LivingEntity target, MoveDef.Launch launch) {
        double gravity = target.getGravity();
        if (gravity <= 0) {
            return; // flying or gravity-free: nothing to suspend against
        }
        double v0 = LaunchMath.velocityForHeight(launch.height(), gravity);
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(v.x * LAUNCH_HORIZONTAL_KEEP, v0, v.z * LAUNCH_HORIZONTAL_KEEP);
        target.hasImpulse = true;
        target.hurtMarked = true;
        target.resetFallDistance();
        if (!(target instanceof Player)) {
            target.setData(ModAttachments.SUSPENSION, new Suspension(launch.suspendTicks()));
        }
    }

    private static Vec3 nearestPoint(AABB box, Vec3 from) {
        return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY),
                Mth.clamp(from.z, box.minZ, box.maxZ));
    }
}
