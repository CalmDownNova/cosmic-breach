package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.relic.Relics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An arrow of the Umbra Cantor: a shaft of shadow with a violet trail, flying straight and dropping a little
 * ({@code gravity} blocks a tick per tick) for up to {@code range} blocks. It strikes the first enemy in its path with
 * its move's motion value (the engine's damage, crits, Resonance and ripostes, as a ranged hit); a charged shot's or a
 * Cadence's ({@link #note}) leaves a resonant note where it lands, on an enemy or a wall ({@link Cantor#placeNote}).
 * One that reaches the end of its range fades. The server moves it; the client draws where it is told (every tick).
 */
public class UmbraArrow extends Projectile {
    public static final byte QUICK = 0;
    public static final byte HEAVY = 1;
    public static final byte NOTE = 2;
    private static final int TRAIL = 6;

    private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(UmbraArrow.class, EntityDataSerializers.BYTE);

    // server
    private @Nullable MoveInstance move;
    private @Nullable WeaponDef weapon;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private double mv = 1.0;
    private double impact = 4.0;
    private double range = 48.0;
    private double gravity = 0.012;
    private boolean note;
    private double travelled;
    // client: the last positions, newest last, for the trail
    private final Deque<Vec3> trail = new ArrayDeque<>();

    public UmbraArrow(EntityType<? extends UmbraArrow> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** An arrow from {@code owner} leaving {@code from} with {@code velocity}. */
    public UmbraArrow(Level level, ServerPlayer owner, Vec3 from, Vec3 velocity, byte style) {
        this(Relics.UMBRA_ARROW.get(), level);
        setOwner(owner);
        setPos(from.x, from.y, from.z);
        setDeltaMovement(velocity);
        face(velocity);
        entityData.set(STYLE, style);
    }

    /** What it deals and how it flies (server). */
    public UmbraArrow configure(MoveInstance move, WeaponDef weapon, ItemStack weaponStack, double mv, double impact, double range,
                                double gravity, boolean note) {
        this.move = move;
        this.weapon = weapon;
        this.weaponStack = weaponStack;
        this.mv = mv;
        this.impact = impact;
        this.range = range;
        this.gravity = gravity;
        this.note = note;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, QUICK);
    }

    public byte style() {
        return entityData.get(STYLE);
    }

    /** Client: the last few positions, oldest first. */
    public List<Vec3> trail() {
        return new ArrayList<>(trail);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            trail.addLast(position());
            while (trail.size() > TRAIL) {
                trail.removeFirst();
            }
            return;
        }
        if (!(getOwner() instanceof ServerPlayer owner) || owner.level() != level() || move == null || weapon == null) {
            discard();
            return;
        }

        Vec3 pos = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 to = pos.add(velocity);
        BlockHitResult block = level().clip(new ClipContext(pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level(), this, pos, end,
                getBoundingBox().expandTowards(velocity).inflate(1.0), e -> canStrike(owner, e), 0.3f);
        if (entity != null && entity.getEntity() instanceof LivingEntity target) {
            land(owner, entity.getLocation(), target, velocity);
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            land(owner, block.getLocation().subtract(velocity.normalize().scale(0.15)), null, velocity);
            return;
        }
        setPos(to.x, to.y, to.z);
        travelled += velocity.length();
        Vec3 next = velocity.add(0, -gravity, 0);
        setDeltaMovement(next);
        face(next);
        if (travelled >= range - 0.05) {
            Cantor.arrowMoment(owner, Cantor.FADE, to, style(), -1);
            discard();
        }
    }

    private boolean canStrike(ServerPlayer owner, Entity e) {
        return e instanceof LivingEntity living && e != owner && HitResolver.isValidTarget(owner, living) && canHitEntity(e);
    }

    /** It lands: on {@code target} (null for a wall). */
    private void land(ServerPlayer owner, Vec3 at, @Nullable LivingEntity target, Vec3 velocity) {
        PlayerCombat combat = PlayerCombat.of(owner);
        if (target != null && HitResolver.strikeRanged(owner, combat, move, weapon, weaponStack, target, mv, impact, at,
                velocity.normalize())) {
            combat.machine().onHitLanded(move, 1);
        }
        Cantor.arrowMoment(owner, Cantor.LAND, at, style(), target == null ? -1 : target.getId());
        if (note) {
            Cantor.placeNote(owner, at, target);
        }
        discard();
    }

    private void face(Vec3 v) {
        if (v.lengthSqr() < 1e-9) {
            return;
        }
        float yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }
}
