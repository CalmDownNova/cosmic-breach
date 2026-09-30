package com.cosmicbreach.entity.edges;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.combat.server.effect.TetherBlades;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Binary Edges' left sickle, thrown by the Tether (GDD 4.2): it flies straight at {@code speed} blocks a
 * tick for up to {@code range} blocks, strikes the first enemy in its path (the Tether's motion value) and
 * sticks in it, riding along, or sticks in the first block; there it stays {@code stick} ticks. It comes back
 * to the thrower's hand when it expires, when it missed (flew its range through nothing), when what it
 * stuck in dies, or when the thrower lets go of the Edges; a blink to it retrieves it in place. Its owner's
 * server state ({@link TetherBlades}) hears of every change, which opens and closes the blink.
 *
 * <p>Both sides move it the same way: straight while flying, riding its target or held in its block while
 * stuck (from synced data: which entity, and where), and home to the owner's left hand while returning. The
 * server alone decides hits. Never saved.
 */
public class ThrownSickle extends Projectile {
    public static final byte FLYING = 0;
    public static final byte STUCK = 1;
    public static final byte RETURNING = 2;

    /** Returning, it closes on the owner's hand at this speed (blocks a tick); gone once this close. */
    public static final double RETURN_SPEED = 2.4;
    private static final double RETURN_ARRIVED = 0.9;
    private static final int RETURN_MAX_TICKS = 14;
    /** A stuck blade sits this far back out of what it hit, along its flight. */
    private static final double STICK_BACK_OFF = 0.12;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ThrownSickle.class, EntityDataSerializers.BYTE);
    /** The entity it is stuck in, plus 1 (0 for a block or none). */
    private static final EntityDataAccessor<Integer> STUCK_IN = SynchedEntityData.defineId(ThrownSickle.class, EntityDataSerializers.INT);
    /** Stuck in a block: where, in the world. Stuck in an entity: where on it, turned with its body. */
    private static final EntityDataAccessor<Vector3f> STUCK_AT = SynchedEntityData.defineId(ThrownSickle.class, EntityDataSerializers.VECTOR3);
    /** The face of the block it is stuck in (a Direction's 3D index), or -1. */
    private static final EntityDataAccessor<Byte> STUCK_FACE = SynchedEntityData.defineId(ThrownSickle.class, EntityDataSerializers.BYTE);

    // server only
    private @Nullable MoveInstance throwMove;
    private double range = 16.0;
    private int stickTicks = 80;
    private double soloScale = 0.6;
    private int markTicks = 60;
    private double markCrit = 0.2;
    private double travelled;
    private int stuckAge;
    private int returnAge;
    private boolean blinkedTo;
    private boolean returnedReported;

    public ThrownSickle(EntityType<? extends ThrownSickle> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** A blade leaving {@code owner}'s hand at {@code from} along {@code direction} (unit) at {@code speed} a tick. */
    public ThrownSickle(Level level, ServerPlayer owner, Vec3 from, Vec3 direction, double speed) {
        this(ModEntities.THROWN_SICKLE.get(), level);
        setOwner(owner);
        setPos(from.x, from.y, from.z);
        setDeltaMovement(direction.scale(speed));
        float yaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    /** The throw's numbers (the Tether's effect parameters) and its move, for the hit. Server only. */
    public void configure(MoveInstance move, double range, int stickTicks, double soloScale, int markTicks, double markCrit) {
        this.throwMove = move;
        this.range = range;
        this.stickTicks = stickTicks;
        this.soloScale = soloScale;
        this.markTicks = markTicks;
        this.markCrit = markCrit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, FLYING);
        builder.define(STUCK_IN, 0);
        builder.define(STUCK_AT, new Vector3f());
        builder.define(STUCK_FACE, (byte) -1);
    }

    // ------------------------------------------------------------------ state

    public byte state() {
        return entityData.get(STATE);
    }

    /** Flying or stuck: away from the thrower's hand (not on its way back). */
    public boolean isOut() {
        return state() != RETURNING && !isRemoved();
    }

    public boolean isStuck() {
        return state() == STUCK;
    }

    /** The entity it is stuck in, if any (either side). */
    public @Nullable Entity stuckEntity() {
        int id = entityData.get(STUCK_IN) - 1;
        return id < 0 ? null : level().getEntity(id);
    }

    /** The face of the block it is stuck in, or null (in an entity, or not stuck). */
    public @Nullable Direction stuckFace() {
        int face = entityData.get(STUCK_FACE);
        return face < 0 ? null : Direction.from3DDataValue(face);
    }

    /** The owner as a player, either side. */
    public @Nullable Player ownerPlayer() {
        return getOwner() instanceof Player player ? player : null;
    }

    public double soloScale() {
        return soloScale;
    }

    public int stuckAge() {
        return stuckAge;
    }

    /** Blocks flown so far (server). For tests. */
    public double travelled() {
        return travelled;
    }

    /** How long an enemy it strikes stays Marked, and the Mark's crit chance bonus. Server only. */
    public int markTicks() {
        return markTicks;
    }

    public double markCrit() {
        return markCrit;
    }

    /** A blink is on its way: stay put until it arrives. Server only. */
    public void blinkedTo() {
        blinkedTo = true;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        switch (state()) {
            case FLYING -> tickFlying();
            case STUCK -> tickStuck();
            default -> tickReturning();
        }
    }

    private void tickFlying() {
        Vec3 from = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 to = from.add(velocity);
        if (level().isClientSide()) {
            setPos(to.x, to.y, to.z); // the server decides where it stops; the synced state says so
            return;
        }
        Player owner = ownerPlayer();
        if (!(owner instanceof ServerPlayer player) || !stillHeld(player) || player.level() != level()) {
            startReturn();
            return;
        }
        BlockHitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level(), this, from, end,
                getBoundingBox().expandTowards(velocity).inflate(1.0), e -> canStrike(player, e), 0.3f);
        if (entity != null && entity.getEntity() instanceof LivingEntity target) {
            strike(player, target, entity.getLocation(), velocity);
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            Vec3 at = block.getLocation().subtract(velocity.normalize().scale(STICK_BACK_OFF));
            setPos(at.x, at.y, at.z);
            entityData.set(STUCK_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
            entityData.set(STUCK_FACE, (byte) block.getDirection().get3DDataValue());
            stick(null);
            return;
        }
        setPos(to.x, to.y, to.z);
        travelled += velocity.length();
        if (travelled >= range - 0.05) { // the look vector is float-built: 8 steps of 2 come to 15.9994
            startReturn(); // a miss: nothing in its path
        }
    }

    private boolean canStrike(ServerPlayer player, Entity entity) {
        return entity instanceof LivingEntity living && entity != player && HitResolver.isValidTarget(player, living)
                && canHitEntity(entity);
    }

    private void strike(ServerPlayer player, LivingEntity target, Vec3 point, Vec3 velocity) {
        PlayerCombat combat = PlayerCombat.of(player);
        if (throwMove != null) {
            HitResolver.strikeTarget(player, combat, throwMove, target, throwMove.mv(), throwMove.def().hit().impact(), point,
                    velocity.normalize());
        }
        Vec3 at = point.subtract(velocity.normalize().scale(STICK_BACK_OFF));
        setPos(at.x, at.y, at.z);
        Vec3 local = toBodyFrame(target, at.subtract(target.position()));
        entityData.set(STUCK_AT, new Vector3f((float) local.x, (float) local.y, (float) local.z));
        stick(target);
    }

    private void stick(@Nullable LivingEntity target) {
        entityData.set(STUCK_IN, target == null ? 0 : target.getId() + 1);
        entityData.set(STATE, STUCK);
        setDeltaMovement(Vec3.ZERO);
        stuckAge = 0;
        if (getOwner() instanceof ServerPlayer player) {
            TetherBlades.stuck(player, this, target, stickTicks, markTicks, markCrit);
        }
    }

    private void tickStuck() {
        Entity in = stuckEntity();
        int id = entityData.get(STUCK_IN) - 1;
        if (id >= 0) {
            if (in == null || !in.isAlive() || in.isRemoved()) {
                if (!level().isClientSide()) {
                    startReturn(); // what it stuck in is gone
                }
                return;
            }
            Vector3f at = entityData.get(STUCK_AT);
            Vec3 world = in.position().add(fromBodyFrame(in, new Vec3(at.x, at.y, at.z)));
            setPos(world.x, world.y, world.z);
        } else {
            Vector3f at = entityData.get(STUCK_AT);
            setPos(at.x, at.y, at.z);
        }
        if (level().isClientSide()) {
            return;
        }
        stuckAge++;
        Player owner = ownerPlayer();
        if (blinkedTo) {
            return; // stays until the blink arrives (the blink retrieves it)
        }
        if (!(owner instanceof ServerPlayer player) || !stillHeld(player) || player.level() != level() || stuckAge >= stickTicks) {
            startReturn(); // run out, let go of, or left behind in another dimension
        }
    }

    private void tickReturning() {
        Player owner = ownerPlayer();
        returnAge++;
        if (owner == null || owner.isRemoved() || owner.level() != level()) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        Vec3 hand = handOf(owner);
        Vec3 to = hand.subtract(position());
        double distance = to.length();
        if (distance <= RETURN_ARRIVED || returnAge > RETURN_MAX_TICKS) {
            if (!level().isClientSide()) {
                discard();
            } else {
                setPos(hand.x, hand.y, hand.z);
            }
            return;
        }
        Vec3 step = to.scale(Math.min(1.0, RETURN_SPEED / distance));
        setDeltaMovement(step);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    /** Back to the hand: the blade has come back as far as the thrower is concerned. Server only. */
    public void startReturn() {
        if (level().isClientSide() || state() == RETURNING) {
            return;
        }
        entityData.set(STATE, RETURNING);
        entityData.set(STUCK_IN, 0);
        returnAge = 0;
        ServerCombatSounds.forEveryone(level(), position(), ModSounds.EDGES_RETURN, 0.8f, 1.0f);
        reportReturn();
    }

    /** A blink arrived: the thrower has it in hand again at once. Server only. */
    public void retrieve() {
        reportReturn();
        discard();
    }

    private void reportReturn() {
        if (!returnedReported && getOwner() instanceof ServerPlayer player) {
            returnedReported = true;
            TetherBlades.returned(player, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            reportReturn(); // unloaded, killed, or gone with its level: the thrower gets it back
            if (getOwner() instanceof ServerPlayer player) {
                TetherBlades.removed(player, this);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean stillHeld(ServerPlayer player) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), false);
        return player.isAlive() && !player.isSpectator() && weapon != null && weapon.offHand().isPresent();
    }

    /** Roughly where a player's left hand is: in front of the left shoulder, a little below the chest. */
    public static Vec3 handOf(Player player) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(forward.z, 0, -forward.x);
        return player.position().add(0, player.getBbHeight() * 0.5, 0).add(left.scale(0.38)).add(forward.scale(0.3));
    }

    private static Vec3 toBodyFrame(Entity entity, Vec3 offset) {
        return offset.yRot(bodyYaw(entity) * Mth.DEG_TO_RAD);
    }

    private static Vec3 fromBodyFrame(Entity entity, Vec3 local) {
        return local.yRot(-bodyYaw(entity) * Mth.DEG_TO_RAD);
    }

    private static float bodyYaw(Entity entity) {
        return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // both sides move the blade themselves (the synced state says how): the server's positions add nothing
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof ThrownSickle);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(0.5);
    }
}
