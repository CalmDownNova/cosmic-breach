package com.cosmicbreach.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Autotest runner for the hidden test client (dev only, client only).
 *
 * <p>{@code ./gradlew runTestClient -Pautotest=<scenario>} starts the game with
 * {@code -Dcosmicbreach.autotest=<scenario>}. From the title screen this creates a fresh
 * superflat creative world with cheats (noon, clear weather, no natural mob spawning, no
 * structures, difficulty normal), waits for the player to spawn, runs the scenario's
 * {@link Steps} one client tick at a time and quits with {@link Minecraft#stop()}. Toasts are
 * suppressed so screenshots only show the game. Everything goes to {@code run-test/autotest/<scenario>/},
 * which is emptied at startup: {@code result.txt} ({@code PASS} or {@code FAIL <reason>}),
 * {@code log.txt} and one PNG per screenshot step.
 *
 * <p>Join mode ({@code -Pjoin=<host:port>}, {@link #JOIN_PROPERTY}): instead of making a world it joins that server
 * from the title screen, runs only {@link Scenario#multiplayer} scenarios (they work through commands, keys and what
 * the client sees), and fails at once if the server disconnects it, with the reason.
 *
 * <p>Any exception, a failed check, a blank screenshot, the window ever becoming visible, or
 * going over the time budget ends the run with FAIL. A watchdog thread also covers a hung
 * client thread: it writes FAIL and, if the game still has not exited a minute after the
 * result, rewrites the result as a FAIL ({@link ExitStall}) and halts the JVM, so a hung stop never reads as a PASS.
 * Screenshots come from the main render target, so they work while the window is hidden.
 */
public final class AutoTest implements StepContext {
    public static final String PROPERTY = "cosmicbreach.autotest";
    /** Join mode: the server to join ({@code host:port}) instead of making a world. */
    public static final String JOIN_PROPERTY = "cosmicbreach.autotest.join";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Game start to first scenario step: resource loading, title screen, world creation. */
    private static final int STARTUP_BUDGET_SECONDS = 300;
    /** How long the watchdog lets a hung client thread run over its budget before stepping in. */
    private static final int WATCHDOG_GRACE_SECONDS = 15;
    /**
     * How long the game may take to exit after the result is written. The environment variable COSMICBREACH_AUTOTEST_EXIT_GRACE
     * (seconds) overrides it, to see whether a stop that hangs ever drains (give scripts/autotest.sh a longer limit too).
     */
    private static final int EXIT_GRACE_SECONDS = envSeconds("COSMICBREACH_AUTOTEST_EXIT_GRACE", 60);
    /** Ticks on some other screen after loading before creating the world anyway. */
    private static final int TITLE_SCREEN_PATIENCE_TICKS = 100;
    private static final long WORLD_SEED = 20260927L;
    private static final Pattern SCENARIO_NAME = Pattern.compile("[a-z0-9_-]+");

    private static int envSeconds(String name, int fallback) {
        try {
            int seconds = Integer.parseInt(System.getenv().getOrDefault(name, "").trim());
            return seconds > 0 ? seconds : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private enum Phase { WAITING_FOR_TITLE, CREATING_WORLD, WAITING_FOR_PLAYER, RUNNING, FINISHED }

    private record ShotRequest(String label, CompletableFuture<Void> captured) {}

    private final String name;
    private final Path outDir;
    /** The server to join, or null to make a single-player world. */
    @Nullable
    private final String join = blankToNull(System.getProperty(JOIN_PROPERTY));
    @Nullable
    private final Scenario scenario;
    @Nullable
    private volatile String setupProblem;
    private final long installedAt = System.nanoTime();
    private final AtomicBoolean resultWritten = new AtomicBoolean();
    private final List<String> chat = new ArrayList<>();
    private final List<CompletableFuture<Void>> pendingWrites = new ArrayList<>();
    private volatile Phase phase = Phase.WAITING_FOR_TITLE;
    private volatile long scenarioStartedAt;
    private volatile long resultWrittenAt;
    private volatile String where = "startup";
    private int ticksOffTitle;
    @Nullable
    private ScenarioRunner runner;
    @Nullable
    private ShotRequest pendingShot;

    private AutoTest(String requested) {
        boolean validName = SCENARIO_NAME.matcher(requested).matches();
        this.name = requested;
        this.outDir = FMLPaths.GAMEDIR.get().resolve("autotest").resolve(validName ? requested : "_invalid_name");
        if (!validName) {
            this.scenario = null;
            this.setupProblem = "scenario names use a-z, 0-9, _ and -, got '" + requested + "'";
        } else {
            this.scenario = Scenarios.create(requested).orElse(null);
            if (scenario == null) {
                this.setupProblem = "unknown scenario '" + requested + "' (known: " + String.join(", ", Scenarios.names()) + ")";
            } else if (join != null && !scenario.multiplayer()) {
                this.setupProblem = "scenario '" + requested + "' reaches into the built-in server, so it can't join " + join;
            } else {
                this.setupProblem = null;
            }
        }
    }

    /** Called from the client mod constructor. Does nothing unless {@code -Dcosmicbreach.autotest} is set. */
    public static void installIfRequested() {
        String requested = System.getProperty(PROPERTY);
        if (requested != null && !requested.isBlank()) {
            new AutoTest(requested.trim()).install();
        }
    }

    private void install() {
        // Safety nets first, so even a failure below cannot leave a hidden game running forever.
        Thread watchdog = new Thread(this::watchdog, "cosmicbreach-autotest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::onJvmExit, "cosmicbreach-autotest-exit"));
        clearOutputFolder();
        log("autotest '" + name + "' installed, hidden window " + (HiddenWindow.enabled() ? "on" : "OFF") + ", output in " + outDir
                + (join != null ? ", joining " + join : ""));
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, ClientTickEvent.Pre.class, event -> onClientTick());
        bus.addListener(EventPriority.LOWEST, RenderFrameEvent.Post.class, event -> onFrameRendered());
        bus.addListener(EventPriority.LOWEST, true, ClientChatReceivedEvent.class, this::onChat);
        bus.addListener(ScreenEvent.Opening.class, event -> log("screen " + screenName(event.getNewScreen())));
        // Toasts ("New Recipes Unlocked!" after a give) only add timing-dependent noise to screenshots.
        bus.addListener(ToastAddEvent.class, event -> event.setCanceled(true));
    }

    // ---- phases, driven from the start of every client tick ----

    private void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (join != null && phase == Phase.RUNNING && (mc.level == null || mc.screen instanceof DisconnectedScreen)) {
                finish("FAIL disconnected from " + join + " at " + (runner != null ? runner.describeCurrent() : where) + ": "
                        + (mc.screen != null ? screenText(mc.screen) : "no world"), null);
                return;
            }
            switch (phase) {
                case WAITING_FOR_TITLE -> waitForTitle(mc);
                case WAITING_FOR_PLAYER -> waitForPlayer(mc);
                case RUNNING -> runScenarioTick();
                case CREATING_WORLD, FINISHED -> {}
            }
        } catch (Throwable t) {
            // The runner does not advance past a failing step, so this names the step that failed.
            String at = phase == Phase.RUNNING && runner != null ? runner.describeCurrent() : where;
            finish("FAIL " + at + ": " + describe(t), t);
        }
    }

    private void waitForTitle(Minecraft mc) {
        if (setupProblem != null) {
            finish("FAIL " + setupProblem, null);
            return;
        }
        if (mc.getOverlay() != null) {
            return; // resources still loading
        }
        if (!(mc.screen instanceof TitleScreen) && ++ticksOffTitle < TITLE_SCREEN_PATIENCE_TICKS) {
            return;
        }
        if (join != null) {
            phase = Phase.WAITING_FOR_PLAYER;
            where = "joining " + join;
            log("joining " + join + " (screen " + screenName(mc.screen) + ")");
            mc.tell(() -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(join),
                    new ServerData("autotest", join, ServerData.Type.OTHER), false, null));
            return;
        }
        if (!(mc.screen instanceof TitleScreen)) {
            log("no title screen after loading (screen " + screenName(mc.screen) + "), creating the world anyway");
        }
        phase = Phase.CREATING_WORLD;
        where = "world creation";
        // Queued so it runs between ticks, the way the Create World button does.
        mc.tell(() -> createWorld(mc));
    }

    private void createWorld(Minecraft mc) {
        try {
            String folder = "autotest-" + name;
            LevelStorageSource saves = mc.getLevelSource();
            if (saves.levelExists(folder)) {
                try (LevelStorageSource.LevelStorageAccess old = saves.createAccess(folder)) {
                    old.deleteLevel();
                }
            }
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
            rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
            LevelSettings settings = new LevelSettings(
                    folder, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
            // No structures unless the scenario asks: the flat preset would otherwise place villages. A scenario
            // that asks for a normal world (Scenario.normalWorld) gets the default terrain with structures on.
            boolean normal = scenario != null && scenario.normalWorld();
            boolean structures = normal || (scenario != null && scenario.generateStructures());
            WorldOptions options = new WorldOptions(WORLD_SEED, structures, false);
            log("creating " + (normal ? "normal" : "superflat") + " creative world" + (structures ? " with structures" : "")
                    + " saves/" + folder);
            phase = Phase.WAITING_FOR_PLAYER;
            where = "waiting for the player to spawn";
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(normal ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
        } catch (Throwable t) {
            finish("FAIL world creation: " + describe(t), t);
        }
    }

    private void waitForPlayer(Minecraft mc) throws Exception {
        if (join != null && mc.screen instanceof DisconnectedScreen) {
            finish("FAIL could not join " + join + ": " + screenText(mc.screen), null);
            return;
        }
        if (join != null && mc.screen instanceof TitleScreen) {
            finish("FAIL joining " + join + " fell back to the title screen (see logs/latest.log)", null);
            return;
        }
        if (mc.screen instanceof TitleScreen) {
            finish("FAIL world creation fell back to the title screen (see logs/latest.log)", null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) {
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                CommandSourceStack quiet = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(quiet, "time set noon");
                server.getCommands().performPrefixedCommand(quiet, "weather clear");
            });
        }
        mc.gui.getChat().clearMessages(false);
        chat.clear();
        Steps steps = new Steps();
        scenario.steps(steps);
        runner = new ScenarioRunner(steps, this);
        log(String.format(Locale.ROOT, "player %s spawned at %s after %.1f s%s; running %d steps, budget %d s",
                mc.player.getGameProfile().getName(), mc.player.blockPosition().toShortString(), secondsSince(installedAt),
                server == null ? " on " + (join != null ? join : "a remote server") : "", runner.size(), scenario.timeBudgetSeconds()));
        scenarioStartedAt = System.nanoTime();
        phase = Phase.RUNNING;
        runScenarioTick();
    }

    private void runScenarioTick() throws Exception {
        where = runner.describeCurrent();
        if (secondsSince(scenarioStartedAt) > scenario.timeBudgetSeconds()) {
            finish("FAIL time budget of " + scenario.timeBudgetSeconds() + " s used up at " + where, null);
            return;
        }
        boolean done = runner.tick();
        where = runner.describeCurrent();
        if (done) {
            finish("PASS", null);
        }
    }

    /** Writes the result and quits. Client thread only; later calls are ignored. */
    private void finish(String result, @Nullable Throwable error) {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        if (error != null) {
            LOGGER.error("[autotest] {}", result, error);
        }
        waitForScreenshotWrites();
        if (HiddenWindow.timesReHidden() > 0 && result.equals("PASS")) {
            result = "FAIL the window became visible " + HiddenWindow.timesReHidden() + " time(s) and was hidden again; see logs/latest.log";
        }
        writeResult(result);
        Minecraft.getInstance().stop();
    }

    // ---- screenshots, taken from the main render target right after a frame is drawn ----

    @Override
    public CompletableFuture<Void> screenshot(String label) {
        if (pendingShot != null) {
            throw new IllegalStateException("screenshot " + pendingShot.label() + " is still pending");
        }
        ShotRequest request = new ShotRequest(label, new CompletableFuture<>());
        pendingShot = request;
        return request.captured();
    }

    private void onFrameRendered() {
        ShotRequest request = pendingShot;
        if (request == null) {
            return;
        }
        pendingShot = null;
        try {
            NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
            String blank = BlankFrameCheck.blankReason(image.getWidth(), image.getHeight(), image::getPixelRGBA);
            Path file = outDir.resolve(request.label() + ".png");
            log("screenshot " + file.getFileName() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            pendingWrites.add(CompletableFuture.runAsync(() -> {
                try (image) {
                    image.writeToFile(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, Util.ioPool()));
            if (blank != null) {
                request.captured().completeExceptionally(new Steps.Failure("screenshot " + request.label() + " is blank: " + blank));
            } else {
                request.captured().complete(null);
            }
        } catch (Throwable t) {
            request.captured().completeExceptionally(t);
        }
    }

    private void waitForScreenshotWrites() {
        try {
            CompletableFuture.allOf(pendingWrites.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("[autotest] saving a screenshot failed", e);
        }
    }

    // ---- what steps can do ----

    @Override
    public void log(String message) {
        ScenarioRunner r = runner;
        String line = phase == Phase.RUNNING && r != null ? "t=" + r.ticks() + " " + message : message;
        LOGGER.info("[autotest] {}", line);
        synchronized (this) {
            try {
                Files.createDirectories(outDir);
                Files.writeString(outDir.resolve("log.txt"), line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.warn("[autotest] could not write log.txt", e);
            }
        }
    }

    @Override
    public void command(String command) {
        log("command /" + command);
        player().connection.sendCommand(command);
    }

    @Override
    public void key(KeyMapping key, boolean down) {
        InputConstants.Key input = key.getKey();
        if (input.equals(InputConstants.UNKNOWN)) {
            throw new Steps.Failure("key " + key.getName() + " is not bound");
        }
        // The same calls KeyboardHandler and MouseHandler make for real input.
        KeyMapping.set(input, down);
        if (down) {
            KeyMapping.click(input);
            if (!key.isDown()) {
                throw new Steps.Failure("key " + key.getName() + " did not go down (screen " + screenName(Minecraft.getInstance().screen) + ")");
            }
        }
        log((down ? "hold " : "release ") + key.getName());
    }

    @Override
    public void look(float yaw, float pitch) {
        LocalPlayer player = player();
        float y = Mth.wrapDegrees(yaw);
        float x = Mth.clamp(pitch, -90.0F, 90.0F);
        player.setYRot(y);
        player.setXRot(x);
        player.yRotO = y;
        player.xRotO = x;
        player.setYHeadRot(y);
        player.yHeadRotO = y;
        player.yBodyRot = y;
        player.yBodyRotO = y;
        player.yBob = y;
        player.yBobO = y;
        player.xBob = x;
        player.xBobO = x;
        log(String.format(Locale.ROOT, "look yaw %.1f pitch %.1f", y, x));
    }

    @Override
    public List<String> chat() {
        return chat;
    }

    private void onChat(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        chat.add(text);
        if (phase == Phase.RUNNING) {
            log("chat: " + text);
        }
    }

    private static LocalPlayer player() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new Steps.Failure("there is no player");
        }
        return player;
    }

    // ---- result file, watchdog, exit ----

    private void writeResult(String result) {
        if (!resultWritten.compareAndSet(false, true)) {
            return;
        }
        resultWrittenAt = System.nanoTime();
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve("result.txt"), result + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[autotest] could not write result.txt", e);
        }
        log("RESULT " + result);
    }

    private void watchdog() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            if (resultWritten.get()) {
                if (secondsSince(resultWrittenAt) > EXIT_GRACE_SECONDS) {
                    LOGGER.error("[autotest] the game did not exit {} s after the result; halting", EXIT_GRACE_SECONDS);
                    // a hung stop is a failed run whatever the scenario said: the result file must say so (see ExitStall)
                    LOGGER.error("[autotest] {}", ExitStall.mark(outDir.resolve("result.txt"), EXIT_GRACE_SECONDS));
                    Runtime.getRuntime().halt(3);
                }
                continue;
            }
            Phase now = phase;
            String problem = null;
            if (now.ordinal() < Phase.RUNNING.ordinal() && secondsSince(installedAt) > STARTUP_BUDGET_SECONDS) {
                problem = "startup took over " + STARTUP_BUDGET_SECONDS + " s (stuck at: " + where + ")";
            } else if (now == Phase.RUNNING && scenario != null
                    && secondsSince(scenarioStartedAt) > scenario.timeBudgetSeconds() + WATCHDOG_GRACE_SECONDS) {
                problem = "time budget of " + scenario.timeBudgetSeconds() + " s used up and the client thread is not responding, at " + where;
            }
            if (problem != null) {
                writeResult("FAIL " + problem);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(mc::stop);
            }
        }
    }

    private void onJvmExit() {
        if (!resultWritten.get()) {
            writeResult("FAIL the game exited before the scenario finished (at: " + where + "); see logs/latest.log and crash-reports");
        }
    }

    /** Empties this scenario's output folder. A failure here is reported as FAIL, never thrown. */
    private void clearOutputFolder() {
        try {
            if (Files.isDirectory(outDir)) {
                try (Stream<Path> files = Files.walk(outDir)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                        if (!path.equals(outDir)) {
                            Files.delete(path);
                        }
                    }
                }
            }
            Files.createDirectories(outDir);
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("[autotest] could not clear {}", outDir, e);
            if (setupProblem == null) {
                setupProblem = "could not clear the output folder " + outDir + " (" + e + ")";
            }
        }
    }

    private static double secondsSince(long nanoTime) {
        return (System.nanoTime() - nanoTime) / 1.0e9;
    }

    private static String screenName(@Nullable Screen screen) {
        return screen == null ? "none" : screen.getClass().getSimpleName();
    }

    /** A screen's title and the text of its text widgets (a disconnect screen's reason), for a FAIL line. */
    private static String screenText(Screen screen) {
        StringBuilder text = new StringBuilder(screen.getTitle().getString());
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && !(child instanceof AbstractButton)) {
                String s = widget.getMessage().getString();
                if (!s.isBlank()) {
                    text.append(": ").append(s);
                }
            }
        }
        return text.toString().replace('\n', ' ');
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String describe(Throwable t) {
        return t instanceof Steps.Failure ? t.getMessage() : t.toString();
    }
}
