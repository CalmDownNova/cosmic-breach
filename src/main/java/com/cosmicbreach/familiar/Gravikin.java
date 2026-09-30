package com.cosmicbreach.familiar;

import com.cosmicbreach.relic.cantor.Silence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Gravikin (GDD 8.2): a pebble golem that hops from surface to surface after its owner (flying only where there is
 * nothing to stand on), with half again the health. Every {@value FamiliarRules#TAUNT_EVERY} ticks it taunts: enemies
 * within {@value FamiliarRules#TAUNT_RADIUS} blocks target it for {@value FamiliarRules#TAUNT_TICKS} ticks ({@link Taunts};
 * a boss takes {@value FamiliarRules#TAUNT_THREAT} threat for its owner instead). Enemies next to it move 20% slower.
 */
public class Gravikin extends FamiliarEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
    private static final RawAnimation HOP = RawAnimation.begin().thenPlay("hop");
    private static final RawAnimation TAUNT = RawAnimation.begin().thenPlay("taunt");
    /** Hops for a hit start this many ticks before it. */
    private static final int HOP_LEAD = 16;

    private final Cadence taunt = new Cadence(FamiliarRules.TAUNT_EVERY);
    private @Nullable Vec3 perch;
    private @Nullable Vec3 hopFrom;
    private @Nullable Vec3 hopTo;
    private int hopTick;
    private int hopTicks;
    private double hopApex;
    private long tauntedAt = Long.MIN_VALUE;
    private int hops;

    public Gravikin(EntityType<? extends Gravikin> type, Level level) {
        super(type, level);
    }

    @Override
    public FamiliarKind kind() {
        return FamiliarKind.GRAVIKIN;
    }

    @Override
    protected Vec3 home(Player owner, double time) {
        Vec3 spot = owner.position().add(FamiliarPaths.perch(owner.getYRot()));
        Vec3 ground = surface(level(), spot, 3, 8);
        return ground != null ? ground : spot.add(0, 1.1, 0);
    }

    @Override
    protected void steer(ServerPlayer owner, @Nullable LivingEntity target, long now) {
        if (hopTo != null) {
            advanceHop(owner);
            return;
        }
        boolean fighting = target != null && attack.left(now) <= HOP_LEAD;
        Vec3 want = fighting ? beside(target) : owner.position().add(FamiliarPaths.perch(owner.getYRot()));
        Vec3 ground = surface(level(), want, 3, 8);
        if (ground == null) {
            // nothing to stand on: it flies at its owner's side (or at its target)
            perch = null;
            flyTo(fighting ? strikePoint(target) : want.add(0, 1.1, 0), 0.3);
            faceToward(fighting ? target.position() : owner.position(), 0.3f);
            return;
        }
        double settle = fighting ? 0.8 : 2.5;
        if (perch == null || perch.distanceToSqr(ground) > settle * settle) {
            startHop(ground);
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        faceToward(fighting ? target.position() : owner.position().add(FamiliarPaths.facing(owner.getYRot()).scale(6)), 0.2f);
    }

    /** A spot on the ground next to {@code t}, on this side of it. */
    private Vec3 beside(LivingEntity t) {
        Vec3 away = new Vec3(getX() - t.getX(), 0, getZ() - t.getZ());
        double len = away.length();
        Vec3 dir = len < 1e-3 ? new Vec3(1, 0, 0) : away.scale(1.0 / len);
        return t.position().add(dir.scale(t.getBbWidth() * 0.5 + 0.55));
    }

    private void startHop(Vec3 to) {
        hopFrom = position();
        hopTo = to;
        hopTick = 0;
        double d = Math.hypot(to.x - hopFrom.x, to.z - hopFrom.z) + Math.abs(to.y - hopFrom.y) * 0.5;
        hopTicks = FamiliarPaths.hopTicks(d);
        hopApex = FamiliarPaths.hopApex(d);
        perch = null;
        act(ACT_HOP);
    }

    private void advanceHop(ServerPlayer owner) {
        hopTick++;
        Vec3 p = FamiliarPaths.hop(hopFrom, hopTo, hopApex, hopTick / (double) hopTicks);
        setDeltaMovement(p.subtract(position()));
        faceToward(hopTo, 0.4f);
        if (hopTick >= hopTicks) {
            perch = hopTo;
            hopTo = null;
            hops++;
            level().playSound(null, getX(), getY(), getZ(), FamiliarRegistry.HOP.get(), SoundSource.PLAYERS, 0.5f, 0.9f + random.nextFloat() * 0.2f);
            FamiliarNet.fx(this, FamiliarFxPayload.LAND, getId(), -1, perch, 0);
        }
    }

    @Override
    protected void onTeleported(Vec3 from, Vec3 to) {
        hopTo = null;
        perch = surface(level(), to, 1, 2);
    }

    @Override
    protected void special(ServerPlayer owner, @Nullable LivingEntity target, FamiliarMode mode, long now) {
        if (!mode.fights()) {
            return;
        }
        if (tickCount % FamiliarRules.SLOW_REFRESH == 0) {
            slowNear(owner);
        }
        if (taunt.ready(now)) {
            int answered = Taunts.taunt(this, owner, mode, now);
            if (answered > 0) {
                taunt.fire(now);
                tauntedAt = now;
                level().playSound(null, getX(), getY(), getZ(), FamiliarRegistry.TAUNT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                FamiliarNet.fx(this, FamiliarFxPayload.TAUNT, getId(), -1, position(), answered);
                act(ACT_TAUNT);
            }
        }
    }

    /** Enemies within {@value FamiliarRules#SLOW_RADIUS} blocks of its middle move 20% slower (refreshed while they stay). */
    private void slowNear(ServerPlayer owner) {
        Vec3 centre = position().add(0, getBbHeight() * 0.5, 0);
        double r = FamiliarRules.SLOW_RADIUS;
        for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r), m -> m.isAlive() && !(m instanceof FamiliarEntity))) {
            if (m.getBoundingBox().distanceToSqr(centre) > r * r || Silence.isBoss(m)
                    || (m instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID()))) {
                continue;
            }
            if (m instanceof Enemy || m.getTarget() == owner || m.getTarget() == this) {
                m.addEffect(new MobEffectInstance(FamiliarRegistry.DRAG, FamiliarRules.SLOW_TICKS, 0, true, false, false), this);
            }
        }
    }

    /**
     * The top of the first surface at {@code at}'s column, searched from {@code up} blocks above it to {@code down}
     * below: a block to stand on with room over it (no liquid). Null if there is none.
     */
    static @Nullable Vec3 surface(Level level, Vec3 at, int up, int down) {
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        int top = Mth.floor(at.y) + up;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int y = top; y >= top - up - down; y--) {
            p.set(x, y, z);
            below.set(x, y - 1, z);
            if (!level.isLoaded(below)) {
                return null;
            }
            BlockState here = level.getBlockState(p);
            if (!here.getCollisionShape(level, p).isEmpty() || !here.getFluidState().isEmpty()) {
                continue;
            }
            BlockState under = level.getBlockState(below);
            VoxelShape shape = under.getCollisionShape(level, below);
            if (!shape.isEmpty() && under.getFluidState().isEmpty()) {
                double surfaceY = below.getY() + shape.max(Direction.Axis.Y);
                return new Vec3(at.x, surfaceY, at.z);
            }
        }
        return null;
    }

    /** Ticks until its next taunt may go (for checks). */
    public int tauntLeft(long now) {
        return taunt.left(now);
    }

    /** When it last taunted, or {@link Long#MIN_VALUE}. */
    public long lastTaunt() {
        return tauntedAt;
    }

    /** Hops landed since it was summoned (for checks). */
    public int hops() {
        return hops;
    }

    /** True while it stands on a surface (not mid-hop, not flying). */
    public boolean perched() {
        return perch != null && hopTo == null;
    }

    @Override
    protected SoundEvent strikeSound() {
        return FamiliarRegistry.SLAM.get();
    }

    @Override
    protected RawAnimation idle() {
        return IDLE;
    }

    @Override
    protected @Nullable RawAnimation action(int act) {
        return switch (act) {
            case ACT_STRIKE -> STRIKE;
            case ACT_HOP -> HOP;
            case ACT_TAUNT -> TAUNT;
            default -> null;
        };
    }

    @Override
    protected int actionTicks(int act) {
        return switch (act) {
            case ACT_HOP -> 12;
            case ACT_TAUNT -> 22;
            default -> 9;
        };
    }

    /** It stands on the ground to be hit: arrows find it. */
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** The server's level, for the taunt. */
    ServerLevel serverLevel() {
        return (ServerLevel) level();
    }
}
