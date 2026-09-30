package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * W6's generated files: block states and item models for the crypt's blocks (their models are hand-made, written by
 * {@code tools/art/gen_crypt.py}), and the tags keeping its unbreakable blocks from withers and dragons. None of them
 * drops anything, so there is no loot.
 */
public final class CryptData {
    private CryptData() {
    }

    public static void gather(net.neoforged.neoforge.data.event.GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(event.includeClient(), new Models(output, event.getExistingFileHelper()));
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
            simpleBlock(CryptRegistry.CONDUCTOR.get(), existing("conductor"));
            simpleBlock(CryptRegistry.CONDUCTOR_TOP.get(), existing("conductor_top"));
            simpleBlock(CryptRegistry.RESONANCE_FLOOR.get(), existing("resonance_floor"));
            getVariantBuilder(CryptRegistry.CRYPT_VAULT.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing(s.getValue(VaultBlock.READY) ? "crypt_vault_ready" : "crypt_vault"))
                    .rotationY(yOf(s.getValue(VaultBlock.FACING))).build());
            getVariantBuilder(CryptRegistry.VOID_RIFT_TILE.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("void_rift_tile")).build());
            simpleBlock(CryptRegistry.VOID_POCKET.get(), existing("void_pocket"));
            simpleBlock(CryptRegistry.POCKET_SEAL.get(), existing("pocket_seal"));
            getVariantBuilder(CryptRegistry.GRAVITY_SIGIL.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("gravity_sigil")).build());
            simpleBlock(CryptRegistry.GRAVITY_PISTON_BLOCK.get(), existing("gravity_piston"));
            getVariantBuilder(CryptRegistry.STARFALL_CHUTE.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("starfall_chute"))
                    .rotationY(s.getValue(StarfallChuteBlock.AXIS) == Direction.Axis.X ? 90 : 0).build());
            simpleBlock(CryptRegistry.STALKER_MARKER.get(), existing("stalker_marker"));
            getVariantBuilder(CryptRegistry.UMBRAL_EMITTER.get()).forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(existing("umbral_emitter")).rotationY(yOf(s.getValue(com.cosmicbreach.structure.trap.KineticEmitterBlock.FACING)))
                    .build());

            item(CryptRegistry.CONDUCTOR.get(), "conductor");
            item(CryptRegistry.RESONANCE_FLOOR.get(), "resonance_floor");
            item(CryptRegistry.CRYPT_VAULT.get(), "crypt_vault");
            item(CryptRegistry.VOID_RIFT_TILE.get(), "void_rift_tile");
            item(CryptRegistry.VOID_POCKET.get(), "void_pocket");
            item(CryptRegistry.POCKET_SEAL.get(), "pocket_seal");
            item(CryptRegistry.GRAVITY_SIGIL.get(), "gravity_sigil");
            item(CryptRegistry.GRAVITY_PISTON_BLOCK.get(), "gravity_piston");
            item(CryptRegistry.STARFALL_CHUTE.get(), "starfall_chute");
            item(CryptRegistry.UMBRAL_EMITTER.get(), "umbral_emitter");
        }

        private void item(Block block, String model) {
            itemModels().withExistingParent(BuiltInRegistries.BLOCK.getKey(block).getPath(), modLoc("block/" + model));
        }

        @Override
        public String getName() {
            return "Block States: " + CosmicBreach.MOD_ID + " crypt";
        }
    }

    /** The crypt's blocks in the wither- and dragon-proof tags, for ModBlockTagsProvider (which owns those files). */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<Block>, Block[]> tag) {
        Block[] sealed = {CryptRegistry.CONDUCTOR.get(), CryptRegistry.CONDUCTOR_TOP.get(), CryptRegistry.RESONANCE_FLOOR.get(),
                CryptRegistry.CRYPT_VAULT.get(), CryptRegistry.VOID_RIFT_TILE.get(), CryptRegistry.VOID_POCKET.get(),
                CryptRegistry.POCKET_SEAL.get(), CryptRegistry.GRAVITY_SIGIL.get(), CryptRegistry.GRAVITY_PISTON_BLOCK.get(),
                CryptRegistry.STARFALL_CHUTE.get(), CryptRegistry.STALKER_MARKER.get()};
        tag.accept(BlockTags.WITHER_IMMUNE, sealed);
        tag.accept(BlockTags.DRAGON_IMMUNE, sealed);
        tag.accept(BlockTags.MINEABLE_WITH_PICKAXE, new Block[] {CryptRegistry.UMBRAL_EMITTER.get()});
        tag.accept(BlockTags.NEEDS_IRON_TOOL, new Block[] {CryptRegistry.UMBRAL_EMITTER.get()});
    }

    /** The Umbral emitter drops itself (disarming a tripwire is a fair answer to one); nothing else here drops anything. */
    public static final class Loot extends net.minecraft.data.loot.BlockLootSubProvider {
        public Loot(net.minecraft.core.HolderLookup.Provider registries) {
            super(java.util.Set.of(), net.minecraft.world.flag.FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return java.util.List.of(CryptRegistry.UMBRAL_EMITTER.get());
        }

        @Override
        protected void generate() {
            dropSelf(CryptRegistry.UMBRAL_EMITTER.get());
        }
    }
}
