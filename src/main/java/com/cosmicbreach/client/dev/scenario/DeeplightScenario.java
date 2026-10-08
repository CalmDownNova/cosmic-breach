package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.sky.AetheriaFog;
import com.cosmicbreach.client.sky.DeepShade;
import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.weather.WeatherClient;
import com.cosmicbreach.entity.stalker.HollowStalker;
import com.cosmicbreach.entity.stalker.StalkerLight;
import com.cosmicbreach.entity.stalker.Stalkers;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.light.DeepLight;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Deep's darkness (W3c), in the game and measured. Sky light in the Deep draws at {@link DeepLight#DEEP_SKY_SHARE}
 * of its level, the rule a Hollow Stalker sees by; block light is untouched.
 *
 * <ol>
 *   <li>The same 7 by 7 patch of white concrete, floating in open sky at noon, in the Reach and in the Deep, seen from
 *       the same camera: the mean luminance of the middle of the frame in each, and in the Deep with the shade off (the
 *       look before W3c). The Reach and the Drift are left as they were (share 1, the Reach's patch identical with
 *       the shade off).</li>
 *   <li>In the Deep: a torch, Neon Lichen, the eclipse night, an Eclipse Surge (and a torch in it), night vision; the
 *       open view and the Drift overhead from the ground; the HUD's hotbar icon unchanged; the fog colour.</li>
 *   <li>A camera falling through Shear band B at 10 blocks a second (slow falling's pace), with the patch's luminance
 *       and the drawn share at each shot.</li>
 *   <li>The rule and the drawing agree (the patch's ground is light 6 to a Stalker, and the lightmap draws 40%), and a
 *       Stalker crossing the pad at noon comes round a lit strip on the dark ground, never into block light 12.</li>
 * </ol>
 */
public final class DeeplightScenario implements Scenario {
    private static final String TAG = "cb_deeplight_test";
    private static final int VIEW_X = 620;
    private static final int VIEW_Z = -380;
    /** The Deep's pad: 25 by 25 (half 12), white concrete in the middle 7 by 7 (half 3). */
    private static final int PAD = 12;
    private static final int PATCH = 3;
    private static final int DEEP_PAD_Y = 128;
    private static final int DEEP_PAD_Y_MAX = 140;
    /** Over the Reach's islands (tops 346 to 381). */
    private static final int REACH_PAD_Y = 396;
    private static final int SEARCH = 144;
    /** The middle of the frame that is measured (pixels), well inside the patch. */
    private static final int MID_W = 240;
    private static final int MID_H = 140;

    private record Sample(Vec3 at, int blockLight, int effective) {
    }

    private final Map<String, double[]> m = new ConcurrentHashMap<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private volatile boolean sampling;
    private BlockPos reachPad = BlockPos.ZERO;
    private BlockPos deepPad = BlockPos.ZERO;
    private @Nullable DevCamera camera;

    @Override
    public int timeBudgetSeconds() {
        return 1000;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("time set 6000")
                .run("render distance 12, no GUI", () -> {
                    mc.options.renderDistance().set(12);
                    mc.options.hideGui = true;
                })
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + VIEW_X + " 400 " + VIEW_Z)
                .run("fly", () -> fly(mc))
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null)
                .command("cosmicbreach weather clear");
        reach(steps, mc);
        drift(steps, mc);
        deep(steps, mc);
        descent(steps, mc);
        stalker(steps, mc);
        steps.run("the GUI back", () -> mc.options.hideGui = false)
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the Reach and the Drift

    private void reach(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto reach")
                .waitUntil("the island is drawn", 1600, settled(mc, 1500))
                .waitUntil("the sky has faded to the Reach", 200, () -> SkyState.frame().layers[0] > 0.999)
                .run("a white patch floating in open sky over the Reach", () -> reachPad = build(mc, REACH_PAD_Y, REACH_PAD_Y, PATCH))
                .run("the player out of the camera's view", () -> park(mc, reachPad))
                .waitTicks(40)
                .check("the patch has the full sky's light and no block light", () -> light(mc, reachPad, LightLayer.SKY) == 15
                        && light(mc, reachPad, LightLayer.BLOCK) == 0)
                .run("a camera over the patch", () -> patchCamera(mc, reachPad))
                .waitTicks(20)
                .screenshot("reach_patch_noon")
                .run("measure", () -> measure("reach", mid(mc)))
                .check("the Reach draws the sky's full light", () -> DeepShade.share(SkyState.frame().layers[2]) == 1f)
                .run("the shade off", () -> DeepShade.enabledForTest = false)
                .waitTicks(3)
                .run("measure", () -> measure("reach_off", mid(mc)))
                .run("the shade on", () -> DeepShade.enabledForTest = true)
                .waitTicks(3)
                .check("the Reach looks exactly as before", () -> Math.abs(lum("reach") - lum("reach_off")) < 0.004)
                .run("back to the player's eyes", () -> dropCamera())
                .command("give @s minecraft:white_concrete")
                .run("the HUD on", () -> mc.options.hideGui = false)
                .waitTicks(5)
                .run("measure the hotbar's first icon", () -> measure("hud_reach", hotbarIcon(mc)))
                .run("the HUD off", () -> mc.options.hideGui = true);
    }

    private void drift(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto drift")
                .waitUntil("the asteroid is drawn", 1600, settled(mc, 1500))
                .waitUntil("the sky has faded to the Drift", 200, () -> SkyState.frame().layers[1] > 0.999)
                .check("the Drift draws the sky's full light (the rule agrees)", () -> DeepShade.share(SkyState.frame().layers[2]) == 1f
                        && DeepLight.skyScale(mc.level, mc.player.getY()) == 1.0)
                .log("drift", () -> {
                    String s = String.format(Locale.ROOT, "the Drift at Y %.0f: drawn share %.2f, rule %.2f", mc.player.getY(),
                            DeepShade.share(SkyState.frame().layers[2]), DeepLight.skyScale(mc.level, mc.player.getY()));
                    summary.add(s);
                    return s;
                });
    }

    // ------------------------------------------------------------------ the Deep

    private void deep(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug goto deep")
                .waitUntil("the platform is drawn", 1600, settled(mc, 1500))
                .waitUntil("the sky has faded to the Deep", 200, () -> SkyState.frame().layers[2] > 0.999)
                .run("no Stalkers about", this::clearStalkers)
                .run("stand still on the ground", () -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                })
                .waitTicks(20)
                .look(0, 5)
                .waitTicks(3)
                .screenshot("deep_open_noon")
                .run("measure", () -> measure("view", whole(mc)))
                .run("the shade off", () -> DeepShade.enabledForTest = false)
                .waitTicks(3)
                .screenshot("deep_open_noon_before")
                .run("measure", () -> {
                    measure("view_off", whole(mc));
                    fog("fog_off");
                })
                .run("the shade on", () -> DeepShade.enabledForTest = true)
                .waitTicks(3)
                .run("fog", () -> fog("fog"))
                .check("the fog's colour is its own (the lightmap doesn't touch it)", () -> close(m.get("fog"), m.get("fog_off"), 1e-4))
                .look(0, -60)
                .waitTicks(3)
                .screenshot("deep_look_up_drift")
                .run("the HUD on", () -> mc.options.hideGui = false)
                .waitTicks(5)
                .run("measure the hotbar's first icon", () -> measure("hud_deep", hotbarIcon(mc)))
                .run("the shade off", () -> DeepShade.enabledForTest = false)
                .waitTicks(3)
                .run("measure the hotbar's first icon", () -> measure("hud_deep_off", hotbarIcon(mc)))
                .run("the shade on, the HUD off", () -> {
                    DeepShade.enabledForTest = true;
                    mc.options.hideGui = true;
                })
                .check("the HUD is untouched", () -> close(m.get("hud_deep"), m.get("hud_deep_off"), 0.004)
                        && close(m.get("hud_deep"), m.get("hud_reach"), 0.02))
                .run("fly", () -> fly(mc))
                .run("a pad floating in open sky over the Deep", () -> deepPad = build(mc, DEEP_PAD_Y, DEEP_PAD_Y_MAX, PAD))
                .run("the player out of the camera's view", () -> park(mc, deepPad))
                .waitTicks(40)
                .check("the patch has the full sky's light and no block light", () -> light(mc, deepPad, LightLayer.SKY) == 15
                        && light(mc, deepPad, LightLayer.BLOCK) == 0)
                .check("the rule: to a Stalker the patch's ground is light 6 at noon, dark", () -> ServerQuery.ask(p ->
                        StalkerLight.effective(p.level(), deepPad.above()) == 6 && StalkerLight.dark(p.level(), deepPad.above())))
                .check("the drawing: the lightmap draws the Deep's share", () ->
                        Math.abs(DeepShade.share(SkyState.frame().layers[2]) - DeepLight.DEEP_SKY_SHARE) < 1e-6)
                .run("a camera over the patch", () -> patchCamera(mc, deepPad))
                .waitTicks(20)
                .screenshot("deep_patch_noon")
                .run("measure", () -> measure("deep", mid(mc)))
                .run("the shade off", () -> DeepShade.enabledForTest = false)
                .waitTicks(3)
                .screenshot("deep_patch_noon_before")
                .run("measure", () -> measure("deep_off", mid(mc)))
                .run("the shade on", () -> DeepShade.enabledForTest = true)
                .waitTicks(3)
                .check("before W3c the Deep's patch looked like the Reach's", () -> Math.abs(lum("deep_off") / lum("reach") - 1.0) < 0.1)
                .check("now it is clearly darker", () -> lum("deep") < 0.75 * lum("deep_off"))
                .log("open ground", () -> ratio("the Deep's open ground at noon", "deep", "reach")
                        + "; " + ratio("before W3c", "deep_off", "reach"));

        // a torch
        steps.run("a torch two blocks off the middle", () -> put(deepPad.offset(-2, 1, 0), Blocks.TORCH.defaultBlockState()))
                .waitTicks(10)
                .screenshot("deep_patch_torch")
                .run("measure", () -> measure("torch", mid(mc)))
                .check("torchlight reads strongly", () -> lum("torch") > 1.25 * lum("deep"))
                .log("torch", () -> ratio("under a torch", "torch", "deep"))
                .run("the torch gone", () -> put(deepPad.offset(-2, 1, 0), Blocks.AIR.defaultBlockState()));

        // Neon Lichen
        steps.run("Neon Lichen round the middle", () -> {
                    BlockState magenta = floorLichen(ModBlocks.MAGENTA_NEON_LICHEN.get().defaultBlockState());
                    BlockState teal = floorLichen(ModBlocks.TEAL_NEON_LICHEN.get().defaultBlockState());
                    put(deepPad.offset(-1, 1, 0), magenta);
                    put(deepPad.offset(1, 1, 1), teal);
                    put(deepPad.offset(0, 1, -1), magenta);
                    put(deepPad.offset(2, 1, -1), teal);
                })
                .waitTicks(10)
                .screenshot("deep_patch_lichen")
                .run("measure", () -> measure("lichen", mid(mc)))
                .check("the lichen lights the patch", () -> lum("lichen") > 1.03 * lum("deep"))
                .log("lichen", () -> ratio("by lichen", "lichen", "deep"));

        // the eclipse night, by the lichen and then without it
        time(steps, mc, 18000);
        steps.screenshot("deep_patch_night_lichen")
                .run("the lichen gone", () -> {
                    for (BlockPos p : new BlockPos[] {deepPad.offset(-1, 1, 0), deepPad.offset(1, 1, 1), deepPad.offset(0, 1, -1),
                            deepPad.offset(2, 1, -1)}) {
                        put(p, Blocks.AIR.defaultBlockState());
                    }
                })
                .waitTicks(10)
                .screenshot("deep_patch_night")
                .run("measure", () -> measure("night", mid(mc)))
                .check("the eclipse night is darker still", () -> lum("night") < lum("deep"))
                .log("night", () -> ratio("the eclipse night", "night", "reach"));
        time(steps, mc, 6000);

        // an Eclipse Surge
        steps.command("cosmicbreach weather surge deep")
                .waitTicks(5)
                .command("cosmicbreach weather skip deep")
                .waitUntil("the Surge has dimmed the sky", 300, () -> WeatherClient.hooks()[2] > 0.98f)
                .waitTicks(3)
                .screenshot("deep_patch_surge")
                .run("measure", () -> measure("surge", mid(mc)))
                .check("the Surge is darker still", () -> lum("surge") < lum("deep"))
                .log("surge", () -> ratio("an Eclipse Surge", "surge", "reach"))
                .run("a torch in the Surge", () -> put(deepPad.offset(-2, 1, 0), Blocks.TORCH.defaultBlockState()))
                .waitTicks(10)
                .screenshot("deep_patch_surge_torch")
                .run("measure", () -> measure("surge_torch", mid(mc)))
                .check("a torch still lights the ground in the Surge", () -> lum("surge_torch") > 1.25 * lum("surge"))
                .run("the torch gone", () -> put(deepPad.offset(-2, 1, 0), Blocks.AIR.defaultBlockState()))
                .command("cosmicbreach weather clear deep")
                .waitUntil("the Surge has passed", 300, () -> WeatherClient.hooks()[2] < 0.005f)
                .waitTicks(3);

        // night vision
        steps.command("effect give @s minecraft:night_vision 60 0 true")
                .waitTicks(10)
                .screenshot("deep_patch_night_vision")
                .run("measure", () -> measure("night_vision", mid(mc)))
                .check("night vision still works", () -> lum("night_vision") > 1.2 * lum("deep"))
                .command("effect clear @s minecraft:night_vision")
                .waitTicks(10)
                .run("back to the player's eyes", this::dropCamera);
    }

    // ------------------------------------------------------------------ Shear band B

    private void descent(Steps steps, Minecraft mc) {
        double[] ys = {176, 158, 152.5, 147, 140};
        steps.run("a camera over the pad at Y 176", () -> descentCamera(mc, 176))
                .waitUntil("the sky has faded to the Drift's", 300, () -> SkyState.frame().layers[2] < 0.001)
                .waitTicks(3)
                .screenshot("descent_1_y176")
                .run("measure", () -> measure("descent_1", small(mc)));
        int shot = 2;
        for (double y = 175.5; y >= 139.99; y -= 0.5) {
            double at = y;
            steps.run(String.format(Locale.ROOT, "the camera at Y %.1f", at), () -> descentCamera(mc, at)).waitTicks(1);
            for (int i = 1; i < ys.length; i++) {
                if (Math.abs(ys[i] - at) < 0.01) {
                    String label = "descent_" + shot + "_y" + (int) Math.floor(at);
                    String key = "descent_" + shot;
                    steps.screenshot(label)
                            .run("measure", () -> measure(key, small(mc)))
                            .log(key, () -> String.format(Locale.ROOT, "Y %.1f: drawn share %.2f (rule %.2f)", at,
                                    DeepShade.share(SkyState.frame().layers[2]), DeepLight.skyScale(at)));
                    shot++;
                }
            }
        }
        int last = shot;
        steps.waitUntil("the fade has finished", 300, () -> SkyState.frame().layers[2] > 0.999)
                .waitTicks(3)
                .screenshot("descent_" + last + "_settled")
                .run("measure", () -> measure("descent_" + last, small(mc)))
                .check("the ground darkens all the way down and ends at the Deep's", () -> {
                    for (int i = 2; i <= last; i++) {
                        if (lum("descent_" + i) > lum("descent_" + (i - 1)) + 0.01) {
                            return false;
                        }
                    }
                    return lum("descent_" + last) < 0.8 * lum("descent_1");
                })
                .log("descent", () -> {
                    StringBuilder b = new StringBuilder("falling through Shear band B, the patch's luminance:");
                    for (int i = 1; i <= last; i++) {
                        b.append(String.format(Locale.ROOT, " %.3f", lum("descent_" + i)));
                    }
                    summary.add(b.toString());
                    return b.toString();
                })
                .run("back to the player's eyes", this::dropCamera);
    }

    // ------------------------------------------------------------------ the Stalker

    private void stalker(Steps steps, Minecraft mc) {
        steps.run("a bright strip across the middle of the pad", () -> ServerQuery.ask(p -> {
                    for (int z = -4; z <= 4; z++) {
                        p.serverLevel().setBlock(deepPad.offset(0, 1, z), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                    }
                    return true;
                }))
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("the player at the pad's west end, back to the east", () -> {
                    Vec3 a = padAt(-10, 0);
                    ColossusScenario.tp(mc, a.x, a.y, a.z, a.x - 10, a.y + 1.6, a.z);
                })
                .waitTicks(10)
                .run("no other Stalkers", this::clearStalkers)
                .check("open ground there is dark to a Stalker at noon", () -> ServerQuery.ask(p ->
                        StalkerLight.dark(p.level(), deepPad.offset(6, 1, 6))))
                .run("a Stalker at the east end (no steps, no grasps)", () -> ServerQuery.ask(p -> {
                    HollowStalker s = Stalkers.HOLLOW_STALKER.get().create(p.serverLevel());
                    Vec3 at = padAt(10, 0);
                    s.moveTo(at.x, at.y, at.z, 90f, 0f);
                    s.setPersistenceRequired();
                    s.addTag(TAG);
                    p.serverLevel().addFreshEntity(s);
                    s.setTarget(p);
                    s.holdBack(100000);
                    return true;
                }))
                .run("record its path", () -> {
                    samples.clear();
                    sampling = true;
                })
                .waitUntil("it comes round the light to the player's back", 500, () -> ServerQuery.ask(p -> {
                    HollowStalker s = nearest(p);
                    return s != null && s.distanceTo(p) < 4.0;
                }))
                .run("stop recording", () -> sampling = false)
                .check("it never stood in block light of 12 or more", () -> !samples.isEmpty() && samples.stream().allMatch(s -> s.blockLight() < 12))
                .check("it went round the strip on the dark ground, not through it", () -> samples.stream()
                        .filter(s -> Math.abs(s.at().x - (deepPad.getX() + 0.5)) < 2.0)
                        .allMatch(s -> Math.abs(s.at().z - (deepPad.getZ() + 0.5)) >= 6.5))
                .log("stalker", () -> {
                    long dark = samples.stream().filter(s -> s.effective() < 8).count();
                    int brightest = samples.stream().mapToInt(Sample::blockLight).max().orElse(-1);
                    String s = String.format(Locale.ROOT, "a Stalker crossing the pad at noon: %d ticks, %d%% of them on ground dark to it "
                            + "(the Deep's open ground counts 6), brightest block light stood in %d", samples.size(),
                            samples.isEmpty() ? 0 : Math.round(100.0 * dark / samples.size()), brightest);
                    summary.add(s);
                    return s;
                })
                .run("the Stalker gone", this::clearStalkers)
                .command("gamemode creative");
    }

    // ------------------------------------------------------------------ helpers

    private void listen() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (!sampling) {
                return;
            }
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                HollowStalker s = nearest(p);
                if (s != null) {
                    samples.add(new Sample(s.position(), StalkerLight.block(s.level(), s.blockPosition()),
                            StalkerLight.effective(s.level(), s.blockPosition())));
                }
            }
        });
    }

    private static @Nullable HollowStalker nearest(ServerPlayer p) {
        HollowStalker best = null;
        double bestD = Double.MAX_VALUE;
        for (HollowStalker s : p.serverLevel().getEntitiesOfClass(HollowStalker.class, p.getBoundingBox().inflate(48.0))) {
            double d = s.distanceToSqr(p);
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    private void clearStalkers() {
        ServerQuery.ask(p -> {
            for (HollowStalker s : p.serverLevel().getEntitiesOfClass(HollowStalker.class, p.getBoundingBox().inflate(96.0))) {
                s.discard();
            }
            return true;
        });
    }

    /**
     * Finds open sky near the player (every column's highest block under the pad's height) and builds a floating pad
     * there, half size {@code half}: white concrete in the middle 7 by 7, Umbral Basalt round it. Returns the pad's
     * middle block. The pad goes at {@code padY}, or as high as {@code maxY} if the open area nearest is that tall.
     */
    private static BlockPos build(Minecraft mc, int padY, int maxY, int half) {
        return ServerQuery.ask(p -> {
            ServerLevel level = p.serverLevel();
            int px = p.getBlockX();
            int pz = p.getBlockZ();
            int span = SEARCH + half;
            int size = 2 * span + 1;
            int[] top = new int[size * size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    top[i * size + j] = level.getHeight(Heightmap.Types.WORLD_SURFACE, px - span + i, pz - span + j);
                }
            }
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            for (int cx = -SEARCH; cx <= SEARCH; cx += 4) {
                for (int cz = -SEARCH; cz <= SEARCH; cz += 4) {
                    int highest = 0;
                    for (int i = cx - half; i <= cx + half && highest <= maxY; i++) {
                        for (int j = cz - half; j <= cz + half; j++) {
                            highest = Math.max(highest, top[(i + span) * size + (j + span)]);
                        }
                    }
                    if (highest > maxY) {
                        continue;
                    }
                    int y = Math.max(padY, highest);
                    double score = Math.hypot(cx, cz) + 20.0 * (y - padY);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(px + cx, y, pz + cz);
                    }
                }
            }
            if (best == null) {
                throw new Steps.Failure("no open sky for a pad within " + SEARCH + " blocks under Y " + maxY);
            }
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean patch = Math.abs(dx) <= PATCH && Math.abs(dz) <= PATCH;
                    level.setBlock(best.offset(dx, 0, dz), patch ? Blocks.WHITE_CONCRETE.defaultBlockState()
                            : ModBlocks.UMBRAL_BASALT.get().defaultBlockState(), 3);
                }
            }
            return best;
        });
    }

    private static void put(BlockPos pos, BlockState state) {
        ServerQuery.ask(p -> p.serverLevel().setBlock(pos, state, 3));
    }

    private static BlockState floorLichen(BlockState lichen) {
        return lichen.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
    }

    private Vec3 padAt(double dx, double dz) {
        return new Vec3(deepPad.getX() + 0.5 + dx, deepPad.getY() + 1, deepPad.getZ() + 0.5 + dz);
    }

    /** The player flying behind and above where the patch camera will be, out of its view. */
    private static void park(Minecraft mc, BlockPos pad) {
        fly(mc);
        ColossusScenario.tp(mc, pad.getX() + 0.5, pad.getY() + 12, pad.getZ() + 0.5 - 10, pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5);
    }

    /** Looks down at the patch's middle from 3 blocks up and 2.5 back. */
    private void patchCamera(Minecraft mc, BlockPos pad) {
        dropCamera();
        Vec3 target = new Vec3(pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5);
        camera = DevCamera.create(mc.level);
        camera.place(target.add(0, 3.0, -2.5), target);
        camera.use();
    }

    /** A camera 9 blocks south of the pad at height {@code y}, looking at the patch. */
    private void descentCamera(Minecraft mc, double y) {
        Vec3 target = new Vec3(deepPad.getX() + 0.5, deepPad.getY() + 1, deepPad.getZ() + 0.5);
        if (camera == null) {
            camera = DevCamera.create(mc.level);
            camera.use();
        }
        camera.place(new Vec3(target.x, y, target.z - 9), target);
    }

    private void dropCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
    }

    private static int light(Minecraft mc, BlockPos pad, LightLayer layer) {
        return mc.level.getBrightness(layer, pad.above());
    }

    private static void fly(Minecraft mc) {
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
    }

    private static void time(Steps steps, Minecraft mc, int dayTime) {
        steps.command("time set " + dayTime)
                .waitUntil("the client's clock reads " + dayTime, 60, () -> Math.floorMod(mc.level.getDayTime(), 24000L) == dayTime)
                .waitTicks(3);
    }

    private void measure(String key, double[] v) {
        m.put(key, v);
        String s = String.format(Locale.ROOT, "%s: luminance %.3f, colour #%02X%02X%02X", key, v[0],
                Math.round(v[1] * 255), Math.round(v[2] * 255), Math.round(v[3] * 255));
        summary.add(s);
    }

    private double lum(String key) {
        double[] v = m.get(key);
        if (v == null) {
            throw new Steps.Failure("nothing measured as " + key);
        }
        return v[0];
    }

    private String ratio(String what, String a, String b) {
        String s = String.format(Locale.ROOT, "%s: luminance %.3f, %.0f%% of %s's %.3f", what, lum(a), 100.0 * lum(a) / lum(b), b, lum(b));
        summary.add(s);
        return s;
    }

    private void fog(String key) {
        float[] c = AetheriaFog.lastColour();
        m.put(key, new double[] {0, c[0], c[1], c[2]});
        summary.add(String.format(Locale.ROOT, "%s: fog colour #%02X%02X%02X", key, Math.round(c[0] * 255), Math.round(c[1] * 255),
                Math.round(c[2] * 255)));
    }

    private static boolean close(double[] a, double[] b, double tolerance) {
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /** Mean luminance and colour (0..1, sRGB values) of the middle of the last frame. */
    private static double[] mid(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int cx = img.getWidth() / 2;
            int cy = img.getHeight() / 2;
            return mean(img, cx - MID_W / 2, cy - MID_H / 2, cx + MID_W / 2, cy + MID_H / 2);
        }
    }

    /** The middle 60 by 40 pixels: inside the patch from 45 blocks away (the descent). */
    private static double[] small(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int cx = img.getWidth() / 2;
            int cy = img.getHeight() / 2;
            return mean(img, cx - 30, cy - 20, cx + 30, cy + 20);
        }
    }

    private static double[] whole(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            return mean(img, 0, 0, img.getWidth(), img.getHeight());
        }
    }

    /** The middle of the hotbar's first icon (a block item, drawn with the GUI's own lighting). */
    private static double[] hotbarIcon(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            double scale = mc.getWindow().getGuiScale();
            int centre = mc.getWindow().getGuiScaledWidth() / 2;
            int bottom = mc.getWindow().getGuiScaledHeight();
            return mean(img, (int) ((centre - 84) * scale), (int) ((bottom - 15) * scale), (int) ((centre - 76) * scale),
                    (int) ((bottom - 7) * scale));
        }
    }

    private static double[] mean(NativeImage img, int x0, int y0, int x1, int y1) {
        double r = 0;
        double g = 0;
        double b = 0;
        int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x1); x++) {
                int abgr = img.getPixelRGBA(x, y);
                r += (abgr & 0xFF) / 255.0;
                g += ((abgr >> 8) & 0xFF) / 255.0;
                b += ((abgr >> 16) & 0xFF) / 255.0;
                n++;
            }
        }
        if (n == 0) {
            throw new Steps.Failure("an empty region to measure");
        }
        r /= n;
        g /= n;
        b /= n;
        return new double[] {0.2126 * r + 0.7152 * g + 0.0722 * b, r, g, b};
    }

    /** No screen, the chunks within 5 have arrived, and the section queue has stayed empty for a while. */
    private static BooleanSupplier settled(Minecraft mc, int maxTicks) {
        int[] state = {0, 0, -1};
        return () -> {
            state[0]++;
            boolean ready = chunksAround(mc, 5);
            int rendered = mc.levelRenderer.countRenderedSections();
            if (ready && mc.levelRenderer.hasRenderedAllSections() && rendered == state[2]) {
                state[1]++;
            } else {
                state[1] = 0;
            }
            state[2] = rendered;
            return state[1] >= 40 || (state[0] >= maxTicks && ready);
        };
    }

    private static boolean chunksAround(Minecraft mc, int radius) {
        if (mc.screen != null || mc.level == null || mc.player == null) {
            return false;
        }
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
