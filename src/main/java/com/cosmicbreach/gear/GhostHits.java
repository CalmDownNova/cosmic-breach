package com.cosmicbreach.gear;

import com.cosmicbreach.combat.HitLedger;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.progression.ProgressionStats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A player's move landing from somewhere the player isn't: the Driftweave's Afterimage repeating an attack, the
 * Choir Regalia's echo of an ability. The move's own hitbox, from {@code feet} facing {@code yaw}, with the same
 * damage formula as the player's own hits (GDD 3.4, the attacker's stats, tier and {@link HitModifiers}) times a
 * {@code scale}, its own damage type (the player gets the credit), a push along its facing, Impact on poise (also
 * scaled), and a launch where the move launches. Hits are counted in a ledger of the ghost's own, so each target
 * takes the move's hits once per repeat. Server only.
 */
public final class GhostHits {
    /** What the hits are and where they come from. */
    public record Source(ServerPlayer owner, WeaponDef weapon, int tierSteps, Vec3 feet, float yaw, double damageScale,
                         double impactScale, ResourceKey<DamageType> type) {
        Vec3 chest() {
            return feet.add(0, HitResolver.CHEST * owner.getBbHeight(), 0);
        }
    }

    /** Hears of each hit: where it landed, along which way, on whom, with what Impact. */
    @FunctionalInterface
    public interface Landed {
        void hit(Vec3 point, Vec3 direction, LivingEntity target, double impact);
    }

    private GhostHits() {
    }

    /**
     * Active tick {@code activeTick} of {@code move}, from the source: everything valid in the hitbox with a clear
     * line from its chest. Returns how many hits landed.
     */
    public static int activeTick(Source src, MoveInstance move, int activeTick, HitLedger ledger, int serial, long tick,
                                 @Nullable Landed landed) {
        MoveDef def = move.def();
        MoveDef.Hit hit = def.hit();
        int now = hit.spread() ? hit.hitsAt(activeTick, def.timing().active()) : 1;
        if (now <= 0) {
            return 0;
        }
        Vec3 origin = src.chest();
        Vec3 forward = HitShape.forward(src.yaw());
        ServerLevel level = src.owner().serverLevel();
        AABB area = new AABB(origin, origin).inflate(def.hitbox().reach() + 1.5);
        int count = 0;
        for (LivingEntity target : HitResolver.candidates(src.owner(), area)) {
            if (!def.hitbox().hits(origin, src.yaw(), target.getBoundingBox()) || !clear(level, origin, target, src.owner())) {
                continue;
            }
            int granted = hit.spread()
                    ? ledger.tryHits(serial, target.getId(), tick, hit.hits(), now)
                    : (ledger.tryHit(serial, target.getId(), tick, hit.hits(), hit.hitInterval()) ? 1 : 0);
            for (int i = 0; i < granted && target.isAlive(); i++) {
                double push = hit.spread() ? HitResolver.knockback(hit.impact() * src.impactScale(), hit.hits())
                        : HitResolver.knockback(hit.impact() * src.impactScale());
                if (strike(src, move, target, move.mv(), hit.impact(), push, origin, forward, def.launch().orElse(null), landed)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** A plunge's landing from the source: a burst around its feet at the fall's motion value. */
    public static int landing(Source src, MoveInstance move, double fallBlocks, HitLedger ledger, int serial, long tick,
                              @Nullable Landed landed) {
        MoveDef def = move.def();
        MoveDef.Hit hit = def.hit();
        double mv = CombatMath.plungeMv(hit.mv(), hit.mvPerBlock(), hit.mvCap(), fallBlocks);
        Vec3 origin = src.feet().add(0, HitResolver.LANDING_HEIGHT, 0);
        ServerLevel level = src.owner().serverLevel();
        AABB area = new AABB(origin, origin).inflate(def.hitbox().reach() + 1.5);
        int count = 0;
        for (LivingEntity target : HitResolver.candidates(src.owner(), area)) {
            if (!def.hitbox().hits(origin, src.yaw(), target.getBoundingBox()) || !clear(level, origin, target, src.owner())) {
                continue;
            }
            if (!ledger.tryHit(serial, target.getId(), tick, hit.hits(), hit.hitInterval())) {
                continue;
            }
            Vec3 away = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
            Vec3 direction = away.lengthSqr() < 1e-6 ? HitShape.forward(src.yaw()) : away.normalize();
            if (strike(src, move, target, mv, hit.impact(), HitResolver.knockback(hit.impact() * src.impactScale()), origin,
                    direction, null, landed)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Everything valid in {@code shape} around {@code origin}, once each (an effect's own hit, like a Gravity Well's
     * Collapse), knocked away from the origin.
     */
    public static int burst(Source src, MoveInstance move, HitShape shape, Vec3 origin, double mv, double impact,
                            @Nullable Landed landed) {
        ServerLevel level = src.owner().serverLevel();
        AABB area = new AABB(origin, origin).inflate(shape.reach() + 1.0);
        int count = 0;
        for (LivingEntity target : HitResolver.candidates(src.owner(), area)) {
            if (!shape.hits(origin, src.yaw(), target.getBoundingBox()) || !clear(level, origin, target, src.owner())) {
                continue;
            }
            Vec3 away = new Vec3(target.getX() - origin.x, 0, target.getZ() - origin.z);
            Vec3 direction = away.lengthSqr() < 1e-6 ? HitShape.forward(src.yaw()) : away.normalize();
            if (strike(src, move, target, mv, impact, HitResolver.knockback(impact * src.impactScale()), origin, direction, null,
                    landed)) {
                count++;
            }
        }
        return count;
    }

    /** One hit on one target that something else found (a thrown blade's echo flying into it). */
    public static boolean single(Source src, MoveInstance move, LivingEntity target, double mv, Vec3 from, Vec3 direction,
                                 @Nullable Landed landed) {
        if (!HitResolver.isValidTarget(src.owner(), target)) {
            return false;
        }
        double impact = move.def().hit().impact();
        return strike(src, move, target, mv, impact, HitResolver.knockback(impact * src.impactScale()), from, direction, null, landed);
    }

    /** The damage one of the source's hits of {@code move} at {@code mv} deals {@code target}, before its armor. */
    public static double damage(Source src, MoveInstance move, double mv, @Nullable LivingEntity target) {
        StatBlock stats = ProgressionStats.of(src.owner());
        double buffs = HitModifiers.damageMultiplier(src.owner(), move, target);
        return src.damageScale() * HitResolver.damage(src.weapon(), move.def(), mv, move.critGuaranteed(), false, stats, buffs,
                src.tierSteps());
    }

    private static boolean strike(Source src, MoveInstance move, LivingEntity target, double mv, double impact, double push,
                                  Vec3 from, Vec3 direction, @Nullable MoveDef.Launch launch, @Nullable Landed landed) {
        ServerLevel level = src.owner().serverLevel();
        float amount = (float) damage(src, move, mv, target);
        DamageSource source = GearDamage.source(level, src.type(), src.owner(), from);
        target.invulnerableTime = 0;
        Vec3 before = target.getDeltaMovement();
        if (!target.hurt(source, amount)) {
            return false;
        }
        target.setDeltaMovement(before);
        target.knockback(push, -direction.x, -direction.z);
        target.hurtMarked = true;
        double scaledImpact = impact * src.impactScale();
        if (target.isAlive()) {
            PoiseTracker.addImpact(target, scaledImpact);
            if (launch != null && PoiseTracker.poiseOf(target) <= launch.maxPoise()) {
                HitResolver.launch(target, launch);
            }
        }
        if (landed != null) {
            AABB box = target.getBoundingBox();
            Vec3 point = new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY),
                    Mth.clamp(from.z, box.minZ, box.maxZ));
            landed.hit(point, direction, target, scaledImpact);
        }
        return true;
    }

    /** No block between {@code from} and the target's middle. */
    private static boolean clear(ServerLevel level, Vec3 from, LivingEntity target, ServerPlayer owner) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType()
                == HitResult.Type.MISS;
    }
}
