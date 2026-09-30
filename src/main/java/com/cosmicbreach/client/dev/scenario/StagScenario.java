package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.mount.MountScreen;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.MountGear;
import com.cosmicbreach.mount.MountMenu;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.mount.StagRules;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Lumen Stag (G6b, GDD 8.1) with real inputs, in the flat test world:
 * <ul>
 *   <li>skittishness: a herd bolts from a sprinter at 13 blocks and loses no trust; approached sneaking it stays, and a
 *       sprint started within 8 blocks costs every stag of the herd 2 trust; a punch costs all of them 2;</li>
 *   <li>taming: sneaking up and hand-feeding Starbloom, 1 or 2 trust a feed, tamed at 5, the antlers photographed at
 *       trust 0 and 5 (their glow is the only meter);</li>
 *   <li>gear: the Astral Saddle by right click, the inventory (the vanilla horse screen with the tack slot) with its
 *       barding and tack moved in by shift-click; armor 5 and 8;</li>
 *   <li>riding: its speed, the triple jump's heights (and the Comet Bridle's fourth), the glide's sink and ratio, and the
 *       Halo Reins' slower sink, all measured on the rider's client, which moves it; no fall damage after a glide;</li>
 *   <li>mounted combat: L1, L2, then L1 again (never L3), the stag unhurt; the ability; the dash key jumps the stag;</li>
 *   <li>looks: in the Sunfield Terraces' daylight, a herd, a geared stag from three sides, walking, and ridden.</li>
 * </ul>
 */
public final class StagScenario implements Scenario {
    private static final ResourceLocation L1 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l1");
    private static final ResourceLocation L2 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l2");
    private static final ResourceLocation ZENITH = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/zenith");

    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private List<Integer> herd = new ArrayList<>();
    private int tame = -1;
    private @Nullable DevCamera camera;

    // measurements (the rider's client)
    private double apex;
    private final double[] rises = new double[5];
    private int jumpIndexSeen;
    private final List<Vec3> track = new ArrayList<>();
    private final List<String> ys = new ArrayList<>();
    private float healthBefore;

    @Override
    public int timeBudgetSeconds() {
        return 420;
    }

    @Override
    public void steps(Steps steps) {
        ClientCombat.addEventListener(events::add);
        setUp(steps);
        skittish(steps);
        taming(steps);
        gear(steps);
        riding(steps);
        mountedCombat(steps);
        looks(steps);
        steps.run("summary", () -> results.forEach(line -> com.cosmicbreach.CosmicBreach.LOGGER.info("[stag] " + line)))
                .log("results", () -> String.join(" | ", results));
    }

    // ------------------------------------------------------------------ helpers

    private @Nullable LumenStag stag(int id) {
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e instanceof LumenStag s ? s : null;
    }

    private static LumenStag serverStag(MinecraftServer srv, int id) {
        Entity e = CryptKit.player(srv).serverLevel().getEntity(id);
        if (!(e instanceof LumenStag s)) {
            throw new Steps.Failure("no stag " + id + " on the server");
        }
        return s;
    }

    private int[] trusts() {
        return CryptKit.server(srv -> herd.stream().mapToInt(id -> serverStag(srv, id).trust()).toArray());
    }

    private boolean allBolting() {
        return CryptKit.server(srv -> herd.stream().allMatch(id -> serverStag(srv, id).bolting()));
    }

    private boolean anyBolting() {
        return CryptKit.server(srv -> herd.stream().anyMatch(id -> serverStag(srv, id).bolting()));
    }

    /** A fresh herd of three, 10 blocks ahead of the player, all at {@code trust}. */
    private void newHerd(Steps steps, int trust) {
        steps.run("remove the last herd", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    for (LumenStag st : p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(200.0))) {
                        st.discard();
                    }
                    return null;
                }))
                .waitTicks(2)
                .run("face south", () -> CryptKit.face(mc, mc.player.position().add(0, 0, 10), 0f))
                .command("cosmicbreach debug mount spawn stag 3")
                .waitUntil("the herd is here", 60, () -> CryptKit.server(srv -> {
                    List<LumenStag> all = CryptKit.player(srv).serverLevel().getEntitiesOfClass(LumenStag.class,
                            CryptKit.player(srv).getBoundingBox().inflate(40.0), LumenStag::isAlive);
                    return all.size() == 3;
                }))
                .run("remember the herd, trust " + trust, () -> {
                    herd = CryptKit.server(srv -> {
                        ServerPlayer p = CryptKit.player(srv);
                        List<LumenStag> all = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(40.0),
                                LumenStag::isAlive));
                        all.sort(Comparator.comparingDouble(s -> s.distanceToSqr(p)));
                        List<Integer> ids = new ArrayList<>();
                        for (LumenStag s : all) {
                            s.setTrust(trust);
                            ids.add(s.getId());
                        }
                        return ids;
                    });
                })
                .waitUntil("the client sees the herd", 40, () -> herd.stream().allMatch(id -> stag(id) != null));
    }

    private double distanceTo(int id) {
        LumenStag s = stag(id);
        return s == null ? Double.MAX_VALUE : s.distanceTo(mc.player);
    }

    /** Walks (sneaking or not) toward stag {@code id} until within {@code within} blocks. */
    private void approach(Steps steps, String what, java.util.function.IntSupplier id, double within, boolean sneak, int timeout) {
        steps.waitUntil(what, timeout, () -> {
            LumenStag s = stag(id.getAsInt());
            if (s == null) {
                return false;
            }
            CryptKit.face(mc, s.position(), 12f);
            boolean there = s.distanceTo(mc.player) <= within;
            CryptKit.set(mc.options.keyShift, sneak);
            CryptKit.set(mc.options.keyUp, !there);
            if (there) {
                CryptKit.set(mc.options.keyUp, false);
            }
            return there;
        });
    }

    private void aimAt(int id) {
        LumenStag s = stag(id);
        if (s == null) {
            throw new Steps.Failure("stag " + id + " is gone");
        }
        CryptKit.aim(mc, s.getBoundingBox().getCenter().add(0, -0.1, 0));
    }

    private void select(Item item) {
        LocalPlayer p = mc.player;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).is(item)) {
                p.getInventory().selected = i;
                return;
            }
        }
        throw new Steps.Failure("no " + item + " in the hotbar");
    }

    private int hotbarSlotOf(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** Shift-click in the open mount inventory: the menu slot of the hotbar holding {@code item}, or the tack slot. */
    private void quickMove(int menuSlot) {
        if (!(mc.player.containerMenu instanceof MountMenu menu)) {
            throw new Steps.Failure("the mount's inventory is not open");
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static int menuSlotOfHotbar(int hotbar) {
        return 29 + hotbar;
    }

    private LumenStag ridden() {
        if (!(mc.player.getVehicle() instanceof LumenStag s)) {
            throw new Steps.Failure("not riding a stag");
        }
        return s;
    }

    // ------------------------------------------------------------------ sections

    private void setUp(Steps steps) {
        steps.command("gamemode survival")
                .waitUntil("survival", 40, () -> !mc.player.isCreative())
                .command("effect give @s minecraft:resistance infinite 4 true")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .command("gamerule doMobSpawning false")
                .command("clear @s")
                .command("give @s cosmicbreach:starbloom 16")
                .command("give @s cosmicbreach:astral_saddle")
                .command("give @s cosmicbreach:starsteel_barding")
                .command("give @s cosmicbreach:nebulite_barding")
                .command("give @s cosmicbreach:comet_bridle")
                .command("give @s cosmicbreach:halo_reins")
                .command("give @s cosmicbreach:meridian")
                .waitUntil("seven kinds of item in the hotbar", 60, () -> hotbarSlotOf(ModItems.MERIDIAN.get()) >= 0
                        && hotbarSlotOf(Mounts.HALO_REINS.get()) >= 0)
                .run("an empty hand for the punch", () -> mc.player.getInventory().selected = 8)
                .waitUntil("slot 9 is empty", 5, () -> mc.player.getInventory().getItem(8).isEmpty());
    }

    private void skittish(Steps steps) {
        // A: a sprinter at 13 blocks: the herd bolts before it can come within 8, and keeps its trust
        newHerd(steps, 3);
        steps.run("back off to 13 blocks", () -> {
            LumenStag s = stag(herd.get(0));
            Vec3 away = mc.player.position().subtract(s.position()).multiply(1, 0, 1).normalize();
            CryptKit.tp(mc, s.position().add(away.scale(13.0)), s.position());
        }).waitTicks(8);
        double[] boltAt = {-1};
        steps.check("nobody bolts from a standing player at 13 blocks", () -> !anyBolting())
                .run("sprint at them", () -> {
                    CryptKit.face(mc, stag(herd.get(0)).position(), 5f);
                    CryptKit.set(mc.options.keySprint, true);
                    CryptKit.set(mc.options.keyUp, true);
                })
                .waitUntil("the whole herd bolts from the sprinter", 60, () -> {
                    if (stag(herd.get(0)) != null) {
                        CryptKit.face(mc, stag(herd.get(0)).position(), 5f);
                    }
                    if (allBolting()) {
                        boltAt[0] = herd.stream().mapToDouble(this::distanceTo).min().orElse(-1);
                        return true;
                    }
                    return false;
                })
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitTicks(20)
                .check("no trust lost: nobody sprinted within 8", () -> java.util.Arrays.stream(trusts()).allMatch(t -> t == 3))
                .run("result A", () -> results.add(String.format(Locale.ROOT, "sprint: bolted at %.1f blocks, trust kept 3", boltAt[0])));

        // B: sneak to 6 blocks, then sprint: every stag loses 2
        newHerd(steps, 3);
        approach(steps, "sneak up to 6 blocks", () -> herd.get(0), 6.0, true, 400);
        double[] sneakDist = {0};
        steps.run("stop sneaking in place", () -> {
                    CryptKit.releaseAll(mc);
                    sneakDist[0] = distanceTo(herd.get(0));
                })
                .check("sneaking close, nobody bolted", () -> !anyBolting())
                .check("sneaking close, trust unchanged", () -> java.util.Arrays.stream(trusts()).allMatch(t -> t == 3))
                .run("sprint", () -> {
                    CryptKit.face(mc, stag(herd.get(0)).position(), 5f);
                    CryptKit.set(mc.options.keySprint, true);
                    CryptKit.set(mc.options.keyUp, true);
                })
                .waitUntil("the herd loses trust", 40, () -> java.util.Arrays.stream(trusts()).allMatch(t -> t < 3))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitTicks(10)
                .check("every stag lost exactly 2 (3 to 1)", () -> java.util.Arrays.stream(trusts()).allMatch(t -> t == 1))
                .check("and they bolted", this::anyBolting)
                .run("result B", () -> results.add(String.format(Locale.ROOT, "sneaked to %.1f unseen; sprint within 8: trust 3,3,3 to 1,1,1",
                        sneakDist[0])));

        // C: a punch on one costs all of them 2
        newHerd(steps, 4);
        approach(steps, "sneak up to a stag", () -> herd.get(0), 2.3, true, 500);
        steps.run("aim", () -> aimAt(herd.get(0)))
                .press(mc.options.keyAttack)
                .waitUntil("the punched herd loses trust", 40, () -> java.util.Arrays.stream(trusts()).allMatch(t -> t < 4))
                .run("release", () -> CryptKit.releaseAll(mc))
                .check("a punch took 2 from every stag (4 to 2)", () -> java.util.Arrays.stream(trusts()).allMatch(t -> t == 2))
                .run("result C", () -> results.add("punch: trust 4,4,4 to 2,2,2"));
    }

    private void taming(Steps steps) {
        newHerd(steps, 0);
        steps.run("the target", () -> tame = herd.get(0))
                .command("data merge entity @e[type=cosmicbreach:lumen_stag,limit=1,sort=nearest] {NoAI:1b}")
                .waitTicks(3)
                .check("the antlers glow faintly at trust 0", () -> Math.abs(stag(tame).antlerGlow() - 0.12f) < 1e-3);
        antlerShot(steps, "stag_antlers_trust0");
        steps.command("data merge entity @e[type=cosmicbreach:lumen_stag,limit=1,sort=nearest] {NoAI:0b}");
        Feeder feeder = new Feeder();
        steps.waitUntil("sneaking up and hand-feeding Starbloom until it is tame", 2400, feeder::tick)
                .run("release", () -> CryptKit.releaseAll(mc))
                .check("each feed gave 1 or 2", () -> !feeder.gains.isEmpty() && feeder.gains.stream().allMatch(g -> g == 1 || g == 2))
                .check("tamed exactly when trust reached 5, by the player", () -> CryptKit.server(srv -> {
                    LumenStag st = serverStag(srv, tame);
                    return st.isTamed() && st.trust() == 5 && CryptKit.player(srv).getUUID().equals(st.getOwnerUUID());
                }) && feeder.trustSeen.get(feeder.trustSeen.size() - 1) == 5
                        && feeder.trustSeen.subList(0, feeder.trustSeen.size() - 1).stream().allMatch(t -> t < 5))
                .waitUntil("the antlers glow full once tamed (the client sees the tame)", 60, () -> stag(tame) != null && stag(tame).antlerGlow() >= 0.999f)
                .run("result taming", () -> results.add("feeds gave " + feeder.gains + ", trust " + feeder.trustSeen
                        + ", tamed at 5; antler glow 0.12 at trust 0, 1.0 tamed"))
                .command("data merge entity @e[type=cosmicbreach:lumen_stag,limit=1,sort=nearest] {NoAI:1b}")
                .waitTicks(3);
        antlerShot(steps, "stag_antlers_trust5");
        steps.command("data merge entity @e[type=cosmicbreach:lumen_stag,limit=1,sort=nearest] {NoAI:0b}");
    }

    /** Sneaks up to the target stag and feeds it Starbloom, one feed after each chew, until it is tamed. */
    private final class Feeder {
        final List<Integer> gains = new ArrayList<>();
        final List<Integer> trustSeen = new ArrayList<>();
        int before = -1;
        int wait;
        int releaseIn;

        boolean tick() {
            if (releaseIn > 0 && --releaseIn == 0) {
                CryptKit.set(mc.options.keyUse, false);
            }
            int[] state = CryptKit.server(srv -> {
                LumenStag st = serverStag(srv, tame);
                return new int[] {st.trust(), st.isTamed() ? 1 : 0, st.isEating() ? 1 : 0};
            });
            if (before >= 0 && (state[0] != before || state[1] == 1)) {
                gains.add(state[0] - before);
                trustSeen.add(state[0]);
                before = -1;
                wait = StagRules.CHEW_TICKS + 3;
            }
            if (state[1] == 1) {
                CryptKit.releaseAll(mc);
                return true;
            }
            if (wait > 0) {
                wait--;
                return false;
            }
            before = -1; // a feed not taken (the stag stepped off, or was still chewing): try again
            LumenStag st = stag(tame);
            if (st == null) {
                return false;
            }
            double d = st.distanceTo(mc.player);
            CryptKit.set(mc.options.keyShift, true);
            if (d > 2.2) {
                CryptKit.face(mc, st.position(), 12f);
                CryptKit.set(mc.options.keyUp, true);
                return false;
            }
            CryptKit.set(mc.options.keyUp, false);
            if (before < 0 && state[2] == 0) {
                select(ModBlocks.STARBLOOM.get().asItem());
                aimAt(tame);
                before = state[0];
                CryptKit.set(mc.options.keyUse, true);
                KeyClick.click(mc.options.keyUse);
                releaseIn = 1;
                wait = 30;
            }
            return false;
        }
    }

    private void antlerShot(Steps steps, String label) {
        steps.run("camera at the antlers", () -> {
                    LumenStag s = stag(tame);
                    float yaw = s.yBodyRot;
                    Vec3 fwd = Vec3.directionFromRotation(0f, yaw);
                    Vec3 side = fwd.yRot((float) Math.PI / 2);
                    Vec3 head = s.position().add(fwd.scale(0.9)).add(0, 2.6, 0);
                    camera = DevCamera.create(mc.level);
                    camera.place(head.add(side.scale(3.8)).add(fwd.scale(2.2)).add(0, 0.2, 0), head);
                    camera.use();
                    mc.options.hideGui = true;
                })
                .waitTicks(4)
                .screenshot(label)
                .run("camera off", this::uncamera);
    }

    private void uncamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
        mc.options.hideGui = false;
    }

    private void gear(Steps steps) {
        approach(steps, "walk up to the tamed stag", () -> tame, 2.2, false, 400);
        steps.run("the saddle in hand", () -> {
                    select(Mounts.ASTRAL_SADDLE.get());
                    aimAt(tame);
                })
                .press(mc.options.keyUse)
                .waitUntil("saddled", 30, () -> CryptKit.server(srv -> serverStag(srv, tame).isSaddled()))
                .run("sneak and aim", () -> {
                    CryptKit.set(mc.options.keyShift, true);
                    aimAt(tame);
                })
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the mount's inventory opens (sneak + use)", 40, () -> mc.screen instanceof MountScreen)
                .run("release sneak", () -> CryptKit.set(mc.options.keyShift, false))
                .run("shift-click the Starsteel Barding in", () -> quickMove(menuSlotOfHotbar(hotbarSlotOf(Mounts.STARSTEEL_BARDING.get()))))
                .waitTicks(3)
                .run("shift-click the Comet Bridle in", () -> quickMove(menuSlotOfHotbar(hotbarSlotOf(Mounts.COMET_BRIDLE.get()))))
                .waitTicks(4)
                .screenshot("stag_inventory")
                .check("the barding sits in the armor slot, the bridle in the tack slot", () -> CryptKit.server(srv -> {
                    LumenStag s = serverStag(srv, tame);
                    return MountGear.of(s.getBodyArmorItem()) == MountGear.STARSTEEL_BARDING && s.wears(MountGear.COMET_BRIDLE);
                }))
                .check("Starsteel: armor 5", () -> CryptKit.server(srv -> serverStag(srv, tame).getAttributeValue(Attributes.ARMOR)) == 5.0)
                .check("a saddle is refused in the tack slot", () -> {
                    MountMenu menu = (MountMenu) mc.player.containerMenu;
                    return !menu.getSlot(menu.tackSlot()).mayPlace(new ItemStack(Mounts.ASTRAL_SADDLE.get()))
                            && !menu.getSlot(1).mayPlace(new ItemStack(Items.DIAMOND_HORSE_ARMOR));
                })
                .run("close", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null)
                .run("result gear", () -> results.add("saddle by right click; inventory: Starsteel into armor (5), Comet Bridle into tack"));
    }

    private void riding(Steps steps) {
        steps.run("aim to ride, empty-handed", () -> {
                    mc.player.getInventory().selected = 8;
                    aimAt(tame);
                })
                .press(mc.options.keyUse)
                .waitUntil("riding the stag", 40, () -> mc.player.getVehicle() instanceof LumenStag)
                .run("health before", () -> healthBefore = ridden().getHealth())
                .look(0f, 0f);
        // speed on flat ground
        double[] speed = {0};
        steps.hold(mc.options.keyUp)
                .waitTicks(30)
                .run("start timing", () -> track.clear())
                .waitUntil("20 ticks of galloping", 25, () -> {
                    track.add(ridden().position());
                    return track.size() > 20;
                })
                .run("speed", () -> {
                    Vec3 a = track.get(0);
                    Vec3 b = track.get(track.size() - 1);
                    speed[0] = Math.hypot(b.x - a.x, b.z - a.z) / (track.size() - 1);
                })
                .release(mc.options.keyUp)
                .check("ridden it gallops (0.30 speed: well over half a block a tick)", () -> speed[0] > 0.55)
                .run("result speed", () -> results.add(String.format(Locale.ROOT, "gallop %.3f blocks/tick (%.1f/s)", speed[0], speed[0] * 20)))
                .waitUntil("stopped", 60, () -> ridden().getDeltaMovement().horizontalDistance() < 0.01 && ridden().onGround());
        // four jumps (the Comet Bridle is on), each measured from where it started to its top
        for (int k = 0; k < 4; k++) {
            int kk = k;
            steps.run("jump " + (k + 1), () -> apex = Double.NEGATIVE_INFINITY)
                    .press(mc.options.keyJump)
                    .waitUntil("jump " + (k + 1) + " starts", 6, () -> ridden().lastJumpIndex() == kk && ridden().jumpsUsed() == kk + 1)
                    .run("trace " + (k + 1), () -> ys.clear())
                    .waitUntil("jump " + (k + 1) + " tops out", 60, () -> {
                        double y = ridden().getY();
                        ys.add(String.format(Locale.ROOT, "%.3f/%.3f", y - ridden().lastJumpStartY(), ridden().getDeltaMovement().y));
                        if (y > apex) {
                            apex = y;
                            return false;
                        }
                        return y < apex - 1e-4;
                    })
                    .run("rise " + (k + 1), () -> rises[kk] = apex - ridden().lastJumpStartY())
                    .log("rise " + (k + 1), () -> String.format(Locale.ROOT, "jump %d rose %.3f: %s", kk + 1, rises[kk], ys));
        }
        steps.press(mc.options.keyJump)
                .waitTicks(3)
                .check("no fifth jump", () -> ridden().jumpsUsed() == 4)
                .check("the jumps rise 2.5, 3.5, 4.0 and the bridle's 4.0", () -> Math.abs(rises[0] - 2.5) < 0.08
                        && Math.abs(rises[1] - 3.5) < 0.08 && Math.abs(rises[2] - 4.0) < 0.08 && Math.abs(rises[3] - 4.0) < 0.08)
                .run("result jumps", () -> results.add(String.format(Locale.ROOT, "jumps rise %.2f %.2f %.2f, bridle %.2f",
                        rises[0], rises[1], rises[2], rises[3])));
        glide(steps, "the glide", "stag_glide", false);
        steps.check("no fall damage after the glide", () -> ridden().getHealth() >= healthBefore - 0.01f);
        // the Halo Reins: swap the tack through the inventory, opened with the inventory key while riding
        steps.press(mc.options.keyInventory)
                .waitUntil("the inventory opens from the saddle", 40, () -> mc.screen instanceof MountScreen)
                .run("the bridle out", () -> quickMove(((MountMenu) mc.player.containerMenu).tackSlot()))
                .waitTicks(3)
                .run("the reins in", () -> quickMove(menuSlotOfHotbar(hotbarSlotOf(Mounts.HALO_REINS.get()))))
                .waitTicks(3)
                .run("the Nebulite barding in place of the Starsteel", () -> {
                    quickMove(1);
                })
                .waitTicks(3)
                .run("nebulite in", () -> quickMove(menuSlotOfHotbar(hotbarSlotOf(Mounts.NEBULITE_BARDING.get()))))
                .waitTicks(3)
                .run("close", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null)
                .check("Halo Reins on, Nebulite: armor 8", () -> CryptKit.server(srv -> {
                    LumenStag s = serverStag(srv, tame);
                    return s.wears(MountGear.HALO_REINS) && s.getAttributeValue(Attributes.ARMOR) == 8.0;
                }))
                .run("result armor", () -> results.add("Nebulite barding: armor 8"));
        // three jumps to get height, then glide
        for (int k = 0; k < 3; k++) {
            int kk = k;
            steps.run("up " + (k + 1), () -> apex = Double.NEGATIVE_INFINITY)
                    .press(mc.options.keyJump)
                    .waitUntil("up " + (k + 1) + " starts", 6, () -> ridden().jumpsUsed() == kk + 1)
                    .waitUntil("up " + (k + 1) + " tops out", 60, () -> {
                        double y = ridden().getY();
                        if (y > apex) {
                            apex = y;
                            return false;
                        }
                        return y < apex - 1e-4;
                    });
        }
        glide(steps, "the Halo Reins' glide", null, true);
    }

    /** Holds jump while falling: 20 ticks measured once the glide has settled; lands. */
    private void glide(Steps steps, String what, @Nullable String shot, boolean reins) {
        double[] m = {0, 0};
        steps.run(what + ": hold jump and forward", () -> {
                    track.clear();
                    CryptKit.set(mc.options.keyJump, true);
                    KeyClick.click(mc.options.keyJump);
                })
                .waitUntil(what + ": gliding", 20, () -> ridden().gliding())
                .waitTicks(12)
                .waitUntil(what + ": 20 ticks measured", 25, () -> {
                    track.add(ridden().position());
                    return track.size() > 20 || ridden().onGround();
                })
                .run(what + ": ratio", () -> {
                    Vec3 a = track.get(0);
                    Vec3 b = track.get(track.size() - 1);
                    int n = track.size() - 1;
                    m[0] = Math.hypot(b.x - a.x, b.z - a.z) / n;
                    m[1] = (a.y - b.y) / n;
                });
        if (shot != null) {
            steps.run("camera beside the glide", () -> {
                        LumenStag s = ridden();
                        Vec3 fwd = Vec3.directionFromRotation(0f, s.getYRot());
                        Vec3 side = fwd.yRot((float) Math.PI / 2);
                        camera = DevCamera.create(mc.level);
                        Vec3 at = s.position().add(fwd.scale(4.0)).add(0, 1.2, 0);
                        camera.place(at.add(side.scale(7.0)).add(0, 1.0, 0), at);
                        camera.use();
                        mc.options.hideGui = true;
                    })
                    .waitTicks(1)
                    .screenshot(shot)
                    .run("camera off", this::uncamera);
        }
        double sink = StagRules.glideSink(reins);
        steps.waitUntil(what + ": glided down to the ground", 600, () -> ridden().onGround())
                .run(what + ": release jump", () -> CryptKit.set(mc.options.keyJump, false))
                .check(what + String.format(Locale.ROOT, ": sinks %.2f a tick at 0.55 forward", sink),
                        () -> Math.abs(m[1] - sink) < 0.005 && Math.abs(m[0] - StagRules.GLIDE_SPEED) < 0.02)
                .run(what + ": result", () -> results.add(String.format(Locale.ROOT, "%s: forward %.3f, sink %.3f, ratio %.2f to 1",
                        what, m[0], m[1], m[0] / m[1])));
    }

    private void mountedCombat(Steps steps) {
        int[] zombie = {-1};
        float[] stagHealth = {0};
        steps.run("Meridian in hand", () -> select(ModItems.MERIDIAN.get()))
                .look(0f, 0f)
                .run("a still zombie ahead", () -> zombie[0] = CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    ServerLevel level = p.serverLevel();
                    Entity v = p.getVehicle();
                    Zombie z = new Zombie(level);
                    Vec3 at = v.position().add(Vec3.directionFromRotation(0f, p.getYRot()).scale(3.2));
                    z.moveTo(at.x, at.y, at.z, p.getYRot() + 180f, 0f);
                    z.setNoAi(true);
                    z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
                    z.setHealth(400f);
                    z.setPersistenceRequired();
                    level.addFreshEntity(z);
                    return z.getId();
                }))
                .waitTicks(5)
                .run("aim at it", () -> {
                    Entity z = mc.level.getEntity(zombie[0]);
                    CryptKit.aim(mc, z.getBoundingBox().getCenter());
                    stagHealth[0] = ridden().getHealth();
                    events.clear();
                })
                .press(mc.options.keyAttack)
                .waitTicks(12)
                .press(mc.options.keyAttack)
                .waitTicks(12)
                .press(mc.options.keyAttack)
                .waitTicks(16)
                .check("riding: L1, L2, then L1 again (never L3)", () -> started().equals(List.of(L1, L2, L1)))
                .check("the zombie was hit", () -> CryptKit.server(srv -> ((Zombie) CryptKit.player(srv).serverLevel().getEntity(zombie[0])).getHealth()) < 400f)
                .check("the stag was not", () -> ridden().getHealth() >= stagHealth[0] - 0.01f)
                .run("clear", () -> events.clear())
                .press(ModKeyMappings.DASH)
                .waitUntil("the dash key makes the stag jump", 10, () -> ridden().lastJumpIndex() == 0 && !ridden().onGround())
                .check("and no dash of the rider's", () -> events.stream().noneMatch(e -> e instanceof CombatEvent.DashStarted))
                .waitUntil("landed", 80, () -> ridden().onGround())
                .run("aim", () -> CryptKit.aim(mc, mc.level.getEntity(zombie[0]).getBoundingBox().getCenter()))
                .command("cosmicbreach debug resonance 100")
                .waitTicks(3)
                .run("clear", () -> events.clear())
                .press(mc.options.keyUse)
                .waitTicks(10)
                .check("the weapon's ability works while riding", () -> started().contains(ZENITH))
                .run("result combat", () -> results.add("mounted: " + List.of(L1, L2, L1) + ", zombie hit, stag unhurt; dash key: the stag jumped; Zenith cast"))
                .run("remove the zombie", () -> CryptKit.server(srv -> {
                    Entity z = CryptKit.player(srv).serverLevel().getEntity(zombie[0]);
                    if (z != null) {
                        z.discard();
                    }
                    return null;
                }))
                .waitTicks(20);
    }

    private List<ResourceLocation> started() {
        List<ResourceLocation> out = new ArrayList<>();
        for (CombatEvent e : events) {
            if (e instanceof CombatEvent.MoveStarted s) {
                out.add(s.move().id());
            }
        }
        return out;
    }

    private void looks(Steps steps) {
        steps.press(mc.options.keyShift)
                .waitUntil("off the stag", 20, () -> mc.player.getVehicle() == null)
                .command("cosmicbreach debug goto sunfield")
                .waitUntil("in Aetheria", 200, () -> mc.level.dimension().location().getPath().equals("aetheria"))
                .waitUntil("the terraces are drawn", 400, CryptKit.settled(mc, 300))
                .command("time set 6000")
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug mount spawn stag 1")
                .waitTicks(20)
                .run("tame, gear and hold it still", () -> tame = CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    List<LumenStag> all = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(30),
                            LumenStag::isAlive));
                    all.sort(Comparator.comparingDouble(st -> st.distanceToSqr(p)));
                    LumenStag st = all.get(0);
                    st.setTrust(5);
                    st.tameTo(p);
                    st.equipSaddle(new ItemStack(Mounts.ASTRAL_SADDLE.get()), null);
                    st.setBodyArmorItem(new ItemStack(Mounts.STARSTEEL_BARDING.get()));
                    st.tackContainer().setItem(0, new ItemStack(Mounts.HALO_REINS.get()));
                    st.setNoAi(true);
                    st.setYRot(0f);
                    st.setYBodyRot(0f);
                    st.setYHeadRot(0f);
                    return st.getId();
                }))
                .waitTicks(50); // the hearts of its taming fade
        for (String[] view : new String[][] {{"stag_side", "90"}, {"stag_front", "0"}, {"stag_back", "180"}, {"stag_34", "40"}}) {
            float turn = Float.parseFloat(view[1]);
            steps.run("camera " + view[0], () -> {
                        LumenStag s = stag(tame);
                        Vec3 fwd = Vec3.directionFromRotation(0f, s.yBodyRot);
                        Vec3 dir = fwd.yRot((float) Math.toRadians(-turn));
                        Vec3 mid = s.position().add(0, 1.6, 0);
                        uncamera();
                        camera = DevCamera.create(mc.level);
                        camera.place(mid.add(dir.scale(5.6)).add(0, 0.9, 0), mid);
                        camera.use();
                        mc.options.hideGui = true;
                    })
                    .waitTicks(3)
                    .screenshot(view[0]);
        }
        steps.run("camera off", this::uncamera)
                .command("data merge entity @e[type=cosmicbreach:lumen_stag,limit=1,sort=nearest] {NoAI:0b}")
                .run("face away for the herd", () -> CryptKit.face(mc, mc.player.position().add(-10, 0, 0), 0f))
                .command("cosmicbreach debug mount spawn stag 4")
                .waitTicks(60)
                .run("herd shot", () -> {
                    List<LumenStag> wild = CryptKit.server(srv -> {
                        ServerPlayer p = CryptKit.player(srv);
                        return new ArrayList<>(p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(30),
                                st -> st.isAlive() && !st.isTamed()));
                    });
                    Vec3 mid = Vec3.ZERO;
                    for (LumenStag w : wild) {
                        mid = mid.add(w.position());
                    }
                    mid = mid.scale(1.0 / Math.max(1, wild.size())).add(0, 1.2, 0);
                    Vec3 from = mc.player.position().subtract(mid).multiply(1, 0, 1).normalize();
                    camera = DevCamera.create(mc.level);
                    camera.place(mid.add(from.yRot(0.5f).scale(12.0)).add(0, 3.0, 0), mid);
                    camera.use();
                    mc.options.hideGui = true;
                })
                .waitTicks(2)
                .screenshot("stag_herd")
                .run("camera off", this::uncamera);
        approach(steps, "walk up to the geared stag", () -> tame, 2.2, false, 400);
        float[] heading = {0f};
        steps.run("ride it", () -> {
                    CryptKit.releaseAll(mc);
                    mc.player.getInventory().selected = 8;
                    aimAt(tame);
                })
                .press(mc.options.keyUse)
                .waitUntil("riding in the Sunfield", 40, () -> mc.player.getVehicle() instanceof LumenStag)
                .run("pick a heading with open ground ahead", () -> heading[0] = openHeading())
                .run("face it", () -> CryptKit.face(mc, mc.player.position().add(Vec3.directionFromRotation(0f, heading[0]).scale(5.0)), 0f))
                .hold(mc.options.keyUp)
                .waitTicks(14)
                .run("camera beside the gallop", () -> {
                    LumenStag s = ridden();
                    Vec3 fwd = Vec3.directionFromRotation(0f, s.getYRot());
                    Vec3 side = fwd.yRot((float) Math.PI / 2);
                    Vec3 at = s.position().add(fwd.scale(3.0)).add(0, 1.4, 0);
                    camera = DevCamera.create(mc.level);
                    camera.place(at.add(side.scale(6.5)).add(0, 0.6, 0), at);
                    camera.use();
                    mc.options.hideGui = true;
                })
                .waitTicks(1)
                .screenshot("stag_ridden")
                .run("the rider's own view", () -> {
                    uncamera();
                    mc.options.hideGui = true;
                })
                .run("look ahead", () -> CryptKit.face(mc, mc.player.position().add(Vec3.directionFromRotation(0f, heading[0]).scale(5.0)), 8f))
                .waitTicks(2)
                .screenshot("stag_rider_view")
                .release(mc.options.keyUp)
                .run("camera off", this::uncamera);
    }

    /** The first of eight headings with 14 blocks of level, open ground ahead of the player; 0 if none. */
    private float openHeading() {
        for (int k = 0; k < 8; k++) {
            float yaw = k * 45f;
            Vec3 dir = Vec3.directionFromRotation(0f, yaw);
            boolean clear = true;
            for (int d = 2; d <= 14 && clear; d++) {
                net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(mc.player.position().add(dir.scale(d)));
                clear = mc.level.getBlockState(p).isAir() && mc.level.getBlockState(p.above()).isAir()
                        && mc.level.getBlockState(p.above(2)).isAir() && !mc.level.getBlockState(p.below()).isAir();
            }
            if (clear) {
                return yaw;
            }
        }
        return 0f;
    }

    /** A click of a key (like a press) without the one-tick wait. */
    static final class KeyClick {
        static void click(net.minecraft.client.KeyMapping key) {
            net.minecraft.client.KeyMapping.click(key.getKey());
        }
    }
}
