package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.world.AetheriaWorld;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Falling into the Breach from the arena (GDD 7.3, the rules in {@link RescueRule}): the player is thrown back onto the
 * rim (or the nearest ring still standing), facing the throne, with half their current health and 60 ticks of
 * Voidsick. Always on. Nothing happens if no floor is left at all (or the Sanctum isn't there), and the void takes them.
 */
public final class FallRescue {
    private static int rescues;
    private static float lastBefore = Float.NaN;
    private static float lastAfter = Float.NaN;
    private static Vec3 lastSpot;

    private FallRescue() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AetheriaWorld.is(p.level()) || !p.isAlive()) {
            return;
        }
        if (RescueRule.shouldRescue(p.getX(), p.getY(), p.getZ(), p.onGround(), p.isCreative() || p.isSpectator())) {
            rescue(p);
        }
    }

    /** Throws {@code p} back onto the arena. False if nothing of it stands. */
    public static boolean rescue(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<double[]> candidates = RescueRule.candidates(p.getX(), p.getZ());
        double[] spot = RescueRule.pick(candidates, (x, z) -> standing(level, x, z));
        if (spot == null) {
            return false;
        }
        double dx = 0.5 - spot[0];
        double dz = 0.5 - spot[1];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.stopRiding();
        p.teleportTo(level, spot[0], SanctumLayout.ARENA_Y, spot[1], Set.of(), yaw, 10f);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        float before = p.getHealth();
        float after = RescueRule.healthAfter(before);
        p.setHealth(after);
        lastBefore = before;
        lastAfter = after;
        lastSpot = new Vec3(spot[0], SanctumLayout.ARENA_Y, spot[1]);
        level.getChunkSource().broadcastAndSend(p, new ClientboundHurtAnimationPacket(p));
        p.addEffect(new MobEffectInstance(SanctumRegistry.VOIDSICK, RescueRule.VOIDSICK_TICKS, 0, false, true, true));
        BlockPos at = BlockPos.containing(spot[0], SanctumLayout.ARENA_Y, spot[1]);
        level.playSound(null, at, SanctumRegistry.FALL_RESCUE.get(), SoundSource.PLAYERS, 1.6f, 1.0f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot[0], SanctumLayout.ARENA_Y + 1.0, spot[1], 60, 0.4, 1.0, 0.4, 0.08);
        level.sendParticles(ParticleTypes.FLAME, spot[0], SanctumLayout.ARENA_Y + 0.2, spot[1], 16, 0.6, 0.1, 0.6, 0.02);
        Sanctums.subtitle(p, Component.translatable("cosmicbreach.sanctum.rescued"));
        rescues++;
        com.cosmicbreach.voice.boss.BossVoices.playerFell(p);
        return true;
    }

    /** True if a player can land at (x, z) on the arena: a solid floor block at Y 63 and room above it. */
    static boolean standing(ServerLevel level, double x, double z) {
        BlockPos floor = BlockPos.containing(x, SanctumLayout.FLOOR_Y, z);
        if (!level.isLoaded(floor)) {
            return false;
        }
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
                && level.getBlockState(floor.above(2)).getCollisionShape(level, floor.above(2)).isEmpty();
    }

    /** "rescues" and the last one's health before and after and where it landed (for tests). */
    public static String counters() {
        return rescues + " " + lastBefore + " " + lastAfter + " " + (lastSpot == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f",
                lastSpot.x, lastSpot.y, lastSpot.z));
    }

    public static int rescues() {
        return rescues;
    }

    public static float[] lastHealth() {
        return new float[] {lastBefore, lastAfter};
    }

    public static Vec3 lastSpot() {
        return lastSpot;
    }

    public static void reset() {
        rescues = 0;
        lastBefore = Float.NaN;
        lastAfter = Float.NaN;
        lastSpot = null;
    }
}
