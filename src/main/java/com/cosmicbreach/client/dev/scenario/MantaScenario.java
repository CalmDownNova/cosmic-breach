package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.mount.MountScreen;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.MantaCall;
import com.cosmicbreach.mount.MantaRules;
import com.cosmicbreach.mount.MountGear;
import com.cosmicbreach.mount.MountMenu;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.structure.choir.ChoirJudge;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Drift Manta (G6b, GDD 8.1) with real inputs:
 * <ul>
 *   <li>outside the Drift (the flat test world, off a platform 24 up) it glides: forward at 0.35, sinking, never rising
 *       even with jump held and the rider looking up;</li>
 *   <li>in the Drift: a wild manta is called with the Resonance Chime and answered on Vesper's beat, each use timed on the
 *       server's tick and judged there; a clean phrase, then a deliberate early answer: it swims off for 30 s and ignores
 *       the Chime meanwhile; then three clean phrases tame it. Every judged use is checked against the offset it landed
 *       at, and a phrase the load spoils is started over;</li>
 *   <li>ridden in the Drift: 0.25 up with jump, 0.35 forward, the Gale Fins' +15%; Phase Blink's 8 blocks, its six
 *       shielded ticks for rider and manta (damage tried on every server tick), its 120-tick cooldown and the Nebula
 *       Reins' 84; mounted light attacks L1, L2, L1;</li>
 *   <li>looks: the wild manta, its song spots flaring on a note, geared from three sides, swimming and ridden.</li>
 * </ul>
 */
public final class MantaScenario implements Scenario {
    private static final ResourceLocation L1 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l1");
    private static final ResourceLocation L2 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l2");

    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private int manta = -1;
    private @Nullable DevCamera camera;
    private final List<Vec3> track = new ArrayList<>();

    /** The shield watch: server ticks after an accepted blink, and whether damage got through on each. */
    private volatile long watchBlinkAt = Long.MIN_VALUE;
    private final List<long[]> shieldTries = new CopyOnWriteArrayList<>();

    @Override
    public int timeBudgetSeconds() {
        return 480;
    }

    @Override
    public void steps(Steps steps) {
        ClientCombat.addEventListener(events::add);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
        setUp(steps);
        glider(steps);
        drift(steps);
        calling(steps);
        riding(steps);
        blink(steps);
        mountedCombat(steps);
        looks(steps);
        steps.run("summary", () -> results.forEach(line -> CosmicBreach.LOGGER.info("[manta] " + line)))
                .log("results", () -> String.join(" | ", results));
    }

    // ------------------------------------------------------------------ helpers

    private @Nullable DriftManta client() {
        Entity e = mc.level == null ? null : mc.level.getEntity(manta);
        return e instanceof DriftManta m ? m : null;
    }

    private DriftManta clientOrFail() {
        DriftManta m = client();
        if (m == null) {
            throw new Steps.Failure("the manta is not loaded here");
        }
        return m;
    }

    private static DriftManta server(MinecraftServer srv, int id) {
        Entity e = CryptKit.player(srv).serverLevel().getEntity(id);
        if (!(e instanceof DriftManta m)) {
            throw new Steps.Failure("no manta " + id + " on the server");
        }
        return m;
    }

    /** The integrated server's tick, read without waiting on the server thread. */
    private long serverTick() {
        MinecraftServer srv = mc.getSingleplayerServer();
        ServerLevel level = srv == null || mc.level == null ? null : srv.getLevel(mc.level.dimension());
        return level != null ? level.getGameTime() : mc.level.getGameTime();
    }

    private DriftManta ridden() {
        if (!(mc.player.getVehicle() instanceof DriftManta m)) {
            throw new Steps.Failure("not riding a manta");
        }
        return m;
    }

    private void select(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                mc.player.getInventory().selected = i;
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

    private void quickMove(int menuSlot) {
        if (!(mc.player.containerMenu instanceof MountMenu menu)) {
            throw new Steps.Failure("the mount's inventory is not open");
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private void aimAtManta() {
        CryptKit.aim(mc, clientOrFail().getBoundingBox().getCenter());
    }

    private void click(net.minecraft.client.KeyMapping key) {
        CryptKit.set(key, true);
        net.minecraft.client.KeyMapping.click(key.getKey());
    }

    /** Brings the manta to {@code blocks} in front of the player, level with the player's feet plus {@code up} (server side). */
    private void fetch(Steps steps, double blocks, double up) {
        steps.run("bring the manta in front", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    DriftManta m = server(srv, manta);
                    Vec3 at = p.position().add(Vec3.directionFromRotation(0f, p.getYRot()).scale(blocks)).add(0, up, 0);
                    m.teleportTo(at.x, at.y, at.z);
                    m.setDeltaMovement(Vec3.ZERO);
                    return null;
                }))
                .waitTicks(4);
    }

    private void uncamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
        mc.options.hideGui = false;
    }

    private void cameraBeside(double sideBlocks, double ahead, double up, float turn) {
        DriftManta m = clientOrFail();
        Vec3 fwd = Vec3.directionFromRotation(0f, m.getYRot());
        Vec3 dir = fwd.yRot((float) Math.toRadians(-turn));
        Vec3 at = m.position().add(fwd.scale(ahead)).add(0, 0.5, 0);
        uncamera();
        camera = DevCamera.create(mc.level);
        camera.place(at.add(dir.scale(sideBlocks)).add(0, up, 0), at);
        camera.use();
        mc.options.hideGui = true;
    }

    /**
     * A spot near {@code near} in full daylight (sky light 15) with open air round it, searched in rings out to 64
     * blocks and up to 24 blocks higher; {@code near} itself if there is none.
     */
    private static Vec3 openSky(ServerLevel level, Vec3 near) {
        for (int up = 0; up <= 24; up += 6) {
            for (int r = 0; r <= 64; r += 4) {
                int steps = Math.max(1, r * 2);
                for (int k = 0; k < steps; k++) {
                    double a = 2 * Math.PI * k / steps;
                    net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(near.x + Math.cos(a) * r, near.y + up,
                            near.z + Math.sin(a) * r);
                    if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, c) < 15) {
                        continue;
                    }
                    boolean clear = true;
                    for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 2, 3))) {
                        if (!level.getBlockState(q).isAir()) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) {
                        return Vec3.atCenterOf(c);
                    }
                }
            }
        }
        return near;
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

    // ------------------------------------------------------------------ the shield watch (server thread)

    private void onServerTick(ServerTickEvent.Post event) {
        if (watchBlinkAt == Long.MIN_VALUE || manta < 0) {
            return;
        }
        try {
            ServerPlayer p = event.getServer().getPlayerList().getPlayers().get(0);
            if (!(p.serverLevel().getEntity(manta) instanceof DriftManta m) || m.blinkAt() == watchBlinkAt) {
                return;
            }
            long now = p.serverLevel().getGameTime();
            long since = now - m.blinkAt();
            if (since > 8) {
                watchBlinkAt = Long.MIN_VALUE;
                return;
            }
            p.invulnerableTime = 0;
            m.invulnerableTime = 0;
            float ph = p.getHealth();
            float mh = m.getHealth();
            p.hurt(p.serverLevel().damageSources().generic(), 1.0f);
            m.hurt(p.serverLevel().damageSources().generic(), 1.0f);
            shieldTries.add(new long[] {since, p.getHealth() < ph ? 1 : 0, m.getHealth() < mh ? 1 : 0});
            p.setHealth(ph);
            m.setHealth(mh);
        } catch (RuntimeException e) {
            CosmicBreach.LOGGER.error("[cosmicbreach] manta: the shield watch failed", e);
            watchBlinkAt = Long.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ sections

    private void setUp(Steps steps) {
        steps.command("gamemode survival")
                .waitUntil("survival", 40, () -> !mc.player.isCreative())
                .command("effect give @s minecraft:resistance infinite 4 true")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .command("clear @s")
                .command("give @s cosmicbreach:resonance_chime")
                .command("give @s cosmicbreach:drift_harness 2")
                .command("give @s cosmicbreach:gale_fins")
                .command("give @s cosmicbreach:nebula_reins")
                .command("give @s cosmicbreach:nebulite_barding")
                .command("give @s cosmicbreach:meridian")
                .waitUntil("the gear is in the hotbar", 60, () -> hotbarSlotOf(ModItems.MERIDIAN.get()) >= 0
                        && hotbarSlotOf(Mounts.RESONANCE_CHIME.get()) >= 0)
                .look(0f, 0f);
    }

    /** Outside the Drift: off a platform 24 blocks up, it can only glide down. */
    private void glider(Steps steps) {
        steps.command("fill ~-2 ~23 ~-2 ~2 ~23 ~2 minecraft:stone")
                .command("tp @s ~ ~24 ~ 0 0")
                .waitUntil("on the platform", 60, () -> mc.player.onGround() && mc.player.getY() > -40)
                .run("a tamed manta beside it", () -> manta = CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
                    Vec3 at = p.position().add(0, 0.3, 1.8); // on the platform, within reach
                    m.moveTo(at.x, at.y, at.z, 0f, 0f);
                    m.tameTo(p);
                    p.serverLevel().addFreshEntity(m);
                    return m.getId();
                }))
                .waitUntil("the client sees it", 40, () -> client() != null)
                .run("the harness in hand", () -> {
                    select(Mounts.DRIFT_HARNESS.get());
                    aimAtManta();
                })
                .press(mc.options.keyUse)
                .waitUntil("harnessed", 30, () -> CryptKit.server(srv -> server(srv, manta).isSaddled()))
                .run("aim empty-handed", () -> {
                    select(ModItems.MERIDIAN.get());
                    mc.player.getInventory().selected = 8;
                    aimAtManta();
                })
                .press(mc.options.keyUse)
                .waitUntil("riding the manta", 40, () -> mc.player.getVehicle() instanceof DriftManta)
                .check("outside the Drift", () -> !ridden().inDrift())
                .look(0f, -40f)
                .run("fly off: forward, jump held, looking up", () -> {
                    track.clear();
                    CryptKit.set(mc.options.keyUp, true);
                    CryptKit.set(mc.options.keyJump, true);
                })
                .waitUntil("70 ticks of gliding", 75, () -> {
                    track.add(ridden().position());
                    return track.size() > 70;
                })
                .run("let go", () -> CryptKit.releaseAll(mc));
        double[] m = {0, 0, -1};
        steps.run("measure", () -> {
                    double rise = -1;
                    for (int i = 1; i < track.size(); i++) {
                        rise = Math.max(rise, track.get(i).y - track.get(i - 1).y);
                    }
                    Vec3 a = track.get(40);
                    Vec3 b = track.get(70);
                    m[0] = Math.hypot(b.x - a.x, b.z - a.z) / 30.0;
                    m[1] = (a.y - b.y) / 30.0;
                    m[2] = rise;
                })
                .check("it never rose (jump held, looking up)", () -> m[2] <= 1e-6)
                .check("it glides at 0.35 forward, sinking 0.06", () -> Math.abs(m[0] - MantaRules.FORWARD) < 0.01
                        && Math.abs(m[1] - MantaRules.GLIDE_SINK) < 0.005)
                .run("result glider", () -> results.add(String.format(Locale.ROOT,
                        "outside the Drift: forward %.3f, sink %.3f (%.1f to 1), highest rise in a tick %.4f", m[0], m[1], m[0] / m[1], m[2])));
    }

    private void drift(Steps steps) {
        steps.command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 300, () -> mc.level.dimension().location().getPath().equals("aetheria") && mc.player.getVehicle() == null)
                .waitUntil("the Drift is drawn", 500, CryptKit.settled(mc, 400))
                .command("cosmicbreach weather clear")
                .command("time set 6000")
                .look(0f, 0f)
                .command("fill ~-15 ~ ~-15 ~15 ~24 ~15 air")
                .command("fill ~-15 ~ ~16 ~15 ~24 ~46 air")
                .waitTicks(10)
                .check("in the Drift", () -> com.cosmicbreach.mount.Mounts.inDrift(mc.level, mc.player.getY()))
                .command("cosmicbreach debug mount spawn manta")
                .waitUntil("a wild manta", 60, () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    List<DriftManta> all = p.serverLevel().getEntitiesOfClass(DriftManta.class, p.getBoundingBox().inflate(30.0));
                    if (all.size() == 1) {
                        manta = all.get(0).getId();
                        return true;
                    }
                    return false;
                }))
                .waitUntil("the client sees it", 40, () -> client() != null)
                .waitTicks(30)
                .run("camera on the wild manta", () -> cameraBeside(8.0, 0.0, 2.5, 60f))
                .waitTicks(3)
                .screenshot("manta_wild")
                .run("camera off", this::uncamera);
    }

    /** Answers the manta's phrases on the beat: note {@code missNote} of phrase {@code missPhrase} early by six ticks. */
    private final class Answerer {
        long phraseStart = Long.MIN_VALUE;
        final boolean[] pressed = new boolean[MantaCall.NOTES];
        int phrase = -1;
        int missPhrase = -1;
        int missNote = -1;
        int releaseIn;

        void tick() {
            if (releaseIn > 0 && --releaseIn == 0) {
                CryptKit.set(mc.options.keyUse, false);
            }
            DriftManta m = client();
            if (m == null) {
                return;
            }
            MantaCall.Phase ph = m.callPhase();
            if (ph != MantaCall.Phase.LEAD && ph != MantaCall.Phase.CALL && ph != MantaCall.Phase.ANSWER) {
                return;
            }
            long cs = m.callStart();
            if (cs != phraseStart) {
                phraseStart = cs;
                phrase++;
                java.util.Arrays.fill(pressed, false);
            }
            long now = serverTick();
            for (int i = 0; i < MantaCall.NOTES; i++) {
                if (pressed[i]) {
                    continue;
                }
                long target = cs + MantaCall.BAR + (long) i * MantaCall.BEAT;
                if (phrase == missPhrase && i == missNote) {
                    target -= 6;
                }
                if (now >= target - 1 && now <= target + 2) {
                    select(Mounts.RESONANCE_CHIME.get());
                    click(mc.options.keyUse);
                    releaseIn = 1;
                    pressed[i] = true;
                }
                break;
            }
        }
    }

    private void calling(Steps steps) {
        Answerer bot = new Answerer();
        int[] retries = {0};
        long[] miss = {0, 0};
        double[] away = {0, 0};
        fetch(steps, 7.0, 2.0);
        steps.run("the Chime in hand", () -> select(Mounts.RESONANCE_CHIME.get()))
                .press(mc.options.keyUse)
                .waitUntil("the manta starts its call", 40, () -> client() != null && client().callPhase() != MantaCall.Phase.IDLE
                        && client().callerId() == mc.player.getId())
                .run("song spots camera", () -> cameraBeside(6.5, 0.0, 2.0, 70f))
                .waitUntil("a note sung", 200, () -> {
                    bot.tick();
                    DriftManta m = client();
                    return m != null && mc.level.getGameTime() - m.sungAt() <= 1;
                })
                .screenshot("manta_singing")
                .run("camera off", this::uncamera)
                .waitUntil("one clean phrase", 400, () -> {
                    bot.tick();
                    return CryptKit.server(srv -> server(srv, manta).call().clean()) >= 1;
                })
                .run("plan the miss: the next phrase's second answer six ticks early", () -> {
                    bot.missPhrase = bot.phrase + 1;
                    bot.missNote = 1;
                })
                .waitUntil("the early answer sends it off", 400, () -> {
                    bot.tick();
                    return CryptKit.server(srv -> server(srv, manta).call().phase()) == MantaCall.Phase.RETREAT;
                })
                .run("release", () -> CryptKit.set(mc.options.keyUse, false))
                .run("the miss", () -> {
                    long[] last = CryptKit.server(srv -> {
                        List<long[]> c = server(srv, manta).chimes();
                        return c.get(c.size() - 1).clone();
                    });
                    miss[0] = last[0];
                    miss[1] = last[3];
                    away[0] = clientOrFail().distanceTo(mc.player);
                })
                .check("judged a miss, six ticks early", () -> CryptKit.server(srv -> {
                    List<long[]> c = server(srv, manta).chimes();
                    long[] last = c.get(c.size() - 1);
                    return last[1] == MantaCall.Verdict.MISS.ordinal() && last[3] <= -4 && last[3] >= -8;
                }))
                .check("away for 600 ticks", () -> CryptKit.server(srv -> server(srv, manta).call().retreatUntil()) - miss[0] == MantaCall.RETREAT_TICKS)
                .waitTicks(100)
                .run("how far it swam", () -> away[1] = clientOrFail().distanceTo(mc.player))
                .check("it swam well away", () -> away[1] - away[0] > 8.0)
                .run("chime at it while it is away", () -> select(Mounts.RESONANCE_CHIME.get()))
                .press(mc.options.keyUse)
                .waitTicks(5)
                .check("a Chime meanwhile does nothing", () -> CryptKit.server(srv -> {
                    DriftManta m = server(srv, manta);
                    return m.call().phase() == MantaCall.Phase.RETREAT && m.chimes().get(m.chimes().size() - 1)[0] == miss[0];
                }))
                .waitUntil("back after 30 s", 700, () -> CryptKit.server(srv -> server(srv, manta).call().phase()) == MantaCall.Phase.IDLE)
                .run("how long", () -> results.add(String.format(Locale.ROOT, "miss: offset %d, swam %.1f to %.1f blocks away, calm again after %d ticks",
                        miss[1], away[0], away[1], CryptKit.server(srv -> srv.getLevel(mc.level.dimension()).getGameTime()) - miss[0])));
        fetch(steps, 6.0, 1.5);
        // three clean phrases in a row; a phrase the load spoils is started over (logged)
        steps.run("the Chime again", () -> {
                    bot.missPhrase = -1;
                    select(Mounts.RESONANCE_CHIME.get());
                })
                .press(mc.options.keyUse)
                .waitUntil("three clean phrases in a row tame it", 2400, () -> {
                    bot.tick();
                    int[] st = CryptKit.server(srv -> {
                        DriftManta m = server(srv, manta);
                        return new int[] {m.isTamed() ? 1 : 0, m.call().phase().ordinal()};
                    });
                    if (st[0] == 1) {
                        return true;
                    }
                    if (st[1] == MantaCall.Phase.RETREAT.ordinal()) {
                        if (++retries[0] > 3) {
                            throw new Steps.Failure("four phrases spoiled under load");
                        }
                        CosmicBreach.LOGGER.warn("[manta] a phrase went wrong under load; calming it and starting over");
                        CryptKit.server(srv -> {
                            server(srv, manta).calm();
                            return null;
                        });
                        CryptKit.set(mc.options.keyUse, false);
                    }
                    if (st[1] == MantaCall.Phase.IDLE.ordinal() && bot.releaseIn == 0) {
                        select(Mounts.RESONANCE_CHIME.get());
                        click(mc.options.keyUse);
                        bot.releaseIn = 1;
                    }
                    return false;
                })
                .run("release", () -> CryptKit.set(mc.options.keyUse, false))
                .check("tamed by the player", () -> CryptKit.server(srv -> {
                    DriftManta m = server(srv, manta);
                    return m.isTamed() && CryptKit.player(srv).getUUID().equals(m.getOwnerUUID());
                }))
                .check("every judged use agrees with the offset it landed at", () -> CryptKit.server(srv -> {
                    DriftManta m = server(srv, manta);
                    for (long[] c : m.chimes()) {
                        boolean hit = c[1] == MantaCall.Verdict.HIT.ordinal() || c[1] == MantaCall.Verdict.DONE.ordinal();
                        if (c[3] == Long.MIN_VALUE) {
                            if (hit) {
                                return false;
                            }
                            continue;
                        }
                        if (hit != ChoirJudge.within(c[3], m.call().window(), 0)) {
                            return false;
                        }
                    }
                    return true;
                }))
                .run("result taming", () -> {
                    List<long[]> c = CryptKit.server(srv -> new ArrayList<>(server(srv, manta).chimes()));
                    StringBuilder sb = new StringBuilder();
                    for (long[] x : c) {
                        sb.append(x[3] == Long.MIN_VALUE ? "x" : Long.toString(x[3])).append(x[1] == MantaCall.Verdict.MISS.ordinal() ? "!" : "")
                                .append(' ');
                    }
                    results.add("answers landed at (ticks from the beat, ! a miss): " + sb.toString().trim() + "; retries " + retries[0] + "; tamed");
                });
    }

    private void riding(Steps steps) {
        fetch(steps, 2.2, 0.4);
        steps.run("the harness", () -> {
                    select(Mounts.DRIFT_HARNESS.get());
                    aimAtManta();
                })
                .press(mc.options.keyUse)
                .waitUntil("harnessed", 30, () -> CryptKit.server(srv -> server(srv, manta).isSaddled()))
                .run("aim empty-handed", () -> {
                    mc.player.getInventory().selected = 8;
                    aimAtManta();
                })
                .press(mc.options.keyUse)
                .waitUntil("riding in the Drift", 40, () -> mc.player.getVehicle() instanceof DriftManta)
                .check("in the Drift", () -> ridden().inDrift())
                .look(0f, 0f);
        double[] up = {0};
        steps.run("climb: jump held", () -> {
                    track.clear();
                    CryptKit.set(mc.options.keyJump, true);
                })
                .waitUntil("40 ticks climbing", 45, () -> {
                    track.add(ridden().position());
                    return track.size() > 40;
                })
                .run("climb rate", () -> {
                    up[0] = (track.get(40).y - track.get(25).y) / 15.0;
                    CryptKit.releaseAll(mc);
                })
                .check("it climbs at 0.25 a tick", () -> Math.abs(up[0] - MantaRules.VERTICAL) < 0.006)
                .waitTicks(25);
        double[] fwd = forward(steps, "flight", 0f);
        steps.check("it flies at 0.35 a tick", () -> Math.abs(fwd[0] - MantaRules.FORWARD) < 0.008 && Math.abs(fwd[1]) < 0.01)
                .press(mc.options.keyInventory)
                .waitUntil("the inventory opens from the saddle", 40, () -> mc.screen instanceof MountScreen)
                .run("the Gale Fins in", () -> quickMove(29 + hotbarSlotOf(Mounts.GALE_FINS.get())))
                .waitTicks(3)
                .run("the Nebulite barding in", () -> quickMove(29 + hotbarSlotOf(Mounts.NEBULITE_BARDING.get())))
                .waitTicks(3)
                .screenshot("manta_inventory")
                .run("close", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null)
                .check("Gale Fins on, armor 8", () -> CryptKit.server(srv -> {
                    DriftManta m = server(srv, manta);
                    return m.wears(MountGear.GALE_FINS) && m.getAttributeValue(Attributes.ARMOR) == 8.0;
                }))
                .waitTicks(25);
        double[] fins = forward(steps, "flight with the Gale Fins", 180f);
        steps.check("the Gale Fins: +15%", () -> Math.abs(fins[0] - MantaRules.FORWARD * MantaRules.GALE_FINS) < 0.008)
                .run("result flight", () -> results.add(String.format(Locale.ROOT,
                        "in the Drift: climb %.3f, forward %.3f (level %.4f), Gale Fins %.3f (x%.3f), Nebulite armor 8",
                        up[0], fwd[0], fwd[1], fins[0], fins[0] / fwd[0])))
                .waitTicks(25);
    }

    /** Holds forward 45 ticks facing {@code yaw}; returns {horizontal speed, vertical speed} over the last 20. */
    private double[] forward(Steps steps, String what, float yaw) {
        double[] out = {0, 0};
        steps.look(yaw, 0f)
                .run(what + ": forward", () -> {
                    track.clear();
                    CryptKit.set(mc.options.keyUp, true);
                })
                .waitUntil(what + ": 45 ticks", 50, () -> {
                    track.add(ridden().position());
                    return track.size() > 45;
                })
                .run(what + ": speed", () -> {
                    Vec3 a = track.get(25);
                    Vec3 b = track.get(45);
                    out[0] = Math.hypot(b.x - a.x, b.z - a.z) / 20.0;
                    out[1] = (b.y - a.y) / 20.0;
                    CryptKit.releaseAll(mc);
                });
        return out;
    }

    private void blink(Steps steps) {
        Vec3[] from = {Vec3.ZERO};
        long[] at = {0, 0};
        steps.look(0f, 0f)
                .waitUntil("hovering still", 60, () -> ridden().getDeltaMovement().length() < 0.01)
                .run("where it is", () -> {
                    from[0] = CryptKit.server(srv -> server(srv, manta).position());
                    at[0] = CryptKit.server(srv -> server(srv, manta).blinkAt());
                    shieldTries.clear();
                    watchBlinkAt = at[0];
                })
                .press(ModKeyMappings.DASH)
                .waitUntil("the server accepts the blink", 20, () -> CryptKit.server(srv -> server(srv, manta).blinkAt()) != at[0])
                .run("camera", () -> cameraBeside(9.0, -3.0, 2.0, 90f))
                .waitTicks(1)
                .screenshot("manta_blink")
                .run("camera off", this::uncamera)
                .waitTicks(10);
        double[] dist = {0};
        steps.run("distance", () -> {
                    Vec3 to = CryptKit.server(srv -> server(srv, manta).position());
                    dist[0] = to.distanceTo(from[0]);
                    at[1] = CryptKit.server(srv -> server(srv, manta).blinkAt());
                })
                .check("Phase Blink carried it 8 blocks forward", () -> Math.abs(dist[0] - MantaRules.BLINK_DISTANCE) < 0.4)
                .check("the shield: nothing got through on ticks 1 to 5, damage did on 6 to 8", () -> {
                    if (shieldTries.size() < 7) {
                        return false;
                    }
                    for (long[] t : shieldTries) {
                        boolean shielded = t[0] < MantaRules.BLINK_SHIELD;
                        if (shielded == (t[1] == 1 || t[2] == 1)) {
                            return false;
                        }
                    }
                    return true;
                })
                .run("result blink", () -> {
                    StringBuilder sb = new StringBuilder();
                    for (long[] t : shieldTries) {
                        sb.append(t[0]).append(t[1] == 0 && t[2] == 0 ? ":none " : ":hurt ");
                    }
                    results.add(String.format(Locale.ROOT, "Phase Blink %.2f blocks; damage by tick after it: %s", dist[0], sb.toString().trim()));
                })
                .press(ModKeyMappings.DASH)
                .waitTicks(5)
                .check("a second blink at once is refused", () -> CryptKit.server(srv -> server(srv, manta).blinkAt()) == at[1]);
        cooldown(steps, "the cooldown", MantaRules.BLINK_COOLDOWN, at);
        steps.press(mc.options.keyInventory)
                .waitUntil("the inventory opens", 40, () -> mc.screen instanceof MountScreen)
                .run("the fins out", () -> quickMove(((MountMenu) mc.player.containerMenu).tackSlot()))
                .waitTicks(3)
                .run("the Nebula Reins in", () -> quickMove(29 + hotbarSlotOf(Mounts.NEBULA_REINS.get())))
                .waitTicks(3)
                .run("close", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null)
                .check("Nebula Reins on", () -> CryptKit.server(srv -> server(srv, manta).wears(MountGear.NEBULA_REINS)))
                .look(180f, 0f)
                .run("remember the last blink", () -> at[1] = CryptKit.server(srv -> server(srv, manta).blinkAt()))
                .waitUntil("the reins' cooldown is surely past", 200, () -> mc.level.getGameTime() - at[1] > MantaRules.BLINK_COOLDOWN + 5)
                .press(ModKeyMappings.DASH)
                .waitUntil("a blink", 20, () -> CryptKit.server(srv -> server(srv, manta).blinkAt()) != at[1])
                .run("its time", () -> at[1] = CryptKit.server(srv -> server(srv, manta).blinkAt()));
        cooldown(steps, "the Nebula Reins' cooldown", MantaRules.blinkCooldown(true), at);
    }

    /** Presses dash every tick from 8 ticks before the cooldown ends: the first accepted press must come right at its end. */
    private void cooldown(Steps steps, String what, int ticks, long[] at) {
        long[] refused = {0};
        steps.waitUntil(what + ": almost over", 300, () -> mc.level.getGameTime() >= at[1] + ticks - 8)
                .waitUntil(what + ": pressing until it blinks", 30, () -> {
                    long now = CryptKit.server(srv -> server(srv, manta).blinkAt());
                    if (now != at[1]) {
                        return true;
                    }
                    click(ModKeyMappings.DASH);
                    CryptKit.set(ModKeyMappings.DASH, false);
                    refused[0]++;
                    return false;
                })
                .run(what + ": result", () -> {
                    long next = CryptKit.server(srv -> server(srv, manta).blinkAt());
                    results.add(String.format(Locale.ROOT, "%s: next blink %d ticks after the last (%d presses refused before it)",
                            what, next - at[1], refused[0] - 1));
                    at[0] = next - at[1];
                    at[1] = next;
                })
                .check(what + String.format(Locale.ROOT, ": %d ticks (the two clocks may differ by two)", ticks),
                        () -> at[0] >= ticks - 2 && at[0] <= ticks + 3);
    }

    private void mountedCombat(Steps steps) {
        int[] zombie = {-1};
        float[] mantaHealth = {0};
        steps.waitTicks(20)
                .run("Meridian in hand", () -> select(ModItems.MERIDIAN.get()))
                .look(0f, 0f)
                .run("a still zombie ahead in the air", () -> zombie[0] = CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    ServerLevel level = p.serverLevel();
                    Zombie z = new Zombie(level);
                    Vec3 a = p.position().add(Vec3.directionFromRotation(0f, p.getYRot()).scale(3.0)).add(0, -0.3, 0);
                    z.moveTo(a.x, a.y, a.z, p.getYRot() + 180f, 0f);
                    z.setNoAi(true);
                    z.setNoGravity(true);
                    z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
                    z.setHealth(400f);
                    z.setPersistenceRequired();
                    level.addFreshEntity(z);
                    return z.getId();
                }))
                .waitTicks(5)
                .run("aim", () -> {
                    CryptKit.aim(mc, mc.level.getEntity(zombie[0]).getBoundingBox().getCenter());
                    mantaHealth[0] = ridden().getHealth();
                    events.clear();
                })
                .press(mc.options.keyAttack)
                .waitTicks(12)
                .press(mc.options.keyAttack)
                .waitTicks(12)
                .press(mc.options.keyAttack)
                .waitTicks(16)
                .check("riding the manta: L1, L2, then L1 again", () -> started().equals(List.of(L1, L2, L1)))
                .check("the zombie was hit", () -> CryptKit.server(srv -> ((Zombie) CryptKit.player(srv).serverLevel().getEntity(zombie[0])).getHealth()) < 400f)
                .check("the manta was not", () -> ridden().getHealth() >= mantaHealth[0] - 0.01f)
                .run("result combat", () -> results.add("mounted on the manta: L1, L2, L1; zombie hit, manta unhurt"))
                .run("remove the zombie", () -> CryptKit.server(srv -> {
                    Entity z = CryptKit.player(srv).serverLevel().getEntity(zombie[0]);
                    if (z != null) {
                        z.discard();
                    }
                    return null;
                }));
    }

    private void looks(Steps steps) {
        steps.look(0f, 0f)
                .hold(mc.options.keyUp)
                .waitTicks(20)
                .run("camera beside the ridden manta", () -> cameraBeside(7.0, 3.0, 1.5, 90f))
                .waitTicks(1)
                .screenshot("manta_ridden")
                .run("camera behind, above", () -> cameraBeside(7.0, 0.0, 3.5, 160f))
                .waitTicks(1)
                .screenshot("manta_ridden_back")
                .run("the rider's own view", () -> {
                    uncamera();
                    mc.options.hideGui = true;
                })
                .waitTicks(2)
                .screenshot("manta_rider_view")
                .release(mc.options.keyUp)
                .run("camera off", this::uncamera)
                .waitTicks(20)
                .press(mc.options.keyShift)
                .waitUntil("off the manta", 20, () -> mc.player.getVehicle() == null)
                .run("hold it still, geared, in open sky (the Reach's islands shade much of the Drift)", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    DriftManta m = server(srv, manta);
                    Vec3 a = openSky(p.serverLevel(), p.position().add(0, 3.0, 0));
                    m.teleportTo(a.x, a.y, a.z);
                    m.setYRot(p.getYRot() + 90f);
                    m.setYBodyRot(p.getYRot() + 90f);
                    m.setNoAi(true);
                    return null;
                }))
                .waitTicks(10);
        for (String[] view : new String[][] {{"manta_side", "90"}, {"manta_front", "0"}, {"manta_back", "180"}, {"manta_34", "35"}, {"manta_above", "20"}}) {
            float turn = Float.parseFloat(view[1]);
            boolean above = view[0].equals("manta_above");
            steps.run("camera " + view[0], () -> {
                        DriftManta m = clientOrFail();
                        Vec3 fwd = Vec3.directionFromRotation(0f, m.yBodyRot);
                        Vec3 dir = fwd.yRot((float) Math.toRadians(-turn));
                        Vec3 mid = m.position().add(0, 0.4, 0);
                        uncamera();
                        camera = DevCamera.create(mc.level);
                        camera.place(mid.add(dir.scale(above ? 3.0 : 6.0)).add(0, above ? 6.0 : 1.2, 0), mid);
                        camera.use();
                        mc.options.hideGui = true;
                    })
                    .waitTicks(3)
                    .screenshot(view[0]);
        }
        steps.run("camera off", this::uncamera);
    }
}
