package com.cosmicbreach.astrolabe;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.world.light.TempLights;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Pocket Star (GDD 4.2, {@link PocketStarRules}): a miniature sun hung where the Astrolabe aimed, for 80 ticks. It
 * pulses every 10 ticks (radius 3, motion value 0.6), swallows enemy projectiles within 4 blocks, and lights the ground
 * round it to 15 ({@link TempLights}, which Hollow Stalkers won't enter). Its owner's second press is the Supernova
 * ({@link #detonate}). Inside a Comet Maul's Gravity Well it becomes a Singularity: the well pulls twice as hard while it
 * is in it (a {@link GravityWell.PullBoost}), and its Supernova deals +50%. One star per player; a new one replaces the
 * old. Never saved.
 *
 * <p>Tells the clients (as the Pocket Star effect's moments, from its owner): {@link #PULSE}, {@link #SWALLOWED}
 * (where), {@link #SINGULARITY}, {@link #FADED}; the Supernova's own moment is the Supernova effect's {@link #NOVA}.
 */
public class PocketStar extends Entity {
    public static final int PLACED = 0;
    public static final int PULSE = 1;
    public static final int SWALLOWED = 2;
    public static final int FADED = 3;
    /** It became a Singularity (inside a Gravity Well). */
    public static final int SINGULARITY = 4;
    public static final int NOVA = 0;

    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PocketStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SINGULAR = SynchedEntityData.defineId(PocketStar.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PocketStar.class, EntityDataSerializers.INT);

    private static final Map<UUID, PocketStar> BY_OWNER = new ConcurrentHashMap<>();

    // server
    private @Nullable UUID ownerId;
    private @Nullable MoveInstance move;
    private @Nullable WeaponDef weapon;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private int pulseEvery = PocketStarRules.PULSE_EVERY;
    private double pulseRadius = PocketStarRules.PULSE_RADIUS;
    private double pulseMv = PocketStarRules.PULSE_MV;
    private double pulseImpact = PocketStarRules.PULSE_IMPACT;
    private double swallowRadius = PocketStarRules.SWALLOW_RADIUS;
    private int light = PocketStarRules.LIGHT;
    private final List<BlockPos> lights = new ArrayList<>();
    private int pulses;
    private int swallowed;
    private double lastNovaMv = -1;

    public PocketStar(EntityType<? extends PocketStar> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public PocketStar(Level level, ServerPlayer owner, Vec3 at) {
        this(Astrolabes.POCKET_STAR.get(), level);
        setPos(at.x, at.y, at.z);
        ownerId = owner.getUUID();
        entityData.set(OWNER, owner.getId());
    }

    /** Its numbers (the Pocket Star effect's parameters) and the cast it came from (server). */
    public PocketStar configure(MoveInstance move, WeaponDef weapon, ItemStack weaponStack, int life, int pulseEvery, double pulseRadius,
                                double pulseMv, double pulseImpact, double swallowRadius, int light) {
        this.move = move;
        this.weapon = weapon;
        this.weaponStack = weaponStack;
        entityData.set(LIFE, life);
        this.pulseEvery = Math.max(1, pulseEvery);
        this.pulseRadius = pulseRadius;
        this.pulseMv = pulseMv;
        this.pulseImpact = pulseImpact;
        this.swallowRadius = swallowRadius;
        this.light = light;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(SINGULAR, false);
        builder.define(LIFE, PocketStarRules.LIFE);
    }

    // ------------------------------------------------------------------ queries

    /** The live star of {@code player}, or null (server). */
    public static @Nullable PocketStar of(Player player) {
        PocketStar star = BY_OWNER.get(player.getUUID());
        return star != null && !star.isRemoved() ? star : null;
    }

    /** True if a live star of anyone sits within {@code radius} of {@code centre} in {@code level} (server). */
    public static boolean anyInside(ServerLevel level, Vec3 centre, double radius) {
        for (PocketStar star : BY_OWNER.values()) {
            if (!star.isRemoved() && star.level() == level && PocketStarRules.inside(star.position(), centre, radius)) {
                return true;
            }
        }
        return false;
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int age() {
        return tickCount;
    }

    public boolean singular() {
        return entityData.get(SINGULAR);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER);
    }

    /** Server: pulses so far, projectiles swallowed, the cells lit, and the Supernova's motion value (-1 before). */
    public int pulses() {
        return pulses;
    }

    public int swallowed() {
        return swallowed;
    }

    public List<BlockPos> lights() {
        return List.copyOf(lights);
    }

    public double lastNovaMv() {
        return lastNovaMv;
    }

    // ------------------------------------------------------------------ life

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ServerPlayer owner = owner();
        if (owner == null || move == null || weapon == null) {
            expire();
            return;
        }
        if (tickCount == 1) {
            BY_OWNER.put(owner.getUUID(), this);
            lightUp((ServerLevel) level());
        }
        if (!singular()) {
            for (GravityWell.Live well : GravityWell.live()) {
                if (well.level() == level() && PocketStarRules.inside(position(), well.centre(), well.radius())) {
                    entityData.set(SINGULAR, true);
                    moment(owner, SINGULARITY, position(), 1f, 1);
                    break;
                }
            }
        }
        if (tickCount % pulseEvery == 0 && tickCount <= life()) {
            pulse(owner);
        }
        swallow(owner);
        if (tickCount >= life()) {
            expire();
        }
    }

    private @Nullable ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(ownerId) instanceof ServerPlayer p && p.level() == level() && p.isAlive()
                ? p : null;
    }

    private void pulse(ServerPlayer owner) {
        pulses++;
        PlayerCombat combat = PlayerCombat.of(owner);
        HitResolver.effectHitRanged(owner, combat, move, weapon, weaponStack, new HitShape.Sphere(pulseRadius, 0.0), position(),
                owner.getYRot(), pulseMv, pulseImpact, null);
        moment(owner, PULSE, position(), (float) pulseRadius, pulses);
        ServerCombatSounds.forEveryone(level(), position(), Astrolabes.PULSE, 0.5f, 1.0f);
    }

    /** Enemy projectiles within reach go out like sparks. */
    private void swallow(ServerPlayer owner) {
        AABB area = getBoundingBox().inflate(swallowRadius);
        for (Projectile p : level().getEntitiesOfClass(Projectile.class, area, p -> hostile(p) && p.position().distanceTo(position()) <= swallowRadius)) {
            Vec3 at = p.position();
            p.discard();
            swallowed++;
            moment(owner, SWALLOWED, at, 0f, swallowed);
            ServerCombatSounds.forEveryone(level(), at, Astrolabes.SWALLOW, 0.7f, 1.0f);
        }
    }

    /** Anything not ours or a player's: a skeleton's arrow, a Shardling's needle. */
    private static boolean hostile(Projectile p) {
        if (p instanceof StarBolt || p instanceof ThrownSickle || p.isRemoved()) {
            return false;
        }
        Entity shooter = p.getOwner();
        return !(shooter instanceof Player);
    }

    /** Lights the star's own cell and the ground round it to its light (15). */
    private void lightUp(ServerLevel level) {
        int ticks = life() + 2;
        BlockPos self = TempLights.place(level, blockPosition(), light, ticks);
        if (self != null) {
            lights.add(self);
        }
        BlockPos under = groundBelow(level, blockPosition(), 6);
        if (under == null) {
            return;
        }
        for (int[] o : PocketStarRules.groundLights()) {
            BlockPos probe = under.offset(o[0], 0, o[1]);
            BlockPos ground = groundNear(level, probe, 3);
            if (ground == null) {
                continue;
            }
            BlockPos lit = TempLights.place(level, ground, light, ticks);
            if (lit != null && !lights.contains(lit)) {
                lights.add(lit);
            }
        }
    }

    /** The first air cell standing on something solid at or below {@code from}, within {@code depth}; null if none. */
    private static @Nullable BlockPos groundBelow(ServerLevel level, BlockPos from, int depth) {
        for (int d = 0; d <= depth; d++) {
            BlockPos p = from.below(d);
            if (open(level, p) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    /** An air cell on the ground within {@code dy} up or down of {@code probe}; null if none. */
    private static @Nullable BlockPos groundNear(ServerLevel level, BlockPos probe, int dy) {
        for (int d = 0; d <= dy; d++) {
            for (int sign : d == 0 ? new int[] {1} : new int[] {1, -1}) {
                BlockPos p = probe.above(d * sign);
                if (open(level, p) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Air, or a light block (the star's own light may already stand there). */
    private static boolean open(ServerLevel level, BlockPos p) {
        var state = level.getBlockState(p);
        return state.isAir() || state.is(net.minecraft.world.level.block.Blocks.LIGHT);
    }

    /**
     * The Supernova: everything within {@code radius} takes the nova's motion value for the star's time left
     * ({@link PocketStarRules#novaMv}, x1.5 for a Singularity) at {@code impact}, as the Supernova move's hit. The star
     * is gone after. Returns how many it hit.
     */
    public int detonate(MoveInstance nova, WeaponDef novaWeapon, ItemStack stack, double radius, double base, double bonus, double impact) {
        ServerPlayer owner = owner();
        if (owner == null || isRemoved()) {
            return 0;
        }
        double mv = PocketStarRules.novaMv(tickCount, life(), base, bonus, singular());
        lastNovaMv = mv;
        PlayerCombat combat = PlayerCombat.of(owner);
        int hit = HitResolver.effectHitRanged(owner, combat, nova, novaWeapon, stack, new HitShape.Sphere(radius, 0.0), position(),
                owner.getYRot(), mv, impact, null);
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), AstrolabeEffects.SUPERNOVA, NOVA, position(),
                (float) radius, (int) Math.round(mv * 100) + (singular() ? 100000 : 0)));
        ServerCombatSounds.forEveryone(level(), position(), Astrolabes.SUPERNOVA, 1.0f, singular() ? 0.85f : 1.0f);
        lightsOff();
        forget();
        discard();
        return hit;
    }

    /** Burned out (or its owner is gone): the lights go out and it fades. */
    private void expire() {
        ServerPlayer owner = owner();
        if (owner != null) {
            moment(owner, FADED, position(), 0f, 0);
        }
        lightsOff();
        forget();
        discard();
    }

    private void lightsOff() {
        if (level() instanceof ServerLevel server) {
            for (BlockPos p : lights) {
                TempLights.remove(server, p);
            }
        }
        lights.clear();
    }

    private void forget() {
        if (ownerId != null) {
            BY_OWNER.remove(ownerId, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            lightsOff();
            forget();
        }
        super.remove(reason);
    }

    private void moment(ServerPlayer owner, int stage, Vec3 at, float value, int ticks) {
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), AstrolabeEffects.POCKET_STAR, stage, at, value, ticks));
    }

    /** Replaces {@code owner}'s star, if any, quietly (a new Pocket Star). */
    static void replaceOf(Player owner) {
        PocketStar old = of(owner);
        if (old != null) {
            old.expire();
        }
    }

    /** Server stopping: forget every star. */
    static void clearAll() {
        BY_OWNER.clear();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }
}
