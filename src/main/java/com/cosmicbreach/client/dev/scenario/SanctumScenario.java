package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gear.AstralForgeScreen;
import com.cosmicbreach.client.sanctum.SanctumClient;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.forge.AstralForgeBlock;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.sanctum.EclipseLockBlock;
import com.cosmicbreach.structure.sanctum.FallRescue;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumBuilder;
import com.cosmicbreach.structure.sanctum.SanctumCommands;
import com.cosmicbreach.structure.sanctum.SanctumData;
import com.cosmicbreach.structure.sanctum.SanctumGate;
import com.cosmicbreach.structure.sanctum.SanctumGateBlock;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.structure.sanctum.SanctumThrone;
import com.cosmicbreach.structure.sanctum.SanctumThroneBlock;
import com.cosmicbreach.structure.sanctum.Sanctums;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.LayerAttunement;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.lwjgl.glfw.GLFW;

/**
 * The Breach Sanctum (W7) with real inputs, at Attunement level 36. Three parts, each able to run alone
 * ({@code sanctum-approach}, {@code sanctum-wings}, {@code sanctum-arena}) or all in one world ({@code sanctum}):
 *
 * <ol>
 *   <li>APPROACH: the Sanctum built at its place (timed); a view of it from the Deep platform where its causeway
 *       starts (the embers' glow must be drawn); the causeway walked to the forecourt; the Gate photographed; walked
 *       into without the attunement (refused: still outside, the doors shut, the subtitle); an attuned stand-in
 *       arrives at the Gate and the player, 12 blocks off, is admitted as their party, walks through, and a stranger
 *       30 blocks off is not (the open veil still stops them); out again, the doors close behind, and back in (a
 *       returning party); then the party forgotten, the player attuned: the Gate opens for them.</li>
 *   <li>WINGS: the west corridor and round the Lens of Solenne to its vault (the Array wakes, photographed), the puzzle
 *       solved by the debug command, the west lock lit, the vault opened with the use key (the Solar Ember and the
 *       Event Horizon Lens); the same for the Choir of the Unsung (the Hymn Crystal and the Hourglass of Vesper); the
 *       Throne Stair opening, photographed from the hall and from its head, and walked down onto the arena.</li>
 *   <li>ARENA: every segment of every ring and every pillar in place; a pillar struck with a pickaxe for three seconds
 *       stands; the arena from the throne and from the rim; a walk off the rim into the Breach and the throw back (the
 *       rim, half the health, 60 ticks of Voidsick, the ability refused until it passes and cast after); a Forge III
 *       by the dais, the Dying Star Heart forged from the wings' prizes and set on the throne (it hums, the sky
 *       flickers, nothing answers yet, the Heart comes back).</li>
 * </ol>
 */
public final class SanctumScenario implements Scenario {
    public enum Part { APPROACH, WINGS, ARENA, WORLD }

    private final EnumSet<Part> parts;

    public SanctumScenario() {
        this.parts = EnumSet.of(Part.APPROACH, Part.WINGS, Part.ARENA);
    }

    public SanctumScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return parts.size() == 3 ? 1500 : 600;
    }

    /** WORLD: worldgen places the Sanctum itself (its one-per-world placement), so structures are on. */
    @Override
    public boolean generateStructures() {
        return parts.contains(Part.WORLD);
    }

    /** What the steps learn as they go. */
    private static final class State {
        SanctumLayout layout;
        final List<double[]> route = new ArrayList<>();
        int waypoint;
        Vec3 last;
        int stuck;
        long walkStart;
        DevCamera camera;
        float healthBefore;
        double resonanceBefore;
        long xpBefore;
        long leftRim;
        int against;
        BlockPos forge;
        final List<String> results = new ArrayList<>();
    }

    private static final UUID OPENER = UUID.nameUUIDFromBytes("sanctum-opener".getBytes());
    private static final UUID STRANGER = UUID.nameUUIDFromBytes("sanctum-stranger".getBytes());

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("cosmicbreach debug level 36")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 120 0.5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .run("fly", () -> fly(mc));
        if (parts.contains(Part.WORLD)) {
            world(steps, mc, s);
            return;
        }
        steps.run("build the Breach Sanctum at its place", () -> {
                    long ms = CryptKit.server(srv -> SanctumCommands.build(level(srv)));
                    s.layout = CryptKit.server(srv -> Sanctums.layout(level(srv)));
                    int[] e = s.layout.end();
                    s.results.add(String.format(Locale.ROOT, "built in %d ms: halls %s, causeway %.1f blocks to %d %d %d, slope %.3f", ms,
                            s.layout.side() < 0 ? "north" : "south", s.layout.causewayLength(), e[0], e[1], e[2], s.layout.causewaySlope()));
                })
                .check("the level is 36", () -> ServerQuery.ask(p -> Attunements.of(p).level()) == 36);
        if (parts.contains(Part.APPROACH)) {
            approach(steps, mc, s);
        }
        if (parts.contains(Part.WINGS)) {
            wings(steps, mc, s);
        }
        if (parts.contains(Part.ARENA)) {
            arena(steps, mc, s);
        }
        steps.run("hand back the HUD", () -> uncamera(s))
                .log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    // ------------------------------------------------------------------ 0. worldgen places it

    private void world(Steps steps, Minecraft mc, State s) {
        steps.run("where worldgen should put it", () -> s.layout = CryptKit.server(srv -> Sanctums.layout(level(srv))))
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .run("hover over the forecourt", () -> {
                    double[] c = s.layout.local(0, SanctumLayout.WING_D);
                    CryptKit.tp(mc, new Vec3(c[0], 120, c[2]), new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5));
                })
                .waitUntil("the Deep round the Breach is generated and drawn", 3000, CryptKit.settled(mc, 2400))
                .check("the Sanctum stands where worldgen put it: the Gate, the seal, the locks, the throne, both puzzles, both vaults",
                        () -> CryptKit.server(srv -> {
                            ServerLevel level = level(srv);
                            boolean gate = s.layout.gateBlocks().stream().allMatch(b -> level.getBlockState(SanctumBuilder.at(b))
                                    .is(SanctumRegistry.SANCTUM_GATE.get()));
                            boolean seal = s.layout.sealBlocks().stream().allMatch(b -> level.getBlockState(SanctumBuilder.at(b))
                                    .is(SanctumRegistry.THRONE_SEAL.get()));
                            boolean locks = level.getBlockState(SanctumBuilder.at(s.layout.lock(Wing.WEST))).is(SanctumRegistry.ECLIPSE_LOCK.get())
                                    && level.getBlockState(SanctumBuilder.at(s.layout.lock(Wing.EAST))).is(SanctumRegistry.ECLIPSE_LOCK.get());
                            boolean throne = level.getBlockState(SanctumArena.THRONE).is(SanctumRegistry.SANCTUM_THRONE.get());
                            boolean lens = level.getBlockEntity(SanctumBuilder.at(s.layout.lensCore())) instanceof LensCoreBlockEntity;
                            boolean choir = level.getBlockEntity(SanctumBuilder.at(s.layout.conductor()))
                                    instanceof com.cosmicbreach.structure.choir.ConductorBlockEntity;
                            boolean vaults = vault(level, s, Wing.WEST).lootTable().equals(SanctumRegistry.LENS_VAULT_LOOT)
                                    && vault(level, s, Wing.EAST).lootTable().equals(SanctumRegistry.CHOIR_VAULT_LOOT);
                            s.results.add("worldgen: gate " + gate + ", seal " + seal + ", locks " + locks + ", throne " + throne + ", lens " + lens
                                    + ", choir " + choir + ", vaults " + vaults);
                            return gate && seal && locks && throne && lens && choir && vaults;
                        }))
                .check("the world knows it as the breach_sanctum structure", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getOrThrow(SanctumRegistry.BREACH_SANCTUM);
                    return level.structureManager().getStructureWithPieceAt(SanctumArena.THRONE, structure).isValid()
                            && level.structureManager().getStructureWithPieceAt(SanctumBuilder.at(s.layout.gateBlocks().get(0)), structure).isValid();
                }))
                .run("an overview from above the Breach", () -> {
                    double[] far = s.layout.local(-30, SanctumLayout.COURT_D1 + 20);
                    camera(mc, s, new Vec3(far[0], 138, far[2]), new Vec3(0.5, SanctumLayout.ARENA_Y + 6, 0.5 + s.layout.side() * 30));
                })
                .waitUntil("the view is drawn", 800, CryptKit.settled(mc, 600))
                .screenshot("sanctum_world_overview")
                .run("back to the player", () -> uncamera(s))
                // the far side of the Breach: the halls are past the fog there, only the embers carry
                .run("across the Breach from the causeway", () -> {
                    int[] e = s.layout.end();
                    CryptKit.tp(mc, new Vec3(-e[0] * 1.1, 110, -e[2] * 1.1), new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5));
                })
                .waitTicks(20)
                .command("cosmicbreach debug goto deep")
                .waitTicks(20)
                .waitUntil("the far platform is drawn", 2400, CryptKit.settled(mc, 1800))
                .run("look toward the Sanctum", () -> {
                    Vec3 eye = mc.player.getEyePosition();
                    camera(mc, s, eye.add(0, 1.0, 0), new Vec3(0.5, SanctumLayout.ARENA_Y + 12, 0.5));
                    s.results.add(String.format(Locale.ROOT, "far platform %.0f blocks from the throne's column", Math.hypot(eye.x - 0.5, eye.z - 0.5)));
                    s.against = SanctumClient.glowFrames();
                })
                .waitTicks(10)
                .check("the embers' glow is drawn from across the Breach", () -> SanctumClient.glowFrames() > s.against)
                .screenshot("sanctum_from_across_the_breach")
                .run("back to the player", () -> uncamera(s))
                .log("results", () -> String.join(System.lineSeparator(), s.results));
    }

    // ------------------------------------------------------------------ 1. the approach and the Gate

    private void approach(Steps steps, Minecraft mc, State s) {
        steps.command("cosmicbreach debug goto sanctum")
                .run("a longer view", () -> mc.options.renderDistance().set(12))
                .waitUntil("the view from the Deep's edge is drawn", 2400, CryptKit.settled(mc, 1800))
                .waitTicks(10)
                .check("the embers over the arena glow through the fog", () -> SanctumClient.glowFrames() > 0)
                .run("the player's view, without the HUD", () -> {
                    Vec3 eye = mc.player.getEyePosition();
                    double[] g = s.layout.gateCentre();
                    camera(mc, s, eye.add(0, 1.5, 0), new Vec3(g[0], g[1] - 4, g[2]));
                })
                .waitTicks(5)
                .screenshot("sanctum_from_the_deep_edge")
                .run("back to the player", () -> uncamera(s))
                .run("the usual view", () -> mc.options.renderDistance().set(8))
                .command("gamemode survival")
                .run("heal", CryptKit::heal)
                .run("plan the walk along the causeway to the forecourt", () -> {
                    List<double[]> a = s.layout.approach();
                    route(s, a.subList(0, a.size() - 2));
                    s.walkStart = mc.level.getGameTime();
                })
                .waitUntil("walked the causeway from the Deep platform to the forecourt", 4000, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .run("result", () -> s.results.add(String.format(Locale.ROOT, "causeway walked in %d ticks (%.1f blocks), %d ticks nudged",
                        mc.level.getGameTime() - s.walkStart, s.layout.causewayLength(), s.stuck)))
                .check("on the forecourt, before the Gate", () -> localD(s, mc.player.getZ()) > SanctumLayout.GATE_D
                        && Math.abs(mc.player.getY() - SanctumLayout.HALL_Y) < 0.6)
                .run("a view of the Gate", () -> {
                    double[] g = s.layout.gateCentre();
                    double[] eye = s.layout.local(3.5, SanctumLayout.COURT_D1 - 1);
                    camera(mc, s, new Vec3(eye[0], eye[1] + 3.2, eye[2]), new Vec3(g[0], g[1] + 1.5, g[2]));
                })
                .waitUntil("the Gate is drawn", 600, CryptKit.settled(mc, 400))
                .screenshot("sanctum_gate")
                .run("back to the player", () -> uncamera(s))
                // refused without the attunement
                .check("not attuned to the Sanctum", () -> !ServerQuery.ask(LayerAttunement::hasSanctum))
                .run("into the Gate", () -> route(s, List.of(s.layout.local(0, SanctumLayout.COURT_D0 + 1),
                        s.layout.local(0, SanctumLayout.GATE_D - 3))))
                .waitUntil("told the Gate doesn't know their song", 400, () -> {
                    follow(mc, s);
                    return subtitle(mc).contains("does not know your song");
                })
                .waitUntil("pressed against the Gate for two seconds", 400, () -> {
                    follow(mc, s);
                    if (localD(s, mc.player.getZ()) < SanctumLayout.COURT_D0 + 0.4) {
                        s.against++;
                    }
                    return s.against >= 40;
                })
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitTicks(5)
                .log("refused", () -> String.format(Locale.ROOT, "at d %.2f, gate %s, counters %s, subtitle '%s'", localD(s, mc.player.getZ()),
                        gateOpen(s) ? "open" : "shut", CryptKit.server(srv -> SanctumGate.counters()), subtitle(mc)))
                .check("refused: still outside, the doors shut, told so", () -> localD(s, mc.player.getZ()) >= SanctumLayout.GATE_D + 1
                        && !gateOpen(s) && refusals() >= 1)
                // a party member comes through with an attuned opener
                .run("an attuned friend arrives at the Gate, 12 blocks from the player; a stranger waits 30 blocks off", () -> {
                    List<UUID> party = CryptKit.server(srv -> {
                        ServerLevel level = level(srv);
                        double[] at = s.layout.local(0, SanctumLayout.COURT_D0 + 2);
                        FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(STRANGER, "Stranger"));
                        double[] far = s.layout.local(0, SanctumLayout.COURT_D1 + 20);
                        stranger.moveTo(far[0], SanctumLayout.HALL_Y, far[2]);
                        return SanctumGate.arrive(level, OPENER, at);
                    });
                    s.results.add("party admitted with the opener: " + party.size() + " (the opener and the player)");
                })
                .log("party", () -> String.join("; ", s.results))
                .check("the player came in as the opener's party, the stranger did not", () -> CryptKit.server(srv -> {
                    SanctumData d = SanctumData.get(level(srv));
                    return d.admitted(CryptKit.player(srv).getUUID()) && d.admitted(OPENER) && !d.admitted(STRANGER);
                }))
                .run("through the Gate", () -> route(s, List.of(s.layout.local(0, SanctumLayout.COURT_D0 + 2),
                        s.layout.local(0, SanctumLayout.GATE_D - 3), s.layout.local(0, SanctumLayout.WING_D + 8))))
                .waitUntil("walked through the Gate as a party member", 600, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .check("inside the halls", () -> s.layout.insideHalls(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                .check("the open veil lets the party through and stops a stranger", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    BlockPos g = SanctumBuilder.at(s.layout.gateBlocks().get(2));
                    BlockState st = level.getBlockState(g);
                    FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(STRANGER, "Stranger"));
                    boolean open = st.getValue(SanctumGateBlock.OPEN);
                    boolean partyPasses = st.getCollisionShape(level, g, CollisionContext.of(CryptKit.player(srv))).isEmpty();
                    boolean strangerStopped = !st.getCollisionShape(level, g, CollisionContext.of(stranger)).isEmpty();
                    s.results.add("veil: open " + open + ", party passes " + partyPasses + ", stranger stopped " + strangerStopped);
                    return open && partyPasses && strangerStopped;
                }))
                .run("out again", () -> route(s, List.of(s.layout.local(0, SanctumLayout.GATE_D - 3),
                        s.layout.local(0, SanctumLayout.COURT_D0 + 2), s.layout.local(0, SanctumLayout.COURT_D1 - 1))))
                .waitUntil("walked back out to the forecourt", 600, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitUntil("the Gate closed behind", 200, () -> !gateOpen(s))
                .run("and back in, alone", () -> route(s, List.of(s.layout.local(0, SanctumLayout.COURT_D0 + 2),
                        s.layout.local(0, SanctumLayout.GATE_D - 3), s.layout.local(0, SanctumLayout.WING_D + 8))))
                .waitUntil("the Gate opened again for a returning party member", 600, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .check("inside again", () -> s.layout.insideHalls(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                // attuned: it opens for them
                .run("out once more", () -> route(s, List.of(s.layout.local(0, SanctumLayout.GATE_D - 3),
                        s.layout.local(0, SanctumLayout.COURT_D0 + 2), s.layout.local(0, SanctumLayout.COURT_D1 - 1))))
                .waitUntil("walked out", 600, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .waitUntil("the Gate closed", 200, () -> !gateOpen(s))
                .run("the Gate forgets the party", () -> CryptKit.server(srv -> {
                    SanctumData.get(level(srv)).clear();
                    SanctumGate.resync(CryptKit.player(srv));
                    return null;
                }))
                .command("advancement grant @s only cosmicbreach:attunement/sanctum")
                .waitUntil("attuned to the Sanctum", 60, () -> ServerQuery.ask(LayerAttunement::hasSanctum))
                .run("mark", () -> s.xpBefore = CryptKit.server(srv -> Attunements.of(CryptKit.player(srv)).totalXp()))
                .run("in, attuned", () -> route(s, List.of(s.layout.local(0, SanctumLayout.COURT_D0 + 2),
                        s.layout.local(0, SanctumLayout.GATE_D - 3), s.layout.local(0, SanctumLayout.WING_D))))
                .waitUntil("the Gate opened for the attuned player", 600, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .check("inside, as an attuned player", () -> s.layout.insideHalls(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                .run("result", () -> s.results.add("gate counters (openings refusals admissions): " + CryptKit.server(srv -> SanctumGate.counters())
                        + "; Structure Found paid on entering: " + (CryptKit.server(srv -> Attunements.of(CryptKit.player(srv)).totalXp())
                        - s.xpBefore)));
    }

    // ------------------------------------------------------------------ 2. the wings, the locks, the stair

    private void wings(Steps steps, Minecraft mc, State s) {
        if (!parts.contains(Part.APPROACH)) {
            steps.command("advancement grant @s only cosmicbreach:attunement/sanctum")
                    .command("gamemode survival")
                    .command("cosmicbreach debug goto sanctum hall")
                    .waitUntil("in the hall", 600, () -> s.layout.insideHalls(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                    .waitUntil("the hall is drawn", 600, CryptKit.settled(mc, 400));
        }
        wing(steps, mc, s, Wing.WEST);
        wing(steps, mc, s, Wing.EAST);
        steps.waitUntil("the Throne Stair opened: the seal is gone", 200, () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    return s.layout.sealBlocks().stream().allMatch(b -> level.getBlockState(SanctumBuilder.at(b)).isAir());
                }))
                .check("both locks burn", () -> CryptKit.server(srv -> lockLit(level(srv), s, Wing.WEST) && lockLit(level(srv), s, Wing.EAST)))
                .waitTicks(10)
                .log("stair", () -> "subtitle '" + subtitle(mc) + "'")
                .run("a view of the locks and the open seal from the hall", () -> {
                    double[] eye = s.layout.local(0, SanctumLayout.SEAL_D + 12);
                    double[] at = s.layout.local(0, SanctumLayout.SEAL_D);
                    camera(mc, s, new Vec3(eye[0], eye[1] + 3.0, eye[2]), new Vec3(at[0], at[1] + 3.0, at[2]));
                })
                .waitUntil("the hall is drawn", 600, CryptKit.settled(mc, 400))
                .screenshot("sanctum_locks_lit")
                .run("a view down the Throne Stair", () -> {
                    double[] eye = s.layout.local(9.0, SanctumLayout.LANDING_D - 3);
                    double[] mid = s.layout.local(0.0, SanctumLayout.STAIR_D0 + 5);
                    camera(mc, s, new Vec3(eye[0], eye[1] + 6.0, eye[2]), new Vec3(mid[0], SanctumLayout.ARENA_Y + 3, mid[2]));
                })
                .waitUntil("the stair is drawn", 600, CryptKit.settled(mc, 400))
                .screenshot("sanctum_throne_stair")
                .run("back to the player", () -> uncamera(s))
                .run("down the stair", () -> {
                    route(s, s.layout.descent());
                    s.walkStart = mc.level.getGameTime();
                })
                .waitUntil("walked from the hall down the Throne Stair onto the arena", 1200, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .run("result", () -> s.results.add("hall to the arena's rim in " + (mc.level.getGameTime() - s.walkStart) + " ticks"))
                .check("on the arena", () -> SanctumLayout.ring(mc.player.getBlockX(), mc.player.getBlockZ()) >= 0
                        && Math.abs(mc.player.getY() - SanctumLayout.ARENA_Y) < 0.6);
    }

    private void wing(Steps steps, Minecraft mc, State s, Wing wing) {
        String name = wing == Wing.WEST ? "the Lens of Solenne" : "the Choir of the Unsung";
        int[] counts = {0, 0};
        Item[] prizes = wing == Wing.WEST ? new Item[] {ModMaterials.SOLAR_EMBER.get(), SanctumRegistry.EVENT_HORIZON_LENS.get()}
                : new Item[] {ModMaterials.HYMN_CRYSTAL.get(), SanctumRegistry.HOURGLASS_OF_VESPER.get()};
        steps.run("plan the walk to " + name, () -> {
                    route(s, s.layout.toWing(wing));
                    s.walkStart = mc.level.getGameTime();
                })
                .waitUntil("walked to " + name + "'s vault", 2400, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc))
                .log("at the vault", () -> where(mc) + ", last waypoint " + s.route.get(s.route.size() - 1)[0] + " "
                        + s.route.get(s.route.size() - 1)[2])
                .run("result", () -> s.results.add(name + ": hall to vault in " + (mc.level.getGameTime() - s.walkStart) + " ticks"));
        if (wing == Wing.WEST) {
            steps.waitUntil("the Lens Array woke", 400, () -> CryptKit.server(srv ->
                    level(srv).getBlockEntity(SanctumBuilder.at(s.layout.lensCore())) instanceof LensCoreBlockEntity c
                            && c.phase() == LensCoreBlockEntity.ACTIVE))
                    .run("a view of the Lens of Solenne", () -> {
                        int[] c = s.layout.lensCore();
                        camera(mc, s, new Vec3(c[0] + 12.5, c[1] + 6.5, c[2] + 0.5 + s.layout.side() * -7), new Vec3(c[0] - 1.5, c[1] + 1.0, c[2] + 0.5));
                    })
                    .waitUntil("the room is drawn", 600, CryptKit.settled(mc, 400))
                    .waitTicks(10)
                    .screenshot("sanctum_west_wing")
                    .run("back to the player", () -> uncamera(s))
                    .log("after the view", () -> where(mc))
                    .command("cosmicbreach debug lens solve");
        } else {
            steps.run("a view of the Choir of the Unsung", () -> {
                        int[] c = s.layout.conductor();
                        camera(mc, s, new Vec3(c[0] - 9.5, c[1] + 6.0, c[2] + 0.5 + s.layout.side() * -6), new Vec3(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5));
                    })
                    .waitUntil("the room is drawn", 600, CryptKit.settled(mc, 400))
                    .waitTicks(10)
                    .screenshot("sanctum_east_wing")
                    .run("back to the player", () -> uncamera(s))
                    .command("cosmicbreach debug choir solve");
        }
        steps.waitUntil(name + " solved: its vault unsealed and its Eclipse Lock lit", 600, () -> CryptKit.server(srv ->
                        vault(level(srv), s, wing).ready() && lockLit(level(srv), s, wing)))
                .run("mark", () -> {
                    counts[0] = count(mc, prizes[0]);
                    counts[1] = count(mc, prizes[1]);
                    s.xpBefore = CryptKit.server(srv -> Attunements.of(CryptKit.player(srv)).totalXp());
                })
                .run("an empty hand", () -> selectEmpty(mc))
                .run("aim at the vault", () -> CryptKit.aim(mc, Vec3.atCenterOf(SanctumBuilder.at(s.layout.vault(wing)))))
                .waitTicks(3)
                .log("before the vault", () -> String.format(Locale.ROOT, "player at %.2f %.2f %.2f, looking at %s", mc.player.getX(), mc.player.getY(),
                        mc.player.getZ(), mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b
                                ? b.getBlockPos().toShortString() + " " + mc.level.getBlockState(b.getBlockPos()) : String.valueOf(mc.hitResult)))
                .press(mc.options.keyUse)
                .waitUntil("the vault's prizes are carried", 200, () -> count(mc, prizes[0]) > counts[0] && count(mc, prizes[1]) > counts[1])
                .run("result", () -> s.results.add(name + " vault: " + inventory(mc) + "; XP " + (CryptKit.server(srv ->
                        Attunements.of(CryptKit.player(srv)).totalXp()) - s.xpBefore)))
                .run("plan the walk back to the hall", () -> route(s, s.layout.fromWing(wing)))
                .waitUntil("walked back to the hall", 2400, () -> follow(mc, s))
                .run("stop", () -> CryptKit.releaseAll(mc));
    }

    // ------------------------------------------------------------------ 3. the arena, the fall, the Heart

    private void arena(Steps steps, Minecraft mc, State s) {
        if (!parts.contains(Part.WINGS)) {
            steps.command("cosmicbreach debug sanctum lock west")
                    .command("cosmicbreach debug sanctum lock east")
                    .command("gamemode survival")
                    .command("cosmicbreach debug goto sanctum arena")
                    .waitUntil("on the arena at the stair's foot", 600, () -> Math.abs(mc.player.getZ() - (s.layout.side() * 26 + 0.5)) < 1.5
                            && Math.abs(mc.player.getY() - SanctumLayout.ARENA_Y) < 0.6)
                    .waitUntil("the arena is drawn", 800, CryptKit.settled(mc, 600));
        }
        steps.check("every block of every segment of every ring is in place", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    StringBuilder sb = new StringBuilder("segments (blocks per segment, rings dais to rim):");
                    for (SanctumArena.Ring ring : SanctumArena.Ring.values()) {
                        sb.append(' ').append(ring.name().toLowerCase(Locale.ROOT)).append(' ');
                        for (int seg = 0; seg < SanctumArena.SEGMENTS; seg++) {
                            List<BlockPos> blocks = SanctumArena.segmentBlocks(ring, seg);
                            for (BlockPos p : blocks) {
                                if (level.getBlockState(p).isAir()) {
                                    return false;
                                }
                            }
                            if (!SanctumArena.standing(level, ring, seg)) {
                                return false;
                            }
                            sb.append(seg == 0 ? "" : "/").append(blocks.size());
                        }
                    }
                    s.results.add(sb.toString());
                    return true;
                }))
                .check("eight Choir Pillars stand at radius 22", () -> CryptKit.server(srv -> {
                    ServerLevel level = level(srv);
                    for (int i = 0; i < SanctumArena.PILLARS; i++) {
                        BlockPos base = SanctumArena.pillarBase(i);
                        if (Math.abs(Math.hypot(base.getX(), base.getZ()) - 22) > 0.8) {
                            return false;
                        }
                        for (BlockPos p : SanctumArena.pillarBlocks(i)) {
                            if (level.getBlockState(p).isAir()) {
                                return false;
                            }
                        }
                    }
                    return true;
                }))
                // a pillar can't be broken
                .command("give @s minecraft:netherite_pickaxe")
                .run("next to pillar 0", () -> {
                    BlockPos b = SanctumArena.pillarBase(0);
                    Vec3 in = new Vec3(-b.getX(), 0, -b.getZ()).normalize();
                    Vec3 at = Vec3.atBottomCenterOf(b).add(in.scale(3.2));
                    CryptKit.tp(mc, at, Vec3.atCenterOf(b.above(1)));
                })
                .waitTicks(10)
                .run("select the pickaxe", () -> selectItem(mc, net.minecraft.world.item.Items.NETHERITE_PICKAXE))
                .waitTicks(3)
                .run("aim at the pillar", () -> CryptKit.aim(mc, Vec3.atCenterOf(SanctumArena.pillarBase(0).above(1))))
                .press(mc.options.keyAttack, 60)
                .check("the pillar still stands", () -> CryptKit.server(srv -> level(srv).getBlockState(SanctumArena.pillarBase(0).above(1))
                        .is(SanctumRegistry.CHOIR_PILLAR.get())))
                // the two views
                .run("the arena from the throne", () -> camera(mc, s, new Vec3(0.5, SanctumLayout.ARENA_Y + 4.5, 0.5 + s.layout.side() * 1.5),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 2.0, 0.5 - s.layout.side() * 26.0)))
                .waitUntil("the arena is drawn", 800, CryptKit.settled(mc, 600))
                .screenshot("sanctum_arena_from_the_throne")
                .run("the arena from the rim", () -> camera(mc, s, new Vec3(0.5, SanctumLayout.ARENA_Y + 3.0, 0.5 - s.layout.side() * 27.5),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 3.0, 0.5)))
                .waitUntil("the arena is drawn", 800, CryptKit.settled(mc, 600))
                .screenshot("sanctum_arena_from_the_rim")
                .run("back to the player", () -> uncamera(s));
        fall(steps, mc, s);
        heart(steps, mc, s);
    }

    private void fall(Steps steps, Minecraft mc, State s) {
        steps.command("cosmicbreach give meridian")
                .waitUntil("Meridian is carried", 60, () -> count(mc, com.cosmicbreach.registry.ModItems.MERIDIAN.get()) > 0)
                .run("select Meridian", () -> selectItem(mc, com.cosmicbreach.registry.ModItems.MERIDIAN.get()))
                .run("heal", CryptKit::heal)
                .run("on the east rim, facing the Breach", () -> CryptKit.tp(mc, new Vec3(26.5, SanctumLayout.ARENA_Y, 0.5),
                        new Vec3(60.5, SanctumLayout.ARENA_Y + 1.6, 0.5)))
                .waitTicks(20)
                .command("cosmicbreach debug resonance 100")
                .waitTicks(5)
                .run("mark", () -> {
                    s.healthBefore = CryptKit.health();
                    s.leftRim = -1;
                })
                .hold(mc.options.keyUp)
                .waitUntil("walked off the rim and thrown back", 400, () -> {
                    if (s.leftRim < 0 && !mc.player.onGround() && mc.player.getY() < SanctumLayout.ARENA_Y - 0.5) {
                        s.leftRim = mc.level.getGameTime();
                    }
                    if (s.leftRim >= 0) {
                        CryptKit.releaseAll(mc);
                    }
                    return CryptKit.server(srv -> FallRescue.rescues()) > 0;
                })
                .release(mc.options.keyUp)
                .waitTicks(3)
                .run("result", () -> {
                    float[] h = CryptKit.server(srv -> FallRescue.lastHealth());
                    Vec3 spot = CryptKit.server(srv -> FallRescue.lastSpot());
                    s.results.add(String.format(Locale.ROOT, "fall: thrown back %d ticks after leaving the rim, onto ring %d segment %d at %.1f %.1f %.1f; "
                                    + "health %.2f to %.2f (before the walk %.2f); Voidsick %d ticks; subtitle '%s'",
                            mc.level.getGameTime() - s.leftRim, SanctumLayout.ring((int) Math.floor(spot.x), (int) Math.floor(spot.z)),
                            SanctumLayout.segment((int) Math.floor(spot.x), (int) Math.floor(spot.z)), spot.x, spot.y, spot.z, h[0], h[1],
                            s.healthBefore, voidsick(mc), subtitle(mc)));
                })
                .check("thrown back onto the rim, where it fell, half the health gone, Voidsick", () -> {
                    float[] h = CryptKit.server(srv -> FallRescue.lastHealth());
                    Vec3 spot = CryptKit.server(srv -> FallRescue.lastSpot());
                    int ring = SanctumLayout.ring((int) Math.floor(spot.x), (int) Math.floor(spot.z));
                    return ring == 3 && spot.x > 20 && Math.abs(h[1] - h[0] * 0.5f) < 1e-4 && Math.abs(h[0] - s.healthBefore) < 1e-4
                            && Math.abs(CryptKit.health() - h[1]) < 0.5f && voidsick(mc) > 0 && voidsick(mc) <= 60
                            && Math.abs(mc.player.getY() - SanctumLayout.ARENA_Y) < 0.6 && subtitle(mc).contains("throws you back");
                })
                // no weapon abilities while Voidsick
                .run("mark Resonance", () -> s.resonanceBefore = machine(mc).resonance())
                .look(0, 30)
                .press(mc.options.keyUse)
                .waitTicks(6)
                .log("while Voidsick", () -> String.format(Locale.ROOT, "Resonance %.1f (was %.1f), move %s, Voidsick %d",
                        machine(mc).resonance(), s.resonanceBefore, machine(mc).current(), voidsick(mc)))
                .check("the ability is refused while Voidsick (Resonance only drifts)", () -> voidsick(mc) > 0
                        && machine(mc).resonance() > s.resonanceBefore - 5.0
                        && (machine(mc).current() == null || machine(mc).current().def().kind() != MoveKind.ABILITY))
                .waitUntil("Voidsick passes", 100, () -> voidsick(mc) == 0)
                .command("cosmicbreach debug resonance 100")
                .waitUntil("ready to cast", 100, () -> machine(mc).abilityCooldown() == 0 && machine(mc).phase() == CombatStateMachine.Phase.IDLE
                        && machine(mc).resonance() >= 99)
                .run("mark Resonance", () -> s.resonanceBefore = machine(mc).resonance())
                .press(mc.options.keyUse)
                .waitUntil("the ability answers again", 40, () -> machine(mc).resonance() < s.resonanceBefore - 5)
                .waitUntil("back on the ground", 100, () -> mc.player.onGround() && machine(mc).phase() == CombatStateMachine.Phase.IDLE);
    }

    private void heart(Steps steps, Minecraft mc, State s) {
        steps.run("heal", CryptKit::heal)
                .command("clear @s minecraft:netherite_pickaxe")
                .command("clear @s cosmicbreach:meridian")
                .command("clear @s cosmicbreach:reverie_draught")
                .run("a Forge at tier III by the dais", () -> {
                    s.forge = new BlockPos(4, SanctumLayout.ARENA_Y, 0);
                    CryptKit.server(srv -> {
                        level(srv).setBlockAndUpdate(s.forge, GearRegistry.ASTRAL_FORGE.get().defaultBlockState().setValue(AstralForgeBlock.TIER, 3));
                        return null;
                    });
                })
                .run("the Silent Sigil and four Eclipsium Ingots (and the wings' prizes if this part runs alone)", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    give(p, ModMaterials.SILENT_SIGIL.get(), 1);
                    give(p, ModMaterials.ECLIPSIUM_INGOT.get(), 4);
                    if (!p.getInventory().hasAnyOf(java.util.Set.of(ModMaterials.SOLAR_EMBER.get()))) {
                        give(p, ModMaterials.SOLAR_EMBER.get(), 1);
                    }
                    if (!p.getInventory().hasAnyOf(java.util.Set.of(ModMaterials.HYMN_CRYSTAL.get()))) {
                        give(p, ModMaterials.HYMN_CRYSTAL.get(), 1);
                    }
                    return null;
                }))
                .waitUntil("the materials are carried", 60, () -> count(mc, ModMaterials.SILENT_SIGIL.get()) >= 1
                        && count(mc, ModMaterials.ECLIPSIUM_INGOT.get()) >= 4 && count(mc, ModMaterials.SOLAR_EMBER.get()) >= 1
                        && count(mc, ModMaterials.HYMN_CRYSTAL.get()) >= 1)
                .run("before the Forge", () -> CryptKit.tp(mc, new Vec3(4.5, SanctumLayout.ARENA_Y, 3.3), Vec3.atCenterOf(s.forge)))
                .waitTicks(10)
                .run("an empty hand", () -> selectEmpty(mc))
                .waitTicks(2)
                .run("look at the Forge", () -> CryptKit.aim(mc, Vec3.atCenterOf(s.forge).add(0, 0.3, 0)))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the Forge's screen opened", 40, () -> mc.screen instanceof AstralForgeScreen)
                .waitTicks(2)
                .run("click the Dying Star Heart", () -> {
                    AstralForgeScreen screen = (AstralForgeScreen) mc.screen;
                    int i = screen.indexOf(SanctumRegistry.DYING_STAR_HEART.get());
                    if (i < 0) {
                        throw new Steps.Failure("no Forge recipe makes the Dying Star Heart");
                    }
                    click(mc, screen.cellCenter(i));
                })
                .check("it can be forged at tier III", () -> ((AstralForgeScreen) mc.screen).canCraftSelected())
                .waitTicks(2)
                .screenshot("sanctum_forge_heart")
                .run("mark the ingots", () -> s.against = count(mc, ModMaterials.ECLIPSIUM_INGOT.get()))
                .run("click Forge", () -> click(mc, ((AstralForgeScreen) mc.screen).craftButtonCenter()))
                .waitUntil("the Heart is forged", 60, () -> count(mc, SanctumRegistry.DYING_STAR_HEART.get()) == 1)
                .check("the Sigil, the Ember, the Crystal and the four ingots were used", () -> count(mc, ModMaterials.SILENT_SIGIL.get()) == 0
                        && count(mc, ModMaterials.SOLAR_EMBER.get()) == 0 && count(mc, ModMaterials.HYMN_CRYSTAL.get()) == 0
                        && count(mc, ModMaterials.ECLIPSIUM_INGOT.get()) == s.against - 4)
                .run("close the Forge", () -> mc.player.closeContainer())
                .waitUntil("the screen closed", 20, () -> mc.screen == null)
                .run("before the throne", () -> CryptKit.tp(mc, new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5 + s.layout.side() * 2.6),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 0.6, 0.5)))
                .waitTicks(10)
                .run("hold the Heart", () -> selectItem(mc, SanctumRegistry.DYING_STAR_HEART.get()))
                .waitTicks(3)
                .run("aim at the throne's seat", () -> CryptKit.aim(mc, new Vec3(0.5, SanctumLayout.ARENA_Y + 0.5, 0.5)))
                .run("mark (the Heliarch's summon set aside: this checks the throne alone)", () -> CryptKit.server(srv -> {
                    SanctumThrone.reset();
                    SanctumThrone.setSummoner(null);
                    return null;
                }))
                .press(mc.options.keyUse)
                .waitUntil("the Heart sits on the throne", 40, () -> throne(mc).getValue(SanctumThroneBlock.HEART)
                        && count(mc, SanctumRegistry.DYING_STAR_HEART.get()) == 0)
                .check("the throne took it (nothing registered to answer yet)", () -> CryptKit.server(srv -> SanctumThrone.counters()).startsWith("1 ")
                        && CryptKit.server(srv -> SanctumThrone.summoner()) == null)
                .waitUntil("the sky flickers", 60, () -> SanctumClient.flickerPeaks() > 0)
                .run("a view of the throne", () -> camera(mc, s, new Vec3(2.8, SanctumLayout.ARENA_Y + 2.2, 0.5 + s.layout.side() * 3.2),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 0.9, 0.5)))
                .waitTicks(4)
                .screenshot("sanctum_throne_heart")
                .run("back to the player", () -> uncamera(s))
                .waitUntil("nothing answers: the Heart comes back", 120, () -> count(mc, SanctumRegistry.DYING_STAR_HEART.get()) == 1
                        && !throne(mc).getValue(SanctumThroneBlock.HEART))
                .waitTicks(2)
                .log("throne", () -> "counters " + CryptKit.server(srv -> SanctumThrone.counters()) + ", subtitle '" + subtitle(mc)
                        + "', flicker peaks " + SanctumClient.flickerPeaks())
                .check("the subtitle says nothing answers yet", () -> subtitle(mc).contains("Nothing answers yet"))
                .run("the Heliarch answers the throne again", () -> CryptKit.server(srv -> {
                    com.cosmicbreach.guardian.heliarch.Heliarchs.installSummoner();
                    return null;
                }));
    }

    // ------------------------------------------------------------------ walking

    private static void route(State s, List<double[]> points) {
        s.route.clear();
        s.route.addAll(points);
        s.waypoint = 0;
        s.stuck = 0;
        s.last = null;
        s.against = 0;
    }

    /** Steers toward the next waypoint with the movement keys; true at the end of the route. */
    private static boolean follow(Minecraft mc, State s) {
        if (s.waypoint >= s.route.size()) {
            CryptKit.releaseAll(mc);
            return true;
        }
        double[] w = s.route.get(s.waypoint);
        Vec3 p = mc.player.position();
        double d = Math.hypot(w[0] - p.x, w[2] - p.z);
        if (d < 0.5 && Math.abs(w[1] - p.y) < 2.5) {
            s.waypoint++;
            return s.waypoint >= s.route.size() && stop(mc);
        }
        CryptKit.face(mc, new Vec3(w[0], w[1] + 1.2, w[2]), 12f);
        CryptKit.set(mc.options.keyUp, true);
        CryptKit.set(mc.options.keySprint, false);
        mc.player.setSprinting(false);
        if (s.last != null && s.last.distanceTo(p) < 0.02) {
            s.stuck++;
            CryptKit.set(mc.options.keyJump, s.stuck % 10 == 0);
            if (s.stuck > 400) {
                throw new Steps.Failure("stuck at " + p + " on the way to waypoint " + s.waypoint + " " + w[0] + " " + w[1] + " " + w[2]);
            }
        } else {
            CryptKit.set(mc.options.keyJump, false);
        }
        s.last = p;
        return false;
    }

    private static boolean stop(Minecraft mc) {
        CryptKit.releaseAll(mc);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static String where(Minecraft mc) {
        return String.format(Locale.ROOT, "player at %.2f %.2f %.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private static ServerLevel level(MinecraftServer srv) {
        return srv.getLevel(AetheriaWorld.LEVEL);
    }

    private static double localD(State s, double z) {
        return s.layout.side() * (z - 0.5) + 0.5;
    }

    private static boolean gateOpen(State s) {
        return CryptKit.server(srv -> level(srv).getBlockState(SanctumBuilder.at(s.layout.gateBlocks().get(0))).getValue(SanctumGateBlock.OPEN));
    }

    private static int refusals() {
        String[] c = CryptKit.server(srv -> SanctumGate.counters()).split(" ");
        return Integer.parseInt(c[1]);
    }

    private static boolean lockLit(ServerLevel level, State s, Wing wing) {
        BlockState st = level.getBlockState(SanctumBuilder.at(s.layout.lock(wing)));
        return st.is(SanctumRegistry.ECLIPSE_LOCK.get()) && st.getValue(EclipseLockBlock.LIT);
    }

    private static VaultBlockEntity vault(ServerLevel level, State s, Wing wing) {
        if (level.getBlockEntity(SanctumBuilder.at(s.layout.vault(wing))) instanceof VaultBlockEntity v) {
            return v;
        }
        throw new Steps.Failure("no vault at " + wing);
    }

    private static BlockState throne(Minecraft mc) {
        return mc.level.getBlockState(SanctumArena.THRONE);
    }

    private static int voidsick(Minecraft mc) {
        var e = mc.player.getEffect(SanctumRegistry.VOIDSICK);
        return e == null ? 0 : e.getDuration();
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static void fly(Minecraft mc) {
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
    }

    private static void camera(Minecraft mc, State s, Vec3 eye, Vec3 target) {
        uncamera(s);
        // the player's input only updates while it is the camera: stop it first, or it walks on unseen
        CryptKit.releaseAll(mc);
        mc.player.input.forwardImpulse = 0f;
        mc.player.input.leftImpulse = 0f;
        mc.player.input.jumping = false;
        mc.player.xxa = 0f;
        mc.player.zza = 0f;
        mc.player.setJumping(false);
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

    private static String subtitle(Minecraft mc) {
        try {
            Field field = Gui.class.getDeclaredField("subtitle");
            field.setAccessible(true);
            Component c = (Component) field.get(mc.gui);
            return c == null ? "" : c.getString();
        } catch (ReflectiveOperationException e) {
            return "(unreadable: " + e + ")";
        }
    }

    private static int count(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static String inventory(Minecraft mc) {
        List<String> out = new ArrayList<>();
        for (ItemStack st : mc.player.getInventory().items) {
            if (!st.isEmpty()) {
                out.add(st.getCount() + " " + BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
            }
        }
        return String.join(", ", out);
    }

    private static void give(ServerPlayer p, Item item, int n) {
        ItemStack stack = new ItemStack(item, n);
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    /** Selects the hotbar slot holding {@code item} with its number key (moving it into the hotbar first if needed). */
    private static void selectItem(Minecraft mc, Item item) {
        var inv = mc.player.getInventory();
        for (int k = 0; k < 9; k++) {
            if (inv.items.get(k).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
                return;
            }
        }
        for (int i = 9; i < inv.items.size(); i++) {
            if (inv.items.get(i).is(item)) {
                int slot = i;
                CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    ItemStack a = p.getInventory().items.get(slot);
                    p.getInventory().items.set(slot, p.getInventory().items.get(8));
                    p.getInventory().items.set(8, a);
                    p.inventoryMenu.broadcastChanges();
                    return null;
                });
                KeyMapping.click(mc.options.keyHotbarSlots[8].getKey());
                return;
            }
        }
        throw new Steps.Failure("no " + BuiltInRegistries.ITEM.getKey(item) + " carried");
    }

    private static void selectEmpty(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int k = 0; k < 9; k++) {
            if (inv.items.get(k).isEmpty()) {
                KeyMapping.click(mc.options.keyHotbarSlots[k].getKey());
                return;
            }
        }
    }

    private static void click(Minecraft mc, double[] at) {
        mc.screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mc.screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
}
