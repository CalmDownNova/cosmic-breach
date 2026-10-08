package com.cosmicbreach.mount;

import com.cosmicbreach.structure.choir.ChoirRules;
import com.cosmicbreach.structure.crypt.CryptConfig;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Drift Manta (GDD 8.1): a pale manta ray the size of a boat that swims through the Drift's air, singing. Health
 * 40. Ridden it truly flies inside the Drift and glides outside it ({@link MantaRules}); the dash key is its Phase
 * Blink. Tamed by call and response with the Resonance Chime ({@link MantaCall}): it sings three notes on Vesper's
 * beat and a player echoes them, each within the Choir Floor's window of its beat; three clean phrases and it is theirs,
 * a miss sends it off for 30 s. A wild one comes to the rock's edge, within reach, while a nearby player holds the Chime;
 * just tamed, it comes to its owner.
 *
 * <p>It never falls: it holds itself up (no gravity) and moves itself: by the rider's client when ridden, by its own
 * swimming otherwise ({@link #travel}).
 */
public class DriftManta extends CelestialMount implements FlyingAnimal {
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_CALL_START = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_NOTES = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_CLEAN = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_SUNG_AT = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_BLINK_AT = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_CALLER = SynchedEntityData.defineId(DriftManta.class, EntityDataSerializers.INT);

    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("hover");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("glide");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("rest");

    /** A Chime starts a call within this many blocks; a call ends if its player goes farther than {@link #CALL_LEASH}. */
    public static final double CALL_RANGE = 12.0;
    public static final double CALL_LEASH = 28.0;

    private final MantaCall call = new MantaCall();
    /** The rider's client: the flight's own speed, kept from tick to tick. */
    private Vec3 flightVelocity = Vec3.ZERO;
    private boolean flying;
    private float bankYaw;
    private @Nullable UUID caller;
    private @Nullable Vec3 retreatFrom;
    private @Nullable Vec3 swimTarget;
    private double swimSpeed = MantaRules.WILD_SPEED;
    /** Every Chime use it judged: {tick, verdict ordinal, note, offset or Long.MIN_VALUE}, for tests. */
    private final java.util.List<long[]> chimes = new java.util.ArrayList<>();

    public DriftManta(EntityType<? extends DriftManta> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, MantaRules.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MantaRules.FORWARD)
                .add(Attributes.FLYING_SPEED, MantaRules.FORWARD)
                .add(Attributes.SAFE_FALL_DISTANCE, 64.0)
                .add(Attributes.STEP_HEIGHT, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public MountGear.Kind kind() {
        return MountGear.Kind.MANTA;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, (byte) 0);
        builder.define(DATA_CALL_START, 0L);
        builder.define(DATA_NOTES, 0);
        builder.define(DATA_CLEAN, (byte) 0);
        builder.define(DATA_SUNG_AT, Long.MIN_VALUE / 2);
        builder.define(DATA_BLINK_AT, Long.MIN_VALUE / 2);
        builder.define(DATA_CALLER, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new ComeGoal());
        goalSelector.addGoal(1, new ListenGoal());
        goalSelector.addGoal(2, new RetreatGoal());
        goalSelector.addGoal(3, new LureGoal());
        goalSelector.addGoal(5, new RoamGoal());
    }

    // ------------------------------------------------------------------ where it is

    /** True inside the Drift's band of Aetheria, where it truly flies (either side). */
    public boolean inDrift() {
        return AetheriaWorld.is(level()) && Layer.at(getY()) == Layer.DRIFT;
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
    }

    @Override
    protected boolean keepsNoGravity() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isTamed() && !isVehicle() && !hasCustomName() && !isLeashed();
    }

    // ------------------------------------------------------------------ the call (synced state)

    public MantaCall.Phase callPhase() {
        return MantaCall.Phase.values()[Mth.clamp(entityData.get(DATA_PHASE), 0, MantaCall.Phase.values().length - 1)];
    }

    public long callStart() {
        return entityData.get(DATA_CALL_START);
    }

    public long answerStart() {
        return callStart() + MantaCall.BAR;
    }

    /** The phrase's pads, synced. */
    public int[] notes() {
        int packed = entityData.get(DATA_NOTES);
        int[] out = new int[MantaCall.NOTES];
        for (int i = 0; i < out.length; i++) {
            out[i] = (packed >> (4 * i)) & 0xF;
        }
        return out;
    }

    public int cleanPhrases() {
        return entityData.get(DATA_CLEAN);
    }

    public long sungAt() {
        return entityData.get(DATA_SUNG_AT);
    }

    /** The entity id of the player answering, or -1. */
    public int callerId() {
        return entityData.get(DATA_CALLER);
    }

    public long blinkAt() {
        return entityData.get(DATA_BLINK_AT);
    }

    /** The server's call, for tests and commands. */
    public MantaCall call() {
        return call;
    }

    public List<long[]> chimes() {
        return chimes;
    }

    private void syncCall() {
        entityData.set(DATA_PHASE, (byte) call.phase().ordinal());
        entityData.set(DATA_CALL_START, call.callStart());
        int[] n = call.notes();
        int packed = 0;
        for (int i = 0; i < n.length; i++) {
            packed |= (n[i] & 0xF) << (4 * i);
        }
        entityData.set(DATA_NOTES, packed);
        entityData.set(DATA_CLEAN, (byte) call.clean());
    }

    /** Pitch that plays pad {@code pad} (D4 up to D5) on a sound recorded at D4. */
    public static float pitchOf(int pad) {
        int p = Mth.clamp(pad, 0, MantaCall.NOTE_PADS - 1);
        return (float) (ChoirRules.HZ[p] / ChoirRules.HZ[0]);
    }

    /**
     * A Chime use by {@code player} near this manta (server side). Returns true if this manta took it: a call started,
     * or an answer judged (on time or not).
     */
    public boolean onChime(ServerPlayer player) {
        if (isTamed() || !isAlive()) {
            return false;
        }
        long now = level().getGameTime();
        if (call.phase() == MantaCall.Phase.RETREAT) {
            return false;
        }
        if (call.phase() == MantaCall.Phase.IDLE) {
            if (distanceTo(player) > CALL_RANGE) {
                return false;
            }
            if (!call.start(now, CryptConfig.window(), MantaCall.phrase(random::nextInt))) {
                return false;
            }
            caller = player.getUUID();
            entityData.set(DATA_CALLER, player.getId());
            syncCall();
            playSound(Mounts.MANTA_NOTE.get(), 0.8f, pitchOf(0));
            return true;
        }
        if (!player.getUUID().equals(caller)) {
            return false;
        }
        int note = call.answered();
        MantaCall.Verdict v = call.chime(now, grace(player));
        long offset = note < MantaCall.NOTES ? now - call.answerBeat(note) : Long.MIN_VALUE;
        chimes.add(new long[] {now, v.ordinal(), note, offset});
        switch (v) {
            case HIT, DONE -> {
                level().playSound(null, player.getX(), player.getY(), player.getZ(), Mounts.CHIME.get(), SoundSource.PLAYERS, 1.0f,
                        pitchOf(call.notes()[Math.min(note, MantaCall.NOTES - 1)]));
                spark(player, v == MantaCall.Verdict.DONE ? 10 : 4);
            }
            case MISS -> missed(player.position());
            case IGNORED -> {
            }
        }
        syncCall();
        return true;
    }

    private static int grace(@Nullable ServerPlayer player) {
        return player == null || player.connection == null ? 0 : ChoirRules.graceTicks(player.connection.latency());
    }

    private void spark(Player player, int count) {
        if (level() instanceof ServerLevel server) {
            Vec3 mid = position().add(player.position()).scale(0.5).add(0, 1.0, 0);
            server.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, count, 0.5, 0.3, 0.5, 0.02);
        }
    }

    private void missed(Vec3 from) {
        retreatFrom = from;
        playSound(Mounts.MANTA_SOUR.get(), 1.0f, 1.0f);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 12, 0.8, 0.2, 0.8, 0.02);
        }
    }

    private @Nullable ServerPlayer callerPlayer() {
        return caller != null && level() instanceof ServerLevel server && server.getPlayerByUUID(caller) instanceof ServerPlayer p ? p : null;
    }

    private void tickCall() {
        if (call.phase() == MantaCall.Phase.IDLE) {
            return;
        }
        long now = level().getGameTime();
        ServerPlayer player = callerPlayer();
        boolean calling = call.phase() == MantaCall.Phase.LEAD || call.phase() == MantaCall.Phase.CALL
                || call.phase() == MantaCall.Phase.ANSWER;
        if (calling && (player == null || !player.isAlive() || distanceTo(player) > CALL_LEASH)) {
            call.stop();
            caller = null;
            entityData.set(DATA_CALLER, -1);
            syncCall();
            return;
        }
        for (MantaCall.Event e : call.tick(now, grace(player), () -> MantaCall.phrase(random::nextInt))) {
            switch (e.kind()) {
                case SING -> {
                    playSound(Mounts.MANTA_NOTE.get(), 1.4f, pitchOf(e.pad()));
                    entityData.set(DATA_SUNG_AT, now);
                }
                case PHRASE_CLEAN -> playSound(Mounts.TAMED.get(), 0.5f, 1.5f);
                case TAMED -> {
                    if (player != null) {
                        tameTo(player);
                    }
                    playSound(Mounts.TAMED.get(), 1.0f, 1.0f);
                    caller = null;
                    entityData.set(DATA_CALLER, -1);
                }
                case MISSED_BEAT -> {
                    chimes.add(new long[] {now, MantaCall.Verdict.MISS.ordinal(), e.note(), Long.MIN_VALUE});
                    missed(player != null ? player.position() : position());
                }
                case RETREAT_OVER -> {
                    caller = null;
                    retreatFrom = null;
                    entityData.set(DATA_CALLER, -1);
                }
            }
        }
        syncCall();
    }

    /** Ends a retreat now (debug). */
    public void calm() {
        call.calm();
        caller = null;
        retreatFrom = null;
        entityData.set(DATA_CALLER, -1);
        syncCall();
    }

    @Override
    protected InteractionResult wildInteract(Player player, InteractionHand hand, ItemStack stack) {
        return InteractionResult.PASS; // the Chime does the taming (its own use)
    }

    @Override
    public void tameTo(Player player) {
        super.tameTo(player);
        comeTicks = MountCareRules.COME_TICKS;
    }

    // ------------------------------------------------------------------ Phase Blink

    /** True if a blink may start now (either side). */
    public boolean blinkReady() {
        return MantaRules.blinkReady(level().getGameTime(), blinkAt(), wears(MountGear.NEBULA_REINS));
    }

    public boolean shielded() {
        return MantaRules.shielded(level().getGameTime(), blinkAt());
    }

    /**
     * The rider's client blinks: along the rider's look, {@value MantaRules#BLINK_DISTANCE} blocks or until blocked
     * (the move is swept like any other, so the server's own check of the move agrees). Returns where it came from, or
     * null if it could not.
     */
    public @Nullable Vec3 blinkLocally(Player rider) {
        if (!blinkReady()) {
            return null;
        }
        double[] d = MantaRules.blinkDirection(rider.getYRot(), rider.getXRot(), inDrift());
        Vec3 from = position();
        move(MoverType.SELF, new Vec3(d[0], d[1], d[2]).scale(MantaRules.BLINK_DISTANCE));
        entityData.set(DATA_BLINK_AT, level().getGameTime());
        return from;
    }

    /** The server accepted a blink at {@code now}: the cooldown starts and the shield rises. */
    public void blinkAccepted(long now) {
        entityData.set(DATA_BLINK_AT, now);
    }

    // ------------------------------------------------------------------ movement

    @Override
    public void travel(Vec3 input) {
        if (!isAlive()) {
            super.travel(input);
            return;
        }
        boolean drift = inDrift();
        if (isControlledByLocalInstance() && getControllingPassenger() instanceof Player rider) {
            // fly from its own speed of last tick (vanilla scales a ridden mount's speed on the rider's client first)
            Vec3 last = flying ? flightVelocity : getDeltaMovement();
            double[] v = {last.x, last.y, last.z};
            // the rider's movement input: vanilla leaves it at 98% of the key's by the time the vehicle reads it
            double forward = Mth.clamp(rider.zza / 0.98f, -1f, 1f);
            double strafe = Mth.clamp(rider.xxa / 0.98f, -1f, 1f);
            double[] want = MantaRules.target(forward, strafe, riderJumping(), rider.getYRot(), rider.getXRot(), drift,
                    onGround(), wears(MountGear.GALE_FINS));
            v = MantaRules.step(v, want, drift);
            setDeltaMovement(v[0], v[1], v[2]);
            move(MoverType.SELF, getDeltaMovement());
            flightVelocity = getDeltaMovement(); // after collisions
            flying = true;
            calculateEntityAnimation(true);
            return;
        }
        flying = false;
        if (isControlledByLocalInstance()) {
            swimOnItsOwn(drift);
            return;
        }
        super.travel(input);
    }

    private void swimOnItsOwn(boolean drift) {
        Vec3 v = getDeltaMovement();
        Vec3 want = Vec3.ZERO;
        if (swimTarget != null) {
            Vec3 d = swimTarget.subtract(position());
            double len = d.length();
            if (len > 0.6) {
                want = d.scale(Math.min(swimSpeed, len * 0.08) / len);
            }
        }
        double wy = want.y;
        if (!drift) {
            wy = onGround() ? 0.0 : Math.min(wy, -MantaRules.GLIDE_SINK);
        }
        double hx = want.x;
        double hz = want.z;
        if (!drift && onGround()) {
            hx *= MantaRules.GROUND_SHARE;
            hz *= MantaRules.GROUND_SHARE;
        }
        double k = 0.08;
        Vec3 next = new Vec3(v.x + (hx - v.x) * k, v.y + (wy - v.y) * k, v.z + (hz - v.z) * k);
        if (!drift && next.y > 0) {
            next = new Vec3(next.x, 0, next.z);
        }
        setDeltaMovement(next);
        move(MoverType.SELF, next);
        double h = Math.hypot(next.x, next.z);
        if (h > 0.02) {
            float face = (float) (Mth.atan2(next.z, next.x) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.approachDegrees(getYRot(), face, 5.0f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        calculateEntityAnimation(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAlive()) {
            tickCall();
            tickSong();
        }
    }

    /** Now and then, between calls, it sings a phrase of its own on the beat (softer than a call). */
    private void tickSong() {
        long now = level().getGameTime();
        if (songStart < 0) {
            if (call.phase() == MantaCall.Phase.IDLE && random.nextInt(isTamed() ? 600 : 300) == 0) {
                songStart = MantaCall.nextCallStart(now);
                songNotes = MantaCall.phrase(random::nextInt);
            }
            return;
        }
        if (call.phase() != MantaCall.Phase.IDLE) {
            songStart = -1;
            return;
        }
        long i = now - songStart;
        if (i >= 0 && i % MantaCall.BEAT == 0 && i / MantaCall.BEAT < MantaCall.NOTES) {
            playSound(Mounts.MANTA_NOTE.get(), 0.7f, pitchOf(songNotes[(int) (i / MantaCall.BEAT)]));
            entityData.set(DATA_SUNG_AT, now);
        }
        if (i > (long) MantaCall.BEAT * MantaCall.NOTES) {
            songStart = -1;
        }
    }

    private long songStart = -1;
    private int[] songNotes = new int[MantaCall.NOTES];

    /** Client: the body's pitch and bank in degrees, smoothed (the model tilts by them). */
    public float tilt;
    public float tiltO;
    public float bank;
    public float bankO;

    @Override
    public void tick() {
        if (!isNoGravity()) {
            setNoGravity(true);
        }
        super.tick();
        if (level().isClientSide) {
            tiltO = tilt;
            bankO = bank;
            double dy = getY() - yo;
            double h = Math.hypot(getX() - xo, getZ() - zo);
            float wantTilt = h + Math.abs(dy) < 0.01 || onGround() ? 0f
                    : (float) Mth.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 0.08))), -35.0, 35.0);
            tilt += (wantTilt - tilt) * 0.15f;
            float turn = Mth.wrapDegrees(getYRot() - bankYaw); // its own last yaw: riding resets yRotO every tick
            bankYaw = getYRot();
            float wantBank = onGround() ? 0f : Mth.clamp(-turn * 4f, -30f, 30f);
            bank += (wantBank - bank) * 0.15f;
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNoGravity(true);
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null; // it sings on the beat instead (see the client's song)
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return Mounts.MANTA_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return Mounts.MANTA_HURT.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && !isTamed() && source.getEntity() instanceof Player p
                && call.phase() != MantaCall.Phase.RETREAT) {
            call.stop();
            retreatFrom = p.position(); // hurt: it swims off
            fleeTicks = 100;
            syncCall();
        }
        return hurt;
    }

    private int fleeTicks;

    /** Ticks left of coming to its owner after taming (1.1 design 4: within reach, not left hanging out of it). */
    private int comeTicks;
    /** When the way to a player is blocked it rises this many blocks at a time, up to {@value #RISE_UNTIL} above them. */
    private static final double RISE = 3.0;
    private static final double RISE_UNTIL = 12.0;

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 6, this::pose));
    }

    private PlayState pose(AnimationState<DriftManta> state) {
        if (onGround()) {
            return state.setAndContinue(REST);
        }
        double speed = Math.hypot(getX() - xo, getZ() - zo);
        if (!inDrift() && getY() - yo < -0.02) {
            return state.setAndContinue(GLIDE);
        }
        return state.setAndContinue(speed > 0.05 || Math.abs(getY() - yo) > 0.05 ? SWIM : HOVER);
    }

    // ------------------------------------------------------------------ goals

    /** True if the {@code blocks} under {@code at} are open air (it swims well clear of the rock). */
    private boolean clearBelow(Vec3 at, int blocks) {
        net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i <= blocks; i++) {
            if (!level().getBlockState(p.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** True if the line from here to {@code target} is open air (the test {@link #swimTo} makes). */
    private boolean openWayTo(Vec3 target) {
        Vec3 eye = position().add(0, 0.4, 0);
        return level().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                == HitResult.Type.MISS;
    }

    /** Swims to a point in open air (a clear line from here). */
    private boolean swimTo(Vec3 target, double speed) {
        if (!openWayTo(target)) {
            return false;
        }
        swimTarget = target;
        swimSpeed = speed;
        return true;
    }

    /**
     * Where to head to reach one of {@code spots} (the best first): the first whose way from here is open; failing that a
     * point {@value #RISE} blocks above here, to rise over what is in the way, while it is below {@code ceiling} and that
     * way is open; failing that the best spot after all. Null only with no spots and nothing to rise to.
     */
    private @Nullable Vec3 wayTo(List<Vec3> spots, double ceiling) {
        for (Vec3 spot : spots) {
            if (openWayTo(spot)) {
                return spot;
            }
        }
        Vec3 up = position().add(0, RISE, 0);
        if (getY() < ceiling && openWayTo(up)) {
            return up;
        }
        return spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * The free spots for its body about {@value MountCareRules#COME_DISTANCE} blocks from {@code player}, the side this
     * stingray is on first, then round to the far side, and last the one above them.
     */
    private List<Vec3> besidePlayerSpots(Player player) {
        List<Vec3> spots = new ArrayList<>();
        Vec3 toMe = position().subtract(player.position());
        double base = Math.atan2(toMe.z, toMe.x);
        for (int i = 0; i < MountCareRules.COME_DIRECTIONS; i++) {
            double a = MountCareRules.comeAngle(base, i);
            Vec3 spot = player.position().add(Math.cos(a) * MountCareRules.COME_DISTANCE, 0.6, Math.sin(a) * MountCareRules.COME_DISTANCE);
            if (level().noCollision(this, getBoundingBox().move(spot.subtract(position())))) {
                spots.add(spot);
            }
        }
        Vec3 above = player.position().add(0, 2.4, 0);
        if (level().noCollision(this, getBoundingBox().move(above.subtract(position())))) {
            spots.add(above);
        }
        return spots;
    }

    /** Just tamed: it swims to its owner and waits beside them, well inside their reach. */
    private final class ComeGoal extends Goal {
        ComeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return comeTicks > 0 && isTamed() && !isVehicle() && getOwner() instanceof Player;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            comeTicks--;
            if (!(getOwner() instanceof Player owner) || owner.level() != level()) {
                comeTicks = 0;
                return;
            }
            List<Vec3> spots = besidePlayerSpots(owner);
            Vec3 way = spots.isEmpty() ? null : wayTo(spots, owner.getY() + RISE_UNTIL);
            if (way == null) {
                return;
            }
            if (spots.contains(way) && position().distanceTo(way) < 0.5) {
                comeTicks = 0; // there
                return;
            }
            swimTarget = way;
            swimSpeed = 0.25;
            float face = (float) (Mth.atan2(owner.getZ() - getZ(), owner.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.approachDegrees(getYRot(), face, 8.0f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }

        @Override
        public void stop() {
            swimTarget = null;
        }
    }

    /** The nearest player within {@value MantaLure#RANGE} blocks holding a Resonance Chime, on their own feet, or null. */
    private @Nullable Player lurer() {
        Player best = null;
        double bestD = MantaLure.RANGE * MantaLure.RANGE;
        for (Player p : level().players()) {
            boolean chime = p.getMainHandItem().is(Mounts.RESONANCE_CHIME.get()) || p.getOffhandItem().is(Mounts.RESONANCE_CHIME.get());
            if (!chime || p.isSpectator() || p.isPassenger()) {
                continue;
            }
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Wild and calm, it comes to the rock's edge nearest a player holding a Resonance Chime, within their reach. */
    private final class LureGoal extends Goal {
        private int retarget;

        LureGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isTamed() && !isVehicle() && call.phase() == MantaCall.Phase.IDLE && retreatFrom == null && fleeTicks <= 0
                    && lurer() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            retarget = 0;
        }

        @Override
        public void tick() {
            Player p = lurer();
            if (p == null) {
                return;
            }
            if (--retarget <= 0) {
                retarget = 10;
                net.minecraft.core.BlockPos feet = p.blockPosition();
                List<Vec3> spots = MantaLure.edgeSpots((x, y, z) -> {
                    net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(x, y, z);
                    return !level().getBlockState(at).getCollisionShape(level(), at).isEmpty();
                }, feet.getX(), feet.getY(), feet.getZ());
                // the nearest edge whose way is open, so one behind a rock goes round to another (and on the next look,
                // from there, to the nearest after all); with no edge at all it comes to the player's side
                swimTarget = wayTo(spots.isEmpty() ? besidePlayerSpots(p) : spots, p.getY() + RISE_UNTIL);
                swimSpeed = 0.2;
            }
            float face = (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.approachDegrees(getYRot(), face, 6.0f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }

        @Override
        public void stop() {
            swimTarget = null;
        }
    }

    /** Calling: it hangs in the air a few blocks from the player answering it, facing them. */
    private final class ListenGoal extends Goal {
        ListenGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            MantaCall.Phase p = call.phase();
            return !isVehicle() && (p == MantaCall.Phase.LEAD || p == MantaCall.Phase.CALL || p == MantaCall.Phase.ANSWER)
                    && callerPlayer() != null;
        }

        @Override
        public void tick() {
            ServerPlayer p = callerPlayer();
            if (p == null) {
                return;
            }
            Vec3 toMe = position().subtract(p.position());
            Vec3 flat = new Vec3(toMe.x, 0, toMe.z);
            if (flat.lengthSqr() < 1e-4) {
                flat = new Vec3(1, 0, 0);
            }
            Vec3 spot = p.position().add(flat.normalize().scale(5.0)).add(0, 2.6, 0);
            swimTarget = spot;
            swimSpeed = 0.1;
            float face = (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.approachDegrees(getYRot(), face, 6.0f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }

        @Override
        public void stop() {
            swimTarget = null;
        }
    }

    /** Sent off: it swims well away from the player who missed, then drifts. */
    private final class RetreatGoal extends Goal {
        RetreatGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !isVehicle() && retreatFrom != null && (call.phase() == MantaCall.Phase.RETREAT || fleeTicks > 0);
        }

        @Override
        public void start() {
            aim();
        }

        @Override
        public void tick() {
            if (fleeTicks > 0) {
                fleeTicks--;
            }
            if (swimTarget == null || position().distanceTo(swimTarget) < 2.0 || horizontalCollision) {
                aim();
            }
        }

        private void aim() {
            if (retreatFrom == null) {
                return;
            }
            Vec3 away = position().subtract(retreatFrom);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1e-4) {
                flat = new Vec3(getRandom().nextDouble() - 0.5, 0, getRandom().nextDouble() - 0.5);
            }
            Vec3 dir = flat.normalize();
            for (int i = 0; i < 8; i++) {
                double turn = (i == 0 ? 0 : (getRandom().nextDouble() - 0.5) * 1.6);
                Vec3 d = dir.yRot((float) turn);
                Vec3 target = retreatFrom.add(d.scale(30.0)).add(0, getY() - retreatFrom.y + (getRandom().nextDouble() - 0.3) * 4, 0);
                if (swimTo(target, 0.3)) {
                    return;
                }
            }
            swimTarget = position().add(dir.scale(6.0));
            swimSpeed = 0.3;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            swimTarget = null;
            if (call.phase() != MantaCall.Phase.RETREAT && fleeTicks <= 0) {
                retreatFrom = null;
            }
        }
    }

    /** A wild manta roams the Drift's open air in slow loops; a tamed one waits where it was left. */
    private final class RoamGoal extends Goal {
        private int wait;

        RoamGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isTamed() || isVehicle()) {
                return false;
            }
            if (--wait > 0) {
                return false;
            }
            wait = 40 + getRandom().nextInt(80);
            for (int i = 0; i < 6; i++) {
                double a = getRandom().nextDouble() * Math.PI * 2;
                double r = 8 + getRandom().nextDouble() * (MantaRules.ROAM - 8);
                double y = getY() + (getRandom().nextDouble() - 0.5) * 8;
                if (AetheriaWorld.is(level())) {
                    y = Mth.clamp(y, Layer.DRIFT.bandMinY + 8, Layer.DRIFT.bandMaxY - 8);
                }
                Vec3 target = new Vec3(getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r);
                if (clearBelow(target, 3) && swimTo(target, MantaRules.WILD_SPEED)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !isTamed() && !isVehicle() && swimTarget != null && position().distanceTo(swimTarget) > 1.5
                    && call.phase() == MantaCall.Phase.IDLE && !horizontalCollision;
        }

        @Override
        public void stop() {
            swimTarget = null;
        }
    }
}
