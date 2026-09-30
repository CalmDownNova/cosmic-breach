package com.cosmicbreach.astrolabe;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A star bolt of the Choir Astrolabe (GDD 4.2): a small star with a short trail, flying {@value Homing#SPEED} blocks a
 * tick for up to {@value Homing#RANGE} blocks and homing softly ({@link Homing}). It strikes the first enemy in its path
 * with its move's motion value (the engine's damage, crits, Resonance and ripostes, as a ranged hit: no hit-stop), or
 * with a splash (the Starfall's, radius 2) where it lands; it bursts on a wall and fizzles at the end of its range.
 * The server moves it and decides everything; the client draws where the server says (sent every tick). Never saved.
 */
public class StarBolt extends Projectile {
    public static final byte BOLT = 0;
    public static final byte TRIAD = 1;
    public static final byte STARFALL = 2;
    public static final byte DECOY = 3;
    /** Moments sent as the Star Bolt effect's (a {@code MoveEffectPayload} from the owner, exact to where it landed). */
    public static final int BURST = 10;
    public static final int SPLASH = 11;
    public static final int FIZZLE = 12;
    private static final int TRAIL = 5;

    private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(StarBolt.class, EntityDataSerializers.BYTE);

    // server
    private @Nullable MoveInstance move;
    private @Nullable WeaponDef weapon;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private double mv = 1.0;
    private double impact = 4.0;
    private double range = Homing.RANGE;
    private double cone = Homing.CONE;
    private double turn = Homing.TURN;
    private double splash;
    private int targetId = -1;
    private double travelled;
    private @Nullable Integer struckId;
    // client: the last positions, newest last, for the trail
    private final Deque<Vec3> trail = new ArrayDeque<>();

    public StarBolt(EntityType<? extends StarBolt> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** A bolt from {@code owner} leaving {@code from} with {@code velocity}. */
    public StarBolt(Level level, ServerPlayer owner, Vec3 from, Vec3 velocity, byte style) {
        this(Astrolabes.STAR_BOLT.get(), level);
        setOwner(owner);
        setPos(from.x, from.y, from.z);
        setDeltaMovement(velocity);
        face(velocity);
        entityData.set(STYLE, style);
    }

    /** What it deals and how it flies (server). */
    public StarBolt configure(MoveInstance move, WeaponDef weapon, ItemStack weaponStack, double mv, double impact, double range,
                              double cone, double turn, double splash) {
        this.move = move;
        this.weapon = weapon;
        this.weaponStack = weaponStack;
        this.mv = mv;
        this.impact = impact;
        this.range = range;
        this.cone = cone;
        this.turn = turn;
        this.splash = splash;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, BOLT);
    }

    public byte style() {
        return entityData.get(STYLE);
    }

    /** Client: the last few positions, oldest first. */
    public List<Vec3> trail() {
        return new ArrayList<>(trail);
    }

    /** Server: the entity it locked onto, or -1. */
    public int targetId() {
        return targetId;
    }

    /** Server: the entity it struck (null until it strikes one). */
    public @Nullable Integer struckId() {
        return struckId;
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
        Vec3 velocity = home(owner, pos, getDeltaMovement());
        setDeltaMovement(velocity);
        face(velocity);
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
            land(owner, block.getLocation().subtract(velocity.normalize().scale(0.1)), null, velocity);
            return;
        }
        setPos(to.x, to.y, to.z);
        travelled += velocity.length();
        if (travelled >= range - 0.05) {
            if (splash > 0) {
                land(owner, to, null, velocity); // a Starfall that met nothing bursts where its flight ends
            } else {
                moment(owner, FIZZLE, to, -1);
                discard();
            }
        }
    }

    /** Soft homing: keep the lock or find one in the cone, and turn toward it (only with a clear line to it). */
    private Vec3 home(ServerPlayer owner, Vec3 pos, Vec3 velocity) {
        double left = Math.max(0.0, range - travelled);
        LivingEntity locked = targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity l && l.isAlive() ? l : null;
        if (locked != null && !Homing.keeps(pos, velocity, centre(locked), Homing.KEEP, left + 2.0)) {
            locked = null;
        }
        if (locked == null) {
            targetId = -1;
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(Math.min(left, range)),
                    e -> HitResolver.isValidTarget(owner, e));
            List<Vec3> centres = new ArrayList<>();
            near.forEach(e -> centres.add(centre(e)));
            int pick = Homing.acquire(pos, velocity, centres, cone, left + 1.0);
            if (pick >= 0 && clear(pos, centres.get(pick))) {
                locked = near.get(pick);
                targetId = locked.getId();
            }
        }
        return locked == null ? velocity : Homing.steer(velocity, centre(locked).subtract(pos), turn);
    }

    private boolean clear(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                == HitResult.Type.MISS;
    }

    private static Vec3 centre(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private boolean canStrike(ServerPlayer owner, Entity e) {
        return e instanceof LivingEntity living && e != owner && HitResolver.isValidTarget(owner, living) && canHitEntity(e);
    }

    /** It lands: on {@code target} (null for a wall or the end of a Starfall's flight). */
    private void land(ServerPlayer owner, Vec3 at, @Nullable LivingEntity target, Vec3 velocity) {
        PlayerCombat combat = PlayerCombat.of(owner);
        if (splash > 0) {
            int hit = HitResolver.effectHitRanged(owner, combat, move, weapon, weaponStack, new HitShape.Sphere(splash, 0.0), at,
                    owner.getYRot(), mv, impact, null);
            if (hit > 0) {
                combat.machine().onHitLanded(move, hit);
            }
            if (target != null) {
                struckId = target.getId();
            }
            moment(owner, SPLASH, at, target == null ? -1 : target.getId());
        } else {
            if (target != null && HitResolver.strikeRanged(owner, combat, move, weapon, weaponStack, target, mv, impact, at,
                    velocity.normalize())) {
                combat.machine().onHitLanded(move, 1);
                struckId = target.getId();
            }
            moment(owner, BURST, at, target == null ? -1 : target.getId());
        }
        discard();
    }

    /** Tells everyone near the owner where the bolt ended and how (its style as the value, what it struck as ticks). */
    private void moment(ServerPlayer owner, int stage, Vec3 at, int struck) {
        com.cosmicbreach.net.ModNetworking.sendToTrackersAndSelf(owner, new com.cosmicbreach.net.MoveEffectPayload(owner.getId(),
                AstrolabeEffects.STAR_BOLT, stage, at, style(), struck));
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
