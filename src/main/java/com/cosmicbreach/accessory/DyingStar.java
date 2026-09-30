package com.cosmicbreach.accessory;

import com.cosmicbreach.gear.GearDamage;
import com.cosmicbreach.net.ModNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The Heart of a Dying Star (GDD 5.2): +4 max health (its item's attribute), and a hit that leaves the wearer alive
 * under 30% of max health releases a nova (radius 5, 8 damage and a push to everything that may be hit, bosses
 * included) and grants Resistance II for 60 ticks; then the Heart rests 90 s (saved on the player). Server only.
 */
final class DyingStar {
    private DyingStar() {
    }

    static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || event.getNewDamage() <= 0f) {
            return;
        }
        if (!Worn.wears(player, Accessory.HEART_OF_A_DYING_STAR)) {
            return;
        }
        long now = player.level().getGameTime();
        long ready = player.getData(AccessoryRegistry.HEART_READY);
        if (ready - now > AccessoryRules.HEART_COOLDOWN) {
            ready = now; // a rest from another world's clock
        }
        if (!AccessoryRules.novaDue(player.getHealth(), player.getMaxHealth(), ready, now)) {
            return;
        }
        player.setData(AccessoryRegistry.HEART_READY, AccessoryRules.heartReadyAfter(now));
        nova(player);
    }

    /** The nova round {@code player}: returns how many it struck. */
    static int nova(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 feet = player.position();
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(AccessoryRegistry.NOVA_DAMAGE);
        DamageSource source = new DamageSource(type, player, player);
        int struck = 0;
        double r = AccessoryRules.NOVA_RADIUS;
        for (LivingEntity target : GearDamage.inRadius(player, level, feet, r, 1.5, 3.0)) {
            if (GearDamage.blast(target, source, AccessoryRules.NOVA_DAMAGE, feet, AccessoryRules.NOVA_IMPACT, 1.2)) {
                struck++;
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, AccessoryRules.NOVA_RESISTANCE_TICKS,
                AccessoryRules.NOVA_RESISTANCE_AMPLIFIER, false, true, true));
        Vec3 middle = feet.add(0, player.getBbHeight() * 0.5, 0);
        level.playSound(null, middle.x, middle.y, middle.z, AccessoryRegistry.NOVA.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(player, new AccessoryFxPayload(AccessoryFxPayload.NOVA, player.getId(), middle, (float) r, struck));
        return struck;
    }
}
