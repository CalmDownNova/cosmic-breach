package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.entity.stalker.HollowStalker;
import com.cosmicbreach.entity.stalker.StalkerGrasp;
import com.cosmicbreach.entity.stalker.StalkerLight;
import com.cosmicbreach.entity.stalker.StalkerRules;
import com.cosmicbreach.entity.stalker.Stalkers;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import com.cosmicbreach.status.Rift;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.world.light.TempLights;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Stalker (G7, GDD 7.1) in a sealed dark hall, through the real key path and measured on the server:
 * <ul>
 *   <li>looks: a still Stalker in pitch dark and in Neon Lichen light, from the front and the side;</li>
 *   <li>shadow routing: across the hall to a player whose back is turned, round a bright patch it never enters (block
 *       light 12 or more), coming the dark way;</li>
 *   <li>caught: looked at from 5 blocks it freezes 10 ticks (the mask flaring), then backs off;</li>
 *   <li>Shadow Step: watched from 12 blocks it never steps; the moment the player turns away it steps behind them;</li>
 *   <li>Rend: parried on its gold glint (it staggers, the player unhurt), then taken (12 damage on Normal, a stack of Rift);</li>
 *   <li>Grasp: from behind in the dark, 3 damage a hit through iron armor, the player pinned; a dash breaks it;</li>
 *   <li>an ability's light: the Binary Edges' Tether strikes it and leaves light 12 at its feet for 40 ticks; it leaves;</li>
 *   <li>drops: a kill drops Umbral Silk; 2000 rolls of its loot table give the Eclipsium Nugget about 20% and the
 *       Mask Shard about 5%; and a Hollow Crypt's Stalker spot wakes one.</li>
 * </ul>
 */
public final class StalkerScenario implements Scenario {
    static final String TAG = "cb_stalker_test";
    private static final int HALL = 14;

    private record Sample(long time, Vec3 at, int blockLight, String state) {
    }

    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private volatile boolean sampling;
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;
    private float healthBefore;
    private int[] countsBefore = new int[7];
    private Vec3 markAt = Vec3.ZERO;

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    // ------------------------------------------------------------------ helpers

    private static @Nullable HollowStalker stalker(ServerPlayer p) {
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

    private static HollowStalker need(ServerPlayer p) {
        HollowStalker s = stalker(p);
        if (s == null) {
            throw new Steps.Failure("no Hollow Stalker near the player");
        }
        return s;
    }

    private static <T> T ask(java.util.function.Function<HollowStalker, T> q) {
        return ServerQuery.ask(p -> q.apply(need(p)));
    }

    private static HollowStalker spawn(ServerPlayer p, Vec3 at, float yaw, boolean ai) {
        HollowStalker s = Stalkers.HOLLOW_STALKER.get().create(p.serverLevel());
        s.moveTo(at.x, at.y, at.z, yaw, 0f);
        s.setYHeadRot(yaw);
        s.setYBodyRot(yaw);
        s.setPersistenceRequired();
        s.setNoAi(!ai);
        s.addTag(TAG);
        p.serverLevel().addFreshEntity(s);
        return s;
    }

    private static void clearStalkers() {
        ServerQuery.ask(p -> {
            for (HollowStalker s : p.serverLevel().getEntitiesOfClass(HollowStalker.class, p.getBoundingBox().inflate(64.0))) {
                s.discard();
            }
            StalkerGrasp.forget(p);
            return true;
        });
    }

    private Vec3 at(double dx, double dz) {
        return new Vec3(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz);
    }

    /** The player to {@code (dx, dz)} in the hall, looking at a point. */
    private void stand(Minecraft mc, double dx, double dz, Vec3 look) {
        Vec3 a = at(dx, dz);
        ColossusScenario.tp(mc, a.x, a.y, a.z, look.x, look.y, look.z);
    }

    private static void key(KeyMapping k, boolean down) {
        KeyMapping.set(k.getKey(), down);
    }

    private void heal() {
        ServerQuery.ask(p -> {
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.getFoodData().setFoodLevel(20);
            return true;
        });
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (!sampling) {
                return;
            }
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                HollowStalker s = stalker(p);
                if (s != null) {
                    samples.add(new Sample(p.level().getGameTime(), s.position(), StalkerLight.block(s.level(), s.blockPosition()),
                            s.serverState().name()));
                }
            }
        });
    }

    // ------------------------------------------------------------------ the run

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("gamerule naturalRegeneration false")
                .command("time set noon")
                .run("a sealed dark hall round the player", () -> base = ServerQuery.ask(p -> {
                    BlockPos c = p.blockPosition();
                    ServerLevel level = p.serverLevel();
                    for (int x = -HALL - 1; x <= HALL + 1; x++) {
                        for (int z = -HALL - 1; z <= HALL + 1; z++) {
                            for (int y = -1; y <= 6; y++) {
                                boolean shell = y == -1 || y == 6 || Math.abs(x) == HALL + 1 || Math.abs(z) == HALL + 1;
                                level.setBlock(c.offset(x, y, z), shell ? (y == -1 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS)
                                        .defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    }
                    return c;
                }))
                .waitTicks(40)
                .check("the hall is dark to a Stalker", () -> ServerQuery.ask(p -> StalkerLight.dark(p.level(), p.blockPosition())))
                .command("gamemode creative");
        looks(steps, mc);
        steps.command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative());
        routing(steps, mc);
        caught(steps, mc);
        shadowStep(steps, mc);
        rend(steps, mc);
        grasp(steps, mc);
        abilityLight(steps, mc);
        drops(steps, mc);
        cryptSpot(steps, mc);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ looks

    private void looks(Steps steps, Minecraft mc) {
        steps.run("a still Stalker 4.5 blocks ahead", () -> {
                    stand(mc, 0, -4.5, at(0, 0).add(0, 1.9, 0));
                    ServerQuery.ask(p -> spawn(p, at(0, 0), 180f, false));
                })
                .waitTicks(20)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("stalker_dark")
                .run("side view in the dark", () -> {
                    camera = DevCamera.create(mc.level);
                    camera.place(at(3.6, -1.2).add(0, 1.9, 0), at(0, 0).add(0, 1.4, 0));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("stalker_dark_side")
                .run("Neon Lichen round it", () -> ServerQuery.ask(p -> {
                    ServerLevel level = p.serverLevel();
                    int[][] spots = {{-2, 1}, {2, 2}, {-1, 3}, {3, -1}, {-3, -2}, {1, -3}};
                    for (int[] s : spots) {
                        BlockPos pos = base.offset(s[0], 0, s[1]);
                        level.setBlock(pos, (s[0] + s[1]) % 2 == 0 ? ModBlocks.MAGENTA_NEON_LICHEN.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN), true)
                                : ModBlocks.TEAL_NEON_LICHEN.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN), true), 3);
                    }
                    return true;
                }))
                .waitTicks(20)
                .screenshot("stalker_lichen_side")
                .run("front view in lichen light", () -> {
                    camera.remove();
                    camera = null;
                    mc.setCameraEntity(mc.player);
                })
                .waitTicks(3)
                .screenshot("stalker_lichen")
                .log("looks", () -> {
                    String s = ServerQuery.ask(p -> String.format(Locale.ROOT, "light at the Stalker: block %d, effective %d",
                            StalkerLight.block(p.level(), need(p).blockPosition()), StalkerLight.effective(p.level(), need(p).blockPosition())));
                    summary.add("looks: " + s);
                    return s;
                })
                .run("the lichen away, the still Stalker gone", () -> {
                    ServerQuery.ask(p -> {
                        for (int x = -4; x <= 4; x++) {
                            for (int z = -4; z <= 4; z++) {
                                BlockPos pos = base.offset(x, 0, z);
                                if (!p.serverLevel().getBlockState(pos).isAir()) {
                                    p.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                }
                            }
                        }
                        return true;
                    });
                    clearStalkers();
                })
                .waitTicks(20);
    }

    // ------------------------------------------------------------------ shadow routing

    private void routing(Steps steps, Minecraft mc) {
        steps.run("a bright patch across the middle of the hall", () -> ServerQuery.ask(p -> {
                    for (int z = -4; z <= 4; z++) {
                        p.serverLevel().setBlock(base.offset(0, 0, z), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                    }
                    return true;
                }))
                .waitTicks(10)
                .run("the player at the west end, back to the east", () -> stand(mc, -10, 0, at(-20, 0).add(0, 1.6, 0)))
                .run("a Stalker at the east end (no steps, no grasps)", () -> ServerQuery.ask(p -> {
                    HollowStalker s = spawn(p, at(10, 0), 90f, true);
                    s.setTarget(p);
                    s.holdBack(100000);
                    return true;
                }))
                .run("record its path", () -> {
                    samples.clear();
                    sampling = true;
                })
                .waitUntil("it comes round the light to the player's back", 400, () -> ServerQuery.ask(p -> need(p).distanceTo(p) < 4.0))
                .run("stop recording", () -> sampling = false)
                .check("it never stood in block light of 12 or more", () -> samples.stream().allMatch(s -> s.blockLight() < 12))
                .check("it went round the patch, not through it", () -> samples.stream()
                        .filter(s -> Math.abs(s.at().x - (base.getX() + 0.5)) < 2.0)
                        .allMatch(s -> Math.abs(s.at().z - (base.getZ() + 0.5)) >= 6.5))
                .log("routing", () -> {
                    int maxLight = samples.stream().mapToInt(Sample::blockLight).max().orElse(-1);
                    double cross = samples.stream().filter(s -> Math.abs(s.at().x - (base.getX() + 0.5)) < 2.0)
                            .mapToDouble(s -> Math.abs(s.at().z - (base.getZ() + 0.5))).min().orElse(-1);
                    String s = String.format(Locale.ROOT, "routing: %d ticks to the player's back, brightest block light stood in %d, "
                            + "crossed the middle %.1f blocks off the line (the patch reaches 7)", samples.size(), maxLight, cross);
                    summary.add(s);
                    return s;
                })
                .run("the patch gone", () -> {
                    clearStalkers();
                    ServerQuery.ask(p -> {
                        for (int z = -4; z <= 4; z++) {
                            p.serverLevel().setBlock(base.offset(0, 0, z), Blocks.AIR.defaultBlockState(), 3);
                        }
                        return true;
                    });
                })
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ caught

    private void caught(Steps steps, Minecraft mc) {
        steps.run("the player in the middle, looking north", () -> stand(mc, 0, 0, at(0, -10).add(0, 1.6, 0)))
                .run("a Stalker 5 blocks north, facing the player", () -> ServerQuery.ask(p -> {
                    HollowStalker s = spawn(p, at(0, -5), 0f, true);
                    s.setTarget(p);
                    s.holdBack(100000);
                    return true;
                }))
                .run("look straight at it, just under its mask", () -> {
                    Vec3 mask = ask(s -> s.position().add(0, StalkerRules.MASK_Y - 0.6, 0));
                    stand(mc, 0, 0, mask);
                    samples.clear();
                    sampling = true;
                })
                .waitUntil("it is caught", 20, () -> ask(s -> s.serverState() == HollowStalker.State.CAUGHT))
                .waitTicks(3)
                .screenshot("stalker_caught")
                .waitUntil("the freeze ends", 20, () -> ask(s -> s.serverState() != HollowStalker.State.CAUGHT))
                .waitTicks(12)
                .run("stop recording", () -> sampling = false)
                .check("it froze for 10 ticks, still", () -> {
                    List<Sample> frozen = samples.stream().filter(s -> s.state().equals("CAUGHT")).toList();
                    if (frozen.size() < StalkerRules.CAUGHT_TICKS - 1 || frozen.size() > StalkerRules.CAUGHT_TICKS + 1) {
                        return false;
                    }
                    Vec3 first = frozen.get(0).at();
                    return frozen.stream().allMatch(s -> s.at().distanceTo(first) < 0.05);
                })
                .check("then it backed off into the dark", () -> samples.stream().anyMatch(s -> s.state().equals("FLEE")))
                .log("caught", () -> {
                    long frozen = samples.stream().filter(s -> s.state().equals("CAUGHT")).count();
                    String s = String.format(Locale.ROOT, "caught: frozen %d ticks, then %s", frozen,
                            samples.isEmpty() ? "?" : samples.get(samples.size() - 1).state());
                    summary.add(s);
                    return s;
                })
                .run("clear", StalkerScenario::clearStalkers)
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ Shadow Step

    private void shadowStep(Steps steps, Minecraft mc) {
        steps.run("the player in the middle, looking south", () -> stand(mc, 0, 0, at(0, 12).add(0, 1.6, 0)))
                .run("a Stalker 12 blocks south, its step ready", () -> ServerQuery.ask(p -> {
                    HollowStalker s = spawn(p, at(0, 12), 180f, true);
                    s.setTarget(p);
                    s.readyNow();
                    return true;
                }))
                .run("watch it", () -> {
                    Vec3 mask = ask(s -> s.position().add(0, StalkerRules.MASK_Y, 0));
                    stand(mc, 0, 0, mask);
                })
                .run("record", () -> {
                    samples.clear();
                    sampling = true;
                })
                .waitTicks(16)
                .check("watched, it never steps", () -> ask(s -> s.counts()[0] == 0))
                .run("turn away (north)", () -> stand(mc, 0, 0, at(0, -12).add(0, 1.6, 0)))
                .waitUntil("it steps", 60, () -> ask(s -> s.counts()[0] >= 1))
                .waitTicks(2)
                .run("stop recording", () -> sampling = false)
                .check("it landed behind the player, near", () -> ServerQuery.ask(p -> {
                    HollowStalker s = need(p);
                    return s.distanceTo(p) < 4.5 && s.getZ() > p.getZ();
                }))
                .check("in one tick it jumped more than 3 blocks", () -> {
                    for (int i = 1; i < samples.size(); i++) {
                        if (samples.get(i).at().distanceTo(samples.get(i - 1).at()) > 3.0) {
                            return true;
                        }
                    }
                    return false;
                })
                .log("step", () -> {
                    String s = ServerQuery.ask(p -> String.format(Locale.ROOT, "shadow step: %.1f blocks from the player after it, behind",
                            need(p).distanceTo(p)));
                    summary.add(s);
                    return s;
                })
                .run("clear", StalkerScenario::clearStalkers)
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ Rend

    private void rend(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach give meridian")
                .waitUntil("Meridian in hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .run("heal", this::heal)
                .run("the player looking south, a Stalker 2 blocks behind (north)", () -> {
                    stand(mc, 0, 0, at(0, 12).add(0, 1.6, 0));
                    ServerQuery.ask(p -> {
                        HollowStalker s = spawn(p, at(0, -2), 0f, true);
                        s.setTarget(p);
                        s.holdBack(100000);
                        return true;
                    });
                })
                .waitUntil("its Rend's glint (tick 16)", 120, () -> ask(s -> s.serverState() == HollowStalker.State.REND_TELL
                        && s.serverStateTicks() >= StalkerRules.REND_GLINT + 1))
                .run("health before", () -> healthBefore = mc.player.getHealth())
                .press(ModKeyMappings.PARRY)
                .waitUntil("the parried Stalker staggers", 12, () -> ask(s -> s.serverState() == HollowStalker.State.STAGGER))
                .waitTicks(2)
                .check("the parry took nothing", () -> ServerQuery.ask(p -> p.getHealth() >= healthBefore - 0.01f && Rift.stacks(p) == 0))
                .log("rend parried", () -> {
                    summary.add("rend: parried on the glint, the Stalker staggered, the player unhurt");
                    return "parried";
                })
                .run("health before the second", () -> healthBefore = ServerQuery.ask(p -> p.getHealth()))
                .waitUntil("its next Rend lands", 160, () -> ServerQuery.ask(p -> p.getHealth() < healthBefore - 0.5f))
                .waitTicks(2)
                .check("12 damage (Normal) and a stack of Rift", () -> ServerQuery.ask(p ->
                        Math.abs((healthBefore - p.getHealth()) - (float) StalkerRules.REND_DAMAGE) < 0.6f && Rift.stacks(p) == 1))
                .log("rend taken", () -> {
                    String s = ServerQuery.ask(p -> String.format(Locale.ROOT, "rend: taken for %.1f damage, Rift %d, armor now %.2f",
                            healthBefore - p.getHealth(), Rift.stacks(p), p.getAttributeValue(Attributes.ARMOR)));
                    summary.add(s);
                    return s;
                })
                .run("clear", StalkerScenario::clearStalkers)
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ Grasp

    private void grasp(Steps steps, Minecraft mc) {
        steps.command("item replace entity @s armor.chest with minecraft:iron_chestplate")
                .command("item replace entity @s armor.legs with minecraft:iron_leggings")
                .run("heal", this::heal)
                .run("the player looking south, a Stalker 1.8 blocks behind with its Grasp ready", () -> {
                    stand(mc, 0, 0, at(0, 12).add(0, 1.6, 0));
                    ServerQuery.ask(p -> {
                        HollowStalker s = spawn(p, at(0, -1.8), 0f, true);
                        s.setTarget(p);
                        s.readyNow();
                        return true;
                    });
                })
                .waitUntil("it grasps the player", 80, () -> ServerQuery.ask(StalkerGrasp::isHeld))
                .run("health at the grasp", () -> healthBefore = ServerQuery.ask(p -> p.getHealth()))
                .waitUntil("the client can't walk", 10, () -> mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1e-6)
                .waitUntil("the first squeeze", 20, () -> ServerQuery.ask(p -> p.getHealth() < healthBefore - 0.5f))
                .check("3 damage through iron armor", () -> ServerQuery.ask(p ->
                        Math.abs((healthBefore - p.getHealth()) - (float) StalkerRules.GRASP_DAMAGE) < 0.3f))
                .run("measure where the player is", () -> markAt = mc.player.position())
                .run("hold forward", () -> key(mc.options.keyUp, true))
                .press(ModKeyMappings.DASH)
                .waitUntil("the dash breaks the hold", 6, () -> !ServerQuery.ask(StalkerGrasp::isHeld))
                .waitTicks(10)
                .run("let go of forward", () -> key(mc.options.keyUp, false))
                .check("free: walking again, and away", () -> mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.05
                        && mc.player.position().distanceTo(markAt) > 2.0)
                .log("grasp", () -> {
                    String s = String.format(Locale.ROOT, "grasp: 3 damage through iron armor, pinned, a dash carried the player %.1f blocks free",
                            mc.player.position().distanceTo(markAt));
                    summary.add(s);
                    return s;
                })
                .command("clear @s minecraft:iron_chestplate")
                .command("clear @s minecraft:iron_leggings")
                .run("clear", StalkerScenario::clearStalkers)
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ an ability's light

    private void abilityLight(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach give binary_edges")
                .waitUntil("the Edges in hand", 60, () -> {
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getItem(i).is(ModItems.BINARY_EDGES.get())) {
                            mc.player.getInventory().selected = i;
                        }
                    }
                    return mc.player.getMainHandItem().is(ModItems.BINARY_EDGES.get());
                })
                .waitTicks(5)
                .command("cosmicbreach debug resonance 100")
                .run("heal", this::heal)
                .run("a Stalker 5 blocks south, looked at", () -> {
                    ServerQuery.ask(p -> {
                        HollowStalker s = spawn(p, at(0, 5), 180f, true);
                        s.setTarget(p);
                        s.holdBack(100000);
                        return true;
                    });
                })
                .waitTicks(2)
                .run("aim at its chest", () -> stand(mc, 0, 0, ask(s -> s.position().add(0, 1.3, 0))))
                .waitTicks(2)
                .run("counts before", () -> countsBefore = ask(HollowStalker::counts))
                .press(mc.options.keyUse)
                .waitUntil("the Tether struck it and left light at its feet", 30, () -> ServerQuery.ask(p -> {
                    HollowStalker s = need(p);
                    BlockPos feet = s.blockPosition();
                    return TempLights.isOurs(p.serverLevel(), feet) || TempLights.isOurs(p.serverLevel(), feet.above());
                }))
                .run("remember the light", () -> markAt = ask(s -> Vec3.atBottomCenterOf(s.blockPosition())))
                .waitTicks(2)
                .check("light 12 there", () -> ServerQuery.ask(p -> StalkerLight.block(p.level(), BlockPos.containing(markAt)) >= 12))
                .waitUntil("it leaves the light", 60, () -> ServerQuery.ask(p -> {
                    HollowStalker s = need(p);
                    return s.counts()[5] > countsBefore[5] && StalkerLight.block(p.level(), s.blockPosition()) < 12
                            && s.position().distanceTo(markAt) > 1.5;
                }))
                .waitUntil("the light goes out after 40 ticks", 70, () -> ServerQuery.ask(p ->
                        !TempLights.isOurs(p.serverLevel(), BlockPos.containing(markAt))
                                && !TempLights.isOurs(p.serverLevel(), BlockPos.containing(markAt).above())))
                .log("ability light", () -> {
                    summary.add("ability light: the Tether left light 12 at the Stalker's feet; it left the light, which went out after 40 ticks");
                    return "ok";
                })
                .run("clear", StalkerScenario::clearStalkers)
                .waitTicks(5);
    }

    // ------------------------------------------------------------------ drops and the crypt

    private void drops(Steps steps, Minecraft mc) {
        steps.run("kill a Stalker", () -> ServerQuery.ask(p -> {
                    HollowStalker s = spawn(p, at(3, 3), 0f, true);
                    s.hurt(p.damageSources().playerAttack(p), 1000f);
                    return true;
                }))
                .waitTicks(15)
                .check("it dropped Umbral Silk", () -> ServerQuery.ask(p -> {
                    int silk = 0;
                    for (ItemEntity item : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12.0))) {
                        if (item.getItem().is(ModMaterials.UMBRAL_SILK.get())) {
                            silk += item.getItem().getCount();
                        }
                    }
                    return silk >= 1 && silk <= 2;
                }))
                .command("kill @e[type=minecraft:item]")
                .log("drops", () -> {
                    String s = ServerQuery.ask(p -> {
                        ServerLevel level = p.serverLevel();
                        LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                                com.cosmicbreach.CosmicBreach.id("entities/hollow_stalker")));
                        HollowStalker dummy = Stalkers.HOLLOW_STALKER.get().create(level);
                        dummy.moveTo(p.getX(), p.getY(), p.getZ());
                        int n = 2000;
                        int silk = 0;
                        int nuggets = 0;
                        int shards = 0;
                        for (int i = 0; i < n; i++) {
                            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, dummy)
                                    .withParameter(LootContextParams.ORIGIN, p.position())
                                    .withParameter(LootContextParams.DAMAGE_SOURCE, p.damageSources().playerAttack(p))
                                    .create(LootContextParamSets.ENTITY);
                            for (ItemStack st : table.getRandomItems(params)) {
                                if (st.is(ModMaterials.UMBRAL_SILK.get())) {
                                    silk += st.getCount();
                                } else if (st.is(ModMaterials.ECLIPSIUM_NUGGET.get())) {
                                    nuggets++;
                                } else if (st.is(Stalkers.MASK_SHARD_ITEM.get())) {
                                    shards++;
                                }
                            }
                        }
                        return String.format(Locale.ROOT, "drops over %d kills: Umbral Silk %.2f a kill, Eclipsium Nugget %.1f%%, Mask Shard %.1f%%",
                                n, silk / (double) n, 100.0 * nuggets / n, 100.0 * shards / n);
                    });
                    summary.add(s);
                    return s;
                })
                .check("the drop rates are the GDD's", () -> {
                    String last = summary.get(summary.size() - 1);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Silk ([0-9.]+) a kill, Eclipsium Nugget ([0-9.]+)%, Mask Shard ([0-9.]+)%")
                            .matcher(last);
                    if (!m.find()) {
                        return false;
                    }
                    double silk = Double.parseDouble(m.group(1));
                    double nug = Double.parseDouble(m.group(2));
                    double shard = Double.parseDouble(m.group(3));
                    return silk > 1.4 && silk < 1.6 && nug > 16 && nug < 24 && shard > 3 && shard < 7;
                });
    }

    private void cryptSpot(Steps steps, Minecraft mc) {
        steps.run("a Hollow Crypt's Stalker spot 6 blocks off", () -> ServerQuery.ask(p -> {
                    p.serverLevel().setBlock(base.offset(6, 0, 6), CryptRegistry.STALKER_MARKER.get().defaultBlockState(), 3);
                    return true;
                }))
                .waitUntil("a Stalker steps out of it", 60, () -> ServerQuery.ask(p -> {
                    HollowStalker s = stalker(p);
                    return s != null && s.position().distanceTo(Vec3.atBottomCenterOf(base.offset(6, 0, 6))) < 2.0
                            && p.serverLevel().getBlockState(base.offset(6, 0, 6)).isAir();
                }))
                .log("crypt", () -> {
                    summary.add("crypt: a Stalker spot woke a Hollow Stalker and was gone");
                    return "ok";
                })
                .run("clear", StalkerScenario::clearStalkers);
    }
}
