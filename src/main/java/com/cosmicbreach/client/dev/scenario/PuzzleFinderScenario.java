package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.codex.LairMote;
import com.cosmicbreach.codex.PuzzleFinder;
import com.cosmicbreach.codex.PuzzleRoomLog;
import com.cosmicbreach.codex.PuzzleRooms;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;

/**
 * Lane A, the Codex's puzzle finder (the puzzle-finder branch, merged into 1.1.2): in the Reach, sneak-using the Codex
 * throws a mote at the boss; using it again while the mote flies switches the book to the nearest puzzle room of the layer
 * and throws at that; again, back to the boss. With every puzzle room on the layer done there is nothing to find: the
 * book falls back to the boss. Real keys; the book's target is read from the server.
 */
public final class PuzzleFinderScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    @Override
    public boolean generateStructures() {
        return true;
    }

    private static void sneakUse(Steps steps, Minecraft mc) {
        steps.hold(mc.options.keyShift)
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitTicks(2)
                .release(mc.options.keyShift);
    }

    private static int slotOf(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(OnboardingRegistry.STARFALL_CODEX.get())) {
                return i;
            }
        }
        return -1;
    }

    private PuzzleRooms.Target target() {
        return ServerQuery.ask(p -> LairMote.target(p.getUUID()));
    }

    private String aimed() {
        return ServerQuery.ask(p -> LairMote.lastTarget() == null ? "none" : LairMote.lastTarget().name());
    }

    @Override
    public void steps(Steps steps) {
        KeyMapping use = mc.options.keyUse;
        steps.command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 400, () -> AetheriaWorld.is(mc.level))
                .waitTicks(60)
                .command("clear @s")
                .command("give @s cosmicbreach:starfall_codex")
                .waitUntil("the Codex in the hotbar", 60, () -> slotOf(mc) >= 0)
                .run("take it in hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[slotOf(mc)].getKey()))
                .waitUntil("the Codex in hand", 20, () -> mc.player.getMainHandItem().is(OnboardingRegistry.STARFALL_CODEX.get()))
                .run("reset the locator", () -> ServerQuery.ask(p -> {
                    LairMote.reset();
                    return true;
                }));

        // 1. the first use throws at the boss
        sneakUse(steps, mc);
        steps.waitUntil("a mote flies", 40, () -> ServerQuery.ask(p -> LairMote.live() > 0))
                .check("the book points at the boss first", () -> target() == PuzzleRooms.Target.BOSS && aimed().equals("colossus"));

        // 2. again while it flies: the nearest puzzle room. Wait past the hold gap first: a use within 6 server ticks of
        // the last is a held button, and the mote can be seen flying sooner than that
        steps.waitTicks(PuzzleRooms.HOLD_GAP + 2);
        sneakUse(steps, mc);
        steps.waitTicks(3)
                .check("the book switched to the puzzle rooms", () -> target() == PuzzleRooms.Target.PUZZLE)
                .check("the new mote points at a puzzle room of this layer", () -> aimed().startsWith("puzzle_"))
                .log("puzzle", () -> "switched: the mote points at " + aimed() + " "
                        + ServerQuery.ask(p -> LairMote.lastTarget() == null ? "" : LairMote.lastTarget().centre().toShortString()))
                .check("and that room is the nearest one the finder knows", () -> ServerQuery.ask(p -> {
                    Optional<PuzzleRooms.Room> room = PuzzleFinder.nearest((ServerLevel) p.level(), p);
                    return room.isPresent() && room.get().at().equals(LairMote.lastTarget().centre());
                }));

        // 3. and again: back to the boss
        steps.waitTicks(10);
        sneakUse(steps, mc);
        steps.waitTicks(3)
                .check("a third use turns it back to the boss", () -> target() == PuzzleRooms.Target.BOSS && aimed().equals("colossus"))
                .waitUntil("the mote fades", 140, () -> ServerQuery.ask(p -> LairMote.live() == 0))
                .waitTicks(45);

        // 4. every puzzle room done: a book turned to the puzzle rooms falls back to the boss
        steps.run("do every puzzle room on this layer", () -> {
                    int rooms = ServerQuery.ask(p -> {
                        ServerLevel level = (ServerLevel) p.level();
                        int n = 0;
                        for (int guard = 0; guard < 400; guard++) {
                            Optional<PuzzleRooms.Room> room = PuzzleFinder.nearest(level, p);
                            if (room.isEmpty()) {
                                break;
                            }
                            PuzzleRoomLog.get(level).opened(room.get().at(), room.get().kind(), p.getUUID());
                            n++;
                        }
                        LairMote.setTarget(p.getUUID(), PuzzleRooms.Target.PUZZLE);
                        return n;
                    });
                    com.cosmicbreach.CosmicBreach.LOGGER.info("[autotest] marked {} puzzle rooms done", rooms);
                })
                .check("the finder has nothing left on this layer", () -> ServerQuery.ask(p -> PuzzleFinder.nearest((ServerLevel) p.level(), p).isEmpty()));
        sneakUse(steps, mc);
        steps.waitUntil("a mote flies", 40, () -> ServerQuery.ask(p -> LairMote.live() > 0))
                .check("with no puzzle room left the book is back on the boss", () -> target() == PuzzleRooms.Target.BOSS && aimed().equals("colossus"))

                // the worst case: a cold cache and every room on the layer done, so the whole search radius is walked once
                .run("time the worst case on each layer", () -> {
                    for (int y : new int[] {400, 230, 80}) {
                        String line = ServerQuery.ask(p -> {
                            ServerLevel level = (ServerLevel) p.level();
                            double oy = p.getY();
                            p.teleportTo(p.getX(), y, p.getZ());
                            for (int guard = 0; guard < 400; guard++) {
                                Optional<PuzzleRooms.Room> room = PuzzleFinder.nearest(level, p);
                                if (room.isEmpty()) {
                                    break;
                                }
                                PuzzleRoomLog.get(level).opened(room.get().at(), room.get().kind(), p.getUUID());
                            }
                            PuzzleFinder.reset();
                            long t0 = System.nanoTime();
                            Optional<PuzzleRooms.Room> none = PuzzleFinder.nearest(level, p);
                            long cold = System.nanoTime() - t0;
                            long t1 = System.nanoTime();
                            PuzzleFinder.nearest(level, p);
                            long warm = System.nanoTime() - t1;
                            p.teleportTo(p.getX(), oy, p.getZ());
                            return String.format(Locale.ROOT, "layer at Y %d: cold full search %.1f ms (found %s), warm %.2f ms", y, cold / 1e6, none.isPresent(), warm / 1e6);
                        });
                        com.cosmicbreach.CosmicBreach.LOGGER.info("[autotest] puzzle finder cost: {}", line);
                    }
                })
                .run("summary", () -> com.cosmicbreach.CosmicBreach.LOGGER.info(
                        String.format(Locale.ROOT, "[autotest] puzzle finder: boss, switch to a puzzle room, back, and the fallback all behaved")));
    }
}
