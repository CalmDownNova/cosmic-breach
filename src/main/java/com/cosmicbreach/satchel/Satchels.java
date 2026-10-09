package com.cosmicbreach.satchel;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModCreativeTab;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Satchel (1.2 design section 3): item, contents component, menu, pickup and packets. */
public final class Satchels {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CosmicBreach.MOD_ID);

    public static final DeferredItem<SatchelItem> SATCHEL = ITEMS.registerItem("satchel", SatchelItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SatchelContents>> CONTENTS = COMPONENTS.registerComponentType("satchel",
            b -> b.persistent(SatchelContents.CODEC).networkSynchronized(SatchelContents.STREAM_CODEC));
    public static final Supplier<MenuType<SatchelMenu>> MENU = MENUS.register("satchel", () -> IMenuTypeExtension.create(SatchelMenu::fromNetwork));

    private Satchels() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, SatchelNet::register);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(SATCHEL.get());
            }
        });
        game.addListener(ItemEntityPickupEvent.Pre.class, SatchelPickup::onPickup);
    }

    /** What a satchel stack holds (empty if it has nothing yet). */
    public static SatchelContents contents(ItemStack stack) {
        return stack.getOrDefault(CONTENTS.get(), SatchelContents.EMPTY);
    }

    public static void setContents(ItemStack stack, SatchelContents contents) {
        stack.set(CONTENTS.get(), contents);
    }
}
