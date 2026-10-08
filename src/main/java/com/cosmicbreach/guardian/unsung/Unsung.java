package com.cosmicbreach.guardian.unsung;

import static com.cosmicbreach.guardian.unsung.UnsungMoves.BEAT;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.guardian.ArenaRules;
import com.cosmicbreach.guardian.BossTargeting;
import com.cosmicbreach.guardian.BreakGauge;
import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.GuardianBossBar;
import com.cosmicbreach.guardian.GuardianFights;
import com.cosmicbreach.guardian.GuardianHealth;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.LairGuardian;
import com.cosmicbreach.guardian.Participants;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.Telegraphs;
import com.cosmicbreach.guardian.unsung.UnsungSong.Cue;
import com.cosmicbreach.world.LayerAttunement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Unsung (Unsung design v1; GDD 7.2): the third guardian, three porcelain masks of the lost Choir circling the
 * dais of the Silent Nave. This entity is the choir itself, invisible at the dais's centre: it keeps the lair, the
 * song, the three {@link UnsungMask}s (each with its own 250 health, one boss bar for all), the shared Break gauge,
 * the attacks and Harmonize. Everything happens on the Vesper clock; the song's arithmetic is {@link UnsungSong}.
 *
 * <ul>
 *   <li><b>Waking.</b> A player not attuned to the Breach Sanctum stepping onto the choir floor or opening the hymnal,
 *       or anyone's Guardian Echo on the altar, once the lair is rested. An 8 to 15 beat intro: the lichen windows dim
 *       one by one, the masks lift out of the dark one by one, each singing its first note, and the bar fills. The fight
 *       starts on an 8-beat line of Vesper.</li>
 *   <li><b>The song.</b> Only the singing mask can be hurt; the song passes Alto, Tenor, Bass every 8 beats on the
 *       line. Its attacks are its line's notes: the Alto's Homing Notes, the Tenor's Sweeping Wave, the Bass's Ground
 *       Ripples and gold Bass Drop.</li>
 *   <li><b>Harmonize</b> every 48 beats: 8 beats ahead the song stops rotating, circles of silence light white and the
 *       masks rise together; on the downbeat everyone outside a lit circle takes 18 and is Silenced.</li>
 *   <li><b>Breaks.</b> Impact on the masks (hits and parries) fills one gauge of 400 over 30 s; a full gauge drops all
 *       the living masks to the floor for 100 ticks, all open to hits at x1.5. A Break cancels a Harmonize.</li>
 *   <li><b>Broken masks.</b> A mask at zero breaks: its line leaves the music, the survivors take its part at half
 *       rate. The last one breaking ends the fight: silence, then one soft chord, then each participant's rewards.</li>
 * </ul>
 * Nobody on the choir floor for 30 s resets it. Fights are not saved: a reload leaves it asleep.
 */
public class Unsung extends Entity implements LairGuardian, GuardianFights.Fight, com.cosmicbreach.voice.boss.VoicedBoss {
    public enum State { DORMANT, INTRO, FIGHT, DYING }

    public static final byte EVENT_AWAKEN = 100;
    public static final byte EVENT_BREAK = 101;
    public static final byte EVENT_BREAK_END = 102;
    public static final byte EVENT_WARNING = 103;
    public static final byte EVENT_HARMONIZE = 104;
    public static final byte EVENT_RIPPLE = 105;
    public static final byte EVENT_DROP = 106;
    public static final byte EVENT_WAVE = 107;
    public static final byte EVENT_DEATH = 108;
    public static final byte EVENT_CHORD = 109;

    private static final int FLAG_BREAK = 1;
    private static final int FLAG_WARNING = 2;
    private static final int BLEND = BEAT;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_STATE_START = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FIGHT_START = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> DATA_SINGER = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_NEXT = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_BROKEN = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_BREAK_START = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_WARNING_START = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_LIT = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_WAVE = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_RIPPLE = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> DATA_RIPPLE_LAND = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_DROP = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> DATA_DROP_LAND = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_DROP_VOICE = SynchedEntityData.defineId(Unsung.class, EntityDataSerializers.BYTE);

    // ------------------------------------------------------------------ server state
    private @Nullable ChoirArena arena;
    private @Nullable BlockPos altarPos;
    private State state = State.DORMANT;
    private long fightStart;
    /** A Guardian Echo woke this fight (its voice greets a returning party). */
    private boolean wokenByEcho;
    private final Map<Voice, UnsungMask> masks = new EnumMap<>(Voice.class);
    private final Map<Voice, Vec3> blendFrom = new EnumMap<>(Voice.class);
    private final Map<Voice, Long> blendStart = new EnumMap<>(Voice.class);
    private final Map<Voice, Vec3> lastFace = new EnumMap<>(Voice.class);
    private final Map<Voice, Vec3> fellAt = new EnumMap<>(Voice.class);
    private @Nullable Voice singer;
    private @Nullable Voice lastSung;
    private @Nullable Voice preparedSinger;
    private @Nullable Voice forcedNext;
    private long preparedLine = Long.MIN_VALUE;
    private final int[] turnsSung = new int[3];
    private final BreakGauge gauge = new BreakGauge(UnsungMoves.BREAK_CAPACITY, UnsungMoves.GAUGE_WINDOW);
    private long breakUntil = Long.MIN_VALUE;
    private final BossTargeting targeting = new BossTargeting();
    private final Participants participants = new Participants();
    private @Nullable UUID target;
    private int playersAtStart = 1;
    private long lastPlayerOnFloor;
    private @Nullable GuardianBossBar bar;
    private final Map<BlockPos, Long> crumbling = new LinkedHashMap<>();
    private final TreeMap<Long, List<Scheduled>> schedule = new TreeMap<>();
    private @Nullable Wave wave;
    private @Nullable Ripple ripple;
    private @Nullable Drop drop;
    private final List<SongNote> notes = new ArrayList<>();
    private boolean warningActive;
    private long warningStart;
    private int[] lit = new int[0];
    private final List<BlockPos> windows = new ArrayList<>();
    private int windowsDimmed;
    private boolean relightOnLoad;
    private long holdUntil = Long.MIN_VALUE;
    private List<ServerPlayer> onFloor = List.of();
    private long onFloorTick = Long.MIN_VALUE;
    // what a fight did, for logs and tests
    private int breaks;
    private int harmonizes;
    private int harmonizeHits;
    private int parries;
    private int glances;
    private int notesBroken;
    private int noteHits;
    private final List<Voice> brokenOrder = new ArrayList<>();
    private final Map<Voice, Integer> attacks = new EnumMap<>(Voice.class);
    private final Map<UnsungSong.Part, Integer> parts = new EnumMap<>(UnsungSong.Part.class);
    /** Every landing this fight, for tests: {tick, kind} (0 note, 1 wave, 2 ripple, 3 drop, 4 Harmonize). */
    private final List<long[]> landings = new ArrayList<>();

    private record Scheduled(Cue cue, Voice performer, long line) {
    }

    private static final class Wave {
        final UnsungMask by;
        final long release;
        final Set<UUID> hit = new HashSet<>();

        Wave(UnsungMask by, long release) {
            this.by = by;
            this.release = release;
        }
    }

    private record Ripple(UnsungMask by, Vec3 centre, long land) {
    }

    private static final class Drop {
        final UnsungMask by;
        final long rise;
        final @Nullable UUID target;
        final Vec3 from;
        Vec3 point;
        boolean parried;
        boolean landed;

        Drop(UnsungMask by, long rise, @Nullable UUID target, Vec3 from, Vec3 point) {
            this.by = by;
            this.rise = rise;
            this.target = target;
            this.from = from;
            this.point = point;
        }

        long land() {
            return rise + UnsungMoves.DROP_TELL;
        }
    }

    public Unsung(EntityType<? extends Unsung> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** A sleeping choir at a lair (the lair's spawner): it rests on the dais of the apse centred at {@code arenaCentre}. */
    public static @Nullable Unsung spawnDormant(ServerLevel level, BlockPos arenaCentre, BlockPos altar) {
        Unsung u = new Unsung(UnsungRegistry.UNSUNG.get(), level);
        u.bindLair(arenaCentre, altar);
        u.moveTo(arenaCentre.getX(), arenaCentre.getY() + 1.0, arenaCentre.getZ(), 0f, 0f);
        level.addFreshEntity(u);
        return u;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_STATE_START, 0L);
        builder.define(DATA_FIGHT_START, 0L);
        builder.define(DATA_HOME, BlockPos.ZERO);
        builder.define(DATA_SINGER, (byte) -1);
        builder.define(DATA_NEXT, (byte) -1);
        builder.define(DATA_BROKEN, (byte) 0);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_BREAK_START, 0L);
        builder.define(DATA_WARNING_START, 0L);
        builder.define(DATA_LIT, (byte) 0);
        builder.define(DATA_WAVE, Long.MIN_VALUE / 2);
        builder.define(DATA_RIPPLE, new Vector3f());
        builder.define(DATA_RIPPLE_LAND, Long.MIN_VALUE / 2);
        builder.define(DATA_DROP, new Vector3f());
        builder.define(DATA_DROP_LAND, Long.MIN_VALUE / 2);
        builder.define(DATA_DROP_VOICE, (byte) -1);
    }

    // ------------------------------------------------------------------ the lair

    @Override
    public void bindLair(BlockPos arenaCentre, BlockPos altar) {
        this.arena = ChoirArena.at(arenaCentre);
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

    public @Nullable ChoirArena arena() {
        if (arena == null && level().isClientSide()) {
            BlockPos home = entityData.get(DATA_HOME);
            return home.equals(BlockPos.ZERO) ? null : ChoirArena.at(home);
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
        return arena != null && arena.distance(point.x, point.z) <= ChoirArena.FLOOR_RADIUS + 0.5 && point.y >= arena.floorY() - 1.0
                && point.y <= arena.floorY() + ChoirArena.INSIDE_HEIGHT + 4.0;
    }

    /** A block placed on the choir floor during the fight: a light goes out at once, anything else crumbles soon. */
    @Override
    public void blockPlaced(BlockPos pos) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        boolean light = server.getBlockState(pos).getLightEmission(server, pos) > 0;
        crumbling.putIfAbsent(pos.immutable(), server.getGameTime() + (light ? 1 : ArenaRules.CRUMBLE_TICKS));
    }

    /** A player who can fight: alive, in survival or adventure, and not on Peaceful. */
    public static boolean fairGame(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** Fair players on the choir floor (worked out once a tick). */
    public List<ServerPlayer> fightersOnFloor() {
        if (arena == null || !(level() instanceof ServerLevel server)) {
            return List.of();
        }
        long now = server.getGameTime();
        if (now != onFloorTick) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : server.players()) {
                if (fairGame(p) && arena.onFloor(p.position())) {
                    out.add(p);
                }
            }
            onFloor = out;
            onFloorTick = now;
        }
        return onFloor;
    }

    /** True while the fight is on (the intro, the fight, the death). */
    public boolean fighting() {
        return state != State.DORMANT;
    }

    // ------------------------------------------------------------------ waking

    @Override
    public void awaken(@Nullable ServerPlayer by, boolean echo) {
        if (state != State.DORMANT || arena == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        wokenByEcho = echo;
        ensureMasks(server, now);
        playersAtStart = Math.max(1, Math.min(GuardianHealth.MAX_PLAYERS, fightersOnFloor().size()));
        double max = GuardianHealth.scaled(UnsungMoves.MASK_HEALTH, playersAtStart);
        for (UnsungMask m : masks.values()) {
            m.restore(max);
            m.setMode(UnsungMask.Mode.REST, now);
        }
        clearFight();
        // the opener's first word is a beat after the Alto's lift and waits for the wake sounds to be over: if the lift would come
        // sooner, the whole intro takes one more turn
        fightStart = UnsungSong.fightStartAfterWake(now, com.cosmicbreach.voice.boss.BossVoiceSounds.clearTicks("unsung/awaken")
                + com.cosmicbreach.voice.boss.VoiceDirector.QUIET_MARGIN);
        entityData.set(DATA_FIGHT_START, fightStart);
        setState(State.INTRO, now);
        lastPlayerOnFloor = now;
        findWindows(server);
        extinguish(server);
        bar = new GuardianBossBar(getDisplayName(), BossEvent.BossBarColor.PURPLE).meets(com.cosmicbreach.codex.CodexChapter.MET_UNSUNG);
        bar.setProgress(0f);
        bar.setMusic(UnsungRegistry.MUSIC_MARKER);
        GuardianFights.start(this);
        server.broadcastEntityEvent(this, EVENT_AWAKEN);
        playAt(arena.centre().add(0, 3, 0), UnsungRegistry.AWAKEN.get(), 3.0f, 1.0f);
        playAt(arena.centre().add(0, 3, 0), UnsungRegistry.DIM.get(), 2.0f, 1.0f);
        com.cosmicbreach.voice.boss.BossVoices.soundPlayed(this, UnsungRegistry.AWAKEN.get());
        com.cosmicbreach.voice.boss.BossVoices.soundPlayed(this, UnsungRegistry.DIM.get());
        CosmicBreach.LOGGER.debug("[cosmicbreach] The Unsung awakened at {} for {} player(s), {} health a mask{}, first line at tick {} ({} ticks of intro)",
                arena.centreBlock(), playersAtStart, (int) max, echo ? " (Guardian Echo)" : "", fightStart, fightStart - now);
    }

    private void clearFight() {
        gauge.clear();
        targeting.clear();
        participants.clear();
        schedule.clear();
        cancelAttacks();
        singer = null;
        lastSung = null;
        preparedSinger = null;
        forcedNext = null;
        preparedLine = Long.MIN_VALUE;
        java.util.Arrays.fill(turnsSung, 0);
        breakUntil = Long.MIN_VALUE;
        warningActive = false;
        lit = new int[0];
        target = null;
        crumbling.clear();
        holdUntil = Long.MIN_VALUE;
        breaks = 0;
        harmonizes = 0;
        harmonizeHits = 0;
        parries = 0;
        glances = 0;
        notesBroken = 0;
        noteHits = 0;
        brokenOrder.clear();
        attacks.clear();
        parts.clear();
        landings.clear();
        blendFrom.clear();
        blendStart.clear();
        fellAt.clear();
        entityData.set(DATA_SINGER, (byte) -1);
        entityData.set(DATA_NEXT, (byte) -1);
        entityData.set(DATA_BROKEN, (byte) 0);
        entityData.set(DATA_FLAGS, (byte) 0);
        entityData.set(DATA_LIT, (byte) 0);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (arena == null) {
            BlockPos below = blockPosition().below();
            bindLair(below, below);
        }
        long now = server.getGameTime();
        holdPosition();
        ensureMasks(server, now);
        if (isRemoved()) {
            return;
        }
        switch (state) {
            case DORMANT -> dormantTick(server, now);
            case INTRO -> introTick(server, now);
            case FIGHT -> fightTick(server, now);
            case DYING -> dyingTick(server, now);
        }
        if (isRemoved()) {
            return;
        }
        placeMasks(now);
        crumbleTick(server, now);
        if (state == State.INTRO || state == State.FIGHT) {
            presenceTick(server, now);
        }
        barTick(server, now);
    }

    private void holdPosition() {
        double x = arena.x();
        double y = arena.floorY() + 1.0;
        double z = arena.z();
        if (Math.abs(getX() - x) > 1e-3 || Math.abs(getY() - y) > 1e-3 || Math.abs(getZ() - z) > 1e-3) {
            setPos(x, y, z);
        }
    }

    /** Makes any missing mask again; one lost mid-fight (a command) resets the fight first. */
    private void ensureMasks(ServerLevel server, long now) {
        boolean lost = false;
        for (Voice v : Voice.values()) {
            UnsungMask m = masks.get(v);
            if (m == null || m.isRemoved()) {
                lost |= m != null && state != State.DORMANT;
                masks.remove(v);
            }
        }
        if (lost) {
            CosmicBreach.LOGGER.debug("[cosmicbreach] An Unsung mask was lost mid-fight: the choir resets");
            reset(server, now);
        }
        for (Voice v : Voice.values()) {
            if (!masks.containsKey(v) && state == State.DORMANT) {
                UnsungMask m = UnsungMask.make(server, this, v, restFace(v));
                m.restore(UnsungMoves.MASK_HEALTH);
                m.setMode(UnsungMask.Mode.REST, now);
                server.addFreshEntity(m);
                masks.put(v, m);
                lastFace.put(v, restFace(v));
            }
        }
    }

    /** Called by a mask removed by a command. */
    void maskLost(UnsungMask mask) {
        masks.values().remove(mask);
    }

    private void dormantTick(ServerLevel server, long now) {
        if (relightOnLoad) {
            relightOnLoad = false;
            findWindows(server);
            relightWindows(server);
        }
        if (Math.floorMod(now + getId(), 10L) != 0 || server.getDifficulty() == Difficulty.PEACEFUL || !lairArmed()) {
            return;
        }
        for (ServerPlayer p : fightersOnFloor()) {
            if (!LayerAttunement.hasSanctum(p)) {
                awaken(p, false);
                return;
            }
        }
    }

    private void introTick(ServerLevel server, long now) {
        long liftStart = fightStart - (long) UnsungMoves.INTRO_BEATS * BEAT;
        dimWindows(server, now, stateStart(), fightStart - 2L * BEAT);
        for (Voice v : Voice.values()) {
            long lift = liftStart + v.ordinal() * 2L * BEAT;
            UnsungMask m = masks.get(v);
            if (now == lift && m != null) {
                m.setMode(UnsungMask.Mode.RISE, now);
                playAt(m.face(), firstNote(v), 2.5f, 1.0f);
                server.broadcastEntityEvent(m, UnsungMask.EVENT_FIRST_NOTE);
            }
        }
        if (now == liftStart + BEAT - com.cosmicbreach.voice.boss.BossVoices.LEAD) {
            // the opener is relayed on the lifts: its first word a beat after the Alto's first note (the wake sounds are over by
            // then, see awaken), each later fragment a beat after its mask's; its words end before the fight starts or it is skipped
            com.cosmicbreach.voice.boss.BossVoices.fire(this, "fight_start", fightStart);
        }
        songPrepare(now);
        if (now >= fightStart) {
            setState(State.FIGHT, now);
            dimWindows(server, now, 0, 0);
            fightTick(server, now);
        }
    }

    private static SoundEvent firstNote(Voice v) {
        return switch (v) {
            case ALTO -> UnsungRegistry.FIRST_ALTO.get();
            case TENOR -> UnsungRegistry.FIRST_TENOR.get();
            case BASS -> UnsungRegistry.FIRST_BASS.get();
        };
    }

    private void fightTick(ServerLevel server, long now) {
        boolean broken = broken(now);
        if (!broken && hasFlag(FLAG_BREAK)) {
            endBreak(now);
        }
        if (BossTargeting.checkDue(now, fightStart) || target == null || !validTarget(target)) {
            chooseTarget(now);
        }
        if (UnsungSong.onBeat(now, fightStart)) {
            long beat = UnsungSong.fightBeat(now, fightStart);
            if (UnsungSong.line(beat)) {
                songLine(server, now, beat);
            }
        }
        songPrepare(now);
        runSchedule(server, now);
        attacksTick(server, now);
        notes.removeIf(Entity::isRemoved);
    }

    // ------------------------------------------------------------------ the song

    /** The living voices (masks not broken). */
    public Set<Voice> living() {
        int bits = entityData.get(DATA_BROKEN);
        return Voice.livingFromBroken(bits);
    }

    private boolean isShattered(Voice v) {
        return (entityData.get(DATA_BROKEN) & (1 << v.ordinal())) != 0;
    }

    /** Half a beat before each line: who sings the next turn, and its attacks scheduled. */
    private void songPrepare(long now) {
        long next = fightStart + Math.floorDiv(now + UnsungMoves.RIPPLE_TELL - fightStart + UnsungMoves.TURN_TICKS - 1,
                UnsungMoves.TURN_TICKS) * UnsungMoves.TURN_TICKS;
        if (next - now != UnsungMoves.RIPPLE_TELL || preparedLine == next) {
            return;
        }
        long lineBeat = (next - fightStart) / BEAT;
        Set<Voice> living = living();
        Voice v = forcedNext != null && living.contains(forcedNext) ? forcedNext : UnsungSong.upcoming(lastSung, living, lineBeat);
        forcedNext = null;
        preparedLine = next;
        preparedSinger = v;
        entityData.set(DATA_NEXT, (byte) (v == null ? -1 : v.ordinal()));
        if (v == null) {
            return;
        }
        boolean continuing = UnsungSong.warningLine(lineBeat) && v == lastSung;
        int turns = turnsSung[v.ordinal()] - (continuing ? 1 : 0);
        for (Cue c : UnsungSong.plan(v, living, Math.max(0, turns), UnsungSong.downbeat(lineBeat))) {
            schedule.computeIfAbsent(next + c.tick(), k -> new ArrayList<>()).add(new Scheduled(c, v, next));
        }
    }

    /** A line: Harmonize lands if it is due, then the song passes (or, for a warning, stops rotating). */
    private void songLine(ServerLevel server, long now, long beat) {
        if (UnsungSong.downbeat(beat)) {
            resolveHarmonize(server, now);
        }
        Voice v = preparedLine == now ? preparedSinger : UnsungSong.upcoming(lastSung, living(), beat);
        if (v != null && isShattered(v)) {
            v = null;
        }
        boolean continuing = UnsungSong.warningLine(beat) && v != null && v == lastSung;
        if (v != null && !continuing) {
            turnsSung[v.ordinal()]++;
        }
        setSinger(v);
        if (v != null) {
            lastSung = v;
        }
        if (UnsungSong.warningLine(beat) && !broken(now)) {
            startWarning(server, now);
        }
    }

    private void setSinger(@Nullable Voice v) {
        Voice before = singer;
        singer = v;
        entityData.set(DATA_SINGER, (byte) (v == null ? -1 : v.ordinal()));
        long now = level().getGameTime();
        for (Map.Entry<Voice, UnsungMask> e : masks.entrySet()) {
            UnsungMask m = e.getValue();
            boolean sings = e.getKey() == v && !m.shattered();
            m.setFlag(UnsungMask.FLAG_SINGING, sings);
            m.setFlag(UnsungMask.FLAG_HUM, !sings && !m.shattered());
            if (before != v && (e.getKey() == before || e.getKey() == v)) {
                startBlend(e.getKey(), now);
            }
        }
    }

    private void runSchedule(ServerLevel server, long now) {
        while (!schedule.isEmpty() && schedule.firstKey() <= now) {
            Map.Entry<Long, List<Scheduled>> first = schedule.pollFirstEntry();
            if (first.getKey() < now) {
                continue; // a tick passed without us (a reset or a Break): too late for these
            }
            for (Scheduled s : first.getValue()) {
                perform(server, s, now);
            }
        }
    }

    private void perform(ServerLevel server, Scheduled s, long now) {
        if (broken(now) || now < holdUntil) {
            return;
        }
        UnsungMask m = masks.get(s.performer());
        boolean sings = s.performer() == singer || (s.cue().tick() < 0 && s.line() == preparedLine && s.performer() == preparedSinger);
        if (m == null || m.shattered() || !sings) {
            return;
        }
        attacks.merge(s.performer(), 1, Integer::sum);
        parts.merge(s.cue().part(), 1, Integer::sum);
        switch (s.cue().part()) {
            case NOTE -> startNote(server, m, s.cue().of(), now);
            case WAVE -> startWave(m, now);
            case RIPPLE -> startRipple(m, now);
            case DROP -> startDrop(m, now);
        }
    }

    // ------------------------------------------------------------------ the attacks

    private void startNote(ServerLevel server, UnsungMask m, Voice of, long now) {
        ServerPlayer p = targetPlayer();
        notes.add(SongNote.form(server, this, m, of, p, now));
        playAt(m.mouth(), UnsungRegistry.NOTE_FORM.get(), 1.4f, 0.95f + 0.1f * random.nextFloat());
        if (p != null) {
            participants.targeted(p.getUUID());
        }
    }

    private void startWave(UnsungMask m, long now) {
        wave = new Wave(m, now + UnsungMoves.WAVE_INHALE);
        entityData.set(DATA_WAVE, wave.release);
        m.setFlag(UnsungMask.FLAG_INHALE, true);
        playAt(m.face(), UnsungRegistry.INHALE.get(), 2.0f, 1.0f);
    }

    private void startRipple(UnsungMask m, long now) {
        Vec3 c = new Vec3(m.getX(), arena.floorY(), m.getZ());
        ripple = new Ripple(m, c, now + UnsungMoves.RIPPLE_TELL);
        entityData.set(DATA_RIPPLE, new Vector3f((float) c.x, (float) c.y, (float) c.z));
        entityData.set(DATA_RIPPLE_LAND, ripple.land());
        playAt(c.add(0, 0.5, 0), UnsungRegistry.RIPPLE_TELL.get(), 2.0f, 1.0f);
    }

    private void startDrop(UnsungMask m, long now) {
        ServerPlayer p = targetPlayer();
        if (p == null) {
            return;
        }
        participants.targeted(p.getUUID());
        Vec3 point = floorUnder(p.position());
        drop = new Drop(m, now, p.getUUID(), m.face(), point);
        m.setMode(UnsungMask.Mode.DROP, now);
        syncDrop();
        entityData.set(DATA_DROP_LAND, drop.land());
        entityData.set(DATA_DROP_VOICE, (byte) m.voice().ordinal());
        playAt(m.face(), UnsungRegistry.DROP_RISE.get(), 2.5f, 1.0f);
    }

    private Vec3 floorUnder(Vec3 p) {
        return new Vec3(p.x, arena.groundY(p.x, p.z), p.z);
    }

    private void syncDrop() {
        entityData.set(DATA_DROP, new Vector3f((float) drop.point.x, (float) drop.point.y, (float) drop.point.z));
    }

    private void attacksTick(ServerLevel server, long now) {
        if (wave != null) {
            waveTick(server, now);
        }
        if (ripple != null && now >= ripple.land()) {
            rippleLands(server, now);
        }
        if (drop != null) {
            dropTick(server, now);
        }
    }

    private void waveTick(ServerLevel server, long now) {
        Wave w = wave;
        if (now == w.release) {
            landed(1, now);
            w.by.setFlag(UnsungMask.FLAG_INHALE, false);
            server.broadcastEntityEvent(this, EVENT_WAVE);
            playAt(arena.centre().add(0, 1, 0), UnsungRegistry.WAVE.get(), 3.0f, 1.0f);
        }
        if (now < w.release) {
            return;
        }
        double before = ChoirArena.waveFront(now - 1 - w.release);
        double after = ChoirArena.waveFront(now - w.release);
        for (ServerPlayer p : fightersOnFloor()) {
            if (w.hit.contains(p.getUUID())) {
                continue;
            }
            double half = p.getBbWidth() / 2.0;
            if (arena.waveCatches(p.getX(), p.getZ(), p.getY(), half, before, after)) {
                w.hit.add(p.getUUID());
                w.by.currentImpact = UnsungMoves.WAVE_IMPACT;
                w.by.parryableNow = false;
                participants.targeted(p.getUUID());
                p.hurt(damageSources().mobAttack(w.by), UnsungMoves.WAVE_DAMAGE);
            }
        }
        if (before > ChoirArena.FLOOR_RADIUS + 1.0) {
            wave = null;
        }
    }

    private void rippleLands(ServerLevel server, long now) {
        Ripple r = ripple;
        ripple = null;
        if (r.by().shattered()) {
            return;
        }
        landed(2, now);
        for (ServerPlayer p : fightersOnFloor()) {
            if (!Telegraphs.inCircle(p.getBoundingBox(), r.centre(), UnsungMoves.RIPPLE_RADIUS)) {
                continue;
            }
            boolean braced = p.isCrouching() && p.onGround();
            Vec3 before = p.getDeltaMovement();
            r.by().currentImpact = UnsungMoves.RIPPLE_IMPACT;
            r.by().parryableNow = false;
            participants.targeted(p.getUUID());
            if (p.hurt(damageSources().mobAttack(r.by()), UnsungMoves.RIPPLE_DAMAGE)) {
                throwFrom(p, r.centre(), braced ? UnsungMoves.RIPPLE_PUSH * UnsungMoves.BRACED_PUSH : UnsungMoves.RIPPLE_PUSH, braced, before);
            }
        }
        server.broadcastEntityEvent(this, EVENT_RIPPLE);
        playAt(r.centre().add(0, 0.5, 0), UnsungRegistry.RIPPLE.get(), 3.0f, 1.0f);
    }

    /** Throws a player away from {@code from}: {@code strength} blocks a tick outward, a hop unless they stood firm. */
    private static void throwFrom(ServerPlayer p, Vec3 from, double strength, boolean braced, Vec3 before) {
        Vec3 away = new Vec3(p.getX() - from.x, 0, p.getZ() - from.z);
        away = away.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : away.normalize();
        double resist = Mth.clamp(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        double s = strength * (1.0 - resist);
        p.setDeltaMovement(away.x * s, braced ? before.y : Math.max(before.y, 0.3), away.z * s);
        p.hurtMarked = true;
    }

    private void dropTick(ServerLevel server, long now) {
        Drop d = drop;
        long t = now - d.rise;
        if (d.by.shattered()) {
            drop = null;
            return;
        }
        if (t < UnsungMoves.DROP_TRACK && d.target != null) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(d.target);
            if (p != null && p.level() == server && fairGame(p) && arena.onFloor(p.position())) {
                d.point = floorUnder(p.position());
                syncDrop();
            }
        }
        if (t == UnsungMoves.DROP_GLINT) {
            d.by.setFlag(UnsungMask.FLAG_GLINT, true);
            playAt(d.point.add(0, UnsungMoves.DROP_HEIGHT, 0), UnsungRegistry.GLINT.get(), 2.5f, 1.0f);
        }
        if (t == UnsungMoves.DROP_TELL) {
            d.by.setFlag(UnsungMask.FLAG_GLINT, false);
            d.landed = true;
            landed(3, now);
            for (ServerPlayer p : fightersOnFloor()) {
                if (!Telegraphs.inCircle(p.getBoundingBox(), d.point, UnsungMoves.DROP_RADIUS)) {
                    continue;
                }
                Vec3 before = p.getDeltaMovement();
                d.by.currentImpact = UnsungMoves.DROP_IMPACT;
                d.by.parryableNow = true;
                participants.targeted(p.getUUID());
                boolean hurt = p.hurt(damageSources().mobAttack(d.by), UnsungMoves.DROP_DAMAGE);
                d.by.parryableNow = false;
                if (hurt) {
                    throwFrom(p, d.point, UnsungMoves.DROP_PUSH, false, before);
                }
            }
            server.broadcastEntityEvent(this, EVENT_DROP);
            server.broadcastEntityEvent(d.by, UnsungMask.EVENT_LAND);
            playAt(d.point.add(0, 0.5, 0), UnsungRegistry.DROP.get(), 3.5f, d.parried ? 1.15f : 1.0f);
        }
        if (t >= UnsungMoves.DROP_TELL + UnsungMoves.DROP_REST) {
            drop = null;
            if (d.by.mode() == UnsungMask.Mode.DROP) {
                d.by.setMode(UnsungMask.Mode.FLOAT, now);
                startBlend(d.by.voice(), now);
            }
        }
    }

    /** The Bass Drop met a parry: 80 went into the gauge (twice its Impact, through the engine). */
    void dropParried(UnsungMask mask, Player player) {
        if (drop != null && drop.by == mask && !drop.parried) {
            drop.parried = true;
            parries++;
            level().broadcastEntityEvent(mask, UnsungMask.EVENT_PARRIED);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Bass Drop parried by {} (gauge {} of {})", player.getGameProfile().getName(),
                    String.format(java.util.Locale.ROOT, "%.0f", gauge.total(level().getGameTime())), (int) UnsungMoves.BREAK_CAPACITY);
        }
    }

    /** Stops every attack under way (a Break, a death, a reset): telegraphs vanish, notes fade. */
    private void cancelAttacks() {
        if (wave != null) {
            wave.by.setFlag(UnsungMask.FLAG_INHALE, false);
        }
        wave = null;
        ripple = null;
        if (drop != null) {
            drop.by.setFlag(UnsungMask.FLAG_GLINT, false);
            if (drop.by.mode() == UnsungMask.Mode.DROP) {
                drop.by.setMode(UnsungMask.Mode.FLOAT, level().getGameTime());
            }
        }
        drop = null;
        for (SongNote n : notes) {
            n.discard();
        }
        notes.clear();
        entityData.set(DATA_WAVE, Long.MIN_VALUE / 2);
        entityData.set(DATA_RIPPLE_LAND, Long.MIN_VALUE / 2);
        entityData.set(DATA_DROP_LAND, Long.MIN_VALUE / 2);
    }

    // ------------------------------------------------------------------ Harmonize

    private void startWarning(ServerLevel server, long now) {
        if (warningActive) {
            endWarning(server, now);
        }
        int players = Math.max(1, fightersOnFloor().size());
        lit = HarmonizeRules.choose(HarmonizeRules.litCount(living().size(), players), random::nextDouble);
        warningActive = true;
        warningStart = now;
        setCircles(server, lit, true);
        byte bits = 0;
        for (int k : lit) {
            bits |= (byte) (1 << k);
        }
        entityData.set(DATA_LIT, bits);
        entityData.set(DATA_WARNING_START, now);
        setFlag(FLAG_WARNING, true);
        for (UnsungMask m : masks.values()) {
            startBlend(m.voice(), now);
        }
        for (int k : lit) {
            playAt(arena.circleCentre(k).add(0, 0.5, 0), UnsungRegistry.CIRCLES.get(), 1.6f, 1.0f);
        }
        server.broadcastEntityEvent(this, EVENT_WARNING);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Harmonize warning: circles {} lit, {} mask(s) rising", java.util.Arrays.toString(lit), living().size());
    }

    private void resolveHarmonize(ServerLevel server, long now) {
        if (!warningActive) {
            return;
        }
        List<HarmonizeRules.Spot> spots = new ArrayList<>();
        for (ServerPlayer p : fightersOnFloor()) {
            spots.add(new HarmonizeRules.Spot(p.getUUID(), p.getX(), p.getZ()));
        }
        Set<UUID> safe = HarmonizeRules.sheltered(spots, arena.circles(lit));
        DamageSource src = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(UnsungRegistry.HARMONIZE_DAMAGE));
        int caught = 0;
        for (ServerPlayer p : fightersOnFloor()) {
            if (safe.contains(p.getUUID())) {
                continue;
            }
            caught++;
            participants.targeted(p.getUUID());
            if (p.hurt(src, UnsungMoves.HARMONIZE_DAMAGE)) {
                p.addEffect(new MobEffectInstance(UnsungRegistry.SILENCED, UnsungMoves.SILENCE_TICKS, 0, false, true, true));
                PlayerCombat.of(p).machine().drainResonance(UnsungMoves.SILENCE_DRAIN);
            }
        }
        harmonizes++;
        harmonizeHits += caught;
        if (caught == 0 && !spots.isEmpty()) {
            // two beats after the clean downbeat, when the beat rules allow
            com.cosmicbreach.voice.boss.BossVoices.raiseEventAt(this, "harmonize_all_safe", now + 2L * BEAT);
        }
        landed(4, now);
        endWarning(server, now);
        server.broadcastEntityEvent(this, EVENT_HARMONIZE);
        playAt(arena.centre().add(0, 4, 0), UnsungRegistry.HARMONIZE.get(), 4.0f, 1.0f);
        CosmicBreach.LOGGER.debug("[cosmicbreach] Harmonize on the downbeat: {} of {} player(s) sheltered", spots.size() - caught, spots.size());
    }

    private void endWarning(ServerLevel server, long now) {
        warningActive = false;
        setCircles(server, lit, false);
        lit = new int[0];
        entityData.set(DATA_LIT, (byte) 0);
        setFlag(FLAG_WARNING, false);
        for (UnsungMask m : masks.values()) {
            startBlend(m.voice(), now);
        }
    }

    private void setCircles(ServerLevel server, int[] which, boolean on) {
        if (arena == null) {
            return;
        }
        Set<Integer> wanted = new HashSet<>();
        for (int k : which) {
            wanted.add(k);
        }
        int y = arena.floorY() - 1;
        int r = (int) Math.ceil(ChoirArena.RING_RADIUS + ChoirArena.CIRCLE_BLOCKS) + 1;
        for (int bx = arena.x() - r; bx <= arena.x() + r; bx++) {
            for (int bz = arena.z() - r; bz <= arena.z() + r; bz++) {
                int k = arena.circleColumn(bx, bz);
                if (k < 0 || !wanted.contains(k)) {
                    continue;
                }
                BlockPos pos = new BlockPos(bx, y, bz);
                BlockState s = server.getBlockState(pos);
                if (s.is(UnsungRegistry.SILENCE_CIRCLE.get()) && s.getValue(SilenceCircleBlock.LIT) != on) {
                    server.setBlock(pos, s.setValue(SilenceCircleBlock.LIT, on), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Break

    /** Impact on a mask: the choir's shared gauge. */
    void maskImpact(UnsungMask mask, double impact) {
        long now = level().getGameTime();
        if (state != State.FIGHT || broken(now) || mask.shattered()) {
            return;
        }
        if (gauge.add(now, impact)) {
            startBreak((ServerLevel) level(), now);
        }
    }

    private void startBreak(ServerLevel server, long now) {
        breaks++;
        breakUntil = now + UnsungMoves.BREAK_TICKS;
        entityData.set(DATA_BREAK_START, now);
        setFlag(FLAG_BREAK, true);
        cancelAttacks();
        if (warningActive) {
            endWarning(server, now);
            CosmicBreach.LOGGER.debug("[cosmicbreach] the Break silenced the Harmonize");
        }
        for (UnsungMask m : masks.values()) {
            if (!m.shattered()) {
                fellAt.put(m.voice(), lastFace.getOrDefault(m.voice(), m.face()));
                m.setMode(UnsungMask.Mode.FALLEN, now);
                m.setFlag(UnsungMask.FLAG_INHALE, false);
                startBlend(m.voice(), now);
            }
        }
        server.broadcastEntityEvent(this, EVENT_BREAK);
        playAt(arena.centre().add(0, 2, 0), UnsungRegistry.BREAK.get(), 3.5f, 1.0f);
        com.cosmicbreach.voice.boss.BossVoices.raiseEventAt(this, "break", now + BEAT);
        CosmicBreach.LOGGER.debug("[cosmicbreach] The Unsung Broken at {} health", String.format(java.util.Locale.ROOT, "%.1f", totalHealth()));
    }

    private void endBreak(long now) {
        setFlag(FLAG_BREAK, false);
        gauge.clear();
        for (UnsungMask m : masks.values()) {
            if (!m.shattered()) {
                m.setMode(UnsungMask.Mode.FLOAT, now);
                startBlend(m.voice(), now);
            }
        }
        level().broadcastEntityEvent(this, EVENT_BREAK_END);
    }

    public boolean broken(long now) {
        return level().isClientSide() ? hasFlag(FLAG_BREAK) : now < breakUntil;
    }

    // ------------------------------------------------------------------ hits on the masks

    /** How much of a hit on {@code mask} lands: 1 on the singer, 1.5 on any living mask in a Break, 0 (a tink) otherwise. */
    double hitMultiplier(UnsungMask mask, DamageSource source) {
        long now = level().getGameTime();
        boolean byPlayer = source.getEntity() instanceof Player || source.getDirectEntity() instanceof Projectile;
        if (arena != null && ArenaRules.burnsUp(source, arena.centre(), UnsungMoves.PROJECTILE_RANGE)) {
            return 0.0;
        }
        if (state == State.FIGHT && !mask.shattered()) {
            if (broken(now)) {
                return UnsungMoves.BREAK_DAMAGE_TAKEN;
            }
            if (mask.voice() == singer) {
                return 1.0;
            }
        }
        if (byPlayer && !mask.shattered()) {
            glances++;
            mask.tink(now);
        }
        return 0.0;
    }

    void hitLanded(UnsungMask mask, DamageSource source, float dealt) {
        if (source.getEntity() instanceof ServerPlayer p) {
            participants.dealtDamage(p.getUUID(), arena != null && arena.onFloor(p.position()));
            targeting.recordDamage(p.getUUID(), dealt, level().getGameTime());
        }
    }

    boolean projectileBurnsUp(DamageSource source) {
        return arena != null && ArenaRules.burnsUp(source, arena.centre(), UnsungMoves.PROJECTILE_RANGE);
    }

    void noteHit(ServerPlayer p) {
        noteHits++;
    }

    /** An attack landed at {@code tick} (0 note, 1 wave, 2 ripple, 3 drop, 4 Harmonize). */
    void landed(int kind, long tick) {
        if (landings.size() < 1000) {
            landings.add(new long[] {tick, kind});
        }
    }

    /** Every landing this fight: {tick, kind}. */
    public List<long[]> landings() {
        return List.copyOf(landings);
    }

    /** How many times each part started this fight. */
    public int partCount(UnsungSong.Part part) {
        return parts.getOrDefault(part, 0);
    }

    void noteBroken() {
        notesBroken++;
    }

    /** A mask's health reached zero: it breaks, its line leaves the song, and the last one ends the fight. */
    void maskBroken(UnsungMask mask) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        Voice v = mask.voice();
        entityData.set(DATA_BROKEN, (byte) (entityData.get(DATA_BROKEN) | (1 << v.ordinal())));
        brokenOrder.add(v);
        fellAt.put(v, lastFace.getOrDefault(v, mask.face()));
        mask.setMode(UnsungMask.Mode.BROKEN, now);
        mask.setFlag(UnsungMask.FLAG_SINGING, false);
        mask.setFlag(UnsungMask.FLAG_HUM, false);
        mask.setFlag(UnsungMask.FLAG_GLINT, false);
        mask.setFlag(UnsungMask.FLAG_INHALE, false);
        startBlend(v, now);
        if (singer == v) {
            setSinger(null);
        }
        if (drop != null && drop.by == mask) {
            drop = null;
            entityData.set(DATA_DROP_LAND, Long.MIN_VALUE / 2);
        }
        if (wave != null && wave.by == mask && now < wave.release) {
            wave = null;
            entityData.set(DATA_WAVE, Long.MIN_VALUE / 2);
        }
        if (ripple != null && ripple.by() == mask) {
            ripple = null;
            entityData.set(DATA_RIPPLE_LAND, Long.MIN_VALUE / 2);
        }
        notes.removeIf(n -> {
            if (n.mask() == mask && server.getGameTime() < n.flies()) {
                n.discard();
                return true;
            }
            return false;
        });
        server.broadcastEntityEvent(mask, UnsungMask.EVENT_SHATTER);
        playAt(mask.face(), UnsungRegistry.CRACK.get(), 3.5f, 1.0f);
        int whole = living().size();
        if (whole == 2 || whole == 1) {
            // a third of the bar each: the survivors' line, from a beat after the crack, on the next beat the rules allow
            com.cosmicbreach.voice.boss.BossVoices.raiseAt(this, whole == 2 ? "hp_threshold:67" : "hp_threshold:33", now + BEAT);
        }
        CosmicBreach.LOGGER.debug("[cosmicbreach] The {} mask broke at tick {} of the fight ({} left): its line leaves the song",
                v.id(), now - fightStart, living().size());
        if (living().isEmpty()) {
            startDying(server, now);
        }
    }

    // ------------------------------------------------------------------ targeting

    private boolean validTarget(UUID id) {
        for (ServerPlayer p : fightersOnFloor()) {
            if (p.getUUID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void chooseTarget(long now) {
        List<BossTargeting.Candidate> candidates = new ArrayList<>();
        for (ServerPlayer p : fightersOnFloor()) {
            candidates.add(new BossTargeting.Candidate(p.getUUID(), p.distanceToSqr(arena.centre())));
        }
        target = targeting.choose(candidates, now).orElse(null);
        if (target != null) {
            participants.targeted(target);
        }
    }

    private @Nullable ServerPlayer targetPlayer() {
        if (target == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer p = server.getServer().getPlayerList().getPlayer(target);
        return p != null && p.level() == server && validTarget(target) ? p : null;
    }

    // ------------------------------------------------------------------ where the masks are

    /** Where a sleeping mask rests: low over the dais, a step from its centre. */
    private Vec3 restFace(Voice v) {
        double a = Math.PI / 2.0 + v.ordinal() * Math.PI * 2.0 / 3.0;
        return new Vec3(arena.x() + 2.4 * Math.cos(a), arena.floorY() + 1.55, arena.z() + 2.4 * Math.sin(a));
    }

    private void startBlend(Voice v, long now) {
        Vec3 from = lastFace.get(v);
        if (from != null) {
            blendFrom.put(v, from);
            blendStart.put(v, now);
        }
    }

    private void placeMasks(long now) {
        for (Map.Entry<Voice, UnsungMask> e : masks.entrySet()) {
            Voice v = e.getKey();
            UnsungMask m = e.getValue();
            Vec3 aim = faceFor(v, m, now);
            Long since = blendStart.get(v);
            Vec3 face = aim;
            if (since != null && now - since < BLEND && blendFrom.get(v) != null) {
                double u = smooth((now - since) / (double) BLEND);
                face = blendFrom.get(v).lerp(aim, u);
            }
            lastFace.put(v, face);
            m.placeFace(face, yawFor(v, m, face));
        }
    }

    private Vec3 faceFor(Voice v, UnsungMask m, long now) {
        return switch (m.mode()) {
            case REST -> restFace(v);
            case RISE -> {
                double u = smooth((now - m.modeStart()) / (2.0 * BEAT));
                if (u >= 1.0) {
                    m.setMode(UnsungMask.Mode.FLOAT, now);
                }
                yield restFace(v).lerp(floatFace(v, now), u);
            }
            case FLOAT -> floatFace(v, now);
            case DROP -> dropFace(v, m, now);
            case FALLEN -> {
                Vec3 at = fellAt.getOrDefault(v, floatFace(v, now));
                double u = Math.min(1.0, (now - m.modeStart()) / 8.0);
                double y = Mth.lerp(u * u, at.y, arena.floorY() + UnsungMoves.FALLEN_FACE);
                yield new Vec3(at.x, y, at.z);
            }
            case BROKEN, GONE -> {
                Vec3 at = fellAt.getOrDefault(v, floatFace(v, now));
                double u = Math.min(1.0, (now - m.modeStart()) / 10.0);
                double y = Mth.lerp(u * u, at.y, arena.groundY(at.x, at.z) + 0.3);
                yield new Vec3(at.x, y, at.z);
            }
        };
    }

    /** A floating mask on its orbit: the singer a little lower, and all of them rising through a Harmonize warning. */
    private Vec3 floatFace(Voice v, long now) {
        Vec3 o = arena.orbit(v, now);
        double dy = v == singer ? -UnsungMoves.SINGER_DIP : UnsungMoves.HUM_LIFT;
        if (warningActive) {
            dy += UnsungMoves.HARMONIZE_RISE * smooth((now - warningStart) / (double) (UnsungMoves.WARNING_BEATS * BEAT));
        }
        return o.add(0, dy, 0);
    }

    /** The Bass Drop: up over the target, a hold, the fall, a beat on the floor, then home. */
    private Vec3 dropFace(Voice v, UnsungMask m, long now) {
        if (drop == null || drop.by != m) {
            return floatFace(v, now);
        }
        long t = now - drop.rise;
        Vec3 over = drop.point.add(0, UnsungMoves.DROP_HEIGHT, 0);
        Vec3 floor = drop.point.add(0, 1.3, 0);
        if (t < UnsungMoves.DROP_TRACK) {
            return drop.from.lerp(over, smooth(t / (double) UnsungMoves.DROP_TRACK));
        }
        if (t < UnsungMoves.DROP_GLINT) {
            return over;
        }
        if (t < UnsungMoves.DROP_TELL) {
            double u = (t - UnsungMoves.DROP_GLINT) / (double) (UnsungMoves.DROP_TELL - UnsungMoves.DROP_GLINT);
            return over.lerp(floor, u * u);
        }
        return floor;
    }

    private float yawFor(Voice v, UnsungMask m, Vec3 face) {
        ServerPlayer p = targetPlayer();
        if (p != null && !m.shattered() && m.mode() != UnsungMask.Mode.REST) {
            return ChoirArena.yawToward(face, p.position());
        }
        return ChoirArena.yawToward(arena.centre().add(0, face.y - arena.floorY(), 0), face);
    }

    private static double smooth(double u) {
        double x = Mth.clamp(u, 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------------ the lichen and the dark

    private void findWindows(ServerLevel server) {
        windows.clear();
        windowsDimmed = 0;
        int r = 20;
        for (int bx = arena.x() - r; bx <= arena.x() + r; bx++) {
            for (int bz = arena.z() - r; bz <= arena.z() + r; bz++) {
                double d = arena.distance(bx + 0.5, bz + 0.5);
                if (d < ChoirArena.FLOOR_RADIUS - 0.5 || d > ChoirArena.FLOOR_RADIUS + 3.0) {
                    continue;
                }
                for (int y = arena.floorY(); y <= arena.floorY() + 14; y++) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    if (server.getBlockState(pos).is(UnsungRegistry.LICHEN_WINDOW.get())) {
                        windows.add(pos);
                    }
                }
            }
        }
        // round the apse, so the dark closes in window by window
        windows.sort(Comparator.comparingDouble(p -> Math.atan2(p.getZ() + 0.5 - arena.z(), p.getX() + 0.5 - arena.x())));
    }

    private void dimWindows(ServerLevel server, long now, long from, long to) {
        int n = windows.size();
        int want = to <= from ? n : (int) Math.min(n, Math.max(0, Math.floor((now - from) / (double) (to - from) * n)));
        while (windowsDimmed < want) {
            BlockPos pos = windows.get(windowsDimmed++);
            BlockState s = server.getBlockState(pos);
            if (s.is(UnsungRegistry.LICHEN_WINDOW.get()) && s.getValue(LichenWindowBlock.LIT)) {
                server.setBlock(pos, s.setValue(LichenWindowBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void relightWindows(ServerLevel server) {
        for (BlockPos pos : windows) {
            BlockState s = server.getBlockState(pos);
            if (s.is(UnsungRegistry.LICHEN_WINDOW.get()) && !s.getValue(LichenWindowBlock.LIT)) {
                server.setBlock(pos, s.setValue(LichenWindowBlock.LIT, true), Block.UPDATE_CLIENTS);
            }
        }
        windowsDimmed = 0;
    }

    /** At the awakening every light brought onto the choir floor goes out: the fight is lit by the singers. */
    private void extinguish(ServerLevel server) {
        int r = (int) Math.ceil(ChoirArena.FLOOR_RADIUS);
        int out = 0;
        for (int bx = arena.x() - r; bx <= arena.x() + r; bx++) {
            for (int bz = arena.z() - r; bz <= arena.z() + r; bz++) {
                if (!arena.floorColumn(bx, bz)) {
                    continue;
                }
                for (int y = arena.floorY() - 1; y <= arena.floorY() + 16; y++) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    BlockState s = server.getBlockState(pos);
                    if (s.isAir() || s.getLightEmission(server, pos) <= 0 || s.is(UnsungRegistry.SILENCE_CIRCLE.get())
                            || s.is(UnsungRegistry.LICHEN_WINDOW.get()) || s.is(UnsungRegistry.HYMNAL_ALTAR.get())) {
                        continue;
                    }
                    server.destroyBlock(pos, true);
                    out++;
                }
            }
        }
        if (out > 0) {
            CosmicBreach.LOGGER.debug("[cosmicbreach] {} light(s) on the choir floor went out at the awakening", out);
        }
    }

    // ------------------------------------------------------------------ death, rewards, reset

    private void startDying(ServerLevel server, long now) {
        setState(State.DYING, now);
        // the ghosts' relay, its first word on death tick 36 after the last soft chord (which is pulled down under it); it ends
        // before the guide speaks
        com.cosmicbreach.voice.boss.BossVoices.fireAt(this, "boss_kill", now + 36);
        cancelAttacks();
        if (warningActive) {
            endWarning(server, now);
        }
        setFlag(FLAG_BREAK, false);
        breakUntil = Long.MIN_VALUE;
        setSinger(null);
        if (bar != null) {
            bar.setMusic(null);
        }
        server.broadcastEntityEvent(this, EVENT_DEATH);
        CosmicBreach.LOGGER.debug("[cosmicbreach] The Unsung defeated after {} ticks of fighting; masks broke {}; {} Break(s), {} Harmonize(s) "
                        + "({} caught), {} parried Drop(s), {} glancing hit(s), {} note(s) broken, {} note hit(s); attacks {} {}",
                now - fightStart, brokenOrder, breaks, harmonizes, harmonizeHits, parries, glances, notesBroken, noteHits, attacks, parts);
    }

    private void dyingTick(ServerLevel server, long now) {
        long t = now - stateStart();
        if (t == UnsungMoves.LAST_CHORD) {
            server.broadcastEntityEvent(this, EVENT_CHORD);
            playAt(arena.centre().add(0, 4, 0), UnsungRegistry.LAST_CHORD.get(), 4.0f, 1.0f, SoundSource.MUSIC);
        }
        if (t == UnsungMoves.DYING - 20) {
            for (UnsungMask m : masks.values()) {
                m.setMode(UnsungMask.Mode.GONE, now);
            }
            relightWindows(server);
        }
        if (t >= UnsungMoves.DYING) {
            finishKill(server, now);
        }
    }

    private void finishKill(ServerLevel server, long now) {
        for (UUID id : participants.all()) {
            // paid now, or held for a participant who is offline or dead until they are back (GuardianPayouts)
            com.cosmicbreach.guardian.GuardianPayouts.payOrOwe(server.getServer(), id, GuardianTypes.UNSUNG);
        }
        GuardianAltarBlockEntity a = altar();
        if (a != null) {
            a.guardianKilled(now);
        }
        endFight();
        for (UnsungMask m : masks.values()) {
            m.discard();
        }
        masks.clear();
        discard();
    }

    /** Back to sleep at full health, no loot (everyone left, or a command). */
    public void reset(ServerLevel server, long now) {
        cancelAttacks();
        if (warningActive) {
            endWarning(server, now);
        }
        setCircles(server, new int[] {0, 1, 2, 3, 4, 5, 6, 7}, false);
        if (windows.isEmpty()) {
            findWindows(server);
        }
        relightWindows(server);
        for (UnsungMask m : masks.values()) {
            m.restore(UnsungMoves.MASK_HEALTH);
            m.setMode(UnsungMask.Mode.REST, now);
        }
        clearFight();
        state = State.DORMANT;
        setState(State.DORMANT, now);
        endFight();
        CosmicBreach.LOGGER.debug("[cosmicbreach] The Unsung reset to sleep (nobody on the choir floor)");
    }

    private void endFight() {
        if (bar != null) {
            bar.remove();
            bar = null;
        }
        GuardianFights.end(this);
    }

    private void presenceTick(ServerLevel server, long now) {
        if (!fightersOnFloor().isEmpty()) {
            lastPlayerOnFloor = now;
            return;
        }
        if (now - lastPlayerOnFloor >= UnsungMoves.RESET_ABSENT) {
            reset(server, now);
        }
    }

    private void crumbleTick(ServerLevel server, long now) {
        Iterator<Map.Entry<BlockPos, Long>> it = crumbling.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (now >= e.getValue()) {
                BlockPos pos = e.getKey();
                BlockState s = server.getBlockState(pos);
                if (!s.isAir()) {
                    if (s.getLightEmission(server, pos) > 0) {
                        // a light brought onto the choir floor goes out: the Nave keeps its dark
                        server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
                        server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.4f);
                    }
                    server.destroyBlock(pos, true);
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ the boss bar

    /** The masks' health added up. */
    public float totalHealth() {
        float sum = 0f;
        for (UnsungMask m : masks.values()) {
            sum += m.shattered() ? 0f : Math.max(0f, m.getHealth());
        }
        return sum;
    }

    public float totalMaxHealth() {
        float sum = 0f;
        for (UnsungMask m : masks.values()) {
            sum += m.getMaxHealth();
        }
        return sum;
    }

    private void barTick(ServerLevel server, long now) {
        if (bar == null) {
            return;
        }
        if (state == State.INTRO) {
            bar.setProgress((float) (now - stateStart()) / Math.max(1, fightStart - stateStart()));
        } else if (state == State.DYING) {
            bar.setProgress(0f);
        } else {
            float max = totalMaxHealth();
            bar.setProgress(max <= 0 ? 0f : totalHealth() / max);
        }
        bar.setName(getDisplayName());
        bar.setColor(BossEvent.BossBarColor.PURPLE);
        bar.setGauge((float) gauge.fraction(now), broken(now));
        bar.setCountdown(0, 0);
        double range = ChoirArena.FLOOR_RADIUS + 18.0;
        bar.update(server, p -> p.distanceToSqr(arena.x(), arena.floorY() + 3.0, arena.z()) <= range * range);
    }

    /** The Break gauge, 0 to 1 (server). */
    public double gaugeFraction() {
        return gauge.fraction(level().getGameTime());
    }

    // ------------------------------------------------------------------ state sync and queries (both sides)

    private void setState(State s, long now) {
        state = s;
        entityData.set(DATA_STATE, (byte) s.ordinal());
        entityData.set(DATA_STATE_START, now);
    }

    public State state() {
        return level().isClientSide() ? State.values()[Math.floorMod(entityData.get(DATA_STATE), State.values().length)] : state;
    }

    public long stateStart() {
        return entityData.get(DATA_STATE_START);
    }

    public long fightStart() {
        return entityData.get(DATA_FIGHT_START);
    }

    public @Nullable Voice singer() {
        return Voice.of(entityData.get(DATA_SINGER));
    }

    /** Who sings from the next line (known half a beat ahead). */
    public @Nullable Voice nextSinger() {
        return Voice.of(entityData.get(DATA_NEXT));
    }

    public int brokenBits() {
        return entityData.get(DATA_BROKEN);
    }

    private boolean hasFlag(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    private void setFlag(int flag, boolean on) {
        byte f = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? f | flag : f & ~flag);
        if (next != f) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    public boolean isBroken() {
        return hasFlag(FLAG_BREAK);
    }

    public long breakStart() {
        return entityData.get(DATA_BREAK_START);
    }

    public boolean warning() {
        return hasFlag(FLAG_WARNING);
    }

    public long warningStart() {
        return entityData.get(DATA_WARNING_START);
    }

    /** The lit circles of silence (bit {@code k} for circle {@code k}). */
    public int litBits() {
        return entityData.get(DATA_LIT);
    }

    public int[] litCircles() {
        List<Integer> out = new ArrayList<>();
        int bits = litBits();
        for (int k = 0; k < ChoirArena.CIRCLES; k++) {
            if ((bits & (1 << k)) != 0) {
                out.add(k);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The tick the last Sweeping Wave left the dais. */
    public long waveRelease() {
        return entityData.get(DATA_WAVE);
    }

    public Vec3 rippleCentre() {
        Vector3f v = entityData.get(DATA_RIPPLE);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public long rippleLand() {
        return entityData.get(DATA_RIPPLE_LAND);
    }

    public Vec3 dropPoint() {
        Vector3f v = entityData.get(DATA_DROP);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public long dropLand() {
        return entityData.get(DATA_DROP_LAND);
    }

    public @Nullable Voice dropVoice() {
        return Voice.of(entityData.get(DATA_DROP_VOICE));
    }

    /** Server: the masks by voice. */
    public Map<Voice, UnsungMask> masks() {
        return java.util.Collections.unmodifiableMap(masks);
    }

    public Participants participants() {
        return participants;
    }

    public int breaks() {
        return breaks;
    }

    public int harmonizes() {
        return harmonizes;
    }

    public int harmonizeHits() {
        return harmonizeHits;
    }

    public int parries() {
        return parries;
    }

    public int glances() {
        return glances;
    }

    public int notesBroken() {
        return notesBroken;
    }

    public int noteHits() {
        return noteHits;
    }

    public List<Voice> brokenOrder() {
        return List.copyOf(brokenOrder);
    }

    /** Server: the attack under way right now for the scenario's bot ("wave", "ripple", "drop" and its tick), or empty. */
    public String activeAttacks(long now) {
        StringBuilder b = new StringBuilder();
        if (wave != null) {
            b.append("wave@").append(now - wave.release).append(' ');
        }
        if (ripple != null) {
            b.append("ripple@").append(now - ripple.land()).append(' ');
        }
        if (drop != null) {
            b.append("drop@").append(now - drop.land()).append(' ');
        }
        return b.toString().trim();
    }

    // ------------------------------------------------------------------ debug

    /** Holds the attacks for {@code ticks} (tests and screenshots). */
    public void holdAttacks(int ticks) {
        holdUntil = level().getGameTime() + ticks;
    }

    /** Makes the song pass to {@code v} on the next line it can (tests). */
    public void forceNextSinger(Voice v) {
        forcedNext = v;
        preparedLine = Long.MIN_VALUE;
    }

    /** Moves the song so the next line starts a Harmonize warning (tests). Keeps every line on Vesper's clock. */
    public void debugHarmonizeNext() {
        if (warningActive || state != State.FIGHT) {
            return; // one is on its way: let it land
        }
        long now = level().getGameTime();
        long next = fightStart + Math.floorDiv(now + UnsungMoves.RIPPLE_TELL + 1 - fightStart + UnsungMoves.TURN_TICKS - 1,
                UnsungMoves.TURN_TICKS) * UnsungMoves.TURN_TICKS;
        fightStart = next - (long) (UnsungMoves.CYCLE_BEATS - UnsungMoves.WARNING_BEATS) * BEAT;
        entityData.set(DATA_FIGHT_START, fightStart);
        preparedLine = Long.MIN_VALUE;
        schedule.clear();
    }

    public void debugBreak() {
        if (state == State.FIGHT && !broken(level().getGameTime())) {
            startBreak((ServerLevel) level(), level().getGameTime());
        }
    }

    /** Breaks a mask for good, as its health running out would (tests). */
    public void debugShatter(Voice v) {
        UnsungMask m = masks.get(v);
        if (m != null && !m.shattered() && state == State.FIGHT) {
            m.setHealth(0f);
        }
    }

    public void debugHealth(Voice v, float hp) {
        UnsungMask m = masks.get(v);
        if (m != null && !m.shattered()) {
            m.setHealth(Math.min(hp, m.getMaxHealth()));
        }
    }

    // ------------------------------------------------------------------ plumbing

    private void playAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        playAt(at, sound, volume, pitch, SoundSource.HOSTILE);
    }

    private void playAt(Vec3 at, SoundEvent sound, float volume, float pitch, SoundSource source) {
        level().playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_AWAKEN && id <= EVENT_CHORD && level().isClientSide()) {
            UnsungEffects.handler().choirEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            if (bar != null) {
                bar.remove();
                bar = null;
            }
            GuardianFights.end(this);
            for (UnsungMask m : masks.values()) {
                m.discard();
            }
            for (SongNote n : notes) {
                n.discard();
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos home = NbtUtils.readBlockPos(tag, "Arena").orElse(null);
        BlockPos altar = NbtUtils.readBlockPos(tag, "Altar").orElse(null);
        if (home != null) {
            bindLair(home, altar == null ? home : altar);
        }
        relightOnLoad = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (arena != null) {
            tag.put("Arena", NbtUtils.writeBlockPos(arena.centreBlock()));
        }
        if (altarPos != null) {
            tag.put("Altar", NbtUtils.writeBlockPos(altarPos));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return getType().getDescription();
    }

    // ------------------------------------------------------------------ the voice (1.1)

    @Override
    public String voiceBoss() {
        return "unsung";
    }

    @Override
    public BlockPos voiceHome() {
        return arena != null ? arena.centreBlock() : blockPosition();
    }

    @Override
    public List<ServerPlayer> voiceFighters() {
        return fightersOnFloor();
    }

    @Override
    public int voicePlayers() {
        return playersAtStart;
    }

    @Override
    public double voiceHealth() {
        float max = totalMaxHealth();
        return max <= 0 ? 0.0 : Math.max(0.0, totalHealth() / max);
    }

    @Override
    public net.minecraft.resources.ResourceLocation voiceKillAdvancement() {
        return GuardianTypes.UNSUNG.advancement();
    }

    @Override
    public boolean voiceWokenByEcho() {
        return wokenByEcho;
    }

    /** The bar's thirds are the mask breaks (67 and 33), fired by the break itself. */
    @Override
    public java.util.Set<Integer> voicePhaseThresholds() {
        return java.util.Set.of(67, 33);
    }

    /** On a beat of the fight's clock, never on a Harmonize downbeat (Voice Script rule 8). */
    @Override
    public boolean voiceMayStart(long now) {
        return state == State.FIGHT && UnsungSong.onBeat(now, fightStart) && !UnsungSong.downbeat(UnsungSong.fightBeat(now, fightStart));
    }

    /** Ticks until the next Harmonize warning (none inside one); nothing to cover in the intro or after the last mask. */
    @Override
    public int voiceQuietTicks(long now) {
        if (state != State.FIGHT) {
            return Integer.MAX_VALUE;
        }
        long beat = UnsungSong.fightBeat(now, fightStart);
        if (UnsungSong.warning(beat)) {
            return 0;
        }
        long beatsLeft = UnsungMoves.CYCLE_BEATS - UnsungMoves.WARNING_BEATS - UnsungSong.inCycle(beat);
        return (int) (beatsLeft * BEAT - Math.floorMod(now - fightStart, BEAT));
    }

    /** The living masks in song order, as the takes of a break line are named: "123", "13", "2". */
    @Override
    public String voiceLiving() {
        StringBuilder s = new StringBuilder();
        for (Voice v : Voice.values()) {
            if (living().contains(v)) {
                s.append(v.ordinal() + 1);
            }
        }
        return s.toString();
    }

    @Override
    public int voiceMasks() {
        return living().size();
    }

    @Override
    public boolean voiceOwns(Entity hit) {
        return hit == this || hit instanceof UnsungMask m && m.choir() == this;
    }
}
