package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.ImpactSink;
import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.Scorch;
import com.cosmicbreach.guardian.ArenaRules;
import com.cosmicbreach.guardian.GuardianBossBar;
import com.cosmicbreach.guardian.GuardianFights;
import com.cosmicbreach.guardian.GuardianHealth;
import com.cosmicbreach.guardian.GuardianPart;
import com.cosmicbreach.guardian.Participants;
import com.cosmicbreach.guardian.Telegraphs;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.status.Rift;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.structure.sanctum.SanctumThroneBlock;
import com.cosmicbreach.structure.sanctum.Sanctums;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.weather.EclipseSurge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Hollow Heliarch (GDD 7.3): the Choir's regent, hollowed out by the thing it tried to seal. A floating ring of six
 * armored plates round a burning core, with two giant gauntleted hands, fought on the Sanctum floor. This entity is the
 * core (its box is the core, the one part that takes full damage) and the fight's controller; its hands, plates and
 * tendrils are {@link GuardianPart}s it moves each tick to where {@link HeliarchPose} puts them.
 *
 * <ul>
 *   <li><b>Intro</b> (10 s): the sky eclipses, the plates rise out of the Breach as debris and assemble round the core as
 *       the bar fills; the Heliarch speaks.</li>
 *   <li><b>Regent</b> (100 to 60%): the core hides in the closed halo except while it attacks (Sunderfall, Corona Sweep,
 *       Solar Lance, and Halo Shed every 30 s with the core exposed at x1.25). The Sunderfall parry feeds the Break gauge.</li>
 *   <li><b>Hollowing</b> (100 ticks, invulnerable): the plates slam into the mid ring as monoliths with three pips.</li>
 *   <li><b>Hollow</b> (60 to 20%): an eclipse for a core, four void tendrils, the Eclipse Beam (cover behind the
 *       monoliths), the Gravity Inversion and its Star Seeds, and Nova at 40%.</li>
 *   <li><b>Collapse</b> (20 to 0%): the arena falls away segment by segment, and Solar Rain.</li>
 *   <li><b>Death</b>: the light comes back, a Reliquary for each participant, the Starfall's voice, and on a world's first
 *       kill the seal over the Breach.</li>
 * </ul>
 * Never saved: a server stop ends the fight and the arena is put right ({@link HeliarchData}).
 */
public class HollowHeliarch extends Mob implements Enemy, GeoEntity, ParryableAttacker, ImpactSink, GuardianPart.Owner,
        GuardianFights.Fight, com.cosmicbreach.voice.boss.VoicedBoss {
    public enum State { INTRO, REGENT, HOLLOWING, HOLLOW, COLLAPSE, DYING }

    public enum Action { NONE, SUNDERFALL, CORONA_SWEEP, SOLAR_LANCE, HALO_SHED, ECLIPSE_BEAM, INVERSION, NOVA, CORONA_FLARE }

    public static final int ROLE_HAND_RIGHT = 0;
    public static final int ROLE_HAND_LEFT = 1;
    public static final int ROLE_PLATE = 2;
    public static final int ROLE_TENDRIL = 8;
    public static final float HAND_SIZE = 4.0f;
    public static final float PLATE_SIZE = 3.4f;
    public static final float TENDRIL_WIDTH = 2.2f;
    public static final float TENDRIL_HEIGHT = 5.0f;
    /** Its health while it dies: just above zero, so vanilla never ends it before its death has played. */
    private static final double DYING_HEALTH = 0.01;

    public static final byte EVENT_IGNITE = 100;
    public static final byte EVENT_GLINT = 101;
    public static final byte EVENT_SLAM = 102;
    public static final byte EVENT_PARRIED = 103;
    public static final byte EVENT_BREAK = 104;
    public static final byte EVENT_SWEEP = 105;
    public static final byte EVENT_LANCE = 106;
    public static final byte EVENT_SHED_RETURN = 107;
    public static final byte EVENT_CLANG = 108;
    public static final byte EVENT_MONOLITHS = 109;
    public static final byte EVENT_NOVA_BLAST = 110;
    public static final byte EVENT_NOVA_BREAK = 111;
    public static final byte EVENT_INVERSION = 112;
    public static final byte EVENT_DEATH = 113;
    public static final byte EVENT_WITHDRAW = 114;
    public static final byte EVENT_HOLLOWING = 115;
    public static final byte EVENT_COLLAPSE = 116;
    public static final byte EVENT_BEAM = 117;
    public static final byte EVENT_HURT = 118;
    public static final byte EVENT_RELIQUARIES = 119;
    /** Plus the monolith's index: a pip lost. */
    public static final byte EVENT_PIP = 120;
    /** Plus the monolith's index: it shatters. */
    public static final byte EVENT_SHATTER = (byte) 130;
    /** Plus the tendril's index: its lash lands. */
    public static final byte EVENT_LASH = (byte) 140;
    /** Plus the tendril's index: it is cut. */
    public static final byte EVENT_CUT = (byte) 150;
    /** Plus the tendril's index: its lash is parried. */
    public static final byte EVENT_LASH_PARRIED = (byte) 160;
    public static final byte EVENT_FLARE = (byte) 170;

    private static final int FLAG_OPEN = 1;
    private static final int FLAG_SUNDER_RIGHT = 2;
    private static final int FLAG_SUNDER_PARRIED = 4;
    private static final int FLAG_LANCE_LOCKED = 8;
    private static final int FLAG_BEAM_BACK = 16;

    private static final net.minecraft.resources.ResourceLocation INVERSION_ID = CosmicBreach.id("heliarch_inversion");
    private static final net.minecraft.resources.ResourceLocation INVERSION_FALL_ID = CosmicBreach.id("heliarch_inversion_fall");

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_STATE_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_ACTION_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_ACTION_POS = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_ACTION_ANGLE = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_OPEN_SINCE = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_BREAK_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_BROKEN_ACTION = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_BROKEN_ACTION_START =
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_STUN_UNTIL = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_NOVA_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_SHIELD = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PIPS = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SILENT = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Long>[] DATA_LASH_START = new EntityDataAccessor[] {
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG)};
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Vector3f>[] DATA_LASH_END = new EntityDataAccessor[] {
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.VECTOR3),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.VECTOR3),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.VECTOR3),
            SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.VECTOR3)};
    private static final EntityDataAccessor<Long> DATA_COLLAPSE_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_SIDE = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_INVERSION_UNTIL = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_FIGHT_START = SynchedEntityData.defineId(HollowHeliarch.class, EntityDataSerializers.LONG);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ------------------------------------------------------------------ server state
    private final PhaseGauge gauge = new PhaseGauge();
    private final HeliarchPicker picker = new HeliarchPicker();
    private final ThreatTable threat = new ThreatTable();
    private final Participants participants = new Participants();
    private final NovaRules nova = new NovaRules();
    private TendrilRules.Tendril[] tendrils = new TendrilRules.Tendril[0];
    private @Nullable CollapseSchedule collapse;
    /** The Collapse's clock up to which its cracks and falls are done. */
    private long collapseDone = -1;
    private @Nullable GuardianBossBar bar;
    private @Nullable ServerBossEvent shieldBar;
    private int playersAtStart = 1;
    /** Its health, past vanilla's cap (1,200 alone, 3,360 for four): see {@link GuardianHealth}. */
    private final GuardianHealth.Pool pool = new GuardianHealth.Pool(HeliarchMoves.BASE_HEALTH);
    private long nextActionAt;
    private long nextBeamAt;
    private long nextRainAt;
    private long holdUntil = Long.MIN_VALUE;
    private @Nullable UUID target;
    private boolean pendingHollowing;
    private boolean pendingBreak;
    private boolean pendingDeath;
    private boolean inversionNext;
    private @Nullable Action forced;
    private final Set<UUID> hitThisAction = new HashSet<>();
    private final Map<Integer, Set<UUID>> plateHits = new HashMap<>();
    private final Map<UUID, Long> lastBurn = new HashMap<>();
    private final Map<UUID, long[]> lastHitBy = new HashMap<>();
    private final Set<Integer> beamTaken = new HashSet<>();
    private final int[] pips = new int[HeliarchArena.MONOLITHS];
    private final long[] lashStart = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private final Vec3[] lashEnd = new Vec3[4];
    private final Set<UUID> inverted = new HashSet<>();
    private List<ServerPlayer> fighters = List.of();
    private long lastFighterSeen;
    // (half the least long, so "now minus it" can't overflow into a negative gap)
    private long lastClang = Long.MIN_VALUE / 2;
    /** The attack whose strike is landing now (a parry reports back inside it). */
    private Action striking = Action.NONE;
    private int seedsFired;
    private @Nullable List<Vec3> rainCircles;
    private long rainLand;
    private final GuardianPart[] hands = new GuardianPart[2];
    private final GuardianPart[] plates = new GuardianPart[6];
    private final GuardianPart[] tendrilParts = new GuardianPart[4];
    private final List<BlockPos> monolithBlocks = new ArrayList<>();
    private final Set<Integer> fallenSegments = new HashSet<>();
    private boolean parryableNow;
    private double currentImpact = 10.0;
    private @Nullable Integer parryingTendril;
    private boolean ended;

    // counters, for logs and checks
    private int sunderfalls;
    private int sunderParries;
    private int sweeps;
    private int lances;
    private int flares;
    /** How long each fighter has stayed inside the Corona Flare's ring (G9c). */
    private final Map<UUID, Integer> hugTicks = new HashMap<>();
    private int sheds;
    private int beams;
    private int pipsLost;
    private int shattered;
    private int lashes;
    private int lashParries;
    private int cuts;
    private int inversions;
    private int seedsLanded;
    private int seedsBroken;
    private int volleys;
    private int segmentsFallen;
    private int playerDowns;
    private int clangs;
    private final Map<String, double[]> dealt = new HashMap<>();
    private long hollowAt = -1;
    private long novaAt = -1;
    private long collapseAt = -1;
    private long deathAt = -1;
    private int reliquaries;
    private boolean firstKillInWorld;

    public HollowHeliarch(EntityType<? extends HollowHeliarch> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 0;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GuardianHealth.Pool.MIRROR)
                .add(Attributes.ARMOR, HeliarchMoves.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, HeliarchMoves.TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) State.INTRO.ordinal());
        builder.define(DATA_STATE_START, 0L);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_ACTION_START, 0L);
        builder.define(DATA_ACTION_POS, new Vector3f());
        builder.define(DATA_ACTION_ANGLE, 0f);
        builder.define(DATA_FLAGS, 0);
        builder.define(DATA_OPEN_SINCE, Long.MIN_VALUE / 2);
        builder.define(DATA_BREAK_START, Long.MIN_VALUE);
        builder.define(DATA_BROKEN_ACTION, (byte) 0);
        builder.define(DATA_BROKEN_ACTION_START, 0L);
        builder.define(DATA_STUN_UNTIL, Long.MIN_VALUE);
        builder.define(DATA_NOVA_START, Long.MIN_VALUE);
        builder.define(DATA_SHIELD, 0f);
        builder.define(DATA_PIPS, 0);
        builder.define(DATA_SILENT, 0);
        for (int i = 0; i < 4; i++) {
            builder.define(DATA_LASH_START[i], Long.MIN_VALUE);
            builder.define(DATA_LASH_END[i], new Vector3f());
        }
        builder.define(DATA_COLLAPSE_START, Long.MIN_VALUE);
        builder.define(DATA_SIDE, (byte) -1);
        builder.define(DATA_INVERSION_UNTIL, Long.MIN_VALUE);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_FIGHT_START, 0L);
    }

    // ------------------------------------------------------------------ the summon

    /**
     * The throne's summon (GDD 7.3): {@code player} set a Dying Star Heart on it. Counts the players within 48 blocks for
     * its health, clears blocks placed round the arena, puts the arena right if an earlier fight left it broken, and
     * raises the Heliarch over the throne. False (the Heart comes back) on Peaceful or while a fight is on.
     */
    public static boolean summon(ServerLevel level, BlockPos throne, ServerPlayer player) {
        if (!AetheriaWorld.is(level)) {
            Sanctums.subtitle(player, Component.translatable("cosmicbreach.heliarch.summon.nowhere"));
            return false;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            Sanctums.subtitle(player, Component.translatable("cosmicbreach.heliarch.summon.peaceful"));
            return false;
        }
        if (Heliarchs.active(level) != null) {
            Sanctums.subtitle(player, Component.translatable("cosmicbreach.heliarch.summon.busy"));
            return false;
        }
        long now = level.getGameTime();
        HeliarchData data = HeliarchData.get(level);
        if (data.dirty()) {
            data.restore(level);
        }
        if (data.summoner() != null) {
            Heliarchs.returnHeart(level); // a fight cut short before this one: its Heart goes back first
        }
        int cleared = HeliarchArenaRules.clearPlaced(level);
        int players = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5)
                    <= HeliarchMoves.COUNT_RADIUS * HeliarchMoves.COUNT_RADIUS) {
                players++;
            }
        }
        HollowHeliarch h = new HollowHeliarch(HeliarchRegistry.HOLLOW_HELIARCH.get(), level);
        Vec3 core = HeliarchArena.core(0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - core.x), player.getZ() - core.z));
        h.moveTo(core.x, core.y - HeliarchArena.CORE_SIZE / 2.0, core.z, yaw, 0f);
        h.setYHeadRot(yaw);
        h.yBodyRot = yaw;
        h.begin(level, now, Math.max(1, Math.min(GuardianHealth.MAX_PLAYERS, players)), player);
        level.addFreshEntity(h);
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch rises for {} player(s), {} health ({} placed blocks cleared)", h.playersAtStart,
                (int) h.pool.max(), cleared);
        return true;
    }

    private void begin(ServerLevel level, long now, int players, ServerPlayer summoner) {
        playersAtStart = players;
        pool.reset(GuardianHealth.scaled(HeliarchMoves.BASE_HEALTH, players));
        super.setHealth(pool.mirror());
        java.util.Arrays.fill(pips, 0);
        syncPips();
        setState(State.INTRO, now);
        entityData.set(DATA_FIGHT_START, now);
        entityData.set(DATA_SIDE, (byte) Sanctums.layout(level).side());
        lastFighterSeen = now;
        target = summoner.getUUID();
        participants.targeted(summoner.getUUID());
        HeliarchData.get(level).summoned(summoner.getUUID()); // the Heart goes back if this ends without a kill
        bar = new GuardianBossBar(Component.translatable("entity.cosmicbreach.hollow_heliarch"), BossEvent.BossBarColor.YELLOW)
                .meets(com.cosmicbreach.codex.CodexChapter.MET_HELIARCH);
        bar.setProgress(0f);
        bar.setMusic(HeliarchRegistry.MUSIC_REGENT.getId());
        GuardianFights.start(this);
        Heliarchs.started(this);
        level.playSound(null, HeliarchArena.CX, HeliarchArena.FLOOR + 2, HeliarchArena.CZ, HeliarchRegistry.ASSEMBLE.get(), SoundSource.HOSTILE,
                4.0f, 1.0f);
    }

    // ------------------------------------------------------------------ the arena

    @Override
    public Entity guardian() {
        return this;
    }

    @Override
    public boolean inArena(Vec3 point) {
        return HeliarchArena.radiusOf(point.x, point.z) <= HeliarchMoves.NO_PLACE_RADIUS && point.y >= HeliarchArena.FIGHT_BOTTOM
                && point.y <= HeliarchArena.FIGHT_TOP;
    }

    @Override
    public void blockPlaced(BlockPos pos) {
        if (level() instanceof ServerLevel server && !server.getBlockState(pos).is(HeliarchRegistry.MONOLITH.get())) {
            server.destroyBlock(pos, true);
        }
    }

    /** True while the fight is on (not after the death's last moment). */
    public boolean fighting() {
        return !isRemoved() && !ended;
    }

    /** The players fighting it now: alive, in survival or adventure, over the arena. */
    public List<ServerPlayer> fighters() {
        return fighters;
    }

    public static boolean fairGame(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private List<ServerPlayer> findFighters(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (fairGame(p) && HeliarchArena.inFight(p.getX(), p.getY(), p.getZ())) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everyone near enough to hear it and see its effects (in and round the Sanctum). */
    private List<ServerPlayer> audience(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (HeliarchArena.radiusOf(p.getX(), p.getZ()) <= 110) {
                out.add(p);
            }
        }
        return out;
    }

    /** True if {@code source} is a projectile fired from too far away: it burns up. */
    public boolean burnsUp(DamageSource source) {
        return ArenaRules.burnsUp(source, HeliarchArena.CENTRE, HeliarchMoves.PROJECTILE_RANGE);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            HeliarchEffects.handler().tick(this);
            return;
        }
        ServerLevel server = (ServerLevel) level();
        long now = server.getGameTime();
        if (!AetheriaWorld.is(server)) {
            end(server, false);
            discard();
            return;
        }
        fighters = findFighters(server);
        if (!fighters.isEmpty() || now < holdUntil) {
            lastFighterSeen = now;
        }
        holdCore(now);
        State s = state();
        switch (s) {
            case INTRO -> introTick(server, now);
            case REGENT -> regentTick(server, now);
            case HOLLOWING -> hollowingTick(server, now);
            case HOLLOW, COLLAPSE -> hollowTick(server, now);
            case DYING -> dyingTick(server, now);
        }
        if (isRemoved()) {
            return;
        }
        if (state() != State.DYING && now - fightStart() == HeliarchMoves.ENRAGE_AFTER) {
            com.cosmicbreach.voice.boss.BossVoices.event(this, "soft_enrage");
        }
        if (state() != State.DYING && now - lastFighterSeen > HeliarchMoves.EMPTY_TICKS) {
            withdraw(server, now);
            return;
        }
        partsTick(server, now);
        inversionTick(now);
        barTick(server, now);
    }

    /** Keeps the entity (the core's box) where the pose puts the core. */
    private void holdCore(long now) {
        Vec3 c = HeliarchPose.core(poseInput(), now);
        double y = c.y - HeliarchArena.CORE_SIZE / 2.0;
        if (Math.abs(getX() - c.x) > 1e-3 || Math.abs(getY() - y) > 1e-3 || Math.abs(getZ() - c.z) > 1e-3) {
            setPos(c.x, y, c.z);
        }
    }

    private void introTick(ServerLevel server, long now) {
        long t = now - stateStart();
        if (t == 20 - com.cosmicbreach.voice.boss.BossVoices.LEAD) {
            // the opener, its first word on tick 20
            com.cosmicbreach.voice.boss.BossVoices.fire(this, "fight_start");
        }
        if (t == 40) {
            server.broadcastEntityEvent(this, EVENT_IGNITE);
            server.playSound(null, getX(), getY(), getZ(), HeliarchRegistry.IGNITE.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
        }
        if (t == HeliarchPose.INTRO_DEBRIS) {
            server.playSound(null, getX(), getY(), getZ(), HeliarchRegistry.SHED_OUT.get(), SoundSource.HOSTILE, 3.0f, 0.7f);
        }
        if (t == HeliarchPose.INTRO_ASSEMBLED) {
            server.playSound(null, getX(), getY(), getZ(), HeliarchRegistry.CLOSE.get(), SoundSource.HOSTILE, 4.0f, 0.8f);
        }
        if (t < HeliarchPose.INTRO_DEBRIS && t % 10 == 0) {
            for (int k = 0; k < 6; k++) {
                Vec3 d = HeliarchPose.plate(poseInput(), facing(), k, now);
                if (d != null) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, SanctumRegistry.SANCTUM_GILT.get().defaultBlockState()),
                            d.x, d.y, d.z, 6, 0.6, 0.4, 0.6, 0.05);
                }
            }
        }
        // through the intro it faces the way in (the halls' side), its hands apart and open toward whoever comes up
        turnToward(HeliarchArena.at(side() > 0 ? 180.0 : 0.0, 20.0), 3f);
        if (t >= HeliarchMoves.INTRO_TICKS) {
            setState(State.REGENT, now);
            picker.start(now);
            gauge.phase(1);
            nextActionAt = now + HeliarchMoves.OPENING_TICKS;
            CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch: phase 1, the Regent");
        }
    }

    // ------------------------------------------------------------------ phase 1: the Regent

    private void regentTick(ServerLevel server, long now) {
        chooseTarget(now);
        if (gauge.broken(now) || recovering(now)) {
            hugTicks.clear(); // under a Broken core is where a player belongs
            return;
        }
        hugTick();
        if (pendingBreak && action() == Action.NONE) {
            pendingBreak = false;
            startBreak(server, now, Action.NONE, now);
            return;
        }
        if (action() == Action.NONE) {
            if (pendingHollowing) {
                startHollowing(server, now);
                return;
            }
            turnToward(target(), 4f);
            if (now >= nextActionAt && now >= holdUntil) {
                startNextAction(server, now);
            }
        } else {
            if (action() != Action.HALO_SHED && action() != Action.CORONA_SWEEP) {
                turnToward(target(), action() == Action.SOLAR_LANCE ? 2f : 4f);
            }
            actionTick(server, now);
        }
    }

    /** True while it rises again after a Break (no attacks for those 20 ticks). */
    private boolean recovering(long now) {
        long b = breakStart();
        return b != Long.MIN_VALUE && now >= b && now < b + HeliarchMoves.BREAK_TICKS + 20;
    }

    private void startNextAction(ServerLevel server, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null) {
            return;
        }
        Action a;
        if (forced != null) {
            a = forced;
            forced = null;
            picker.force(toAttack(a), now);
        } else {
            int inBand = 0;
            for (ServerPlayer f : fighters) {
                if (CoronaSweep.inBand(f.getX(), f.getZ())) {
                    inBand++;
                }
            }
            double d = Math.hypot(p.getX() - HeliarchArena.CX, p.getZ() - HeliarchArena.CZ);
            HeliarchPicker.Attack attack = picker.next(new HeliarchPicker.Context(fighters.size(), inBand, d, hugged()), now,
                    random::nextDouble);
            if (attack == null) {
                return;
            }
            a = switch (attack) {
                case SUNDERFALL -> Action.SUNDERFALL;
                case CORONA_SWEEP -> Action.CORONA_SWEEP;
                case SOLAR_LANCE -> Action.SOLAR_LANCE;
                case HALO_SHED -> Action.HALO_SHED;
                case CORONA_FLARE -> Action.CORONA_FLARE;
            };
        }
        startAction(server, a, now, p);
    }

    /** One more tick of each fighter's hug: counting while inside the Flare's ring, back to zero outside. */
    private void hugTick() {
        Map<UUID, Integer> next = new HashMap<>();
        for (ServerPlayer f : fighters) {
            int t = CoronaFlare.hug(hugTicks.getOrDefault(f.getUUID(), 0), CoronaFlare.inside(f.getX(), f.getZ()));
            if (t > 0) {
                next.put(f.getUUID(), t);
            }
        }
        hugTicks.clear();
        hugTicks.putAll(next);
    }

    /** True if anyone has hugged the core long enough to call the Flare. */
    private boolean hugged() {
        for (int t : hugTicks.values()) {
            if (CoronaFlare.hugged(t)) {
                return true;
            }
        }
        return false;
    }

    private static HeliarchPicker.Attack toAttack(Action a) {
        return switch (a) {
            case CORONA_SWEEP -> HeliarchPicker.Attack.CORONA_SWEEP;
            case SOLAR_LANCE -> HeliarchPicker.Attack.SOLAR_LANCE;
            case HALO_SHED -> HeliarchPicker.Attack.HALO_SHED;
            case CORONA_FLARE -> HeliarchPicker.Attack.CORONA_FLARE;
            default -> HeliarchPicker.Attack.SUNDERFALL;
        };
    }

    /** Starts {@code a} at {@code now} against {@code p}. */
    public void startAction(ServerLevel server, Action a, long now, ServerPlayer p) {
        hitThisAction.clear();
        plateHits.clear();
        setFlag(FLAG_SUNDER_PARRIED, false);
        setFlag(FLAG_LANCE_LOCKED, false);
        setOpen(true, now);
        Vec3 core = core(now);
        switch (a) {
            case SUNDERFALL -> {
                Vec3 ring = floorUnder(p.position());
                Vec3 r = HeliarchPose.right(facing());
                setFlag(FLAG_SUNDER_RIGHT, ring.subtract(core).dot(r) >= 0);
                setActionPos(ring);
                sunderfalls++;
                playAt(core, HeliarchRegistry.HAND_RISE.get(), 3.0f, 1.0f);
            }
            case CORONA_SWEEP -> {
                entityData.set(DATA_ACTION_ANGLE, (float) facing());
                sweeps++;
                playAt(HeliarchArena.CENTRE.add(0, 1, 0), HeliarchRegistry.SWEEP_TELL.get(), 4.0f, 1.0f);
            }
            case SOLAR_LANCE -> {
                setActionPos(p.position().add(0, 1.0, 0));
                entityData.set(DATA_TARGET, p.getId());
                lances++;
                playAt(core, HeliarchRegistry.LANCE_TRACK.get(), 3.0f, 1.0f);
            }
            case HALO_SHED -> {
                entityData.set(DATA_ACTION_ANGLE, (float) (HeliarchPose.crownAngle(0, now) % 360.0));
                sheds++;
                playAt(core, HeliarchRegistry.SHED_FLASH.get(), 4.0f, 1.0f);
            }
            case CORONA_FLARE -> {
                flares++;
                hugTicks.clear();
                playAt(core, HeliarchRegistry.FLARE_TELL.get(), 4.0f, 1.0f);
            }
            default -> {
            }
        }
        setAction(a, now);
    }

    private void actionTick(ServerLevel server, long now) {
        long t = now - actionStart();
        switch (action()) {
            case SUNDERFALL -> {
                if (t == HeliarchMoves.SUNDER_GLINT) {
                    server.broadcastEntityEvent(this, EVENT_GLINT);
                    playAt(actionPos().add(0, HeliarchPose.HAND_RISE, 0), HeliarchRegistry.GLINT.get(), 3.0f, 1.0f);
                }
                if (t == HeliarchMoves.SUNDER_TELL) {
                    sunderLands(server, now);
                }
                if (t >= HeliarchPose.sunderLength(hasFlag(FLAG_SUNDER_PARRIED))) {
                    endAction(now);
                }
            }
            case CORONA_SWEEP -> {
                long w = t - HeliarchMoves.SWEEP_TELL;
                if (w == 0) {
                    server.broadcastEntityEvent(this, EVENT_SWEEP);
                    playAt(HeliarchArena.CENTRE.add(0, 1, 0), HeliarchRegistry.SWEEP.get(), 4.0f, 1.0f);
                }
                if (w >= 0 && w <= HeliarchMoves.SWEEP_TICKS) {
                    for (ServerPlayer p : fighters) {
                        double feet = p.getY() - HeliarchArena.FLOOR;
                        if (!hitThisAction.contains(p.getUUID())
                                && CoronaSweep.strikes(p.getX(), p.getZ(), feet, actionAngle(), w - 1, w)) {
                            hitThisAction.add(p.getUUID());
                            strikePlayer(p, source(HeliarchRegistry.SOLAR), HeliarchMoves.SWEEP_DAMAGE, HeliarchMoves.SWEEP_IMPACT, false);
                            tally("corona sweep");
                        }
                    }
                }
                if (w >= HeliarchMoves.SWEEP_TICKS + 12) {
                    endAction(now);
                }
            }
            case SOLAR_LANCE -> {
                Entity e = server.getEntity(entityData.get(DATA_TARGET));
                if (t < HeliarchMoves.LANCE_TRACK && e instanceof ServerPlayer p && p.isAlive()) {
                    setActionPos(p.position().add(0, 1.0, 0));
                }
                if (t == HeliarchMoves.LANCE_TRACK) {
                    setFlag(FLAG_LANCE_LOCKED, true);
                    playAt(core(now), HeliarchRegistry.LANCE_LOCK.get(), 3.0f, 1.0f);
                }
                int fire = HeliarchMoves.LANCE_TRACK + HeliarchMoves.LANCE_LOCK;
                if (t == fire) {
                    server.broadcastEntityEvent(this, EVENT_LANCE);
                    playAt(core(now), HeliarchRegistry.LANCE_FIRE.get(), 4.0f, 1.0f);
                }
                if (t >= fire && t < fire + HeliarchMoves.LANCE_BURN) {
                    Vec3[] line = lanceLine(now);
                    for (ServerPlayer p : fighters) {
                        if (!hitThisAction.contains(p.getUUID()) && Telegraphs.onLine(p.getBoundingBox(), line[0], line[1], HeliarchMoves.LANCE_RADIUS)) {
                            hitThisAction.add(p.getUUID());
                            if (strikePlayer(p, source(HeliarchRegistry.SOLAR), HeliarchMoves.LANCE_DAMAGE, HeliarchMoves.LANCE_IMPACT, false)) {
                                p.addEffect(new MobEffectInstance(GearRegistry.SCORCH, Scorch.TICKS), this);
                            }
                            tally("solar lance");
                        }
                    }
                }
                if (t >= fire + HeliarchMoves.LANCE_BURN + 10) {
                    endAction(now);
                }
            }
            case HALO_SHED -> {
                if (t == HaloShed.FLASH) {
                    playAt(core(now), HeliarchRegistry.SHED_OUT.get(), 4.0f, 1.0f);
                }
                if (t == HaloShed.RETURN_START) {
                    server.broadcastEntityEvent(this, EVENT_SHED_RETURN);
                    playAt(HeliarchArena.CENTRE.add(0, 2, 0), HeliarchRegistry.SHED_RETURN.get(), 4.0f, 1.0f);
                }
                if (HaloShed.returning(t) || t == HaloShed.RETURN_END) {
                    HeliarchPose.Input in = poseInput();
                    for (int k = 0; k < 6; k++) {
                        Vec3 a = HeliarchPose.plate(in, facing(), k, now - 1);
                        Vec3 b = HeliarchPose.plate(in, facing(), k, now);
                        if (a == null || b == null) {
                            continue;
                        }
                        Set<UUID> hit = plateHits.computeIfAbsent(k, x -> new HashSet<>());
                        for (ServerPlayer p : fighters) {
                            if (!hit.contains(p.getUUID()) && HaloShed.strikes(p.getBoundingBox(), a, b)) {
                                hit.add(p.getUUID());
                                strikePlayer(p, source(HeliarchRegistry.PLATE), HeliarchMoves.SHED_DAMAGE, HeliarchMoves.SHED_IMPACT, false);
                                tally("halo shed");
                            }
                        }
                    }
                }
                if (t >= HaloShed.TOTAL) {
                    endAction(now);
                }
            }
            case CORONA_FLARE -> {
                if (t == HeliarchMoves.FLARE_TELL) {
                    flareBurns(server, now);
                }
                if (t >= HeliarchMoves.FLARE_TELL + HeliarchMoves.FLARE_REST) {
                    endAction(now);
                }
            }
            default -> endAction(now);
        }
    }

    /** The Flare: everyone inside the ring burns for 12 and is thrown outward (a dash's i-frames slip it). */
    private void flareBurns(ServerLevel server, long now) {
        server.broadcastEntityEvent(this, EVENT_FLARE);
        playAt(core(now), HeliarchRegistry.FLARE.get(), 5.0f, 1.0f);
        boolean threw = false;
        for (ServerPlayer p : fighters) {
            if (!CoronaFlare.strikes(p.getX(), p.getZ(), p.getY() - HeliarchArena.FLOOR)) {
                continue;
            }
            if (strikePlayer(p, source(HeliarchRegistry.SOLAR), HeliarchMoves.FLARE_DAMAGE, HeliarchMoves.FLARE_IMPACT, false)) {
                // vanilla's knockback pushes away from the direction it's given
                Vec3 out = CoronaFlare.outward(p.getX(), p.getZ());
                p.knockback(HeliarchMoves.FLARE_KNOCKBACK, -out.x, -out.z);
                p.hurtMarked = true;
                tally("corona flare");
                threw = true;
            }
        }
        if (threw) {
            com.cosmicbreach.voice.boss.BossVoices.event(this, "corona_flare");
        }
    }

    private void sunderLands(ServerLevel server, long now) {
        Vec3 ring = actionPos();
        server.broadcastEntityEvent(this, EVENT_SLAM);
        playAt(ring, HeliarchRegistry.SLAM.get(), 4.0f, 1.0f);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, SanctumRegistry.SANCTUM_IVORY.get().defaultBlockState()),
                ring.x, ring.y + 0.2, ring.z, 40, 2.0, 0.1, 2.0, 0.2);
        for (ServerPlayer p : fighters) {
            if (Telegraphs.inCircle(p.getBoundingBox(), ring, HeliarchMoves.SUNDER_RADIUS)) {
                if (strikePlayer(p, source(HeliarchRegistry.HAND), HeliarchMoves.SUNDER_DAMAGE, HeliarchMoves.SUNDER_IMPACT, true)) {
                    Vec3 away = p.position().subtract(ring).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? HeliarchPose.forward(facing()) : away.normalize();
                    p.push(away.x * 0.6, 0.35, away.z * 0.6);
                    p.hurtMarked = true;
                    tally("sunderfall");
                }
            }
        }
    }

    private void endAction(long now) {
        Action was = action();
        setAction(Action.NONE, now);
        setOpen(false, now);
        entityData.set(DATA_TARGET, -1);
        nextActionAt = now + HeliarchMoves.GAP_TICKS;
        if (was == Action.HALO_SHED || was == Action.CORONA_SWEEP) {
            nextActionAt += 10;
        }
        playAt(core(now), HeliarchRegistry.CLOSE.get(), 1.6f, 1.0f);
    }

    /** The Solar Lance's line now: from the core through its aim point, to where it meets stone. */
    public Vec3[] lanceLine(long now) {
        return lanceLine(level(), core(now), actionPos(), this);
    }

    /** The lance from {@code core} through {@code aim}, {@link HeliarchMoves#LANCE_LENGTH} long, stopped by blocks. */
    public static Vec3[] lanceLine(Level level, Vec3 core, Vec3 aim, @Nullable Entity self) {
        Vec3 d = aim.subtract(core);
        if (d.lengthSqr() < 1e-6) {
            d = new Vec3(0, -1, 0);
        }
        Vec3 far = core.add(d.normalize().scale(HeliarchMoves.LANCE_LENGTH));
        var hit = level.clip(new ClipContext(core, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                self == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(self)));
        return new Vec3[] {core, hit.getType() == HitResult.Type.MISS ? far : hit.getLocation()};
    }

    /** The floor point under {@code at}, kept on the disc. */
    private static Vec3 floorUnder(Vec3 at) {
        double r = HeliarchArena.radiusOf(at.x, at.z);
        Vec3 flat = new Vec3(at.x, HeliarchArena.FLOOR, at.z);
        if (r > 27.0) {
            Vec3 d = flat.subtract(HeliarchArena.CENTRE).normalize();
            flat = HeliarchArena.CENTRE.add(d.scale(27.0));
        }
        return flat;
    }

    // ------------------------------------------------------------------ the Break

    private void startBreak(ServerLevel server, long now, Action was, long wasStart) {
        entityData.set(DATA_BREAK_START, now);
        entityData.set(DATA_BROKEN_ACTION, (byte) was.ordinal());
        entityData.set(DATA_BROKEN_ACTION_START, wasStart);
        setAction(Action.NONE, now);
        setOpen(true, now);
        server.broadcastEntityEvent(this, EVENT_BREAK);
        playAt(core(now), HeliarchRegistry.BREAK.get(), 4.0f, 1.0f);
        com.cosmicbreach.voice.boss.BossVoices.event(this, "break");
        nextActionAt = now + HeliarchMoves.BREAK_TICKS + 30;
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch Breaks (break {} of the fight)", gauge.breaks());
    }

    /** The gauge filled: a Break (after a Halo Shed if one is out; the plates come home first). */
    private void onGaugeFilled(long now) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Action a = action();
        if (a == Action.HALO_SHED || a == Action.NOVA || state() == State.HOLLOWING) {
            pendingBreak = true;
            return;
        }
        startBreak(server, now, a, actionStart());
    }

    // ------------------------------------------------------------------ the Hollowing

    private void startHollowing(ServerLevel server, long now) {
        pendingHollowing = false;
        hollowAt = now;
        setAction(Action.NONE, now);
        setOpen(true, now);
        entityData.set(DATA_BREAK_START, Long.MIN_VALUE);
        gauge.endBreak();
        setState(State.HOLLOWING, now);
        removeParts(hands);
        com.cosmicbreach.voice.boss.BossVoices.fire(this, "hp_threshold:60");
        server.broadcastEntityEvent(this, EVENT_HOLLOWING);
        playAt(core(now), HeliarchRegistry.TEAR.get(), 4.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollowing at {} health", (int) pool.left());
    }

    private void hollowingTick(ServerLevel server, long now) {
        long t = now - stateStart();
        turnToward(target(), 2f);
        if (t == HeliarchMoves.HOLLOWING_SLAM) {
            raiseMonoliths(server, now);
            removeParts(plates);
        }
        if (t == 60) {
            removeParts(hands);
        }
        if (t >= HeliarchMoves.HOLLOWING_TICKS) {
            setState(State.HOLLOW, now);
            gauge.phase(2);
            bar.setMusic(HeliarchRegistry.MUSIC_HOLLOW.getId());
            tendrils = TendrilRules.create(now);
            nextBeamAt = now + HeliarchMoves.BEAM_OPENING;
            CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch: phase 2, the Hollow");
        }
    }

    /** The plates stand up as monoliths: twelve blocks each, three pips each; anyone in the way is pushed clear. */
    private void raiseMonoliths(ServerLevel server, long now) {
        HeliarchData data = HeliarchData.get(server);
        MonolithBlock block = HeliarchRegistry.MONOLITH.get();
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            List<int[]> cols = m.columns();
            for (int c = 0; c < cols.size(); c++) {
                for (int row = 0; row < HeliarchArena.MONOLITH_HEIGHT; row++) {
                    BlockPos p = new BlockPos(cols.get(c)[0], (int) HeliarchArena.FLOOR + row, cols.get(c)[1]);
                    BlockState was = server.getBlockState(p);
                    if (!was.isAir() && !was.is(block)) {
                        continue;
                    }
                    server.setBlock(p, block.cell(m.alongX(), c, row), Block.UPDATE_ALL);
                    data.placed(p);
                    monolithBlocks.add(p);
                }
            }
            pips[m.index()] = HeliarchArena.PIPS;
            double[] r = m.rect();
            AABB box = new AABB(r[0], HeliarchArena.FLOOR, r[1], r[2], HeliarchArena.FLOOR + HeliarchArena.MONOLITH_HEIGHT, r[3]);
            for (ServerPlayer p : fighters) {
                if (p.getBoundingBox().intersects(box)) {
                    Vec3 out = HeliarchArena.dir(m.angle());
                    Vec3 to = m.middle().add(out.scale(2.2));
                    p.teleportTo(to.x, HeliarchArena.FLOOR, to.z);
                    p.push(out.x * 0.5, 0.3, out.z * 0.5);
                    p.hurtMarked = true;
                }
            }
            Vec3 mid = m.middle();
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, SanctumRegistry.SANCTUM_IVORY.get().defaultBlockState()),
                    mid.x, mid.y + 0.3, mid.z, 50, 1.5, 0.2, 1.5, 0.25);
        }
        syncPips();
        server.broadcastEntityEvent(this, EVENT_MONOLITHS);
        playAt(HeliarchArena.CENTRE.add(0, 2, 0), HeliarchRegistry.MONOLITH_SLAM.get(), 5.0f, 1.0f);
    }

    /** A beam takes a pip from monolith {@code k}; at none it shatters. */
    private void losePip(ServerLevel server, int k) {
        if (pips[k] <= 0) {
            return;
        }
        pips[k]--;
        pipsLost++;
        syncPips();
        Vec3 mid = HeliarchPose.monolithMiddle(k);
        if (pips[k] > 0) {
            server.broadcastEntityEvent(this, (byte) (EVENT_PIP + k));
            playAt(mid, HeliarchRegistry.PIP.get(), 3.0f, 1.0f);
            return;
        }
        shatterMonolith(server, k, true);
    }

    private void shatterMonolith(ServerLevel server, int k, boolean loud) {
        pips[k] = 0;
        syncPips();
        shattered++;
        HeliarchArena.Monolith m = HeliarchArena.monoliths().get(k);
        for (int[] c : m.columns()) {
            for (int row = 0; row < HeliarchArena.MONOLITH_HEIGHT; row++) {
                BlockPos p = new BlockPos(c[0], (int) HeliarchArena.FLOOR + row, c[1]);
                if (server.getBlockState(p).is(HeliarchRegistry.MONOLITH.get())) {
                    server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (loud) {
            Vec3 mid = HeliarchPose.monolithMiddle(k);
            server.broadcastEntityEvent(this, (byte) (EVENT_SHATTER + k));
            playAt(mid, HeliarchRegistry.MONOLITH_SHATTER.get(), 4.0f, 1.0f);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HeliarchRegistry.MONOLITH.get().defaultBlockState()),
                    mid.x, mid.y, mid.z, 80, 1.2, 1.6, 1.2, 0.3);
        }
    }

    private void syncPips() {
        int packed = 0;
        for (int k = 0; k < pips.length; k++) {
            packed |= (pips[k] & 3) << (2 * k);
        }
        entityData.set(DATA_PIPS, packed);
    }

    /** Integrity pips of each monolith (0 when shattered or not raised). */
    public int[] pips() {
        int packed = entityData.get(DATA_PIPS);
        int[] out = new int[HeliarchArena.MONOLITHS];
        for (int k = 0; k < out.length; k++) {
            out[k] = (packed >> (2 * k)) & 3;
        }
        return out;
    }

    // ------------------------------------------------------------------ phase 2: the Hollow, and the Collapse

    private void hollowTick(ServerLevel server, long now) {
        chooseTarget(now);
        boolean stunned = nova.stunned(now);
        boolean broken = gauge.broken(now) || recovering(now);
        if (!stunned && !broken) {
            tendrilsTick(server, now);
        } else {
            cancelLashes();
        }
        if (state() == State.COLLAPSE) {
            collapseTick(server, now);
        }
        if (stunned || broken) {
            return;
        }
        if (pendingBreak && action() == Action.NONE) {
            pendingBreak = false;
            startBreak(server, now, Action.NONE, now);
            return;
        }
        if (pendingDeath) {
            return;
        }
        Action a = action();
        if (a == Action.NONE) {
            turnToward(target(), 3f);
            if (state() == State.HOLLOW && nova.broken() && pool.left() <= pool.max() * HeliarchMoves.COLLAPSE_AT + 1e-3) {
                startCollapse(server, now);
                return;
            }
            if (state() == State.HOLLOW && nova.due(now, pool.fraction())) {
                startNova(server, now);
                return;
            }
            if (now >= holdUntil && (forced == Action.ECLIPSE_BEAM || forced == null && now >= nextBeamAt)) {
                forced = null;
                startBeam(server, now);
            } else if (now >= holdUntil && forced == Action.INVERSION) {
                forced = null;
                startInversion(server, now);
            }
            return;
        }
        switch (a) {
            case ECLIPSE_BEAM -> beamTick(server, now);
            case INVERSION -> inversionActionTick(server, now);
            case NOVA -> novaTick(server, now);
            default -> endAction(now);
        }
    }

    // ------------------------------------------------------------------ tendrils

    private void tendrilsTick(ServerLevel server, long now) {
        int silent = 0;
        for (TendrilRules.Tendril td : tendrils) {
            int i = td.index();
            if (td.silenced(now)) {
                silent |= 1 << i;
            }
            Vec3 anchor = HeliarchArena.tendrilAnchor(i);
            if (lashStart[i] != Long.MIN_VALUE) {
                long a = now - lashStart[i];
                if (a == HeliarchMoves.LASH_TELL) {
                    lashLands(server, i, anchor);
                }
                if (a > HeliarchMoves.LASH_TELL + HeliarchMoves.LASH_REST + 22) {
                    setLash(i, Long.MIN_VALUE, anchor);
                }
                continue;
            }
            if (!td.lashDue(now) || now < holdUntil) {
                continue;
            }
            ServerPlayer mark = null;
            double best = (HeliarchMoves.LASH_LENGTH + 1.5) * (HeliarchMoves.LASH_LENGTH + 1.5);
            for (ServerPlayer p : fighters) {
                double d = Math.pow(p.getX() - anchor.x, 2) + Math.pow(p.getZ() - anchor.z, 2);
                if (d < best && p.getY() < HeliarchArena.FLOOR + 6) {
                    best = d;
                    mark = p;
                }
            }
            if (mark == null) {
                continue;
            }
            td.lashed(now);
            lashes++;
            setLash(i, now, TendrilRules.lashEnd(anchor, mark.position()));
            playAt(anchor, HeliarchRegistry.LASH_TELL.get(), 2.5f, 1.0f);
        }
        entityData.set(DATA_SILENT, silent);
    }

    private void lashLands(ServerLevel server, int i, Vec3 anchor) {
        Vec3 end = lashEnd[i];
        server.broadcastEntityEvent(this, (byte) (EVENT_LASH + i));
        playAt(anchor.add(end).scale(0.5), HeliarchRegistry.LASH.get(), 3.0f, 1.0f);
        for (ServerPlayer p : fighters) {
            if (TendrilRules.onLash(p.getX(), p.getZ(), p.getY() - HeliarchArena.FLOOR, anchor, end)) {
                parryingTendril = i;
                boolean hit;
                try {
                    hit = strikePlayer(p, source(HeliarchRegistry.VOID), HeliarchMoves.LASH_DAMAGE, HeliarchMoves.LASH_IMPACT, true);
                } finally {
                    parryingTendril = null;
                }
                if (hit) {
                    Rift.addStack(p);
                    tally("tendril lash");
                }
            }
        }
    }

    private void setLash(int i, long start, Vec3 end) {
        lashStart[i] = start;
        lashEnd[i] = end;
        entityData.set(DATA_LASH_START[i], start);
        entityData.set(DATA_LASH_END[i], new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    private void cancelLashes() {
        for (int i = 0; i < 4; i++) {
            if (lashStart[i] != Long.MIN_VALUE) {
                setLash(i, Long.MIN_VALUE, HeliarchArena.tendrilAnchor(i));
            }
        }
    }

    public long lashStart(int i) {
        return entityData.get(DATA_LASH_START[i]);
    }

    public Vec3 lashEnd(int i) {
        Vector3f v = entityData.get(DATA_LASH_END[i]);
        return new Vec3(v.x, v.y, v.z);
    }

    /** True while tendril {@code i} is cut and sunk into its crack. */
    public boolean tendrilSilent(int i) {
        return (entityData.get(DATA_SILENT) & (1 << i)) != 0;
    }

    /** Tendril {@code i}'s middle for its hit part: standing over its crack, or lying along its lash. */
    public Vec3 tendrilMiddle(int i, long now) {
        Vec3 anchor = HeliarchArena.tendrilAnchor(i);
        long s = lashStart(i);
        if (s != Long.MIN_VALUE) {
            long a = now - s;
            if (a >= HeliarchMoves.LASH_TELL && a < HeliarchMoves.LASH_TELL + HeliarchMoves.LASH_REST + 8) {
                Vec3 end = lashEnd(i);
                return anchor.add(end.subtract(anchor).scale(0.45)).add(0, 0.2, 0);
            }
        }
        return anchor.add(0, 0.2, 0);
    }

    // ------------------------------------------------------------------ the Eclipse Beam and the inversion

    private void startBeam(ServerLevel server, long now) {
        ServerPlayer p = targetPlayer();
        double aim = p != null ? HeliarchArena.angleOf(p.getX(), p.getZ()) : random.nextDouble() * 360.0;
        int dir = random.nextBoolean() ? 1 : -1;
        double start = EclipseCover.startFor(aim, dir, random.nextDouble() * 50.0 - 25.0);
        entityData.set(DATA_ACTION_ANGLE, (float) start);
        setFlag(FLAG_BEAM_BACK, dir < 0);
        beamTaken.clear();
        lastBurn.clear();
        beams++;
        setAction(Action.ECLIPSE_BEAM, now);
        nextBeamAt = now + HeliarchMoves.BEAM_COOLDOWN;
        inversionNext = random.nextDouble() < HeliarchMoves.INVERSION_CHANCE;
        playAt(core(now), HeliarchRegistry.BEAM_CHARGE.get(), 5.0f, 1.0f);
    }

    public int beamDir() {
        return hasFlag(FLAG_BEAM_BACK) ? -1 : 1;
    }

    private void beamTick(ServerLevel server, long now) {
        long a = now - actionStart();
        long t = a - HeliarchMoves.BEAM_TELL;
        if (t == 0) {
            server.broadcastEntityEvent(this, EVENT_BEAM);
            playAt(core(now), HeliarchRegistry.BEAM.get(), 5.0f, 1.0f);
        }
        if (t >= 0 && t <= HeliarchMoves.BEAM_SWEEP) {
            double start = actionAngle();
            int dir = beamDir();
            setYRot(HeliarchArena.yawOf(EclipseCover.beamAngle(start, dir, t)));
            yBodyRot = getYRot();
            setYHeadRot(getYRot());
            for (int k : EclipseCover.monolithsReached(start, dir, t - 1, t)) {
                if (beamTaken.add(k)) {
                    losePip(server, k);
                }
            }
            List<EclipseCover.Blocker> blockers = EclipseCover.blockers(pips, pillarsDown());
            for (ServerPlayer p : fighters) {
                if (EclipseCover.beamHits(p.getX(), p.getZ(), start, dir, t, blockers)) {
                    long last = lastBurn.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
                    if (now - last >= HeliarchMoves.BEAM_EVERY) {
                        lastBurn.put(p.getUUID(), now);
                        if (strikePlayer(p, source(HeliarchRegistry.ECLIPSE), HeliarchMoves.BEAM_DAMAGE, 4.0, false)) {
                            p.addEffect(new MobEffectInstance(GearRegistry.SCORCH, Scorch.TICKS), this);
                        }
                        tally("eclipse beam");
                    }
                }
            }
        }
        if (t >= HeliarchMoves.BEAM_SWEEP + 10) {
            setAction(Action.NONE, now);
            if (inversionNext) {
                startInversion(server, now);
            }
        }
    }

    private void startInversion(ServerLevel server, long now) {
        inversions++;
        seedsFired = 0;
        setAction(Action.INVERSION, now);
        entityData.set(DATA_INVERSION_UNTIL, now + HeliarchMoves.INVERSION_TICKS);
        server.broadcastEntityEvent(this, EVENT_INVERSION);
        playAt(HeliarchArena.CENTRE.add(0, 3, 0), HeliarchRegistry.INVERSION.get(), 5.0f, 1.0f);
    }

    private void inversionActionTick(ServerLevel server, long now) {
        long a = now - actionStart();
        if (a >= 10 && seedsFired < HeliarchMoves.SEEDS && (a - 10) % HeliarchMoves.SEED_EVERY == 0 && !fighters.isEmpty()) {
            ServerPlayer mark = fighters.get(random.nextInt(fighters.size()));
            Vec3 from = core(now).add(HeliarchPose.forward(facing()).scale(1.6)).add(0, 0.5, 0);
            StarSeed.fire(server, this, from, mark);
            seedsFired++;
        }
        if (a >= HeliarchMoves.INVERSION_TICKS) {
            setAction(Action.NONE, now);
        }
    }

    /** Players in the fight fall at 0.3x through the inversion (their own gravity, synced to them). */
    private void inversionTick(long now) {
        boolean on = now < entityData.get(DATA_INVERSION_UNTIL) && state() != State.DYING;
        Set<UUID> want = new HashSet<>();
        if (on) {
            for (ServerPlayer p : fighters) {
                want.add(p.getUUID());
                setGravity(p, true);
            }
        }
        if (level() instanceof ServerLevel server) {
            for (UUID id : new ArrayList<>(inverted)) {
                if (!want.contains(id)) {
                    ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
                    if (p != null) {
                        setGravity(p, false);
                    }
                    inverted.remove(id);
                }
            }
        }
        inverted.addAll(want);
    }

    private static void setGravity(ServerPlayer p, boolean low) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        AttributeInstance fall = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (g == null) {
            return;
        }
        if (low) {
            if (!g.hasModifier(INVERSION_ID)) {
                g.addOrUpdateTransientModifier(new AttributeModifier(INVERSION_ID, HeliarchMoves.INVERSION_GRAVITY - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (fall != null && !fall.hasModifier(INVERSION_FALL_ID)) {
                fall.addOrUpdateTransientModifier(new AttributeModifier(INVERSION_FALL_ID, 12.0, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            if (g.hasModifier(INVERSION_ID)) {
                g.removeModifier(INVERSION_ID);
            }
            if (fall != null && fall.hasModifier(INVERSION_FALL_ID)) {
                fall.removeModifier(INVERSION_FALL_ID);
            }
        }
    }

    /** True while the inversion's low gravity holds. */
    public boolean inverted(long now) {
        return now < entityData.get(DATA_INVERSION_UNTIL);
    }

    public long inversionUntil() {
        return entityData.get(DATA_INVERSION_UNTIL);
    }

    void seedLanded() {
        seedsLanded++;
    }

    void seedBroken() {
        seedsBroken++;
    }

    // ------------------------------------------------------------------ Nova

    private void startNova(ServerLevel server, long now) {
        boolean first = novaAt < 0;
        novaAt = first ? now : novaAt;
        nova.begin(now, playersAtStart);
        // it gathers its roots: every cut tendril rises again, so its shield can always be struck through them
        for (TendrilRules.Tendril td : tendrils) {
            td.regrow();
        }
        entityData.set(DATA_NOVA_START, now);
        entityData.set(DATA_SHIELD, 1f);
        setAction(Action.NOVA, now);
        cancelLashes();
        if (first) {
            com.cosmicbreach.voice.boss.BossVoices.fire(this, "hp_threshold:40");
        } else {
            // a return after a detonation: the channel's spoken warning
            com.cosmicbreach.voice.boss.BossVoices.event(this, "nova_return");
        }
        shieldBar = new ServerBossEvent(Component.translatable("cosmicbreach.heliarch.shield", HeliarchMoves.NOVA_CHANNEL / 20),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Nova: a Corona Shield of {}", (int) nova.shieldMax());
    }

    private void novaTick(ServerLevel server, long now) {
        long a = now - actionStart();
        if (a == HeliarchMoves.NOVA_CHANNEL - 100) {
            playAt(core(now), HeliarchRegistry.NOVA_CHARGE.get(), 6.0f, 1.0f);
        }
        entityData.set(DATA_SHIELD, (float) nova.shieldFraction());
        NovaRules.Outcome o = nova.tick(now);
        if (o == NovaRules.Outcome.BROKEN) {
            entityData.set(DATA_STUN_UNTIL, nova.stunnedUntil());
            entityData.set(DATA_NOVA_START, Long.MIN_VALUE);
            endShield();
            setAction(Action.NONE, now);
            server.broadcastEntityEvent(this, EVENT_NOVA_BREAK);
            playAt(core(now), HeliarchRegistry.NOVA_BREAK.get(), 6.0f, 1.0f);
            nextBeamAt = Math.max(nextBeamAt, now + HeliarchMoves.NOVA_STUN + 60);
            com.cosmicbreach.voice.boss.BossVoices.event(this, "nova_broken");
            CosmicBreach.LOGGER.debug("[cosmicbreach] Nova broken: stunned {} ticks, the heart exposed", HeliarchMoves.NOVA_STUN);
        } else if (o == NovaRules.Outcome.DETONATED) {
            entityData.set(DATA_NOVA_START, Long.MIN_VALUE);
            endShield();
            setAction(Action.NONE, now);
            server.broadcastEntityEvent(this, EVENT_NOVA_BLAST);
            playAt(core(now), HeliarchRegistry.NOVA_BLAST.get(), 8.0f, 1.0f);
            List<EclipseCover.Blocker> blockers = EclipseCover.blockers(pips, pillarsDown());
            for (ServerPlayer p : fighters) {
                boolean covered = EclipseCover.covered(p.getX(), p.getZ(), blockers);
                strikePlayer(p, source(HeliarchRegistry.ECLIPSE), NovaRules.detonation(covered), 20.0, false);
                tally(covered ? "nova (covered)" : "nova (open)");
            }
            nextBeamAt = Math.max(nextBeamAt, now + 80);
            com.cosmicbreach.voice.boss.BossVoices.event(this, "nova_detonated");
            CosmicBreach.LOGGER.debug("[cosmicbreach] Nova detonated; it returns in {} ticks", HeliarchMoves.NOVA_RETURN);
        } else if (shieldBar != null) {
            shieldBar.setProgress((float) nova.shieldFraction());
            long left = Math.max(0, HeliarchMoves.NOVA_CHANNEL - a);
            if (a % 20 == 0) {
                shieldBar.setName(Component.translatable("cosmicbreach.heliarch.shield", (left + 19) / 20));
            }
        }
    }

    private void endShield() {
        if (shieldBar != null) {
            shieldBar.removeAllPlayers();
            shieldBar = null;
        }
    }

    public long novaStart() {
        return entityData.get(DATA_NOVA_START);
    }

    public float shield() {
        return entityData.get(DATA_SHIELD);
    }

    public long stunUntil() {
        return entityData.get(DATA_STUN_UNTIL);
    }

    // ------------------------------------------------------------------ the Collapse

    private void startCollapse(ServerLevel server, long now) {
        collapseAt = now;
        setState(State.COLLAPSE, now);
        entityData.set(DATA_COLLAPSE_START, now);
        collapse = new CollapseSchedule(entityData.get(DATA_SIDE));
        bar.setMusic(HeliarchRegistry.MUSIC_COLLAPSE.getId());
        bar.setColor(BossEvent.BossBarColor.RED);
        com.cosmicbreach.voice.boss.BossVoices.fire(this, "hp_threshold:20");
        server.broadcastEntityEvent(this, EVENT_COLLAPSE);
        playAt(HeliarchArena.CENTRE.add(0, 2, 0), HeliarchRegistry.ECLIPSE_DRONE.get(), 6.0f, 1.0f);
        nextRainAt = now + 60;
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Collapse at {} health", (int) pool.left());
    }

    private void collapseTick(ServerLevel server, long now) {
        if (collapse == null) {
            collapse = new CollapseSchedule(entityData.get(DATA_SIDE));
        }
        long t = now - collapseStart();
        // everything due since the last tick (the clock can jump: a debug skip, a lagging server)
        long from = Math.min(collapseDone, t - 1);
        for (CollapseSchedule.Crack c : collapse.cracking(from, t)) {
            Vec3 mid = segmentMiddle(c.ring(), c.segment());
            playAt(mid, HeliarchRegistry.CRACK.get(), 4.0f, 1.0f);
        }
        for (CollapseSchedule.Crack c : collapse.falling(from, t)) {
            dropSegment(server, c.ring(), c.segment());
        }
        collapseDone = t;
        if (now >= nextRainAt && !pendingDeath) {
            nextRainAt = now + HeliarchMoves.RAIN_EVERY;
            List<Vec3> ps = new ArrayList<>();
            for (ServerPlayer p : fighters) {
                if (p.getY() > HeliarchArena.FLOOR - 1 && p.getY() < HeliarchArena.FLOOR + 4) {
                    ps.add(p.position());
                }
            }
            rainCircles = SolarRain.pick(ps, collapse.radiusLeft(t), random::nextDouble, (x, z) -> standingFloor(server, x, z));
            rainLand = now + HeliarchMoves.RAIN_TELL;
            volleys++;
            HeliarchNet.rain(audience(server), rainCircles, rainLand);
            playAt(HeliarchArena.CENTRE.add(0, 8, 0), HeliarchRegistry.RAIN_TELL.get(), 4.0f, 1.0f);
        }
        if (rainCircles != null && now == rainLand) {
            for (Vec3 c : rainCircles) {
                playAt(c, HeliarchRegistry.RAIN.get(), 2.5f, 0.9f + random.nextFloat() * 0.2f);
                for (ServerPlayer p : fighters) {
                    if (SolarRain.inCircle(p.getX(), p.getZ(), p.getY() - HeliarchArena.FLOOR, c)) {
                        strikePlayer(p, source(HeliarchRegistry.SOLAR), HeliarchMoves.RAIN_DAMAGE, 8.0, false);
                        tally("solar rain");
                    }
                }
            }
            rainCircles = null;
        }
    }

    private static boolean standingFloor(ServerLevel level, double x, double z) {
        BlockPos p = BlockPos.containing(x, HeliarchArena.FLOOR - 1, z);
        return !level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }

    private static Vec3 segmentMiddle(SanctumArena.Ring ring, int segment) {
        double r = (ring.inner + ring.outer) / 2.0;
        return HeliarchArena.at(22.5 + 45.0 * segment, r).add(0, 0.5, 0);
    }

    /** A segment falls: clients keep its blocks to draw them falling, the blocks go (kept for the restore). */
    private void dropSegment(ServerLevel server, SanctumArena.Ring ring, int segment) {
        HeliarchNet.fall(audience(server), ring.ordinal(), segment, false);
        HeliarchData data = HeliarchData.get(server);
        for (BlockPos p : SanctumArena.segmentBlocks(ring, segment)) {
            BlockState s = server.getBlockState(p);
            if (!s.isAir()) {
                data.removed(p, s);
                server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (ring == SanctumArena.Ring.OUTER) {
            // the pillar in the middle of this outer segment goes down with its floor (never left hanging over nothing)
            for (BlockPos p : SanctumArena.pillarBlocks(Math.floorMod(segment, SanctumArena.PILLARS))) {
                BlockState s = server.getBlockState(p);
                if (!s.isAir()) {
                    data.removed(p, s);
                    server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (ring == SanctumArena.Ring.MID) {
            for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
                int[] c = m.columns().get(1);
                if (SanctumLayout.segment(c[0], c[1]) == segment && pips[m.index()] > 0) {
                    shatterMonolith(server, m.index(), false);
                }
            }
        }
        fallenSegments.add(ring.ordinal() * 8 + segment);
        segmentsFallen++;
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Collapse: {} segment {} falls", ring.name().toLowerCase(Locale.ROOT), segment);
        playAt(segmentMiddle(ring, segment), HeliarchRegistry.FALL.get(), 5.0f, 1.0f);
    }

    /** A bit for each Choir Pillar that has fallen with its segment in the Collapse (both sides read the world). */
    public int pillarsDown() {
        int down = 0;
        for (int i = 0; i < SanctumArena.PILLARS; i++) {
            if (level().getBlockState(SanctumArena.pillarBase(i).above()).isAir()) {
                down |= 1 << i;
            }
        }
        return down;
    }

    public long collapseStart() {
        return entityData.get(DATA_COLLAPSE_START);
    }

    /** The halls' side (-1 north, +1 south), for the Collapse's order. */
    public int side() {
        return entityData.get(DATA_SIDE);
    }

    // ------------------------------------------------------------------ death, withdrawal, rewards

    private void startDying(ServerLevel server, long now) {
        HeliarchData.get(server).clearSummoner(); // a kill: the Heart is spent
        pendingDeath = false;
        deathAt = now;
        setAction(Action.NONE, now);
        cancelLashes();
        endShield();
        entityData.set(DATA_INVERSION_UNTIL, Long.MIN_VALUE);
        setState(State.DYING, now);
        removeParts(tendrilParts);
        removeParts(hands);
        removeParts(plates);
        rainCircles = null;
        com.cosmicbreach.voice.boss.BossVoices.fire(this, "boss_kill");
        server.broadcastEntityEvent(this, EVENT_DEATH);
        playAt(core(now), HeliarchRegistry.DEATH.get(), 6.0f, 1.0f);
        bar.setMusic(null);
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch falls after {} s", (now - fightStart()) / 20);
    }

    private void dyingTick(ServerLevel server, long now) {
        long t = now - stateStart();
        if (t == 20) {
            for (int k = 0; k < HeliarchArena.MONOLITHS; k++) {
                if (pips[k] > 0) {
                    shatterMonolith(server, k, true);
                }
            }
        }
        if (t == 50) {
            restoreArena(server);
        }
        if (t == 100) {
            placeReliquaries(server);
        }
        if (t == 120) {
            if (!EclipseSurge.heliarchFallen(server.getServer())) {
                firstKillInWorld = true;
                EclipseSurge.onHeliarchFirstDeath(server.getServer());
                for (ServerPlayer p : server.players()) {
                    HeliarchNet.seal(p, true, true);
                }
                playAt(HeliarchArena.CENTRE.add(0, 30, 0), HeliarchRegistry.SEAL.get(), 8.0f, 1.0f);
                CosmicBreach.LOGGER.debug("[cosmicbreach] the first Heliarch of this world has fallen: the Breach is sealed");
            }
        }
        if (t == 130) {
            for (UUID id : participants.all()) {
                ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    Echo.say(p, EchoLine.SEALED);
                }
            }
        }
        if (t >= HeliarchMoves.DYING_TICKS) {
            end(server, true);
            discard();
        }
    }

    /** A Reliquary for each participant on the dais round the throne, facing out. */
    private void placeReliquaries(ServerLevel server) {
        List<BlockPos> spots = new ArrayList<>();
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 3.5 : 6.0;
            int n = ring == 0 ? 4 : 8;
            for (int i = 0; i < n; i++) {
                Vec3 at = HeliarchArena.at(45.0 + 360.0 * i / n + (ring == 0 ? 0 : 22.5), r);
                spots.add(BlockPos.containing(at.x, HeliarchArena.FLOOR, at.z));
            }
        }
        int k = 0;
        for (UUID id : participants.all()) {
            while (k < spots.size() && !server.getBlockState(spots.get(k)).isAir()) {
                k++;
            }
            if (k >= spots.size()) {
                break;
            }
            BlockPos spot = spots.get(k++);
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
            String name = p != null ? p.getGameProfile().getName() : id.toString();
            double a = HeliarchArena.angleOf(spot.getX() + 0.5, spot.getZ() + 0.5);
            Direction facing = Direction.fromYRot(HeliarchArena.yawOf(a) + 180.0);
            if (ReliquaryBlockEntity.place(server, spot, facing, id, name)) {
                reliquaries++;
                server.sendParticles(ParticleTypes.END_ROD, spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
            }
        }
        server.broadcastEntityEvent(this, EVENT_RELIQUARIES);
        playAt(HeliarchArena.CENTRE.add(0, 1, 0), HeliarchRegistry.RELIQUARY_APPEAR.get(), 4.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} Reliquar{} rose on the dais", reliquaries, reliquaries == 1 ? "y" : "ies");
    }

    /** No one left to fight: the regent withdraws into the Breach, the arena is put right and the Heart goes back. */
    private void withdraw(ServerLevel server, long now) {
        server.broadcastEntityEvent(this, EVENT_WITHDRAW);
        playAt(core(now), HeliarchRegistry.CLOSE.get(), 4.0f, 0.6f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] the Hollow Heliarch withdraws: no one left in the arena");
        end(server, false);
        discard();
    }

    /** Ends the fight: the arena put right, the bars gone, the throne empty, gravity back. */
    private void end(ServerLevel server, boolean killed) {
        if (ended) {
            return;
        }
        ended = true;
        for (int k = 0; k < HeliarchArena.MONOLITHS; k++) {
            if (pips[k] > 0) {
                shatterMonolith(server, k, false);
            }
        }
        restoreArena(server);
        for (UUID id : inverted) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                setGravity(p, false);
            }
        }
        inverted.clear();
        endShield();
        if (bar != null) {
            bar.remove();
        }
        removeParts(hands);
        removeParts(plates);
        removeParts(tendrilParts);
        BlockState throne = server.getBlockState(SanctumArena.THRONE);
        if (throne.is(SanctumRegistry.SANCTUM_THRONE.get()) && throne.getValue(SanctumThroneBlock.HEART)) {
            server.setBlock(SanctumArena.THRONE, throne.setValue(SanctumThroneBlock.HEART, false), Block.UPDATE_ALL);
        }
        GuardianFights.end(this);
        Heliarchs.ended(this, killed);
        if (!killed) {
            Heliarchs.returnHeart(server); // a failed attempt never strands anyone
        }
    }

    private void restoreArena(ServerLevel server) {
        HeliarchData data = HeliarchData.get(server);
        for (int code : fallenSegments) {
            HeliarchNet.fall(audience(server), code / 8, code % 8, true);
        }
        fallenSegments.clear();
        int n = data.restore(server);
        if (n > 0) {
            CosmicBreach.LOGGER.debug("[cosmicbreach] the arena rises again: {} blocks put back", n);
        }
    }

    // ------------------------------------------------------------------ targeting and facing

    private void chooseTarget(long now) {
        if (Math.floorMod(now - stateStart(), HeliarchMoves.THREAT_CHECK) != 0 && target != null && validTarget(target)) {
            return;
        }
        List<ThreatTable.Candidate> c = new ArrayList<>();
        for (ServerPlayer p : fighters) {
            c.add(new ThreatTable.Candidate(p.getUUID(), p.distanceToSqr(HeliarchArena.CX, HeliarchArena.FLOOR, HeliarchArena.CZ)));
        }
        target = threat.check(c).orElse(null);
        if (target != null) {
            participants.targeted(target);
        }
    }

    private boolean validTarget(UUID id) {
        for (ServerPlayer p : fighters) {
            if (p.getUUID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable ServerPlayer targetPlayer() {
        if (target == null) {
            return fighters.isEmpty() ? null : fighters.get(0);
        }
        for (ServerPlayer p : fighters) {
            if (p.getUUID().equals(target)) {
                return p;
            }
        }
        return fighters.isEmpty() ? null : fighters.get(0);
    }

    private @Nullable Vec3 target() {
        ServerPlayer p = targetPlayer();
        return p == null ? null : p.position();
    }

    private void turnToward(@Nullable Vec3 at, float maxDeg) {
        if (at == null) {
            return;
        }
        float want = HeliarchArena.yawOf(HeliarchArena.angleOf(at.x, at.z));
        if (HeliarchArena.radiusOf(at.x, at.z) < 1.0) {
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), want, maxDeg);
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    /** Its facing as a compass angle. */
    public double facing() {
        return HeliarchArena.compassOf(yBodyRot);
    }

    public @Nullable UUID currentTarget() {
        return target;
    }

    public ThreatTable threat() {
        return threat;
    }

    // ------------------------------------------------------------------ parts

    private void partsTick(ServerLevel server, long now) {
        HeliarchPose.Input in = poseInput();
        double f = facing();
        for (int i = 0; i < 2; i++) {
            Vec3 at = HeliarchPose.hand(in, f, i == 0, now);
            hands[i] = keepPart(server, hands[i], at, i == 0 ? ROLE_HAND_RIGHT : ROLE_HAND_LEFT, HAND_SIZE, HAND_SIZE);
        }
        State s = state();
        for (int k = 0; k < 6; k++) {
            Vec3 at = s == State.INTRO && now - stateStart() < HeliarchPose.INTRO_DEBRIS ? null : HeliarchPose.plate(in, f, k, now);
            plates[k] = keepPart(server, plates[k], at, ROLE_PLATE + k, PLATE_SIZE, PLATE_SIZE);
        }
        boolean hollow = s == State.HOLLOW || s == State.COLLAPSE;
        for (int i = 0; i < 4; i++) {
            Vec3 at = null;
            if (hollow && i < tendrils.length && !tendrils[i].silenced(now)) {
                at = tendrilMiddle(i, now).add(0, TENDRIL_HEIGHT / 2.0, 0);
            }
            tendrilParts[i] = keepPart(server, tendrilParts[i], at, ROLE_TENDRIL + i, TENDRIL_WIDTH, TENDRIL_HEIGHT);
        }
    }

    private @Nullable GuardianPart keepPart(ServerLevel server, @Nullable GuardianPart part, @Nullable Vec3 at, int role, float w, float h) {
        if (at == null) {
            if (part != null) {
                part.discard();
            }
            return null;
        }
        if (part == null || part.isRemoved()) {
            part = GuardianPart.spawn(server, this, role, w, h, at);
        }
        part.placeCentre(at);
        return part;
    }

    private static void removeParts(GuardianPart[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null) {
                parts[i].discard();
                parts[i] = null;
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
                end(server, false);
            }
            discard();
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !vulnerable() || burnsUp(source)) {
            return false;
        }
        if (duplicateHit(player, 0)) {
            return false;
        }
        long now = level().getGameTime();
        if (state() == State.REGENT && !coreOpen(now)) {
            clang(player, now);
            return false;
        }
        return applyHit(source, amount, player, 1.0, "core");
    }

    /** True while it can be hurt at all (not in the intro, the Hollowing or its death). */
    private boolean vulnerable() {
        State s = state();
        return (s == State.REGENT || s == State.HOLLOW || s == State.COLLAPSE) && !pendingDeath;
    }

    /** True while the core can be struck in phase 1: while it attacks (the halo open), in a Break, in a Halo Shed. */
    public boolean coreOpen(long now) {
        return hasFlag(FLAG_OPEN) || gauge.broken(now) || recovering(now);
    }

    private void clang(ServerPlayer player, long now) {
        clangs++;
        if (now - lastClang >= 6) {
            lastClang = now;
            if (level() instanceof ServerLevel server) {
                server.broadcastEntityEvent(this, EVENT_CLANG);
            }
            playAt(core(now), HeliarchRegistry.CLANG.get(), 1.6f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    /**
     * A hit that passed the checks: x1.5 in a Break or a Nova's stun, x1.25 while the halo is shed; during a Nova's
     * channel it goes into the Corona Shield instead. {@code share} is the part's (a hand's 0.5).
     */
    private boolean applyHit(DamageSource source, float amount, ServerPlayer player, double share, String what) {
        long now = level().getGameTime();
        if (player != null && CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
            invulnerableTime = 0; // the engine paces its own hits
        }
        if (nova.channelling()) {
            double before = nova.shield();
            nova.absorb(amount);
            entityData.set(DATA_SHIELD, (float) nova.shieldFraction());
            recordDamage(player, before - nova.shield(), "shield");
            return true;
        }
        boolean broken = gauge.broken(now) || nova.stunned(now);
        double mult = broken ? Math.max(HeliarchMoves.BREAK_TAKEN, nova.taken(now)) : share;
        if (state() == State.REGENT && action() == Action.HALO_SHED && HaloShed.exposed(now - actionStart())) {
            mult *= HeliarchMoves.SHED_EXPOSED;
        }
        double before = pool.left();
        boolean hurt = super.hurt(source, (float) (amount * mult));
        if (hurt) {
            recordDamage(player, before - pool.left(), what);
            if (level() instanceof ServerLevel server && now % 3 == 0) {
                server.broadcastEntityEvent(this, EVENT_HURT);
            }
        }
        return hurt;
    }

    private void recordDamage(@Nullable ServerPlayer player, double dealtNow, String what) {
        if (player == null || dealtNow <= 0) {
            return;
        }
        participants.dealtDamage(player.getUUID(), HeliarchArena.inFight(player.getX(), player.getY(), player.getZ()));
        threat.addDamage(player.getUUID(), dealtNow);
        dealt.computeIfAbsent(what, k -> new double[2])[0] += dealtNow;
        dealt.get(what)[1]++;
    }

    @Override
    public boolean hurtByPart(GuardianPart part, DamageSource source, float amount) {
        if (level().isClientSide() || !(source.getEntity() instanceof ServerPlayer player) || !vulnerable() || burnsUp(source)) {
            return false;
        }
        int role = part.role();
        long now = level().getGameTime();
        if (role >= ROLE_PLATE && role < ROLE_TENDRIL) {
            clang(player, now);
            return false; // plates take nothing: they block
        }
        if (role >= ROLE_TENDRIL) {
            int i = role - ROLE_TENDRIL;
            if (i >= tendrils.length) {
                return false;
            }
            if (CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
                part.invulnerableTime = 0;
            }
            if (nova.channelling()) {
                // gathering Nova, it draws through its tendrils: a blow on one drains the Corona Shield (and cuts nothing)
                return applyHit(source, amount, player, 1.0, "tendrils");
            }
            // high over the pillars, the eclipse bleeds through its roots
            applyHit(source, amount, player, HeliarchMoves.TENDRIL_SHARE, "tendrils");
            boolean cut = tendrils[i].hit(amount, now);
            participants.dealtDamage(player.getUUID(), true);
            if (cut) {
                cuts++;
                CombatStateMachine m = PlayerCombat.of(player).machine();
                m.syncFromServer(m.resonance() + HeliarchMoves.TENDRIL_RESONANCE, m.dashCharges(), m.abilityCooldown());
                if (lashStart[i] != Long.MIN_VALUE) {
                    setLash(i, Long.MIN_VALUE, HeliarchArena.tendrilAnchor(i));
                }
                if (level() instanceof ServerLevel server) {
                    server.broadcastEntityEvent(this, (byte) (EVENT_CUT + i));
                }
                playAt(HeliarchArena.tendrilAnchor(i).add(0, 2, 0), HeliarchRegistry.TENDRIL_CUT.get(), 3.0f, 1.0f);
            }
            return true;
        }
        if (duplicateHit(player, role + 1)) {
            return false;
        }
        return applyHit(source, amount, player, HeliarchMoves.HAND_DAMAGE, "hands");
    }

    /** One swing reaching the core and a hand counts once (the first it lands on). */
    private boolean duplicateHit(ServerPlayer player, int key) {
        long now = level().getGameTime();
        long[] last = lastHitBy.computeIfAbsent(player.getUUID(), id -> new long[] {Long.MIN_VALUE, -1});
        if (last[1] >= 0 && last[1] != key && now - last[0] < 5) {
            return true;
        }
        last[0] = now;
        last[1] = key;
        return false;
    }

    /**
     * Health goes into the pool ({@link GuardianHealth}) as real health, and can't pass a phase's line in one hit: 60%
     * brings the Hollowing, 40% the Nova (until one is broken), zero its death. Vanilla's synced health keeps the pool's
     * share of the mirror.
     */
    @Override
    public void setHealth(float health) {
        if (pool == null || level().isClientSide()) {
            super.setHealth(health); // (vanilla's constructor sets it before the pool exists)
            return;
        }
        double next = health;
        if (next < pool.left()) {
            next = lines(next);
        }
        pool.set(next);
        super.setHealth(pool.mirror());
    }

    /** Real health on the server (the pool's); the mirror on a client. */
    @Override
    public float getHealth() {
        return pool == null || level().isClientSide() ? super.getHealth() : (float) pool.left();
    }

    /** Where a fall in health to {@code next} stops: at a phase's line, or at its death (kept just alive while it dies). */
    private double lines(double next) {
        State s = state();
        double max = pool.max();
        if (s == State.REGENT && next <= max * HeliarchMoves.HOLLOW_AT) {
            pendingHollowing = true;
            return max * HeliarchMoves.HOLLOW_AT;
        }
        if (s == State.HOLLOW && !nova.broken() && next <= max * HeliarchMoves.NOVA_AT) {
            return max * HeliarchMoves.NOVA_AT;
        }
        if ((s == State.HOLLOW || s == State.COLLAPSE) && next <= 0.0) {
            if (!pendingDeath && level() instanceof ServerLevel server) {
                pendingDeath = true;
                startDying(server, server.getGameTime());
            }
            return DYING_HEALTH;
        }
        return Math.max(0.0, next);
    }

    /** The fight's health (zero once it dies) and its full measure. */
    public double health() {
        return deathAt >= 0 ? 0.0 : pool.left();
    }

    public double maxHealth() {
        return pool.max();
    }

    @Override
    public void takeImpact(double impact) {
        if (!vulnerable() || level().isClientSide()) {
            return;
        }
        long now = level().getGameTime();
        if (gauge.add(now, impact)) {
            onGaugeFilled(now);
        }
    }

    @Override
    public void impactOnPart(GuardianPart part, double impact) {
        if (part.role() == ROLE_HAND_RIGHT || part.role() == ROLE_HAND_LEFT || part.role() >= ROLE_TENDRIL) {
            takeImpact(impact);
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
        long now = level().getGameTime();
        if (parryingTendril != null) {
            int i = parryingTendril;
            lashParries++;
            if (level() instanceof ServerLevel server) {
                server.broadcastEntityEvent(this, (byte) (EVENT_LASH_PARRIED + i));
            }
            playAt(lashEnd[i] == null ? core(now) : lashEnd[i], HeliarchRegistry.PARRIED.get(), 3.0f, 1.2f);
            return;
        }
        // (the parry's Impact reaches the gauge first: a Break it fills has already ended the Sunderfall by now)
        if (action() == Action.SUNDERFALL || striking == Action.SUNDERFALL) {
            if (!hasFlag(FLAG_SUNDER_PARRIED)) {
                sunderParries++;
            }
            setFlag(FLAG_SUNDER_PARRIED, true);
            if (level() instanceof ServerLevel server) {
                server.broadcastEntityEvent(this, EVENT_PARRIED);
            }
            playAt(actionPos(), HeliarchRegistry.PARRIED.get(), 4.0f, 1.0f);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Sunderfall parried by {}: the gauge at {}", player.getGameProfile().getName(),
                    String.format(Locale.ROOT, "%.2f", gauge.fraction(now)));
        }
    }

    /**
     * Deals {@code base} (times the soft enrage) to {@code p}: {@code impact} toward its poise, parryable if
     * {@code parryable} (the gold attacks: Sunderfall and the Tendril Lash). True if it landed.
     */
    boolean strikePlayer(ServerPlayer p, DamageSource source, double base, double impact, boolean parryable) {
        long now = level().getGameTime();
        float amount = (float) (base * SoftEnrage.multiplier(now - fightStart()));
        currentImpact = impact;
        parryableNow = parryable;
        striking = action();
        boolean hit;
        try {
            hit = p.hurt(source, amount);
        } finally {
            parryableNow = false;
            striking = Action.NONE;
        }
        if (hit) {
            participants.targeted(p.getUUID());
        }
        return hit;
    }

    private DamageSource source(ResourceKey<DamageType> key) {
        Holder<DamageType> type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(type, this);
    }

    private final Map<String, Integer> tallies = new HashMap<>();

    private void tally(String what) {
        tallies.merge(what, 1, Integer::sum);
    }

    /** A participant fell (its voice hears deaths through its own listener, {@code BossVoices}). */
    void onPlayerDown(ServerPlayer p) {
        playerDowns++;
    }

    // ------------------------------------------------------------------ the bar

    private void barTick(ServerLevel server, long now) {
        if (bar == null) {
            return;
        }
        if (state() == State.INTRO) {
            bar.setProgress((float) Math.min(1.0, (now - stateStart()) / (double) HeliarchMoves.INTRO_TICKS));
        } else if (state() == State.DYING) {
            bar.setProgress(0f);
        } else {
            bar.setProgress((float) pool.fraction());
        }
        State s = state();
        bar.setColor(s == State.COLLAPSE ? BossEvent.BossBarColor.RED : s == State.HOLLOW || s == State.HOLLOWING
                ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
        bar.setGauge((float) gauge.fraction(now), gauge.broken(now) || nova.stunned(now));
        bar.update(server, p -> HeliarchArena.radiusOf(p.getX(), p.getZ()) <= 64 && p.getY() > HeliarchArena.FLOOR - 40
                && p.getY() < HeliarchArena.FLOOR + 60);
        if (shieldBar != null) {
            for (ServerPlayer p : bar.viewers()) {
                if (!shieldBar.getPlayers().contains(p)) {
                    shieldBar.addPlayer(p);
                }
            }
        }
    }

    public double gaugeFraction() {
        return gauge.fraction(level().getGameTime());
    }

    // ------------------------------------------------------------------ voice and sound

    private void playAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------ synced state

    public State state() {
        return State.values()[Math.floorMod(entityData.get(DATA_STATE), State.values().length)];
    }

    public long stateStart() {
        return entityData.get(DATA_STATE_START);
    }

    private void setState(State s, long now) {
        entityData.set(DATA_STATE, (byte) s.ordinal());
        entityData.set(DATA_STATE_START, now);
    }

    public Action action() {
        return Action.values()[Math.floorMod(entityData.get(DATA_ACTION), Action.values().length)];
    }

    public long actionStart() {
        return entityData.get(DATA_ACTION_START);
    }

    private void setAction(Action a, long now) {
        entityData.set(DATA_ACTION, (byte) a.ordinal());
        entityData.set(DATA_ACTION_START, now);
    }

    public Vec3 actionPos() {
        Vector3f v = entityData.get(DATA_ACTION_POS);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setActionPos(Vec3 p) {
        entityData.set(DATA_ACTION_POS, new Vector3f((float) p.x, (float) p.y, (float) p.z));
    }

    public double actionAngle() {
        return entityData.get(DATA_ACTION_ANGLE);
    }

    private boolean hasFlag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void setFlag(int f, boolean on) {
        int flags = entityData.get(DATA_FLAGS);
        int next = on ? flags | f : flags & ~f;
        if (next != flags) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    private void setOpen(boolean open, long now) {
        if (hasFlag(FLAG_OPEN) != open) {
            setFlag(FLAG_OPEN, open);
            entityData.set(DATA_OPEN_SINCE, now);
        }
    }

    public boolean lanceLocked() {
        return hasFlag(FLAG_LANCE_LOCKED);
    }

    public int lanceTargetId() {
        return entityData.get(DATA_TARGET);
    }

    public long breakStart() {
        return entityData.get(DATA_BREAK_START);
    }

    public long fightStart() {
        return entityData.get(DATA_FIGHT_START);
    }

    /** The core's middle at {@code time} (either side). */
    public Vec3 core(double time) {
        return HeliarchPose.core(poseInput(), time);
    }

    /** Everything the pose needs, from the synced state (either side). */
    public HeliarchPose.Input poseInput() {
        return new HeliarchPose.Input(state(), stateStart(), action(), actionStart(), actionPos(), (float) actionAngle(), hasFlag(FLAG_OPEN),
                entityData.get(DATA_OPEN_SINCE), breakStart(), stunUntil(), novaStart(), hasFlag(FLAG_SUNDER_RIGHT), hasFlag(FLAG_SUNDER_PARRIED),
                Action.values()[Math.floorMod(entityData.get(DATA_BROKEN_ACTION), Action.values().length)], entityData.get(DATA_BROKEN_ACTION_START));
    }

    /** True while Broken (the gauge) or stunned (a broken Nova), either side. */
    public boolean brokenNow(long now) {
        long b = breakStart();
        boolean broken = b != Long.MIN_VALUE && now >= b && now < b + HeliarchMoves.BREAK_TICKS;
        return broken || now < stunUntil();
    }

    // ------------------------------------------------------------------ debug and checks

    /** Forces the next attack (debug); it starts at the next free moment. */
    public void forceNext(Action a) {
        forced = a;
        nextActionAt = Math.min(nextActionAt, level().getGameTime());
        holdUntil = Long.MIN_VALUE;
    }

    /** No attacks for {@code ticks} (debug and looks). */
    public void holdAttacks(int ticks) {
        holdUntil = level().getGameTime() + ticks;
    }

    /** Sets health (debug), through the phase lines. */
    public void debugHealth(float health) {
        setHealth(health);
    }

    /** Adds {@code impact} to the Break gauge (debug); a full gauge Breaks it as usual. */
    public void debugGauge(double impact) {
        long now = level().getGameTime();
        if (gauge.add(now, impact)) {
            onGaugeFilled(now);
        }
    }

    public void debugBreak() {
        if (level() instanceof ServerLevel server) {
            long now = server.getGameTime();
            gauge.add(now, HeliarchMoves.BREAK_POISE);
            onGaugeFilled(now);
        }
    }

    /** Jumps straight to phase 2 (debug): health to the line, the Hollowing at once. */
    public void debugHollow() {
        if (state() == State.REGENT && level() instanceof ServerLevel server) {
            pool.set(pool.max() * HeliarchMoves.HOLLOW_AT);
            super.setHealth(pool.mirror());
            setAction(Action.NONE, server.getGameTime());
            startHollowing(server, server.getGameTime());
        }
    }

    /** Health to Nova's line (debug, phase 2). */
    public void debugNova() {
        nova.skipWait();
        if (state() == State.HOLLOW) {
            pool.set(pool.max() * HeliarchMoves.NOVA_AT);
            super.setHealth(pool.mirror());
            nextBeamAt = Long.MAX_VALUE / 2;
            if (action() != Action.NOVA && level() instanceof ServerLevel server) {
                setAction(Action.NONE, server.getGameTime());
            }
        }
    }

    /** Breaks a Nova's shield at once (debug). */
    public void debugBreakShield() {
        if (nova.channelling()) {
            nova.absorb(nova.shield() + 1);
        }
    }

    /** To the Collapse (debug): a broken Nova is taken as read. */
    public void debugCollapse() {
        if ((state() == State.HOLLOW || state() == State.REGENT) && level() instanceof ServerLevel server) {
            if (state() == State.REGENT) {
                debugHollow();
                return;
            }
            if (!nova.broken()) {
                nova.begin(server.getGameTime(), playersAtStart);
                nova.absorb(nova.shieldMax() + 1);
                nova.tick(server.getGameTime());
                entityData.set(DATA_STUN_UNTIL, Long.MIN_VALUE);
            }
            endShield();
            entityData.set(DATA_NOVA_START, Long.MIN_VALUE);
            pool.set(pool.max() * HeliarchMoves.COLLAPSE_AT);
            super.setHealth(pool.mirror());
            setAction(Action.NONE, server.getGameTime());
            startCollapse(server, server.getGameTime());
        }
    }

    /** Skips the Collapse's clock ahead by {@code ticks} (debug): segments due by then fall now. */
    public void debugCollapseAhead(int ticks) {
        if (state() == State.COLLAPSE) {
            entityData.set(DATA_COLLAPSE_START, collapseStart() - ticks);
        }
    }

    public String summary() {
        long now = level().getGameTime();
        return String.format(Locale.ROOT, "state %s, action %s, health %.1f/%.0f, gauge %.2f, breaks %d, target %s, players at start %d, "
                        + "sunderfalls %d (%d parried), sweeps %d, lances %d, flares %d, sheds %d, beams %d, pips lost %d, shattered %d, lashes %d "
                        + "(%d parried), cuts %d, inversions %d, seeds %d landed %d broken, nova %s (detonations %d), volleys %d, "
                        + "segments fallen %d, player downs %d, clangs %d, dealt %s, taken %s",
                state(), action(), health(), pool.max(), gauge.fraction(now), gauge.breaks(), target, playersAtStart, sunderfalls,
                sunderParries, sweeps, lances, flares, sheds, beams, pipsLost, shattered, lashes, lashParries, cuts, inversions, seedsLanded, seedsBroken,
                nova.broken() ? "broken" : nova.channelling() ? "channelling" : "not yet", nova.detonations(), volleys, segmentsFallen,
                playerDowns, clangs, dealtSummary(), tallies);
    }

    private String dealtSummary() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, double[]> e : dealt.entrySet()) {
            sb.append(String.format(Locale.ROOT, "%s %.0f in %d hits; ", e.getKey(), e.getValue()[0], (int) e.getValue()[1]));
        }
        return sb.toString();
    }

    public int breaks() {
        return gauge.breaks();
    }

    public int sunderParries() {
        return sunderParries;
    }

    public int sunderfalls() {
        return sunderfalls;
    }

    public int sheds() {
        return sheds;
    }

    public int sweeps() {
        return sweeps;
    }

    public int flares() {
        return flares;
    }

    public int lances() {
        return lances;
    }

    public int beams() {
        return beams;
    }

    public int pipsLost() {
        return pipsLost;
    }

    public int cuts() {
        return cuts;
    }

    public int lashes() {
        return lashes;
    }

    public int inversions() {
        return inversions;
    }

    public int seedsFired() {
        return seedsFired;
    }

    public int volleys() {
        return volleys;
    }

    public int segmentsFallen() {
        return segmentsFallen;
    }

    public int playerDowns() {
        return playerDowns;
    }

    public int clangs() {
        return clangs;
    }

    public NovaRules nova() {
        return nova;
    }

    public Set<UUID> participants() {
        return participants.all();
    }

    public int playersAtStart() {
        return playersAtStart;
    }

    public Map<String, Integer> tallies() {
        return tallies;
    }

    public long hollowAt() {
        return hollowAt;
    }

    public long novaAt() {
        return novaAt;
    }

    public long collapseAt() {
        return collapseAt;
    }

    public long deathAt() {
        return deathAt;
    }

    public int reliquaries() {
        return reliquaries;
    }

    public boolean firstKillInWorld() {
        return firstKillInWorld;
    }

    public @Nullable List<Vec3> rainCircles() {
        return rainCircles;
    }

    public long rainLand() {
        return rainLand;
    }

    public long nextBeamAt() {
        return nextBeamAt;
    }

    // ------------------------------------------------------------------ an immovable, unsaved giant

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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(HeliarchArena.CX - 32, HeliarchArena.FLOOR - 8, HeliarchArena.CZ - 32, HeliarchArena.CX + 32, HeliarchArena.FLOOR + 24,
                HeliarchArena.CZ + 32);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel server && !ended) {
            end(server, false);
        }
        super.remove(reason);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HeliarchRegistry.HURT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if ((id & 0xFF) >= 100) {
            HeliarchEffects.handler().event(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ the voice (1.1)

    @Override
    public String voiceBoss() {
        return "heliarch";
    }

    @Override
    public BlockPos voiceHome() {
        return BlockPos.containing(HeliarchArena.CENTRE);
    }

    @Override
    public List<ServerPlayer> voiceFighters() {
        return fighters;
    }

    @Override
    public int voicePlayers() {
        return playersAtStart;
    }

    @Override
    public double voiceHealth() {
        return maxHealth() <= 0 ? 0.0 : Math.max(0.0, health() / maxHealth());
    }

    /** "Breach Sealed": a party with someone who has sealed it before is a returning one. */
    @Override
    public net.minecraft.resources.ResourceLocation voiceKillAdvancement() {
        return com.cosmicbreach.codex.Codices.BREACH_SEALED;
    }

    /** The Hollowing (60), the first Nova (40) and the Collapse (20) are its own moments; 80 and 10 are plain health. */
    @Override
    public java.util.Set<Integer> voicePhaseThresholds() {
        return java.util.Set.of(60, 40, 20);
    }
}
