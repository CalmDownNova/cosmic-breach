package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.leviathan.LeviathanCommands;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ShedScale;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Thalassine Leviathan and its Rift (task G5), in parts:
 * <ul>
 *   <li>{@code leviathan-looks}: the Rift from outside and inside, the Leviathan asleep, rising, and swimming its orbit
 *       seen from its side, from ahead and from behind in daylight, and every telegraph: the dive's dust wake, the song's
 *       rings, the flick's gold fan, the shed scales, a Break, the Moorage (glands, bridges) and a shudder's ripple.</li>
 *   <li>{@code leviathan}: level 24 in the Driftweave with the Comet Maul, real inputs: in along the walkway (entering
 *       wakes it), each attack and its answer (leave the wake; dash out of a pull; hide behind the core; parry a flick,
 *       then the free hits on the drooping tail; break a shed scale), and the failures measured (a dive and a bite).</li>
 *   <li>{@code leviathan-moorage}: both Moorages (the swim in, the coil's walkable back, the bridges, hits on the glands,
 *       holding on through a shudder), the kill, each reward, the Deep's attunement and the Starfall's line.</li>
 *   <li>{@code leviathan-repeat}: the bell for an attuned player and an unattuned one, a Guardian Echo after the
 *       cooldown, a repeat kill's rewards, and the reset when everyone leaves.</li>
 *   <li>{@code leviathan-fight}: a whole fight played by a scripted bot, timed ({@link LeviathanFightBot}).</li>
 * </ul>
 */
public final class LeviathanScenario implements Scenario {
    public enum Part { LOOKS, MECHANICS, MOORAGE, REPEAT, FIGHT }

    private final Part part;
    final List<String> summary = new ArrayList<>();
    /** Damage the Leviathan and its scales dealt the player (server, as it happened). */
    final List<Float> playerDamage = new CopyOnWriteArrayList<>();
    /** Damage the player dealt the Leviathan (server, as it happened). */
    final List<Float> dealt = new CopyOnWriteArrayList<>();

    public LeviathanScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return part == Part.FIGHT ? 900 : 700;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal");
        buildLair(steps, mc);
        switch (part) {
            case LOOKS -> looks(steps, mc);
            case MECHANICS -> new LeviathanMechanics(this).steps(steps, mc);
            case MOORAGE -> new LeviathanMechanics(this).moorage(steps, mc);
            case REPEAT -> new LeviathanMechanics(this).repeat(steps, mc);
            case FIGHT -> new LeviathanFightBot(this).steps(steps, mc);
        }
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    private void listen() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer && (event.getSource().getEntity() instanceof ThalassineLeviathan
                            || event.getSource().getDirectEntity() instanceof ShedScale)) {
                        playerDamage.add(event.getNewDamage());
                    }
                    if (event.getEntity() instanceof ThalassineLeviathan && event.getSource().getEntity() instanceof ServerPlayer) {
                        dealt.add(event.getNewDamage());
                    }
                });
    }

    // ------------------------------------------------------------------ the lair

    private void buildLair(Steps steps, Minecraft mc) {
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the asteroid is drawn", 1600, settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built and its Leviathan sleeps in it", 400, () -> layout() != null
                        && ServerQuery.ask(p -> lev(p) != null && lev(p).state() == ThalassineLeviathan.State.DORMANT))
                .log("lair", () -> {
                    RiftLayout l = layout();
                    String s = String.format(Locale.ROOT, "Leviathan Rift: centre %d %d %d, built in %d ms, entrance ledge %s", l.x(), l.y(), l.z(),
                            LeviathanCommands.lastBuildMillis(), l.ledgeTop());
                    summary.add(s);
                    return s;
                });
    }

    static @Nullable RiftLayout layout() {
        return LeviathanCommands.lastBuilt();
    }

    /** The nearest Leviathan to the player (server). */
    static @Nullable ThalassineLeviathan lev(ServerPlayer p) {
        ThalassineLeviathan best = null;
        double bestD = Double.MAX_VALUE;
        for (ThalassineLeviathan l : p.serverLevel().getEntitiesOfClass(ThalassineLeviathan.class, p.getBoundingBox().inflate(256.0))) {
            double d = l.distanceToSqr(p);
            if (d < bestD) {
                best = l;
                bestD = d;
            }
        }
        return best;
    }

    static <T> T ask(Function<ThalassineLeviathan, T> query) {
        return ServerQuery.ask(p -> {
            ThalassineLeviathan l = lev(p);
            if (l == null) {
                throw new Steps.Failure("no Leviathan near the player");
            }
            return query.apply(l);
        });
    }

    static boolean is(ThalassineLeviathan.State state) {
        return ServerQuery.ask(p -> lev(p) != null && lev(p).state() == state);
    }

    /** Puts the player's eye at {@code eye} looking at {@code at}. */
    static void camera(Minecraft mc, Vec3 eye, Vec3 at) {
        ColossusScenario.tp(mc, eye.x, eye.y - 1.62, eye.z, at.x, at.y, at.z);
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    static final int SETTLE_TICKS = 20;

    static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = mc.screen == null && mc.level != null && mc.player != null
                    && mc.level.getChunkSource().hasChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
            int rendered = mc.levelRenderer.countRenderedSections();
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= SETTLE_TICKS || (state[0] >= maxTicks && ready);
        };
    }

    /** Level 24 with Power 7 and Resilience 16 of its own, the Driftweave on, the Comet Maul in hand. */
    void equip(Steps steps, Minecraft mc) {
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach debug level 24")
                .command("cosmicbreach debug stats 7 0 0 16")
                .waitTicks(2)
                .command("clear @s")
                .command("item replace entity @s armor.head with cosmicbreach:driftweave_hood")
                .command("item replace entity @s armor.chest with cosmicbreach:driftweave_coat")
                .command("item replace entity @s armor.legs with cosmicbreach:driftweave_leggings")
                .command("item replace entity @s armor.feet with cosmicbreach:driftweave_boots")
                .command("give @s cosmicbreach:comet_maul")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .waitUntil("the Comet Maul is in the inventory", 60, () -> mc.player.getInventory().contains(
                        new net.minecraft.world.item.ItemStack(com.cosmicbreach.registry.ModItems.COMET_MAUL.get())))
                .run("hold it", () -> mc.player.getInventory().selected = slot(mc, com.cosmicbreach.registry.ModItems.COMET_MAUL.get()))
                .waitUntil("the Comet Maul is in the main hand on the server", 60, () -> ServerQuery.ask(p ->
                        p.getMainHandItem().is(com.cosmicbreach.registry.ModItems.COMET_MAUL.get())))
                .log("player", () -> ServerQuery.ask(p -> {
                    String s = String.format(Locale.ROOT, "player: level %d, armor %.0f, toughness %.0f, max health %.1f, %s",
                            com.cosmicbreach.progression.Attunements.of(p).level(), p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),
                            p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS), p.getMaxHealth(),
                            com.cosmicbreach.progression.ProgressionStats.of(p));
                    summary.add(s);
                    return s;
                }));
    }

    static int slot(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ looks

    private Vec3 headNow() {
        return ask(l -> l.point(0));
    }

    private Vec3 facingNow() {
        return ask(ThalassineLeviathan::facing);
    }

    private void looks(Steps steps, Minecraft mc) {
        RiftLayout[] l = new RiftLayout[1];
        steps.command("gamemode creative")
                .run("remember the lair", () -> l[0] = layout())
                .run("outside the Rift, beyond its entrance ledge", () -> {
                    Vec3 c = l[0].centre();
                    Vec3 ledge = l[0].ledgeTop();
                    Vec3 out = new Vec3(ledge.x - c.x, 0, ledge.z - c.z).normalize();
                    camera(mc, ledge.add(out.scale(16)).add(0, 12, 0), c);
                })
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .waitUntil("the view is drawn", 800, settled(mc, 600))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("rift_outside")
                .run("over platform 0's inner edge, above its boulders, looking in", () -> {
                    RiftLayout.Platform p0 = l[0].platform(0);
                    Vec3 c = l[0].centre();
                    Vec3 in = new Vec3(c.x - p0.x(), 0, c.z - p0.z()).normalize();
                    camera(mc, p0.topCentre().add(in.scale(p0.radius() - 1.0)).add(0, 5.0, 0), c.add(0, -8, 0));
                })
                .waitUntil("the view is drawn", 400, settled(mc, 300))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("rift_inside")
                .run("by the bell's lit posts, on the way in from the entrance", () -> {
                    RiftLayout.Platform p0 = l[0].platform(0);
                    Vec3 c = l[0].centre();
                    Vec3 out = new Vec3(p0.x() - c.x, 0, p0.z() - c.z).normalize();
                    Vec3 side = new Vec3(-out.z, 0, out.x);
                    Vec3 bell = Vec3.atCenterOf(l[0].bell());
                    camera(mc, bell.add(out.scale(5.5)).add(side.scale(2.5)).add(0, 2.4, 0), bell.add(0, 0.3, 0));
                })
                .waitTicks(5)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("rift_bell")
                .run("over the platform above its sleeping head, looking down", () -> {
                    Vec3 head = ask(le -> le.point(0));
                    Vec3 c = l[0].centre();
                    Vec3 out = new Vec3(head.x - c.x, 0, head.z - c.z).normalize();
                    camera(mc, head.add(out.scale(22)).add(0, 34, 0), head);
                })
                .waitTicks(10)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_asleep")
                .command("cosmicbreach debug leviathan awaken")
                .waitUntil("it wakes", 40, () -> is(ThalassineLeviathan.State.INTRO))
                .waitTicks(110)
                .run("from over platform 0's inner edge (above its boulders), watching it rise", () -> {
                    Vec3 head = headNow();
                    RiftLayout.Platform p0 = l[0].platform(0);
                    Vec3 c = l[0].centre();
                    Vec3 in = new Vec3(c.x - p0.x(), 0, c.z - p0.z()).normalize();
                    camera(mc, p0.topCentre().add(in.scale(p0.radius() - 1.0)).add(0, 5.0, 0), head);
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_rising")
                .waitUntil("the intro is over", 200, () -> is(ThalassineLeviathan.State.FIGHT))
                .check("the boss music is on", com.cosmicbreach.client.guardian.GuardianMusic::active);
        // a target that takes no harm, so it has someone to aim at
        steps.command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:slow_falling 100000 0 true")
                .command("cosmicbreach debug leviathan hold 1000000")
                .run("stand on platform 0", () -> standOn(mc, l[0].platform(0)))
                .waitTicks(20);
        sides(steps, mc);
        telegraphs(steps, mc, l);
        steps.run("the usual view", () -> mc.options.renderDistance().set(8));
    }

    private void standOn(Minecraft mc, RiftLayout.Platform p) {
        Vec3 c = layout().centre();
        Vec3 in = new Vec3(c.x - p.x(), 0, c.z - p.z()).normalize();
        Vec3 at = p.topCentre().add(in.scale(p.radius() - 1.5));
        ColossusScenario.tp(mc, at.x, at.y, at.z, c.x, c.y, c.z);
    }

    /** The Leviathan swimming its orbit, from its side, from ahead of it and from behind, in daylight. */
    private void sides(Steps steps, Minecraft mc) {
        steps.run("beside it, from over the platforms' rim looking in", () -> {
                    Vec3 mid = ask(le -> le.point(2));
                    Vec3 c = layout().centre();
                    Vec3 out = new Vec3(mid.x - c.x, 0, mid.z - c.z).normalize();
                    camera(mc, mid.add(out.scale(16)).add(0, 10, 0), mid.add(0, -1, 0));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_side")
                .waitTicks(20)
                .run("ahead of it on its orbit", () -> {
                    Vec3 head = headNow();
                    Vec3 f = facingNow();
                    Vec3 c = layout().centre();
                    Vec3 in = new Vec3(c.x - head.x, 0, c.z - head.z).normalize();
                    camera(mc, head.add(f.scale(11)).add(in.scale(2.5)).add(0, 2.5, 0), head.subtract(f.scale(8)));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_front")
                .waitTicks(20)
                .run("behind it, above its tail", () -> {
                    Vec3 tail = ask(le -> le.point(ThalassineLeviathan.FOLLOWERS));
                    Vec3 f = facingNow();
                    Vec3 mid = ask(le -> le.point(2));
                    camera(mc, tail.subtract(f.scale(12)).add(0, 9, 0), mid);
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_back")
                .run("stand on platform 0", () -> standOn(mc, layout().platform(0)))
                .waitTicks(10);
    }

    private void telegraphs(Steps steps, Minecraft mc, RiftLayout[] l) {
        long[] at = {0};
        // the Breach Dive's dust wake
        steps.run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitTicks(10)
                .command("cosmicbreach debug leviathan attack dive")
                .waitUntil("it dives", 40, () -> ask(le -> le.action() == LeviathanTactics.Attack.DIVE))
                .waitUntil("the wake is drawn all the way to where it lands, just before the head enters it", 40, () -> ask(le ->
                        le.level().getGameTime() - le.actionStart() >= LeviathanMoves.DIVE_TELL - 2))
                .run("high over the wake's middle, off to its side (clear of the platform's boulders)", () -> {
                    com.cosmicbreach.guardian.leviathan.Polyline path = ask(ThalassineLeviathan::divePath);
                    Vec3 me = mc.player.position();
                    double near = 0;
                    double best = Double.MAX_VALUE;
                    for (double d = 0; d <= path.length(); d += 0.5) {
                        double dd = path.at(d).distanceToSqr(me);
                        if (dd < best) {
                            best = dd;
                            near = d;
                        }
                    }
                    // side on to the wake, from outside it and above, so it runs across the frame from the head to its landing ring
                    Vec3 from = path.at(0.0);
                    Vec3 land = path.at(near);
                    Vec3 mid = from.lerp(land, 0.5);
                    Vec3 chord = new Vec3(land.x - from.x, 0, land.z - from.z).normalize();
                    Vec3 side = new Vec3(-chord.z, 0, chord.x);
                    Vec3 c = l[0].centre();
                    if (side.dot(new Vec3(mid.x - c.x, 0, mid.z - c.z)) < 0) {
                        side = side.scale(-1); // away from the core
                    }
                    double span = from.distanceTo(land);
                    camera(mc, mid.add(side.scale(span * 0.9 + 4)).add(0, span * 0.45 + 5, 0), mid.add(0, -1.0, 0));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("telegraph_dive_wake")
                .run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitUntil("the dive is over", 300, () -> ask(le -> le.action() == LeviathanTactics.Attack.NONE))
                .waitTicks(20);
        // the Song of Pulling's rings
        steps.command("cosmicbreach debug leviathan attack song")
                .waitUntil("it sings", 40, () -> ask(le -> le.action() == LeviathanTactics.Attack.SONG))
                .waitTicks(28)
                .run("beside the song", () -> {
                    Vec3 mouth = ask(ThalassineLeviathan::mouth);
                    Vec3 axis = ask(le -> le.songCone().axis());
                    Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
                    camera(mc, mouth.add(axis.scale(10)).add(side.scale(18)).add(0, 5, 0), mouth.add(axis.scale(10)));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("telegraph_song_rings")
                .run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitUntil("the song is over", 200, () -> ask(le -> le.action() == LeviathanTactics.Attack.NONE))
                .waitTicks(20);
        // the Tail Flick's gold fan
        steps.command("cosmicbreach debug leviathan attack flick")
                .waitUntil("it flicks", 40, () -> ask(le -> le.action() == LeviathanTactics.Attack.FLICK))
                .waitTicks(14)
                .run("by its tail", () -> {
                    Vec3 tail = ask(le -> le.point(ThalassineLeviathan.FOLLOWERS));
                    Vec3 f = ask(le -> le.renderDirection(ThalassineLeviathan.FOLLOWERS, 0f));
                    Vec3 side = f.cross(new Vec3(0, 1, 0)).normalize();
                    camera(mc, tail.add(side.scale(9)).add(f.scale(-5)).add(0, 4, 0), tail.add(f.scale(-3)));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("telegraph_flick_gold")
                .run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitUntil("the flick is over", 200, () -> ask(le -> le.action() == LeviathanTactics.Attack.NONE))
                .waitTicks(20);
        // Scale Shed
        steps.command("cosmicbreach debug leviathan attack shed")
                .waitUntil("it sheds", 40, () -> ask(le -> le.action() == LeviathanTactics.Attack.SHED))
                .waitTicks(44)
                .run("beside the drifting scales, looking along them to the body", () -> {
                    List<Vec3> found = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class, p.getBoundingBox().inflate(80))
                            .stream().map(e -> e.getBoundingBox().getCenter()).toList());
                    Vec3 me = mc.player.position();
                    Vec3 mid = ask(le -> le.point(2));
                    Vec3 sum = Vec3.ZERO;
                    for (Vec3 v : found) {
                        sum = sum.add(v);
                    }
                    Vec3 scales = found.isEmpty() ? mid : sum.scale(1.0 / found.size());
                    Vec3 toMe = new Vec3(me.x - scales.x, 0, me.z - scales.z).normalize();
                    Vec3 side = new Vec3(-toMe.z, 0, toMe.x);
                    camera(mc, scales.add(toMe.scale(7)).add(side.scale(6)).add(0, 3.5, 0), scales.add(mid.subtract(scales).scale(0.25)));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("telegraph_scale_shed")
                .run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitUntil("the scales are gone", 400, () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(ShedScale.class,
                        p.getBoundingBox().inflate(120)).isEmpty()))
                .waitTicks(10);
        // a Break: nose-down, its head level with the platforms
        steps.command("cosmicbreach debug leviathan break")
                .waitUntil("it is Broken", 20, () -> ask(ThalassineLeviathan::isBroken))
                .waitTicks(45)
                .run("beside its sunk head", () -> {
                    Vec3 head = headNow();
                    Vec3 c = l[0].centre();
                    Vec3 out = new Vec3(head.x - c.x, 0, head.z - c.z).normalize();
                    camera(mc, head.add(out.scale(12)).add(0, 5, 0), head);
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("leviathan_break")
                .run("stand on platform 2", () -> standOn(mc, l[0].platform(2)))
                .waitUntil("the Break is over", 200, () -> !ask(ThalassineLeviathan::isBroken));
        // the Moorage
        steps.command("cosmicbreach debug leviathan health " + (int) (LeviathanMoves.BASE_HEALTH * 0.49))
                .waitUntil("it swims into its Moorage", 60, () -> is(ThalassineLeviathan.State.MOORAGE))
                .waitUntil("the coil settles", 400, () -> ask(le -> le.moorStage() == ThalassineLeviathan.Moor.COILED))
                .run("mark", () -> at[0] = mc.level.getGameTime())
                .waitTicks(40)
                .run("over a platform by the coil", () -> {
                    Vec3 mid = ask(le -> le.point(2));
                    Vec3 c = l[0].centre();
                    Vec3 out = new Vec3(mid.x - c.x, 0, mid.z - c.z).normalize();
                    camera(mc, c.add(out.scale(36)).add(0, 14, 0), mid.add(0, 1, 0));
                })
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("moorage_coil")
                .log("moorage", () -> ask(le -> {
                    String s = String.format(Locale.ROOT, "Moorage: %d coil blocks, %d bridge blocks, glands exposed %s", le.coilBlocks().size(),
                            le.bridgeBlocks().size(), le.glandsExposed());
                    summary.add(s);
                    return s;
                }))
                .waitUntil("a shudder's ripple runs", 200, () -> ask(le -> {
                    long t = le.level().getGameTime() - le.moorStart();
                    return t >= 72 && t < 80;
                }))
                .run("close over the coil", () -> {
                    Vec3 mid = ask(le -> le.point(2));
                    Vec3 c = l[0].centre();
                    Vec3 out = new Vec3(mid.x - c.x, 0, mid.z - c.z).normalize();
                    camera(mc, mid.add(out.scale(9)).add(0, 7, 0), mid.add(0, 1, 0));
                })
                .waitTicks(1)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("moorage_ripple")
                .command("gamemode creative")
                .check("the coil's walkable back and the bridges were laid", () -> ask(le -> le.coilBlocks().size() > 30 && le.bridgeBlocks().size() > 10));
    }
}
