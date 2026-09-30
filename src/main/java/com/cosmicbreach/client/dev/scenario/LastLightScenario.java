package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.lastlight.LastLight;
import com.cosmicbreach.relic.lastlight.LastLightRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Last Light (G9b, GDD 7.3) through the real key path, every number measured on the server:
 * <ul>
 *   <li>looks: the glaive in the hand in first person (at rest and at full Resonance) and in third person, a thrust
 *       from the eyes and from the side;</li>
 *   <li>the three thrusts' loop on a dummy, four loops tapped in rhythm: its damage per second at zero stats against
 *       the GDD's 15.5, every thrust landing;</li>
 *   <li>a charged Sunspear with no Sunlight: the baseline;</li>
 *   <li>Dawnguard among three zombies: every blow in the 30-tick stance parried for exactly 10 Resonance, no harm taken,
 *       a Daybreak on the zombies (motion value 2.4), a Sunlight charge per parry up to three, the flare photographed;</li>
 *   <li>Dawnguard against a skeleton's arrow: parried, and the Daybreak cuts the archer;</li>
 *   <li>a Sunspear spending three charges: +40% each (x2.2 on the baseline), the charges gone after.</li>
 * </ul>
 */
public final class LastLightScenario implements Scenario {
    static final String DUMMY = "cb_lastlight";
    private static final double BASE = 9.0;
    private static final double CRIT = 1.5;

    private static ResourceLocation move(String name) {
        return CosmicBreach.id("last_light/" + name);
    }

    private record Hit(int entity, double amount, long time) {
    }

    private record Started(ResourceLocation id, long time) {
    }

    /** A hit on the player the stance saw: its Resonance before and after, cancelled or not, what threw it. */
    private record Guarded(long time, double before, double after, boolean cancelled, boolean arrow, boolean stance) {
    }

    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<Started> started = new CopyOnWriteArrayList<>();
    /** Move starts as the server's machine had them (exact ticks; the client's clock is resynced now and then). */
    private final List<Started> serverStarted = new CopyOnWriteArrayList<>();
    private final List<Guarded> guarded = new CopyOnWriteArrayList<>();
    private final List<Double> playerHurt = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private volatile double resonanceBefore = Double.NaN;
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;
    private final int[] tapTick = {0};
    private double baseline = Double.NaN;
    private final boolean looksOnly;

    public LastLightScenario() {
        this(false);
    }

    /** {@code looksOnly}: only the pictures (the glaive at rest and at each thrust's contact, both views). */
    public LastLightScenario(boolean looksOnly) {
        this.looksOnly = looksOnly;
    }

    @Override
    public int timeBudgetSeconds() {
        return 420;
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

    /** A zombie that fights: its AI on, a pumpkin against the sun, 300 health, planted (no knockback). */
    private static Zombie brawler(ServerPlayer p, Vec3 at) {
        ServerLevel level = p.serverLevel();
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(at.x, at.y, at.z, 0f, 0f);
        z.setPersistenceRequired();
        z.setBaby(false);
        z.addTag(DUMMY);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
        z.setHealth(300f);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        level.addFreshEntity(z);
        z.setTarget(p);
        return z;
    }

    /** A skeleton rooted to the spot (speed 0) with its bow, 100 health, a pumpkin against the sun. */
    static Skeleton archer(ServerPlayer p, Vec3 at, String tag) {
        ServerLevel level = p.serverLevel();
        Skeleton s = EntityType.SKELETON.create(level);
        s.moveTo(at.x, at.y, at.z, 180f, 0f);
        s.setPersistenceRequired();
        s.addTag(tag);
        s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        s.setDropChance(EquipmentSlot.HEAD, 0f);
        s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        s.setHealth(100f);
        s.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        s.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        level.addFreshEntity(s);
        s.setTarget(p);
        return s;
    }

    private static List<LivingEntity> tagged(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40.0), e -> e.getTags().contains(DUMMY));
    }

    private static void clearTagged() {
        ServerQuery.ask(p -> {
            tagged(p).forEach(e -> e.discard());
            p.serverLevel().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(40.0)).forEach(a -> a.discard());
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

    /** Full Resonance and no cooldown, on the server (the client hears it through the usual sync). */
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

    /** The amount without a crit: the nearer of it and it over 1.5 to one of the {@code plain} amounts expected. */
    private static double plain(double amount, double... expected) {
        double best = amount;
        double bestD = Double.MAX_VALUE;
        for (double e : expected) {
            for (double c : new double[] {1.0, CRIT}) {
                double d = Math.abs(amount - e * c);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static boolean near(double amount, double expected) {
        return Math.abs(amount - expected) < 0.05 || Math.abs(amount - expected * CRIT) < 0.05;
    }

    private long startedCount(String name) {
        ResourceLocation id = move(name);
        return started.stream().filter(s -> s.id().equals(id)).count();
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide()) {
                return;
            }
            if (e.getTags().contains(DUMMY)) {
                hits.add(new Hit(e.getId(), event.getNewDamage(), e.level().getGameTime()));
            } else if (e instanceof ServerPlayer) {
                playerHurt.add((double) event.getNewDamage());
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer p) {
                resonanceBefore = PlayerCombat.of(p).machine().resonance();
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer p) {
                CombatStateMachine m = PlayerCombat.of(p).machine();
                boolean stance = m.current() != null && m.phase() == CombatStateMachine.Phase.ACTIVE
                        && m.current().def().traits().effect(LastLight.DAWNGUARD).isPresent();
                guarded.add(new Guarded(p.level().getGameTime(), resonanceBefore, m.resonance(), event.isCanceled(),
                        event.getSource().getDirectEntity() instanceof AbstractArrow, stance));
            }
        });
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.MoveStarted m && Minecraft.getInstance().level != null) {
                started.add(new Started(m.move().id(), Minecraft.getInstance().level.getGameTime()));
            }
        });
        com.cosmicbreach.combat.server.CombatHooks.register(new com.cosmicbreach.combat.server.CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if (event instanceof CombatEvent.MoveStarted m) {
                    serverStarted.add(new Started(m.move().id(), player.level().getGameTime()));
                }
            }

            @Override
            public void onParried(ServerPlayer player, PlayerCombat combat, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity attacker,
                    float amount, boolean early) {
                hookedParries.add(early);
            }
        });
    }

    /** Parries the combat hooks heard of (the Leechstar Signet and the Event Horizon Lens listen there): early or not. */
    private final List<Boolean> hookedParries = new java.util.concurrent.CopyOnWriteArrayList<>();

    private boolean inStance(Minecraft mc) {
        CombatStateMachine m = machine(mc);
        return m.current() != null && m.current().id().equals(move("dawnguard")) && m.phase() == CombatStateMachine.Phase.ACTIVE;
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
                .command("give @s cosmicbreach:last_light")
                .waitUntil("Last Light in hand with its data", 60, () -> mc.player.getMainHandItem().is(Relics.LAST_LIGHT.get())
                        && machine(mc).weapon() != null && machine(mc).weapon().ability().isPresent())
                .run("look south", () -> stand(mc, 0, 0, at(0, 20).add(0, 1.6, 0)))
                .waitTicks(20)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("lastlight_fp_idle")
                .command("cosmicbreach debug resonance 100")
                .waitTicks(30)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("lastlight_fp_full")
                .run("a camera to the player's right", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(-2.6, 1.5, 1.2), p.add(0, 1.1, 0.4));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("lastlight_third")
                .run("front camera", () -> camera.place(mc.player.position().add(0.8, 1.6, 3.0), mc.player.position().add(0, 1.2, 0)))
                .waitTicks(3)
                .screenshot("lastlight_third_front")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                });
        thrusts(steps, mc);
        if (looksOnly) {
            return;
        }
        loop(steps, mc);
        charged(steps, mc, false);
        dawnguardZombies(steps, mc);
        dawnguardSkeleton(steps, mc);
        charged(steps, mc, true);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the thrusts, seen

    /** Each thrust at its contact from the eyes (L1, L2, L3, the Sunspear, Dawnguard's guard), then L3 from the side. */
    private void thrusts(Steps steps, Minecraft mc) {
        int[] tap = {0};
        steps.run("face south, nothing in the way", () -> stand(mc, 0, 0, at(0, 12.0).add(0, 1.6, 0)))
                .command("cosmicbreach debug resonance 0")
                .waitTicks(10)
                .run("clear", () -> {
                    started.clear();
                    tap[0] = 0;
                });
        for (String name : new String[] {"l1", "l2", "l3"}) {
            steps.waitUntil(name + "'s contact", 80, () -> {
                        int n = tap[0]++;
                        key(mc.options.keyAttack, n % 2 == 0 && startedCount("l3") < 1);
                        CombatStateMachine m = machine(mc);
                        return m.current() != null && m.current().id().equals(move(name)) && m.phase() == CombatStateMachine.Phase.ACTIVE;
                    })
                    .screenshot("lastlight_fp_" + name);
        }
        steps.run("let go", () -> key(mc.options.keyAttack, false))
                .waitTicks(30)
                .run("hold attack", () -> key(mc.options.keyAttack, true))
                .waitUntil("charging", 30, () -> machine(mc).phase() == CombatStateMachine.Phase.CHARGING)
                .waitTicks(12)
                .screenshot("lastlight_fp_charge")
                .waitUntil("held past full", 60, () -> machine(mc).attackHeldTicks() >= 30)
                .run("release", () -> key(mc.options.keyAttack, false))
                .waitUntil("the Sunspear's thrust", 20, () -> machine(mc).current() != null
                        && machine(mc).current().id().equals(move("sunspear")) && machine(mc).phase() == CombatStateMachine.Phase.ACTIVE)
                .screenshot("lastlight_fp_sunspear")
                .waitTicks(30)
                .run("full and ready", LastLightScenario::fillAndReady)
                .waitTicks(5)
                .press(mc.options.keyUse)
                .waitUntil("the stance", 20, () -> inStance(mc))
                .waitTicks(6)
                .screenshot("lastlight_fp_dawnguard")
                .waitUntil("the stance ends", 60, () -> !inStance(mc))
                .waitTicks(20)
                .run("stand back", () -> stand(mc, 0, 0, at(0, 12.0).add(0, 1.4, 0)))
                .run("a camera to the side", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(-3.8, 1.5, 2.2), p.add(0, 1.1, 2.0));
                    camera.use();
                    started.clear();
                    tap[0] = 0;
                })
                .waitUntil("again, to L3's thrust", 80, () -> {
                    int n = tap[0]++;
                    key(mc.options.keyAttack, n % 2 == 0 && startedCount("l3") < 1);
                    CombatStateMachine m = machine(mc);
                    return m.current() != null && m.current().id().equals(move("l3")) && m.phase() == CombatStateMachine.Phase.ACTIVE;
                })
                .run("let go", () -> key(mc.options.keyAttack, false))
                .screenshot("lastlight_third_thrust")
                .waitTicks(30)
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ the loop: DPS

    private void loop(Steps steps, Minecraft mc) {
        long[] from = {0};
        long[] lastL1 = {-1};
        steps.run("a dummy 3 blocks south", () -> ServerQuery.ask(p -> dummy(p, at(0, 3.0))))
                .run("face it", () -> stand(mc, 0, 0, at(0, 3.0).add(0, 1.2, 0)))
                .command("cosmicbreach debug resonance 0")
                .waitTicks(20)
                .run("clear the counts", () -> {
                    hits.clear();
                    started.clear();
                    serverStarted.clear();
                    tapTick[0] = 0;
                    lastL1[0] = -1;
                    from[0] = now();
                })
                .waitUntil("four loops of three thrusts tapped", 320, () -> {
                    int n = tapTick[0]++;
                    boolean down = n % 2 == 0 && startedCount("l3") < 4;
                    key(mc.options.keyAttack, down);
                    // each L3 lunges forward: step back to the start whenever a loop begins, so the dummy stays in reach
                    List<Started> l1 = started.stream().filter(s -> s.id().equals(move("l1"))).toList();
                    if (!l1.isEmpty() && l1.get(l1.size() - 1).time() != lastL1[0]) {
                        lastL1[0] = l1.get(l1.size() - 1).time();
                        stand(mc, 0, 0, at(0, 3.0).add(0, 1.2, 0));
                    }
                    return startedCount("l3") >= 4;
                })
                .run("stop tapping", () -> key(mc.options.keyAttack, false))
                .waitTicks(40)
                .log("loop DPS", () -> {
                    String s = dpsLine();
                    summary.add(s);
                    return s;
                })
                .check("12 thrusts landed; after the first press every loop is 44 ticks: 15.5 DPS without crits", () -> {
                    List<Hit> got = hitsSince(from[0]);
                    double[] r = steady();
                    return got.size() == 12 && r[0] == 44 && r[1] == 44 && r[2] == 44 && r[4] > 15.5 && r[4] < 15.6;
                })
                .run("clear", LastLightScenario::clearTagged)
                .waitTicks(10);
    }

    /**
     * The loops after the first press, from the server's move starts: {loop 2, loop 3, loop 4 in ticks (L1 to L1, the
     * fourth to its L3's end), damage without crits in those three loops, their DPS}. The first loop runs a tick short: its
     * press lands between ticks, the buffered presses on them.
     */
    private double[] steady() {
        List<Started> l1 = serverStarted.stream().filter(s -> s.id().equals(move("l1"))).toList();
        List<Started> l3 = serverStarted.stream().filter(s -> s.id().equals(move("l3"))).toList();
        if (l1.size() < 4 || l3.size() < 4) {
            return new double[] {0, 0, 0, 0, 0};
        }
        long from = l1.get(1).time();
        long to = l3.get(3).time() + 19;
        double plainSum = 0;
        for (Hit h : hits) {
            if (h.time() >= from && h.time() < to) {
                plainSum += plain(h.amount(), BASE * 1.0, BASE * 1.1, BASE * 1.7);
            }
        }
        return new double[] {l1.get(2).time() - l1.get(1).time(), l1.get(3).time() - l1.get(2).time(), to - l1.get(3).time(),
                plainSum, plainSum / ((to - from) / 20.0)};
    }

    private String dpsLine() {
        double[] r = steady();
        List<String> starts = new ArrayList<>();
        long t0 = serverStarted.isEmpty() ? 0 : serverStarted.get(0).time();
        serverStarted.forEach(st -> starts.add(st.id().getPath().replace("last_light/", "") + "@" + (st.time() - t0)));
        double sum = 0;
        int crits = 0;
        for (Hit h : hits) {
            sum += h.amount();
            crits += h.amount() > plain(h.amount(), BASE * 1.0, BASE * 1.1, BASE * 1.7) + 0.01 ? 1 : 0;
        }
        return String.format(Locale.ROOT, "loop (server ticks): %d thrusts landed (%d crits, %.2f damage in all); loops 2 to 4 took "
                        + "%.0f, %.0f and %.0f ticks, %.2f damage without crits: %.2f DPS (GDD 15.5 at zero stats); moves %s",
                hits.size(), crits, sum, r[0], r[1], r[2], r[3], r[4], String.join(" ", starts));
    }

    // ------------------------------------------------------------------ the Sunspear

    private void charged(Steps steps, Minecraft mc, boolean withCharges) {
        long[] released = {-1};
        String label = withCharges ? "three charges" : "no charges";
        steps.run("a dummy 3 blocks south", () -> ServerQuery.ask(p -> dummy(p, at(0, 3.0))))
                .run("face it", () -> stand(mc, 0, 0, at(0, 3.0).add(0, 1.2, 0)))
                .waitTicks(10);
        if (withCharges) {
            steps.check("three Sunlight charges held", () -> ServerQuery.ask(p -> LastLight.charges(p)) == 3);
        } else {
            steps.check("no Sunlight charges held", () -> ServerQuery.ask(p -> LastLight.charges(p)) == 0);
        }
        steps.run("clear", () -> {
                    hits.clear();
                    started.clear();
                })
                .run("hold attack", () -> key(mc.options.keyAttack, true))
                .waitUntil("charging", 30, () -> machine(mc).phase() == CombatStateMachine.Phase.CHARGING)
                .waitUntil("held past full", 60, () -> machine(mc).attackHeldTicks() >= 32)
                .run("release", () -> key(mc.options.keyAttack, false))
                .waitUntil("the Sunspear", 20, () -> startedCount("sunspear") >= 1)
                .run("its start", () -> released[0] = started.stream().filter(s -> s.id().equals(move("sunspear"))).findFirst()
                        .map(Started::time).orElse(-1L));
        if (withCharges) {
            steps.run("a camera to the side", () -> {
                        camera = DevCamera.create(mc.level);
                        Vec3 p = mc.player.position();
                        camera.place(p.add(-4.0, 1.6, 1.8), p.add(0, 1.1, 1.8));
                        camera.use();
                    })
                    .waitUntil("its thrust", 10, () -> machine(mc).phase() == CombatStateMachine.Phase.ACTIVE)
                    .screenshot("lastlight_sunspear")
                    .run("back to the eyes", () -> {
                        camera.remove();
                        camera = null;
                    });
        }
        steps.waitTicks(30)
                .log("Sunspear, " + label, () -> {
                    List<Hit> after = hits.stream().filter(h -> h.time() >= released[0]).toList();
                    double amount = after.isEmpty() ? 0 : after.get(0).amount();
                    String s;
                    if (!withCharges) {
                        baseline = plain(amount, BASE * 3.0);
                        s = String.format(Locale.ROOT, "Sunspear, no charges: %.2f (%.2f without a crit; 9 x 3.0 = 27.0)", amount, baseline);
                    } else {
                        double pl = plain(amount, BASE * 3.0 * LastLightRules.chargedMultiplier(3));
                        s = String.format(Locale.ROOT, "Sunspear, three charges: %.2f (%.2f without a crit): x%.3f the baseline "
                                + "(GDD: +40%% each, x2.2)", amount, pl, pl / baseline);
                    }
                    summary.add(s);
                    return s;
                })
                .check("the Sunspear's hit, " + label, () -> {
                    List<Hit> after = hits.stream().filter(h -> h.time() >= released[0]).toList();
                    if (after.size() != 1) {
                        return false;
                    }
                    double expected = BASE * 3.0 * (withCharges ? LastLightRules.chargedMultiplier(3) : 1.0);
                    return near(after.get(0).amount(), expected);
                });
        if (withCharges) {
            steps.check("the charges are spent", () -> ServerQuery.ask(p -> LastLight.charges(p)) == 0);
        }
        steps.run("clear", LastLightScenario::clearTagged).waitTicks(10);
    }

    // ------------------------------------------------------------------ Dawnguard

    private void dawnguardZombies(Steps steps, Minecraft mc) {
        long[] from = {0};
        steps.run("face south", () -> stand(mc, 0, 0, at(0, 6).add(0, 1.4, 0)))
                .run("three zombies close round", () -> ServerQuery.ask(p -> {
                    brawler(p, at(-1.5, 0.9));
                    brawler(p, at(1.5, 0.9));
                    brawler(p, at(0, 1.7));
                    return true;
                }))
                .run("full and ready", LastLightScenario::fillAndReady)
                .waitUntil("the zombies are at arm's length", 120, () -> ServerQuery.ask(p -> tagged(p).stream()
                        .allMatch(z -> z.distanceTo(p) < 2.2)))
                .run("a camera behind and above", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(2.8, 3.0, -3.2), p.add(0, 1.0, 0.6));
                })
                .run("mark", () -> {
                    from[0] = now();
                    guarded.clear();
                    hits.clear();
                    playerHurt.clear();
                    hookedParries.clear();
                })
                .press(mc.options.keyUse)
                .waitUntil("the stance", 20, () -> inStance(mc))
                .run("watch from the camera", () -> camera.use())
                .waitUntil("a parry", 40, () -> guarded.stream().anyMatch(Guarded::cancelled))
                .screenshot("lastlight_dawnguard_flare")
                .waitUntil("the stance ends", 60, () -> !inStance(mc))
                .waitTicks(4)
                .screenshot("lastlight_dawnguard_after")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .log("Dawnguard among zombies", () -> {
                    List<Guarded> parried = guarded.stream().filter(Guarded::cancelled).toList();
                    List<String> drains = new ArrayList<>();
                    parried.forEach(g -> drains.add(String.format(Locale.ROOT, "%.1f", g.before() - g.after())));
                    List<Hit> daybreaks = hitsSince(from[0]);
                    List<String> amounts = new ArrayList<>();
                    daybreaks.forEach(h -> amounts.add(String.format(Locale.ROOT, "%.2f", h.amount())));
                    String s = String.format(Locale.ROOT, "Dawnguard among 3 zombies: %d blows parried (Resonance paid each: %s), "
                                    + "%d landed, %d Daybreak hits (%s; 9 x 2.4 = 21.6), Sunlight %d, player hurt %s",
                            parried.size(), String.join(" ", drains), guarded.size() - parried.size(), daybreaks.size(),
                            String.join(" ", amounts), ServerQuery.ask(p -> LastLight.charges(p)), playerHurt);
                    summary.add(s);
                    return s;
                })
                .check("every blow the stance could pay for parried for exactly 10 Resonance; only unpaid ones landed", () -> {
                    List<Guarded> parried = guarded.stream().filter(g -> g.stance() && g.cancelled()).toList();
                    List<Guarded> landed = guarded.stream().filter(g -> g.stance() && !g.cancelled()).toList();
                    return parried.size() >= 3 && parried.stream().allMatch(g -> Math.abs(g.before() - g.after() - 10.0) < 1e-6)
                            && landed.stream().allMatch(g -> g.before() < LastLightRules.PARRY_COST);
                })
                .check("Daybreak cut the zombies at motion value 2.4", () -> {
                    List<Hit> daybreaks = hitsSince(from[0]);
                    return !daybreaks.isEmpty() && daybreaks.stream().allMatch(h -> near(h.amount(), BASE * LastLightRules.DAYBREAK_MV));
                })
                .check("three Sunlight charges stored", () -> ServerQuery.ask(p -> LastLight.charges(p)) == 3)
                // the stance's parries are parries to everything that listens (the Leechstar heals, the Lens opens on early ones)
                .check("each of the stance's parries reached the parry hooks", () -> hookedParries.size()
                        == guarded.stream().filter(g -> g.stance() && g.cancelled()).count())
                .run("clear", LastLightScenario::clearTagged)
                .waitTicks(10);
    }

    private void dawnguardSkeleton(Steps steps, Minecraft mc) {
        long[] from = {0};
        steps.run("face south", () -> stand(mc, 0, 0, at(0, 6).add(0, 1.4, 0)))
                .run("a skeleton 4.2 blocks off", () -> ServerQuery.ask(p -> archer(p, at(0, 4.2), DUMMY)))
                .run("full and ready", LastLightScenario::fillAndReady)
                .run("mark", () -> {
                    guarded.clear();
                    hits.clear();
                    playerHurt.clear();
                })
                .waitUntil("the skeleton draws its bow", 300, () -> ServerQuery.ask(p -> tagged(p).stream()
                        .anyMatch(s -> s.isUsingItem() && s.getTicksUsingItem() >= 12)))
                .run("mark the time", () -> from[0] = now())
                .press(mc.options.keyUse)
                .waitUntil("the stance", 20, () -> inStance(mc))
                .waitUntil("its arrow parried", 40, () -> guarded.stream().anyMatch(g -> g.cancelled() && g.arrow()))
                .waitTicks(3)
                .screenshot("lastlight_dawnguard_arrow")
                .waitUntil("the stance ends", 60, () -> !inStance(mc))
                .log("Dawnguard against a skeleton", () -> {
                    List<Hit> daybreaks = hitsSince(from[0]);
                    List<String> amounts = new ArrayList<>();
                    daybreaks.forEach(h -> amounts.add(String.format(Locale.ROOT, "%.2f", h.amount())));
                    String s = String.format(Locale.ROOT, "Dawnguard against a skeleton 4.2 blocks off: %d arrows parried, "
                                    + "the Daybreak struck it %d times (%s), player hurt %s",
                            guarded.stream().filter(g -> g.cancelled() && g.arrow()).count(), daybreaks.size(),
                            String.join(" ", amounts), playerHurt);
                    summary.add(s);
                    return s;
                })
                .check("the arrow parried and the archer cut by Daybreak", () -> {
                    List<Hit> daybreaks = hitsSince(from[0]);
                    return playerHurt.isEmpty() && !daybreaks.isEmpty()
                            && daybreaks.stream().allMatch(h -> near(h.amount(), BASE * LastLightRules.DAYBREAK_MV));
                })
                .run("clear", LastLightScenario::clearTagged)
                .waitTicks(10);
    }
}
