package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.array.ApertureBlock;
import com.cosmicbreach.structure.array.FilterBlock;
import com.cosmicbreach.structure.array.FocusBlock;
import com.cosmicbreach.structure.array.MirrorBlock;
import com.cosmicbreach.structure.array.ReceptorBlock;
import com.cosmicbreach.structure.array.SplitterBlock;
import com.cosmicbreach.structure.array.WardenEyeBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/**
 * W5's generated files: block states for the structures' blocks (their models are hand-made, written by
 * {@code tools/art/gen_structures.py}), item models, the light blocks' and emitter's drops, and the tags keeping the
 * unbreakable puzzle blocks from withers and dragons.
 */
public final class StructureData {
    private StructureData() {
    }

    public static void gather(net.neoforged.neoforge.data.event.GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        event.getGenerator().addProvider(event.includeClient(), new Models(output, files));
        // Block tags go through ModBlockTagsProvider (blockTags below): two providers writing one tag file overwrite each other.
    }

    /** Block states and item models. */
    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper files) {
            super(output, CosmicBreach.MOD_ID, files);
        }

        private ModelFile existing(String name) {
            return models().getExistingFile(modLoc("block/" + name));
        }

        private static int yOf(Direction d) {
            return switch (d) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
        }

        @Override
        protected void registerStatesAndModels() {
            simpleBlock(StructureRegistry.LENS_PEDESTAL.get(), existing("lens_pedestal"));
            getVariantBuilder(StructureRegistry.LENS_MIRROR.get()).forAllStates(s -> {
                int turn = s.getValue(MirrorBlock.TURN);
                String loose = s.getValue(MirrorBlock.LOOSE) ? "_loose" : "";
                String shape = turn % 2 == 0 ? "lens_mirror_straight" : "lens_mirror_diagonal";
                return ConfiguredModel.builder().modelFile(existing(shape + loose)).rotationY(turn >= 2 ? 90 : 0).build();
            });
            // the splitter model's point faces north: the light it takes comes from there, heading south
            getVariantBuilder(StructureRegistry.LENS_SPLITTER.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("lens_splitter")).rotationY((yOf(s.getValue(SplitterBlock.FACING)) + 180) % 360).build());
            getVariantBuilder(StructureRegistry.LENS_FILTER.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("lens_filter_" + s.getValue(FilterBlock.COLOR).getSerializedName()
                            + (s.getValue(FilterBlock.LOOSE) ? "_loose" : ""))).build());
            simpleBlock(StructureRegistry.LENS_UMBRAL.get(), existing("lens_umbral"));
            getVariantBuilder(StructureRegistry.LENS_FOCUS.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("lens_focus")).rotationY(yOf(s.getValue(FocusBlock.FACING))).build());
            getVariantBuilder(StructureRegistry.LENS_RECEPTOR.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("lens_receptor_" + s.getValue(ReceptorBlock.COLOR).getSerializedName()
                            + (s.getValue(ReceptorBlock.LIT) ? "_lit" : ""))).build());
            getVariantBuilder(StructureRegistry.WARDEN_EYE.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing(s.getValue(WardenEyeBlock.AWAKE) ? "warden_eye_awake" : "warden_eye"))
                    .rotationY(yOf(s.getValue(WardenEyeBlock.FACING))).build());
            simpleBlock(StructureRegistry.LENS_SOCKET.get(), existing("lens_socket"));
            getVariantBuilder(StructureRegistry.SUN_APERTURE.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing(s.getValue(ApertureBlock.OPEN) ? "sun_aperture_open" : "sun_aperture")).build());
            simpleBlock(StructureRegistry.LENS_CORE.get(), existing("lens_core"));
            vault(StructureRegistry.RELIQUARY_VAULT.get(), "reliquary_vault");
            vault(StructureRegistry.OBSERVATORY_VAULT.get(), "observatory_vault");
            simpleBlock(StructureRegistry.SUNSTONE.get());
            simpleBlock(StructureRegistry.NEBULITE_LAMP.get());
            getVariantBuilder(StructureRegistry.KINETIC_EMITTER.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("kinetic_emitter")).rotationY(yOf(s.getValue(KineticEmitterBlock.FACING))).build());
            getVariantBuilder(StructureRegistry.KINETIC_THREAD.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("kinetic_thread")).build());
            getVariantBuilder(StructureRegistry.UPDRAFT.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("updraft")).build());
            simpleBlock(StructureRegistry.GUARD_MARKER.get(), existing("guard_marker"));

            item(StructureRegistry.LENS_PEDESTAL.get(), "lens_pedestal");
            item(StructureRegistry.LENS_MIRROR.get(), "lens_mirror_diagonal");
            item(StructureRegistry.LENS_SPLITTER.get(), "lens_splitter");
            item(StructureRegistry.LENS_FILTER.get(), "lens_filter_gold");
            item(StructureRegistry.LENS_UMBRAL.get(), "lens_umbral");
            item(StructureRegistry.LENS_FOCUS.get(), "lens_focus");
            item(StructureRegistry.LENS_RECEPTOR.get(), "lens_receptor_teal_lit");
            item(StructureRegistry.WARDEN_EYE.get(), "warden_eye");
            item(StructureRegistry.LENS_SOCKET.get(), "lens_socket");
            item(StructureRegistry.SUN_APERTURE.get(), "sun_aperture_open");
            item(StructureRegistry.RELIQUARY_VAULT.get(), "reliquary_vault");
            item(StructureRegistry.OBSERVATORY_VAULT.get(), "observatory_vault");
            item(StructureRegistry.SUNSTONE.get(), "sunstone");
            item(StructureRegistry.NEBULITE_LAMP.get(), "nebulite_lamp");
            item(StructureRegistry.KINETIC_EMITTER.get(), "kinetic_emitter");
            itemModels().basicItem(StructureRegistry.LOOSE_MIRROR.get());
            itemModels().basicItem(StructureRegistry.LOOSE_GOLD_FILTER.get());
            itemModels().basicItem(StructureRegistry.LOOSE_TEAL_FILTER.get());
            itemModels().basicItem(StructureRegistry.LOOSE_MAGENTA_FILTER.get());
        }

        private void vault(Block block, String name) {
            getVariantBuilder(block).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing(s.getValue(VaultBlock.READY) ? name + "_ready" : name))
                    .rotationY(yOf(s.getValue(VaultBlock.FACING))).build());
        }

        private void item(Block block, String model) {
            itemModels().withExistingParent(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath(),
                    modLoc("block/" + model));
        }

        @Override
        public String getName() {
            return "Block States: " + CosmicBreach.MOD_ID + " structures";
        }
    }

    /** The light blocks and the emitter drop themselves; the puzzle blocks drop nothing (no loot table). */
    public static final class Loot extends BlockLootSubProvider {
        public Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(StructureRegistry.SUNSTONE.get(), StructureRegistry.NEBULITE_LAMP.get(), StructureRegistry.KINETIC_EMITTER.get());
        }

        @Override
        protected void generate() {
            dropSelf(StructureRegistry.SUNSTONE.get());
            dropSelf(StructureRegistry.NEBULITE_LAMP.get());
            dropSelf(StructureRegistry.KINETIC_EMITTER.get());
        }
    }

    /** The structures' blocks in the wither- and dragon-proof tags, for ModBlockTagsProvider (which owns those files). */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<Block>, Block[]> tag) {
        Block[] puzzle = {StructureRegistry.LENS_PEDESTAL.get(), StructureRegistry.LENS_MIRROR.get(), StructureRegistry.LENS_SPLITTER.get(),
                StructureRegistry.LENS_FILTER.get(), StructureRegistry.LENS_UMBRAL.get(), StructureRegistry.LENS_FOCUS.get(),
                StructureRegistry.LENS_RECEPTOR.get(), StructureRegistry.WARDEN_EYE.get(), StructureRegistry.LENS_SOCKET.get(),
                StructureRegistry.SUN_APERTURE.get(), StructureRegistry.LENS_CORE.get(), StructureRegistry.RELIQUARY_VAULT.get(),
                StructureRegistry.OBSERVATORY_VAULT.get(), StructureRegistry.KINETIC_THREAD.get(), StructureRegistry.UPDRAFT.get(),
                StructureRegistry.GUARD_MARKER.get()};
        tag.accept(BlockTags.WITHER_IMMUNE, puzzle);
        tag.accept(BlockTags.DRAGON_IMMUNE, puzzle);
    }
}
