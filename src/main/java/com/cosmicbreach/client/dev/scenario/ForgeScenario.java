package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Mannequin;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gear.AstralForgeScreen;
import com.cosmicbreach.client.gear.GearClient;
import com.cosmicbreach.client.gear.SetAbilityHud;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.forge.AstralForgeBlock;
import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeTiers;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetState;
import com.cosmicbreach.gear.vanguard.MeteorCall;
import com.cosmicbreach.gear.vanguard.VanguardRules;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Astral Forge, reforging and the Starfall Vanguard, end to end through the real input paths (keys, the
 * crafting table's recipe placement, clicks in the Forge's screen, placing and using blocks):
 * <ol>
 *   <li>The Forge crafted at a crafting table from 4 Starsteel Ingots, 4 Starfall Stone and a Spire Quartz, and
 *       placed: tier I, one ring (screenshot).</li>
 *   <li>Its screen: the tier II recipes (Comet Maul, Binary Edges) locked though their materials are carried;
 *       Meridian crafted from 3 Starsteel Ingots, a Spire Quartz and a Stick.</li>
 *   <li>A Prism Heart raises it to tier II (screenshot of the rings); the Maul and the Edges crafted.</li>
 *   <li>Meridian's L1 on a dummy at T1, reforged to T2 with 4 Nebulite Ingots, the L1 again: x1.15.</li>
 *   <li>The Vanguard (the helm crafted, the rest given) put on with the use key: armor 15, toughness 4, knockback
 *       resistance 0.2, Power 8, Resilience 4.</li>
 *   <li>A 6-block fall: half the fall damage, a shockwave that hits a dummy for 3 + 0.5 a block, the avoided
 *       damage stored as Heat and added to the next L1.</li>
 *   <li>Meteor Call with its key: the mark, the meteor on a dummy 8 blocks off for 12 x (1 + 0.55 x S(8)), the
 *       crater's Scorch, the cooldown and its HUD pip.</li>
 *   <li>The armor on a mannequin from the front, the side and the back, at rest, with Heat, and running.</li>
 * </ol>
 */
public final class ForgeScenario implements Scenario {
    private static final String DUMMY = "cosmicbreach_forge_dummy";
    private static final ResourceLocation FORGE_RECIPE = CosmicBreach.id("astral_forge");

    /** A hit a dummy took, after every modifier (the Heat) and before armor. */
    private record Hit(String type, float amount) {}

    private final List<Hit> dummyHits = new CopyOnWriteArrayList<>();
    private final List<Float> playerFallDamage = new CopyOnWriteArrayList<>();
    private final List<Float> fallDistances = new CopyOnWriteArrayList<>();
    private final List<String> summary = new ArrayList<>();
    private BlockPos forgePos = BlockPos.ZERO;
    private final double[] measured = new double[4];
    private @Nullable DevCamera camera;
    private @Nullable Mannequin mannequin;
    private final boolean looksOnly;

    public ForgeScenario() {
        this(false);
    }

    /** {@code looksOnly}: only the look reviews (the Forge at every tier, the armor, the meteor from the side). */
    public ForgeScenario(boolean looksOnly) {
        this.looksOnly = looksOnly;
    }

    @Override
    public int timeBudgetSeconds() {
        return 360;
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(DUMMY)) {
                dummyHits.add(new Hit(event.getSource().typeHolder().getRegisteredName(), event.getAmount()));
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer && event.getSource().getMsgId().equals("fall")) {
                playerFallDamage.add(event.getNewDamage());
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingFallEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer) {
                fallDistances.add(event.getDistance());
            }
        });
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("clear @s");
        if (looksOnly) {
            reviewLooks(steps, mc);
            return;
        }
        craftTheForge(steps, mc);
        tierOne(steps, mc);
        tierTwo(steps, mc);
        reforge(steps, mc);
        vanguard(steps, mc);
        heavyLanding(steps, mc);
        meteorCall(steps, mc);
        looks(steps, mc);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ 1. the Forge

    private void craftTheForge(Steps steps, Minecraft mc) {
        BlockPos[] table = {BlockPos.ZERO};
        give(steps, "starsteel_ingot 4", "starfall_stone 4", "spire_quartz 1");
        steps.command("recipe give @s " + FORGE_RECIPE)
                .run("remember where the table goes", () -> table[0] = BlockPos.containing(mc.player.position()).offset(0, 0, 2))
                .command("setblock ~ ~ ~2 minecraft:crafting_table")
                .waitUntil("the crafting table stands there", 60,
                        () -> mc.level.getBlockState(table[0]).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))
                .run("look at the crafting table", () -> lookAt(mc, mc.player.position().add(0, 0.5, 2.0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the crafting table opened", 40, () -> mc.screen instanceof CraftingScreen)
                .run("click the Forge's recipe (the recipe book's placement)", () -> {
                    RecipeHolder<?> recipe = mc.level.getRecipeManager().byKey(FORGE_RECIPE)
                            .orElseThrow(() -> new Steps.Failure("no recipe " + FORGE_RECIPE));
                    mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, recipe, false);
                })
                .waitUntil("the grid shows the Forge as the result", 40,
                        () -> mc.player.containerMenu.getSlot(0).getItem().is(GearRegistry.ASTRAL_FORGE_ITEM.get()))
                .waitTicks(2)
                .screenshot("crafting_table")
                .run("shift-click the result", () -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0,
                        ClickType.QUICK_MOVE, mc.player))
                .waitUntil("the Forge is in the inventory", 40, () -> count(mc, GearRegistry.ASTRAL_FORGE_ITEM.get()) == 1)
                .waitUntil("its materials were used up", 20, () -> count(mc, ModMaterials.STARSTEEL_INGOT.get()) == 0)
                .run("close the crafting table", () -> mc.player.closeContainer())
                .command("setblock ~ ~ ~2 minecraft:air")
                // place it with the use key, 2 blocks ahead
                .run("select the Forge", () -> selectHotbar(mc, GearRegistry.ASTRAL_FORGE_ITEM.get()))
                .waitTicks(2)
                .run("look at the ground 2 blocks ahead", () -> lookAt(mc, mc.player.position().add(0, -0.5, 2.5)))
                .waitTicks(3)
                .run("remember where it will go", () -> {
                    if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                        throw new Steps.Failure("not looking at a block");
                    }
                    forgePos = hit.getBlockPos().relative(hit.getDirection());
                })
                .press(mc.options.keyUse)
                .waitUntil("the Forge stands there, on both sides", 120, () -> mc.level.getBlockState(forgePos).is(GearRegistry.ASTRAL_FORGE.get())
                        && ServerQuery.ask(p -> p.level().getBlockState(forgePos).is(GearRegistry.ASTRAL_FORGE.get())))
                .check("at tier I", () -> forgeTier(mc) == 1)
                .run("a camera on the Forge from its far side, the HUD hidden", () -> forgeCamera(mc))
                .waitTicks(10)
                .screenshot("forge_tier1")
                .run("back to the player's eyes", this::dropCamera);
    }

    // ------------------------------------------------------------------ 2. tier I: locked recipes, Meridian

    private void tierOne(Steps steps, Minecraft mc) {
        give(steps, "starsteel_ingot 3", "spire_quartz 1", "minecraft:stick 1", "nebulite_ingot 7", "heartstone 1", "starshard 2",
                "driftwood_log 2", "gyre_blade 2", "rimeglass 1");
        openForge(steps, mc);
        steps.check("every recipe is listed (at least 8)", () -> screen(mc).recipeCount() >= 8)
                .check("the Comet Maul and the Binary Edges show locked at tier I", () ->
                        screen(mc).isLocked(screen(mc).indexOf(ModItems.COMET_MAUL.get()))
                                && screen(mc).isLocked(screen(mc).indexOf(ModItems.BINARY_EDGES.get()))
                                && !screen(mc).isLocked(screen(mc).indexOf(ModItems.MERIDIAN.get())))
                .run("click the Comet Maul", () -> clickCell(mc, ModItems.COMET_MAUL.get()))
                .check("its materials are carried, but it can't be forged here yet", () -> !screen(mc).canCraftSelected())
                .waitTicks(2)
                .screenshot("gui_tier1_locked")
                .run("click Meridian", () -> clickCell(mc, ModItems.MERIDIAN.get()))
                .check("Meridian can be forged", () -> screen(mc).canCraftSelected())
                .waitTicks(2)
                .screenshot("gui_tier1_meridian")
                .run("click Forge", () -> click(mc, screen(mc).craftButtonCenter()))
                .waitUntil("Meridian is in the inventory", 40, () -> count(mc, ModItems.MERIDIAN.get()) == 1)
                .waitUntil("3 Starsteel Ingots, the Spire Quartz and the Stick were used", 20,
                        () -> count(mc, ModMaterials.STARSTEEL_INGOT.get()) == 0 && count(mc, net.minecraft.world.item.Items.STICK) == 0)
                .run("close the Forge", () -> mc.player.closeContainer());
    }

    // ------------------------------------------------------------------ 3. tier II

    private void tierTwo(Steps steps, Minecraft mc) {
        give(steps, "prism_heart 1");
        steps.run("select the Prism Heart", () -> selectHotbar(mc, ModMaterials.PRISM_HEART.get()))
                .waitTicks(2)
                .run("look at the Forge", () -> lookAt(mc, Vec3.atCenterOf(forgePos).add(0, 0.3, 0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the Forge rose to tier II", 40, () -> forgeTier(mc) == 2
                        && ServerQuery.ask(p -> p.level().getBlockState(forgePos).getValue(AstralForgeBlock.TIER)) == 2)
                .waitUntil("the Prism Heart was used", 20, () -> count(mc, ModMaterials.PRISM_HEART.get()) == 0)
                .run("a camera on the Forge from its far side, the HUD hidden", () -> forgeCamera(mc))
                .waitTicks(8)
                .screenshot("forge_tier2_growing")
                .waitTicks(30)
                .screenshot("forge_tier2")
                .run("back to the player's eyes", this::dropCamera);
        openForge(steps, mc);
        steps.check("tier II opened the Maul and the Edges", () -> !screen(mc).isLocked(screen(mc).indexOf(ModItems.COMET_MAUL.get()))
                        && !screen(mc).isLocked(screen(mc).indexOf(ModItems.BINARY_EDGES.get())))
                .run("click the Comet Maul", () -> clickCell(mc, ModItems.COMET_MAUL.get()))
                .waitTicks(2)
                .screenshot("gui_tier2_maul")
                .run("click Forge", () -> click(mc, screen(mc).craftButtonCenter()))
                .waitUntil("the Comet Maul is in the inventory", 40, () -> count(mc, ModItems.COMET_MAUL.get()) == 1)
                .run("click the Binary Edges", () -> clickCell(mc, ModItems.BINARY_EDGES.get()))
                .run("click Forge", () -> click(mc, screen(mc).craftButtonCenter()))
                .waitUntil("the Binary Edges are in the inventory", 40, () -> count(mc, ModItems.BINARY_EDGES.get()) == 1)
                .waitUntil("their materials were used up", 20, () -> count(mc, ModMaterials.NEBULITE_INGOT.get()) == 0
                        && count(mc, ModMaterials.GYRE_BLADE.get()) == 0 && count(mc, ModMaterials.HEARTSTONE.get()) == 0)
                .run("close the Forge", () -> mc.player.closeContainer());
    }

    // ------------------------------------------------------------------ 4. reforging

    private void reforge(Steps steps, Minecraft mc) {
        l1OnDummy(steps, mc, "Meridian at T1", 0, 5.0);
        give(steps, "nebulite_ingot 4");
        openForge(steps, mc);
        steps.run("shift-click Meridian into the reforge slot", () -> {
                    int slot = menuSlotOf(mc, ModItems.MERIDIAN.get());
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
                })
                .waitUntil("Meridian waits in the reforge slot", 40, () -> menu(mc).piece().is(ModItems.MERIDIAN.get()))
                .waitTicks(2)
                .screenshot("gui_reforge_ready")
                .run("click Reforge", () -> click(mc, screen(mc).reforgeButtonCenter()))
                .waitUntil("Meridian is tier II", 40, () -> GearTier.of(menu(mc).piece(), 1) == 2)
                .waitUntil("the 4 Nebulite Ingots were used", 20, () -> count(mc, ModMaterials.NEBULITE_INGOT.get()) == 0)
                .waitTicks(2)
                .screenshot("gui_reforged")
                .run("shift-click it back", () -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 0, 0,
                        ClickType.QUICK_MOVE, mc.player))
                .waitUntil("Meridian is back in the inventory", 40, () -> count(mc, ModItems.MERIDIAN.get()) == 1)
                .run("close the Forge", () -> mc.player.closeContainer())
                .check("its tooltip says Tier II and base damage 5.8", () -> {
                    ItemStack stack = find(mc, ModItems.MERIDIAN.get());
                    List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                            TooltipFlag.NORMAL);
                    String all = String.join(" | ", lines.stream().map(Component::getString).toList());
                    summary.add("reforged tooltip: " + all);
                    return all.contains("Tier II") && all.contains("5.8");
                });
        l1OnDummy(steps, mc, "Meridian at T2", 1, 5.0 * 1.15);
        steps.check("the reforged L1 hits x1.15 (crits taken out)", () -> {
            double ratio = measured[1] / measured[0];
            summary.add(String.format(Locale.ROOT, "reforge: T1 L1 %.3f, T2 L1 %.3f, ratio %.4f", measured[0], measured[1], ratio));
            return Math.abs(ratio - 1.15) < 1e-3 && Math.abs(measured[0] - 5.0) < 1e-3;
        });
    }

    /**
     * An L1 with Meridian on a fresh dummy 2.2 blocks ahead (facing north, away from the Forge); its damage goes to
     * measured[index], with a crit's x1.5 taken out if it crit (zero Power). {@code plain} is the expected plain hit.
     */
    private void l1OnDummy(Steps steps, Minecraft mc, String what, int index, double plain) {
        steps.run("clear the dummies", ForgeScenario::discardDummies)
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .look(180, 8)
                .waitTicks(3)
                .run("a dummy 2.2 blocks ahead", () -> spawnAhead(2.2))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(mc) == 1)
                .waitUntil("the machine holds Meridian", 60, () -> machine(mc).weapon() != null
                        && machine(mc).phase() == CombatStateMachine.Phase.IDLE)
                .waitTicks(5)
                .run("forget earlier hits", dummyHits::clear)
                .press(mc.options.keyAttack)
                .waitUntil(what + ": the L1 landed", 60, () -> !dummyHits.isEmpty())
                .run("record " + what, () -> {
                    Hit hit = dummyHits.get(0);
                    boolean crit = Math.abs(hit.amount() - plain * 1.5) < 1e-3;
                    measured[index] = crit ? hit.amount() / 1.5 : hit.amount();
                    summary.add(String.format(Locale.ROOT, "%s: %.3f (%s)", what, hit.amount(), hit.type()));
                })
                .waitUntil("the move is over", 60, () -> machine(mc).phase() == CombatStateMachine.Phase.IDLE);
    }

    // ------------------------------------------------------------------ 5. the Vanguard

    private void vanguard(Steps steps, Minecraft mc) {
        steps.run("clear the dummies", ForgeScenario::discardDummies)
                .command("clear @s")
                .command("give @s cosmicbreach:meridian[cosmicbreach:gear_tier=2]");
        give(steps, "starsteel_ingot 5", "starshard 1");
        openForge(steps, mc);
        steps.run("click the Vanguard Helm", () -> clickCell(mc, GearRegistry.VANGUARD.helmet().get()))
                .run("click Forge", () -> click(mc, screen(mc).craftButtonCenter()))
                .waitUntil("the helm is in the inventory", 40, () -> count(mc, GearRegistry.VANGUARD.helmet().get()) == 1)
                .run("close the Forge", () -> mc.player.closeContainer());
        give(steps, "starfall_vanguard_chestplate 1", "starfall_vanguard_greaves 1", "starfall_vanguard_boots 1");
        steps.look(180, -10).waitTicks(3);
        for (var piece : GearRegistry.VANGUARD.all()) {
            steps.run("select the " + piece.getId().getPath(), () -> selectHotbar(mc, piece.get()))
                    .waitTicks(2)
                    .press(mc.options.keyUse)
                    .waitUntil("it is worn", 40, () -> ArmorSets.pieces(mc.player, GearRegistry.VANGUARD.set())
                            > GearRegistry.VANGUARD.all().indexOf(piece));
        }
        steps.waitUntil("all four pieces are worn on the server", 40,
                        () -> ServerQuery.ask(p -> ArmorSets.pieces(p, GearRegistry.VANGUARD.set())) == 4)
                .waitTicks(5)
                .log("armor now", () -> String.format(Locale.ROOT, "client armor %.4f, toughness %.4f, kb %.4f; server armor %.4f",
                        mc.player.getAttributeValue(Attributes.ARMOR), mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                        mc.player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),
                        ServerQuery.ask(p -> p.getAttributeValue(Attributes.ARMOR))))
                .waitUntil("armor 15 and toughness 4 on both sides, knockback resistance 0.2 on the server (it isn't synced)", 40, () ->
                        Math.abs(mc.player.getAttributeValue(Attributes.ARMOR) - 15) < 1e-6
                                && Math.abs(mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 4) < 1e-6
                                && ServerQuery.ask(p -> Math.abs(p.getAttributeValue(Attributes.ARMOR) - 15) < 1e-6
                                && Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 4) < 1e-6
                                && Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.2) < 1e-6))
                .waitUntil("+8 Power and +4 Resilience, on both sides", 40, () ->
                        ProgressionStats.of(mc.player).power() == 8 && ProgressionStats.of(mc.player).resilience() == 4
                                && ServerQuery.ask(p -> ProgressionStats.of(p).power() == 8 && ProgressionStats.of(p).resilience() == 4))
                .log("armor", () -> ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "armor %.2f, toughness %.2f, knockback resistance %.2f, stats %s", p.getAttributeValue(Attributes.ARMOR),
                        p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),
                        ProgressionStats.of(p))))
                .command("item replace entity @s armor.head with cosmicbreach:starfall_vanguard_helm[cosmicbreach:gear_tier=2]")
                .waitUntil("a helm reforged to T2 gives x1.15 armor and toughness: 15.3 and 4.15", 40, () ->
                        ServerQuery.ask(p -> Math.abs(p.getAttributeValue(Attributes.ARMOR) - (13 + 2 * 1.15)) < 1e-6
                                && Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - (3 + 1.15)) < 1e-6))
                .log("reforged helm", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "with a T2 helm: armor %.3f, toughness %.3f",
                        p.getAttributeValue(Attributes.ARMOR), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS))))
                .run("third person, facing the camera, the chat cleared", () -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    mc.gui.getChat().clearMessages(false);
                })
                .waitTicks(5)
                .screenshot("vanguard_worn_front")
                .run("first person", () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------------ 6. Heavy Landing and Heat

    private void heavyLanding(Steps steps, Minecraft mc) {
        float[] healthBefore = {0};
        steps.run("clear the dummies", ForgeScenario::discardDummies)
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .look(180, 8)
                .waitTicks(3)
                .run("a dummy 2.2 blocks ahead", () -> spawnAhead(2.2))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(mc) == 1)
                .command("effect give @s minecraft:instant_health 1 5 true")
                .waitTicks(20)
                .run("forget", () -> {
                    dummyHits.clear();
                    playerFallDamage.clear();
                    fallDistances.clear();
                    healthBefore[0] = mc.player.getHealth();
                })
                .command("tp @s ~ ~6.3 ~")
                .waitUntil("airborne", 20, () -> !mc.player.onGround())
                .waitUntil("landed, and the server saw the fall", 80, () -> mc.player.onGround() && !fallDistances.isEmpty())
                .waitUntil("the fall damage and the shockwave arrived", 20, () -> !playerFallDamage.isEmpty() && !dummyHits.isEmpty())
                .check("the fall did half its vanilla damage (then Resilience's 2%)", () -> {
                    double distance = fallDistances.get(0);
                    double vanilla = VanguardRules.vanillaFallDamage(distance, 1.0, 3.0, 1.0);
                    double expected = vanilla * 0.5 * (1.0 - 0.02);
                    float taken = playerFallDamage.get(0);
                    summary.add(String.format(Locale.ROOT, "fall %.2f blocks: vanilla %.1f, taken %.3f (expected %.3f)", distance,
                            vanilla, taken, expected));
                    return vanilla >= 3.0 && Math.abs(taken - expected) < 0.02;
                })
                .check("the shockwave hit the dummy for 3 + 0.5 a block", () -> {
                    double distance = fallDistances.get(0);
                    Hit hit = dummyHits.get(0);
                    double expected = VanguardRules.shockwaveDamage(distance);
                    summary.add(String.format(Locale.ROOT, "shockwave: %.3f (%s), expected %.3f", hit.amount(), hit.type(), expected));
                    return hit.type().equals("cosmicbreach:shockwave") && Math.abs(hit.amount() - expected) < 1e-3;
                })
                .check("the avoided half is stored as Heat", () -> {
                    double heat = ServerQuery.ask(p -> (double) ArmorSets.state(p).meter(p.level().getGameTime()));
                    double avoided = VanguardRules.vanillaFallDamage(fallDistances.get(0), 1.0, 3.0, 1.0) * 0.5;
                    summary.add(String.format(Locale.ROOT, "Heat stored %.3f (avoided %.3f)", heat, avoided));
                    measured[2] = heat;
                    return heat > 0 && Math.abs(heat - avoided) < 1e-3;
                })
                .run("forget the shockwave", dummyHits::clear)
                .waitUntil("the machine is idle", 20, () -> machine(mc).phase() == CombatStateMachine.Phase.IDLE)
                .press(mc.options.keyAttack)
                .waitUntil("the L1 landed", 40, () -> !dummyHits.isEmpty())
                .check("the L1 carried the Heat: T2 base x Power 8 scaling (+crit), plus the Heat", () -> {
                    Hit hit = dummyHits.get(0);
                    double scaling = 1.0 + 0.55 * (8.0 / 38.0);
                    double base = 5.0 * 1.15 * scaling;
                    double plain = base + measured[2];
                    double crit = base * (1.5 + 0.08) + measured[2];
                    summary.add(String.format(Locale.ROOT, "L1 with Heat: %.3f (plain %.3f or crit %.3f)", hit.amount(), plain, crit));
                    return Math.abs(hit.amount() - plain) < 1e-3 || Math.abs(hit.amount() - crit) < 1e-3;
                })
                .check("the Heat is spent", () -> ServerQuery.ask(p -> ArmorSets.state(p).meter(p.level().getGameTime()) == 0f))
                .waitUntil("the move is over", 60, () -> machine(mc).phase() == CombatStateMachine.Phase.IDLE);
    }

    // ------------------------------------------------------------------ 7. Meteor Call

    private void meteorCall(Steps steps, Minecraft mc) {
        steps.run("clear the dummies", ForgeScenario::discardDummies)
                .look(180, 8)
                .waitTicks(3)
                .run("a dummy 8 blocks ahead", () -> spawnAhead(8.0))
                .waitUntil("the dummy stands there", 40, () -> dummyCount(mc) == 1)
                .run("look at the ground under it", () -> lookAt(mc, mc.player.position().add(
                        HitShape.forward(mc.player.getYRot()).scale(8.0))))
                .waitTicks(5)
                .run("forget", dummyHits::clear)
                .check("the ability is ready", () -> ServerQuery.ask(p -> ArmorSets.state(p).ready(p.level().getGameTime())))
                .press(GearClient.SET_ABILITY)
                .waitUntil("the meteor is called", 20, () -> ServerQuery.ask(p -> MeteorCall.pendingCount()) > 0)
                .check("the cooldown started: 600 ticks at zero Arcane", () -> ServerQuery.ask(p -> {
                    SetState s = ArmorSets.state(p);
                    return s.cooldownTicks() == 600 && !s.ready(p.level().getGameTime());
                }))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .waitTicks(10)
                .screenshot("meteor_mark")
                .waitTicks(12)
                .screenshot("meteor_falling")
                .waitUntil("the meteor landed on the dummy", 30, () -> dummyHits.stream().anyMatch(h -> h.type().equals("cosmicbreach:meteor")))
                .waitTicks(1)
                .screenshot("meteor_impact")
                .check("it hit for 12 x (1 + 0.55 x S(8))", () -> {
                    Hit hit = dummyHits.stream().filter(h -> h.type().equals("cosmicbreach:meteor")).findFirst().orElseThrow();
                    double expected = VanguardRules.meteorDamage(8, false);
                    summary.add(String.format(Locale.ROOT, "meteor: %.3f, expected %.3f", hit.amount(), expected));
                    return Math.abs(hit.amount() - expected) < 1e-3;
                })
                .waitUntil("the crater Scorched the dummy", 20, () -> ServerQuery.ask(p -> dummies(p).stream()
                        .anyMatch(z -> z.hasEffect(GearRegistry.SCORCH))))
                .check("the HUD pip shows the cooldown filling", () -> {
                    SetAbilityHud.Pip pip = SetAbilityHud.pip();
                    return pip != null && !pip.ready() && pip.fill() < 0.2f;
                })
                .waitTicks(20)
                .screenshot("hud_pip_cooling")
                .run("clear the dummies", ForgeScenario::discardDummies);
    }

    // ------------------------------------------------------------------ 8. the armor's look

    private void looks(Steps steps, Minecraft mc) {
        Vec3[] at = {Vec3.ZERO};
        steps.run("a mannequin in the Vanguard, 6 blocks off", () -> {
                    at[0] = mc.player.position().add(-6.0, 0, 0);
                    mannequin = Mannequin.spawn(mc.level, at[0], 0f, new ItemStack(ModItems.MERIDIAN.get()));
                    for (var piece : GearRegistry.VANGUARD.all()) {
                        mannequin.body().setItemSlot(piece.get().getEquipmentSlot(), new ItemStack(piece.get()));
                    }
                    camera = DevCamera.create(mc.level);
                    camera.use();
                    mc.options.hideGui = true;
                })
                .run("front", () -> camera.place(at[0].add(0, 1.25, 3.1), at[0].add(0, 1.0, 0)))
                .waitTicks(4)
                .screenshot("armor_front")
                .run("side", () -> camera.place(at[0].add(3.1, 1.25, 0), at[0].add(0, 1.0, 0)))
                .waitTicks(4)
                .screenshot("armor_side")
                .run("back", () -> camera.place(at[0].add(0, 1.25, -3.1), at[0].add(0, 1.0, 0)))
                .waitTicks(4)
                .screenshot("armor_back")
                .run("three quarters", () -> camera.place(at[0].add(-2.3, 1.6, 2.3), at[0].add(0, 1.0, 0)))
                .waitTicks(4)
                .screenshot("armor_34")
                .run("20 Heat on the mannequin", () -> mannequin.body().setData(GearRegistry.SET_STATE,
                        SetState.NONE.withMeter(20f, mc.level.getGameTime() + 10_000)))
                .waitTicks(3)
                .screenshot("armor_34_heat")
                .run("the mannequin runs across the view", () -> {
                    mannequin.body().setData(GearRegistry.SET_STATE, SetState.NONE);
                    mannequin.moveTo(at[0].add(0, 0, -3.0));
                    mannequin.walk(new Vec3(0, 0, 0.28));
                    camera.place(at[0].add(3.4, 1.3, 0), at[0].add(0, 1.0, 0));
                })
                .waitTicks(11)
                .screenshot("armor_running_side")
                .run("stop and clean up", () -> {
                    mannequin.remove();
                    mannequin = null;
                    dropCamera();
                });
    }

    // ------------------------------------------------------------------ looks only

    /** The Forge at each tier, the armor worn and on a mannequin, and the meteor seen from the side. */
    private void reviewLooks(Steps steps, Minecraft mc) {
        steps.run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .run("remember where the Forge goes", () -> forgePos = BlockPos.containing(mc.player.position()).offset(0, 0, 3));
        for (int tier = 1; tier <= 4; tier++) {
            int t = tier;
            steps.command("setblock ~ ~ ~3 cosmicbreach:astral_forge[tier=" + tier + ",facing=north]")
                    .waitUntil("the Forge is tier " + tier, 40, () -> forgeTier(mc) == t)
                    .run("a camera on the Forge from its far side, the HUD hidden", () -> forgeCamera(mc))
                    .waitTicks(t == 1 ? 10 : 3)
                    .screenshot("look_forge_tier" + tier)
                    .run("back to the player's eyes", this::dropCamera);
        }
        steps.command("setblock ~ ~ ~3 minecraft:air")
                .command("give @s cosmicbreach:meridian[cosmicbreach:gear_tier=2]")
                .command("item replace entity @s armor.head with cosmicbreach:starfall_vanguard_helm")
                .command("item replace entity @s armor.chest with cosmicbreach:starfall_vanguard_chestplate")
                .command("item replace entity @s armor.legs with cosmicbreach:starfall_vanguard_greaves")
                .command("item replace entity @s armor.feet with cosmicbreach:starfall_vanguard_boots")
                .waitUntil("the Vanguard is worn", 60, () -> ArmorSets.pieces(mc.player, GearRegistry.VANGUARD.set()) == 4
                        && count(mc, ModItems.MERIDIAN.get()) == 1)
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()));
        looks(steps, mc);
        steps.look(180, 8)
                .waitTicks(3)
                .run("a dummy 8 blocks ahead", () -> spawnAhead(8.0))
                .waitUntil("the dummy stands there", 40, () -> dummyCount(mc) == 1)
                .run("look at the ground under it", () -> lookAt(mc, mc.player.position().add(
                        HitShape.forward(mc.player.getYRot()).scale(8.0))))
                .waitTicks(5)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .press(GearClient.SET_ABILITY)
                .waitUntil("the meteor is called", 20, () -> ServerQuery.ask(p -> MeteorCall.pendingCount()) > 0)
                .waitTicks(8)
                .screenshot("look_meteor_mark_fp")
                .run("a camera off to the side", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 me = mc.player.position();
                    Vec3 spot = me.add(HitShape.forward(mc.player.getYRot()).scale(8.0));
                    camera.place(me.add(9.0, 2.0, 0).add(HitShape.forward(mc.player.getYRot()).scale(2.0)), spot.add(0, 6.0, 0));
                    camera.use();
                    mc.options.hideGui = true;
                })
                .waitTicks(8)
                .screenshot("look_meteor_falling_early")
                .waitTicks(7)
                .screenshot("look_meteor_falling_late")
                .waitUntil("the meteor landed", 30, () -> ServerQuery.ask(p -> MeteorCall.pendingCount()) == 0)
                .waitTicks(2)
                .screenshot("look_meteor_impact")
                .waitTicks(25)
                .screenshot("look_meteor_crater")
                .run("back to the player's eyes", this::dropCamera)
                .run("clear the dummies", ForgeScenario::discardDummies);
    }

    // ------------------------------------------------------------------ helpers

    /** Gives each "id count" (the cosmicbreach namespace unless one is named) and waits until the inventory has them. */
    private void give(Steps steps, String... items) {
        for (String item : items) {
            String id = item.contains(":") ? item : "cosmicbreach:" + item;
            steps.command("give @s " + id);
        }
        steps.waitUntil("the inventory holds what was given", 80, () -> {
            for (String item : items) {
                String[] parts = (item.contains(":") ? item : "cosmicbreach:" + item).split(" ");
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0]));
                if (count(mc(), it) < Integer.parseInt(parts[1])) {
                    return false;
                }
            }
            return true;
        });
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private void openForge(Steps steps, Minecraft mc) {
        steps.run("select a harmless hotbar slot (no weapon, relic or armor)", () -> selectHarmless(mc))
                .waitTicks(2)
                .run("look at the Forge", () -> lookAt(mc, Vec3.atCenterOf(forgePos).add(0, 0.3, 0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the Forge's screen opened", 40, () -> mc.screen instanceof AstralForgeScreen)
                .waitTicks(2);
    }

    private void dropCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        Minecraft.getInstance().options.hideGui = false;
    }

    /** A camera south-east of the Forge (the player stands north of it), looking at its anvil, with the HUD off. */
    private void forgeCamera(Minecraft mc) {
        camera = DevCamera.create(mc.level);
        Vec3 c = Vec3.atCenterOf(forgePos);
        camera.place(c.add(1.9, 1.1, 2.2), c.add(0, 0.25, 0));
        camera.use();
        mc.options.hideGui = true;
    }

    private int forgeTier(Minecraft mc) {
        var state = mc.level.getBlockState(forgePos);
        return state.is(GearRegistry.ASTRAL_FORGE.get()) ? state.getValue(AstralForgeBlock.TIER) : 0;
    }

    private static AstralForgeScreen screen(Minecraft mc) {
        if (!(mc.screen instanceof AstralForgeScreen screen)) {
            throw new Steps.Failure("the Forge's screen is not open");
        }
        return screen;
    }

    private static AstralForgeMenu menu(Minecraft mc) {
        return screen(mc).getMenu();
    }

    private static void clickCell(Minecraft mc, Item result) {
        int index = screen(mc).indexOf(result);
        if (index < 0) {
            throw new Steps.Failure("no Forge recipe makes " + BuiltInRegistries.ITEM.getKey(result));
        }
        click(mc, screen(mc).cellCenter(index));
        if (screen(mc).selectedIndex() != index) {
            throw new Steps.Failure("the click didn't select " + BuiltInRegistries.ITEM.getKey(result));
        }
    }

    private static void click(Minecraft mc, double[] at) {
        mc.screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mc.screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static int count(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static ItemStack find(Minecraft mc, Item item) {
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(item)) {
                return stack;
            }
        }
        throw new Steps.Failure("no " + BuiltInRegistries.ITEM.getKey(item) + " in the inventory");
    }

    /** The menu slot index of {@code item} in the player's inventory part of the open menu. */
    private static int menuSlotOf(Minecraft mc, Item item) {
        var slots = mc.player.containerMenu.slots;
        for (int i = 1; i < slots.size(); i++) {
            if (slots.get(i).getItem().is(item)) {
                return i;
            }
        }
        throw new Steps.Failure("no " + BuiltInRegistries.ITEM.getKey(item) + " in the menu");
    }

    /** Clicks the hotbar key of the slot holding {@code item} (moving it into the hotbar first if needed). */
    private static void selectHotbar(Minecraft mc, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
                return;
            }
        }
        throw new Steps.Failure(BuiltInRegistries.ITEM.getKey(item) + " is not in the hotbar");
    }

    /** An empty hotbar slot, else one whose item neither casts, raises the Forge nor gets worn on use. */
    private static void selectHarmless(Minecraft mc) {
        int pick = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                pick = slot;
                break;
            }
            boolean risky = stack.getItem() instanceof CombatWeaponItem || stack.getItem() instanceof ArmorItem
                    || ForgeTiers.relicTier(BuiltInRegistries.ITEM.getKey(stack.getItem())) > 0;
            if (!risky && pick < 0) {
                pick = slot;
            }
        }
        if (pick < 0) {
            throw new Steps.Failure("no harmless hotbar slot");
        }
        KeyMapping.click(mc.options.keyHotbarSlots[pick].getKey());
    }

    /** Turns the player to look at {@code target}, as the harness's look step does. */
    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        float pitch = Mth.clamp((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG), -90f, 90f);
        var p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static void spawnAhead(double blocks) {
        ServerQuery.ask(p -> {
            MaulScenario.spawnDummy(p.serverLevel(), p.position().add(HitShape.forward(p.getYRot()).scale(blocks)), DUMMY);
            return true;
        });
    }

    private static List<Zombie> dummies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(24.0), z -> z.getTags().contains(DUMMY));
    }

    private static int dummyCount(Minecraft mc) {
        int server = ServerQuery.ask(p -> dummies(p).size());
        int client = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(24.0)).size();
        return server == client ? server : -1;
    }

    private static void discardDummies() {
        ServerQuery.ask(p -> {
            p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(48.0), e -> e.getTags().contains(DUMMY))
                    .forEach(Entity::discard);
            return true;
        });
    }

}
