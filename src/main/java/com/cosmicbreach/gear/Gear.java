package com.cosmicbreach.gear;

import com.cosmicbreach.gear.net.GearFxPayload;
import com.cosmicbreach.gear.net.SetAbilityPayload;
import com.cosmicbreach.gear.set.SetArmorItem;
import com.cosmicbreach.gear.set.SetEvents;
import com.cosmicbreach.gear.vanguard.MeteorCall;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.net.ModNetworking;
import java.util.List;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Gear (GDD 3.5 and 5.1): the Astral Forge, reforging, the armor set framework and the Starfall Vanguard.
 * Common entry point: registrations, payloads and server hooks. Client side: {@code client.gear.GearClient}.
 */
public final class Gear {
    private Gear() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        GearTier.register(modBus);
        GearRegistry.register(modBus);
        GearSets.register(modBus, game);
        modBus.addListener(RegisterPayloadHandlersEvent.class, Gear::registerPayloads);
        SetEvents.register(game);
        game.addListener(ItemAttributeModifierEvent.class, Gear::onItemAttributes);
        game.addListener(ServerTickEvent.Post.class, MeteorCall::onServerTick);
        game.addListener(ServerStoppingEvent.class, MeteorCall::onServerStopping);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToServer(SetAbilityPayload.TYPE, SetAbilityPayload.STREAM_CODEC, SetEvents::onAbility);
        registrar.playToClient(GearFxPayload.TYPE, GearFxPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.gear.GearClientHandlers.fx(payload, context));
    }

    /**
     * A reforged set piece: its armor and toughness x1.15 per tier above the set's (GDD 3.5). The rest of its
     * modifiers (knockback resistance, the stat bonuses) stay as they are.
     */
    private static void onItemAttributes(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof SetArmorItem piece)) {
            return;
        }
        int steps = GearTier.stepsAbove(event.getItemStack(), piece.unlockTier());
        if (steps <= 0) {
            return;
        }
        double scale = GearTier.multiplier(steps);
        List<ItemAttributeModifiers.Entry> entries = List.copyOf(event.getModifiers());
        for (ItemAttributeModifiers.Entry entry : entries) {
            if (entry.attribute().is(Attributes.ARMOR) || entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                AttributeModifier m = entry.modifier();
                event.replaceModifier(entry.attribute(), new AttributeModifier(m.id(), m.amount() * scale, m.operation()), entry.slot());
            }
        }
    }
}
