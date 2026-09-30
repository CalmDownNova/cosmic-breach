package com.cosmicbreach.gear.driftweave;

import com.cosmicbreach.combat.HitLedger;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.GhostHits;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.net.ModNetworking;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Slipstream (GDD 5.1), the Driftweave's four-piece bonus: a perfect dodge leaves an Afterimage where the dodged
 * blow was aimed (where the dash started), for 60 ticks. It repeats the wearer's next 3 attacks (light, charged, dash and
 * plunge moves; not abilities) from where it stands, each at 40% of the damage the wearer's own hit would deal
 * (crit included when the attack is a guaranteed crit), 40% of its Impact, with the move's own hitbox. It faces
 * the nearest enemy within the move's reach, or where the wearer faces. A new perfect dodge moves it. It fades
 * when its 60 ticks are up, or once its third repeat is over.
 *
 * <p>Clients draw it (a translucent star-dust copy of the wearer playing the same moves): {@code AFTERIMAGE},
 * {@code AFTERIMAGE_REPEAT}, {@code AFTERIMAGE_STRIKE} and {@code AFTERIMAGE_END} of {@link SetFxPayload}.
 */
public final class Slipstream {
    /** Players this far away see it. */
    private static final double FX_RANGE = 64.0;

    private static final class Repeat {
        final MoveInstance move;
        final GhostHits.Source source;

        Repeat(MoveInstance move, GhostHits.Source source) {
            this.move = move;
            this.source = source;
        }
    }

    /** One Afterimage. */
    public static final class Afterimage {
        final ServerLevel level;
        final Vec3 feet;
        final float yaw;
        final long until;
        int repeatsLeft = DriftweaveRules.AFTERIMAGE_REPEATS;
        final HitLedger ledger = new HitLedger();
        final Map<Integer, Repeat> running = new HashMap<>();

        Afterimage(ServerLevel level, Vec3 feet, float yaw, long until) {
            this.level = level;
            this.feet = feet;
            this.yaw = yaw;
            this.until = until;
        }

        public Vec3 feet() {
            return feet;
        }

        public int repeatsLeft() {
            return repeatsLeft;
        }

        boolean done(long now) {
            return running.isEmpty() && (now >= until || repeatsLeft <= 0);
        }
    }

    private static final Map<UUID, Afterimage> AFTERIMAGES = new HashMap<>();
    /** Where each wearer's last dash started: the spot the dodged blow was aimed at, where the Afterimage stands. */
    private static final Map<UUID, Vec3> DASH_FROM = new HashMap<>();

    private Slipstream() {
    }

    /** A combat event of a player in the full set. */
    static void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
        long now = player.level().getGameTime();
        switch (event) {
            case CombatEvent.DashStarted dash -> DASH_FROM.put(player.getUUID(), player.position());
            case CombatEvent.PerfectDodge dodge -> spawn(player, now);
            case CombatEvent.MoveStarted started -> repeat(player, combat, started.move(), now);
            case CombatEvent.ActiveTick active -> {
                Afterimage image = AFTERIMAGES.get(player.getUUID());
                Repeat repeat = image == null ? null : image.running.get(active.move().serial());
                if (repeat != null && active.move().def().kind() != MoveKind.PLUNGE) {
                    GhostHits.activeTick(repeat.source, repeat.move, active.activeTick(), image.ledger, repeat.move.serial(), now,
                            (point, direction, target, impact) -> struck(player, point, direction, impact));
                }
            }
            case CombatEvent.PlungeLanded landed -> {
                Afterimage image = AFTERIMAGES.get(player.getUUID());
                Repeat repeat = image == null ? null : image.running.get(landed.move().serial());
                if (repeat != null) {
                    GhostHits.landing(repeat.source, repeat.move, landed.fallBlocks(), image.ledger, repeat.move.serial(), now,
                            (point, direction, target, impact) -> struck(player, point, direction, impact));
                }
            }
            case CombatEvent.MoveEnded ended -> {
                Afterimage image = AFTERIMAGES.get(player.getUUID());
                if (image != null && image.running.remove(ended.move().serial()) != null) {
                    image.ledger.forget(ended.move().serial());
                }
            }
            default -> {
            }
        }
    }

    /** The Afterimage of {@code player}, or null. */
    public static @Nullable Afterimage of(ServerPlayer player) {
        return AFTERIMAGES.get(player.getUUID());
    }

    private static void spawn(ServerPlayer player, long now) {
        Vec3 from = DASH_FROM.get(player.getUUID());
        Vec3 feet = from != null && from.distanceToSqr(player.position()) < 64.0 ? from : player.position();
        Afterimage image = new Afterimage(player.serverLevel(), feet, player.getYRot(), now + DriftweaveRules.AFTERIMAGE_TICKS);
        AFTERIMAGES.put(player.getUUID(), image);
        player.level().playSound(null, feet.x, feet.y + 1.0, feet.z, GearSets.AFTERIMAGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new SetFxPayload(SetFxPayload.Kind.AFTERIMAGE, player.getId(), feet,
                player.getYRot(), Vec3.ZERO, SetFxPayload.NO_MOVE, DriftweaveRules.AFTERIMAGE_TICKS, image.repeatsLeft));
    }

    /** The wearer started a move: an attack is repeated while the Afterimage stands and has repeats left. */
    private static void repeat(ServerPlayer player, PlayerCombat combat, MoveInstance move, long now) {
        Afterimage image = AFTERIMAGES.get(player.getUUID());
        WeaponDef weapon = combat.machine().weapon();
        if (image == null || weapon == null || move.def().kind() == MoveKind.ABILITY || image.repeatsLeft <= 0
                || now >= image.until || image.level != player.level()) {
            return;
        }
        image.repeatsLeft--;
        float yaw = aim(player, image, move);
        int tierSteps = GearTier.stepsAbove(player.getMainHandItem(), weapon.tier());
        GhostHits.Source source = new GhostHits.Source(player, weapon, tierSteps, image.feet, yaw, DriftweaveRules.AFTERIMAGE_DAMAGE,
                DriftweaveRules.AFTERIMAGE_IMPACT, GearSets.AFTERIMAGE_DAMAGE);
        image.running.put(move.serial(), new Repeat(move, source));
        int length = move.def().timing().startup() + move.def().timing().active() + move.def().timing().recovery();
        ModNetworking.sendToTrackersAndSelf(player, new SetFxPayload(SetFxPayload.Kind.AFTERIMAGE_REPEAT, player.getId(), image.feet,
                yaw, Vec3.ZERO, move.id(), length, image.repeatsLeft));
    }

    /** Toward the nearest enemy the move could reach from the Afterimage, else where the wearer faces. */
    private static float aim(ServerPlayer player, Afterimage image, MoveInstance move) {
        double reach = move.def().hitbox().reach() + 1.0;
        Vec3 chest = image.feet.add(0, HitResolver.CHEST * player.getBbHeight(), 0);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity target : HitResolver.candidates(player, new AABB(chest, chest).inflate(reach, 2.0, reach))) {
            double d = target.position().distanceToSqr(image.feet);
            if (d < best && d <= reach * reach) {
                best = d;
                nearest = target;
            }
        }
        if (nearest == null) {
            return player.getYRot();
        }
        Vec3 d = nearest.position().subtract(image.feet);
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
    }

    private static void struck(ServerPlayer player, Vec3 point, Vec3 direction, double impact) {
        player.level().playSound(null, point.x, point.y, point.z, GearSets.AFTERIMAGE_STRIKE.get(), SoundSource.PLAYERS, 0.8f,
                0.95f + player.getRandom().nextFloat() * 0.1f);
        ModNetworking.sendToTrackersAndSelf(player, new SetFxPayload(SetFxPayload.Kind.AFTERIMAGE_STRIKE, player.getId(), point, 0f,
                direction, SetFxPayload.NO_MOVE, 0, (float) impact));
    }

    /** Every server tick: Afterimages that are done fade. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DASH_FROM.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        Iterator<Map.Entry<UUID, Afterimage>> it = AFTERIMAGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Afterimage> e = it.next();
            Afterimage image = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            long now = image.level.getGameTime();
            if (player == null || player.level() != image.level || !player.isAlive()) {
                it.remove();
                continue;
            }
            if (image.done(now)) {
                it.remove();
                ModNetworking.sendToTrackersAndSelf(player, SetFxPayload.at(SetFxPayload.Kind.AFTERIMAGE_END, player.getId(), image.feet, 0));
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        AFTERIMAGES.clear();
        DASH_FROM.clear();
    }

    /** Players this far away are told (the payload goes to the wearer's trackers, which covers it). */
    static double fxRange() {
        return FX_RANGE;
    }
}
