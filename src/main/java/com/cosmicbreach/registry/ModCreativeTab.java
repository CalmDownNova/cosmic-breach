package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CosmicBreach.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COSMIC_BREACH = TABS.register("cosmic_breach",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cosmicbreach"))
                    .icon(() -> new ItemStack(ModItems.MERIDIAN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MERIDIAN.get());
                        output.accept(ModItems.COMET_MAUL.get());
                        output.accept(ModItems.BINARY_EDGES.get());
                        output.accept(ModItems.CHOIR_ASTROLABE.get());
                        output.accept(ModItems.STARSHARD.get());
                        ModMaterials.addTo(output);
                    })
                    .build());

    private ModCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
