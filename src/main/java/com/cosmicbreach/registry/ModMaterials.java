package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Inert materials and drops (GDD 3.5 and the bestiary): the three metals of the tiers, the gem, and
 * what the guardians and elites leave behind. They do nothing on their own; recipes and loot tables
 * give them their use. Textures from {@code tools/art/gen_materials.py}.
 */
public final class ModMaterials {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);

    // The Reach (T1). Starsteel Ore and Meteorites drop the raw metal.
    public static final DeferredItem<Item> RAW_STARSTEEL = simple("raw_starsteel", Rarity.COMMON);
    public static final DeferredItem<Item> STARSTEEL_INGOT = simple("starsteel_ingot", Rarity.COMMON);
    public static final DeferredItem<Item> STARSTEEL_NUGGET = simple("starsteel_nugget", Rarity.COMMON);
    /** A Meteorite's 5% gem. */
    public static final DeferredItem<Item> HEARTSTONE = simple("heartstone", Rarity.UNCOMMON);

    // The Drift (T2).
    public static final DeferredItem<Item> RAW_NEBULITE = simple("raw_nebulite", Rarity.COMMON);
    public static final DeferredItem<Item> NEBULITE_INGOT = simple("nebulite_ingot", Rarity.COMMON);
    /** Gyre Knight: always. */
    public static final DeferredItem<Item> GYRE_CORE = simple("gyre_core", Rarity.UNCOMMON);
    /** Gyre Knight: 50%. */
    public static final DeferredItem<Item> GYRE_BLADE = simple("gyre_blade", Rarity.UNCOMMON);

    // The Deep (T3).
    public static final DeferredItem<Item> RAW_ECLIPSIUM = simple("raw_eclipsium", Rarity.COMMON);
    public static final DeferredItem<Item> ECLIPSIUM_INGOT = simple("eclipsium_ingot", Rarity.COMMON);
    /** Hollow Stalker: 20%. */
    public static final DeferredItem<Item> ECLIPSIUM_NUGGET = simple("eclipsium_nugget", Rarity.COMMON);
    /** Hollow Stalker: 1 to 2. */
    public static final DeferredItem<Item> UMBRAL_SILK = simple("umbral_silk", Rarity.COMMON);

    // Guardians and the Sanctum.
    /** Prism Colossus, first kill: unlocks Astral Forge II. */
    public static final DeferredItem<Item> PRISM_HEART = simple("prism_heart", Rarity.RARE);
    /** Thalassine Leviathan: 3 per first kill. */
    public static final DeferredItem<Item> LEVIATHAN_SCALE = simple("leviathan_scale", Rarity.UNCOMMON);
    /** Thalassine Leviathan, first kill: unlocks Astral Forge III. */
    public static final DeferredItem<Item> LEVIATHAN_PEARL = simple("leviathan_pearl", Rarity.RARE);
    /** The Unsung, first kill: for the Dying Star Heart. */
    public static final DeferredItem<Item> SILENT_SIGIL = simple("silent_sigil", Rarity.RARE);
    /** The Sanctum's west wing vault: for the Dying Star Heart. */
    public static final DeferredItem<Item> SOLAR_EMBER = simple("solar_ember", Rarity.RARE);
    /** The Sanctum's east wing vault: for the Dying Star Heart. */
    public static final DeferredItem<Item> HYMN_CRYSTAL = simple("hymn_crystal", Rarity.RARE);
    /** The Hollow Heliarch: unlocks Astral Forge IV. */
    public static final DeferredItem<Item> SOLAR_HEART = simple("solar_heart", Rarity.EPIC);

    private ModMaterials() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** Adds every material to a creative tab, in the order above. */
    public static void addTo(CreativeModeTab.Output output) {
        ITEMS.getEntries().forEach(item -> output.accept(item.get()));
    }

    private static DeferredItem<Item> simple(String name, Rarity rarity) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().rarity(rarity));
    }
}
