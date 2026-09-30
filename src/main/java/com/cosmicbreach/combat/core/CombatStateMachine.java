package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatEvent.ActiveTick;
import com.cosmicbreach.combat.core.CombatEvent.ChargeCancelled;
import com.cosmicbreach.combat.core.CombatEvent.ChargeFull;
import com.cosmicbreach.combat.core.CombatEvent.ChargeReady;
import com.cosmicbreach.combat.core.CombatEvent.ChargeStarted;
import com.cosmicbreach.combat.core.CombatEvent.DashEnded;
import com.cosmicbreach.combat.core.CombatEvent.DashStarted;
import com.cosmicbreach.combat.core.CombatEvent.Denied;
import com.cosmicbreach.combat.core.CombatEvent.MoveCancelled;
import com.cosmicbreach.combat.core.CombatEvent.MoveEnded;
import com.cosmicbreach.combat.core.CombatEvent.MoveStarted;
import com.cosmicbreach.combat.core.CombatEvent.ParryStarted;
import com.cosmicbreach.combat.core.CombatEvent.ParrySucceeded;
import com.cosmicbreach.combat.core.CombatEvent.ParryWhiffed;
import com.cosmicbreach.combat.core.CombatEvent.PerfectDodge;
import com.cosmicbreach.combat.core.CombatEvent.PlungeLanded;
import com.cosmicbreach.combat.core.CombatEvent.ResonanceFull;
import com.cosmicbreach.combat.core.CombatEvent.Rise;
import com.cosmicbreach.combat.core.CombatEvent.Staggered;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits.CancelBy;
import com.cosmicbreach.combat.data.MoveTraits.Stage;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The move state machine (GDD section 4.1). Pure logic with no world access, so it is unit-tested and
 * runs identically on both sides: the server runs one per player as the authority, and the client runs
 * one for its own player to predict animations and the HUD.
 *
 * <p>Call the {@code press...}/{@code release...} methods when input arrives, then {@link #tick} once per
 * game tick; {@code tick} returns everything that happened since the last call, input effects included.
 * The tick in which a move starts is its startup tick 0.
 *
 * <p>A move's {@link MoveTraits} add to the rules: hyper armor ({@link #poiseBonus}), cancel windows that
 * may open before recovery (each input cancel reports a {@link MoveCancelled}), root and walk speed
 * ({@link #movementMultiplier}), and the ability's second press ({@link WeaponDef.Recast}). A stagger
 * ({@link #onStaggered}) stops whatever runs and locks every input for its ticks.
 *
 * <p>For the Binary Edges: a weapon's {@link WeaponDef.DashFollow} (the Weave) starts the chain at a later
 * move when an attack comes soon after a dash; an ability with {@code cooldown_after_recast} holds its
 * cooldown back until its second press is over (the thrown blade came back, or the blink arrived); a move's
 * {@code invulnerable} phases add i-frames ({@link #isInvulnerable}). Switching weapons closes an open
 * second press.
 *
 * <p>For the Choir Astrolabe: a weapon's {@code aerial} is a timed move an attack starts in the air (its Starfall),
 * where the melee weapons' aerial is their plunge; a plunge, when the weapon has one and the aim is down, comes first.
 *
 * <p>For the accessories (GDD 5.2): gear adds max Resonance ({@link #setGearResonance}), parry window ticks
 * ({@link #setGearParryWindow}) and free dashes for each time in the air ({@link #setGearAirDashes}), and gear that
 * earns Resonance by its own rule hands it over with {@link #grantResonance}.
 */
public final class CombatStateMachine {

    public enum Phase { IDLE, STARTUP, ACTIVE, RECOVERY, CHARGING, PLUNGING }

    /**
     * What the machine needs to know about the body. {@code mounted}: riding (GDD 8.1): only the light chain's first
     * {@value CombatRules#MOUNTED_CHAIN} moves and the ability; no charge, plunge, aerial or dash attack, and the dash
     * key is the mount's.
     */
    public record Context(boolean onGround, float pitch, double y, boolean mounted) {
        public static final Context GROUNDED = new Context(true, 0f, 64.0);

        /** On foot. */
        public Context(boolean onGround, float pitch, double y) {
            this(onGround, pitch, y, false);
        }
    }

    @FunctionalInterface
    public interface MoveLookup {
        @Nullable
        MoveDef get(ResourceLocation id);
    }

    private static final int FAR = 1_000;
    private static final int MAX_PLUNGE_TICKS = 200;
    private static final MoveDef.Charge DEFAULT_CHARGE = new MoveDef.Charge(12, 24);

    private final MoveLookup moves;
    private @Nullable WeaponDef weapon;
    private StatBlock stats = StatBlock.ZERO;

    private Phase phase = Phase.IDLE;
    private int phaseTick;
    private @Nullable MoveInstance current;
    private @Nullable ResourceLocation nextCombo;
    private int comboWindowLeft;
    private boolean buffered;
    private @Nullable ResourceLocation chargeFromNext;
    private double plungeStartY;

    private boolean attackHeld;
    private int attackHeldTicks;
    private boolean abilityHeld;

    private int dashElapsed = -1;
    private int ticksSinceDashEnd = FAR;
    private int dashCharges;
    private double dashRecharge;

    private int parryElapsed = -1;
    private int parryWhiffLeft;

    private double resonance;
    private int ticksSinceCombat = FAR;
    private int riposteLeft;
    private int critWindowLeft;
    private int abilityCooldownLeft;
    private int latencyGrace;
    private int serial;
    private int staggerLeft;
    private @Nullable ResourceLocation recastMove;
    private int recastLeft;
    /** An ability's cooldown held back until its second press is over ({@code cooldown_after_recast}); 0 for none. */
    private int pendingCooldown;
    /** The serial of the second press's move while it runs, else -1. */
    private int recastSerial = -1;
    /** Weather's multiplier on Resonance earned (a Solar Flare doubles it); 1 in calm weather. */
    private double resonanceGain = 1.0;
    /** Weather's multiplier on ability costs (an Eclipse Surge makes it 0.75); 1 in calm weather. */
    private double abilityCostScale = 1.0;
    /** Dash charges gear adds (GDD 3.4 "more from gear": the Driftweave's +1). */
    private int gearDashCharges;
    /** Haste from gear and buffs (the Hymn of Alignment's +30), on top of 1.5 x Arcane. */
    private double gearHaste;
    /** The share of max Resonance the out-of-combat drift settles at (the Choir Regalia makes it 0.5). */
    private double driftTarget = CombatRules.DRIFT_TARGET;
    /** True while a dash in the air costs no charge (the Driftweave's Drift). */
    private boolean freeAirDashes;
    /** Max Resonance gear adds (GDD 3.4 "Max = 100 + 3 x Arcane + gear": the Choir Pendant's +20). */
    private int gearResonance;
    /** Ticks gear adds to the parry window (the Event Horizon Lens's +2). */
    private int gearParryTicks;
    /** Dashes each time in the air that cost no charge, spent before the charges (the Twin Comet Band's +1). */
    private int gearAirDashes;
    /** How many of those this time in the air has used; back to none on the ground. */
    private int airDashesUsed;

    private final List<CombatEvent> events = new ArrayList<>();

    public CombatStateMachine(MoveLookup moves) {
        this.moves = moves;
        this.dashCharges = maxDashCharges();
    }

    // ------------------------------------------------------------------ setup

    /** Change the held weapon. Anything in progress is cancelled and the chain resets. */
    public void setWeapon(@Nullable WeaponDef newWeapon) {
        if (newWeapon == weapon || (newWeapon != null && newWeapon.equals(weapon))) {
            return;
        }
        if (current != null) {
            endMove(true);
        }
        if (phase == Phase.CHARGING) {
            events.add(new ChargeCancelled());
        }
        toIdleWithCombo(null);
        buffered = false;
        weapon = newWeapon;
        disarmRecast(); // the second press belonged to the weapon put away (its thrown blade comes back)
    }

    /**
     * The effective attribute points. Charges and Resonance above a lowered maximum are cut to it;
     * a raised maximum fills the usual way (charges recharge, Resonance is earned).
     */
    public void setStats(StatBlock newStats) {
        this.stats = newStats;
        this.dashCharges = Math.min(dashCharges, maxDashCharges());
        this.resonance = Math.min(resonance, maxResonance());
    }

    public StatBlock stats() {
        return stats;
    }

    /**
     * Cosmic weather (GDD 2.5): Resonance earned from hits, perfect dodges and parries is multiplied by
     * {@code gain} (2 in a Solar Flare). The out-of-combat drift is not earned and stays as it is. Both sides
     * set it every tick from the synced weather, so prediction agrees with the server.
     */
    public void setResonanceGain(double gain) {
        this.resonanceGain = Math.max(0.0, gain);
    }

    public double resonanceGain() {
        return resonanceGain;
    }

    /**
     * Resonance taken from outside (the Unsung's Silence drains 30): never below zero, untouched by the gain, and
     * it earns nothing. Server side; the client catches up through the usual sync. Returns how much was taken.
     */
    public double drainResonance(double amount) {
        if (!(amount > 0.0)) {
            return 0.0;
        }
        double taken = Math.min(resonance, amount);
        resonance -= taken;
        return taken;
    }

    /** Cosmic weather: ability costs are multiplied by {@code scale} (0.75 in an Eclipse Surge). Both sides. */
    public void setAbilityCostScale(double scale) {
        this.abilityCostScale = Math.max(0.0, scale);
    }

    public double abilityCostScale() {
        return abilityCostScale;
    }

    /**
     * Dash charges armor adds (GDD 3.4: "more from gear"). A lower count cuts the charges to the new maximum; a
     * higher one recharges the usual way. Both sides set it every tick from the worn gear.
     */
    public void setGearDashCharges(int charges) {
        this.gearDashCharges = Math.max(0, charges);
        this.dashCharges = Math.min(dashCharges, maxDashCharges());
    }

    public int gearDashCharges() {
        return gearDashCharges;
    }

    /** Haste from gear and buffs, added to Arcane's (GDD 3.4: Haste = 1.5 x Arcane + gear). Both sides. */
    public void setGearHaste(double haste) {
        this.gearHaste = Math.max(0.0, haste);
    }

    public double gearHaste() {
        return gearHaste;
    }

    /**
     * The share of max Resonance the out-of-combat drift settles at, 0 to 1 (GDD 3.4: 30%; the Choir Regalia
     * makes it 50%). NaN restores the engine's 30%. Both sides.
     */
    public void setDriftTarget(double share) {
        this.driftTarget = Double.isNaN(share) ? CombatRules.DRIFT_TARGET : Math.max(0.0, Math.min(1.0, share));
    }

    public double driftTarget() {
        return driftTarget;
    }

    /** While true a dash started in the air costs no charge (and works with none left). Both sides. */
    public void setFreeAirDashes(boolean free) {
        this.freeAirDashes = free;
    }

    public boolean freeAirDashes() {
        return freeAirDashes;
    }

    /**
     * Max Resonance gear adds (the Choir Pendant's +20). Resonance above a lowered maximum is cut to it; a raised one
     * fills the usual way. Both sides set it every tick from the worn gear.
     */
    public void setGearResonance(int bonus) {
        this.gearResonance = Math.max(0, bonus);
        this.resonance = Math.min(resonance, maxResonance());
    }

    public int gearResonance() {
        return gearResonance;
    }

    /** Ticks gear adds to the parry window (the Event Horizon Lens's +2). Both sides. */
    public void setGearParryWindow(int ticks) {
        this.gearParryTicks = Math.max(0, ticks);
    }

    public int gearParryWindow() {
        return gearParryTicks;
    }

    /** How long a parry stays up, before latency grace: 4 + floor(Resilience / 15) + gear (GDD 3.4). */
    public int parryWindow() {
        return CombatMath.parryWindow(stats, gearParryTicks);
    }

    /**
     * Dashes each time in the air that cost no charge (the Twin Comet Band's +1), spent before the charges; landing
     * gives them back. Both sides set it every tick from the worn gear.
     */
    public void setGearAirDashes(int dashes) {
        this.gearAirDashes = Math.max(0, dashes);
    }

    public int gearAirDashes() {
        return gearAirDashes;
    }

    /** The free air dashes this time in the air still has. */
    public int airDashesLeft() {
        return Math.max(0, gearAirDashes - airDashesUsed);
    }

    /**
     * Resonance earned by gear outside the engine's own rules (the Choir Pendant's ability hits): multiplied by the gain
     * like every earned point, capped at the maximum, and it counts as fighting. Server side; the client hears it through
     * the sync.
     */
    public void grantResonance(double amount) {
        if (!(amount > 0.0)) {
            return;
        }
        addResonance(amount);
        markCombat();
    }

    /** What the held weapon's ability costs in Resonance right now, weather included; 0 without one. */
    public double abilityCost() {
        return weapon == null || weapon.ability().isEmpty() ? 0.0 : weapon.ability().get().cost() * abilityCostScale;
    }

    /** The server extends dodge and parry windows by the player's one-way latency, capped. */
    public void setLatencyGrace(int ticks) {
        this.latencyGrace = Math.max(0, Math.min(CombatRules.MAX_LATENCY_GRACE, ticks));
    }

    /**
     * Client side: accept the server's authoritative numbers. A running cooldown from the server while this
     * side still holds one back means the server's second press is over (its blade came back): so is this one.
     */
    public void syncFromServer(double serverResonance, int serverDashCharges, int serverAbilityCooldown) {
        this.resonance = Math.max(0, Math.min(maxResonance(), serverResonance));
        this.dashCharges = Math.max(0, Math.min(maxDashCharges(), serverDashCharges));
        this.abilityCooldownLeft = Math.max(0, serverAbilityCooldown);
        if (serverAbilityCooldown > 0 && pendingCooldown > 0) {
            pendingCooldown = 0;
            if (recastSerial < 0) {
                closeRecast();
            }
        }
    }

    // ------------------------------------------------------------------ input

    public void pressAttack(Context ctx) {
        attackHeld = true;
        attackHeldTicks = 0;
        if (weapon == null) {
            return;
        }
        if (staggerLeft > 0) {
            events.add(new Denied(CombatEvent.Action.STAGGERED));
            return;
        }
        if (parryElapsed >= 0 || parryWhiffLeft > 0) {
            events.add(new Denied(CombatEvent.Action.ATTACK));
            return;
        }
        if (dashElapsed >= 0) {
            if (dashElapsed >= CombatRules.DASH_TICKS - CombatRules.DASH_ATTACK_WINDOW && weapon.dashAttack().isPresent()) {
                endDash(false);
                startMove(weapon.dashAttack().get(), -1);
            }
            return;
        }
        switch (phase) {
            case IDLE -> startFromIdle(ctx);
            case RECOVERY -> {
                if (cancelOpen(CancelBy.ATTACK)) {
                    attackCancel(ctx);
                    return;
                }
                int left = current.def().timing().recovery() - phaseTick;
                if (left <= CombatRules.BUFFER_WINDOW) {
                    buffered = true;
                }
            }
            case STARTUP, ACTIVE, PLUNGING -> {
                if (cancelOpen(CancelBy.ATTACK)) {
                    attackCancel(ctx);
                }
            }
            default -> {
                // charging ignores new presses
            }
        }
    }

    public void releaseAttack(Context ctx) {
        boolean wasHeld = attackHeld;
        int held = attackHeldTicks;
        attackHeld = false;
        if (!wasHeld || phase != Phase.CHARGING || weapon == null) {
            return;
        }
        ResourceLocation chargedId = weapon.charged().orElse(null);
        MoveDef charged = chargedId == null ? null : moves.get(chargedId);
        if (charged == null) {
            events.add(new ChargeCancelled());
            toIdleWithCombo(chargeFromNext);
            return;
        }
        MoveDef.Charge c = charged.charge().orElse(DEFAULT_CHARGE);
        if (held >= c.min()) {
            double mv = CombatMath.chargedMv(charged.hit().mv(), charged.hit().mvCap(), held, c.min(), c.full());
            startMove(chargedId, mv);
        } else {
            events.add(new ChargeCancelled());
            toIdleWithCombo(chargeFromNext);
        }
    }

    public void pressAbility(Context ctx) {
        abilityHeld = true;
        if (weapon == null || weapon.ability().isEmpty()) {
            return;
        }
        if (staggerLeft > 0) {
            events.add(new Denied(CombatEvent.Action.STAGGERED));
            return;
        }
        WeaponDef.Ability ability = weapon.ability().get();
        boolean recast = recastLeft > 0 && recastMove != null;
        if (!recast && (abilityCooldownLeft > 0 || pendingCooldown > 0)) {
            events.add(new Denied(CombatEvent.Action.ABILITY_COOLDOWN));
            return;
        }
        double cost = ability.cost() * abilityCostScale;
        if (!recast && resonance < cost) {
            events.add(new Denied(CombatEvent.Action.ABILITY_RESONANCE));
            return;
        }
        if (parryElapsed >= 0 || parryWhiffLeft > 0) {
            events.add(new Denied(CombatEvent.Action.ATTACK));
            return;
        }
        ResourceLocation moveId = recast ? recastMove : ability.move();
        boolean moving = stage() != null;
        boolean allowed = phase == Phase.IDLE || phase == Phase.CHARGING || (moving && cancelOpen(CancelBy.ABILITY));
        if (!allowed || moves.get(moveId) == null) {
            return;
        }
        if (dashElapsed >= 0) {
            endDash(false);
        }
        if (phase == Phase.CHARGING) {
            events.add(new ChargeCancelled());
        } else if (moving) {
            cancelMove(CancelBy.ABILITY);
        }
        toIdleWithCombo(null);
        if (recast) {
            closeRecast();
        } else {
            resonance -= cost;
            int cooldown = (int) Math.ceil(CombatMath.cooldownTicks(ability.cooldown(), CombatMath.haste(stats, gearHaste)));
            if (ability.cooldownAfterRecast()) {
                pendingCooldown = Math.max(1, cooldown);
            } else {
                abilityCooldownLeft = cooldown;
            }
        }
        startMove(moveId, -1);
        if (recast && current != null) {
            recastSerial = current.serial();
        }
    }

    public void releaseAbility() {
        abilityHeld = false;
    }

    public void pressDash(Context ctx) {
        if (ctx.mounted()) {
            events.add(new Denied(CombatEvent.Action.DASH)); // the dash key is the mount's move
            return;
        }
        if (staggerLeft > 0) {
            events.add(new Denied(CombatEvent.Action.STAGGERED));
            return;
        }
        boolean free = freeAirDashes && !ctx.onGround();
        boolean comet = !free && !ctx.onGround() && airDashesUsed < gearAirDashes;
        if (dashCharges < 1 && !free && !comet) {
            events.add(new Denied(CombatEvent.Action.DASH));
            return;
        }
        if (parryElapsed >= 0) {
            return;
        }
        if (parryWhiffLeft > CombatRules.PARRY_WHIFF_RECOVERY - CombatRules.PARRY_NO_DASH) {
            return;
        }
        boolean moving = stage() != null;
        boolean allowed = phase == Phase.IDLE || phase == Phase.CHARGING || (moving && cancelOpen(CancelBy.DASH));
        if (!allowed) {
            return;
        }
        if (phase == Phase.CHARGING) {
            events.add(new ChargeCancelled());
        } else if (moving) {
            cancelMove(CancelBy.DASH);
        }
        toIdleWithCombo(null);
        buffered = false;
        parryWhiffLeft = 0;
        if (comet) {
            airDashesUsed++;
        } else if (!free) {
            dashCharges--;
        }
        dashElapsed = 0;
        events.add(new DashStarted());
    }

    public void pressParry() {
        if (parryElapsed >= 0 || parryWhiffLeft > 0 || dashElapsed >= 0 || staggerLeft > 0) {
            return;
        }
        boolean moving = stage() != null;
        boolean allowed = phase == Phase.IDLE || phase == Phase.CHARGING || (moving && cancelOpen(CancelBy.PARRY));
        if (!allowed) {
            return;
        }
        if (phase == Phase.CHARGING) {
            events.add(new ChargeCancelled());
        } else if (moving) {
            cancelMove(CancelBy.PARRY);
        }
        toIdleWithCombo(null);
        buffered = false;
        parryElapsed = 0;
        events.add(new ParryStarted());
    }

    // ------------------------------------------------------------------ server callbacks

    /** A hit from this move landed on {@code targets} entities. Abilities don't generate Resonance. */
    public void onHitLanded(MoveInstance move, int targets) {
        if (targets <= 0) {
            return;
        }
        if (move.def().kind() != MoveKind.ABILITY) {
            addResonance(move.def().hit().resonance() * Math.min(targets, CombatRules.MAX_RESONANCE_TARGETS));
        }
        markCombat();
    }

    public void onDamaged() {
        markCombat();
    }

    public void onPerfectDodge() {
        addResonance(CombatRules.PERFECT_DODGE_RESONANCE);
        dashCharges = Math.min(maxDashCharges(), dashCharges + 1);
        critWindowLeft = CombatRules.GUARANTEED_CRIT_WINDOW;
        markCombat();
        events.add(new PerfectDodge());
    }

    public void onParrySuccess() {
        parryElapsed = -1;
        parryWhiffLeft = 0;
        addResonance(CombatRules.PARRY_RESONANCE);
        riposteLeft = CombatRules.RIPOSTE_WINDOW;
        markCombat();
        events.add(new ParrySucceeded());
    }

    /**
     * Impact broke this player's poise (the server decides; the client hears it from the server): the
     * move, charge, parry or dash in progress stops and every input is refused for {@code ticks}.
     */
    public void onStaggered(int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (phase == Phase.CHARGING) {
            events.add(new ChargeCancelled());
        } else if (current != null) {
            endMove(true);
        }
        if (dashElapsed >= 0) {
            endDash(false);
        }
        toIdleWithCombo(null);
        buffered = false;
        parryElapsed = -1;
        parryWhiffLeft = 0;
        disarmRecast();
        staggerLeft = Math.max(staggerLeft, ticks);
        events.add(new Staggered(ticks));
    }

    /**
     * Opens the ability's second press for {@code ticks}: pressing the ability then starts {@code move}
     * for free. The ability's own recast arms itself on its first active tick; an effect may call this to
     * open it at another moment.
     */
    public void armRecast(ResourceLocation move, int ticks) {
        recastMove = move;
        recastLeft = Math.max(0, ticks);
    }

    /**
     * Closes the second press (the thrown blade stuck in nothing or came back, the star burst on its own). A
     * cooldown held back for it starts now, unless the second press's own move is running (it starts that
     * one when it arrives).
     */
    public void disarmRecast() {
        closeRecast();
        if (recastSerial < 0) {
            releasePendingCooldown();
        }
    }

    /** True once per successful parry, for the first hit that lands inside the riposte window. */
    public boolean consumeRiposte() {
        if (riposteLeft > 0) {
            riposteLeft = 0;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tick

    public List<CombatEvent> tick(Context ctx) {
        if (attackHeld) {
            attackHeldTicks++;
        }
        if (staggerLeft > 0) {
            staggerLeft--;
        }
        if (ctx.onGround()) {
            airDashesUsed = 0; // landing gives the free air dashes back
        }
        tickDash();
        tickParry();
        if (riposteLeft > 0) {
            riposteLeft--;
        }
        if (critWindowLeft > 0) {
            critWindowLeft--;
        }
        if (abilityCooldownLeft > 0) {
            abilityCooldownLeft--;
        }
        if (recastLeft > 0 && --recastLeft == 0) {
            recastMove = null;
            if (recastSerial < 0) {
                releasePendingCooldown(); // the second press ran out: a cooldown held back for it starts
            }
        }
        tickResonance();
        tickDashRecharge();

        switch (phase) {
            case IDLE -> {
                if (comboWindowLeft > 0 && --comboWindowLeft == 0) {
                    nextCombo = null;
                }
            }
            case STARTUP -> {
                if (++phaseTick >= current.def().timing().startup()) {
                    enterAfterStartup(ctx);
                }
            }
            case ACTIVE -> {
                events.add(new ActiveTick(current, phaseTick));
                if (phaseTick == 0) {
                    armOwnRecast();
                    if (current.serial() == recastSerial) {
                        recastSerial = -1; // the second press arrived: a cooldown held back for it starts
                        releasePendingCooldown();
                    }
                }
                if (phaseTick == 0 && abilityHeld && current.def().kind() == MoveKind.ABILITY) {
                    current.def().launch().ifPresent(l -> {
                        if (l.rise() > 0) {
                            events.add(new Rise(l.rise()));
                        }
                    });
                }
                if (++phaseTick >= current.def().timing().active()) {
                    phase = Phase.RECOVERY;
                    phaseTick = 0;
                }
            }
            case RECOVERY -> {
                if (current.def().kind() == MoveKind.LIGHT && attackHeld && !ctx.mounted()
                        && attackHeldTicks >= CombatRules.CHARGE_HOLD_THRESHOLD
                        && weapon != null && weapon.charged().isPresent()) {
                    startCharging();
                } else if (++phaseTick >= current.def().timing().recovery()) {
                    finishMove(ctx);
                }
            }
            case CHARGING -> {
                phaseTick++;
                MoveDef charged = weapon == null ? null : weapon.charged().map(moves::get).orElse(null);
                if (charged != null) {
                    MoveDef.Charge c = charged.charge().orElse(DEFAULT_CHARGE);
                    if (attackHeldTicks == c.min()) {
                        events.add(new ChargeReady());
                    }
                    if (attackHeldTicks == c.full()) {
                        events.add(new ChargeFull());
                    }
                }
            }
            case PLUNGING -> {
                if (ctx.onGround()) {
                    land(ctx);
                } else if (++phaseTick > MAX_PLUNGE_TICKS) {
                    endMove(true);
                    toIdleWithCombo(null);
                }
            }
        }
        List<CombatEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    // ------------------------------------------------------------------ queries

    public Phase phase() {
        return phase;
    }

    public int phaseTick() {
        return phaseTick;
    }

    public @Nullable MoveInstance current() {
        return current;
    }

    public @Nullable WeaponDef weapon() {
        return weapon;
    }

    public double resonance() {
        return resonance;
    }

    public int maxResonance() {
        return CombatMath.maxResonance(stats, gearResonance);
    }

    public int dashCharges() {
        return dashCharges;
    }

    public int maxDashCharges() {
        return CombatMath.maxDashCharges(stats, gearDashCharges);
    }

    public double dashRechargeProgress() {
        return dashRecharge;
    }

    public int abilityCooldown() {
        return abilityCooldownLeft;
    }

    public boolean isDashing() {
        return dashElapsed >= 0;
    }

    public int dashElapsed() {
        return dashElapsed;
    }

    /** I-frames: a dash's first ticks (plus latency grace), or a move's own invulnerable phases. */
    public boolean isInvulnerable() {
        if (dashElapsed >= 0 && dashElapsed < CombatMath.dashIframes(stats) + latencyGrace) {
            return true;
        }
        Stage stage = stage();
        return stage != null && current.def().traits().invulnerableIn(stage);
    }

    public boolean inPerfectDodgeWindow() {
        return dashElapsed >= 0 && dashElapsed < CombatRules.PERFECT_WINDOW + latencyGrace;
    }

    public boolean isParrying() {
        return parryElapsed >= 0;
    }

    public boolean inPerfectParryWindow() {
        return parryElapsed >= 0 && parryElapsed < CombatRules.PERFECT_WINDOW + latencyGrace;
    }

    public boolean inParryWhiff() {
        return parryWhiffLeft > 0;
    }

    public boolean isAttackHeld() {
        return attackHeld;
    }

    public boolean isAbilityHeld() {
        return abilityHeld;
    }

    /** Ticks the attack button has been held; while charging this is the charge progress. */
    public int attackHeldTicks() {
        return attackHeldTicks;
    }

    public boolean isCritGuaranteedWindow() {
        return critWindowLeft > 0;
    }

    /** Walk speed now: slowed while charging, the move's own while one runs (0 when it roots), else 1. */
    public float movementMultiplier() {
        if (phase == Phase.CHARGING) {
            return CombatRules.CHARGING_MOVE_MULTIPLIER;
        }
        if (current != null && stage() != null) {
            return current.def().traits().walkMultiplier();
        }
        return 1.0f;
    }

    /** True while a move that roots runs: no walking and no jumping. */
    public boolean isRooted() {
        return current != null && stage() != null && current.def().traits().root();
    }

    /** Extra poise right now: the move's hyper armor during its startup and active ticks, else 0. */
    public double poiseBonus() {
        if (current == null) {
            return 0.0;
        }
        return switch (phase) {
            case STARTUP, ACTIVE, PLUNGING -> current.def().traits().hyperArmor();
            default -> 0.0;
        };
    }

    public boolean isStaggered() {
        return staggerLeft > 0;
    }

    /** Ticks left in which the ability's second press works (0: none armed). */
    public int recastWindow() {
        return recastMove == null ? 0 : recastLeft;
    }

    /** The ability's cooldown held back until its second press is over, in ticks (0: none). */
    public int pendingCooldown() {
        return pendingCooldown;
    }

    // ------------------------------------------------------------------ internals

    /** The current move's phase in its data's terms (a plunge's dive is active), or null when none runs. */
    private @Nullable Stage stage() {
        if (current == null) {
            return null;
        }
        return switch (phase) {
            case STARTUP -> Stage.STARTUP;
            case ACTIVE, PLUNGING -> Stage.ACTIVE;
            case RECOVERY -> Stage.RECOVERY;
            default -> null;
        };
    }

    /**
     * True if {@code by} may cut the current move short now: the engine's windows (a dash or parry from
     * the 3rd recovery tick, an ability from the 1st) or one of the move's own.
     */
    private boolean cancelOpen(CancelBy by) {
        Stage stage = stage();
        if (stage == null) {
            return false;
        }
        if (stage == Stage.RECOVERY) {
            int from = switch (by) {
                case DASH, PARRY -> CombatRules.DASH_CANCEL_FROM;
                case ABILITY -> CombatRules.ABILITY_CANCEL_FROM;
                case ATTACK -> Integer.MAX_VALUE;
            };
            if (phaseTick >= from) {
                return true;
            }
        }
        return current.def().traits().cancelOpen(by, stage, phaseTick);
    }

    /** An input cuts the current move short: reported, then ended as cancelled. */
    private void cancelMove(CancelBy by) {
        events.add(new MoveCancelled(current, by, stage(), phaseTick));
        endMove(true);
    }

    /** An attack cancel: the chain's next move (or its first) starts at once. */
    private void attackCancel(Context ctx) {
        ResourceLocation next = current.def().kind() == MoveKind.LIGHT ? current.def().next().orElse(null) : null;
        cancelMove(CancelBy.ATTACK);
        toIdleWithCombo(next);
        buffered = false;
        startFromIdle(ctx);
    }

    /** The ability's first active tick opens its own second press, if it has one. */
    private void armOwnRecast() {
        if (weapon == null || current.def().kind() != MoveKind.ABILITY || weapon.ability().isEmpty()) {
            return;
        }
        WeaponDef.Ability ability = weapon.ability().get();
        if (ability.move().equals(current.id())) {
            ability.recast().filter(WeaponDef.Recast::auto).ifPresent(r -> armRecast(r.move(), r.window()));
        }
    }

    /** The second press closes, with nothing else: its window and its move. */
    private void closeRecast() {
        recastMove = null;
        recastLeft = 0;
    }

    /** A cooldown held back for the second press starts now. */
    private void releasePendingCooldown() {
        if (pendingCooldown > 0) {
            abilityCooldownLeft = Math.max(abilityCooldownLeft, pendingCooldown);
            pendingCooldown = 0;
        }
    }

    private void startFromIdle(Context ctx) {
        if (ctx.mounted()) {
            // riding: L1 and L2 only (the chain comes back to L1 after L2)
            ResourceLocation next = comboWindowLeft > 0 && nextCombo != null ? nextCombo : null;
            int at = next == null ? -1 : weapon.combo().indexOf(next);
            startMove(at >= 0 && at < CombatRules.MOUNTED_CHAIN ? next : weapon.combo().get(0), -1);
            return;
        }
        if (ticksSinceDashEnd < CombatRules.DASH_ATTACK_WINDOW && weapon.dashAttack().isPresent()) {
            ticksSinceDashEnd = FAR;
            startMove(weapon.dashAttack().get(), -1);
            return;
        }
        if (!ctx.onGround() && ctx.pitch() >= CombatRules.PLUNGE_PITCH_DEG && weapon.plunge().isPresent()) {
            plungeStartY = ctx.y();
            startMove(weapon.plunge().get(), -1);
            return;
        }
        if (!ctx.onGround() && weapon.aerial().isPresent()) {
            startMove(weapon.aerial().get(), -1); // a timed aerial (the Astrolabe's Starfall), not a plunge
            return;
        }
        WeaponDef.DashFollow follow = weapon.dashFollow().orElse(null);
        if (follow != null && ticksSinceDashEnd < follow.window()) {
            ticksSinceDashEnd = FAR; // the Weave: soon after a dash the chain starts further in
            startMove(follow.move(), -1);
            return;
        }
        ResourceLocation id = comboWindowLeft > 0 && nextCombo != null ? nextCombo : weapon.combo().get(0);
        startMove(id, -1);
    }

    private void startMove(ResourceLocation id, double mvOverride) {
        MoveDef def = moves.get(id);
        if (def == null) {
            events.add(new Denied(CombatEvent.Action.ATTACK));
            return;
        }
        boolean crit = critWindowLeft > 0;
        critWindowLeft = 0;
        double mv = mvOverride >= 0 ? mvOverride : def.hit().mv();
        current = new MoveInstance(++serial, id, def, mv, crit);
        buffered = false;
        comboWindowLeft = 0;
        nextCombo = null;
        phaseTick = 0;
        phase = Phase.STARTUP;
        events.add(new MoveStarted(current));
        if (def.timing().startup() == 0) {
            phase = def.kind() == MoveKind.PLUNGE ? Phase.PLUNGING : Phase.ACTIVE;
        }
    }

    private void enterAfterStartup(Context ctx) {
        phaseTick = 0;
        if (current.def().kind() == MoveKind.PLUNGE) {
            phase = Phase.PLUNGING;
            if (ctx.onGround()) {
                land(ctx);
            }
        } else {
            phase = Phase.ACTIVE;
        }
    }

    private void land(Context ctx) {
        double fall = Math.max(0.0, plungeStartY - ctx.y());
        events.add(new PlungeLanded(current, fall));
        phase = Phase.RECOVERY;
        phaseTick = 0;
    }

    private void startCharging() {
        ResourceLocation keep = current.def().next().orElse(null);
        endMove(true);
        chargeFromNext = keep;
        buffered = false;
        phase = Phase.CHARGING;
        phaseTick = 0;
        events.add(new ChargeStarted(weapon.charged().get()));
    }

    private void finishMove(Context ctx) {
        MoveInstance done = current;
        ResourceLocation next = done.def().kind() == MoveKind.LIGHT ? done.def().next().orElse(null) : null;
        boolean wasBuffered = buffered;
        endMove(false);
        toIdleWithCombo(next);
        if (wasBuffered && weapon != null) {
            startFromIdle(ctx);
        }
    }

    private void endMove(boolean cancelled) {
        if (current != null) {
            events.add(new MoveEnded(current, cancelled));
            if (current.serial() == recastSerial) {
                recastSerial = -1; // the second press ended before it arrived
                releasePendingCooldown();
            }
        }
        current = null;
        phase = Phase.IDLE;
        phaseTick = 0;
    }

    private void toIdleWithCombo(@Nullable ResourceLocation next) {
        current = null;
        phase = Phase.IDLE;
        phaseTick = 0;
        nextCombo = next;
        comboWindowLeft = next != null ? CombatRules.COMBO_WINDOW : 0;
        chargeFromNext = null;
    }

    private void tickDash() {
        if (dashElapsed >= 0) {
            dashElapsed++;
            if (dashElapsed >= CombatRules.DASH_TICKS) {
                endDash(true);
            }
        } else if (ticksSinceDashEnd < FAR) {
            ticksSinceDashEnd++;
        }
    }

    private void endDash(boolean natural) {
        dashElapsed = -1;
        ticksSinceDashEnd = natural ? 0 : FAR;
        events.add(new DashEnded());
    }

    private void tickParry() {
        if (parryElapsed >= 0) {
            parryElapsed++;
            if (parryElapsed >= parryWindow() + latencyGrace) {
                parryElapsed = -1;
                parryWhiffLeft = CombatRules.PARRY_WHIFF_RECOVERY;
                events.add(new ParryWhiffed());
            }
        } else if (parryWhiffLeft > 0) {
            parryWhiffLeft--;
        }
    }

    private void tickResonance() {
        if (ticksSinceCombat < FAR) {
            ticksSinceCombat++;
        }
        if (ticksSinceCombat >= CombatRules.OUT_OF_COMBAT_TICKS) {
            double target = driftTarget * maxResonance();
            if (resonance < target) {
                resonance = Math.min(target, resonance + CombatRules.DRIFT_PER_TICK);
            } else if (resonance > target) {
                resonance = Math.max(target, resonance - CombatRules.DRIFT_PER_TICK);
            }
        }
    }

    private void tickDashRecharge() {
        if (dashCharges >= maxDashCharges()) {
            dashRecharge = 0;
            return;
        }
        dashRecharge += 1.0 / CombatMath.dashRechargeTicks(stats);
        if (dashRecharge >= 1.0 - 1e-9) {
            dashCharges++;
            dashRecharge = 0;
        }
    }

    private void markCombat() {
        ticksSinceCombat = 0;
    }

    private void addResonance(double amount) {
        double max = maxResonance();
        boolean wasFull = resonance >= max;
        resonance = Math.min(max, resonance + amount * resonanceGain);
        if (!wasFull && resonance >= max) {
            events.add(new ResonanceFull());
        }
    }
}
