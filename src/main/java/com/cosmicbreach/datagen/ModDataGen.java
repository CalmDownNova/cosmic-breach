package com.cosmicbreach.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The data run ({@code ./gradlew runData}): writes block states, block and item models, block loot,
 * tags, recipes and the generated names into {@code src/generated/resources/}, which is committed and
 * part of the jar. Textures must exist first (the model checks look them up in
 * {@code src/main/resources}), so run {@code python tools/art/build_all.py} before the data run.
 */
public final class ModDataGen {
    private ModDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ModModelProvider(output, files));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output));

        ModBlockTagsProvider blockTags = generator.addProvider(event.includeServer(), new ModBlockTagsProvider(output, lookup, files));
        generator.addProvider(event.includeServer(), new ModItemTagsProvider(output, lookup, blockTags.contentsGetter(), files));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK),
                        new LootTableProvider.SubProviderEntry(OnboardingData.Loot::new, LootContextParamSets.BLOCK),
                        new LootTableProvider.SubProviderEntry(StructureData.Loot::new, LootContextParamSets.BLOCK),
                        new LootTableProvider.SubProviderEntry(CryptData.Loot::new, LootContextParamSets.BLOCK)), lookup));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, lookup));
        com.cosmicbreach.gear.datagen.GearDataGen.gather(event);
        com.cosmicbreach.provision.ProvisionData.gather(event);

        // the way in (W4)
        generator.addProvider(event.includeClient(), new OnboardingData.Models(output, files));
        generator.addProvider(event.includeServer(), new OnboardingData.BlockTagsData(output, lookup, files));

        // the first dungeons (W5)
        StructureData.gather(event);

        // the Hollow Crypt (W6)
        CryptData.gather(event);
    }
}
