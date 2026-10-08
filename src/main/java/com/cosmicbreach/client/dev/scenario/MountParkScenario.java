package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Playtest 3: a stingray stays where its rider leaves it, on every layer, part {@code mount-park}, with real inputs (use to
 * mount, forward to fly, sneak to get off). Three test rocks stand in open air one above the other: in the Reach, the Drift
 * and the Deep. A tamed, harnessed stingray first lives in the Drift (so it remembers a safe spot there, as a played one
 * does); then:
 * <ul>
 *   <li>the Drift: flown out over open air and left there, it hovers in place;</li>
 *   <li>the Deep (the playtest's case): ridden down onto the Deep rock and left standing there, it stays put and does not
 *       climb back to its safe spot in the Drift; flown out over the Deep's open air and left, it hovers in place, neither
 *       climbing nor gliding down toward the void;</li>
 *   <li>the Reach: flown out over open air and left, it hovers in place instead of gliding down.</li>
 * </ul>
 * Each wait is 10 seconds; "in place" is under 2 blocks of drift after it has settled.
 */
public final class MountParkScenario implements Scenario {
    private static final int DRIFT_Y = 230;
    private static final int DEEP_Y = 120;
    private static final int REACH_Y = 400;
    private static final int WATCH_TICKS = 200;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private int x;
    private int z;
    private int manta = -1;
    private @Nullable Vec3 settledAt;
    private double maxY;
    private double minY;

    @Override
    public int timeBudgetSeconds() {
        return 480;
    }

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode creative")
                .command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .run("where the rocks go", () -> {
                    BlockPos at = ServerQuery.ask(ServerPlayer::blockPosition);
                    x = at.getX();
                    z = at.getZ();
                });
        for (int y : new int[] {DRIFT_Y, DEEP_Y, REACH_Y}) {
            steps.run("build the rock at Y " + y + " with open air round it and to the east", () -> {
                command("fill %d %d %d %d %d %d minecraft:air", x - 8, y - 1, z - 8, x + 8, y + 12, z + 8);
                command("fill %d %d %d %d %d %d minecraft:air", x + 7, y - 6, z - 5, x + 30, y + 8, z + 5);
                command("fill %d %d %d %d %d %d cosmicbreach:driftstone", x - 6, y - 1, z - 6, x + 6, y - 1, z + 6);
            }).waitUntil("the rock at Y " + y + " stands", 100,
                    () -> mc.level.getBlockState(new BlockPos(x, y - 1, z)).is(ModBlocks.DRIFTSTONE.get()));
        }
        steps.check("the rocks are in the layers they stand for", () -> Layer.at(DRIFT_Y) == Layer.DRIFT && Layer.at(DEEP_Y) == Layer.DEEP
                        && Layer.at(REACH_Y) == Layer.REACH)
                .run("onto the Drift rock", () -> onRock(DRIFT_Y))
                .waitTicks(20)
                .command("clear @s")
                .run("a tamed, harnessed stingray beside the player", () -> manta = ServerQuery.ask(p -> {
                    DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
                    m.moveTo(x + 2.5, DRIFT_Y + 0.5, z + 0.5, 0f, 0f);
                    m.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
                    m.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(m);
                    m.tameTo(p);
                    m.equipSaddle(new ItemStack(Mounts.DRIFT_HARNESS.get()), SoundSource.NEUTRAL);
                    return m.getId();
                }))
                .waitUntil("it remembers a safe spot in the Drift", 200, () -> ServerQuery.ask(p -> {
                    Vec3 safe = server(p).lastSafe();
                    return safe != null && Layer.at(safe.y) == Layer.DRIFT;
                }));

        // the Drift: out over open air and left there
        mount(steps, "Drift");
        flyOut(steps, "Drift");
        leaveInTheAir(steps, DRIFT_Y);
        watch(steps, "Drift, left in the air", 2.0);

        // the Deep: ridden down onto the rock and left standing (the playtest's case), then out over the open air
        bring(steps, DRIFT_Y);
        mount(steps, "Drift again");
        steps.run("rider and stingray go down onto the Deep rock together", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p);
                    m.teleportTo(x + 2.5, DEEP_Y + 0.2, z + 0.5);
                    p.connection.send(new ClientboundMoveVehiclePacket(m));
                    return true;
                }))
                .waitUntil("down in the Deep, still riding", 100, () -> mc.player.getY() < 160.0 && mc.player.getVehicle() instanceof DriftManta)
                .waitTicks(30)
                .check("it still remembers its safe spot up in the Drift (so the old rescue would fire)", () -> ServerQuery.ask(p -> {
                    Vec3 safe = server(p).lastSafe();
                    return safe != null && Layer.at(safe.y) == Layer.DRIFT;
                }))
                .press(mc.options.keyShift, 5)
                .waitUntil("off it, standing on the Deep rock", 60, () -> !mc.player.isPassenger())
                .check("its rider left it below its layer", () -> ServerQuery.ask(p -> server(p).leftBelowByRider()));
        watch(steps, "Deep, left on the rock", 2.0);
        mount(steps, "Deep");
        flyOut(steps, "Deep");
        leaveInTheAir(steps, DEEP_Y);
        watch(steps, "Deep, left in the air", 2.0);

        // the Reach: out over open air and left there
        steps.command("gamemode creative")
                .run("up onto the Reach rock", () -> onRock(REACH_Y))
                .waitTicks(20);
        bring(steps, REACH_Y);
        mount(steps, "Reach");
        flyOut(steps, "Reach");
        leaveInTheAir(steps, REACH_Y);
        watch(steps, "Reach, left in the air", 2.0);

        steps.log("results", () -> String.join(" | ", results));
    }

    /** Gets on with the real keys: survival, an empty hand, aim, use. */
    private void mount(Steps steps, String where) {
        steps.command("gamemode survival")
                .waitUntil("in survival (" + where + ")", 40, () -> !mc.player.isCreative())
                .run("an empty hand", () -> {
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getItem(i).isEmpty()) {
                            mc.player.getInventory().selected = i;
                            return;
                        }
                    }
                    throw new Steps.Failure("no empty hotbar slot");
                })
                .waitUntil("the stingray within reach (" + where + ")", 100, () -> reach() <= 3.0)
                .run("aim at it", () -> {
                    Entity e = client();
                    if (e == null) {
                        throw new Steps.Failure("the stingray is not loaded here");
                    }
                    CryptKit.aim(mc, e.getBoundingBox().getCenter());
                })
                .press(mc.options.keyUse)
                .waitUntil("riding it (" + where + ")", 40, () -> mc.player.getVehicle() instanceof DriftManta);
    }

    /** Flies east, level, off the rock and out over the open air for a second and a half. */
    private void flyOut(Steps steps, String where) {
        steps.look(-90f, 0f)
                .hold(mc.options.keyUp)
                .waitTicks(30)
                .release(mc.options.keyUp)
                .waitTicks(10)
                .check("out over open air past the rock (" + where + ")", () -> mc.player.getX() > x + 7.5);
    }

    /** Gets off in the air (creative, so the player can be put back on the rock), and goes back onto the rock. */
    private void leaveInTheAir(Steps steps, int rockY) {
        steps.command("gamemode creative")
                .waitUntil("in creative", 40, () -> mc.player.isCreative())
                .press(mc.options.keyShift, 5)
                .waitUntil("off it", 60, () -> !mc.player.isPassenger())
                .run("back onto the rock", () -> onRock(rockY));
    }

    /**
     * Lets the stingray settle for a second, then watches it for {@value #WATCH_TICKS} ticks: it must stay within {@code most}
     * blocks of where it settled, rise or sink no more than that, and never start a rescue.
     */
    private void watch(Steps steps, String label, double most) {
        boolean[] rescued = {false};
        steps.waitTicks(20)
                .run("where it settled (" + label + ")", () -> {
                    settledAt = ServerQuery.ask(p -> server(p).position());
                    maxY = settledAt.y;
                    minY = settledAt.y;
                    rescued[0] = false;
                });
        for (int i = 0; i < WATCH_TICKS / 10; i++) {
            steps.waitTicks(10).run("look at it", () -> {
                boolean[] r = {false};
                Vec3 at = ServerQuery.ask(p -> {
                    DriftManta m = server(p);
                    r[0] = m.isRescuing();
                    return m.position();
                });
                rescued[0] |= r[0];
                maxY = Math.max(maxY, at.y);
                minY = Math.min(minY, at.y);
            });
        }
        steps.log(label, () -> {
                    Vec3 now = ServerQuery.ask(p -> server(p).position());
                    String line = String.format(Locale.ROOT, "%s: moved %.2f blocks in %d ticks (Y %.1f to %.1f, settled at %.1f), rescue %s",
                            label, now.distanceTo(settledAt), WATCH_TICKS, minY, maxY, settledAt.y, rescued[0]);
                    results.add(line);
                    return line;
                })
                .check("it stayed where it was left (" + label + ")", () -> ServerQuery.ask(p -> server(p).position()).distanceTo(settledAt) <= most
                        && maxY - settledAt.y <= most && settledAt.y - minY <= most)
                .check("no rescue started (" + label + ")", () -> !rescued[0]);
    }

    /** Brings the stingray (left out in the air) back beside the player on the rock at {@code rockY}. */
    private void bring(Steps steps, int rockY) {
        steps.run("bring the stingray beside the player", () -> ServerQuery.ask(p -> {
                    server(p).teleportTo(x + 2.5, rockY + 0.5, z + 0.5);
                    return true;
                }))
                .waitTicks(20);
    }

    private void onRock(int rockY) {
        ColossusScenario.tp(mc, x + 0.5, rockY, z + 0.5, x + 10.0, rockY + 1, z + 0.5);
    }

    private DriftManta server(ServerPlayer p) {
        Entity e = p.serverLevel().getEntity(manta);
        if (!(e instanceof DriftManta m)) {
            throw new Steps.Failure("no stingray " + manta + " on the server");
        }
        return m;
    }

    private @Nullable DriftManta client() {
        Entity e = mc.level == null ? null : mc.level.getEntity(manta);
        return e instanceof DriftManta m ? m : null;
    }

    private double reach() {
        Entity e = client();
        if (e == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eye = mc.player.getEyePosition();
        var b = e.getBoundingBox();
        double dx = Math.max(0, Math.max(b.minX - eye.x, eye.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - eye.y, eye.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - eye.z, eye.z - b.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void command(String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }
}
