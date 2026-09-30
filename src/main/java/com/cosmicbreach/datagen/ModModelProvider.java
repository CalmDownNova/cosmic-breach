package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.block.StarbloomCropBlock;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModBlocks.StoneSet;
import com.cosmicbreach.registry.ModMaterials;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Block states, block models and item models for every W1 block and material. Textures are
 * {@code cosmicbreach:block/<name>} unless noted; cutout and translucent blocks name their render type
 * in the model, so no client registration is needed.
 */
public final class ModModelProvider extends BlockStateProvider {
    public ModModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, CosmicBreach.MOD_ID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        // cubes that show one texture on every face
        for (Block b : new Block[] {
                ModBlocks.STARFALL_STONE.get(), ModBlocks.POLISHED_STARFALL_STONE.get(), ModBlocks.STARFALL_STONE_BRICKS.get(),
                ModBlocks.SPIRE_QUARTZ.get(), ModBlocks.STARSTEEL_ORE.get(), ModBlocks.STARSTEEL_BLOCK.get(),
                ModBlocks.METEORITE.get(), ModBlocks.DRIFTSTONE.get(), ModBlocks.DRIFTSTONE_BRICKS.get(),
                ModBlocks.NEBULITE_ORE.get(), ModBlocks.NEBULITE_BLOCK.get(), ModBlocks.DRIFTWOOD_PLANKS.get(),
                ModBlocks.POLISHED_UMBRAL_BASALT.get(), ModBlocks.UMBRAL_BASALT_BRICKS.get(), ModBlocks.ECLIPSIUM_ORE.get(),
                ModBlocks.ECLIPSIUM_BLOCK.get()}) {
            simpleBlockWithItem(b, cubeAll(b));
        }
        for (StoneSet set : ModBlocks.stoneSets()) {
            ResourceLocation tex = blockTexture(set.base().get());
            slabBlock(set.slab().get(), tex, tex);
            blockItem(set.slab().get());
            stairsBlock(set.stairs().get(), tex);
            blockItem(set.stairs().get());
            if (set.wall() != null) {
                wallBlock(set.wall().get(), tex);
                itemModels().getBuilder(name(set.wall().get()))
                        .parent(models().wallInventory(name(set.wall().get()) + "_inventory", tex));
            }
        }

        // the Reach
        simpleBlockWithItem(ModBlocks.GLIMMER_GRASS.get(), models().cubeBottomTop("glimmer_grass",
                modLoc("block/glimmer_grass_side"), modLoc("block/starfall_stone"), modLoc("block/glimmer_grass_top")));
        cutoutCross(ModBlocks.HALO_MOSS.get(), "halo_moss");
        cutoutCross(ModBlocks.HALO_MOSS_PLANT.get(), "halo_moss_plant");
        flatItem(ModBlocks.HALO_MOSS.get().asItem(), modLoc("block/halo_moss"));
        cutoutCross(ModBlocks.STARBLOOM.get(), "starbloom");
        flatItem(ModBlocks.STARBLOOM.get().asItem(), modLoc("block/starbloom"));
        simpleBlock(ModBlocks.POTTED_STARBLOOM.get(),
                models().flowerPotCross("potted_starbloom", modLoc("block/starbloom")).renderType("cutout"));
        getVariantBuilder(ModBlocks.STARBLOOM_CROP.get()).forAllStates(state -> {
            int age = state.getValue(StarbloomCropBlock.AGE);
            return ConfiguredModel.builder().modelFile(models()
                    .crop("starbloom_stage" + age, modLoc("block/starbloom_stage" + age)).renderType("cutout")).build();
        });
        // the Breach Frame, dark or lit while its ring is open (W4's BreachFrameBlock.ACTIVE)
        ModelFile frame = models().cubeBottomTop("breach_frame",
                modLoc("block/breach_frame_side"), mcLoc("block/cobblestone"), modLoc("block/breach_frame_top"));
        ModelFile litFrame = models().cubeBottomTop("breach_frame_active",
                modLoc("block/breach_frame_side_active"), mcLoc("block/cobblestone"), modLoc("block/breach_frame_top_active"));
        getVariantBuilder(ModBlocks.BREACH_FRAME.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(com.cosmicbreach.onboarding.BreachFrameBlock.ACTIVE) ? litFrame : frame).build());
        simpleBlockItem(ModBlocks.BREACH_FRAME.get(), frame);

        // the Drift: petrified Driftwood
        logBlock(ModBlocks.DRIFTWOOD_LOG.get());
        axisBlock(ModBlocks.DRIFTWOOD_WOOD.get(), modLoc("block/driftwood_log"), modLoc("block/driftwood_log"));
        logBlock(ModBlocks.STRIPPED_DRIFTWOOD_LOG.get());
        axisBlock(ModBlocks.STRIPPED_DRIFTWOOD_WOOD.get(), modLoc("block/stripped_driftwood_log"),
                modLoc("block/stripped_driftwood_log"));
        for (Block b : new Block[] {ModBlocks.DRIFTWOOD_LOG.get(), ModBlocks.DRIFTWOOD_WOOD.get(),
                ModBlocks.STRIPPED_DRIFTWOOD_LOG.get(), ModBlocks.STRIPPED_DRIFTWOOD_WOOD.get()}) {
            blockItem(b);
        }
        ResourceLocation planks = modLoc("block/driftwood_planks");
        slabBlock(ModBlocks.DRIFTWOOD_SLAB.get(), planks, planks);
        blockItem(ModBlocks.DRIFTWOOD_SLAB.get());
        stairsBlock(ModBlocks.DRIFTWOOD_STAIRS.get(), planks);
        blockItem(ModBlocks.DRIFTWOOD_STAIRS.get());
        fenceBlock(ModBlocks.DRIFTWOOD_FENCE.get(), planks);
        itemModels().getBuilder("driftwood_fence").parent(models().fenceInventory("driftwood_fence_inventory", planks));
        fenceGateBlock(ModBlocks.DRIFTWOOD_FENCE_GATE.get(), planks);
        blockItem(ModBlocks.DRIFTWOOD_FENCE_GATE.get());
        doorBlockWithRenderType(ModBlocks.DRIFTWOOD_DOOR.get(), modLoc("block/driftwood_door_bottom"),
                modLoc("block/driftwood_door_top"), "cutout");
        itemModels().basicItem(ModBlocks.DRIFTWOOD_DOOR_ITEM.get());
        trapdoorBlockWithRenderType(ModBlocks.DRIFTWOOD_TRAPDOOR.get(), modLoc("block/driftwood_trapdoor"), true, "cutout");
        itemModels().getBuilder("driftwood_trapdoor").parent(uncheckedBlockModel("driftwood_trapdoor_bottom"));
        pressurePlateBlock(ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get(), planks);
        blockItem(ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get());
        buttonBlock(ModBlocks.DRIFTWOOD_BUTTON.get(), planks);
        itemModels().getBuilder("driftwood_button").parent(models().buttonInventory("driftwood_button_inventory", planks));
        simpleBlockWithItem(ModBlocks.RIMEGLASS.get(),
                models().cubeAll("rimeglass", modLoc("block/rimeglass")).renderType("translucent"));

        // the Deep
        axisBlock(ModBlocks.UMBRAL_BASALT.get(), modLoc("block/umbral_basalt"), modLoc("block/umbral_basalt_top"));
        blockItem(ModBlocks.UMBRAL_BASALT.get());
        simpleBlockWithItem(ModBlocks.RIFT_GLASS.get(),
                models().cubeAll("rift_glass", modLoc("block/rift_glass")).renderType("translucent"));
        lichen(ModBlocks.MAGENTA_NEON_LICHEN.get(), "magenta_neon_lichen");
        lichen(ModBlocks.TEAL_NEON_LICHEN.get(), "teal_neon_lichen");

        // seeds and materials: flat sprites from textures/item/
        itemModels().basicItem(ModBlocks.STARBLOOM_SEEDS.get());
        for (DeferredHolder<Item, ? extends Item> item : ModMaterials.ITEMS.getEntries()) {
            itemModels().basicItem(item.get());
        }
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private ModelFile uncheckedBlockModel(String name) {
        return new ModelFile.UncheckedModelFile(modLoc("block/" + name));
    }

    /** The item shows the block's own model ({@code block/<name>}). */
    private void blockItem(Block block) {
        itemModels().getBuilder(name(block)).parent(uncheckedBlockModel(name(block)));
    }

    private void flatItem(Item item, ResourceLocation texture) {
        itemModels().withExistingParent(BuiltInRegistries.ITEM.getKey(item).getPath(), mcLoc("item/generated"))
                .texture("layer0", texture);
    }

    private void cutoutCross(Block block, String texture) {
        simpleBlock(block, models().cross(name(block), modLoc("block/" + texture)).renderType("cutout"));
    }

    /**
     * Glow lichen's multipart state: one face model per direction that is set, and every face when
     * none is (vanilla does the same). The face model reuses vanilla's glow lichen geometry.
     */
    private void lichen(Block block, String texture) {
        ModelFile face = models().withExistingParent(name(block), mcLoc("block/glow_lichen"))
                .texture("glow_lichen", modLoc("block/" + texture))
                .texture("particle", modLoc("block/" + texture))
                .renderType("cutout");
        Map<Direction, int[]> rotation = Map.of(
                Direction.NORTH, new int[] {0, 0}, Direction.EAST, new int[] {0, 90},
                Direction.SOUTH, new int[] {0, 180}, Direction.WEST, new int[] {0, 270},
                Direction.UP, new int[] {270, 0}, Direction.DOWN, new int[] {90, 0});
        MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
        for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP,
                Direction.DOWN}) {
            int[] r = rotation.get(d);
            builder.part().modelFile(face).rotationX(r[0]).rotationY(r[1]).uvLock(true).addModel()
                    .condition(MultifaceBlock.getFaceProperty(d), true).end();
            MultiPartBlockStateBuilder.PartBuilder none = builder.part().modelFile(face).rotationX(r[0]).rotationY(r[1])
                    .uvLock(true).addModel();
            for (Direction other : Direction.values()) {
                none.condition(MultifaceBlock.getFaceProperty(other), false);
            }
            none.end();
        }
        flatItem(block.asItem(), modLoc("block/" + texture));
    }
}
