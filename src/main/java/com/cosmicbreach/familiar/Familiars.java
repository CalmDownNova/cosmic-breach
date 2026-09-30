package com.cosmicbreach.familiar;

import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.registry.ModCreativeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The combat familiars (G10, GDD 8.2). Star Eggs come from vaults (Reliquary 20%, Observatory 25%, Crypt 30%: the
 * vault loot tables, before their accessory pools); a Brazier of Solenne (Forge I) hatches one after 10 minutes of loaded time, a Solar
 * Flare at once; the hatchling is its Familiar Lantern. Summon and dismiss by using the lantern or holding the familiar
 * key (H; G is Curios's inventory), tap it to
 * cycle Attack, Guard and Passive ({@link FamiliarSessions}). The three familiars ({@link Emberwisp}, {@link Gravikin},
 * {@link PrismMoth}) and the statuses they prime: Scorch (the gear's), {@link Refract}, and {@link Kindled} for the
 * owner. Debug: {@code /cosmicbreach familiar ...} ({@link FamiliarCommands}).
 */
public final class Familiars {
    private Familiars() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        FamiliarRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, FamiliarNet::register);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(FamiliarRegistry.EMBERWISP.get(), FamiliarEntity.createAttributes().build());
            event.put(FamiliarRegistry.GRAVIKIN.get(), FamiliarEntity.createAttributes().build());
            event.put(FamiliarRegistry.PRISM_MOTH.get(), FamiliarEntity.createAttributes().build());
        });
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Familiars::fillCreativeTab);

        FamiliarCombat combat = new FamiliarCombat();
        CombatHooks.register(combat);
        HitModifiers.register(combat);
        HitResolver.onStrike(FamiliarCombat::onStrike);

        game.addListener(PlayerTickEvent.Post.class, FamiliarSessions::onPlayerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, FamiliarSessions::onLoggedOut);
        game.addListener(LivingDeathEvent.class, FamiliarSessions::onDeath);
        game.addListener(LivingDamageEvent.Post.class, FamiliarSessions::onDamaged);
        game.addListener(ServerStoppingEvent.class, event -> {
            FamiliarSessions.onServerStopping(event);
            Taunts.clear();
            BrazierBlockEntity.setHatchTicks(-1);
        });
        game.addListener(LivingChangeTargetEvent.class, Taunts::onChangeTarget);
        game.addListener(ServerTickEvent.Post.class, Taunts::onServerTick);
        game.addListener(RegisterCommandsEvent.class, event -> FamiliarCommands.register(event.getDispatcher()));
    }

    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            event.accept(FamiliarRegistry.BRAZIER_ITEM.get());
            event.accept(new ItemStack(FamiliarRegistry.STAR_EGG.get()));
            for (FamiliarKind kind : FamiliarKind.values()) {
                event.accept(StarEggItem.of(kind));
            }
        }
    }
}
