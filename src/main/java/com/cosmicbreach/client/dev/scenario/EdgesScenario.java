package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.combat.TwinBlades;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.fx.EdgesVisuals;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.effect.EdgesEffects;
import com.cosmicbreach.combat.server.effect.TetherBlades;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Binary Edges through the real key path (G4b), every number measured on the server:
 * <ul>
 *   <li>L1 to L5 on a training dummy: each hit against Base 3.8 x MV (a crit is x1.5), the Cross Cut's two
 *       hits of 0.6 and the Gyre's four of 0.5, each move's animation on its first active tick;</li>
 *   <li>the Weave: an attack 6 ticks after a dash starts the chain at L4, one 12 ticks after at L1; the
 *       Scissor out of the dash's last ticks;</li>
 *   <li>the Binary Orbit at full charge: six hits of 0.5, walking at 60%; the Twin Meteor from 3 and 8 blocks;</li>
 *   <li>the Tether into a dummy 8 blocks off: the throw's hit (0.8), the blade stuck in it, the dummy Marked (+20%
 *       crit chance: 25% against 5%), no cooldown while the blade is out, the right blade alone at 60% on a
 *       second dummy; the blink: 6 ticks of travel, invulnerable, arriving beside the dummy, the arrival slash
 *       (2.0), the Mark renewed for 60 ticks, the blade back and the 6 s cooldown starting on arrival;</li>
 *   <li>the Tether thrown at nothing: it flies 16 blocks, comes back, and the cooldown starts then; thrown into
 *       a wall: stuck there 80 ticks, then back, cooldown from then;</li>
 *   <li>screenshots: both trails, the chain and the blink, in first person and from the side.</li>
 * </ul>
 */
public final class EdgesScenario implements Scenario {
    static final String DUMMY = "cb_edges";
    static final String DUMMY_B = "cb_edges_b";
    static final String STRIKER = "cb_edges_striker";
    private static final double BASE = 3.8;
    private static final double CRIT = 1.5;
    private static final double TOLERANCE = 0.05;
    private static final float FP_PITCH = 12f;
    private static final int COOLDOWN = 120;

    private static ResourceLocation move(String name) {
        return CosmicBreach.id("binary_edges/" + name);
    }

    private static final ResourceLocation L1 = move("l1");
    private static final ResourceLocation L2 = move("l2");
    private static final ResourceLocation L3 = move("l3");
    private static final ResourceLocation L4 = move("l4");
    private static final ResourceLocation L5 = move("l5");
    private static final ResourceLocation ORBIT = move("orbit");
    private static final ResourceLocation METEOR = move("twin_meteor");
    private static final ResourceLocation SCISSOR = move("scissor");
    private static final ResourceLocation TETHER = move("tether");
    private static final ResourceLocation BLINK = move("blink");

    private record Hit(int entity, String tag, double amount, long time) {
    }

    /** The server's blade, sampled every server tick while one is out. */
    private record BladeSample(long time, Vec3 at, byte state, @Nullable Integer stuckIn, int cooldown, int pending,
                               boolean invulnerable, boolean blinking, double travelled, int age) {
    }

    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<ResourceLocation> movesStarted = new CopyOnWriteArrayList<>();
    private final List<BladeSample> blade = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private volatile boolean sampling;
    private @Nullable DevCamera camera;
    private Vec3 throwFrom = Vec3.ZERO;
    /** False for the mechanics alone (no animation checks, no side views): {@code edges-mechanics}. */
    private final boolean full;
    /** Only the first-person framing shots (for tuning the held sickles' display): {@code edges-fp}. */
    private boolean framingOnly;

    /** The first-person framing shots alone. */
    public static EdgesScenario framing() {
        EdgesScenario scenario = new EdgesScenario(false);
        scenario.framingOnly = true;
        return scenario;
    }

    public EdgesScenario() {
        this(true);
    }

    public EdgesScenario(boolean full) {
        this.full = full;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        KeyMapping attack = mc.options.keyAttack;

        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach give binary_edges")
                .waitUntil("the Binary Edges are in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.BINARY_EDGES.get()))
                .look(0, FP_PITCH)
                .waitTicks(5)
                .waitUntil("the machine holds the Edges with their data", 60, () -> machine(mc).weapon() != null
                        && machine(mc).weapon().offHand().isPresent() && CombatData.client().move(BLINK) != null
                        && machine(mc).phase() == Phase.IDLE)
                .check("the off hand shows the left sickle", () -> TwinBlades.offHandStack(mc.player, mc.player.getOffhandItem())
                        .is(ModItems.BINARY_EDGES_LEFT.get()))
                .screenshot("edges_idle_fp");
        if (framingOnly) {
            steps.waitTicks(30)
                    .screenshot("edges_idle_settled_fp")
                    .look(0, 40)
                    .screenshot("edges_idle_down_fp")
                    .look(0, FP_PITCH)
                    .hold(attack)
                    .waitTicks(24)
                    .screenshot("edges_charge_hold_fp")
                    .release(attack)
                    .waitUntil("idle", 60, () -> machine(mc).phase() == Phase.IDLE)
                    .waitTicks(10)
                    .hold(ModKeyMappings.DASH).waitTicks(1).release(ModKeyMappings.DASH)
                    .waitTicks(2)
                    .screenshot("edges_dash_fp");
            return;
        }

        chain(steps, mc, attack, "fp", full);
        weave(steps, mc, attack);
        scissor(steps, mc, attack);
        orbit(steps, mc, attack, "fp", true);
        meteor(steps, mc, attack);
        tetherIntoDummy(steps, mc, attack, "fp", true);
        tetherMiss(steps, mc);
        tetherIntoWall(steps, mc);

        if (!full) {
            steps.run("clear the dummies", () -> {
                        discard(DUMMY);
                        discard(DUMMY_B);
                        discard(STRIKER);
                    })
                    .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
            return;
        }
        // the same moves again, shot from the side: a camera to the player's right, used only for the shots (while
        // another entity is the camera the client sends the server nothing of the player's moves and turns)
        steps.run("a camera to the player's right", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(-4.6, 1.7, 2.6), p.add(0, 0.9, 2.4));
                })
                .waitTicks(2);
        chain(steps, mc, attack, "side", false);
        orbit(steps, mc, attack, "side", false);
        tetherIntoDummy(steps, mc, attack, "side", false);
        steps.run("back to the player's eyes", () -> {
                    if (camera != null) {
                        camera.remove();
                        camera = null;
                    }
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                })
                .run("clear the dummies", () -> {
                    discard(DUMMY);
                    discard(DUMMY_B);
                    discard(STRIKER);
                })
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the chain

    /** L1 to L5 on a dummy put back 2 blocks ahead before each: every hit and count measured. */
    private void chain(Steps steps, Minecraft mc, KeyMapping attack, String view, boolean checkAnimations) {
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .run("a dummy 2 blocks ahead", () -> spawnAhead(2.0, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(DUMMY) == 1)
                .waitTicks(3)
                .run("forget earlier hits", hits::clear);
        String[] names = {"l1", "l2", "l3", "l4", "l5"};
        ResourceLocation[] ids = {L1, L2, L3, L4, L5};
        double[] mvs = {1.0, 1.0, 0.6, 1.2, 0.5};
        int[] counts = {1, 1, 2, 1, 4};
        String[] animations = {"edges_l1", "edges_l2", "edges_l3", "edges_l4", "edges_l5"};
        for (int i = 0; i < 5; i++) {
            ResourceLocation id = ids[i];
            String name = names[i];
            int n = i;
            if (i > 0) {
                steps.waitUntil(name + ": " + names[i - 1] + " is over, its combo window open", 60,
                        () -> machine(mc).phase() == Phase.IDLE);
            }
            double at = i == 3 ? 3.2 : 2.3; // the Lunge steps 2 blocks in first
            steps.run(name + ": the dummy back ahead", () -> placeDummy(DUMMY, at))
                    .hold(attack).waitTicks(1).release(attack)
                    .waitUntil(name + ": its first active tick", 30, () -> isFirstActive(mc, id));
            if (checkAnimations) {
                steps.check(name + ": its animation is on the move's own tick", () -> {
                    PlayerAnimations.State state = PlayerAnimations.state(mc.player);
                    MoveDef def = CombatData.client().move(id);
                    boolean ok = state.animation() != null && state.animation().getPath().equals(animations[n])
                            && Math.abs(state.time() - def.timing().startup()) <= 1.01;
                    summary.add(String.format(Locale.ROOT, "%s first active tick: animation %s", name, state));
                    return ok;
                });
            }
            shot(steps, "edges_" + name + "_" + view, view);
            if (i == 2 || i == 4) {
                // both blades have cut by now: their two trails, cyan and violet
                steps.waitTicks(i == 2 ? 1 : 2);
                shot(steps, "edges_" + name + "_trails_" + view, view);
            }
            steps.waitUntil(name + ": its hits reached the server", 30, () -> hits.size() >= counts[n])
                    .waitTicks(2)
                    .check(name + " dealt " + counts[i] + " x 3.8 x " + mvs[i],
                            () -> expectHits(name + " (" + view + ")", counts[n], BASE * mvs[n]));
        }
        steps.waitUntil("the chain is over", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    // ------------------------------------------------------------------ the Weave and the Scissor

    /** A backstep, then an attack 6 ticks after it ends (L4), and another 12 after (L1). */
    private void weave(Steps steps, Minecraft mc, KeyMapping attack) {
        int[] waits = {6, 12};
        ResourceLocation[] expected = {L4, L1};
        for (int i = 0; i < 2; i++) {
            int wait = waits[i];
            ResourceLocation want = expected[i];
            steps.run("clear the dummies", () -> discard(DUMMY))
                    .waitUntil("idle", 60, () -> machine(mc).phase() == Phase.IDLE && !machine(mc).isDashing())
                    .waitTicks(25) // a combo window long closed and a dash charge back
                    .run("forget", movesStarted::clear)
                    .hold(ModKeyMappings.DASH).waitTicks(1).release(ModKeyMappings.DASH)
                    .waitUntil("dashing", 10, () -> machine(mc).isDashing())
                    .waitUntil("the dash is over", 20, () -> !machine(mc).isDashing())
                    .waitTicks(wait - 1)
                    .hold(attack).waitTicks(1).release(attack)
                    .waitTicks(1)
                    .check("an attack " + wait + " ticks after the dash started " + want.getPath(), () -> {
                        List<ResourceLocation> started = new ArrayList<>(movesStarted);
                        ResourceLocation server = ServerQuery.ask(p -> {
                            MoveInstance m = PlayerCombat.of(p).machine().current();
                            return m == null ? null : m.id();
                        });
                        summary.add(String.format(Locale.ROOT, "weave: attack %d ticks after the dash: client %s, server %s",
                                wait, started, server));
                        return started.size() == 1 && started.get(0).equals(want) && want.equals(server);
                    })
                    .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE)
                    .command("tp @s ~ ~ ~5");
        }
    }

    /** A dash forward at a dummy 7.5 blocks ahead, attack in its last ticks: the Scissor. */
    private void scissor(Steps steps, Minecraft mc, KeyMapping attack) {
        KeyMapping forward = mc.options.keyUp;
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .waitTicks(30)
                .run("a dummy 7.5 blocks ahead", () -> spawnAhead(7.5, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(DUMMY) == 1)
                .waitTicks(3)
                .run("forget", () -> {
                    hits.clear();
                    movesStarted.clear();
                })
                .hold(forward)
                .hold(ModKeyMappings.DASH).waitTicks(1).release(ModKeyMappings.DASH)
                .waitTicks(4)
                .check("inside the dash-attack window: the dash's last 4 ticks", () -> machine(mc).isDashing()
                        && machine(mc).dashElapsed() >= 4)
                .hold(attack).waitTicks(1).release(attack)
                .release(forward)
                .check("the dash turned into the Scissor", () -> movesStarted.contains(SCISSOR))
                .waitUntil("its first active tick", 20, () -> isFirstActive(mc, SCISSOR))
                .screenshot("edges_scissor_fp")
                .waitUntil("its hit reached the server", 30, () -> !hits.isEmpty())
                .waitTicks(2)
                .check("the Scissor dealt 3.8 x 1.3", () -> expectHits("scissor", 1, BASE * 1.3))
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE && !machine(mc).isDashing());
    }

    // ------------------------------------------------------------------ the Orbit and the Meteor

    /** Hold 24 ticks and release: the Binary Orbit on a dummy 2.5 blocks ahead, six hits of 0.5. */
    private void orbit(Steps steps, Minecraft mc, KeyMapping attack, String view, boolean measure) {
        float[] walk = {Float.NaN};
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, FP_PITCH)
                .waitUntil("idle", 60, () -> machine(mc).phase() == Phase.IDLE)
                .run("a dummy 2.5 blocks ahead", () -> spawnAhead(2.5, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(DUMMY) == 1)
                .waitTicks(3)
                .run("forget", hits::clear)
                .hold(attack)
                .waitTicks(24)
                .check("charging", () -> machine(mc).phase() == Phase.CHARGING);
        shot(steps, "edges_charge_" + view, view)
                .run("forget the light attack's hit", hits::clear)
                .release(attack)
                .waitUntil("the Orbit's first active tick", 20, () -> isFirstActive(mc, ORBIT))
                .run("its walk speed", () -> walk[0] = machine(mc).movementMultiplier())
                .waitTicks(3);
        shot(steps, "edges_orbit_" + view, view)
                .waitUntil("its hits reached the server", 40, () -> hits.size() >= 6)
                .waitTicks(4)
                .check("the Binary Orbit dealt 6 x 3.8 x 0.5", () -> expectHits("orbit (" + view + ")", 6, BASE * 0.5));
        if (measure) {
            steps.check("the Orbit walks at 60%", () -> {
                summary.add(String.format(Locale.ROOT, "orbit walk multiplier %.2f", walk[0]));
                return Math.abs(walk[0] - 0.6f) < 1e-4;
            });
        }
        steps.waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
    }

    /** Two dives, 3 and 8 blocks, onto a dummy just ahead: damage 3.8 x min(1.8, 1.0 + 0.08 x fall). */
    private void meteor(Steps steps, Minecraft mc, KeyMapping attack) {
        int[] heights = {3, 8};
        for (int height : heights) {
            steps.run("clear the dummies", () -> discard(DUMMY))
                    .look(0, FP_PITCH)
                    .run("a dummy 1.4 blocks ahead", () -> spawnAhead(1.4, DUMMY))
                    .waitUntil("the dummy stands ahead", 40, () -> dummyCount(DUMMY) == 1)
                    .waitTicks(3)
                    .run("forget", hits::clear)
                    .command("tp @s ~ ~" + height + " ~")
                    .look(0, 80)
                    .waitUntil("airborne", 20, () -> !mc.player.onGround() && mc.player.getY() > ServerQuery.ask(p -> p.getY()) - 0.5)
                    .hold(attack).waitTicks(1).release(attack)
                    .check("the Twin Meteor started", () -> isCurrent(mc, METEOR))
                    .waitUntil("it lands", 80, () -> machine(mc).phase() == Phase.RECOVERY || machine(mc).phase() == Phase.IDLE)
                    .screenshot("edges_meteor_" + height)
                    .waitUntil("its hit reached the server", 30, () -> !hits.isEmpty())
                    .check("the Twin Meteor from " + height + " blocks follows its fall", () -> {
                        double fall = ServerQuery.ask(p -> PlayerCombat.of(p).server().lastPlungeFall());
                        double mv = Math.min(1.8, 1.0 + 0.08 * fall);
                        return expectHits(String.format(Locale.ROOT, "twin meteor from %d (fall %.2f, MV %.3f)", height, fall, mv),
                                1, BASE * mv);
                    })
                    .look(0, FP_PITCH)
                    .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE);
        }
    }

    // ------------------------------------------------------------------ the Tether

    /**
     * The Tether into a dummy 8 blocks ahead: its hit, the stick, the Mark, no cooldown while it is out, the single
     * blade at 60% on a second dummy, then the blink: invulnerable travel, arrival beside the dummy, the arrival
     * slash, the Mark renewed, the blade back, the cooldown from the arrival.
     */
    private void tetherIntoDummy(Steps steps, Minecraft mc, KeyMapping attack, String view, boolean measure) {
        KeyMapping use = mc.options.keyUse;
        double[] healthBefore = {0};
        steps.run("clear the dummies", () -> {
                    discard(DUMMY);
                    discard(DUMMY_B);
                })
                .look(0, 4)
                .waitUntil("idle, cooldown over", 200, () -> machine(mc).phase() == Phase.IDLE
                        && ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0
                        && PlayerCombat.of(p).machine().pendingCooldown() == 0))
                .run("Resonance", () -> resonance(mc, 100))
                .run("a dummy 8 blocks ahead", () -> spawnAhead(8.0, DUMMY))
                .waitUntil("the dummy stands ahead", 40, () -> dummyCount(DUMMY) == 1)
                .waitTicks(5)
                .run("forget", () -> {
                    hits.clear();
                    blade.clear();
                    throwFrom = mc.player.getEyePosition();
                    sampling = true;
                })
                .press(use)
                .check("the Tether started", () -> isCurrent(mc, TETHER))
                .waitUntil("the blade is out", 20, () -> EdgesVisuals.bladeOf(mc.player) != null)
                .check("the left hand is empty while the blade flies", () -> TwinBlades.offHandStack(mc.player,
                        mc.player.getOffhandItem()).isEmpty());
        shot(steps, "edges_tether_throw_" + view, view)
                .waitUntil("the blade stuck in the dummy", 30, () -> ServerQuery.ask(p -> {
                    ThrownSickle b = TetherBlades.bladeOf(p);
                    return b != null && b.isStuck() && b.stuckEntity() instanceof Zombie z && z.getTags().contains(DUMMY);
                }))
                .waitUntil("the throw's hit reached the server", 10, () -> !hits.isEmpty())
                .check("the throw dealt 3.8 x 0.8", () -> expectHits("tether throw (" + view + ")", 1, BASE * 0.8))
                .waitTicks(3);
        shot(steps, "edges_tether_chain_" + view, view);
        if (measure) {
            steps.check("the stuck dummy is Marked: 25% crit chance against it, 5% against another", () -> ServerQuery.ask(p -> {
                        Zombie marked = dummies(p, DUMMY).get(0);
                        double against = HitResolver.critChance(p, PlayerCombat.of(p).machine().stats(), marked);
                        double plain = HitResolver.critChance(p, PlayerCombat.of(p).machine().stats(), p);
                        int left = TetherBlades.markTicksLeft(marked);
                        summary.add(String.format(Locale.ROOT, "mark on stick: crit chance %.3f against the marked dummy, %.3f"
                                + " against another, %d ticks left", against, plain, left));
                        return Math.abs(against - 0.25) < 1e-9 && Math.abs(plain - 0.05) < 1e-9 && left > 40 && left <= 60;
                    }))
                    .check("no cooldown while the blade is out, the blink open", () -> ServerQuery.ask(p -> {
                        CombatStateMachine m = PlayerCombat.of(p).machine();
                        summary.add(String.format(Locale.ROOT, "blade out: cooldown %d, held back %d, blink window %d",
                                m.abilityCooldown(), m.pendingCooldown(), m.recastWindow()));
                        return m.abilityCooldown() == 0 && m.pendingCooldown() == COOLDOWN && m.recastWindow() > 60;
                    }))
                    .waitUntil("the client's blink is open too", 20, () -> machine(mc).recastWindow() > 0)
                    // the right blade alone: L1 (as the right hook) on a second dummy 2 blocks ahead
                    .run("a second dummy 2 blocks ahead", () -> spawnAhead(2.0, DUMMY_B))
                    .waitUntil("the second dummy stands ahead", 40, () -> dummyCount(DUMMY_B) == 1)
                    .waitTicks(2)
                    .run("forget", hits::clear)
                    .hold(attack).waitTicks(1).release(attack)
                    .waitUntil("L1's first active tick", 20, () -> isFirstActive(mc, L1))
                    .check("one-bladed, L1 plays the right hook", () -> {
                        PlayerAnimations.State state = PlayerAnimations.state(mc.player);
                        summary.add("solo L1 plays " + state);
                        return !full || state.animation() != null && state.animation().getPath().equals("edges_l2");
                    })
                    .screenshot("edges_solo_l1_fp")
                    .waitUntil("its hit reached the server", 20, () -> !hits.isEmpty())
                    .waitTicks(2)
                    .check("the right blade alone deals 60%: 3.8 x 0.6", () -> expectHits("solo l1", 1, BASE * 0.6))
                    .waitUntil("idle", 40, () -> machine(mc).phase() == Phase.IDLE)
                    .run("clear the second dummy", () -> discard(DUMMY_B))
                    .run("a striker behind the player", () -> spawnAhead(-2.0, STRIKER))
                    .waitTicks(25);
        }
        steps.run("forget", () -> {
                    hits.clear();
                    healthBefore[0] = mc.player.getHealth();
                })
                .press(use)
                .check("the blink started", () -> isCurrent(mc, BLINK));
        if (measure) {
            steps.command("damage @s 2 minecraft:mob_attack by @e[tag=" + STRIKER + ",limit=1]")
                    .waitTicks(1)
                    .check("invulnerable while travelling: the server's machine and a hit that did nothing", () -> ServerQuery.ask(p -> {
                        boolean inv = PlayerCombat.of(p).machine().isInvulnerable() && EdgesEffects.isBlinking(p);
                        summary.add(String.format(Locale.ROOT, "blink travel: invulnerable %s, health %.1f -> %.1f", inv,
                                healthBefore[0], p.getHealth()));
                        return inv && p.getHealth() >= healthBefore[0] - 1e-3;
                    }));
        } else {
            steps.waitTicks(2);
        }
        shot(steps, "edges_blink_" + view, view);
        steps.waitUntil("the blink arrives (its first active tick)", 20, () -> isFirstActive(mc, BLINK));
        shot(steps, "edges_blink_arrival_" + view, view)
                .waitUntil("the arrival slash reached the server", 20, () -> !hits.isEmpty())
                .waitTicks(2)
                .check("the arrival slash dealt 3.8 x 2.0", () -> expectHits("blink arrival (" + view + ")", 1, BASE * 2.0));
        if (measure) {
            steps.check("arrived beside the dummy, the blade back in hand, the Mark renewed, the cooldown running",
                    () -> ServerQuery.ask(p -> {
                        Zombie dummy = dummies(p, DUMMY).get(0);
                        double gap = horizontal(p.position(), dummy.position());
                        CombatStateMachine m = PlayerCombat.of(p).machine();
                        int mark = TetherBlades.markTicksLeft(dummy);
                        summary.add(String.format(Locale.ROOT, "blink: %.2f blocks from the dummy, blade %s, mark %d ticks left,"
                                        + " cooldown %d (held back %d), travel from %.1f blocks away", gap, TetherBlades.bladeOf(p),
                                mark, m.abilityCooldown(), m.pendingCooldown(), horizontal(throwFrom, dummy.position())));
                        return gap < 2.2 && TetherBlades.bladeOf(p) == null && mark > 50 && mark <= 60
                                && m.abilityCooldown() > COOLDOWN - 8 && m.abilityCooldown() <= COOLDOWN && m.pendingCooldown() == 0;
                    }))
                    .run("the striker goes", () -> discard(STRIKER));
        }
        steps.run("stop sampling", () -> sampling = false)
                .waitUntil("idle again", 60, () -> machine(mc).phase() == Phase.IDLE)
                .command("tp @s ~ ~ ~-8")
                .look(0, FP_PITCH);
    }

    /** Thrown at nothing: it flies its 16 blocks, comes back, and the cooldown starts then. */
    private void tetherMiss(Steps steps, Minecraft mc) {
        KeyMapping use = mc.options.keyUse;
        steps.run("clear the dummies", () -> discard(DUMMY))
                .look(0, -25)
                .waitUntil("the cooldown is over", 200, () -> ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0))
                .run("Resonance", () -> resonance(mc, 100))
                .run("forget", () -> {
                    blade.clear();
                    throwFrom = mc.player.getEyePosition();
                    sampling = true;
                })
                .press(use)
                .waitUntil("the blade flew and turned back", 40, () -> blade.stream().anyMatch(s -> s.state() == ThrownSickle.RETURNING))
                .check("it flew 16 blocks, and the cooldown started when it turned back", () -> {
                    double far = 0;
                    BladeSample back = null;
                    for (BladeSample s : blade) {
                        if (s.state() == ThrownSickle.FLYING || back == null && s.state() == ThrownSickle.RETURNING) {
                            far = Math.max(far, s.at().distanceTo(throwFrom)); // up to where it turned back
                        }
                        if (back == null && s.state() == ThrownSickle.RETURNING) {
                            back = s;
                        }
                    }
                    BladeSample flying = blade.stream().filter(s -> s.state() == ThrownSickle.FLYING).reduce((a, b) -> b).orElse(null);
                    summary.add(String.format(Locale.ROOT, "miss: flew %.2f blocks from the eye; while flying cooldown %d held back %d;"
                            + " turning back cooldown %d held back %d", far, flying == null ? -1 : flying.cooldown(),
                            flying == null ? -1 : flying.pending(), back == null ? -1 : back.cooldown(), back == null ? -1 : back.pending()));
                    boolean ok = far > 15.0 && far < 17.5 && flying != null && flying.cooldown() == 0 && flying.pending() == COOLDOWN
                            && back != null && back.cooldown() > COOLDOWN - 3 && back.pending() == 0;
                    if (!ok) {
                        throw new Steps.Failure(summary.get(summary.size() - 1) + "; samples " + blade);
                    }
                    return true;
                })
                .run("stop sampling", () -> sampling = false)
                .look(0, FP_PITCH)
                .waitUntil("the blade is home", 40, () -> EdgesVisuals.bladeOf(mc.player) == null);
    }

    /** Into a wall 6 blocks ahead: stuck there for 80 ticks, then back, and the cooldown from then. */
    private void tetherIntoWall(Steps steps, Minecraft mc) {
        KeyMapping use = mc.options.keyUse;
        steps.look(0, 0)
                .command("fill ~-2 ~ ~6 ~2 ~3 ~6 minecraft:stone")
                .waitUntil("the cooldown is over", 200, () -> ServerQuery.ask(p -> PlayerCombat.of(p).machine().abilityCooldown() == 0))
                .run("Resonance", () -> resonance(mc, 100))
                .run("forget", () -> {
                    blade.clear();
                    sampling = true;
                })
                .press(use)
                .waitUntil("the blade stuck in the wall", 30, () -> blade.stream().anyMatch(s -> s.state() == ThrownSickle.STUCK))
                .waitTicks(6)
                .screenshot("edges_tether_wall_fp")
                .waitUntil("it came back", 120, () -> blade.stream().anyMatch(s -> s.state() == ThrownSickle.RETURNING))
                .check("stuck for 80 ticks in the wall, then back, the cooldown from then", () -> {
                    BladeSample first = blade.stream().filter(s -> s.state() == ThrownSickle.STUCK).findFirst().orElse(null);
                    BladeSample back = blade.stream().filter(s -> s.state() == ThrownSickle.RETURNING).findFirst().orElse(null);
                    long stuck = first == null || back == null ? -1 : back.time() - first.time();
                    summary.add(String.format(Locale.ROOT, "wall: stuck %d ticks (in %s), cooldown at return %d held back %d", stuck,
                            first == null ? "?" : first.stuckIn(), back == null ? -1 : back.cooldown(), back == null ? -1 : back.pending()));
                    boolean ok = first != null && first.stuckIn() == null && stuck >= 79 && stuck <= 81
                            && back.cooldown() > COOLDOWN - 3 && back.pending() == 0;
                    if (!ok) {
                        throw new Steps.Failure(summary.get(summary.size() - 1));
                    }
                    return true;
                })
                .run("stop sampling", () -> sampling = false)
                .command("fill ~-2 ~ ~6 ~2 ~3 ~6 minecraft:air")
                .look(0, FP_PITCH)
                .waitUntil("the blade is home", 40, () -> EdgesVisuals.bladeOf(mc.player) == null);
    }

    /** A screenshot; from the side camera for the side views (the player's own camera again right after). */
    private Steps shot(Steps steps, String label, String view) {
        if (!"side".equals(view)) {
            return steps.screenshot(label);
        }
        return steps.run("the side camera", () -> {
                    if (camera != null) {
                        camera.use();
                    }
                })
                .screenshot(label)
                .run("the player's camera", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setCameraEntity(mc.player);
                });
    }

    // ------------------------------------------------------------------ measuring

    private void listen() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            Entity e = event.getEntity();
            if (e.level().isClientSide()) {
                return;
            }
            for (String tag : List.of(DUMMY, DUMMY_B)) {
                if (e.getTags().contains(tag)) {
                    hits.add(new Hit(e.getId(), tag, event.getNewDamage(), e.level().getGameTime()));
                }
            }
        });
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.MoveStarted started) {
                movesStarted.add(started.move().id());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (!sampling) {
                return;
            }
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                ThrownSickle b = TetherBlades.bladeOf(p);
                CombatStateMachine m = PlayerCombat.of(p).machine();
                Integer in = b == null || b.stuckEntity() == null ? null : b.stuckEntity().getId();
                blade.add(new BladeSample(p.level().getGameTime(), b == null ? p.position() : b.position(),
                        b == null ? (byte) -1 : b.state(), in, m.abilityCooldown(), m.pendingCooldown(), m.isInvulnerable(),
                        EdgesEffects.isBlinking(p), b == null ? -1 : b.travelled(), b == null ? -1 : b.tickCount));
            }
        });
    }

    /** The hits since the last clear: {@code count} of them, each the expected amount (or a crit of it). */
    private boolean expectHits(String what, int count, double expected) {
        List<Hit> got = List.copyOf(hits);
        List<String> amounts = new ArrayList<>();
        boolean ok = got.size() == count;
        for (Hit h : got) {
            boolean plain = Math.abs(h.amount() - expected) <= TOLERANCE;
            boolean crit = Math.abs(h.amount() - expected * CRIT) <= TOLERANCE;
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

    /** Full Resonance on both machines (the Tether costs 25). */
    private static void resonance(Minecraft mc, int value) {
        CombatStateMachine local = machine(mc);
        local.syncFromServer(value, local.dashCharges(), local.abilityCooldown());
        ServerQuery.ask(p -> {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            m.syncFromServer(value, m.dashCharges(), m.abilityCooldown());
            return true;
        });
    }

    private static void discard(String tag) {
        ServerQuery.ask(p -> {
            p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(48.0), e -> e.getTags().contains(tag))
                    .forEach(Entity::discard);
            return true;
        });
    }

    private static void spawnAhead(double blocks, String tag) {
        ServerQuery.ask(p -> {
            MaulScenario.spawnDummy(p.serverLevel(), p.position().add(HitShape.forward(p.getYRot()).scale(blocks)), tag);
            return true;
        });
    }

    /** Puts the dummy with {@code tag} back {@code blocks} ahead of the player, still. */
    private static void placeDummy(String tag, double blocks) {
        ServerQuery.ask(p -> {
            Vec3 at = p.position().add(HitShape.forward(p.getYRot()).scale(blocks));
            for (Zombie z : dummies(p, tag)) {
                z.teleportTo(at.x, at.y, at.z);
                z.setDeltaMovement(Vec3.ZERO);
            }
            return true;
        });
    }

    private static int dummyCount(String tag) {
        return ServerQuery.ask(p -> dummies(p, tag).size());
    }

    private static List<Zombie> dummies(ServerPlayer p, String tag) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(24.0), z -> z.getTags().contains(tag));
    }

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }
}
