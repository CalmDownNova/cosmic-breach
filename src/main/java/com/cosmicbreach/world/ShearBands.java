package com.cosmicbreach.world;

import com.cosmicbreach.CosmicBreach;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Shear bands (GDD 2.1): open sky between the layers that only attuned players fall through.
 *
 * <ul>
 *   <li>A player without the attunement of the layer below who falls into a band is caught: put back on the
 *       last block they stood on in Aetheria, 4 damage ({@code cosmicbreach:shear}, through armour), the
 *       subtitle "The song won't carry you yet." and a chime. Arrival grace: a player who has not stood on
 *       anything in Aetheria yet (just arrived and missed the island) goes to the nearest safe island top
 *       and takes no damage. Creative and spectator players pass freely; rising through a band is never
 *       stopped.</li>
 *   <li>An attuned player falls through and gets 5 s of Slow Falling, once per pass.</li>
 *   <li>A mob that falls into a band is removed without drops, so each layer keeps its own population. Tamed
 *       mobs go back to their owner instead; fliers are only taken when plummeting; a mount follows its
 *       rider's fate.</li>
 * </ul>
 * Server side; called every tick for each entity in Aetheria from {@link AetheriaRules}.
 */
public final class ShearBands {
    public static final float DAMAGE = 4.0f;
    public static final int SLOW_FALLING_TICKS = 100;
    public static final ResourceKey<DamageType> SHEAR = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("shear"));
    public static final String SUBTITLE_KEY = "cosmicbreach.shear.caught";

    /** Last block each player stood on in Aetheria. */
    private static final Map<UUID, BlockPos> LAST_STOOD = new ConcurrentHashMap<>();
    /** The band each attuned player is falling through (Slow Falling once per pass). */
    private static final Map<UUID, ShearBand> PASSING = new ConcurrentHashMap<>();
    /** Each player's height last tick: a server player's motion comes from packets, so it is measured here. */
    private static final Map<UUID, Double> LAST_Y = new ConcurrentHashMap<>();
    /** Counters for tests: players caught, players passed, mobs removed. */
    private static int caught;
    private static int passed;
    private static int removed;

    private ShearBands() {
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (!AetheriaWorld.is(player.level())) {
            return;
        }
        UUID id = player.getUUID();
        Double lastY = LAST_Y.put(id, player.getY());
        double dy = lastY == null ? 0.0 : player.getY() - lastY;
        if (player.onGround() && !player.isPassenger()) {
            BlockPos on = player.getOnPos();
            // the supporting block is cached from the last move, so just after a teleport it can still be the one
            // the player left (in another place or dimension): only trust one right under their feet
            boolean underFeet = Math.abs(player.getY() - (on.getY() + 1)) < 1.0
                    && Math.abs(player.getX() - (on.getX() + 0.5)) < 1.5 && Math.abs(player.getZ() - (on.getZ() + 0.5)) < 1.5;
            if (underFeet && !player.level().getBlockState(on).isAir() && ShearBand.at(on.getY()) == null) {
                LAST_STOOD.put(id, on.immutable());
            }
        }
        ShearBand band = ShearBand.at(player.getY());
        if (band == null) {
            PASSING.remove(id);
            return;
        }
        if (player.isCreative() || player.isSpectator() || dy > 0.0) {
            return;
        }
        if (LayerAttunement.has(player, band.guards)) {
            if (PASSING.get(id) != band) {
                PASSING.put(id, band);
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true, true));
                passed++;
            }
            return;
        }
        catchPlayer(player, band);
    }

    private static void catchPlayer(ServerPlayer player, ShearBand band) {
        ServerLevel level = player.serverLevel();
        // arrival grace: someone who has not stood on anything here yet (just arrived, or logged in mid-air)
        // is set down on the nearest island without the damage
        boolean grace = !LAST_STOOD.containsKey(player.getUUID());
        BlockPos feet = returnSpot(level, player, band);
        player.stopRiding();
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        if (!grace) {
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().source(SHEAR), DAMAGE);
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(SUBTITLE_KEY)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        level.playSound(null, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.END_ROD, feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, 24, 0.4, 0.8, 0.4, 0.02);
        caught++;
    }

    /** Where a caught player goes: the last block they stood on (if it is still safe), else the nearest island top. */
    private static BlockPos returnSpot(ServerLevel level, ServerPlayer player, ShearBand band) {
        BlockPos last = LAST_STOOD.get(player.getUUID());
        if (last != null && last.getY() >= band.maxY) {
            Optional<BlockPos> feet = AetheriaSpots.settle(level, last.above(), 2, 3);
            if (feet.isPresent()) {
                return feet.get();
            }
        }
        AetheriaSpots.Kind kind = band == ShearBand.A ? AetheriaSpots.Kind.SPIRES : AetheriaSpots.Kind.DRIFT;
        Optional<AetheriaSpots.Spot> spot = AetheriaSpots.safeSpot(level, kind, player.blockPosition());
        if (spot.isEmpty() && kind == AetheriaSpots.Kind.SPIRES) {
            spot = AetheriaSpots.safeSpot(level, AetheriaSpots.Kind.SUNFIELD, player.blockPosition());
        }
        return spot.map(AetheriaSpots.Spot::feet).orElse(new BlockPos(player.getBlockX(), band.maxY + 40, player.getBlockZ()));
    }

    /** Mobs (and anything they carry) falling into a band. */
    public static void onMobTick(Mob mob) {
        ShearBand band = ShearBand.at(mob.getY());
        if (band == null || mob.isNoGravity() || mob.getDeltaMovement().y >= 0 || mob.isPassenger()) {
            return;
        }
        if (mob.getFirstPassenger() instanceof Player) {
            return; // the rider decides; if the rider is caught the mount is left to fall alone
        }
        boolean flier = mob instanceof FlyingAnimal || mob.getNavigation() instanceof FlyingPathNavigation;
        if (flier && mob.getDeltaMovement().y > -0.4) {
            return;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            Entity owner = ownable.getOwner();
            if (owner instanceof ServerPlayer player && player.level() == mob.level() && ShearBand.at(player.getY()) == null) {
                mob.teleportTo(player.getX(), player.getY(), player.getZ());
                mob.setDeltaMovement(Vec3.ZERO);
                mob.resetFallDistance();
                return;
            }
        }
        mob.discard();
        removed++;
    }

    public static void forget(Player player) {
        LAST_STOOD.remove(player.getUUID());
        PASSING.remove(player.getUUID());
        LAST_Y.remove(player.getUUID());
    }

    /** The last block {@code player} stood on in Aetheria, if known. */
    public static @Nullable BlockPos lastStood(Player player) {
        return LAST_STOOD.get(player.getUUID());
    }

    /** "caught passed removed" since the server started (for tests). */
    public static String counters() {
        return caught + " " + passed + " " + removed;
    }

    /** Forgets everything (server start and stop: positions mean nothing in another world). */
    public static void resetCounters() {
        caught = 0;
        passed = 0;
        removed = 0;
        LAST_STOOD.clear();
        PASSING.clear();
        LAST_Y.clear();
    }
}
