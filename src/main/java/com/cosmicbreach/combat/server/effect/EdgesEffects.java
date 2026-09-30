package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Binary Edges' server effects (GDD 4.2): the Tether's throw ({@link #TETHER}: the left sickle leaves the
 * hand on the throw's first active tick, see {@link ThrownSickle} and {@link TetherBlades}) and the blink to it
 * ({@link #BLINK}: 6 ticks of travel the client flies, invulnerable from the move's own phases, then the
 * arrival: an enemy the blade was in is Marked, the blade is back in hand, and the move's arc slashes). No fall
 * damage from a blink. The Orbit's and the Twin Meteor's effects are visual only.
 */
public final class EdgesEffects {
    public static final ResourceLocation TETHER = TetherBlades.ID;
    public static final ResourceLocation BLINK = CosmicBreach.id("blink");
    /** Blink moment: the player arrived ({@code at}); others hear the chime and see the flash. */
    public static final int ARRIVED = 0;
    /** No fall damage for this many ticks after a blink ends. */
    private static final int FALL_GRACE = 10;

    /** Game time until which a blinking player takes no fall damage. */
    private static final Map<ServerPlayer, Long> FALL_SAFE = new WeakHashMap<>();

    private EdgesEffects() {
    }

    public static void register(IEventBus gameBus) {
        ServerMoveEffects.register(TETHER, new Tether());
        ServerMoveEffects.register(BLINK, new Blink());
        HitModifiers.register(TetherBlades.MODIFIER);
        gameBus.addListener(LivingFallEvent.class, EdgesEffects::onFall);
        gameBus.addListener(ServerStoppingEvent.class, event -> {
            TetherBlades.clear();
            FALL_SAFE.clear();
        });
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && FALL_SAFE.containsKey(player)) {
                player.resetFallDistance();
            }
        });
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Long until = FALL_SAFE.get(player);
            if (until != null) {
                if (player.level().getGameTime() <= until) {
                    event.setCanceled(true);
                } else {
                    FALL_SAFE.remove(player);
                }
            }
        }
    }

    /** The throw: the left sickle leaves the hand toward the crosshair on the first active tick. */
    static final class Tether implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true; // the blade strikes what it flies into
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            Vec3 look = player.getLookAngle();
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vec3 from = player.getEyePosition().add(look.scale(0.4)).add(left.scale(0.28)).add(0, -0.3, 0);
            ThrownSickle blade = new ThrownSickle(player.level(), player, from, look, use.param("speed", 2.0));
            blade.configure(use.move(), use.param("range", 16.0), use.intParam("stick", 80), use.param("solo", 0.6),
                    use.intParam("mark_ticks", 60), use.param("mark_crit", 0.2));
            player.level().addFreshEntity(blade);
            TetherBlades.thrown(player, blade);
        }
    }

    /** The blink: the blade holds still for it; on arrival the enemy it was in is Marked and the blade comes back. */
    static final class Blink implements ServerMoveEffect {
        @Override
        public void started(Use use) {
            ServerPlayer player = use.player();
            ThrownSickle blade = TetherBlades.bladeOf(player);
            if (blade != null && blade.isStuck()) {
                blade.blinkedTo();
            }
            safeFromFalls(player, use.intParam("travel", 6) + FALL_GRACE);
            ServerCombatSounds.forOthers(player, ModSounds.EDGES_BLINK, 1.0f, 1.0f);
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            safeFromFalls(player, FALL_GRACE);
            ThrownSickle blade = TetherBlades.bladeOf(player);
            if (blade != null) {
                if (blade.isStuck() && blade.stuckEntity() instanceof LivingEntity target && target.isAlive()) {
                    TetherBlades.mark(player, target, blade.markTicks(), blade.markCrit());
                }
                blade.retrieve();
            }
            ModNetworking.sendToTrackersAndSelf(player, new MoveEffectPayload(player.getId(), BLINK, ARRIVED, player.position(), 0f, 0));
            ServerCombatSounds.forOthers(player, ModSounds.EDGES_CHIME, 0.9f, 1.0f);
        }

        @Override
        public void ended(Use use, boolean cancelled) {
            ThrownSickle blade = TetherBlades.bladeOf(use.player());
            if (blade != null && blade.isOut()) {
                blade.startReturn(); // cut short before it arrived
            }
        }

        private static void safeFromFalls(ServerPlayer player, int ticks) {
            player.resetFallDistance();
            FALL_SAFE.put(player, player.level().getGameTime() + ticks);
        }
    }

    /** True while {@code player}'s machine runs the blink (for tests and tools). */
    public static boolean isBlinking(ServerPlayer player) {
        MoveInstance move = PlayerCombat.of(player).machine().current();
        return move != null && move.def().traits().effect(BLINK).isPresent();
    }
}
