package com.cosmicbreach.client.dev.scenario;

import static com.cosmicbreach.client.dev.scenario.CryptKit.player;
import static com.cosmicbreach.client.dev.scenario.CryptKit.server;

import com.cosmicbreach.client.crypt.ChuteRenderer;
import com.cosmicbreach.client.crypt.GravityPlateRenderer;
import com.cosmicbreach.client.crypt.RiftTileRenderer;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.structure.ThreadRenderer;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.structure.crypt.CryptCommands;
import com.cosmicbreach.structure.crypt.CryptLayout;
import com.cosmicbreach.structure.crypt.CryptLayout.Kind;
import com.cosmicbreach.structure.crypt.CryptPiece;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.crypt.trap.ChuteRules;
import com.cosmicbreach.structure.crypt.trap.CryptTraps;
import com.cosmicbreach.structure.crypt.trap.GravityPistonBlockEntity;
import com.cosmicbreach.structure.crypt.trap.GravityPlateRules;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidPocketBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidRiftBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidRiftRules;
import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.KineticTripwires;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Hollow Crypt (W6, GDD 6.1, 6.4), in the Deep:
 *
 * <ol>
 *   <li>WALK: a crypt built on a Rift Abyss pillar (timed); its gatehouse from outside; then on foot (creative, real
 *       input) from the top of the entrance stair along the main path, down every stair and through every trap
 *       corridor, into the Choir Floor's room and up to the vault; the floor solved by debug, the vault opened
 *       (loot, Structure Found XP). Screenshots of a corridor and the vault.</li>
 *   <li>TRAPS (survival, measured): a Kinetic Tripwire walked and sprinted; the Void Rift tiles' tell seen walking and
 *       not sprinting, crossed walking (sprung, opened behind), dashed (never sprung), stood on (the fall, the Void
 *       Pocket, its way out after 45 s); the Gravity Plate's rings and hum only when careful, stepped on (gravity,
 *       jump, speed, a half dash, the piston's damage and stagger on tick 40), and escaped in time; a Starfall
 *       Chute's glint only when careful, and a star-rock's damage. Screenshots of each tell.</li>
 * </ol>
 */
public final class CryptScenario implements Scenario {
    public enum Part { WALK, TRAPS }

    static final long SEED = 12L;

    private final EnumSet<Part> parts;

    public CryptScenario() {
        this.parts = EnumSet.allOf(Part.class);
    }

    public CryptScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return parts.size() == 2 ? 900 : 500;
    }

    static final class State {
        CryptPiece crypt;
        final List<String> results = new ArrayList<>();
        final List<Vec3> route = new ArrayList<>();
        int waypoint;
        int stuck;
        Vec3 last;
        long walkStart;
        long xpBefore;
        DevCamera camera;
        boolean corridorShot;
        final List<long[]> hums = new ArrayList<>();
        SoundEventListener listener;
        float before;
        Vec3 from;
        long mark;
        boolean seenWhileSprinting;
        final List<double[]> dashes = new java.util.concurrent.CopyOnWriteArrayList<>();
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
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0 400 0")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .command("cosmicbreach debug goto deep")
                .waitUntil("the Deep is drawn", 1600, CryptKit.settled(mc, 1200))
                .run("build a Hollow Crypt ahead", () -> {
                    long t0 = System.nanoTime();
                    s.crypt = server(srv -> CryptCommands.placeCryptAhead(player(srv), SEED));
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    CryptLayout plan = s.crypt.plan();
                    StringBuilder kinds = new StringBuilder();
                    for (Kind k : Kind.values()) {
                        if (k != Kind.NONE && plan.count(k) > 0) {
                            kinds.append(k.name().toLowerCase(Locale.ROOT)).append(' ').append(plan.count(k)).append(", ");
                        }
                    }
                    s.results.add("crypt built in " + ms + " ms: " + plan.levels + " levels, " + plan.rooms() + " rooms (" + kinds
                            + "), main path " + plan.mainPath().size() + " cells, gatehouse at " + s.crypt.entrance().toShortString());
                });
        steps.command("gamerule sendCommandFeedback true")
                .command("cosmicbreach debug locate")
                .waitForChat("Nearest cosmicbreach:hollow_crypt: -?[0-9]+, [0-9]+, -?[0-9]+", 200)
                .run("worldgen would start the located crypt", () -> {
                    String found = server(srv -> {
                        ServerLevel level = level(srv);
                        CryptCommands.Found hit = CryptCommands.nearest(level, player(srv).blockPosition(), 24);
                        if (hit == null) {
                            return null;
                        }
                        long t0 = System.nanoTime();
                        var start = com.cosmicbreach.structure.StructureCommands.startAt(level, CryptCommands.CRYPT, hit.chunk());
                        long us = (System.nanoTime() - t0) / 1000;
                        return start == null ? null : hit.gate().toShortString() + " (" + (int) Math.sqrt(hit.gate().distSqr(player(srv).blockPosition()))
                                + " blocks away; its start found in " + us + " us)";
                    });
                    if (found == null) {
                        throw new Steps.Failure("the located crypt should be a structure start worldgen makes");
                    }
                    s.results.add("nearest natural Hollow Crypt: " + found);
                })
                .command("gamerule sendCommandFeedback false");
        if (parts.contains(Part.WALK)) {
            walk(steps, mc, s);
        }
        if (parts.contains(Part.TRAPS)) {
            traps(steps, mc, s);
        }
        steps.log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    // ------------------------------------------------------------------ the walk

    private void walk(Steps steps, Minecraft mc, State s) {
        steps.run("a view of the gatehouse", () -> {
                    BlockPos g = s.crypt.entrance();
                    camera(mc, s, Vec3.atCenterOf(g).add(-11, 7, -13), Vec3.atCenterOf(g).add(0, 1, 0));
                })
                .waitUntil("the view is drawn", 800, CryptKit.settled(mc, 600))
                .screenshot("crypt_gatehouse")
                .run("back to the player", () -> uncamera(s))
                .run("plan the route", () -> {
                    s.route.clear();
                    s.route.addAll(route(s.crypt));
                    s.waypoint = 1;
                    s.results.add("route: " + s.route.size() + " waypoints");
                })
                .run("at the top of the stair", () -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                    CryptKit.tp(mc, s.route.get(0), s.route.get(1).add(0, 1.2, 0));
                })
                .waitTicks(10)
                .run("remember", () -> {
                    s.walkStart = mc.level.getGameTime();
                    s.xpBefore = server(srv -> Attunements.of(player(srv)).totalXp());
                })
                .waitUntil("walked from the gatehouse to the Choir Floor's vault", 6000, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .run("result", () -> s.results.add("walked entrance to vault in " + (mc.level.getGameTime() - s.walkStart) + " ticks, "
                        + s.stuck + " ticks nudged"))
                .command("cosmicbreach debug choir solve")
                .waitUntil("the vault unsealed", 60, () -> server(srv -> vaultOf(srv, s).ready()))
                .run("aim at the vault", () -> CryptKit.aim(mc, Vec3.atCenterOf(s.crypt.vault())))
                .press(mc.options.keyUse)
                .waitUntil("the vault gave its loot", 60, () -> server(srv -> level(srv).getEntitiesOfClass(ItemEntity.class,
                        new AABB(s.crypt.vault()).inflate(4)).size() + player(srv).getInventory().items.stream()
                        .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()) > 0)
                .run("result", () -> {
                    long gained = server(srv -> Attunements.of(player(srv)).totalXp()) - s.xpBefore;
                    s.results.add("vault opened: XP gained " + gained + " (Puzzle Solved by debug and Structure Found)");
                })
                .run("a view of the vault", () -> {
                    Vec3 v = Vec3.atCenterOf(s.crypt.vault());
                    Vec3 c = Vec3.atBottomCenterOf(s.crypt.conductor());
                    Vec3 back = c.subtract(v).normalize();
                    camera(mc, s, v.add(back.scale(5.5)).add(back.z * 2.5, 2.6, -back.x * 2.5), v);
                })
                .waitUntil("the view is drawn", 400, CryptKit.settled(mc, 200))
                .screenshot("crypt_vault_room")
                .run("back to the player", () -> uncamera(s));
    }

    /** Steers along the route one tick; true at the end. Takes the corridor screenshot's view on the way. */
    private static boolean follow(Minecraft mc, State s) {
        if (s.waypoint >= s.route.size()) {
            return true;
        }
        Vec3 w = s.route.get(s.waypoint);
        Vec3 p = mc.player.position();
        double d = Math.hypot(w.x - p.x, w.z - p.z);
        if (d < 0.45 && Math.abs(w.y - p.y) < 1.6) {
            s.waypoint++;
            return s.waypoint >= s.route.size();
        }
        CryptKit.face(mc, w.add(0, 1.2, 0), 15f);
        CryptKit.set(mc.options.keyUp, true);
        CryptKit.set(mc.options.keySprint, false);
        mc.player.setSprinting(false);
        if (s.last != null && s.last.distanceTo(p) < 0.02) {
            s.stuck++;
            CryptKit.set(mc.options.keyJump, s.stuck % 10 == 0);
            if (s.stuck > 200) {
                throw new Steps.Failure("stuck at " + p + " on the way to waypoint " + s.waypoint + " " + w);
            }
        } else {
            CryptKit.set(mc.options.keyJump, false);
        }
        s.last = p;
        return false;
    }

    /**
     * The route from the top of the entrance stair to the vault: the stair's strip down to level 0, then through
     * each main-path cell's doors (down each stair on the way), through the Choir Floor's door, round the Conductor
     * and to the vault's front.
     */
    static List<Vec3> route(CryptPiece crypt) {
        CryptLayout plan = crypt.plan();
        List<Vec3> out = new ArrayList<>();
        List<int[]> path = plan.mainPath();
        int[] first = path.get(0);
        int es = plan.side(0, first[1], first[2]);
        out.add(point(crypt, 0, first[1], first[2], strip(es, 1), crypt.floorY(0) + 9 - crypt.floorY(0)));
        out.add(point(crypt, 0, first[1], first[2], strip(es, 5), 5));
        out.add(point(crypt, 0, first[1], first[2], strip(es, 9), 1));
        for (int i = 0; i < path.size(); i++) {
            int[] c = path.get(i);
            int l = c[0];
            Kind k = plan.kind(l, c[1], c[2]);
            if (i > 0) {
                int[] prev = path.get(i - 1);
                if (prev[0] == l) {
                    int side = sideTo(prev[1], prev[2], c[1], c[2]);
                    out.add(door(crypt, l, prev[1], prev[2], side));
                }
            }
            if (k == Kind.STAIR_DOWN) {
                int side = plan.side(l, c[1], c[2]);
                int rail = side == 0 || side == 1 ? 7 : 3;
                int[] landing = side == 0 || side == 2 ? new int[] {rail, 1} : new int[] {1, rail};
                out.add(point(crypt, l, c[1], c[2], landing, 1));
                out.add(point(crypt, l, c[1], c[2], strip(side, 1), 1));
                out.add(point(crypt, l, c[1], c[2], strip(side, 5), -3));
                out.add(point(crypt, l + 1, c[1], c[2], strip(side, 9), 1));
            } else if (k != Kind.STAIR_UP && k != Kind.ENTRANCE) {
                out.add(point(crypt, l, c[1], c[2], new int[] {5, 5}, 1));
            }
        }
        int[] last = path.get(path.size() - 1);
        int s = crypt.choirEntry();
        out.add(door(crypt, last[0], last[1], last[2], s));
        // into the room, round the Conductor on the side away from the door's line, to the vault's front
        Vec3 c = Vec3.atBottomCenterOf(crypt.conductor());
        Vec3 in = new Vec3(CryptLayout.DX[s], 0, CryptLayout.DZ[s]);
        Vec3 side = new Vec3(-in.z, 0, in.x);
        Vec3 doorAt = out.get(out.size() - 1);
        double sgn = Math.signum(doorAt.subtract(c).dot(side));
        if (sgn == 0) {
            sgn = 1;
        }
        out.add(c.add(in.scale(-6)).add(side.scale(sgn * 7.5)));
        out.add(c.add(side.scale(sgn * 8)));
        out.add(c.add(in.scale(6.5)).add(side.scale(sgn * 3)));
        out.add(Vec3.atBottomCenterOf(crypt.vault()).subtract(in.scale(1.6)));
        return out;
    }

    private static int[] strip(int side, int along) {
        return switch (side) {
            case 0 -> new int[] {8, along};
            case 1 -> new int[] {along, 8};
            case 2 -> new int[] {2, along};
            default -> new int[] {along, 2};
        };
    }

    private static int sideTo(int x, int z, int tx, int tz) {
        for (int s = 0; s < 4; s++) {
            if (x + CryptLayout.DX[s] == tx && z + CryptLayout.DZ[s] == tz) {
                return s;
            }
        }
        return 0;
    }

    /** Block (lx, lz) of cell (x, z) of level l, standing {@code up} blocks over that level's floor block. */
    static Vec3 point(CryptPiece crypt, int l, int x, int z, int[] local, int up) {
        BlockPos o = crypt.origin();
        return new Vec3(o.getX() + CryptLayout.CELL * x + local[0] + 0.5, crypt.floorY(l) + up,
                o.getZ() + CryptLayout.CELL * z + local[1] + 0.5);
    }

    /** The middle of the door on side {@code side} of cell (x, z), standing on the floor. */
    static Vec3 door(CryptPiece crypt, int l, int x, int z, int side) {
        int[] local = switch (side) {
            case 0 -> new int[] {10, 5};
            case 1 -> new int[] {5, 10};
            case 2 -> new int[] {0, 5};
            default -> new int[] {5, 0};
        };
        return point(crypt, l, x, z, local, 1);
    }

    // ------------------------------------------------------------------ the traps

    private void traps(Steps steps, Minecraft mc, State s) {
        steps.run("listen for the ceiling's hum", () -> {
                    s.listener = new SoundEventListener() {
                        @Override
                        public void onPlaySound(SoundInstance sound, WeighedSoundEvents accessor, float range) {
                            if (sound.getLocation().equals(CryptRegistry.PLATE_HUM.get().getLocation())) {
                                s.hums.add(new long[] {mc.level.getGameTime()});
                            }
                        }
                    };
                    mc.getSoundManager().addListener(s.listener);
                })
                .run("watch the dashes", () -> com.cosmicbreach.client.combat.ClientCombat.addEventListener(e -> {
                    if (e instanceof com.cosmicbreach.combat.core.CombatEvent.DashStarted && mc.player != null) {
                        s.dashes.add(new double[] {mc.level.getGameTime(), mc.player.getX(), mc.player.getZ(),
                                com.cosmicbreach.client.combat.BodyMotion.dashScale(), CryptTraps.heavy(mc.player) ? 1 : 0});
                    }
                }))
                .command("gamerule naturalRegeneration false")
                .run("the crypt's Hollow Stalkers kept out of the trap tests (the stalker scenario tests their spots)", () -> server(srv -> {
                    net.minecraft.server.level.ServerPlayer p = player(srv);
                    for (com.cosmicbreach.entity.stalker.HollowStalker h : p.serverLevel().getEntitiesOfClass(
                            com.cosmicbreach.entity.stalker.HollowStalker.class, p.getBoundingBox().inflate(256.0))) {
                        h.discard();
                    }
                    net.minecraft.world.level.levelgen.structure.BoundingBox box = s.crypt.getBoundingBox();
                    for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                        if (p.serverLevel().getBlockState(pos).is(com.cosmicbreach.structure.crypt.CryptRegistry.STALKER_MARKER.get())) {
                            p.serverLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    return true;
                }))
                .command("gamemode survival")
                .command("clear @s")
                .command("cosmicbreach give meridian")
                .waitUntil("Meridian in the first slot", 40, () -> mc.player.getInventory().getItem(0).getItem()
                        instanceof com.cosmicbreach.item.CombatWeaponItem);
        tripwire(steps, mc, s);
        rift(steps, mc, s);
        gravity(steps, mc, s);
        chutes(steps, mc, s);
        steps.run("stop listening", () -> mc.getSoundManager().removeListener(s.listener))
                .command("gamemode creative");
    }

    /** The first cell of {@code kind} in the crypt: {level, x, z}. */
    static int[] cell(CryptPiece crypt, Kind kind) {
        CryptLayout plan = crypt.plan();
        for (int l = 0; l < plan.levels; l++) {
            for (int x = 0; x < CryptLayout.GRID; x++) {
                for (int z = 0; z < CryptLayout.GRID; z++) {
                    if (plan.kind(l, x, z) == kind) {
                        return new int[] {l, x, z};
                    }
                }
            }
        }
        throw new Steps.Failure("the crypt has no " + kind);
    }

    /** A point along a corridor cell's axis: {@code p} blocks along (0 at its lower door), {@code a} across (5 the middle). */
    static Vec3 along(CryptPiece crypt, int[] c, double p, double a, double up) {
        int axis = crypt.plan().side(c[0], c[1], c[2]);
        BlockPos o = crypt.origin();
        double x = o.getX() + CryptLayout.CELL * c[1] + (axis == 0 ? p : a) + 0.5;
        double z = o.getZ() + CryptLayout.CELL * c[2] + (axis == 0 ? a : p) + 0.5;
        return new Vec3(x, crypt.floorY(c[0]) + up, z);
    }

    private void tripwire(Steps steps, Minecraft mc, State s) {
        int[][] c = {null};
        steps.run("to the tripwire corridor", () -> {
                    c[0] = cell(s.crypt, Kind.TRIPWIRE);
                    CryptKit.heal();
                    CryptKit.tp(mc, along(s.crypt, c[0], 0.5, 5, 1), along(s.crypt, c[0], 9, 5, 2));
                })
                .waitUntil("standing in the corridor", 60, () -> mc.player.onGround()
                        && mc.player.position().distanceTo(along(s.crypt, c[0], 0.5, 5, 1)) < 0.3)
                .waitTicks(40)
                .run("full health", () -> {
                    CryptKit.heal();
                    s.before = CryptKit.health();
                })
                .hold(mc.options.keyUp)
                .waitUntil("walked past the first thread", 120, () -> passed(mc, s, c[0], 4.2))
                .release(mc.options.keyUp)
                .waitTicks(8)
                .run("result", () -> {
                    float lost = s.before - CryptKit.health();
                    BlockPos emitter = BlockPos.containing(along(s.crypt, c[0], 3, 3, 1));
                    Long shown = ThreadRenderer.shownAt(emitter);
                    s.results.add(String.format(Locale.ROOT, "tripwire walked: %.1f damage, thread shown: %s", lost, shown != null));
                    if (lost > 0.01f) {
                        String source = server(srv -> String.valueOf(player(srv).getLastDamageSource() == null ? "none"
                                : player(srv).getLastDamageSource().getMsgId()));
                        double[] fired = KineticTripwires.lastFired();
                        throw new Steps.Failure("walking through a tripwire should be safe (lost " + lost + " to " + source + "; the crossing: "
                                + fired[0] + " blocks a tick, bolts of " + fired[1] + "; crypt " + s.crypt.entrance().toShortString() + ")");
                    }
                    s.before = CryptKit.health();
                })
                .run("stand before the second thread", () -> CryptKit.tp(mc, along(s.crypt, c[0], 5.2, 5, 1), along(s.crypt, c[0], 7, 5, 1.3)))
                .waitTicks(12)
                .run("look at it", () -> {
                    CryptKit.aim(mc, along(s.crypt, c[0], 7.5, 5, 1.2));
                    mc.options.hideGui = true;
                })
                .waitTicks(3)
                .screenshot("crypt_tell_tripwire_thread")
                .run("the HUD back", () -> mc.options.hideGui = false)
                .run("back to its start", () -> CryptKit.tp(mc, along(s.crypt, c[0], 0.5, 5, 1), along(s.crypt, c[0], 9, 5, 2)))
                .waitTicks(15)
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("sprinted past the first thread", 120, () -> passed(mc, s, c[0], 4.2))
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .waitTicks(10)
                .run("result", () -> {
                    float lost = s.before - CryptKit.health();
                    double[] fired = KineticTripwires.lastFired();
                    s.results.add(String.format(Locale.ROOT, "tripwire sprinted: %.1f damage (bolts of %.1f at %.2f times walking pace)",
                            lost, fired[1], KineticRules.ratio(fired[0])));
                    if (lost < 1) {
                        throw new Steps.Failure("sprinting through a tripwire should cost health");
                    }
                });
    }

    /** True once the player is past {@code p} blocks along the corridor. */
    private static boolean passed(Minecraft mc, State s, int[] c, double p) {
        Vec3 at = along(s.crypt, c, p, 5, 1);
        Vec3 start = along(s.crypt, c, 0, 5, 1);
        Vec3 dir = at.subtract(start).normalize();
        return mc.player.position().subtract(start).dot(dir) >= at.subtract(start).length();
    }

    private void rift(Steps steps, Minecraft mc, State s) {
        int[][] c = {null};
        BlockPos[] mid = {null, null};
        long[][] before = {null, null};
        steps.run("to the rift corridor", () -> {
                    c[0] = cell(s.crypt, Kind.RIFT);
                    mid[0] = BlockPos.containing(along(s.crypt, c[0], 3, 5, 0));
                    mid[1] = BlockPos.containing(along(s.crypt, c[0], 7, 5, 0));
                    CryptKit.heal();
                    CryptKit.tp(mc, along(s.crypt, c[0], -2.5, 5, 1), along(s.crypt, c[0], 9, 5, 1));
                })
                .waitTicks(20)
                .run("mark", () -> {
                    s.mark = mc.level.getGameTime();
                    s.seenWhileSprinting = false;
                })
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("sprinting up to the first cluster", 60, () -> {
                    Long t = RiftTileRenderer.shownAt(mid[0]);
                    s.seenWhileSprinting |= t != null && t >= s.mark + 3 && mc.player.isSprinting();
                    return passed(mc, s, c[0], 0.9);
                })
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .check("no cracks while sprinting", () -> !s.seenWhileSprinting)
                .run("step back", () -> CryptKit.tp(mc, along(s.crypt, c[0], -2.5, 5, 1), along(s.crypt, c[0], 9, 5, 1)))
                .waitTicks(20)
                .hold(mc.options.keyUp)
                .waitUntil("walking up to the first cluster", 80, () -> passed(mc, s, c[0], 0.6))
                .release(mc.options.keyUp)
                .waitTicks(4)
                .check("the cracks show to a careful walker", () -> {
                    Long t = RiftTileRenderer.shownAt(mid[0]);
                    return t != null && mc.level.getGameTime() - t <= 3;
                })
                .run("look down at them", () -> {
                    CryptKit.aim(mc, along(s.crypt, c[0], 3, 5, 1));
                    mc.options.hideGui = true;
                })
                .waitTicks(3)
                .screenshot("crypt_tell_rift_cracks")
                .run("the HUD back", () -> mc.options.hideGui = false)
                .run("back to the start, the timelines noted", () -> {
                    CryptKit.tp(mc, along(s.crypt, c[0], 0.5, 5, 1), along(s.crypt, c[0], 9, 5, 2));
                    before[0] = server(srv -> riftAt(srv, mid[0]).timeline());
                    before[1] = server(srv -> riftAt(srv, mid[1]).timeline());
                    s.before = CryptKit.health();
                })
                .waitTicks(15)
                .hold(mc.options.keyUp)
                .waitUntil("walked the whole corridor", 200, () -> passed(mc, s, c[0], 9.5))
                .release(mc.options.keyUp)
                .waitTicks(20)
                .run("result", () -> {
                    long[] t = server(srv -> riftAt(srv, mid[0]).timeline());
                    long[] t2 = server(srv -> riftAt(srv, mid[1]).timeline());
                    boolean fell = mc.player.getY() < s.crypt.floorY(c[0][0]) - 1;
                    boolean sprang = t[1] != before[0][1] || t2[1] != before[1][1];
                    s.results.add(String.format(Locale.ROOT, "rift walked across: sprang %s, fell %s", sprang, fell));
                    if (fell || sprang) {
                        throw new Steps.Failure("walking across should never spring the tiles");
                    }
                })
                .waitTicks(VoidRiftRules.OPEN_TICKS + VoidRiftRules.REARM_TICKS + 10)
                .run("back to the start for a dash", () -> {
                    CryptKit.tp(mc, along(s.crypt, c[0], 0.3, 5, 1), along(s.crypt, c[0], 9, 5, 2.6));
                    before[0] = server(srv -> riftAt(srv, mid[0]).timeline());
                })
                .waitTicks(15)
                .run("hold Meridian", () -> select(mc, 0))
                .waitTicks(5)
                .check("Meridian in hand, on the ground", () -> mc.player.getMainHandItem().getItem() instanceof com.cosmicbreach.item.CombatWeaponItem
                        && mc.player.onGround())
                .run("mark", () -> s.mark = s.dashes.size())
                .hold(mc.options.keyUp)
                .waitTicks(1)
                .press(com.cosmicbreach.client.combat.ModKeyMappings.DASH)
                .release(mc.options.keyUp)
                .waitTicks(16)
                .check("the dash started", () -> s.dashes.size() > s.mark)
                .run("result", () -> {
                    long[] t = server(srv -> riftAt(srv, mid[0]).timeline());
                    double p = mc.player.position().subtract(along(s.crypt, c[0], 0, 5, 1)).length();
                    String line = String.format(Locale.ROOT, "rift dashed: ended %.2f blocks along, first cluster sprang again: %s (%s; "
                            + "cluster timeline before %s, after %s)", p, t[1] != before[0][1], lastDash(mc, s),
                            java.util.Arrays.toString(before[0]), java.util.Arrays.toString(t));
                    s.results.add(line);
                    if (t[1] != before[0][1]) {
                        throw new Steps.Failure("a dash across should never spring the tiles: " + line);
                    }
                })
                .run("onto the second cluster, then stand still", () -> {
                    CryptKit.heal();
                    s.before = CryptKit.health();
                    CryptKit.tp(mc, along(s.crypt, c[0], 7, 5, 1), along(s.crypt, c[0], 9, 5, 1));
                    s.mark = -1;
                })
                .waitUntil("the floor gives way", 120, () -> mc.player.getY() < s.crypt.floorY(c[0][0]) - 2)
                .waitUntil("landed in the Void Pocket", 60, () -> mc.player.onGround())
                .command("gamemode creative")
                .waitTicks(10)
                .run("result", () -> {
                    long[] t = server(srv -> riftAt(srv, mid[1]).timeline());
                    float lost = s.before - CryptKit.health();
                    VoidPocketBlockEntity pocket = server(srv -> pocketUnder(srv, s, c[0]));
                    s.results.add(String.format(Locale.ROOT, "rift stood on: sprang on tick %d, opened on tick %d, the fall cost %.1f; "
                            + "the pocket woke: %s, Stalkers: %d woke (the test waits them out in creative)", t[1] - t[0], t[2] - t[0], lost,
                            pocket.wokeAt() >= 0,
                            pocket.spawned()));
                    s.mark = mc.level.getGameTime();
                })
                .check("three Hollow Stalkers woke in the pocket", () -> server(srv -> pocketUnder(srv, s, c[0]).spawned())
                        == com.cosmicbreach.structure.crypt.trap.PocketRules.STALKERS)
                .check("the pocket is sealed", () -> server(srv -> pocketUnder(srv, s, c[0]).sealed()))
                .waitUntil("the way out opens after 45 s", 1100, () -> !server(srv -> pocketUnder(srv, s, c[0]).sealed()))
                .run("result", () -> {
                    VoidPocketBlockEntity pocket = server(srv -> pocketUnder(srv, s, c[0]));
                    s.results.add("the pocket's way out opened " + (pocket.openedAt() - pocket.wokeAt()) + " ticks after the fall");
                    if (Math.abs(pocket.openedAt() - pocket.wokeAt() - 900) > 6) {
                        throw new Steps.Failure("the pocket should open after 45 s");
                    }
                })
                .command("kill @e[type=cosmicbreach:hollow_stalker]")
                .command("gamemode survival");
    }

    private void gravity(Steps steps, Minecraft mc, State s) {
        int[][] c = {null};
        BlockPos[] piston = {null};
        Vec3[] sigil = {null};
        steps.run("to the gravity room", () -> {
                    c[0] = cell(s.crypt, Kind.GRAVITY);
                    Vec3 centre = CryptScenario.point(s.crypt, c[0][0], c[0][1], c[0][2], new int[] {5, 5}, 1);
                    sigil[0] = centre;
                    piston[0] = BlockPos.containing(centre.x, s.crypt.floorY(c[0][0]) + 7, centre.z);
                    CryptKit.heal();
                    // along the sigil's side, a block off it: sprinted first, then walked
                    CryptKit.tp(mc, centre.add(-3.0, 0, -3.2), centre.add(4.0, 1.6, -3.2));
                })
                .waitTicks(20)
                .run("clear what was heard", s.hums::clear)
                .run("mark", () -> {
                    s.mark = mc.level.getGameTime();
                    s.seenWhileSprinting = false;
                })
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("sprinted along the sigil", 60, () -> {
                    Long t = GravityPlateRenderer.shownAt(piston[0]);
                    s.seenWhileSprinting |= t != null && t >= s.mark + 3 && mc.player.isSprinting();
                    return mc.player.getX() > sigil[0].x + 3.0;
                })
                .run("note", () -> {
                    s.results.add("gravity plate sprinted past: rings shown while sprinting " + s.seenWhileSprinting + ", hums heard " + s.hums.size());
                    if (s.seenWhileSprinting || !s.hums.isEmpty()) {
                        throw new Steps.Failure("a sprinter should neither see the rings nor hear the hum");
                    }
                })
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .run("back", () -> CryptKit.tp(mc, sigil[0].add(-3.0, 0, -3.2), sigil[0].add(4.0, 1.6, -3.2)))
                .waitTicks(10)
                .run("clear what was heard", s.hums::clear)
                .hold(mc.options.keyUp)
                .waitUntil("walked along the sigil", 200, () -> mc.player.getX() > sigil[0].x + 3.0)
                .release(mc.options.keyUp)
                .waitTicks(45)
                .run("note", () -> {
                    Long t = GravityPlateRenderer.shownAt(piston[0]);
                    s.results.add("gravity plate walked past: rings shown " + (t != null && mc.level.getGameTime() - t < 60)
                            + ", hums heard " + s.hums.size());
                    if (t == null || s.hums.isEmpty()) {
                        throw new Steps.Failure("a careful walker should see the rings and hear the hum");
                    }
                })
                .run("look at the rings", () -> CryptKit.tp(mc, sigil[0].add(-3.6, 0, 0), sigil[0].add(0, 0.4, 0)))
                .waitTicks(10)
                .run("aim", () -> {
                    CryptKit.aim(mc, sigil[0].add(0.8, 0.2, 0));
                    mc.options.hideGui = true;
                })
                .waitTicks(3)
                .screenshot("crypt_tell_gravity_rings")
                .run("the HUD back", () -> mc.options.hideGui = false)
                .run("onto the sigil's middle", () -> {
                    CryptKit.heal();
                    s.before = CryptKit.health();
                    CryptKit.tp(mc, sigil[0], sigil[0].add(3, 1.6, 0));
                })
                .waitUntil("heavy", 40, () -> CryptTraps.heavy(mc.player))
                .run("measure", () -> {
                    s.mark = mc.level.getGameTime();
                    s.results.add(String.format(Locale.ROOT, "on the sigil: gravity %.3f (x%.1f), jump strength %.2f, speed %.3f (x%.2f)",
                            mc.player.getAttributeValue(Attributes.GRAVITY), mc.player.getAttributeValue(Attributes.GRAVITY) / 0.08,
                            mc.player.getAttributeValue(Attributes.JUMP_STRENGTH), mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                            mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1));
                    s.from = mc.player.position();
                })
                .press(mc.options.keyJump)
                .waitTicks(6)
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "jumping while heavy rose %.2f blocks",
                        Math.max(0, mc.player.getY() - s.from.y))))
                .waitUntil("the piston slams", 80, () -> server(srv -> pistonAt(srv, piston[0]).timeline()[1]) >= 0)
                .waitTicks(2)
                .run("result", () -> {
                    long[] t = server(srv -> pistonAt(srv, piston[0]).timeline());
                    float lost = s.before - CryptKit.health();
                    boolean staggered = server(srv -> PoiseTracker.isStaggered(player(srv)));
                    s.results.add(String.format(Locale.ROOT, "the piston slammed on tick %d of the cycle: %.1f damage, staggered: %s",
                            t[1] - t[0], lost, staggered));
                    if (t[1] - t[0] != GravityPlateRules.PISTON_TICK || Math.abs(lost - GravityPlateRules.PISTON_DAMAGE) > 0.01f || !staggered) {
                        throw new Steps.Failure("the piston should slam on tick 40 for 10 damage and a stagger");
                    }
                })
                .run("off the sigil", () -> CryptKit.tp(mc, sigil[0].add(-3.0, 0, 3.2), sigil[0].add(4, 1.6, 3.2)))
                .waitUntil("light again", 80, () -> !CryptTraps.heavy(mc.player))
                .run("result", () -> s.results.add("heavy until " + (mc.level.getGameTime() - s.mark) + " ticks after it was first seen here"))
                .waitTicks(GravityPlateRules.REARM_TICKS + 5)
                .run("a dash, light, beside the sigil", () -> CryptKit.tp(mc, sigil[0].add(-3.0, 0, 3.2), sigil[0].add(4, 1.6, 3.2)))
                .waitTicks(15)
                .run("mark", () -> {
                    select(mc, 0);
                    s.from = mc.player.position();
                })
                .waitTicks(5)
                .hold(mc.options.keyUp)
                .waitTicks(1)
                .press(com.cosmicbreach.client.combat.ModKeyMappings.DASH)
                .release(mc.options.keyUp)
                .waitTicks(12)
                .run("result", () -> s.results.add("a dash, light: " + lastDash(mc, s)))
                .run("onto the sigil's middle again", () -> {
                    CryptKit.heal();
                    s.before = CryptKit.health();
                    CryptKit.tp(mc, sigil[0], sigil[0].add(-6, 1.6, 0));
                })
                .waitUntil("heavy", 40, () -> CryptTraps.heavy(mc.player))
                .run("mark", () -> s.from = mc.player.position())
                .hold(mc.options.keyUp)
                .waitTicks(1)
                .press(com.cosmicbreach.client.combat.ModKeyMappings.DASH)
                .release(mc.options.keyUp)
                .waitTicks(12)
                .run("result", () -> s.results.add("a dash, heavy: " + lastDash(mc, s)))
                .hold(mc.options.keyUp)
                .waitTicks(12)
                .release(mc.options.keyUp)
                .waitUntil("the piston slams", 80, () -> {
                    long[] t = server(srv -> pistonAt(srv, piston[0]).timeline());
                    return t[1] >= 0 && t[1] > t[0];
                })
                .waitTicks(2)
                .run("result", () -> {
                    float lost = s.before - CryptKit.health();
                    long[] t = server(srv -> pistonAt(srv, piston[0]).timeline());
                    s.results.add(String.format(Locale.ROOT, "dashed and walked off in time: %.1f damage, %d hit by the slam", lost, t[2]));
                    if (lost > 0.01f) {
                        throw new Steps.Failure("getting off before tick 40 should be safe");
                    }
                });
    }

    private void chutes(Steps steps, Minecraft mc, State s) {
        int[][] c = {null};
        BlockPos[] chute = {null, null};
        steps.run("to the chute corridor's door", () -> {
                    c[0] = cell(s.crypt, Kind.CHUTES);
                    chute[0] = BlockPos.containing(along(s.crypt, c[0], 2, 5, 7));
                    chute[1] = BlockPos.containing(along(s.crypt, c[0], 5, 5, 7));
                    CryptKit.heal();
                    CryptKit.tp(mc, along(s.crypt, c[0], -2.8, 5, 1), along(s.crypt, c[0], 5, 5, 1.2));
                })
                .waitTicks(10)
                .waitUntil("the first chute's glint is about to start", 100, () -> glintIn(mc, chute[0]) == ChuteRules.GLINT_LEAD + 1)
                .run("mark", () -> {
                    s.mark = mc.level.getGameTime();
                    s.seenWhileSprinting = false;
                })
                .hold(mc.options.keySprint)
                .hold(mc.options.keyUp)
                .waitUntil("sprinted in during the glint", 20, () -> {
                    Long t = ChuteRenderer.shownAt(chute[0]);
                    s.seenWhileSprinting |= t != null && t >= s.mark + 3 && mc.player.isSprinting();
                    return mc.level.getGameTime() >= s.mark + 9;
                })
                .release(mc.options.keyUp)
                .release(mc.options.keySprint)
                .run("result", () -> {
                    s.results.add("chute glint while sprinting in: shown " + s.seenWhileSprinting);
                    if (s.seenWhileSprinting) {
                        throw new Steps.Failure("a sprinter should not see the glint");
                    }
                })
                .run("back, then stand and watch", () -> CryptKit.tp(mc, along(s.crypt, c[0], 0.9, 5, 1), along(s.crypt, c[0], 2, 5, 7)))
                .waitTicks(10)
                .waitUntil("a glint shows to a careful player", 120, () -> {
                    Long t = ChuteRenderer.shownAt(chute[0]);
                    return t != null && mc.level.getGameTime() - t <= 1;
                })
                .run("note", () -> s.results.add("glint shown " + glintIn(mc, chute[0]) + " ticks before its drop, to a player standing still"))
                .run("look up", () -> {
                    CryptKit.aim(mc, along(s.crypt, c[0], 2, 5, 6.9));
                    mc.options.hideGui = true;
                })
                .waitTicks(6)
                .screenshot("crypt_tell_chute_glint")
                .run("the HUD back", () -> mc.options.hideGui = false)
                .run("under the middle strip", () -> {
                    CryptKit.heal();
                    s.before = CryptKit.health();
                    CryptKit.tp(mc, along(s.crypt, c[0], 5.5, 5, 1), along(s.crypt, c[0], 9, 5, 1.6));
                })
                .waitTicks(3)
                .run("mark", () -> s.mark = server(srv -> chuteAt(srv, chute[1]).timeline()[0]))
                .waitUntil("a star-rock lands", 120, () -> server(srv -> chuteAt(srv, chute[1]).timeline()[0]) > s.mark)
                .waitTicks(2)
                .run("result", () -> {
                    long[] t = server(srv -> chuteAt(srv, chute[1]).timeline());
                    StarfallChuteBlockEntity be = server(srv -> chuteAt(srv, chute[1]));
                    float lost = s.before - CryptKit.health();
                    s.results.add(String.format(Locale.ROOT, "a star-rock landed %d ticks after its release (period %d, phase %d): %.1f damage",
                            ChuteRules.sinceRelease(t[0], be.period(), be.phase()), be.period(), be.phase(), lost));
                    if (Math.abs(lost - ChuteRules.DAMAGE) > 0.01f) {
                        throw new Steps.Failure("a star-rock should cost 8 health");
                    }
                })
                .run("out of the way", () -> CryptKit.tp(mc, along(s.crypt, c[0], -5.5, 5, 1), along(s.crypt, c[0], 5, 5, 1.2)))
                .waitTicks(5)
                .run("a view down the corridor", () -> camera(mc, s, along(s.crypt, c[0], -3.0, 5, 2.4), along(s.crypt, c[0], 6, 5, 3.4)))
                .waitUntil("a rock is about to fall", 100, () -> {
                    StarfallChuteBlockEntity be = (StarfallChuteBlockEntity) mc.level.getBlockEntity(chute[1]);
                    return ChuteRules.untilRelease(mc.level.getGameTime(), be.period(), be.phase()) == 1;
                })
                .waitTicks(2)
                .screenshot("crypt_corridor_chutes")
                .run("back to the player", () -> uncamera(s));
    }

    /** Ticks until chute {@code pos} releases (this client's copy). */
    private static int glintIn(Minecraft mc, BlockPos pos) {
        StarfallChuteBlockEntity be = (StarfallChuteBlockEntity) mc.level.getBlockEntity(pos);
        return be == null ? -1 : ChuteRules.untilRelease(mc.level.getGameTime(), be.period(), be.phase());
    }

    // ------------------------------------------------------------------ helpers

    private static ServerLevel level(MinecraftServer srv) {
        return srv.getLevel(AetheriaWorld.LEVEL);
    }

    private static VaultBlockEntity vaultOf(MinecraftServer srv, State s) {
        if (level(srv).getBlockEntity(s.crypt.vault()) instanceof VaultBlockEntity v) {
            return v;
        }
        throw new Steps.Failure("no vault at " + s.crypt.vault());
    }

    private static VoidRiftBlockEntity riftAt(MinecraftServer srv, BlockPos pos) {
        if (level(srv).getBlockEntity(pos) instanceof VoidRiftBlockEntity r) {
            return r;
        }
        throw new Steps.Failure("no Void Rift cluster at " + pos);
    }

    private static VoidPocketBlockEntity pocketUnder(MinecraftServer srv, State s, int[] rift) {
        BlockPos p = BlockPos.containing(CryptScenario.point(s.crypt, rift[0] + 1, rift[1], rift[2], new int[] {5, 5}, 0));
        if (level(srv).getBlockEntity(p) instanceof VoidPocketBlockEntity pocket) {
            return pocket;
        }
        throw new Steps.Failure("no Void Pocket at " + p);
    }

    private static GravityPistonBlockEntity pistonAt(MinecraftServer srv, BlockPos pos) {
        if (level(srv).getBlockEntity(pos) instanceof GravityPistonBlockEntity p) {
            return p;
        }
        throw new Steps.Failure("no Gravity Piston at " + pos);
    }

    private static StarfallChuteBlockEntity chuteAt(MinecraftServer srv, BlockPos pos) {
        if (level(srv).getBlockEntity(pos) instanceof StarfallChuteBlockEntity c) {
            return c;
        }
        throw new Steps.Failure("no Starfall Chute at " + pos);
    }

    /** The last dash: how far the player got from where it started in the 11 ticks since, its scale, heavy or not. */
    private static String lastDash(Minecraft mc, State s) {
        if (s.dashes.isEmpty()) {
            return "no dash started";
        }
        double[] d = s.dashes.get(s.dashes.size() - 1);
        double moved = Math.hypot(mc.player.getX() - d[1], mc.player.getZ() - d[2]);
        return String.format(Locale.ROOT, "%.2f blocks from its start (scale %.2f, heavy %s, %d ticks ago)", moved, d[3], d[4] > 0,
                mc.level.getGameTime() - (long) d[0]);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static void select(Minecraft mc, int k) {
        net.minecraft.client.KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
    }

    private static void camera(Minecraft mc, State s, Vec3 eye, Vec3 target) {
        s.camera = DevCamera.create(mc.level);
        s.camera.place(eye, target);
        s.camera.use();
        mc.options.hideGui = true;
    }

    private static void uncamera(State s) {
        Minecraft.getInstance().options.hideGui = false;
        if (s.camera != null) {
            s.camera.remove();
            s.camera = null;
        }
    }

    static ConductorBlockEntity conductor(MinecraftServer srv, State s) {
        return level(srv).getBlockEntity(s.crypt.conductor()) instanceof ConductorBlockEntity c ? c : null;
    }
}
