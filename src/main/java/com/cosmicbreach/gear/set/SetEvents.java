package com.cosmicbreach.gear.set;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.gear.net.SetAbilityPayload;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Runs every worn set's {@link SetBehavior} from the game's events, and casts set abilities. Server side.
 *
 * <ul>
 *   <li>Landing: {@code LivingFallEvent} at low priority, even when something cancelled it (a plunge landing
 *       cancels the fall damage; the Vanguard still banks the fall).</li>
 *   <li>Damage taken: {@code LivingIncomingDamageEvent} (before armor).</li>
 *   <li>Damage dealt: {@code LivingIncomingDamageEvent} at the lowest priority, only if nothing cancelled the
 *       hit (a parry or a dodge), so a bonus is never spent on a hit that didn't land.</li>
 *   <li>Poise and combat events: through the engine's {@link CombatHooks}.</li>
 * </ul>
 */
public final class SetEvents {
    private SetEvents() {
    }

    public static void register(IEventBus game) {
        game.addListener(PlayerTickEvent.Post.class, SetEvents::onPlayerTick);
        game.addListener(EventPriority.LOW, true, LivingFallEvent.class, SetEvents::onFall);
        game.addListener(LivingIncomingDamageEvent.class, SetEvents::onHurt);
        game.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, SetEvents::onHitDealt);
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public double poiseBonus(Player player) {
                return ArmorSets.poiseBonus(player);
            }

            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                ArmorSets.worn(player).forEach((set, pieces) -> set.behavior().onCombatEvent(player, pieces, combat, event));
            }
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long now = player.level().getGameTime();
            ArmorSets.worn(player).forEach((set, pieces) -> set.behavior().tick(player, pieces, now));
            SetState state = ArmorSets.state(player);
            if (state.meter() > 0f && state.meter(now) <= 0f) {
                ArmorSets.setState(player, state.withoutMeter()); // ran out: tell the clients (the glow fades)
            }
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Map.Entry<ArmorSet, Integer> e : ArmorSets.worn(player).entrySet()) {
                e.getKey().behavior().onLanding(player, e.getValue(), event.getDistance(), event.getDamageMultiplier(),
                        event.isCanceled());
            }
        }
    }

    private static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            float amount = event.getAmount();
            for (Map.Entry<ArmorSet, Integer> e : ArmorSets.worn(player).entrySet()) {
                amount = e.getKey().behavior().onHurt(player, e.getValue(), event.getSource(), amount);
            }
            event.setAmount(amount);
        }
    }

    private static void onHitDealt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != target) {
            float amount = event.getAmount();
            for (Map.Entry<ArmorSet, Integer> e : ArmorSets.worn(player).entrySet()) {
                amount = e.getKey().behavior().onHitDealt(player, e.getValue(), target, event.getSource(), amount);
            }
            event.setAmount(amount);
        }
    }

    /** The Set Ability key (main thread): cast the worn set's ability if it is ready. */
    public static void onAbility(SetAbilityPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            tryCast(player);
        }
    }

    /** Casts the player's set ability if a set allows it and its cooldown is over. True if it went off. */
    public static boolean tryCast(ServerPlayer player) {
        ArmorSet set = ArmorSets.withAbility(player);
        if (set == null) {
            return false;
        }
        SetAbility ability = set.ability().orElseThrow();
        long now = player.level().getGameTime();
        if (!ArmorSets.state(player).ready(now)) {
            return false;
        }
        // the cooldown as it stands when the ability is cast: one that buffs Haste (the Hymn) never shortens its own
        int cooldown = ArmorSets.cooldownTicks(player, ability);
        if (!ability.cast(player, now)) {
            return false;
        }
        ArmorSets.setState(player, ArmorSets.state(player).withCooldown(now, cooldown));
        return true;
    }
}
