package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.accessory.AccessoryRegistry;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.GuardianCommands;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.guardian.unsung.UnsungCommands;
import com.cosmicbreach.shrine.ShrineData;
import com.cosmicbreach.shrine.ShrineKind;
import com.cosmicbreach.shrine.ShrineSave;
import com.cosmicbreach.shrine.ShrineSites;
import com.cosmicbreach.shrine.ShrineSpots;
import com.cosmicbreach.structure.sanctum.Sanctums;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The boss shrines (1.1 design section 9) with real inputs. Part {@code shrines}: a Crown Spire built by the debug
 * command gets its shrine from the placing tick; the player (37 diamonds, a sword, level 23, a ring worn) right clicks
 * it, falls into the void and wakes beside it with all of it; after a spawn point beside it, a death drops everything as
 * before. Part {@code shrines-sites}: the Rift's, the nave's and the Sanctum's shrines are placed where their layouts
 * say, each framed for the review. Part {@code shrines-deaths}: every death path on solid ground, counting what lands on
 * the floor: a saved death beside a grave mod that takes the inventory before the keep, one that takes it after, one that
 * cancels the death, a saved death alone, and an unsaved one. No path may duplicate or lose an item.
 */
public final class ShrineScenario implements Scenario {
    public enum Part { KEEP, SITES, DEATHS }

    private final Part part;
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private BlockPos shrine = BlockPos.ZERO;
    private Map<String, Integer> carried = Map.of();

    public ShrineScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    private static @Nullable ShrineData.Placed placed(ServerPlayer p, ShrineKind kind) {
        return ShrineData.get(p.serverLevel()).all().values().stream().filter(s -> s.kind() == kind).findFirst().orElse(null);
    }

    private Map<String, Integer> inventory() {
        Map<String, Integer> out = new TreeMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private boolean ringWorn() {
        return ServerQuery.ask(p -> CuriosApi.getCuriosInventory(p).map(inv -> inv.isEquipped(AccessoryRegistry.TWIN_COMET_BAND.get())).orElse(false));
    }

    private boolean dead() {
        return mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen;
    }

    private void besideShrine(ShrineKind kind) {
        ShrineData.Placed s = ServerQuery.ask(p -> placed(p, kind));
        shrine = s.pos();
        Vec3 stand = ServerQuery.ask(p -> ShrineSpots.standSpot(p.serverLevel(), s.pos(), s.facing()).orElseThrow());
        ColossusScenario.tp(mc, stand.x, stand.y, stand.z, shrine.getX() + 0.5, shrine.getY() + 0.8, shrine.getZ() + 0.5);
    }

    /**
     * A look at the placed shrine from about {@code dist} blocks out and {@code up} blocks above its base (creative,
     * hovering): the first of its front, front corners, sides and back, nearer if need be, where the eye is in open air and
     * has a clear line to the shrine.
     */
    private void frame(ShrineKind kind, double dist, double up) {
        ShrineData.Placed s = ServerQuery.ask(p -> placed(p, kind));
        BlockPos b = s.pos();
        Vec3 target = new Vec3(b.getX() + 0.5, b.getY() + 1.3, b.getZ() + 0.5);
        Vec3 at = ServerQuery.ask(p -> {
            int fx = s.facing().getStepX();
            int fz = s.facing().getStepZ();
            int[][] dirs = {{fx, fz}, {fx - fz, fz + fx}, {fx + fz, fz - fx}, {-fz, fx}, {fz, -fx}, {-fx, -fz}};
            for (double k : new double[] {1.0, 0.7, 0.5}) {
                for (int[] d : dirs) {
                    double len = Math.hypot(d[0], d[1]);
                    Vec3 feet = new Vec3(target.x + d[0] / len * dist * k, b.getY() + up, target.z + d[1] / len * dist * k);
                    Vec3 eye = feet.add(0, 1.62, 0);
                    BlockPos fp = BlockPos.containing(feet);
                    boolean open = p.serverLevel().getBlockState(fp).getCollisionShape(p.serverLevel(), fp).isEmpty()
                            && p.serverLevel().getBlockState(fp.above()).getCollisionShape(p.serverLevel(), fp.above()).isEmpty();
                    if (open && p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye, target,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p))
                            .getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                        return feet;
                    }
                }
            }
            return new Vec3(target.x + fx * dist, b.getY() + up, target.z + fz * dist);
        });
        ColossusScenario.tp(mc, at.x, at.y, at.z, target.x, target.y, target.z);
    }

    /** From the death screen: respawn the way its button does, and wait until standing again. */
    private void respawn(Steps steps) {
        steps.waitTicks(20)
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("respawned", 300, () -> {
                    if (mc.player != null && mc.player.isAlive() && mc.screen instanceof DeathScreen) {
                        mc.setScreen(null);
                    }
                    return mc.player != null && mc.player.isAlive() && mc.screen == null;
                })
                .waitTicks(40);
    }

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false");
        if (part == Part.KEEP) {
            keep(steps);
        } else if (part == Part.DEATHS) {
            deaths(steps);
        } else {
            sites(steps);
        }
        steps.log("results", () -> String.join(" | ", results));
    }

    private void keep(Steps steps) {
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the Crown Spire is built", 400, () -> GuardianCommands.lastBuilt() != null)
                .waitUntil("the placing tick set its shrine down", 800, () -> ServerQuery.ask(p -> placed(p, ShrineKind.COLOSSUS) != null))
                .run("a few blocks back from it", () -> frame(ShrineKind.COLOSSUS, 6.0, 0.5))
                .waitUntil("the view is drawn", 400, LeviathanScenario.settled(mc, 300))
                .screenshot("shrine_1_view")
                .run("further back and higher", () -> frame(ShrineKind.COLOSSUS, 16.0, 4.0))
                .waitTicks(40)
                .screenshot("shrine_1_wide")
                .run("in front of the shrine, facing it", () -> besideShrine(ShrineKind.COLOSSUS))
                .waitUntil("the view is drawn", 400, LeviathanScenario.settled(mc, 300))
                .screenshot("shrine_1")
                .command("gamemode survival")
                .command("clear @s")
                .command("xp set @s 23 levels")
                .command("give @s minecraft:diamond 37")
                .command("item replace entity @s hotbar.1 with cosmicbreach:twin_comet_band")
                .waitUntil("the ring is in the hotbar", 40, () -> mc.player.getInventory().getItem(1).is(AccessoryRegistry.TWIN_COMET_BAND.get()))
                .press(mc.options.keyHotbarSlots[1])
                .waitUntil("the ring is in hand", 10, () -> mc.player.getMainHandItem().is(AccessoryRegistry.TWIN_COMET_BAND.get()))
                .run("look at the sky, not at the shrine (a use on it would save instead)", () -> CryptKit.aim(mc, mc.player.getEyePosition().add(0, 30, 0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the ring is worn", 60, this::ringWorn)
                .press(mc.options.keyHotbarSlots[0])
                .command("give @s cosmicbreach:meridian")
                .waitUntil("the sword arrived", 40, () -> inventory().containsKey("cosmicbreach:meridian"))
                .run("aim at the shrine", () -> CryptKit.aim(mc, Vec3.atCenterOf(shrine).add(0, 0.2, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the shrine keeps this player's place", 60, () -> ServerQuery.ask(ShrineSave::active))
                .run("what is carried", () -> carried = inventory())
                .log("carried", () -> "carried " + carried)
                .check("37 diamonds and the sword", () -> carried.getOrDefault("minecraft:diamond", 0) == 37
                        && carried.getOrDefault("cosmicbreach:meridian", 0) == 1)
                // within 48 blocks of the Breach's axis a fall is thrown onto the boss 4 arena: die well away from it
                .run("into the void, far from the Breach", () -> {
                    double r = Math.max(1.0, Math.hypot(shrine.getX(), shrine.getZ()));
                    double k = (r + 200.0) / r;
                    MpKit.send("tp @s %.1f -70 %.1f", shrine.getX() * k, shrine.getZ() * k);
                })
                .waitUntil("dead in the void", 300, this::dead);
        respawn(steps);
        steps.check("back in Aetheria beside the shrine", () -> AetheriaWorld.is(mc.level)
                        && mc.player.position().distanceTo(Vec3.atBottomCenterOf(shrine)) < 3.5)
                .check("everything it carried came back", () -> inventory().equals(carried))
                .check("its experience came back", () -> mc.player.experienceLevel == 23)
                .check("the ring is still worn", this::ringWorn)
                .run("kept", () -> results.add("kept " + inventory() + " level " + mc.player.experienceLevel))
                .command("spawnpoint @s")
                .waitUntil("a spawn point here ends the save", 40, () -> !ServerQuery.ask(ShrineSave::active))
                .command("kill @s")
                .waitUntil("dead", 300, this::dead)
                .waitUntil("without the save everything dropped as before", 60, () -> ServerQuery.ask(p -> {
                    int diamonds = 0;
                    boolean ring = false;
                    for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8.0))) {
                        diamonds += e.getItem().is(Items.DIAMOND) ? e.getItem().getCount() : 0;
                        ring |= e.getItem().is(AccessoryRegistry.TWIN_COMET_BAND.get());
                    }
                    return diamonds == 37 && ring;
                }))
                .run("clear the drops before the respawn can pick them up", () -> ServerQuery.ask(p -> {
                    p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8.0)).forEach(Entity::discard);
                    return true;
                }));
        respawn(steps);
        steps.check("and nothing came back with the player", () -> mc.player.getInventory().isEmpty() && !ringWorn())
                .run("dropped", () -> results.add("dropped as before"))
                .command("gamemode creative");
    }

    // ------------------------------------------------------------------ every death path

    /** What another mod that handles deaths does in this run (a stand-in for a grave or a revive mod). */
    private enum Rival { OFF, GRAVE_BEFORE, GRAVE_AFTER, CANCEL_AFTER }

    private static volatile Rival rival = Rival.OFF;
    private static final List<ItemStack> GRAVE = new ArrayList<>();
    private static boolean rivalsListening;
    /** What already lies about (a world's own drops, a flower knocked off a ledge) before a death: only what a death adds counts. */
    private Map<String, Integer> floorBase = Map.of();
    private int orbsBase;

    private static void takeIntoGrave(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                GRAVE.add(inv.getItem(i));
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** The rival listeners: one ahead of the shrine's keep, one registered after it at the same, last priority. */
    private static void listenAsRivals() {
        if (rivalsListening) {
            return;
        }
        rivalsListening = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingDeathEvent.class, event -> {
            if (rival == Rival.GRAVE_BEFORE && event.getEntity() instanceof ServerPlayer p) {
                takeIntoGrave(p);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, event -> {
            if (!(event.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            if (rival == Rival.GRAVE_AFTER) {
                takeIntoGrave(p);
            } else if (rival == Rival.CANCEL_AFTER) {
                event.setCanceled(true);
                p.setHealth(p.getMaxHealth());
            }
        });
    }

    private static Map<String, Integer> countOf(Iterable<ItemStack> stacks) {
        Map<String, Integer> out = new TreeMap<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * The carried kinds of item lying on the floor within 48 blocks of the player, counted by item (this world's own drops,
     * a flower knocked off a ledge, are none of a death's business).
     */
    private Map<String, Integer> floorItems(ServerPlayer p) {
        List<ItemStack> on = new ArrayList<>();
        p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(48.0)).forEach(e -> {
            if (carried.containsKey(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString())) {
                on.add(e.getItem());
            }
        });
        return countOf(on);
    }

    private static int floorOrbs(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(48.0)).size();
    }

    private static Map<String, Integer> minus(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new TreeMap<>(a);
        b.forEach((k, v) -> out.merge(k, -v, Integer::sum));
        out.values().removeIf(v -> v == 0);
        return out;
    }

    private static void sweepFloor(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(48.0)).forEach(Entity::discard);
        p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(48.0)).forEach(Entity::discard);
    }

    /** Fills the player's inventory the same way every time (main slots, armor and off hand) and sets level 23. */
    private void loadout(Steps steps) {
        steps.command("clear @s")
                .command("xp set @s 0 levels")
                .command("xp set @s 23 levels")
                .command("give @s minecraft:diamond 37")
                .command("give @s minecraft:cobblestone 76")
                .command("give @s cosmicbreach:meridian")
                .command("item replace entity @s armor.feet with minecraft:iron_boots")
                .command("item replace entity @s weapon.offhand with minecraft:shield")
                .waitUntil("the loadout arrived", 60, () -> inventory().size() == 5 && inventory().getOrDefault("minecraft:cobblestone", 0) == 76)
                .run("what is carried", () -> carried = inventory());
    }

    /** One death with the rival doing {@code how}, saved or not; then the checks, with the floor counted on the server. */
    private void death(Steps steps, String name, Rival how, boolean saved) {
        steps.run(name + ": set the rival", () -> {
            GRAVE.clear();
            rival = how;
        });
        if (!saved) {
            steps.command("spawnpoint @s")
                    .waitUntil("a spawn point here ends the save", 40, () -> !ServerQuery.ask(ShrineSave::active));
        }
        // /give leaves a "fake" item for a moment (the pickup animation): let it go before counting what lies about
        steps.waitTicks(20);
        steps.run(name + ": what lies about already", () -> ServerQuery.ask(p -> {
            floorBase = floorItems(p);
            orbsBase = floorOrbs(p);
            return true;
        }));
        steps.command("kill @s");
        if (how == Rival.CANCEL_AFTER) {
            steps.waitTicks(40)
                    .check(name + ": the player lived on", () -> mc.player.isAlive() && !dead())
                    .check(name + ": everything is in the inventory once", () -> inventory().equals(carried))
                    .log(name + ": the floor", () -> ServerQuery.ask(p -> "items " + floorItems(p) + " orbs " + floorOrbs(p)))
                    .check(name + ": nothing was added to the floor", () -> ServerQuery.ask(p -> floorItems(p).equals(floorBase) && floorOrbs(p) == orbsBase));
            steps.run(name + ": rival off", () -> rival = Rival.OFF);
            return;
        }
        steps.waitUntil(name + ": dead", 300, this::dead).waitTicks(30);
        if (saved) {
            steps.log(name + ": the floor", () -> ServerQuery.ask(p -> "items " + floorItems(p) + " (before " + floorBase + ") orbs " + floorOrbs(p)
                            + " (before " + orbsBase + ")"))
                    .check(name + ": nothing was added to the floor", () -> ServerQuery.ask(p -> floorItems(p).equals(floorBase) && floorOrbs(p) == orbsBase));
        } else {
            steps.check(name + ": everything dropped, exactly once, and the experience as orbs",
                    () -> ServerQuery.ask(p -> minus(floorItems(p), floorBase).equals(carried) && floorOrbs(p) > orbsBase));
            steps.run(name + ": clear the drops", () -> ServerQuery.ask(p -> {
                sweepFloor(p);
                return true;
            }));
        }
        respawn(steps);
        if (saved) {
            steps.check(name + ": the inventory and the grave hold each item exactly once", () -> {
                Map<String, Integer> all = new TreeMap<>(inventory());
                countOf(GRAVE).forEach((k, v) -> all.merge(k, v, Integer::sum));
                return all.equals(carried);
            });
            if (how == Rival.GRAVE_BEFORE) {
                steps.check(name + ": the keep had nothing to copy", () -> inventory().isEmpty());
            } else if (how == Rival.GRAVE_AFTER) {
                steps.check(name + ": the grave found an empty inventory", GRAVE::isEmpty);
            }
            steps.check(name + ": the experience came back", () -> mc.player.experienceLevel == 23);
        } else {
            steps.check(name + ": nothing came back with the player", () -> mc.player.getInventory().isEmpty());
        }
        steps.run(name + ": rival off", () -> rival = Rival.OFF)
                .run(name + ": done", () -> results.add(name + " ok"));
    }

    private void deaths(Steps steps) {
        listenAsRivals();
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the Crown Spire is built", 400, () -> GuardianCommands.lastBuilt() != null)
                .waitUntil("the placing tick set its shrine down", 800, () -> ServerQuery.ask(p -> placed(p, ShrineKind.COLOSSUS) != null))
                .run("in front of the shrine, facing it", () -> besideShrine(ShrineKind.COLOSSUS))
                .waitUntil("the view is drawn", 400, LeviathanScenario.settled(mc, 300))
                .command("gamemode survival");
        loadout(steps);
        steps.run("aim at the shrine", () -> CryptKit.aim(mc, Vec3.atCenterOf(shrine).add(0, 0.2, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the shrine keeps this player's place", 60, () -> ServerQuery.ask(ShrineSave::active))
                .log("carried", () -> "carried " + carried);
        // each path leaves the player at the shrine, standing, the save still active, and is followed by a fresh loadout
        death(steps, "saved death alone", Rival.OFF, true);
        loadout(steps);
        death(steps, "grave mod ahead of the keep", Rival.GRAVE_BEFORE, true);
        loadout(steps);
        death(steps, "grave mod after the keep", Rival.GRAVE_AFTER, true);
        loadout(steps);
        death(steps, "death cancelled by another mod after the keep", Rival.CANCEL_AFTER, true);
        death(steps, "unsaved death", Rival.OFF, false);
        steps.command("gamemode creative");
    }

    private void sites(Steps steps) {
        steps.command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("advancement grant @s only cosmicbreach:attunement/sanctum");
        // boss 2: the Rift's ledge
        steps.command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built", 400, () -> LeviathanScenario.layout() != null)
                .run("to its ledge", () -> {
                    Vec3 at = LeviathanScenario.layout().ledgeTop();
                    ColossusScenario.tp(mc, at.x, at.y, at.z, LeviathanScenario.layout().centre().x, at.y, LeviathanScenario.layout().centre().z);
                })
                .waitUntil("its shrine is placed", 800, () -> ServerQuery.ask(p -> placed(p, ShrineKind.LEVIATHAN) != null))
                .check("where its layout says, outside the sphere", () -> ServerQuery.ask(p -> {
                    RiftLayout l = LeviathanScenario.layout();
                    BlockPos at = placed(p, ShrineKind.LEVIATHAN).pos();
                    BlockPos want = ShrineSites.rift(l).pos();
                    results.add("boss 2 shrine " + at.toShortString() + " (site " + want.toShortString() + ")");
                    return at.distManhattan(want) <= 5 && l.shellFraction(at.getX(), at.getY(), at.getZ()) > 1.0;
                }))
                .run("in front of it", () -> besideShrine(ShrineKind.LEVIATHAN))
                .waitTicks(30)
                .screenshot("shrine_2")
                .run("a few blocks back from it", () -> frame(ShrineKind.LEVIATHAN, 6.0, 0.5))
                .waitTicks(40)
                .screenshot("shrine_2_view")
                .run("further back and higher", () -> frame(ShrineKind.LEVIATHAN, 14.0, 3.0))
                .waitTicks(40)
                .screenshot("shrine_2_wide");
        // boss 3: the nave's landing
        steps.command("cosmicbreach debug goto deep")
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach debug lair unsung")
                .waitUntil("the nave is built", 400, () -> UnsungCommands.lastBuilt() != null)
                .run("to its door", () -> {
                    NaveLayout l = UnsungCommands.lastBuilt();
                    int[] w = l.world(NaveLayout.LANDING + 6.0, 0.5);
                    int[] door = l.world(NaveLayout.DOOR_WALL, 0.5);
                    ColossusScenario.tp(mc, w[0] + 0.5, l.floorY() + 1, w[1] + 0.5, door[0] + 0.5, l.floorY() + 2, door[1] + 0.5);
                })
                .waitUntil("its shrine is placed", 800, () -> ServerQuery.ask(p -> placed(p, ShrineKind.UNSUNG) != null))
                .check("where its layout says", () -> ServerQuery.ask(p -> {
                    BlockPos at = placed(p, ShrineKind.UNSUNG).pos();
                    BlockPos want = ShrineSites.nave(UnsungCommands.lastBuilt()).pos();
                    results.add("boss 3 shrine " + at.toShortString() + " (site " + want.toShortString() + ")");
                    return at.distManhattan(want) <= 5;
                }))
                .run("in front of it", () -> besideShrine(ShrineKind.UNSUNG))
                .waitTicks(30)
                .screenshot("shrine_3")
                .run("a few blocks back from it", () -> frame(ShrineKind.UNSUNG, 6.0, 0.5))
                .waitTicks(40)
                .screenshot("shrine_3_view")
                .run("further back and higher", () -> frame(ShrineKind.UNSUNG, 14.0, 3.0))
                .waitTicks(40)
                .screenshot("shrine_3_wide");
        // boss 4: the Sanctum's antechamber
        steps.command("cosmicbreach debug place sanctum")
                .command("cosmicbreach debug goto sanctum hall")
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .waitUntil("its shrine is placed", 800, () -> ServerQuery.ask(p -> placed(p, ShrineKind.HELIARCH) != null))
                .check("where its layout says", () -> ServerQuery.ask(p -> {
                    BlockPos at = placed(p, ShrineKind.HELIARCH).pos();
                    BlockPos want = ShrineSites.sanctum(Sanctums.layout(p.serverLevel())).pos();
                    results.add("boss 4 shrine " + at.toShortString() + " (site " + want.toShortString() + ")");
                    return at.distManhattan(want) <= 5;
                }))
                .run("in front of it", () -> besideShrine(ShrineKind.HELIARCH))
                .waitTicks(30)
                .screenshot("shrine_4")
                .run("a few blocks back from it", () -> frame(ShrineKind.HELIARCH, 6.0, 0.5))
                .waitTicks(40)
                .screenshot("shrine_4_view")
                .run("further back and higher", () -> frame(ShrineKind.HELIARCH, 14.0, 3.0))
                .waitTicks(40)
                .screenshot("shrine_4_wide");
    }
}
