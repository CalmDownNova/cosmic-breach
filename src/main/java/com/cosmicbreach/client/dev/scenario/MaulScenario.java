package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.combat.server.effect.Crater;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Comet Maul through the real key path (G4a), every number measured on the server:
 * <ul>
 *   <li>L1, L2, L3 on a training dummy (a zombie with no AI, no armor, 200 health): each hit's damage
 *       against Base 11 x MV (a crit is x1.5), contact frames in first person;</li>
 *   <li>hyper armor: a zombie's hit (Impact 10) on the idle player staggers it (poise 10); the same hit
 *       during L1's startup doesn't (poise 10 + 30), and L1 still lands;</li>
 *   <li>the Impact Crater at full charge (MV 3.5), and the Cratered ground: a zombie walking through it
 *       against one walking outside, speed measured, about 30% slower;</li>
 *   <li>the Meteorfall from two heights: damage 11 x min(3.3, 1.8 + 0.15 x fall), higher is harder;</li>
 *   <li>the Ram out of a dash;</li>
 *   <li>the Gravity Well: dummies 5 blocks from the plant end within about 2, the Collapse hits each for
 *       11 x 2.0; a dash from the well's 12th tick fires the Collapse early at 60%;</li>
 *   <li>the swings, the crater, the plant and the Collapse again from the side (a dev camera).</li>
 * </ul>
 */
public final class MaulScenario implements Scenario {
    static final String DUMMY = "cb_maul";
    static final String STRIKER = "cb_maul_striker";
    static final String WALKER = "cb_maul_walker";
    private static final double BASE = 11.0;
    private static final double CRIT = 1.5;
    /** Allowed difference between a measured and an expected hit. */
    private static final double TOLERANCE = 0.05;
    /** The camera's pitch for the first-person frames: a little down, where a slam lands. */
    private static final float FP_PITCH = 18f;

    private static ResourceLocation move(String name) {
        return CosmicBreach.id("comet_maul/" + name);
    }

    private static final ResourceLocation L1 = move("l1");
    private static final ResourceLocation L2 = move("l2");
    private static final ResourceLocation L3 = move("l3");
    private static final ResourceLocation CRATER = move("crater");
    private static final ResourceLocation METEORFALL = move("meteorfall");
    private static final ResourceLocation RAM = move("ram");
    private static final ResourceLocation WELL = move("gravity_well");

    /** One hit a dummy took, on the server. */
    private record Hit(int entity, double amount, long time) {
    }

    /** What the server knew when the player took a zombie's hit. */
    private record PlayerHit(Phase phase, @Nullable ResourceLocation move, double poiseBonus, boolean staggered) {
    }

    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<PlayerHit> playerHits = new CopyOnWriteArrayList<>();
    private final List<ResourceLocation> movesStarted = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    /** Walkers' positions every server tick while {@link #walking}. */
    private final Map<String, List<Vec3>> walks = new HashMap<>();
    private volatile boolean walking;
    private final Map<String, Vec3> walkTargets = new HashMap<>();
    private @Nullable DevCamera camera;

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        KeyMapping attack = mc.options.keyAttack;

        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach give comet_maul")
                .waitUntil("the Comet Maul is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.COMET_MAUL.get()))
                .look(0, FP_PITCH)
                .waitTicks(5)
                .waitUntil("the machine holds the Maul with its data", 60, () -> machine(mc).weapon() != null
                        && CombatData.client().move(WELL) != null && machine(mc).phase() == Phase.IDLE);

        chain(steps, mc, attack, "fp");
        hyperArmor(steps, mc, attack);
        crater(steps, mc, attack, "fp", true);
        meteorfall(steps, mc, attack);
        ram(steps, mc, attack);
        well(steps, mc, "fp", true);
        earlyCollapse(steps, mc);

        // the same again from the side
        steps.run("a camera to the player's right", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    // facing south (+Z), the player's right is west (-X)
                    camera.place(p.add(-5.2, 1.4, 1.0), p.add(0, 0.9, 1.0));
                    camera.use();
                })
                .waitTicks(2);
        chain(steps, mc, attack, "side");
        crater(steps, mc, attack, "side", false);
        well(steps, mc, "side", false);
        steps.run("back to the player's eyes", () -> {
                    if (camera != null) {
                        camera.remove();
                        camera = null;
                    }
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                })
                .run("clear the dummies", () -> discard(DUMMY))
                .run("clear the striker", () -> discard(STRIKER))
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ sections

    /** L1, L2, L3 on a dummy 2.2 blocks ahead: each hit measured, each contact frame captured. */
    private void chain(Steps steps, Minecraft mc, KeyMapping attack, String view) {
        steps.run("clear the dummies", () -> discard(DUMMY))
                .run("a dummy 2.2 blocks ahead", () -> spawnAhead(mc, 2.2, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyReady(mc, 2.2))
                .waitTicks(3)
                .run("forget earlier hits", hits::clear);
        String[] names = {"l1", "l2", "l3"};
        ResourceLocation[] ids = {L1, L2, L3};
        double[] mvs = {1.0, 1.1, 1.6};
        for (int i = 0; i < 3; i++) {
            ResourceLocation id = ids[i];
            String name = names[i];
            if (i == 0) {
                steps.hold(attack).waitTicks(1).release(attack);
            } else {
                // pressed in the combo window right after the recovery: whether the server's machine runs a tick
                // or two behind (its buffer takes it) or ahead (its combo window does), it chains the same way
                steps.waitUntil(name + ": " + names[i - 1] + " is over, its combo window open", 80,
                                () -> machine(mc).phase() == Phase.IDLE)
                        .hold(attack).waitTicks(1).release(attack);
            }
            steps.waitUntil(name + ": its first active tick", 60, () -> isFirstActive(mc, id))
                    .screenshot("maul_" + name + "_" + view)
                    .waitUntil(name + ": its hit reached the server", 30, () -> !hits.isEmpty());
            double mv = mvs[i];
            steps.check(name + " dealt 11 x " + mv, () -> expectHits(name + " (" + view + ")", 1, BASE * mv));
        }
        steps.waitUntil("the chain is over", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    /** A zombie's hit staggers the idle player and doesn't during L1's startup. */
    private void hyperArmor(Steps steps, Minecraft mc, KeyMapping attack) {
        String damage = "damage @s 1 minecraft:mob_attack by @e[tag=" + STRIKER + ",limit=1]";
        steps.run("a striker behind the player", () -> spawnAhead(mc, -2.0, STRIKER))
                .waitTicks(30) // the player's own i-frames from any earlier hurt are long gone
                .run("forget", () -> {
                    playerHits.clear();
                    hits.clear();
                })
                .command(damage)
                .waitUntil("the idle player's hit is recorded", 20, () -> !playerHits.isEmpty())
                .check("idle, a zombie's hit (Impact 10) staggers the player (poise 10)", () -> {
                    PlayerHit h = playerHits.get(0);
                    summary.add(String.format(Locale.ROOT, "idle hit: phase %s, hyper armor %.0f, staggered %s", h.phase(),
                            h.poiseBonus(), h.staggered()));
                    return h.staggered() && h.poiseBonus() == 0.0;
                })
                .waitUntil("the client's machine hears of the stagger", 10, () -> machine(mc).isStaggered())
                .waitTicks(35) // the stagger (10 ticks) and the player's i-frames (20) pass
                .run("forget", playerHits::clear)
                .hold(attack).waitTicks(1).release(attack)
                .waitTicks(2)
                .check("L1 is in its startup", () -> isCurrent(mc, L1) && machine(mc).phase() == Phase.STARTUP)
                .command(damage)
                .waitUntil("the hit during L1 is recorded", 20, () -> !playerHits.isEmpty())
                .check("during L1's startup the same hit doesn't stagger (poise 10 + 30)", () -> {
                    PlayerHit h = playerHits.get(0);
                    summary.add(String.format(Locale.ROOT, "hit in L1: phase %s, move %s, hyper armor %.0f, staggered %s",
                            h.phase(), h.move(), h.poiseBonus(), h.staggered()));
                    return !h.staggered() && h.poiseBonus() == 30.0 && h.phase() == Phase.STARTUP && L1.equals(h.move());
                })
                .waitUntil("L1 still reaches its active ticks", 20, () -> isCurrent(mc, L1) && machine(mc).phase() != Phase.STARTUP)
                .waitUntil("and lands", 30, () -> !hits.isEmpty())
                .check("L1 landed through the hit (11)", () -> expectHits("l1 through a hit", 1, BASE))
                .check("the server never staggered the player", () -> !ServerQuery.ask(PoiseTracker::isStaggered))
                .run("clear the striker", () -> discard(STRIKER))
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    /** A full charge (MV 3.5) on a dummy; then (once) walkers through and beside the Cratered ground. */
    private void crater(Steps steps, Minecraft mc, KeyMapping attack, String view, boolean measureSlow) {
        steps.run("clear the dummies", () -> discard(DUMMY))
                .run("spawn", () -> spawnAhead(mc, 2.0, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyReady(mc, 2.0))
                .waitTicks(3)
                .run("forget", hits::clear)
                .hold(attack)
                .waitTicks(34)
                .check("charging (out of the light attack the press started)", () -> machine(mc).phase() == Phase.CHARGING)
                .screenshot("maul_charge_" + view)
                .run("forget the light attack's hit", hits::clear)
                .release(attack)
                .waitUntil("the Impact Crater's first active tick", 30, () -> isFirstActive(mc, CRATER))
                .screenshot("maul_crater_" + view)
                .waitUntil("its hit reached the server", 30, () -> !hits.isEmpty())
                .check("the full charge dealt 11 x 3.5", () -> expectHits("crater (" + view + ")", 1, BASE * 3.5))
                .waitUntil("a crater is open", 10, () -> ServerQuery.ask(p -> Crater.openCount()) > 0);
        if (!measureSlow) {
            steps.waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
            return;
        }
        steps.run("clear the dummies", () -> discard(DUMMY))
                .waitUntil("the slam's active ticks are over on both sides (it would hit the walkers)", 20,
                        () -> machine(mc).phase() == Phase.RECOVERY
                                && ServerQuery.ask(p -> PlayerCombat.of(p).machine().phase() != Phase.ACTIVE))
                .run("two walkers: one across the crater, one well outside it", () -> ServerQuery.ask(p -> {
                    Vec3 c = Crater.centres().get(Crater.centres().size() - 1);
                    walkTargets.clear();
                    walks.clear();
                    spawnWalker(p, "inside", c.add(3.0, 0, 1.5), c.add(-3.0, 0, 1.5));
                    spawnWalker(p, "outside", c.add(3.0, 0, 13.0), c.add(-3.0, 0, 13.0));
                    walking = true;
                    return true;
                }))
                .waitTicks(26)
                .run("stop walking", () -> walking = false)
                .check("the walker in the crater was slowed, the other not", () -> {
                    String detail = ServerQuery.ask(p -> {
                        StringBuilder b = new StringBuilder();
                        for (Zombie z : walkers(p)) {
                            b.append(String.format(Locale.ROOT, "%s at %.2f %.2f %.2f ground %s slowed %s speed %.3f; ",
                                    z.getTags().contains("inside") ? "inside" : "outside", z.getX(), z.getY(), z.getZ(),
                                    z.onGround(), Crater.isSlowed(z), z.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                        }
                        b.append("craters ").append(Crater.centres());
                        return b.toString();
                    });
                    int slowedTicks = walks.getOrDefault("inside_slowed", List.of()).size();
                    summary.add("walkers: " + detail + " inside slowed on " + slowedTicks + " ticks; recorded "
                            + walks.getOrDefault("inside", List.of()).size() + "/" + walks.getOrDefault("outside", List.of()).size()
                            + "; inside path " + walks.getOrDefault("inside", List.of()));
                    if (slowedTicks < 8 || detail.contains("outside at") && detail.matches(".*outside at [^;]*slowed true.*")) {
                        throw new Steps.Failure("walkers: " + String.join(" | ", summary));
                    }
                    return true;
                })
                .check("the Cratered ground slows by about 30%", () -> {
                    double inside = speed(walks.get("inside"));
                    double outside = speed(walks.get("outside"));
                    double ratio = inside / outside;
                    String line = String.format(Locale.ROOT, "crater walk: inside %.3f b/t, outside %.3f b/t, ratio %.2f (slow %.0f%%); %s",
                            inside, outside, ratio, (1 - ratio) * 100, summary.get(summary.size() - 1));
                    summary.add(line);
                    if (!(outside > 0.05 && ratio > 0.62 && ratio < 0.78)) {
                        throw new Steps.Failure(line);
                    }
                    return true;
                })
                .run("clear the walkers", () -> discard(WALKER))
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    /** Two dives, 3 and 8 blocks, onto a dummy just ahead: the damage follows the fall. */
    private void meteorfall(Steps steps, Minecraft mc, KeyMapping attack) {
        double[] damage = new double[2];
        int[] heights = {3, 8};
        for (int i = 0; i < 2; i++) {
            int n = i;
            int height = heights[i];
            steps.run("clear the dummies", () -> discard(DUMMY))
                    .look(0, FP_PITCH)
                    .run("spawn", () -> spawnAhead(mc, 1.6, DUMMY))
                    .waitUntil("the dummy stands ahead", 40, () -> dummyReady(mc, 1.6))
                    .waitTicks(3)
                    .run("forget", hits::clear)
                    .command("tp @s ~ ~" + height + " ~")
                    .look(0, 80)
                    .waitUntil("airborne", 20, () -> !mc.player.onGround() && mc.player.getY() > ServerQuery.ask(p -> p.getY()) - 0.5)
                    .hold(attack).waitTicks(1).release(attack)
                    .check("the Meteorfall started", () -> isCurrent(mc, METEORFALL))
                    .waitUntil("it lands", 80, () -> machine(mc).phase() == Phase.RECOVERY || machine(mc).phase() == Phase.IDLE)
                    .screenshot("maul_meteorfall_" + height)
                    .waitUntil("its hit reached the server", 30, () -> !hits.isEmpty())
                    .check("the Meteorfall from " + height + " blocks follows its fall", () -> {
                        double fall = ServerQuery.ask(p -> PlayerCombat.of(p).server().lastPlungeFall());
                        double mv = Math.min(3.3, 1.8 + 0.15 * fall);
                        double amount = hits.get(0).amount();
                        // the damage before a crit (x1.5), so a lucky low dive can't outdo a high one
                        damage[n] = Math.abs(amount - BASE * mv * CRIT) <= TOLERANCE ? amount / CRIT : amount;
                        return expectHits(String.format(Locale.ROOT, "meteorfall from %d (server fall %.2f, MV %.3f)", height,
                                fall, mv), 1, BASE * mv);
                    })
                    .look(0, FP_PITCH)
                    .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
        }
        steps.check("the higher dive hit harder", () -> damage[1] > damage[0] * 1.15 || damage[1] > damage[0] && damage[0] > BASE * 2.4);
    }

    /** A dash forward into the Ram on a dummy 7.5 blocks ahead. */
    private void ram(Steps steps, Minecraft mc, KeyMapping attack) {
        KeyMapping forward = mc.options.keyUp;
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .run("spawn", () -> spawnAhead(mc, 7.5, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyReady(mc, 7.5))
                .waitTicks(3)
                .run("forget", () -> {
                    hits.clear();
                    movesStarted.clear();
                })
                .hold(forward)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .waitTicks(4)
                .hold(attack).waitTicks(1).release(attack)
                .release(forward)
                .check("the dash turned into the Ram", () -> movesStarted.contains(RAM))
                .waitUntil("its hit reached the server", 30, () -> !hits.isEmpty())
                .check("the Ram dealt 11 x 1.3", () -> expectHits("ram", 1, BASE * 1.3))
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE && !machine(mc).isDashing());
    }

    /**
     * The Gravity Well: dummies 5 blocks from the plant (ahead, left, right) are pulled to within about 2
     * over the 30 ticks, then the Collapse hits each for 11 x 2.0.
     */
    private void well(Steps steps, Minecraft mc, String view, boolean measure) {
        Vec3[] centre = {null};
        Map<Integer, Double> startDistance = new HashMap<>();
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .waitTicks(3)
                .run("Resonance and a fresh cooldown", () -> ready(mc))
                .run("three dummies 5 blocks from the plant", () -> ServerQuery.ask(p -> {
                    centre[0] = GravityWell.centre(p.position(), p.getYRot(), 1.0);
                    Vec3 f = HitShape.forward(p.getYRot());
                    Vec3 r = new Vec3(-f.z, 0, f.x);
                    // ahead, left, right; seen from the side, ahead and the two front diagonals (clear of the camera)
                    List<Vec3> dirs = measure ? List.of(f, r, r.scale(-1))
                            : List.of(f, f.add(r).normalize(), f.subtract(r).normalize());
                    for (Vec3 dir : dirs) {
                        spawnDummy(p.serverLevel(), centre[0].add(dir.scale(5)), DUMMY);
                    }
                    return true;
                }))
                .waitTicks(5)
                .run("forget", () -> {
                    hits.clear();
                    startDistance.clear();
                    ServerQuery.ask(p -> {
                        dummies(p).forEach(z -> startDistance.put(z.getId(), z.position().distanceTo(centre[0])));
                        return true;
                    });
                })
                .check("three dummies stand 5 blocks from the plant", () -> startDistance.size() == 3
                        && startDistance.values().stream().allMatch(d -> Math.abs(d - 5.0) < 0.3))
                .press(mc.options.keyUse)
                .check("the ability started the well", () -> isCurrent(mc, WELL))
                .waitUntil("the plant (its first active tick)", 20, () -> isFirstActive(mc, WELL))
                .screenshot("maul_well_" + view)
                .waitUntil("the pull's 18th tick", 30, () -> isCurrent(mc, WELL) && machine(mc).phaseTick() >= 17)
                .screenshot("maul_well_pull_" + view)
                .waitUntil("the pull's 28th tick on the server", 40, () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    return m.current() != null && m.current().id().equals(WELL) && m.phase() == Phase.ACTIVE && m.phaseTick() >= 28;
                }))
                .check("the pull brought every dummy from 5 blocks to within about 2", () -> ServerQuery.ask(p -> {
                    List<String> d = new ArrayList<>();
                    boolean all = true;
                    for (Zombie z : dummies(p)) {
                        double now = horizontal(z.position(), centre[0]);
                        d.add(String.format(Locale.ROOT, "%.2f -> %.2f", startDistance.getOrDefault(z.getId(), -1.0), now));
                        all &= now < 2.2;
                    }
                    summary.add("well pull (" + view + "): " + d);
                    return all && d.size() == 3;
                }))
                .waitUntil("the Collapse (the well's last active tick)", 10, () -> {
                    MoveInstance m = machine(mc).current();
                    return m != null && m.id().equals(WELL) && machine(mc).phase() == Phase.ACTIVE && machine(mc).phaseTick() >= 30
                            || machine(mc).phase() == Phase.RECOVERY;
                })
                .waitTicks(1)
                .screenshot("maul_collapse_" + view)
                .waitUntil("the Collapse hit all three", 20, () -> hits.size() >= 3)
                .check("the Collapse dealt 11 x 2.0 to each", () -> expectHits("collapse (" + view + ")", 3, BASE * 2.0))
                .check("the camera kicked", () -> ClientCombat.cameraTrauma() > 0.05 || ClientCombat.cameraKick() > 0.1)
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    /** A dash from the well's 15th active tick frees the player and fires the Collapse early at 60%. */
    private void earlyCollapse(Steps steps, Minecraft mc) {
        Vec3[] start = {null};
        KeyMapping back = mc.options.keyDown;
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .waitTicks(3)
                .run("Resonance and a fresh cooldown", () -> ready(mc))
                .run("two dummies 3.5 blocks from the plant", () -> ServerQuery.ask(p -> {
                    Vec3 c = GravityWell.centre(p.position(), p.getYRot(), 1.0);
                    Vec3 f = HitShape.forward(p.getYRot());
                    Vec3 r = new Vec3(-f.z, 0, f.x);
                    spawnDummy(p.serverLevel(), c.add(r.scale(3.5)), DUMMY);
                    spawnDummy(p.serverLevel(), c.add(r.scale(-3.5)), DUMMY);
                    return true;
                }))
                .waitTicks(5)
                .run("forget", () -> {
                    hits.clear();
                    start[0] = mc.player.position();
                })
                .press(mc.options.keyUse)
                .waitUntil("the well's 11th active tick on the client", 40, () -> {
                    MoveInstance m = machine(mc).current();
                    return m != null && m.id().equals(WELL) && machine(mc).phase() == Phase.ACTIVE && machine(mc).phaseTick() == 10;
                })
                .hold(back)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .check("rooted before the 12th tick: no dash", () -> !machine(mc).isDashing() && isCurrent(mc, WELL))
                .waitUntil("the well's 15th active tick", 20, () -> isCurrent(mc, WELL) && machine(mc).phaseTick() >= 14)
                .check("nothing collapsed yet", hits::isEmpty)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .check("the dash freed the player", () -> machine(mc).isDashing() && machine(mc).current() == null)
                .waitTicks(8)
                .release(back)
                .check("and carried it away", () -> mc.player.position().distanceTo(start[0]) > 2.5)
                .waitUntil("the early Collapse hit both", 20, () -> hits.size() >= 2)
                .check("the early Collapse dealt 60%: 11 x 2.0 x 0.6", () -> expectHits("early collapse", 2, BASE * 2.0 * 0.6))
                .run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE && !machine(mc).isDashing())
                .command("tp @s ~ ~ ~5")
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ measuring

    private void listen() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            Entity e = event.getEntity();
            if (e.level().isClientSide()) {
                return;
            }
            // only the player's own hits count: a dummy that spawns late under load can take fall damage
            if (e.getTags().contains(DUMMY) && event.getSource().getEntity() instanceof ServerPlayer) {
                hits.add(new Hit(e.getId(), event.getNewDamage(), e.level().getGameTime()));
            } else if (e instanceof ServerPlayer player && event.getSource().getEntity() != null
                    && event.getSource().getEntity().getTags().contains(STRIKER)) {
                CombatStateMachine m = PlayerCombat.of(player).machine();
                playerHits.add(new PlayerHit(m.phase(), m.current() == null ? null : m.current().id(), m.poiseBonus(),
                        PoiseTracker.isStaggered(player)));
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(WALKER)) {
                summary.add("a walker died: " + event.getSource().getMsgId() + " by " + event.getSource().getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(WALKER)) {
                summary.add(String.format(Locale.ROOT, "a walker was hurt: %.2f %s by %s", event.getNewDamage(),
                        event.getSource().getMsgId(), event.getSource().getEntity()));
            }
        });
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.MoveStarted started) {
                movesStarted.add(started.move().id());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (!walking) {
                return;
            }
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (Zombie z : level.getEntitiesOfClass(Zombie.class, new AABB(-3.0E7, -64, -3.0E7, 3.0E7, 320, 3.0E7),
                        z -> z.getTags().contains(WALKER))) {
                    String key = z.getTags().contains("inside") ? "inside" : "outside";
                    Vec3 target = walkTargets.get(key);
                    if (target != null && z.getNavigation().isDone()) {
                        z.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
                    }
                    walks.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(z.position());
                    if (key.equals("inside") && Crater.isSlowed(z)) {
                        walks.computeIfAbsent("inside_slowed", k -> new CopyOnWriteArrayList<>()).add(z.position());
                    }
                }
            }
        });
    }

    /** The hits since the last clear: {@code count} of them, each the expected amount (or a crit of it). */
    private boolean expectHits(String what, int count, double expected) {
        List<Hit> got = List.copyOf(hits);
        List<String> amounts = new ArrayList<>();
        boolean ok = got.size() == count;
        int crits = 0;
        for (Hit h : got) {
            boolean plain = Math.abs(h.amount() - expected) <= TOLERANCE;
            boolean crit = Math.abs(h.amount() - expected * CRIT) <= TOLERANCE;
            crits += crit ? 1 : 0;
            ok &= plain || crit;
            amounts.add(String.format(Locale.ROOT, "%.2f%s", h.amount(), crit ? " (crit)" : ""));
        }
        String line = String.format(Locale.ROOT, "%s: expected %d x %.2f, measured %s", what, count, expected, amounts);
        summary.add(line);
        hits.clear();
        if (!ok) {
            throw new Steps.Failure("damage mismatch: " + line);
        }
        return true;
    }

    private static double speed(@Nullable List<Vec3> path) {
        if (path == null || path.size() < 16) {
            return 0.0;
        }
        // skip the first ticks (the path is planned and the walk speeds up), then the average over the rest
        Vec3 a = path.get(8);
        Vec3 b = path.get(path.size() - 1);
        return horizontal(a, b) / (path.size() - 9);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ helpers

    static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static boolean isCurrent(Minecraft mc, ResourceLocation id) {
        MoveInstance m = machine(mc).current();
        return m != null && m.id().equals(id);
    }

    private static boolean isFirstActive(Minecraft mc, ResourceLocation id) {
        return isCurrent(mc, id) && machine(mc).phase() == Phase.ACTIVE && machine(mc).phaseTick() == 0;
    }

    /** Full Resonance and no ability cooldown, on both machines. */
    private static void ready(Minecraft mc) {
        CombatStateMachine local = machine(mc);
        local.syncFromServer(100, local.dashCharges(), 0);
        ServerQuery.ask(p -> {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            m.syncFromServer(100, m.dashCharges(), 0);
            return true;
        });
    }

    /** Removes every entity with {@code tag} at once, on the server (a kill command would run a tick late). */
    private static void discard(String tag) {
        ServerQuery.ask(p -> {
            p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(48.0), e -> e.getTags().contains(tag))
                    .forEach(Entity::discard);
            return true;
        });
    }

    /** A dummy {@code blocks} ahead of the player (negative: behind), with {@code tag}. */
    private static void spawnAhead(Minecraft mc, double blocks, String tag) {
        ServerQuery.ask(p -> {
            spawnDummy(p.serverLevel(), p.position().add(HitShape.forward(p.getYRot()).scale(blocks)), tag);
            return true;
        });
    }

    /** A training dummy: a zombie with no AI, no armor, 200 health and a pumpkin against the sun. */
    static Zombie spawnDummy(ServerLevel level, Vec3 at, String tag) {
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(at.x, at.y, at.z, 180f, 0f);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setBaby(false);
        z.addTag(tag);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        z.setHealth(200f);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        level.addFreshEntity(z);
        return z;
    }

    /** A zombie that walks only where it is sent: no goals, no target, sent from {@code from} to {@code to}. */
    private void spawnWalker(ServerPlayer p, String name, Vec3 from, Vec3 to) {
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(from.x, from.y, from.z, 90f, 0f);
        z.setPersistenceRequired();
        z.setBaby(false);
        z.addTag(WALKER);
        z.addTag(name);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        z.goalSelector.removeAllGoals(g -> true);
        z.targetSelector.removeAllGoals(g -> true);
        p.serverLevel().addFreshEntity(z);
        walkTargets.put(name, to);
    }

    private static List<Zombie> dummies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(16.0), z -> z.getTags().contains(DUMMY));
    }

    private static List<Zombie> walkers(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(24.0), z -> z.getTags().contains(WALKER));
    }

    /**
     * True once the server has a dummy about {@code blocks} ahead of the player and this client sees a zombie
     * there too (tags aren't sent to clients, so the client side goes by the place).
     */
    private static boolean dummyReady(Minecraft mc, double blocks) {
        boolean server = ServerQuery.ask(p -> {
            Vec3 f = HitShape.forward(p.getYRot());
            for (Zombie z : dummies(p)) {
                Vec3 d = z.position().subtract(p.position());
                if (Math.abs(d.x * f.x + d.z * f.z - blocks) < 0.5) {
                    return true;
                }
            }
            return false;
        });
        return server && dummyAhead(mc, blocks) != null;
    }

    /** The dummy about {@code blocks} ahead of the player (client side), or null. */
    private static @Nullable Zombie dummyAhead(Minecraft mc, double blocks) {
        Vec3 feet = mc.player.position();
        Vec3 f = HitShape.forward(mc.player.getYRot());
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(blocks + 3.0))) {
            Vec3 d = z.position().subtract(feet);
            double along = d.x * f.x + d.z * f.z;
            if (Math.abs(along - blocks) < 0.6 && Math.abs(d.x * f.z - d.z * f.x) < 0.6) {
                return z;
            }
        }
        return null;
    }

    @Override
    public int timeBudgetSeconds() {
        return 330;
    }
}
