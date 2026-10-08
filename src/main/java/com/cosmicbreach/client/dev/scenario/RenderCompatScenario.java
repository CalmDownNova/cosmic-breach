package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.sky.AetheriaFog;
import com.cosmicbreach.client.sky.DeepShade;
import com.cosmicbreach.client.sky.ShaderPacks;
import com.cosmicbreach.client.sky.SkyStats;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;

/**
 * Sodium and Iris beside ours (F1). Needs both as runtime mods and a shader pack configured but off
 * ({@code AUTOTEST_ARGS=-PrenderCompat=<folder of their jars> bash scripts/client-gate.sh run <who> client scripts/autotest.sh
 * compat-render}, with {@code run-test/shaderpacks/} and {@code run-test/config/iris.properties} set up). Under Sodium with shaders off: our sky
 * draws, our fog runs, the Deep's darkness still comes through the lightmap. With a pack on: our sky steps aside for the
 * pack's, and what the pack makes of the Deep is measured. Nothing of ours throws. Screenshots of each.
 */
public final class RenderCompatScenario implements Scenario {
    private final List<String> results = new ArrayList<>();
    private final double[] lum = new double[4];

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        int[] sky = {0};
        steps.check("Sodium and Iris are loaded", () -> ModList.get().isLoaded("sodium") && ModList.get().isLoaded("iris"))
                .log("versions", () -> String.join(", ", List.of("sodium", "iris").stream().map(id -> id + " "
                        + ModList.get().getModContainerById(id).map(c -> c.getModInfo().getVersion().toString()).orElse("missing")).toList()))
                .check("no shader pack at first", () -> !ShaderPacks.inUse())
                .command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 400 0.5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in the Reach", 600, () -> mc.player.getY() > 300)
                .waitUntil("the island is drawn", 1600, CryptKit.settled(mc, 1500))
                .run("the HUD hidden", () -> mc.options.hideGui = true)
                .run("count our sky's draws", () -> {
                    SkyStats.reset();
                    SkyStats.enabled = true;
                })
                .waitTicks(20)
                .run("stop counting", () -> {
                    SkyStats.enabled = false;
                    sky[0] = SkyStats.samples();
                })
                .check("under Sodium our sky draws", () -> sky[0] > 0)
                .log("fog", () -> String.format(Locale.ROOT, "our fog under Sodium: %.0f to %.0f blocks, shape index %d (Sodium's terrain "
                        + "shaders do not read our vertical factor)", AetheriaFog.lastDistances()[0], AetheriaFog.lastDistances()[1],
                        AetheriaFog.activeShapeIndex()))
                .run("look across the island", () -> {
                    mc.player.setXRot(10f);
                })
                .waitTicks(10)
                .screenshot("compat_sodium_reach")
                .command("cosmicbreach debug goto deep")
                .waitUntil("in the Deep", 600, () -> mc.player.getY() < 145)
                .waitUntil("the Deep is drawn", 1600, CryptKit.settled(mc, 1500))
                .run("look at the ground", () -> mc.player.setXRot(35f))
                .waitTicks(10);
        shade(steps, mc, 0, "Sodium");
        steps.screenshot("compat_sodium_deep")
                .run("a shader pack on", () -> shaders(true))
                .waitUntil("the pack is in use", 1200, ShaderPacks::inUse)
                .waitTicks(60)
                .run("count our sky's draws", () -> {
                    SkyStats.reset();
                    SkyStats.enabled = true;
                })
                .waitTicks(20)
                .run("stop counting", () -> {
                    SkyStats.enabled = false;
                    sky[0] = SkyStats.samples();
                })
                .check("with a pack on, our sky steps aside", () -> sky[0] == 0)
                .check("and our vertical fog factor too", () -> AetheriaFog.activeShapeIndex() == 0);
        shade(steps, mc, 2, "Iris with a pack");
        steps.screenshot("compat_iris_deep")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in the Reach", 600, () -> mc.player.getY() > 300)
                .waitUntil("the island is drawn", 1600, CryptKit.settled(mc, 1500))
                .run("look across the island", () -> mc.player.setXRot(10f))
                .waitTicks(20)
                .screenshot("compat_iris_reach")
                .run("the pack off", () -> shaders(false))
                .waitUntil("the pack is off", 1200, () -> !ShaderPacks.inUse())
                .run("the HUD on", () -> mc.options.hideGui = false)
                .run("nothing of ours threw", () -> {
                    List<String> trouble = new ArrayList<>();
                    try {
                        List<String> lines = Files.readAllLines(Path.of("logs", "latest.log"), StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            String l = lines.get(i);
                            if ((l.contains("Exception") || l.contains("Error")) && i + 1 < lines.size()
                                    && (l.contains("cosmicbreach") || lines.get(i + 1).contains("com.cosmicbreach"))) {
                                trouble.add(l);
                            }
                        }
                    } catch (IOException e) {
                        throw new Steps.Failure("could not read logs/latest.log: " + e);
                    }
                    if (!trouble.isEmpty()) {
                        throw new Steps.Failure("errors from Cosmic Breach: " + trouble.subList(0, Math.min(3, trouble.size())));
                    }
                })
                .log("results", () -> String.join(System.lineSeparator(), results));
    }

    /** The Deep's ground with our shade on and off: how much of the darkness comes through. */
    private void shade(Steps steps, Minecraft mc, int slot, String what) {
        steps.run("the shade on", () -> DeepShade.enabledForTest = true)
                .waitTicks(5)
                .run("measure with the shade", () -> lum[slot] = lowerHalf(mc))
                .run("the shade off", () -> DeepShade.enabledForTest = false)
                .waitTicks(5)
                .run("measure without", () -> lum[slot + 1] = lowerHalf(mc))
                .run("the shade on again", () -> DeepShade.enabledForTest = true)
                .run("note it", () -> results.add(String.format(Locale.ROOT, "%s: the Deep's ground draws at %.0f%% of its unshaded "
                        + "brightness (luminance %.3f against %.3f)", what, 100.0 * lum[slot] / Math.max(1e-6, lum[slot + 1]), lum[slot], lum[slot + 1])))
                .log(what, () -> results.get(results.size() - 1));
    }

    private static double lowerHalf(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            double sum = 0;
            int n = 0;
            for (int y = img.getHeight() / 2; y < img.getHeight(); y += 4) {
                for (int x = 0; x < img.getWidth(); x += 4) {
                    int abgr = img.getPixelRGBA(x, y);
                    int r = abgr & 255;
                    int g = abgr >> 8 & 255;
                    int b = abgr >> 16 & 255;
                    sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
                    n++;
                }
            }
            return sum / n;
        }
    }

    private static void shaders(boolean on) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("Iris's API was not found: " + e);
        }
    }
}
