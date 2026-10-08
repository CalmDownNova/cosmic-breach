package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.leviathan.LeviathanClient;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanOrbit;
import com.cosmicbreach.guardian.leviathan.LeviathanRegistry;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.Polyline;
import com.cosmicbreach.guardian.leviathan.RiftBellBlock;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ShedScale;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.LayerAttunement;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * The Leviathan's mechanics with real inputs (parts {@code leviathan}, {@code leviathan-moorage}, {@code leviathan-repeat}):
 * the player is level 24 with Power 7 and Resilience 16 of its own, in the Driftweave, the Comet Maul in hand; it moves
 * with the movement keys (looking where it walks), strikes with the attack key, dashes and parries with their keys, rings
 * the bell with the use key. Debug commands only hold off the Leviathan's own choices, force an attack to test its answer,
 * or skip health ahead.
 */
final class LeviathanMechanics {
    private final LeviathanScenario s;
    private final double[] marks = new double[16];
    private final long[] ticks = new long[8];

    LeviathanMechanics(LeviathanScenario scenario) {
        this.s = scenario;
    }

    // ------------------------------------------------------------------ helpers

    private static RiftLayout layout() {
        return LeviathanScenario.layout();
    }

    private static <T> T ask(java.util.function.Function<ThalassineLeviathan, T> query) {
        return LeviathanScenario.ask(query);
    }

    private static void lookFlat(Minecraft mc, Vec3 target) {
        ColossusScenario.lookAt(mc, new Vec3(target.x, mc.player.getEyeY(), target.z));
    }

    private static void key(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    private static void click(KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
    }

    /** Walks (holding forward, and jump over steps) toward {@code target} until within {@code within} blocks, flat. */
    static void walk(Steps steps, Minecraft mc, String what, Supplier<Vec3> target, double within, int timeout) {
        steps.waitUntil(what, timeout, () -> {
            Vec3 t = target.get();
            double d = Math.hypot(t.x - mc.player.getX(), t.z - mc.player.getZ());
            if (d <= within) {
                key(mc.options.keyUp, false);
                key(mc.options.keyJump, false);
                key(mc.options.keySprint, false);
                return true;
            }
            lookFlat(mc, t);
            key(mc.options.keyUp, true);
            key(mc.options.keySprint, d > 4.0);
            key(mc.options.keyJump, mc.player.horizontalCollision);
            return false;
        });
        steps.run("stop", () -> {
            key(mc.options.keyUp, false);
            key(mc.options.keyJump, false);
            key(mc.options.keySprint, false);
        });
    }

    private static void releaseAll(Minecraft mc) {
        for (KeyMapping k : new KeyMapping[] {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump,
                mc.options.keySprint, mc.options.keyAttack, mc.options.keyUse, ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            key(k, false);
        }
    }

    private static void standOn(Minecraft mc, RiftLayout.Platform p, double fromEdge) {
        Vec3 c = layout().centre();
        Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
        // never nearer the axis than the rock goes (the rim is trimmed clear of the orbit), less 0.8
        Vec3 at = p.topCentre().add(in.scale(Math.min(p.radius() - fromEdge, RiftLayout.PLATFORM_RING - RiftLayout.CLEAR_RADIUS - 0.8)));
        ColossusScenario.tp(mc, at.x, at.y, at.z, c.x, c.y, c.z);
    }

    private float takenSince(int from) {
        float sum = 0f;
        List<Float> d = s.playerDamage;
        for (int i = from; i < d.size(); i++) {
            sum += d.get(i);
        }
        return sum;
    }

    private void heal(Steps steps) {
        steps.command("effect give @s minecraft:instant_health 1 4 true").waitTicks(2);
    }

    private static int count(Minecraft mc, Item item) {
        return ServerQuery.ask(p -> p.getInventory().countItem(item));
    }

    /** In through the entrance: walk from the ledge along the walkway until platform 0; entering the sphere wakes it. */
    private void arrive(Steps steps, Minecraft mc, boolean expectWake) {
        s.equip(steps, mc);
        steps.command("gamerule naturalRegeneration true")
                .run("onto the ledge outside the entrance", () -> {
                    Vec3 ledge = layout().ledgeTop();
                    Vec3 c = layout().centre();
                    ColossusScenario.tp(mc, ledge.x, ledge.y, ledge.z, c.x, ledge.y + 1.6, c.z);
                })
                .waitTicks(10)
                .check("outside the sphere, it sleeps", () -> ServerQuery.ask(p -> !layout().inside(p.position()))
                        && LeviathanScenario.is(ThalassineLeviathan.State.DORMANT));
        walk(steps, mc, "along the walkway to platform 0 (sprinting)", () -> {
            RiftLayout.Platform p0 = layout().platform(0);
            return p0.topCentre();
        }, 2.5, 600);
        if (expectWake) {
            steps.waitUntil("entering the sphere woke it (an unattuned player)", 60, () -> !LeviathanScenario.is(ThalassineLeviathan.State.DORMANT))
                    .log("awakened", () -> ask(l -> {
                        String r = String.format(Locale.ROOT, "entering the Rift woke it: %s, %.0f health for %d player(s)", l.state(), l.getMaxHealth(),
                                l.playersAtStart());
                        s.summary.add(r);
                        return r;
                    }))
                    .check((int) LeviathanMoves.BASE_HEALTH + " health for one player", () -> ask(l ->
                            Math.abs(l.getMaxHealth() - (float) LeviathanMoves.BASE_HEALTH) < 0.01f))
                    .waitUntil("the intro is over (10 s)", 260, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                    .check("the boss music plays", com.cosmicbreach.client.guardian.GuardianMusic::active)
                    .command("cosmicbreach debug leviathan hold 1000000");
        }
    }

    // ------------------------------------------------------------------ part leviathan: each attack and its answer

    void steps(Steps steps, Minecraft mc) {
        arrive(steps, mc, true);
        dive(steps, mc);
        song(steps, mc);
        flick(steps, mc);
        shed(steps, mc);
        steps.run("release every key", () -> releaseAll(mc));
    }

    private void dive(Steps steps, Minecraft mc) {
        int[] from = {0};
        Vec3[] dest = {null};
        steps.run("on platform 0, forget the damage so far", () -> {
                    standOn(mc, layout().platform(0), 2.0);
                    from[0] = s.playerDamage.size();
                })
                .waitTicks(10)
                .command("cosmicbreach debug leviathan attack dive")
                .waitUntil("it chose the dive", 20, () -> ask(l -> l.action() == LeviathanTactics.Attack.DIVE))
                .waitUntil("the wake reached this client", 20, () -> ask(l -> l.divePath() != null))
                .waitTicks(8)
                .run("out of the wake: square to the path, staying on the platform", () -> {
                    Polyline path = ask(ThalassineLeviathan::divePath);
                    Vec3 me = mc.player.position();
                    double best = Double.MAX_VALUE;
                    double at = 0;
                    for (double d = 0; d <= path.length(); d += 0.5) {
                        double dd = path.at(d).distanceToSqr(me);
                        if (dd < best) {
                            best = dd;
                            at = d;
                        }
                    }
                    Vec3 along = path.direction(at);
                    Vec3 side = new Vec3(-along.z, 0, along.x).normalize();
                    RiftLayout.Platform p0 = layout().platform(0);
                    Vec3 centre = p0.topCentre();
                    Vec3 a = me.add(side.scale(4.5));
                    Vec3 b = me.subtract(side.scale(4.5));
                    dest[0] = a.distanceTo(centre) < b.distanceTo(centre) ? a : b;
                });
        walk(steps, mc, "walk out of the wake", () -> dest[0], 0.6, 60);
        steps.waitUntil("the dive is over", 300, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE))
                .log("dive dodged", () -> {
                    float t = takenSince(from[0]);
                    String r = String.format(Locale.ROOT, "Breach Dive, left the wake: took %.1f", t);
                    s.summary.add(r);
                    return r;
                })
                .check("leaving the wake took no dive damage", () -> takenSince(from[0]) == 0f)
                .run("stand still in its path this time", () -> {
                    standOn(mc, layout().platform(0), 2.0);
                    from[0] = s.playerDamage.size();
                })
                .waitTicks(20)
                .command("cosmicbreach debug leviathan attack dive")
                .waitUntil("the dive struck", 300, () -> s.playerDamage.size() > from[0])
                .log("dive hit", () -> {
                    String r = String.format(Locale.ROOT, "Breach Dive, stood in the wake: took %.2f through the Driftweave and Resilience 16 (design: about 10)",
                            s.playerDamage.get(from[0]));
                    s.summary.add(r);
                    return r;
                })
                .check("about 10 (7 to 13)", () -> s.playerDamage.get(from[0]) >= 7f && s.playerDamage.get(from[0]) <= 13f)
                .waitUntil("the dive is over", 300, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE));
        heal(steps);
    }

    private void song(Steps steps, Minecraft mc) {
        int[] bites = {0};
        // dash out of the pull
        steps.run("on platform 0", () -> {
                    standOn(mc, layout().platform(0), 1.0);
                    bites[0] = ask(l -> l.counts()[2]);
                })
                .waitUntil("its head comes within the song's reach", 900, () -> ServerQuery.ask(p ->
                        LeviathanScenario.lev(p).mouth().distanceTo(p.getBoundingBox().getCenter()) < 20.0))
                .command("cosmicbreach debug leviathan attack song")
                .waitUntil("the song's telegraph", 20, () -> ask(l -> l.action() == LeviathanTactics.Attack.SONG))
                .waitUntil("it pulls", 60, () -> ask(l -> l.level().getGameTime() - l.actionStart() >= LeviathanMoves.SONG_TELL + 8))
                .run("pulled so far", () -> marks[0] = LeviathanClient.pulled())
                .waitTicks(6)
                .log("pull", () -> String.format(Locale.ROOT, "pulled %.2f in 6 ticks, rock between %s, server says blocked %s, in the cone %s",
                        LeviathanClient.pulled() - marks[0], LeviathanClient.lastBlocked(), ServerQuery.ask(p -> LeviathanScenario.lev(p).songBlocked(p)),
                        ServerQuery.ask(p -> com.cosmicbreach.guardian.leviathan.SongPull.inCone(LeviathanScenario.lev(p).songCone(), p.getBoundingBox().getCenter()))))
                .check("the pull moves the player toward the mouth", () -> LeviathanClient.pulled() - marks[0] > 0.3)
                .run("face the mouth", () -> ColossusScenario.lookAt(mc, ask(ThalassineLeviathan::mouth)))
                .run("dash back out of it", () -> click(ModKeyMappings.DASH))
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.DASH, false))
                .waitTicks(3)
                .run("pulled after the dash", () -> marks[1] = LeviathanClient.pulled())
                .waitTicks(14)
                .check("a dash breaks the pull (nothing for its 20 ticks)", () -> LeviathanClient.pulled() - marks[1] < 0.01)
                .run("face the mouth", () -> ColossusScenario.lookAt(mc, ask(ThalassineLeviathan::mouth)))
                .run("dash again as it ends", () -> click(ModKeyMappings.DASH))
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.DASH, false))
                .waitTicks(18)
                .run("face the mouth", () -> ColossusScenario.lookAt(mc, ask(ThalassineLeviathan::mouth)))
                .run("and again", () -> click(ModKeyMappings.DASH))
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.DASH, false))
                .waitTicks(18)
                .run("face the mouth", () -> ColossusScenario.lookAt(mc, ask(ThalassineLeviathan::mouth)))
                .run("and once more (a charge back by now)", () -> click(ModKeyMappings.DASH))
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.DASH, false))
                .waitUntil("the song is over", 120, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE))
                .log("song dashed", () -> {
                    String r = String.format(Locale.ROOT, "Song of Pulling, dashed: pulled %.2f blocks in all, bites %d", LeviathanClient.pulled() - marks[0]
                            + 0.0, ask(l -> l.counts()[2]) - bites[0]);
                    s.summary.add(r);
                    return r;
                })
                .check("no bite", () -> ask(l -> l.counts()[2]) == bites[0]);
        heal(steps);
        // hide behind a boulder on the platform
        RiftLayout.Platform[] cover = {null};
        steps.run("behind a boulder on platform 0, the boulder toward the orbit", () -> {
                    RiftLayout.Platform p0 = layout().platform(0);
                    cover[0] = p0;
                    Vec3 b = layout().boulders(0).get(0);
                    Vec3 c = layout().centre();
                    Vec3 out = new Vec3(b.x - c.x, 0, b.z - c.z).normalize();
                    Vec3 at = new Vec3(b.x, p0.top() + 1.0, b.z).add(out.scale(RiftLayout.BOULDER + 0.9));
                    ColossusScenario.tp(mc, at.x, at.y, at.z, b.x, b.y, b.z);
                })
                .waitTicks(5)
                .waitUntil("its head within the song's reach with the boulder between", 900, () -> ServerQuery.ask(p -> {
                    ThalassineLeviathan l = LeviathanScenario.lev(p);
                    Vec3 me = p.getBoundingBox().getCenter();
                    Vec3 b = layout().boulders(0).get(0);
                    Vec3 m = l.mouth();
                    Vec3 flatToHead = new Vec3(m.x - me.x, 0, m.z - me.z).normalize();
                    Vec3 flatToRock = new Vec3(b.x - me.x, 0, b.z - me.z).normalize();
                    return m.distanceTo(me) < 24.0 && flatToHead.dot(flatToRock) > 0.93;
                }))
                .command("cosmicbreach debug leviathan attack song")
                .waitUntil("the song's telegraph", 20, () -> ask(l -> l.action() == LeviathanTactics.Attack.SONG))
                .waitUntil("it pulls", 60, () -> ask(l -> l.level().getGameTime() - l.actionStart() >= LeviathanMoves.SONG_TELL + 2))
                .run("pulled so far", () -> marks[2] = LeviathanClient.pulled())
                .waitTicks(30)
                .log("song hidden", () -> {
                    String r = String.format(Locale.ROOT, "Song of Pulling behind a boulder: rock between %s, pulled %.2f", LeviathanClient.lastBlocked(),
                            LeviathanClient.pulled() - marks[2]);
                    s.summary.add(r);
                    return r;
                })
                .check("the asteroid blocks it: not pulled", () -> LeviathanClient.lastBlocked() && LeviathanClient.pulled() - marks[2] < 0.01)
                .waitUntil("the song is over", 120, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE));
        // the failure: stand in the open and be pulled into the mouth
        int[] from = {0};
        steps.run("on platform 0, doing nothing", () -> {
                    standOn(mc, layout().platform(0), 1.0);
                    bites[0] = ask(l -> l.counts()[2]);
                    from[0] = s.playerDamage.size();
                })
                .waitUntil("its head comes close", 900, () -> ServerQuery.ask(p ->
                        LeviathanScenario.lev(p).mouth().distanceTo(p.getBoundingBox().getCenter()) < 9.0))
                .command("cosmicbreach debug leviathan attack song")
                .waitUntil("it bites", 160, () -> ask(l -> l.counts()[2]) > bites[0])
                .waitUntil("the bite is counted", 10, () -> s.playerDamage.size() > from[0])
                .log("bitten", () -> {
                    String r = String.format(Locale.ROOT, "Song of Pulling, stood still: pulled into the mouth and bitten for %.2f (design: about 7)",
                            s.playerDamage.get(from[0]));
                    s.summary.add(r);
                    return r;
                })
                .check("about 7 (5 to 10)", () -> s.playerDamage.get(from[0]) >= 5f && s.playerDamage.get(from[0]) <= 10f)
                .waitUntil("the song is over", 120, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE))
                .waitUntil("landed", 400, () -> mc.player.onGround());
        heal(steps);
    }

    /** The platform whose inner edge the orbit (so the tail) passes nearest, and that distance (from the player's middle there). */
    private double[] flickPlatform() {
        return ServerQuery.ask(p -> {
            ThalassineLeviathan l = LeviathanScenario.lev(p);
            Vec3 c = layout().centre();
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (RiftLayout.Platform q : layout().platforms()) {
                Vec3 in = new Vec3(c.x - q.x(), 0, c.z - q.z()).normalize();
                Vec3 stand = q.topCentre().add(in.scale(Math.min(q.radius() - 0.8, RiftLayout.PLATFORM_RING - RiftLayout.CLEAR_RADIUS - 0.8)))
                        .add(0, 0.9, 0);
                for (double a = q.angle() - 0.5; a <= q.angle() + 0.5; a += 0.02) {
                    double d = LeviathanOrbit.point(c, a, l.orbitWave()).distanceTo(stand);
                    if (d < bestD) {
                        bestD = d;
                        best = q.index();
                    }
                }
            }
            return new double[] {best, bestD};
        });
    }

    private void flick(Steps steps, Minecraft mc) {
        int[] parried = {0};
        float[] health = {0};
        RiftLayout.Platform[] at = {null};
        double[] near = {0};
        steps.run("to the platform whose edge its tail passes closest", () -> {
                    double[] best = flickPlatform();
                    at[0] = layout().platform((int) best[0]);
                    near[0] = Math.max(5.5, best[1] + 0.6);
                    standOn(mc, at[0], 0.8);
                    parried[0] = ask(l -> l.counts()[4]);
                })
                .log("flick spot", () -> String.format(Locale.ROOT, "the tail passes %.1f blocks from the edge of platform %d", near[0] - 0.6,
                        at[0].index()))
                .waitUntil("its tail passes closest", 900, () -> ServerQuery.ask(p -> {
                    ThalassineLeviathan l = LeviathanScenario.lev(p);
                    return p.getBoundingBox().getCenter().distanceTo(l.point(ThalassineLeviathan.FOLLOWERS)) <= near[0];
                }))
                .command("cosmicbreach debug leviathan attack flick")
                .waitUntil("it flicks", 10, () -> ask(l -> l.action() == LeviathanTactics.Attack.FLICK))
                .run("face the tail", () -> ColossusScenario.lookAt(mc, ask(l -> l.point(ThalassineLeviathan.FOLLOWERS))))
                .waitUntil("the gold glint (tick 16)", 30, () -> ask(l -> l.level().getGameTime() - l.actionStart() >= 17))
                .run("parry", () -> click(ModKeyMappings.PARRY))
                .waitTicks(1)
                .run("", () -> key(ModKeyMappings.PARRY, false))
                .waitTicks(8)
                .log("flick", () -> ask(l -> {
                    String r = String.format(Locale.ROOT, "Tail Flick parried: %d, drooping %s, Break gauge %.2f", l.counts()[4] - parried[0], l.drooping(),
                            l.gaugeFraction());
                    s.summary.add(r);
                    return r;
                }))
                .check("the parry landed: the tail droops and 60 went into the Break gauge", () -> ask(l -> l.counts()[4] > parried[0]
                        && l.drooping() && l.gaugeFraction() >= 0.19))
                .run("health before the free hits", () -> health[0] = ask(l -> l.getHealth()));
        // the free window on the drooping tail
        for (int k = 0; k < 4; k++) {
            steps.run("face the tail", () -> ColossusScenario.lookAt(mc, ask(l -> l.point(ThalassineLeviathan.FOLLOWERS))))
                    .run("swing", () -> click(mc.options.keyAttack))
                    .waitTicks(2)
                    .run("", () -> key(mc.options.keyAttack, false))
                    .waitTicks(7);
        }
        steps.log("free hits", () -> {
                    String r = String.format(Locale.ROOT, "free hits on the drooping tail: %.1f damage", health[0] - ask(l -> l.getHealth()));
                    s.summary.add(r);
                    return r;
                })
                .check("the free hits landed", () -> ask(l -> l.getHealth()) < health[0] - 5f)
                .waitUntil("the flick is over", 100, () -> ask(l -> l.action() == LeviathanTactics.Attack.NONE));
        heal(steps);
    }

    /**
     * How close a scale must be before the swing at it: a swing reaches about 3 blocks, and a scale keeps drifting in at 0.16 blocks
     * a tick, so waiting for it to come inside this (and not for 3.6, as before) makes the swing connect wherever in its orbit the
     * Leviathan was when it shed.
     */
    private static final double SWING_REACH = 2.4;

    private void shed(Steps steps, Minecraft mc) {
        int[] from = {0};
        int[] popped = {0};
        steps.run("on platform 0", () -> {
                    standOn(mc, layout().platform(0), 1.0);
                    from[0] = s.playerDamage.size();
                })
                .waitTicks(10)
                .command("cosmicbreach debug leviathan attack shed")
                .waitUntil("its scales drift in", 60, () -> ServerQuery.ask(p -> !p.serverLevel().getEntitiesOfClass(ShedScale.class,
                        p.getBoundingBox().inflate(80)).isEmpty()))
                .waitUntil("the first scale is near", 520, () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class,
                        p.getBoundingBox().inflate(SWING_REACH)).size() > 0))
                .run("face it and swing", () -> {
                    Vec3 scale = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class, p.getBoundingBox().inflate(SWING_REACH)).get(0)
                            .getBoundingBox().getCenter());
                    ColossusScenario.lookAt(mc, scale);
                    popped[0] = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class, p.getBoundingBox().inflate(80)).size());
                    click(mc.options.keyAttack);
                })
                .waitTicks(2)
                .run("", () -> key(mc.options.keyAttack, false))
                .waitTicks(10)
                .log("scale", () -> {
                    int left = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class, p.getBoundingBox().inflate(80)).size());
                    String r = String.format(Locale.ROOT, "Scale Shed: %d scales drifted in; one swing broke %d", popped[0], popped[0] - left);
                    s.summary.add(r);
                    return r;
                })
                .check("a swing breaks a scale (4 health)", () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class,
                        p.getBoundingBox().inflate(80)).size()) < popped[0])
                .waitUntil("the rest are gone", 400, () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class,
                        p.getBoundingBox().inflate(120)).isEmpty()))
                .log("scale hits", () -> {
                    String r = String.format(Locale.ROOT, "scales that reached the player dealt %.1f in all (8 each before armour)", takenSince(from[0]));
                    s.summary.add(r);
                    return r;
                });
        heal(steps);
    }

    // ------------------------------------------------------------------ part leviathan-moorage

    void moorage(Steps steps, Minecraft mc) {
        long[] xp = {0};
        int[] points = {0};
        arrive(steps, mc, true);
        steps.run("before the kill", () -> {
            xp[0] = ServerQuery.ask(p -> Attunements.of(p).totalXp());
            points[0] = ServerQuery.ask(p -> Attunements.of(p).bonusPoints());
        });
        oneMoorage(steps, mc, 1, (float) (LeviathanMoves.BASE_HEALTH * 0.49));
        steps.check("phase 2 below half", () -> ask(ThalassineLeviathan::phaseTwo));
        oneMoorage(steps, mc, 2, (float) (LeviathanMoves.BASE_HEALTH * 0.17));
        steps.run("", () -> { })
                .log("death", () -> "it goes still and sinks onto the lower shell")
                .waitUntil("it sinks, a pearl of light rises, the rewards are given", 500, () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) == null))
                .waitTicks(40)
                .log("rewards", () -> {
                    String r = String.format(Locale.ROOT, "first kill rewards: Leviathan Pearl %d, Halo of Nine %d, Leviathan Scale %d, XP +%d, stat points +%d, "
                                    + "Moored %s, attuned to the Deep %s, the Starfall's line heard %s", count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_PEARL.get()),
                            count(mc, LeviathanRegistry.HALO_OF_NINE.get()), count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_SCALE.get()),
                            ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0], ServerQuery.ask(p -> Attunements.of(p).bonusPoints()) - points[0],
                            ServerQuery.ask(p -> GuardianRewards.killedBefore(p, GuardianTypes.LEVIATHAN)), ServerQuery.ask(p -> LayerAttunement.has(p, Layer.DEEP)),
                            ServerQuery.ask(p -> Echo.heard(p, EchoLine.DEEP)));
                    s.summary.add(r);
                    return r;
                })
                .check("the pearl, the halo and three scales", () -> count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_PEARL.get()) == 1
                        && count(mc, LeviathanRegistry.HALO_OF_NINE.get()) == 1 && count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_SCALE.get()) == 3)
                .check("9,000 XP and a stat point", () -> ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0] == 9000
                        && ServerQuery.ask(p -> Attunements.of(p).bonusPoints()) - points[0] == 1)
                .check("Moored: attuned to the Deep, and the Starfall's line for the Deep heard", () -> ServerQuery.ask(p ->
                        GuardianRewards.killedBefore(p, GuardianTypes.LEVIATHAN) && LayerAttunement.has(p, Layer.DEEP) && Echo.heard(p, EchoLine.DEEP)))
                .check("the bell rests 20 minutes", () -> ServerQuery.ask(p -> p.serverLevel().getBlockEntity(layout().bell())
                        instanceof com.cosmicbreach.guardian.GuardianAltarBlockEntity a && !a.armed()))
                .run("release every key", () -> releaseAll(mc));
    }

    /** One Moorage: health to the line, the swim in, onto the coil by a bridge, the glands, the shudders, tearing free. */
    private void oneMoorage(Steps steps, Minecraft mc, int n, float health) {
        RiftLayout.Platform[] from = {null};
        float[] before = {0};
        int[] glandHits = {0};
        int[] taken = {0};
        steps.run("on platform 0", () -> standOn(mc, layout().platform(0), 2.0))
                .command("cosmicbreach debug leviathan health " + (int) health)
                .waitUntil("Moorage " + n + " begins at its health line", 40, () -> LeviathanScenario.is(ThalassineLeviathan.State.MOORAGE))
                .waitUntil("the coil settles round the central asteroid", 400, () -> ask(l -> l.moorStage() == ThalassineLeviathan.Moor.COILED))
                .run("mark", () -> ticks[0] = ask(ThalassineLeviathan::moorStart))
                .waitUntil("the bridges have grown", 60, () -> ask(l -> l.bridgeBlocks().size() > 10))
                .log("moorage " + n, () -> ask(l -> {
                    String r = String.format(Locale.ROOT, "Moorage %d: %.0f health (%.0f%%), %d coil blocks, %d bridge blocks, glands exposed %s", n,
                            l.getHealth(), 100 * l.getHealth() / l.getMaxHealth(), l.coilBlocks().size(), l.bridgeBlocks().size(), l.glandsExposed());
                    s.summary.add(r);
                    return r;
                }))
                .run("to the platform with a bridge nearest the coil's middle", () -> {
                    Vec3 mid = ask(l -> l.point(2));
                    double a = LeviathanOrbit.angleOf(layout().centre(), mid);
                    from[0] = layout().nearestPlatform(a);
                    standOn(mc, from[0], 1.2);
                })
                .waitTicks(5);
        walk(steps, mc, "along the grown bridge onto its back", () -> {
            Vec3 c = layout().centre();
            double a = from[0].angle();
            return new Vec3(c.x + (LeviathanMoves.COIL_RADIUS) * Math.cos(a), c.y, c.z + LeviathanMoves.COIL_RADIUS * Math.sin(a));
        }, 0.9, 200);
        steps.waitUntil("standing on the coil's back", 60, () -> ServerQuery.ask(p -> p.onGround()
                        && p.serverLevel().getBlockState(p.blockPosition().below()).is(LeviathanRegistry.LEVIATHAN_COIL.get())))
                .log("on the coil", () -> String.format(Locale.ROOT, "walked onto the coil's back at Y %.2f", mc.player.getY()))
                .run("count", () -> {
                    before[0] = ask(ThalassineLeviathan::getHealth);
                    glandHits[0] = ask(l -> l.counts()[8]);
                });
        // to the nearest gland, and hit it
        walk(steps, mc, "to the nearest song gland", () -> nearestGland(mc).subtract(0, 0, 0), 1.3, 120);
        // the second Moorage starts at a fifth of its health: one hit, so it lives through the shudder test
        swingAtGland(steps, mc);
        if (n == 1) {
            // The coil holds still for 300 ticks and shudders at 80, 160 and 240, and how long the walk onto its back takes depends on the
            // Rift's layout (the platform the bridge leaves from). So the shudder away from the glands, which needs no walk and no hits,
            // comes first, at the first shudder the walk leaves (the first hit is struck while it is awaited), and the second hit and the
            // hold-on shudder after it, at the one after that
            shudderThrown(steps, mc, taken);
            steps.run("back to the nearest song gland", () -> {
                Vec3 g = nearestGland(mc);
                Vec3 along = ask(l -> l.point(2).subtract(l.point(1)));
                Vec3 flat = new Vec3(along.x, 0, along.z).normalize();
                ColossusScenario.tp(mc, g.x + flat.x, Math.floor(layout().centre().y + LeviathanMoves.COIL_TOP), g.z + flat.z, g.x, g.y, g.z);
            }).waitTicks(2);
            swingAtGland(steps, mc);
        }
        steps.log("glands", () -> {
                    String r = String.format(Locale.ROOT, "Moorage %d: %d hits on the glands dealt %.1f (last gland hit %.1f)", n,
                            ask(l -> l.counts()[8]) - glandHits[0], before[0] - ask(ThalassineLeviathan::getHealth),
                            s.dealt.isEmpty() ? 0f : s.dealt.get(s.dealt.size() - 1));
                    s.summary.add(r);
                    return r;
                })
                .check("the glands took the hits", () -> ask(l -> l.counts()[8]) > glandHits[0]);
        // a shudder, holding on beside a gland
        steps.waitUntil("a shudder's ripple", 200, () -> ask(l -> {
                    long t = l.level().getGameTime() - l.moorStart();
                    return t % 80 >= 66 && t % 80 < 78;
                }))
                .run("hold on by the gland", () -> {
                    taken[0] = s.playerDamage.size();
                    marks[3] = mc.player.getY();
                })
                .waitUntil("the shudder", 20, () -> ask(l -> (l.level().getGameTime() - l.moorStart()) % 80 == 1))
                .waitTicks(6)
                .log("shudder held", () -> {
                    String r = String.format(Locale.ROOT, "shudder beside a gland: held on (rose %.2f, took %.1f)", mc.player.getY() - marks[3],
                            takenSince(taken[0]));
                    s.summary.add(r);
                    return r;
                })
                .check("holding on: not thrown, not hurt", () -> mc.player.getY() - marks[3] < 1.0 && takenSince(taken[0]) == 0f);
        if (n == 2) {
            // the last blow, on a gland: it dies moored
            steps.command("cosmicbreach debug leviathan health 4")
                    .run("face the gland", () -> ColossusScenario.lookAt(mc, nearestGland(mc)))
                    .run("swing", () -> click(mc.options.keyAttack))
                    .waitTicks(2)
                    .run("", () -> key(mc.options.keyAttack, false))
                    .waitUntil("the last blow lands on a gland", 60, () -> LeviathanScenario.is(ThalassineLeviathan.State.DYING))
                    .check("the coil and the bridges went with it", () -> ServerQuery.ask(p -> p.serverLevel().getBlockStates(p.getBoundingBox().inflate(40))
                            .noneMatch(st -> st.is(LeviathanRegistry.LEVIATHAN_COIL.get()) || st.is(LeviathanRegistry.RIFT_BRIDGE.get()))));
            return;
        }
        steps.waitUntil("it tears free and swims back to its orbit", 400, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                .check("the coil and the bridges are gone", () -> ask(l -> l.coilBlocks().isEmpty() && l.bridgeBlocks().isEmpty())
                        && ServerQuery.ask(p -> p.serverLevel().getBlockStates(p.getBoundingBox().inflate(40))
                        .noneMatch(st -> st.is(LeviathanRegistry.LEVIATHAN_COIL.get()) || st.is(LeviathanRegistry.RIFT_BRIDGE.get()))))
                .log("torn free", () -> String.format(Locale.ROOT, "Moorage %d over after %d ticks coiled", n, ask(l -> l.level().getGameTime()) - ticks[0]))
                .waitUntil("landed", 600, () -> mc.player.onGround())
                .command("effect give @s minecraft:instant_health 1 4 true");
    }

    /** One swing at the nearest gland, and its 30 ticks (the Maul's recovery, and the hit lands in them). */
    private void swingAtGland(Steps steps, Minecraft mc) {
        steps.run("face the gland", () -> ColossusScenario.lookAt(mc, nearestGland(mc)))
                .run("swing", () -> click(mc.options.keyAttack))
                .waitTicks(2)
                .run("", () -> key(mc.options.keyAttack, false))
                .waitTicks(30);
    }

    /** A shudder away from the glands, at the coil's head end: the player is thrown up and hurt. */
    private void shudderThrown(Steps steps, Minecraft mc, int[] taken) {
        steps.run("step away from the glands, to the coil's head end", () -> {
                    Vec3 head = ask(l -> l.point(0));
                    Vec3 seg = ask(l -> l.point(1));
                    marks[4] = 0;
                    Vec3 spot = head.add(seg.subtract(head).scale(0.35));
                    ColossusScenario.tp(mc, spot.x, Math.floor(layout().centre().y + LeviathanMoves.COIL_TOP), spot.z, head.x, head.y, head.z);
                })
                .waitUntil("a shudder's ripple", 200, () -> ask(l -> {
                    long t = l.level().getGameTime() - l.moorStart();
                    return t % 80 >= 66 && t % 80 < 78;
                }))
                .run("mark", () -> {
                    taken[0] = s.playerDamage.size();
                    marks[3] = mc.player.getY();
                })
                .waitUntil("the shudder", 20, () -> ask(l -> (l.level().getGameTime() - l.moorStart()) % 80 == 1))
                .waitTicks(8)
                .log("shudder thrown", () -> {
                    String r = String.format(Locale.ROOT, "shudder away from the glands: thrown (rose %.2f), took %.1f", mc.player.getY() - marks[3],
                            takenSince(taken[0]));
                    s.summary.add(r);
                    return r;
                })
                .check("away from the glands: thrown up and hurt", () -> mc.player.getY() - marks[3] > 0.8 && takenSince(taken[0]) > 0f);
    }

    private static Vec3 nearestGland(Minecraft mc) {
        Vec3 me = mc.player.position();
        return ServerQuery.ask(p -> {
            ThalassineLeviathan l = LeviathanScenario.lev(p);
            Vec3 best = l.glandPoint(0);
            for (int i = 1; i < ThalassineLeviathan.GLANDS; i++) {
                if (l.glandPoint(i).distanceToSqr(me) < best.distanceToSqr(me)) {
                    best = l.glandPoint(i);
                }
            }
            return best;
        });
    }

    // ------------------------------------------------------------------ part leviathan-repeat

    void repeat(Steps steps, Minecraft mc) {
        long[] xp = {0};
        int[] points = {0};
        int[] wakes = {0};
        s.equip(steps, mc);
        steps.command("advancement grant @s only cosmicbreach:guardian/moored")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .waitUntil("attuned to the Deep, with a kill behind it", 40, () -> ServerQuery.ask(p -> LayerAttunement.has(p, Layer.DEEP)
                        && GuardianRewards.killedBefore(p, GuardianTypes.LEVIATHAN)))
                .run("into the Rift, on platform 0 by the bell (on its outer side, inside its lit posts, clear of the boulders)", () -> {
                    RiftLayout.Platform p0 = layout().platform(0);
                    Vec3 c = layout().centre();
                    Vec3 out = new Vec3(p0.x() - c.x, 0, p0.z() - c.z).normalize();
                    Vec3 bell = Vec3.atCenterOf(layout().bell());
                    Vec3 at = new Vec3(bell.x, p0.top() + 1.0, bell.z).add(out.scale(1.5));
                    ColossusScenario.tp(mc, at.x, at.y, at.z, bell.x, bell.y, bell.z);
                })
                .waitTicks(60)
                .check("a player attuned to the Deep walks in and it sleeps on", () -> LeviathanScenario.is(ThalassineLeviathan.State.DORMANT))
                .run("empty-handed (the Maul would take the use key), face the bell", () -> {
                    wakes[0] = RiftBellBlock.wakes();
                    mc.player.getInventory().selected = 8;
                    ColossusScenario.lookAt(mc, Vec3.atCenterOf(layout().bell()));
                })
                .waitTicks(2)
                .run("ring it", () -> click(mc.options.keyUse))
                .waitTicks(2)
                .run("", () -> key(mc.options.keyUse, false))
                .waitTicks(30)
                .check("the bell rings for an attuned player (it says so), and it sleeps on", () -> LeviathanScenario.is(ThalassineLeviathan.State.DORMANT)
                        && RiftBellBlock.wakes() == wakes[0] && RiftBellBlock.rings() > 0)
                .waitUntil("just after its sleeping look round for unattuned players (every 10 ticks)", 20, () -> ServerQuery.ask(p -> {
                    ThalassineLeviathan l = LeviathanScenario.lev(p);
                    return Math.floorMod(p.serverLevel().getGameTime() + l.getId(), 10L) == 2;
                }))
                .command("advancement revoke @s only cosmicbreach:attunement/deep")
                .run("ring it unattuned", () -> click(mc.options.keyUse))
                .waitTicks(2)
                .run("", () -> key(mc.options.keyUse, false))
                .waitUntil("the bell woke it", 20, () -> !LeviathanScenario.is(ThalassineLeviathan.State.DORMANT))
                .check("by the bell", () -> RiftBellBlock.wakes() == wakes[0] + 1)
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .waitUntil("the intro is over", 260, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                .run("before the kill", () -> {
                    xp[0] = ServerQuery.ask(p -> Attunements.of(p).totalXp());
                    points[0] = ServerQuery.ask(p -> Attunements.of(p).bonusPoints());
                })
                .waitUntil("it has chosen the player as its target (a participant)", 80, () -> ServerQuery.ask(p ->
                        LeviathanScenario.lev(p).participants().contains(p.getUUID())))
                .command("cosmicbreach debug leviathan kill")
                .waitUntil("a repeat kill plays out", 500, () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) == null))
                .waitTicks(40)
                .log("repeat rewards", () -> {
                    String r = String.format(Locale.ROOT, "repeat kill: XP +%d, stat points +%d, Leviathan Scale %d, Pearl %d, Halo %d",
                            ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0], ServerQuery.ask(p -> Attunements.of(p).bonusPoints()) - points[0],
                            count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_SCALE.get()),
                            count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_PEARL.get()), count(mc, LeviathanRegistry.HALO_OF_NINE.get()));
                    s.summary.add(r);
                    return r;
                })
                .check("1,800 XP, no stat point, 1 to 3 scales", () -> ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0] == 1800
                        && ServerQuery.ask(p -> Attunements.of(p).bonusPoints()) == points[0]
                        && count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_SCALE.get()) >= 1
                        && count(mc, com.cosmicbreach.registry.ModMaterials.LEVIATHAN_SCALE.get()) <= 3)
                .waitTicks(60)
                .check("the lair rests: no new Leviathan for 20 minutes", () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) == null))
                .command("cosmicbreach debug leviathan cooldown")
                .waitUntil("the bell raises a new one, asleep", 100, () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) != null
                        && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT))
                .command("give @s cosmicbreach:guardian_echo")
                .waitUntil("the Echo is in the inventory", 40, () -> count(mc, com.cosmicbreach.guardian.GuardianRegistry.GUARDIAN_ECHO.get()) == 1)
                .run("hold the Echo, face the bell", () -> {
                    mc.player.getInventory().selected = LeviathanScenario.slot(mc, com.cosmicbreach.guardian.GuardianRegistry.GUARDIAN_ECHO.get());
                    ColossusScenario.lookAt(mc, Vec3.atCenterOf(layout().bell()));
                })
                .waitTicks(4)
                .run("use it on the bell", () -> click(mc.options.keyUse))
                .waitTicks(2)
                .run("", () -> key(mc.options.keyUse, false))
                .waitUntil("a Guardian Echo wakes it for an attuned player", 40, () -> !LeviathanScenario.is(ThalassineLeviathan.State.DORMANT))
                .check("the Echo is spent", () -> count(mc, com.cosmicbreach.guardian.GuardianRegistry.GUARDIAN_ECHO.get()) == 0)
                .waitUntil("the intro is over", 260, () -> LeviathanScenario.is(ThalassineLeviathan.State.FIGHT))
                // Everyone leaves: out onto the entrance ledge, outside the sphere (the arrival checks as much) and still near enough
                // that the Rift ticks. It used to be 40 blocks beyond the ledge, in open air, but the Rift only ticks while a player is within
                // the simulation distance (8 chunks in the test client): how far the ledge is from the axis, and which way it faces, depend
                // on the Rift's layout, and from there the player was too far for the Rift to tick (so it never reset) and fell out of the
                // world while waiting
                .run("everyone leaves: out onto the entrance ledge, outside the sphere", () -> {
                    Vec3 c = layout().centre();
                    Vec3 ledge = layout().ledgeTop();
                    ColossusScenario.tp(mc, ledge.x, ledge.y, ledge.z, c.x, ledge.y + 1.6, c.z);
                })
                .check("outside the sphere", () -> ServerQuery.ask(p -> !layout().inside(p.position())))
                .run("mark", () -> ticks[1] = mc.level.getGameTime())
                .waitUntil("after 30 s with nobody inside it resets to sleep", 900, () -> LeviathanScenario.is(ThalassineLeviathan.State.DORMANT))
                .log("reset", () -> {
                    String r = String.format(Locale.ROOT, "reset after %d ticks with nobody inside: asleep at %.0f of %.0f health", mc.level.getGameTime() - ticks[1],
                            ask(ThalassineLeviathan::getHealth), ask(ThalassineLeviathan::getMaxHealth));
                    s.summary.add(r);
                    return r;
                })
                .check("full health again, and the bell is not resting", () -> ask(l -> l.getHealth() >= l.getMaxHealth() - 0.1f)
                        && ServerQuery.ask(p -> p.serverLevel().getBlockEntity(layout().bell()) instanceof com.cosmicbreach.guardian.GuardianAltarBlockEntity a
                        && a.armed()))
                .run("release every key", () -> releaseAll(mc));
    }
}
