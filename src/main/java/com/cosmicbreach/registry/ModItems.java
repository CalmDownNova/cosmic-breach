package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.item.CombatWeaponItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);

    /** The Starblade. Its weapon data is {@code data/cosmicbreach/combat/weapons/meridian.json}. */
    public static final DeferredItem<CombatWeaponItem> MERIDIAN =
            ITEMS.registerItem("meridian", CombatWeaponItem::new, new Item.Properties());

    /** The greathammer. Its weapon data is {@code data/cosmicbreach/combat/weapons/comet_maul.json}. */
    public static final DeferredItem<CombatWeaponItem> COMET_MAUL =
            ITEMS.registerItem("comet_maul", CombatWeaponItem::new, new Item.Properties());

    /** The twin sickles. Their weapon data is {@code data/cosmicbreach/combat/weapons/binary_edges.json}. */
    public static final DeferredItem<CombatWeaponItem> BINARY_EDGES =
            ITEMS.registerItem("binary_edges", CombatWeaponItem::new, new Item.Properties());

    /** The catalyst (G7). Its weapon data is {@code data/cosmicbreach/combat/weapons/choir_astrolabe.json}. */
    public static final DeferredItem<CombatWeaponItem> CHOIR_ASTROLABE = ITEMS.registerItem("choir_astrolabe", CombatWeaponItem::new, new Item.Properties());

    /** The Binary Edges' left sickle as the off hand draws it (and the thrown blade): never in an inventory. */
    public static final DeferredItem<Item> BINARY_EDGES_LEFT =
            ITEMS.registerItem("binary_edges_left", Item::new, new Item.Properties().stacksTo(1));

    /** A crystal shard a Shardling drops (1 to 2). A crafting material later; for now only loot. */
    public static final DeferredItem<Item> STARSHARD = ITEMS.registerSimpleItem("starshard");

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
