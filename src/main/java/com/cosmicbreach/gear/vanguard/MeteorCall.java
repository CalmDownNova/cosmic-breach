package com.cosmicbreach.gear.vanguard;

import com.cosmicbreach.gear.GearDamage;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.Scorch;
import com.cosmicbreach.gear.net.GearFxPayload;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetAbility;
import com.cosmicbreach.progression.ProgressionStats;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Meteor Call (GDD 5.1), the Starfall Vanguard's set ability: 30 s cooldown, no Resonance. It marks the ground
 * where the player looks, up to 24 blocks away; a burning circle (radius 4) shows everyone the spot for 30
 * ticks, then a meteor lands there: 12 x (1 + 0.55 x S(Power)) damage (x1.5 on a Scorched target), Impact 60,
 * and the crater burns for 60 ticks, Scorching whatever stands in it.
 */
public final class MeteorCall implements SetAbility {
    /** Players this far away see the mark, the fall and the crater. */
    private static final double FX_RANGE = 128.0;
    private static final double BAND_BELOW = 1.5;
    private static final double BAND_ABOVE = 3.0;
    private static final double MAX_PUSH = 1.2;
    /** Looking past the ground's reach, the spot is found this far down under the aim's end. */
    private static final int GROUND_SEARCH = 24;

    private record Pending(ServerLevel level, Vec3 centre, UUID owner, long landsAt) {}

    private record Crater(ServerLevel level, Vec3 centre, UUID owner, long until) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Crater> CRATERS = new ArrayList<>();

    @Override
    public int baseCooldown() {
        return VanguardRules.METEOR_COOLDOWN;
    }

    @Override
    public boolean cast(ServerPlayer player, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 centre = aim(player);
        if (centre == null) {
            player.displayClientMessage(Component.translatable("message.cosmicbreach.meteor_call.no_ground"), true);
            return false;
        }
        PENDING.add(new Pending(level, centre, player.getUUID(), now + VanguardRules.METEOR_WARNING_TICKS));
        ArmorSets.setState(player, ArmorSets.state(player).withActive(StarfallVanguard.SET.id(),
                now + VanguardRules.METEOR_WARNING_TICKS + VanguardRules.CRATER_TICKS));
        level.playSound(null, centre.x, centre.y, centre.z, GearRegistry.METEOR_INCOMING.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, FX_RANGE,
                new GearFxPayload(GearFxPayload.Kind.METEOR_MARK, player.getId(), centre,
                        (float) VanguardRules.METEOR_RADIUS, VanguardRules.METEOR_WARNING_TICKS));
        return true;
    }

    /**
     * The spot the player marks: where its look meets a block within 24 blocks (the top of that block's column
     * if it hit a side), or failing that the ground under the look's end. Null if there is no ground there.
     */
    public static @Nullable Vec3 aim(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(VanguardRules.METEOR_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        BlockPos start = BlockPos.containing(point.x, point.y + 0.5, point.z);
        for (int i = 0; i <= GROUND_SEARCH; i++) {
            BlockPos below = start.below(i + 1);
            BlockPos at = start.below(i);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return new Vec3(point.x, at.getY(), point.z);
            }
        }
        return null;
    }

    /** Every server tick: land the meteors that are due and let the craters burn. */
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Pending> meteors = PENDING.iterator();
        while (meteors.hasNext()) {
            Pending meteor = meteors.next();
            if (meteor.level().getGameTime() >= meteor.landsAt()) {
                meteors.remove();
                land(meteor);
            }
        }
        Iterator<Crater> craters = CRATERS.iterator();
        while (craters.hasNext()) {
            Crater crater = craters.next();
            long now = crater.level().getGameTime();
            if (now >= crater.until()) {
                craters.remove();
                continue;
            }
            burn(crater, now);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        CRATERS.clear();
    }

    private static void land(Pending meteor) {
        ServerLevel level = meteor.level();
        Vec3 centre = meteor.centre();
        level.playSound(null, centre.x, centre.y, centre.z, GearRegistry.METEOR_IMPACT.get(), SoundSource.PLAYERS, 3.0f, 1.0f);
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z, 3, 1.0, 0.3, 1.0, 0.0);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(meteor.owner());
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, FX_RANGE,
                new GearFxPayload(GearFxPayload.Kind.METEOR_IMPACT, owner == null ? -1 : owner.getId(), centre,
                        (float) VanguardRules.METEOR_RADIUS, VanguardRules.CRATER_TICKS));
        CRATERS.add(new Crater(level, centre, meteor.owner(), level.getGameTime() + VanguardRules.CRATER_TICKS));
        if (owner == null || owner.level() != level) {
            return; // its caster left: the crater still burns, but nothing is hit in their name
        }
        int power = ProgressionStats.of(owner).power();
        DamageSource source = GearDamage.source(level, GearRegistry.METEOR_DAMAGE, owner, centre);
        for (LivingEntity target : GearDamage.inRadius(owner, level, centre, VanguardRules.METEOR_RADIUS, BAND_BELOW, BAND_ABOVE)) {
            boolean scorched = target.hasEffect(GearRegistry.SCORCH);
            float damage = (float) VanguardRules.meteorDamage(power, scorched);
            GearDamage.blast(target, source, damage, centre, VanguardRules.METEOR_IMPACT, MAX_PUSH);
        }
    }

    /** Scorches what stands in the crater and isn't already burning. */
    private static void burn(Crater crater, long now) {
        ServerLevel level = crater.level();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(crater.owner());
        if (owner != null && owner.level() == level) {
            for (LivingEntity target : GearDamage.inRadius(owner, level, crater.centre(), VanguardRules.METEOR_RADIUS, 0.5, 1.5)) {
                if (!target.hasEffect(GearRegistry.SCORCH)) {
                    target.addEffect(new MobEffectInstance(GearRegistry.SCORCH, Scorch.TICKS), owner);
                }
            }
        }
        if (now % 4 == 0) {
            Vec3 c = crater.centre();
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.1, c.z, 6, VanguardRules.METEOR_RADIUS * 0.45, 0.05,
                    VanguardRules.METEOR_RADIUS * 0.45, 0.01);
        }
    }

    /** Test and scenario access: meteors still falling. */
    public static int pendingCount() {
        return PENDING.size();
    }
}
