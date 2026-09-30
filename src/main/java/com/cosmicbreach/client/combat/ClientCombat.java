package com.cosmicbreach.client.combat;

import com.cosmicbreach.client.anim.EngineAnimations;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.fx.Chargers;
import com.cosmicbreach.client.fx.ClientMoveEffects;
import com.cosmicbreach.client.fx.CombatAudio;
import com.cosmicbreach.client.fx.CombatEffects;
import com.cosmicbreach.client.fx.WeaponGlow;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.net.CombatFxPayload;
import com.cosmicbreach.net.HitFxPayload;
import com.cosmicbreach.net.MoveStartedPayload;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client half of the combat engine. For the local player, every tick, before the body moves:
 * input into the local machine (prediction, see {@link CombatInput}), the machine's tick, then its
 * events turned into animation, sound ({@link CombatAudio}), effects ({@link CombatEffects}) and
 * movement ({@link BodyMotion}). For everyone, the server's payloads turned into feel: other players'
 * animations and effects, hit sparks, hit-stop, camera shake ({@link CameraShake}).
 *
 * <p>The local machine predicts moves, dashes and parries so they start with no delay; the server's
 * machine stays the authority for hits and defense, and its sync corrects Resonance, dash charges
 * and the ability cooldown. The server also tells this client when its parry caught something or its
 * dash was a perfect dodge, and the local machine takes those in, so both stay in step.
 */
public final class ClientCombat {
    private static final BodyMotion MOTION = new BodyMotion();
    private static final CameraShake SHAKE = new CameraShake();

    /** The local camera's shake, for other effects (a guardian's slam nearby). */
    public static CameraShake shake() {
        return SHAKE;
    }
    private static final List<Consumer<CombatEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    /** The local player the state above belongs to; a new one (respawn, dimension change) resets it. */
    private static @Nullable LocalPlayer owner;
    private static int lastHurtTime;

    private ClientCombat() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, CombatInput::onInteraction);
        gameBus.addListener(PlayerTickEvent.Pre.class, ClientCombat::onPlayerTick);
        gameBus.addListener(MovementInputUpdateEvent.class, ClientCombat::onMovementInput);
        gameBus.addListener(ViewportEvent.ComputeCameraAngles.class, ClientCombat::onCameraAngles);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> forget());
    }

    /** Every event the local machine reports, after the runtime reacted to it. For tests and tools. */
    public static void addEventListener(Consumer<CombatEvent> listener) {
        LISTENERS.add(listener);
    }

    /** The camera shake's trauma right now, 0 to 1. For tests. */
    public static double cameraTrauma() {
        return SHAKE.trauma();
    }

    /** The camera's kick right now (degrees down). For tests. */
    public static double cameraKick() {
        return SHAKE.kickAt(0f);
    }

    /**
     * The local player blinks: its body flies to {@code to} (the feet's arrival) over {@code ticks} ticks from
     * the next tick, ignoring its own movement input (the Binary Edges' Tether, {@link BodyMotion#startBlink}).
     */
    public static void startBlink(Vec3 to, int ticks) {
        MOTION.startBlink(to, ticks);
    }

    /** True while the local player's body is on a blink. */
    public static boolean isBlinking() {
        return MOTION.isBlinking();
    }

    /** A slam felt through the local player's camera: shake ({@code trauma}) and a dip of {@code kickDegrees}. */
    public static void feelSlam(double trauma, double kickDegrees) {
        SHAKE.addTrauma(trauma);
        SHAKE.kick(kickDegrees);
    }

    // ------------------------------------------------------------------ the local player's tick

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        // Also fires for remote players, and on the integrated server's thread for server players.
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerCombat combat = PlayerCombat.of(player);
        CombatStateMachine machine = combat.machine();
        if (player != owner) {
            forget();
            owner = player;
            CombatInput.reset(mc);
            WeaponGlow.reset(machine.resonance() >= machine.maxResonance());
        }
        if (player.hurtTime > lastHurtTime) {
            markCombat(machine); // the server marks the fight on damage taken; keep the drift clocks together
        }
        lastHurtTime = player.hurtTime;

        CombatInput.tick(mc, player, combat);
        List<CombatEvent> events = combat.tick();
        react(player, events);
        WeaponGlow.tick(player, machine);
        SHAKE.tick();

        // Zenith's hold lasts while the ability stays held and the body is off the ground
        MOTION.ground(player.onGround());
        if (!machine.isAbilityHeld() || player.isPassenger() || player.isFallFlying()) {
            MOTION.releaseLift();
        }
        Vec3 velocity = MOTION.velocity(player.getDeltaMovement(), player.getYRot(),
                machine.phase() == CombatStateMachine.Phase.PLUNGING, player.position());
        if (!player.isPassenger()) {
            player.setDeltaMovement(velocity);
        }
    }

    private static void react(LocalPlayer player, List<CombatEvent> events) {
        boolean stop = false;
        for (CombatEvent event : events) {
            switch (event) {
                case CombatEvent.MoveStarted started -> {
                    MoveInstance move = started.move();
                    MoveDef def = move.def();
                    PlayerAnimations.play(player, def.animation(), false);
                    stop = false;
                    MOTION.moveStarted();
                    MOTION.startLunge(move.serial(), def.lunge(), def.timing().startup() + def.timing().active());
                    CombatEffects.moveStarted(player, def);
                    if (def.kind() == MoveKind.PLUNGE) {
                        CombatAudio.playOwn(player, ModSounds.PLUNGE_WHISTLE, 1.0f, 1.0f);
                    }
                }
                case CombatEvent.ActiveTick active -> {
                    if (active.activeTick() == 0) {
                        CombatAudio.swing(player, active.move());
                        CombatEffects.moveActive(player, active.move().def());
                    }
                }
                case CombatEvent.MoveEnded ended -> {
                    if (ended.cancelled()) {
                        stop = true;
                        MOTION.moveCancelled(ended.move().serial());
                    }
                }
                case CombatEvent.ChargeStarted charge -> {
                    MoveDef charged = CombatData.client().move(charge.chargedMove());
                    if (charged != null && charged.charge().isPresent() && charged.charge().get().animation().isPresent()) {
                        PlayerAnimations.play(player, charged.charge().get().animation().get(), false);
                        stop = false;
                    }
                    Chargers.start(player, charge.chargedMove(), () -> machineOf(player).phase() == CombatStateMachine.Phase.CHARGING);
                }
                case CombatEvent.ChargeReady ready -> {
                    CombatAudio.playOwn(player, ModSounds.CHARGE_READY, 0.9f, 1.0f);
                    CombatEffects.chargeGlint(player, false);
                }
                case CombatEvent.ChargeFull full -> CombatEffects.chargeGlint(player, true);
                case CombatEvent.ChargeCancelled cancelled -> stop = true;
                case CombatEvent.PlungeLanded landed -> {
                    landed.move().def().landAnimation().ifPresent(animation -> PlayerAnimations.play(player, animation, false));
                    stop = false;
                    if (!ClientMoveEffects.landed(player, landed.move().def(), landed.fallBlocks())) {
                        CombatEffects.plungeLanding(player.position(), landed.fallBlocks()); // the boom comes from the server
                    }
                }
                case CombatEvent.Staggered staggered -> {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.STAGGER), false);
                    stop = false;
                }
                case CombatEvent.Rise rise -> MOTION.rise(rise.height(), player.getGravity(), rise.hoverTicks());
                case CombatEvent.DashStarted dash -> {
                    float[] move = movementInput(player);
                    float forward = move[0];
                    float left = move[1];
                    BodyMotion.DashKind kind = BodyMotion.dashKind(forward, left);
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, switch (kind) {
                        case FORWARD -> EngineAnimations.DASH_FORWARD;
                        case BACK -> EngineAnimations.DASH_BACK;
                        case LEFT, RIGHT -> EngineAnimations.DASH_SIDE;
                    }), kind == BodyMotion.DashKind.LEFT);
                    stop = false;
                    MOTION.startDash(BodyMotion.dashDirection(player.getYRot(), forward, left), player.onGround(),
                            CombatHooks.dashDistanceScale(player));
                    if (!player.onGround() && CombatHooks.airDashesHoldHeight(player)) {
                        MOTION.holdHeight(); // the Twin Comet Band: air dashes fly level and chain
                    }
                    CombatAudio.playOwn(player, ModSounds.DASH, 0.9f, 1.0f);
                    CombatEffects.dash(player);
                }
                case CombatEvent.DashEnded ended -> MOTION.dashEnded();
                case CombatEvent.ParryStarted parry -> {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.PARRY), false);
                    stop = false;
                }
                case CombatEvent.ParrySucceeded parried -> {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.PARRY_SUCCESS), false);
                    stop = false;
                }
                case CombatEvent.ParryWhiffed whiffed -> CombatAudio.playOwn(player, ModSounds.WHIFF, 0.8f, 1.0f);
                default -> {
                    // Parries and perfect dodges show from the server's CombatFx (it decides them), full
                    // Resonance from WeaponGlow (the server's sync fills it); denials have no feedback yet.
                }
            }
            for (Consumer<CombatEvent> listener : LISTENERS) {
                listener.accept(event);
            }
        }
        if (stop) {
            PlayerAnimations.stop(player);
        }
    }

    /** Charging walks slower; a dash or lunge ignores the player's own movement input. */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != owner) {
            return;
        }
        Input input = event.getInput();
        if (MOTION.suppressesInput()) {
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
            return;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat != null && combat.machine().isRooted()) {
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
            input.jumping = false;
            return;
        }
        float multiplier = combat == null ? 1f : combat.machine().movementMultiplier();
        if (multiplier != 1f) {
            input.forwardImpulse *= multiplier;
            input.leftImpulse *= multiplier;
        }
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!SHAKE.active()) {
            return;
        }
        float[] offsets = SHAKE.offsets((float) event.getPartialTick(), ClientConfig.screenShake());
        event.setYaw(event.getYaw() + offsets[0]);
        event.setPitch(event.getPitch() + offsets[1]);
        event.setRoll(event.getRoll() + offsets[2]);
    }

    private static void forget() {
        if (owner != null) {
            Chargers.stop(owner.getId());
        }
        owner = null;
        lastHurtTime = 0;
        MOTION.reset();
        SHAKE.reset();
    }

    // ------------------------------------------------------------------ what the server tells us

    /**
     * Another player started a move: its animation and its slash trail (which follows the blade) now,
     * its pillar or line when its active ticks come, and the end of any charge it was holding. The local
     * player predicted its own.
     */
    public static void onMoveStarted(MoveStartedPayload payload) {
        if (otherPlayer(payload.entityId()) instanceof AbstractClientPlayer player) {
            Chargers.stop(player.getId());
            MoveDef def = CombatData.client().move(payload.moveId());
            if (def != null) {
                PlayerAnimations.play(player, def.animation(), false);
                CombatEffects.remoteMoveStarted(player, def);
            }
        }
    }

    /**
     * A hit landed: sparks at the point struck, hit-stop on a player attacker, shake for the local player
     * on either end. The hit's sound comes from the server.
     */
    public static void onHitFx(HitFxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (payload.hitstop() > 0 && level.getEntity(payload.attackerId()) instanceof AbstractClientPlayer attacker) {
            PlayerAnimations.hitStop(attacker, payload.hitstop());
        }
        LocalPlayer self = mc.player;
        if (self != null && payload.attackerId() == self.getId()) {
            SHAKE.addTrauma(CameraShake.forLandedHit(payload.impact(), payload.crit()));
            markCombat(PlayerCombat.of(self).machine());
        } else if (self != null && payload.targetId() == self.getId()) {
            SHAKE.addTrauma(CameraShake.forTakenHit(payload.impact()));
        }
        CombatEffects.hit(payload.position(), payload.direction(), payload.impact(), payload.crit());
    }

    /**
     * A parry, perfect dodge, dash, stagger, raised parry, charge or plunge landing on some entity: its
     * animation and effects, and the local machine kept in step with the server's.
     */
    public static void onCombatFx(CombatFxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(payload.entityId());
        LocalPlayer self = mc.player;
        boolean local = self != null && entity == self;
        switch (payload.kind()) {
            case PARRY -> {
                if (local) {
                    // The server's machine ended the parry with a catch; the local one does too, and its
                    // ParrySucceeded event plays the animation next tick (and no whiff lock-out follows).
                    PlayerCombat.of(self).machine().onParrySuccess();
                } else if (entity instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.PARRY_SUCCESS), false);
                }
                if (entity != null) {
                    CombatEffects.parry(entity.position().add(payload.offset()));
                }
            }
            case PERFECT_DODGE -> {
                if (local) {
                    PlayerCombat.of(self).machine().onPerfectDodge(); // the charge back and the crit window, at once
                }
                if (entity != null) {
                    CombatEffects.perfectDodge(entity);
                }
            }
            case DASH -> {
                if (!local && entity instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.DASH_FORWARD), false); // the direction isn't sent
                    CombatEffects.dash(player);
                }
            }
            case CHARGE_START -> {
                if (!local && entity instanceof AbstractClientPlayer player) {
                    ResourceLocation charged = chargedMoveOf(player);
                    MoveDef def = charged == null ? null : CombatData.client().move(charged);
                    if (def != null) {
                        def.charge().flatMap(MoveDef.Charge::animation).ifPresent(hold -> PlayerAnimations.play(player, hold, false));
                    }
                    Chargers.start(player, charged, null);
                }
            }
            case CHARGE_STOP -> {
                // Only a charge let go early or cancelled: a release starts the charged move instead.
                if (!local && entity != null) {
                    Chargers.stop(entity.getId());
                    if (entity instanceof AbstractClientPlayer player) {
                        PlayerAnimations.stop(player);
                    }
                }
            }
            case PLUNGE_LANDED -> {
                if (!local && entity != null) {
                    boolean drawn = false;
                    if (entity instanceof AbstractClientPlayer player) {
                        MoveDef plunge = plungeOf(player);
                        if (plunge != null) {
                            plunge.landAnimation().ifPresent(landing -> PlayerAnimations.play(player, landing, false));
                            drawn = ClientMoveEffects.landed(player, plunge, payload.value());
                        }
                    }
                    if (!drawn) {
                        CombatEffects.plungeLanding(entity.position(), payload.value());
                    }
                }
            }
            case PARRY_START -> {
                if (!local && entity instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.PARRY), false);
                }
            }
            case STAGGER -> {
                if (local) {
                    // the local machine stops what it predicted and plays the stagger from its Staggered event
                    PlayerCombat.of(self).machine().onStaggered(PoiseTracker.PLAYER_STAGGER_TICKS);
                } else if (entity instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, EngineAnimations.forHeld(player, EngineAnimations.STAGGER), false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static @Nullable Entity otherPlayer(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (mc.player != null && mc.player.getId() == entityId)) {
            return null;
        }
        return mc.level.getEntity(entityId);
    }

    /**
     * The fight goes on: the server's machine marks it on hits landed and damage taken, and only
     * then stops drifting Resonance. {@code onDamaged} only marks the fight, so it does for both.
     */
    private static void markCombat(CombatStateMachine machine) {
        machine.onDamaged();
    }

    private static CombatStateMachine machineOf(Player player) {
        return PlayerCombat.of(player).machine();
    }

    /** The charged move of the weapon in a player's main hand, for the charge's timing. */
    private static @Nullable ResourceLocation chargedMoveOf(Player player) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
        return weapon == null ? null : weapon.charged().orElse(null);
    }

    /** The plunge of the weapon in a player's main hand, for its landing animation. */
    private static @Nullable MoveDef plungeOf(Player player) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
        return weapon == null ? null : weapon.plunge().map(CombatData.client()::move).orElse(null);
    }

    /**
     * This tick's movement input as {forward, left}. The input object only updates after this point in
     * the tick, so keyboard input comes straight from the movement keys (as KeyboardInput reads them);
     * another input source (a controller mod) is taken from its input object.
     */
    private static float[] movementInput(LocalPlayer player) {
        if (!(player.input instanceof KeyboardInput)) {
            return new float[] {player.input.forwardImpulse, player.input.leftImpulse};
        }
        Options options = Minecraft.getInstance().options;
        return new float[] {axis(options.keyUp, options.keyDown), axis(options.keyLeft, options.keyRight)};
    }

    private static float axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1f : 0f) - (negative.isDown() ? 1f : 0f);
    }
}
