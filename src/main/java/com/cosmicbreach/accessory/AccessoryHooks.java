package com.cosmicbreach.accessory;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.net.ModNetworking;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * What the accessories tell the combat engine (GDD 5.2), in one hook and one hit modifier:
 * <ul>
 *   <li>the machine's gear numbers, on both sides from the synced slots: the Choir Pendant's +20 max Resonance, the
 *       Event Horizon Lens's +2 parry ticks, the Twin Comet Band's free air dash (and its level air dashes, read by the
 *       client's body);</li>
 *   <li>the server's combat events: a dash's end (the Perihelion Loop's window), a plunge's landing (the Gravity Loop's
 *       well from 10 blocks up), a perfect dodge (the Hourglass of Vesper's slow);</li>
 *   <li>every parry the server grants: the Leechstar Signet's heal, the Lens's black hole on an early one;</li>
 *   <li>every hit: the Perihelion Loop's +8% crit chance and its +0.25 crit multiplier after a dash, the Gravity Loop's
 *       plunges without a cap.</li>
 * </ul>
 */
final class AccessoryHooks implements CombatHooks.Hook, HitModifiers.Modifier {
    /** When each player's last dash ended, in game time (server). */
    private static final Map<UUID, Long> LAST_DASH_END = new ConcurrentHashMap<>();
    private static final DustParticleOptions SAND = new DustParticleOptions(new Vector3f(0.95f, 0.8f, 0.5f), 1.1f);

    static void forget(UUID player) {
        LAST_DASH_END.remove(player);
    }

    static void clear() {
        LAST_DASH_END.clear();
    }

    // ------------------------------------------------------------------ the machine's numbers

    @Override
    public int maxResonanceBonus(Player player) {
        return Worn.wears(player, Accessory.CHOIR_PENDANT) ? AccessoryRules.PENDANT_RESONANCE : 0;
    }

    @Override
    public int parryWindowBonus(Player player) {
        return Worn.wears(player, Accessory.EVENT_HORIZON_LENS) ? AccessoryRules.LENS_PARRY_TICKS : 0;
    }

    @Override
    public int airDashBonus(Player player) {
        return Worn.wears(player, Accessory.TWIN_COMET_BAND) ? AccessoryRules.COMET_AIR_DASHES : 0;
    }

    @Override
    public boolean airDashesHoldHeight(Player player) {
        return Worn.wears(player, Accessory.TWIN_COMET_BAND);
    }

    // ------------------------------------------------------------------ combat events

    @Override
    public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
        switch (event) {
            case CombatEvent.DashStarted started -> {
                if (!player.onGround() && Worn.wears(player, Accessory.TWIN_COMET_BAND)) {
                    player.level().playSound(null, player.getX(), player.getY() + 0.9, player.getZ(),
                            AccessoryRegistry.COMET_CHAIN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }
            case CombatEvent.DashEnded ended -> LAST_DASH_END.put(player.getUUID(), player.level().getGameTime());
            case CombatEvent.PlungeLanded landed -> {
                if (AccessoryRules.opensWell(landed.fallBlocks()) && Worn.wears(player, Accessory.GRAVITY_LOOP)) {
                    openWell(player, player.position());
                }
            }
            case CombatEvent.PerfectDodge dodge -> {
                if (Worn.wears(player, Accessory.HOURGLASS_OF_VESPER)) {
                    slowTheRoom(player);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ parries

    @Override
    public void onParried(ServerPlayer player, PlayerCombat combat, @Nullable LivingEntity attacker, float amount, boolean early) {
        if (Worn.wears(player, Accessory.LEECHSTAR_SIGNET)) {
            float heal = (float) AccessoryRules.leechHeal(amount);
            player.heal(heal);
            Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0);
            player.level().playSound(null, chest.x, chest.y, chest.z, AccessoryRegistry.LEECH_HEAL.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.LEECH, player.getId(), chest, heal, 0));
        }
        if (early && attacker != null && attacker.isAlive() && Worn.wears(player, Accessory.EVENT_HORIZON_LENS)) {
            Vec3 at = attacker.position();
            PullFields.open(PullFields.Kind.BLACK_HOLE, player, at, AccessoryRules.HOLE_RADIUS, AccessoryRules.HOLE_TICKS);
            Vec3 middle = at.add(0, attacker.getBbHeight() * 0.5, 0);
            player.level().playSound(null, middle.x, middle.y, middle.z, AccessoryRegistry.BLACK_HOLE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.BLACK_HOLE, player.getId(), middle,
                    (float) AccessoryRules.HOLE_RADIUS, AccessoryRules.HOLE_TICKS));
        }
    }

    // ------------------------------------------------------------------ hits

    @Override
    public double critChanceBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
        return Worn.wears(attacker, Accessory.PERIHELION_LOOP) ? AccessoryRules.PERIHELION_CRIT_CHANCE : 0.0;
    }

    @Override
    public double critMultiplierBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
        if (attacker == null || !Worn.wears(attacker, Accessory.PERIHELION_LOOP)) {
            return 0.0;
        }
        return AccessoryRules.perihelionCritBonus(PlayerCombat.of(attacker).machine().isDashing(),
                LAST_DASH_END.getOrDefault(attacker.getUUID(), Long.MIN_VALUE / 2), attacker.level().getGameTime());
    }

    @Override
    public boolean uncapsPlunge(@Nullable ServerPlayer attacker) {
        return Worn.wears(attacker, Accessory.GRAVITY_LOOP);
    }

    // ------------------------------------------------------------------ the moments

    /** The Gravity Loop's well where a long plunge landed. */
    static void openWell(ServerPlayer player, Vec3 at) {
        PullFields.open(PullFields.Kind.WELL, player, at, AccessoryRules.LOOP_WELL_RADIUS, AccessoryRules.LOOP_WELL_TICKS);
        player.level().playSound(null, at.x, at.y + 0.5, at.z, AccessoryRegistry.GRAVITY_WELL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.WELL, player.getId(), at,
                (float) AccessoryRules.LOOP_WELL_RADIUS, AccessoryRules.LOOP_WELL_TICKS));
    }

    /** The Hourglass of Vesper: every enemy within 6 blocks slowed by 70% for 20 ticks. Returns how many. */
    static int slowTheRoom(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double r = AccessoryRules.HOURGLASS_RADIUS;
        Vec3 feet = player.position();
        int slowed = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(r),
                t -> HitResolver.isValidTarget(player, t) && isEnemy(player, t))) {
            if (target.position().distanceTo(feet) > r) {
                continue;
            }
            target.addEffect(new MobEffectInstance(AccessoryRegistry.SLOWED, AccessoryRules.HOURGLASS_TICKS, 0, false, true, true), player);
            level.sendParticles(SAND, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 14,
                    target.getBbWidth() * 0.5, target.getBbHeight() * 0.35, target.getBbWidth() * 0.5, 0.01);
            slowed++;
        }
        level.playSound(null, feet.x, feet.y + 1.0, feet.z, AccessoryRegistry.VESPER_SLOW.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.SLOW, player.getId(), feet, (float) r, slowed));
        return slowed;
    }

    /** An enemy of {@code player}: a monster, or anything hunting them. Never another player (safe in multiplayer). */
    static boolean isEnemy(ServerPlayer player, LivingEntity target) {
        if (target instanceof Player) {
            return false;
        }
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player;
    }
}
