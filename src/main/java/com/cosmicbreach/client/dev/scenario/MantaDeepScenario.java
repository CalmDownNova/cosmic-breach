package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.MantaRules;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.ShearBand;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;

/**
 * Lane A, the manta in the Deep (1.2 design section 5), with the real keys: a tamed, harnessed manta on a test rock in
 * the Deep is mounted (use), and flown with forward, jump and looking up. It climbs on layer 3 (it used to only glide
 * there), and is stopped by the soft ceiling under the lower Shear band: it never reaches the band, and a dive takes it
 * down again. The 1.1.1 parking behaviour is {@code mount-park}'s to check.
 */
public final class MantaDeepScenario implements Scenario {
    private static final int ROCK_Y = 90;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Double> trace = new ArrayList<>();
    private int x;
    private int z;
    private int manta = -1;

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    private double y() {
        return mc.player.getVehicle() != null ? mc.player.getVehicle().getY() : mc.player.getY();
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
                .run("where the rock goes", () -> {
                    BlockPos at = ServerQuery.ask(ServerPlayer::blockPosition);
                    x = at.getX();
                    z = at.getZ();
                })
                .run("a rock in the Deep and open air above it up past the ceiling", () -> {
                    command("fill %d %d %d %d %d %d minecraft:air", x - 5, ROCK_Y - 1, z - 5, x + 5, 160, z + 5);
                    command("fill %d %d %d %d %d %d cosmicbreach:driftstone", x - 5, ROCK_Y - 1, z - 5, x + 5, ROCK_Y - 1, z + 5);
                })
                .waitUntil("the rock stands", 100, () -> mc.level.getBlockState(new BlockPos(x, ROCK_Y - 1, z)).is(ModBlocks.DRIFTSTONE.get()))
                .check("the rock is in the Deep", () -> Layer.at(ROCK_Y) == Layer.DEEP)
                .run("onto the rock", () -> ColossusScenario.tp(mc, x + 0.5, ROCK_Y, z + 0.5, x + 10.0, ROCK_Y + 1, z + 0.5))
                .waitTicks(20)
                .command("clear @s")
                .run("a tamed, harnessed manta beside the player", () -> manta = ServerQuery.ask(p -> {
                    DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
                    m.moveTo(x + 2.5, ROCK_Y + 0.5, z + 0.5, 0f, 0f);
                    m.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
                    m.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(m);
                    m.tameTo(p);
                    m.equipSaddle(new ItemStack(Mounts.DRIFT_HARNESS.get()), SoundSource.NEUTRAL);
                    return m.getId();
                }))
                .waitTicks(10)
                .command("gamemode survival")
                .waitUntil("survival", 40, () -> !mc.player.isCreative())
                .run("an empty hand", () -> mc.player.getInventory().selected = 8)
                .waitUntil("the manta within reach", 100, () -> reach() <= 3.0)
                .run("aim at it", () -> CryptKit.aim(mc, client().getBoundingBox().getCenter()))
                .press(mc.options.keyUse)
                .waitUntil("riding it", 40, () -> mc.player.getVehicle() instanceof DriftManta)
                .check("it flies here: the Deep is a flying layer", () -> client() != null && ((DriftManta) client()).canFly())

                // climb on layer 3
                .run("look up, forward and jump", () -> {
                    trace.clear();
                    CryptKit.set(mc.options.keyJump, true);
                })
                .look(-90f, -35f)
                .run("forward", () -> CryptKit.set(mc.options.keyUp, true))
                .waitUntil("it has climbed 20 blocks on layer 3", 200, () -> {
                    trace.add(y());
                    return y() > ROCK_Y + 20;
                })
                .check("it never left the Deep on the way", () -> Layer.at(y()) == Layer.DEEP)
                .check("it climbed (the old glider only sank)", () -> y() > ROCK_Y + 20)

                // blocked at the ceiling
                .run("keep climbing", () -> {
                    CryptKit.set(mc.options.keyJump, true);
                    CryptKit.set(mc.options.keyUp, false);
                })
                .look(-90f, -90f)
                .waitUntil("it has run out of room", 600, () -> {
                    trace.add(y());
                    int n = trace.size();
                    return n > 80 && Math.abs(trace.get(n - 1) - trace.get(n - 40)) < 0.05;
                })
                .waitTicks(60)
                .run("record the top", () -> trace.add(y()))
                .log("ceiling", () -> String.format(Locale.ROOT, "the manta stopped at Y %.2f; the ceiling is %.1f and the lower Shear band starts at %d", y(),
                        MantaRules.DEEP_CEILING, ShearBand.B.minY))
                .check("it stopped at the soft ceiling", () -> y() <= MantaRules.DEEP_CEILING + 0.6 && y() >= MantaRules.DEEP_CEILING - 3.0)
                .check("it never entered the Shear band", () -> trace.stream().allMatch(v -> v < ShearBand.B.minY))
                .check("it is still on layer 3", () -> Layer.at(y()) == Layer.DEEP)
                .check("it is not past the ceiling after a long hold", () -> {
                    double max = trace.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                    return max <= MantaRules.DEEP_CEILING + 0.6;
                })

                // and down again
                .run("dive: look down, forward, no jump", () -> {
                    CryptKit.set(mc.options.keyJump, false);
                    CryptKit.set(mc.options.keyUp, true);
                })
                .look(-90f, 60f)
                .waitUntil("it dives", 100, () -> y() < MantaRules.DEEP_CEILING - 15)
                .run("let go", () -> CryptKit.releaseAll(mc))
                .check("a dive takes it down: it flies both ways", () -> y() < MantaRules.DEEP_CEILING - 15)
                .run("summary", () -> com.cosmicbreach.CosmicBreach.LOGGER.info(
                        "[autotest] manta in the Deep: climbed from Y {} to the ceiling at {}, never into the band, dived back", ROCK_Y, MantaRules.DEEP_CEILING));
    }

    private Entity client() {
        return mc.level == null ? null : mc.level.getEntity(manta);
    }

    private double reach() {
        Entity e = client();
        if (e == null) {
            return Double.MAX_VALUE;
        }
        var eye = mc.player.getEyePosition();
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
