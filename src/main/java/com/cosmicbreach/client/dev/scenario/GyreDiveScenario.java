package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreModes;
import com.cosmicbreach.entity.gyre.GyreModes.Mode;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.VoidSafeDrops;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Gyre Knight's 1.1 changes in the Drift (Lane A, A3.4), part {@code gyre-dive}, on a test floor in the Drift's air
 * with a level 24 player on foot: a forced Dive tells (the rings swell, the blades glow gold), comes down beside the player
 * at their level, cuts once and hangs in sword reach with its core open; a second one is parried with the real parry key;
 * dives then come on their own after two or three Lance Volleys; a Knight stunned out over open air sinks only to the
 * player's level and never below it; and a Knight killed over open sky puts its drops and its experience in the killer's
 * pack and none falls.
 *
 * <p>A server tick sampler records the fight every tick (the Knight's mode, place and core, the player's place, whether the
 * Knight's body is within a sword's 3 blocks of the player's chest), so the log quotes measured numbers: the tell's length,
 * the drop, the cut, the ticks hanging in reach, and the Lance Volleys between one dive and the next.
 */
public final class GyreDiveScenario implements Scenario {
    /** One server tick of the fight as the server saw it. */
    private record Sample(long time, Mode mode, int t, int approachEnd, Vec3 knight, Vec3 player, boolean exposed, int dives, int lances,
                          boolean reach) {}

    /** What the samples say about one dive: ticks counted from the dive's first tick. */
    private record Dive(int number, long time, int tell, int drop, int cut, double damage, double shove, int exposed, int hang, int hangReach,
                        int reachRun, double flat, double up, int lancesSince) {
        String line() {
            return String.format(Locale.ROOT, "dive %d at tick %d: tell %d ticks (%.1f s), drop %d ticks, cut on tick %d (took %.1f, shoved me %.1f blocks), "
                            + "core open %d ticks, hung %d ticks (%.1f s) of which %d within %.1f blocks of my chest, within reach %d ticks in all (%.1f s); "
                            + "at the hang's middle %.1f blocks out and %.2f above my feet%s", number, time, tell, tell / 20.0, drop, cut, damage, shove, exposed, hang,
                    hang / 20.0, hangReach, SWORD_REACH, reachRun, reachRun / 20.0, flat, up,
                    number == 1 ? "" : ", " + lancesSince + " lance volley(s) since the dive before");
        }
    }

    /** The reach of a plain weapon swing, from the player's chest to the nearest point of the Knight's body. */
    private static final double SWORD_REACH = 3.0;
    /** A parry lasts 6 ticks: pressed this long before the cut lands, the cut falls in the middle of it. */
    private static final int PARRY_LEAD = 3;
    /** Natural dives to wait for: the first only starts the count, each of the others is measured against the one before. */
    private static final int NATURAL_DIVES = 5;

    private final List<String> summary = new ArrayList<>();
    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private final List<double[]> hits = new CopyOnWriteArrayList<>();
    private volatile long parryAt = Long.MAX_VALUE;
    private volatile boolean armParry;
    private BlockPos centre = BlockPos.ZERO;
    private Vec3 stand = Vec3.ZERO;
    private final double[] mark = new double[8];

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    private static @Nullable GyreKnight knight(ServerPlayer p) {
        GyreKnight best = null;
        double bestD = Double.MAX_VALUE;
        for (GyreKnight k : p.serverLevel().getEntitiesOfClass(GyreKnight.class, p.getBoundingBox().inflate(96.0), GyreKnight::isAlive)) {
            double d = k.distanceToSqr(p);
            if (d < bestD) {
                best = k;
                bestD = d;
            }
        }
        return best;
    }

    private static <T> T ask(Function<GyreKnight, T> query) {
        return ServerQuery.ask(p -> {
            GyreKnight k = knight(p);
            if (k == null) {
                throw new Steps.Failure("no Gyre Knight near the player");
            }
            return query.apply(k);
        });
    }

    private static boolean home(GyreKnight k) {
        return k.blade(0) == GyreKnight.Blade.ORBIT && k.blade(1) == GyreKnight.Blade.ORBIT && k.blade(2) == GyreKnight.Blade.ORBIT;
    }

    private static int cores(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModMaterials.GYRE_CORE.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void key(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    private static void click(KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
    }

    /** The test floor is small and a lance's knockback adds up over minutes: back to the spot if the player has been pushed off it. */
    private void keepStanding(Minecraft mc) {
        if (mc.player.position().distanceTo(stand) > 3.0) {
            ColossusScenario.tp(mc, stand.x, stand.y, stand.z, centre.getX() + 0.5, centre.getY() + 4, centre.getZ() + 0.5);
        }
    }

    // ------------------------------------------------------------------ the sampler

    /** Called every server tick (server thread): one {@link Sample}, and the parry's timing when a parry is armed. */
    private void sample(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GyreKnight k = knight(p);
            if (k == null) {
                continue;
            }
            long now = k.level().getGameTime();
            int t = (int) (now - k.modeStart());
            boolean reach = k.getBoundingBox().distanceToSqr(p.position().add(0, 1.0, 0)) <= SWORD_REACH * SWORD_REACH;
            samples.add(new Sample(now, k.mode(), t, k.approachEnd(), k.position(), p.position(), k.coreExposed(), k.dives(),
                    k.modesSeen()[Mode.LANCE.ordinal()], reach));
            if (armParry && k.mode() == Mode.DIVE && t == GyreModes.DIVE_TELL - 1) {
                // the next tick it picks where to drop (beside the player, at their level) and flies there at its dive speed;
                // the cut lands on the first tick it starts less than 0.6 from the spot
                Vec3 spot = GyreModes.standOff(p.position(), k.position());
                double d = spot.distanceTo(k.position());
                int ticks = d < 0.6 ? 0 : (int) Math.floor((d - 0.6) / GyreModes.DIVE_SPEED) + 1;
                armParry = false;
                parryAt = now + 1 + ticks - PARRY_LEAD;
            }
        }
    }

    /** Every dive the sampler saw, in the order they began, and what the samples say about each. */
    private List<Dive> dives() {
        List<Sample> s = new ArrayList<>(samples);
        List<Integer> starts = new ArrayList<>();
        for (int i = 1; i < s.size(); i++) {
            if (s.get(i).dives() > s.get(i - 1).dives()) {
                starts.add(i);
            }
        }
        List<Dive> out = new ArrayList<>();
        for (int n = 0; n < starts.size(); n++) {
            int a = starts.get(n);
            int b = a;
            while (b < s.size() && s.get(b).mode() == Mode.DIVE) {
                b++;
            }
            int cut = -1;
            int exposed = 0;
            for (int i = a; i < b; i++) {
                cut = Math.max(cut, s.get(i).approachEnd());
                exposed += s.get(i).exposed() ? 1 : 0;
            }
            int hang = 0;
            int hangReach = 0;
            for (int i = a; i < b; i++) {
                if (cut >= 0 && s.get(i).t() >= cut + GyreModes.DIVE_STRIKE) {
                    hang++;
                    hangReach += s.get(i).reach() ? 1 : 0;
                }
            }
            // the tell is the stillness before the drop: the first tick it moves faster than any tick of its station keeping (0.25 at most)
            int tell = -1;
            for (int i = a + 1; i < b; i++) {
                if (s.get(i).knight().distanceTo(s.get(i - 1).knight()) > 0.3) {
                    tell = i - a;
                    break;
                }
            }
            int m = Math.min(s.size() - 1, a + Math.max(0, cut) + GyreModes.DIVE_STRIKE + GyreModes.DIVE_HANG / 2);
            int lo = m;
            int hi = m;
            while (lo > 0 && s.get(lo - 1).reach()) {
                lo--;
            }
            while (hi < s.size() - 1 && s.get(hi + 1).reach()) {
                hi++;
            }
            double damage = 0;
            for (double[] h : hits) {
                if (Math.abs(h[0] - (s.get(a).time() + cut)) <= 1) {
                    damage += h[1];
                }
            }
            double shove = 0;
            if (cut >= 0 && a + cut < s.size()) {
                Vec3 at = s.get(a + cut).player();
                for (int i = a + cut; i < Math.min(s.size(), a + cut + 40); i++) {
                    shove = Math.max(shove, Math.hypot(s.get(i).player().x - at.x, s.get(i).player().z - at.z));
                }
            }
            Sample mid = s.get(m);
            out.add(new Dive(n + 1, s.get(a).time(), tell, cut - GyreModes.DIVE_TELL, cut, damage, shove, exposed, hang, hangReach, mid.reach() ? hi - lo + 1 : 0,
                    Math.hypot(mid.knight().x - mid.player().x, mid.knight().z - mid.player().z), mid.knight().y - mid.player().y,
                    n == 0 ? s.get(a).lances() : s.get(a).lances() - s.get(starts.get(n - 1)).lances()));
        }
        return out;
    }

    /** The first dive tick by tick, every second tick: its mode and clock, its height over my feet, how far out it is, whether it is in reach. */
    private String trace() {
        List<Sample> s = new ArrayList<>(samples);
        StringBuilder out = new StringBuilder("tick by tick (every second tick):");
        int a = -1;
        for (int i = 1; i < s.size() && a < 0; i++) {
            if (s.get(i).dives() > s.get(i - 1).dives()) {
                a = i;
            }
        }
        for (int i = Math.max(0, a); a >= 0 && i < Math.min(s.size(), a + 120); i += 2) {
            Sample x = s.get(i);
            out.append(String.format(Locale.ROOT, "%n  +%d %s t%d cut@%d up %.2f out %.2f me@%.2f,%.2f%s%s", i - a, x.mode(), x.t(), x.approachEnd(),
                    x.knight().y - x.player().y, Math.hypot(x.knight().x - x.player().x, x.knight().z - x.player().z), x.player().x - s.get(a).player().x,
                    x.player().z - s.get(a).player().z, x.exposed() ? " open" : "", x.reach() ? " reach" : ""));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ the scenario

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> sample(event.getServer()));
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p && event.getSource().getEntity() instanceof GyreKnight && event.getNewDamage() > 0f) {
                hits.add(new double[] {p.level().getGameTime(), event.getNewDamage()});
            }
        });
        steps.command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the asteroid is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .run("a test floor out in the Drift's air", () -> centre = ServerQuery.ask(p -> {
                    BlockPos c = p.blockPosition().offset(0, 14, 0);
                    var level = p.serverLevel();
                    for (int x = -16; x <= 16; x++) {
                        for (int z = -16; z <= 16; z++) {
                            for (int y = 0; y <= 18; y++) {
                                level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                            }
                            level.setBlock(c.offset(x, -1, z), com.cosmicbreach.registry.ModBlocks.DRIFTSTONE_BRICKS.get().defaultBlockState(), 2);
                        }
                    }
                    return c;
                }))
                .run("stand on it", () -> {
                    stand = new Vec3(centre.getX() + 0.5, centre.getY(), centre.getZ() - 4.5);
                    ColossusScenario.tp(mc, stand.x, stand.y, stand.z, centre.getX() + 0.5, centre.getY() + 4, centre.getZ() + 0.5);
                })
                .waitTicks(10);
        LeviathanScenario helper = new LeviathanScenario(LeviathanScenario.Part.LOOKS);
        helper.equip(steps, mc);
        steps.command("effect give @s minecraft:resistance 100000 3 true")
                .command("effect give @s minecraft:regeneration 100000 1 true")
                .command("cosmicbreach debug gyre spawn")
                .waitUntil("a Knight", 40, () -> ServerQuery.ask(p -> knight(p) != null))
                .command("cosmicbreach debug gyre hold 100000")
                .waitUntil("it targets the player", 80, () -> ServerQuery.ask(p -> knight(p) != null && knight(p).getTarget() == p));

        forcedDive(steps, mc);
        parriedDive(steps, mc);
        naturalDives(steps, mc);
        stunOverOpenAir(steps, mc);
        killOverOpenSky(steps);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ a dive on command

    private void forcedDive(Steps steps, Minecraft mc) {
        steps.waitUntil("it keeps its Shield Orbit with its blades home", 300, () -> ask(k -> k.mode() == Mode.SHIELD && home(k)))
                .command("cosmicbreach debug gyre mode dive")
                .waitUntil("the tell begins", 30, () -> ask(k -> k.mode() == Mode.DIVE))
                .waitUntil("the tell is under way", 30, () -> ask(k -> k.level().getGameTime() - k.modeStart() >= GyreModes.DIVE_TELL - 7))
                .run("face it", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("gyre_dive_tell")
                .waitUntil("it comes down beside me and hangs there", GyreModes.DIVE_TELL + GyreModes.DIVE_DESCENT_MAX + GyreModes.DIVE_STRIKE + 20,
                        () -> ask(k -> k.mode() == Mode.DIVE && k.coreExposed()))
                .waitTicks(8)
                .run("where it hangs, eight ticks into the hang", () -> mark[0] = ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    mark[1] = Math.hypot(k.getX() - p.getX(), k.getZ() - p.getZ());
                    mark[4] = k.getY() - p.getY();
                    mark[5] = Math.sqrt(k.getBoundingBox().distanceToSqr(p.position().add(0, 1.0, 0)));
                    return k.mode() == Mode.DIVE && k.coreExposed() ? 1.0 : 0.0;
                }))
                .log("hanging", () -> String.format(Locale.ROOT, "eight ticks into the hang: in the dive with its core open %s, %.1f blocks out, %.2f above my feet, "
                        + "%.1f blocks from my chest to its body", mark[0] == 1.0, mark[1], mark[4], mark[5]))
                .check("at my level, in sword reach, its core open", () -> mark[0] == 1.0 && Math.abs(mark[4]) < 1.0 && mark[1] <= GyreModes.DIVE_REACH
                        && mark[5] <= SWORD_REACH)
                .run("face it", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("gyre_dive_hang")
                .waitUntil("it rises again", GyreModes.DIVE_HANG + 40, () -> ask(k -> k.mode() != Mode.DIVE))
                .waitTicks(70)
                .log("the first dive", () -> {
                    List<Dive> d = dives();
                    String r = d.isEmpty() ? "no dive seen" : d.get(0).line();
                    summary.add(r);
                    return r;
                })
                .log("the first dive, tick by tick", this::trace)
                .check("one dive, its tell about a second, the hang about two seconds and all of it in sword reach", () -> {
                    List<Dive> d = dives();
                    return d.size() == 1 && d.get(0).tell >= 18 && d.get(0).tell <= 24 && d.get(0).hang >= 36 && d.get(0).hang <= 44
                            && d.get(0).hangReach >= d.get(0).hang - 2 && d.get(0).exposed >= d.get(0).hang;
                });
    }

    // ------------------------------------------------------------------ a dive parried

    private void parriedDive(Steps steps, Minecraft mc) {
        int[] hitsBefore = {0};
        int[] broken = {0};
        steps.waitUntil("it keeps its Shield Orbit with its blades home", 300, () -> ask(k -> k.mode() == Mode.SHIELD && home(k)))
                .run("back to my spot, and arm the parry's timer", () -> {
                    keepStanding(mc);
                    hitsBefore[0] = hits.size();
                    broken[0] = ask(k -> k.counts()[0]);
                    parryAt = Long.MAX_VALUE;
                    armParry = true;
                })
                .waitTicks(5)
                .command("cosmicbreach debug gyre mode dive")
                .waitUntil("the cut is about to land", GyreModes.DIVE_TELL + GyreModes.DIVE_DESCENT_MAX + 30, () -> mc.level.getGameTime() >= parryAt)
                .run("face it and parry", () -> {
                    ColossusScenario.lookAt(mc, ask(GyreKnight::core));
                    mark[6] = mc.level.getGameTime();
                    click(ModKeyMappings.PARRY);
                })
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.PARRY, false))
                .waitUntil("the dive is over", 140, () -> ask(k -> k.mode() != Mode.DIVE))
                .log("parry", () -> ask(k -> {
                    List<Dive> d = dives();
                    Dive last = d.get(d.size() - 1);
                    String r = String.format(Locale.ROOT, "parried dive: pressed on tick %d (planned %d), the cut landed on tick %d; blades broken %d -> %d, hits taken %d",
                            (long) mark[6], parryAt, last.time() + last.cut(), broken[0], k.counts()[0], hits.size() - hitsBefore[0]);
                    summary.add(r);
                    return r;
                }))
                .check("the parry broke a blade and the cut did not land", () -> ask(k -> k.counts()[0] > broken[0]) && hits.size() == hitsBefore[0]);
    }

    // ------------------------------------------------------------------ dives of its own

    private void naturalDives(Steps steps, Minecraft mc) {
        steps.waitUntil("it keeps its Shield Orbit with its blades home", 300, () -> ask(k -> k.mode() == Mode.SHIELD && home(k)))
                .run("back to my spot", () -> keepStanding(mc))
                .command("cosmicbreach debug gyre hold 0")
                .command("cosmicbreach debug gyre cooldowns")
                .run("count the dives so far", () -> mark[3] = ask(GyreKnight::dives))
                .waitUntil(NATURAL_DIVES + " dives of its own, after two or three volleys each", 6000, () -> {
                    int[] now = ask(k -> new int[] {k.dives(), k.mode() == Mode.DIVE ? 1 : 0});
                    if (now[1] == 0) {
                        keepStanding(mc);
                    }
                    return now[0] >= mark[3] + NATURAL_DIVES;
                })
                .waitUntil("the last dive is over", 200, () -> ask(k -> k.mode() != Mode.DIVE))
                .waitTicks(70)
                .log("dives of its own", () -> {
                    List<Dive> d = dives();
                    StringBuilder r = new StringBuilder("dives of its own, from dive " + (int) (mark[3] + 1) + " on:");
                    List<Integer> between = new ArrayList<>();
                    for (int i = (int) mark[3]; i < d.size(); i++) {
                        r.append("\n  ").append(d.get(i).line());
                        summary.add(d.get(i).line());
                        if (i > mark[3]) {
                            between.add(d.get(i).lancesSince);
                        }
                    }
                    String volleys = "lance volleys between one of its own dives and the next: " + between;
                    summary.add(volleys);
                    return r.append("\n  ").append(volleys).toString();
                })
                .check("each of the dives after the first of them came two or three volleys after the one before", () -> {
                    List<Dive> d = dives();
                    int from = (int) mark[3];
                    if (d.size() < from + NATURAL_DIVES) {
                        return false;
                    }
                    for (int i = from + 1; i < d.size(); i++) {
                        int n = d.get(i).lancesSince;
                        if (n < GyreModes.VOLLEYS_MIN || n > GyreModes.VOLLEYS_MAX) {
                            return false;
                        }
                    }
                    return true;
                })
                .check("and each held its tell and its hang in reach", () -> {
                    List<Dive> d = dives();
                    for (int i = (int) mark[3]; i < d.size(); i++) {
                        Dive x = d.get(i);
                        if (x.tell < 18 || x.tell > 24 || x.hang < 36 || x.hang > 44 || x.hangReach < x.hang - 2) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    // ------------------------------------------------------------------ a stun out over open air

    /** A corridor of open air at the player's level and a shaft of it below, out beside the floor; returns the spot over the shaft. */
    private BlockPos openSky(ServerPlayer p) {
        var level = p.serverLevel();
        int sz = (int) Math.floor(stand.z);
        for (int x = 17; x <= 36; x++) {
            for (int z = sz - 6; z <= sz + 6; z++) {
                for (int y = 0; y <= 14; y++) {
                    level.setBlock(new BlockPos(centre.getX() + x, centre.getY() + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        BlockPos at = new BlockPos(centre.getX() + 27, centre.getY() + 8, sz);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -VoidSafeDrops.VOID_DEPTH - 3; dy <= 0; dy++) {
                    level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return at;
    }

    private void stunOverOpenAir(Steps steps, Minecraft mc) {
        long[] from = {0};
        BlockPos[] spot = {BlockPos.ZERO};
        steps.command("cosmicbreach debug gyre hold 100000")
                .waitUntil("it keeps its Shield Orbit with its blades home", 300, () -> ask(k -> k.mode() == Mode.SHIELD && home(k)))
                .run("back to my spot", () -> keepStanding(mc))
                .run("open air out beside the floor", () -> spot[0] = ServerQuery.ask(this::openSky))
                .run("the Knight out over it, eight blocks up", () -> ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    k.teleportTo(spot[0].getX() + 0.5, spot[0].getY(), spot[0].getZ() + 0.5);
                    k.setDeltaMovement(Vec3.ZERO);
                    from[0] = p.level().getGameTime();
                    return true;
                }))
                .command("cosmicbreach debug gyre mode stunned")
                .waitUntil("it is stunned", 20, () -> ask(k -> k.mode() == Mode.STUNNED))
                .waitTicks(GyreModes.STUN - 8)
                .log("stun", () -> {
                    double minRel = Double.MAX_VALUE;
                    double endRel = Double.NaN;
                    double startRel = Double.NaN;
                    int ticks = 0;
                    for (Sample x : new ArrayList<>(samples)) {
                        if (x.mode() == Mode.STUNNED && x.time() >= from[0]) {
                            double rel = x.knight().y - x.player().y;
                            minRel = Math.min(minRel, rel);
                            endRel = rel;
                            startRel = Double.isNaN(startRel) ? rel : startRel;
                            ticks++;
                        }
                    }
                    mark[4] = minRel;
                    mark[5] = endRel;
                    mark[6] = ticks;
                    String r = String.format(Locale.ROOT, "stun over open air: %d ticks, it began %.1f above my level, ended %.2f above it, lowest %.2f", ticks, startRel,
                            endRel, minRel);
                    summary.add(r);
                    return r;
                })
                .check("stunned over open air, it sank to my level and never below it", () -> mark[6] >= GyreModes.STUN - 12 && mark[4] >= -0.1
                        && mark[5] <= 0.6 && mark[5] >= -0.1);
    }

    // ------------------------------------------------------------------ a kill over open sky

    private void killOverOpenSky(Steps steps) {
        int[] before = {0};
        int[] xpBefore = {0};
        steps.waitUntil("it is back in its Shield Orbit", 200, () -> ask(k -> k.mode() == Mode.SHIELD))
                .run("the Knight over the shaft of open sky, and a hit that kills it", () -> ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    k.teleportTo(centre.getX() + 27.5, centre.getY() + 8, Math.floor(stand.z) + 0.5);
                    k.setDeltaMovement(Vec3.ZERO);
                    if (VoidSafeDrops.groundDepth(p.serverLevel(), k.blockPosition()) >= 0) {
                        throw new Steps.Failure("there is ground under the test spot: " + VoidSafeDrops.groundDepth(p.serverLevel(), k.blockPosition()));
                    }
                    before[0] = cores(p);
                    xpBefore[0] = p.totalExperience;
                    k.hurt(p.damageSources().playerAttack(p), 1000f);
                    return true;
                }))
                .waitTicks(10)
                .log("void kill", () -> ServerQuery.ask(p -> {
                    AABB around = new AABB(centre.getX() + 27, centre.getY() - 100, Math.floor(stand.z), centre.getX() + 28, centre.getY() + 30, Math.floor(stand.z) + 1)
                            .inflate(30, 0, 30);
                    boolean falling = !p.serverLevel().getEntitiesOfClass(ItemEntity.class, around, e -> true).isEmpty();
                    boolean orbs = !p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, around).isEmpty();
                    int gained = p.totalExperience - xpBefore[0];
                    String r = String.format(Locale.ROOT, "void kill: Gyre Cores in my pack %d -> %d, experience +%d%s%s", before[0], cores(p), gained,
                            falling ? ", an item still lying out there" : ", no item fell", orbs ? ", an orb of experience fell" : ", no orb fell");
                    summary.add(r);
                    mark[7] = (cores(p) > before[0] ? 1 : 0) + (falling ? 10 : 0) + (orbs ? 100 : 0) + (gained == 15 ? 1000 : 0);
                    return r;
                }))
                .check("its Gyre Core went into my pack and its experience straight to me, and nothing fell", () -> mark[7] == 1001);
    }
}
