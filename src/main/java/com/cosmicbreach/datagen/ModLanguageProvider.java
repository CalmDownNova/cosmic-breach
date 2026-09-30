package com.cosmicbreach.datagen;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * English names for every W1 block and material: the registry id in title case ("starfall_stone_brick_slab"
 * is "Starfall Stone Brick Slab"), plain names with no lore. A new block or item gets its name from the
 * next data run.
 *
 * <p>The file goes to {@code assets/cosmicbreach_names/lang/en_us.json}, not the mod's own namespace:
 * the hand-written {@code assets/cosmicbreach/lang/en_us.json} (commands, keys, subtitles) already owns
 * that path, and Minecraft merges the lang files of every namespace ({@code ClientLanguage.loadFrom}).
 * Never put the same key in both files.
 */
public final class ModLanguageProvider extends LanguageProvider {
    public static final String NAMESPACE = "cosmicbreach_names";

    public ModLanguageProvider(PackOutput output) {
        super(output, NAMESPACE, "en_us");
    }

    @Override
    protected void addTranslations() {
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            Block block = holder.get();
            add(block, title(BuiltInRegistries.BLOCK.getKey(block).getPath()));
        }
        for (var holder : ModBlocks.ITEMS.getEntries()) {
            // block items share their block's name; only items named after themselves need one
            if (!(holder.get() instanceof BlockItem) || holder.get() instanceof ItemNameBlockItem) {
                add(holder.get(), title(holder.getId().getPath()));
            }
        }
        for (var holder : ModMaterials.ITEMS.getEntries()) {
            add(holder.get(), title(holder.getId().getPath()));
        }
        add("itemGroup.cosmicbreach.blocks", "Cosmic Breach Blocks");
    }

    static String title(String id) {
        return Arrays.stream(id.split("_"))
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
