package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.gear.forge.ForgeCrafting;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.mount.CelestialMount;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.provision.ProvisionRegistry;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * The levels' provisions in a real world (Lane A, A1.8), part {@code provisions}: on a test floor high over the Reach,
 * the recipes and tags the data run wrote are live (and the forge takes either the native material or the vanilla one);
 * the biomes list the new features; lichen and Driftwood burn; the two pickaxes mine at iron's and diamond's tiers; a
 * fallen Driftwood log places; a tamed Lumen Stag and a tamed Drift Manta drop their saddle and no meat or hide, a wild
 * Lumen Stag and a wild Drift Manta drop their meat; a furnace on Driftwood sears venison; Halo Moss gives berries; the
 * Umbral Cap holds on basalt only, a patch of them places on basalt, and one spreads in a closed dark room but not
 * under the open sky. For worlds explored before 1.1 (no fallen logs, no caps on the floors): Halo Moss is crafted into
 * planks and cut Neon Lichen turns up caps.
 *
 * <p>Every position is read when its step runs ({@link #base} is only known then), never when the steps are listed.
 */
public final class ProvisionsScenario implements Scenario {
    private final List<String> summary = new ArrayList<>();
    private BlockPos base = BlockPos.ZERO;

    @Override
    public int timeBudgetSeconds() {
        return 420;
    }

    // ------------------------------------------------------------------ where things are (relative to the floor)

    /** The closed dark room (a basalt floor, stone walls and ceiling). */
    private BlockPos room() {
        return base.offset(20, 0, 0);
    }

    /** A cap under the open sky, well clear of the room. */
    private BlockPos open() {
        return base.offset(8, 0, -9);
    }

    /** The centre of a basalt floor a cap patch is placed on. */
    private BlockPos patch() {
        return base.offset(-6, 0, -8);
    }

    private BlockPos tamedStag() {
        return base.offset(-8, 0, 9);
    }

    private BlockPos tamedManta() {
        return base.offset(4, 2, 9);
    }

    // ------------------------------------------------------------------ helpers

    private static ServerLevel level(MinecraftServer srv) {
        return CryptKit.player(srv).serverLevel();
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void send(Minecraft mc, String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }

    private int countItems(Item item, BlockPos at, double radius) {
        return CryptKit.server(srv -> {
            int n = 0;
            for (ItemEntity e : level(srv).getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(radius))) {
                if (e.getItem().is(item)) {
                    n += e.getItem().getCount();
                }
            }
            return n;
        });
    }

    private int countBlocks(Block block, BlockPos from, BlockPos to) {
        return CryptKit.server(srv -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(from, to)) {
                if (level(srv).getBlockState(p).is(block)) {
                    n++;
                }
            }
            return n;
        });
    }

    private static ForgeRecipe forge(MinecraftServer srv, String name) {
        return srv.getRecipeManager().byKey(CosmicBreach.id("forge/" + name))
                .map(holder -> (ForgeRecipe) holder.value())
                .orElseThrow(() -> new Steps.Failure("no forge recipe " + name));
    }

    /**
     * What a mount's loot table gives over {@code rolls} rolls, per watched item, for a wild or a tamed one (an animal
     * made but never added to the world: the table reads its saved data, where a tamed horse says {@code Tame:1b}).
     */
    private static int[] rolled(MinecraftServer srv, EntityType<? extends CelestialMount> type, boolean tamed, int rolls, Item... watched) {
        ServerLevel level = level(srv);
        CelestialMount mount = type.create(level);
        if (mount == null) {
            throw new Steps.Failure("cannot make a " + type);
        }
        mount.setTamed(tamed);
        LootTable table = srv.reloadableRegistries().getLootTable(type.getDefaultLootTable());
        RandomSource random = RandomSource.create(7L);
        int[] counts = new int[watched.length];
        for (int i = 0; i < rolls; i++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, mount)
                    .withParameter(LootContextParams.ORIGIN, mount.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                    .create(LootContextParamSets.ENTITY);
            for (ItemStack stack : table.getRandomItems(params, random)) {
                for (int k = 0; k < watched.length; k++) {
                    if (stack.is(watched[k])) {
                        counts[k] += stack.getCount();
                    }
                }
            }
        }
        mount.discard();
        return counts;
    }

    /** What a block's loot table gives over {@code rolls} cuts with {@code tool} in hand, per watched item. */
    private static int[] cut(MinecraftServer srv, BlockState state, ItemStack tool, int rolls, Item... watched) {
        ServerLevel level = level(srv);
        LootTable table = srv.reloadableRegistries().getLootTable(state.getBlock().getLootTable());
        RandomSource random = RandomSource.create(5L);
        int[] counts = new int[watched.length];
        for (int i = 0; i < rolls; i++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withParameter(LootContextParams.TOOL, tool)
                    .create(LootContextParamSets.BLOCK);
            for (ItemStack stack : table.getRandomItems(params, random)) {
                for (int k = 0; k < watched.length; k++) {
                    if (stack.is(watched[k])) {
                        counts[k] += stack.getCount();
                    }
                }
            }
        }
        return counts;
    }

    /** A right click with the cap in hand on the top of {@code floor}: what the cap item's own useOn does. */
    private static InteractionResult clickWithCap(ServerPlayer player, BlockPos floor) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false);
        return player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static boolean lists(Registry<Biome> biomes, String biome, String feature) {
        Biome b = biomes.get(CosmicBreach.id(biome));
        if (b == null) {
            return false;
        }
        for (HolderSet<PlacedFeature> step : b.getGenerationSettings().features()) {
            for (Holder<PlacedFeature> f : step) {
                if (f.is(CosmicBreach.id(feature))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the island is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .run("a test floor 40 blocks up", () -> base = CryptKit.server(srv -> {
                    BlockPos b = CryptKit.player(srv).blockPosition().above(40);
                    fill(level(srv), b.offset(-12, -1, -12), b.offset(30, -1, 12), ModBlocks.STARFALL_STONE.get().defaultBlockState());
                    fill(level(srv), b.offset(-12, 0, -12), b.offset(30, 6, 12), Blocks.AIR.defaultBlockState());
                    return b;
                }))
                .run("stand on it", () -> CryptKit.tp(mc, net.minecraft.world.phys.Vec3.atBottomCenterOf(base.offset(0, 0, -8)),
                        net.minecraft.world.phys.Vec3.atCenterOf(base)))
                .waitTicks(10);

        // the data run's recipes, tags and fuel are live
        steps.check("the provision recipes are loaded", () -> CryptKit.server(srv -> {
                    for (String id : List.of("starsteel_pickaxe", "nebulite_pickaxe", "glass_bottle_from_spire_quartz", "shears_from_starsteel",
                            "torch_from_neon_lichen", "rime_thread", "seared_lumen_venison", "seared_manta_fillet")) {
                        if (srv.getRecipeManager().byKey(CosmicBreach.id(id)).isEmpty()) {
                            throw new Steps.Failure("no recipe " + id);
                        }
                    }
                    return true;
                }))
                .check("Starhide is leather, Rime Thread string, Starsteel gilds, Starfall Stone makes stone tools", () -> CryptKit.server(srv ->
                        new ItemStack(ProvisionRegistry.STARHIDE.get()).is(Tags.Items.LEATHERS)
                                && new ItemStack(ProvisionRegistry.RIME_THREAD.get()).is(Tags.Items.STRINGS)
                                && new ItemStack(ModMaterials.STARSTEEL_INGOT.get()).is(ProvisionRegistry.GILDING)
                                && new ItemStack(ModBlocks.STARFALL_STONE.get()).is(ItemTags.STONE_TOOL_MATERIALS)))
                .check("lichen burns 200 ticks, Driftwood 300", () -> CryptKit.server(srv ->
                        new ItemStack(ModBlocks.MAGENTA_NEON_LICHEN.get()).getBurnTime(RecipeType.SMELTING) == 200
                                && new ItemStack(ModBlocks.DRIFTWOOD_LOG.get()).getBurnTime(RecipeType.SMELTING) == 300))
                .check("Starsteel mines Nebulite, not Eclipsium; Nebulite mines Eclipsium", () -> CryptKit.server(srv ->
                        new ItemStack(ProvisionRegistry.STARSTEEL_PICKAXE.get()).isCorrectToolForDrops(ModBlocks.NEBULITE_ORE.get().defaultBlockState())
                                && !new ItemStack(ProvisionRegistry.STARSTEEL_PICKAXE.get()).isCorrectToolForDrops(ModBlocks.ECLIPSIUM_ORE.get().defaultBlockState())
                                && new ItemStack(ProvisionRegistry.NEBULITE_PICKAXE.get()).isCorrectToolForDrops(ModBlocks.ECLIPSIUM_ORE.get().defaultBlockState())))
                .check("every provision has its English name", () -> {
                    List<String> missing = new ArrayList<>();
                    ProvisionRegistry.ITEMS.getEntries().forEach(item -> {
                        if (!I18n.exists(item.get().getDescriptionId())) {
                            missing.add(item.get().getDescriptionId());
                        }
                    });
                    if (!I18n.exists(ProvisionRegistry.UMBRAL_CAP.get().getDescriptionId())) {
                        missing.add(ProvisionRegistry.UMBRAL_CAP.get().getDescriptionId());
                    }
                    if (!missing.isEmpty()) {
                        throw new Steps.Failure("no name for " + missing);
                    }
                    return I18n.get(ProvisionRegistry.STARSTEEL_PICKAXE.get().getDescriptionId()).equals("Starsteel Pickaxe");
                })
                .check("the forge takes Starhide or leather, Rime Thread or string, Starsteel or gold", () -> CryptKit.server(srv -> {
                    ItemStack steel2 = new ItemStack(ModMaterials.STARSTEEL_INGOT.get(), 2);
                    ItemStack bloom = new ItemStack(ModBlocks.STARBLOOM.get());
                    ItemStack nebulite2 = new ItemStack(ModMaterials.NEBULITE_INGOT.get(), 2);
                    ForgeRecipe saddle = forge(srv, "astral_saddle");
                    boolean saddles = ForgeCrafting.covers(saddle.ingredients(), List.of(new ItemStack(ProvisionRegistry.STARHIDE.get(), 4), steel2, bloom))
                            && ForgeCrafting.covers(saddle.ingredients(), List.of(new ItemStack(Items.LEATHER, 4), steel2, bloom))
                            && ForgeCrafting.covers(saddle.ingredients(), List.of(new ItemStack(ProvisionRegistry.STARHIDE.get(), 2),
                                    new ItemStack(Items.LEATHER, 2), steel2, bloom))
                            && !ForgeCrafting.covers(saddle.ingredients(), List.of(new ItemStack(ProvisionRegistry.STARHIDE.get(), 3), steel2, bloom));
                    ForgeRecipe harness = forge(srv, "drift_harness");
                    boolean harnesses = ForgeCrafting.covers(harness.ingredients(), List.of(new ItemStack(ProvisionRegistry.STARHIDE.get(), 4),
                            nebulite2, new ItemStack(ProvisionRegistry.RIME_THREAD.get(), 4)))
                            && ForgeCrafting.covers(harness.ingredients(), List.of(new ItemStack(Items.LEATHER, 4), nebulite2, new ItemStack(Items.STRING, 4)));
                    ForgeRecipe reins = forge(srv, "halo_reins");
                    ItemStack moss = new ItemStack(ModBlocks.HALO_MOSS.get(), 4);
                    boolean gilded = ForgeCrafting.covers(reins.ingredients(), List.of(moss, steel2, new ItemStack(ProvisionRegistry.STARHIDE.get())))
                            && ForgeCrafting.covers(reins.ingredients(), List.of(moss, new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.LEATHER)));
                    summary.add("forge tags: saddle " + saddles + ", harness " + harnesses + ", reins " + gilded);
                    return saddles && harnesses && gilded;
                }))
                .check("the Reach's biomes list fallen Driftwood and the Deep's lists the cap patch", () -> CryptKit.server(srv -> {
                    Registry<Biome> biomes = srv.registryAccess().registryOrThrow(Registries.BIOME);
                    return lists(biomes, "shattered_spires", "fallen_driftwood_sparse")
                            && lists(biomes, "sunfield_terraces", "fallen_driftwood")
                            && lists(biomes, "rift_abyss", "umbral_cap_patch");
                }))
                .check("rolled 300 times, a wild mount gives its meat and Starhide and a tamed one nothing", () -> CryptKit.server(srv -> {
                    Item venison = ProvisionRegistry.LUMEN_VENISON.get();
                    Item fillet = ProvisionRegistry.MANTA_FILLET.get();
                    Item hide = ProvisionRegistry.STARHIDE.get();
                    int[] wildStag = rolled(srv, Mounts.LUMEN_STAG.get(), false, 300, venison, hide);
                    int[] tamedStag = rolled(srv, Mounts.LUMEN_STAG.get(), true, 300, venison, hide);
                    int[] wildManta = rolled(srv, Mounts.DRIFT_MANTA.get(), false, 300, fillet, hide);
                    int[] tamedManta = rolled(srv, Mounts.DRIFT_MANTA.get(), true, 300, fillet, hide);
                    summary.add("300 rolls: wild stag " + wildStag[0] + " venison, " + wildStag[1] + " hide; wild manta " + wildManta[0]
                            + " fillets, " + wildManta[1] + " hide; tamed " + (tamedStag[0] + tamedStag[1] + tamedManta[0] + tamedManta[1]) + " items");
                    return wildStag[0] >= 300 && wildStag[1] > 0 && wildManta[0] >= 300 && wildManta[1] > 0
                            && tamedStag[0] == 0 && tamedStag[1] == 0 && tamedManta[0] == 0 && tamedManta[1] == 0;
                }))
                .check("Halo Moss is crafted into Driftwood planks, two for one (an explored island's first wood)", () -> CryptKit.server(srv -> {
                    CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(ModBlocks.HALO_MOSS.get())));
                    var recipe = srv.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level(srv));
                    if (recipe.isEmpty()) {
                        throw new Steps.Failure("nothing is crafted from one Halo Moss");
                    }
                    ItemStack out = recipe.get().value().assemble(input, srv.registryAccess());
                    return out.is(ModBlocks.DRIFTWOOD_PLANKS.get().asItem()) && out.getCount() == 2;
                }));

        // a fallen log
        steps.run("place a fallen Driftwood log", () -> send(mc, "place feature cosmicbreach:fallen_driftwood %d %d %d",
                        base.getX(), base.getY(), base.getZ()))
                .waitTicks(5)
                .check("three to six logs lie on the floor", () -> {
                    int logs = countBlocks(ModBlocks.DRIFTWOOD_LOG.get(), base.offset(-6, 0, -6), base.offset(6, 2, 6));
                    summary.add("fallen Driftwood: " + logs + " logs");
                    return logs >= 3 && logs <= 8;
                });

        // tamed mounts give no meat or hide, only their saddle (planning decision: tamed mounts drop only their gear)
        String saddleId = BuiltInRegistries.ITEM.getKey(Mounts.ASTRAL_SADDLE.get()).toString();
        String harnessId = BuiltInRegistries.ITEM.getKey(Mounts.DRIFT_HARNESS.get()).toString();
        steps.run("a tamed stag in its saddle and a tamed manta in its harness", () -> {
                    send(mc, "summon cosmicbreach:lumen_stag %d %d %d {Tame:1b,SaddleItem:{id:\"%s\",count:1}}",
                            tamedStag().getX(), tamedStag().getY(), tamedStag().getZ(), saddleId);
                    send(mc, "summon cosmicbreach:drift_manta %d %d %d {Tame:1b,SaddleItem:{id:\"%s\",count:1}}",
                            tamedManta().getX(), tamedManta().getY(), tamedManta().getZ(), harnessId);
                })
                .waitTicks(10)
                .command("kill @e[type=cosmicbreach:lumen_stag]")
                .command("kill @e[type=cosmicbreach:drift_manta]")
                .waitTicks(30)
                .check("the tamed ones dropped their saddle and harness", () -> {
                    int saddles = countItems(Mounts.ASTRAL_SADDLE.get(), tamedStag(), 5);
                    int harnesses = countItems(Mounts.DRIFT_HARNESS.get(), tamedManta(), 5);
                    summary.add("tamed drops: " + saddles + " saddle, " + harnesses + " harness");
                    return saddles == 1 && harnesses == 1;
                })
                .check("and no meat and no hide", () -> {
                    int meat = countItems(ProvisionRegistry.LUMEN_VENISON.get(), tamedStag(), 6)
                            + countItems(ProvisionRegistry.MANTA_FILLET.get(), tamedManta(), 6);
                    int hide = countItems(ProvisionRegistry.STARHIDE.get(), tamedStag(), 6) + countItems(ProvisionRegistry.STARHIDE.get(), tamedManta(), 6);
                    summary.add("tamed drops: " + meat + " meat, " + hide + " hide");
                    return meat == 0 && hide == 0;
                })
                .command("kill @e[type=minecraft:item]")
                .waitTicks(5);

        // meat from the wild stag and the wild manta
        steps.run("a stag and a manta", () -> {
                    send(mc, "summon cosmicbreach:lumen_stag %d %d %d", base.getX() + 10, base.getY(), base.getZ());
                    send(mc, "summon cosmicbreach:drift_manta %d %d %d", base.getX() + 10, base.getY() + 2, base.getZ() + 6);
                })
                .waitTicks(10)
                .command("kill @e[type=cosmicbreach:lumen_stag]")
                .command("kill @e[type=cosmicbreach:drift_manta]")
                .waitTicks(30)
                .check("the stag dropped venison", () -> countItems(ProvisionRegistry.LUMEN_VENISON.get(), base.offset(10, 0, 0), 8) >= 1)
                .check("the manta dropped fillets", () -> countItems(ProvisionRegistry.MANTA_FILLET.get(), base.offset(10, 0, 6), 10) >= 1)
                .log("hides", () -> "Starhide dropped: " + countItems(ProvisionRegistry.STARHIDE.get(), base.offset(10, 0, 3), 12));

        // a furnace on Driftwood
        BlockPos[] furnace = {BlockPos.ZERO};
        steps.run("a furnace with venison over Driftwood planks", () -> {
                    furnace[0] = base.offset(-4, 0, 4);
                    BlockPos f = furnace[0];
                    send(mc, "setblock %d %d %d minecraft:furnace", f.getX(), f.getY(), f.getZ());
                    send(mc, "item replace block %d %d %d container.0 with cosmicbreach:lumen_venison", f.getX(), f.getY(), f.getZ());
                    send(mc, "item replace block %d %d %d container.1 with cosmicbreach:driftwood_planks 2", f.getX(), f.getY(), f.getZ());
                })
                .waitUntil("it sears", 260, () -> CryptKit.server(srv -> level(srv).getBlockEntity(furnace[0]) instanceof AbstractFurnaceBlockEntity fe
                        && fe.getItem(2).is(ProvisionRegistry.SEARED_LUMEN_VENISON.get())));

        // berries from Halo Moss
        steps.check("Halo Moss cut forty times drops berries", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockPos moss = base.offset(-8, 3, 0);
                    level.setBlock(moss.above(), ModBlocks.STARFALL_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
                    for (int i = 0; i < 40; i++) {
                        level.setBlock(moss, ModBlocks.HALO_MOSS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.destroyBlock(moss, true);
                    }
                    return true;
                }))
                .waitTicks(5)
                .check("at least one Halo Berry fell", () -> {
                    int berries = countItems(ProvisionRegistry.HALO_BERRIES.get(), base.offset(-8, 3, 0), 6);
                    summary.add("Halo Berries from 40 cuts: " + berries);
                    return berries >= 1;
                });

        // caps from Neon Lichen: the Deep's food in chunks generated before the world features put caps on its floors
        steps.check("Neon Lichen cut forty times turns up caps", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockPos lichen = base.offset(-10, 0, 6);
                    BlockState onTheFloor = ModBlocks.MAGENTA_NEON_LICHEN.get().defaultBlockState()
                            .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
                    for (int i = 0; i < 40; i++) {
                        level.setBlock(lichen, onTheFloor, Block.UPDATE_CLIENTS);
                        level.destroyBlock(lichen, true);
                    }
                    return true;
                }))
                .waitTicks(5)
                .check("at least one Umbral Cap fell", () -> {
                    int caps = countItems(ProvisionRegistry.UMBRAL_CAP_ITEM.get(), base.offset(-10, 0, 6), 5);
                    summary.add("Umbral Caps from 40 cuts of lichen: " + caps);
                    return caps >= 1;
                });

        // a patch of caps on a basalt floor (the world feature, placed by command)
        steps.run("a basalt floor, and a patch of Umbral Caps placed on it", () -> {
                    CryptKit.server(srv -> {
                        fill(level(srv), patch().offset(-4, -1, -4), patch().offset(4, -1, 4), ModBlocks.UMBRAL_BASALT.get().defaultBlockState());
                        return true;
                    });
                    send(mc, "place feature cosmicbreach:umbral_cap_patch %d %d %d", patch().getX(), patch().getY(), patch().getZ());
                })
                .waitTicks(5)
                .check("the patch put caps on the basalt", () -> {
                    int caps = countBlocks(ProvisionRegistry.UMBRAL_CAP.get(), patch().offset(-5, 0, -5), patch().offset(5, 2, 5));
                    summary.add("Umbral Caps in a placed patch: " + caps);
                    return caps >= 1;
                });

        // shears take Halo Moss and Neon Lichen whole and give no berry or cap; the real tables, rolled with each tool in hand
        steps.check("shears and Silk Touch take Halo Moss whole and give no berry; by hand it gives berries", () -> CryptKit.server(srv -> {
                    BlockState moss = ModBlocks.HALO_MOSS.get().defaultBlockState();
                    Item berry = ProvisionRegistry.HALO_BERRIES.get();
                    Item strand = ModBlocks.HALO_MOSS.get().asItem();
                    ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
                    silk.enchant(level(srv).registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
                    int[] shears = cut(srv, moss, new ItemStack(Items.SHEARS), 300, berry, strand);
                    int[] silked = cut(srv, moss, silk, 300, berry, strand);
                    int[] hand = cut(srv, moss, ItemStack.EMPTY, 300, berry, strand);
                    summary.add("Halo Moss over 300 cuts: shears " + shears[0] + " berries and " + shears[1] + " strands, Silk Touch " + silked[0] + " and "
                            + silked[1] + ", by hand " + hand[0] + " and " + hand[1]);
                    return shears[0] == 0 && shears[1] == 300 && silked[0] == 0 && silked[1] == 300 && hand[0] >= 40;
                }))
                .check("shears take Neon Lichen whole and give no cap; by hand it turns up caps", () -> CryptKit.server(srv -> {
                    BlockState lichen = ModBlocks.TEAL_NEON_LICHEN.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
                    Item cap = ProvisionRegistry.UMBRAL_CAP_ITEM.get();
                    Item whole = ModBlocks.TEAL_NEON_LICHEN.get().asItem();
                    int[] shears = cut(srv, lichen, new ItemStack(Items.SHEARS), 300, cap, whole);
                    int[] hand = cut(srv, lichen, ItemStack.EMPTY, 300, cap, whole);
                    summary.add("Neon Lichen over 300 cuts: shears " + shears[0] + " caps and " + shears[1] + " pieces, by hand " + hand[0] + " and " + hand[1]);
                    return shears[0] == 0 && shears[1] == 300 && hand[0] >= 40 && hand[1] == 0;
                }));

        // the cap is also a block item: a hungry player eats it on a right click whatever the cursor is on, sneaking plants it
        steps.check("a hungry player eats a cap aimed at the floor; sneaking, or being full, plants it", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    ServerPlayer player = CryptKit.player(srv);
                    BlockPos floor = base.offset(12, -1, 8);
                    BlockPos above = floor.above();
                    Block capBlock = ProvisionRegistry.UMBRAL_CAP.get();
                    level.setBlock(floor, ModBlocks.UMBRAL_BASALT.get().defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                    int food = player.getFoodData().getFoodLevel();
                    boolean sneaking = player.isShiftKeyDown();
                    try {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProvisionRegistry.UMBRAL_CAP_ITEM.get(), 3));
                        player.getFoodData().setFoodLevel(10);
                        player.setShiftKeyDown(false);
                        InteractionResult eat = clickWithCap(player, floor);
                        boolean eating = eat.consumesAction() && player.isUsingItem() && level.getBlockState(above).isAir();
                        player.stopUsingItem();
                        player.setShiftKeyDown(true);
                        InteractionResult plant = clickWithCap(player, floor);
                        boolean plantedSneaking = plant.consumesAction() && !player.isUsingItem() && level.getBlockState(above).is(capBlock);
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        player.getFoodData().setFoodLevel(20);
                        player.setShiftKeyDown(false);
                        InteractionResult full = clickWithCap(player, floor);
                        boolean plantedFull = full.consumesAction() && !player.isUsingItem() && level.getBlockState(above).is(capBlock);
                        summary.add("the cap on a basalt floor: hungry " + eat + " eating " + eating + "; hungry and sneaking " + plant + " planted "
                                + plantedSneaking + "; full " + full + " planted " + plantedFull);
                        return eating && plantedSneaking && plantedFull;
                    } finally {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        player.stopUsingItem();
                        player.setItemInHand(InteractionHand.MAIN_HAND, held);
                        player.getFoodData().setFoodLevel(food);
                        player.setShiftKeyDown(sneaking);
                    }
                }));

        // the Umbral Cap: basalt only, and spreading only in the dark
        steps.check("the cap holds on basalt and not on Starfall Stone", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockState cap = ProvisionRegistry.UMBRAL_CAP.get().defaultBlockState();
                    level.setBlock(open().below(), ModBlocks.UMBRAL_BASALT.get().defaultBlockState(), Block.UPDATE_ALL);
                    return cap.canSurvive(level, open()) && !cap.canSurvive(level, open().offset(2, 0, 0));
                }))
                .run("a closed dark room with a basalt floor and one cap; one cap under a column of open sky", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockPos room = room();
                    BlockPos open = open();
                    BlockState stone = ModBlocks.STARFALL_STONE.get().defaultBlockState();
                    fill(level, room.offset(-5, -1, -5), room.offset(5, 4, 5), stone);
                    fill(level, room.offset(-4, -1, -4), room.offset(4, -1, 4), ModBlocks.UMBRAL_BASALT.get().defaultBlockState());
                    fill(level, room.offset(-4, 0, -4), room.offset(4, 3, 4), Blocks.AIR.defaultBlockState());
                    // nothing above the open cap to the top of the world, so no spire can shade it
                    fill(level, open.offset(-1, 1, -1), new BlockPos(open.getX() + 1, level.getMaxBuildHeight() - 1, open.getZ() + 1),
                            Blocks.AIR.defaultBlockState());
                    level.setBlock(room, ProvisionRegistry.UMBRAL_CAP.get().defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(open, ProvisionRegistry.UMBRAL_CAP.get().defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .waitTicks(40)
                .run("three thousand random ticks each", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockPos room = room();
                    BlockPos open = open();
                    RandomSource random = RandomSource.create(11L);
                    for (int i = 0; i < 3000; i++) {
                        for (BlockPos p : BlockPos.betweenClosed(room.offset(-4, 0, -4), room.offset(4, 1, 4))) {
                            BlockState s = level.getBlockState(p);
                            if (s.is(ProvisionRegistry.UMBRAL_CAP.get())) {
                                s.randomTick(level, p.immutable(), random);
                                break;
                            }
                        }
                        level.getBlockState(open).randomTick(level, open, random);
                    }
                    return true;
                }))
                .check("it spread in the dark room and not under the sky", () -> {
                    int dark = countBlocks(ProvisionRegistry.UMBRAL_CAP.get(), room().offset(-4, 0, -4), room().offset(4, 1, 4));
                    int lit = countBlocks(ProvisionRegistry.UMBRAL_CAP.get(), open().offset(-5, -1, -5), open().offset(5, 1, 5));
                    int sky = CryptKit.server(srv -> level(srv).getBrightness(net.minecraft.world.level.LightLayer.SKY, open()));
                    int inside = CryptKit.server(srv -> level(srv).getBrightness(net.minecraft.world.level.LightLayer.SKY, room()));
                    summary.add("Umbral Caps after 3000 ticks: " + dark + " in the dark, " + lit + " in the open (sky light " + sky + " there, "
                            + inside + " in the room)");
                    return dark >= 2 && lit == 1;
                })
                .log("summary", () -> String.join("; ", summary));
    }
}
