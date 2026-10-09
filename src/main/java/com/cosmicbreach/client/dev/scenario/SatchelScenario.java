package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.satchel.SatchelClient;
import com.cosmicbreach.client.satchel.SatchelScreen;
import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.MountMenu;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.provision.ProvisionRegistry;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.satchel.SatchelContents;
import com.cosmicbreach.satchel.SatchelLocator;
import com.cosmicbreach.satchel.SatchelMenu;
import com.cosmicbreach.satchel.SatchelNet;
import com.cosmicbreach.satchel.Satchels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Lane A, the Satchel (1.2 design section 3), end to end on the integrated server:
 * <ul>
 *   <li>pickup: material items on the ground go into the satchel (not the inventory), gear and renamed items do not, the
 *       one worn in the back slot is the one in force, and a type switched off stays out;</li>
 *   <li>the screen: the open key opens it, a withdraw request moves exactly that many out;</li>
 *   <li>no duplicates: shift click, the throw and swap clicks on the locked slot, the mount menu and the gear slots, with
 *       every item counted before and after;</li>
 *   <li>the Forge: it crafts from the satchel's materials (loose items first), and loads a piece from the Gear tab;</li>
 *   <li>death: both satchels drop as one item each with their contents intact, and nothing else of theirs drops.</li>
 * </ul>
 */
public final class SatchelScenario implements Scenario {
    private static final ResourceLocation COBBLE = ResourceLocation.withDefaultNamespace("cobblestone");
    private static final ResourceLocation STARHIDE = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "starhide");
    private static final ResourceLocation INGOT = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "starsteel_ingot");
    private static final ResourceLocation BLOOM = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "starbloom");

    private final Minecraft mc = Minecraft.getInstance();
    private Map<String, Long> before;
    private BlockPos death;
    private SatchelContents wornAtDeath;
    private SatchelContents carriedAtDeath;

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    // ------------------------------------------------------------------ helpers (server thread)

    private static List<ItemStack> wornCurios(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(p).ifPresent(h -> {
            var all = h.getEquippedCurios();
            for (int i = 0; i < all.getSlots(); i++) {
                if (!all.getStackInSlot(i).isEmpty()) {
                    out.add(all.getStackInSlot(i));
                }
            }
        });
        return out;
    }

    private static void add(Map<String, Long> totals, ItemStack s) {
        if (s.isEmpty()) {
            return;
        }
        if (s.getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
            totals.merge("satchel", (long) s.getCount(), Long::sum);
            SatchelContents c = Satchels.contents(s);
            c.materials().forEach((id, n) -> totals.merge(id.toString(), (long) n, Long::sum));
            c.gear().forEach(g -> add(totals, g));
            return;
        }
        totals.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), (long) s.getCount(), Long::sum);
    }

    /** Every item the player has anywhere (inventory, worn, inside satchels, on the ground nearby, carried by the menu cursor). */
    private static Map<String, Long> totals(ServerPlayer p) {
        Map<String, Long> t = new TreeMap<>();
        p.getInventory().items.forEach(s -> add(t, s));
        p.getInventory().armor.forEach(s -> add(t, s));
        p.getInventory().offhand.forEach(s -> add(t, s));
        wornCurios(p).forEach(s -> add(t, s));
        add(t, p.containerMenu.getCarried());
        for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12))) {
            add(t, e.getItem());
        }
        return t;
    }

    private static long count(ServerPlayer p, Item item) {
        return totals(p).getOrDefault(BuiltInRegistries.ITEM.getKey(item).toString(), 0L);
    }

    private static long loose(ServerPlayer p, Item item) {
        long n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void wearSatchel(ServerPlayer p, ItemStack satchel) {
        CuriosApi.getCuriosInventory(p).orElseThrow().getCurios().get("back").getStacks().setStackInSlot(0, satchel);
    }

    private static ItemStack worn(ServerPlayer p) {
        for (ItemStack s : wornCurios(p)) {
            if (s.getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack carriedSatchel(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void drop(ServerPlayer p, String item, int count, String nbt) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)), count);
        if (nbt != null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(nbt));
        }
        ItemEntity e = new ItemEntity(p.serverLevel(), p.getX(), p.getY() + 0.2, p.getZ(), stack, 0, 0, 0);
        e.setPickUpDelay(0);
        p.serverLevel().addFreshEntity(e);
    }

    private static int slotOfInventory(SatchelMenu menu, ServerPlayer p, Item item) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container == p.getInventory() && menu.slots.get(i).getItem().is(item)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ steps

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode survival")
                .waitUntil("survival", 40, () -> !mc.player.isCreative())
                .command("gamerule keepInventory false")
                .command("gamerule doMobSpawning false")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("clear @s")
                .command("give @s cosmicbreach:satchel")
                .waitUntil("the satchel is in the inventory", 40, () -> ServerQuery.ask(p -> carriedSatchel(p).getItem() instanceof com.cosmicbreach.satchel.SatchelItem))

                // ---- pickup into the satchel in the inventory
                .run("drop materials, gear and a renamed block at the player's feet", () -> ServerQuery.ask(p -> {
                    drop(p, "minecraft:cobblestone", 30, null);
                    drop(p, "cosmicbreach:starsteel_ingot", 5, null);
                    drop(p, "minecraft:stick", 3, null);
                    drop(p, "minecraft:diamond_sword", 1, null);
                    drop(p, "minecraft:dirt", 7, "Keepsake");
                    return true;
                }))
                .waitUntil("the satchel holds the cobblestone, the ingots and the sticks", 100, () -> ServerQuery.ask(p -> {
                    SatchelContents c = Satchels.contents(carriedSatchel(p));
                    return c.count(COBBLE) == 30 && c.count(INGOT) == 5 && c.count(ResourceLocation.withDefaultNamespace("stick")) == 3;
                }))
                .check("none of it is loose in the inventory", () -> ServerQuery.ask(p -> loose(p, Items.COBBLESTONE) == 0 && loose(p, Items.STICK) == 0))
                .check("the sword and the renamed block went to the inventory", () -> ServerQuery.ask(p -> loose(p, Items.DIAMOND_SWORD) == 1
                        && loose(p, Items.DIRT) == 7))
                .check("the satchel took no gear and no renamed item", () -> ServerQuery.ask(p -> {
                    SatchelContents c = Satchels.contents(carriedSatchel(p));
                    return c.gear().stream().allMatch(ItemStack::isEmpty) && c.count(ResourceLocation.withDefaultNamespace("dirt")) == 0
                            && c.count(ResourceLocation.withDefaultNamespace("diamond_sword")) == 0;
                }))

                // ---- the worn satchel is the one in force
                .run("a second satchel in the back slot", () -> ServerQuery.ask(p -> {
                    wearSatchel(p, new ItemStack(Satchels.SATCHEL.get()));
                    return true;
                }))
                .run("drop more cobblestone", () -> ServerQuery.ask(p -> {
                    drop(p, "minecraft:cobblestone", 10, null);
                    return true;
                }))
                .waitUntil("the worn satchel took it", 100, () -> ServerQuery.ask(p -> Satchels.contents(worn(p)).count(COBBLE) == 10))
                .check("the carried one is unchanged", () -> ServerQuery.ask(p -> Satchels.contents(carriedSatchel(p)).count(COBBLE) == 30))

                // ---- the screen: key opens it, a switch stops pickup, a withdraw moves exactly that many
                .press(SatchelClient.KEY)
                .waitUntil("the screen opened", 60, () -> mc.screen instanceof SatchelScreen)
                .run("switch cobblestone pickup off and withdraw 64 of it", () -> {
                    int id = mc.player.containerMenu.containerId;
                    PacketDistributor.sendToServer(new SatchelNet.SatchelActionPayload(id, SatchelMenu.TOGGLE, COBBLE, 0));
                    PacketDistributor.sendToServer(new SatchelNet.SatchelActionPayload(id, SatchelMenu.WITHDRAW, COBBLE, 64));
                })
                .waitUntil("ten came out of the worn satchel (all it had)", 60, () -> ServerQuery.ask(p -> loose(p, Items.COBBLESTONE) == 10
                        && Satchels.contents(worn(p)).count(COBBLE) == 0 && !Satchels.contents(worn(p)).pickupOn(COBBLE)))
                .run("drop cobblestone again", () -> ServerQuery.ask(p -> {
                    drop(p, "minecraft:cobblestone", 4, null);
                    return true;
                }))
                .waitUntil("it went to the inventory, the satchel stayed empty of it", 100, () -> ServerQuery.ask(p -> loose(p, Items.COBBLESTONE) == 14
                        && Satchels.contents(worn(p)).count(COBBLE) == 0))
                .run("close the screen", () -> mc.setScreen(null))
                .waitTicks(5)

                // ---- no duplicates through the menu
                .run("sand in the inventory, an iron sword too; open the carried satchel", () -> ServerQuery.ask(p -> {
                    p.getInventory().add(new ItemStack(Items.SAND, 100));
                    p.getInventory().add(new ItemStack(Items.IRON_SWORD));
                    before = totals(p);
                    int at = -1;
                    for (int i = 0; i < p.getInventory().items.size(); i++) {
                        if (p.getInventory().items.get(i).getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                            at = i;
                        }
                    }
                    SatchelMenu.open(p, new SatchelLocator.Ref(false, at)); // the carried one, so its own slot is the locked one
                    return true;
                }))
                .waitUntil("the server menu is open", 40, () -> ServerQuery.ask(p -> p.containerMenu instanceof SatchelMenu))
                .run("shift click the sand, the sword and the cobblestone", () -> ServerQuery.ask(p -> {
                    SatchelMenu menu = (SatchelMenu) p.containerMenu;
                    for (Item item : new Item[] {Items.SAND, Items.IRON_SWORD, Items.COBBLESTONE}) {
                        for (int guard = 0; guard < 10; guard++) {
                            int slot = slotOfInventory(menu, p, item);
                            if (slot < 0) {
                                break;
                            }
                            menu.clicked(slot, 0, ClickType.QUICK_MOVE, p);
                        }
                    }
                    return true;
                }))
                .check("the sand went into the satchel, the sword into its Gear slot, nothing was lost or made", () -> ServerQuery.ask(p -> {
                    SatchelContents c = Satchels.contents(SatchelLocator.get(p, ((SatchelMenu) p.containerMenu).ref()));
                    return c.count(ResourceLocation.withDefaultNamespace("sand")) == 100
                            && c.gear().stream().anyMatch(s -> s.is(Items.IRON_SWORD))
                            && loose(p, Items.SAND) == 0 && loose(p, Items.IRON_SWORD) == 0 && totals(p).equals(before);
                }))
                .run("throw and swap clicks on the locked slot, the throw of a gear slot, a swap with the hotbar", () -> ServerQuery.ask(p -> {
                    SatchelMenu menu = (SatchelMenu) p.containerMenu;
                    int locked = -1;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        if (menu.slots.get(i).container == p.getInventory() && menu.slots.get(i).getItem().getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                            locked = i;
                        }
                    }
                    menu.clicked(locked, 1, ClickType.THROW, p);
                    menu.clicked(locked, 0, ClickType.QUICK_MOVE, p);
                    menu.clicked(locked, 3, ClickType.SWAP, p);
                    menu.clicked(locked, 0, ClickType.PICKUP, p);
                    return true;
                }))
                .check("the open satchel could not be picked up, thrown or swapped; still no change in any total", () -> ServerQuery.ask(p ->
                        carriedSatchel(p).getItem() instanceof com.cosmicbreach.satchel.SatchelItem
                                && p.containerMenu.getCarried().isEmpty() && totals(p).equals(before)))
                .run("take the sword back out with a shift click", () -> ServerQuery.ask(p -> {
                    p.containerMenu.clicked(slotGear(p), 0, ClickType.QUICK_MOVE, p);
                    return true;
                }))
                .check("it is in the inventory again, once", () -> ServerQuery.ask(p -> loose(p, Items.IRON_SWORD) == 1 && totals(p).equals(before)))
                .run("close", () -> ServerQuery.ask(p -> {
                    p.closeContainer();
                    return true;
                }))

                // ---- mount gear: the satchel does not go into a mount's slots
                .run("a tamed stag and its menu", () -> ServerQuery.ask(p -> {
                    LumenStag stag = Mounts.LUMEN_STAG.get().create(p.serverLevel());
                    stag.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0f, 0f);
                    stag.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(stag.blockPosition()), MobSpawnType.COMMAND, null);
                    stag.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(stag);
                    stag.tameTo(p);
                    MountMenu menu = new MountMenu(99, p.getInventory(), stag);
                    ItemStack satchel = new ItemStack(Satchels.SATCHEL.get());
                    boolean any = false;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        if (menu.slots.get(i).container != p.getInventory() && menu.slots.get(i).mayPlace(satchel)) {
                            any = true;
                        }
                    }
                    stag.discard();
                    if (any) {
                        throw new Steps.Failure("a mount slot takes a satchel");
                    }
                    return true;
                }))

                // ---- the mount crystal: stowing and setting down a mount changes no satchel and makes or loses no item
                .run("stow a tamed stag in a Stable Crystal and set it down again, satchels in the pack", () -> ServerQuery.ask(p -> {
                    Map<String, Long> was = totals(p);
                    SatchelContents wornWas = Satchels.contents(worn(p));
                    SatchelContents carriedWas = Satchels.contents(carriedSatchel(p));
                    LumenStag stag = Mounts.LUMEN_STAG.get().create(p.serverLevel());
                    stag.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0f, 0f);
                    stag.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(stag.blockPosition()), MobSpawnType.COMMAND, null);
                    stag.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(stag);
                    stag.tameTo(p);
                    for (int i = 0; i < 9; i++) {
                        if (p.getInventory().items.get(i).isEmpty()) {
                            p.getInventory().selected = i; // an empty hand slot, so no item is replaced
                            break;
                        }
                    }
                    ItemStack crystal = new ItemStack(com.cosmicbreach.mount.Stable.STABLE_CRYSTAL.get());
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crystal);
                    crystal.getItem().interactLivingEntity(crystal, p, stag, net.minecraft.world.InteractionHand.MAIN_HAND);
                    if (com.cosmicbreach.mount.StableCrystalItem.stowed(p.getMainHandItem()) == null) {
                        throw new Steps.Failure("the stag was not stowed");
                    }
                    if (!totals(p).equals(was) && !totals(p).entrySet().containsAll(was.entrySet())) {
                        throw new Steps.Failure("stowing changed the items: " + was + " then " + totals(p));
                    }
                    var hit = new net.minecraft.world.phys.BlockHitResult(p.position().add(3, -1, 0), net.minecraft.core.Direction.UP,
                            BlockPos.containing(p.getX() + 3, p.getY() - 1, p.getZ()), false);
                    p.getMainHandItem().getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p.serverLevel(), p,
                            net.minecraft.world.InteractionHand.MAIN_HAND, p.getMainHandItem(), hit));
                    if (com.cosmicbreach.mount.StableCrystalItem.stowed(p.getMainHandItem()) != null) {
                        throw new Steps.Failure("the stag was not set down");
                    }
                    p.getMainHandItem().shrink(1);
                    p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(14)).forEach(e -> e.discard());
                    if (!Satchels.contents(worn(p)).equals(wornWas) || !Satchels.contents(carriedSatchel(p)).equals(carriedWas)
                            || !totals(p).equals(was)) {
                        throw new Steps.Failure("a satchel or an item changed across stow and release: " + was + " then " + totals(p));
                    }
                    return true;
                }))

                // ---- the Forge: loose items first, then the satchel; a piece from the Gear tab
                .run("starhide, ingots and starbloom: 2 starhide loose, the rest in the worn satchel", () -> ServerQuery.ask(p -> {
                    p.getInventory().add(new ItemStack(ProvisionRegistry.STARHIDE.get(), 2));
                    ItemStack w = worn(p).copy();
                    SatchelContents c = Satchels.contents(w);
                    c = c.deposit(STARHIDE, 6).contents().deposit(INGOT, 4).contents().deposit(BLOOM, 2).contents();
                    Satchels.setContents(w, c);
                    wearSatchel(p, w);
                    before = totals(p);
                    return true;
                }))
                .run("craft the satchel at the Forge", () -> ServerQuery.ask(p -> {
                    AstralForgeMenu menu = new AstralForgeMenu(98, p.getInventory(), ContainerLevelAccess.create(p.serverLevel(), p.blockPosition()), p.blockPosition());
                    List<RecipeHolder<ForgeRecipe>> recipes = menu.recipes();
                    int index = -1;
                    for (int i = 0; i < recipes.size(); i++) {
                        if (recipes.get(i).id().getPath().equals("forge/satchel")) {
                            index = i;
                        }
                    }
                    if (index < 0 || !menu.clickMenuButton(p, index)) {
                        throw new Steps.Failure("the Forge would not craft the satchel from the satchel's materials (recipe " + index + ")");
                    }
                    return true;
                }))
                .check("it took the 2 loose starhide first, 2 more from the satchel, 2 ingots and 1 starbloom, and made one satchel", () -> ServerQuery.ask(p -> {
                    SatchelContents c = Satchels.contents(worn(p));
                    long satchels = totals(p).getOrDefault("satchel", 0L);
                    return loose(p, ProvisionRegistry.STARHIDE.get()) == 0 && c.count(STARHIDE) == 4 && c.count(INGOT) == 2
                            && c.count(BLOOM) == 1 && satchels == before.getOrDefault("satchel", 0L) + 1;
                }))
                .run("a Meridian in the Gear tab, loaded into the reforge slot", () -> ServerQuery.ask(p -> {
                    ItemStack w = worn(p).copy();
                    Satchels.setContents(w, Satchels.contents(w).withGear(0, new ItemStack(ModItems.MERIDIAN.get())));
                    wearSatchel(p, w);
                    before = totals(p);
                    AstralForgeMenu menu = new AstralForgeMenu(97, p.getInventory(), ContainerLevelAccess.create(p.serverLevel(), p.blockPosition()), p.blockPosition());
                    if (menu.satchelPicks(p).size() != 1 || !menu.clickMenuButton(p, AstralForgeMenu.PICK_GEAR)) {
                        throw new Steps.Failure("the Gear tab piece could not be loaded");
                    }
                    if (!menu.piece().is(ModItems.MERIDIAN.get()) || !Satchels.contents(worn(p)).gear().get(0).isEmpty()) {
                        throw new Steps.Failure("the piece is not in the slot and out of the satchel");
                    }
                    menu.removed(p); // the piece goes back to the inventory
                    return true;
                }))
                .check("the Meridian came out of the satchel once, nothing duplicated", () -> ServerQuery.ask(p -> loose(p, ModItems.MERIDIAN.get()) == 1
                        && count(p, ModItems.MERIDIAN.get()) == 1 && totals(p).equals(before)))

                // ---- death: each satchel drops as one item with its contents intact
                .run("record both satchels, then die", () -> ServerQuery.ask(p -> {
                    wornAtDeath = Satchels.contents(worn(p));
                    carriedAtDeath = Satchels.contents(carriedSatchel(p));
                    death = p.blockPosition();
                    before = totals(p);
                    p.kill();
                    return true;
                }))
                .waitUntil("the death screen", 80, () -> mc.screen instanceof DeathScreen)
                .check("all three satchels (worn, carried and the one just forged) lie on the ground as items with their contents intact, each once", () -> ServerQuery.ask(p -> {
                    List<SatchelContents> found = new ArrayList<>();
                    for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(death).inflate(14))) {
                        if (e.getItem().getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                            found.add(Satchels.contents(e.getItem()));
                        }
                    }
                    long nothingLooseOfTheirs = p.serverLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(death).inflate(14)).stream()
                            .filter(e -> e.getItem().is(ProvisionRegistry.STARHIDE.get()) || e.getItem().is(Items.SAND)).count();
                    return found.size() == 3 && found.contains(SatchelContents.EMPTY) && found.contains(wornAtDeath) && found.contains(carriedAtDeath) && nothingLooseOfTheirs == 0
                            && totals(p).equals(before);
                }))
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("alive again", 100, () -> mc.screen == null && mc.player != null && mc.player.isAlive())
                .run("walk back to the satchels", () -> ServerQuery.ask(p -> {
                    p.teleportTo(death.getX() + 0.5, death.getY(), death.getZ() + 0.5);
                    return true;
                }))
                .waitUntil("the three satchels are picked up again", 600, () -> ServerQuery.ask(p -> {
                    // the drops scatter a few blocks, so walk to the nearest item still on the ground
                    ItemEntity nearest = null;
                    for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(death).inflate(14))) {
                        if (nearest == null || e.distanceToSqr(p) < nearest.distanceToSqr(p)) {
                            nearest = e;
                        }
                    }
                    if (nearest != null && nearest.distanceToSqr(p) > 0.25) {
                        p.teleportTo(nearest.getX(), nearest.getY(), nearest.getZ());
                    }
                    int got = 0;
                    for (ItemStack s : p.getInventory().items) {
                        if (s.getItem() instanceof com.cosmicbreach.satchel.SatchelItem) {
                            got++;
                        }
                    }
                    return got == 3 && nearest == null;
                }))
                .waitTicks(40)
                .check("every item is back exactly once: the totals equal those at the moment of death", () -> ServerQuery.ask(p -> {
                    Map<String, Long> now = totals(p);
                    if (!now.equals(before)) {
                        throw new Steps.Failure("totals differ: before " + before + " now " + now);
                    }
                    return true;
                }))

                // ---- a partial pickup near the cap: the satchel takes what fits, the rest goes to the inventory, nothing is made
                .run("the satchel in force holds cobblestone 5 short of the cap; a stack of 64 is dropped", () -> ServerQuery.ask(p -> {
                    for (int i = 0; i < p.getInventory().items.size(); i++) {
                        if (p.getInventory().items.get(i).is(Items.COBBLESTONE)) {
                            p.getInventory().items.set(i, ItemStack.EMPTY);
                        }
                    }
                    for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(14))) {
                        if (e.getItem().is(Items.COBBLESTONE)) {
                            e.discard();
                        }
                    }
                    SatchelLocator.update(p, c -> {
                        SatchelContents t = c.pickupOn(COBBLE) ? c : c.togglePickup(COBBLE);
                        return t.withMaterials(withCount(t, COBBLE, SatchelContents.CAP - 5));
                    });
                    before = totals(p);
                    drop(p, "minecraft:cobblestone", 64, null);
                    return true;
                }))
                .waitTicks(40)
                .check("the satchel is full, the other 59 are in the inventory, and 64 more exist in all, not 123", () -> ServerQuery.ask(p -> {
                    long satchel = Satchels.contents(SatchelLocator.find(p).orElseThrow().stack()).count(COBBLE);
                    long inv = loose(p, Items.COBBLESTONE);
                    long all = totals(p).getOrDefault("minecraft:cobblestone", 0L);
                    long was = before.getOrDefault("minecraft:cobblestone", 0L);
                    if (satchel != SatchelContents.CAP || inv != 59 || all != was + 64) {
                        throw new Steps.Failure("satchel " + satchel + ", loose " + inv + ", all " + all + ", was " + was);
                    }
                    return true;
                }))
                .run("summary", () -> com.cosmicbreach.CosmicBreach.LOGGER.info("[autotest] satchel: pickup, key and screen, shift clicks, locked slot, forge and death all conserved every item"));
    }

    /** The materials of {@code c} with {@code id} set to {@code n}. */
    private static Map<ResourceLocation, Integer> withCount(SatchelContents c, ResourceLocation id, int n) {
        Map<ResourceLocation, Integer> m = new TreeMap<>(c.materials());
        m.put(id, n);
        return m;
    }

    /** The menu slot of the first filled Gear slot. */
    private static int slotGear(ServerPlayer p) {
        for (int i = 0; i < SatchelContents.GEAR_SLOTS; i++) {
            if (!p.containerMenu.slots.get(i).getItem().isEmpty()) {
                return i;
            }
        }
        throw new Steps.Failure("no gear in the satchel's gear slots");
    }
}
