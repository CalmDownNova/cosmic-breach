package com.cosmicbreach.relic.lastlight;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.combat.server.effect.ServerMoveEffect;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.registry.ModSounds;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.lastlight.LastLightRules.Outcome;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Last Light on the server (GDD 7.3; the numbers are {@link LastLightRules}):
 * <ul>
 *   <li><b>Dawnguard</b> ({@link #DAWNGUARD}, the ability move's effect): while its 30 active ticks hold, a listener
 *       ahead of the engine's defense parries every attack on the player automatically, for 10 Resonance each: the hit
 *       is cancelled, the attacker takes twice its Impact in poise (and hears its parry, if it has a gold-telegraphed
 *       attack running), a Sunlight charge is stored, and a golden sunrise flares where the blow met the glaive while
 *       a bell tolls. Each parry triggers <b>Daybreak</b>, the counter-slash: the move's own hitbox (an arc of radius 5
 *       and 270 degrees, turned toward the attacker) at its motion value 2.4, struck once at the end of the tick (parries
 *       in one tick share it, and nothing hurts the attacker in the middle of its own swing). The move itself deals no
 *       damage on its active ticks: Daybreak is its damage.</li>
 *   <li><b>Sunlight</b>: every parry with Last Light in hand stores a charge, the parry key's too (up to 3). The
 *       Sunspear's effect ({@link #SUNLIGHT}) spends them all as its release starts, and a {@link HitModifiers} entry
 *       multiplies that release's hits by 1 + 0.4 per charge.</li>
 * </ul>
 * Tells the clients (a {@link MoveEffectPayload} under {@link #DAWNGUARD}): {@link #STANCE} (the stance is up),
 * {@link #FLARE} (a parry: at the meeting point, value the charges held after it), {@link #DAYBREAK} (at the chest,
 * value the arc's yaw, ticks how many it cut), {@link #CHARGES} (value the charges held now, for the glaive's gems) and
 * {@link #SPEND} (value the charges a release spent).
 */
public final class LastLight {
    public static final ResourceLocation DAWNGUARD = CosmicBreach.id("dawnguard");
    public static final ResourceLocation SUNLIGHT = CosmicBreach.id("sunlight");
    /** The Sunfall's landing burst: only a look (the client's), no server handler. */
    public static final ResourceLocation SUNFALL = CosmicBreach.id("sunfall");
    public static final int STANCE = 0;
    public static final int FLARE = 1;
    public static final int DAYBREAK = 2;
    public static final int CHARGES = 3;
    public static final int SPEND = 4;

    private static final Map<UUID, Integer> CHARGED = new ConcurrentHashMap<>();
    /** The release that spent the charges: its move's serial and multiplier. */
    private static final Map<UUID, Spent> SPENT = new ConcurrentHashMap<>();
    /** The Daybreak each player's parries owe at the end of this tick. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DAYBREAK = new ConcurrentHashMap<>();

    private record Spent(int serial, double multiplier) {
    }

    private record Pending(ServerPlayer player, MoveInstance move, float yaw) {
    }

    private LastLight() {
    }

    public static void register(IEventBus game) {
        ServerMoveEffects.register(DAWNGUARD, new Dawnguard());
        ServerMoveEffects.register(SUNLIGHT, new Sunlight());
        game.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, LastLight::onIncomingDamage);
        game.addListener(ServerTickEvent.Post.class, event -> strikePending());
        game.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                setCharges(player, 0);
            }
        });
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> forget(event.getEntity().getUUID()));
        game.addListener(ServerStoppingEvent.class, event -> {
            CHARGED.clear();
            SPENT.clear();
            PENDING.clear();
            LAST_DAYBREAK.clear();
        });
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
                if (attacker == null || move == null) {
                    return 1.0;
                }
                Spent spent = SPENT.get(attacker.getUUID());
                return spent != null && spent.serial() == move.serial() ? spent.multiplier() : 1.0;
            }
        });
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if (event instanceof CombatEvent.ParrySucceeded && holds(player)) {
                    // the parry key's catch: its own flash and clang came from the engine; Sunlight adds its charge
                    int charges = store(player);
                    Vec3 point = player.position().add(0, player.getBbHeight() * 0.63, 0)
                            .add(HitShape.forward(player.getYRot()).scale(0.8));
                    flare(player, point, charges);
                }
            }
        });
    }

    /** True if the player's main hand holds Last Light. */
    public static boolean holds(Player player) {
        return player.getMainHandItem().is(Relics.LAST_LIGHT.get());
    }

    /** The Sunlight charges the player holds (server). */
    public static int charges(Player player) {
        return CHARGED.getOrDefault(player.getUUID(), 0);
    }

    /** Sets the player's Sunlight charges (server; the scenarios use it too) and tells the clients. */
    public static void setCharges(ServerPlayer player, int charges) {
        int now = Math.max(0, Math.min(LastLightRules.MAX_CHARGES, charges));
        Integer before = CHARGED.put(player.getUUID(), now);
        if (before == null || before != now) {
            send(player, CHARGES, player.position(), now, 0);
        }
    }

    private static int store(ServerPlayer player) {
        int charges = LastLightRules.store(charges(player));
        setCharges(player, charges);
        return charges;
    }

    private static void forget(UUID id) {
        CHARGED.remove(id);
        SPENT.remove(id);
        PENDING.remove(id);
        LAST_DAYBREAK.remove(id);
    }

    // ------------------------------------------------------------------ Dawnguard

    /** The stance: the move's effect. It deals the move's damage itself (as Daybreaks), so the engine skips the arc. */
    static final class Dawnguard implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick == 0) {
                use.send(STANCE, use.player().position(), 0f, use.move().def().timing().active());
            }
        }
    }

    /** The Dawnguard move running now, in its stance, or null. */
    static @Nullable MoveInstance stance(CombatStateMachine machine) {
        MoveInstance move = machine.current();
        if (move == null || machine.phase() != CombatStateMachine.Phase.ACTIVE
                || move.def().traits().effect(DAWNGUARD).isEmpty()) {
            return null;
        }
        return move;
    }

    /** Ahead of the engine's own defense: the stance parries every attack it can pay for. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat == null || !combat.isServerSide()) {
            return;
        }
        CombatStateMachine machine = combat.machine();
        MoveInstance move = stance(machine);
        if (move == null) {
            return;
        }
        DamageSource source = event.getSource();
        Entity cause = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (cause instanceof LivingEntity staggered && cause != player && PoiseTracker.isStaggered(staggered)) {
            return; // the engine cancels a staggered attacker's hit anyway
        }
        Outcome outcome = LastLightRules.answer(cause != null || direct != null,
                source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), machine.resonance());
        if (outcome != Outcome.PARRY) {
            return;
        }
        event.setCanceled(true);
        Optional<MoveEffect> effect = move.def().traits().effect(DAWNGUARD);
        machine.drainResonance(effect.map(e -> e.param("parry_cost", LastLightRules.PARRY_COST)).orElse(LastLightRules.PARRY_COST));
        machine.onDamaged(); // the fight goes on
        int charges = store(player);
        LivingEntity attacker = cause instanceof LivingEntity living && cause != player ? living : null;
        if (attacker != null) {
            boolean telegraphed = attacker instanceof ParryableAttacker parryable && parryable.isParryableAttackActive();
            double impact = telegraphed ? ((ParryableAttacker) attacker).attackImpact() : LastLightRules.DEFAULT_IMPACT;
            PoiseTracker.addImpact(attacker, 2.0 * impact);
            if (telegraphed) {
                ((ParryableAttacker) attacker).onParried(player);
            }
        }
        // a parry like any other to whatever listens for one: the Leechstar Signet heals, the Event Horizon Lens opens
        // on one in the stance's first ticks (the Lens's early window)
        com.cosmicbreach.combat.server.CombatHooks.parried(player, combat, attacker, event.getAmount(),
                machine.phaseTick() < LastLightRules.STANCE_EARLY_TICKS);
        Entity from = cause != null ? cause : direct;
        flare(player, meetingPoint(player, direct != null ? direct : from), charges);
        ServerCombatSounds.forEveryone(player.level(), player.position().add(0, 1.0, 0), ModSounds.PARRY, 0.9f, 1.1f);
        long now = player.level().getGameTime();
        if (LastLightRules.daybreakDue(LAST_DAYBREAK.getOrDefault(player.getUUID(), Long.MIN_VALUE), now)) {
            LAST_DAYBREAK.put(player.getUUID(), now);
            PENDING.put(player.getUUID(), new Pending(player, move, yawToward(player, from)));
        }
    }

    /** The Daybreaks owed this tick, struck now that every entity has had its turn. */
    private static void strikePending() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Pending pending : PENDING.values()) {
            ServerPlayer player = pending.player();
            if (!player.isAlive() || player.isRemoved() || !holds(player)) {
                continue;
            }
            PlayerCombat combat = PlayerCombat.of(player);
            MoveInstance move = pending.move();
            Vec3 chest = player.position().add(0, HitResolver.CHEST * player.getBbHeight(), 0);
            int hit = HitResolver.effectHit(player, combat, move, move.def().hitbox(), chest, pending.yaw(), move.mv(),
                    move.def().hit().impact());
            if (hit > 0) {
                combat.machine().onHitLanded(move, hit);
            }
            send(player, DAYBREAK, chest, pending.yaw(), hit);
            ServerCombatSounds.forEveryone(player.level(), chest, Relics.LL_DAYBREAK, 1.0f, 1.0f);
        }
        PENDING.clear();
    }

    /** A parry's look and bell, for everyone: the sunrise flare at {@code point}. */
    private static void flare(ServerPlayer player, Vec3 point, int charges) {
        send(player, FLARE, point, charges, 0);
        ServerCombatSounds.forEveryone(player.level(), point, Relics.LL_BELL, 1.0f, 1.0f);
        ServerCombatSounds.forEveryone(player.level(), point, Relics.LL_SUNLIGHT, 0.6f, 0.9f + 0.1f * charges);
    }

    /** Where the blow met the glaive: in front of the chest, toward what struck (a shot's own position), at most 0.9 out. */
    static Vec3 meetingPoint(Player player, @Nullable Entity from) {
        Vec3 chest = player.position().add(0, 0.63 * player.getBbHeight(), 0);
        Vec3 toward = from == null ? Vec3.ZERO : new Vec3(from.getX() - player.getX(), 0, from.getZ() - player.getZ());
        double distance = toward.length();
        Vec3 direction = distance < 1e-3 ? HitShape.forward(player.getYRot()) : toward.scale(1.0 / distance);
        return chest.add(direction.scale(Math.max(0.5, Math.min(0.9, distance * 0.5))));
    }

    /** The yaw (Minecraft degrees) from the player toward {@code target}, or the player's own facing. */
    static float yawToward(Player player, @Nullable Entity target) {
        if (target == null) {
            return player.getYRot();
        }
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 1e-6) {
            return player.getYRot();
        }
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
    }

    private static void send(ServerPlayer player, int stage, Vec3 at, float value, int ticks) {
        ModNetworking.sendToTrackersAndSelf(player, new MoveEffectPayload(player.getId(), DAWNGUARD, stage, at, value, ticks));
    }

    // ------------------------------------------------------------------ Sunlight

    /** On the Sunspear: its release spends every charge the player holds. */
    static final class Sunlight implements ServerMoveEffect {
        @Override
        public void started(Use use) {
            ServerPlayer player = use.player();
            int charges = charges(player);
            if (charges <= 0) {
                SPENT.remove(player.getUUID());
                return;
            }
            SPENT.put(player.getUUID(), new Spent(use.move().serial(), LastLightRules.chargedMultiplier(charges)));
            setCharges(player, 0);
            Vec3 blade = player.position().add(0, player.getBbHeight() * 0.62, 0).add(HitShape.forward(player.getYRot()).scale(1.2));
            send(player, SPEND, blade, charges, 0);
            ServerCombatSounds.forEveryone(player.level(), player.position(), Relics.LL_SUNLIGHT, 1.0f, 0.75f);
        }

        @Override
        public void ended(Use use, boolean cancelled) {
            Spent spent = SPENT.get(use.player().getUUID());
            if (spent != null && spent.serial() == use.move().serial()) {
                SPENT.remove(use.player().getUUID());
            }
        }
    }

    /** The damage multiplier the player's move with this serial carries from spent charges (1 for none). */
    public static double spentMultiplier(Player player, int serial) {
        Spent spent = SPENT.get(player.getUUID());
        return spent != null && spent.serial() == serial ? spent.multiplier() : 1.0;
    }
}
