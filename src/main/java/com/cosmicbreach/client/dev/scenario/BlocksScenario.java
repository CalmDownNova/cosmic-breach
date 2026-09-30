package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.block.StarbloomCropBlock;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.registry.ModMaterials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The W1 blocks and materials, checked in the game:
 *
 * <ol>
 *   <li>Every block and item is registered, named, in its creative tab, and every block state and item
 *       has a real baked model with no missing-texture sprite on any face.</li>
 *   <li>Every block stands in a labelled grid on the superflat (a row per layer and family; plants on
 *       their soil, the crop in all four stages, Halo Moss hanging from a ceiling), shot in daylight from
 *       two angles plus close-ups along each row. The legend (label number, block) is in the log.</li>
 *   <li>In survival, each ore and a Meteorite are mined through the real attack key with the right
 *       pickaxe and drop their raw metal; Nebulite with a stone pickaxe and Eclipsium with an iron one
 *       drop nothing; Glimmer Grass gives Starfall Stone; an axe strips a Driftwood log.</li>
 *   <li>A Meteorite's loot rolled 4,000 times gives a Heartstone about 5% of the time; the Breach Frame,
 *       smelting and stonecutter recipes resolve.</li>
 * </ol>
 */
public final class BlocksScenario implements Scenario {
    /** The display grid, a row per group, west to east. {@code starbloom_crop} takes four columns (ages 0 to 3). */
    static final String[][] ROWS = {
            {"starfall_stone", "starfall_stone_slab", "starfall_stone_stairs", "starfall_stone_wall",
                    "polished_starfall_stone", "polished_starfall_stone_slab", "polished_starfall_stone_stairs",
                    "polished_starfall_stone_wall", "starfall_stone_bricks", "starfall_stone_brick_slab",
                    "starfall_stone_brick_stairs", "starfall_stone_brick_wall"},
            {"glimmer_grass", "spire_quartz", "starsteel_ore", "starsteel_block", "meteorite", "breach_frame", "starbloom",
                    "potted_starbloom", "starbloom_crop", "halo_moss"},
            {"driftstone", "driftstone_slab", "driftstone_stairs", "driftstone_wall", "driftstone_bricks",
                    "driftstone_brick_slab", "driftstone_brick_stairs", "driftstone_brick_wall", "nebulite_ore",
                    "nebulite_block", "rimeglass"},
            {"driftwood_log", "driftwood_wood", "stripped_driftwood_log", "stripped_driftwood_wood", "driftwood_planks",
                    "driftwood_slab", "driftwood_stairs", "driftwood_fence", "driftwood_fence_gate", "driftwood_door",
                    "driftwood_trapdoor", "driftwood_pressure_plate", "driftwood_button"},
            {"umbral_basalt", "polished_umbral_basalt", "polished_umbral_basalt_slab", "polished_umbral_basalt_stairs",
                    "umbral_basalt_bricks", "umbral_basalt_brick_slab", "umbral_basalt_brick_stairs",
                    "umbral_basalt_brick_wall", "rift_glass", "eclipsium_ore", "eclipsium_block", "magenta_neon_lichen",
                    "teal_neon_lichen"},
    };
    private static final String[] ROW_TITLES = {"Reach stone", "Reach", "Drift stone", "Driftwood", "Deep"};
    /** Shown only as part of a Halo Moss strand. */
    private static final String HIDDEN_IN_STRAND = "halo_moss_plant";
    static final List<String> MATERIALS = List.of("raw_starsteel", "starsteel_ingot", "starsteel_nugget", "heartstone",
            "raw_nebulite", "nebulite_ingot", "gyre_core", "gyre_blade", "raw_eclipsium", "eclipsium_ingot",
            "eclipsium_nugget", "umbral_silk", "prism_heart", "leviathan_scale", "leviathan_pearl", "silent_sigil",
            "solar_ember", "hymn_crystal", "solar_heart");
    private static final Set<String> NO_ITEM = Set.of("halo_moss_plant", "potted_starbloom", "starbloom_crop");
    private static final int COLUMN = 2;
    private static final int ROW = 5;

    private final Minecraft mc = Minecraft.getInstance();
    private DevCamera camera;
    /** The grid's first column of its first row, at ground level (the block the grid stands on is below). */
    private BlockPos origin;
    /** Where the ores go for mining: two blocks south of the player, at head height. */
    private BlockPos target;

    @Override
    public void steps(Steps steps) {
        BlockPos feet = mc.player.blockPosition();
        origin = feet.offset(-12, 0, -8);
        target = feet.offset(0, 1, 2);
        registrations(steps);
        grid(steps);
        mining(steps);
        lootAndRecipes(steps);
    }

    @Override
    public int timeBudgetSeconds() {
        return 240;
    }

    // ------------------------------------------------------------------ 1. registry, models, names, tabs

    static List<String> expectedBlocks() {
        List<String> ids = new ArrayList<>();
        for (String[] row : ROWS) {
            ids.addAll(List.of(row));
        }
        ids.add(HIDDEN_IN_STRAND);
        return ids;
    }

    static List<String> expectedItems() {
        List<String> ids = new ArrayList<>();
        for (String id : expectedBlocks()) {
            if (!NO_ITEM.contains(id)) {
                ids.add(id);
            }
        }
        ids.add("starbloom_seeds");
        ids.addAll(MATERIALS);
        return ids;
    }

    private void registrations(Steps steps) {
        steps.check("the mod registers exactly the 60 blocks of the grid", () -> {
            Set<String> registered = ModBlocks.BLOCKS.getEntries().stream().map(h -> h.getId().getPath())
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> expected = new TreeSet<>(expectedBlocks());
            if (!registered.equals(expected)) {
                throw new Steps.Failure("blocks differ from the grid: registered but not shown " + minus(registered, expected)
                        + ", shown but not registered " + minus(expected, registered));
            }
            return expected.size() == 60;
        }).check("every block and item id is in the game's registries", () -> {
            for (String id : expectedBlocks()) {
                require(BuiltInRegistries.BLOCK.containsKey(CosmicBreach.id(id)), "block " + id + " is not registered");
            }
            for (String id : expectedItems()) {
                require(BuiltInRegistries.ITEM.containsKey(CosmicBreach.id(id)), "item " + id + " is not registered");
            }
            Set<String> items = new TreeSet<>();
            ModBlocks.ITEMS.getEntries().forEach(h -> items.add(h.getId().getPath()));
            ModMaterials.ITEMS.getEntries().forEach(h -> items.add(h.getId().getPath()));
            require(items.equals(new TreeSet<>(expectedItems())), "items differ: " + minus(items, new TreeSet<>(expectedItems()))
                    + " / " + minus(new TreeSet<>(expectedItems()), items));
            return true;
        }).check("every block state has a baked model, and no face uses the missing texture", () -> {
            BakedModel missing = mc.getModelManager().getMissingModel();
            int states = 0;
            for (String id : expectedBlocks()) {
                Block block = block(id);
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                    require(model != missing, "no model for " + state);
                    // some states legitimately draw nothing (a wall with no post and no sides); the default never does
                    requireSprites(model, state, "block state " + state, state == block.defaultBlockState());
                    states++;
                }
            }
            logLater("checked " + states + " block states");
            return true;
        }).check("every item has a baked model with real textures", () -> {
            BakedModel missing = mc.getModelManager().getMissingModel();
            for (String id : expectedItems()) {
                ItemStack stack = new ItemStack(item(id));
                BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                require(model != missing, "no model for item " + id);
                requireSprites(model, null, "item " + id, true);
            }
            return true;
        }).check("every block and item has an English name", () -> {
            for (String id : expectedBlocks()) {
                String key = block(id).getDescriptionId();
                require(I18n.exists(key), "no name for " + key);
            }
            for (String id : expectedItems()) {
                String key = item(id).getDescriptionId();
                require(I18n.exists(key), "no name for " + key);
            }
            require(I18n.get("block.cosmicbreach.starfall_stone_brick_slab").equals("Starfall Stone Brick Slab"),
                    "names come from the data run");
            require(I18n.exists("itemGroup.cosmicbreach.blocks"), "the blocks tab has no title");
            return true;
        }).check("the blocks tab shows every block item and the main tab every material", () -> {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            Collection<ItemStack> blocks = ModBlocks.BLOCKS_TAB.get().getDisplayItems();
            Collection<ItemStack> main = ModCreativeTab.COSMIC_BREACH.get().getDisplayItems();
            for (var h : ModBlocks.ITEMS.getEntries()) {
                require(blocks.stream().anyMatch(s -> s.is(h.get())), h.getId() + " is not in the blocks tab");
            }
            for (var h : ModMaterials.ITEMS.getEntries()) {
                require(main.stream().anyMatch(s -> s.is(h.get())), h.getId() + " is not in the Cosmic Breach tab");
            }
            logLater("blocks tab: " + blocks.size() + " stacks, main tab: " + main.size());
            return true;
        });
        steps.log("registrations", () -> pendingLog());
    }

    private void requireSprites(BakedModel model, BlockState state, String what, boolean mustDraw) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, random));
        for (Direction d : Direction.values()) {
            quads.addAll(model.getQuads(state, d, random));
        }
        require(!mustDraw || !quads.isEmpty() || isEmptyMultiface(state), what + " has no faces at all");
        for (BakedQuad q : quads) {
            require(!isMissing(q.getSprite()), what + " uses the missing texture");
        }
        require(!isMissing(model.getParticleIcon()), what + " has the missing texture as its particle");
    }

    /** A multiface block with no face set draws every face (vanilla does the same); that state never exists in a world. */
    private static boolean isEmptyMultiface(BlockState state) {
        return state != null && state.getBlock() instanceof MultifaceBlock
                && Direction.stream().noneMatch(d -> state.getValue(MultifaceBlock.getFaceProperty(d)));
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    // ------------------------------------------------------------------ 2. the grid

    private void grid(Steps steps) {
        steps.command("gamerule randomTickSpeed 0")
                .command("time set noon")
                .command("weather clear")
                .run("build the grid", () -> ServerQuery.ask(player -> {
                    buildGrid(player);
                    return true;
                }))
                .log("legend", this::legend)
                .run("hide the HUD, make the camera", () -> {
                    mc.options.hideGui = true;
                    camera = DevCamera.create(mc.level);
                })
                .waitTicks(40)
                .check("the client sees the whole grid", () -> {
                    for (int r = 0; r < ROWS.length; r++) {
                        int c = 0;
                        for (String id : ROWS[r]) {
                            int span = id.equals("starbloom_crop") ? 4 : 1;
                            for (int k = 0; k < span; k++, c++) {
                                BlockState s = mc.level.getBlockState(cell(r, c));
                                require(!s.isAir() || id.equals("halo_moss"), "no " + id + " at " + cell(r, c).toShortString());
                            }
                        }
                    }
                    return true;
                });
        Vec3 centre = Vec3.atBottomCenterOf(origin).add(12, 0, -10);
        shot(steps, "grid_south", centre.add(0, 12, 19), centre);
        shot(steps, "grid_northeast", centre.add(18, 10, -17), centre);
        for (int r = 0; r < ROWS.length; r++) {
            Vec3 rowCentre = Vec3.atBottomCenterOf(cell(r, 0)).add(0, 0.5, 0);
            // 4.6 blocks back puts the camera just north of the row in front, so that row stays out of frame
            shot(steps, String.format(Locale.ROOT, "row%d_west", r), rowCentre.add(6, 4.2, 4.6), rowCentre.add(6, 0, 0));
            shot(steps, String.format(Locale.ROOT, "row%d_east", r), rowCentre.add(18, 4.2, 4.6), rowCentre.add(18, 0, 0));
        }
        steps.run("the player's view and HUD again", () -> {
            camera.remove();
            mc.options.hideGui = false;
        });
    }

    private void shot(Steps steps, String label, Vec3 eye, Vec3 at) {
        steps.run("camera for " + label, () -> {
            camera.place(eye, at);
            camera.use();
        }).waitTicks(14).screenshot(label);
    }

    private BlockPos cell(int row, int column) {
        return origin.offset(column * COLUMN, 0, -row * ROW);
    }

    private final List<String> legendLines = new ArrayList<>();

    private String legend() {
        return "grid legend (label: block):\n" + String.join("\n", legendLines);
    }

    private void buildGrid(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        // a calm stage: smooth stone under the grid, nothing above it
        BlockPos a = origin.offset(-4, -1, 3);
        BlockPos b = origin.offset(13 * COLUMN + 2, -1, -(ROWS.length - 1) * ROW - 3);
        for (BlockPos p : BlockPos.betweenClosed(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), a.getY() + 6, Math.max(a.getZ(), b.getZ()))) {
            level.setBlock(p, p.getY() == a.getY() ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), flags);
        }
        legendLines.clear();
        int label = 0;
        for (int r = 0; r < ROWS.length; r++) {
            summonLabel(server, level, Vec3.atBottomCenterOf(cell(r, -1)).add(0, 1.2, 0), ROW_TITLES[r], 1.0f);
            int c = 0;
            for (String id : ROWS[r]) {
                Block block = block(id);
                if (id.equals("starbloom_crop")) {
                    for (int age = 0; age <= StarbloomCropBlock.MAX_AGE; age++, c++) {
                        BlockPos p = cell(r, c);
                        level.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), flags);
                        level.setBlock(p, block.defaultBlockState().setValue(StarbloomCropBlock.AGE, age), flags);
                        label++;
                        legendLines.add(label + ": " + id + " age " + age);
                        summonLabel(server, level, Vec3.atBottomCenterOf(p).add(0, 1.5, 0), String.valueOf(label), 1.4f);
                    }
                    continue;
                }
                BlockPos p = cell(r, c++);
                double labelHeight = 1.5;
                switch (id) {
                    case "starbloom" -> {
                        level.setBlock(p.below(), block("glimmer_grass").defaultBlockState(), flags);
                        level.setBlock(p, block.defaultBlockState(), flags);
                    }
                    case "halo_moss" -> {
                        level.setBlock(p.above(3), block("starfall_stone").defaultBlockState(), flags);
                        level.setBlock(p.above(2), block(HIDDEN_IN_STRAND).defaultBlockState(), flags);
                        level.setBlock(p.above(1), block(HIDDEN_IN_STRAND).defaultBlockState(), flags);
                        level.setBlock(p, block.defaultBlockState(), flags);
                        labelHeight = 4.5;
                    }
                    case "driftwood_door" -> {
                        BlockState door = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
                        level.setBlock(p, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), flags);
                        level.setBlock(p.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), flags);
                        labelHeight = 2.5;
                    }
                    case "driftwood_fence_gate" -> level.setBlock(p, block.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH), flags);
                    case "driftwood_button" -> level.setBlock(p, block.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR), flags);
                    case "magenta_neon_lichen", "teal_neon_lichen" -> level.setBlock(p,
                            block.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true), flags);
                    default -> level.setBlock(p, block.defaultBlockState(), flags);
                }
                label++;
                legendLines.add(label + ": " + id + (id.equals("halo_moss") ? " (tip) under 2 x " + HIDDEN_IN_STRAND : ""));
                summonLabel(server, level, Vec3.atBottomCenterOf(p).add(0, labelHeight, 0), String.valueOf(label), 1.4f);
            }
        }
    }

    private static void summonLabel(MinecraftServer server, ServerLevel level, Vec3 at, String text, float scale) {
        String cmd = String.format(Locale.ROOT,
                "summon minecraft:text_display %.2f %.2f %.2f {text:'{\"text\":\"%s\"}',billboard:\"center\",alignment:\"center\","
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
                        + "scale:[%.2ff,%.2ff,%.2ff]}}",
                at.x, at.y, at.z, text, scale, scale, scale);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
    }

    // ------------------------------------------------------------------ 3. mining through the attack key

    private void mining(Steps steps) {
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("effect give @s minecraft:resistance infinite 4 true")
                .command("effect give @s minecraft:saturation infinite 0 true");
        mine(steps, "starsteel_ore", "minecraft:stone_pickaxe", "cosmicbreach:raw_starsteel", 120);
        mine(steps, "meteorite", "minecraft:stone_pickaxe", "cosmicbreach:raw_starsteel", 120);
        mine(steps, "nebulite_ore", "minecraft:iron_pickaxe", "cosmicbreach:raw_nebulite", 120);
        mine(steps, "eclipsium_ore", "minecraft:diamond_pickaxe", "cosmicbreach:raw_eclipsium", 120);
        mine(steps, "glimmer_grass", "minecraft:stone_pickaxe", "cosmicbreach:starfall_stone", 60);
        mine(steps, "nebulite_ore", "minecraft:stone_pickaxe", null, 200);
        mine(steps, "eclipsium_ore", "minecraft:iron_pickaxe", null, 200);
        // An axe strips a Driftwood log (NeoForge's tool hook). Through the server's own item-use method,
        // not the use key: in this dev client a right click with an axe never reaches a block (a vanilla
        // oak log does not strip either), while stone places and shears carve a pumpkin through the same
        // key. Most likely Better Combat, a dev-only comparison mod that treats axes as weapons.
        steps.command("clear @s")
                .command("item replace entity @s weapon.mainhand with minecraft:iron_axe")
                .waitUntil("holding an iron axe", 40, () -> mc.player.getMainHandItem().is(Items.IRON_AXE))
                .run("a Driftwood log at head height", () -> ServerQuery.ask(p -> {
                    p.serverLevel().setBlock(target, block("driftwood_log").defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .waitUntil("the client sees the log", 40, () -> mc.level.getBlockState(target).is(block("driftwood_log")))
                .check("using the axe on its north face strips it, keeping the axis", () -> ServerQuery.ask(p -> {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0, -0.5), Direction.NORTH, target, false);
                    InteractionResult result = p.gameMode.useItemOn(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    BlockState now = p.serverLevel().getBlockState(target);
                    logLater("axe on driftwood_log: " + result + ", now " + now);
                    return result.consumesAction() && now.is(block("stripped_driftwood_log"))
                            && now.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y;
                }))
                .log("strip", this::pendingLog)
                .command("setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:air");
    }

    /** Places {@code blockId}, mines it with {@code tool} by holding attack, and checks the drop ({@code null}: nothing). */
    private void mine(Steps steps, String blockId, String tool, String drop, int timeoutTicks) {
        String what = blockId + " with " + tool.replace("minecraft:", "");
        Item toolItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tool));
        steps.command("kill @e[type=item]")
                .command("clear @s")
                .command("item replace entity @s weapon.mainhand with " + tool)
                .waitUntil("holding " + tool, 40, () -> mc.player.getMainHandItem().is(toolItem))
                .run("place " + blockId, () -> ServerQuery.ask(p -> {
                    p.serverLevel().setBlock(target, block(blockId).defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .waitUntil("the client sees " + blockId, 40, () -> mc.level.getBlockState(target).is(block(blockId)))
                .run("aim at " + blockId, () -> aim(Vec3.atCenterOf(target)))
                .waitTicks(2)
                .check("the crosshair is on " + blockId, this::onTarget)
                .hold(mc.options.keyAttack)
                .waitUntil(what + " breaks", timeoutTicks, () -> ServerQuery.ask(p -> p.serverLevel().getBlockState(target).isAir()))
                .release(mc.options.keyAttack)
                .waitTicks(12)
                .check(what + (drop == null ? " drops nothing" : " drops " + drop), () -> {
                    Map<String, Integer> drops = ServerQuery.ask(this::dropsNearTarget);
                    logLater(what + ": " + drops);
                    if (drop == null) {
                        return drops.isEmpty();
                    }
                    return drops.getOrDefault(drop, 0) >= 1 && drops.keySet().stream().allMatch(k -> k.equals(drop) || k.equals(tool));
                })
                .log(what, this::pendingLog);
    }

    /** Items lying round the target plus what the player holds (other than the tool), by item id. */
    private Map<String, Integer> dropsNearTarget(ServerPlayer player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity e : player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(4))) {
            counts.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(), Integer::sum);
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && i != inv.selected) {
                counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private boolean onTarget() {
        return mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target);
    }

    private void aim(Vec3 at) {
        LocalPlayer p = mc.player;
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    // ------------------------------------------------------------------ 4. loot rates and recipes

    private void lootAndRecipes(Steps steps) {
        steps.check("a Meteorite without Silk Touch gives 1 to 2 raw Starsteel, and a Heartstone about 5% of the time", () -> {
            double[] r = ServerQuery.ask(p -> {
                ServerLevel level = p.serverLevel();
                BlockState meteorite = block("meteorite").defaultBlockState();
                ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
                int n = 4000;
                int hearts = 0;
                int steel = 0;
                int minSteel = Integer.MAX_VALUE;
                int maxSteel = 0;
                for (int i = 0; i < n; i++) {
                    int s = 0;
                    for (ItemStack drop : Block.getDrops(meteorite, level, target, null, p, pick)) {
                        if (drop.is(ModMaterials.HEARTSTONE.get())) {
                            hearts += drop.getCount();
                        } else if (drop.is(ModMaterials.RAW_STARSTEEL.get())) {
                            s += drop.getCount();
                        } else {
                            return new double[] {-1, 0, 0, 0};
                        }
                    }
                    steel += s;
                    minSteel = Math.min(minSteel, s);
                    maxSteel = Math.max(maxSteel, s);
                }
                return new double[] {hearts / (double) n, steel / (double) n, minSteel, maxSteel};
            });
            logLater(String.format(Locale.ROOT, "meteorite x4000: heartstone rate %.4f, raw starsteel mean %.3f (min %d, max %d)",
                    r[0], r[1], (int) r[2], (int) r[3]));
            return r[0] >= 0.035 && r[0] <= 0.065 && r[1] > 1.35 && r[1] < 1.65 && r[2] == 1 && r[3] == 2;
        }).log("meteorite loot", this::pendingLog).check("Fortune III raises Starsteel Ore's raw drops above one on average", () -> {
            double mean = ServerQuery.ask(p -> {
                ServerLevel level = p.serverLevel();
                ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
                pick.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE), 3);
                int total = 0;
                for (int i = 0; i < 1000; i++) {
                    for (ItemStack drop : Block.getDrops(block("starsteel_ore").defaultBlockState(), level, target, null, p, pick)) {
                        total += drop.is(ModMaterials.RAW_STARSTEEL.get()) ? drop.getCount() : 0;
                    }
                }
                return total / 1000.0;
            });
            logLater(String.format(Locale.ROOT, "starsteel ore with fortune III: %.2f raw starsteel on average", mean));
            return mean > 1.8;
        }).log("fortune", this::pendingLog).check("an empty flower pot takes a Starbloom", () ->
                ((FlowerPotBlock) Blocks.FLOWER_POT).getFullPotsView().get(CosmicBreach.id("starbloom")).get() == block("potted_starbloom")
        ).check("the Breach Frame recipe: copper corners, stone elsewhere, gives 6 (two crafts make a 4 by 4 ring)", () -> ServerQuery.ask(p -> {
            ServerLevel level = p.serverLevel();
            for (Item stone : new Item[] {Items.COBBLESTONE, Items.COBBLED_DEEPSLATE}) {
                ItemStack c = new ItemStack(Items.COPPER_INGOT);
                ItemStack s = new ItemStack(stone);
                CraftingInput input = CraftingInput.of(3, 3, List.of(c, s, c, s, s, s, c, s, c));
                ItemStack out = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                        .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
                if (!out.is(item("breach_frame")) || out.getCount() != 6) {
                    return false;
                }
            }
            return true;
        })).check("raw Starsteel smelts and blasts into an ingot; Driftwood logs make planks; the stonecutter cuts", () -> ServerQuery.ask(p -> {
            ServerLevel level = p.serverLevel();
            var rm = level.getRecipeManager();
            SingleRecipeInput raw = new SingleRecipeInput(new ItemStack(ModMaterials.RAW_STARSTEEL.get()));
            boolean smelt = rm.getRecipeFor(RecipeType.SMELTING, raw, level)
                    .map(h -> h.value().assemble(raw, level.registryAccess()).is(ModMaterials.STARSTEEL_INGOT.get())).orElse(false);
            boolean blast = rm.getRecipeFor(RecipeType.BLASTING, raw, level)
                    .map(h -> h.value().assemble(raw, level.registryAccess()).is(ModMaterials.STARSTEEL_INGOT.get())).orElse(false);
            CraftingInput log = CraftingInput.of(1, 1, List.of(new ItemStack(item("stripped_driftwood_wood"))));
            boolean planks = rm.getRecipeFor(RecipeType.CRAFTING, log, level)
                    .map(h -> h.value().assemble(log, level.registryAccess())).filter(s -> s.is(item("driftwood_planks")) && s.getCount() == 4)
                    .isPresent();
            SingleRecipeInput stone = new SingleRecipeInput(new ItemStack(item("starfall_stone")));
            Set<String> cuts = new LinkedHashSet<>();
            rm.getRecipesFor(RecipeType.STONECUTTING, stone, level)
                    .forEach(h -> cuts.add(BuiltInRegistries.ITEM.getKey(h.value().assemble(stone, level.registryAccess()).getItem()).getPath()));
            boolean cutter = cuts.containsAll(List.of("starfall_stone_slab", "polished_starfall_stone", "starfall_stone_bricks",
                    "starfall_stone_brick_wall", "polished_starfall_stone_stairs"));
            logLater("smelt " + smelt + ", blast " + blast + ", planks " + planks + ", starfall stone cuts into " + cuts.size() + ": " + cuts);
            return smelt && blast && planks && cutter;
        })).log("recipes", this::pendingLog).check("Starbloom grows on Glimmer Grass and Halo Moss hangs from the grid's ceiling", () -> ServerQuery.ask(p -> {
            ServerLevel level = p.serverLevel();
            BlockPos flower = findInGrid(level, block("starbloom"));
            BlockPos moss = findInGrid(level, block("halo_moss"));
            return flower != null && moss != null
                    && level.getBlockState(flower).canSurvive(level, flower)
                    && level.getBlockState(flower.below()).is(block("glimmer_grass"))
                    && level.getBlockState(moss).canSurvive(level, moss);
        }));
    }

    private BlockPos findInGrid(ServerLevel level, Block block) {
        for (int r = 0; r < ROWS.length; r++) {
            for (int c = 0; c < 16; c++) {
                if (level.getBlockState(cell(r, c)).is(block)) {
                    return cell(r, c);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(CosmicBreach.id(id));
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(CosmicBreach.id(id));
        if (item == Items.AIR) {
            throw new Steps.Failure("no item " + id);
        }
        return item;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new Steps.Failure(message);
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    private final List<String> pending = new ArrayList<>();

    private void logLater(String line) {
        pending.add(line);
    }

    private String pendingLog() {
        String s = pending.isEmpty() ? "(nothing)" : String.join("; ", pending);
        pending.clear();
        return s;
    }
}
