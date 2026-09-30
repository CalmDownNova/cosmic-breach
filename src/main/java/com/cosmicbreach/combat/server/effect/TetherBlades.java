package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server's side of the Binary Edges' thrown blade (GDD 4.2), between the blade ({@link ThrownSickle}) and
 * its thrower: which player has a blade out; when it sticks, the blink opens (the ability's second press, for
 * as long as it stays) and an enemy it stuck in is Marked; when it comes back, the second press closes, which
 * starts the ability's cooldown (the weapon's {@code cooldown_after_recast}). As a {@link HitModifiers} source:
 * +{@code mark_crit} crit chance against a Marked enemy for anyone's hits, and the thrower's other moves at
 * {@code solo} (60%) while the left blade is out.
 *
 * <p>Tells the clients (effect {@link #ID}): {@link #STUCK} (at the blade; value 1 in an enemy, 0 in a block;
 * ticks how long it stays, which opens the thrower's own blink), {@link #RETURNED} (the thrower's blink closes)
 * and {@link #MARKED} (at the enemy; value the ticks, ticks the enemy's entity id).
 */
public final class TetherBlades {
    public static final ResourceLocation ID = CosmicBreach.id("tether");
    public static final int STUCK = 1;
    public static final int RETURNED = 2;
    public static final int MARKED = 3;

    private record Mark(long until, double bonus) {
    }

    private static final Map<ServerPlayer, ThrownSickle> BLADES = new WeakHashMap<>();
    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

    private TetherBlades() {
    }

    /** The modifier the Edges register: the Mark's crit chance and the single blade's damage. */
    static final HitModifiers.Modifier MODIFIER = new HitModifiers.Modifier() {
        @Override
        public double critChanceBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
            return target == null ? 0.0 : markBonus(target);
        }

        @Override
        public double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
            if (attacker == null || move == null || move.def().kind() == MoveKind.ABILITY) {
                return 1.0; // the throw and the blink are the blades' own
            }
            ThrownSickle blade = bladeOf(attacker);
            return blade != null && blade.isOut() ? blade.soloScale() : 1.0;
        }
    };

    // ------------------------------------------------------------------ the blade's life

    /** {@code player} threw {@code blade}: an older blade still out comes back first. */
    static void thrown(ServerPlayer player, ThrownSickle blade) {
        ThrownSickle old = BLADES.put(player, blade);
        if (old != null && old != blade && !old.isRemoved()) {
            old.startReturn();
        }
    }

    /**
     * The blade stuck, in {@code target} or (null) in a block: the thrower's blink opens for {@code stickTicks},
     * and an enemy is Marked for {@code markTicks} (+{@code markCrit} crit chance).
     */
    public static void stuck(ServerPlayer player, ThrownSickle blade, @Nullable LivingEntity target, int stickTicks,
                             int markTicks, double markCrit) {
        CombatStateMachine machine = PlayerCombat.of(player).machine();
        WeaponDef weapon = machine.weapon();
        if (weapon != null) {
            weapon.ability().flatMap(WeaponDef.Ability::recast).ifPresent(r -> machine.armRecast(r.move(), stickTicks));
        }
        send(player, STUCK, blade.position(), target == null ? 0f : 1f, stickTicks);
        ServerCombatSounds.forEveryone(player.level(), blade.position(), ModSounds.EDGES_STICK, 0.9f,
                target == null ? 0.95f : 1.1f);
        if (target != null) {
            mark(player, target, markTicks, markCrit);
        }
    }

    /** The blade is back (or on its way back, which counts): the blink closes, the cooldown starts. */
    public static void returned(ServerPlayer player, ThrownSickle blade) {
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat != null && combat.isServerSide()) {
            combat.machine().disarmRecast();
        }
        send(player, RETURNED, blade.position(), 0f, 0);
    }

    /** The blade is gone from the world (home in the hand, or unloaded). */
    public static void removed(ServerPlayer player, ThrownSickle blade) {
        if (BLADES.get(player) == blade) {
            BLADES.remove(player);
        }
    }

    /** {@code target} is Marked for {@code ticks}: +{@code bonus} crit chance against it. */
    public static void mark(ServerPlayer by, LivingEntity target, int ticks, double bonus) {
        MARKS.put(target, new Mark(target.level().getGameTime() + ticks, bonus));
        send(by, MARKED, target.position().add(0, target.getBbHeight(), 0), ticks, target.getId());
    }

    // ------------------------------------------------------------------ queries

    /** {@code player}'s blade, if one is out or on its way back. */
    public static @Nullable ThrownSickle bladeOf(ServerPlayer player) {
        ThrownSickle blade = BLADES.get(player);
        return blade != null && !blade.isRemoved() ? blade : null;
    }

    /** The crit chance bonus against {@code target} while it is Marked, else 0. */
    public static double markBonus(LivingEntity target) {
        Mark mark = MARKS.get(target);
        if (mark == null) {
            return 0.0;
        }
        if (target.level().getGameTime() >= mark.until()) {
            MARKS.remove(target);
            return 0.0;
        }
        return mark.bonus();
    }

    /** Ticks {@code target} stays Marked (0: not Marked). */
    public static int markTicksLeft(LivingEntity target) {
        Mark mark = MARKS.get(target);
        return mark == null ? 0 : (int) Math.max(0, mark.until() - target.level().getGameTime());
    }

    static void clear() {
        BLADES.clear();
        MARKS.clear();
    }

    private static void send(ServerPlayer player, int stage, Vec3 at, float value, int ticks) {
        ModNetworking.sendToTrackersAndSelf(player, new MoveEffectPayload(player.getId(), ID, stage, at, value, ticks));
    }
}
