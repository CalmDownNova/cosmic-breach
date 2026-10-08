package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.lift.LiftClient;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.lift.RiftLift;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code mp-existing} (join mode, against a copy of an existing world on a dedicated server): the way back up beside the
 * boss 1 and boss 2 shrines, found in the old world by the placing pass, carries a survival player up and sets them down in
 * front of the shrine above without damage; and the boss 2 arena's rescue lift, which that world's arena never had, catches
 * a walk off an inner rim, an outer rim and a jump from the bowl, each set down on a platform at the first try in under 8
 * seconds with no damage. Everything from the client's side: commands, keys and what the client sees.
 */
public final class MpExistingScenario implements Scenario {
    private static final Pattern CURRENT = Pattern.compile(
            "current (\\w+) (-?\\d+\\.\\d) (-?\\d+\\.\\d) from (-?\\d+) to (-?\\d+) land (-?\\d+\\.\\d) (-?\\d+\\.\\d)");
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private List<String[]> currents = List.of();
    private @Nullable RiftLayout layout;
    private @Nullable Vec3 standAt;
    private @Nullable Vec3 lookAt;
    private @Nullable Vec3 jumpFrom;
    private float health;
    private long start;
    private int catches;
    private boolean wasRiding;
    private int mark;

    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    private void tp(double x, double y, double z, double lx, double ly, double lz) {
        MpKit.send("tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", x, y, z, lx, ly, lz);
    }

    private void fly() {
        if (mc.player != null && mc.player.isCreative() && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
    }

    private java.util.function.BooleanSupplier hovering(java.util.function.BooleanSupplier settled) {
        return () -> {
            fly();
            return settled.getAsBoolean();
        };
    }

    private @Nullable String[] currentOf(String kind) {
        for (String[] c : currents) {
            if (c[0].equals(kind)) {
                return c;
            }
        }
        return null;
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private boolean standingBy(String[] c) {
        return mc.player.onGround() && Math.hypot(mc.player.getX() - d(c[5]), mc.player.getZ() - d(c[6])) <= 1.5
                && Math.abs(mc.player.getY() - d(c[4])) < 0.6;
    }

    private void climb(Steps steps, String kind) {
        steps.command("gamemode creative")
                .run("to the " + kind + " shrine", () -> MpKit.send("cosmicbreach debug shrines go %s", kind))
                .waitTicks(20)
                .waitUntil("in Aetheria, the view drawn", 1600, hovering(LeviathanScenario.settled(mc, 1200)))
                .check("the server listed a current for this shrine (" + kind + ")", () -> currentOf(kind) != null)
                .run("in its foot", () -> {
                    String[] c = currentOf(kind);
                    tp(d(c[1]), d(c[3]), d(c[2]), d(c[5]), d(c[4]) + 1.6, d(c[6]));
                })
                .waitTicks(40)
                .waitUntil("the foot is drawn", 1600, hovering(LeviathanScenario.settled(mc, 1200)))
                .waitUntil("this client knows the currents", 400, () -> LiftClient.knownCurrents() >= 1)
                .command("gamemode survival")
                .run("health now", () -> {
                    health = mc.player.getHealth();
                    start = mc.level.getGameTime();
                })
                .waitUntil("carried (" + kind + ")", 200, LiftClient::ascending)
                .waitUntil("set down in front of the shrine above (" + kind + ")", 900, () -> !LiftClient.ascending() && standingBy(currentOf(kind)))
                .log(kind + " ascent", () -> {
                    long t = mc.level.getGameTime() - start;
                    results.add("way up (" + kind + ") " + t + " ticks");
                    return "way up (" + kind + "): in front of the shrine above after " + t + " ticks, health " + mc.player.getHealth();
                })
                .check("no damage on the way up (" + kind + ")", () -> mc.player.getHealth() >= health)
                .check("set down outside the current (" + kind + ")", () -> {
                    String[] c = currentOf(kind);
                    return Math.hypot(mc.player.getX() - d(c[1]), mc.player.getZ() - d(c[2])) > 2.5;
                })
                .waitTicks(60)
                .check("and not lifted again (" + kind + ")", () -> !LiftClient.ascending() && standingBy(currentOf(kind)))
                .command("gamemode creative");
    }

    @SuppressWarnings("unchecked")
    private @Nullable RiftLayout nearestRift() {
        try {
            Field f = LiftClient.class.getDeclaredField("RIFTS");
            f.setAccessible(true);
            Map<BlockPos, RiftLayout> rifts = (Map<BlockPos, RiftLayout>) f.get(null);
            RiftLayout best = null;
            double bestD = Double.MAX_VALUE;
            for (RiftLayout l : rifts.values()) {
                double dd = l.centre().distanceTo(mc.player.position());
                if (dd < bestD) {
                    best = l;
                    bestD = dd;
                }
            }
            return best;
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot read the client's Rifts: " + e);
        }
    }

    private static boolean solid(net.minecraft.world.level.Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private double standY(int k) {
        return RiftLift.standY(layout.platform(k));
    }

    private boolean onAPlatform() {
        if (!mc.player.onGround()) {
            return false;
        }
        Vec3 me = mc.player.position();
        for (RiftLayout.Platform p : layout.platforms()) {
            double over = me.y - RiftLift.standY(p);
            if (Math.hypot(me.x - p.x(), me.z - p.z()) <= p.radius() + 0.7 && over >= -0.6 && over <= 4.0) {
                return true;
            }
        }
        return false;
    }

    private void trackCatches() {
        boolean now = LiftClient.riding();
        if (now && !wasRiding) {
            catches++;
        }
        wasRiding = now;
    }

    private void findWalkOff(int k, boolean outer) {
        RiftLayout.Platform p = layout.platform(k);
        Vec3 c = layout.centre();
        double middle = Math.atan2(c.z - p.z(), c.x - p.x()) + (outer ? Math.PI : 0.0);
        double stand = RiftLift.standY(p);
        var level = mc.level;
        Vec3 found = null;
        for (int i = 0; i < 13 && found == null; i++) {
            double angle = middle + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.15;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean open = solid(level, (int) Math.floor(p.x() + dx * (p.radius() - 1.2)), (int) stand - 1,
                    (int) Math.floor(p.z() + dz * (p.radius() - 1.2)));
            boolean edge = false;
            for (double dd = p.radius() - 1.5; dd <= p.radius() + 2.5 && open; dd += 0.25) {
                int bx = (int) Math.floor(p.x() + dx * dd);
                int bz = (int) Math.floor(p.z() + dz * dd);
                open = !solid(level, bx, (int) stand, bz) && !solid(level, bx, (int) stand + 1, bz);
                if (dd >= p.radius() + 0.5 && !solid(level, bx, (int) stand - 1, bz)) {
                    edge = true;
                }
            }
            if (open && edge) {
                found = new Vec3(p.x() + dx * (p.radius() - 1.2), stand, p.z() + dz * (p.radius() - 1.2));
            }
        }
        if (found == null) {
            throw new Steps.Failure("no clear way off platform " + k + "'s " + (outer ? "outer" : "inner") + " rim");
        }
        standAt = found;
        Vec3 dir = found.subtract(new Vec3(p.x(), stand, p.z())).normalize();
        lookAt = found.add(dir.scale(10)).add(0, 1.6, 0);
    }

    private void pickJumpSpot(int k) {
        RiftLayout.Platform p = layout.platform(k);
        Vec3 c = layout.centre();
        var level = mc.level;
        jumpFrom = null;
        for (int i = 0; i < 24 && jumpFrom == null; i++) {
            double a = p.angle() + 0.1 + i * 0.15;
            double r = 20.0;
            double x = c.x + r * Math.cos(a);
            double z = c.z + r * Math.sin(a);
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            for (int y = (int) Math.ceil(layout.bowlSurface(r)) + 14; y >= (int) Math.floor(layout.bowlSurface(r)) - 6; y--) {
                if (solid(level, bx, y, bz) && !solid(level, bx, y + 1, bz) && !solid(level, bx, y + 2, bz)) {
                    Vec3 feet = new Vec3(x, y + 1.0, z);
                    if (RiftLift.start(layout, feet.add(0, 0.3, 0)) != null && RiftLift.start(layout, feet.add(0, 0.55, 0)) != null
                            && RiftLift.start(layout, feet.add(0, 0.9, 0)) != null) {
                        jumpFrom = feet;
                    }
                    break;
                }
            }
        }
        if (jumpFrom == null) {
            throw new Steps.Failure("no open spot to jump from in the bowl under platform " + k);
        }
    }

    private void fallOff(Steps steps, int k, boolean outer) {
        String label = (outer ? "outer" : "inner") + " rim";
        steps.command("gamemode creative")
                .run("onto platform " + k + "'s " + label + ", facing off it", () -> {
                    findWalkOff(k, outer);
                    tp(standAt.x, standAt.y, standAt.z, lookAt.x, lookAt.y, lookAt.z);
                })
                .waitTicks(15)
                .command("gamemode survival")
                .waitUntil("standing in survival", 80, () -> !mc.player.isCreative() && mc.player.onGround())
                .run("health now", () -> health = mc.player.getHealth())
                .hold(mc.options.keyUp)
                .waitUntil("off the " + label, 80, () -> !mc.player.onGround() && mc.player.getY() < standY(k) - 0.5)
                .release(mc.options.keyUp)
                .run("the fall starts", () -> {
                    start = mc.level.getGameTime();
                    catches = 0;
                    wasRiding = false;
                })
                .waitUntil("the Rift's air catches the fall", 120, () -> {
                    trackCatches();
                    return LiftClient.riding();
                })
                .waitUntil("set down on a platform again", 260, () -> {
                    trackCatches();
                    return onAPlatform();
                })
                .log(label, () -> {
                    long t = mc.level.getGameTime() - start;
                    results.add("lift " + label + " " + t + " ticks");
                    return "lift " + label + ": on a platform again after " + t + " ticks";
                })
                .check("under 8 s from the fall to standing (" + label + ")", () -> mc.level.getGameTime() - start < 160)
                .check("caught once, set down at the first try (" + label + ")", () -> catches == 1)
                .check("no fall damage (" + label + ")", () -> mc.player.getHealth() >= health);
    }

    private void fromTheBowl(Steps steps, int k) {
        steps.command("gamemode creative")
                .run("onto platform " + k, () -> {
                    Vec3 at = layout.platform(k).topCentre();
                    tp(at.x, at.y, at.z, layout.centre().x, at.y + 1.0, layout.centre().z);
                })
                .waitTicks(15)
                .command("gamemode survival")
                .waitUntil("standing on the platform in survival", 80, () -> !mc.player.isCreative() && mc.player.onGround())
                .run("find an open spot of the bowl under it", () -> pickJumpSpot(k))
                .run("down onto the bowl", () -> {
                    Vec3 c = layout.centre();
                    tp(jumpFrom.x, jumpFrom.y, jumpFrom.z, c.x, jumpFrom.y + 2.0, c.z);
                })
                .waitUntil("standing on the bowl's floor", 160, () -> mc.player.onGround() && !LiftClient.riding() && mc.player.getY() < jumpFrom.y + 1.5)
                .run("health now", () -> health = mc.player.getHealth())
                .press(mc.options.keyJump)
                .run("the jump", () -> {
                    start = mc.level.getGameTime();
                    catches = 0;
                    wasRiding = false;
                })
                .waitUntil("caught and lifted", 80, () -> {
                    trackCatches();
                    return LiftClient.riding();
                })
                .waitUntil("set down on a platform", 260, () -> {
                    trackCatches();
                    return onAPlatform();
                })
                .log("bowl", () -> {
                    long t = mc.level.getGameTime() - start;
                    results.add("lift bowl " + t + " ticks");
                    return "lift bowl: on a platform after " + t + " ticks";
                })
                .check("under 8 s from the bowl to a platform", () -> mc.level.getGameTime() - start < 160)
                .check("caught once, set down at the first try (bowl)", () -> catches == 1)
                .check("no damage from the bowl", () -> mc.player.getHealth() >= health);
    }

    @Override
    public void steps(Steps steps) {
        MpKit.listen();
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .run("mark the chat", () -> mark = MpKit.chatSize())
                .command("cosmicbreach debug shrines currents")
                .waitUntil("the server lists the currents", 100, () -> !MpKit.allMatches(CURRENT, mark).isEmpty())
                .waitTicks(10)
                .run("read them", () -> currents = MpKit.allMatches(CURRENT, mark))
                .log("currents", () -> "the old world has " + currents.size() + " rising currents, for " + currents.stream().map(c -> c[0]).toList());
        climb(steps, "colossus");
        climb(steps, "leviathan");
        steps.command("gamemode creative")
                .waitUntil("the client has the Rift's lift zone", 600, () -> {
                    fly();
                    return LiftClient.knownRifts() >= 1;
                })
                .run("find the Rift", () -> {
                    layout = nearestRift();
                    if (layout == null) {
                        throw new Steps.Failure("no Rift known to the client");
                    }
                })
                .run("into the Rift", () -> {
                    Vec3 at = layout.platform(0).topCentre();
                    tp(at.x, at.y, at.z, layout.centre().x, at.y + 1, layout.centre().z);
                })
                .waitTicks(40)
                .waitUntil("the Rift is drawn", 1600, hovering(LeviathanScenario.settled(mc, 1200)));
        fallOff(steps, 3, false);
        fallOff(steps, 5, true);
        fromTheBowl(steps, 6);
        steps.command("gamemode creative").log("results", () -> String.join(" | ", results));
    }
}
