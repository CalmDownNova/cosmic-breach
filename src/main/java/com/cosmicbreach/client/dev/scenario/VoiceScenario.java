package com.cosmicbreach.client.dev.scenario;

import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.aim;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.count;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.emptyHand;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.player;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.select;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.server;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.standNear;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.tp;
import static com.cosmicbreach.client.dev.scenario.OnboardingScenario.walkToward;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.onboarding.BreachWind;
import com.cosmicbreach.client.onboarding.FallUpClient;
import com.cosmicbreach.client.voice.EchoClient;
import com.cosmicbreach.guardian.GuardianCommands;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.mixin.client.GuiSubtitleAccessor;
import com.cosmicbreach.onboarding.BreachRing;
import com.cosmicbreach.onboarding.FallUp;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.onboarding.Starfalls;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.voice.EchoQueue;
import com.cosmicbreach.world.AetheriaWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Starfall's voice (A1), each moment reached with real steps in a fresh normal world, listening to the sound
 * events the client plays:
 *
 * <ol>
 *   <li>A Starfall now: its boom reaches the player (the recorded impact is in it); the shard broken by hand and
 *       picked up: "Take me home." after its lead-in, its subtitle on screen (screenshot) and still up after
 *       vanilla's 3 s; a second shard picked up: nothing.</li>
 *   <li>A ring of 12 frames, opened with a shard (the use key): the ring's line after the chord; the Breach's wind
 *       starts as one loop that keeps playing past its 7.75 s length without being started again, fades out 40
 *       blocks away and comes back with the player; the ring broken and opened again: no second line.</li>
 *   <li>Stepping in: the arrival's line only once the white of falling up has cleared (screenshot); home and up
 *       again through the same ring: nothing.</li>
 *   <li>The Colossus through the debug path (lair, walk in, shatter, a punch, {@code /kill} the shards): "One more
 *       voice" after the rewards; a repeat kill woken with a Guardian Echo: nothing.</li>
 *   <li>Two lines said at once ({@code /cosmicbreach echo play}): the second waits for the first to end.</li>
 * </ol>
 * The master volume is set to 1e-5 (-100 dB) for the run: the engine really plays, so loops, lengths and
 * subtitles are real, but nothing can be heard. The test client's options put it back to 0 next run.
 */
public final class VoiceScenario implements Scenario {
    private static final double QUIET = 1.0E-5;
    private static final ResourceLocation BOOM = CosmicBreach.id("onboarding/starfall_boom");
    private static final ResourceLocation WIND = CosmicBreach.id("onboarding/breach_wind");

    /** Sound id to the EchoClient ticks at which the client played it. */
    private final Map<ResourceLocation, List<Long>> played = new ConcurrentHashMap<>();
    private final List<String> summary = new ArrayList<>();

    private static final class State {
        BlockPos shard;
        BlockPos ring;
        long mark;
        SoundInstance wind;
        int windStarts;
    }

    @Override
    public boolean normalWorld() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, event -> {
            if (event.getOriginalSound() != null) {
                played.computeIfAbsent(event.getOriginalSound().getLocation(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                        .add(EchoClient.ticks());
            }
        });
        steps.run("clouds off", () -> mc.options.cloudStatus().set(CloudStatus.OFF))
                .run("sound at -100 dB: really played, never heard", () -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(QUIET))
                .run("subtitles on, the HUD on", () -> {
                    mc.options.showSubtitles().set(true);
                    mc.options.hideGui = false;
                })
                .command("gamemode survival")
                .command("difficulty peaceful")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .check("every line is a sound whose subtitle is its words in quotes", () -> {
                    for (EchoLine line : EchoLine.values()) {
                        WeighedSoundEvents events = mc.getSoundManager().getSoundEvent(CosmicBreach.id(line.soundPath()));
                        if (events == null || events.getSubtitle() == null
                                || !(events.getSubtitle().getContents() instanceof TranslatableContents t)
                                || !t.getKey().equals(line.subtitleKey())
                                || !I18n.get(line.subtitleKey()).equals("\"" + line.words() + "\"")) {
                            return false;
                        }
                    }
                    return true;
                });
        firstShard(steps, mc, s);
        ring(steps, mc, s);
        arrival(steps, mc, s);
        colossus(steps, mc, s);
        oneAtATime(steps, mc);
        steps.check("the story's lines each played once, in order, never overlapping", () -> {
                    List<EchoClient.Played> story = EchoClient.history().stream()
                            .filter(p -> p.line() != EchoLine.DEEP && p.line() != EchoLine.SANCTUM).toList();
                    List<EchoLine> order = story.stream().map(EchoClient.Played::line).toList();
                    return order.equals(List.of(EchoLine.FIRST_SHARD, EchoLine.RING_OPEN, EchoLine.ARRIVAL, EchoLine.DRIFT))
                            && noOverlap(EchoClient.history());
                })
                .check("each ran its length (the engine played them)", () -> EchoClient.history().stream()
                        .allMatch(p -> p.end() - p.start() >= p.line().lengthTicks() - 2 && p.end() - p.start() <= p.line().lengthTicks() + 25))
                .check("the server remembers exactly the four", () -> ServerQuery.ask(p -> {
                    for (EchoLine line : EchoLine.values()) {
                        boolean want = line == EchoLine.FIRST_SHARD || line == EchoLine.RING_OPEN || line == EchoLine.ARRIVAL
                                || line == EchoLine.DRIFT;
                        if (Echo.heard(p, line) != want) {
                            return false;
                        }
                    }
                    return true;
                }))
                .log("summary", () -> {
                    List<String> lines = new ArrayList<>(summary);
                    for (EchoClient.Played p : EchoClient.history()) {
                        lines.add(String.format(Locale.ROOT, "%s: ticks %d to %d (%d, its file %d)", p.line().id(), p.start(), p.end(),
                                p.end() - p.start(), p.line().lengthTicks()));
                    }
                    return "SUMMARY\n  " + String.join("\n  ", lines);
                })
                .run("sound off again", () -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
    }

    // ------------------------------------------------------------------ a Starfall and its shard

    private void firstShard(Steps steps, Minecraft mc, State s) {
        steps.run("step up into the open", () -> tp(server(srv -> {
                    ServerPlayer p = player(srv);
                    int y = srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
                    return new Vec3(p.getBlockX() + 0.5, y, p.getBlockZ() + 0.5);
                }, 5)))
                .waitTicks(10)
                .command("cosmicbreach starfall now")
                .waitUntil("the Starfall lands", 300, () -> server(srv -> Starfalls.last() != null, 5))
                .waitUntil("its boom reaches the player", 60, () -> played(BOOM) == 1)
                .run("read the landing", () -> s.shard = server(srv -> Starfalls.last().shard(), 5))
                .run("stand on the crater's edge", () -> tp(server(srv -> standNear(srv.overworld(), s.shard), 5)))
                .waitTicks(10)
                .run("an empty hand", () -> emptyHand(mc))
                .waitTicks(2)
                .run("look at the shard", () -> aim(mc, Vec3.atCenterOf(s.shard)))
                .waitTicks(1)
                .hold(mc.options.keyAttack)
                .waitUntil("the shard breaks by hand", 20, () -> mc.level.getBlockState(s.shard).isAir())
                .release(mc.options.keyAttack)
                .hold(mc.options.keyUp)
                .waitUntil("the shard is picked up", 240, () -> {
                    walkToward(mc, OnboardingRegistry.STARFALL_SHARD.get());
                    return count(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 1;
                })
                .release(mc.options.keyUp)
                .run("mark the pickup", () -> s.mark = EchoClient.ticks())
                .waitUntil("the Starfall's voice speaks", 80, () -> played(line(EchoLine.FIRST_SHARD)) == 1)
                .check("after its lead-in", () -> first(EchoLine.FIRST_SHARD) - s.mark >= EchoLine.FIRST_SHARD.leadTicks() - 2)
                .log("the shard's line", () -> String.format(Locale.ROOT, "\"Take me home.\" %d ticks after the pickup",
                        first(EchoLine.FIRST_SHARD) - s.mark))
                .waitTicks(40)
                .check("its subtitle is on screen", () -> subtitleShowing(mc, EchoLine.FIRST_SHARD))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("subtitle_first_shard")
                .waitTicks(70)
                .check("still on screen past vanilla's 3 s", () -> EchoClient.playing() == EchoLine.FIRST_SHARD
                        && subtitleShowing(mc, EchoLine.FIRST_SHARD))
                .check("the server remembers it", () -> ServerQuery.ask(p -> Echo.heard(p, EchoLine.FIRST_SHARD)))
                .waitUntil("the line ends", 200, () -> EchoClient.playing() == null)
                .command("summon item ~ ~ ~ {Item:{id:\"cosmicbreach:starfall_shard\",count:1}}")
                .waitUntil("a second shard is picked up", 100, () -> count(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 2)
                .waitTicks(60)
                .check("the shard's line played once, not twice", () -> played(line(EchoLine.FIRST_SHARD)) == 1
                        && EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .run("note it", () -> summary.add("first shard: the boom, \"Take me home.\" once, not for the second shard"));
    }

    // ------------------------------------------------------------------ a ring, the wind

    private void ring(Steps steps, Minecraft mc, State s) {
        steps.run("lay a ring of 12 frames on flat ground 8 blocks east", () -> server(srv -> {
                    ServerPlayer p = player(srv);
                    ServerLevel ow = srv.overworld();
                    int x = p.getBlockX() + 8;
                    int z = p.getBlockZ();
                    int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    s.ring = new BlockPos(x, y, z);
                    for (int dx = -4; dx <= 5; dx++) {
                        for (int dz = -4; dz <= 6; dz++) {
                            for (int dy = -2; dy <= 5; dy++) {
                                ow.setBlock(s.ring.offset(dx, dy, dz), dy < 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                        : Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                    for (int[] o : BreachRing.RING) {
                        ow.setBlock(s.ring.offset(o[0], 0, o[1]), ModBlocks.BREACH_FRAME.get().defaultBlockState(), 3);
                    }
                    return null;
                }, 10))
                .waitTicks(10)
                .run("stand south of it", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY(), s.ring.getZ() + 3.6)))
                .waitTicks(10)
                .run("hold a shard", () -> select(mc, OnboardingRegistry.STARFALL_SHARD.get()))
                .waitTicks(2)
                .run("look at a south frame", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(0, 0, 2)).add(0, 0.5, 0)))
                .run("count the wind's loops so far", () -> s.windStarts = BreachWind.started())
                .press(mc.options.keyUse)
                .waitUntil("the Breach opens", 20, () -> mc.level.getBlockState(s.ring).is(OnboardingRegistry.BREACH.get()))
                .run("mark the opening", () -> s.mark = EchoClient.ticks())
                .waitUntil("the wind starts", 40, () -> BreachWind.playing() == 1)
                .run("remember the loop", () -> s.wind = BreachWind.loopAt(s.ring))
                .check("one loop, repeated by the engine (looping, no delay)", () -> s.wind != null && s.wind.isLooping()
                        && s.wind.getDelay() == 0 && played(WIND) == 1)
                .waitUntil("the Starfall's voice speaks", 120, () -> played(line(EchoLine.RING_OPEN)) == 1)
                .check("after its lead-in (the ring's chord first)", () -> first(EchoLine.RING_OPEN) - s.mark >= EchoLine.RING_OPEN.leadTicks() - 2)
                .waitTicks(100)
                .check("its subtitle is still up 5 s in", () -> EchoClient.playing() == EchoLine.RING_OPEN
                        && subtitleShowing(mc, EchoLine.RING_OPEN))
                .waitTicks(100)
                .check("10 s on, past the loop's 7.75 s: the same loop still playing, never started again", () ->
                        BreachWind.loopAt(s.ring) == s.wind && played(WIND) == 1 && BreachWind.started() == s.windStarts + 1)
                .run("walk 40 blocks away", () -> tp(server(srv -> {
                    int x = s.ring.getX() + 1;
                    int z = s.ring.getZ() + 41;
                    return new Vec3(x + 0.5, srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
                }, 5)))
                .waitUntil("the wind fades out and stops", 80, () -> BreachWind.playing() == 0)
                .run("come back", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY(), s.ring.getZ() + 3.6)))
                .waitUntil("the wind starts again", 60, () -> BreachWind.playing() == 1 && played(WIND) == 2)
                .waitUntil("the line ends", 200, () -> EchoClient.playing() == null)
                .run("break a frame: the ring closes", () -> server(srv -> srv.overworld().destroyBlock(s.ring.offset(-1, 0, -1), false), 5))
                .waitUntil("the Breach is gone", 20, () -> !mc.level.getBlockState(s.ring).is(OnboardingRegistry.BREACH.get()))
                .waitUntil("and its wind with it", 60, () -> BreachWind.playing() == 0)
                .run("the frame back", () -> server(srv -> srv.overworld().setBlock(s.ring.offset(-1, 0, -1),
                        ModBlocks.BREACH_FRAME.get().defaultBlockState(), 3), 5))
                .waitTicks(5)
                .run("hold the other shard", () -> select(mc, OnboardingRegistry.STARFALL_SHARD.get()))
                .waitTicks(2)
                .run("look at a south frame", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(0, 0, 2)).add(0, 0.5, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the Breach opens again", 20, () -> mc.level.getBlockState(s.ring).is(OnboardingRegistry.BREACH.get()))
                .waitTicks(120)
                .check("the ring's line played once, not twice", () -> played(line(EchoLine.RING_OPEN)) == 1
                        && EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "ring: its line %d ticks after opening, once; the wind one "
                        + "loop past 10 s, stopped 40 blocks away, back with the player", first(EchoLine.RING_OPEN) - s.mark)));
    }

    // ------------------------------------------------------------------ falling up

    private void stepIn(Steps steps, Minecraft mc, State s) {
        steps.run("stand on the south frame", () -> tp(new Vec3(s.ring.getX() + 1.5, s.ring.getY() + 1, s.ring.getZ() + 2.5)))
                .waitTicks(10)
                .run("face the Breach", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(1, 0, 1)).add(0, 1.2, 0)))
                .hold(mc.options.keyUp)
                .waitUntil("the pull starts", 80, () -> {
                    if (!FallUpClient.active()) {
                        aim(mc, new Vec3(s.ring.getX() + 1.5, mc.player.getEyeY() - 0.3, s.ring.getZ() + 1.5));
                    }
                    return FallUpClient.active();
                })
                .release(mc.options.keyUp)
                .waitUntil("in Aetheria", 300, () -> AetheriaWorld.is(mc.level))
                .waitUntil("the white has cleared", 200, () -> !FallUpClient.active());
    }

    private void arrival(Steps steps, Minecraft mc, State s) {
        stepIn(steps, mc, s);
        steps.run("mark the white clearing", () -> s.mark = EchoClient.ticks())
                .waitUntil("the Starfall's voice speaks", 100, () -> played(line(EchoLine.ARRIVAL)) == 1)
                .check("only after the white of falling up", () -> first(EchoLine.ARRIVAL) >= s.mark + EchoLine.ARRIVAL.leadTicks() - 1)
                .waitTicks(30)
                .check("its subtitle is on screen", () -> subtitleShowing(mc, EchoLine.ARRIVAL))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("subtitle_arrival")
                .waitUntil("the line ends", 200, () -> EchoClient.playing() == null)
                .run("home, beside the ring", () -> server(srv -> {
                    ServerPlayer p = player(srv);
                    p.teleportTo(srv.overworld(), s.ring.getX() + 1.5, s.ring.getY(), s.ring.getZ() + 3.5, java.util.Set.of(), 180f, 20f);
                    return null;
                }, 10))
                .waitUntil("in the Overworld", 300, () -> mc.level.dimension() == Level.OVERWORLD && mc.screen == null)
                .waitUntil("the Breach takes them again (10 s after an arrival)", 400, () -> server(srv -> {
                    FallUp.Arrival a = FallUp.lastArrival(player(srv).getUUID());
                    return a == null || srv.getTickCount() - a.tick() > FallUp.COOLDOWN_TICKS;
                }, 5));
        stepIn(steps, mc, s);
        steps.waitTicks(100)
                .check("the arrival's line played once, not twice", () -> played(line(EchoLine.ARRIVAL)) == 1
                        && EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .run("note it", () -> summary.add("arrival: after the white cleared, once; a second fall up: nothing"));
    }

    // ------------------------------------------------------------------ the Colossus

    private static PrismColossus.State colossusState() {
        return ServerQuery.ask(p -> {
            PrismColossus c = ColossusScenario.colossusOrNull(p);
            return c == null ? null : c.state();
        });
    }

    private void fightToTheShards(Steps steps, Minecraft mc) {
        int[] tick = {0};
        steps.waitUntil("the intro ends", 260, () -> colossusState() == PrismColossus.State.FIGHT)
                .command("cosmicbreach debug colossus hold 1000000")
                .command("cosmicbreach debug colossus shatter")
                .waitUntil("it shatters into three", 40, () -> ServerQuery.ask(p -> {
                    PrismColossus c = ColossusScenario.colossusOrNull(p);
                    return c != null && c.state() == PrismColossus.State.SHATTERED && c.shardIds().size() == 3;
                }))
                .waitTicks(30)
                .waitUntil("a shard is hit (swinging at the nearest: now a participant)", 300, () -> hitAShard(mc, tick[0]++))
                .run("let go of the attack key", () -> net.minecraft.client.KeyMapping.set(mc.options.keyAttack.getKey(), false))
                .command("kill @e[type=cosmicbreach:prism_shard]")
                .waitUntil("the Colossus falls", 200, () -> colossusState() == null);
    }

    private void colossus(Steps steps, Minecraft mc, State s) {
        steps.command("difficulty normal")                       // a guardian sleeps through Peaceful
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 1 true")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .command("cosmicbreach debug goto reach")
                .waitUntil("on a Reach island", 600, () -> AetheriaWorld.is(mc.level) && mc.screen == null)
                .waitTicks(100)
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair colossus")
                .waitUntil("the lair is built and its Colossus stands", 200, () -> GuardianCommands.lastBuilt() != null
                        && colossusState() == PrismColossus.State.DORMANT)
                .run("onto the crown (not attuned: it wakes)", () -> {
                    CrownArena a = ColossusScenario.arena();
                    ColossusScenario.tp(mc, a.x() + 0.5, a.floorY(), a.z() + 8.5, a.x(), a.floorY() + 3, a.z());
                })
                .waitUntil("it wakes", 80, () -> colossusState() == PrismColossus.State.INTRO);
        fightToTheShards(steps, mc);
        steps.run("mark the kill", () -> s.mark = EchoClient.ticks())
                .check("a first kill: Refracted", () -> ServerQuery.ask(p -> GuardianRewards.hasAdvancement(p, GuardianTypes.COLOSSUS.advancement())))
                .waitUntil("the Starfall's voice speaks", 200, () -> played(line(EchoLine.DRIFT)) == 1)
                .check("after the rewards and its lead-in", () -> first(EchoLine.DRIFT) - s.mark >= EchoLine.DRIFT.leadTicks() - 2)
                .waitUntil("the line ends", 250, () -> EchoClient.playing() == null)
                .command("cosmicbreach debug colossus cooldown")
                .waitUntil("a new statue stands", 100, () -> colossusState() == PrismColossus.State.DORMANT)
                .command("give @s cosmicbreach:guardian_echo")
                .run("to the altar", () -> {
                    int[] a = GuardianCommands.lastBuilt().altar();
                    CrownArena ar = ColossusScenario.arena();
                    Vec3 in = new Vec3(ar.x() - a[0], 0, ar.z() - a[2]).normalize();
                    ColossusScenario.tp(mc, a[0] + 0.5 + in.x * 2.2, a[1], a[2] + 0.5 + in.z * 2.2, a[0] + 0.5, a[1] + 0.6, a[2] + 0.5);
                })
                .waitTicks(3)
                .run("hold the Guardian Echo", () -> select(mc, GuardianRegistry.GUARDIAN_ECHO.get()))
                .waitTicks(2)
                .run("look at the altar", () -> {
                    int[] a = GuardianCommands.lastBuilt().altar();
                    aim(mc, new Vec3(a[0] + 0.5, a[1] + 0.6, a[2] + 0.5));
                })
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the Echo wakes it", 40, () -> colossusState() == PrismColossus.State.INTRO);
        fightToTheShards(steps, mc);
        steps.waitTicks(EchoLine.DRIFT.leadTicks() + 60)
                .check("a repeat kill: the Colossus's line played once, not twice", () -> played(line(EchoLine.DRIFT)) == 1
                        && EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Colossus: \"One more voice\" %d ticks after the kill; "
                        + "a repeat kill: nothing", first(EchoLine.DRIFT) - s.mark)));
    }

    /**
     * One swing's worth of work toward hitting a shard: step up to the nearest when it is out of reach, face it, and
     * swing every 9 ticks (as ColossusScenario does). True once any shard has taken damage from the player.
     */
    private static boolean hitAShard(Minecraft mc, int t) {
        net.minecraft.client.KeyMapping key = mc.options.keyAttack;
        Object[] found = ServerQuery.ask(p -> {
            PrismColossus c = ColossusScenario.colossusOrNull(p);
            if (c == null) {
                return null;
            }
            PrismShard best = null;
            boolean hurt = false;
            for (java.util.UUID id : c.shardIds()) {
                if (p.serverLevel().getEntity(id) instanceof PrismShard sh && sh.isAlive()) {
                    hurt |= sh.getHealth() < sh.getMaxHealth();
                    if (best == null || sh.distanceToSqr(p) < best.distanceToSqr(p)) {
                        best = sh;
                    }
                }
            }
            return new Object[] {hurt, best == null ? null : best.position()};
        });
        if (found == null) {
            throw new Steps.Failure("the Colossus is gone before a shard was hit");
        }
        if ((boolean) found[0]) {
            return true;
        }
        Vec3 shard = (Vec3) found[1];
        if (shard == null) {
            return false;
        }
        Vec3 me = mc.player.position();
        if (me.distanceTo(shard) > 2.6 && t % 9 == 2) {
            Vec3 from = shard.add(new Vec3(me.x - shard.x, 0, me.z - shard.z).normalize().scale(1.6));
            ColossusScenario.tp(mc, from.x, shard.y, from.z, shard.x, shard.y + 0.3, shard.z);
        }
        aim(mc, shard.add(0, 0.3, 0));
        if (t % 9 == 0) {
            net.minecraft.client.KeyMapping.set(key.getKey(), true);
            net.minecraft.client.KeyMapping.click(key.getKey());
        } else if (t % 9 == 1) {
            net.minecraft.client.KeyMapping.set(key.getKey(), false);
        }
        return false;
    }

    // ------------------------------------------------------------------ never over itself

    private void oneAtATime(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach echo play deep")
                .command("cosmicbreach echo play sanctum")
                .waitUntil("both have played", EchoLine.DEEP.leadTicks() + EchoLine.DEEP.lengthTicks() + EchoLine.SANCTUM.lengthTicks() + 120,
                        () -> EchoClient.history().stream().anyMatch(p -> p.line() == EchoLine.SANCTUM))
                .check("the second waited for the first to end", () -> {
                    EchoClient.Played deep = find(EchoLine.DEEP);
                    EchoClient.Played sanctum = find(EchoLine.SANCTUM);
                    return deep != null && sanctum != null && sanctum.start() >= deep.end() + EchoQueue.GAP;
                })
                .check("played from the debug command, not remembered", () -> ServerQuery.ask(p -> !Echo.heard(p, EchoLine.DEEP)
                        && !Echo.heard(p, EchoLine.SANCTUM)))
                .run("note it", () -> {
                    EchoClient.Played deep = find(EchoLine.DEEP);
                    EchoClient.Played sanctum = find(EchoLine.SANCTUM);
                    summary.add(String.format(Locale.ROOT, "two at once: the second started %d ticks after the first ended",
                            sanctum.start() - deep.end()));
                });
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation line(EchoLine line) {
        return CosmicBreach.id(line.soundPath());
    }

    private int played(ResourceLocation sound) {
        return played.getOrDefault(sound, List.of()).size();
    }

    private long first(EchoLine line) {
        List<Long> at = played.get(line(line));
        if (at == null || at.isEmpty()) {
            throw new Steps.Failure(line.id() + " never played");
        }
        return at.get(0);
    }

    private static EchoClient.Played find(EchoLine line) {
        return EchoClient.history().stream().filter(p -> p.line() == line).findFirst().orElse(null);
    }

    private static boolean noOverlap(List<EchoClient.Played> history) {
        for (int i = 1; i < history.size(); i++) {
            if (history.get(i).start() < history.get(i - 1).end()) {
                return false;
            }
        }
        return true;
    }

    /** True if the subtitle overlay holds the line's subtitle now. */
    private static boolean subtitleShowing(Minecraft mc, EchoLine line) {
        SubtitleOverlay overlay = ((GuiSubtitleAccessor) mc.gui).cosmicbreach$subtitleOverlay();
        Component want = Component.translatable(line.subtitleKey());
        try {
            Field field = SubtitleOverlay.class.getDeclaredField("subtitles");
            field.setAccessible(true);
            for (Object subtitle : (List<?>) field.get(overlay)) {
                Method text = subtitle.getClass().getDeclaredMethod("getText");
                Method active = subtitle.getClass().getDeclaredMethod("isStillActive");
                text.setAccessible(true);
                active.setAccessible(true);
                if (want.equals(text.invoke(subtitle)) && (boolean) active.invoke(subtitle)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot read the subtitles: " + e);
        }
    }
}
