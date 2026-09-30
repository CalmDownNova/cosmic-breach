package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Mannequin;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.fx.GhostBodies;
import com.cosmicbreach.client.gear.DriftweaveLook;
import com.cosmicbreach.client.gear.GearClient;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.driftweave.Drift;
import com.cosmicbreach.gear.driftweave.DriftweaveRules;
import com.cosmicbreach.gear.driftweave.Slipstream;
import com.cosmicbreach.gear.regalia.Echoes;
import com.cosmicbreach.gear.regalia.Harmonics;
import com.cosmicbreach.gear.regalia.HymnRings;
import com.cosmicbreach.gear.regalia.RegaliaRules;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetArmorItem;
import com.cosmicbreach.gear.set.SetState;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

/**
 * The Driftweave and the Choir Regalia, end to end through the real input paths (the use key to put the pieces on,
 * the dash, attack, use and Set Ability keys), measured:
 * <ol>
 *   <li>The looks: each set on a mannequin in daylight from the front, the side and the back, standing and moving
 *       (the Driftweave's scarf and cape streaming and its fins flared; the Regalia's mantle lifted).</li>
 *   <li>The Driftweave put on: armor 18, toughness 4, Agility 8, Power 4; 3 dash charges with no Agility allocated
 *       (2 and the set's 1); a backstep 20% longer than without it (measured over the dash's 8 ticks); the fourth
 *       dash in a row refused.</li>
 *   <li>Slipstream: a zombie strikes into a backstep's perfect dodge window; the Afterimage stands where the blow
 *       passed; the next three attacks (L1, L2, L3), thrown from out of reach, land from the Afterimage at 40% each;
 *       a fourth attack is not repeated; the player's own L1 for the ratio.</li>
 *   <li>Drift on its key: gravity 0.4x on both sides, a jump 1.5 times the low-gravity one, a dash in the air with no
 *       charge left, an L1 off the ground 20% harder than on it; then everything back.</li>
 *   <li>The Regalia put on: armor 20, toughness 10, Arcane 8, Resilience 4; the out-of-combat drift settles at 50% of
 *       124; Zenith costs 27 instead of 30; three Zeniths within 10 s: the third echoes 10 ticks later (its hit at the
 *       move's first active tick) for 60%; the Hymn on its key: +30 Haste (Zenith's cooldown 71 instead of 90) and
 *       double Resonance (an L1 gives 12) inside the ring, the zombie Aligned and taking 10% more from Zenith.</li>
 * </ol>
 */
public final class SetsScenario implements Scenario {
    /** Which part runs: everything, one set's mechanics, or the looks. */
    public enum Part { ALL, DRIFTWEAVE, REGALIA, LOOKS }

    private static final String DUMMY = "cosmicbreach_sets_dummy";
    private static final ResourceLocation L1 = CosmicBreach.id("meridian/l1");
    private static final ResourceLocation L2 = CosmicBreach.id("meridian/l2");
    private static final ResourceLocation L3 = CosmicBreach.id("meridian/l3");
    private static final ResourceLocation ZENITH = CosmicBreach.id("meridian/zenith");
    private static final String PLAYER_ATTACK = "minecraft:player_attack";
    private static final String AFTERIMAGE = "cosmicbreach:afterimage";
    private static final String ECHO = "cosmicbreach:echo";

    /** A hit a dummy took: its type, the amount after every modifier and before armor (the dummies have none), when. */
    private record Hit(String type, float amount, long time) {}

    private final Part part;
    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> summary = new ArrayList<>();
    private final Vec3[] dashFrom = {null};
    private final int[] dashTick = {Integer.MIN_VALUE};
    private final double[] dashDistance = {Double.NaN};
    private final double[] measured = new double[16];
    /** The highest ability cooldown the server's machine showed since the last reset (a cast sets it, then it counts down). */
    private final int[] serverCooldownPeak = {0};
    private @Nullable DevCamera camera;
    private @Nullable Mannequin mannequin;

    public SetsScenario() {
        this(Part.ALL);
    }

    public SetsScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return part == Part.ALL ? 600 : 360;
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(DUMMY)) {
                hits.add(new Hit(event.getSource().typeHolder().getRegisteredName(), event.getAmount(),
                        event.getEntity().level().getGameTime()));
            }
        });
        ClientCombat.addEventListener(event -> {
            events.add(event);
            Minecraft mc = Minecraft.getInstance();
            if (event instanceof CombatEvent.DashStarted && mc.player != null) {
                dashFrom[0] = mc.player.position();
                dashTick[0] = mc.player.tickCount;
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                serverCooldownPeak[0] = Math.max(serverCooldownPeak[0], PlayerCombat.of(player).machine().abilityCooldown());
            }
        });
        // the dash's reach: where the player is 8 ticks (8 moves) after the tick it started
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Pre.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (event.getEntity() == mc.player && dashFrom[0] != null && mc.player.tickCount == dashTick[0] + com.cosmicbreach.combat.core.CombatRules.DASH_TICKS) {
                Vec3 d = mc.player.position().subtract(dashFrom[0]);
                dashDistance[0] = Math.sqrt(d.x * d.x + d.z * d.z);
            }
        });
    }

    /** Server ticks left to watch for the dash's perfect-dodge window (set by a step, counted on the server). */
    private volatile int dodgeWatch;
    /** True once the zombie struck into that window. */
    private volatile boolean dodgeStruck;

    /**
     * The perfect-dodge window is two server ticks: a client tick can miss it when the server runs behind, so it is
     * watched on the server's own ticks (as the fx scenario does), and the zombie strikes on the first one that finds
     * the dash in it.
     */
    private void watchDodgeWindow(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (dodgeWatch <= 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (PlayerCombat.of(player).machine().inPerfectDodgeWindow()) {
                dodgeWatch = 0;
                try {
                    zombieStrikes(player);
                    dodgeStruck = true;
                } catch (RuntimeException e) {
                    CosmicBreach.LOGGER.error("[cosmicbreach] sets: the zombie couldn't strike", e); // never into the server's tick
                }
                return;
            }
        }
        dodgeWatch--;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, this::watchDodgeWindow);
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("clear @s")
                .command("gamerule doDaylightCycle false")
                .command("time set noon")
                .command("weather clear");
        if (part == Part.ALL || part == Part.LOOKS) {
            looks(steps, mc);
        }
        if (part == Part.ALL || part == Part.DRIFTWEAVE) {
            driftweave(steps, mc);
            slipstream(steps, mc);
            drift(steps, mc);
        }
        if (part == Part.ALL || part == Part.REGALIA) {
            regalia(steps, mc);
            harmonics(steps, mc);
            hymn(steps, mc);
        }
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ 1. the looks

    private void looks(Steps steps, Minecraft mc) {
        for (GearRegistry.SetItems set : GearSets.sets()) {
            String name = set.set().id().getPath();
            Vec3[] at = {Vec3.ZERO};
            steps.run("a mannequin in the " + name + ", 6 blocks off", () -> {
                        at[0] = mc.player.position().add(-6.0, 0, 0);
                        mannequin = Mannequin.spawn(mc.level, at[0], 0f, new ItemStack(ModItems.MERIDIAN.get()));
                        for (DeferredItem<SetArmorItem> piece : set.all()) {
                            mannequin.body().setItemSlot(piece.get().getEquipmentSlot(), new ItemStack(piece.get()));
                        }
                        mannequin.body().setData(GearRegistry.SET_STATE, SetState.NONE.withMeter(set.set().id(), 3f, Long.MAX_VALUE / 4));
                        camera = DevCamera.create(mc.level);
                        camera.use();
                        mc.options.hideGui = true;
                    })
                    .run("front", () -> camera.place(at[0].add(0, 1.2, 3.3), at[0].add(0, 1.05, 0)))
                    .waitTicks(5)
                    .screenshot(name + "_front")
                    .run("side", () -> camera.place(at[0].add(3.3, 1.2, 0), at[0].add(0, 1.05, 0)))
                    .waitTicks(4)
                    .screenshot(name + "_side")
                    .run("back", () -> camera.place(at[0].add(0, 1.2, -3.3), at[0].add(0, 1.05, 0)))
                    .waitTicks(4)
                    .screenshot(name + "_back")
                    .run("one charge ready (the Driftweave's bands), an echo primed (the Regalia's halo)", () ->
                            mannequin.body().setData(GearRegistry.SET_STATE, SetState.NONE.withMeter(set.set().id(),
                                    set.set() == GearSets.DRIFTWEAVE.set() ? 1f : 2f, Long.MAX_VALUE / 4)))
                    .waitTicks(3)
                    .screenshot(name + "_back_meter")
                    .run("back to full", () -> mannequin.body().setData(GearRegistry.SET_STATE,
                            SetState.NONE.withMeter(set.set().id(), set.set() == GearSets.DRIFTWEAVE.set() ? 3f : 0f, Long.MAX_VALUE / 4)))
                    .run("three quarters", () -> camera.place(at[0].add(-2.4, 1.7, 2.4), at[0].add(0, 1.05, 0)))
                    .waitTicks(4)
                    .screenshot(name + "_34")
                    .run("the mannequin walks across the view", () -> {
                        mannequin.moveTo(at[0].add(0, 0, -1.2));
                        mannequin.walk(new Vec3(0, 0, 0.1));
                        camera.place(at[0].add(3.4, 1.2, 0), at[0].add(0, 1.0, 0));
                    })
                    .waitTicks(12)
                    .screenshot(name + "_walking_side")
                    .run("the mannequin runs across the view", () -> {
                        mannequin.moveTo(at[0].add(0, 0, -3.0));
                        mannequin.walk(new Vec3(0, 0, 0.28));
                        camera.place(at[0].add(3.6, 1.3, 0), at[0].add(0, 1.0, 0));
                    })
                    .waitTicks(10)
                    .run("its fins flare (a dash)", () -> DriftweaveLook.dashed(mannequin.body()))
                    .waitTicks(1)
                    .screenshot(name + "_running_side")
                    .run("from behind as it runs away", () -> camera.place(at[0].add(0, 1.7, -4.2), at[0].add(0, 1.0, 2.0)))
                    .waitTicks(2)
                    .screenshot(name + "_running_back")
                    .run("stop and clean up", () -> {
                        mannequin.remove();
                        mannequin = null;
                        dropCamera();
                    });
        }
    }

    // ------------------------------------------------------------------ 2. the Driftweave

    private void driftweave(Steps steps, Minecraft mc) {
        steps.command("give @s cosmicbreach:meridian")
                .waitUntil("Meridian is in the inventory", 40, () -> count(mc, ModItems.MERIDIAN.get()) == 1)
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitUntil("idle with both charges", 100, () -> ready(mc) && machine(mc).dashCharges() == 2)
                .run("forget the last dash", () -> dashDistance[0] = Double.NaN)
                .press(ModKeyMappings.DASH)
                .waitUntil("the backstep's 8 ticks are measured", 40, () -> !Double.isNaN(dashDistance[0]))
                .run("remember the plain backstep", () -> {
                    measured[0] = dashDistance[0];
                    summary.add(String.format(Locale.ROOT, "backstep without the set: %.3f blocks over its 8 ticks", measured[0]));
                })
                .check("a plain backstep covers 5 blocks", () -> Math.abs(measured[0] - 5.0) < 0.05);
        wear(steps, mc, GearSets.DRIFTWEAVE);
        steps.waitUntil("armor 18 and toughness 4 on both sides", 60, () ->
                        near(mc.player.getAttributeValue(Attributes.ARMOR), 18) && near(mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 4)
                                && ServerQuery.ask(p -> near(p.getAttributeValue(Attributes.ARMOR), 18)
                                && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 4)))
                .waitUntil("+8 Agility and +4 Power on both sides", 60, () -> {
                    StatBlock c = ProgressionStats.of(mc.player);
                    return c.agility() == 8 && c.power() == 4
                            && ServerQuery.ask(p -> ProgressionStats.of(p).agility() == 8 && ProgressionStats.of(p).power() == 4);
                })
                .log("driftweave worn", () -> ServerQuery.ask(p -> {
                    String s = String.format(Locale.ROOT, "Driftweave: armor %.1f, toughness %.1f, stats %s, allocated Agility %d",
                            p.getAttributeValue(Attributes.ARMOR), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), ProgressionStats.of(p),
                            (int) p.getAttribute(com.cosmicbreach.registry.ModAttributes.of(com.cosmicbreach.combat.core.Stat.AGILITY)).getBaseValue());
                    summary.add(s);
                    return s;
                }))
                .check("3 dash charges on both sides with no Agility allocated (2 + the set's 1)", () ->
                        machine(mc).maxDashCharges() == 3 && ServerQuery.ask(p -> PlayerCombat.of(p).machine().maxDashCharges() == 3))
                .waitUntil("all three charges ready", 200, () -> ready(mc) && machine(mc).dashCharges() == 3)
                .run("forget the last dash", () -> dashDistance[0] = Double.NaN)
                .press(ModKeyMappings.DASH)
                .waitUntil("the backstep's 8 ticks are measured", 40, () -> !Double.isNaN(dashDistance[0]))
                .run("remember the Driftweave's backstep", () -> {
                    measured[1] = dashDistance[0];
                    summary.add(String.format(Locale.ROOT, "backstep in the Driftweave: %.3f blocks, x%.3f", measured[1],
                            measured[1] / measured[0]));
                })
                .check("the dash goes 20% further", () -> Math.abs(measured[1] / measured[0] - 1.2) < 0.01)
                .waitUntil("the dash is over", 20, () -> !machine(mc).isDashing())
                .press(ModKeyMappings.DASH)
                .waitUntil("the second is over", 20, () -> !machine(mc).isDashing() && machine(mc).dashCharges() <= 1)
                .press(ModKeyMappings.DASH)
                .waitUntil("the third is over", 20, () -> !machine(mc).isDashing())
                .run("forget the events", events::clear)
                .check("no charge left", () -> machine(mc).dashCharges() == 0)
                .press(ModKeyMappings.DASH)
                .waitTicks(1)
                .check("a fourth dash in a row is refused", () -> events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d
                        && d.action() == CombatEvent.Action.DASH) && events.stream().noneMatch(e -> e instanceof CombatEvent.DashStarted))
                .run("note it", () -> summary.add("three dashes in a row, the fourth refused"));
        reforgedAndRecipes(steps, mc, GearSets.DRIFTWEAVE);
    }

    /**
     * A piece reforged a tier up (the tier component): its armor and toughness x1.15 and its icon's trim in the new
     * tier's colour; then the plain one back. And the set's four Forge recipes, at the set's tier, as the GDD lists them.
     */
    private void reforgedAndRecipes(Steps steps, Minecraft mc, GearRegistry.SetItems set) {
        ArmorSet armor = set.set();
        int tier = armor.tier() + 1;
        String helmet = BuiltInRegistries.ITEM.getKey(set.helmet().get()).toString();
        int helmArmor = armor.piece(net.minecraft.world.item.ArmorItem.Type.HELMET).orElseThrow().armor();
        double expectedArmor = armor.totalArmor() - helmArmor + helmArmor * 1.15;
        double expectedToughness = armor.toughness() * 3 + armor.toughness() * 1.15;
        steps.command("item replace entity @s armor.head with " + helmet + "[cosmicbreach:gear_tier=" + tier + "]")
                .waitUntil("a helmet reforged to tier " + tier + ": its armor and toughness x1.15", 40, () -> ServerQuery.ask(p ->
                        near(p.getAttributeValue(Attributes.ARMOR), expectedArmor)
                                && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), expectedToughness)))
                .check("its icon's trim takes the tier's colour", () -> {
                    int trim = GearClient.trimColor(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
                    return trim == (0xFF000000 | com.cosmicbreach.item.GearTier.color(tier));
                })
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "%s reforged to T%d: armor %.2f, toughness %.2f, trim #%06X",
                        armor.id().getPath(), tier, ServerQuery.ask(p -> p.getAttributeValue(Attributes.ARMOR)),
                        ServerQuery.ask(p -> p.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
                        GearClient.trimColor(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) & 0xFFFFFF)))
                .command("item replace entity @s armor.head with " + helmet)
                .waitUntil("the plain helmet back", 40, () -> ServerQuery.ask(p -> near(p.getAttributeValue(Attributes.ARMOR), armor.totalArmor())))
                .check("the Forge makes each piece at the set's tier", () -> {
                    for (DeferredItem<SetArmorItem> piece : set.all()) {
                        ResourceLocation id = CosmicBreach.id("forge/" + piece.getId().getPath());
                        var holder = mc.level.getRecipeManager().byKey(id).orElse(null);
                        if (holder == null || !(holder.value() instanceof com.cosmicbreach.gear.forge.ForgeRecipe recipe)
                                || recipe.tier() != armor.tier() || !recipe.result().is(piece.get())) {
                            return false;
                        }
                        StringBuilder need = new StringBuilder();
                        for (var ingredient : recipe.ingredients()) {
                            ItemStack[] items = ingredient.getItems();
                            need.append(' ').append(ingredient.count()).append(' ')
                                    .append(items.length == 0 ? "?" : BuiltInRegistries.ITEM.getKey(items[0].getItem()).getPath());
                        }
                        summary.add("Forge " + armor.tier() + ": " + piece.getId().getPath() + " =" + need);
                    }
                    return true;
                });
    }

    // ------------------------------------------------------------------ 3. Slipstream

    private void slipstream(Steps steps, Minecraft mc) {
        KeyMapping attack = mc.options.keyAttack;
        long[] dodgeAt = {0};
        steps.run("clear the dummies", SetsScenario::discardDummies)
                .look(0, 0)
                .waitUntil("idle, standing still, all three charges back", 200, () -> ready(mc) && machine(mc).dashCharges() == 3
                        && mc.player.getDeltaMovement().horizontalDistance() < 0.01)
                .run("a zombie 1.8 blocks ahead", () -> spawnAhead(1.8))
                .waitUntil("the zombie stands there", 40, () -> dummyCount(mc) == 1)
                .run("forget", () -> {
                    events.clear();
                    hits.clear();
                })
                .run("watch the server's next 20 ticks for the perfect-dodge window", () -> {
                    dodgeStruck = false;
                    dodgeWatch = 20;
                })
                .press(ModKeyMappings.DASH)
                .waitUntil("the server's watch is over", 200, () -> dodgeStruck || dodgeWatch <= 0)
                .check("the zombie struck into the server's perfect dodge window", () -> dodgeStruck)
                .waitUntil("the perfect dodge left an Afterimage on the server", 10, () -> ServerQuery.ask(p -> Slipstream.of(p) != null))
                .run("remember when", () -> dodgeAt[0] = mc.level.getGameTime())
                .waitUntil("this client draws it", 10, () -> GhostBodies.count() >= 1)
                .log("the afterimage", () -> ServerQuery.ask(p -> {
                    Slipstream.Afterimage image = Slipstream.of(p);
                    String s = String.format(Locale.ROOT, "Afterimage at %.2f blocks from the zombie, %d repeats left",
                            image.feet().distanceTo(dummies(p).get(0).position()), image.repeatsLeft());
                    summary.add(s);
                    return s;
                }))
                .waitUntil("the dash is over and the player stands still", 30, () -> ready(mc)
                        && mc.player.getDeltaMovement().horizontalDistance() < 0.01)
                .run("third person, from the side", () -> sideCamera(mc))
                .waitTicks(1)
                .screenshot("afterimage_appears")
                .run("back to the player's eyes, so the server hears where the dash went", this::dropCamera)
                .waitTicks(2)
                .waitUntil("the guaranteed crit's window is over", 60,
                        () -> mc.level.getGameTime() - dodgeAt[0] >= 21 && ready(mc) && !machine(mc).isCritGuaranteedWindow())
                .check("the player stands out of Meridian's reach of the zombie", () -> ServerQuery.ask(p ->
                        dummies(p).get(0).distanceTo(p) > 6.0))
                .run("forget the hits", hits::clear)
                .run("the side view (the player stands still, so looking away from it loses nothing)", () -> sideCamera(mc))
                // three attacks: taps at T, T+8, T+17 chain L1, L2, L3 (the combo scenario's timing)
                .hold(attack).waitTicks(1).release(attack)
                .waitTicks(3)
                .screenshot("afterimage_repeats_l1")
                .waitTicks(4)
                .hold(attack).waitTicks(1).release(attack)
                .waitTicks(8)
                .hold(attack).waitTicks(1).release(attack)
                .waitTicks(8)
                .screenshot("afterimage_repeats_l3")
                .waitUntil("the chain ran and the copies landed", 80, () -> machine(mc).phase() == Phase.IDLE
                        && hits.stream().filter(h -> h.type().equals(AFTERIMAGE)).count() >= 3)
                .run("back to the player's eyes", this::dropCamera)
                .waitTicks(10)
                .check("the zombie took the three copies and nothing else", () -> hits.size() == 3
                        && hits.stream().allMatch(h -> h.type().equals(AFTERIMAGE)))
                .check("each copy dealt 40% of the plain hit it repeated (L1, L2, L3)", () -> {
                    double[] plain = ServerQuery.ask(p -> new double[] {plainHit(p, L1), plainHit(p, L2), plainHit(p, L3)});
                    boolean ok = true;
                    for (int i = 0; i < 3; i++) {
                        double expected = DriftweaveRules.AFTERIMAGE_DAMAGE * plain[i];
                        summary.add(String.format(Locale.ROOT, "copy %d: %.3f (expected 0.4 x %.3f = %.3f)", i + 1, hits.get(i).amount(),
                                plain[i], expected));
                        ok &= Math.abs(hits.get(i).amount() - expected) < 1e-3;
                    }
                    measured[2] = hits.get(0).amount();
                    return ok;
                })
                .check("its repeats are spent", () -> ServerQuery.ask(p -> Slipstream.of(p) == null || Slipstream.of(p).repeatsLeft() == 0))
                .run("forget the hits", hits::clear)
                .waitUntil("idle", 40, () -> ready(mc))
                .waitTicks(10)
                .hold(attack).waitTicks(1).release(attack)
                .waitUntil("the attack is over", 40, () -> ready(mc))
                .waitTicks(10)
                .check("a fourth attack is not repeated", () -> hits.isEmpty())
                .waitUntil("the Afterimage faded (60 ticks and its repeats done)", 60, () -> ServerQuery.ask(p -> Slipstream.of(p) == null))
                // the player's own L1 on the zombie, for the ratio
                .command("tp @e[tag=" + DUMMY + "] ^ ^ ^2.2")
                .waitTicks(5)
                .run("forget the hits", hits::clear)
                .hold(attack).waitTicks(1).release(attack)
                .waitUntil("the L1 landed", 40, () -> !hits.isEmpty())
                .check("the copies were 40% of the player's own L1", () -> {
                    Hit own = hits.get(0);
                    double plain = ServerQuery.ask(p -> plainHit(p, L1));
                    double critL1 = ServerQuery.ask(p -> critHit(p, L1));
                    boolean crit = Math.abs(own.amount() - critL1) < 1e-3;
                    double ownPlain = crit ? own.amount() * plain / critL1 : own.amount();
                    summary.add(String.format(Locale.ROOT, "the player's own L1: %.3f%s; first copy / own = %.4f", own.amount(),
                            crit ? " (a crit: its plain share taken)" : "", measured[2] / ownPlain));
                    return own.type().equals(PLAYER_ATTACK) && Math.abs(measured[2] / ownPlain - 0.4) < 1e-3;
                })
                .waitUntil("idle", 40, () -> ready(mc));
    }

    // ------------------------------------------------------------------ 4. Drift

    private void drift(Steps steps, Minecraft mc) {
        KeyMapping attack = mc.options.keyAttack;
        double[] start = {0};
        double[] top = {0};
        boolean[] tracking = {false};
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (tracking[0] && event.getEntity() == mc.player) {
                top[0] = Math.max(top[0], mc.player.getY() - start[0]);
            }
        });
        steps.run("clear the dummies", SetsScenario::discardDummies)
                .look(0, 0)
                .waitUntil("idle on the ground", 60, () -> ready(mc) && mc.player.onGround());
        jump(steps, mc, start, top, tracking, "a plain jump");
        steps.run("remember the plain jump", () -> {
                    measured[3] = top[0];
                    summary.add(String.format(Locale.ROOT, "plain jump: %.4f blocks", measured[3]));
                })
                .check("the ability is ready", () -> ServerQuery.ask(p -> ArmorSets.state(p).ready(p.level().getGameTime())))
                .press(GearClient.SET_ABILITY)
                .waitUntil("Drift runs on both sides", 20, () -> Drift.active(mc.player) && ServerQuery.ask(Drift::active))
                .waitUntil("the gravity attribute is 0.4x on both sides", 20, () ->
                        near(mc.player.getAttributeValue(Attributes.GRAVITY), 0.032)
                                && ServerQuery.ask(p -> near(p.getAttributeValue(Attributes.GRAVITY), 0.032)))
                .check("the cooldown started: 560 ticks", () -> ServerQuery.ask(p -> ArmorSets.state(p).cooldownTicks() == 560))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Drift: gravity %.4f, jump strength %.4f (x%.4f)",
                        mc.player.getAttributeValue(Attributes.GRAVITY), mc.player.getAttributeValue(Attributes.JUMP_STRENGTH),
                        DriftweaveRules.driftJumpMultiplier())))
                .run("third person from the side", () -> sideCamera(mc))
                .waitTicks(1)
                .screenshot("drift_start")
                .run("back to the player's eyes", this::dropCamera);
        jump(steps, mc, start, top, tracking, "a Drift jump");
        steps.run("remember the Drift jump", () -> measured[4] = top[0])
                .check("the jump rose 1.5 times as high as the low gravity alone would take it", () -> {
                    double gravity = DriftweaveRules.VANILLA_GRAVITY * DriftweaveRules.DRIFT_GRAVITY;
                    double alone = DriftweaveRules.jumpApex(DriftweaveRules.VANILLA_JUMP, gravity);
                    double expected = DriftweaveRules.jumpApex(DriftweaveRules.VANILLA_JUMP * DriftweaveRules.driftJumpMultiplier(), gravity);
                    summary.add(String.format(Locale.ROOT, "Drift jump: %.4f blocks (expected %.4f; 0.4x gravity alone %.4f; x%.3f of it, "
                            + "x%.3f of a plain jump)", measured[4], expected, alone, measured[4] / alone, measured[4] / measured[3]));
                    return Math.abs(measured[4] - expected) < 0.05 * expected;
                })
                // a dash in the air with no charge left
                .run("empty the dash charges on the server", () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    m.syncFromServer(m.resonance(), 0, m.abilityCooldown());
                    return true;
                }))
                .waitUntil("this client has no charge either", 20, () -> machine(mc).dashCharges() == 0)
                .run("forget the events", events::clear)
                .press(mc.options.keyJump)
                .waitUntil("airborne", 10, () -> !mc.player.onGround())
                .waitTicks(4)
                .check("still no charge", () -> machine(mc).dashCharges() == 0)
                .press(ModKeyMappings.DASH)
                .waitTicks(1)
                .check("the air dash went off with no charge", () -> events.stream().anyMatch(e -> e instanceof CombatEvent.DashStarted)
                        && machine(mc).dashCharges() == 0)
                .waitUntil("the server's machine dashed too and spent nothing", 10, () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    return m.isDashing() && m.dashCharges() == 0;
                }))
                .run("note it", () -> summary.add("an air dash under Drift with 0 charges: it went, 0 left on both sides"))
                .waitUntil("back on the ground and idle", 80, () -> mc.player.onGround() && ready(mc))
                // an attack off the ground: +20%
                .run("a zombie 2.2 blocks ahead", () -> spawnAhead(2.2))
                .waitUntil("the zombie stands there", 40, () -> dummyCount(mc) == 1)
                .run("forget the hits", hits::clear)
                .command("tp @s ~ ~1 ~")
                .waitUntil("airborne", 10, () -> !mc.player.onGround())
                .hold(attack).waitTicks(1).release(attack)
                .waitUntil("the L1 landed", 20, () -> !hits.isEmpty())
                .check("it landed while airborne", () -> !mc.player.onGround())
                .run("remember it", () -> measured[5] = hits.get(0).amount())
                .waitUntil("back on the ground and idle, the chain reset", 80, () -> mc.player.onGround() && ready(mc))
                .waitTicks(12)
                .command("tp @e[tag=" + DUMMY + "] ^ ^ ^2.2")
                .waitTicks(3)
                .run("forget the hits", hits::clear)
                .check("Drift still runs", () -> Drift.active(mc.player))
                .hold(attack).waitTicks(1).release(attack)
                .waitUntil("the L1 landed", 20, () -> !hits.isEmpty())
                .check("the aerial L1 dealt 20% more than the grounded one", () -> {
                    double grounded = hits.get(0).amount();
                    double plain = ServerQuery.ask(p -> plainHit(p, L1));
                    double crit = ServerQuery.ask(p -> critHit(p, L1));
                    double air = measured[5];
                    double airPlain = Math.abs(air - 1.2 * crit) < 1e-3 ? air * plain / crit : air;
                    double groundPlain = Math.abs(grounded - crit) < 1e-3 ? grounded * plain / crit : grounded;
                    summary.add(String.format(Locale.ROOT, "Drift L1 in the air %.3f, on the ground %.3f: x%.4f", air, grounded,
                            airPlain / groundPlain));
                    return Math.abs(airPlain / groundPlain - 1.2) < 1e-3 && Math.abs(groundPlain - plain) < 1e-3;
                })
                .waitUntil("Drift ends", 120, () -> !Drift.active(mc.player) && ServerQuery.ask(p -> !Drift.active(p)))
                .waitUntil("the gravity and the jump are back on both sides", 20, () ->
                        near(mc.player.getAttributeValue(Attributes.GRAVITY), 0.08) && near(mc.player.getAttributeValue(Attributes.JUMP_STRENGTH), 0.42)
                                && ServerQuery.ask(p -> near(p.getAttributeValue(Attributes.GRAVITY), 0.08)
                                && near(p.getAttributeValue(Attributes.JUMP_STRENGTH), 0.42)))
                .waitUntil("idle on the ground", 60, () -> ready(mc) && mc.player.onGround());
        jump(steps, mc, start, top, tracking, "a jump after Drift");
        steps.check("jumps are plain again", () -> {
                    summary.add(String.format(Locale.ROOT, "after Drift: jump %.4f", top[0]));
                    return Math.abs(top[0] - measured[3]) < 0.02;
                })
                .run("empty the dash charges on the server", () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    m.syncFromServer(m.resonance(), 0, m.abilityCooldown());
                    return true;
                }))
                .waitUntil("this client has no charge either", 20, () -> machine(mc).dashCharges() == 0)
                .run("forget the events", events::clear)
                .press(mc.options.keyJump)
                .waitUntil("airborne", 10, () -> !mc.player.onGround())
                .waitTicks(2)
                .press(ModKeyMappings.DASH)
                .waitTicks(1)
                .check("an air dash with no charge is refused again", () -> events.stream().noneMatch(e -> e instanceof CombatEvent.DashStarted)
                        && events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d && d.action() == CombatEvent.Action.DASH))
                .waitUntil("back on the ground", 60, () -> mc.player.onGround())
                .run("clear the dummies", SetsScenario::discardDummies);
    }

    /** A jump with the jump key, tracking the highest the feet get above where they started. */
    private void jump(Steps steps, Minecraft mc, double[] start, double[] top, boolean[] tracking, String what) {
        steps.waitUntil("standing on the ground", 80, () -> mc.player.onGround())
                .run("start tracking " + what, () -> {
                    start[0] = mc.player.getY();
                    top[0] = 0;
                    tracking[0] = true;
                })
                .press(mc.options.keyJump)
                .waitUntil(what + " left the ground", 10, () -> !mc.player.onGround())
                .waitUntil(what + " came down", 160, () -> mc.player.onGround())
                .run("stop tracking", () -> tracking[0] = false);
    }

    // ------------------------------------------------------------------ 5. the Choir Regalia

    private void regalia(Steps steps, Minecraft mc) {
        if (part == Part.REGALIA) {
            steps.command("give @s cosmicbreach:meridian")
                    .waitUntil("Meridian is in the inventory", 40, () -> count(mc, ModItems.MERIDIAN.get()) == 1)
                    .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()));
        }
        wear(steps, mc, GearSets.REGALIA);
        steps.waitUntil("armor 20 and toughness 10 on both sides", 60, () ->
                        near(mc.player.getAttributeValue(Attributes.ARMOR), 20) && near(mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 10)
                                && ServerQuery.ask(p -> near(p.getAttributeValue(Attributes.ARMOR), 20)
                                && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 10)))
                .waitUntil("+8 Arcane and +4 Resilience on both sides", 60, () -> {
                    StatBlock c = ProgressionStats.of(mc.player);
                    return c.arcane() == 8 && c.resilience() == 4
                            && ServerQuery.ask(p -> ProgressionStats.of(p).arcane() == 8 && ProgressionStats.of(p).resilience() == 4);
                })
                .log("regalia worn", () -> ServerQuery.ask(p -> {
                    String s = String.format(Locale.ROOT, "Choir Regalia: armor %.1f, toughness %.1f, stats %s, max Resonance %d",
                            p.getAttributeValue(Attributes.ARMOR), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), ProgressionStats.of(p),
                            PlayerCombat.of(p).machine().maxResonance());
                    summary.add(s);
                    return s;
                }))
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .run("Resonance 80 on the server", () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    m.syncFromServer(80, m.dashCharges(), m.abilityCooldown());
                    return true;
                }))
                .waitUntil("out of combat, Resonance settles at 50% of 124 on both sides", 400, () ->
                        near(machine(mc).resonance(), 62.0) && ServerQuery.ask(p -> near(PlayerCombat.of(p).machine().resonance(), 62.0)))
                .waitTicks(30)
                .check("and stays there", () -> near(machine(mc).resonance(), 62.0)
                        && ServerQuery.ask(p -> near(PlayerCombat.of(p).machine().resonance(), 62.0)))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "out-of-combat drift settled at %.2f of %d (%.0f%%)",
                        machine(mc).resonance(), machine(mc).maxResonance(), 100 * machine(mc).resonance() / machine(mc).maxResonance())))
                .check("Zenith costs 27 on both sides (30 less 10%)", () -> near(machine(mc).abilityCost(), 27.0)
                        && ServerQuery.ask(p -> near(PlayerCombat.of(p).machine().abilityCost(), 27.0)));
        reforgedAndRecipes(steps, mc, GearSets.REGALIA);
    }

    // ------------------------------------------------------------------ 6. Harmonics

    private void harmonics(Steps steps, Minecraft mc) {
        KeyMapping use = mc.options.keyUse;
        long[] third = {0};
        double[] before = {0};
        steps.run("clear the dummies", SetsScenario::discardDummies)
                .look(0, 0)
                .waitTicks(3)
                .run("a zombie 1.6 blocks ahead (200 health: too heavy for Zenith to launch)", () -> spawnAhead(1.6))
                .waitUntil("the zombie stands there", 40, () -> dummyCount(mc) == 1)
                .run("full Resonance on the server", () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    m.syncFromServer(m.maxResonance(), m.dashCharges(), m.abilityCooldown());
                    return true;
                }))
                .run("forget the hits", hits::clear)
                .waitUntil("idle", 40, () -> ready(mc))
                // an L1 first: in combat, Resonance stops drifting toward half (and stays full)
                .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                .waitUntil("the L1 landed", 20, () -> hits.size() == 1)
                .waitUntil("full Resonance on both sides", 40, () -> near(machine(mc).resonance(), 124.0)
                        && ServerQuery.ask(p -> near(PlayerCombat.of(p).machine().resonance(), 124.0)))
                .waitUntil("idle again", 40, () -> ready(mc))
                .run("forget", () -> {
                    hits.clear();
                    before[0] = ServerQuery.ask(p -> PlayerCombat.of(p).machine().resonance());
                })
                .waitUntil("Zenith is ready", 40, () -> ready(mc) && machine(mc).abilityCooldown() == 0)
                .run("reset the cooldown peak", () -> serverCooldownPeak[0] = 0)
                .press(use)
                .waitUntil("the first Zenith landed", 30, () -> hits.size() == 1)
                .check("it cost 27", () -> ServerQuery.ask(p -> near(before[0] - PlayerCombat.of(p).machine().resonance(), 27.0)))
                .check("Harmonics counts one cast", () -> ServerQuery.ask(Harmonics::count) == 1)
                .check("the cooldown is 90 ticks (Haste 12 from Arcane 8)", () -> {
                    summary.add("Zenith's cooldown in the Regalia: the server showed " + serverCooldownPeak[0] + " the tick it was cast");
                    return serverCooldownPeak[0] == 90 || serverCooldownPeak[0] == 89;
                })
                .waitUntil("Zenith is ready again", 120, () -> ready(mc) && machine(mc).abilityCooldown() == 0
                        && ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0))
                .press(use)
                .waitUntil("the second Zenith landed", 30, () -> hits.size() == 2)
                .check("Harmonics is primed (two counted)", () -> ServerQuery.ask(Harmonics::count) == 2)
                .waitUntil("the halo knows it is primed (the set's meter on this client)", 20, () ->
                        com.cosmicbreach.client.gear.RegaliaLook.primed(mc.player))
                .waitUntil("Zenith is ready again", 120, () -> ready(mc) && machine(mc).abilityCooldown() == 0
                        && ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0))
                .run("remember when the third is cast", () -> third[0] = ServerQuery.ask(p -> p.level().getGameTime()))
                .press(use)
                .waitUntil("the third Zenith landed", 30, () -> hits.size() == 3)
                .check("the three casts fell within 10 s", () -> hits.get(2).time() - hits.get(0).time() < RegaliaRules.ECHO_WINDOW)
                .run("the side view", () -> sideCamera(mc))
                .waitUntil("the echo began (10 ticks after the cast)", 20, () -> GhostBodies.count() >= 1)
                .waitTicks(4)
                .screenshot("echo_ghost")
                .waitUntil("the echo landed", 30, () -> hits.stream().anyMatch(h -> h.type().equals(ECHO)))
                .run("back to the player's eyes", this::dropCamera)
                .check("the echo hit 10 ticks after the third cast, at Zenith's first active tick", () -> {
                    Hit echo = hits.stream().filter(h -> h.type().equals(ECHO)).findFirst().orElseThrow();
                    Hit cast = hits.get(2);
                    long startup = CombatData.client().move(ZENITH).timing().startup();
                    summary.add(String.format(Locale.ROOT, "third Zenith hit at +%d from its cast, the echo's at +%d (%d later)",
                            cast.time() - third[0], echo.time() - third[0], echo.time() - cast.time()));
                    return echo.time() - cast.time() == RegaliaRules.ECHO_DELAY;
                })
                .check("the echo dealt 60% of a plain Zenith", () -> {
                    Hit echo = hits.stream().filter(h -> h.type().equals(ECHO)).findFirst().orElseThrow();
                    double plain = ServerQuery.ask(p -> plainHit(p, ZENITH));
                    double crit = ServerQuery.ask(p -> critHit(p, ZENITH));
                    Hit cast = hits.get(2);
                    double castPlain = Math.abs(cast.amount() - crit) < 1e-3 ? cast.amount() * plain / crit : cast.amount();
                    summary.add(String.format(Locale.ROOT, "echo %.3f, the Zenith it echoed %.3f%s: x%.4f (a plain Zenith %.3f)",
                            echo.amount(), cast.amount(), castPlain != cast.amount() ? " (a crit)" : "", echo.amount() / castPlain, plain));
                    return Math.abs(echo.amount() - 0.6 * plain) < 1e-3 && Math.abs(echo.amount() / castPlain - 0.6) < 1e-3;
                })
                .check("the echo was free", () -> ServerQuery.ask(p -> near(before[0] - PlayerCombat.of(p).machine().resonance(), 81.0)))
                .check("the count started over", () -> ServerQuery.ask(Harmonics::count) == 0)
                .waitUntil("the echo is over", 40, () -> ServerQuery.ask(p -> Echoes.pending()) == 0);
    }

    // ------------------------------------------------------------------ 7. the Hymn of Alignment

    private void hymn(Steps steps, Minecraft mc) {
        KeyMapping attack = mc.options.keyAttack;
        KeyMapping use = mc.options.keyUse;
        double[] before = {0};
        if (part == Part.ALL) {
            // one cooldown for every set: Drift's, cast in the Driftweave, still runs after the swap
            steps.check("the set ability's cooldown carried over from Drift (one for every set)", () -> ServerQuery.ask(p -> {
                        SetState state = ArmorSets.state(p);
                        return !state.ready(p.level().getGameTime()) && state.cooldownTicks() == 560;
                    }))
                    .run("end it here, to go on", () -> ServerQuery.ask(p -> {
                        ArmorSets.setState(p, ArmorSets.state(p).withCooldown(0L, 0));
                        return true;
                    }));
        }
        steps.check("the Hymn is ready", () -> ServerQuery.ask(p -> ArmorSets.state(p).ready(p.level().getGameTime())))
                .waitUntil("this client knows it is ready", 20, () -> ArmorSets.state(mc.player).ready(mc.level.getGameTime()))
                .press(GearClient.SET_ABILITY)
                .waitUntil("the ring is on the ground on both sides", 20, () -> ServerQuery.ask(p -> HymnRings.on(p.level()).size() == 1)
                        && HymnRings.on(mc.level).size() == 1)
                .check("the cooldown started: 800 at Haste 12, 715 ticks", () -> ServerQuery.ask(p -> ArmorSets.state(p).cooldownTicks() == 715))
                .waitUntil("the player stands in it: +30 Haste on both sides", 20, () -> near(machine(mc).gearHaste(), 30.0)
                        && ServerQuery.ask(p -> near(PlayerCombat.of(p).machine().gearHaste(), 30.0)))
                .waitUntil("the zombie is Aligned", 20, () -> ServerQuery.ask(p -> dummies(p).get(0).hasEffect(GearSets.ALIGNED)))
                .run("a camera over the ring", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 me = mc.player.position();
                    camera.place(me.add(7.5, 6.0, -5.5), me.add(0, 0.2, 1.0));
                    camera.use();
                    mc.options.hideGui = true;
                })
                .waitTicks(6)
                .screenshot("hymn_ring")
                .run("from the side, low", () -> camera.place(mc.player.position().add(8.5, 1.8, 1.0), mc.player.position().add(0, 0.8, 1.0)))
                .waitTicks(3)
                .screenshot("hymn_ring_side")
                .run("back to the player's eyes", this::dropCamera)
                // double Resonance: an L1 inside gives 12
                .run("forget", () -> {
                    hits.clear();
                    before[0] = ServerQuery.ask(p -> PlayerCombat.of(p).machine().resonance());
                })
                .waitUntil("idle", 40, () -> ready(mc))
                .hold(attack).waitTicks(1).release(attack)
                .waitUntil("the L1 landed", 20, () -> !hits.isEmpty())
                .waitTicks(2)
                .check("it gave 12 Resonance (6, doubled)", () -> {
                    double gained = ServerQuery.ask(p -> PlayerCombat.of(p).machine().resonance()) - before[0];
                    summary.add(String.format(Locale.ROOT, "L1 inside the Hymn: +%.1f Resonance", gained));
                    return near(gained, 12.0);
                })
                .check("the L1 took no Aligned bonus (not an ability)", () -> {
                    double plain = ServerQuery.ask(p -> plainHit(p, L1));
                    double crit = ServerQuery.ask(p -> critHit(p, L1));
                    return near(hits.get(0).amount(), plain) || near(hits.get(0).amount(), crit);
                })
                .run("forget the hits", hits::clear)
                .waitUntil("Zenith is ready and the ring still stands", 120, () -> ready(mc) && machine(mc).abilityCooldown() == 0
                        && ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0) && HymnRings.on(mc.level).size() == 1)
                .run("reset the cooldown peak", () -> serverCooldownPeak[0] = 0)
                .press(use)
                .waitUntil("the Zenith landed", 30, () -> !hits.isEmpty())
                .check("Zenith's cooldown inside is 71 ticks (Haste 12 + 30)", () -> {
                    summary.add("Zenith's cooldown inside the Hymn: the server showed " + serverCooldownPeak[0]
                            + " the tick it was cast (90 outside)");
                    return serverCooldownPeak[0] == 71 || serverCooldownPeak[0] == 70;
                })
                .check("the Aligned zombie took 10% more from it", () -> {
                    Hit hit = hits.get(0);
                    double plain = ServerQuery.ask(p -> plainHit(p, ZENITH));
                    double crit = ServerQuery.ask(p -> critHit(p, ZENITH));
                    boolean wasCrit = near(hit.amount(), 1.1 * crit);
                    double ratio = hit.amount() / (wasCrit ? crit : plain);
                    summary.add(String.format(Locale.ROOT, "Zenith on the Aligned zombie: %.3f (plain %.3f): x%.4f%s", hit.amount(),
                            wasCrit ? crit : plain, ratio, wasCrit ? " (a crit)" : ""));
                    return Math.abs(ratio - 1.1) < 1e-3;
                })
                .waitUntil("the ring runs out", 140, () -> ServerQuery.ask(p -> HymnRings.on(p.level()).isEmpty()) && HymnRings.on(mc.level).isEmpty())
                .waitUntil("the Haste is gone", 20, () -> near(machine(mc).gearHaste(), 0.0))
                .run("clear the dummies", SetsScenario::discardDummies);
    }

    // ------------------------------------------------------------------ helpers

    /** Puts on a set's four pieces with the use key, from the hotbar. */
    private void wear(Steps steps, Minecraft mc, GearRegistry.SetItems set) {
        for (DeferredItem<SetArmorItem> piece : set.all()) {
            steps.command("give @s " + BuiltInRegistries.ITEM.getKey(piece.get()));
        }
        steps.waitUntil("the pieces are in the inventory", 60, () -> set.all().stream().allMatch(p -> count(mc, p.get()) == 1));
        steps.look(0, 0).waitTicks(2);
        for (DeferredItem<SetArmorItem> piece : set.all()) {
            steps.run("select the " + piece.getId().getPath(), () -> selectHotbar(mc, piece.get()))
                    .waitTicks(2)
                    .press(mc.options.keyUse)
                    .waitUntil("it is worn", 40, () -> mc.player.getItemBySlot(piece.get().getEquipmentSlot()).is(piece.get()));
        }
        steps.waitUntil("all four are worn on the server", 40, () -> ServerQuery.ask(p -> ArmorSets.pieces(p, set.set()) == ArmorSet.FULL_SET))
                .run("select Meridian", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .waitTicks(3);
    }

    /** L1's (or any Meridian move's) damage by this player now: the full formula, no crit. */
    private static double plainHit(ServerPlayer p, ResourceLocation move) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(new ItemStack(ModItems.MERIDIAN.get()), false);
        MoveDef def = CombatData.server().move(move);
        return HitResolver.damage(weapon, def, def.hit().mv(), false, false, ProgressionStats.of(p), 1.0, 0);
    }

    private static double critHit(ServerPlayer p, ResourceLocation move) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(new ItemStack(ModItems.MERIDIAN.get()), false);
        MoveDef def = CombatData.server().move(move);
        return HitResolver.damage(weapon, def, def.hit().mv(), true, false, ProgressionStats.of(p), 1.0, 0);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static boolean ready(Minecraft mc) {
        CombatStateMachine m = machine(mc);
        return m.phase() == Phase.IDLE && !m.isDashing() && !m.isParrying() && !m.inParryWhiff();
    }

    private void sideCamera(Minecraft mc) {
        camera = DevCamera.create(mc.level);
        Vec3 me = mc.player.position();
        Vec3 forward = new Vec3(-Mth.sin(mc.player.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(mc.player.getYRot() * Mth.DEG_TO_RAD));
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 middle = me.add(forward.scale(4.0));
        camera.place(middle.add(side.scale(8.5)).add(0, 1.8, 0), middle.add(0, 0.9, 0));
        camera.use();
        mc.options.hideGui = true;
    }

    private void dropCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        Minecraft.getInstance().options.hideGui = false;
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

    /** Clicks the hotbar key of the slot holding {@code item}. */
    private static void selectHotbar(Minecraft mc, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
                return;
            }
        }
        throw new Steps.Failure(BuiltInRegistries.ITEM.getKey(item) + " is not in the hotbar");
    }

    private static void spawnAhead(double blocks) {
        ServerQuery.ask(p -> {
            Vec3 at = p.position().add(com.cosmicbreach.combat.data.HitShape.forward(p.getYRot()).scale(blocks));
            Zombie z = MaulScenario.spawnDummy(p.serverLevel(), at, DUMMY);
            z.setYRot(p.getYRot() + 180f);
            z.setYHeadRot(p.getYRot() + 180f);
            z.setYBodyRot(p.getYRot() + 180f);
            return true;
        });
    }

    /** The dummy swings at the player (its own attack, as a zombie's). */
    private static void zombieStrikes(ServerPlayer p) {
        List<Zombie> zombies = dummies(p);
        if (zombies.isEmpty()) {
            throw new Steps.Failure("no dummy near the player");
        }
        Zombie zombie = zombies.get(0);
        zombie.swing(InteractionHand.MAIN_HAND);
        p.invulnerableTime = 0;
        zombie.doHurtTarget(p);
    }

    private static List<Zombie> dummies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(32.0), z -> z.getTags().contains(DUMMY));
    }

    private static int dummyCount(Minecraft mc) {
        int server = ServerQuery.ask(p -> dummies(p).size());
        int client = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(32.0)).size();
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
