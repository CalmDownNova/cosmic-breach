package com.cosmicbreach.guardian.leviathan;

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
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.LairGuardian;
import com.cosmicbreach.guardian.Participants;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics.Attack;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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
 * The Thalassine Leviathan (Thalassine Leviathan design v1; GDD 7.2): the second guardian, a sky-whale serpent about 40
 * blocks long that swims an orbit round the Leviathan Rift's central asteroid. The Drift's exam: a fight about moving in
 * low gravity.
 *
 * <p>Its body is a head (this entity, whose own hit box is the head) and five followers (four tapering body segments and
 * the tail) that follow the head's recorded path ({@link PathTrail}); each follower is a {@link GuardianPart} that hands
 * its hits here: head x1.0, body x0.8, tail x0.6, and the four song glands on its spine x2.0 while moored. It never
 * breaks blocks. Asleep it lies coiled on the bowl under the central asteroid; ringing the bell (a player not yet
 * attuned to the Deep, or anyone with a Guardian Echo after the lair's cooldown) or an unattuned player entering the
 * sphere wakes it: its song rises from below, it spirals up into its orbit and the bar fills (10 s).
 *
 * <p>From the orbit it chooses ({@link LeviathanTactics}): <b>Breach Dive</b> (its song swells for 30 ticks while a
 * silver dust wake marks the whole path 30 ticks ahead of its head, then it dives out through the target's platform and
 * loops back: 20 damage, Impact 40; leave the wake), <b>Song of Pulling</b> ({@link SongPull}: dash out, or hide behind
 * rock; the mouth bites for 14), <b>Tail Flick</b> (20 ticks, the fin glows gold with a glint at 16; parry it for 60 on
 * the Break gauge and a 40-tick droop, or step back from the edge; 12 damage, knockback 1.6) and in phase 2 <b>Scale
 * Shed</b> ({@link ShedScale}). A full Break gauge (300 in a rolling minute) Breaks it for 100 ticks: it drifts nose-down,
 * its head sinking level with the platforms, and takes x1.5. At half health and again at a fifth it swims into a
 * <b>Moorage</b> ({@link Moorage}): it coils round the central asteroid, its back walkable, bridges growing out to it, its
 * glands exposed, shuddering every 80 ticks. At zero it goes still and sinks onto the lower shell; a pearl of light
 * rises and each participant gets their own rewards ({@link LeviathanLoot}). Nobody in the sphere for 30 s resets it.
 */
public class ThalassineLeviathan extends Mob implements Enemy, GeoEntity, ParryableAttacker, ImpactSink, GuardianPart.Owner,
        LairGuardian, GuardianFights.Fight {
    public enum State { DORMANT, INTRO, FIGHT, MOORAGE, DYING }

    public enum Moor { NONE, SWIM_IN, COILED, TEAR, SWIM_OUT }

    private enum Swim { NONE, DIVE, RISE, MOOR_IN, MOOR_OUT, DROOP, RECOVER, SINK }

    /** Where it sleeps: a ring on the bowl floor this far from the axis. */
    public static final double SLEEP_RADIUS = 14.0;
    public static final int FOLLOWERS = 5;
    public static final int GLANDS = 4;
    /** Parts: 1 to 4 the body segments, 5 the tail, 6 to 9 the glands (0 is the head, this entity). */
    public static final int ROLE_TAIL = 5;
    public static final int ROLE_GLAND = 6;
    public static final int SWING_DEDUPE_TICKS = 5;

    // entity events for the client's effects
    public static final byte EVENT_AWAKEN = 100;
    public static final byte EVENT_DIVE = 101;
    public static final byte EVENT_GLINT = 102;
    public static final byte EVENT_FLICK = 103;
    public static final byte EVENT_PARRIED = 104;
    public static final byte EVENT_BREAK = 105;
    public static final byte EVENT_COIL = 106;
    public static final byte EVENT_RIPPLE = 107;
    public static final byte EVENT_SHUDDER = 108;
    public static final byte EVENT_TEAR = 109;
    public static final byte EVENT_BITE = 110;
    public static final byte EVENT_SHED = 111;
    public static final byte EVENT_DEATH = 112;
    public static final byte EVENT_PEARL = 113;
    public static final byte EVENT_GLAND = 114;
    public static final byte EVENT_SONG = 115;

    private static final int FLAG_PHASE2 = 1;
    private static final int FLAG_BROKEN = 2;
    private static final int FLAG_GLANDS = 4;
    private static final int FLAG_HIDDEN = 8;
    private static final int FLAG_DROOP = 16;
    private static final int FLAG_MOORED = 32;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_STATE_START = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_ACTION_START = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Long> DATA_MOOR_START = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.VECTOR3);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Vector3f>[] DATA_POINTS = new EntityDataAccessor[FOLLOWERS + 1];

    static {
        for (int i = 0; i <= FOLLOWERS; i++) {
            DATA_POINTS[i] = SynchedEntityData.defineId(ThalassineLeviathan.class, EntityDataSerializers.VECTOR3);
        }
    }

    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation MOORED_ANIM = RawAnimation.begin().thenPlay("moor").thenLoop("moored");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ------------------------------------------------------------------ server state
    private @Nullable RiftLayout layout;
    private @Nullable BlockPos altarPos;
    private State state = State.DORMANT;
    private Moor moor = Moor.NONE;
    private int phase = 1;
    private Attack action = Attack.NONE;
    private long actionStart;
    private long nextActionAt;
    private long holdUntil = Long.MIN_VALUE;
    private long lastPlayerInside;
    private int playersAtStart = 1;
    private long fightStart;
    private long breakUntil = Long.MIN_VALUE;
    private boolean pendingMoorage;
    private boolean pendingDeath;
    private int mooragesStarted;
    /** Its health when the current Moorage began, and whether that Moorage has closed its plates over the glands. */
    private float moorStartHealth;
    private boolean platesClosed;
    private long moorStart;
    private long tearStart;
    private long pearlStart = Long.MIN_VALUE;
    private double angle;
    private double wave;
    private double sleepAngle;
    private Vec3 head = Vec3.ZERO;
    private Vec3 lastHead = Vec3.ZERO;
    private final PathTrail trail = new PathTrail(480, 0.25);
    private @Nullable Polyline swim;
    private double swimAt;
    private double swimSpeed;
    private double swimEnd = Double.NaN;
    private Swim swimKind = Swim.NONE;
    private final BreakGauge gauge = new BreakGauge(LeviathanMoves.BREAK_POISE, LeviathanMoves.BREAK_WINDOW);
    private final BossTargeting targeting = new BossTargeting();
    private final AttackPicker<Attack> picker = new AttackPicker<>();
    private final Participants participants = new Participants();
    private @Nullable UUID target;
    private @Nullable GuardianBossBar bar;
    private final GuardianPart[] parts = new GuardianPart[FOLLOWERS];
    private final GuardianPart[] glands = new GuardianPart[GLANDS];
    private final Vec3[] points = new Vec3[FOLLOWERS + 1];
    private final Vec3[] clientPrev = new Vec3[FOLLOWERS + 1];
    private final Vec3[] clientCur = new Vec3[FOLLOWERS + 1];
    private final Map<UUID, long[]> lastHitBy = new HashMap<>();
    private final Map<BlockPos, Long> crumbling = new LinkedHashMap<>();
    private final Set<UUID> struck = new HashSet<>();
    private List<ServerPlayer> inside = List.of();
    private long insideTick = Long.MIN_VALUE;
    private double currentImpact;
    private boolean parryableNow;
    private boolean flickParried;
    private @Nullable Polyline divePath;
    private boolean diveChained;
    private Vec3 aim = new Vec3(0, 0, 1);
    /** How far its head has reared up out of the orbit to sing over the platforms' rims, and how far it is going. */
    private double lift;
    private double liftTarget;
    private long nextSong;
    private final List<BlockPos> coilBlocks = new ArrayList<>();
    private final List<BlockPos> bridgeBlocks = new ArrayList<>();
    private int bridgesPlaced;
    // what tests and commands read
    private int breaks;
    private int dives;
    private int songs;
    private int bites;
    private int flicks;
    private int flicksParried;
    private int sheds;
    private int shudders;
    private int glandHits;
    private int moorages;

    public ThalassineLeviathan(EntityType<? extends ThalassineLeviathan> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 0;
        setNoGravity(true);
        setPersistenceRequired();
        for (int i = 0; i < points.length; i++) {
            points[i] = Vec3.ZERO;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, LeviathanMoves.BASE_HEALTH)
                .add(Attributes.ARMOR, LeviathanMoves.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, LeviathanMoves.TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    /** A sleeping Leviathan at a Rift (the lair's spawner): coiled on the bowl floor under the central asteroid. */
    public static @Nullable ThalassineLeviathan spawnDormant(ServerLevel level, BlockPos arenaCentre, BlockPos altar) {
        ThalassineLeviathan l = new ThalassineLeviathan(LeviathanRegistry.THALASSINE_LEVIATHAN.get(), level);
        l.bindLair(arenaCentre, altar);
        l.lieDown();
        level.addFreshEntity(l);
        return l;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_STATE_START, 0L);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_ACTION_START, 0L);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_HOME, BlockPos.ZERO);
        builder.define(DATA_MOOR_START, 0L);
        builder.define(DATA_AIM, new Vector3f(0, 0, 1));
        for (EntityDataAccessor<Vector3f> a : DATA_POINTS) {
            builder.define(a, new Vector3f());
        }
    }

    // ------------------------------------------------------------------ the lair

    @Override
    public void bindLair(BlockPos arenaCentre, BlockPos altar) {
        if (level() instanceof ServerLevel server) {
            this.layout = LeviathanRiftStructure.layout(server.getSeed(), arenaCentre);
        }
        this.altarPos = altar.immutable();
        entityData.set(DATA_HOME, arenaCentre.immutable());
    }

    /** Lies down asleep: its head on the bowl floor opposite the entrance, its body curled round behind it. */
    private void lieDown() {
        if (layout == null) {
            return;
        }
        Vec3 c = layout.centre();
        sleepAngle = layout.entranceAngle() + Math.PI;
        double y = layout.bowlSurface(SLEEP_RADIUS) + 2.3;
        head = LeviathanPaths.polar(c, sleepAngle, SLEEP_RADIUS, y);
        lastHead = LeviathanPaths.polar(c, sleepAngle - 0.05, SLEEP_RADIUS, y);
        trail.reset(LeviathanPaths.sleepingBody(c, sleepAngle, SLEEP_RADIUS, y, 45.0));
        placeHead();
        updatePoints();
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

    public @Nullable RiftLayout layout() {
        return layout;
    }

    /** The lair's centre (both sides). */
    public @Nullable Vec3 home() {
        BlockPos h = entityData.get(DATA_HOME);
        return h.equals(BlockPos.ZERO) ? null : Vec3.atCenterOf(h);
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
        return layout != null && layout.inside(point);
    }

    @Override
    public void blockPlaced(BlockPos pos) {
        crumbling.putIfAbsent(pos, level().getGameTime() + ArenaRules.CRUMBLE_TICKS);
    }

    /** A player who can fight: alive, in survival or adventure. */
    public boolean fairGame(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Fair players inside the sphere (worked out once a tick). */
    public List<ServerPlayer> fightersInside() {
        if (layout == null || !(level() instanceof ServerLevel server)) {
            return List.of();
        }
        long now = server.getGameTime();
        if (now != insideTick) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : server.players()) {
                if (fairGame(p) && layout.inside(p.position())) {
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
        if (state != State.DORMANT || layout == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        playersAtStart = Math.max(1, fightersInside().size());
        double max = GuardianHealth.scaled(LeviathanMoves.BASE_HEALTH, playersAtStart);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
        phase = 1;
        mooragesStarted = 0;
        breaks = dives = songs = bites = flicks = flicksParried = sheds = shudders = glandHits = moorages = 0;
        gauge.clear();
        targeting.clear();
        picker.clear();
        participants.clear();
        lastHitBy.clear();
        target = null;
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        pendingMoorage = pendingDeath = platesClosed = false;
        breakUntil = Long.MIN_VALUE;
        setFlags(0);
        wave = layout.wavePhase(); // its lair's platforms are set on this wave
        setState(State.INTRO, now);
        lastPlayerInside = now;
        fightStart = now + LeviathanMoves.INTRO;
        nextActionAt = fightStart + LeviathanMoves.FIRST_ATTACK;
        nextSong = now + 40;
        LeviathanPaths.Swim rise = LeviathanPaths.rise(layout.centre(), wave, head, sleepAngle);
        startSwim(rise, Swim.RISE, rise.path().length() / (LeviathanMoves.INTRO - 30.0));
        bar = new GuardianBossBar(getDisplayName(), BossEvent.BossBarColor.BLUE).meets(com.cosmicbreach.codex.CodexChapter.MET_LEVIATHAN);
        bar.setProgress(0f);
        bar.setMusic(LeviathanRegistry.MUSIC_LEVIATHAN.getId());
        GuardianFights.start(this);
        ensureParts(server);
        server.broadcastEntityEvent(this, EVENT_AWAKEN);
        playSoundAt(head, LeviathanRegistry.AWAKEN.get(), 5.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan awakened at {} for {} player(s), {} health{}", layout.centreBlock(),
                playersAtStart, (int) max, echo ? " (Guardian Echo)" : by != null ? " (" + by.getGameProfile().getName() + ")" : "");
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
        ServerLevel server = (ServerLevel) level();
        if (layout == null) {
            BlockPos home = entityData.get(DATA_HOME);
            if (home.equals(BlockPos.ZERO)) {
                discard(); // not a lair's (a stray summon)
                return;
            }
            bindLair(home, altarPos == null ? home : altarPos);
            lieDown();
        }
        long now = server.getGameTime();
        lastHead = head;
        switch (state) {
            case DORMANT -> dormantTick(server, now);
            case INTRO -> introTick(server, now);
            case FIGHT -> fightTick(server, now);
            case MOORAGE -> moorageTick(server, now);
            case DYING -> dyingTick(server, now);
        }
        if (isRemoved()) {
            return;
        }
        placeHead();
        updatePoints();
        moveParts();
        crumbleTick(server, now);
        if (state != State.DORMANT && state != State.DYING) {
            presenceTick(server, now);
            songTick(now);
        }
        barTick(server, now);
    }

    /** Swims along the current path, or its orbit; records the head for the body to follow. */
    private void swimTick(long now, double orbitSpeed) {
        if (swim != null) {
            swimAt = Math.min(swim.length(), swimAt + swimSpeed);
            head = swim.at(swimAt);
            if (swimAt >= swim.length() - 1e-9) {
                Swim done = swimKind;
                if (!Double.isNaN(swimEnd)) {
                    angle = swimEnd;
                }
                swim = null;
                swimKind = Swim.NONE;
                swimEnded(done, now);
            }
        } else if (orbitSpeed > 0 && layout != null) {
            angle = LeviathanOrbit.advance(angle, orbitSpeed);
            lift += Math.max(-0.3, Math.min(0.3, liftTarget - lift));
            head = LeviathanOrbit.point(layout.centre(), angle, wave).add(0, lift, 0);
        }
        trail.record(head);
    }

    private void startSwim(LeviathanPaths.Swim s, Swim kind, double speed) {
        swim = s.path();
        swimAt = 0.0;
        swimSpeed = speed;
        swimEnd = s.endAngle();
        swimKind = kind;
    }

    private void swimEnded(Swim kind, long now) {
        switch (kind) {
            case DIVE -> endDive(now);
            case MOOR_IN -> settleCoil(now);
            case MOOR_OUT -> {
                moor = Moor.NONE;
                setState(State.FIGHT, now);
                scheduleNext(now);
            }
            case SINK -> {
                pearlStart = now;
                level().broadcastEntityEvent(this, EVENT_PEARL);
                playSoundAt(head, LeviathanRegistry.PEARL.get(), 4.0f, 1.0f);
            }
            default -> {
            }
        }
    }

    private void placeHead() {
        double h = getBbHeight() / 2.0;
        setPos(head.x, head.y - h, head.z);
        Vec3 d = head.subtract(lastHead);
        if (d.lengthSqr() > 1e-6) {
            float yaw = LeviathanOrbit.yawOf(d);
            float pitch = LeviathanOrbit.pitchOf(d);
            setYRot(yaw);
            yRotO = yaw;
            yBodyRot = yaw;
            yBodyRotO = yaw;
            yHeadRot = yaw;
            yHeadRotO = yaw;
            setXRot(pitch);
            xRotO = pitch;
        }
    }

    private void updatePoints() {
        points[0] = head;
        for (int i = 0; i < FOLLOWERS; i++) {
            points[i + 1] = trail.behind(head, LeviathanMoves.FOLLOW[i]);
        }
        for (int i = 0; i <= FOLLOWERS; i++) {
            Vec3 p = points[i];
            entityData.set(DATA_POINTS[i], new Vector3f((float) p.x, (float) p.y, (float) p.z));
        }
    }

    /** A body point (0 the head, 1 to 4 the segments, 5 the tail): the server's, or on a client the last synced. */
    public Vec3 point(int i) {
        if (!level().isClientSide()) {
            return points[i];
        }
        Vector3f v = entityData.get(DATA_POINTS[i]);
        return new Vec3(v.x, v.y, v.z);
    }

    /** Where the mouth is: ahead of the head's middle along its aim while singing, else along the body. */
    public Vec3 mouth() {
        return point(0).add(facing().scale(LeviathanMoves.MOUTH_AHEAD));
    }

    /** The direction the head faces: its aim while singing, else from the first segment to the head. */
    public Vec3 facing() {
        if (action() == Attack.SONG) {
            Vector3f v = entityData.get(DATA_AIM);
            return new Vec3(v.x, v.y, v.z);
        }
        Vec3 d = point(0).subtract(point(1));
        return d.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private void dormantTick(ServerLevel server, long now) {
        head = points[0];
        if (Math.floorMod(now + getId(), 300L) == 0 && layout != null) {
            // the geode core sings faintly
            playSoundAt(layout.centre(), LeviathanRegistry.SONG.get(), 1.2f, 0.7f);
        }
        if (Math.floorMod(now + getId(), 10L) != 0 || server.getDifficulty() == Difficulty.PEACEFUL || !lairArmed()) {
            return;
        }
        for (ServerPlayer p : fightersInside()) {
            if (!LayerAttunement.has(p, GuardianTypes.LEVIATHAN.attunes())) {
                awaken(p, false);
                return;
            }
        }
    }

    private void introTick(ServerLevel server, long now) {
        swimTick(now, LeviathanMoves.SPEED);
        long t = now - stateStart();
        if (t == 150) {
            playSoundAt(head, LeviathanRegistry.SONG.get(), 5.0f, 0.9f);
        }
        if (t >= LeviathanMoves.INTRO) {
            setState(State.FIGHT, now);
            fightStart = now;
        }
    }

    private void fightTick(ServerLevel server, long now) {
        if (pendingDeath) {
            startDying(server, now);
            return;
        }
        if (pendingMoorage) {
            pendingMoorage = false;
            startMoorage(server, now);
            return;
        }
        boolean broken = broken(now);
        if (!broken && hasFlag(FLAG_BROKEN)) {
            endBreak(now);
        }
        if (BossTargeting.checkDue(now, fightStart) || target == null || !validTarget(target)) {
            chooseTarget(now);
        }
        swimTick(now, broken || swim != null ? 0.0 : orbitSpeed(now));
        if (broken) {
            return;
        }
        if (action != Attack.NONE) {
            actionTick(server, now, now - actionStart);
        } else if (now >= nextActionAt && swim == null) {
            startNextAction(server, now);
        }
    }

    /** Its speed along the orbit this tick: slower while singing, slowing to a stop for a flick, still while drooping. */
    private double orbitSpeed(long now) {
        double speed = LeviathanMoves.orbitSpeed(phase == 2);
        long t = now - actionStart;
        return switch (action) {
            case SONG -> t < LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL ? LeviathanMoves.SONG_SPEED : speed;
            case FLICK -> t < 8 ? speed * (1.0 - t / 8.0) : 0.0;
            default -> speed;
        };
    }

    // ------------------------------------------------------------------ targeting

    private boolean validTarget(UUID id) {
        return level() instanceof ServerLevel server && server.getPlayerByUUID(id) instanceof ServerPlayer p && fairGame(p)
                && layout != null && layout.inside(p.position());
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

    // ------------------------------------------------------------------ choosing and running attacks

    private LeviathanTactics.Situation situation(@Nullable ServerPlayer p) {
        double ahead = Double.NaN;
        boolean front = false;
        if (p != null && layout != null) {
            ahead = LeviathanPaths.targetAhead(layout.centre(), angle, p.position());
            Vec3 heading = LeviathanOrbit.heading(angle);
            front = SongPull.inCone(new SongPull.Cone(mouth(), heading), p.getEyePosition());
        }
        boolean tailNear = false;
        for (ServerPlayer q : fightersInside()) {
            if (near(q.getBoundingBox(), points[FOLLOWERS], LeviathanMoves.FLICK_TRIGGER)) {
                tailNear = true;
                break;
            }
        }
        return new LeviathanTactics.Situation(phase == 2, ahead, front, tailNear);
    }

    private void startNextAction(ServerLevel server, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null) {
            return;
        }
        List<AttackPicker.Option<Attack>> options = LeviathanTactics.options(situation(p));
        Attack chosen = picker.pick(options, now, random::nextDouble);
        if (chosen == null) {
            return;
        }
        picker.used(chosen, LeviathanTactics.cooldown(chosen, phase == 2), now);
        startAction(server, chosen, now, p);
    }

    /** Starts {@code chosen} now against {@code p} (also the debug command's way). */
    public void startAction(ServerLevel server, Attack chosen, long now, ServerPlayer p) {
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan attack: {}", chosen);
        action = chosen;
        actionStart = now;
        entityData.set(DATA_ACTION, (byte) chosen.ordinal());
        entityData.set(DATA_ACTION_START, now);
        struck.clear();
        float pitch = phase == 2 ? 1.2f : 1.0f;
        switch (chosen) {
            case DIVE -> {
                dives++;
                double startAngle = LeviathanOrbit.advance(angle, LeviathanMoves.DIVE_TELL * LeviathanMoves.orbitSpeed(phase == 2));
                // level over the target's platform until past its outer rim, then under it
                RiftLayout.Platform under = layout.nearestPlatform(LeviathanOrbit.angleOf(layout.centre(), p.position()));
                double clearOut = RiftLayout.PLATFORM_RING + under.radius() + 3.5;
                LeviathanPaths.Swim s = LeviathanPaths.dive(layout.centre(), wave, startAngle, p.position(), clearOut);
                divePath = s.path();
                swimEnd = s.endAngle();
                PacketDistributor.sendToPlayersTrackingEntity(this, new LeviathanPathPayload(getId(), now, (float) LeviathanMoves.DIVE_SPEED,
                        p.position(), s.path().points()));
                playSoundAt(head, LeviathanRegistry.SWELL.get(), 5.0f, pitch);
            }
            case SONG -> {
                songs++;
                // it rears its head up level with its target's eyes, to sing over the platform's rim
                liftTarget = Math.max(0.0, Math.min(10.0, p.getEyeY() - head.y));
                aimAt(p);
                playSoundAt(head, LeviathanRegistry.SWELL.get(), 5.0f, pitch * 1.05f);
                level().broadcastEntityEvent(this, EVENT_SONG);
            }
            case FLICK -> {
                flicks++;
                flickParried = false;
                playSoundAt(points[FOLLOWERS], LeviathanRegistry.FLICK_TELL.get(), 3.0f, 1.0f);
            }
            case SHED -> {
                sheds++;
                level().broadcastEntityEvent(this, EVENT_SHED);
                playSoundAt(points[2], LeviathanRegistry.SHED.get(), 4.0f, 1.0f);
            }
            case NONE -> {
            }
        }
    }

    private void actionTick(ServerLevel server, long now, long t) {
        switch (action) {
            case DIVE -> diveTick(server, now, t);
            case SONG -> songTick(server, now, t);
            case FLICK -> flickTick(server, now, t);
            case SHED -> shedTick(server, now, t);
            case NONE -> {
            }
        }
    }

    private void endAction(long now) {
        liftTarget = 0.0;
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        parryableNow = false;
        setFlag(FLAG_DROOP, false);
        scheduleNext(now);
    }

    // Breach Dive

    private void diveTick(ServerLevel server, long now, long t) {
        if (t == LeviathanMoves.DIVE_TELL && divePath != null) {
            double end = swimEnd;
            swim = divePath;
            swimAt = 0.0;
            swimSpeed = LeviathanMoves.DIVE_SPEED;
            swimEnd = end;
            swimKind = Swim.DIVE;
            server.broadcastEntityEvent(this, EVENT_DIVE);
            playSoundAt(head, LeviathanRegistry.DIVE.get(), 5.0f, phase == 2 ? 1.1f : 1.0f);
        }
        if (swimKind != Swim.DIVE) {
            return;
        }
        currentImpact = LeviathanMoves.DIVE_IMPACT;
        for (ServerPlayer p : fightersInside()) {
            if (struck.contains(p.getUUID())) {
                continue;
            }
            AABB box = p.getBoundingBox();
            if (near(box, points[0], LeviathanMoves.DIVE_REACH) || near(box, points[1], LeviathanMoves.DIVE_REACH - 0.2)) {
                struck.add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), (float) LeviathanMoves.DIVE_DAMAGE)) {
                    Vec3 away = p.position().subtract(points[0]);
                    Vec3 flat = new Vec3(away.x, 0, away.z);
                    flat = flat.lengthSqr() < 1e-6 ? facing() : flat.normalize();
                    p.setDeltaMovement(p.getDeltaMovement().add(flat.scale(0.9)).add(0, 0.45, 0));
                    p.hurtMarked = true;
                }
            }
        }
    }

    private void endDive(long now) {
        divePath = null;
        ServerPlayer p = targetPlayer();
        if (phase == 2 && !diveChained && p != null && random.nextDouble() < LeviathanMoves.DIVE_CHAIN && layout != null
                && LeviathanPaths.diveWindow(LeviathanPaths.targetAhead(layout.centre(), angle, p.position()))) {
            diveChained = true;
            startAction((ServerLevel) level(), Attack.DIVE, now, p);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan chains a second Breach Dive");
            return;
        }
        diveChained = false;
        endAction(now);
    }

    private static boolean near(AABB box, Vec3 p, double reach) {
        double nx = Math.max(box.minX, Math.min(p.x, box.maxX));
        double ny = Math.max(box.minY, Math.min(p.y, box.maxY));
        double nz = Math.max(box.minZ, Math.min(p.z, box.maxZ));
        return (nx - p.x) * (nx - p.x) + (ny - p.y) * (ny - p.y) + (nz - p.z) * (nz - p.z) <= reach * reach;
    }

    // Song of Pulling

    private void aimAt(ServerPlayer p) {
        Vec3 d = p.getBoundingBox().getCenter().subtract(point(0));
        if (d.lengthSqr() > 1e-6) {
            aim = d.normalize();
            entityData.set(DATA_AIM, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
        }
    }

    private void songTick(ServerLevel server, long now, long t) {
        ServerPlayer p = targetPlayer();
        if (t < LeviathanMoves.SONG_TELL && p != null) {
            aimAt(p); // the mouth follows its target through the telegraph, then holds
        }
        if (t == LeviathanMoves.SONG_TELL) {
            playSoundAt(mouth(), LeviathanRegistry.PULL.get(), 5.0f, phase == 2 ? 1.15f : 1.0f);
        }
        if (SongPull.pulling(t)) {
            Vec3 m = mouth();
            for (ServerPlayer q : fightersInside()) {
                if (SongPull.bites(m, q.getBoundingBox())) {
                    bite(server, q, m, now);
                    return;
                }
            }
        }
        if (t >= LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL) {
            endAction(now);
        }
    }

    private void bite(ServerLevel server, ServerPlayer p, Vec3 mouth, long now) {
        bites++;
        currentImpact = 20.0;
        server.broadcastEntityEvent(this, EVENT_BITE);
        playSoundAt(mouth, LeviathanRegistry.BITE.get(), 4.0f, 1.0f);
        if (p.hurt(damageSources().mobAttack(this), (float) LeviathanMoves.BITE_DAMAGE)) {
            Vec3 out = p.position().subtract(mouth);
            out = out.lengthSqr() < 1e-6 ? facing() : out.normalize();
            p.setDeltaMovement(out.scale(LeviathanMoves.BITE_THROW).add(0, 0.5, 0));
            p.hurtMarked = true;
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan bites {}", p.getGameProfile().getName());
        endAction(now);
    }

    /** True if rock hides both {@code p}'s middle and eyes from the mouth (an asteroid blocks the song). Either side. */
    public boolean songBlocked(Player p) {
        Vec3 from = mouth();
        return hidden(p, from, p.getBoundingBox().getCenter()) && hidden(p, from, p.getEyePosition());
    }

    private boolean hidden(Player p, Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK;
    }

    /** The song's cone now (both sides; meaningful while it sings). */
    public SongPull.Cone songCone() {
        return new SongPull.Cone(mouth(), facing());
    }

    // Tail Flick

    private void flickTick(ServerLevel server, long now, long t) {
        if (t == LeviathanMoves.FLICK_GLINT) {
            server.broadcastEntityEvent(this, EVENT_GLINT);
            playSoundAt(points[FOLLOWERS], LeviathanRegistry.GLINT.get(), 3.0f, 1.0f);
        }
        if (t >= LeviathanMoves.FLICK_TELL && t < LeviathanMoves.FLICK_TELL + LeviathanMoves.FLICK_ACTIVE && !flickParried) {
            if (t == LeviathanMoves.FLICK_TELL) {
                server.broadcastEntityEvent(this, EVENT_FLICK);
                playSoundAt(points[FOLLOWERS], LeviathanRegistry.FLICK.get(), 4.0f, 1.0f);
            }
            Vec3 tail = points[FOLLOWERS];
            currentImpact = LeviathanMoves.FLICK_IMPACT;
            for (ServerPlayer p : fightersInside()) {
                if (struck.contains(p.getUUID()) || !near(p.getBoundingBox(), tail, LeviathanMoves.FLICK_REACH)) {
                    continue;
                }
                struck.add(p.getUUID());
                parryableNow = true;
                boolean hurt = p.hurt(damageSources().mobAttack(this), (float) LeviathanMoves.FLICK_DAMAGE);
                parryableNow = false;
                if (hurt) {
                    Vec3 away = p.position().subtract(tail);
                    Vec3 flat = new Vec3(away.x, 0, away.z);
                    flat = flat.lengthSqr() < 1e-6 ? LeviathanOrbit.outward(angle) : flat.normalize();
                    p.setDeltaMovement(flat.scale(LeviathanMoves.FLICK_KNOCKBACK * 0.6).add(0, 0.45, 0));
                    p.hurtMarked = true;
                }
                if (flickParried) {
                    break;
                }
            }
        }
        long end = LeviathanMoves.FLICK_TELL + LeviathanMoves.FLICK_ACTIVE + (flickParried ? LeviathanMoves.DROOP : LeviathanMoves.FLICK_RECOVER);
        if (t >= end) {
            endAction(now);
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

    @Override
    public void onParried(Player player) {
        if (action == Attack.FLICK && parryableNow && !flickParried) {
            flickParried = true;
            flicksParried++;
            setFlag(FLAG_DROOP, true);
            level().broadcastEntityEvent(this, EVENT_PARRIED);
            playSoundAt(points[FOLLOWERS], LeviathanRegistry.DROOP.get(), 4.0f, 1.0f);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Tail Flick parried by {}: the tail droops for {} ticks", player.getGameProfile().getName(),
                    LeviathanMoves.DROOP);
        }
    }

    // Scale Shed

    private void shedTick(ServerLevel server, long now, long t) {
        if (t == LeviathanMoves.SHED_TELL) {
            List<ServerPlayer> targets = fightersInside();
            if (!targets.isEmpty()) {
                for (int i = 0; i < LeviathanMoves.SHED_COUNT; i++) {
                    Vec3 seg = points[1 + i % 4];
                    Vec3 side = facing().cross(new Vec3(0, 1, 0));
                    side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
                    Vec3 at = seg.add(side.scale((i % 2 == 0 ? 1 : -1) * 2.4)).add(0, 0.6, 0);
                    ShedScale.shed(server, this, at, targets.get(i % targets.size()));
                }
            }
        }
        if (t >= LeviathanMoves.SHED_TELL + 10) {
            endAction(now);
        }
    }

    // ------------------------------------------------------------------ Break

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
        breakUntil = now + LeviathanMoves.BREAK_TICKS;
        setFlag(FLAG_BROKEN, true);
        setFlag(FLAG_DROOP, false);
        if (swimKind == Swim.DIVE) {
            divePath = null;
        }
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        parryableNow = false;
        if (layout != null) {
            double a = LeviathanOrbit.angleOf(layout.centre(), head);
            RiftLayout.Platform p = layout.nearestPlatform(a + 0.22);
            startSwim(LeviathanPaths.droop(layout.centre(), head, a, p.top()), Swim.DROOP, 0.3);
        }
        level().broadcastEntityEvent(this, EVENT_BREAK);
        playSoundAt(head, LeviathanRegistry.BREAK.get(), 5.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan Broken at {} health", String.format("%.1f", getHealth()));
    }

    private void endBreak(long now) {
        setFlag(FLAG_BROKEN, false);
        gauge.clear();
        if (layout != null) {
            double a = LeviathanOrbit.angleOf(layout.centre(), head);
            startSwim(LeviathanPaths.recover(layout.centre(), wave, head, a), Swim.RECOVER, 0.35);
        }
        scheduleNext(now);
    }

    public boolean broken(long now) {
        return level().isClientSide() ? hasFlag(FLAG_BROKEN) : now < breakUntil;
    }

    // ------------------------------------------------------------------ Moorage

    private void startMoorage(ServerLevel server, long now) {
        mooragesStarted++;
        moorStartHealth = getHealth();
        platesClosed = false;
        moorages++;
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        parryableNow = false;
        breakUntil = Long.MIN_VALUE;
        setFlag(FLAG_BROKEN, false);
        setFlag(FLAG_DROOP, false);
        divePath = null;
        gauge.clear();
        if (mooragesStarted >= 1) {
            phase = 2;
            setFlag(FLAG_PHASE2, true);
        }
        setState(State.MOORAGE, now);
        moor = Moor.SWIM_IN;
        double a = LeviathanOrbit.angleOf(layout.centre(), head);
        // it coils beside its target's platform, so that platform's bridge lands mid-coil
        ServerPlayer t = targetPlayer();
        double mid = t != null ? layout.nearestPlatform(LeviathanOrbit.angleOf(layout.centre(), t.position())).angle() : a + 2.6;
        startSwim(LeviathanPaths.moorage(layout.centre(), wave, head, a, mid), Swim.MOOR_IN, LeviathanMoves.SWIM_IN_SPEED);
        playSoundAt(head, LeviathanRegistry.SWELL.get(), 5.0f, 0.8f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan swims into Moorage {} at {} health", mooragesStarted,
                String.format("%.1f", getHealth()));
    }

    private void moorageTick(ServerLevel server, long now) {
        if (pendingDeath) {
            startDying(server, now);
            return;
        }
        if (BossTargeting.checkDue(now, fightStart) || target == null || !validTarget(target)) {
            chooseTarget(now);
        }
        swimTick(now, 0.0);
        switch (moor) {
            case COILED -> {
                int t = (int) (now - moorStart);
                growBridges(server, t);
                if (Moorage.rippleStarts(t)) {
                    server.broadcastEntityEvent(this, EVENT_RIPPLE);
                    playSoundAt(points[2], LeviathanRegistry.RIPPLE.get(), 4.0f, 1.0f);
                }
                if (Moorage.shudders(t)) {
                    shudder(server);
                }
                if (Moorage.tearsFree(t)) {
                    tearFree(server, now);
                }
            }
            case TEAR -> {
                if (now - tearStart >= Moorage.TEAR_FREE) {
                    moor = Moor.SWIM_OUT;
                    double a = LeviathanOrbit.angleOf(layout.centre(), head);
                    startSwim(LeviathanPaths.tearFree(layout.centre(), wave, head, a), Swim.MOOR_OUT, 0.5);
                }
            }
            default -> {
            }
        }
        if (moor == Moor.SWIM_OUT && swim == null) {
            moor = Moor.NONE;
            setState(State.FIGHT, now);
            scheduleNext(now);
        }
    }

    private void settleCoil(long now) {
        moor = Moor.COILED;
        moorStart = now;
        entityData.set(DATA_MOOR_START, now);
        setFlag(FLAG_MOORED, true);
        setFlag(FLAG_GLANDS, true);
        if (level() instanceof ServerLevel server) {
            ensureGlands(server);
            layCoil(server);
            planBridges();
            server.broadcastEntityEvent(this, EVENT_COIL);
        }
        playSoundAt(head, LeviathanRegistry.COIL.get(), 5.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan moored: {} coil blocks, {} bridge blocks", coilBlocks.size(), bridgeBlocks.size());
    }

    /** True while moored and holding still (the coil and the bridges stay). */
    public boolean holdsMoorage() {
        return state == State.MOORAGE && moor == Moor.COILED;
    }

    /** The coil's walkable back: an invisible solid strip along the top of the body, level at the coil's top. */
    private void layCoil(ServerLevel server) {
        coilBlocks.clear();
        int y = (int) Math.floor(layout.centre().y + LeviathanMoves.COIL_TOP) - 1;
        BlockState coil = LeviathanRegistry.LEVIATHAN_COIL.get().defaultBlockState();
        Vec3 c = layout.centre();
        Set<BlockPos> strip = new java.util.LinkedHashSet<>();
        for (double s = 0; s <= LeviathanMoves.FOLLOW[3] + 1.0; s += 0.4) {
            Vec3 p = trail.behind(head, s);
            double half = s < 2.0 ? 1.0 : Math.min(1.35, LeviathanPaths.halfWidth(s) - 0.15); // narrowing to the tail
            for (double dx = -half; dx <= half; dx += 0.5) {
                for (double dz = -half; dz <= half; dz += 0.5) {
                    if (dx * dx + dz * dz <= half * half) {
                        strip.add(BlockPos.containing(p.x + dx, y, p.z + dz));
                    }
                }
            }
        }
        for (BlockPos pos : strip) {
            if (server.getBlockState(pos).isAir() && server.getEntitiesOfClass(Player.class, new AABB(pos)).isEmpty()
                    && Math.hypot(pos.getX() + 0.5 - c.x, pos.getZ() + 0.5 - c.z) > RiftLayout.CORE_RADIUS - 1) {
                server.setBlock(pos, coil, Block.UPDATE_ALL);
                coilBlocks.add(pos.immutable());
            }
        }
    }

    /** The bridges that grow out to the coil from the three platforms nearest its middle, planned now, laid over time. */
    private void planBridges() {
        bridgeBlocks.clear();
        bridgesPlaced = 0;
        Vec3 c = layout.centre();
        double coilMid = LeviathanOrbit.angleOf(c, trail.behind(head, LeviathanMoves.FOLLOW[1]));
        double coilTop = Math.floor(c.y + LeviathanMoves.COIL_TOP);
        for (RiftLayout.Platform p : layout.nearestPlatforms(coilMid, 3)) {
            double a = p.angle();
            double r0 = RiftLayout.PLATFORM_RING - p.radius() + 1.0;
            double r1 = LeviathanMoves.COIL_RADIUS + 1.6;
            double s0 = p.top() + 1.0;
            int steps = (int) Math.ceil((r0 - r1) / 0.5);
            Set<BlockPos> path = new java.util.LinkedHashSet<>();
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double r = r0 + (r1 - r0) * f;
                double surface = s0 + (coilTop - s0) * f;
                int floor = (int) Math.round(surface) - 1;
                for (double w = -1.0; w <= 1.0; w += 1.0) {
                    double x = c.x + r * Math.cos(a) - Math.sin(a) * w;
                    double z = c.z + r * Math.sin(a) + Math.cos(a) * w;
                    path.add(BlockPos.containing(x, floor, z));
                }
            }
            bridgeBlocks.addAll(path);
        }
    }

    private void growBridges(ServerLevel server, int t) {
        if (bridgeBlocks.isEmpty() || bridgesPlaced >= bridgeBlocks.size()) {
            return;
        }
        int want = (int) Math.ceil(bridgeBlocks.size() * Math.min(1.0, (t + 1) / (double) Moorage.BRIDGE_GROW));
        BlockState plank = LeviathanRegistry.RIFT_BRIDGE.get().defaultBlockState();
        while (bridgesPlaced < want) {
            BlockPos pos = bridgeBlocks.get(bridgesPlaced++);
            if (server.getBlockState(pos).isAir()) {
                server.setBlock(pos, plank, Block.UPDATE_ALL);
                if (bridgesPlaced % 6 == 0) {
                    server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.0);
                }
            } else {
                bridgeBlocks.set(bridgesPlaced - 1, BlockPos.ZERO); // not ours to take away later
            }
        }
        if (t == 0) {
            playSoundAt(points[2], LeviathanRegistry.GROW.get(), 4.0f, 1.0f);
        }
    }

    /** Takes the coil's back and the bridges away (they crumble). */
    private void clearMoorageBlocks(ServerLevel server) {
        for (BlockPos pos : coilBlocks) {
            if (server.getBlockState(pos).is(LeviathanRegistry.LEVIATHAN_COIL.get())) {
                server.removeBlock(pos, false);
            }
        }
        coilBlocks.clear();
        for (BlockPos pos : bridgeBlocks) {
            if (!pos.equals(BlockPos.ZERO) && server.getBlockState(pos).is(LeviathanRegistry.RIFT_BRIDGE.get())) {
                server.destroyBlock(pos, false);
            }
        }
        bridgeBlocks.clear();
        bridgesPlaced = 0;
    }

    /** A shudder: everyone standing on the back and not holding on by a gland takes 6 and is thrown up and out. */
    private void shudder(ServerLevel server) {
        shudders++;
        server.broadcastEntityEvent(this, EVENT_SHUDDER);
        playSoundAt(points[2], LeviathanRegistry.SHUDDER.get(), 5.0f, 1.0f);
        currentImpact = 10.0;
        double top = Math.floor(layout.centre().y + LeviathanMoves.COIL_TOP);
        for (ServerPlayer p : fightersInside()) {
            if (!p.onGround() || Math.abs(p.getY() - top) > 0.6 || !onCoil(p.position())) {
                continue;
            }
            boolean holding = false;
            for (int i = 0; i < GLANDS; i++) {
                if (glandPoint(i).distanceTo(p.position()) <= LeviathanMoves.HOLD_ON) {
                    holding = true; // by a gland, open or under its closed plates
                    break;
                }
            }
            if (holding) {
                continue;
            }
            if (p.hurt(damageSources().mobAttack(this), (float) LeviathanMoves.SHUDDER_DAMAGE)) {
                Vec3 out = LeviathanOrbit.outward(LeviathanOrbit.angleOf(layout.centre(), p.position()));
                p.setDeltaMovement(out.scale(0.45).add(0, 0.5 * LeviathanMoves.SHUDDER_UP, 0));
                p.hurtMarked = true;
            }
        }
    }

    /** True if {@code p} is over the coil's back (near a point of the moored body). */
    public boolean onCoil(Vec3 p) {
        for (double s = 0; s <= LeviathanMoves.FOLLOW[3] + 1.0; s += 1.0) {
            Vec3 b = trail.behind(head, s);
            if (Math.hypot(p.x - b.x, p.z - b.z) <= 1.9) {
                return true;
            }
        }
        return false;
    }

    private void tearFree(ServerLevel server, long now) {
        moor = Moor.TEAR;
        tearStart = now;
        setFlag(FLAG_MOORED, false);
        setFlag(FLAG_GLANDS, false);
        removeGlands();
        clearMoorageBlocks(server);
        server.broadcastEntityEvent(this, EVENT_TEAR);
        playSoundAt(head, LeviathanRegistry.TEAR_FREE.get(), 6.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan tears free at {} health", String.format("%.1f", getHealth()));
    }

    /**
     * A Moorage before the last has lost its fifth: the plates close over the glands (they go dark and take no more hits)
     * and it holds its coil, doing nothing more, till it tears free.
     */
    private void closePlates() {
        platesClosed = true;
        setFlag(FLAG_GLANDS, false);
        removeGlands();
        playSoundAt(points[2], LeviathanRegistry.COIL.get(), 5.0f, 0.7f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan closes its plates over the glands at {} health", String.format("%.1f", getHealth()));
    }

    /** The coil's settle tick (both sides), for the ripple. */
    public long moorStart() {
        return entityData.get(DATA_MOOR_START);
    }

    // ------------------------------------------------------------------ death, reset

    private void startDying(ServerLevel server, long now) {
        pendingDeath = false;
        setState(State.DYING, now);
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        moor = Moor.NONE;
        setFlags(hasFlag(FLAG_PHASE2) ? FLAG_PHASE2 : 0);
        removeGlands();
        clearMoorageBlocks(server);
        double floor = layout.bowlSurface(Math.max(RiftLayout.CORE_RADIUS + 3.0, LeviathanPaths.radius(layout.centre(), head))) + 2.2;
        startSwim(LeviathanPaths.sink(head, facing(), floor), Swim.SINK, LeviathanMoves.SINK_SPEED);
        server.broadcastEntityEvent(this, EVENT_DEATH);
        playSoundAt(head, LeviathanRegistry.DEATH.get(), 6.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan defeated after {} ticks of fighting", now - fightStart);
    }

    private void dyingTick(ServerLevel server, long now) {
        swimTick(now, 0.0);
        long t = now - stateStart();
        if (pearlStart == Long.MIN_VALUE && t >= LeviathanMoves.SINK_MAX) {
            swim = null;
            swimEnded(Swim.SINK, now);
        }
        if (pearlStart != Long.MIN_VALUE && now - pearlStart >= LeviathanMoves.PEARL) {
            finishKill(server, now);
        }
    }

    private void finishKill(ServerLevel server, long now) {
        for (UUID id : participants.all()) {
            // paid now, or held for a participant who is offline or dead until they are back (GuardianPayouts)
            com.cosmicbreach.guardian.GuardianPayouts.payOrOwe(server.getServer(), id, GuardianTypes.LEVIATHAN);
        }
        GuardianAltarBlockEntity a = altar();
        if (a != null) {
            a.guardianKilled(now);
        }
        endFight();
        discard();
    }

    private void presenceTick(ServerLevel server, long now) {
        if (!fightersInside().isEmpty()) {
            lastPlayerInside = now;
            return;
        }
        if (now - lastPlayerInside >= LeviathanMoves.RESET_ABSENT) {
            reset(server, now);
        }
    }

    /** Back to sleep at full health, no loot, no cooldown (everyone left, or a command). */
    public void reset(ServerLevel server, long now) {
        action = Attack.NONE;
        entityData.set(DATA_ACTION, (byte) 0);
        moor = Moor.NONE;
        swim = null;
        swimKind = Swim.NONE;
        divePath = null;
        breakUntil = Long.MIN_VALUE;
        pendingMoorage = pendingDeath = platesClosed = false;
        clearMoorageBlocks(server);
        removeParts();
        removeGlands();
        setFlags(0);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(LeviathanMoves.BASE_HEALTH);
        state = State.DORMANT;
        setHealth((float) LeviathanMoves.BASE_HEALTH);
        setState(State.DORMANT, now);
        phase = 1;
        participants.clear();
        endFight();
        lieDown();
        CosmicBreach.LOGGER.debug("[cosmicbreach] Thalassine Leviathan reset to sleep (nobody in the Rift)");
    }

    private void endFight() {
        if (bar != null) {
            bar.remove();
            bar = null;
        }
        GuardianFights.end(this);
        removeParts();
        removeGlands();
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

    /** Its song, the fight's clock: low and slow while it orbits (higher in phase 2), swelling before each attack. */
    private void songTick(long now) {
        if (now >= nextSong && state == State.FIGHT && action == Attack.NONE) {
            nextSong = now + 110 + random.nextInt(60);
            playSoundAt(head, LeviathanRegistry.SONG.get(), 4.0f, phase == 2 ? 1.12f : 0.92f);
        }
    }

    // ------------------------------------------------------------------ the boss bar

    private void barTick(ServerLevel server, long now) {
        if (bar == null) {
            return;
        }
        if (state == State.INTRO) {
            bar.setProgress((float) (now - stateStart()) / LeviathanMoves.INTRO);
        } else if (state == State.DYING) {
            bar.setProgress(0f);
        } else {
            bar.setProgress(getHealth() / getMaxHealth());
            bar.setColor(state == State.MOORAGE ? BossEvent.BossBarColor.YELLOW : phase == 2 ? BossEvent.BossBarColor.PURPLE
                    : BossEvent.BossBarColor.BLUE);
        }
        bar.setGauge((float) gauge.fraction(now), broken(now));
        Vec3 c = layout.centre();
        bar.update(server, p -> layout.shellFraction(p.getX(), p.getY(), p.getZ()) < 1.25 && p.distanceToSqr(c) < 120 * 120);
    }

    public double gaugeFraction() {
        return gauge.fraction(level().getGameTime());
    }

    // ------------------------------------------------------------------ parts

    private void ensureParts(ServerLevel server) {
        for (int i = 0; i < FOLLOWERS; i++) {
            if (parts[i] == null || parts[i].isRemoved()) {
                parts[i] = GuardianPart.spawn(server, this, i + 1, LeviathanMoves.PART_WIDTH[i + 1], LeviathanMoves.PART_HEIGHT[i + 1],
                        points[i + 1]);
            }
        }
    }

    private void ensureGlands(ServerLevel server) {
        for (int i = 0; i < GLANDS; i++) {
            if (glands[i] == null || glands[i].isRemoved()) {
                glands[i] = GuardianPart.spawn(server, this, ROLE_GLAND + i, LeviathanMoves.GLAND_SIZE, LeviathanMoves.GLAND_SIZE, glandPoint(i));
            }
        }
    }

    /** Gland {@code i} (0 to 3): on the spine of segment {@code i + 1}. */
    public Vec3 glandPoint(int i) {
        Vec3 p = point(i + 1);
        return p.add(0, LeviathanPaths.halfHeight(LeviathanMoves.FOLLOW[i]) + 0.3, 0);
    }

    private void moveParts() {
        for (int i = 0; i < FOLLOWERS; i++) {
            if (parts[i] != null && !parts[i].isRemoved()) {
                parts[i].placeCentre(points[i + 1]);
            }
        }
        for (int i = 0; i < GLANDS; i++) {
            if (glands[i] != null && !glands[i].isRemoved()) {
                glands[i].placeCentre(glandPoint(i));
            }
        }
    }

    private void removeParts() {
        for (int i = 0; i < FOLLOWERS; i++) {
            if (parts[i] != null) {
                parts[i].discard();
                parts[i] = null;
            }
        }
    }

    private void removeGlands() {
        for (int i = 0; i < GLANDS; i++) {
            if (glands[i] != null) {
                glands[i].discard();
                glands[i] = null;
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
            if (level() instanceof ServerLevel server) {
                clearMoorageBlocks(server);
            }
            endFight();
            discard();
            return false;
        }
        if (state != State.FIGHT && state != State.MOORAGE) {
            return false;
        }
        if (source.getEntity() == this) {
            return false;
        }
        if (layout != null && ArenaRules.burnsUp(source, layout.centre(), RiftLayout.RX)) {
            return false;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player != null && duplicateHit(player, 0)) {
            return false;
        }
        return applyHit(source, (float) (amount * LeviathanMoves.HEAD_TAKEN), player);
    }

    /** A hit that passed the checks: x1.5 while Broken; the attacker took part and counts toward the target. */
    private boolean applyHit(DamageSource source, float amount, @Nullable ServerPlayer player) {
        float dealt = broken(level().getGameTime()) ? (float) (amount * LeviathanMoves.BREAK_DAMAGE_TAKEN) : amount;
        boolean hurt = super.hurt(source, dealt);
        if (hurt && player != null) {
            participants.dealtDamage(player.getUUID(), layout != null && layout.inside(player.position()));
            targeting.recordDamage(player.getUUID(), dealt, level().getGameTime());
        }
        return hurt;
    }

    @Override
    public boolean hurtByPart(GuardianPart part, DamageSource source, float amount) {
        if ((state != State.FIGHT && state != State.MOORAGE) || level().isClientSide()) {
            return false;
        }
        if (layout != null && ArenaRules.burnsUp(source, layout.centre(), RiftLayout.RX)) {
            return false;
        }
        int role = part.role();
        double taken;
        if (role >= ROLE_GLAND) {
            if (!hasFlag(FLAG_GLANDS)) {
                return false;
            }
            taken = LeviathanMoves.GLAND_TAKEN;
        } else {
            taken = role == ROLE_TAIL ? LeviathanMoves.TAIL_TAKEN : LeviathanMoves.BODY_TAKEN;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player != null) {
            if (duplicateHit(player, role)) {
                return false;
            }
            if (CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
                invulnerableTime = 0; // the engine paces its own hits
            }
        }
        boolean hurt = applyHit(source, (float) (amount * taken), player);
        if (hurt && role >= ROLE_GLAND) {
            glandHits++;
            level().broadcastEntityEvent(this, EVENT_GLAND);
            playSoundAt(part.getBoundingBox().getCenter(), LeviathanRegistry.GLAND_HIT.get(), 2.0f, 0.9f + random.nextFloat() * 0.2f);
        }
        return hurt;
    }

    /**
     * One swing may reach two parts (on the same active tick or the next); only the first part it lands on counts, so a
     * swing never deals the Leviathan damage twice. {@code key} 0 is the head, 1 to 9 the parts.
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

    /** Health can't pass a Moorage line in one hit, and the kill waits for its death to play out. */
    @Override
    public void setHealth(float health) {
        if ((state == State.FIGHT || state == State.MOORAGE) && !level().isClientSide() && health < getHealth()) {
            if (state == State.MOORAGE) {
                health = Moorage.hold(moorStartHealth, health, getMaxHealth(), mooragesStarted);
                if (!platesClosed && Moorage.platesClose(moorStartHealth, health, getMaxHealth(), mooragesStarted)) {
                    closePlates();
                }
            }
            float kept = Moorage.clamp(getHealth(), health, getMaxHealth(), mooragesStarted);
            if (kept > health) {
                pendingMoorage = true;
                health = kept;
            }
            if (health <= 0.0f) {
                health = 0.01f;
                pendingDeath = true;
            }
        }
        super.setHealth(health);
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
    public boolean isPickable() {
        return state != State.DORMANT && !isRemoved();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(46.0);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            if (level() instanceof ServerLevel server && !coilBlocks.isEmpty()) {
                clearMoorageBlocks(server);
            }
            endFight();
        }
        super.remove(reason);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return LeviathanRegistry.HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 3.0f;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
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

    public Attack action() {
        int o = entityData.get(DATA_ACTION);
        Attack[] all = Attack.values();
        return o >= 0 && o < all.length ? all[o] : Attack.NONE;
    }

    public long actionStart() {
        return entityData.get(DATA_ACTION_START);
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

    public boolean isBroken() {
        return hasFlag(FLAG_BROKEN);
    }

    public boolean glandsExposed() {
        return hasFlag(FLAG_GLANDS);
    }

    public boolean moored() {
        return hasFlag(FLAG_MOORED);
    }

    public boolean drooping() {
        return hasFlag(FLAG_DROOP);
    }

    // ------------------------------------------------------------------ what tests and commands read (server)

    public int phase() {
        return phase;
    }

    public Moor moorStage() {
        return moor;
    }

    public Set<UUID> participants() {
        return participants.all();
    }

    public @Nullable UUID currentTarget() {
        return target;
    }

    public long fightStart() {
        return fightStart;
    }

    public int playersAtStart() {
        return playersAtStart;
    }

    public double orbitAngle() {
        return angle;
    }

    public int breaks() {
        return breaks;
    }

    /** {dives, songs, bites, flicks, parried flicks, sheds, Moorages, shudders, gland hits}. */
    public int[] counts() {
        return new int[] {dives, songs, bites, flicks, flicksParried, sheds, moorages, shudders, glandHits};
    }

    public List<BlockPos> coilBlocks() {
        return List.copyOf(coilBlocks);
    }

    public List<BlockPos> bridgeBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < Math.min(bridgesPlaced, bridgeBlocks.size()); i++) {
            if (!bridgeBlocks.get(i).equals(BlockPos.ZERO)) {
                out.add(bridgeBlocks.get(i));
            }
        }
        return out;
    }

    public @Nullable Polyline divePath() {
        return divePath;
    }

    /** Debug: starts {@code next} now if it is free to act (forced attacks ignore their conditions and cooldowns). */
    public void forceNext(Attack next) {
        if (level() instanceof ServerLevel server && state == State.FIGHT && action == Attack.NONE && swim == null
                && !broken(server.getGameTime())) {
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

    private void scheduleNext(long now) {
        nextActionAt = Math.max(now + LeviathanMoves.GAP, holdUntil);
    }

    /** True while attacks are held by the debug command. */
    public boolean held() {
        return level().getGameTime() < holdUntil;
    }

    /** Debug: sets the health (through the Moorage lines). */
    public void debugHealth(float health) {
        setHealth(health);
    }

    /** Debug: straight to its death (the kill and its rewards play out as ever). */
    public void debugKill() {
        if (state == State.FIGHT || state == State.MOORAGE) {
            pendingDeath = true;
        }
    }

    /** The orbit's wave phase this fight (server, for tests). */
    public double orbitWave() {
        return wave;
    }

    /** Debug: fills the Break gauge. */
    public void debugBreak() {
        takeImpact(gauge.capacity());
    }

    // ------------------------------------------------------------------ saving: never (noSave), but a bound one reloads asleep

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = State.DORMANT;
        setFlags(0);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        for (int i = 0; i <= FOLLOWERS; i++) {
            Vec3 now = point(i);
            clientPrev[i] = clientCur[i] == null ? now : clientCur[i];
            clientCur[i] = now;
        }
        // the model is placed bone by bone from the body's points: keep the renderer's own turn out of it
        yBodyRot = 0f;
        yBodyRotO = 0f;
        yHeadRot = 0f;
        yHeadRotO = 0f;
        LeviathanEffects.handler().leviathanTick(this);
    }

    /** A body point between the last two ticks (client, for drawing). */
    public Vec3 renderPoint(int i, float partial) {
        if (clientCur[i] == null) {
            return point(i);
        }
        return clientPrev[i].lerp(clientCur[i], partial);
    }

    /** The direction of the body at point {@code i} between the last two ticks (client): the head's aim while it sings. */
    public Vec3 renderDirection(int i, float partial) {
        Vec3 d;
        if (i == 0) {
            if (action() == Attack.SONG) {
                return facing();
            }
            d = renderPoint(0, partial).subtract(renderPoint(1, partial));
        } else if (i == FOLLOWERS) {
            d = renderPoint(FOLLOWERS - 1, partial).subtract(renderPoint(FOLLOWERS, partial));
        } else {
            d = renderPoint(i - 1, partial).subtract(renderPoint(i + 1, partial));
        }
        return d.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : d.normalize();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_AWAKEN && id <= EVENT_SONG && level().isClientSide()) {
            LeviathanEffects.handler().leviathanEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 6, this::animate));
    }

    private PlayState animate(AnimationState<ThalassineLeviathan> s) {
        State st = state();
        RawAnimation anim = st == State.DORMANT ? SLEEP_ANIM : moored() ? MOORED_ANIM : SWIM_ANIM;
        return s.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
