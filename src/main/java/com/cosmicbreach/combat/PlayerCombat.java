package com.cosmicbreach.combat;

import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.GroundWave;
import com.cosmicbreach.combat.server.InputRateLimiter;
import com.cosmicbreach.combat.server.SyncThrottle;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's combat state: an attachment on every player, on both sides, never saved. The server's
 * copy is the authority. The client's copy of its own player predicts moves for instant feedback.
 *
 * <p>Shared by both sides: the {@link CombatStateMachine} (its moves come from this side's
 * {@link CombatData}), applying inputs with the body's context, the weapon from the main hand, per-use
 * hit bookkeeping and the dash counter. Server only: {@link ServerState}, which does not exist on the
 * client. The server's combat logic itself lives in {@code combat.server}.
 */
public final class PlayerCombat {
    /** Inputs one player may send per server tick; the rest are dropped. */
    public static final int MAX_INPUTS_PER_TICK = 8;
    /** A plunge landing also cancels fall damage this many ticks after the machine saw it. */
    private static final int PLUNGE_FALL_GRACE_TICKS = 2;

    private final Player player;
    private final CombatStateMachine machine;
    private final HitLedger hits = new HitLedger();
    private final @Nullable ServerState server;
    private int dashSerial;

    public PlayerCombat(Player player) {
        this.player = player;
        boolean clientSide = player.level().isClientSide();
        this.machine = new CombatStateMachine(CombatData.forSide(clientSide)::move);
        this.server = clientSide ? null : new ServerState();
    }

    public static PlayerCombat of(Player player) {
        return player.getData(ModAttachments.PLAYER_COMBAT);
    }

    /** The player's combat state if anything ever created it, without creating it. */
    public static @Nullable PlayerCombat existing(Player player) {
        return player.getExistingDataOrNull(ModAttachments.PLAYER_COMBAT);
    }

    public CombatStateMachine machine() {
        return machine;
    }

    public HitLedger hits() {
        return hits;
    }

    /** The body as the machine needs it right now (riding anything counts as mounted, GDD 8.1). */
    public Context context() {
        return new Context(player.onGround(), player.getXRot(), player.getY(), player.isPassenger());
    }

    /** The combat weapon in the main hand (the weapons are two-handed; the off hand never counts). */
    public @Nullable WeaponDef heldWeapon() {
        return CombatWeaponItem.weaponOf(player.getMainHandItem(), player.level().isClientSide());
    }

    /** Tells the machine what is in hand. Switching weapons cancels whatever was in progress. */
    public void syncWeapon() {
        machine.setWeapon(heldWeapon());
    }

    public void apply(CombatAction action) {
        apply(action, context());
    }

    /** Feeds one input edge to the machine, with the given body context for presses. */
    public void apply(CombatAction action, Context ctx) {
        switch (action) {
            case ATTACK_PRESS -> machine.pressAttack(ctx);
            case ATTACK_RELEASE -> machine.releaseAttack(ctx);
            case ABILITY_PRESS -> machine.pressAbility(ctx);
            case ABILITY_RELEASE -> machine.releaseAbility();
            case DASH -> {
                machine.pressDash(ctx);
                if (machine.isDashing() && machine.dashElapsed() == 0) {
                    dashSerial++;
                }
            }
            case PARRY -> machine.pressParry();
        }
    }

    /**
     * One game tick: the weapon from the main hand, the effective stats from the player's attributes
     * (synced to the client, so prediction uses the same numbers), what the worn gear adds and cosmic
     * weather's multipliers (both from synced state too, see {@link CombatHooks#applyTo}), then the machine
     * with the body's context.
     */
    public List<CombatEvent> tick() {
        syncWeapon();
        machine.setStats(ProgressionStats.of(player));
        CombatHooks.applyTo(machine, player, com.cosmicbreach.world.weather.CosmicWeather.resonanceGain(player),
                com.cosmicbreach.world.weather.CosmicWeather.abilityCostScale(player));
        return machine.tick(context());
    }

    /** Counts dashes as they start, so a reward can be limited to once per dash. */
    public int dashSerial() {
        return dashSerial;
    }

    /** True from a plunge's press until it lands: the landing takes no fall damage. */
    public boolean isPlunging() {
        MoveInstance move = machine.current();
        if (move == null || move.def().kind() != MoveKind.PLUNGE) {
            return false;
        }
        CombatStateMachine.Phase phase = machine.phase();
        return phase == CombatStateMachine.Phase.STARTUP || phase == CombatStateMachine.Phase.PLUNGING;
    }

    /** Whether a fall ending at {@code gameTime} is a plunge landing (no fall damage). */
    public boolean protectsFromFall(long gameTime) {
        if (isPlunging()) {
            return true;
        }
        return server != null && gameTime - server.plungeLandedAt <= PLUNGE_FALL_GRACE_TICKS;
    }

    /** The server's bookkeeping. Throws on the client, where it does not exist. */
    public ServerState server() {
        if (server == null) {
            throw new IllegalStateException("PlayerCombat.server() on the client");
        }
        return server;
    }

    public boolean isServerSide() {
        return server != null;
    }

    /** What only the server keeps per player. */
    public static final class ServerState {
        private final InputRateLimiter inputs = new InputRateLimiter(MAX_INPUTS_PER_TICK);
        private final SyncThrottle sync = new SyncThrottle();
        private final List<GroundWave> waves = new ArrayList<>();
        private int rewardedDashSerial = -1;
        private long plungeLandedAt = Long.MIN_VALUE / 2;
        private double lastPlungeFall;
        /** Where a Zenith lift started (feet Y), NaN when no lift is in the air. */
        private double liftFromY = Double.NaN;
        /** How far the body had already fallen when the lift started. */
        private double liftFallBefore;
        private int liftTicks;
        private boolean liftLeftGround;
        /** A lift that never leaves the ground (blocked, or the client never rose) is forgotten after this long. */
        private static final int LIFT_GIVE_UP_TICKS = 200;

        /**
         * Zenith lifted the player (the ability held): until it lands, a fall counts only from where the lift
         * started, so rising and hanging with the foes never hurts on the way down, but dropping below that point
         * (off a ledge) still does.
         */
        public void lifted(double fromY, double fallBefore) {
            liftFromY = fromY;
            liftFallBefore = Math.max(0.0, fallBefore);
            liftTicks = 0;
            liftLeftGround = false;
        }

        /**
         * Once a server tick: a lift that left the ground and is back on it is over (its landing already counted),
         * and one that never left it is dropped after a while. The client moves the body, so the server may see
         * the rise a few ticks after its own machine lifted.
         */
        public void tickLift(boolean onGround) {
            if (Double.isNaN(liftFromY)) {
                return;
            }
            liftTicks++;
            if (!onGround) {
                liftLeftGround = true;
            } else if (liftLeftGround || liftTicks > LIFT_GIVE_UP_TICKS) {
                liftFromY = Double.NaN;
            }
        }

        /** The fall distance a landing at {@code landingY} really counts, given the fall the body measured. */
        public double liftedFall(double measured, double landingY) {
            if (Double.isNaN(liftFromY)) {
                return measured;
            }
            double counted = liftFallBefore + Math.max(0.0, liftFromY - landingY);
            liftFromY = Double.NaN;
            return Math.min(measured, counted);
        }

        public boolean lifting() {
            return !Double.isNaN(liftFromY);
        }

        public InputRateLimiter inputs() {
            return inputs;
        }

        public SyncThrottle sync() {
            return sync;
        }

        /** Ground waves still travelling. */
        public List<GroundWave> waves() {
            return waves;
        }

        /** True the first time it is asked for this dash: a perfect dodge is rewarded once per dash. */
        public boolean claimPerfectDodge(int dashSerial) {
            if (rewardedDashSerial == dashSerial) {
                return false;
            }
            rewardedDashSerial = dashSerial;
            return true;
        }

        public void plungeLanded(long gameTime, double fallBlocks) {
            plungeLandedAt = gameTime;
            lastPlungeFall = fallBlocks;
        }

        public long plungeLandedAt() {
            return plungeLandedAt;
        }

        public double lastPlungeFall() {
            return lastPlungeFall;
        }
    }
}
