package com.cosmicbreach.client.gear;

import com.cosmicbreach.client.fx.CombatEffects;
import com.cosmicbreach.client.fx.GhostBodies;
import com.cosmicbreach.client.fx.SetVisuals;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.driftweave.Driftweave;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.gear.regalia.ChoirRegalia;
import com.cosmicbreach.gear.regalia.HymnRings;
import com.cosmicbreach.world.VesperClock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Driftweave and the Choir Regalia on the client: their bone drivers ({@link DriftweaveLook}, {@link RegaliaLook})
 * and glows (the Driftweave's star dust adds light and brightens with the dash charges ready, shown one band per charge on
 * its scarf; the Regalia's engraved orbits, halo and rings add gold light, brighter with an echo primed, pulsing on
 * Vesper's beat), the tier trim on their icons,
 * the fins' dash flare, the ghosts, the Hymn's rings and the effects the server sends.
 */
public final class SetsClient {
    private SetsClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        SetArmorModel.driver(Driftweave.SET.id(), new DriftweaveLook());
        SetArmorRenderer.glow(Driftweave.SET.id(), new SetArmorRenderer.Glow(0x5A50D8, 0xC8F6FF, 3f, 0.3f, true));
        SetArmorRenderer.pulse(Driftweave.SET.id(), (wearer, partialTick) ->
                0.9f + 0.1f * (float) Math.sin((wearer.tickCount + partialTick) * 0.23));

        SetArmorModel.driver(ChoirRegalia.SET.id(), new RegaliaLook());
        SetArmorRenderer.glow(ChoirRegalia.SET.id(), new SetArmorRenderer.Glow(0xE8B04C, 0xFFF3CF, 2f, 0.55f, true));
        SetArmorRenderer.pulse(ChoirRegalia.SET.id(), (wearer, partialTick) ->
                0.7f + 0.6f * VesperClock.pulse(wearer.level().getGameTime(), partialTick));

        CombatEffects.addDashListener(DriftweaveLook::dashed);
        GhostBodies.register(gameBus);
        modBus.addListener(RegisterColorHandlersEvent.Item.class, SetsClient::registerItemColors);
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                HymnRings.clientTick(mc.level);
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> SetVisuals.clear());
    }

    /** A set effect from the server (main thread). */
    public static void handle(SetFxPayload payload, IPayloadContext context) {
        SetVisuals.handle(payload);
    }

    /** Layer 1 of each piece's icon is its trim: the tier's colour above the set's tier. */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        List<Item> items = new ArrayList<>();
        for (GearRegistry.SetItems set : GearSets.sets()) {
            set.all().forEach(piece -> items.add(piece.get()));
        }
        event.register((stack, tintIndex) -> tintIndex == 1 ? GearClient.trimColor(stack) : -1, items.toArray(Item[]::new));
    }
}
