package com.cosmicbreach.gear.regalia;

import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Hymn of Alignment (GDD 5.1), the Choir Regalia's set ability: 40 s cooldown, no Resonance. A ring of radius
 * 6 on the ground at the caster's feet for 120 ticks ({@link HymnRings}): allies inside get +30 Haste and double
 * Resonance gain, enemies inside are Aligned (+10% damage taken from abilities, {@link Aligned}).
 */
public final class HymnOfAlignment implements SetAbility {
    /** Players this far away see and hear the ring (and so can stand in it). */
    private static final double FX_RANGE = 96.0;

    @Override
    public int baseCooldown() {
        return RegaliaRules.HYMN_COOLDOWN;
    }

    @Override
    public boolean cast(ServerPlayer player, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 centre = player.position();
        long until = now + RegaliaRules.HYMN_TICKS;
        HymnRings.add(level, new HymnRings.Ring(level.dimension(), centre, RegaliaRules.HYMN_RADIUS, until, player.getId()));
        ArmorSets.setState(player, ArmorSets.state(player).withActive(ChoirRegalia.SET.id(), until));
        level.playSound(null, centre.x, centre.y, centre.z, GearSets.HYMN.get(), SoundSource.PLAYERS, 1.6f, 1.0f);
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, FX_RANGE,
                new SetFxPayload(SetFxPayload.Kind.HYMN, player.getId(), centre, 0f, Vec3.ZERO, SetFxPayload.NO_MOVE,
                        RegaliaRules.HYMN_TICKS, (float) RegaliaRules.HYMN_RADIUS));
        return true;
    }
}
