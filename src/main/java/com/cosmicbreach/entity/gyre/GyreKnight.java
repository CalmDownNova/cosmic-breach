package com.cosmicbreach.entity.gyre;

import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PoiseSource;
import com.cosmicbreach.combat.Staggerable;
import com.cosmicbreach.entity.gyre.GyreModes.Mode;
import com.cosmicbreach.guardian.AttackPicker;
import com.cosmicbreach.guardian.Telegraphs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Gyre Knight (GDD 7.1), the Drift Belt's elite: an empty suit of silver-blue armor round a spinning gyroscope core,
 * three blades orbiting it on tilted rings ({@link GyreOrbit}). Health 90, armor 10, poise 50, flies at 0.25 and strafes
 * in 3D, holding 6 to 10 blocks above its target; its orbit modes ({@link GyreModes}) come round on cooldowns, picked by
 * range. Every two or three Lance Volleys at a player on foot it dives to sword reach (1.1: a telegraphed drop, one parryable
 * cut, two seconds hanging beside its target with its core open). A poise break sinks it stunned, slowly, to its target's
 * level for {@value GyreModes#STUN} ticks with its core exposed (x1.5), as while its blades are out. Drops: Gyre Core,
 * 1 to 2 Nebulite Ingots, a Gyre Blade (50%), a Gravity Loop (8%). 600 Attunement XP.
 */
public class GyreKnight extends Monster implements GeoEntity, ParryableAttacker, PoiseSource, Staggerable {
    public static final double HEALTH = 90.0;
    public static final double ARMOR = 10.0;
    public static final double POISE = 50.0;
    public static final double FLY_SPEED = 0.25;
    public static final double HOVER_MIN = 6.0;
    public static final double HOVER_MAX = 10.0;
    public static final float WIDTH = 0.9f;
    public static final float HEIGHT = 2.3f;
    /** The gyroscope core's height over its feet. */
    public static final double CORE_Y = 1.35;
    public static final double FOLLOW_RANGE = 40.0;
    public static final int EXPERIENCE = 15;

    public enum Blade { ORBIT, FLY, STUCK, RETURN, OUT, AIMED, RIP, BROKEN }

    public static final byte EVENT_GLINT = 70;
    public static final byte EVENT_PARRIED = 71;
    public static final byte EVENT_STUN = 72;
    public static final byte EVENT_DEFLECT = 73;
    public static final byte EVENT_FIRE = 74;
    public static final byte EVENT_RIP = 75;

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_MODE_START = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_APPROACH = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SPIN_BASE = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BLADES = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_MARK = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.VECTOR3);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Vector3f>[] DATA_BLADE_AT = new EntityDataAccessor[GyreOrbit.BLADES];

    static {
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            DATA_BLADE_AT[i] = SynchedEntityData.defineId(GyreKnight.class, EntityDataSerializers.VECTOR3);
        }
    }

    private static final RawAnimation HOVER_ANIM = RawAnimation.begin().thenLoop("hover");
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenLoop("sweep");
    private static final RawAnimation LANCE_ANIM = RawAnimation.begin().thenLoop("lance");
    private static final RawAnimation STUN_ANIM = RawAnimation.begin().thenPlayAndHold("stunned");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // server state
    private Mode mode = Mode.SHIELD;
    private long modeStart;
    private int approachEnd = -1;
    private double spinBase;
    private final AttackPicker<Mode> picker = new AttackPicker<>();
    private final Blade[] blades = {Blade.ORBIT, Blade.ORBIT, Blade.ORBIT};
    private final Vec3[] bladeAt = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
    private final Vec3[] bladeDir = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
    private final Vec3[] bladeFrom = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
    private final long[] bladeSince = new long[GyreOrbit.BLADES];
    private final long[] brokenUntil = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private final Set<Long> hits = new HashSet<>();
    private Vec3 mark = Vec3.ZERO;
    private double strafe;
    private int strafeSign = 1;
    private boolean parryableNow;
    private double currentImpact;
    private int hittingBlade = -1;
    private int bladesBroken;
    private int deflected;
    private int stuns;
    private long holdUntil = Long.MIN_VALUE;
    // the dive (1.1): a melee commit after every two or three lance volleys at a player on foot
    private int volleys;
    private int divesAfter;
    private int dives;
    private @Nullable Vec3 diveAnchor;
    private boolean diveCueGiven;
    private @Nullable Vec3 diveMark;
    private long diveMarkTick;
    private @Nullable Vec3 stunAnchor;
    // client: the last two synced blade points
    private final Vec3[] clientPrev = new Vec3[GyreOrbit.BLADES];
    private final Vec3[] clientCur = new Vec3[GyreOrbit.BLADES];
    private final int[] modesSeen = new int[Mode.values().length];

    public GyreKnight(EntityType<? extends GyreKnight> type, Level level) {
        super(type, level);
        this.xpReward = EXPERIENCE;
        setNoGravity(true);
        this.divesAfter = rollDivesAfter();
    }

    private int rollDivesAfter() {
        return GyreModes.VOLLEYS_MIN + random.nextInt(GyreModes.VOLLEYS_MAX - GyreModes.VOLLEYS_MIN + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, FLY_SPEED)
                .add(Attributes.FLYING_SPEED, FLY_SPEED)
                .add(Attributes.ATTACK_DAMAGE, GyreModes.SWEEP_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) 0);
        builder.define(DATA_MODE_START, 0L);
        builder.define(DATA_APPROACH, -1);
        builder.define(DATA_SPIN_BASE, 0f);
        builder.define(DATA_BLADES, 0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_MARK, new Vector3f());
        for (EntityDataAccessor<Vector3f> a : DATA_BLADE_AT) {
            builder.define(a, new Vector3f());
        }
    }

    // ------------------------------------------------------------------ spawning

    /** Natural spawns (Drift Belt air near rock, rare) and anything else by the usual rules; see {@link GyreKnights}. */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0f;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ the core

    public Vec3 core() {
        return position().add(0, CORE_Y, 0);
    }

    /** True while its core is exposed: stunned, hanging after a dive's cut, or any blade away from its ring. */
    public boolean coreExposed() {
        if (mode() == Mode.STUNNED || mode() == Mode.DIVE && GyreModes.diveHanging(level().getGameTime() - modeStart(), approachEnd())) {
            return true;
        }
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            Blade b = blade(i);
            if (b != Blade.ORBIT && b != Blade.BROKEN) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                Vec3 now = bladePoint(i);
                clientPrev[i] = clientCur[i] == null ? now : clientCur[i];
                clientCur[i] = now;
            }
            GyreEffects.handler().knightTick(this);
        }
    }

    /**
     * Blade {@code i}'s middle for drawing (client): on its ring, from the synced mode at the frame's time (smooth); away
     * from it, between the last two synced points.
     */
    public Vec3 renderBlade(int i, float partial) {
        if (blade(i) == Blade.ORBIT) {
            double t = level().getGameTime() + partial - modeStart();
            double r = GyreModes.ringRadius(mode(), t, approachEnd());
            double spin = spinBase() + GyreModes.spin(mode(), t, approachEnd());
            return getPosition(partial).add(0, CORE_Y, 0).add(GyreOrbit.offset(i, r, spin));
        }
        if (clientCur[i] == null) {
            return bladePoint(i);
        }
        return clientPrev[i].lerp(clientCur[i], partial);
    }

    /** Which way blade {@code i} points for drawing: out from the core on its ring, along its flight away from it. */
    public Vec3 renderBladeDirection(int i, float partial) {
        Vec3 at = renderBlade(i, partial);
        Blade b = blade(i);
        if (b == Blade.ORBIT) {
            double t = level().getGameTime() + partial - modeStart();
            return GyreOrbit.direction(i, GyreModes.ringRadius(mode(), t, approachEnd()), spinBase() + GyreModes.spin(mode(), t, approachEnd()));
        }
        if ((b == Blade.FLY || b == Blade.RETURN || b == Blade.RIP) && clientCur[i] != null && clientPrev[i] != null) {
            Vec3 d = clientCur[i].subtract(clientPrev[i]);
            if (d.lengthSqr() > 1e-4) {
                return b == Blade.RETURN ? d.normalize().scale(-1) : d.normalize();
            }
        }
        Vec3 d = at.subtract(getPosition(partial).add(0, CORE_Y, 0));
        return d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel server = (ServerLevel) level();
        long now = server.getGameTime();
        long t = now - modeStart;
        Player target = chooseTarget(now);
        if (target == null && mode != Mode.STUNNED && mode != Mode.SHIELD) {
            switchMode(Mode.SHIELD, now);
            t = 0;
        }
        steer(target, now, t);
        faceTarget(target);
        switch (mode) {
            case SHIELD -> {
                if (target != null && t >= GyreModes.SHIELD_MIN && now >= holdUntil) {
                    pickMode(target, now);
                }
            }
            case SWEEP -> sweepTick(server, target, now, t);
            case LANCE -> lanceTick(server, target, now, t);
            case RECALL -> recallTick(server, target, now, t);
            case DIVE -> diveTick(server, now, t);
            case STUNNED -> {
                if (t >= GyreModes.STUN) {
                    for (int i = 0; i < GyreOrbit.BLADES; i++) {
                        setBlade(i, Blade.RETURN, now);
                    }
                    switchMode(Mode.SHIELD, now);
                }
            }
        }
        if (mode != Mode.SHIELD && mode != Mode.STUNNED && GyreModes.done(mode, now - modeStart, approachEnd)) {
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                if (blades[i] != Blade.ORBIT && blades[i] != Blade.BROKEN) {
                    setBlade(i, Blade.RETURN, now);
                }
            }
            switchMode(Mode.SHIELD, now);
        }
        moveBlades(server, now);
        syncBlades();
    }

    private @Nullable Player chooseTarget(long now) {
        Player current = getTarget() instanceof Player p ? p : null;
        if (current != null && fair(current) && current.distanceToSqr(this) < FOLLOW_RANGE * FOLLOW_RANGE && (now + getId()) % 20 != 0) {
            return current;
        }
        Player best = null;
        double bestD = FOLLOW_RANGE * FOLLOW_RANGE;
        for (Player p : level().players()) {
            double d = p.distanceToSqr(this);
            if (fair(p) && d < bestD) {
                best = p;
                bestD = d;
            }
        }
        setTarget(best);
        entityData.set(DATA_TARGET, best == null ? -1 : best.getId());
        return best;
    }

    private boolean fair(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Flies: holds above its target, strafing round it; swoops level for a sweep; dives to sword reach; sinks when stunned. */
    private void steer(@Nullable Player target, long now, long t) {
        Vec3 v = getDeltaMovement();
        Vec3 want;
        if (mode == Mode.STUNNED) {
            // it sinks slowly to beside its target at their level, or onto the rock under it, never into the gap (1.1)
            if (stunAnchor == null) {
                stunAnchor = GyreModes.stunAnchor(position(), target == null ? null : target.position());
            }
            setDeltaMovement(onGround() ? Vec3.ZERO : GyreModes.stunVelocity(position(), stunAnchor));
            return;
        }
        if (mode == Mode.DIVE) {
            steerDive(target, t);
            return;
        }
        if (target == null) {
            want = new Vec3(0, 0.02 * Math.sin(now * 0.05), 0);
        } else if (mode == Mode.SWEEP) {
            Vec3 chest = target.position().add(0, target.getBbHeight() * 0.5 - CORE_Y + 0.2, 0);
            Vec3 from = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
            from = from.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : from.normalize();
            // level with its target and a blade's length from it: the blades' circle (radius 4) passes through the target
            Vec3 point = chest.add(from.scale(GyreModes.SWEEP_RADIUS - 0.2));
            Vec3 d = point.subtract(position());
            double speed = approachEnd < 0 ? 0.42 : 0.12;
            want = d.length() > speed ? d.normalize().scale(speed) : d;
            if (approachEnd < 0 && (d.length() < 1.3 || t >= GyreModes.SWEEP_APPROACH)) {
                approachEnd = (int) t;
                entityData.set(DATA_APPROACH, approachEnd);
            }
        } else {
            if ((now + getId()) % 80 == 0 && random.nextFloat() < 0.35f) {
                strafeSign = -strafeSign;
            }
            strafe += 0.03 * strafeSign;
            double height = (HOVER_MIN + HOVER_MAX) / 2.0 + 1.6 * Math.sin(now * 0.035 + getId());
            Vec3 point = target.position().add(Math.cos(strafe) * 5.0, height, Math.sin(strafe) * 5.0);
            Vec3 d = point.subtract(position());
            double len = d.length();
            want = len > FLY_SPEED ? d.scale(FLY_SPEED / len) : d;
        }
        Vec3 next = v.add(want.subtract(v).scale(0.25));
        if (horizontalCollision) {
            next = next.add(0, 0.12, 0);
        }
        setDeltaMovement(next);
    }

    /**
     * The dive: still through the tell, then down to where its target stood when the tell ended (beside them, at their level),
     * then hanging within reach of them wherever they go.
     */
    private void steerDive(@Nullable Player target, long t) {
        if (t < GyreModes.DIVE_TELL || target == null && diveAnchor == null) {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            return;
        }
        if (diveAnchor == null) {
            diveAnchor = GyreModes.standOff(target.position(), position());
        }
        Vec3 d = diveAnchor.subtract(position());
        if (approachEnd < 0) {
            double len = d.length();
            setDeltaMovement(len > GyreModes.DIVE_SPEED ? d.scale(GyreModes.DIVE_SPEED / len) : d);
            if (GyreModes.strikeCueDue(len, diveCueGiven)) {
                // the cut is about to land: a glint on the blades and a rising whine, in time to parry
                diveCueGiven = true;
                ((ServerLevel) level()).broadcastEntityEvent(this, EVENT_GLINT);
                playSound(GyreKnights.WHINE.get(), 1.8f, 1.4f);
            }
            // pressed against rock (an overhang, a ledge): it cuts where it is instead of burning the descent's whole cap
            boolean stuck = false;
            if (diveMark == null) {
                diveMark = position();
                diveMarkTick = t;
            } else if (t - diveMarkTick >= GyreModes.DIVE_STUCK_TICKS) {
                stuck = GyreModes.diveStuck(diveMark.distanceTo(position()));
                diveMark = position();
                diveMarkTick = t;
            }
            if (len < 0.6 || stuck || t >= GyreModes.DIVE_TELL + GyreModes.DIVE_DESCENT_MAX) {
                approachEnd = (int) t;
                entityData.set(DATA_APPROACH, approachEnd);
            }
        } else {
            // hanging: it stays beside its target, wherever the cut's knockback or a dodge has put them (1.1)
            setDeltaMovement(target == null ? d.scale(0.3) : GyreModes.hangVelocity(position(), target.position()));
        }
    }

    private static boolean onFoot(Player p) {
        return !p.isPassenger() && p.onGround();
    }

    private void faceTarget(@Nullable Player target) {
        if (target == null || mode == Mode.STUNNED) {
            return;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float yaw = Mth.approachDegrees(getYRot(), want, 12.0f);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void pickMode(Player target, long now) {
        int ready = 0;
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            if (blades[i] == Blade.ORBIT) {
                ready++;
            }
        }
        // a dive that would press into rock waits (the count is kept, so it is owed again at the next pick)
        boolean lineClear = volleys < divesAfter || diveLineClear(target);
        if (ready > 0 && GyreModes.diveDue(volleys, divesAfter, onFoot(target), core().distanceTo(target.getBoundingBox().getCenter()), lineClear)) {
            volleys = 0;
            divesAfter = rollDivesAfter();
            switchMode(Mode.DIVE, now);
            return;
        }
        List<AttackPicker.Option<Mode>> options = GyreModes.options(core().distanceTo(target.getBoundingBox().getCenter()),
                getHealth() / getMaxHealth(), ready);
        Mode next = picker.pick(options, now, random::nextDouble);
        if (next == null) {
            return;
        }
        int cooldown = options.stream().filter(o -> o.attack() == next).findFirst().map(AttackPicker.Option::cooldownTicks).orElse(0);
        picker.used(next, cooldown, now);
        switchMode(next, now);
        if (next == Mode.LANCE) {
            volleys++;
        }
        if (next == Mode.RECALL) {
            startRecall(target, now);
        }
    }

    /** True if nothing solid lies between its core and the spot beside {@code target} where a dive would end. */
    private boolean diveLineClear(Player target) {
        Vec3 spot = GyreModes.standOff(target.position(), position()).add(0, CORE_Y, 0);
        return level().clip(new ClipContext(core(), spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Starts {@code next} now (the debug command's way too; the Recall needs a target). */
    public void forceMode(Mode next) {
        long now = level().getGameTime();
        Player target = getTarget() instanceof Player p ? p : null;
        switchMode(next, now);
        if (next == Mode.RECALL && target != null) {
            startRecall(target, now);
        }
        if (next == Mode.STUNNED) {
            onStagger(GyreModes.STUN);
        }
    }

    private void switchMode(Mode next, long now) {
        spinBase = (spinBase + GyreModes.spin(mode, now - modeStart, approachEnd)) % (Math.PI * 2.0);
        mode = next;
        modeStart = now;
        approachEnd = -1;
        hits.clear();
        modesSeen[next.ordinal()]++;
        entityData.set(DATA_MODE, (byte) next.ordinal());
        entityData.set(DATA_MODE_START, now);
        entityData.set(DATA_APPROACH, -1);
        entityData.set(DATA_SPIN_BASE, (float) spinBase);
        diveAnchor = null;
        diveCueGiven = false;
        diveMark = null;
        stunAnchor = null;
        switch (next) {
            case LANCE -> playSound(GyreKnights.LANCE_TELL.get(), 1.4f, 1.0f);
            case RECALL -> playSound(GyreKnights.RECALL_TELL.get(), 1.6f, 1.0f);
            case DIVE -> {
                dives++;
                playSound(GyreKnights.DIVE_TELL.get(), 1.8f, 1.0f);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Sweep Orbit

    private void sweepTick(ServerLevel server, @Nullable Player target, long now, long t) {
        if (approachEnd < 0) {
            return;
        }
        long u = t - approachEnd;
        if (u == 0) {
            playSound(GyreKnights.WHINE.get(), 1.6f, 1.0f);
        }
        if (u == GyreModes.SWEEP_TELL - 4) {
            server.broadcastEntityEvent(this, EVENT_GLINT);
        }
        if (u == GyreModes.SWEEP_TELL) {
            playSound(GyreKnights.SWEEP.get(), 1.8f, 1.0f);
        }
        if (!GyreModes.sweepCutting(t, approachEnd)) {
            return;
        }
        currentImpact = GyreModes.SWEEP_IMPACT;
        double r = GyreModes.ringRadius(Mode.SWEEP, t, approachEnd);
        for (double sub : new double[] {t - 0.5, t}) {
            double spin = spinBase + GyreModes.spin(Mode.SWEEP, sub, approachEnd);
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                if (blades[i] != Blade.ORBIT) {
                    continue;
                }
                Vec3[] edge = GyreOrbit.edge(GyreOrbit.offset(i, r, spin));
                Vec3 a = core().add(edge[0]);
                Vec3 b = core().add(edge[1]);
                for (Player p : level().getEntitiesOfClass(Player.class, new AABB(a, b).inflate(1.0), this::fair)) {
                    long key = ((long) i << 32) ^ p.getId();
                    if (hits.contains(key) || !Telegraphs.onLine(p.getBoundingBox(), a, b, GyreOrbit.BLADE_THICKNESS)) {
                        continue;
                    }
                    hits.add(key);
                    hittingBlade = i;
                    parryableNow = true;
                    p.hurt(damageSources().mobAttack(this), (float) GyreModes.SWEEP_DAMAGE);
                    parryableNow = false;
                    hittingBlade = -1;
                }
            }
        }
    }

    @Override
    public boolean isParryableAttackActive() {
        return parryableNow;
    }

    @Override
    public double attackImpact() {
        return currentImpact;
    }

    /** A parried blade breaks for 5 s: it tumbles away. */
    @Override
    public void onParried(Player player) {
        if (hittingBlade >= 0 && blades[hittingBlade] == Blade.ORBIT) {
            int i = hittingBlade;
            long now = level().getGameTime();
            bladeFrom[i] = bladeAt[i];
            bladeDir[i] = bladeAt[i].subtract(core()).normalize().scale(0.18);
            setBlade(i, Blade.BROKEN, now);
            brokenUntil[i] = now + GyreModes.BLADE_BROKEN;
            bladesBroken++;
            level().broadcastEntityEvent(this, EVENT_PARRIED);
            playSound(GyreKnights.BLADE_BREAK.get(), 1.8f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ Dive (1.1)

    /** The dive's glint near the tell's end, then one cut through everyone within reach of its core as the descent ends. */
    private void diveTick(ServerLevel server, long now, long t) {
        if (t == GyreModes.DIVE_TELL - 4) {
            server.broadcastEntityEvent(this, EVENT_GLINT);
        }
        if (approachEnd < 0 || t != approachEnd) {
            return;
        }
        playSound(GyreKnights.SWEEP.get(), 1.8f, 1.2f);
        currentImpact = GyreModes.DIVE_IMPACT;
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(GyreModes.DIVE_REACH), this::fair)) {
            if (p.getBoundingBox().getCenter().distanceTo(core()) > GyreModes.DIVE_REACH + 0.6) {
                continue;
            }
            hittingBlade = 0;
            parryableNow = true;
            p.hurt(damageSources().mobAttack(this), (float) GyreModes.DIVE_DAMAGE);
            parryableNow = false;
            hittingBlade = -1;
        }
    }

    // ------------------------------------------------------------------ Lance Volley

    private void lanceTick(ServerLevel server, @Nullable Player target, long now, long t) {
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            if (t == GyreModes.fireTick(i) - GyreModes.BLADE_TELL && blades[i] == Blade.ORBIT) {
                playSound(GyreKnights.LANCE_TELL.get(), 1.2f, 1.1f + 0.1f * i);
            }
            if (t == GyreModes.fireTick(i) && blades[i] == Blade.ORBIT && target != null) {
                Vec3 aim = target.getBoundingBox().getCenter();
                bladeFrom[i] = bladeAt[i];
                bladeDir[i] = aim.subtract(bladeAt[i]).normalize();
                setBlade(i, Blade.FLY, now);
                server.broadcastEntityEvent(this, EVENT_FIRE);
                level().playSound(null, bladeAt[i].x, bladeAt[i].y, bladeAt[i].z, GyreKnights.LANCE_FIRE.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
            }
        }
    }

    // ------------------------------------------------------------------ Recall Crash

    private void startRecall(Player target, long now) {
        mark = target.getBoundingBox().getCenter();
        entityData.set(DATA_MARK, new Vector3f((float) mark.x, (float) mark.y, (float) mark.z));
        Vec3 away = mark.subtract(core());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            if (blades[i] != Blade.ORBIT) {
                continue;
            }
            double turn = Math.toRadians(28.0 * (i - 1));
            Vec3 d = new Vec3(flat.x * Math.cos(turn) - flat.z * Math.sin(turn), 0, flat.x * Math.sin(turn) + flat.z * Math.cos(turn));
            bladeFrom[i] = bladeAt[i];
            bladeDir[i] = mark.add(d.scale(GyreModes.RECALL_BEYOND)).add(0, -0.4 + 0.4 * i, 0); // the spot it flies out to
            setBlade(i, Blade.OUT, now);
        }
    }

    private void recallTick(ServerLevel server, @Nullable Player target, long now, long t) {
        if (t == GyreModes.RECALL_OUT) {
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                if (blades[i] == Blade.OUT) {
                    setBlade(i, Blade.AIMED, now);
                }
            }
        }
        if (t == GyreModes.RECALL_OUT + GyreModes.RECALL_TELL) {
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                if (blades[i] == Blade.AIMED) {
                    bladeFrom[i] = bladeAt[i];
                    setBlade(i, Blade.RIP, now);
                }
            }
            server.broadcastEntityEvent(this, EVENT_RIP);
            playSound(GyreKnights.RECALL.get(), 2.0f, 1.0f);
        }
    }

    /** Where a ripping blade is {@code f} (0 to 1) of the way back: along its spot, through the mark, to the core. */
    static Vec3 ripPoint(Vec3 spot, Vec3 mark, Vec3 core, double f) {
        double a = spot.distanceTo(mark);
        double b = mark.distanceTo(core);
        double s = f * (a + b);
        if (s <= a) {
            return spot.add(mark.subtract(spot).scale(a < 1e-6 ? 1.0 : s / a));
        }
        return mark.add(core.subtract(mark).scale(b < 1e-6 ? 1.0 : (s - a) / b));
    }

    // ------------------------------------------------------------------ the blades

    private void setBlade(int i, Blade state, long now) {
        blades[i] = state;
        bladeSince[i] = now;
    }

    private void moveBlades(ServerLevel server, long now) {
        double t = now - modeStart;
        double r = GyreModes.ringRadius(mode, t, approachEnd);
        double spin = spinBase + GyreModes.spin(mode, t, approachEnd);
        Vec3 core = core();
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            long since = now - bladeSince[i];
            Vec3 slot = core.add(GyreOrbit.offset(i, r, spin));
            switch (blades[i]) {
                case ORBIT -> bladeAt[i] = slot;
                case FLY -> {
                    Vec3 from = bladeAt[i];
                    Vec3 to = from.add(bladeDir[i].scale(GyreModes.LANCE_SPEED));
                    BlockHitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                    Player hit = null;
                    for (Player p : level().getEntitiesOfClass(Player.class, new AABB(from, to).inflate(0.6), this::fair)) {
                        if (Telegraphs.onLine(p.getBoundingBox(), from, to, 0.4)) {
                            hit = p;
                            break;
                        }
                    }
                    if (hit != null) {
                        currentImpact = GyreModes.LANCE_IMPACT;
                        hit.hurt(damageSources().mobAttack(this), (float) GyreModes.LANCE_DAMAGE);
                        level().playSound(null, hit.getX(), hit.getY(), hit.getZ(), GyreKnights.LANCE_HIT.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
                        bladeAt[i] = hit.getBoundingBox().getCenter();
                        setBlade(i, Blade.RETURN, now);
                    } else if (block.getType() == HitResult.Type.BLOCK) {
                        bladeAt[i] = block.getLocation();
                        setBlade(i, Blade.STUCK, now);
                        level().playSound(null, bladeAt[i].x, bladeAt[i].y, bladeAt[i].z, GyreKnights.LANCE_HIT.get(), SoundSource.HOSTILE, 1.2f, 0.8f);
                    } else {
                        bladeAt[i] = to;
                        if (to.distanceTo(bladeFrom[i]) >= GyreModes.LANCE_RANGE) {
                            setBlade(i, Blade.RETURN, now);
                        }
                    }
                }
                case STUCK -> {
                    if (since >= GyreModes.LANCE_STICK) {
                        setBlade(i, Blade.RETURN, now);
                    }
                }
                case RETURN -> {
                    Vec3 d = slot.subtract(bladeAt[i]);
                    double len = d.length();
                    if (len <= GyreModes.RETURN_SPEED) {
                        bladeAt[i] = slot;
                        setBlade(i, Blade.ORBIT, now);
                    } else {
                        bladeAt[i] = bladeAt[i].add(d.scale(GyreModes.RETURN_SPEED / len));
                    }
                }
                case OUT -> {
                    double f = Math.min(1.0, since / (double) GyreModes.RECALL_OUT);
                    double e = f * f * (3 - 2 * f);
                    bladeAt[i] = bladeFrom[i].add(bladeDir[i].subtract(bladeFrom[i]).scale(e));
                }
                case AIMED -> {
                }
                case RIP -> {
                    double f = Math.min(1.0, since / (double) GyreModes.RECALL_RIP);
                    Vec3 before = bladeAt[i];
                    bladeAt[i] = ripPoint(bladeFrom[i], mark, core, f);
                    currentImpact = GyreModes.RECALL_IMPACT;
                    for (Player p : level().getEntitiesOfClass(Player.class, new AABB(before, bladeAt[i]).inflate(0.7), this::fair)) {
                        long key = ((long) (i + 8) << 32) ^ p.getId();
                        if (!hits.contains(key) && Telegraphs.onLine(p.getBoundingBox(), before, bladeAt[i], 0.45)) {
                            hits.add(key);
                            p.hurt(damageSources().mobAttack(this), (float) GyreModes.RECALL_DAMAGE);
                        }
                    }
                    if (f >= 1.0) {
                        setBlade(i, Blade.ORBIT, now);
                    }
                }
                case BROKEN -> {
                    bladeDir[i] = bladeDir[i].scale(0.94).add(0, -0.012, 0);
                    Vec3 next = bladeAt[i].add(bladeDir[i]);
                    if (level().clip(new ClipContext(bladeAt[i], next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                            != HitResult.Type.BLOCK) {
                        bladeAt[i] = next;
                    } else {
                        bladeDir[i] = Vec3.ZERO;
                    }
                    if (now >= brokenUntil[i] && mode != Mode.STUNNED) {
                        setBlade(i, Blade.RETURN, now);
                    }
                }
            }
        }
    }

    private void syncBlades() {
        int packed = 0;
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            packed |= blades[i].ordinal() << (4 * i);
            Vec3 p = bladeAt[i];
            entityData.set(DATA_BLADE_AT[i], new Vector3f((float) p.x, (float) p.y, (float) p.z));
        }
        entityData.set(DATA_BLADES, packed);
    }

    // ------------------------------------------------------------------ poise, hits

    @Override
    public double poise() {
        return POISE;
    }

    /** Its poise broke: it sinks, stunned, to its target's level (see {@link GyreModes#stunAnchor}), its blades falling, its core exposed. */
    @Override
    public void onStagger(int ticks) {
        if (level().isClientSide() || mode == Mode.STUNNED) {
            return;
        }
        long now = level().getGameTime();
        stuns++;
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            bladeDir[i] = bladeAt[i].subtract(core()).normalize().scale(0.1);
            setBlade(i, Blade.BROKEN, now);
            brokenUntil[i] = now + GyreModes.STUN;
        }
        switchMode(Mode.STUNNED, now);
        level().broadcastEntityEvent(this, EVENT_STUN);
        playSound(GyreKnights.STUN.get(), 1.8f, 1.0f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return super.hurt(source, amount);
        }
        if (source.getDirectEntity() instanceof Projectile projectile && mode == Mode.SHIELD && bladesAtRing() > 0) {
            Vec3 from = projectile.position().subtract(position());
            Vec3 facing = Vec3.directionFromRotation(0f, getYRot());
            if (from.x * facing.x + from.z * facing.z > 0) {
                deflected++;
                level().broadcastEntityEvent(this, EVENT_DEFLECT);
                playSound(GyreKnights.DEFLECT.get(), 1.4f, 0.9f + random.nextFloat() * 0.2f);
                projectile.discard();
                return false;
            }
        }
        float dealt = coreExposed() ? (float) (amount * GyreModes.CORE_EXPOSED) : amount;
        return super.hurt(source, dealt);
    }

    private int bladesAtRing() {
        int n = 0;
        for (Blade b : blades) {
            if (b == Blade.ORBIT) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GyreKnights.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GyreKnights.DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.4f;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 128.0 * 128.0;
    }

    // ------------------------------------------------------------------ state (both sides)

    public Mode mode() {
        int o = entityData.get(DATA_MODE);
        Mode[] all = Mode.values();
        return o >= 0 && o < all.length ? all[o] : Mode.SHIELD;
    }

    public long modeStart() {
        return entityData.get(DATA_MODE_START);
    }

    /** Ticks into the sweep its approach ended (it began to tell), or -1. */
    public int approachEnd() {
        return entityData.get(DATA_APPROACH);
    }

    public double spinBase() {
        return entityData.get(DATA_SPIN_BASE);
    }

    public Blade blade(int i) {
        int o = (entityData.get(DATA_BLADES) >> (4 * i)) & 0xF;
        Blade[] all = Blade.values();
        return o < all.length ? all[o] : Blade.ORBIT;
    }

    /** Blade {@code i}'s middle, as the server last placed it. */
    public Vec3 bladePoint(int i) {
        Vector3f v = entityData.get(DATA_BLADE_AT[i]);
        return new Vec3(v.x, v.y, v.z);
    }

    /** Where the target stood when a Recall Crash began. */
    public Vec3 recallMark() {
        Vector3f v = entityData.get(DATA_MARK);
        return new Vec3(v.x, v.y, v.z);
    }

    public @Nullable Entity targetEntity() {
        int id = entityData.get(DATA_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Server counters for tests: {blades broken by parries, projectiles deflected, stuns}. */
    public int[] counts() {
        return new int[] {bladesBroken, deflected, stuns};
    }

    /** Dives begun (server, for tests). */
    public int dives() {
        return dives;
    }

    /** Debug: its next pick at a player on foot is a dive. */
    public void diveNext() {
        volleys = divesAfter;
    }

    /** How many times each mode began (server). */
    public int[] modesSeen() {
        return modesSeen.clone();
    }

    /** Debug: it keeps its Shield Orbit for {@code ticks} (forced modes still go). */
    public void holdModes(int ticks) {
        holdUntil = level().getGameTime() + ticks;
    }

    /** Debug: its mode's picker forgets every cooldown. */
    public void clearCooldowns() {
        picker.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_GLINT && id <= EVENT_RIP && level().isClientSide()) {
            GyreEffects.handler().knightEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 5, this::animate));
    }

    private PlayState animate(AnimationState<GyreKnight> s) {
        RawAnimation anim = switch (mode()) {
            case STUNNED -> STUN_ANIM;
            case SWEEP, DIVE -> SWEEP_ANIM;
            case LANCE, RECALL -> LANCE_ANIM;
            case SHIELD -> HOVER_ANIM;
        };
        return s.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The natural spawn rule's randomness, kept in one place for the placement predicate. */
    static boolean rare(RandomSource random) {
        return random.nextInt(GyreKnights.RARITY) == 0;
    }

    /** A new Knight's ids to log by. */
    public UUID id() {
        return getUUID();
    }
}
