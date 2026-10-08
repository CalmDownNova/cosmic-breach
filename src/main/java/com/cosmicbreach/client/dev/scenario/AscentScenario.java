package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.lift.LiftClient;
import com.cosmicbreach.client.lift.StreamRenderer;
import com.cosmicbreach.client.lift.StreamSound;
import com.cosmicbreach.guardian.GuardianCommands;
import com.cosmicbreach.lift.AscentCurrent;
import com.cosmicbreach.lift.Lifts;
import com.cosmicbreach.shrine.ShrineData;
import com.cosmicbreach.shrine.ShrineKind;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code ascent}: the way back up (1.1 design section 5) with real inputs, in survival. For each of the two shrines that stand
 * over a drop: the current beside it is found and the client knows it; its stream is drawn and seen from 40 blocks off; a player
 * put in its foot (as one who has walked into it) is carried up and set down in front of the shrine above with no damage, the
 * server seeing it too; a player dropped in from above lands on the floor at its foot and is not carried back up, even
 * standing in it, until they jump; a player who sneaks is not carried, and a sneak while carried lets go.
 */
public final class AscentScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private AscentCurrent.Current current;
    private float health;
    private long start;
    private boolean everAscended;
    private AscentCurrent.Current saved;

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }

    private static @Nullable AscentCurrent.Current currentOf(ServerPlayer p, ShrineKind kind) {
        ShrineData data = ShrineData.get(p.serverLevel());
        for (Map.Entry<String, ShrineData.Placed> e : data.all().entrySet()) {
            if (e.getValue().kind() == kind && data.current(e.getKey()).isPresent()) {
                return data.current(e.getKey()).get();
            }
        }
        return null;
    }

    /** A spot about {@code dist} blocks from the current's axis, in open air, with a clear line to the middle of its stream; the axis itself if none. */
    private Vec3 viewSpot(AscentCurrent.Current c, double dist) {
        return ServerQuery.ask(p -> {
            double span = c.top() - c.bottom();
            for (double share : new double[] {0.5, 0.3, 0.7, 0.15, 0.85}) {
                double y = c.bottom() + span * share;
                Vec3 mid = new Vec3(c.x(), y, c.z());
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4.0;
                    Vec3 feet = new Vec3(c.x() + Math.cos(a) * dist, y - 1.62, c.z() + Math.sin(a) * dist);
                    BlockPos fp = BlockPos.containing(feet);
                    var level = p.serverLevel();
                    boolean open = level.getBlockState(fp).getCollisionShape(level, fp).isEmpty()
                            && level.getBlockState(fp.above()).getCollisionShape(level, fp.above()).isEmpty();
                    if (open && level.clip(new ClipContext(feet.add(0, 1.62, 0), mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                            .getType() == HitResult.Type.MISS) {
                        return feet;
                    }
                }
            }
            return new Vec3(c.x() + dist, c.bottom() + span * 0.5, c.z());
        });
    }

    /** A hovering creative player lands (and so stops flying) on the first tick after a teleport; say it again. */
    private void fly() {
        if (mc.player.isCreative() && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
    }

    /** {@code settled} for a hovering player: holds them up while the view loads. */
    private java.util.function.BooleanSupplier hovering(java.util.function.BooleanSupplier settled) {
        return () -> {
            fly();
            return settled.getAsBoolean();
        };
    }

    private String where() {
        return String.format(Locale.ROOT, "at %.1f %.1f %.1f, creative %b flying %b, health %.1f, alive %b", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.isCreative(), mc.player.getAbilities().flying, mc.player.getHealth(), mc.player.isAlive());
    }

    private boolean standingBy(AscentCurrent.Current c) {
        return mc.player.onGround() && Math.hypot(mc.player.getX() - c.landX(), mc.player.getZ() - c.landZ()) <= 1.5
                && Math.abs(mc.player.getY() - c.top()) < 0.6;
    }

    /** Waits for the carried player to be set down, noting how long it took. */
    private void arrive(Steps steps, String tag, String what) {
        steps.waitUntil("set down in front of the shrine above (" + what + ")", 700, () -> !LiftClient.ascending() && standingBy(current))
                .log(tag + " " + what, () -> {
                    long t = mc.level.getGameTime() - start;
                    results.add(tag + " " + what + " " + t + " ticks");
                    return tag + " " + what + ": in front of the shrine after " + t + " ticks, health " + mc.player.getHealth();
                })
                .check("no damage on the way up (" + tag + " " + what + ")", () -> mc.player.getHealth() >= health)
                .check("set down outside the current (" + tag + " " + what + ")", () -> Math.hypot(mc.player.getX() - current.x(), mc.player.getZ() - current.z())
                        > AscentCurrent.CATCH + 1.0);
    }

    private void climb(Steps steps, ShrineKind kind, String tag) {
        steps.waitUntil("its current is found", 1200, () -> (current = ServerQuery.ask(p -> currentOf(p, kind))) != null)
                .log(tag + " current", () -> String.format(Locale.ROOT, "%s current at %.1f %.1f from %.0f to %.0f, landing %.1f %.1f", tag,
                        current.x(), current.z(), current.bottom(), current.top(), current.landX(), current.landZ()))
                .check("its landing is clear of it", () -> current.landingClear())
                // seen from afar
                .run("forty blocks off, looking at it", () -> {
                    Vec3 at = viewSpot(current, 40.0);
                    ColossusScenario.tp(mc, at.x, at.y, at.z, current.x(), at.y + 1.62, current.z());
                })
                .log("viewing from", this::where)
                .waitUntil("this client knows it", 400, () -> LiftClient.knownCurrents() >= 1)
                .waitUntil("the view is drawn", 600, hovering(LeviathanScenario.settled(mc, 400)))
                .log("viewing from", this::where)
                .waitTicks(20)
                .check("its stream is drawn", () -> StreamRenderer.drawnLastFrame() >= 1)
                .check("and its air is heard", () -> StreamSound.playing() >= 1)
                .screenshot(tag + "_seen")
                .run("and from 120 blocks", () -> {
                    Vec3 at = viewSpot(current, 120.0);
                    ColossusScenario.tp(mc, at.x, at.y, at.z, current.x(), at.y + 1.62, current.z());
                })
                .waitUntil("the view is drawn", 600, hovering(LeviathanScenario.settled(mc, 400)))
                .waitTicks(20)
                .screenshot(tag + "_far");
        // a world saved before the currents existed: the shrine is saved, the current is not, and the next placing pass finds it again
        steps.run("remember it", () -> saved = current)
                .command("cosmicbreach debug shrines currents reset")
                .waitUntil("none is saved", 100, () -> ServerQuery.ask(p -> currentOf(p, kind) == null))
                .command("cosmicbreach debug shrines place")
                .waitUntil("the same current is found again", 200, () -> ServerQuery.ask(p -> saved.equals(currentOf(p, kind))))
                .waitUntil("this client has it again", 200, () -> LiftClient.knownCurrents() >= 1);
        // a walker or stander in its foot is carried
        steps.run("in its foot", () -> ColossusScenario.tp(mc, current.x(), current.bottom(), current.z(), current.landX(), current.top() + 1.6, current.landZ()))
                .waitUntil("the view is drawn", 600, LeviathanScenario.settled(mc, 400))
                .command("gamemode survival")
                .run("health now", () -> {
                    health = mc.player.getHealth();
                    start = mc.level.getGameTime();
                })
                .waitUntil("carried", 80, LiftClient::ascending)
                .check("the server sees it too", () -> ServerQuery.ask(Lifts::ascending))
                .waitTicks(30)
                .screenshot(tag + "_rising");
        arrive(steps, tag, "from its foot");
        steps.screenshot(tag + "_arrived")
                .waitTicks(60)
                .check("and not lifted again, set down outside it", () -> !LiftClient.ascending() && standingBy(current))
                .command("gamemode creative");
        // dropped in from above lands normally
        steps.run("high over it", () -> ColossusScenario.tp(mc, current.x(), current.top() + 60.0, current.z(), current.x(), current.top(), current.z()))
                .command("gamemode survival")
                .run("health now", () -> {
                    health = mc.player.getHealth();
                    everAscended = false;
                })
                .waitUntil("dropped to the floor at its foot, never carried", 1500, () -> {
                    everAscended |= LiftClient.ascending();
                    return mc.player.onGround() && mc.player.getY() < current.top() - 30.0;
                })
                .waitTicks(100)
                .log(tag + " drop", () -> String.format(Locale.ROOT, "%s drop: landed at y %.1f (its foot %.0f), health %.1f of %.1f, ever carried: %b", tag,
                        mc.player.getY(), current.bottom(), mc.player.getHealth(), health, everAscended))
                .check("never carried on the way down or after landing in it", () -> !everAscended && !LiftClient.ascending())
                .check("alive", () -> mc.player.isAlive())
                .screenshot(tag + "_dropped");
        // and jumping in it is walking into it
        steps.press(mc.options.keyJump, 2)
                .waitUntil("a jump in it carries them", 40, LiftClient::ascending)
                .run("health now", () -> {
                    health = mc.player.getHealth();
                    start = mc.level.getGameTime();
                });
        arrive(steps, tag, "after a drop and a jump");
        // sneaking in its foot is not carried; standing up is (the sneak is held before they are put there, in survival, as a walker's would be)
        steps.hold(mc.options.keyShift)
                .run("in its foot again, sneaking", () -> ColossusScenario.tp(mc, current.x(), current.bottom(), current.z(), current.landX(), current.top() + 1.6,
                        current.landZ()))
                .waitTicks(60)
                .check("a sneaking player in its foot is not carried", () -> !LiftClient.ascending())
                .release(mc.options.keyShift)
                .waitTicks(3)
                .waitUntil("carried once they stand up", 40, LiftClient::ascending)
                .waitTicks(15)
                .hold(mc.options.keyShift)
                .waitTicks(4)
                .check("a sneak lets go", () -> !LiftClient.ascending())
                .waitUntil("and drops back to the floor", 600, () -> mc.player.onGround() && mc.player.getY() < current.bottom() + 2.0)
                .release(mc.options.keyShift)
                .command("gamemode creative");
    }

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the Crown Spire is built", 400, () -> GuardianCommands.lastBuilt() != null);
        climb(steps, ShrineKind.COLOSSUS, "gap_a");
        steps.command("cosmicbreach debug goto drift")
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built", 400, () -> LeviathanScenario.layout() != null)
                .run("to its ledge", () -> {
                    Vec3 at = LeviathanScenario.layout().ledgeTop();
                    ColossusScenario.tp(mc, at.x, at.y, at.z, LeviathanScenario.layout().centre().x, at.y, LeviathanScenario.layout().centre().z);
                })
                .waitUntil("its shrine is placed", 800, () -> ServerQuery.ask(p -> ShrineData.get(p.serverLevel()).all().values().stream()
                        .anyMatch(s -> s.kind() == ShrineKind.LEVIATHAN)));
        climb(steps, ShrineKind.LEVIATHAN, "gap_b");
        steps.log("results", () -> String.join(" | ", results));
    }
}
