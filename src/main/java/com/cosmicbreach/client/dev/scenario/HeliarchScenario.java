package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.Heliarchs;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.structure.sanctum.SanctumCommands;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.Sanctums;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Heliarch (G9a, GDD 7.3) in the hidden test client, on the Breach Sanctum built at its place in Aetheria,
 * with a level-36 player in the Choir Regalia and Meridian reforged to T3.
 *
 * <ul>
 *   <li>{@code heliarch-looks}: the fight from the arena's rim, the dais and above, in every phase: the intro, each
 *       telegraph, the Break, the Hollowing, the monoliths, the eclipse core and its tendrils, the beam, Nova, the
 *       Collapse, the death, the Reliquaries and the seal over the Breach.</li>
 *   <li>{@code heliarch-regent}, {@code heliarch-hollow}, {@code heliarch-end}: the mechanics with real inputs
 *       ({@link HeliarchMechanics}); {@code heliarch} runs all three.</li>
 *   <li>{@code heliarch-fight}: a whole fight played by script ({@link HeliarchFightBot}), timed.</li>
 * </ul>
 */
public final class HeliarchScenario implements Scenario {
    public enum Part { LOOKS, REGENT, HOLLOW, END, ALL, FIGHT }

    private final Part part;
    private final List<String> results = new ArrayList<>();
    private @Nullable DevCamera camera;

    public HeliarchScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return switch (part) {
            case LOOKS -> 600;
            case FIGHT -> 1100;
            case ALL -> 1000;
            default -> 520;
        };
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        setup(steps, mc);
        switch (part) {
            case LOOKS -> new HeliarchLooks(this).steps(steps, mc);
            case FIGHT -> new HeliarchFightBot(this).steps(steps, mc);
            case REGENT -> new HeliarchMechanics(this).regent(steps, mc);
            case HOLLOW -> new HeliarchMechanics(this).hollow(steps, mc);
            case END -> new HeliarchMechanics(this).end(steps, mc);
            case ALL -> {
                HeliarchMechanics m = new HeliarchMechanics(this);
                m.regent(steps, mc);
                m.hollow(steps, mc);
                m.end(steps, mc);
            }
        }
        steps.run("hand back the view", this::uncamera)
                .log("results", () -> String.join(System.lineSeparator(), results));
    }

    /** Noon, clear, Normal; into Aetheria; the Sanctum built at its place; the player on the arena's rim. */
    private void setup(Steps steps, Minecraft mc) {
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule keepInventory true")
                .command("time set 6000")
                .command("weather clear")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 120 0.5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .run("build the Breach Sanctum at its place", () -> {
                    long ms = CryptKit.server(srv -> SanctumCommands.build(level(srv)));
                    int side = CryptKit.server(srv -> Sanctums.layout(level(srv)).side());
                    results.add(String.format(Locale.ROOT, "Sanctum built in %d ms, halls to the %s", ms, side < 0 ? "north" : "south"));
                })
                .command("cosmicbreach weather clear")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run spawnpoint @s 0 " + SanctumLayout.ARENA_Y + " 20")
                .command("cosmicbreach debug goto sanctum arena")
                .waitUntil("on the arena", 600, () -> Math.abs(mc.player.getY() - SanctumLayout.ARENA_Y) < 1.5
                        && HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ()) < 30)
                .run("a longer view", () -> mc.options.renderDistance().set(10))
                .waitUntil("the arena is drawn", 900, CryptKit.settled(mc, 700));
    }

    // ------------------------------------------------------------------ shared helpers

    List<String> results() {
        return results;
    }

    static ServerLevel level(MinecraftServer srv) {
        return srv.getLevel(AetheriaWorld.LEVEL);
    }

    /** The Heliarch fighting now, on the server, or null. */
    static @Nullable HollowHeliarch heliarch(MinecraftServer srv) {
        ServerLevel level = level(srv);
        return level == null ? null : Heliarchs.active(level);
    }

    /** {@code query} of the Heliarch on the server thread; fails if there is none. */
    static <T> T ask(Function<HollowHeliarch, T> query) {
        return CryptKit.server(srv -> {
            HollowHeliarch h = heliarch(srv);
            if (h == null) {
                throw new Steps.Failure("there is no Heliarch fight");
            }
            return query.apply(h);
        });
    }

    /** True if a Heliarch fights now. */
    static boolean fighting() {
        return CryptKit.server(srv -> heliarch(srv) != null);
    }

    static long serverTick(Minecraft mc) {
        MinecraftServer srv = mc.getSingleplayerServer();
        ServerLevel level = srv == null ? null : level(srv);
        return level != null ? level.getGameTime() : mc.level.getGameTime();
    }

    void camera(Minecraft mc, Vec3 eye, Vec3 target) {
        uncamera();
        CryptKit.releaseAll(mc);
        mc.player.input.forwardImpulse = 0f;
        mc.player.input.leftImpulse = 0f;
        mc.player.input.jumping = false;
        mc.player.xxa = 0f;
        mc.player.zza = 0f;
        camera = DevCamera.create(mc.level);
        camera.place(eye, target);
        camera.use();
        mc.options.hideGui = true;
    }

    void uncamera() {
        Minecraft.getInstance().options.hideGui = false;
        if (camera != null) {
            camera.remove();
            camera = null;
        }
    }

    /** A point of the arena: {@code radius} from the throne toward a compass angle, {@code up} over the floor. */
    static Vec3 at(double compass, double radius, double up) {
        return HeliarchArena.at(compass, radius).add(0, up, 0);
    }

    /** Where the halls lie (-1 north, +1 south). */
    static int side() {
        return CryptKit.server(srv -> Sanctums.layout(level(srv)).side());
    }
}
