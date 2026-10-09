package com.cosmicbreach.jelly;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The bounce and its skim (1.2 design section 7). A player landing on a jelly's bell has the fall cancelled and is
 * launched {@link JellyRules#launchSpeed} (1.3 times the impact, never less than five blocks of height, a slime block
 * returns 1.0), keeping the sideways speed they came with; one who stands on it without sneaking is bounced at the least
 * ({@link DriftJelly#standing}); one who sneaks lands softly and may rest there. Either grants Skim: +30 percent air acceleration for 4 s.
 *
 * <p>The player's motion is the client's, so the launch is sent to them as a motion packet, and Skim's extra air
 * acceleration is added on the client ({@link #onPlayerTick}); the server only decides the launch.
 */
public final class JellyBounce {
    private JellyBounce() {
    }

    public static void register(IEventBus game) {
        game.addListener(LivingFallEvent.class, JellyBounce::onFall);
        game.addListener(PlayerTickEvent.Pre.class, JellyBounce::onPlayerTick);
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        List<DriftJelly> near = player.level().getEntitiesOfClass(DriftJelly.class, player.getBoundingBox().inflate(0.6, 1.2, 0.6));
        for (DriftJelly jelly : near) {
            if (jelly.isAlive() && JellyRules.landsOnBell(player.getX() - jelly.getX(), player.getZ() - jelly.getZ(), player.getY(), jelly.getY())) {
                event.setCanceled(true);      // soft either way: no fall damage on a jelly
                if (!player.isShiftKeyDown()) {
                    bounce(jelly, player, JellyRules.launchSpeed(event.getDistance()));
                }
                return;
            }
        }
    }

    /** Launches {@code player} up at {@code speed} (blocks a tick) off {@code jelly}, unless it did so a moment ago. */
    static void bounce(DriftJelly jelly, Player player, double speed) {
        if (!jelly.bounceReady(player)) {
            return;
        }
        jelly.bounced(player);
        // the server does not track a player's sideways speed; what they covered this tick is a fair measure of it
        Vec3 along = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
        player.setDeltaMovement(along.x, speed, along.z);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
        player.addEffect(new MobEffectInstance(Jellies.SKIM, JellyRules.SKIM_TICKS, 0, false, true, true));
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, jelly.getX(), jelly.getY() + JellyRules.HEIGHT, jelly.getZ(), SoundEvents.SLIME_JUMP, SoundSource.NEUTRAL, 1.0f,
                    0.9f + (float) Math.min(0.5, speed * 0.2));
            level.sendParticles(ParticleTypes.GLOW, jelly.getX(), jelly.getY() + JellyRules.HEIGHT, jelly.getZ(), 10, 0.9, 0.1, 0.9, 0.05);
        }
    }

    /**
     * Skim: while airborne and steering, the player gets {@link JellyRules#extraAirAccel} on top of vanilla's air
     * acceleration, along the keys they hold. Only the controlling client applies it (the server never hears of a
     * player's speed but through their position).
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide() || !p.isLocalPlayer() || !p.hasEffect(Jellies.SKIM)) {
            return;
        }
        if (p.onGround() || p.isFallFlying() || p.getAbilities().flying || p.isInWater() || p.isPassenger() || p.onClimbable()) {
            return;
        }
        float extra = JellyRules.extraAirAccel(true);
        if (p.xxa != 0.0f || p.zza != 0.0f) {
            p.moveRelative(extra, new Vec3(p.xxa, 0.0, p.zza));
        }
    }
}
