package com.cosmicbreach.gear.vanguard;

import com.cosmicbreach.gear.GearDamage;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.net.GearFxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Heavy Landing's shockwave (GDD 5.1): landing from 4 blocks or more knocks everything within 3 blocks away
 * for 3 damage + 0.5 a block fallen (at most 10), Impact 12.
 */
public final class Shockwave {
    /** Everything within this much height of the landing (feet to feet) is caught. */
    private static final double BAND_BELOW = 1.0;
    private static final double BAND_ABOVE = 2.0;
    private static final double MAX_PUSH = 0.9;
    /** Players this far away see and hear it. */
    private static final double FX_RANGE = 64.0;

    private Shockwave() {
    }

    /** Releases the shockwave of a landing after {@code fallBlocks}. Returns how many it hit. */
    public static int release(ServerPlayer player, double fallBlocks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        float damage = (float) VanguardRules.shockwaveDamage(fallBlocks);
        if (damage <= 0f) {
            return 0;
        }
        Vec3 centre = player.position();
        DamageSource source = GearDamage.source(level, GearRegistry.SHOCKWAVE_DAMAGE, player, centre);
        int hit = 0;
        for (LivingEntity target : GearDamage.inRadius(player, level, centre, VanguardRules.SHOCKWAVE_RADIUS, BAND_BELOW, BAND_ABOVE)) {
            if (GearDamage.blast(target, source, damage, centre, VanguardRules.SHOCKWAVE_IMPACT, MAX_PUSH)) {
                hit++;
            }
        }
        level.playSound(null, centre.x, centre.y, centre.z, GearRegistry.VANGUARD_SHOCKWAVE.get(), SoundSource.PLAYERS,
                1.0f, (float) (1.1 - Math.min(0.3, fallBlocks * 0.02)));
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, FX_RANGE,
                new GearFxPayload(GearFxPayload.Kind.SHOCKWAVE, player.getId(), centre, (float) fallBlocks, 0));
        return hit;
    }
}
