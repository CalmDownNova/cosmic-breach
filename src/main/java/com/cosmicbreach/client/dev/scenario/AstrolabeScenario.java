package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.astrolabe.AstrolabeEffects;
import com.cosmicbreach.astrolabe.PocketStar;
import com.cosmicbreach.astrolabe.PocketStarRules;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.entity.stalker.HollowStalker;
import com.cosmicbreach.entity.stalker.StalkerLight;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.world.light.TempLights;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Choir Astrolabe (G7, GDD 4.2) through the real key path, every number measured on the server:
 * <ul>
 *   <li>looks: in the hand in first person (at rest and at full Resonance) and in third person;</li>
 *   <li>the chain loop (L1, L2, the Triad) on a dummy 8 blocks off, four loops: its damage per second at zero stats
 *       against the GDD's 11.2, every bolt landing;</li>
 *   <li>homing: a bolt aimed 7 degrees wide of a dummy bends onto it; one 22 degrees wide flies straight past;</li>
 *   <li>the Constellation: a charge swept across five dummies marks all five, the beam strikes each for 1.8;</li>
 *   <li>the Starfall from a jump (a 45 degree bolt, splash 2) and the Parallax out of a dash (a decoy, two bolts);</li>
 *   <li>at night, the Pocket Star: the ground round it lit to 15, an arrow swallowed, a Hollow Stalker leaving its
 *       light; the Supernova's motion value at two ages against 1 + 3 x the time left;</li>
 *   <li>the Singularity: a partner's Comet Maul Gravity Well (a stand-in player whose machine the scenario ticks)
 *       pulls a dummy twice as fast with the Pocket Star inside it, and the Supernova deals +50%.</li>
 * </ul>
 */
public final class AstrolabeScenario implements Scenario {
    static final String DUMMY = "cb_astro";
    private static final double BASE = 4.8;
    private static final double CRIT = 1.5;

    private static ResourceLocation move(String name) {
        return CosmicBreach.id("choir_astrolabe/" + name);
    }

    private record Hit(int entity, double amount, long time) {
    }

    private record Started(ResourceLocation id, long time) {
    }

    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<Started> started = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private final List<Vec3> pulled = new CopyOnWriteArrayList<>();
    private volatile @Nullable FakePlayer partner;
    private volatile boolean samplingPull;
    private volatile int pullDummy = -1;
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;
    private double baselinePull;
    private double pullWithStar;
    /** The last Supernova's motion value, read from the star as it goes (server thread). */
    private volatile double lastNova = -1;
    private final List<BlockPos> starLights = new CopyOnWriteArrayList<>();
    private double novaEarly;
    private double novaLate;
    private int starAgeEarly;
    private int starAgeLate;
    private final int[] tapTick = {0};

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    // ------------------------------------------------------------------ helpers

    private Vec3 at(double dx, double dz) {
        return new Vec3(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz);
    }

    private static Zombie dummy(ServerPlayer p, Vec3 at) {
        Zombie z = MaulScenario.spawnDummy(p.serverLevel(), at, DUMMY);
        z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        z.setHealth(1000f);
        return z;
    }

    private static List<Zombie> dummies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40.0), z -> z.getTags().contains(DUMMY));
    }

    private static void clearDummies() {
        ServerQuery.ask(p -> {
            dummies(p).forEach(z -> z.discard());
            return true;
        });
    }

    private void stand(Minecraft mc, double dx, double dz, Vec3 look) {
        Vec3 a = at(dx, dz);
        ColossusScenario.tp(mc, a.x, a.y, a.z, look.x, look.y, look.z);
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static void key(KeyMapping k, boolean down) {
        KeyMapping.set(k.getKey(), down);
    }

    private static void fillAndReady() {
        ServerQuery.ask(p -> {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            m.syncFromServer(m.maxResonance(), m.dashCharges(), 0);
            return true;
        });
    }

    private List<Hit> hitsSince(long time) {
        return hits.stream().filter(h -> h.time() >= time).toList();
    }

    private static long now() {
        return ServerQuery.ask(p -> p.level().getGameTime());
    }

    /** A hit's amount with a crit taken back out. */
    private static double plain(double amount, double expected) {
        return Math.abs(amount - expected * CRIT) < Math.abs(amount - expected) ? amount / CRIT : amount;
    }

    /** A chain bolt's hit without its crit: the nearest of a Star Bolt (1.0) or a Triad bolt (0.7), plain or crit. */
    private static double plainChain(double amount) {
        double[][] kinds = {{BASE, BASE}, {BASE * CRIT, BASE}, {BASE * 0.7, BASE * 0.7}, {BASE * 0.7 * CRIT, BASE * 0.7}};
        double best = amount;
        double bestD = Double.MAX_VALUE;
        for (double[] k : kinds) {
            double d = Math.abs(amount - k[0]);
            if (d < bestD) {
                bestD = d;
                best = k[1];
            }
        }
        return best;
    }

    private static boolean near(double amount, double expected) {
        return Math.abs(amount - expected) < 0.05 || Math.abs(amount - expected * CRIT) < 0.05;
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(DUMMY)) {
                hits.add(new Hit(event.getEntity().getId(), event.getNewDamage(), event.getEntity().level().getGameTime()));
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof PocketStar s && s.lastNovaMv() > 0) {
                lastNova = s.lastNovaMv();
            }
        });
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.MoveStarted m && Minecraft.getInstance().level != null) {
                started.add(new Started(m.move().id(), Minecraft.getInstance().level.getGameTime()));
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            FakePlayer f = partner;
            if (f != null) {
                PlayerCombat combat = PlayerCombat.of(f);
                long t = f.level().getGameTime();
                for (CombatEvent e : combat.tick()) {
                    ServerMoveEffects.dispatch(f, combat, e, t);
                    if (e instanceof CombatEvent.MoveEnded ended) {
                        combat.hits().forget(ended.move().serial());
                    }
                }
            }
            if (samplingPull && pullDummy >= 0) {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    if (p.level().getEntity(pullDummy) instanceof Zombie z) {
                        pulled.add(z.position());
                    }
                }
            }
        });
    }

    private long startedCount(String name) {
        ResourceLocation id = move(name);
        return started.stream().filter(s -> s.id().equals(id)).count();
    }

    // ------------------------------------------------------------------ the run

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("time set noon")
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("where we stand", () -> base = ServerQuery.ask(p -> p.blockPosition()))
                .command("cosmicbreach give choir_astrolabe")
                .waitUntil("the Astrolabe in hand with its data", 60, () -> mc.player.getMainHandItem().is(ModItems.CHOIR_ASTROLABE.get())
                        && machine(mc).weapon() != null && machine(mc).weapon().aerial().isPresent())
                .run("look south", () -> stand(mc, 0, 0, at(0, 20).add(0, 1.6, 0)))
                .waitTicks(20)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("astrolabe_fp_idle")
                .command("cosmicbreach debug resonance 100")
                .waitTicks(30)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("astrolabe_fp_full")
                .run("a camera to the player's right", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(-2.2, 1.5, 1.0), p.add(0, 1.2, 0.3));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("astrolabe_third")
                .run("front camera", () -> camera.place(mc.player.position().add(0.6, 1.6, 2.6), mc.player.position().add(0, 1.3, 0)))
                .waitTicks(3)
                .screenshot("astrolabe_third_front")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                });
        chain(steps, mc);
        homing(steps, mc);
        constellation(steps, mc);
        starfall(steps, mc);
        parallax(steps, mc);
        pocketStar(steps, mc);
        singularity(steps, mc);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the chain: DPS

    private void chain(Steps steps, Minecraft mc) {
        long[] from = {0};
        steps.run("a dummy 8 blocks south", () -> ServerQuery.ask(p -> dummy(p, at(0, 8))))
                .run("look at it", () -> stand(mc, 0, 0, at(0, 8).add(0, 1.1, 0)))
                .command("cosmicbreach debug resonance 0")
                .waitTicks(10)
                .run("clear the counts", () -> {
                    hits.clear();
                    started.clear();
                    tapTick[0] = 0;
                    from[0] = now();
                })
                .waitUntil("four loops of L1, L2, Triad tapped", 260, () -> {
                    int n = tapTick[0]++;
                    boolean down = n % 2 == 0 && startedCount("l3") < 4;
                    key(mc.options.keyAttack, down);
                    return startedCount("l3") >= 4;
                })
                .run("stop tapping", () -> key(mc.options.keyAttack, false))
                .waitTicks(40)
                .log("chain DPS", () -> {
                    List<Started> l1 = started.stream().filter(s -> s.id().equals(move("l1"))).toList();
                    List<Started> l3 = started.stream().filter(s -> s.id().equals(move("l3"))).toList();
                    long t0 = l1.get(0).time();
                    long t1 = l3.get(3).time() + 17;
                    double seconds = (t1 - t0) / 20.0;
                    double sum = 0;
                    double plainSum = 0;
                    int crits = 0;
                    List<Hit> got = hitsSince(from[0]);
                    for (Hit h : got) {
                        sum += h.amount();
                        double pl = plainChain(h.amount());
                        crits += pl < h.amount() - 0.01 ? 1 : 0;
                        plainSum += pl;
                    }
                    List<String> starts = new ArrayList<>();
                    started.forEach(st -> starts.add(st.id().getPath().replace("choir_astrolabe/", "") + "@" + (st.time() - t0)));
                    String s = String.format(Locale.ROOT, "chain: 4 loops in %d ticks (%.2f s), %d bolts landed (%d crits), %.2f damage "
                                    + "(%.2f without crits): %.2f DPS, %.2f without crits (GDD 11.2 at zero stats); moves %s",
                            t1 - t0, seconds, got.size(), crits, sum, plainSum, sum / seconds, plainSum / seconds, String.join(" ", starts));
                    summary.add(s);
                    return s;
                })
                .check("20 bolts landed and the loop is 35 ticks: 11.2 DPS without crits", () -> {
                    List<Hit> got = hitsSince(from[0]);
                    List<Started> l1 = started.stream().filter(s -> s.id().equals(move("l1"))).toList();
                    List<Started> l3 = started.stream().filter(s -> s.id().equals(move("l3"))).toList();
                    if (got.size() != 20 || l1.size() < 4 || l3.size() < 4) {
                        return false;
                    }
                    long ticks = l3.get(3).time() + 17 - l1.get(0).time();
                    double plainSum = 0;
                    for (Hit h : got) {
                        plainSum += plainChain(h.amount());
                    }
                    double dps = plainSum / (ticks / 20.0);
                    return ticks >= 139 && ticks <= 142 && dps > 11.1 && dps < 11.4;
                })
                .run("a Triad for the camera", () -> {
                    hits.clear();
                    started.clear();
                    tapTick[0] = 0;
                })
                .waitUntil("L1 and L2 tapped into the Triad", 60, () -> {
                    int n = tapTick[0]++;
                    key(mc.options.keyAttack, n % 3 == 0 && startedCount("l3") < 1);
                    CombatStateMachine m = machine(mc);
                    return m.current() != null && m.current().id().equals(move("l3")) && m.phase() == CombatStateMachine.Phase.ACTIVE;
                })
                .run("release", () -> key(mc.options.keyAttack, false))
                .waitTicks(1)
                .screenshot("astrolabe_triad")
                .waitTicks(40)
                .run("clear", AstrolabeScenario::clearDummies);
    }

    // ------------------------------------------------------------------ homing

    private void homing(Steps steps, Minecraft mc) {
        long[] from = {0};
        int[] ids = {0, 0};
        steps.run("two dummies 10 blocks off: 7 degrees right, 22 degrees left", () -> {
                    stand(mc, 0, 0, at(0, 20).add(0, 1.1, 0));
                    ServerQuery.ask(p -> {
                        Vec3 right = at(-10 * Math.sin(Math.toRadians(7)), 10 * Math.cos(Math.toRadians(7)));
                        Vec3 left = at(10 * Math.sin(Math.toRadians(22)), 10 * Math.cos(Math.toRadians(22)));
                        ids[0] = dummy(p, right).getId();
                        ids[1] = dummy(p, left).getId();
                        return true;
                    });
                })
                .run("aim straight between them at chest height", () -> stand(mc, 0, 0, at(0, 10).add(0, 1.1, 0)))
                .waitTicks(10)
                .run("mark", () -> from[0] = now())
                .press(mc.options.keyAttack)
                .waitTicks(30)
                .check("the bolt bent onto the dummy 7 degrees off, and missed the one 22 off", () -> {
                    List<Hit> got = hitsSince(from[0]);
                    return got.size() == 1 && got.get(0).entity() == ids[0];
                })
                .log("homing", () -> {
                    summary.add("homing: a bolt aimed 7 degrees wide of a dummy 10 blocks off hit it; the dummy 22 degrees off was untouched");
                    return "ok";
                })
                .run("clear", AstrolabeScenario::clearDummies)
                .waitTicks(20);
    }

    // ------------------------------------------------------------------ the Constellation

    private void constellation(Steps steps, Minecraft mc) {
        long[] from = {0};
        int[] marks = {0};
        float[] yaw = {0};
        float[] yaw0 = {0};
        int[] sweep = {0};
        List<String> sweepLog = new ArrayList<>();
        steps.run("five dummies in an arc 10 blocks off", () -> ServerQuery.ask(p -> {
                    for (int i = 0; i < 5; i++) {
                        double a = Math.toRadians(-40 + 20 * i);
                        dummy(p, at(-10 * Math.sin(a), 10 * Math.cos(a)));
                    }
                    return true;
                }))
                .run("aim far left of them", () -> {
                    double a = Math.toRadians(-85);
                    stand(mc, 0, 0, at(-10 * Math.sin(a), 10 * Math.cos(a)).add(0, 1.1, 0));
                })
                .waitTicks(10)
                .run("the yaw we start the sweep from", () -> {
                    yaw0[0] = mc.player.getYRot();
                    sweep[0] = 0;
                    sweepLog.clear();
                })
                .run("mark", () -> from[0] = now())
                .run("hold attack", () -> key(mc.options.keyAttack, true))
                .waitUntil("charging", 20, () -> machine(mc).phase() == CombatStateMachine.Phase.CHARGING)
                .waitUntil("sweep the aim across all five (and back if need be)", 140, () -> {
                    int n = sweep[0]++;
                    double phase = (n % 60) < 30 ? (n % 60) : 60 - (n % 60); // 0..30..0: over and back
                    yaw[0] = yaw0[0] + (float) (phase * 4.5);
                    mc.player.setYRot(yaw[0]);
                    mc.player.yRotO = yaw[0];
                    int m = ServerQuery.ask(p -> AstrolabeEffects.marksOf(p).size());
                    String ph = ServerQuery.ask(p -> PlayerCombat.of(p).machine().phase().name());
                    if (n % 5 == 0) {
                        sweepLog.add(String.format(Locale.ROOT, "%d:%.0f:%d:%s", n, yaw[0], m, ph));
                    }
                    return m >= 5 || n >= 120;
                })
                .log("the sweep", () -> String.join(" ", sweepLog))
                .check("five marked", () -> ServerQuery.ask(p -> AstrolabeEffects.marksOf(p).size()) >= 5)
                .run("count the marks", () -> marks[0] = ServerQuery.ask(p -> AstrolabeEffects.marksOf(p).size()))
                .waitTicks(1)
                .screenshot("astrolabe_constellation_marks")
                .run("release", () -> key(mc.options.keyAttack, false))
                .waitUntil("the beam", 20, () -> machine(mc).current() != null
                        && machine(mc).current().id().equals(move("constellation")) && machine(mc).phase() == CombatStateMachine.Phase.ACTIVE)
                .waitTicks(4)
                .screenshot("astrolabe_constellation_beam")
                .waitTicks(30)
                .check("five marked, and the beam struck each once for 1.8", () -> {
                    List<Hit> got = hitsSince(from[0]).stream().filter(h -> near(h.amount(), BASE * 1.8)).toList();
                    long distinct = got.stream().mapToInt(Hit::entity).distinct().count();
                    return marks[0] == 5 && got.size() == 5 && distinct == 5;
                })
                .log("constellation", () -> {
                    List<Hit> got = hitsSince(from[0]).stream().filter(h -> near(h.amount(), BASE * 1.8)).toList();
                    List<String> amounts = new ArrayList<>();
                    got.forEach(h -> amounts.add(String.format(Locale.ROOT, "%.2f", h.amount())));
                    String s = "constellation: " + marks[0] + " marked, the beam struck " + got.size() + " (" + String.join(", ", amounts)
                            + "; 1.8 x 4.8 = 8.64)";
                    summary.add(s);
                    return s;
                })
                .run("clear", AstrolabeScenario::clearDummies)
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ the Starfall and the Parallax

    private void starfall(Steps steps, Minecraft mc) {
        long[] from = {0};
        steps.run("a dummy 2.5 blocks south", () -> {
                    stand(mc, 0, 0, at(0, 20).add(0, 1.4, 0));
                    ServerQuery.ask(p -> dummy(p, at(0, 2.5)));
                })
                .waitTicks(10)
                .run("mark", () -> {
                    from[0] = now();
                    started.clear();
                })
                .press(mc.options.keyJump)
                .waitTicks(4)
                .press(mc.options.keyAttack)
                .waitTicks(2)
                .screenshot("astrolabe_starfall")
                .waitTicks(30)
                .check("the attack in the air was the Starfall, and its splash hit the dummy for 1.5", () ->
                        startedCount("starfall") == 1 && hitsSince(from[0]).stream().anyMatch(h -> near(h.amount(), BASE * 1.5)))
                .log("starfall", () -> {
                    summary.add("starfall: jumped and attacked, the 45 degree bolt's splash hit a dummy 2.5 blocks off for "
                            + String.format(Locale.ROOT, "%.2f", hitsSince(from[0]).get(0).amount()));
                    return "ok";
                })
                .run("clear", AstrolabeScenario::clearDummies)
                .waitTicks(10);
    }

    private void parallax(Steps steps, Minecraft mc) {
        long[] from = {0};
        steps.run("a dummy 9 blocks south", () -> {
                    stand(mc, 0, 0, at(0, 9).add(0, 1.1, 0));
                    ServerQuery.ask(p -> dummy(p, at(0, 9)));
                })
                .waitTicks(10)
                .run("mark", () -> {
                    from[0] = now();
                    started.clear();
                })
                .run("hold forward", () -> key(mc.options.keyUp, true))
                .press(ModKeyMappings.DASH)
                .run("stop", () -> key(mc.options.keyUp, false))
                .waitTicks(4)
                .press(mc.options.keyAttack)
                .run("a camera behind and to the side", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(3.5, 2.0, -3.0), p.add(0, 1.0, 1.5));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("astrolabe_parallax")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .waitTicks(30)
                .check("the dash attack was the Parallax and its decoy's two bolts struck for 0.8 each", () ->
                        startedCount("parallax") == 1 && hitsSince(from[0]).stream().filter(h -> near(h.amount(), BASE * 0.8)).count() == 2)
                .log("parallax", () -> {
                    summary.add("parallax: out of a dash, the decoy star's two bolts struck the dummy for 3.84 each");
                    return "ok";
                })
                .run("clear", AstrolabeScenario::clearDummies)
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ the Pocket Star and the Supernova

    private @Nullable static PocketStar star(ServerPlayer p) {
        return PocketStar.of(p);
    }

    private void pocketStar(Steps steps, Minecraft mc) {
        long[] from = {0};
        Vec3[] starAt = {Vec3.ZERO};
        steps.command("time set midnight")
                .run("back to the middle, aiming at the ground 6 blocks south", () -> stand(mc, 0, 0, at(0, 6).add(0, 0.05, 0)))
                .run("resonance and the ability ready", AstrolabeScenario::fillAndReady)
                .waitTicks(20)
                .press(mc.options.keyUse)
                .waitUntil("a Pocket Star burns", 20, () -> ServerQuery.ask(p -> star(p) != null))
                .waitTicks(4)
                .run("where it is and what it lit", () -> {
                    starAt[0] = ServerQuery.ask(p -> star(p).position());
                    starLights.clear();
                    starLights.addAll(ServerQuery.ask(p -> star(p).lights()));
                })
                .log("the star's light", () -> ServerQuery.ask(p -> {
                    PocketStar s = star(p);
                    BlockPos under = BlockPos.containing(s.position().x, base.getY(), s.position().z);
                    return String.format(Locale.ROOT, "star at %s, cells %s, block light under it %d, ours %d, base y %d",
                            s.position(), s.lights(), StalkerLight.block(p.level(), under), TempLights.count(p.serverLevel()), base.getY());
                }))
                .check("the ground round it is lit to 15 (our light blocks)", () -> ServerQuery.ask(p -> {
                    PocketStar s = star(p);
                    BlockPos under = BlockPos.containing(s.position().x, base.getY(), s.position().z);
                    return s.lights().size() >= 3 && StalkerLight.block(p.level(), under) >= 14 && TempLights.count(p.serverLevel()) >= 3;
                }))
                .run("stand back and look at it", () -> stand(mc, 0, -2, starAt[0]))
                .waitTicks(2)
                .screenshot("astrolabe_pocket_star")
                .run("an arrow fired at it from the side", () -> ServerQuery.ask(p -> {
                    Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, p.serverLevel());
                    Vec3 from0 = starAt[0].add(7, 1.5, 0);
                    Vec3 d = starAt[0].add(0, 0.3, 0).subtract(from0).normalize();
                    arrow.setPos(from0.x, from0.y, from0.z);
                    arrow.shoot(d.x, d.y, d.z, 1.6f, 0f);
                    p.serverLevel().addFreshEntity(arrow);
                    return true;
                }))
                .waitUntil("the star swallowed it", 30, () -> ServerQuery.ask(p -> star(p) != null && star(p).swallowed() >= 1))
                .run("a Hollow Stalker beside the star", () -> ServerQuery.ask(p -> {
                    HollowStalker s = com.cosmicbreach.entity.stalker.Stalkers.HOLLOW_STALKER.get().create(p.serverLevel());
                    Vec3 a = starAt[0].add(1.2, 0, 0);
                    s.moveTo(a.x, base.getY(), a.z, 0f, 0f);
                    s.setPersistenceRequired();
                    s.holdBack(100000);
                    p.serverLevel().addFreshEntity(s);
                    return true;
                }))
                .waitUntil("it leaves the star's light", 50, () -> ServerQuery.ask(p -> {
                    for (HollowStalker s : p.serverLevel().getEntitiesOfClass(HollowStalker.class, p.getBoundingBox().inflate(32))) {
                        return s.counts()[5] >= 1 && StalkerLight.block(p.level(), s.blockPosition()) < 12;
                    }
                    return false;
                }))
                .log("pocket star", () -> {
                    String s = ServerQuery.ask(p -> String.format(Locale.ROOT, "pocket star: %d cells lit to 15, block light %d on the ground under "
                                    + "it, %d projectile swallowed, a Stalker beside it left its light",
                            star(p).lights().size(), StalkerLight.block(p.level(), BlockPos.containing(star(p).position().x, base.getY(),
                                    star(p).position().z)), star(p).swallowed()));
                    summary.add(s);
                    return s;
                })
                .command("kill @e[type=cosmicbreach:hollow_stalker]")
                .waitUntil("the star burns out", 100, () -> ServerQuery.ask(p -> star(p) == null))
                .waitTicks(5)
                .check("its lights went out with it", () -> ServerQuery.ask(p -> starLights.stream()
                        .noneMatch(c -> TempLights.isOurs(p.serverLevel(), c))));
        // the Supernova, early and late
        for (int round = 0; round < 2; round++) {
            int wait = round == 0 ? 14 : 54;
            boolean early = round == 0;
            steps.run("dummies round where the star will be", () -> {
                        stand(mc, 0, 0, at(0, 6).add(0, 0.05, 0));
                        ServerQuery.ask(p -> {
                            dummy(p, at(1.5, 5.5));
                            dummy(p, at(-1.5, 5.5));
                            return true;
                        });
                    })
                    .run("ready", AstrolabeScenario::fillAndReady)
                    .waitTicks(10)
                    .press(mc.options.keyUse)
                    .waitUntil("a Pocket Star burns", 20, () -> ServerQuery.ask(p -> star(p) != null))
                    .waitTicks(wait)
                    .run("mark", () -> from[0] = now())
                    .press(mc.options.keyUse)
                    .waitTicks(3)
                    .screenshot(early ? "astrolabe_supernova" : "astrolabe_supernova_late")
                    .waitTicks(10)
                    .run("the nova's numbers", () -> {
                        List<Hit> got = hitsSince(from[0]);
                        if (got.isEmpty()) {
                            throw new Steps.Failure("the Supernova hit nothing");
                        }
                        double mv = got.get(0).amount() / BASE; // a crit reads x1.5; fits() takes that back out
                        if (early) {
                            novaEarly = mv;
                        } else {
                            novaLate = mv;
                        }
                    })
                    .run("clear", AstrolabeScenario::clearDummies)
                    .waitTicks(10);
        }
        steps.check("the Supernova's damage follows 1 + 3 x the time left (an early one hits harder)", () -> {
                    // the two readings, each possibly a crit (x1.5): some pair of plain values must fit the formula at the
                    // ages the star was detonated at (16 to 20 and 56 to 60 ticks)
                    boolean fitEarly = fits(novaEarly, 16, 21);
                    boolean fitLate = fits(novaLate, 56, 61);
                    return fitEarly && fitLate && plainOf(novaEarly, 16, 21) > plainOf(novaLate, 56, 61);
                })
                .log("supernova", () -> {
                    String s = String.format(Locale.ROOT, "supernova: detonated early, motion value %.3f (formula at age 18: %.3f); late, %.3f "
                                    + "(age 58: %.3f)", plainOf(novaEarly, 16, 21), PocketStarRules.novaMv(18, 80, 1, 3, false),
                            plainOf(novaLate, 56, 61), PocketStarRules.novaMv(58, 80, 1, 3, false));
                    summary.add(s);
                    return s;
                });
    }

    /** True if {@code mv} (or it without a crit) is the Supernova's motion value at an age in [lo, hi). */
    private static boolean fits(double mv, int lo, int hi) {
        return plainOf(mv, lo, hi) > 0;
    }

    private static double plainOf(double mv, int lo, int hi) {
        for (double candidate : new double[] {mv, mv / CRIT}) {
            for (int age = lo; age < hi; age++) {
                if (Math.abs(candidate - PocketStarRules.novaMv(age, 80, 1, 3, false)) < 0.004) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the Singularity

    private void singularity(Steps steps, Minecraft mc) {
        double[] novaMv = {0};
        int[] novaAge = {0};
        steps.command("time set noon")
                .run("a partner with a Comet Maul, 12 blocks off; a dummy 5 blocks east of its well", () -> ServerQuery.ask(p -> {
                    FakePlayer f = FakePlayerFactory.get(p.serverLevel(), new GameProfile(UUID.fromString("7a1b2c3d-0000-4000-8000-00000000c0de"),
                            "cb_partner"));
                    Vec3 a = at(8, 10);
                    f.moveTo(a.x, a.y, a.z, 90f, 0f);
                    f.setYHeadRot(90f);
                    f.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.COMET_MAUL.get()));
                    partner = f;
                    return true;
                }))
                .waitTicks(5);
        measurePull(steps, mc, false);
        steps.run("the player's Pocket Star at the partner's well", () -> {
                    stand(mc, 0, 0, at(7, 10).add(0, 0.05, 0));
                    fillAndReady();
                })
                .waitTicks(5)
                .press(mc.options.keyUse)
                .waitUntil("the star burns", 20, () -> ServerQuery.ask(p -> star(p) != null))
                .check("the star is inside the well's reach", () -> ServerQuery.ask(p ->
                        PocketStarRules.inside(star(p).position(), at(7, 10), 6.0)));
        measurePull(steps, mc, true);
        steps.check("the star became a Singularity", () -> ServerQuery.ask(p -> star(p) != null && star(p).singular()))
                .run("its age", () -> novaAge[0] = ServerQuery.ask(p -> star(p).age()))
                .press(mc.options.keyUse)
                .waitUntil("the Supernova", 20, () -> ServerQuery.ask(p -> star(p) == null))
                .run("its motion value", () -> novaMv[0] = ServerQuery.ask(p -> lastNova))
                .log("singularity", () -> {
                    double ratio = pullWithStar / Math.max(1e-6, baselinePull);
                    int age = novaAge[0];
                    for (int a = novaAge[0]; a <= novaAge[0] + 4; a++) {
                        if (Math.abs(novaMv[0] - PocketStarRules.novaMv(a, 80, 1, 3, true)) < 0.004) {
                            age = a;
                        }
                    }
                    String s = String.format(Locale.ROOT, "singularity: the well pulled %.3f blocks a tick alone and %.3f with the star in it "
                            + "(x%.2f); the Singularity's Supernova at age %d: motion value %.3f = 1.5 x %.3f", baselinePull,
                            pullWithStar, ratio, age, novaMv[0], PocketStarRules.novaMv(age, 80, 1, 3, false));
                    summary.add(s);
                    return s;
                })
                .check("the pull doubled and the Supernova dealt +50%", () -> {
                    double ratio = pullWithStar / Math.max(1e-6, baselinePull);
                    boolean nova = false;
                    for (int age = novaAge[0]; age <= novaAge[0] + 4; age++) {
                        nova |= Math.abs(novaMv[0] - PocketStarRules.novaMv(age, 80, 1, 3, true)) < 0.004;
                    }
                    return ratio > 1.8 && ratio < 2.2 && nova;
                })
                .run("the partner goes", () -> partner = null);
    }

    /** The partner's well pulls a dummy 5 blocks from its centre: the mean pull per tick over ticks 4 to 14 of it. */
    private void measurePull(Steps steps, Minecraft mc, boolean withStar) {
        steps.run("a dummy 5 blocks east of the well", () -> pullDummy = ServerQuery.ask(p -> dummy(p, at(12, 10)).getId()))
                .run("the partner plants its well", () -> ServerQuery.ask(p -> {
                    FakePlayer f = partner;
                    PlayerCombat combat = PlayerCombat.of(f);
                    combat.syncWeapon();
                    CombatStateMachine m = combat.machine();
                    m.syncFromServer(m.maxResonance(), m.dashCharges(), 0);
                    combat.apply(CombatAction.ABILITY_PRESS, CombatStateMachine.Context.GROUNDED);
                    combat.apply(CombatAction.ABILITY_RELEASE, CombatStateMachine.Context.GROUNDED);
                    pulled.clear();
                    samplingPull = true;
                    return true;
                }))
                .waitTicks(8 + 16)
                .run("stop sampling", () -> samplingPull = false)
                .run("the pull per tick", () -> {
                    // the plant is 8 ticks in; skip the first 4 pull ticks, then 10 ticks
                    List<Vec3> ps = List.copyOf(pulled);
                    if (ps.size() < 22) {
                        throw new Steps.Failure("too few samples of the pulled dummy: " + ps.size());
                    }
                    double d = ps.get(8 + 4).distanceTo(ps.get(8 + 14)) / 10.0;
                    if (withStar) {
                        pullWithStar = d;
                    } else {
                        baselinePull = d;
                    }
                })
                .waitTicks(16)
                .run("clear", AstrolabeScenario::clearDummies)
                .waitTicks(5);
    }
}
