package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.codex.LairMote;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianType;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Playtest 3: the Codex's lair locator points at the boss of the layer the player stands in, part {@code codex-layers}, with
 * the real keys (sneak and use the book). A lair is built by the debug commands in each of the first three layers (the
 * guardians asleep, nothing beaten, the Drift and the Deep attuned). The player then stands where the old rule went wrong:
 * straight above or below another layer's lair, so that lair is the nearest across (distance 0), and the mote must still
 * fly toward the lair of the layer they stand in:
 * <ul>
 *   <li>layer 1 (the Reach), over the Deep's lair: the Reach's;</li>
 *   <li>layer 2 (the Drift), under the Reach's lair: the Drift's;</li>
 *   <li>layer 3 (the Deep), under the Reach's lair: the Deep's;</li>
 *   <li>layer 1 with its own boss beaten: the next in the order of play (the Drift's);</li>
 *   <li>layer 4 (the Sanctum, in the Deep's band), with the first three beaten: the Sanctum.</li>
 * </ul>
 */
public final class CodexLayersScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private final BlockPos[] lairs = new BlockPos[3];

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    @Override
    public void steps(Steps steps) {
        steps.command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("effect give @s minecraft:resistance 100000 4 true");
        build(steps, "reach", "colossus", GuardianTypes.COLOSSUS, 0);
        build(steps, "drift", "leviathan", GuardianTypes.LEVIATHAN, 1);
        build(steps, "deep", "unsung", GuardianTypes.UNSUNG, 2);
        steps.check("the lairs are in their own layers", () -> Layer.at(lairs[0].getY()) == Layer.REACH
                        && Layer.at(lairs[1].getY()) == Layer.DRIFT && Layer.at(lairs[2].getY()) == Layer.DEEP)
                .log("lairs", () -> String.format(Locale.ROOT, "reach lair %s, drift lair %s, deep lair %s",
                        lairs[0].toShortString(), lairs[1].toShortString(), lairs[2].toShortString()))
                .command("clear @s")
                .command("give @s cosmicbreach:starfall_codex")
                .waitUntil("the Codex in the hotbar", 60, () -> slotOf() >= 0)
                .run("take it in hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[slotOf()].getKey()))
                .waitUntil("the Codex in hand", 20, () -> mc.player.getMainHandItem().is(OnboardingRegistry.STARFALL_CODEX.get()))
                .command("gamemode survival");

        locate(steps, "layer 1, over the deep lair", l -> new BlockPos(l[2].getX(), 400, l[2].getZ()), "colossus");
        locate(steps, "layer 2, under the reach lair", l -> new BlockPos(l[0].getX(), 230, l[0].getZ()), "leviathan");
        locate(steps, "layer 3, under the reach lair", l -> new BlockPos(l[0].getX(), 130, l[0].getZ()), "unsung");
        steps.command("advancement grant @s only cosmicbreach:guardian/refracted").waitTicks(5);
        locate(steps, "layer 1, its boss beaten", l -> new BlockPos(l[2].getX(), 400, l[2].getZ()), "leviathan");
        steps.command("advancement grant @s only cosmicbreach:guardian/moored")
                .command("advancement grant @s only cosmicbreach:guardian/unsung")
                .command("advancement grant @s only cosmicbreach:attunement/sanctum")
                .waitTicks(5);
        locate(steps, "layer 4, the first three beaten",
                l -> new BlockPos(SanctumArena.THRONE.getX(), SanctumArena.THRONE.getY() + 4, SanctumArena.THRONE.getZ() + 20), "sanctum");
        steps.log("results", () -> String.join(" | ", results));
    }

    /** Goes to {@code layer} and builds its guardian's lair with the debug command, then remembers where it is. */
    private void build(Steps steps, String layer, String lair, GuardianType type, int i) {
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto " + layer)
                .waitUntil("in Aetheria (" + layer + ")", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitTicks(40)
                .waitUntil("the view is drawn (" + layer + ")", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach debug lair " + lair)
                // a Rift takes the server several seconds to build, longer than one server query waits: a query that times out
                // while it builds is only "not yet"
                .waitUntil("the " + layer + " lair is known", 1200, () -> {
                    try {
                        return ServerQuery.ask(p -> {
                            var known = GuardianLairs.get(p.serverLevel()).all().values().stream()
                                    .filter(x -> x.guardian().equals(type.name())).findFirst();
                            known.ifPresent(x -> lairs[i] = x.arenaCentre());
                            return known.isPresent();
                        });
                    } catch (Steps.Failure busy) {
                        return false;
                    }
                });
    }

    /**
     * Stands at the spot {@code where} picks (on a glass floor with air above it), sneak-uses the Codex and checks the mote
     * flies toward {@code expected}.
     */
    private void locate(Steps steps, String label, Function<BlockPos[], BlockPos> where, String expected) {
        steps.run("stand at " + label, () -> ServerQuery.ask(p -> {
                    BlockPos at = where.apply(lairs);
                    stand(p, at);
                    LairMote.reset();
                    return true;
                }))
                .waitTicks(40)
                .waitUntil("standing (" + label + ")", 100, () -> mc.player.onGround())
                .check("in the layer meant (" + label + ")", () -> Layer.at(mc.player.getY()) == Layer.at(where.apply(lairs).getY()));
        steps.hold(mc.options.keyShift)
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitTicks(2)
                .release(mc.options.keyShift)
                .waitUntil("a mote flies (" + label + ")", 40, () -> ServerQuery.ask(p -> LairMote.live() > 0 && LairMote.lastTarget() != null))
                .log(label, () -> ServerQuery.ask(p -> {
                    LairMote.Candidate t = LairMote.lastTarget();
                    StringBuilder near = new StringBuilder();
                    String[] names = {"colossus", "leviathan", "unsung"};
                    for (int k = 0; k < 3; k++) {
                        near.append(String.format(Locale.ROOT, " %s %.0f", names[k],
                                Math.hypot(lairs[k].getX() - p.getX(), lairs[k].getZ() - p.getZ())));
                    }
                    String line = String.format(Locale.ROOT, "%s (Y %.0f, %s): the mote flew toward %s at %s; across to each lair:%s",
                            label, p.getY(), Layer.at(p.getY()), t.name(), t.centre().toShortString(), near);
                    results.add(line);
                    return line;
                }))
                .check("toward the " + expected + " (" + label + ")", () -> ServerQuery.ask(p -> expected.equals(LairMote.lastTarget().name())))
                .waitUntil("the mote fades (" + label + ")", 120, () -> ServerQuery.ask(p -> LairMote.live() == 0));
    }

    /** A 3 by 3 glass floor under {@code at} with air over it, and the player on it. */
    private static void stand(ServerPlayer p, BlockPos at) {
        ServerLevel level = p.serverLevel();
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.GLASS.defaultBlockState());
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), p.getYRot(), 0f);
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.fallDistance = 0;
    }

    private int slotOf() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(OnboardingRegistry.STARFALL_CODEX.get())) {
                return i;
            }
        }
        return -1;
    }
}
