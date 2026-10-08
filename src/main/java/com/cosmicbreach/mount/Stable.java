package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModCreativeTab;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Stable Crystal (1.1 design section 4): the item and the data component holding its mount. Forge I recipe in data. */
public final class Stable {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CosmicBreach.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StowedMount>> STOWED =
            COMPONENTS.registerComponentType("stowed_mount", b -> b.persistent(StowedMount.CODEC).networkSynchronized(StowedMount.STREAM_CODEC));
    public static final DeferredItem<StableCrystalItem> STABLE_CRYSTAL = ITEMS.registerItem("stable_crystal", StableCrystalItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());

    private Stable() {
    }

    /** The game bus: a full crystal's item entity protected the moment it joins a level, and handled when it is killed (CrystalGuard). */
    static void registerGame(IEventBus game) {
        game.addListener(EntityJoinLevelEvent.class, CrystalGuard::onJoin);
        game.addListener(EntityLeaveLevelEvent.class, CrystalGuard::onLeave);
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(STABLE_CRYSTAL.get());
            }
        });
    }
}
