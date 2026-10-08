package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.lift.LiftClient;
import com.cosmicbreach.client.lift.StreamRenderer;
import com.cosmicbreach.client.lift.StreamSound;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.lift.Lifts;
import com.cosmicbreach.lift.RiftLift;
import com.cosmicbreach.structure.trap.UpdraftRiders;
import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.GravityTide;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The boss 2 arena's rescue lift and air vents (1.1 design section 6), part {@code rift-lift}, in a Rift built by the
 * debug command with its Leviathan asleep (the player is attuned past it), real inputs: walking off an inner rim and off
 * an outer rim, and jumping from the bowl floor, each ends standing on a platform in under 8 seconds with no damage;
 * both vents' streams are drawn and the near one is heard, seen from a platform across the arena in daylight and at dusk;
 * the rider's trail is captured on the way up from the bowl, from the side and from a platform across the arena; the
 * Leviathan sleeps through all of it; a player carried up a vent is not on the lift; a fall and a jump in a Gravity Tide (the
 * flow of the Drift's weather would carry a rider let go in the air off an inner rim) are set down at the first try. Then the same falls
 * with the Leviathan awake (its attacks held), which the lift neither wakes, hurts nor changes, and once more after its
 * kill. The times are logged in ticks (20 a second) for the report.
 */
public final class RiftLiftScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private float health;
    private long leftAt;
    /** How many times the lift caught the player since the fall or the jump began, and whether it held them on the last tick looked at. */
    private int catches;
    private boolean wasRiding;
    private @Nullable Vec3 jumpFrom;
    /** Where the next fall starts (a platform's rim, a block or two short of the edge) and what the player looks at: found when the step runs. */
    private @Nullable Vec3 standAt;
    private @Nullable Vec3 lookAt;

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }

    private static RiftLayout layout() {
        RiftLayout l = LeviathanScenario.layout();
        if (l == null) {
            throw new Steps.Failure("no Rift was built");
        }
        return l;
    }

    private static double standY(int k) {
        return RiftLift.standY(layout().platform(k));
    }

    /** True when the player stands on a platform: its top, or a boulder or crystal standing on it. */
    private boolean onAPlatform() {
        if (!mc.player.onGround()) {
            return false;
        }
        Vec3 me = mc.player.position();
        for (RiftLayout.Platform p : layout().platforms()) {
            double over = me.y - RiftLift.standY(p);
            if (Math.hypot(me.x - p.x(), me.z - p.z()) <= p.radius() + 0.7 && over >= -0.6 && over <= 4.0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built, its Leviathan asleep", 400, () -> LeviathanScenario.layout() != null
                        && ServerQuery.ask(p -> LeviathanScenario.lev(p) != null && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT))
                .run("into the Rift", () -> {
                    Vec3 at = layout().platform(0).topCentre();
                    ColossusScenario.tp(mc, at.x, at.y, at.z, layout().centre().x, at.y + 1, layout().centre().z);
                })
                .waitUntil("the client has the Rift's lift zone", 200, () -> LiftClient.knownRifts() >= 1);
        fallOff(steps, 3, false, "");
        fallOff(steps, 5, true, "");
        vents(steps);
        upAVent(steps);
        fromTheBowl(steps, 6, "", watching -> sideShots(watching, "lift_trail"));
        fromTheBowl(steps, 6, "far view", watching -> farShots(watching, FAR_PLATFORM, "lift_trail_far"));
        sinking(steps);
        inATide(steps);
        steps.check("the Leviathan slept through it all", () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) != null
                && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT));
        withTheLeviathanAwake(steps);
        afterTheKill(steps);
        steps.log("results", () -> String.join(" | ", results));
    }

    /**
     * Wakes the Leviathan (the player is attuned, so the debug push is all it takes), holds its attacks off so the check is
     * about the lift and not the fight, and lets players fall off an inner and an outer rim: the lift catches them as
     * ever, and the Leviathan stays in its fight with its health untouched.
     */
    private void withTheLeviathanAwake(Steps steps) {
        float[] health = {0f};
        steps.command("gamemode creative")
                .run("onto platform 0 to wake it", () -> {
                    Vec3 at = layout().platform(0).topCentre();
                    ColossusScenario.tp(mc, at.x, at.y, at.z, layout().centre().x, at.y + 1, layout().centre().z);
                })
                .waitTicks(10)
                .command("cosmicbreach debug leviathan awaken")
                .waitUntil("it wakes", 40, () -> LeviathanScenario.is(ThalassineLeviathan.State.INTRO))
                .waitUntil("the intro is over", 400, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                .command("cosmicbreach debug leviathan hold 1000000")
                .run("its health now", () -> health[0] = LeviathanScenario.ask(ThalassineLeviathan::getHealth));
        fallOff(steps, 3, false, "awake");
        fallOff(steps, 5, true, "awake");
        steps.check("the lift left it in its fight, its health untouched", () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT)
                && LeviathanScenario.ask(ThalassineLeviathan::getHealth) == health[0]);
        // the vents with the Leviathan swimming its orbit between a platform and them: nothing of them clutters its path or hides it
        steps.command("gamemode creative")
                .run("on platform 0's inner rim, looking across at the vent, the HUD hidden", () -> {
                    RiftLayout l = layout();
                    RiftLayout.Platform p = l.platform(0);
                    Vec3 c = l.centre();
                    Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
                    Vec3 at = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(in.scale(p.radius() - 1.2));
                    RiftLayout.Updraft u = l.updrafts().get(0);
                    ColossusScenario.tp(mc, at.x, at.y, at.z, u.x() + 0.5, (u.bottom() + u.top()) / 2.0 + 8.0, u.z() + 0.5);
                    mc.options.hideGui = true;
                    mc.gui.getChat().clearMessages(false);
                });
        for (int i = 0; i < 6; i++) {
            steps.waitTicks(35).screenshot("whale_vent_" + i);
        }
        steps.run("the HUD again", () -> mc.options.hideGui = false);
    }

    /** Kills the Leviathan (the debug push, the kill plays out as ever, and it is gone) and lets a player fall off a rim and the bowl's jump: the lift still works. */
    private void afterTheKill(Steps steps) {
        steps.command("gamemode creative")
                .command("cosmicbreach debug leviathan kill")
                .waitUntil("the kill plays out and it is gone", 900, () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) == null))
                .waitTicks(40);
        fallOff(steps, 3, false, "after the kill");
        fromTheBowl(steps, 2, "after the kill", null);
    }

    private void fallOff(Steps steps, int k, boolean outer, String when) {
        fallOff(steps, () -> k, "platform " + k, outer, when);
    }

    /** A fall off the rim of the platform {@code platform} picks at run time ({@code name} says which, for the steps' names). */
    private void fallOff(Steps steps, IntSupplier platform, String name, boolean outer, String when) {
        String side = outer ? "outer" : "inner";
        String label = when.isEmpty() ? side + " rim" : when + ", " + side + " rim";
        String shot = when.isEmpty() ? "lift_" + side : "lift_" + when.replace(' ', '_') + "_" + side;
        steps.command("gamemode creative")
                .run("onto " + name + "'s " + side + " rim, facing off it", () -> {
                    findWalkOff(platform.getAsInt(), outer);
                    ColossusScenario.tp(mc, standAt.x, standAt.y, standAt.z, lookAt.x, lookAt.y, lookAt.z);
                })
                .waitTicks(10)
                .command("gamemode survival")
                .waitUntil("standing in survival", 80, () -> !mc.player.isCreative() && mc.player.onGround())
                .run("health now", () -> health = mc.player.getHealth())
                .hold(mc.options.keyUp)
                .waitUntil("off the " + side + " rim", 80, () -> !mc.player.onGround() && mc.player.getY() < standY(platform.getAsInt()) - 0.5)
                .release(mc.options.keyUp)
                .run("the fall starts", () -> {
                    leftAt = mc.level.getGameTime();
                    catches = 0;
                    wasRiding = false;
                })
                .waitUntil("the Rift's air catches the fall", 120, () -> {
                    trackCatches();
                    return LiftClient.riding();
                })
                .screenshot(shot)
                .waitUntil("set down on a platform again", 220, () -> {
                    trackCatches();
                    return onAPlatform();
                })
                .log(label, () -> {
                    long t = mc.level.getGameTime() - leftAt;
                    results.add(label + " " + t + " ticks");
                    return label + ": on a platform again after " + t + " ticks";
                })
                .check("under 8 s from the fall to standing (" + label + ")", () -> mc.level.getGameTime() - leftAt < 160)
                .check("caught once, set down at the first try (" + label + ")", () -> catches == 1)
                .check("no fall damage (" + label + ")", () -> mc.player.getHealth() >= health);
    }

    /** Counts the times the lift takes hold of the player: once for a clean rescue, more if they were let go in the air and caught again. */
    private void trackCatches() {
        boolean now = LiftClient.riding();
        if (now && !wasRiding) {
            catches++;
        }
        wasRiding = now;
    }

    /**
     * Finds a way off platform {@code k}'s rim on the side toward the core (or away from it, {@code outer}) with nothing in the
     * way on the real blocks: the player stands a block and a bit in from the edge and walks straight out over it. A boulder
     * or a rim crystal can stand on any one line, so the lines either side of the middle one are tried too.
     */
    private void findWalkOff(int k, boolean outer) {
        RiftLayout l = layout();
        RiftLayout.Platform p = l.platform(k);
        Vec3 c = l.centre();
        double middle = Math.atan2(c.z - p.z(), c.x - p.x()) + (outer ? Math.PI : 0.0);
        double stand = RiftLift.standY(p);
        Vec3 found = ServerQuery.ask(player -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < 13; i++) {
                double angle = middle + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.15;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                // from a block short of where the player will stand (not from the platform's middle: the boulders on its inner half
                // leave only a narrow gap there) out over the rim
                boolean open = solid(level, (int) Math.floor(p.x() + dx * (p.radius() - 1.2)), (int) stand - 1,
                        (int) Math.floor(p.z() + dz * (p.radius() - 1.2)));
                boolean edge = false;
                for (double d = p.radius() - 1.5; d <= p.radius() + 2.5 && open; d += 0.25) {
                    int bx = (int) Math.floor(p.x() + dx * d);
                    int bz = (int) Math.floor(p.z() + dz * d);
                    // nothing at the feet or head on the way (a bridge's planks and a boulder's rock are in the way too)
                    open = !solid(level, bx, (int) stand, bz) && !solid(level, bx, (int) stand + 1, bz);
                    if (d >= p.radius() + 0.5 && !solid(level, bx, (int) stand - 1, bz)) {
                        edge = true; // and past the rim there is nothing under it
                    }
                }
                if (open && edge) {
                    return new Vec3(p.x() + dx * (p.radius() - 1.2), stand, p.z() + dz * (p.radius() - 1.2));
                }
            }
            return null;
        });
        if (found == null) {
            throw new Steps.Failure("no clear way off platform " + k + "'s " + (outer ? "outer" : "inner") + " rim");
        }
        standAt = found;
        Vec3 dir = found.subtract(new Vec3(p.x(), stand, p.z())).normalize();
        lookAt = found.add(dir.scale(10)).add(0, 1.6, 0);
    }

    /** Finds the open floor of the bowl under platform {@code k} the lift would take someone from, on the server's real blocks. */
    private void pickJumpSpot(int k) {
        RiftLayout l = layout();
        RiftLayout.Platform p = l.platform(k);
        Vec3 c = l.centre();
        jumpFrom = ServerQuery.ask(player -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < 24; i++) {
                double a = p.angle() + 0.1 + i * 0.15;
                double r = 20.0;
                double x = c.x + r * Math.cos(a);
                double z = c.z + r * Math.sin(a);
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                for (int y = (int) Math.ceil(l.bowlSurface(r)) + 14; y >= (int) Math.floor(l.bowlSurface(r)) - 6; y--) {
                    if (solid(level, bx, y, bz) && !solid(level, bx, y + 1, bz) && !solid(level, bx, y + 2, bz)) {
                        Vec3 feet = new Vec3(x, y + 1.0, z);
                        // the lift looks at them from their first airborne tick (a jump rises 0.55 in the first), so it must
                        // take them from a little under, at and over that height
                        if (RiftLift.start(l, feet.add(0, 0.3, 0)) != null && RiftLift.start(l, feet.add(0, 0.55, 0)) != null
                                && RiftLift.start(l, feet.add(0, 0.9, 0)) != null) {
                            return feet;
                        }
                        break; // under rock: some other spot
                    }
                }
            }
            return null;
        });
        if (jumpFrom == null) {
            throw new Steps.Failure("no open spot to jump from in the bowl under platform " + k);
        }
    }

    private static boolean solid(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * A jump from the bowl's floor under platform {@code k} ({@code when} names the situation for the log: empty for the plain
     * run), with the steps that take pictures of the rider on the way up, if any.
     */
    private void fromTheBowl(Steps steps, int k, String when, @Nullable Consumer<Steps> watching) {
        String label = when.isEmpty() ? "bowl" : when + ", bowl";
        jumpFromTheBowl(steps, k);
        if (watching != null) {
            watching.accept(steps);
        }
        steps.waitUntil("set down on a platform", 260, () -> {
                    trackCatches();
                    return onAPlatform();
                })
                .log(label, () -> {
                    long t = mc.level.getGameTime() - leftAt;
                    results.add(label + " " + t + " ticks");
                    return label + ": on a platform after " + t + " ticks";
                })
                .check("under 8 s from the bowl to a platform (" + label + ")", () -> mc.level.getGameTime() - leftAt < 160)
                .check("caught once, set down at the first try (" + label + ")", () -> catches == 1)
                .check("no damage from the bowl (" + label + ")", () -> mc.player.getHealth() >= health);
    }

    /** Everything up to the lift catching a jump from the bowl's floor under platform {@code k}: a standing survival player on the floor, a jump, caught and rising. */
    private void jumpFromTheBowl(Steps steps, int k) {
        steps.command("gamemode creative")
                .run("onto platform " + k + " to start from standing in survival", () -> {
                    Vec3 at = layout().platform(k).topCentre();
                    ColossusScenario.tp(mc, at.x, at.y, at.z, layout().centre().x, at.y + 1.0, layout().centre().z);
                })
                .waitTicks(10)
                .command("gamemode survival")
                .waitUntil("standing on the platform in survival", 80, () -> !mc.player.isCreative() && mc.player.onGround())
                .run("find an open spot of the bowl under platform " + k, () -> pickJumpSpot(k))
                .run("down onto the bowl under platform " + k + " (from standing, so the lift does not catch them in the air)", () -> {
                    Vec3 c = layout().centre();
                    ColossusScenario.tp(mc, jumpFrom.x, jumpFrom.y, jumpFrom.z, c.x, jumpFrom.y + 2.0, c.z);
                })
                .waitUntil("standing on the bowl's floor", 120, () -> mc.player.onGround() && !LiftClient.riding() && mc.player.getY() < jumpFrom.y + 1.5)
                .run("health now", () -> health = mc.player.getHealth())
                .press(mc.options.keyJump)
                .run("the jump", () -> {
                    leftAt = mc.level.getGameTime();
                    catches = 0;
                    wasRiding = false;
                })
                .waitTicks(3)
                .log("after the jump", () -> {
                    Vec3 me = mc.player.position();
                    RiftLayout l = layout();
                    return String.format(java.util.Locale.ROOT, "jumped from %s: now at %.2f %.2f %.2f, moving %s, on ground %b, below the line %b, the lift would take them %b, riding %b",
                            jumpFrom, me.x, me.y, me.z, mc.player.getDeltaMovement(), mc.player.onGround(), RiftLift.caught(l, me),
                            RiftLift.start(l, me) != null, LiftClient.riding());
                })
                .waitUntil("caught and lifted", 60, () -> {
                    trackCatches();
                    return LiftClient.riding();
                });
    }

    /**
     * The quality review's M4: a player an air vent carries up from the bowl is not on the lift, for the client (which stands aside for
     * the vent) and now for the server too: nobody hears a catch's whoosh, the server does not list them, and no ribbon is drawn
     * behind them.
     */
    private void upAVent(Steps steps) {
        steps.command("gamemode creative")
                .run("in the middle of vent 1's column, then in survival", () -> {
                    RiftLayout.Updraft u = layout().updrafts().get(1);
                    ColossusScenario.tp(mc, u.x() + 0.5, u.bottom() + 12.0, u.z() + 0.5, u.x() + 0.5, u.top() + 5.0, u.z() + 0.5);
                })
                .waitTicks(5)
                .command("gamemode survival")
                .waitUntil("a vent is carrying them up", 80, () -> UpdraftRiders.riding(mc.player, mc.level.getGameTime()) && mc.player.getDeltaMovement().y > 0.3)
                .waitTicks(10)
                .check("the vent still carries them up", () -> UpdraftRiders.riding(mc.player, mc.level.getGameTime()))
                .check("the client's lift stands aside", () -> !LiftClient.riding())
                .check("the server's mirror does not list them either", () -> ServerQuery.ask(p -> !Lifts.riding(p)))
                .check("no ribbon behind them", () -> StreamRenderer.trailsDrawnLastFrame() == 0);
    }

    /**
     * The Drift's weather reaches inside the arena (the quality review's I1): in a Gravity Tide (its gravity, and its current of 0.06 a
     * tick along its heading, ticks of the game's own weather) a fall off the inner rim of the platform the current blows toward the
     * core from, where a rider let go in the air over an inner landing was carried off the edge and looped, ends on that platform at
     * the first try; so does a jump from the bowl.
     */
    private void inATide(Steps steps) {
        steps.command("gamemode creative")
                .command("cosmicbreach weather tide")
                .command("cosmicbreach weather skip")
                .waitUntil("the Tide runs", 100, () -> GravityTide.active(mc.level))
                .log("the Tide", () -> String.format(Locale.ROOT, "Gravity Tide: heading %.0f degrees, gravity %.2f; it blows toward the core from platform %d",
                        Math.toDegrees(CosmicWeather.client(Layer.DRIFT).heading()), AetheriaGravity.multiplier(mc.level, mc.player.getY()), flowFacingPlatform()));
        fallOff(steps, this::flowFacingPlatform, "the platform the Tide blows toward the core from", false, "in a Tide");
        fromTheBowl(steps, 6, "in a Tide", null);
        steps.command("cosmicbreach weather clear")
                .waitUntil("the Tide is over", 100, () -> !GravityTide.active(mc.level));
    }

    /** The platform whose inner rim faces into the Tide's current: the one it blows toward the core from, the worst case for an inner landing. */
    private int flowFacingPlatform() {
        double heading = CosmicWeather.client(Layer.DRIFT).heading();
        int best = 0;
        double bestToward = -2.0;
        for (RiftLayout.Platform p : layout().platforms()) {
            double toward = -Math.cos(heading - p.angle()); // the current's share along the way from that platform to the core
            if (toward > bestToward) {
                bestToward = toward;
                best = p.index();
            }
        }
        return best;
    }

    /**
     * A rider who sneaks is lowered, not carried up, and trails nothing (the spec re-review's R5): the trail is drawn while they
     * rise, and 26 ticks into sneaking, after the last of it has faded (it lasts 20), no trail is drawn although they are still
     * on the lift.
     */
    private void sinking(Steps steps) {
        jumpFromTheBowl(steps, 6);
        steps.waitUntil("they are 8 blocks up", 120, () -> mc.player.getY() > jumpFrom.y + 8.0)
                .check("their trail is drawn while they rise", () -> StreamRenderer.trailsDrawnLastFrame() >= 1)
                .hold(mc.options.keyShift)
                .waitTicks(26)
                .check("sneaking, they are lowered on the lift and trail nothing", () -> LiftClient.riding() && StreamRenderer.trailsDrawnLastFrame() == 0)
                .release(mc.options.keyShift);
    }

    /**
     * The rising trail of someone lifted from the bowl (a long climb, so a long trail), seen as everyone else sees it: a
     * watching camera (the dev camera, which exists only in this client) keeps 8 blocks to the side of them, level with their
     * head, looking a little under their feet so the trail lies in the picture below them. Shots a third of the way up and
     * most of the way up.
     */
    private void sideShots(Steps steps, String name) {
        DevCamera[] cam = {null};
        steps.run("watch from the side", () -> {
                    cam[0] = DevCamera.create(mc.level);
                    watch(cam[0], WATCH);
                    cam[0].use();
                    mc.options.hideGui = true;
                    mc.gui.getChat().clearMessages(false);
                })
                .waitUntil("they are 14 blocks up", 120, () -> {
                    watch(cam[0], WATCH);
                    return mc.player.getY() > jumpFrom.y + 14.0;
                })
                .check("their trail is drawn", () -> StreamRenderer.trailsDrawnLastFrame() >= 1)
                .screenshot(name + "_side")
                .waitUntil("they are 30 blocks up", 120, () -> {
                    watch(cam[0], WATCH);
                    return mc.player.getY() > jumpFrom.y + 30.0;
                })
                .screenshot(name + "_high")
                .run("back to the player's own view", () -> {
                    cam[0].remove();
                    mc.options.hideGui = false;
                });
    }

    /** How far the watching camera keeps from the rider, in blocks. */
    private static final double WATCH = 8.0;

    /** Puts the watching camera {@code distance} blocks to the side of the player (the side with open air), level with their head, looking a little under their feet. */
    private void watch(DevCamera cam, double distance) {
        Vec3 me = mc.player.position();
        Vec3 c = layout().centre();
        Vec3 radial = new Vec3(me.x - c.x, 0, me.z - c.z).normalize();
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        Vec3 eye = me.add(tangent.scale(distance)).add(0, 1.0, 0);
        if (!mc.level.getBlockState(BlockPos.containing(eye)).isAir()) {
            eye = me.add(tangent.scale(-distance)).add(0, 1.0, 0);
        }
        cam.place(eye, me.add(0, -3.0, 0));
    }

    /** The platform across the arena from platform 6, under which the jump starts: the far platform the lift is watched from. */
    private static final int FAR_PLATFORM = 2;

    /**
     * The same climb watched from the far side: the dev camera stands on the inner rim of platform {@code platform}, at a player's
     * eye height, and turns to keep the rider in the picture (a player standing there would see the same). Shots just off the floor
     * and half way up, where the rider is seen against the bowl and not against the platform's crystals.
     */
    private void farShots(Steps steps, int platform, String name) {
        DevCamera[] cam = {null};
        steps.run("watch from platform " + platform + "'s inner rim", () -> {
                    cam[0] = DevCamera.create(mc.level);
                    watchFrom(cam[0], platform);
                    cam[0].use();
                    mc.options.hideGui = true;
                    mc.gui.getChat().clearMessages(false);
                })
                .waitUntil("they are 9 blocks up", 120, () -> {
                    watchFrom(cam[0], platform);
                    return mc.player.getY() > jumpFrom.y + 9.0;
                })
                .check("their trail is drawn", () -> StreamRenderer.trailsDrawnLastFrame() >= 1)
                .screenshot(name + "_low")
                .waitUntil("they are 22 blocks up", 120, () -> {
                    watchFrom(cam[0], platform);
                    return mc.player.getY() > jumpFrom.y + 22.0;
                })
                .screenshot(name + "_mid")
                .run("back to the player's own view", () -> {
                    cam[0].remove();
                    mc.options.hideGui = false;
                });
    }

    /** Puts the watching camera at a player's eye on platform {@code platform}'s inner rim, looking at the rider's middle. */
    private void watchFrom(DevCamera cam, int platform) {
        RiftLayout l = layout();
        RiftLayout.Platform p = l.platform(platform);
        Vec3 c = l.centre();
        Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
        Vec3 stand = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(in.scale(p.radius() - 1.2));
        cam.place(stand.add(0, 1.62, 0), mc.player.position().add(0, 0.9, 0));
    }

    private void vents(Steps steps) {
        steps.command("gamemode creative")
                .run("on platform 0's inner rim, looking across the arena at the first air vent", () -> {
                    // where a player stands to look across: a block and a bit in from the inner rim (the bell and its posts are on the other side),
                    // looking at the vent a dozen blocks under its top
                    RiftLayout l = layout();
                    RiftLayout.Platform p = l.platform(0);
                    Vec3 c = l.centre();
                    Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
                    Vec3 at = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(in.scale(p.radius() - 1.2));
                    RiftLayout.Updraft u = l.updrafts().get(0);
                    ColossusScenario.tp(mc, at.x, at.y, at.z, u.x() + 0.5, u.top() - 12.0, u.z() + 0.5);
                    mc.options.hideGui = true; // the pictures show the arena only: no chat, hotbar or crosshair over it
                    mc.gui.getChat().clearMessages(false);
                })
                .waitTicks(20)
                .screenshot("vent_from_platform")
                .check("both vents' streams are drawn", () -> StreamRenderer.drawnLastFrame() >= 2)
                .run("beside that vent", () -> {
                    RiftLayout.Updraft u = layout().updrafts().get(0);
                    ColossusScenario.tp(mc, u.x() + 3.5, u.top() - 1.0, u.z() + 0.5, u.x() + 0.5, u.top(), u.z() + 0.5);
                })
                .waitTicks(20)
                .check("its rising air is heard", () -> StreamSound.playing() >= 1)
                .run("inside that vent's column, looking up it", () -> {
                    RiftLayout.Updraft u = layout().updrafts().get(0);
                    ColossusScenario.tp(mc, u.x() + 0.5, (u.bottom() + u.top()) / 2.0, u.z() + 0.5, u.x() + 0.5, u.top() + 20.0, u.z() + 0.5);
                })
                .waitTicks(10)
                .screenshot("vent_inside")
                .run("down in the bowl, looking up at a vent", () -> {
                    // the open floor of the bowl under platform 6 (the one the jump starts from), looking up at that platform's vent
                    pickJumpSpot(6);
                    RiftLayout.Updraft u = layout().updrafts().get(1);
                    ColossusScenario.tp(mc, jumpFrom.x, jumpFrom.y + 0.1, jumpFrom.z, u.x() + 0.5, (u.bottom() + u.top()) / 2.0, u.z() + 0.5);
                })
                .waitTicks(20)
                .screenshot("vent_from_bowl");
        dusk(steps, "vent_at_dusk");
        // from the far side of the arena: the same vent from the platform 135 degrees round from it, then from the one opposite
        // (a vent stands outside its own platform, so from the opposite one the platform is in the line of sight and the
        // vent shows below it and above it, in its plume), and its top and plume against the sky
        farView(steps, 5, 0, "vent_far", Double.NaN);
        farView(steps, 6, 0, "vent_opposite", Double.NaN);
        farView(steps, 0, 0, "vent_plume", 6.0);
        steps.run("the HUD again", () -> mc.options.hideGui = false);
    }

    /** Dusk: waits long enough for the sky to follow the clock, takes the picture, puts noon back. */
    private void dusk(Steps steps, String name) {
        steps.command("time set 13000")
                .waitTicks(40)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot(name)
                .command("time set noon")
                .waitTicks(5);
    }

    /**
     * Daylight and dusk views of vent {@code vent} from the inner rim of platform {@code platform}, across the arena, looking at
     * the middle of its length (or, with {@code aimAboveTop} a number, at that many blocks over its top).
     */
    private void farView(Steps steps, int platform, int vent, String name, double aimAboveTop) {
        steps.command("gamemode creative")
                .run("on platform " + platform + "'s inner rim, looking across at vent " + vent, () -> {
                    RiftLayout l = layout();
                    RiftLayout.Platform p = l.platform(platform);
                    Vec3 c = l.centre();
                    Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
                    Vec3 at = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(in.scale(p.radius() - 1.2));
                    RiftLayout.Updraft u = l.updrafts().get(vent);
                    results.add(String.format(java.util.Locale.ROOT, "%s %.0f blocks", name, Math.hypot(at.x - (u.x() + 0.5), at.z - (u.z() + 0.5))));
                    double aimY = Double.isNaN(aimAboveTop) ? (u.bottom() + u.top()) / 2.0 : u.top() + aimAboveTop;
                    ColossusScenario.tp(mc, at.x, at.y, at.z, u.x() + 0.5, aimY, u.z() + 0.5);
                    mc.options.hideGui = true;
                    mc.gui.getChat().clearMessages(false);
                })
                .waitTicks(25)
                .screenshot(name + "_day");
        dusk(steps, name + "_dusk");
    }
}
