package com.cosmicbreach.progression;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModCreativeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Everything the progression system registers: its attachments, the Reverie Draught and the level-up chime. */
public final class ProgressionRegistry {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    /**
     * A player's {@link Attunement}: saved with the player, kept through death, and synced to that
     * player's own client only (nobody else needs it).
     */
    public static final Supplier<AttachmentType<Attunement>> ATTUNEMENT = ATTACHMENTS.register("attunement",
            () -> AttachmentType.builder(() -> Attunement.START)
                    .serialize(Attunement.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, Attunement.STREAM_CODEC)
                    .build());

    /** On a creature players have damaged (server, not saved): who shares its kill. */
    public static final Supplier<AttachmentType<KillCredit>> KILL_CREDIT = ATTACHMENTS.register("kill_credit",
            () -> AttachmentType.builder(() -> new KillCredit()).build());

    /** Drink it to take back every spent stat point. Its Astral Forge recipe comes with the Forge. */
    public static final DeferredItem<ReverieDraughtItem> REVERIE_DRAUGHT = ITEMS.registerItem("reverie_draught",
            ReverieDraughtItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    /** A bright rising chime when an Attunement level is gained. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = SOUNDS.register("progression/level_up",
            () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id("progression/level_up")));

    private ProgressionRegistry() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, ProgressionRegistry::fillCreativeTab);
    }

    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            event.accept(REVERIE_DRAUGHT.get());
        }
    }
}
