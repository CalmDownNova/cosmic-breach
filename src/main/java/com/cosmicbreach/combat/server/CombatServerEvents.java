package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.net.CombatDataSyncPayload;
import com.cosmicbreach.net.CombatFxPayload;
import com.cosmicbreach.net.CombatInputPayload;
import com.cosmicbreach.net.CombatSyncPayload;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveStartedPayload;
import com.cosmicbreach.registry.ModAttachments;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * The server side of the combat engine: validates input, ticks each player's machine, turns its
 * events into hits, runs ground waves and Suspension, decides defense (parry, i-frames, perfect
 * dodge), keeps clients in sync, and cancels a plunge landing's fall damage. It also tells the other
 * players around what a player is doing (moves, dashes, charges, landings) and plays the sounds they
 * should hear ({@link ServerCombatSounds}).
 */
public final class CombatServerEvents {
    /** Impact of a vanilla mob's melee hit, for the parry's poise damage. */
    public static final double VANILLA_ATTACK_IMPACT = 10.0;
    public static final double PARRY_KNOCKBACK = 0.6;

    private CombatServerEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(PlayerTickEvent.Post.class, CombatServerEvents::onPlayerTick);
        bus.addListener(LivingIncomingDamageEvent.class, CombatServerEvents::onIncomingDamage);
        bus.addListener(LivingDamageEvent.Post.class, CombatServerEvents::onDamaged);
        bus.addListener(LivingFallEvent.class, CombatServerEvents::onFall);
        bus.addListener(EntityTickEvent.Post.class, CombatServerEvents::onEntityTick);
        bus.addListener(OnDatapackSyncEvent.class, CombatServerEvents::onDatapackSync);
    }

    // ------------------------------------------------------------------ input

    /** A {@link CombatInputPayload} from a client (main thread). */
    public static void onInput(CombatInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        CombatAction action = payload.decoded();
        if (action == null) {
            return;
        }
        PlayerCombat combat = PlayerCombat.of(player);
        if (!combat.server().inputs().tryAcquire()) {
            return;
        }
        if (action.needsWeapon() && !CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
            return;
        }
        combat.syncWeapon();
        combat.apply(action);
    }

    // ------------------------------------------------------------------ tick

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickPlayer(player, PlayerCombat.of(player));
        }
    }

    private static void tickPlayer(ServerPlayer player, PlayerCombat combat) {
        PlayerCombat.ServerState server = combat.server();
        server.inputs().nextTick();
        CombatStateMachine machine = combat.machine();
        machine.setLatencyGrace(latencyGraceTicks(player));
        long now = player.level().getGameTime();
        boolean canHit = player.isAlive() && !player.isSpectator();

        List<CombatEvent> events = combat.tick();
        for (CombatEvent event : events) {
            if (canHit) {
                ServerMoveEffects.dispatch(player, combat, event, now);
            }
            CombatHooks.combatEvent(player, combat, event);
            switch (event) {
                case CombatEvent.MoveStarted started -> {
                    ModNetworking.sendToTrackers(player, new MoveStartedPayload(player.getId(), started.move().id()));
                    if (canHit && started.move().def().kind() == MoveKind.PLUNGE) {
                        ServerCombatSounds.forOthers(player, ModSounds.PLUNGE_WHISTLE, 1.0f, 1.0f);
                    }
                }
                case CombatEvent.ActiveTick active -> {
                    if (canHit) {
                        if (active.activeTick() == 0) {
                            ServerCombatSounds.swing(player, active.move());
                        }
                        int hit = ServerMoveEffects.dealsItsOwnHits(active.move().def()) ? 0
                                : HitResolver.activeTick(player, combat, active.move(), active.activeTick(), now);
                        if (hit > 0) {
                            machine.onHitLanded(active.move(), hit);
                        }
                        if (active.activeTick() == 0) {
                            startWave(player, combat, active.move(), now);
                        }
                    }
                }
                case CombatEvent.PlungeLanded landed -> {
                    server.plungeLanded(now, landed.fallBlocks());
                    ModNetworking.sendToTrackers(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.PLUNGE_LANDED,
                            Vec3.ZERO, (float) landed.fallBlocks()));
                    if (canHit) {
                        if (landed.move().def().traits().sound().flatMap(MoveTraits.Sounds::slam).isPresent()) {
                            ServerCombatSounds.slam(player, landed.move().def(),
                                    0.9f + (float) Math.min(0.5, landed.fallBlocks() * 0.05), true);
                        } else {
                            ServerCombatSounds.plungeImpact(player, landed.fallBlocks());
                        }
                        int hit = HitResolver.plungeLanding(player, combat, landed.move(), landed.fallBlocks(), now);
                        if (hit > 0) {
                            machine.onHitLanded(landed.move(), hit);
                        }
                    }
                }
                case CombatEvent.MoveEnded ended -> combat.hits().forget(ended.move().serial());
                case CombatEvent.DashStarted dash -> {
                    ModNetworking.sendToTrackers(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.DASH));
                    if (canHit) {
                        ServerCombatSounds.forOthers(player, ModSounds.DASH, 0.9f, 1.0f);
                    }
                }
                case CombatEvent.ParryStarted parry ->
                        ModNetworking.sendToTrackers(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.PARRY_START));
                case CombatEvent.ParryWhiffed whiffed -> {
                    if (canHit) {
                        ServerCombatSounds.forOthers(player, ModSounds.WHIFF, 0.8f, 1.0f);
                    }
                }
                case CombatEvent.ChargeStarted charge ->
                        ModNetworking.sendToTrackers(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.CHARGE_START));
                case CombatEvent.ChargeCancelled cancelled ->
                        ModNetworking.sendToTrackers(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.CHARGE_STOP));
                case CombatEvent.ChargeReady ready -> {
                    if (canHit) {
                        ServerCombatSounds.forOthers(player, ModSounds.CHARGE_READY, 0.9f, 1.0f);
                    }
                }
                default -> {
                    // full charge, dodge and Resonance events only matter to the player's own client
                }
            }
        }
        tickWaves(player, combat, now, canHit);

        if (server.sync().update(machine.resonance(), machine.dashCharges(), machine.abilityCooldown())) {
            PacketDistributor.sendToPlayer(player, new CombatSyncPayload((float) machine.resonance(),
                    machine.dashCharges(), machine.abilityCooldown()));
        }
    }

    /** One-way latency in ticks; the machine caps the grace at 2. */
    private static int latencyGraceTicks(ServerPlayer player) {
        return player.connection == null ? 0 : CombatRules.latencyGraceTicks(player.connection.latency());
    }

    private static void startWave(ServerPlayer player, PlayerCombat combat, MoveInstance move, long now) {
        MoveDef def = move.def();
        WeaponDef weapon = combat.machine().weapon();
        if (def.wave().isEmpty() || weapon == null) {
            return;
        }
        MoveDef.Wave wave = def.wave().get();
        double width = def.hitbox() instanceof HitShape.Line line ? line.width() : GroundWave.DEFAULT_WIDTH;
        combat.server().waves().add(new GroundWave(player.position(), player.getYRot(), wave.length(), width, now,
                move, weapon, wave.mv()));
    }

    private static void tickWaves(ServerPlayer player, PlayerCombat combat, long now, boolean canHit) {
        Iterator<GroundWave> it = combat.server().waves().iterator();
        while (it.hasNext()) {
            GroundWave wave = it.next();
            if (!canHit) {
                it.remove();
                continue;
            }
            if (!wave.dueAt(now)) {
                continue;
            }
            GroundWave.Step step = wave.advance();
            if (step != null) {
                int hit = HitResolver.waveStep(player, combat, wave, step);
                if (hit > 0 && wave.move() != null) {
                    combat.machine().onHitLanded(wave.move(), hit);
                }
            }
            if (wave.finished()) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ defense

    /**
     * Damage a player took from a mob's melee: its Impact counts toward the player's poise (a mob that
     * declares a parryable attack says how much; any other melee is {@value #VANILLA_ATTACK_IMPACT}).
     * Other players' hits add theirs through the {@link HitResolver}; projectiles and the world add none.
     */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0f && player.isAlive()) {
            double impact = meleeImpact(event.getSource());
            if (impact > 0) {
                PoiseTracker.addImpact(player, impact);
            }
        }
    }

    /** The Impact of a mob's melee hit on a player, or 0 for anything else. */
    static double meleeImpact(DamageSource source) {
        Entity causing = source.getEntity();
        if (!(causing instanceof LivingEntity) || causing instanceof Player || source.getDirectEntity() != causing) {
            return 0.0;
        }
        return causing instanceof ParryableAttacker attacker ? attacker.attackImpact() : VANILLA_ATTACK_IMPACT;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && attacker != victim && PoiseTracker.isStaggered(attacker)) {
            event.setCanceled(true); // a staggered attacker deals nothing until the stagger ends
            return;
        }
        if (victim instanceof Player player) {
            PlayerCombat combat = PlayerCombat.existing(player);
            if (combat != null && combat.isServerSide()) {
                defend(event, player, combat);
            }
        }
    }

    private static void defend(LivingIncomingDamageEvent event, Player player, PlayerCombat combat) {
        DamageSource source = event.getSource();
        CombatStateMachine machine = combat.machine();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            machine.onDamaged();
            return;
        }
        if (machine.isParrying()) {
            double impact = parryableImpact(source);
            if (!Double.isNaN(impact)) {
                event.setCanceled(true);
                boolean early = machine.inPerfectParryWindow(); // read before the success closes the parry
                machine.onParrySuccess();
                Vec3 meeting = parryPoint(player, source.getEntity());
                if (source.getEntity() instanceof LivingEntity attacker) {
                    PoiseTracker.addImpact(attacker, 2.0 * impact);
                    attacker.knockback(PARRY_KNOCKBACK, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                }
                if (source.getEntity() instanceof ParryableAttacker parried) {
                    parried.onParried(player);
                }
                ModNetworking.sendToTrackersAndSelf(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.PARRY,
                        meeting.subtract(player.position()), (float) impact));
                ServerCombatSounds.forEveryone(player.level(), meeting, ModSounds.PARRY, 1.0f, 1.0f);
                if (player instanceof ServerPlayer server) {
                    CombatHooks.parried(server, combat, source.getEntity() instanceof LivingEntity living && living != player
                            ? living : null, event.getAmount(), early);
                }
                return;
            }
        }
        if (machine.isInvulnerable() && isDodgeable(source)) {
            event.setCanceled(true);
            if (machine.inPerfectDodgeWindow() && combat.server().claimPerfectDodge(combat.dashSerial())) {
                machine.onPerfectDodge();
                ModNetworking.sendToTrackersAndSelf(player, new CombatFxPayload(player.getId(), CombatFxPayload.Kind.PERFECT_DODGE));
                ServerCombatSounds.forEveryone(player.level(), player.position().add(0, player.getBbHeight() * 0.5, 0),
                        ModSounds.PERFECT_DODGE, 1.0f, 1.0f);
            }
            return;
        }
        machine.onDamaged();
    }

    /**
     * Where a parried attack met the blade: in front of the parrying player's chest, toward the
     * attacker (along the player's facing if there is none), about halfway to it and at most 0.9 out.
     */
    static Vec3 parryPoint(Player player, @Nullable Entity attacker) {
        Vec3 chest = player.position().add(0, (HitResolver.CHEST + 0.08) * player.getBbHeight(), 0);
        Vec3 toward = attacker == null ? Vec3.ZERO
                : new Vec3(attacker.getX() - player.getX(), 0, attacker.getZ() - player.getZ());
        double distance = toward.length();
        Vec3 direction;
        if (distance < 1e-3) {
            direction = HitShape.forward(player.getYRot());
            distance = 1.6;
        } else {
            direction = toward.scale(1.0 / distance);
        }
        return chest.add(direction.scale(Math.max(0.5, Math.min(0.9, distance * 0.5))));
    }

    /**
     * The Impact of a parryable attack, or NaN. An attacker that implements {@link ParryableAttacker}
     * decides for itself; otherwise only plain melee from a living, non-player mob can be parried.
     */
    static double parryableImpact(DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof ParryableAttacker attacker) {
            return attacker.isParryableAttackActive() ? attacker.attackImpact() : Double.NaN;
        }
        boolean plainMelee = causing instanceof LivingEntity && !(causing instanceof Player)
                && source.getDirectEntity() == causing;
        return plainMelee ? VANILLA_ATTACK_IMPACT : Double.NaN;
    }

    /** I-frames dodge attacks (anything with an attacker, a projectile or a blast), not the world's hazards. */
    static boolean isDodgeable(DamageSource source) {
        return source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerCombat combat = PlayerCombat.existing(player);
            if (combat != null && combat.protectsFromFall(player.level().getGameTime())) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------ Suspension

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        Suspension suspension = entity.getExistingDataOrNull(ModAttachments.SUSPENSION);
        if (suspension == null) {
            return;
        }
        if (!entity.isAlive()) {
            entity.removeData(ModAttachments.SUSPENSION);
            return;
        }
        Vec3 v = entity.getDeltaMovement();
        double vertical = suspension.afterTick(v.y, entity.onGround());
        if (!Double.isNaN(vertical)) {
            entity.setDeltaMovement(v.x * Suspension.HORIZONTAL_KEEP, vertical, v.z * Suspension.HORIZONTAL_KEEP);
            entity.resetFallDistance();
        }
        if (suspension.finished()) {
            entity.removeData(ModAttachments.SUSPENSION);
        }
    }

    // ------------------------------------------------------------------ data sync

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        CombatDataSyncPayload payload = CombatDataSyncPayload.of(CombatData.server());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}
