package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.GuardianCommands;
import com.cosmicbreach.guardian.colossus.ColossusMoves;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.CrownSpireLayout;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Prism Colossus and its Crown Spire (task G3), in parts:
 * <ul>
 *   <li>{@code colossus-looks}: the spire from outside, the arena, the Colossus from the front, the side and the
 *       back (dormant and awake, in daylight), and every telegraph: the slam's gold ring and glint, the Facet
 *       Sweep's white band, Refraction's red lines and beam, a Break, the Fracture, the Prism Burst's white glow,
 *       the double slam, the Shatter and the shards.</li>
 *   <li>{@code colossus-refraction} (1.2.1): arrows from a real bow turn the gold-lit crystal until its beam is turned back;
 *       the beam deals exactly 50 and Breaks it with an empty gauge (no parry first); the one-time hint shows on the first
 *       Refraction and not on the second.</li>
 *   <li>{@code colossus-rest} (1.2.1): every crystal rests two round and no resting path reaches the body, in the world's own
 *       geometry; eight forced Refractions (six in phase 1 from players round the ring, two in phase 2) never aim a path at the
 *       body, keep the core 1 to 3 turns away, never light opposite crystals together, and settle back to rest.</li>
 * </ul>
 */
public final class ColossusScenario implements Scenario {
    public enum Part { LOOKS, MECHANICS, SHATTER, WORLD, FIGHT, DEATH, REFRACTION, REST }

    private final Part part;
    private @Nullable DevCamera camera;
    private final List<String> summary = new ArrayList<>();

    public ColossusScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return part == Part.LOOKS ? 360 : 600;
    }

    /** The world part needs the Crown Spire to generate as the world is made. */
    @Override
    public boolean generateStructures() {
        return part == Part.WORLD;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal");
        if (part == Part.WORLD) {
            world(steps, mc);
        } else {
            buildLair(steps, mc);
        }
        switch (part) {
            case LOOKS -> looks(steps, mc);
            case MECHANICS -> mechanics(steps, mc);
            case SHATTER -> shatter(steps, mc);
            case FIGHT -> new ColossusFightBot(this, summary).steps(steps, mc);
            case DEATH -> death(steps, mc);
            case REFRACTION -> refraction(steps, mc);
            case REST -> rest(steps, mc);
            case WORLD -> {
            }
        }
        steps.run("back to the player's eyes", this::dropCamera)
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the lair

    private void buildLair(Steps steps, Minecraft mc) {
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the island is drawn", 1600, settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the lair is built and its Colossus stands", 200, () -> layout() != null && colossus(mc) != null)
                .log("lair", () -> {
                    CrownSpireLayout l = layout();
                    return String.format(Locale.ROOT, "Crown Spire: arena centre %d %d %d, foot at Y %d, root at Y %d (%d tall), lift door %s",
                            l.x(), l.floorY(), l.z(), l.baseY(), l.rootY(), l.floorY() - l.rootY(), java.util.Arrays.toString(l.riseDoorOutside()));
                })
                .run("note the lair", () -> {
                    CrownSpireLayout l = layout();
                    summary.add(String.format(Locale.ROOT, "lair: crown floor at Y %d, %d blocks from the root's tip", l.floorY() - 1,
                            l.floorY() - l.rootY()));
                });
    }

    // ------------------------------------------------------------------ what the test hears from the server

    private final java.util.concurrent.atomic.AtomicInteger perfectDodges = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger parries = new java.util.concurrent.atomic.AtomicInteger();
    private final List<Float> playerDamage = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Damage the Colossus and its shards dealt the player (server, as it happened). */
    List<Float> playerDamage() {
        return playerDamage;
    }

    private void listen() {
        com.cosmicbreach.combat.server.CombatHooks.register(new com.cosmicbreach.combat.server.CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, com.cosmicbreach.combat.PlayerCombat combat,
                                      com.cosmicbreach.combat.core.CombatEvent event) {
                if (event instanceof com.cosmicbreach.combat.core.CombatEvent.PerfectDodge) {
                    perfectDodges.incrementAndGet();
                } else if (event instanceof com.cosmicbreach.combat.core.CombatEvent.ParrySucceeded) {
                    parries.incrementAndGet();
                }
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer && (event.getSource().getEntity() instanceof PrismColossus
                            || event.getSource().getEntity() instanceof com.cosmicbreach.guardian.colossus.PrismShard)) {
                        playerDamage.add(event.getNewDamage());
                    }
                });
    }

    // ------------------------------------------------------------------ mechanics, with real inputs

    private final double[] measured = new double[8];
    private final long[] marks = new long[8];

    /** Level 12 with Power 6 and Resilience 4 of its own, the Starfall Vanguard on, Meridian in hand. */
    void equip(Steps steps, Minecraft mc) {
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach debug level 12")
                .command("cosmicbreach debug stats 6 2 0 4")
                .waitTicks(2)
                .command("clear @s")
                .command("item replace entity @s armor.head with cosmicbreach:starfall_vanguard_helm")
                .command("item replace entity @s armor.chest with cosmicbreach:starfall_vanguard_chestplate")
                .command("item replace entity @s armor.legs with cosmicbreach:starfall_vanguard_greaves")
                .command("item replace entity @s armor.feet with cosmicbreach:starfall_vanguard_boots")
                .command("cosmicbreach give")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .waitUntil("Meridian is in the main hand", 60,
                        () -> mc.player.getMainHandItem().is(com.cosmicbreach.registry.ModItems.MERIDIAN.get()))
                .waitUntil("level 12, Power 14 and Resilience 8 with the Vanguard", 60, () -> ServerQuery.ask(p -> {
                    com.cosmicbreach.combat.core.StatBlock s = com.cosmicbreach.progression.ProgressionStats.of(p);
                    return com.cosmicbreach.progression.Attunements.of(p).level() == 12 && Math.round(s.power()) == 14
                            && Math.round(s.resilience()) == 8 && p.getArmorValue() == 15;
                }))
                .log("player", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "player: level %d, armor %d, toughness %.0f, %s",
                        com.cosmicbreach.progression.Attunements.of(p).level(), p.getArmorValue(),
                        p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS),
                        com.cosmicbreach.progression.ProgressionStats.of(p))));
    }

    private void mechanics(Steps steps, Minecraft mc) {
        equip(steps, mc);
        rideTheLift(steps, mc);
        // the player has no Drift attunement: arriving on the crown wakes it
        steps.waitUntil("the Colossus wakes as the player arrives", 80, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .check(ColossusMoves.BASE_HEALTH + " health for one player", () -> ask(mc, c -> Math.abs(c.maxHealth() - ColossusMoves.BASE_HEALTH) < 0.01))
                .waitTicks(100)
                .run("look at it", () -> lookAt(mc, arena().centre().add(0, 4, 0)))
                .waitTicks(2)
                .screenshot("awakening")
                .waitUntil("the intro is over", 220, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .command("cosmicbreach debug colossus hold 1000000")
                .check("the boss music is on", com.cosmicbreach.client.guardian.GuardianMusic::active);
        standInFront(steps, mc, 8.0);
        // an unparried slam, measured against the Vanguard and Resilience 8
        steps.run("forget the damage so far", playerDamage::clear)
                .command("cosmicbreach debug colossus attack slam")
                .waitUntil("the slam landed", 60, () -> !playerDamage.isEmpty())
                .log("slam damage", () -> String.format(Locale.ROOT, "an unparried Prism Slam dealt %.2f through the Vanguard", playerDamage.get(0)))
                .check("about 9.4 (9.0 to 9.8)", () -> playerDamage.get(0) >= 9.0f && playerDamage.get(0) <= 9.8f)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Prism Slam through the Vanguard: %.2f", playerDamage.get(0))))
                .command("effect give @s minecraft:instant_health 1 3 true")
                .waitUntil("the fists are home", 80, () -> ask(mc, this::fistsHome))
                .waitTicks(30);
        parry(steps, mc, "parry_stuck");
        // the free hits on the stuck fist
        steps.run("walk up to the stuck fist", () -> {
                    Vec3 fist = ServerQuery.ask(p -> stuckFist(colossusOf(p)));
                    Vec3 me = mc.player.position();
                    Vec3 from = new Vec3(fist.x, arena().floorY(), fist.z).add(new Vec3(me.x - fist.x, 0, me.z - fist.z).normalize().scale(2.2));
                    tp(mc, from.x, from.y, from.z, fist.x, fist.y, fist.z);
                })
                .waitTicks(2)
                .run("look at the fist", () -> lookAt(mc, ServerQuery.ask(p -> stuckFist(colossusOf(p)))))
                .waitUntil("10 ticks after it landed", 20, () -> ask(mc, c -> stuckFor(c) >= 10))
                .check("still stuck in the floor (an unparried fist rests only 8)", () -> ask(mc, c -> c.fistRest(true) >= 0 || c.fistRest(false) >= 0))
                .run("health before the free hits", () -> measured[0] = health(mc));
        combo(steps, mc);
        steps.check("the free hits on the stuck fist landed", () -> health(mc) < measured[0] - 5.0)
                .log("free hits", () -> String.format(Locale.ROOT, "three free hits on the stuck fist dealt %.1f", measured[0] - health(mc)))
                .waitUntil("the fists are home", 120, () -> ask(mc, this::fistsHome))
                .waitTicks(20);
        // dash through a Facet Sweep
        standInFront(steps, mc, 6.0);
        steps.run("face outward", () -> {
                    Vec3 me = mc.player.position();
                    lookAt(mc, me.add(new Vec3(me.x - arena().x(), 0, me.z - arena().z()).normalize().scale(10)).add(0, 1.6, 0));
                })
                .run("reset the counters", () -> {
                    perfectDodges.set(0);
                    playerDamage.clear();
                })
                .command("cosmicbreach debug colossus attack sweep")
                .waitUntil("the band has glowed for 20 ticks", 40, () -> actionTick(mc) >= 20)
                .run("camera: the band from above", () -> place(mc, high(mc)))
                .waitTicks(1)
                .screenshot("sweep_band_live")
                .run("back to the player's eyes", this::dropCamera)
                .waitUntil("the sweep is two ticks from the player", 30, () -> actionTick(mc) >= 31)
                .hold(mc.options.keyUp)
                .press(com.cosmicbreach.client.combat.ModKeyMappings.DASH)
                .waitTicks(8)
                .release(mc.options.keyUp)
                .waitUntil("the sweep is over", 60, () -> ask(mc, c -> c.action() != PrismColossus.Action.SWEEP))
                .check("the sweep never touched the player", playerDamage::isEmpty)
                .check("the dash caught it in its first i-frames: a perfect dodge", () -> perfectDodges.get() >= 1)
                .run("note it", () -> summary.add("Facet Sweep dashed through: no damage, " + perfectDodges.get() + " perfect dodge(s)"))
                .waitUntil("the fists are home", 80, () -> ask(mc, this::fistsHome))
                .waitTicks(20);
        // the Refraction: parry first (60 in the gauge), then turn the lit crystal back into the core (it Breaks either way since 1.2.1)
        standInFront(steps, mc, 8.0);
        parry(steps, mc, null);
        steps.waitUntil("the fists are home", 120, () -> ask(mc, this::fistsHome))
                .command("cosmicbreach debug colossus attack refraction")
                .waitUntil("a crown crystal is lit", 20, () -> ask(mc, c -> c.litCrystals().length == 1))
                .log("refraction", () -> ServerQuery.ask(p -> {
                    PrismColossus c = colossusOf(p);
                    int k = c.litCrystals()[0];
                    marks[0] = k;
                    marks[1] = crystalTarget(p, k);
                    marks[2] = com.cosmicbreach.guardian.colossus.Refraction.stepsToCore(k, (int) marks[1]);
                    return String.format(Locale.ROOT, "crystal %d lit, aimed at %d: the core is %d turn(s) away; path %s", k, marks[1], marks[2],
                            java.util.Arrays.toString(c.refractionPaths().get(0)));
                }))
                .check("the core is 1 to 3 turns away", () -> marks[2] >= 1 && marks[2] <= 3)
                .run("step up to the lit crystal", () -> {
                    CrownArena a = arena();
                    Vec3 k = a.crystalPoint((int) marks[0]);
                    Vec3 in = new Vec3(a.x() - k.x, 0, a.z() - k.z).normalize();
                    Vec3 stand = new Vec3(k.x, a.floorY(), k.z).add(in.scale(2.4));
                    tp(mc, stand.x, stand.y, stand.z, k.x, a.floorY() + 1.6, k.z);
                })
                .waitTicks(1)
                .run("camera: the red lines", () -> place(mc, overShoulder(mc)))
                .waitTicks(1)
                .screenshot("refraction_turning")
                .run("back to the player's eyes", this::dropCamera)
                .run("look at the crystal", () -> {
                    Vec3 k = arena().crystalPoint((int) marks[0]);
                    lookAt(mc, new Vec3(k.x, arena().floorY() + 1.6, k.z));
                });
        swingUntil(steps, mc, "the beam's path ends at the core", 70, () -> ask(mc, c -> !c.refractionPaths().isEmpty()
                && com.cosmicbreach.guardian.colossus.Refraction.endsAtCore(c.refractionPaths().get(0))));
        steps.run("health before the beam", () -> {
                    measured[1] = health(mc);
                    marks[3] = ServerQuery.ask(p -> (long) colossusOf(p).coreHits());
                })
                .waitUntil("the beam fires and its light reaches the body", ColossusMoves.REFRACTION_CHARGE + 20,
                        () -> ask(mc, c -> c.coreHits() > marks[3]))
                .run("the beam's hit", () -> measured[2] = measured[1] - health(mc))
                .log("core hit", () -> String.format(Locale.ROOT, "the beam turned back into the core dealt %.2f", measured[2]))
                .check("exactly 50", () -> Math.abs(measured[2] - ColossusMoves.CORE_HIT_DAMAGE) < 0.01)
                .waitUntil("and it Breaks", ColossusMoves.CORE_HIT_BREAK_DELAY + 5, () -> ask(mc, PrismColossus::isBroken))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Refraction sent back into the core: %.1f damage, Break", measured[2])))
                .run("camera: the Break", () -> place(mc, side(12, 3.0)))
                .waitTicks(3)
                .screenshot("core_hit_break")
                .run("back to the player's eyes", this::dropCamera)
                .run("health at the Break", () -> measured[3] = health(mc));
        // hits during a Break take x1.5
        standInFront(steps, mc, 2.6);
        combo(steps, mc);
        steps.log("break hits", () -> String.format(Locale.ROOT, "a combo into the Broken core: %.1f", measured[3] - health(mc)))
                .waitUntil("the Break is over", 140, () -> ask(mc, c -> !c.isBroken()))
                .waitUntil("the fists are home", 80, () -> ask(mc, this::fistsHome));
        // phase 2: over the half-health line with real hits
        steps.command("cosmicbreach debug colossus health " + (int) (ColossusMoves.BASE_HEALTH / 2 + 2));
        standInFront(steps, mc, 2.6);
        swingUntil(steps, mc, "the Fracture", 120, () -> ask(mc, c -> c.state() == PrismColossus.State.FRACTURE));
        steps.check("it stops at half health", () -> Math.abs(health(mc) - ColossusMoves.BASE_HEALTH / 2) < 0.01)
                .waitTicks(20)
                .run("camera: the Fracture", () -> place(mc, front(15, 4.5)))
                .waitTicks(2)
                .screenshot("fracture_live")
                .run("back to the player's eyes", this::dropCamera)
                .waitUntil("phase 2", 80, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT && c.phase() == 2))
                .command("effect give @s minecraft:resistance 600 3 true");
        standInFront(steps, mc, 9.0);
        steps.command("cosmicbreach debug colossus attack double")
                .waitUntil("both fists are up (the second ring 12 ticks after the first)", 30,
                        () -> ask(mc, c -> c.fistMode(true) == PrismColossus.FistMode.SLAM && c.fistMode(false) == PrismColossus.FistMode.SLAM))
                .check("only the second glints gold", () -> ask(mc, c -> {
                    boolean firstRight = c.fistStart(true) < c.fistStart(false);
                    return c.fistGlints(!firstRight) && !c.fistGlints(firstRight)
                            && Math.abs(c.fistStart(true) - c.fistStart(false)) == com.cosmicbreach.guardian.colossus.ColossusMoves.DOUBLE_SLAM_DELAY;
                }))
                .waitTicks(4)
                .run("camera: the double slam", () -> place(mc, beside(mc)))
                .waitTicks(1)
                .screenshot("double_slam_live")
                .run("back to the player's eyes", this::dropCamera)
                .waitUntil("the fists are home", 120, () -> ask(mc, this::fistsHome))
                .waitTicks(10)
                .command("cosmicbreach debug colossus attack refraction")
                .waitUntil("two crystals lit, two beams", 20, () -> ask(mc, c -> c.litCrystals().length == 2 && c.refractionPaths().size() == 2))
                .waitTicks(10)
                .run("camera: two beams", () -> place(mc, above(14)))
                .waitTicks(1)
                .screenshot("two_beams_lines")
                .waitUntil("they fire", ColossusMoves.REFRACTION_CHARGE + 10, () -> actionTick(mc) >= ColossusMoves.REFRACTION_CHARGE + 8)
                .screenshot("two_beams_firing")
                .run("back to the player's eyes", this::dropCamera)
                .run("note it", () -> summary.add("phase 2: a double slam (only the second glints) and two beams"));
    }

    /** Rides the lift: from the spire's door into the rising light, up to the crown, set down beside the altar. */
    private void rideTheLift(Steps steps, Minecraft mc) {
        steps.run("at the spire's door, facing the rising light", () -> {
                    CrownSpireLayout l = layout();
                    int[] d = l.riseDoorOutside();
                    int[] r = l.riseColumn();
                    tp(mc, d[0] + 0.5, d[1], d[2] + 0.5, r[0] + 0.5, l.baseY() + 1.6, r[1] + 0.5);
                })
                .waitTicks(20)
                .waitUntil("the door is drawn", 600, settled(mc, 400))
                .run("look at the column", () -> {
                    CrownSpireLayout l = layout();
                    int[] r = l.riseColumn();
                    lookAt(mc, new Vec3(r[0] + 0.5, l.baseY() + 1.6, r[1] + 0.5));
                })
                .hold(mc.options.keyUp)
                .waitTicks(30)
                .log("walking", () -> String.format(Locale.ROOT, "walking at %s, yaw %.0f", mc.player.position(), mc.player.getYRot()))
                .screenshot("walking_in")
                .waitUntil("walked into the rising light", 200, () -> inLight(mc, com.cosmicbreach.guardian.GuardianRegistry.RISING_LIGHT.get()))
                .release(mc.options.keyUp)
                .run("the ride starts", () -> marks[5] = mc.level.getGameTime())
                .waitTicks(60)
                .screenshot("lift_ride")
                .waitUntil("set down on the crown", 500, () -> mc.player.onGround() && mc.player.getY() >= layout().floorY() - 0.01)
                .run("the ride ends", () -> marks[6] = mc.level.getGameTime())
                .log("lift", () -> {
                    int[] a = layout().altar();
                    double d = Math.hypot(mc.player.getX() - (a[0] + 0.5), mc.player.getZ() - (a[2] + 0.5));
                    measured[7] = d;
                    return String.format(Locale.ROOT, "the lift carried the player %d blocks in %.1f s, set down %.1f blocks from the altar",
                            layout().floorY() - layout().baseY(), (marks[6] - marks[5]) / 20.0, d);
                })
                .check("set down beside the altar (within 3.5 blocks)", () -> measured[7] <= 3.5)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "lift: %d blocks in %.1f s", layout().floorY() - layout().baseY(),
                        (marks[6] - marks[5]) / 20.0)));
    }

    /** Parries the next Prism Slam with the parry key, on the gold glint; checks the stuck fist and the gauge. */
    private void parry(Steps steps, Minecraft mc, @Nullable String shot) {
        steps.run("reset the counters", () -> {
                    parries.set(0);
                    playerDamage.clear();
                })
                .command("cosmicbreach debug colossus attack slam")
                .waitUntil("the gold glint", 40, () -> slamTick(mc) >= com.cosmicbreach.guardian.colossus.ColossusMoves.SLAM_GLINT + 1)
                .press(com.cosmicbreach.client.combat.ModKeyMappings.PARRY)
                .waitUntil("the slam met the parry", 30, () -> parries.get() >= 1 || !playerDamage.isEmpty())
                .check("parried, no damage", () -> parries.get() >= 1 && playerDamage.isEmpty())
                .waitUntil("the fist is in the floor", 10, () -> ask(mc, c -> c.fistRest(true) >= 0 || c.fistRest(false) >= 0))
                .check("stuck for 30 ticks", () -> ask(mc, c -> c.fistRest(true) == 30 || c.fistRest(false) == 30))
                .check(shot != null ? "60 of 150 in the Break gauge" : "at least the parry's 60 in the gauge",
                        () -> ask(mc, c -> shot != null ? Math.abs(c.gaugeFraction() - 0.4) < 0.01 : c.gaugeFraction() >= 0.399))
                .waitUntil("the bar shows the gauge", 20, () -> com.cosmicbreach.client.guardian.GuardianBarHud.anyGauge() >= 0.38f);
        if (shot != null) {
            steps.run("the HUD on, a step back", () -> mc.options.hideGui = false)
                    .run("camera: the stuck fist", () -> place(mc, beside(mc)))
                    .run("but show the HUD", () -> mc.options.hideGui = false)
                    .waitTicks(2)
                    .screenshot(shot)
                    .run("back to the player's eyes", this::dropCamera)
                    .run("note it", () -> summary.add("Prism Slam parried on the glint: fist stuck 30 ticks, gauge 60/150"));
        }
    }

    /** Presses attack three times, a combo's rhythm. */
    private void combo(Steps steps, Minecraft mc) {
        steps.press(mc.options.keyAttack)
                .waitTicks(8)
                .press(mc.options.keyAttack)
                .waitTicks(9)
                .press(mc.options.keyAttack)
                .waitTicks(16);
    }

    /** Swings (a press every 9 ticks) until {@code done}, at most {@code timeout} ticks. */
    private void swingUntil(Steps steps, Minecraft mc, String what, int timeout, BooleanSupplier done) {
        int[] tick = {0};
        net.minecraft.client.KeyMapping key = mc.options.keyAttack;
        steps.waitUntil(what + " (swinging)", timeout, () -> {
            if (done.getAsBoolean()) {
                net.minecraft.client.KeyMapping.set(key.getKey(), false);
                return true;
            }
            int t = tick[0]++;
            if (t % 9 == 0) {
                net.minecraft.client.KeyMapping.set(key.getKey(), true);
                net.minecraft.client.KeyMapping.click(key.getKey());
            } else if (t % 9 == 1) {
                net.minecraft.client.KeyMapping.set(key.getKey(), false);
            }
            return false;
        });
    }

    private void standInFront(Steps steps, Minecraft mc, double distance) {
        steps.run("stand " + distance + " blocks in front of it", () -> {
                    CrownArena a = arena();
                    float yaw = ServerQuery.ask(p -> colossusOf(p).getYRot());
                    Vec3 at = new Vec3(a.x(), a.floorY(), a.z()).add(CrownArena.forward(yaw).scale(distance));
                    tp(mc, at.x, at.y, at.z, a.x(), a.floorY() + 3.5, a.z());
                })
                .waitTicks(3);
    }

    // ------------------------------------------------------------------ dying to it

    private final java.util.UUID[] colossusIds = new java.util.UUID[2];

    /**
     * {@code colossus-death}: the player's respawn point at the spire's foot (below the crown, out of the bar's
     * reach), a fight, a death to a Prism Slam, a respawn: no Colossus bar may stay on screen. Then the fight resets
     * (nobody in the arena) and the ride back up wakes a new one: exactly one bar. Then the same again with a respawn
     * in the Overworld, which leaves the lair's chunks to unload mid-fight.
     */
    private void death(Steps steps, Minecraft mc) {
        steps.command("gamerule keepInventory true");
        equip(steps, mc);
        steps.run("the respawn point at the spire's foot", () -> ServerQuery.ask(p -> {
            int[] d = layout().riseDoorOutside();
            p.setRespawnPosition(p.level().dimension(), new net.minecraft.core.BlockPos(d[0], d[1], d[2]), 0f, true, false);
            return true;
        }));
        dieInAFight(steps, mc, "spire foot", 0);
        steps.log("after respawning", () -> ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "respawned at %s in %s; colossus %s; bars on screen: %d vanilla, %d gauges",
                        p.blockPosition(), p.level().dimension().location(), describeColossus(p), vanillaBars(mc),
                        com.cosmicbreach.client.guardian.GuardianBarHud.count())))
                .check("respawned out of its reach: no Colossus bar on screen", () -> vanillaBars(mc) == 0
                        && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 0)
                .check("and no boss music", () -> !com.cosmicbreach.client.guardian.GuardianMusic.active())
                .waitUntil("the fight resets with nobody in the arena", 800, () -> ask(mc, c -> c.state() == PrismColossus.State.DORMANT))
                .check("still no bar", () -> vanillaBars(mc) == 0 && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 0);
        rideTheLift(steps, mc);
        steps.waitUntil("the ride back up wakes it again", 100, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .waitTicks(20)
                .log("the new fight", () -> String.format(Locale.ROOT, "bars on screen: %d vanilla, %d gauges", vanillaBars(mc),
                        com.cosmicbreach.client.guardian.GuardianBarHud.count()))
                .check("exactly one Colossus bar, the new fight's", () -> vanillaBars(mc) == 1
                        && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 1)
                .screenshot("death_new_fight_bar")
                .run("note it", () -> summary.add("death with the respawn at the spire's foot: bar gone, one bar in the new fight"));

        // the far respawn: the Overworld, so the lair's chunks unload with the fight under way
        steps.run("no respawn point (the Overworld's spawn)", () -> ServerQuery.ask(p -> {
            p.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, null, 0f, false, false);
            return true;
        }));
        steps.waitUntil("the intro is over", 260, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT));
        dieInAFight(steps, mc, "overworld", 1);
        steps.waitTicks(200)
                .log("in the Overworld", () -> ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "respawned in %s; bars on screen: %d vanilla, %d gauges", p.level().dimension().location(), vanillaBars(mc),
                        com.cosmicbreach.client.guardian.GuardianBarHud.count())))
                .check("respawned in the Overworld: no Colossus bar on screen", () -> vanillaBars(mc) == 0
                        && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 0)
                .check("and the server shows no guardian bar to anyone (the unloaded fight's bar is gone)",
                        () -> ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianBossBar.liveCount() == 0))
                .command("cosmicbreach debug goto reach")
                .waitUntil("back in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitTicks(40);
        rideTheLift(steps, mc);
        steps.waitUntil("a Colossus stands and wakes", 200, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .waitTicks(20)
                .log("the fight after the far respawn", () -> ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "colossus %s; bars on screen: %d vanilla, %d gauges", describeColossus(p), vanillaBars(mc),
                        com.cosmicbreach.client.guardian.GuardianBarHud.count())))
                .check("exactly one Colossus bar again", () -> vanillaBars(mc) == 1
                        && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 1)
                .run("note it", () -> summary.add("death with the respawn in the Overworld: bar gone, one bar in the next fight"));
    }

    /** Wakes it (the lift), lets the intro pass, stands in front at 1 health, takes a slam, respawns. */
    private void dieInAFight(Steps steps, Minecraft mc, String where, int k) {
        if (k == 0) {
            rideTheLift(steps, mc);
            steps.waitUntil("the Colossus wakes as the player arrives", 80, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                    .waitUntil("the intro is over", 220, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT));
        }
        steps.command("cosmicbreach debug colossus hold 1000000")
                .waitTicks(10)
                .check("one Colossus bar on screen in the fight (" + where + ")", () -> vanillaBars(mc) == 1
                        && com.cosmicbreach.client.guardian.GuardianBarHud.count() == 1)
                .run("note the Colossus", () -> colossusIds[k] = ServerQuery.ask(p -> colossusOf(p).getUUID()));
        standInFront(steps, mc, 8.0);
        steps.run("down to 1 health", () -> ServerQuery.ask(p -> {
                    p.setHealth(1f);
                    return true;
                }))
                .run("forget the damage so far", playerDamage::clear)
                .command("cosmicbreach debug colossus attack slam")
                .waitUntil("the slam landed", 80, () -> !playerDamage.isEmpty())
                .waitUntil("the slam killed the player", 60, () -> mc.player.isDeadOrDying()
                        || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)
                .waitTicks(20)
                .log("dead", () -> String.format(Locale.ROOT, "dead (%s); bars on screen: %d vanilla, %d gauges", where, vanillaBars(mc),
                        com.cosmicbreach.client.guardian.GuardianBarHud.count()))
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("respawned", 200, () -> {
                    if (mc.player != null && mc.player.isAlive() && mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
                        mc.setScreen(null);
                    }
                    return mc.player != null && mc.player.isAlive() && mc.screen == null;
                })
                .waitTicks(60);
    }

    private static String describeColossus(ServerPlayer p) {
        CrownSpireLayout l = layout();
        if (l == null) {
            return "no lair";
        }
        net.minecraft.server.level.ServerLevel level = p.server.getLevel(AetheriaWorld.LEVEL);
        if (level == null) {
            return "no Aetheria";
        }
        List<PrismColossus> all = level.getEntitiesOfClass(PrismColossus.class, l.arena().bounds());
        return all.isEmpty() ? "not loaded" : all.size() + " loaded, " + all.get(0).getUUID() + " " + all.get(0).state();
    }

    /** Boss bars on the vanilla overlay of this client. */
    static int vanillaBars(Minecraft mc) {
        net.minecraft.client.gui.components.BossHealthOverlay overlay = mc.gui.getBossOverlay();
        try {
            for (java.lang.reflect.Field f : net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return ((java.util.Map<?, ?>) f.get(overlay)).size();
                }
            }
        } catch (IllegalAccessException e) {
            throw new Steps.Failure("can't read the boss overlay: " + e);
        }
        throw new Steps.Failure("the boss overlay has no map of bars");
    }

    // ------------------------------------------------------------------ Shatter, rewards, the second kill, the reset

    private void shatter(Steps steps, Minecraft mc) {
        equip(steps, mc);
        steps.command("effect give @s minecraft:resistance 100000 3 true")
                .command("effect give @s minecraft:regeneration 100000 1 true")
                .run("before: XP and points", () -> ServerQuery.ask(p -> {
                    com.cosmicbreach.progression.Attunement a = com.cosmicbreach.progression.Attunements.of(p);
                    marks[3] = a.totalXp();
                    marks[4] = a.bonusPoints();
                    return true;
                }))
                .check("not attuned to the Drift yet", () -> ServerQuery.ask(p -> !com.cosmicbreach.world.LayerAttunement.has(p,
                        com.cosmicbreach.world.Layer.DRIFT)))
                .run("onto the crown", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 8.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("it wakes", 80, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .waitUntil("the intro is over", 220, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .command("cosmicbreach debug colossus hold 1000000")
                .run("hit it once (a participant)", () -> {
                })
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters into three shards", 20, () -> ask(mc, c -> c.state() == PrismColossus.State.SHATTERED
                        && c.shardIds().size() == 3))
                .waitTicks(30)
                .run("camera: the shards", () -> place(mc, above(12)))
                .waitTicks(2)
                .screenshot("shards_live")
                .run("back to the player's eyes", this::dropCamera);
        killShards(steps, mc, 1, 400);
        steps.check("the first death started the 20 s countdown", () -> ask(mc, c -> c.shatterState() != null && c.shatterState().counting()))
                .run("the HUD on", () -> mc.options.hideGui = false)
                .run("look at the arena", () -> lookAt(mc, arena().centre().add(0, 2, 0)))
                .waitTicks(20)
                .screenshot("countdown")
                .check("the bar counts down", () -> com.cosmicbreach.client.guardian.GuardianBarHud.count() >= 1)
                .run("stand back and let it run out", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 16.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("the survivors fly home: the re-merge", 440, () -> ask(mc, c -> c.state() == PrismColossus.State.REFORMING))
                .waitUntil("it re-forms", 80, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .log("re-merge", () -> String.format(Locale.ROOT, "re-formed at %.1f of %.1f", health(mc), maxHealth(mc)))
                .check("at 25% of its health (" + ColossusMoves.BASE_HEALTH * 0.25 + ")", () -> Math.abs(health(mc) - ColossusMoves.BASE_HEALTH * 0.25) < 0.01)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "countdown ran out: re-formed at %.0f (25%%)", health(mc))))
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters again", 20, () -> ask(mc, c -> c.state() == PrismColossus.State.SHATTERED && c.shardIds().size() == 3))
                .waitTicks(30)
                .run("the kill's clock starts", () -> marks[7] = mc.level.getGameTime());
        killShards(steps, mc, 3, 400);
        steps.run("the kill's clock stops", () -> measured[6] = (mc.level.getGameTime() - marks[7]) / 20.0)
                .log("shards", () -> String.format(Locale.ROOT, "all three shards killed in %.1f s", measured[6]))
                .waitUntil("the Colossus dies", 100, () -> colossus(mc) == null)
                .waitTicks(30)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "three shards killed in %.1f s: the kill", measured[6])));
        rewardChecks(steps, mc);
        secondKill(steps, mc);
        resetCheck(steps, mc);
    }

    private void rewardChecks(Steps steps, Minecraft mc) {
        steps.check("a Prism Heart", () -> count(mc, com.cosmicbreach.registry.ModMaterials.PRISM_HEART.get()) == 1)
                .check("a Heart of a Dying Star", () -> count(mc, com.cosmicbreach.guardian.GuardianRegistry.HEART_OF_A_DYING_STAR.get()) == 1)
                .check("3,000 Attunement XP", () -> ServerQuery.ask(p -> com.cosmicbreach.progression.Attunements.of(p).totalXp() - marks[3] == 3000))
                .check("a stat point", () -> ServerQuery.ask(p -> com.cosmicbreach.progression.Attunements.of(p).bonusPoints() - marks[4] == 1))
                .check("the \"Refracted\" advancement", () -> ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianRewards.hasAdvancement(p,
                        com.cosmicbreach.guardian.GuardianTypes.COLOSSUS.advancement())))
                .check("attuned to the Drift", () -> ServerQuery.ask(p -> com.cosmicbreach.world.LayerAttunement.has(p, com.cosmicbreach.world.Layer.DRIFT)))
                .check("the lair rests for 20 minutes", () -> ServerQuery.ask(p -> {
                    com.cosmicbreach.guardian.GuardianAltarBlockEntity altar = altarOf(p);
                    return altar != null && !altar.armed() && altar.clock().remaining(p.level().getGameTime()) > 23_000;
                }))
                .check("the pillar dimmed to a stump", () -> ServerQuery.ask(p -> !p.level().getBlockState(arena().centreBlock())
                        .getValue(com.cosmicbreach.guardian.colossus.CrownPillarBlock.LIT)))
                .run("the HUD off", () -> mc.options.hideGui = true)
                .run("look down at the stump", () -> lookAt(mc, arena().centre()))
                .waitTicks(3)
                .screenshot("stump")
                .run("note it", () -> summary.add("first kill: Prism Heart, Heart of a Dying Star, 3,000 XP, a stat point, Refracted, the Drift"))
                .run("before the repeat", () -> ServerQuery.ask(p -> {
                    com.cosmicbreach.progression.Attunement a = com.cosmicbreach.progression.Attunements.of(p);
                    marks[3] = a.totalXp();
                    marks[4] = a.bonusPoints();
                    return true;
                }))
                .command("clear @s cosmicbreach:prism_heart")
                .command("clear @s cosmicbreach:heart_of_a_dying_star");
    }

    private void secondKill(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug colossus cooldown")
                .waitUntil("a new statue stands", 100, () -> colossus(mc) != null)
                .waitTicks(40)
                .check("an attuned player walking in doesn't wake it", () -> ask(mc, c -> c.state() == PrismColossus.State.DORMANT))
                .command("give @s cosmicbreach:guardian_echo 2");
        useEcho(steps, mc);
        steps.waitUntil("the Echo wakes it", 40, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .waitUntil("the Echo was spent", 20, () -> count(mc, com.cosmicbreach.guardian.GuardianRegistry.GUARDIAN_ECHO.get()) == 1)
                .run("Meridian back in hand", () -> selectHotbar(mc, com.cosmicbreach.registry.ModItems.MERIDIAN.get()))
                .waitUntil("the intro is over", 220, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .command("cosmicbreach debug colossus hold 1000000");
        standInFront(steps, mc, 2.6);
        combo(steps, mc);
        steps.command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters", 20, () -> ask(mc, c -> c.state() == PrismColossus.State.SHATTERED))
                .waitTicks(30)
                .command("kill @e[type=cosmicbreach:prism_shard]")
                .waitUntil("the second kill", 120, () -> colossus(mc) == null)
                .waitTicks(30)
                .check("repeat kill: 600 XP", () -> ServerQuery.ask(p -> com.cosmicbreach.progression.Attunements.of(p).totalXp() - marks[3] == 600))
                .check("no stat point", () -> ServerQuery.ask(p -> com.cosmicbreach.progression.Attunements.of(p).bonusPoints() == marks[4]))
                .check("4 to 8 Spire Quartz", () -> {
                    int q = count(mc, com.cosmicbreach.registry.ModBlocks.SPIRE_QUARTZ.get().asItem());
                    return q >= 4 && q <= 8;
                })
                .log("repeat drops", () -> String.format(Locale.ROOT, "repeat kill: %d Spire Quartz, %d Prism Heart, %d Heart of a Dying Star, %d Heartstone",
                        count(mc, com.cosmicbreach.registry.ModBlocks.SPIRE_QUARTZ.get().asItem()),
                        count(mc, com.cosmicbreach.registry.ModMaterials.PRISM_HEART.get()),
                        count(mc, com.cosmicbreach.guardian.GuardianRegistry.HEART_OF_A_DYING_STAR.get()),
                        count(mc, com.cosmicbreach.registry.ModMaterials.HEARTSTONE.get())))
                .run("note it", () -> summary.add("second kill (Guardian Echo): repeat drops only, 600 XP, no stat point"));
    }

    private void resetCheck(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug colossus cooldown")
                .waitUntil("a new statue stands", 100, () -> colossus(mc) != null)
                .waitTicks(20);
        useEcho(steps, mc);
        steps.waitUntil("the Echo wakes it", 40, () -> ask(mc, c -> c.state() == PrismColossus.State.INTRO))
                .waitUntil("the intro is over", 220, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .run("leave by the falling well", () -> {
                    int[] f = layout().fallColumn();
                    tp(mc, f[0] + 0.5, arena().floorY() - 0.4, f[1] + 0.5, f[0] + 0.5, arena().floorY() - 4, f[1] + 3.5);
                })
                .run("the reset's clock starts", () -> marks[7] = mc.level.getGameTime())
                .waitUntil("lowered to the spire's foot", 600, () -> mc.player.onGround() && mc.player.getY() < layout().baseY() + 1.5)
                .log("falling well", () -> {
                    measured[5] = (mc.level.getGameTime() - marks[7]) / 20.0;
                    return String.format(Locale.ROOT, "the falling well lowered the player %d blocks in %.1f s, landing unhurt: %s",
                            layout().floorY() - layout().baseY(), measured[5], mc.player.getHealth() >= mc.player.getMaxHealth() - 0.01);
                })
                .check("unhurt by the descent", () -> ServerQuery.ask(p -> p.getHealth() >= p.getMaxHealth() - 0.01))
                .waitTicks(100)
                .log("outside", () -> ServerQuery.ask(p -> {
                    PrismColossus c = colossusOf(p);
                    return String.format(Locale.ROOT, "player at %s (%s, alive %s), colossus %s, %d inside, server t=%d",
                            p.position(), p.level().dimension().location(), p.isAlive(), c.state(), c.fightersInside().size(), p.level().getGameTime());
                }))
                .waitUntil("it resets to a dormant statue", 700, () -> ask(mc, c -> c.state() == PrismColossus.State.DORMANT))
                .log("reset", () -> String.format(Locale.ROOT, "reset %.1f s after the player left", (mc.level.getGameTime() - marks[7]) / 20.0))
                .check("about 30 s", () -> Math.abs((mc.level.getGameTime() - marks[7]) - 600) <= 25)
                .check("at full health", () -> Math.abs(health(mc) - ColossusMoves.BASE_HEALTH) < 0.01)
                .check("with no loot and the lair still armed", () -> ServerQuery.ask(p -> {
                    com.cosmicbreach.guardian.GuardianAltarBlockEntity altar = altarOf(p);
                    return altar != null && altar.armed();
                }))
                .check("and its bar gone", () -> com.cosmicbreach.client.guardian.GuardianBarHud.count() == 0)
                .run("note it", () -> summary.add("reset: dormant at full health 30 s after the player left"));
    }

    /** Walks up to the altar, takes the Guardian Echo in hand and uses it on the altar (the use key). */
    private void useEcho(Steps steps, Minecraft mc) {
        steps.run("to the altar", () -> {
                    int[] a = layout().altar();
                    CrownArena ar = arena();
                    Vec3 in = new Vec3(ar.x() - a[0], 0, ar.z() - a[2]).normalize();
                    tp(mc, a[0] + 0.5 + in.x * 2.2, a[1], a[2] + 0.5 + in.z * 2.2, a[0] + 0.5, a[1] + 0.6, a[2] + 0.5);
                })
                .waitTicks(3)
                .run("hold the Echo", () -> selectHotbar(mc, com.cosmicbreach.guardian.GuardianRegistry.GUARDIAN_ECHO.get()))
                .waitTicks(2)
                .run("look at the altar", () -> {
                    int[] a = layout().altar();
                    lookAt(mc, new Vec3(a[0] + 0.5, a[1] + 0.6, a[2] + 0.5));
                })
                .waitTicks(2)
                .press(mc.options.keyUse);
    }

    /** Kills {@code n} shards with swings: step up to the nearest, face it, swing until it dies. */
    private void killShards(Steps steps, Minecraft mc, int n, int timeout) {
        int[] start = {-1};
        int[] tick = {0};
        net.minecraft.client.KeyMapping key = mc.options.keyAttack;
        steps.run("count the shards", () -> start[0] = ServerQuery.ask(p -> colossusOf(p).shatterState().alive()))
                .waitUntil(n + " shard(s) killed (swinging)", timeout, () -> {
                    int alive = ServerQuery.ask(p -> {
                        PrismColossus c = colossusOrNull(p);
                        return c == null || c.shatterState() == null ? 0 : c.shatterState().alive();
                    });
                    if (start[0] - alive >= n || alive == 0) {
                        net.minecraft.client.KeyMapping.set(key.getKey(), false);
                        return true;
                    }
                    int t = tick[0]++;
                    Vec3 shard = ServerQuery.ask(p -> {
                        PrismColossus c = colossusOrNull(p);
                        if (c == null) {
                            return null;
                        }
                        com.cosmicbreach.guardian.colossus.PrismShard best = null;
                        for (java.util.UUID id : c.shardIds()) {
                            if (p.serverLevel().getEntity(id) instanceof com.cosmicbreach.guardian.colossus.PrismShard s && s.isAlive()
                                    && (best == null || s.distanceToSqr(p) < best.distanceToSqr(p))) {
                                best = s;
                            }
                        }
                        return best == null ? null : best.position();
                    });
                    if (shard == null) {
                        return false;
                    }
                    Vec3 me = mc.player.position();
                    if (me.distanceTo(shard) > 2.6 && t % 9 == 2) {
                        Vec3 from = shard.add(new Vec3(me.x - shard.x, 0, me.z - shard.z).normalize().scale(1.6));
                        tp(mc, from.x, shard.y, from.z, shard.x, shard.y + 0.3, shard.z);
                    }
                    lookAt(mc, shard.add(0, 0.3, 0));
                    if (t % 9 == 0) {
                        net.minecraft.client.KeyMapping.set(key.getKey(), true);
                        net.minecraft.client.KeyMapping.click(key.getKey());
                    } else if (t % 9 == 1) {
                        net.minecraft.client.KeyMapping.set(key.getKey(), false);
                    }
                    return false;
                });
    }

    // ------------------------------------------------------------------ a lair the world generated

    private @Nullable CrownArena natural;

    /**
     * A Crown Spire the world generated: found by the locate API and the commands, visited, its statue raised by
     * the altar; then the anti-cheese rules there (a block placed in the arena crumbles, an arrow from afar burns up).
     */
    private void world(Steps steps, Minecraft mc) {
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the island is drawn", 1600, settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("locate structure cosmicbreach:crown_spire")
                .waitForChat("The nearest cosmicbreach:crown_spire is at", 600)
                .command("cosmicbreach debug locate")
                .waitForChat("Nearest colossus lair: -?\\d+, \\d+, -?\\d+", 600)
                .run("the locate API", () -> {
                    long t0 = System.nanoTime();
                    net.minecraft.core.BlockPos found = ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianLairs.nearest(p.serverLevel(),
                            p.blockPosition(), com.cosmicbreach.guardian.GuardianTypes.COLOSSUS).orElse(null));
                    summary.add(String.format(Locale.ROOT, "GuardianLairs.nearest answered in %.2f s", (System.nanoTime() - t0) / 1e9));
                    if (found == null) {
                        throw new Steps.Failure("GuardianLairs.nearest found no Crown Spire");
                    }
                    natural = CrownArena.at(found);
                    double d = Math.hypot(found.getX() - mc.player.getX(), found.getZ() - mc.player.getZ());
                    summary.add(String.format(Locale.ROOT, "GuardianLairs.nearest: a Crown Spire %.0f blocks away, crown floor at Y %d", d,
                            found.getY() - 1));
                })
                .command("gamemode creative")
                .run("fly to it", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                    tp(mc, natural.x() + 30.5, natural.floorY() + 12, natural.z() + 30.5, natural.x(), natural.floorY(), natural.z());
                })
                .waitTicks(60)
                .waitUntil("the lair is drawn", 1600, settled(mc, 1400))
                .waitUntil("the world built its crown", 400, () -> ServerQuery.ask(p -> p.level().getBlockState(natural.centreBlock())
                        .is(com.cosmicbreach.guardian.GuardianRegistry.CROWN_PILLAR.get())))
                .waitUntil("its altar raised a dormant statue", 400, () -> ServerQuery.ask(p -> {
                    PrismColossus c = p.serverLevel().getEntitiesOfClass(PrismColossus.class, natural.bounds()).stream().findFirst().orElse(null);
                    return c != null && c.dormant();
                }))
                .check("and registered itself for the locate API", () -> ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianLairs
                        .get(p.serverLevel()).nearestKnown(com.cosmicbreach.guardian.GuardianTypes.COLOSSUS, p.blockPosition())
                        .map(b -> b.equals(natural.centreBlock())).orElse(false)))
                .check("the Guardian Echo is a Forge recipe", () -> mc.level.getRecipeManager()
                        .byKey(com.cosmicbreach.CosmicBreach.id("forge/guardian_echo")).isPresent())
                .run("the HUD hidden", () -> mc.options.hideGui = true)
                .run("camera: the generated spire", () -> place(mc, new Vec3[] {
                        new Vec3(natural.x() + 60, natural.floorY() - 20, natural.z() + 70), new Vec3(natural.x(), natural.floorY() - 35, natural.z())}))
                .waitTicks(20)
                .screenshot("world_spire")
                .run("camera: its crown", () -> place(mc, new Vec3[] {
                        new Vec3(natural.x() + 20, natural.floorY() + 12, natural.z() + 20), new Vec3(natural.x(), natural.floorY() + 2, natural.z())}))
                .waitTicks(5)
                .screenshot("world_crown")
                .run("back to the player's eyes", this::dropCamera)
                .run("the HUD on", () -> mc.options.hideGui = false)
                .run("note it", () -> summary.add("the world generated the lair; its altar raised the statue and registered it"));
        // the anti-cheese rules, in a fight at this lair
        steps.command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("give @s minecraft:stone 4")
                .run("onto the crown", () -> tp(mc, natural.x() + 0.5, natural.floorY(), natural.z() + 9.5, natural.x(), natural.floorY() + 3,
                        natural.z()))
                .waitUntil("an unattuned player on the crown wakes it", 100, () -> ServerQuery.ask(p -> {
                    PrismColossus c = p.serverLevel().getEntitiesOfClass(PrismColossus.class, natural.bounds()).stream().findFirst().orElse(null);
                    return c != null && c.state() == PrismColossus.State.INTRO;
                }))
                // the given stone reaches this client a round trip after the command, and the wake above can pass in
                // the same tick as the command: wait for the stone rather than read the hotbar at once
                .waitUntil("the stone is in the hotbar", 100, () -> hotbarHas(mc, net.minecraft.world.item.Items.STONE))
                .run("hold the stone", () -> selectHotbar(mc, net.minecraft.world.item.Items.STONE))
                .run("look at the floor in front", () -> lookAt(mc, new Vec3(natural.x() + 0.5, natural.floorY() - 0.5, natural.z() + 7.5)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the stone stands on the floor", 20, () -> mc.level.getBlockState(
                        new net.minecraft.core.BlockPos(natural.x(), natural.floorY(), natural.z() + 7)).is(net.minecraft.world.level.block.Blocks.STONE))
                .waitTicks(38)
                .check("still there after 38 ticks", () -> ServerQuery.ask(p -> p.level().getBlockState(
                        new net.minecraft.core.BlockPos(natural.x(), natural.floorY(), natural.z() + 7)).is(net.minecraft.world.level.block.Blocks.STONE)))
                .waitUntil("it crumbles at 40", 10, () -> ServerQuery.ask(p -> p.level().getBlockState(
                        new net.minecraft.core.BlockPos(natural.x(), natural.floorY(), natural.z() + 7)).isAir()))
                .run("note it", () -> summary.add("a block placed in the arena mid-fight crumbled after 40 ticks"))
                .waitUntil("the intro is over", 220, () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(PrismColossus.class,
                        natural.bounds()).stream().findFirst().map(c -> c.state() == PrismColossus.State.FIGHT).orElse(false)))
                .command("cosmicbreach debug colossus hold 1000000")
                .run("health before the arrows", () -> measured[0] = ServerQuery.ask(p -> (double) naturalColossus(p).getHealth()))
                .run("an arrow from 30 blocks", () -> ServerQuery.ask(p -> shoot(p, 30.0)))
                .waitTicks(30)
                .run("burned up: no damage, no arrow left", () -> {
                    String why = ServerQuery.ask(p -> {
                        float now = naturalColossus(p).getHealth();
                        var arrows = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                                natural.bounds().inflate(40));
                        if (now == (float) measured[0] && arrows.isEmpty()) {
                            return "";
                        }
                        List<String> at = new ArrayList<>();
                        for (var a : arrows) {
                            at.add(String.format(Locale.ROOT, "%.1f %.1f %.1f%s", a.getX(), a.getY(), a.getZ(), a.isRemoved() ? " removed" : ""));
                        }
                        return String.format(Locale.ROOT, "health %.2f then %.2f; arrows left %s; colossus at %s", measured[0], now, at,
                                naturalColossus(p).position());
                    });
                    if (!why.isEmpty()) {
                        throw new Steps.Failure("check failed: burned up: no damage, no arrow left (" + why + ")");
                    }
                })
                .run("an arrow from 12 blocks", () -> ServerQuery.ask(p -> shoot(p, 12.0)))
                .waitTicks(30)
                .check("it hurts", () -> ServerQuery.ask(p -> naturalColossus(p).getHealth() < (float) measured[0]))
                .log("arrows", () -> String.format(Locale.ROOT, "an arrow from 30 blocks burned up; one from 12 dealt %.1f",
                        measured[0] - ServerQuery.ask(p -> (double) naturalColossus(p).getHealth())))
                .run("note it", () -> summary.add("an arrow from 30 blocks burned up, one from 12 hit"))
                .command("cosmicbreach debug colossus reset");
    }

    private PrismColossus naturalColossus(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(PrismColossus.class, natural.bounds()).stream().findFirst()
                .orElseThrow(() -> new Steps.Failure("no Colossus at the generated lair"));
    }

    /** An arrow fired at the Colossus's chest from {@code distance} blocks south, by the player (its owner). */
    private boolean shoot(ServerPlayer p, double distance) {
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(p.serverLevel(), p,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        // from high enough to clear the crown's battlements (merlons stand 4 above the floor at radius 19) and the
        // Refraction crystals, dropping onto the Colossus's body; a level shot from 30 blocks sticks in a merlon
        Vec3 target = natural.centre().add(0, 4.0, 0);
        Vec3 from = natural.centre().add(0, distance > 20 ? 7.5 : 5.0, distance);
        arrow.setPos(from.x, from.y, from.z);
        arrow.getPersistentData().remove("cosmicbreach_fired_from");
        Vec3 dir = target.subtract(from).normalize();
        arrow.shoot(dir.x, dir.y, dir.z, 3.0f, 0f);
        p.serverLevel().addFreshEntity(arrow);
        // fired from where it starts (the owner stands elsewhere in this test)
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putDouble("x", from.x);
        tag.putDouble("y", from.y);
        tag.putDouble("z", from.z);
        arrow.getPersistentData().put("cosmicbreach_fired_from", tag);
        return true;
    }

    // ------------------------------------------------------------------ Refraction (1.2.1): turned back by arrows, and the ring at rest

    /** Wakes the Colossus with the player standing on the crown in survival, then holds its own attacks. */
    private void wakeAndHold(Steps steps, Minecraft mc) {
        steps.run("on the crown, 10 south of the centre", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 10.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("the view is drawn", 900, settled(mc, 800))
                .command("cosmicbreach debug colossus awaken")
                .waitUntil("the intro is over", 320, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .command("cosmicbreach debug colossus hold 1000000")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 4 true")
                .waitTicks(20);
    }

    private static final String HINT = "Strike the lit crystal";

    /** The action bar's text now (the Gui keeps it in a private field), or "". */
    private static String actionBar(Minecraft mc) {
        try {
            java.lang.reflect.Field text = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
            java.lang.reflect.Field time = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageTime");
            text.setAccessible(true);
            time.setAccessible(true);
            Object c = text.get(mc.gui);
            return c instanceof net.minecraft.network.chat.Component comp && time.getInt(mc.gui) > 0 ? comp.getString() : "";
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot read the action bar: " + e);
        }
    }

    private static void clearActionBar(Minecraft mc) {
        try {
            java.lang.reflect.Field time = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageTime");
            time.setAccessible(true);
            time.setInt(mc.gui, 0);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot clear the action bar: " + e);
        }
    }

    /** True if every leg after the eye's stays clear of the body (a leg into the body may only end on it, and only last). */
    private static boolean legsClear(List<Vec3> pts, boolean turnedBack) {
        CrownArena a = arena();
        int legs = pts.size() - 1;
        for (int i = 1; i < legs; i++) {
            Vec3 p = pts.get(i);
            Vec3 q = pts.get(i + 1);
            boolean last = i == legs - 1;
            if (last && turnedBack) {
                if (Math.abs(a.distance(q.x, q.z) - CrownArena.BODY_RADIUS) > 0.05) {
                    return false; // a turned-back leg ends on the body's skin
                }
                continue;
            }
            if (com.cosmicbreach.guardian.colossus.Refraction.segmentNearCentre(p.x - a.x(), p.z - a.z(), q.x - a.x(), q.z - a.z(),
                    CrownArena.BODY_RADIUS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code colossus-refraction}: the first Refraction shows the hint; a real bow's arrows turn the gold-lit crystal until its beam
     * is turned back; the beam deals exactly 50 when its light reaches the body and Breaks it from an empty gauge. A second
     * Refraction shows no hint.
     */
    private void refraction(Steps steps, Minecraft mc) {
        equip(steps, mc);
        steps.command("give @s minecraft:bow")
                .command("give @s minecraft:arrow 64")
                .waitUntil("a bow in the hotbar", 40, () -> hotbarHas(mc, net.minecraft.world.item.Items.BOW));
        wakeAndHold(steps, mc);
        steps.run("forget the action bar", () -> clearActionBar(mc))
                .check("the gauge is empty (no parry, no hits)", () -> ask(mc, c -> c.gaugeFraction() < 0.01))
                .command("cosmicbreach debug colossus attack refraction")
                .waitUntil("a crown crystal is lit", 20, () -> ask(mc, c -> c.litCrystals().length == 1))
                .waitUntil("the hint is on the action bar", 20, () -> actionBar(mc).contains(HINT))
                .log("hint", () -> "action bar: " + actionBar(mc))
                .log("refraction", () -> ServerQuery.ask(p -> {
                    PrismColossus c = colossusOf(p);
                    int k = c.litCrystals()[0];
                    marks[0] = k;
                    marks[1] = crystalTarget(p, k);
                    marks[2] = com.cosmicbreach.guardian.colossus.Refraction.stepsToCore(k, (int) marks[1]);
                    StringBuilder ring = new StringBuilder();
                    for (int i = 0; i < 6; i++) {
                        ring.append(i).append("->").append(crystalTarget(p, i)).append(' ');
                    }
                    return String.format(Locale.ROOT, "crystal %d lit, aimed at %d: the core is %d turn(s) away; path %s; ring %s", k, marks[1],
                            marks[2], java.util.Arrays.toString(c.refractionPaths().get(0)), ring.toString().trim());
                }))
                .check("the core is 1 to 3 turns away", () -> marks[2] >= 1 && marks[2] <= 3)
                .check("every other crystal rests two round", () -> ServerQuery.ask(p -> {
                    for (int i = 0; i < 6; i++) {
                        if (i != marks[0] && crystalTarget(p, i) != com.cosmicbreach.guardian.colossus.Refraction.resting(i)) {
                            return false;
                        }
                    }
                    return true;
                }))
                .run("stand 9 out, a quarter turn off the lit crystal, bow in hand", () -> {
                    CrownArena a = arena();
                    Vec3 k = a.crystalPoint((int) marks[0]);
                    double ang = Math.atan2(k.z - a.z(), k.x - a.x()) + Math.toRadians(25);
                    Vec3 stand = new Vec3(a.x() + 9 * Math.cos(ang), a.floorY(), a.z() + 9 * Math.sin(ang));
                    tp(mc, stand.x, stand.y, stand.z, k.x, a.floorY() + 2.0, k.z);
                    selectHotbar(mc, net.minecraft.world.item.Items.BOW);
                })
                .waitTicks(3)
                .run("look at the lit crystal", () -> {
                    Vec3 k = arena().crystalPoint((int) marks[0]);
                    lookAt(mc, new Vec3(k.x, arena().floorY() + 2.1, k.z));
                })
                .run("camera: the gold crystal and the red lines", () -> place(mc, overShoulder(mc)))
                .waitTicks(1)
                .screenshot("refraction_gold_marker")
                .run("back to the player's eyes", this::dropCamera)
                .run("turns so far", () -> marks[4] = ServerQuery.ask(p -> (long) colossusOf(p).crystalTurns()));
        shootUntil(steps, mc, "the beam's path ends at the core (shooting)", ColossusMoves.REFRACTION_CHARGE,
                () -> ask(mc, c -> !c.refractionPaths().isEmpty() && com.cosmicbreach.guardian.colossus.Refraction.endsAtCore(c.refractionPaths().get(0))));
        steps.log("turns", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "arrows turned the crystal %d time(s) (needed %d), %d ticks into the charge",
                        colossusOf(p).crystalTurns() - marks[4], marks[2], p.level().getGameTime() - colossusOf(p).actionStart())))
                .check("the arrows turned it exactly as often as it needed", () -> ServerQuery.ask(p -> colossusOf(p).crystalTurns() - marks[4] == marks[2]))
                .check("the red lines end on the body (the client's copy)", () -> {
                    com.cosmicbreach.client.guardian.RefractionView.Beams b = com.cosmicbreach.client.guardian.RefractionView.of(colossus(mc).getId());
                    return b != null && b.core().get(0) && legsClear(b.paths().get(0), true);
                })
                .run("camera: the lines turned back", () -> place(mc, overShoulder(mc)))
                .waitTicks(1)
                .screenshot("refraction_turned_lines")
                .run("back to the player's eyes", this::dropCamera)
                .run("health before the beam", () -> {
                    measured[1] = health(mc);
                    marks[3] = ServerQuery.ask(p -> (long) colossusOf(p).coreHits());
                })
                .waitUntil("the beam fires and its light reaches the body", ColossusMoves.REFRACTION_CHARGE + 20, () -> ask(mc, c -> c.coreHits() > marks[3]))
                .run("the beam's hit", () -> measured[2] = measured[1] - health(mc))
                .log("core hit", () -> String.format(Locale.ROOT, "the beam turned back into the body dealt %.2f; broken now: %s", measured[2],
                        ask(mc, PrismColossus::isBroken)))
                .check("exactly 50", () -> Math.abs(measured[2] - ColossusMoves.CORE_HIT_DAMAGE) < 0.01)
                .check("not Broken yet: the beam is seen burning in first", () -> !ask(mc, PrismColossus::isBroken))
                .run("camera: the impact", () -> place(mc, side(13, 4.0)))
                .waitTicks(3)
                .screenshot("refraction_turned_impact")
                .waitUntil("it Breaks with an empty gauge", ColossusMoves.CORE_HIT_BREAK_DELAY + 5, () -> ask(mc, PrismColossus::isBroken))
                .waitTicks(3)
                .screenshot("refraction_turned_break")
                .run("back to the player's eyes", this::dropCamera)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Refraction turned back by %d arrow(s): %.1f damage, Break without a parry",
                        marks[2], measured[2])))
                .waitUntil("the Break is over", 140, () -> ask(mc, c -> !c.isBroken()))
                .waitTicks(30)
                .run("forget the action bar", () -> clearActionBar(mc))
                .command("cosmicbreach debug colossus attack refraction")
                .waitUntil("a second Refraction", 20, () -> ask(mc, c -> c.litCrystals().length == 1))
                .waitTicks(15)
                .check("no hint the second time", () -> !actionBar(mc).contains(HINT))
                .waitUntil("it is over", ColossusMoves.refractionLength() + 20, () -> ask(mc, c -> c.action() != PrismColossus.Action.REFRACTION))
                .run("note the hint", () -> summary.add("the hint showed on the first Refraction only"));
    }

    /** Draws and looses the bow (12 ticks drawn, a shot every 20, checked 8 ticks after each lands) until {@code done}, at most {@code timeout} ticks. */
    private void shootUntil(Steps steps, Minecraft mc, String what, int timeout, BooleanSupplier done) {
        int[] tick = {0};
        net.minecraft.client.KeyMapping key = mc.options.keyUse;
        steps.waitUntil(what, timeout, () -> {
            int t = tick[0]++;
            if (t % 20 == 0 && done.getAsBoolean()) {
                net.minecraft.client.KeyMapping.set(key.getKey(), false);
                return true;
            }
            if (t % 20 == 0) {
                Vec3 k = arena().crystalPoint((int) marks[0]);
                lookAt(mc, new Vec3(k.x, arena().floorY() + 2.1, k.z));
                net.minecraft.client.KeyMapping.set(key.getKey(), true);
                net.minecraft.client.KeyMapping.click(key.getKey());
            } else if (t % 20 == 12) {
                net.minecraft.client.KeyMapping.set(key.getKey(), false);
            }
            return false;
        });
    }

    /**
     * {@code colossus-rest}: the ring at rest in the world, then eight forced Refractions (players round the ring in phase 1, then
     * phase 2's pairs): no aimed path reaches the body, every lit crystal leaves the core 1 to 3 turns away, phase 2 never lights
     * opposite crystals, and after each beam every crystal is back at rest.
     */
    private void rest(Steps steps, Minecraft mc) {
        equip(steps, mc);
        wakeAndHold(steps, mc);
        steps.log("ring", () -> ServerQuery.ask(p -> {
                    StringBuilder ring = new StringBuilder();
                    for (int i = 0; i < 6; i++) {
                        ring.append(i).append("->").append(crystalTarget(p, i)).append(' ');
                    }
                    return "the ring at rest: " + ring.toString().trim();
                }))
                .check("every crystal rests two round", () -> ServerQuery.ask(p -> {
                    for (int i = 0; i < 6; i++) {
                        if (crystalTarget(p, i) != com.cosmicbreach.guardian.colossus.Refraction.resting(i)) {
                            return false;
                        }
                    }
                    return true;
                }))
                .check("no resting path reaches the body, in the world's geometry", () -> ServerQuery.ask(p -> {
                    CrownArena a = arena();
                    int[] t = new int[6];
                    for (int i = 0; i < 6; i++) {
                        t[i] = crystalTarget(p, i);
                    }
                    for (int i = 0; i < 6; i++) {
                        int[] path = com.cosmicbreach.guardian.colossus.Refraction.path(i, t);
                        if (com.cosmicbreach.guardian.colossus.Refraction.endsAtCore(path)
                                || !legsClear(a.beamPoints(path, a.eye(0f), a.core(0f, false)), false)) {
                            return false;
                        }
                    }
                    return true;
                }));
        for (int n = 0; n < 8; n++) {
            int round = n;
            if (n == 6) {
                steps.command("cosmicbreach debug colossus phase2")
                        .waitUntil("phase 2", 120, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT && c.phase() == 2))
                        .command("cosmicbreach debug colossus hold 1000000")
                        .waitTicks(10);
            }
            steps.run("stand round the ring (" + n + ")", () -> {
                        CrownArena a = arena();
                        double ang = Math.toRadians(30 + 60 * round + 17 * (round / 6));
                        tp(mc, a.x() + 10 * Math.cos(ang), a.floorY(), a.z() + 10 * Math.sin(ang), a.x(), a.floorY() + 3, a.z());
                    })
                    .waitTicks(5)
                    .command("cosmicbreach debug colossus attack refraction")
                    .waitUntil("lit", 20, () -> ask(mc, c -> c.litCrystals().length == (round >= 6 ? 2 : 1)))
                    .log("refraction " + n, () -> ServerQuery.ask(p -> {
                        PrismColossus c = colossusOf(p);
                        List<String> out = new ArrayList<>();
                        for (int[] path : c.refractionPaths()) {
                            out.add(java.util.Arrays.toString(path));
                        }
                        return "lit " + java.util.Arrays.toString(c.litCrystals()) + ", paths " + out;
                    }))
                    .check("no aimed path reaches the body; the core 1 to 3 turns away; never opposite", () -> ServerQuery.ask(p -> {
                        PrismColossus c = colossusOf(p);
                        int[] lit = c.litCrystals();
                        if (lit.length == 2 && lit[1] == com.cosmicbreach.guardian.colossus.Refraction.opposite(lit[0])) {
                            return false;
                        }
                        for (int k : lit) {
                            int s = com.cosmicbreach.guardian.colossus.Refraction.stepsToCore(k, crystalTarget(p, k));
                            if (s < 1 || s > 3) {
                                return false;
                            }
                        }
                        for (int[] path : c.refractionPaths()) {
                            if (com.cosmicbreach.guardian.colossus.Refraction.endsAtCore(path)) {
                                return false;
                            }
                        }
                        return true;
                    }))
                    .waitUntil("the Colossus faces its crystals and the client has the lines", 40, () -> {
                        com.cosmicbreach.client.guardian.RefractionView.Beams b = com.cosmicbreach.client.guardian.RefractionView.of(colossus(mc).getId());
                        return b != null && b.paths().size() == (round >= 6 ? 2 : 1);
                    })
                    .check("every drawn leg stays clear of the body", () -> {
                        com.cosmicbreach.client.guardian.RefractionView.Beams b = com.cosmicbreach.client.guardian.RefractionView.of(colossus(mc).getId());
                        for (List<Vec3> pts : b.paths()) {
                            if (!legsClear(pts, false)) {
                                return false;
                            }
                        }
                        return true;
                    });
            if (n == 0 || n == 6) {
                String tag = n == 0 ? "rest_lines_above" : "rest_two_lines_above";
                steps.waitTicks(30)
                        .run("camera: from above", () -> place(mc, above(16)))
                        .waitTicks(1)
                        .screenshot(tag)
                        .waitUntil("it fires", ColossusMoves.REFRACTION_CHARGE + 10, () -> actionTick(mc) >= ColossusMoves.REFRACTION_CHARGE + 10)
                        .screenshot(n == 0 ? "rest_beam_above" : "rest_two_beams_above")
                        .run("back to the player's eyes", this::dropCamera);
            }
            steps.waitUntil("the Refraction ends", ColossusMoves.refractionLength() + 20, () -> ask(mc, c -> c.action() != PrismColossus.Action.REFRACTION))
                    .check("the ring is back at rest", () -> ServerQuery.ask(p -> {
                        for (int i = 0; i < 6; i++) {
                            if (crystalTarget(p, i) != com.cosmicbreach.guardian.colossus.Refraction.resting(i)) {
                                return false;
                            }
                        }
                        return true;
                    }))
                    .waitTicks(10);
        }
        steps.run("note it", () -> summary.add("the ring rests clear of the body; 8 Refractions (2 in phase 2) never aimed into it"));
    }

    // ------------------------------------------------------------------ looks

    private void looks(Steps steps, Minecraft mc) {
        steps.command("gamemode creative")
                .run("fly, the HUD hidden", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                    mc.options.hideGui = true;
                })
                .run("stand on the crown", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 12.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitTicks(40)
                .waitUntil("the view is drawn", 900, settled(mc, 800))
                .run("camera: out in the sky, the whole spire", () -> {
                    CrownSpireLayout l = layout();
                    place(mc, new Vec3[] {new Vec3(l.x() + 62, l.floorY() - 18, l.z() + 78), new Vec3(l.x(), l.floorY() - 40, l.z())});
                })
                .waitTicks(40)
                .screenshot("spire_outside")
                .run("camera: above the crown", () -> {
                    CrownSpireLayout l = layout();
                    place(mc, new Vec3[] {new Vec3(l.x() + 22, l.floorY() + 16, l.z() + 22), new Vec3(l.x(), l.floorY(), l.z())});
                })
                .waitTicks(10)
                .screenshot("crown_above")
                .run("back to the player's eyes", this::dropCamera)
                .run("inside, the lift", () -> {
                    CrownSpireLayout l = layout();
                    int[] d = l.riseDoorOutside();
                    int[] r = l.riseColumn();
                    tp(mc, d[0] + 0.5, d[1] + 1.2, d[2] + 0.5, r[0] + 0.5, l.baseY() + 6, r[1] + 0.5);
                })
                .waitUntil("the view is drawn", 600, settled(mc, 500))
                .screenshot("lift_door")
                .run("camera: the gate, from out on its walkway", () -> {
                    CrownSpireLayout l = layout();
                    double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
                    double far = CrownSpireLayout.GATE_ALONG + 14.0;
                    double near = CrownSpireLayout.GATE_ALONG;
                    place(mc, new Vec3[] {new Vec3(l.x() + far * Math.cos(a) + 4.0, l.baseY() + 3.5, l.z() + far * Math.sin(a) - 3.0),
                            new Vec3(l.x() + near * Math.cos(a), l.baseY() + 4.5, l.z() + near * Math.sin(a))});
                })
                .waitTicks(5)
                .screenshot("lift_gate")
                .run("back to the player's eyes", this::dropCamera);
        // the statue, dormant, from three sides
        views(steps, mc, "dormant");
        steps.command("cosmicbreach debug colossus awaken")
                .waitTicks(100)
                .run("camera: the intro from the front", () -> place(mc, front(14, 4.5)))
                .waitTicks(2)
                .screenshot("intro")
                .waitUntil("the intro is over", 200, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT))
                .waitTicks(40);
        views(steps, mc, "awake");
        steps.run("camera: the core and the eye, close", () -> place(mc, front(7.5, 4.8)))
                .waitTicks(3)
                .screenshot("core_close")
                .run("back to the player's eyes", this::dropCamera);
        // telegraphs, one at a time: the player in survival on the floor (Resistance keeps it alive)
        steps.run("back to the player's eyes", this::dropCamera)
                .command("gamemode survival")
                .command("effect give @s minecraft:resistance 600 4 true")
                .command("effect give @s minecraft:regeneration 600 4 true")
                .command("cosmicbreach debug colossus hold 100000")
                .run("stand 9 blocks south of the Colossus", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 9.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitTicks(50);
        telegraph(steps, mc, "slam", 12, "slam_ring", 13, "slam_glint", () -> beside(mc));
        steps.waitTicks(60);
        telegraph(steps, mc, "sweep", 20, "sweep_band", 12, "sweep_swing", () -> high(mc));
        steps.waitTicks(40);
        telegraph(steps, mc, "sweep", 20, "sweep_band_eye", 0, null, () -> eyeLevel(mc));
        steps.waitTicks(40);
        telegraph(steps, mc, "refraction", ColossusMoves.REFRACTION_CHARGE - 15, "refraction_lines", 25, "refraction_beam", () -> overShoulder(mc));
        steps.waitTicks(60)
                .command("cosmicbreach debug colossus break")
                .waitTicks(20)
                .run("camera: the Break from the side", () -> place(mc, side(12, 3.0)))
                .waitTicks(2)
                .screenshot("break")
                .run("back to the player's eyes", this::dropCamera)
                .waitUntil("the Break is over", 140, () -> ask(mc, c -> !c.isBroken()))
                .waitTicks(30)
                .command("cosmicbreach debug colossus phase2")
                .waitTicks(24)
                .run("camera: the Fracture", () -> place(mc, front(15, 4.5)))
                .waitTicks(2)
                .screenshot("fracture")
                .run("back to the player's eyes", this::dropCamera)
                .waitUntil("phase 2", 120, () -> ask(mc, c -> c.state() == PrismColossus.State.FIGHT && c.phase() == 2))
                .waitTicks(30);
        telegraph(steps, mc, "double", 18, "double_slam", 16, "double_slam_second", () -> beside(mc));
        steps.waitTicks(80)
                .run("hug it", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 3.2, a.x(), a.floorY() + 3, a.z());
                })
                .waitTicks(10);
        telegraph(steps, mc, "burst", 10, "burst_glow", 0, null, () -> side(12, 3.0));
        steps.waitTicks(40)
                .run("step back", () -> {
                    CrownArena a = arena();
                    tp(mc, a.x() + 0.5, a.floorY(), a.z() + 9.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitTicks(20)
                .run("camera: the Shatter, from above the floor", () -> place(mc, front(16, 8.0)))
                .waitTicks(2)
                .command("cosmicbreach debug colossus shatter")
                .waitTicks(4)
                .screenshot("shatter")
                .waitTicks(5)
                .screenshot("shatter_burst")
                .waitTicks(40)
                .run("camera: the shards", () -> place(mc, above(10)))
                .waitTicks(2)
                .screenshot("shards")
                .log("the shards", () -> ServerQuery.ask(p -> {
                    StringBuilder b = new StringBuilder();
                    for (com.cosmicbreach.guardian.colossus.PrismShard s : p.serverLevel().getEntitiesOfClass(
                            com.cosmicbreach.guardian.colossus.PrismShard.class, arena().bounds().inflate(8))) {
                        b.append(String.format(Locale.ROOT, "[colour %d, %s, health %.1f, hurt %d] ", s.color(), s.phase(), s.getHealth(), s.hurtTime));
                    }
                    return b.toString();
                }))
                .run("camera: a shard up close", () -> {
                    net.minecraft.world.entity.Entity s = mc.level.getEntitiesOfClass(com.cosmicbreach.guardian.colossus.PrismShard.class,
                            arena().bounds().inflate(8)).stream().findFirst().orElse(null);
                    if (s != null) {
                        Vec3 at = s.position();
                        Vec3 side = CrownArena.forward(s.getYRot() + 70f);
                        place(mc, new Vec3[] {at.add(side.scale(3.6)).add(0, 1.5, 0), at.add(0, 0.7, 0)});
                    }
                })
                .waitTicks(2)
                .screenshot("shard_close")
                .run("back to the player's eyes", this::dropCamera)
                .command("cosmicbreach debug colossus reset");
    }

    /** Forces {@code attack}, takes a screenshot {@code first} ticks in and another {@code second} ticks after. */
    private void telegraph(Steps steps, Minecraft mc, String attack, int first, String shot1, int second, @Nullable String shot2,
                           java.util.function.Supplier<Vec3[]> view) {
        steps.command("cosmicbreach debug colossus attack " + attack)
                .waitTicks(first)
                .log("fists", () -> {
                    PrismColossus client = mc.level.getEntitiesOfClass(PrismColossus.class, arena().bounds()).stream().findFirst().orElse(null);
                    String c = client == null ? "none" : String.format(Locale.ROOT, "client t=%d R %s start %d param %s pos %s | L %s start %d pos %s",
                            mc.level.getGameTime(), client.fistMode(true), client.fistStart(true), client.fistParam(true),
                            client.fistPosition(true, mc.level.getGameTime()), client.fistMode(false), client.fistStart(false),
                            client.fistPosition(false, mc.level.getGameTime()));
                    String s = ServerQuery.ask(p -> {
                        PrismColossus sc = colossusOf(p);
                        long now = p.level().getGameTime();
                        return String.format(Locale.ROOT, "server t=%d R %s start %d pos %s | L %s pos %s", now, sc.fistMode(true),
                                sc.fistStart(true), sc.fistPosition(true, now), sc.fistMode(false), sc.fistPosition(false, now));
                    });
                    return c + "\n    " + s;
                })
                .run("camera: " + attack, () -> place(mc, view.get()))
                .waitTicks(1)
                .screenshot(shot1);
        if (shot2 != null) {
            steps.waitTicks(Math.max(0, second - 3)).screenshot(shot2);
        }
        steps.run("back to the player's eyes", this::dropCamera);
    }

    private void views(Steps steps, Minecraft mc, String state) {
        steps.run("camera: " + state + " front", () -> place(mc, front(13, 4.0)))
                .waitTicks(3)
                .screenshot(state + "_front")
                .run("camera: " + state + " side", () -> place(mc, side(13, 4.0)))
                .waitTicks(3)
                .screenshot(state + "_side")
                .run("camera: " + state + " back", () -> place(mc, back(13, 4.0)))
                .waitTicks(3)
                .screenshot(state + "_back")
                .run("back to the player's eyes", this::dropCamera);
    }

    // ------------------------------------------------------------------ cameras

    /** {eye, target}: in front of the Colossus (it faces its yaw), {@code d} out, {@code h} up. */
    private Vec3[] front(double d, double h) {
        return around(0, d, h);
    }

    private Vec3[] side(double d, double h) {
        return around(90, d, h);
    }

    private Vec3[] back(double d, double h) {
        return around(180, d, h);
    }

    private Vec3[] above(double h) {
        CrownArena a = arena();
        Vec3 c = a.centre();
        return new Vec3[] {c.add(14, h, 14), c.add(0, 1, 0)};
    }

    private Vec3[] around(double degrees, double d, double h) {
        CrownArena a = arena();
        float yaw = ServerQuery.ask(p -> colossusOf(p).getYRot());
        Vec3 dir = CrownArena.forward((float) (yaw + degrees));
        Vec3 c = a.centre();
        return new Vec3[] {c.add(dir.scale(d)).add(0, h, 0), c.add(0, 3.6, 0)};
    }

    /** Square to the line from the Colossus to the player, far enough to see both and what flies between. */
    private Vec3[] beside(Minecraft mc) {
        CrownArena a = arena();
        Vec3 p = mc.player.position();
        Vec3 c = new Vec3(a.x(), a.floorY(), a.z());
        Vec3 dir = new Vec3(p.x - c.x, 0, p.z - c.z).normalize();
        Vec3 perp = new Vec3(-dir.z, 0, dir.x);
        Vec3 mid = c.add(p.subtract(c).scale(0.55));
        return new Vec3[] {mid.add(perp.scale(17)).add(0, 4.5, 0), mid.add(0, 3.0, 0)};
    }

    /** High over the Colossus's shoulder, looking down at the floor in front of it. */
    private Vec3[] high(Minecraft mc) {
        CrownArena a = arena();
        Vec3 p = mc.player.position();
        Vec3 c = new Vec3(a.x(), a.floorY(), a.z());
        Vec3 dir = new Vec3(p.x - c.x, 0, p.z - c.z).normalize();
        Vec3 perp = new Vec3(-dir.z, 0, dir.x);
        return new Vec3[] {c.add(dir.scale(-3)).add(perp.scale(10)).add(0, 14, 0), c.add(dir.scale(6))};
    }

    /** At a player's eye height, 12 blocks out on the player's side, looking at the Colossus's feet. */
    private Vec3[] eyeLevel(Minecraft mc) {
        CrownArena a = arena();
        Vec3 p = mc.player.position();
        Vec3 c = new Vec3(a.x(), a.floorY(), a.z());
        Vec3 dir = new Vec3(p.x - c.x, 0, p.z - c.z).normalize();
        return new Vec3[] {c.add(dir.scale(12)).add(0, 1.62, 0), c.add(0, 1.2, 0)};
    }

    /** Behind and beside the player, looking past them at the Colossus. */
    private Vec3[] overShoulder(Minecraft mc) {
        CrownArena a = arena();
        Vec3 p = mc.player.position();
        Vec3 away = new Vec3(p.x - a.x(), 0, p.z - a.z()).normalize();
        Vec3 sideways = new Vec3(-away.z, 0, away.x);
        return new Vec3[] {p.add(away.scale(9)).add(sideways.scale(7)).add(0, 7, 0), new Vec3(a.x(), a.floorY() + 1.5, a.z()).add(away.scale(4))};
    }

    private void place(Minecraft mc, Vec3[] eyeTarget) {
        if (camera == null) {
            camera = DevCamera.create(mc.level);
        }
        camera.place(eyeTarget[0], eyeTarget[1]);
        camera.use();
        mc.options.hideGui = true;
    }

    private void dropCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static @Nullable CrownSpireLayout layout() {
        return GuardianCommands.lastBuilt();
    }

    static CrownArena arena() {
        CrownSpireLayout l = layout();
        if (l == null) {
            throw new Steps.Failure("no lair was built");
        }
        return l.arena();
    }

    static PrismColossus colossusOf(ServerPlayer p) {
        CrownArena a = arena();
        return p.serverLevel().getEntitiesOfClass(PrismColossus.class, a.bounds()).stream().findFirst()
                .orElseThrow(() -> new Steps.Failure("no Prism Colossus in the arena"));
    }

    private static @Nullable PrismColossus colossus(Minecraft mc) {
        return ServerQuery.ask(p -> layout() == null ? null
                : p.serverLevel().getEntitiesOfClass(PrismColossus.class, layout().arena().bounds()).stream().findFirst().orElse(null));
    }

    private static boolean ask(Minecraft mc, Function<PrismColossus, Boolean> query) {
        return ServerQuery.ask(p -> query.apply(colossusOf(p)));
    }

    static @Nullable PrismColossus colossusOrNull(ServerPlayer p) {
        CrownSpireLayout l = layout();
        return l == null ? null : p.serverLevel().getEntitiesOfClass(PrismColossus.class, l.arena().bounds()).stream().findFirst().orElse(null);
    }

    private static @Nullable com.cosmicbreach.guardian.GuardianAltarBlockEntity altarOf(ServerPlayer p) {
        int[] a = layout().altar();
        return p.level().getBlockEntity(new net.minecraft.core.BlockPos(a[0], a[1], a[2]))
                instanceof com.cosmicbreach.guardian.GuardianAltarBlockEntity altar ? altar : null;
    }

    private boolean fistsHome(PrismColossus c) {
        return c.fistMode(true) == PrismColossus.FistMode.ATTACHED && c.fistMode(false) == PrismColossus.FistMode.ATTACHED
                && c.action() == PrismColossus.Action.NONE;
    }

    /** The resting fist's middle (a stuck one after a parry). */
    private static Vec3 stuckFist(PrismColossus c) {
        boolean right = c.fistRest(true) >= 0;
        return c.fistPosition(right, c.level().getGameTime());
    }

    /** How long the resting fist has lain in the floor. */
    private static long stuckFor(PrismColossus c) {
        boolean right = c.fistRest(true) >= 0;
        if (c.fistRest(right) < 0) {
            return -1;
        }
        return c.level().getGameTime() - c.fistStart(right);
    }

    private static double health(Minecraft mc) {
        return ServerQuery.ask(p -> (double) colossusOf(p).getHealth());
    }

    private static double maxHealth(Minecraft mc) {
        return ServerQuery.ask(p -> colossusOf(p).maxHealth());
    }

    /** Ticks into the Colossus's current attack (server). */
    private static long actionTick(Minecraft mc) {
        return ServerQuery.ask(p -> {
            PrismColossus c = colossusOf(p);
            return c.level().getGameTime() - c.actionStart();
        });
    }

    /** Ticks into the slam of the fist that is slamming (server), or -1. */
    private static long slamTick(Minecraft mc) {
        return ServerQuery.ask(p -> {
            PrismColossus c = colossusOf(p);
            for (boolean right : new boolean[] {true, false}) {
                if (c.fistMode(right) == PrismColossus.FistMode.SLAM && c.fistGlints(right)) {
                    return c.level().getGameTime() - c.fistStart(right);
                }
            }
            return -1L;
        });
    }

    private static int crystalTarget(ServerPlayer p, int k) {
        return p.level().getBlockEntity(arena().crystalBase(k)) instanceof com.cosmicbreach.guardian.colossus.CrownCrystalBlockEntity c ? c.target() : -1;
    }

    private static boolean inLight(Minecraft mc, net.minecraft.world.level.block.Block light) {
        return mc.level.getBlockState(mc.player.blockPosition()).is(light) || mc.level.getBlockState(mc.player.blockPosition().above()).is(light);
    }

    static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = net.minecraft.util.Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        float pitch = net.minecraft.util.Mth.clamp((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90f, 90f);
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

    private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
        int n = 0;
        for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static boolean hotbarHas(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void selectHotbar(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                net.minecraft.client.KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
                return;
            }
        }
        throw new Steps.Failure(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + " is not in the hotbar");
    }

    static void tp(Minecraft mc, double x, double y, double z, double lookX, double lookY, double lookZ) {
        if (mc.player.isCreative()) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        ServerQuery.ask(p -> {
            Vec3 d = new Vec3(lookX - x, lookY - (y + p.getEyeHeight()), lookZ - z);
            float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            p.teleportTo(p.serverLevel(), x, y, z, java.util.Set.of(), yaw, pitch);
            p.setDeltaMovement(Vec3.ZERO);
            return true;
        });
    }

    private static final int SETTLE_TICKS = 20;

    private static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = mc.screen == null && mc.level != null && mc.player != null
                    && mc.level.getChunkSource().hasChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
            int rendered = mc.levelRenderer.countRenderedSections();
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= SETTLE_TICKS || (state[0] >= maxTicks && ready);
        };
    }

    @SuppressWarnings("unused")
    private static String id() {
        return CosmicBreach.MOD_ID;
    }
}
