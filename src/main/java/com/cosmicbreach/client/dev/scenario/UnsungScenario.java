package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.unsung.ChoirArena;
import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungCommands;
import com.cosmicbreach.guardian.unsung.UnsungMask;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.Voice;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Unsung and the Silent Nave (task G8), in parts:
 * <ul>
 *   <li>{@code unsung-looks}: the Nave from outside and in, the sleeping masks in the lichen light, the awakening, each
 *       mask from the front, three quarters and the side (singing and humming), every telegraph (a note forming and
 *       flying, the Tenor's inhale and the wave, the ripples' dark rings, the Bass Drop's gold ring and glint), the
 *       Harmonize circles, a Break, a broken mask.</li>
 *   <li>{@code unsung}: the mechanics with real inputs ({@link UnsungMechanics}).</li>
 *   <li>{@code unsung-fight}: a whole fight played by a bot ({@link UnsungFightBot}), timed.</li>
 * </ul>
 */
public final class UnsungScenario implements Scenario {
    public enum Part { LOOKS, MECHANICS, FIGHT, WORLD }

    private final Part part;
    private @Nullable DevCamera camera;
    final List<String> summary = new ArrayList<>();

    public UnsungScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return part == Part.FIGHT ? 900 : 600;
    }

    /** The world part needs the Silent Nave to generate as the world is made. */
    @Override
    public boolean generateStructures() {
        return part == Part.WORLD;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("difficulty normal");
        if (part == Part.WORLD) {
            world(steps, mc);
            steps.run("back to the player's eyes", this::dropCamera)
                    .run("the HUD on", () -> mc.options.hideGui = false)
                    .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
            return;
        }
        buildLair(steps, mc);
        switch (part) {
            case LOOKS -> looks(steps, mc);
            case MECHANICS -> new UnsungMechanics(this, summary).steps(steps, mc);
            case FIGHT -> new UnsungFightBot(this, summary).steps(steps, mc);
            case WORLD -> {
            }
        }
        steps.run("back to the player's eyes", this::dropCamera)
                .run("the HUD on", () -> mc.options.hideGui = false)
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the lair

    private void buildLair(Steps steps, Minecraft mc) {
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamemode creative")
                .command("cosmicbreach debug goto deep")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the Deep is drawn", 1600, settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair unsung")
                .waitUntil("the Nave is built and its choir sleeps", 200, () -> layout() != null && ServerQuery.ask(p -> choirOrNull(p) != null))
                .waitUntil("its three masks rest on the dais", 100, () -> ServerQuery.ask(p -> choirOrNull(p) != null && choirOrNull(p).masks().size() == 3))
                .log("lair", () -> {
                    NaveLayout l = layout();
                    return String.format(Locale.ROOT, "Silent Nave: apse centre %d %d %d, facing %d, altar %s", l.cx(), l.floorY(), l.cz(),
                            l.facing(), java.util.Arrays.toString(l.altar()));
                });
    }

    // ------------------------------------------------------------------ a Nave the world made

    private @Nullable ChoirArena natural;
    /** The world's Nave's layout, its facing read from the blocks it built. */
    private @Nullable NaveLayout naturalLayout;

    /** A point in a layout's local frame: {@code u} along the nave toward its door, {@code h} over its floor, {@code v} across. */
    static Vec3 inLayout(NaveLayout l, double u, double h, double v) {
        int[] a = l.axis();
        return new Vec3(l.cx() + u * a[0] - v * a[1], l.floorY() + h, l.cz() + u * a[1] + v * a[0]);
    }

    /** A Silent Nave the world generated: found by /locate and the lair API, visited, its hymnal's choir asleep. */
    private void world(Steps steps, Minecraft mc) {
        steps.command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamemode creative")
                .command("cosmicbreach debug goto deep")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the Deep is drawn", 1600, settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("locate structure cosmicbreach:silent_nave")
                .waitForChat("The nearest cosmicbreach:silent_nave is at", 1200)
                .command("cosmicbreach debug locate")
                .waitForChat("Nearest unsung lair: -?\\d+, \\d+, -?\\d+", 1200)
                .run("the locate API", () -> {
                    net.minecraft.core.BlockPos found = ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianLairs.nearest(p.serverLevel(),
                            p.blockPosition(), com.cosmicbreach.guardian.GuardianTypes.UNSUNG).orElse(null));
                    if (found == null) {
                        throw new Steps.Failure("GuardianLairs.nearest found no Silent Nave");
                    }
                    natural = ChoirArena.at(found);
                    summary.add(String.format(Locale.ROOT, "the lair API: a Silent Nave %.0f blocks away, its choir floor at Y %d",
                            Math.hypot(found.getX() - mc.player.getX(), found.getZ() - mc.player.getZ()), found.getY() - 1));
                })
                .run("fly to it", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                    tp(mc, natural.x() + 0.5, natural.floorY() + 2, natural.z() + 0.5, natural.x() + 8, natural.floorY() + 2, natural.z());
                })
                .waitTicks(60)
                .waitUntil("the Nave is drawn", 1600, settled(mc, 1400))
                .waitUntil("the world built its choir floor", 600, () -> ServerQuery.ask(p -> {
                    var s = p.level().getBlockState(natural.centreBlock());
                    return s.is(com.cosmicbreach.registry.ModBlocks.POLISHED_UMBRAL_BASALT.get())
                            || s.is(com.cosmicbreach.registry.ModBlocks.UMBRAL_BASALT_BRICKS.get());
                }))
                .waitUntil("its hymnal raised the sleeping choir", 600, () -> ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(Unsung.class,
                        new net.minecraft.world.phys.AABB(natural.centreBlock()).inflate(8.0)).stream().anyMatch(Unsung::dormant)))
                .check("and registered the lair", () -> ServerQuery.ask(p -> com.cosmicbreach.guardian.GuardianLairs.get(p.serverLevel())
                        .nearestKnown(com.cosmicbreach.guardian.GuardianTypes.UNSUNG, p.blockPosition()).map(b -> b.equals(natural.centreBlock()))
                        .orElse(false)))
                .check("circles of silence in the floor", () -> ServerQuery.ask(p -> {
                    net.minecraft.world.phys.Vec3 c = natural.circleCentre(0);
                    return p.level().getBlockState(net.minecraft.core.BlockPos.containing(c.x, natural.floorY() - 1, c.z))
                            .is(com.cosmicbreach.guardian.unsung.UnsungRegistry.SILENCE_CIRCLE.get());
                }))
                .run("its axis, from its ridge", () -> {
                    int facing = ServerQuery.ask(p -> {
                        for (int f = 0; f < 4; f++) {
                            NaveLayout l = new NaveLayout(natural.x(), natural.z(), natural.floorY(), f, 0L);
                            int ridge = 0;
                            for (double u = 24.5; u <= 40.5; u += 4.0) {
                                Vec3 at = inLayout(l, u, NaveLayout.GABLE_TOP + 0.5, 0.5);
                                ridge += p.level().getBlockState(net.minecraft.core.BlockPos.containing(at))
                                        .is(com.cosmicbreach.registry.ModBlocks.POLISHED_UMBRAL_BASALT.get()) ? 1 : 0;
                            }
                            if (ridge >= 3) {
                                return f;
                            }
                        }
                        return -1;
                    });
                    if (facing < 0) {
                        throw new Steps.Failure("no gabled nave with its ridge runs from this apse");
                    }
                    naturalLayout = new NaveLayout(natural.x(), natural.z(), natural.floorY(), facing, 0L);
                    summary.add("the Nave the world made runs " + net.minecraft.core.Direction.from2DDataValue((facing + 3) % 4)
                            + " from its apse, under a gabled roof");
                })
                .run("the HUD hidden", () -> mc.options.hideGui = true)
                .run("camera: the Nave the world made, its west front and flank", () -> place(mc,
                        inLayout(naturalLayout, NaveLayout.LANDING + 22.0, 20.0, 38.0), inLayout(naturalLayout, 26.0, 9.0, 0.0)))
                .waitTicks(40)
                .waitUntil("drawn", 400, settled(mc, 300))
                .screenshot("world_nave")
                .run("camera: its flank, the flying buttresses and the dome", () -> place(mc,
                        inLayout(naturalLayout, 20.0, 16.0, -46.0), inLayout(naturalLayout, 18.0, 8.0, 0.0)))
                .waitTicks(20)
                .waitUntil("drawn", 400, settled(mc, 300))
                .screenshot("world_flank")
                .run("camera: its apse", () -> place(mc, natural.centre().add(8, 5, 8), natural.centre().add(0, 1, 0)))
                .waitTicks(10)
                .screenshot("world_apse")
                .run("note it", () -> summary.add("the world generated a Silent Nave in the Rift Abyss: its floor, circles and hymnal; the choir sleeps; the lair registered"));
    }

    // ------------------------------------------------------------------ looks

    private void looks(Steps steps, Minecraft mc) {
        steps.run("the HUD hidden", () -> mc.options.hideGui = true)
                .run("camera: the Nave from outside its door", () -> place(mc, fromLocal(NaveLayout.LANDING + 24.0, 12.0, 22.0),
                        fromLocal(NaveLayout.NAVE_END - 6.0, 12.0, 0.0)))
                .waitUntil("drawn", 400, settled(mc, 300))
                .screenshot("nave_outside")
                .run("camera: the nave, from its door toward the apse", () -> place(mc, fromLocal(NaveLayout.NAVE_END - 1.0, 1.0, 3.0),
                        fromLocal(0.0, 0.0, 3.0)))
                .waitTicks(10)
                .screenshot("nave_inside")
                .run("camera: the hymnal", () -> {
                    int[] a = layout().altar();
                    Vec3 altar = new Vec3(a[0] + 0.5, a[1] + 1.0, a[2] + 0.5);
                    place(mc, altar.add(axis().scale(2.4)).add(0, 0.9, 0), altar);
                })
                .waitTicks(5)
                .screenshot("hymnal")
                .run("camera: the apse asleep, in its lichen light", () -> place(mc, fromLocal(14.5, 4.0, 0.0), arena().centre().add(0, 1.2, 0)))
                .waitTicks(5)
                .screenshot("apse_asleep")
                .run("camera: a sleeping mask close", () -> {
                    Vec3 f = maskFace(Voice.ALTO);
                    place(mc, f.add(outward(f).scale(2.6)).add(0, 1.6, 0), f);
                })
                .waitTicks(3)
                .screenshot("mask_asleep");
        // the awakening: a player not attuned to the Sanctum steps onto the choir floor
        steps.run("back to the player's eyes", this::dropCamera)
                .command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 4 true")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .run("onto the choir floor", () -> {
                    Vec3 at = fromLocal(10.0, 0.0, 0.0);
                    tp(mc, at.x, arena().floorY(), at.z, arena().x(), arena().floorY() + 3, arena().z());
                })
                .waitUntil("it wakes", 60, () -> ask(u -> u.state() == Unsung.State.INTRO))
                .waitUntil("the Alto lifts", 300, () -> ask(u -> u.masks().get(Voice.ALTO).mode() != UnsungMask.Mode.REST))
                .waitTicks(20)
                .run("camera: the awakening", () -> place(mc, fromLocal(12.0, 3.0, 6.0), arena().centre().add(0, 3.0, 0)))
                .waitTicks(2)
                .screenshot("awakening")
                .waitUntil("the fight", 300, () -> ask(u -> u.state() == Unsung.State.FIGHT))
                .command("cosmicbreach debug unsung hold 100000")
                .waitTicks(30);
        for (Voice v : Voice.values()) {
            for (String angle : new String[] {"front", "34", "side"}) {
                steps.run("camera: the " + v.id() + " from the " + angle, () -> {
                            Vec3 f = maskFace(v);
                            Vec3 look = maskLook(v);
                            double turn = angle.equals("front") ? 0.0 : angle.equals("34") ? 45.0 : 90.0;
                            Vec3 dir = rotate(look, turn);
                            place(mc, f.add(dir.scale(3.6)).add(0, 0.4, 0), f);
                        })
                        .waitTicks(1)
                        .screenshot("mask_" + v.id() + "_" + angle);
            }
        }
        steps.run("camera: the three masks and the singer", () -> place(mc, fromLocal(13.5, 2.0, 5.0), arena().centre().add(0, 3.5, 0)))
                .waitTicks(2)
                .screenshot("masks_three");
        // the telegraphs
        telegraph(steps, mc, Voice.ALTO, "note",
                u -> u.level().getGameTime() - u.fightStart() >= 0 && !notes(u).isEmpty() && notes(u).get(0).level().getGameTime() - notes(u).get(0).formed() == 8,
                "note_forming", u -> !notes(u).isEmpty() && notes(u).get(0).level().getGameTime() - notes(u).get(0).formed() == 22, "note_flying");
        telegraph(steps, mc, Voice.TENOR, "wave", u -> u.waveRelease() - u.level().getGameTime() == 5, "wave_inhale",
                u -> u.level().getGameTime() - u.waveRelease() == 4, "wave_running");
        steps.command("cosmicbreach debug unsung singer bass")
                .command("cosmicbreach debug unsung hold 0")
                .waitUntil("the bass sings", 300, () -> ask(u -> u.singer() == Voice.BASS))
                .waitUntil("the floor darkens under the Bass", 200, () -> ask(u -> u.rippleLand() - u.level().getGameTime() == 4))
                .run("camera: the ripple's dark rings, from above", () -> {
                    Vec3 c = get(Unsung::rippleCentre);
                    place(mc, c.add(outward(c).scale(6.0)).add(0, 7.0, 0), c);
                })
                .waitTicks(1)
                .screenshot("ripple_dark")
                .waitUntil("the Bass Drop's ring half full", 200, () -> ask(u -> u.dropLand() - u.level().getGameTime() == 12))
                .run("camera: drop_ring", () -> place(mc, fromLocal(12.5, 6.0, 8.0), mc.player.position().add(0, 1.0, 0)))
                .waitTicks(1)
                .screenshot("drop_ring");
        steps.waitUntil("the gold glint", 200, () -> ask(u -> u.dropLand() - u.level().getGameTime() == 5))
                .run("camera: the glint over the target", () -> place(mc, mc.player.position().add(4.5, 4.0, 4.5), mc.player.position().add(0, 3.5, 0)))
                .waitTicks(1)
                .screenshot("drop_glint")
                .command("cosmicbreach debug unsung hold 100000");
        // Harmonize's circles
        steps.command("cosmicbreach debug unsung harmonize")
                .command("cosmicbreach debug unsung hold 0")
                .waitUntil("the warning", 300, () -> ask(Unsung::warning))
                .waitTicks(UnsungMoves.BEAT * 4)
                .run("camera: the lit circles from high over the apse", () -> place(mc, arena().centre().add(0.0, 13.0, -0.5)
                        .add(axis().scale(5.0)), arena().centre()))
                .waitTicks(2)
                .screenshot("harmonize_circles")
                .run("camera: the masks rising", () -> place(mc, fromLocal(12.0, 4.0, 3.0), arena().centre().add(0, 5.0, 0)))
                .waitTicks(2)
                .screenshot("harmonize_rise")
                .waitUntil("the downbeat", 200, () -> ask(u -> !u.warning()))
                .waitTicks(2)
                .screenshot("harmonize_downbeat");
        // a Break, and a mask breaking for good
        steps.command("cosmicbreach debug unsung hold 100000")
                .command("cosmicbreach debug unsung break")
                .waitTicks(14)
                .run("camera: the Break", () -> place(mc, fromLocal(11.0, 3.0, 4.0), arena().centre().add(0, 0.8, 0)))
                .waitTicks(2)
                .screenshot("break")
                .command("cosmicbreach debug unsung shatter tenor")
                .waitUntil("a mask broke", 40, () -> ask(u -> u.brokenBits() != 0))
                .waitTicks(20)
                .run("camera: the broken mask", () -> {
                    Vec3 f = ServerQuery.ask(p -> {
                        for (UnsungMask m : choirOf(p).masks().values()) {
                            if (m.shattered()) {
                                return m.face();
                            }
                        }
                        return arena().centre();
                    });
                    place(mc, f.add(outward(f).scale(3.0)).add(0, 2.2, 0), f);
                })
                .waitTicks(2)
                .screenshot("mask_broken")
                .run("note it", () -> summary.add("looks: the Nave, the masks asleep and awake from three angles, every telegraph, Harmonize, a Break"));
    }

    /** Makes {@code singer} sing next and shoots two moments of its telegraphs (server-tick exact). */
    private void telegraph(Steps steps, Minecraft mc, Voice singer, String what, Function<Unsung, Boolean> first, String shot1,
                           Function<Unsung, Boolean> second, String shot2) {
        steps.command("cosmicbreach debug unsung singer " + singer.id())
                .command("cosmicbreach debug unsung hold 0")
                .waitUntil("the " + singer.id() + " sings", 300, () -> ask(u -> u.singer() == singer))
                .waitUntil("the " + what + " (first)", 200, () -> ask(first))
                .run("camera: " + shot1, () -> place(mc, fromLocal(11.5, 5.0, 7.0), mc.player.position().add(0, 1.0, 0)))
                .waitTicks(1)
                .screenshot(shot1)
                .waitUntil("the " + what + " (second)", 200, () -> ask(second))
                .run("camera: " + shot2, () -> place(mc, fromLocal(12.5, 6.0, 8.0), mc.player.position().add(0, 1.0, 0)))
                .waitTicks(1)
                .screenshot(shot2);
    }

    // ------------------------------------------------------------------ helpers

    static @Nullable NaveLayout layout() {
        return UnsungCommands.lastBuilt();
    }

    static ChoirArena arena() {
        NaveLayout l = layout();
        if (l == null) {
            throw new Steps.Failure("no Silent Nave was built");
        }
        return l.arena();
    }

    /** The unit vector from the apse toward the Nave's door. */
    static Vec3 axis() {
        int[] a = layout().axis();
        return new Vec3(a[0], 0, a[1]);
    }

    /** A point at local {@code (u, v)} of the Nave, {@code h} over the choir floor. */
    static Vec3 fromLocal(double u, double h, double v) {
        NaveLayout l = layout();
        int[] a = l.axis();
        double x = l.cx() + u * a[0] - v * a[1];
        double z = l.cz() + u * a[1] + v * a[0];
        return new Vec3(x, l.floorY() + h, z);
    }

    static @Nullable Unsung choirOrNull(ServerPlayer p) {
        NaveLayout l = layout();
        if (l == null) {
            return null;
        }
        ChoirArena a = l.arena();
        return p.serverLevel().getEntitiesOfClass(Unsung.class, new net.minecraft.world.phys.AABB(a.centreBlock()).inflate(8.0)).stream()
                .findFirst().orElse(null);
    }

    static Unsung choirOf(ServerPlayer p) {
        Unsung u = choirOrNull(p);
        if (u == null) {
            throw new Steps.Failure("no Unsung at the Nave");
        }
        return u;
    }

    static boolean ask(Function<Unsung, Boolean> query) {
        return ServerQuery.ask(p -> query.apply(choirOf(p)));
    }

    static <T> T get(Function<Unsung, T> query) {
        return ServerQuery.ask(p -> query.apply(choirOf(p)));
    }

    static List<com.cosmicbreach.guardian.unsung.SongNote> notes(Unsung u) {
        return u.level().getEntitiesOfClass(com.cosmicbreach.guardian.unsung.SongNote.class, u.getBoundingBox().inflate(24.0));
    }

    static Vec3 maskFace(Voice v) {
        return ServerQuery.ask(p -> choirOf(p).masks().get(v).face());
    }

    /** Where the mask looks, flat. */
    static Vec3 maskLook(Voice v) {
        return ServerQuery.ask(p -> com.cosmicbreach.guardian.Telegraphs.forward(choirOf(p).masks().get(v).getYRot()));
    }

    static Vec3 outward(Vec3 p) {
        Vec3 d = new Vec3(p.x - arena().x(), 0, p.z - arena().z());
        return d.lengthSqr() < 1e-6 ? axis() : d.normalize();
    }

    static Vec3 rotate(Vec3 flat, double degrees) {
        double r = Math.toRadians(degrees);
        return new Vec3(flat.x * Math.cos(r) - flat.z * Math.sin(r), 0, flat.x * Math.sin(r) + flat.z * Math.cos(r));
    }

    /** The integrated server's tick, read without waiting on the server thread. */
    static long serverTick(Minecraft mc) {
        MinecraftServer srv = mc.getSingleplayerServer();
        ServerLevel level = srv == null || mc.level == null ? null : srv.getLevel(mc.level.dimension());
        return level != null ? level.getGameTime() : mc.level.getGameTime();
    }

    void place(Minecraft mc, Vec3 eye, Vec3 target) {
        if (camera == null) {
            camera = DevCamera.create(mc.level);
        }
        camera.place(eye, target);
        camera.use();
        mc.options.hideGui = true;
    }

    void dropCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
    }

    static void tp(Minecraft mc, double x, double y, double z, double lookX, double lookY, double lookZ) {
        ColossusScenario.tp(mc, x, y, z, lookX, lookY, lookZ);
    }

    static void lookAt(Minecraft mc, Vec3 target) {
        ColossusScenario.lookAt(mc, target);
    }

    private static final int SETTLE_TICKS = 20;

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
}
