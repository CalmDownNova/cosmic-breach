package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.ImpactSink;
import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.guardian.ArenaRules;
import com.cosmicbreach.guardian.AttackPicker;
import com.cosmicbreach.guardian.BossTargeting;
import com.cosmicbreach.guardian.BreakGauge;
import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.GuardianBossBar;
import com.cosmicbreach.guardian.GuardianFights;
import com.cosmicbreach.guardian.GuardianHealth;
import com.cosmicbreach.guardian.GuardianPart;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.LairGuardian;
import com.cosmicbreach.guardian.Participants;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.Telegraphs;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
 * The Prism Colossus (Prism Colossus design v1; GDD 7.2): the first guardian, a crystal giant fused from the waist
 * down into the Crown Spire's central pillar. It never walks, it turns. Dormant it is a dim statue; a player
 * without the Drift attunement entering the arena (or a Guardian Echo on the altar) wakes it after the lair's
 * cooldown, and its health scales with the players inside.
 *
 * <p>Its attacks each teach one verb, with the telegraph the engine's language gives it:
 * <ul>
 *   <li><b>Prism Slam</b>: a fist leaves its wrist, rises over the target and a gold ring fills under it (it tracks
 *       for 12 ticks), a gold glint at tick 20, the slam at 24: 16 damage, Impact 30. Parry it and the fist sticks in
 *       the floor for 30 ticks and 60 goes into the Break gauge. Phase 2 slams twice; only the second glints.</li>
 *   <li><b>Facet Sweep</b>: a white band glows on the floor from radius 3 to 9 across 200 degrees for 30 ticks,
 *       then a fist sweeps it: 12 damage, knockback 1. Dash through it.</li>
 *   <li><b>Refraction</b>: the eye charges for 40 ticks while one crown crystal lights (two in phase 2) and red
 *       lines trace the beam's path; hits on a lit crystal turn it ({@link Refraction}). The beam then burns for 40
 *       ticks: 6 damage every 10 in it; ending in the core it deals the Colossus 40 and 100 to its Break gauge.</li>
 *   <li><b>Prism Burst</b> (phase 2): anyone hugging it for 3 s makes its body glow white for 16 ticks, then burst:
 *       8 damage, knockback 1.5.</li>
 * </ul>
 * A full Break gauge (150 over a rolling 10 s) Breaks it for 100 ticks: it slumps, its core opens low and it takes
 * x1.5. At half health it Fractures (60 ticks, invulnerable) into phase 2; at zero it Shatters into three Prism
 * Shards that must all die within 20 s of the first, or it re-forms at 25%. Then it dies: each participant gets
 * their own rewards at their feet ({@link ColossusLoot}). Nobody in the arena for 30 s resets it to dormant.
 */
public class PrismColossus extends Mob implements Enemy, GeoEntity, ParryableAttacker, ImpactSink, GuardianPart.Owner,
        LairGuardian, GuardianFights.Fight {
    public enum State { DORMANT, INTRO, FIGHT, FRACTURE, SHATTERED, REFORMING, DYING }

    public enum Action { NONE, SLAM, DOUBLE_SLAM, SWEEP, REFRACTION, BURST }

    public enum FistMode { ATTACHED, SLAM, SWEEP, REST, DROP, RETURNING }

    /** The model is drawn this much bigger than it is built; its hitbox is set directly. */
    public static final float SCALE = 2.0f;
    public static final float WIDTH = 3.2f;
    public static final float HEIGHT = 6.0f;
    /** It stands on the pillar's stump, this far above the arena floor. */
    public static final double STUMP = 1.0;
    public static final double PROJECTILE_RANGE = 24.0;
    /** Fist hitboxes. */
    public static final float FIST_SIZE = 1.6f;
    /** Ticks a dropped fist takes to fall to the floor. */
    public static final int DROP_TICKS = 6;

    // entity events for the client's effects
    public static final byte EVENT_SLAM_RIGHT = 100;
    public static final byte EVENT_SLAM_LEFT = 101;
    public static final byte EVENT_PARRIED = 102;
    public static final byte EVENT_BREAK = 103;
    public static final byte EVENT_FRACTURE = 104;
    public static final byte EVENT_SHATTER = 105;
    public static final byte EVENT_REFORM = 106;
    public static final byte EVENT_DEATH = 107;
    public static final byte EVENT_AWAKEN = 108;
    public static final byte EVENT_CORE_HIT = 109;
    public static final byte EVENT_BURST = 110;
    public static final byte EVENT_GLINT_RIGHT = 111;
    public static final byte EVENT_GLINT_LEFT = 112;
    public static final byte EVENT_SWEEP = 113;

    public static final ResourceKey<DamageType> PRISM_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("prism_beam"));
    /** The beam turned back into its core: 40, through its armor. */
    public static final ResourceKey<DamageType> PRISM_CORE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("prism_core"));

    private static final int FLAG_PHASE2 = 1;
    private static final int FLAG_BROKEN = 2;
    private static final int FLAG_GLOW = 4;
    private static final int FLAG_HIDDEN = 8;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_STATE_START = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_ACTION_START = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_ACTION_YAW = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Long> DATA_BREAK_START = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_SWEEP_YAW = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_FIST_MODE_R = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_FIST_START_R = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_FIST_PARAM_R = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_FIST_MODE_L = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_FIST_START_L = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_FIST_PARAM_L = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_FIST_GLINT = SynchedEntityData.defineId(PrismColossus.class, EntityDataSerializers.BYTE);

    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop("dormant");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay("intro").thenLoop("idle");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenPlayAndHold("refraction_charge");
    private static final RawAnimation FIRE_ANIM = RawAnimation.begin().thenLoop("refraction_fire");
    private static final RawAnimation BURST_TELL_ANIM = RawAnimation.begin().thenPlayAndHold("burst_tell");
    private static final RawAnimation BURST_ANIM = RawAnimation.begin().thenPlay("burst").thenLoop("idle");
    private static final RawAnimation BREAK_ANIM = RawAnimation.begin().thenPlay("break").thenLoop("broken");
    private static final RawAnimation RISE_ANIM = RawAnimation.begin().thenPlay("rise").thenLoop("idle");
    private static final RawAnimation FRACTURE_ANIM = RawAnimation.begin().thenPlay("fracture").thenLoop("idle");
    private static final RawAnimation REFORM_ANIM = RawAnimation.begin().thenPlay("reform").thenLoop("idle");
    private static final RawAnimation DYING_ANIM = RawAnimation.begin().thenPlayAndHold("dying");
    private static final RawAnimation SHATTERED_ANIM = RawAnimation.begin().thenLoop("shattered");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ------------------------------------------------------------------ server state
    private @Nullable CrownArena arena;
    private @Nullable BlockPos altarPos;
    private State state = State.DORMANT;
    private int phase = 1;
    private Action action = Action.NONE;
    private long actionStart;
    private long nextActionAt;
    private long holdUntil = Long.MIN_VALUE;
    private long lastPlayerInside;
    private int playersAtStart = 1;
    /** Its health, past vanilla's cap at four players (1,176): see {@link GuardianHealth}. */
    private final GuardianHealth.Pool pool = new GuardianHealth.Pool(ColossusMoves.BASE_HEALTH);
    private long breakUntil = Long.MIN_VALUE;
    private boolean pendingFracture;
    private boolean pendingShatter;
    private final BreakGauge gauge = new BreakGauge(ColossusMoves.BREAK_POISE);
    private final BossTargeting targeting = new BossTargeting();
    private final AttackPicker<Action> picker = new AttackPicker<>();
    private final Participants participants = new Participants();
    private @Nullable UUID target;
    private long fightStart;
    private @Nullable GuardianBossBar bar;
    private final Fist right = new Fist(true);
    private final Fist left = new Fist(false);
    private @Nullable Fist slamming;
    private double currentImpact;
    private boolean parryableNow;
    private final Map<UUID, Integer> hugTicks = new HashMap<>();
    private final Map<UUID, long[]> lastHitBy = new HashMap<>();
    private final Map<BlockPos, Long> crumbling = new LinkedHashMap<>();
    private long lastGrind;
    private List<ServerPlayer> inside = List.of();
    private long insideTick = Long.MIN_VALUE;

    // Refraction
    private int[] lit = new int[0];
    private List<int[]> paths = List.of();
    private boolean pathsDirty;
    private boolean beamFiring;
    private final Map<UUID, Long> lastPulse = new HashMap<>();
    // Shatter
    private @Nullable ShatterState shatter;
    private final List<UUID> shards = new ArrayList<>();
    private int shatters;
    private int breaks;
    private int coreHits;
    private int crystalTurns;
    private final Set<UUID> sweepHit = new java.util.HashSet<>();
    // client
    private boolean wasBroken;
    private long riseUntil = Long.MIN_VALUE;

    public PrismColossus(EntityType<? extends PrismColossus> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 0;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GuardianHealth.Pool.MIRROR)
                .add(Attributes.ARMOR, ColossusMoves.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, ColossusMoves.TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** A dormant statue at a lair (the lair's spawner). */
    public static @Nullable PrismColossus spawnDormant(ServerLevel level, BlockPos arenaCentre, BlockPos altar) {
        PrismColossus c = new PrismColossus(GuardianRegistry.PRISM_COLOSSUS.get(), level);
        c.bindLair(arenaCentre, altar);
        c.moveTo(arenaCentre.getX(), arenaCentre.getY() + STUMP, arenaCentre.getZ(), 180f, 0f);
        c.setYHeadRot(180f);
        c.yBodyRot = 180f;
        level.addFreshEntity(c);
        c.setPillarLit(true);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_STATE_START, 0L);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_ACTION_START, 0L);
        builder.define(DATA_ACTION_YAW, 0f);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_HOME, BlockPos.ZERO);
        builder.define(DATA_BREAK_START, 0L);
        builder.define(DATA_SWEEP_YAW, 0f);
        builder.define(DATA_FIST_MODE_R, (byte) 0);
        builder.define(DATA_FIST_START_R, 0L);
        builder.define(DATA_FIST_PARAM_R, new Vector3f());
        builder.define(DATA_FIST_MODE_L, (byte) 0);
        builder.define(DATA_FIST_START_L, 0L);
        builder.define(DATA_FIST_PARAM_L, new Vector3f());
        builder.define(DATA_FIST_GLINT, (byte) 0);
    }

    // ------------------------------------------------------------------ the lair

    @Override
    public void bindLair(BlockPos arenaCentre, BlockPos altar) {
        this.arena = CrownArena.at(arenaCentre);
        this.altarPos = altar.immutable();
        entityData.set(DATA_HOME, arenaCentre.immutable());
    }

    @Override
    public boolean dormant() {
        return state == State.DORMANT;
    }

    @Override
    public Entity guardianEntity() {
        return this;
    }

    @Override
    public Entity guardian() {
        return this;
    }

    public @Nullable CrownArena arena() {
        if (arena == null && level().isClientSide()) {
            BlockPos home = entityData.get(DATA_HOME);
            return home.equals(BlockPos.ZERO) ? null : CrownArena.at(home);
        }
        return arena;
    }

    private @Nullable GuardianAltarBlockEntity altar() {
        return altarPos != null && level().getBlockEntity(altarPos) instanceof GuardianAltarBlockEntity a ? a : null;
    }

    private boolean lairArmed() {
        GuardianAltarBlockEntity a = altar();
        return a == null || a.armed();
    }

    @Override
    public boolean inArena(Vec3 point) {
        return arena != null && arena.contains(point);
    }

    @Override
    public void blockPlaced(BlockPos pos) {
        crumbling.putIfAbsent(pos, level().getGameTime() + com.cosmicbreach.guardian.ArenaRules.CRUMBLE_TICKS);
    }

    /** A player who can fight: alive, in survival or adventure. */
    public boolean fairGame(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Fair players inside the arena (worked out once a tick). */
    public List<ServerPlayer> fightersInside() {
        if (arena == null || !(level() instanceof ServerLevel server)) {
            return List.of();
        }
        long now = server.getGameTime();
        if (now != insideTick) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : server.players()) {
                if (fairGame(p) && arena.contains(p.position())) {
                    out.add(p);
                }
            }
            inside = out;
            insideTick = now;
        }
        return inside;
    }

    // ------------------------------------------------------------------ waking

    @Override
    public void awaken(@Nullable ServerPlayer by, boolean echo) {
        if (state != State.DORMANT || arena == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        playersAtStart = Math.max(1, fightersInside().size());
        double max = GuardianHealth.scaled(ColossusMoves.BASE_HEALTH, playersAtStart);
        pool.reset(max);
        setHealth((float) max);
        phase = 1;
        shatters = 0;
        breaks = 0;
        coreHits = 0;
        gauge.clear();
        targeting.clear();
        picker.clear();
        participants.clear();
        lastHitBy.clear();
        hugTicks.clear();
        target = null;
        setFlags(0);
        setState(State.INTRO, now);
        lastPlayerInside = now;
        fightStart = now + ColossusMoves.INTRO;
        picker.holdUntil(Action.REFRACTION, fightStart + ColossusMoves.FIRST_REFRACTION);
        nextActionAt = fightStart + ColossusMoves.GAP;
        bar = new GuardianBossBar(getDisplayName(), BossEvent.BossBarColor.BLUE).meets(com.cosmicbreach.codex.CodexChapter.MET_COLOSSUS);
        bar.setProgress(0f);
        bar.setMusic(GuardianRegistry.MUSIC_COLOSSUS.getId());
        GuardianFights.start(this);
        ensureParts(server);
        server.broadcastEntityEvent(this, EVENT_AWAKEN);
        playSound(GuardianRegistry.COLOSSUS_AWAKEN.get(), 3.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus awakened at {} for {} player(s), {} health{}", arena.centreBlock(),
                playersAtStart, (int) max, echo ? " (Guardian Echo)" : "");
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        if (arena == null) {
            bindLair(blockPosition().below((int) STUMP), blockPosition());
        }
        ServerLevel server = (ServerLevel) level();
        long now = server.getGameTime();
        holdPosition();
        switch (state) {
            case DORMANT -> dormantTick(server, now);
            case INTRO -> introTick(server, now);
            case FIGHT -> fightTick(server, now);
            case FRACTURE -> fractureTick(server, now);
            case SHATTERED -> shatteredTick(server, now);
            case REFORMING -> reformTick(server, now);
            case DYING -> dyingTick(server, now);
        }
        if (isRemoved()) {
            return;
        }
        crumbleTick(server, now);
        if (state != State.DORMANT && state != State.DYING) {
            presenceTick(server, now);
        }
        barTick(server, now);
    }

    private void holdPosition() {
        if (arena == null) {
            return;
        }
        double x = arena.x();
        double y = arena.floorY() + STUMP;
        double z = arena.z();
        if (Math.abs(getX() - x) > 1e-3 || Math.abs(getY() - y) > 1e-3 || Math.abs(getZ() - z) > 1e-3) {
            setPos(x, y, z);
        }
    }

    private void dormantTick(ServerLevel server, long now) {
        if (Math.floorMod(now + getId(), 10L) != 0 || server.getDifficulty() == Difficulty.PEACEFUL || !lairArmed()) {
            return;
        }
        for (ServerPlayer p : fightersInside()) {
            if (!LayerAttunement.has(p, GuardianTypes.COLOSSUS.attunes())) {
                awaken(p, false);
                return;
            }
        }
    }

    private void introTick(ServerLevel server, long now) {
        long t = now - stateStart();
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            if (t == 15 + 25L * k) {
                crystalLight(k, true);
                playSoundAt(arena.crystalPoint(k), GuardianRegistry.COLOSSUS_CRYSTAL_TURN.get(), 1.2f, 0.8f + 0.1f * k);
            }
        }
        if (t == 185) {
            for (int k = 0; k < Refraction.CRYSTALS; k++) {
                crystalLight(k, false);
            }
        }
        if (t == 90) {
            playSound(GuardianRegistry.COLOSSUS_ROAR.get(), 3.0f, 1.1f);
        }
        if (t >= ColossusMoves.INTRO) {
            setState(State.FIGHT, now);
            fightStart = now;
        }
    }

    private void fightTick(ServerLevel server, long now) {
        if (pendingShatter) {
            pendingShatter = false;
            startShatter(server, now);
            return;
        }
        if (pendingFracture) {
            pendingFracture = false;
            startFracture(server, now);
            return;
        }
        boolean broken = broken(now);
        if (!broken && hasFlag(FLAG_BROKEN)) {
            endBreak(now);
        }
        if (BossTargeting.checkDue(now, fightStart) || target == null || !validTarget(target)) {
            chooseTarget(now);
        }
        if (phase == 2) {
            trackHugs();
        }
        tickFist(server, right, now);
        tickFist(server, left, now);
        if (broken) {
            return;
        }
        if (action != Action.NONE) {
            actionTick(server, now, now - actionStart);
        } else {
            turnTowardTarget(now);
            if (now >= nextActionAt) {
                startNextAction(server, now);
            }
        }
    }

    private void fractureTick(ServerLevel server, long now) {
        tickFist(server, right, now);
        tickFist(server, left, now);
        long t = now - stateStart();
        if (t == 20) {
            for (int k = 0; k < Refraction.CRYSTALS; k++) {
                crystalLight(k, false);
            }
        }
        if (t >= ColossusMoves.FRACTURE) {
            phase = 2;
            recallFists(now);
            setState(State.FIGHT, now);
            scheduleNext(now);
        }
    }

    // ------------------------------------------------------------------ targeting and turning

    private boolean validTarget(UUID id) {
        return level() instanceof ServerLevel server && server.getPlayerByUUID(id) instanceof ServerPlayer p && fairGame(p)
                && arena.contains(p.position());
    }

    private void chooseTarget(long now) {
        List<BossTargeting.Candidate> candidates = new ArrayList<>();
        for (ServerPlayer p : fightersInside()) {
            candidates.add(new BossTargeting.Candidate(p.getUUID(), p.distanceToSqr(this)));
        }
        target = targeting.choose(candidates, now).orElse(null);
        if (target != null) {
            participants.targeted(target);
        }
    }

    private @Nullable ServerPlayer targetPlayer() {
        return target != null && level() instanceof ServerLevel server && server.getPlayerByUUID(target) instanceof ServerPlayer p ? p : null;
    }

    private void turnTowardTarget(long now) {
        ServerPlayer p = targetPlayer();
        if (p == null || right.mode != FistMode.ATTACHED || left.mode != FistMode.ATTACHED) {
            return;
        }
        float want = CrownArena.yawToward(getX(), getZ(), p.getX(), p.getZ());
        float before = getYRot();
        float yaw = Mth.approachDegrees(before, want, ColossusMoves.TURN_SPEED);
        face(yaw);
        if (Math.abs(Mth.wrapDegrees(yaw - before)) > 1.5f && now - lastGrind > 18) {
            lastGrind = now;
            playSound(GuardianRegistry.COLOSSUS_GRIND.get(), 1.4f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    private void face(float yaw) {
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
    }

    // ------------------------------------------------------------------ choosing and running attacks

    private void startNextAction(ServerLevel server, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null) {
            return;
        }
        List<AttackPicker.Option<Action>> options = new ArrayList<>();
        if (phase == 2 && hugged()) {
            options.add(AttackPicker.Option.weighted(Action.BURST, 0, ColossusMoves.BURST_COOLDOWN).asPriority());
        }
        if (hasCrystals()) {
            options.add(AttackPicker.Option.weighted(Action.REFRACTION, 0, ColossusMoves.REFRACTION_COOLDOWN).asPriority());
        }
        Action slam = phase == 2 ? Action.DOUBLE_SLAM : Action.SLAM;
        options.add(AttackPicker.Option.weighted(slam, ColossusMoves.SLAM_WEIGHT, ColossusMoves.SLAM_COOLDOWN).asRepeatable());
        if (anyoneInSweepBand(getYRot())) {
            options.add(AttackPicker.Option.weighted(Action.SWEEP, ColossusMoves.SWEEP_WEIGHT, ColossusMoves.SWEEP_COOLDOWN));
        }
        Action chosen = picker.pick(options, now, random::nextDouble);
        if (chosen == null) {
            return;
        }
        int cooldown = options.stream().filter(o -> o.attack() == chosen).findFirst().map(AttackPicker.Option::cooldownTicks).orElse(0);
        picker.used(chosen, cooldown, now);
        startAction(server, chosen, now, p);
    }

    /** Starts {@code chosen} now against {@code p} (also used by the debug command). */
    public void startAction(ServerLevel server, Action chosen, long now, ServerPlayer p) {
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus attack: {}", chosen);
        action = chosen;
        actionStart = now;
        entityData.set(DATA_ACTION, (byte) chosen.ordinal());
        entityData.set(DATA_ACTION_START, now);
        entityData.set(DATA_ACTION_YAW, getYRot());
        switch (chosen) {
            case SLAM -> launchSlam(slamArm(p), p, now, true);
            case DOUBLE_SLAM -> launchSlam(slamArm(p), p, now, false);
            case SWEEP -> {
                entityData.set(DATA_SWEEP_YAW, getYRot());
                sweepHit.clear();
                setFist(left, FistMode.SWEEP, now, Vec3.ZERO);
            }
            case REFRACTION -> startRefraction(server, now);
            case BURST -> {
                setFlag(FLAG_GLOW, true);
                playSound(GuardianRegistry.COLOSSUS_BURST_TELL.get(), 2.5f, 1.0f);
            }
            case NONE -> {
            }
        }
    }

    private Fist slamArm(ServerPlayer p) {
        double angle = Telegraphs.angleFrom(new Vec3(getX(), getY(), getZ()), p.position(), getYRot());
        return angle >= 0 ? right : left; // clockwise of its facing is its right
    }

    private void launchSlam(Fist fist, ServerPlayer p, long now, boolean glint) {
        fist.glint = glint;
        fist.parried = false;
        setFist(fist, FistMode.SLAM, now, ringUnder(p));
        setGlintFlags();
    }

    /** The ring's centre under a player: their feet on the floor, kept inside the disc. */
    private Vec3 ringUnder(Player p) {
        double dx = p.getX() - arena.x();
        double dz = p.getZ() - arena.z();
        double d = Math.hypot(dx, dz);
        double max = CrownArena.FLOOR_RADIUS - 1.0;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
        }
        return new Vec3(arena.x() + dx, arena.floorY(), arena.z() + dz);
    }

    private void actionTick(ServerLevel server, long now, long t) {
        switch (action) {
            case SLAM -> {
                if (right.mode == FistMode.ATTACHED && left.mode == FistMode.ATTACHED && t > 2) {
                    endAction(now);
                }
            }
            case DOUBLE_SLAM -> {
                if (t == ColossusMoves.DOUBLE_SLAM_DELAY) {
                    Fist second = slamming == right ? left : right;
                    ServerPlayer p = targetPlayer();
                    if (p != null) {
                        launchSlam(second, p, now, true);
                    }
                }
                if (t > ColossusMoves.DOUBLE_SLAM_DELAY + 2 && right.mode == FistMode.ATTACHED && left.mode == FistMode.ATTACHED) {
                    endAction(now);
                }
            }
            case SWEEP -> {
                if (t > ColossusMoves.SWEEP_TELL && left.mode == FistMode.ATTACHED) {
                    endAction(now);
                }
            }
            case REFRACTION -> refractionTick(server, now, t);
            case BURST -> {
                if (t == ColossusMoves.BURST_TELL) {
                    burst(server);
                }
                if (t >= ColossusMoves.burstLength()) {
                    endAction(now);
                }
            }
            case NONE -> {
            }
        }
    }

    private void endAction(long now) {
        action = Action.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        scheduleNext(now);
        setFlag(FLAG_GLOW, false);
    }

    // ------------------------------------------------------------------ fists

    /** One fist's flight, the same on both sides from the synced mode, start tick and parameter. */
    final class Fist {
        final boolean isRight;
        FistMode mode = FistMode.ATTACHED;
        long start;
        Vec3 param = Vec3.ZERO;
        boolean glint;
        boolean parried;
        int restTicks;
        @Nullable GuardianPart part;

        Fist(boolean isRight) {
            this.isRight = isRight;
        }
    }

    private void setFist(Fist fist, FistMode mode, long start, Vec3 param) {
        fist.mode = mode;
        fist.start = start;
        fist.param = param;
        entityData.set(fist.isRight ? DATA_FIST_MODE_R : DATA_FIST_MODE_L, (byte) mode.ordinal());
        entityData.set(fist.isRight ? DATA_FIST_START_R : DATA_FIST_START_L, start);
        entityData.set(fist.isRight ? DATA_FIST_PARAM_R : DATA_FIST_PARAM_L, new Vector3f((float) param.x, (float) param.y, (float) param.z));
        if (mode == FistMode.SLAM) {
            slamming = fist;
        }
        setGlintFlags();
    }

    private void setGlintFlags() {
        int g = (right.glint && right.mode == FistMode.SLAM ? 1 : 0) | (left.glint && left.mode == FistMode.SLAM ? 2 : 0);
        entityData.set(DATA_FIST_GLINT, (byte) g);
    }

    private void tickFist(ServerLevel server, Fist fist, long now) {
        long t = now - fist.start;
        switch (fist.mode) {
            case SLAM -> {
                if (t <= ColossusMoves.SLAM_TRACK) {
                    ServerPlayer p = targetPlayer();
                    if (p != null) {
                        Vec3 ring = ringUnder(p);
                        if (ring.distanceToSqr(fist.param) > 1e-4) {
                            fist.param = ring;
                            entityData.set(fist.isRight ? DATA_FIST_PARAM_R : DATA_FIST_PARAM_L,
                                    new Vector3f((float) ring.x, (float) ring.y, (float) ring.z));
                        }
                    }
                }
                if (t == ColossusMoves.SLAM_GLINT && fist.glint) {
                    server.broadcastEntityEvent(this, fist.isRight ? EVENT_GLINT_RIGHT : EVENT_GLINT_LEFT);
                    playSoundAt(fist.param.add(0, ColossusMoves.HOVER_HIGH, 0), GuardianRegistry.COLOSSUS_GLINT.get(), 1.6f, 1.0f);
                }
                if (t >= ColossusMoves.SLAM_TELL) {
                    slamLands(server, fist, now);
                }
            }
            case SWEEP -> {
                if (t >= ColossusMoves.SWEEP_TELL && t < ColossusMoves.SWEEP_TELL + ColossusMoves.SWEEP_SWING) {
                    if (t == ColossusMoves.SWEEP_TELL) {
                        server.broadcastEntityEvent(this, EVENT_SWEEP);
                        playSound(GuardianRegistry.COLOSSUS_SWEEP.get(), 2.5f, 1.0f);
                    }
                    sweepHits(t);
                }
                if (t >= ColossusMoves.SWEEP_TELL + ColossusMoves.SWEEP_SWING) {
                    setFist(fist, FistMode.RETURNING, now, fistPosition(fist.isRight, now));
                }
            }
            case REST -> {
                if (t >= fist.restTicks && !broken(now) && state == State.FIGHT) {
                    setFist(fist, FistMode.RETURNING, now, fist.param);
                }
            }
            case DROP -> {
                if (t >= DROP_TICKS) {
                    Vec3 floor = dropTarget(fist.param);
                    fist.restTicks = Integer.MAX_VALUE;
                    setFist(fist, FistMode.REST, now, floor);
                }
            }
            case RETURNING -> {
                if (t >= ColossusMoves.FIST_RETURN) {
                    fist.glint = false;
                    setFist(fist, FistMode.ATTACHED, now, Vec3.ZERO);
                }
            }
            case ATTACHED -> {
            }
        }
        if (fist.part != null && !fist.part.isRemoved()) {
            fist.part.placeCentre(fistPosition(fist.isRight, now));
        }
    }

    private Vec3 dropTarget(Vec3 from) {
        return new Vec3(from.x, arena.floorY() + ColossusMoves.FIST_REST, from.z);
    }

    /** Where a fist is at game time {@code time} (fractional on the client), from the synced state: both sides. */
    public Vec3 fistPosition(boolean isRight, double time) {
        CrownArena a = arena();
        if (a == null) {
            return position();
        }
        FistMode mode = fistMode(isRight);
        long start = entityData.get(isRight ? DATA_FIST_START_R : DATA_FIST_START_L);
        Vector3f p = entityData.get(isRight ? DATA_FIST_PARAM_R : DATA_FIST_PARAM_L);
        Vec3 param = new Vec3(p.x(), p.y(), p.z());
        double t = time - start;
        Vec3 centre = a.centre();
        float actionYaw = entityData.get(DATA_ACTION_YAW);
        return switch (mode) {
            case ATTACHED -> ColossusMoves.wrist(centre, getYRot(), isRight);
            case SLAM -> ColossusMoves.slamFist(t, ColossusMoves.wrist(centre, actionYaw, isRight), param);
            case SWEEP -> ColossusMoves.sweepFist(t, ColossusMoves.wrist(centre, actionYaw, isRight), centre,
                    entityData.get(DATA_SWEEP_YAW));
            case REST -> param;
            case DROP -> {
                double u = ColossusMoves.smooth(Math.min(1.0, t / DROP_TICKS));
                Vec3 floor = new Vec3(param.x, a.floorY() + ColossusMoves.FIST_REST, param.z);
                yield ColossusMoves.lerp(param, floor, u * u);
            }
            case RETURNING -> ColossusMoves.returningFist(t, param, ColossusMoves.wrist(centre, getYRot(), isRight));
        };
    }

    public FistMode fistMode(boolean isRight) {
        int m = entityData.get(isRight ? DATA_FIST_MODE_R : DATA_FIST_MODE_L);
        FistMode[] modes = FistMode.values();
        return m >= 0 && m < modes.length ? modes[m] : FistMode.ATTACHED;
    }

    /** True if that fist's slam glints gold (the parryable one). */
    public boolean fistGlints(boolean isRight) {
        return (entityData.get(DATA_FIST_GLINT) & (isRight ? 1 : 2)) != 0;
    }

    public long fistStart(boolean isRight) {
        return entityData.get(isRight ? DATA_FIST_START_R : DATA_FIST_START_L);
    }

    /** Server: the ticks this fist rests in the floor ({@value ColossusMoves#SLAM_STUCK} after a parry), or -1 if it isn't. */
    public int fistRest(boolean isRight) {
        Fist f = isRight ? right : left;
        return f.mode == FistMode.REST ? f.restTicks : -1;
    }

    public Vec3 fistParam(boolean isRight) {
        Vector3f p = entityData.get(isRight ? DATA_FIST_PARAM_R : DATA_FIST_PARAM_L);
        return new Vec3(p.x(), p.y(), p.z());
    }

    /** Every fist in flight comes down where it is (a Break, the Fracture). */
    private void dropFists(long now) {
        for (Fist fist : List.of(right, left)) {
            if (fist.mode == FistMode.SLAM || fist.mode == FistMode.SWEEP || fist.mode == FistMode.RETURNING) {
                setFist(fist, FistMode.DROP, now, fistPosition(fist.isRight, now));
            } else if (fist.mode == FistMode.REST) {
                fist.restTicks = Integer.MAX_VALUE;
            }
            fist.glint = false;
        }
        setGlintFlags();
    }

    /** Fists lying on the floor fly home (after a Break or the Fracture). */
    private void recallFists(long now) {
        for (Fist fist : List.of(right, left)) {
            if (fist.mode == FistMode.REST || fist.mode == FistMode.DROP) {
                setFist(fist, FistMode.RETURNING, now, fistPosition(fist.isRight, now));
            }
        }
    }

    private void slamLands(ServerLevel server, Fist fist, long now) {
        Vec3 ring = fist.param;
        slamming = fist;
        fist.parried = false;
        currentImpact = ColossusMoves.SLAM_IMPACT;
        for (ServerPlayer p : fightersInside()) {
            if (!Telegraphs.inCircle(p.getBoundingBox(), ring, ColossusMoves.SLAM_RADIUS)) {
                continue;
            }
            parryableNow = fist.glint;
            p.hurt(damageSources().mobAttack(this), (float) ColossusMoves.SLAM_DAMAGE);
            parryableNow = false;
        }
        server.broadcastEntityEvent(this, fist.isRight ? EVENT_SLAM_RIGHT : EVENT_SLAM_LEFT);
        playSoundAt(ring, GuardianRegistry.COLOSSUS_SLAM.get(), 3.0f, fist.glint ? 1.0f : 1.12f);
        fist.restTicks = fist.parried ? ColossusMoves.SLAM_STUCK : ColossusMoves.SLAM_REST;
        if (fist.parried) {
            server.broadcastEntityEvent(this, EVENT_PARRIED);
        }
        fist.glint = false;
        setFist(fist, FistMode.REST, now, ring.add(0, ColossusMoves.FIST_REST, 0));
    }

    private void sweepHits(long t) {
        double from = ColossusMoves.sweepAngle(t);
        double to = t + 1 >= ColossusMoves.SWEEP_TELL + ColossusMoves.SWEEP_SWING ? ColossusMoves.SWEEP_ARC / 2.0 + 1e-6
                : ColossusMoves.sweepAngle(t + 1);
        float yaw = entityData.get(DATA_SWEEP_YAW);
        Vec3 centre = arena.centre();
        currentImpact = ColossusMoves.SWEEP_IMPACT;
        for (ServerPlayer p : fightersInside()) {
            if (sweepHit.contains(p.getUUID())
                    || !Telegraphs.inBand(p.getBoundingBox(), centre, ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER, yaw,
                    ColossusMoves.SWEEP_ARC)) {
                continue;
            }
            double angle = Telegraphs.angleFrom(centre, p.getBoundingBox().getCenter(), yaw);
            if (angle < from - 1e-6 || angle >= to) {
                continue;
            }
            sweepHit.add(p.getUUID());
            if (p.hurt(damageSources().mobAttack(this), (float) ColossusMoves.SWEEP_DAMAGE)) {
                push(p, centre, ColossusMoves.SWEEP_KNOCKBACK - 0.4);
            }
        }
    }

    private void burst(ServerLevel server) {
        setFlag(FLAG_GLOW, false);
        server.broadcastEntityEvent(this, EVENT_BURST);
        playSound(GuardianRegistry.COLOSSUS_BURST.get(), 3.0f, 1.0f);
        currentImpact = 10.0;
        Vec3 centre = arena.centre();
        for (ServerPlayer p : fightersInside()) {
            if (Telegraphs.inCircle(p.getBoundingBox(), centre, ColossusMoves.BURST_RADIUS)
                    && p.hurt(damageSources().mobAttack(this), (float) ColossusMoves.BURST_DAMAGE)) {
                push(p, centre, ColossusMoves.BURST_KNOCKBACK - 0.4);
            }
        }
        hugTicks.clear();
    }

    /** Knocks a player away from {@code from} by {@code strength} on top of the hit's own knockback. */
    private static void push(ServerPlayer p, Vec3 from, double strength) {
        if (strength <= 0) {
            return;
        }
        p.knockback(strength, from.x - p.getX(), from.z - p.getZ());
        p.hurtMarked = true;
    }

    private void trackHugs() {
        Set<UUID> near = new java.util.HashSet<>();
        for (ServerPlayer p : fightersInside()) {
            if (Telegraphs.inCircle(p.getBoundingBox(), arena.centre(), ColossusMoves.BURST_RADIUS)) {
                near.add(p.getUUID());
                hugTicks.merge(p.getUUID(), 1, Integer::sum);
            }
        }
        hugTicks.keySet().retainAll(near);
    }

    private boolean hugged() {
        for (int ticks : hugTicks.values()) {
            if (ticks >= ColossusMoves.BURST_HUG_TICKS) {
                return true;
            }
        }
        return false;
    }

    private boolean anyoneInSweepBand(float yaw) {
        for (ServerPlayer p : fightersInside()) {
            if (Telegraphs.inBand(p.getBoundingBox(), arena.centre(), ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER, yaw,
                    ColossusMoves.SWEEP_ARC)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Refraction

    private @Nullable CrownCrystalBlockEntity crystal(int k) {
        return arena != null && level().getBlockEntity(arena.crystalBase(k)) instanceof CrownCrystalBlockEntity c ? c : null;
    }

    private boolean hasCrystals() {
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            if (crystal(k) == null) {
                return false;
            }
        }
        return true;
    }

    private int[] targets() {
        int[] t = new int[Refraction.CRYSTALS];
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            CrownCrystalBlockEntity c = crystal(k);
            t[k] = c == null ? Refraction.opposite(k) : c.target();
        }
        return t;
    }

    private void crystalLight(int k, boolean on) {
        if (arena != null) {
            CrownCrystalBlock.setLit(level(), arena.crystalBase(k), on);
        }
    }

    /** The points a node path runs through: the eye, then each node. */
    private List<Vec3> points(int[] path) {
        List<Vec3> out = new ArrayList<>(path.length + 1);
        out.add(arena.eye(getYRot()));
        for (int node : path) {
            out.add(arena.node(node, getYRot(), broken(level().getGameTime())));
        }
        return out;
    }

    private int playersCrossed(int[] path) {
        List<Vec3> pts = points(path);
        int n = 0;
        for (ServerPlayer p : fightersInside()) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                if (Telegraphs.onLine(p.getBoundingBox(), pts.get(i), pts.get(i + 1), ColossusMoves.BEAM_RADIUS)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /**
     * How good a path is to light: the players it crosses first; between equals, the lit crystal nearest a player, so
     * someone can reach it in time to turn the beam back (the design leaves the choice of crystal open).
     */
    private int lightScore(int[] path) {
        int crossed = playersCrossed(path);
        Vec3 crystal = arena.crystalPoint(path[0]);
        double nearest = CrownArena.FLOOR_RADIUS * 2.0;
        for (ServerPlayer p : fightersInside()) {
            nearest = Math.min(nearest, Math.hypot(p.getX() - crystal.x, p.getZ() - crystal.z));
        }
        return crossed * 1000 - (int) Math.round(nearest * 10.0);
    }

    private void startRefraction(ServerLevel server, long now) {
        int[] targets = targets();
        Refraction.settle(targets);
        lit = Refraction.light(phase == 2 ? 2 : 1, targets, this::lightScore, n -> random.nextInt(Math.max(1, n)));
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            CrownCrystalBlockEntity c = crystal(k);
            if (c != null) {
                c.setTarget(targets[k]);
            }
        }
        for (int k : lit) {
            crystalLight(k, true);
            CrownCrystalBlockEntity c = crystal(k);
            if (c != null) {
                c.makeTurnable(now + ColossusMoves.REFRACTION_CHARGE, getUUID());
            }
        }
        beamFiring = false;
        lastPulse.clear();
        recomputePaths();
        sendRefraction(RefractionPayload.CHARGING, now);
        playSound(GuardianRegistry.COLOSSUS_CHARGE.get(), 3.0f, 1.0f);
    }

    private void recomputePaths() {
        int[] targets = targets();
        List<int[]> out = new ArrayList<>();
        for (int k : lit) {
            out.add(Refraction.path(k, targets));
        }
        paths = out;
        pathsDirty = false;
    }

    /** A lit crystal was turned by a hit: the red lines redraw at once. */
    public void onCrystalTurned(int k) {
        crystalTurns++;
        if (action == Action.REFRACTION && !beamFiring) {
            pathsDirty = true;
        }
    }

    private void refractionTick(ServerLevel server, long now, long t) {
        if (t < ColossusMoves.REFRACTION_CHARGE) {
            if (pathsDirty) {
                recomputePaths();
                sendRefraction(RefractionPayload.CHARGING, actionStart);
            }
            return;
        }
        if (t == ColossusMoves.REFRACTION_CHARGE) {
            for (int k : lit) {
                CrownCrystalBlockEntity c = crystal(k);
                if (c != null) {
                    c.makeTurnable(Long.MIN_VALUE, null);
                }
            }
            recomputePaths();
            beamFiring = true;
            sendRefraction(RefractionPayload.FIRING, now);
            playSound(GuardianRegistry.COLOSSUS_BEAM.get(), 3.0f, 1.0f);
            for (int[] path : paths) {
                if (Refraction.endsAtCore(path)) {
                    coreHit(server);
                }
            }
        }
        if (t < ColossusMoves.REFRACTION_CHARGE + ColossusMoves.REFRACTION_FIRE) {
            beamPulses(now);
            return;
        }
        if (t == ColossusMoves.REFRACTION_CHARGE + ColossusMoves.REFRACTION_FIRE) {
            stopRefraction(now);
        }
        if (t >= ColossusMoves.refractionLength()) {
            endAction(now);
        }
    }

    private void beamPulses(long now) {
        DamageSource beam = beamSource();
        for (int[] path : paths) {
            List<Vec3> pts = points(path);
            for (ServerPlayer p : fightersInside()) {
                for (int i = 0; i + 1 < pts.size(); i++) {
                    Vec3 a = pts.get(i);
                    Vec3 b = pts.get(i + 1);
                    if (!Telegraphs.onLine(p.getBoundingBox(), a, b, ColossusMoves.BEAM_RADIUS)) {
                        continue;
                    }
                    Long last = lastPulse.get(p.getUUID());
                    if (last == null || now - last >= ColossusMoves.BEAM_PULSE) {
                        lastPulse.put(p.getUUID(), now);
                        currentImpact = 0.0;
                        if (p.hurt(beam, (float) ColossusMoves.BEAM_DAMAGE)) {
                            Vec3 out = Telegraphs.outOfLine(p.position(), a, b);
                            p.knockback(ColossusMoves.BEAM_KNOCKBACK, -out.x, -out.z);
                            p.hurtMarked = true;
                        }
                    }
                    break;
                }
            }
        }
    }

    private void coreHit(ServerLevel server) {
        coreHits++;
        server.broadcastEntityEvent(this, EVENT_CORE_HIT);
        playSound(GuardianRegistry.COLOSSUS_CORE_HIT.get(), 3.0f, 1.0f);
        invulnerableTime = 0;
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PRISM_CORE);
        hurt(new DamageSource(type, this), (float) ColossusMoves.CORE_HIT_DAMAGE);
        takeImpact(ColossusMoves.CORE_HIT_GAUGE);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Refraction turned back into the core: {} health left", String.format("%.1f", getHealth()));
    }

    private void stopRefraction(long now) {
        for (int k : lit) {
            crystalLight(k, false);
            CrownCrystalBlockEntity c = crystal(k);
            if (c != null) {
                c.makeTurnable(Long.MIN_VALUE, null);
            }
        }
        int[] targets = targets();
        Refraction.settle(targets);
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            CrownCrystalBlockEntity c = crystal(k);
            if (c != null) {
                c.setTarget(targets[k]);
            }
        }
        lit = new int[0];
        paths = List.of();
        beamFiring = false;
        sendRefraction(RefractionPayload.OFF, now);
    }

    private DamageSource beamSource() {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PRISM_BEAM);
        return new DamageSource(type, this);
    }

    private void sendRefraction(byte mode, long start) {
        List<List<Vec3>> pts = new ArrayList<>();
        List<Boolean> core = new ArrayList<>();
        for (int[] path : paths) {
            pts.add(points(path));
            core.add(Refraction.endsAtCore(path));
        }
        com.cosmicbreach.net.ModNetworking.sendToTrackers(this, new RefractionPayload(getId(), mode, start, pts, core));
    }

    /** The lit crystals (server), for tests. */
    public int[] litCrystals() {
        return lit.clone();
    }

    /** The current beam paths as node lists (server), for tests. */
    public List<int[]> refractionPaths() {
        return paths;
    }

    // ------------------------------------------------------------------ Break, Fracture

    @Override
    public void takeImpact(double impact) {
        if (level().isClientSide() || state != State.FIGHT) {
            return;
        }
        long now = level().getGameTime();
        if (broken(now)) {
            return;
        }
        if (gauge.add(now, impact)) {
            startBreak(now);
        }
    }

    @Override
    public void impactOnPart(GuardianPart part, double impact) {
        takeImpact(impact);
    }

    private void startBreak(long now) {
        breaks++;
        breakUntil = now + BreakGauge.BREAK_TICKS;
        entityData.set(DATA_BREAK_START, now);
        setFlag(FLAG_BROKEN, true);
        setFlag(FLAG_GLOW, false);
        if (action == Action.REFRACTION) {
            stopRefraction(now);
        }
        action = Action.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        dropFists(now);
        level().broadcastEntityEvent(this, EVENT_BREAK);
        playSound(GuardianRegistry.COLOSSUS_BREAK.get(), 3.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus Broken at {} health", String.format("%.1f", getHealth()));
    }

    private void endBreak(long now) {
        setFlag(FLAG_BROKEN, false);
        gauge.clear();
        recallFists(now);
        scheduleNext(now);
    }

    public boolean broken(long now) {
        return level().isClientSide() ? hasFlag(FLAG_BROKEN) : now < breakUntil;
    }

    public long breakStart() {
        return entityData.get(DATA_BREAK_START);
    }

    private void startFracture(ServerLevel server, long now) {
        if (action == Action.REFRACTION) {
            stopRefraction(now);
        }
        action = Action.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        breakUntil = Long.MIN_VALUE;
        setFlag(FLAG_BROKEN, false);
        setFlag(FLAG_GLOW, false);
        setFlag(FLAG_PHASE2, true);
        dropFists(now);
        gauge.clear();
        setState(State.FRACTURE, now);
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            crystalLight(k, true);
        }
        server.broadcastEntityEvent(this, EVENT_FRACTURE);
        playSound(GuardianRegistry.COLOSSUS_FRACTURE.get(), 3.5f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus Fracture: phase 2");
    }

    // ------------------------------------------------------------------ Shatter

    private void startShatter(ServerLevel server, long now) {
        if (action == Action.REFRACTION) {
            stopRefraction(now);
        }
        action = Action.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        breakUntil = Long.MIN_VALUE;
        setFlag(FLAG_BROKEN, false);
        setFlag(FLAG_GLOW, false);
        setFlag(FLAG_HIDDEN, true);
        for (Fist fist : List.of(right, left)) {
            fist.glint = false;
            setFist(fist, FistMode.ATTACHED, now, Vec3.ZERO);
        }
        removeParts();
        setState(State.SHATTERED, now);
        shatters++;
        shatter = new ShatterState(3);
        shards.clear();
        double health = GuardianHealth.scaled(ColossusMoves.SHARD_HEALTH, playersAtStart);
        Vec3 chest = arena.centre().add(0, CrownArena.CORE_HEIGHT, 0);
        for (int i = 0; i < 3; i++) {
            PrismShard shard = PrismShard.burstFrom(server, this, i, chest, getYRot() + i * 120f + 60f, health);
            shards.add(shard.getUUID());
        }
        server.broadcastEntityEvent(this, EVENT_SHATTER);
        playSound(GuardianRegistry.COLOSSUS_SHATTER.get(), 4.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus Shatter #{}: three shards of {} health", shatters, (int) health);
    }

    private void shatteredTick(ServerLevel server, long now) {
        if (shatter == null) {
            return;
        }
        if (shatter.tick(now) == ShatterState.Result.REMERGE) {
            startReform(server, now);
        }
    }

    /** One of its shards died. */
    public void onShardDied(PrismShard shard) {
        if (state != State.SHATTERED || shatter == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        ShatterState.Result r = shatter.shardDied(now);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Shard died ({} left, {} ticks on the countdown)", shatter.alive(), shatter.ticksLeft(now));
        if (r == ShatterState.Result.KILLED) {
            startDying(server, now);
        }
    }

    /** A shard hurt by a player: they took part. */
    public void onShardHurtBy(ServerPlayer player) {
        participants.dealtDamage(player.getUUID(), arena != null && arena.contains(player.position()));
    }

    private void startReform(ServerLevel server, long now) {
        for (UUID id : shards) {
            if (server.getEntity(id) instanceof PrismShard shard && shard.isAlive()) {
                shard.flyHome(arena.centre().add(0, CrownArena.CORE_HEIGHT, 0), now);
            }
        }
        shards.clear();
        shatter = null;
        setState(State.REFORMING, now);
        server.broadcastEntityEvent(this, EVENT_REFORM);
        playSound(GuardianRegistry.COLOSSUS_REFORM.get(), 3.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] The shards re-merge");
    }

    private void reformTick(ServerLevel server, long now) {
        long t = now - stateStart();
        if (t == 20) {
            setFlag(FLAG_HIDDEN, false);
        }
        if (t >= ColossusMoves.REFORM) {
            setHealth((float) (pool.max() * ShatterState.REMERGE_HEALTH));
            gauge.clear();
            ensureParts(server);
            setState(State.FIGHT, now);
            scheduleNext(now);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus re-formed at {} health", String.format("%.1f", getHealth()));
        }
    }

    // ------------------------------------------------------------------ death and rewards

    private void startDying(ServerLevel server, long now) {
        setState(State.DYING, now);
        setFlag(FLAG_HIDDEN, true);
        server.broadcastEntityEvent(this, EVENT_DEATH);
        playSound(GuardianRegistry.COLOSSUS_DEATH.get(), 4.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus defeated after {} ticks of fighting", now - fightStart);
    }

    private void dyingTick(ServerLevel server, long now) {
        long t = now - stateStart();
        if (t == ColossusMoves.DYING / 2) {
            setPillarLit(false);
        }
        if (t >= ColossusMoves.DYING) {
            finishKill(server, now);
        }
    }

    private void finishKill(ServerLevel server, long now) {
        for (UUID id : participants.all()) {
            // paid now, or held for a participant who is offline or dead until they are back (GuardianPayouts)
            com.cosmicbreach.guardian.GuardianPayouts.payOrOwe(server.getServer(), id, GuardianTypes.COLOSSUS);
        }
        GuardianAltarBlockEntity a = altar();
        if (a != null) {
            a.guardianKilled(now);
        }
        endFight();
        discard();
    }

    /** Lights or dims the pillar's stump. */
    private void setPillarLit(boolean on) {
        if (arena == null) {
            return;
        }
        BlockPos c = arena.centreBlock();
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                BlockState s = level().getBlockState(p);
                if (s.is(GuardianRegistry.CROWN_PILLAR.get()) && s.getValue(CrownPillarBlock.LIT) != on) {
                    level().setBlock(p, s.setValue(CrownPillarBlock.LIT, on), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ presence, reset, crumbling

    private void presenceTick(ServerLevel server, long now) {
        if (!fightersInside().isEmpty()) {
            lastPlayerInside = now;
            return;
        }
        if (now - lastPlayerInside >= ColossusMoves.RESET_ABSENT) {
            reset(server, now);
        }
    }

    /** Back to a dormant statue at full health, no loot (everyone left, or a command). */
    public void reset(ServerLevel server, long now) {
        if (action == Action.REFRACTION) {
            stopRefraction(now);
        }
        for (UUID id : shards) {
            if (server.getEntity(id) instanceof PrismShard shard) {
                shard.discard();
            }
        }
        shards.clear();
        shatter = null;
        for (int k = 0; k < Refraction.CRYSTALS; k++) {
            crystalLight(k, false);
        }
        action = Action.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        breakUntil = Long.MIN_VALUE;
        for (Fist fist : List.of(right, left)) {
            fist.glint = false;
            setFist(fist, FistMode.ATTACHED, now, Vec3.ZERO);
        }
        removeParts();
        setFlags(0);
        pool.reset(ColossusMoves.BASE_HEALTH);
        state = State.DORMANT;
        setHealth((float) ColossusMoves.BASE_HEALTH);
        setState(State.DORMANT, now);
        phase = 1;
        participants.clear();
        endFight();
        CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Colossus reset to dormant (nobody in the arena)");
    }

    private void endFight() {
        if (bar != null) {
            bar.remove();
            bar = null;
        }
        GuardianFights.end(this);
        removeParts();
    }

    private void crumbleTick(ServerLevel server, long now) {
        Iterator<Map.Entry<BlockPos, Long>> it = crumbling.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (now >= e.getValue()) {
                if (!server.getBlockState(e.getKey()).isAir()) {
                    server.destroyBlock(e.getKey(), true);
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ the boss bar

    private void barTick(ServerLevel server, long now) {
        if (bar == null) {
            return;
        }
        if (state == State.INTRO) {
            bar.setProgress((float) (now - stateStart()) / ColossusMoves.INTRO);
        } else if (state == State.SHATTERED && shatter != null) {
            float total = 0f;
            float max = 0f;
            for (UUID id : shards) {
                if (server.getEntity(id) instanceof PrismShard s) {
                    total += Math.max(0f, s.getHealth());
                    max += s.getMaxHealth();
                }
            }
            if (shatter.counting()) {
                int left = shatter.ticksLeft(now);
                bar.setName(Component.translatable("bar.cosmicbreach.prism_shards.countdown", shatter.alive(), (left + 19) / 20));
                bar.setColor(BossEvent.BossBarColor.RED);
                bar.setProgress(left / (float) ShatterState.COUNTDOWN_TICKS);
                bar.setCountdown(left, ShatterState.COUNTDOWN_TICKS);
            } else {
                bar.setName(Component.translatable("bar.cosmicbreach.prism_shards"));
                bar.setColor(BossEvent.BossBarColor.PURPLE);
                bar.setProgress(max <= 0 ? 0f : total / max);
                bar.setCountdown(0, 0);
            }
        } else if (state == State.DYING) {
            bar.setProgress(0f);
        } else {
            bar.setName(getDisplayName());
            bar.setColor(phase == 2 ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.BLUE);
            bar.setProgress((float) pool.fraction());
            bar.setCountdown(0, 0);
        }
        bar.setGauge((float) gauge.fraction(now), broken(now));
        double range = CrownArena.INSIDE_RADIUS + 16.0;
        bar.update(server, p -> p.distanceToSqr(arena.x(), arena.floorY() + 3.0, arena.z()) <= range * range);
    }

    /** The Break gauge, 0 to 1 (server). */
    public double gaugeFraction() {
        return gauge.fraction(level().getGameTime());
    }

    // ------------------------------------------------------------------ parts

    private void ensureParts(ServerLevel server) {
        long now = server.getGameTime();
        for (Fist fist : List.of(right, left)) {
            if (fist.part == null || fist.part.isRemoved()) {
                fist.part = GuardianPart.spawn(server, this, fist.isRight ? 0 : 1, FIST_SIZE, FIST_SIZE, fistPosition(fist.isRight, now));
            }
        }
    }

    private void removeParts() {
        for (Fist fist : List.of(right, left)) {
            if (fist.part != null) {
                fist.part.discard();
                fist.part = null;
            }
        }
    }

    // ------------------------------------------------------------------ taking hits

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            endFight();
            discard();
            return false;
        }
        if (state != State.FIGHT) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == this && !source.is(PRISM_CORE)) {
            return false;
        }
        if (arena != null && ArenaRules.burnsUp(source, arena.centre(), PROJECTILE_RANGE)) {
            return false;
        }
        ServerPlayer player = attacker instanceof ServerPlayer sp ? sp : null;
        if (player != null && duplicateHit(player, 0)) {
            return false;
        }
        return applyHit(source, amount, player);
    }

    /** A hit that passed the checks: x1.5 while Broken; the attacker took part and counts toward the target. */
    private boolean applyHit(DamageSource source, float amount, @Nullable ServerPlayer player) {
        float dealt = broken(level().getGameTime()) ? (float) (amount * ColossusMoves.BREAK_DAMAGE_TAKEN) : amount;
        boolean hurt = super.hurt(source, dealt);
        if (hurt && player != null) {
            participants.dealtDamage(player.getUUID(), arena != null && arena.contains(player.position()));
            targeting.recordDamage(player.getUUID(), dealt, level().getGameTime());
        }
        return hurt;
    }

    @Override
    public boolean hurtByPart(GuardianPart part, DamageSource source, float amount) {
        if (state != State.FIGHT || level().isClientSide()) {
            return false;
        }
        if (arena != null && ArenaRules.burnsUp(source, arena.centre(), PROJECTILE_RANGE)) {
            return false;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player != null) {
            if (duplicateHit(player, part.role() + 1)) {
                return false;
            }
            if (CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
                invulnerableTime = 0; // the engine paces its own hits
            }
        }
        return applyHit(source, amount, player);
    }

    /**
     * One swing may reach the body and a fist (on the same active tick or the next); only the first part it lands on
     * counts, so a swing never deals the Colossus damage twice. Returns true for the second. {@code key} 0 is the body,
     * 1 and 2 the fists. Hits on the same part are the engine's to pace (multi-hit moves).
     */
    private boolean duplicateHit(ServerPlayer player, int key) {
        long now = level().getGameTime();
        long[] last = lastHitBy.computeIfAbsent(player.getUUID(), id -> new long[] {Long.MIN_VALUE, -1});
        if (last[1] >= 0 && last[1] != key && now - last[0] < SWING_DEDUPE_TICKS) {
            return true;
        }
        last[0] = now;
        last[1] = key;
        return false;
    }

    /** Hits on two different parts closer than this are one swing (Meridian's quickest swings are 10 ticks apart). */
    public static final int SWING_DEDUPE_TICKS = 5;

    /**
     * Health can't pass the phase line in one hit: at half it Fractures, at zero it Shatters. The value goes into the
     * pool (real health); vanilla's synced health keeps the pool's share of the mirror.
     */
    @Override
    public void setHealth(float health) {
        if (pool == null || level().isClientSide()) {
            super.setHealth(health); // (vanilla's constructor sets it before the pool exists)
            return;
        }
        if (state == State.FIGHT && health < getHealth()) {
            float max = (float) pool.max();
            if (phase == 1 && health <= max * 0.5f) {
                health = max * 0.5f;
                pendingFracture = true;
            } else if (phase == 2 && health <= 0.0f) {
                health = 0.01f;
                pendingShatter = true;
            }
        }
        pool.set(health);
        super.setHealth(pool.mirror());
    }

    /** Real health on the server (the pool's); the mirror on a client. */
    @Override
    public float getHealth() {
        return pool == null || level().isClientSide() ? super.getHealth() : (float) pool.left();
    }

    /** Its full health for this fight (vanilla's {@code getMaxHealth()} answers the mirror). */
    public double maxHealth() {
        return pool.max();
    }

    @Override
    public boolean isParryableAttackActive() {
        return parryableNow;
    }

    @Override
    public double attackImpact() {
        return currentImpact;
    }

    @Override
    public void onParried(Player player) {
        if (slamming != null && parryableNow) {
            slamming.parried = true;
            CosmicBreach.LOGGER.debug("[cosmicbreach] Prism Slam parried by {}: the fist sticks for {} ticks", player.getGameProfile().getName(),
                    ColossusMoves.SLAM_STUCK);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(CrownArena.FLOOR_RADIUS + 4.0, 8.0, CrownArena.FLOOR_RADIUS + 4.0);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            endFight();
        }
        super.remove(reason);
    }

    /**
     * Also called when the lair's chunk unloads, which never calls {@link #remove}: the fight can't survive that (it
     * loads back as a dormant statue), so its bar goes now rather than staying on the players' screens.
     */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide() && bar != null) {
            bar.remove();
            bar = null;
            GuardianFights.end(this);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GuardianRegistry.COLOSSUS_HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }

    private void playSoundAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------ state sync

    private void setState(State s, long now) {
        state = s;
        entityData.set(DATA_STATE, (byte) s.ordinal());
        entityData.set(DATA_STATE_START, now);
    }

    public State state() {
        int o = entityData.get(DATA_STATE);
        State[] all = State.values();
        return o >= 0 && o < all.length ? all[o] : State.DORMANT;
    }

    public long stateStart() {
        return entityData.get(DATA_STATE_START);
    }

    public Action action() {
        int o = entityData.get(DATA_ACTION);
        Action[] all = Action.values();
        return o >= 0 && o < all.length ? all[o] : Action.NONE;
    }

    public long actionStart() {
        return entityData.get(DATA_ACTION_START);
    }

    public float actionYaw() {
        return entityData.get(DATA_ACTION_YAW);
    }

    public float sweepYaw() {
        return entityData.get(DATA_SWEEP_YAW);
    }

    private boolean hasFlag(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    private void setFlag(int flag, boolean on) {
        int f = entityData.get(DATA_FLAGS);
        int next = on ? f | flag : f & ~flag;
        if (next != f) {
            entityData.set(DATA_FLAGS, (byte) next);
        }
    }

    private void setFlags(int flags) {
        entityData.set(DATA_FLAGS, (byte) flags);
    }

    public boolean phaseTwo() {
        return hasFlag(FLAG_PHASE2);
    }

    public boolean glowing() {
        return hasFlag(FLAG_GLOW);
    }

    public boolean hidden() {
        return hasFlag(FLAG_HIDDEN);
    }

    public boolean isBroken() {
        return hasFlag(FLAG_BROKEN);
    }

    // ------------------------------------------------------------------ what tests and commands read (server)

    public int phase() {
        return phase;
    }

    public Set<UUID> participants() {
        return participants.all();
    }

    public @Nullable UUID currentTarget() {
        return target;
    }

    public @Nullable ShatterState shatterState() {
        return shatter;
    }

    public List<UUID> shardIds() {
        return List.copyOf(shards);
    }

    public int shatters() {
        return shatters;
    }

    /** Breaks so far this fight (server, for tests). */
    public int breaks() {
        return breaks;
    }

    /** Crown crystal turns landed so far (server, for tests). */
    public int crystalTurns() {
        return crystalTurns;
    }

    /** Beams turned back into its core so far this fight (server, for tests). */
    public int coreHits() {
        return coreHits;
    }

    public long fightStart() {
        return fightStart;
    }

    public int playersAtStart() {
        return playersAtStart;
    }

    /** Debug: sets the gap so the next attack starts at once, and makes it {@code next}. */
    public void forceNext(Action next) {
        if (level() instanceof ServerLevel server && state == State.FIGHT && action == Action.NONE && !broken(server.getGameTime())) {
            ServerPlayer p = targetPlayer();
            if (p == null) {
                chooseTarget(server.getGameTime());
                p = targetPlayer();
            }
            if (p != null) {
                picker.used(next, 0, server.getGameTime());
                startAction(server, next, server.getGameTime(), p);
            }
        }
    }

    /** Debug: holds off attacks for {@code ticks} (forced attacks still go). */
    public void holdAttacks(int ticks) {
        holdUntil = level().getGameTime() + ticks;
        nextActionAt = holdUntil;
    }

    /** The next attack after a gap, or after a debug hold. */
    private void scheduleNext(long now) {
        nextActionAt = Math.max(now + ColossusMoves.GAP, holdUntil);
    }

    /** Debug: sets the health (for skipping ahead), through the phase lines. */
    public void debugHealth(float health) {
        setHealth(health);
    }

    /** Debug: fills the Break gauge. */
    public void debugBreak() {
        takeImpact(gauge.capacity());
    }

    /** Debug: straight to the Fracture (half health). */
    public void debugPhaseTwo() {
        if (state == State.FIGHT && phase == 1) {
            setHealth((float) (pool.max() * 0.5));
        }
    }

    /** Debug: straight to the Shatter (from phase 2, or re-formed). */
    public void debugShatter() {
        if (state == State.FIGHT) {
            if (phase == 1) {
                phase = 2;
                setFlag(FLAG_PHASE2, true);
            }
            setHealth(0f);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (arena != null) {
            tag.put("Arena", NbtUtils.writeBlockPos(arena.centreBlock()));
        }
        if (altarPos != null) {
            tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
        }
    }

    /** A fight doesn't survive the world closing: it loads as a dormant statue at full health. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Optional<BlockPos> centre = NbtUtils.readBlockPos(tag, "Arena");
        Optional<BlockPos> altar = NbtUtils.readBlockPos(tag, "Altar");
        if (centre.isPresent()) {
            bindLair(centre.get(), altar.orElse(centre.get()));
        }
        pool.reset(ColossusMoves.BASE_HEALTH);
        state = State.DORMANT;
        setHealth((float) ColossusMoves.BASE_HEALTH);
        entityData.set(DATA_STATE, (byte) 0);
        setFlags(0);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        // the body turns with the synced yaw exactly (the fists' math uses it)
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        long now = level().getGameTime();
        boolean broken = isBroken();
        if (wasBroken && !broken) {
            riseUntil = now + 30;
        }
        wasBroken = broken;
        ColossusEffects.handler().colossusTick(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_SLAM_RIGHT && id <= EVENT_SWEEP && level().isClientSide()) {
            ColossusEffects.handler().colossusEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<PrismColossus> s) {
        State st = state();
        RawAnimation anim = switch (st) {
            case DORMANT -> DORMANT_ANIM;
            case INTRO -> INTRO_ANIM;
            case FRACTURE -> FRACTURE_ANIM;
            case SHATTERED -> SHATTERED_ANIM;
            case REFORMING -> REFORM_ANIM;
            case DYING -> DYING_ANIM;
            case FIGHT -> {
                if (isBroken()) {
                    yield BREAK_ANIM;
                }
                long t = level().getGameTime() - actionStart();
                yield switch (action()) {
                    case REFRACTION -> t < ColossusMoves.REFRACTION_CHARGE ? CHARGE_ANIM
                            : t < ColossusMoves.REFRACTION_CHARGE + ColossusMoves.REFRACTION_FIRE ? FIRE_ANIM : IDLE_ANIM;
                    case BURST -> t < ColossusMoves.BURST_TELL ? BURST_TELL_ANIM : BURST_ANIM;
                    default -> level().getGameTime() < riseUntil ? RISE_ANIM : IDLE_ANIM;
                };
            }
        };
        return s.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
