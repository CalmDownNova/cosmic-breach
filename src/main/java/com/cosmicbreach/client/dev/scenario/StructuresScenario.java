package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.structure.ReceptorHum;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.structure.Guards;
import com.cosmicbreach.structure.StructureCommands;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.array.LensPieceItem;
import com.cosmicbreach.structure.gen.ObservatoryLayout;
import com.cosmicbreach.structure.gen.ObservatoryPiece;
import com.cosmicbreach.structure.gen.ReliquaryLayout;
import com.cosmicbreach.structure.gen.ReliquaryPiece;
import com.cosmicbreach.structure.lens.BeamTrace;
import com.cosmicbreach.structure.lens.Lens;
import com.cosmicbreach.structure.lens.LensSolver;
import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.KineticTripwires;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.ReachIslands;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The first dungeons (W5), in Aetheria, in daylight:
 *
 * <ol>
 *   <li>RELIQUARY: a Spire Reliquary built on a Shattered Spires island (timed); its outside from the air, the
 *       entrance hall, a guard hall (its Shardling pack appears), the ramp; the Lens room wakes (timed) and is
 *       seen before; the hint's timer shortened to 3 s and its ghost seen; then a bot solves the 5 by 5 with real
 *       input only: use and sneak-use on mirrors, a loose mirror lifted bare-handed and set down, an Umbral block
 *       knocked by the Comet Maul's charged hit. It re-plans with the solver after every move, first sends the
 *       light into the Warden Eye once (two Shardlings must come), and logs every receptor's hum pitch against
 *       the beam's distance. The room after; the vault opened by the player (loot, XP) and by a second, fake
 *       player (their own share), and nothing more for either.</li>
 *   <li>OBSERVATORY: a Gyre Observatory in the Drift (timed); outside, a floor inside; a Kinetic Tripwire walked
 *       (thread shown, no bolt, no damage) and sprinted (thread hidden, bolts, damage 4 x speed ratio,
 *       measured); a gravity lift ridden to the next floor (timed); the Gyre Knight's post waking a Gyre Knight
 *       (removed again so the puzzle is solved in peace); the telescope chamber's 7 by 7 solved by the same bot; its vault.</li>
 * </ol>
 * Trace costs (microseconds, per move) and every timing go to the log.
 */
public final class StructuresScenario implements Scenario {
    public enum Part { RELIQUARY, OBSERVATORY }

    /**
     * Structure seeds whose puzzles make the bot use every verb (turns both ways, a carry, a knock, the Eye): a
     * medium 5 by 5 (at least 7 moves) and a hard 7 by 7 (three receptors, two splitters, at least 10 moves).
     */
    static final long RELIQUARY_SEED = 3L;
    static final long OBSERVATORY_SEED = 13L;

    private final EnumSet<Part> parts;

    public StructuresScenario() {
        this.parts = EnumSet.allOf(Part.class);
    }

    public StructuresScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return parts.size() == 2 ? 900 : 480;
    }

    /** What the steps learn as they go. */
    private static final class State {
        ReliquaryPiece reliquary;
        Vec3 placedFrom;
        ObservatoryPiece observatory;
        BlockPos core;
        long placeMillis;
        long nearAt;
        long wokeAt;
        long xpBefore;
        long solveStart;
        final List<Long> traceNanos = new ArrayList<>();
        final List<String> hum = new ArrayList<>();
        float humFirst = Float.NaN;
        float humLast = Float.NaN;
        int humChecks;
        boolean eyeWoke;
        int eyeShardlings;
        int moves;
        int turnsCw;
        int turnsCcw;
        int carries;
        int knocks;
        float healthBefore;
        double[] firedBefore;
        long sprintShown;
        long walkShown;
        BlockPos emitter;
        long liftStart;
        final List<String> results = new ArrayList<>();
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("cosmicbreach give comet_maul")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0 400 0")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL);
        if (parts.contains(Part.RELIQUARY)) {
            reliquary(steps, mc, s);
            natural(steps, mc, s, true);
        }
        if (parts.contains(Part.OBSERVATORY)) {
            observatory(steps, mc, s);
            natural(steps, mc, s, false);
        }
        // Stopping the server while chunk generation is still queued can hang it (the game then waits for the watchdog), and the
        // last view above asks for a wide ring of fresh chunks: shrink the view and let the loaded chunks settle first.
        steps.run("a small view", () -> mc.options.renderDistance().set(2))
                .waitUntil("the chunks settle", 1200, chunksSettled(mc));
        steps.log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    /** True once the Drift's server level has held the same number of chunks for 100 ticks. */
    private static BooleanSupplier chunksSettled(Minecraft mc) {
        long[] last = {-1, 0};
        return () -> {
            long loaded = server(srv -> srv.getLevel(AetheriaWorld.LEVEL).getChunkSource().getLoadedChunksCount());
            long now = mc.level.getGameTime();
            if (loaded != last[0]) {
                last[0] = loaded;
                last[1] = now;
                return false;
            }
            return now - last[1] >= 100;
        };
    }

    // ------------------------------------------------------------------ the Reliquary

    private void reliquary(Steps steps, Minecraft mc, State s) {
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1200))
                .run("face the island's open middle", () -> server(srv -> {
                    ServerPlayer p = player(srv);
                    p.setYRot(openYaw(p));
                    return null;
                }))
                .run("build a Spire Reliquary ahead", () -> {
                    long t0 = System.nanoTime();
                    s.reliquary = server(srv -> (ReliquaryPiece) StructureCommands.placeAhead(player(srv), true, RELIQUARY_SEED));
                    s.placedFrom = server(srv -> player(srv).position());
                    s.placeMillis = (System.nanoTime() - t0) / 1_000_000;
                    s.core = s.reliquary.core();
                    s.results.add("reliquary built in " + s.placeMillis + " ms at " + s.reliquary.origin().toShortString() + ": "
                            + s.reliquary.plan().chambers() + " chambers, " + s.reliquary.plan().difficulty().name());
                })
                .command("gamemode spectator")
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .waitTicks(5)
                .run("to a viewpoint in the air", () -> {
                    BlockPos o = s.reliquary.origin();
                    Vec3 eye = Vec3.atCenterOf(o).add(toward(s.placedFrom, o).scale(46)).add(0, 26, 0);
                    tp(mc, eye, Vec3.atCenterOf(o).add(0, 18, 0));
                })
                .waitUntil("the view is drawn", 1600, settled(mc, 1200))
                .screenshot("reliquary_exterior")
                .run("the usual view", () -> mc.options.renderDistance().set(8))
                .command("gamemode creative")
                .waitTicks(5)
                .run("fly", () -> fly(mc))
                .run("into the entrance hall", () -> {
                    BlockPos o = s.reliquary.origin();
                    tp(mc, Vec3.atCenterOf(o).add(-6, 2.0, 0), Vec3.atCenterOf(o).add(0, 1, 0));
                })
                .waitUntil("the hall is drawn", 600, settled(mc, 400))
                .screenshot("reliquary_entrance_hall")
                .run("into the first guard hall", () -> {
                    BlockPos o = s.reliquary.origin();
                    tp(mc, Vec3.atCenterOf(o).add(0, ReliquaryLayout.STOREY + 3.5, 7), Vec3.atCenterOf(o).add(0, ReliquaryLayout.STOREY, 0));
                })
                .waitUntil("the hall's pack arrived", 200, () -> server(srv -> shardlingsNear(srv, s.reliquary.origin(), 40)) >= 3)
                .waitUntil("the hall is drawn", 600, settled(mc, 400))
                .screenshot("reliquary_guard_hall")
                .run("result", () -> s.results.add("guard hall: " + server(srv -> shardlingsNear(srv, s.reliquary.origin(), 40))
                        + " Shardlings came when a player came near"))
                .run("onto the ramp", () -> {
                    ReliquaryLayout plan = s.reliquary.plan();
                    double a = plan.entryAngle() + Math.PI / 3;
                    double h = plan.ramp(0, Math.PI / 3);
                    Vec3 at = Vec3.atBottomCenterOf(s.reliquary.origin()).add(Math.cos(a) * 10.5, h + 1.8, Math.sin(a) * 10.5);
                    double b = a + 0.5;
                    tp(mc, at, Vec3.atBottomCenterOf(s.reliquary.origin()).add(Math.cos(b) * 10.5, plan.ramp(0, Math.PI / 3 + 0.5) + 1.2,
                            Math.sin(b) * 10.5));
                })
                .waitUntil("the ramp is drawn", 600, settled(mc, 400))
                .screenshot("reliquary_ramp")
                .command("kill @e[type=cosmicbreach:shardling]");
        walkRamp(steps, mc, s);
        toLensView(steps, mc, s, 5.5, 5.4)
                .check("the Lens Array woke as the player climbed near", () -> server(srv -> core(srv, s).phase()) == LensCoreBlockEntity.ACTIVE)
                .run("result", () -> s.results.add("reliquary Lens Array: " + server(srv -> wakeReport(core(srv, s)))))
                .waitUntil("the beams reached this client", 100, () -> clientCore(mc, s) != null && clientCore(mc, s).segments().length > 0)
                .run("above the grid, inside the ceiling", () -> topView(mc, s))
                .waitUntil("the room is drawn", 600, settled(mc, 400))
                .screenshot("reliquary_lens_before")
;
        toLensView(steps, mc, s, 5.5, 5.4);
        hint(steps, mc, s, "reliquary");
        solve(steps, mc, s, "reliquary");
        vault(steps, mc, s, "reliquary", () -> s.reliquary.vault(), 150);
    }

    /**
     * From just inside the entrance, on foot (creative, sprinting), up the spiral ramp to the Lens room's door:
     * the ramp must be climbable all the way (half-block steps only). Steers along the gallery's middle each tick.
     * Also notes when the player first came within 24 blocks of the Lens Array and when it woke.
     */
    private void walkRamp(Steps steps, Minecraft mc, State s) {
        long[] start = {0};
        int[] stuck = {0};
        double[] last = {0, 0, 0};
        steps.command("gamemode creative")
                .run("on foot, just inside the entrance", () -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                    select(mc, 8);
                    ReliquaryLayout plan = s.reliquary.plan();
                    double a = plan.entryAngle() + 2 * Math.PI - 0.3;
                    Vec3 o = Vec3.atBottomCenterOf(s.reliquary.origin());
                    Vec3 at = o.add(Math.cos(a) * 10.5, 0.0, Math.sin(a) * 10.5);
                    tp(mc, at, at.add(-Math.sin(a), 1.6, Math.cos(a)));
                    s.nearAt = 0;
                    s.wokeAt = 0;
                })
                .waitTicks(10)
                .run("mark", () -> start[0] = mc.level.getGameTime())
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("walked up the ramp into the Lens room", 2400, () -> {
                    ReliquaryLayout plan = s.reliquary.plan();
                    Vec3 o = Vec3.atBottomCenterOf(s.reliquary.origin());
                    double dx = mc.player.getX() - o.x;
                    double dz = mc.player.getZ() - o.z;
                    double r = Math.hypot(dx, dz);
                    double a = Math.atan2(dz, dx);
                    double top = o.y + plan.floorY(plan.lensChamber()) + 1;
                    long now = mc.level.getGameTime();
                    if (s.nearAt == 0 && mc.player.position().distanceTo(Vec3.atCenterOf(s.core)) <= 24) {
                        s.nearAt = now;
                    }
                    LensCoreBlockEntity c = clientCore(mc, s);
                    if (s.wokeAt == 0 && c != null && c.phase() == LensCoreBlockEntity.ACTIVE) {
                        s.wokeAt = now;
                    }
                    double hx;
                    double hz;
                    double phi = plan.phase((int) Math.floor(dx), (int) Math.floor(dz));
                    if (now % 200 == 0) {
                        com.cosmicbreach.CosmicBreach.LOGGER.info(String.format(Locale.ROOT, "[structures] ramp t=%d at y %.1f (top %.1f), radius %.1f, phase %.2f",
                                now - start[0], mc.player.getY(), top, r, phi));
                    }
                    if (mc.player.getY() >= top - 0.1 && (phi < 0.4 || phi > 2 * Math.PI - 0.05)) {
                        // on the top landing at the door: in, toward the middle
                        hx = -dx;
                        hz = -dz;
                    } else {
                        hx = -Math.sin(a) + (10.5 - r) * Math.cos(a) * 0.6;
                        hz = Math.cos(a) + (10.5 - r) * Math.sin(a) * 0.6;
                    }
                    float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-hx, hz)));
                    mc.player.setYRot(yaw);
                    mc.player.yRotO = yaw;
                    mc.player.setXRot(10);
                    double moved = Math.hypot(mc.player.getX() - last[0], mc.player.getZ() - last[2]) + Math.abs(mc.player.getY() - last[1]);
                    stuck[0] = moved < 0.02 ? stuck[0] + 1 : 0;
                    last[0] = mc.player.getX();
                    last[1] = mc.player.getY();
                    last[2] = mc.player.getZ();
                    if (stuck[0] > 40) {
                        throw new Steps.Failure(String.format(Locale.ROOT, "stuck on the ramp at %.1f %.1f %.1f (radius %.1f)",
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(), r));
                    }
                    return mc.player.getY() >= top - 0.1 && r < 7.5;
                })
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "reliquary ramp walked (sprinting) from the entrance to the Lens room in %d ticks, %d turns",
                        mc.level.getGameTime() - start[0], s.reliquary.plan().chambers() - 1)));
    }

    // ------------------------------------------------------------------ the Observatory

    private void observatory(Steps steps, Minecraft mc, State s) {
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto drift")
                .waitUntil("the asteroid is drawn", 1600, settled(mc, 1200))
                .run("build a Gyre Observatory ahead", () -> {
                    long t0 = System.nanoTime();
                    s.observatory = server(srv -> (ObservatoryPiece) StructureCommands.placeAhead(player(srv), false, OBSERVATORY_SEED));
                    s.placedFrom = server(srv -> player(srv).position());
                    s.placeMillis = (System.nanoTime() - t0) / 1_000_000;
                    s.core = s.observatory.core();
                    s.results.add("observatory built in " + s.placeMillis + " ms at " + s.observatory.origin().toShortString() + ": "
                            + s.observatory.plan().floors() + " floors, " + s.observatory.plan().difficulty().name());
                })
                .command("gamemode spectator")
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .waitTicks(5)
                .run("to a viewpoint in the air", () -> {
                    BlockPos o = s.observatory.origin();
                    Vec3 eye = Vec3.atCenterOf(o).add(toward(s.placedFrom, o).scale(66)).add(0, 40, 0);
                    tp(mc, eye, Vec3.atCenterOf(o).add(0, 30, 0));
                })
                .waitUntil("the view is drawn", 1600, settled(mc, 1200))
                .screenshot("observatory_exterior")
                .run("the usual view", () -> mc.options.renderDistance().set(8))
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "observatory seen from %.0f %.0f %.0f (its asteroid's middle %s)",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(), s.observatory.origin().toShortString())))
                .command("gamemode creative")
                .waitTicks(5)
                .run("fly", () -> fly(mc))
                .run("inside the second floor, by its wall", () -> {
                    ObservatoryLayout plan = s.observatory.plan();
                    Vec3 o = Vec3.atBottomCenterOf(s.observatory.origin());
                    Direction side = plan.lift().getCounterClockWise();
                    Vec3 at = o.add(side.getStepX() * 6.5, plan.floorY(1) + 2.2, side.getStepZ() * 6.5);
                    select(mc, 8);
                    tp(mc, at, o.add(-side.getStepX() * 3, plan.floorY(1) + 0.8, -side.getStepZ() * 3));
                })
                .waitUntil("the floor is drawn", 600, settled(mc, 400))
                .screenshot("observatory_floor");
        tripwire(steps, mc, s);
        lift(steps, mc, s);
        steps.command("gamemode creative")
                .run("mark", () -> s.nearAt = mc.level.getGameTime())
                .command("gamemode creative")
                .waitTicks(4)
                .run("into the telescope chamber, high by the wall", () -> lensView(mc, s, 7.5, 6.6))
                .waitUntil("the Lens Array woke", 400, () -> server(srv -> core(srv, s).phase()) == LensCoreBlockEntity.ACTIVE)
                .run("result", () -> s.results.add("observatory Lens Array: " + server(srv -> wakeReport(core(srv, s)))))
                .waitUntil("the beams reached this client", 100, () -> clientCore(mc, s) != null && clientCore(mc, s).segments().length > 0)
                .run("above the grid, inside the ceiling", () -> topView(mc, s))
                .waitUntil("the room is drawn", 600, settled(mc, 400))
                .screenshot("observatory_lens_before")
                .command("gamemode creative")
                .waitTicks(4)
                .run("back into the room", () -> lensView(mc, s, 7.5, 6.6))
                .waitUntil("the Gyre Knight's post woke a Gyre Knight in the telescope chamber", 100, () -> Guards.knightRegistered()
                        && server(srv -> {
                            BlockPos post = s.observatory.origin().offset(0, s.observatory.plan().floorY(s.observatory.plan().top()) + 5, 0);
                            ServerLevel level = srv.getLevel(AetheriaWorld.LEVEL);
                            return !level.getBlockState(post).is(StructureRegistry.GUARD_MARKER.get())
                                    && !level.getEntitiesOfClass(com.cosmicbreach.entity.gyre.GyreKnight.class, new AABB(post).inflate(24.0)).isEmpty();
                        }))
                .run("result", () -> s.results.add("observatory: the Knight post woke a Gyre Knight"))
                .command("kill @e[type=cosmicbreach:gyre_knight]");
        solve(steps, mc, s, "observatory");
        vault(steps, mc, s, "observatory", () -> s.observatory.vault(), 400);
    }

    /** Walk across floor 1's thread (safe, shown), then sprint back (bolts, measured damage, hidden). */
    private void tripwire(Steps steps, Minecraft mc, State s) {
        int[] tick = {0};
        steps.command("gamemode survival")
                .command("gamerule naturalRegeneration false")
                .run("to one side of floor 1's thread, facing across it", () -> {
                    ObservatoryPiece o = s.observatory;
                    ObservatoryLayout plan = o.plan();
                    Direction toward = plan.lift().getOpposite();
                    Vec3 base = Vec3.atBottomCenterOf(o.origin()).add(0, plan.floorY(1), 0);
                    Vec3 from = base.add(-toward.getStepX() * 3.5, 0, -toward.getStepZ() * 3.5);
                    tp(mc, from, from.add(toward.getStepX() * 5, 1.6, toward.getStepZ() * 5));
                    Direction across = plan.lift().getClockWise();
                    s.emitter = o.origin().offset(across.getStepX() * 9, plan.floorY(1), across.getStepZ() * 9);
                })
                .waitUntil("the floor is drawn", 400, settled(mc, 300))
                .run("mark", () -> {
                    s.healthBefore = mc.player.getHealth();
                    s.firedBefore = KineticTripwires.lastFired();
                    s.walkShown = 0;
                    tick[0] = 0;
                })
                .hold(mc.options.keyUp)
                .waitUntil("walked over the thread", 60, () -> {
                    tick[0]++;
                    Long shown = com.cosmicbreach.client.structure.ThreadRenderer.shownAt(s.emitter);
                    if (shown != null && shown >= mc.level.getGameTime() - 1) {
                        s.walkShown++;
                    }
                    return tick[0] >= 40;
                })
                .release(mc.options.keyUp)
                .waitTicks(10)
                .check("walking over the thread fired nothing and hurt no one", () -> KineticTripwires.lastFired()[2] == s.firedBefore[2]
                        && mc.player.getHealth() >= s.healthBefore)
                .check("walking near it, the thread was shown", () -> s.walkShown > 0)
                .run("turn round", () -> mc.player.setYRot(mc.player.getYRot() + 180))
                .run("mark", () -> {
                    s.healthBefore = server(srv -> player(srv).getHealth());
                    s.sprintShown = 0;
                    tick[0] = 0;
                })
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("sprinted over the thread", 60, () -> {
                    tick[0]++;
                    double speed = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
                    Long shown = com.cosmicbreach.client.structure.ThreadRenderer.shownAt(s.emitter);
                    if (mc.player.isSprinting() && speed > KineticRules.WALK_SPEED * 1.2 && shown != null
                            && shown >= mc.level.getGameTime()) {
                        s.sprintShown++;
                    }
                    return tick[0] >= 30;
                })
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .waitTicks(12)
                .check("sprinting over the thread fired it", () -> KineticTripwires.lastFired()[2] != s.firedBefore[2])
                .check("the bolts hurt 4 x the speed ratio (at most 12)", () -> {
                    double[] f = KineticTripwires.lastFired();
                    float lost = s.healthBefore - server(srv -> player(srv).getHealth());
                    String line = String.format(Locale.ROOT, "tripwire sprinted: %.3f b/t (x%.2f walking), bolt damage %.2f, health lost %.2f",
                            f[0], KineticRules.ratio(f[0]), f[1], lost);
                    s.results.add(line);
                    if (!(f[1] > 4.0 && f[1] <= 12.0 && Math.abs(lost - f[1]) < 0.6)) {
                        throw new Steps.Failure(line);
                    }
                    return true;
                })
                .check("sprinting, the thread stayed hidden", () -> s.sprintShown == 0)
                .command("gamerule naturalRegeneration true")
                .command("effect give @s minecraft:instant_health 1 3");
    }

    /** Stand in floor 0's lift and ride it to floor 1. */
    private void lift(Steps steps, Minecraft mc, State s) {
        steps.run("into floor 0's updraft", () -> {
                    BlockPos lift = s.observatory.lift(0);
                    Vec3 at = Vec3.atBottomCenterOf(lift);
                    tp(mc, at, at.add(1, 1.6, 0));
                    s.liftStart = mc.level.getGameTime();
                })
                .waitUntil("the lift set us down on floor 1", 200, () -> {
                    int floor1 = s.observatory.origin().getY() + s.observatory.plan().floorY(1);
                    return mc.player.onGround() && Math.abs(mc.player.getY() - floor1) < 0.1;
                })
                .run("result", () -> s.results.add("gravity lift: floor 0 to floor 1 in " + (mc.level.getGameTime() - s.liftStart) + " ticks"))
                .run("pour water beside floor 0's updraft", () -> server(srv -> {
                    BlockPos lift = s.observatory.lift(0);
                    Direction in = s.observatory.plan().liftSide(0).getOpposite();
                    srv.getLevel(AetheriaWorld.LEVEL).setBlockAndUpdate(lift.above().relative(in),
                            net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                    return null;
                }))
                .waitTicks(40)
                .check("the water flowed round the updraft and left it standing", () -> server(srv -> {
                    ServerLevel level = srv.getLevel(AetheriaWorld.LEVEL);
                    BlockPos lift = s.observatory.lift(0);
                    boolean ok = true;
                    for (int i = 0; i < 3; i++) {
                        ok &= level.getBlockState(lift.above(i)).is(StructureRegistry.UPDRAFT.get());
                    }
                    Direction in = s.observatory.plan().liftSide(0).getOpposite();
                    boolean wet = level.getFluidState(lift.above().relative(in).relative(in)).is(net.minecraft.tags.FluidTags.WATER);
                    level.setBlockAndUpdate(lift.above().relative(in), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    s.results.add("water beside the lift: the updraft " + (ok ? "stood" : "was washed away") + (wet ? ", the water spread elsewhere" : ""));
                    return ok && wet;
                }));
    }

    // ------------------------------------------------------------------ as worldgen makes them

    /**
     * The nearest place worldgen would start one (the locate), started there through the structure's own
     * generation (its site test and biome check, as a new chunk would), written in, its piece saved and loaded
     * back the way a chunk is, and a look at it from the air.
     */
    private void natural(Steps steps, Minecraft mc, State s, boolean reliquary) {
        String label = reliquary ? "reliquary" : "observatory";
        BlockPos[] core = {null};
        Vec3[] centre = {null};
        steps.command("gamemode spectator")
                .run(label + " as worldgen makes one", () -> server(srv -> {
                    ServerLevel level = srv.getLevel(AetheriaWorld.LEVEL);
                    ServerPlayer p = player(srv);
                    var key = reliquary ? StructureCommands.RELIQUARY : StructureCommands.OBSERVATORY;
                    long t0 = System.nanoTime();
                    StructureCommands.Found found = StructureCommands.nearest(level, key, p.blockPosition(), StructureCommands.LOCATE_REGIONS);
                    long locateNs = System.nanoTime() - t0;
                    if (found == null) {
                        throw new Steps.Failure("no " + label + " within " + StructureCommands.LOCATE_REGIONS + " regions");
                    }
                    var start = StructureCommands.startAt(level, key, found.chunk());
                    if (start == null || start.getPieces().size() != 1) {
                        throw new Steps.Failure("worldgen would not start the located " + label + " in chunk " + found.chunk());
                    }
                    var piece = start.getPieces().get(0);
                    t0 = System.nanoTime();
                    StructureCommands.build(level, start);
                    long buildNs = System.nanoTime() - t0;
                    var tag = piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
                    if (piece instanceof ReliquaryPiece r) {
                        ReliquaryPiece back = new ReliquaryPiece(tag);
                        if (!back.origin().equals(r.origin()) || back.plan().chambers() != r.plan().chambers() || !back.core().equals(r.core())) {
                            throw new Steps.Failure("the Reliquary piece did not load back as it was saved");
                        }
                        core[0] = r.core();
                        centre[0] = Vec3.atCenterOf(r.origin());
                    } else if (piece instanceof ObservatoryPiece o) {
                        ObservatoryPiece back = new ObservatoryPiece(tag);
                        if (!back.origin().equals(o.origin()) || back.plan().floors() != o.plan().floors() || !back.core().equals(o.core())) {
                            throw new Steps.Failure("the Observatory piece did not load back as it was saved");
                        }
                        core[0] = o.core();
                        centre[0] = Vec3.atCenterOf(o.origin());
                    } else {
                        throw new Steps.Failure("unexpected piece " + piece);
                    }
                    if (!level.getBlockState(core[0]).is(StructureRegistry.LENS_CORE.get())) {
                        throw new Steps.Failure("no Lens Array core where the " + label + " put it");
                    }
                    s.results.add(String.format(Locale.ROOT, "%s as worldgen makes it: located %d blocks away in %.1f ms, started and written in %.0f ms at %s",
                            label, (int) Math.sqrt(found.stub().distSqr(p.blockPosition())), locateNs / 1e6, buildNs / 1e6, found.stub().toShortString()));
                    return null;
                }))
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .run("to a viewpoint in the air", () -> {
                    Vec3 c = centre[0];
                    Vec3 from = new Vec3(mc.player.getX(), c.y, mc.player.getZ());
                    Vec3 d = from.subtract(c);
                    Vec3 away = d.lengthSqr() < 1 ? new Vec3(1, 0, 0) : d.normalize();
                    tp(mc, c.add(away.scale(reliquary ? 46 : 66)).add(0, reliquary ? 26 : 40, 0), c.add(0, reliquary ? 18 : 30, 0));
                })
                .waitUntil("the view is drawn", 1600, settled(mc, 1200))
                .screenshot(label + "_natural")
                .run("the usual view", () -> mc.options.renderDistance().set(8));
    }

    // ------------------------------------------------------------------ the hint, the solve, the vault

    private void hint(Steps steps, Minecraft mc, State s, String label) {
        // GDD 6.2: after the wait the Codex offers a hint, and its Lens Array page's link lights one mirror
        steps.command("cosmicbreach debug lens hint 3")
                .waitUntil("the Codex offers the hint after the shortened timer", 140, () -> clientCore(mc, s) != null
                        && clientCore(mc, s).hintOffered())
                .check("nothing is lit until the hint is taken", () -> clientCore(mc, s).hintCell() < 0)
                .check("the page's link finds this room", () -> com.cosmicbreach.structure.array.LensArrays.nearest(mc.player) == clientCore(mc, s))
                .run("take the hint as the Codex page's link does", () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        com.cosmicbreach.codex.Codices.HintPayload.INSTANCE))
                .waitUntil("the hint lit", 40, () -> clientCore(mc, s).hintCell() >= 0)
                .check("the offer is spent", () -> !clientCore(mc, s).hintOffered())
                .run("above the grid, inside the ceiling", () -> topView(mc, s))
                .waitTicks(10)
                .screenshot(label + "_hint")
                .run("result", () -> s.results.add(label + " hint: offered after the 3 s timer, pedestal " + clientCore(mc, s).hintCell()
                        + " lit when the Codex's link was used"));
    }

    private void solve(Steps steps, Minecraft mc, State s, String label) {
        Bot[] bot = {null};
        // survival: on foot (a flying player's sneak only sinks), and every input as a player would give it
        steps.command("gamemode survival")
                .waitTicks(4)
                .check("on foot, not flying", () -> !mc.player.getAbilities().flying)
                .run("mark", () -> {
                    s.xpBefore = server(srv -> Attunements.of(player(srv)).totalXp());
                    s.solveStart = mc.level.getGameTime();
                    s.traceNanos.clear();
                    s.hum.clear();
                    s.humFirst = Float.NaN;
                    s.eyeWoke = false;
                    s.eyeShardlings = 0;
                    s.moves = 0;
                    s.turnsCw = 0;
                    s.turnsCcw = 0;
                    s.carries = 0;
                    s.knocks = 0;
                    bot[0] = new Bot(mc, s);
                })
                .waitUntil("the bot solved the " + label + "'s Lens Array with real input", 6000, () -> bot[0].tick())
                .log("results so far", () -> String.join(System.lineSeparator(), s.results))
                .run("result", () -> {
                    List<long[]> log = server(srv -> core(srv, s).traceLog());
                    List<Long> trace = new ArrayList<>();
                    List<Long> whole = new ArrayList<>();
                    for (long[] e : log) {
                        trace.add(e[0]);
                        whole.add(e[1]);
                    }
                    Collections.sort(trace);
                    Collections.sort(whole);
                    s.results.add(String.format(Locale.ROOT, "%s solved in %d moves over %d ticks; %d retraces, the trace itself %.1f / %.1f / %.1f microseconds, "
                            + "the whole retrace with its block updates %.0f / %.0f / %.0f (min / median / max)",
                            label, s.moves, mc.level.getGameTime() - s.solveStart, log.size(), micros(trace, 0), micros(trace, 1), micros(trace, 2),
                            micros(whole, 0), micros(whole, 1), micros(whole, 2)));
                    s.results.add(label + " hum, distance to pitch per move: " + String.join("; ", s.hum));
                })
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "%s moves by kind: %d use presses, %d sneak-use presses, %d loose pieces carried, %d Umbral blocks knocked by the Maul's charged hit",
                        label, s.turnsCw, s.turnsCcw, s.carries, s.knocks)))
                .check("mirrors were turned both ways, a loose piece carried, an Umbral block knocked by a charged hit", () -> {
                    if (!(s.turnsCw > 0 && s.turnsCcw > 0 && s.carries > 0 && s.knocks > 0)) {
                        throw new Steps.Failure(String.join(" | ", s.results));
                    }
                    return true;
                })
                .check("the Warden Eye woke and called two Shardlings (the bot found a way to it: " + "see results)",
                        () -> s.eyeWoke && s.eyeShardlings >= 2)
                .check("the hum followed the beam (pitch rose from " + "the start to lit)", () -> s.humChecks > 0
                        && !Float.isNaN(s.humFirst) && s.humLast > s.humFirst)
                .waitUntil("the room is drawn", 400, settled(mc, 200))
                .run("above the grid, inside the ceiling", () -> topView(mc, s))
                .waitTicks(10)
                .screenshot(label + "_lens_after")
                .command("gamemode creative")
                .waitTicks(4)
                .run("look over the room from its wall", () -> lensView(mc, s, label.equals("reliquary") ? 5.5 : 7.5,
                        label.equals("reliquary") ? 5.4 : 6.6))
                .waitTicks(10)
                .screenshot(label + "_lens_after_wall")
                .check("the puzzle's XP was paid", () -> {
                    long gained = server(srv -> Attunements.of(player(srv)).totalXp()) - s.xpBefore;
                    s.results.add(label + " puzzle XP: " + gained);
                    return gained >= (label.equals("reliquary") ? 800 : 2000);
                });
    }

    private void vault(Steps steps, Minecraft mc, State s, String label, java.util.function.Supplier<BlockPos> where, int xp) {
        int[] items = {0, 0};
        BlockPos[] vaultAt = {null};
        steps.command("gamemode creative")
                .run("to the vault", () -> {
                    vaultAt[0] = where.get();
                    BlockPos vault = vaultAt[0];
                    Vec3 v = Vec3.atBottomCenterOf(vault);
                    Vec3 c = Vec3.atBottomCenterOf(s.core.above());
                    Vec3 in = c.subtract(v).normalize();
                    fly(mc);
                    select(mc, 8);
                    tp(mc, v.add(in.scale(1.6)), Vec3.atCenterOf(vault));
                })
                .waitTicks(10)
                .command("effect give @s minecraft:instant_health 1 5")
                .command("gamemode survival")
                .waitTicks(10)
                .check("the vault stands ready", () -> server(srv -> srv.getLevel(AetheriaWorld.LEVEL).getBlockEntity(vaultAt[0])
                        instanceof VaultBlockEntity v && v.ready()))
                .run("mark", () -> {
                    s.xpBefore = server(srv -> Attunements.of(player(srv)).totalXp());
                    items[0] = server(srv -> loot(srv, vaultAt[0]));
                })
                .run("aim at the vault", () -> aim(mc, Vec3.atCenterOf(vaultAt[0])))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitTicks(30)
                .screenshot(label + "_vault")
                .check("the vault gave the player their share", () -> {
                    int got = server(srv -> loot(srv, vaultAt[0])) - items[0];
                    long gained = server(srv -> Attunements.of(player(srv)).totalXp()) - s.xpBefore;
                    s.results.add(label + " vault: " + got + " items (" + server(srv -> inventory(player(srv))) + " carried), XP " + gained);
                    return got > 0 && gained >= xp;
                })
                .run("mark", () -> items[0] = server(srv -> loot(srv, vaultAt[0])))
                .run("aim at the vault", () -> aim(mc, Vec3.atCenterOf(vaultAt[0])))
                .press(mc.options.keyUse)
                .waitTicks(30)
                .check("a second open gives the player nothing", () -> server(srv -> loot(srv, vaultAt[0])) <= items[0])
                .check("a second player gets their own share, once", () -> server(srv -> {
                    ServerLevel level = srv.getLevel(AetheriaWorld.LEVEL);
                    VaultBlockEntity v = (VaultBlockEntity) level.getBlockEntity(vaultAt[0]);
                    ServerPlayer other = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("visitor".getBytes()), "Visitor"));
                    other.moveTo(Vec3.atCenterOf(vaultAt[0].above()));
                    int first = v.open(level, other).size();
                    int second = v.open(level, other).size();
                    s.results.add(label + " vault for a second player: " + first + " stacks, then " + second);
                    return first > 0 && second == 0;
                }));
    }

    // ------------------------------------------------------------------ the bot

    /** One small thing the bot does over some ticks; true when done. */
    private interface Act {
        boolean tick();
    }

    /**
     * Solves a Lens Array with the player's own inputs. Each round it asks the solver (on the server) for the
     * cheapest finished layout from the grid as it is, then makes one move toward it: an Umbral knock first (the
     * Maul's charged hit can knock others, so it re-plans after), then a loose piece carried, then a turn. Before
     * its first move it sends the light into the Warden Eye once with a single turn, if one can.
     */
    private static final class Bot {
        final Minecraft mc;
        final State s;
        final Deque<Act> acts = new ArrayDeque<>();
        int rounds;
        boolean eyeDone;

        Bot(Minecraft mc, State s) {
            this.mc = mc;
            this.s = s;
        }

        boolean tick() {
            if (!acts.isEmpty()) {
                if (acts.peek().tick()) {
                    acts.poll();
                }
                return false;
            }
            if (++rounds > 60) {
                List<String> tail = s.results.subList(Math.max(0, s.results.size() - 8), s.results.size());
                String grid = server(srv -> {
                    LensCoreBlockEntity c = core(srv, s);
                    return new com.cosmicbreach.structure.lens.LensPuzzle(c.size(), c.readCells(), c.ports(), c.readCells(), -1, 0, false).toString();
                });
                throw new Steps.Failure("the bot did not finish in 60 rounds; last: " + String.join(" | ", tail) + System.lineSeparator() + grid);
            }
            int phase = server(srv -> core(srv, s).phase());
            if (phase == LensCoreBlockEntity.SOLVED) {
                return true;
            }
            logHum();
            List<Move> m = server(srv -> plan(srv));
            if (m == null) {
                acts.add(wait(10));
                return false;
            }
            for (Move move : m) {
                s.moves += move.clicks() == 0 ? 1 : move.clicks();
                s.results.add("  move " + move);
                enqueue(move);
            }
            return false;
        }

        /** The receptors' hum on this client against the beam's distance. */
        void logHum() {
            LensCoreBlockEntity c = clientCore(mc, s);
            if (c == null) {
                return;
            }
            int n = c.size();
            for (int p = 0; p < c.ports().length; p++) {
                if (!Lens.isReceptor(c.ports()[p])) {
                    continue;
                }
                float d = BeamTrace.closest(c.segments(), Lens.portX(n, p), Lens.portZ(n, p));
                boolean lit = (c.litMask() & 1 << p) != 0;
                float pitch = ReceptorHum.pitch(c.getBlockPos(), p);
                if (Float.isNaN(pitch)) {
                    continue;
                }
                if (Math.abs(pitch - ReceptorHum.pitchFor(d, lit)) > 1e-3) {
                    throw new Steps.Failure("receptor " + p + " hums at " + pitch + " but its beam is " + d + " away");
                }
                s.humChecks++;
                if (Float.isNaN(s.humFirst)) {
                    s.humFirst = pitch;
                }
                s.humLast = pitch;
                s.hum.add(String.format(Locale.ROOT, "p%d %.1f->%.2f", p, Math.min(d, 99f), pitch));
            }
        }

        record Move(String kind, int cell, int to, int clicks, boolean back, int heading, boolean eye) {}

        /** The fewest turns (at most 3) that send light into the Warden Eye without solving, or null. */
        static List<Move> eyePath(int n, int[] start, int[] ports) {
            record Node(int[] cells, List<Move> path) {}
            Deque<Node> queue = new ArrayDeque<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            queue.add(new Node(start, List.of()));
            seen.add(java.util.Arrays.toString(start));
            while (!queue.isEmpty()) {
                Node node = queue.poll();
                if (node.path().size() >= 2) {
                    continue;
                }
                for (int i = 0; i < node.cells().length; i++) {
                    if (!Lens.rotatable(node.cells()[i])) {
                        continue;
                    }
                    for (int dir : new int[] {1, 3}) {
                        int[] c2 = node.cells().clone();
                        c2[i] = Lens.withTurn(c2[i], Lens.turn(c2[i]) + dir);
                        if (!seen.add(java.util.Arrays.toString(c2))) {
                            continue;
                        }
                        List<Move> path = new ArrayList<>(node.path());
                        BeamTrace.Result r = BeamTrace.trace(n, c2, ports);
                        boolean eye = r.eye() && !r.solves(ports);
                        path.add(new Move("turn", i, -1, 1, dir == 3, 0, eye));
                        if (eye) {
                            return path;
                        }
                        queue.add(new Node(c2, path));
                    }
                }
            }
            return null;
        }

        List<Move> plan(MinecraftServer srv) {
            LensCoreBlockEntity core = core(srv, s);
            s.traceNanos.add(core.lastTraceNanos());
            int n = core.size();
            int[] cells = core.readCells();
            int[] ports = core.ports();
            if (BeamTrace.trace(n, cells, ports).solves(ports)) {
                return null; // holding: the 60 ticks run
            }
            if (!eyeDone) {
                // each round, until it has happened once: if a turn or two sends the light into the Warden Eye, do it
                List<Move> path = eyePath(n, cells, ports);
                if (path != null) {
                    eyeDone = true;
                    return path;
                }
            }
            LensSolver.Answer a = LensSolver.solve(n, cells, ports, 30);
            if (a.layout() == null) {
                throw new Steps.Failure("the solver found no way from here");
            }
            int[] target = a.layout();
            // Umbral blocks first: knock the first misplaced one a tile toward where it belongs
            List<Integer> from = new ArrayList<>();
            List<Integer> to = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                if (Lens.kind(cells[i]) == Lens.UMBRAL && Lens.kind(target[i]) != Lens.UMBRAL) {
                    from.add(i);
                }
                if (Lens.kind(target[i]) == Lens.UMBRAL && Lens.kind(cells[i]) != Lens.UMBRAL) {
                    to.add(i);
                }
            }
            if (!from.isEmpty() && !to.isEmpty()) {
                int u = from.get(0);
                int best = to.get(0);
                for (int t : to) {
                    if (dist(n, u, t) < dist(n, u, best)) {
                        best = t;
                    }
                }
                int heading = step(n, cells, u, best);
                return List.of(new Move("knock", u, best, 0, false, heading, false));
            }
            // loose pieces: one lifted and set where it belongs
            for (int i = 0; i < cells.length; i++) {
                if (!Lens.loose(cells[i]) || target[i] == cells[i] || (Lens.loose(target[i]) && sameKind(target[i], cells[i]))) {
                    continue;
                }
                for (int j = 0; j < target.length; j++) {
                    if (j != i && Lens.loose(target[j]) && sameKind(target[j], cells[i]) && Lens.kind(cells[j]) == Lens.EMPTY) {
                        return List.of(new Move("carry", i, j, 0, false, 0, false));
                    }
                }
            }
            // turns
            for (int i = 0; i < cells.length; i++) {
                if (Lens.rotatable(cells[i]) && Lens.kind(cells[i]) == Lens.kind(target[i]) && Lens.turn(cells[i]) != Lens.turn(target[i])) {
                    int cw = (Lens.turn(target[i]) - Lens.turn(cells[i])) & 3;
                    // two presses either way round: sneak-use (counter-clockwise) for those, so both verbs are used
                    return List.of(cw == 1 ? new Move("turn", i, -1, 1, false, 0, false) : new Move("turn", i, -1, cw == 2 ? 2 : 1, true, 0, false));
                }
            }
            throw new Steps.Failure("no move toward the solver's layout:\n" + new com.cosmicbreach.structure.lens.LensPuzzle(n, cells, ports,
                    target, -1, 0, false).render(cells) + "\n" + new com.cosmicbreach.structure.lens.LensPuzzle(n, target, ports,
                    target, -1, 0, false).render(target));
        }

        private static boolean sameKind(int a, int b) {
            return Lens.kind(a) == Lens.kind(b) && (Lens.kind(a) != Lens.FILTER || Lens.color(a) == Lens.color(b));
        }

        private static int dist(int n, int a, int b) {
            return Math.abs(a % n - b % n) + Math.abs(a / n - b / n);
        }

        /** The first heading from u toward t onto an empty pedestal. */
        private static int step(int n, int[] cells, int u, int t) {
            int dx = Integer.signum(t % n - u % n);
            int dz = Integer.signum(t / n - u / n);
            int[] options = dx != 0 && dz != 0 ? new int[] {dx > 0 ? Lens.EAST : Lens.WEST, dz > 0 ? Lens.SOUTH : Lens.NORTH}
                    : dx != 0 ? new int[] {dx > 0 ? Lens.EAST : Lens.WEST} : new int[] {dz > 0 ? Lens.SOUTH : Lens.NORTH};
            for (int h : options) {
                int x = u % n + Lens.DX[h];
                int z = u / n + Lens.DZ[h];
                if (x >= 0 && x < n && z >= 0 && z < n && Lens.kind(cells[z * n + x]) == Lens.EMPTY) {
                    return h;
                }
            }
            return options[0];
        }

        void enqueue(Move m) {
            LensCoreBlockEntity c = clientCore(mc, s);
            BlockPos cell = c.cellPos(m.cell());
            switch (m.kind()) {
                case "turn" -> {
                    acts.add(standBy(cell, -1));
                    acts.add(aimAt(cell, Vec3.atCenterOf(cell)));
                    if (m.back()) {
                        acts.add(key(mc.options.keyShift, true));
                    }
                    for (int k = 0; k < m.clicks(); k++) {
                        acts.add(tap(mc.options.keyUse));
                        acts.add(wait(3));
                    }
                    if (m.back()) {
                        acts.add(key(mc.options.keyShift, false));
                        s.turnsCcw += m.clicks();
                    } else {
                        s.turnsCw += m.clicks();
                    }
                    acts.add(wait(4));
                    if (m.eye()) {
                        acts.add(eyeCheck());
                    }
                }
                case "carry" -> {
                    BlockPos dest = c.cellPos(m.to());
                    acts.add(freeHand());
                    acts.add(standBy(cell, -1));
                    acts.add(aimAt(cell, Vec3.atCenterOf(cell)));
                    acts.add(tap(mc.options.keyAttack));
                    acts.add(until(20, "the lifted piece reaches the hotbar", () -> pieceSlot() >= 0));
                    acts.add(() -> {
                        int k = pieceSlot();
                        KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
                        return true;
                    });
                    acts.add(standBy(dest, -1));
                    acts.add(aimAt(dest, Vec3.atBottomCenterOf(dest).add(0, 0.3, 0)));
                    acts.add(tap(mc.options.keyUse));
                    acts.add(until(20, "the piece leaves the hotbar for the pedestal", () -> pieceSlot() < 0));
                    acts.add(() -> {
                        s.carries++;
                        return true;
                    });
                    acts.add(wait(4));
                }
                case "knock" -> {
                    acts.add(slot(0));
                    acts.add(standBy(cell, Lens.opposite(m.heading())));
                    acts.add(aimAt(null, Vec3.atCenterOf(cell)));
                    acts.add(key(mc.options.keyAttack, true));
                    acts.add(wait(32));
                    acts.add(key(mc.options.keyAttack, false));
                    acts.add(wait(24));
                    acts.add(() -> {
                        if (!(mc.level.getBlockState(cell).getBlock() instanceof com.cosmicbreach.structure.array.LensUmbralBlock)) {
                            s.knocks++;
                        } else {
                            s.results.add("a charged hit left the Umbral block at " + cell.toShortString() + " where it was");
                        }
                        return true;
                    });
                }
                default -> throw new Steps.Failure("unknown move " + m.kind());
            }
        }

        int pieceSlot() {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).getItem() instanceof LensPieceItem) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * An empty hand for lifting a piece: selects an empty hotbar slot, first clearing the last one by command if
         * the hotbar is full (vault loot and Shardling drops can fill it before the second array).
         */
        Act freeHand() {
            int[] t = {0};
            return () -> {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).isEmpty()) {
                        KeyMapping.click(mc.options.keyHotbarSlots[i].getKey());
                        return true;
                    }
                }
                if (t[0]++ == 0) {
                    s.results.add("the hotbar was full (" + hotbar() + "): slot 9 cleared to carry a piece");
                    mc.player.connection.sendCommand("item replace entity @s hotbar.8 with minecraft:air");
                }
                if (t[0] > 40) {
                    throw new Steps.Failure("no empty hotbar slot to lift a piece with: " + hotbar());
                }
                return false;
            };
        }

        String hotbar() {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.getInventory().getItem(i);
                names.add(st.isEmpty() ? "-" : st.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
            }
            return String.join(", ", names);
        }

        Act slot(int k) {
            return () -> {
                KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
                return true;
            };
        }

        /** Stands on the walkway beside {@code cell} (on side {@code heading}, or the first clear side). */
        Act standBy(BlockPos cell, int heading) {
            int[] t = {0};
            return () -> {
                if (t[0]++ == 0) {
                    BlockPos spot = null;
                    for (int h = 0; h < 4 && spot == null; h++) {
                        int side = heading >= 0 ? heading : h;
                        BlockPos g = cell.relative(com.cosmicbreach.structure.array.PuzzleBlock.direction(side));
                        if (mc.level.getBlockState(g).getCollisionShape(mc.level, g).isEmpty()
                                && mc.level.getBlockState(g.above()).getCollisionShape(mc.level, g.above()).isEmpty()
                                && !mc.level.getBlockState(g).is(StructureRegistry.UPDRAFT.get())
                                && !mc.level.getBlockState(g.below()).getCollisionShape(mc.level, g.below()).isEmpty()) {
                            spot = g;
                        }
                        if (heading >= 0) {
                            break;
                        }
                    }
                    if (spot == null) {
                        throw new Steps.Failure("nowhere to stand by " + cell.toShortString());
                    }
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %d %.2f", spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
                }
                return t[0] > 6;
            };
        }

        /** Looks at {@code at} until the crosshair rests on {@code block} (any block if null). */
        Act aimAt(BlockPos block, Vec3 at) {
            int[] t = {0};
            return () -> {
                aim(mc, at);
                t[0]++;
                if (block == null) {
                    return t[0] > 2;
                }
                if (mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(block)) {
                    return true;
                }
                if (t[0] > 20) {
                    throw new Steps.Failure("could not aim at " + block.toShortString() + ", the crosshair is on " + mc.hitResult);
                }
                return false;
            };
        }

        Act key(KeyMapping key, boolean down) {
            return () -> {
                InputConstants.Key k = key.getKey();
                KeyMapping.set(k, down);
                if (down) {
                    KeyMapping.click(k);
                }
                return true;
            };
        }

        Act tap(KeyMapping key) {
            int[] t = {0};
            return () -> {
                InputConstants.Key k = key.getKey();
                if (t[0]++ == 0) {
                    KeyMapping.set(k, true);
                    KeyMapping.click(k);
                    return false;
                }
                KeyMapping.set(k, false);
                return true;
            };
        }

        Act wait(int ticks) {
            int[] t = {0};
            return () -> ++t[0] >= ticks;
        }

        Act until(int timeout, String what, BooleanSupplier done) {
            int[] t = {0};
            return () -> {
                if (done.getAsBoolean()) {
                    return true;
                }
                if (++t[0] > timeout) {
                    throw new Steps.Failure("a bot step timed out waiting until " + what + "; hotbar: " + hotbar());
                }
                return false;
            };
        }

        /** After the turn that sends light into the Eye: two Shardlings must come; then they go. */
        Act eyeCheck() {
            int[] t = {0};
            int[] before = {-1};
            return () -> {
                if (before[0] < 0) {
                    before[0] = 0;
                }
                int now = server(srv -> shardlingsNear(srv, s.core, 30));
                if (now >= 2) {
                    s.eyeWoke = server(srv -> core(srv, s).ports().length > 0);
                    s.eyeShardlings = now;
                    s.results.add("Warden Eye: " + now + " Shardlings came " + t[0] + " ticks after the light reached it");
                    mc.player.connection.sendCommand("kill @e[type=cosmicbreach:shardling]");
                    return true;
                }
                if (++t[0] > 80) {
                    throw new Steps.Failure("the Warden Eye called no Shardlings");
                }
                return false;
            };
        }
    }

    // ------------------------------------------------------------------ helpers

    private static LensCoreBlockEntity core(MinecraftServer srv, State s) {
        BlockEntity be = srv.getLevel(AetheriaWorld.LEVEL).getBlockEntity(s.core);
        if (!(be instanceof LensCoreBlockEntity core)) {
            throw new Steps.Failure("no Lens Array core at " + s.core);
        }
        return core;
    }

    /** Min (0), median (1) or max (2) of sorted nanoseconds, in microseconds. */
    private static double micros(List<Long> sorted, int which) {
        if (sorted.isEmpty()) {
            return 0;
        }
        long v = which == 0 ? sorted.get(0) : which == 1 ? sorted.get(sorted.size() / 2) : sorted.get(sorted.size() - 1);
        return v / 1000.0;
    }

    private static String wakeReport(LensCoreBlockEntity core) {
        return String.format(Locale.ROOT, "%s, at least %d moves; woke %d ticks after a player came within 24 blocks (generated in %.1f ms off the server thread)",
                core.difficulty(), core.minMoves(), core.activatedAt() - core.wakeRequestedAt(), core.generationNanos() / 1e6);
    }

    private static LensCoreBlockEntity clientCore(Minecraft mc, State s) {
        return mc.level != null && mc.level.getBlockEntity(s.core) instanceof LensCoreBlockEntity c ? c : null;
    }

    /** High in the lens room by its wall, looking down over the grid (creative, flying: players wake rooms). */
    private static void lensView(Minecraft mc, State s, double out, double up) {
        Vec3 core = Vec3.atBottomCenterOf(s.core);
        mc.options.hideGui = false;
        fly(mc);
        select(mc, 8);
        tp(mc, core.add(out, up, 2.0), core.add(0, 1.0, 0));
    }

    /** Creative (a player: rooms wake for them), flying, high in the lens room by its wall. */
    private static Steps toLensView(Steps steps, Minecraft mc, State s, double out, double up) {
        return steps.command("gamemode creative")
                .waitTicks(4)
                .run("high in the lens room by its wall", () -> lensView(mc, s, out, up))
                .waitTicks(2)
                .run("still flying", () -> fly(mc));
    }

    /**
     * A spectator's eye inside the room's ceiling, straight over the grid, looking down: the ceiling's blocks are not
     * drawn from inside, so the whole grid shows.
     */
    private static void topView(Minecraft mc, State s) {
        LensCoreBlockEntity c = clientCore(mc, s);
        int ceiling = c == null ? 8 : c.ceiling();
        Vec3 eye = Vec3.atBottomCenterOf(s.core).add(0, 1 + ceiling + 0.45, 0.01);
        mc.player.connection.sendCommand("gamemode spectator");
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f 0 90", eye.x, eye.y - 1.62, eye.z));
        mc.options.hideGui = true;
    }

    /** Selects hotbar slot {@code k} with its key (slot 8: an empty hand, for screenshots). */
    private static void select(Minecraft mc, int k) {
        KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
    }

    private static void fly(Minecraft mc) {
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
    }

    private static int shardlingsNear(MinecraftServer srv, BlockPos at, double radius) {
        return srv.getLevel(AetheriaWorld.LEVEL).getEntities(ModEntities.SHARDLING.get(), new AABB(at).inflate(radius), e -> e.isAlive()).size();
    }

    /** Items on the floor near {@code at} plus everything the player carries. */
    private static int loot(MinecraftServer srv, BlockPos at) {
        int floor = srv.getLevel(AetheriaWorld.LEVEL).getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
        return floor + player(srv).getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
    }

    /** What the player carries, as "3 starsteel_ingot, 5 spire_quartz". */
    private static String inventory(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (ItemStack st : p.getInventory().items) {
            if (!st.isEmpty()) {
                out.add(st.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
            }
        }
        return String.join(", ", out);
    }

    private static int itemsNear(MinecraftServer srv, BlockPos at) {
        return srv.getLevel(AetheriaWorld.LEVEL).getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).size();
    }

    /** A yaw from the player toward the island's open ground (26 blocks out on island, away from the rim). */
    private static float openYaw(ServerPlayer p) {
        AetheriaTerrain t = AetheriaTerrain.of(p.serverLevel().getChunkSource().randomState());
        ReachIslands.Column col = new ReachIslands.Column();
        float best = p.getYRot();
        double bestEdge = -1;
        for (int k = 0; k < 16; k++) {
            float yaw = k * 22.5f;
            Vec3 at = p.position().add(Vec3.directionFromRotation(0, yaw).scale(26));
            t.reach.sample(at.x, at.z, col);
            if (col.island && col.edge > bestEdge) {
                bestEdge = col.edge;
                best = yaw;
            }
        }
        return best;
    }

    /** The horizontal unit vector from {@code origin} toward {@code from}. */
    private static Vec3 toward(Vec3 from, BlockPos origin) {
        Vec3 d = new Vec3(from.x - origin.getX() - 0.5, 0, from.z - origin.getZ() - 0.5);
        return d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private static void tp(Minecraft mc, Vec3 at, Vec3 lookAt) {
        Vec3 d = lookAt.subtract(at.add(0, 1.62, 0));
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", at.x, at.y, at.z, yaw, pitch));
    }

    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
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

    private static final int SETTLE_TICKS = 30;

    private static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = mc.screen == null && mc.level != null && mc.player != null;
            int rendered = ready ? mc.levelRenderer.countRenderedSections() : -1;
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= SETTLE_TICKS || (state[0] >= maxTicks && ready);
        };
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static <T> T server(Function<MinecraftServer, T> call) {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv == null) {
            throw new Steps.Failure("no integrated server");
        }
        try {
            return srv.submit(() -> call.apply(srv)).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Steps.Failure f) {
                throw f;
            }
            throw new Steps.Failure("server call failed: " + cause);
        }
    }
}
