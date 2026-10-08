package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.cosmicbreach.lift.RiftLift;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Playtest 3: things dropped into a Leviathan Rift come back (RiftItemReturn), part {@code rift-items}, in a Rift built by
 * the debug command with its Leviathan asleep. Three ways an item goes over: the player throws one off an outer rim with
 * the real drop key; one falls from just past another platform's rim; one already lies on the bowl's floor under a third.
 * Each must go well under the platforms (or start under the lift's catch line), then be set down on a platform's top within a couple of
 * seconds, and still lie there, once, three seconds later. The player is moved to a platform across the arena after each
 * drop so they cannot pick the item up before it is looked at.
 */
public final class RiftItemsScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    /** The item under watch (server entity id), the lowest Y it reached, and when it went over. */
    private int item = -1;
    private double lowest;
    private long startedAt;
    private @Nullable Vec3 restedAt;

    @Override
    public int timeBudgetSeconds() {
        return 420;
    }

    private static RiftLayout layout() {
        RiftLayout l = LeviathanScenario.layout();
        if (l == null) {
            throw new Steps.Failure("no Rift was built");
        }
        return l;
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
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug lair leviathan")
                .waitUntil("the Rift is built, its Leviathan asleep", 400, () -> LeviathanScenario.layout() != null
                        && ServerQuery.ask(p -> LeviathanScenario.lev(p) != null && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT))
                .run("onto platform 0", () -> toPlatform(0))
                .waitTicks(20);
        thrown(steps);
        fallen(steps);
        fromTheFloor(steps);
        steps.check("the Leviathan slept through it all", () -> ServerQuery.ask(p -> LeviathanScenario.lev(p) != null
                        && LeviathanScenario.lev(p).state() == ThalassineLeviathan.State.DORMANT))
                .log("results", () -> String.join(" | ", results));
    }

    /** The player throws a diamond off platform 3's outer rim with the drop key. */
    private void thrown(Steps steps) {
        int k = 3;
        steps.command("clear @s")
                .command("give @s minecraft:diamond 1")
                .waitUntil("the diamond in the hotbar", 60, () -> slotOf(Items.DIAMOND) >= 0)
                .run("take it in hand", () -> mc.player.getInventory().selected = slotOf(Items.DIAMOND))
                .run("onto platform " + k + "'s outer rim, facing out and a little up", () -> {
                    Vec3[] at = outerRim(k);
                    ColossusScenario.tp(mc, at[0].x, at[0].y, at[0].z, at[1].x, at[1].y, at[1].z);
                })
                .waitTicks(15)
                .check("standing on platform " + k, () -> onPlatform(mc.player.position(), 0.6, 1.5))
                .press(mc.options.keyDrop)
                .waitUntil("the diamond is in the air", 40, () -> {
                    item = ServerQuery.ask(p -> find(p.serverLevel(), Items.DIAMOND));
                    return item >= 0;
                })
                .check("the hand is empty", () -> slotOf(Items.DIAMOND) < 0)
                .run("start the watch, move the player across the arena", () -> {
                    lowest = Double.MAX_VALUE;
                    startedAt = mc.level.getGameTime();
                    toPlatform((k + 4) % 8);
                });
        watch(steps, "thrown off an outer rim", Items.DIAMOND);
    }

    /** An emerald let go 2 blocks past platform 5's outer rim, a block over its top: it falls on its own. */
    private void fallen(Steps steps) {
        int k = 5;
        steps.run("an emerald falls past platform " + k + "'s rim", () -> {
            item = ServerQuery.ask(p -> {
                RiftLayout l = layout();
                RiftLayout.Platform pl = l.platform(k);
                Vec3 c = l.centre();
                Vec3 out = new Vec3(pl.x() - c.x, 0, pl.z() - c.z).normalize();
                Vec3 at = new Vec3(pl.x(), RiftLift.standY(pl) + 1.0, pl.z()).add(out.scale(pl.radius() + 2.0));
                return spawn(p.serverLevel(), at, Items.EMERALD);
            });
            lowest = Double.MAX_VALUE;
            startedAt = mc.level.getGameTime();
        });
        watch(steps, "fell past a rim", Items.EMERALD);
    }

    /** A gold ingot lying on the bowl's floor under platform 6, where the lift catches a player who jumps. */
    private void fromTheFloor(Steps steps) {
        int k = 6;
        steps.run("a gold ingot lies on the bowl's floor under platform " + k, () -> {
            item = ServerQuery.ask(p -> {
                Vec3 floor = bowlFloor(p.serverLevel(), k);
                return floor == null ? -2 : spawn(p.serverLevel(), floor.add(0, 0.1, 0), Items.GOLD_INGOT);
            });
            if (item == -2) {
                throw new Steps.Failure("no open floor in the bowl under platform " + k);
            }
            lowest = Double.MAX_VALUE;
            startedAt = mc.level.getGameTime();
        });
        watch(steps, "on the bowl's floor", Items.GOLD_INGOT);
    }

    /** The checks every item goes through: under the catch line, then set down on a platform, then still there, once. */
    private void watch(Steps steps, String label, Item kind) {
        long[] backAt = {0};
        steps.waitUntil("it is set down on a platform's top (" + label + ")", 200, () -> {
                    Vec3 at = ServerQuery.ask(p -> {
                        Entity e = p.serverLevel().getEntity(item);
                        if (!(e instanceof ItemEntity ie) || !ie.isAlive()) {
                            return null;
                        }
                        return ie.onGround() ? ie.position().add(0, 1000, 0) : ie.position();
                    });
                    if (at == null) {
                        throw new Steps.Failure("the item is gone (" + label + ")");
                    }
                    boolean grounded = at.y > 500;
                    Vec3 pos = grounded ? at.add(0, -1000, 0) : at;
                    lowest = Math.min(lowest, pos.y);
                    if (grounded && onPlatform(pos, 0.6, 3.0)) {
                        restedAt = pos;
                        backAt[0] = mc.level.getGameTime();
                        return true;
                    }
                    return false;
                })
                // the sweep runs every 10 ticks and may take it on the very tick it crosses the line, between two looks
                // from here, so the check asks for well under the rim; the log gives the depth seen
                .check("it went well under the platforms first (" + label + ")", () -> lowest < nearestStandY(restedAt) - 3.0)
                .log(label, () -> {
                    String line = String.format(Locale.ROOT, "%s: back on a platform after %d ticks, lowest %.1f blocks under it, set down at %.1f %.1f %.1f",
                            label, backAt[0] - startedAt, nearestStandY(restedAt) - lowest, restedAt.x, restedAt.y, restedAt.z);
                    results.add(line);
                    return line;
                })
                .waitTicks(60)
                .check("it still lies there, once (" + label + ")", () -> ServerQuery.ask(p -> {
                    Entity e = p.serverLevel().getEntity(item);
                    long copies = p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(200),
                            ie -> ie.getItem().is(kind)).size();
                    return e instanceof ItemEntity ie && ie.isAlive() && ie.position().distanceTo(restedAt) < 0.6 && copies == 1;
                }))
                .run("tidy it away", () -> ServerQuery.ask(p -> {
                    Entity e = p.serverLevel().getEntity(item);
                    if (e != null) {
                        e.discard();
                    }
                    return true;
                }));
    }

    // ------------------------------------------------------------------ helpers

    private void toPlatform(int k) {
        RiftLayout l = layout();
        Vec3 at = l.platform(k).topCentre();
        ColossusScenario.tp(mc, at.x, at.y, at.z, l.centre().x, at.y + 1, l.centre().z);
    }

    /** True if {@code pos} is on a platform's top: inside its radius (plus {@code slack}) and up to {@code over} above it. */
    private static boolean onPlatform(Vec3 pos, double under, double over) {
        for (RiftLayout.Platform p : layout().platforms()) {
            double up = pos.y - RiftLift.standY(p);
            if (Math.hypot(pos.x - p.x(), pos.z - p.z()) <= p.radius() + 0.7 && up >= -under && up <= over) {
                return true;
            }
        }
        return false;
    }

    private static double nearestStandY(@Nullable Vec3 pos) {
        if (pos == null) {
            return Double.NaN;
        }
        RiftLayout.Platform best = null;
        double bestD = Double.MAX_VALUE;
        for (RiftLayout.Platform p : layout().platforms()) {
            double d = Math.hypot(pos.x - p.x(), pos.z - p.z());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return RiftLift.standY(best);
    }

    /** Where to stand on platform {@code k}'s outer rim (a block and a bit in from the edge) and a point out and up to look at. */
    private Vec3[] outerRim(int k) {
        RiftLayout l = layout();
        RiftLayout.Platform p = l.platform(k);
        Vec3 c = l.centre();
        double middle = Math.atan2(p.z() - c.z, p.x() - c.x);
        double stand = RiftLift.standY(p);
        Vec3 found = ServerQuery.ask(player -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < 13; i++) {
                double angle = middle + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.15;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                double r = p.radius() - 1.2;
                int bx = (int) Math.floor(p.x() + dx * r);
                int bz = (int) Math.floor(p.z() + dz * r);
                boolean floor = solid(level, bx, (int) stand - 1, bz);
                boolean open = !solid(level, bx, (int) stand, bz) && !solid(level, bx, (int) stand + 1, bz);
                // nothing in the throw's way for 4 blocks out at head height
                for (double d = r + 0.5; d <= p.radius() + 4.0 && open; d += 0.5) {
                    open = !solid(level, (int) Math.floor(p.x() + dx * d), (int) stand + 1, (int) Math.floor(p.z() + dz * d))
                            && !solid(level, (int) Math.floor(p.x() + dx * d), (int) stand + 2, (int) Math.floor(p.z() + dz * d));
                }
                if (floor && open) {
                    return new Vec3(p.x() + dx * r, stand, p.z() + dz * r);
                }
            }
            return null;
        });
        if (found == null) {
            throw new Steps.Failure("no clear outer rim on platform " + k);
        }
        Vec3 dir = found.subtract(new Vec3(p.x(), stand, p.z())).normalize();
        return new Vec3[] {found, found.add(dir.scale(10)).add(0, 1.6 + 3.0, 0)};
    }

    /** The open floor of the bowl under platform {@code k}, 20 blocks out from the core, on the server's real blocks. */
    private static @Nullable Vec3 bowlFloor(ServerLevel level, int k) {
        RiftLayout l = layout();
        RiftLayout.Platform p = l.platform(k);
        Vec3 c = l.centre();
        for (int i = 0; i < 24; i++) {
            double a = p.angle() + 0.1 + i * 0.15;
            double r = 20.0;
            double x = c.x + r * Math.cos(a);
            double z = c.z + r * Math.sin(a);
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            for (int y = (int) Math.ceil(l.bowlSurface(r)) + 14; y >= (int) Math.floor(l.bowlSurface(r)) - 6; y--) {
                if (solid(level, bx, y, bz) && !solid(level, bx, y + 1, bz) && !solid(level, bx, y + 2, bz)) {
                    Vec3 feet = new Vec3(x, y + 1.0, z);
                    RiftLayout.Platform near = l.nearestPlatform(Math.atan2(z - c.z, x - c.x));
                    if (RiftLift.standY(near) - feet.y > RiftLift.CATCH_BELOW + 1.0) {
                        return feet;
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static boolean solid(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static int spawn(ServerLevel level, Vec3 at, Item kind) {
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(kind));
        e.setDeltaMovement(Vec3.ZERO);
        e.setPickUpDelay(40);
        level.addFreshEntity(e);
        return e.getId();
    }

    private static int find(ServerLevel level, Item kind) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ItemEntity ie && ie.isAlive() && ie.getItem().is(kind)) {
                return ie.getId();
            }
        }
        return -1;
    }

    private int slotOf(Item kind) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(kind)) {
                return i;
            }
        }
        return -1;
    }
}
