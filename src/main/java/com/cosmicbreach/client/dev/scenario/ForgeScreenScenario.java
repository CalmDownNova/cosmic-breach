package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevMouse;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gear.AstralForgeScreen;
import com.cosmicbreach.client.gear.ForgeLayout;
import com.cosmicbreach.client.gear.TextFit;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.forge.AstralForgeBlock;
import com.cosmicbreach.gear.forge.ForgeCategory;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.gear.forge.ForgeRecipes;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The Forge screen of 1.1 (design section 10), through its real inputs: clicks on tabs, cells and buttons, and the
 * cursor placed where the game reads it ({@link DevMouse}) for hovering.
 * <ol>
 *   <li>Tabs: exactly the categories that have recipes, in order, each listing only its own (a screenshot each).</li>
 *   <li>Nothing cut short: every recipe chosen in turn at GUI scales 1, 2 and 3, and the reforge row in its states.</li>
 *   <li>The longest name chosen, whole on two lines at full size, captured at GUI scales 2 and 3.</li>
 *   <li>Hovering a recipe, the result, an ingredient, a tab, the reforge preview and its cost draws the full tooltip
 *       (the item's inventory lines first), captured at GUI scales 2 and 3.</li>
 *   <li>The last tab comes back after closing, and reforging works through the moved slot and button.</li>
 *   <li>The empty parts of the tab strip count as outside the screen: with an item on the cursor, a click there throws
 *       it, while a click inside the panel on nothing leaves it.</li>
 * </ol>
 * The tabs are also compared with what the server filed each recipe under, so a sync that moved a recipe to another
 * tab is caught.
 * GUI scale 4 can't be chosen in the 1280 by 720 test window (Minecraft caps the option at 3 there); the layout unit
 * test covers every scale by checking the screen fits 320 by 240.
 *
 * <p>Every check that reads what the last frame drew waits two ticks after the change, so a frame has been drawn.
 */
public final class ForgeScreenScenario implements Scenario {
    private BlockPos forgePos = BlockPos.ZERO;

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        List<RecipeHolder<ForgeRecipe>> recipes = ForgeRecipes.sorted(mc.level.getRecipeManager());
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("clear @s")
                .run("remember where the Forge goes", () -> forgePos = BlockPos.containing(mc.player.position()).offset(0, 0, 2))
                .command("setblock ~ ~ ~2 cosmicbreach:astral_forge[tier=1,facing=north]")
                .waitUntil("a tier I Forge stands there", 60, () -> forgeTier(mc) == 1)
                .command("give @s cosmicbreach:meridian")
                .command("give @s cosmicbreach:meridian[cosmicbreach:gear_tier=4]")
                .command("give @s cosmicbreach:starsteel_ingot 9")
                .command("give @s cosmicbreach:nebulite_ingot 4")
                .waitUntil("the inventory holds them", 80, () -> count(mc, ModItems.MERIDIAN.get()) == 2
                        && count(mc, ModMaterials.STARSTEEL_INGOT.get()) == 9 && count(mc, ModMaterials.NEBULITE_INGOT.get()) == 4);
        open(steps, mc);
        tabs(steps, mc, recipes);
        nothingCutShort(steps, mc, recipes);
        longestName(steps, mc, recipes);
        hovers(steps, mc);
        memoryAndReforge(steps, mc);
        emptyStrip(steps, mc);
        steps.run("GUI scale back to auto, cursor parked", () -> {
            mc.options.guiScale().set(0);
            DevMouse.park();
        });
    }

    // ------------------------------------------------------------------ 1. tabs

    private void tabs(Steps steps, Minecraft mc, List<RecipeHolder<ForgeRecipe>> recipes) {
        List<ForgeCategory> expected = new ArrayList<>();
        for (ForgeCategory category : ForgeCategory.values()) {
            if (recipes.stream().anyMatch(h -> h.value().category() == category)) {
                expected.add(category);
            }
        }
        steps.check("every recipe is in the tab the server filed it under", () -> sameCategoriesAsTheServer(recipes))
                .check("the tabs are the categories that have recipes, in order " + expected,
                        () -> screen(mc).shownTabs().equals(expected));
        for (ForgeCategory category : expected) {
            String name = category.getSerializedName();
            long count = recipes.stream().filter(h -> h.value().category() == category).count();
            steps.run("click the " + name + " tab", () -> click(mc, screen(mc).tabCenter(category)))
                    .waitTicks(2)
                    .check(name + " is open", () -> screen(mc).currentTab() == category)
                    .check(name + " lists its " + count + " recipes and nothing else", () -> {
                        List<Integer> shown = screen(mc).shownRecipes();
                        return shown.size() == count && shown.stream().allMatch(i -> screen(mc).categoryOf(i) == category);
                    })
                    .screenshot("tab_" + name);
        }
    }

    // ------------------------------------------------------------------ 2. nothing cut short

    private void nothingCutShort(Steps steps, Minecraft mc, List<RecipeHolder<ForgeRecipe>> recipes) {
        for (int scale = 1; scale <= 3; scale++) {
            int s = scale;
            guiScale(steps, mc, s);
            for (int i = 0; i < recipes.size(); i++) {
                int index = i;
                String id = recipes.get(i).id().getPath();
                steps.run("choose " + id, () -> screen(mc).select(index))
                        .waitTicks(2)
                        .check("nothing cut short with " + id + " chosen at GUI " + s, () -> wholeText(mc, id + " at GUI " + s));
            }
            steps.run("shift-click the tier I Meridian into the reforge slot", () -> quickMove(mc, slotWhere(mc, meridian(1))))
                    .waitUntil("it waits there", 40, () -> meridian(1).test(piece(mc)))
                    .waitTicks(2)
                    .check("the reforge row at Forge I fits at GUI " + s, () -> wholeText(mc, "reforge row, needs Forge II, GUI " + s))
                    .run("shift-click it back", () -> quickMove(mc, 0))
                    .waitUntil("the slot is empty", 40, () -> piece(mc).isEmpty())
                    .run("shift-click the tier IV Meridian in", () -> quickMove(mc, slotWhere(mc, meridian(4))))
                    .waitUntil("it waits there", 40, () -> meridian(4).test(piece(mc)))
                    .waitTicks(2)
                    .check("the fully reforged row fits at GUI " + s, () -> wholeText(mc, "reforge row, fully reforged, GUI " + s))
                    .run("shift-click it back", () -> quickMove(mc, 0))
                    .waitUntil("the slot is empty", 40, () -> piece(mc).isEmpty());
        }
    }

    // ------------------------------------------------------------------ 3. the longest name

    private void longestName(Steps steps, Minecraft mc, List<RecipeHolder<ForgeRecipe>> recipes) {
        int[] longest = {-1};
        steps.run("find the recipe with the longest name", () -> {
            int widest = -1;
            for (int i = 0; i < recipes.size(); i++) {
                int width = mc.font.width(name(recipes, i));
                if (width > widest) {
                    widest = width;
                    longest[0] = i;
                }
            }
        }).log("the longest name", () -> name(recipes, longest[0]) + ", " + mc.font.width(name(recipes, longest[0])) + " px");
        for (int scale : new int[] {2, 3}) {
            guiScale(steps, mc, scale);
            steps.run("click its tab", () -> click(mc, screen(mc).tabCenter(screen(mc).categoryOf(longest[0]))))
                    .waitTicks(2)
                    .run("click its cell", () -> click(mc, cell(mc, longest[0])))
                    .waitTicks(2)
                    .check("it is chosen", () -> screen(mc).selectedIndex() == longest[0])
                    .check("its whole name shows on at most two lines at full size", () -> {
                        TextFit.Fit fit = screen(mc).lastNameFit();
                        return fit != null && !fit.shortened() && fit.scale() == 1.0f && fit.lines().size() <= 2
                                && String.join(" ", fit.lines()).equals(name(recipes, longest[0]));
                    })
                    .check("nothing cut short", () -> wholeText(mc, "the longest name at GUI " + scale))
                    .screenshot("gui" + scale + "_long_name");
        }
    }

    // ------------------------------------------------------------------ 4. hovering

    private void hovers(Steps steps, Minecraft mc) {
        String preview = Component.translatable("gui.cosmicbreach.forge.preview").getString();
        String weapons = Component.translatable(ForgeCategory.WEAPONS.translationKey()).getString();
        for (int scale : new int[] {2, 3}) {
            String gui = "gui" + scale;
            guiScale(steps, mc, scale);
            steps.run("click the weapons tab", () -> click(mc, screen(mc).tabCenter(ForgeCategory.WEAPONS)))
                    .waitTicks(2)
                    .run("the cursor on Meridian's cell", () -> hover(cell(mc, meridianIndex(mc))))
                    .waitTicks(2)
                    .check("Meridian's own tooltip, tier and damage lines included", () ->
                            tooltipStartsWith(mc, new ItemStack(ModItems.MERIDIAN.get()), "Tier I", "Base damage"))
                    .screenshot(gui + "_hover_recipe")
                    .run("choose Meridian", () -> click(mc, cell(mc, meridianIndex(mc))))
                    .run("the cursor on the chosen result", () -> hover(centre(mc, ForgeLayout.RESULT)))
                    .waitTicks(2)
                    .check("the result shows the same tooltip", () ->
                            tooltipStartsWith(mc, new ItemStack(ModItems.MERIDIAN.get()), "Tier I", "Base damage"))
                    .screenshot(gui + "_hover_result")
                    .run("the cursor on the first ingredient", () -> hover(centre(mc, ForgeLayout.ingredient(0))))
                    .waitTicks(2)
                    .check("the ingredient shows its own tooltip", () ->
                            tooltipStartsWith(mc, new ItemStack(ModMaterials.STARSTEEL_INGOT.get())))
                    .run("the cursor on the weapons tab", () -> hover(screen(mc).tabCenter(ForgeCategory.WEAPONS)))
                    .waitTicks(2)
                    .check("the tab shows its name", () -> lines(mc).equals(List.of(weapons)))
                    .screenshot(gui + "_hover_tab")
                    .run("shift-click the tier I Meridian into the reforge slot", () -> quickMove(mc, slotWhere(mc, meridian(1))))
                    .waitUntil("it waits there", 40, () -> meridian(1).test(piece(mc)))
                    .run("the cursor on the upgrade preview", () -> hover(centre(mc, ForgeLayout.PREVIEW)))
                    .waitTicks(2)
                    .check("the preview is Meridian at tier II, base damage 5.8", () -> {
                        ItemStack next = new ItemStack(ModItems.MERIDIAN.get());
                        GearTier.set(next, 2);
                        return tooltipStartsWith(mc, next, "Tier II", "5.8", preview);
                    })
                    .screenshot(gui + "_hover_preview")
                    .run("the cursor on the reforge cost", () -> hover(centre(mc, ForgeLayout.COST)))
                    .waitTicks(2)
                    .check("the cost shows the metal's own tooltip", () ->
                            tooltipStartsWith(mc, new ItemStack(ModMaterials.NEBULITE_INGOT.get())))
                    .run("shift-click it back", () -> quickMove(mc, 0))
                    .waitUntil("the slot is empty", 40, () -> piece(mc).isEmpty())
                    .run("park the cursor", DevMouse::park);
        }
    }

    // ------------------------------------------------------------------ 5. memory and reforging

    private void memoryAndReforge(Steps steps, Minecraft mc) {
        guiScale(steps, mc, 3);
        steps.run("click the armor tab", () -> click(mc, screen(mc).tabCenter(ForgeCategory.ARMOR)))
                .waitTicks(1)
                .run("close the Forge", () -> mc.player.closeContainer())
                .waitUntil("it closed", 20, () -> mc.screen == null)
                .command("setblock ~ ~ ~2 cosmicbreach:astral_forge[tier=2,facing=north]")
                .waitUntil("the Forge is tier II", 60, () -> forgeTier(mc) == 2);
        open(steps, mc);
        steps.check("the armor tab came back", () -> screen(mc).currentTab() == ForgeCategory.ARMOR)
                .run("shift-click the tier I Meridian into the reforge slot", () -> quickMove(mc, slotWhere(mc, meridian(1))))
                .waitUntil("it waits there", 40, () -> meridian(1).test(piece(mc)))
                .waitTicks(2)
                .check("the ready reforge row fits", () -> wholeText(mc, "reforge row, ready"))
                .screenshot("gui3_reforge_ready")
                .run("click Reforge", () -> click(mc, screen(mc).reforgeButtonCenter()))
                .waitUntil("Meridian is tier II", 40, () -> meridian(2).test(piece(mc)))
                .waitUntil("the 4 Nebulite Ingots were used", 20, () -> count(mc, ModMaterials.NEBULITE_INGOT.get()) == 0)
                .run("close the Forge", () -> mc.player.closeContainer())
                .waitUntil("it closed", 20, () -> mc.screen == null);
    }

    // ------------------------------------------------------------------ 6. the empty parts of the tab strip

    private void emptyStrip(Steps steps, Minecraft mc) {
        Predicate<ItemStack> ingots = stack -> stack.is(ModMaterials.STARSTEEL_INGOT.get());
        // closing the Forge hands the reforged Meridian back a moment later, into the first empty slot: a hotbar slot
        // picked as harmless before that could be the one it lands in, and a weapon in hand takes the use key
        steps.waitUntil("the reforged Meridian is back in the inventory", 40, () -> count(mc, ModItems.MERIDIAN.get()) == 2);
        open(steps, mc);
        steps.run("pick the Starsteel Ingots up onto the cursor", () -> mc.gameMode.handleInventoryMouseClick(
                        mc.player.containerMenu.containerId, slotWhere(mc, ingots), 0, ClickType.PICKUP, mc.player))
                .waitUntil("they are on the cursor", 40, () -> ingots.test(mc.player.containerMenu.getCarried()))
                .run("click the panel's title, inside the screen and on nothing", () -> click(mc, centre(mc, ForgeLayout.TITLE)))
                .waitTicks(3)
                .check("a click inside the screen on nothing leaves them on the cursor", () -> ingots.test(mc.player.containerMenu.getCarried()))
                .run("click the strip's empty part below the last tab", () -> click(mc, stripBelowTheTabs(mc)))
                .waitUntil("they were thrown from the cursor", 40, () -> mc.player.containerMenu.getCarried().isEmpty())
                .waitUntil("they lie in the world", 40, () -> !mc.level.getEntitiesOfClass(ItemEntity.class,
                        mc.player.getBoundingBox().inflate(8.0), e -> ingots.test(e.getItem())).isEmpty())
                .run("close the Forge", () -> mc.player.closeContainer())
                .waitUntil("it closed", 20, () -> mc.screen == null);
    }

    /** A point on the strip below the last shown tab, where nothing is drawn. */
    private static double[] stripBelowTheTabs(Minecraft mc) {
        AstralForgeScreen screen = screen(mc);
        ForgeLayout.Rect next = ForgeLayout.tab(screen.shownTabs().size());
        return new double[] {screen.getGuiLeft() + next.centerX(), screen.getGuiTop() + next.y() + 4};
    }

    // ------------------------------------------------------------------ helpers

    /** The middle of a layout box on the open screen, in GUI coordinates. */
    private static double[] centre(Minecraft mc, ForgeLayout.Rect box) {
        AstralForgeScreen screen = screen(mc);
        return new double[] {screen.getGuiLeft() + box.centerX(), screen.getGuiTop() + box.centerY()};
    }

    /**
     * True if every recipe the client lists has the category the integrated server's own recipe list gives it (the
     * screen takes its tabs from the client's copy, which a sync bug could change without the data being wrong); fails
     * naming the differences otherwise.
     */
    private static boolean sameCategoriesAsTheServer(List<RecipeHolder<ForgeRecipe>> client) {
        Map<ResourceLocation, ForgeCategory> server = ServerQuery.ask(player -> {
            Map<ResourceLocation, ForgeCategory> filed = new HashMap<>();
            for (RecipeHolder<ForgeRecipe> holder : ForgeRecipes.sorted(player.server.getRecipeManager())) {
                filed.put(holder.id(), holder.value().category());
            }
            return filed;
        });
        List<String> differences = new ArrayList<>();
        for (RecipeHolder<ForgeRecipe> holder : client) {
            ForgeCategory there = server.get(holder.id());
            if (there != holder.value().category()) {
                differences.add(holder.id().getPath() + " is " + holder.value().category() + " here but " + there + " on the server");
            }
        }
        if (server.size() != client.size()) {
            differences.add("the client lists " + client.size() + " recipes, the server " + server.size());
        }
        if (!differences.isEmpty()) {
            throw new Steps.Failure("the tabs differ from the server's: " + differences);
        }
        return true;
    }

    /** A harmless hand, a look at the Forge and the use key, as a player opens it; the chat is cleared for clean captures. */
    private void open(Steps steps, Minecraft mc) {
        steps.run("select a harmless hotbar slot (an empty one, else no weapon or armor)", () -> selectHarmless(mc))
                .waitTicks(2)
                .run("look at the Forge", () -> lookAt(mc, Vec3.atCenterOf(forgePos).add(0, 0.3, 0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the Forge's screen opened", 40, () -> mc.screen instanceof AstralForgeScreen)
                .run("clear the chat and park the cursor", () -> {
                    mc.gui.getChat().clearMessages(false);
                    DevMouse.park();
                })
                .waitTicks(2);
    }

    /**
     * Selects the first empty hotbar slot, else one holding neither a weapon nor armor: with a weapon in hand the combat
     * system takes the use key, and a piece shift-clicked back out of the reforge slot lands in the last hotbar slot.
     */
    private static void selectHarmless(Minecraft mc) {
        int pick = -1;
        for (int slot = 0; slot < 9 && pick < 0; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) {
                pick = slot;
            }
        }
        for (int slot = 0; slot < 9 && pick < 0; slot++) {
            Item item = mc.player.getInventory().getItem(slot).getItem();
            if (!(item instanceof CombatWeaponItem) && !(item instanceof ArmorItem)) {
                pick = slot;
            }
        }
        if (pick < 0) {
            throw new Steps.Failure("no harmless hotbar slot");
        }
        KeyMapping.click(mc.options.keyHotbarSlots[pick].getKey());
    }

    private static void guiScale(Steps steps, Minecraft mc, int scale) {
        steps.run("GUI scale " + scale, () -> mc.options.guiScale().set(scale))
                .waitTicks(3)
                .check("the window is at GUI scale " + scale, () -> (int) mc.getWindow().getGuiScale() == scale)
                .run("park the cursor", DevMouse::park)
                .waitTicks(2);
    }

    /** True when the last frame cut nothing short; otherwise fails naming what was cut. */
    private static boolean wholeText(Minecraft mc, String what) {
        List<String> cut = screen(mc).shortenedTexts();
        if (!cut.isEmpty()) {
            throw new Steps.Failure(what + ": cut short " + cut);
        }
        return true;
    }

    /**
     * The last frame drew a tooltip that starts with {@code stack}'s inventory tooltip (the same lines, in order) and
     * holds each of {@code words}; otherwise fails showing both.
     */
    private static boolean tooltipStartsWith(Minecraft mc, ItemStack stack, String... words) {
        List<String> shown = lines(mc);
        List<String> own = Screen.getTooltipFromItem(mc, stack).stream().map(Component::getString).toList();
        if (shown.size() < own.size() || !shown.subList(0, own.size()).equals(own)) {
            throw new Steps.Failure("tooltip " + shown + " does not start with the inventory's " + own);
        }
        String all = String.join(" | ", shown);
        for (String word : words) {
            if (!all.contains(word)) {
                throw new Steps.Failure("tooltip " + shown + " lacks " + word);
            }
        }
        return true;
    }

    /** The lines of the tooltip the last frame drew; fails if there was none. */
    private static List<String> lines(Minecraft mc) {
        List<Component> tooltip = screen(mc).lastTooltip();
        if (tooltip == null) {
            throw new Steps.Failure("no tooltip was drawn");
        }
        return tooltip.stream().map(Component::getString).toList();
    }

    private static String name(List<RecipeHolder<ForgeRecipe>> recipes, int index) {
        return recipes.get(index).value().result().getHoverName().getString();
    }

    private static AstralForgeScreen screen(Minecraft mc) {
        if (!(mc.screen instanceof AstralForgeScreen screen)) {
            throw new Steps.Failure("the Forge's screen is not open");
        }
        return screen;
    }

    /** The middle of recipe {@code index}'s cell in the open tab; fails if the tab doesn't show it. */
    private static double[] cell(Minecraft mc, int index) {
        double[] at = screen(mc).visibleCellCenter(index);
        if (at == null) {
            throw new Steps.Failure("recipe " + index + " is not on the open tab's page");
        }
        return at;
    }

    private static ItemStack piece(Minecraft mc) {
        return screen(mc).getMenu().piece();
    }

    private static int meridianIndex(Minecraft mc) {
        return screen(mc).indexOf(ModItems.MERIDIAN.get());
    }

    private static Predicate<ItemStack> meridian(int tier) {
        return stack -> stack.is(ModItems.MERIDIAN.get()) && GearTier.of(stack, 1) == tier;
    }

    /** The open menu's first inventory slot holding a wanted stack (slot 0 is the reforge slot). */
    private static int slotWhere(Minecraft mc, Predicate<ItemStack> wanted) {
        var slots = mc.player.containerMenu.slots;
        for (int i = 1; i < slots.size(); i++) {
            if (wanted.test(slots.get(i).getItem())) {
                return i;
            }
        }
        throw new Steps.Failure("no such stack in the menu");
    }

    private static void quickMove(Minecraft mc, int slot) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static void click(Minecraft mc, double[] at) {
        mc.screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mc.screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void hover(double[] at) {
        DevMouse.moveTo(at[0], at[1]);
    }

    private int forgeTier(Minecraft mc) {
        var state = mc.level.getBlockState(forgePos);
        return state.is(GearRegistry.ASTRAL_FORGE.get()) ? state.getValue(AstralForgeBlock.TIER) : 0;
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
}
