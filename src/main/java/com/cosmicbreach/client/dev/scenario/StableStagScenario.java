package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.Stable;
import com.cosmicbreach.mount.StableCrystalItem;
import com.cosmicbreach.mount.StagRules;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mount care for a mount that cannot fly (quality review, Important 3), part {@code stable-stag}, on level 1 on a 13 by 13
 * test rock built in open air, with real inputs. A stag put down in the air falls, climbs back and falls again for ever,
 * so: a stowed stag is not set down against the rock's side (it would hang in the air; the crystal keeps it and says so)
 * but is set down on the rock's top, where it stands; a stag with no remembered spot, dropped below level 1 while its
 * owner stands at the rock's edge, ends on solid ground beside the owner and stays (the spot two blocks toward the stag
 * would be over the edge); and a stag whose remembered spot has lost its ground (a hole under it) ends on the ground
 * beside the hole and stays. The sections note an expectation that fails and go on, so one run shows every one that
 * does; the run fails on them at the end.
 */
public final class StableStagScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    /** The block players stand in is {@code rock}; the rock itself is one below. */
    private BlockPos rock = BlockPos.ZERO;
    private int stag = -1;

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    private @Nullable LumenStag server(ServerPlayer p) {
        return p.serverLevel().getEntity(stag) instanceof LumenStag s ? s : null;
    }

    private void select(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        throw new Steps.Failure("no " + item + " in the hotbar");
    }

    private void command(String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }

    private void expect(String what, boolean ok) {
        if (!ok) {
            failures.add(what);
        }
    }

    private void softWait(Steps steps, String what, int ticks, BooleanSupplier condition) {
        int[] waited = {0};
        steps.waitUntil(what, ticks + 20, () -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (++waited[0] > ticks) {
                failures.add("timed out after " + ticks + " ticks waiting until " + what);
                return true;
            }
            return false;
        });
    }

    @Override
    public void steps(Steps steps) {
        setUp(steps);
        releaseNeedsGround(steps);
        ownersSideOnGround(steps);
        spotWithoutGround(steps);
        steps.log("results", () -> String.join(" | ", results));
        steps.log("expectations that failed", () -> failures.isEmpty() ? "none" : String.join(" | ", failures));
        steps.check("every expectation held", failures::isEmpty);
    }

    private void setUp(Steps steps) {
        steps.command("gamemode creative")
                .command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("cosmicbreach debug goto sunfield")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                // the rock hangs 20 above wherever the player arrived, kept inside level 1 (above the gap, where the stag
                // is safe) with open air round it, a way out to the east and a shaft down beside it into the gap
                .run("build the rock, the air round it and the shaft", () -> {
                    BlockPos at = ServerQuery.ask(p -> p.blockPosition());
                    rock = new BlockPos(at.getX(), Mth.clamp(at.getY() + 20, 345, 440), at.getZ());
                    int x = rock.getX();
                    int y = rock.getY();
                    int z = rock.getZ();
                    command("fill %d %d %d %d %d %d minecraft:air", x - 8, y - 4, z - 8, x + 8, y + 12, z + 8);
                    command("fill %d %d %d %d %d %d minecraft:air", x - 8, 296, z - 8, x + 8, y - 5, z + 8);
                    command("fill %d %d %d %d %d %d minecraft:air", x + 7, y - 4, z - 5, x + 26, y + 8, z + 5);
                    command("fill %d %d %d %d %d %d minecraft:air", x + 8, 296, z - 2, x + 12, y - 5, z + 2);
                    command("fill %d %d %d %d %d %d cosmicbreach:driftstone", x - 6, y - 4, z - 6, x + 6, y - 1, z + 6);
                })
                .waitUntil("the rock stands", 100, () -> mc.level.getBlockState(rock.below()).is(ModBlocks.DRIFTSTONE.get()))
                .run("onto the rock", () -> ColossusScenario.tp(mc, rock.getX() + 0.5, rock.getY(), rock.getZ() + 0.5,
                        rock.getX() + 0.5, rock.getY() + 1, rock.getZ() - 5.0))
                .waitTicks(20)
                .command("clear @s");
    }

    /** A new tamed stag at the rock's (dx, dy, dz) from the middle of its top; any earlier ones are sent away. */
    private void newStag(Steps steps, double dx, double dy, double dz) {
        steps.run("send the earlier stags away", () -> ServerQuery.ask(p -> {
                    p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(200), LumenStag::isTamed).forEach(Entity::discard);
                    return true;
                }))
                .command("cosmicbreach debug mount spawn stag 1")
                .waitTicks(5)
                .run("tame the new one and put it where it goes", () -> stag = ServerQuery.ask(p -> {
                    LumenStag s = p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(40), m -> !m.isTamed())
                            .stream().findFirst().orElse(null);
                    if (s == null) {
                        return -1;
                    }
                    s.teleportTo(rock.getX() + dx, rock.getY() + dy, rock.getZ() + dz);
                    s.setDeltaMovement(Vec3.ZERO);
                    s.setTrust(StagRules.TAME_AT);
                    s.tameTo(p);
                    s.setPersistenceRequired();
                    return s.getId();
                }))
                .check("a stag is here", () -> stag >= 0);
        softWait(steps, "it stands on the rock", 80, () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s != null && s.onGround();
        }));
        steps.log("the new stag", () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s == null ? "stag " + stag + " is not on the server"
                    : String.format(Locale.ROOT, "stag %d at %s, on the ground %s, tamed %s, its owner %s, health %.1f; the rock's top is %d at (%d, %d), the player at %s",
                            stag, s.position(), s.onGround(), s.isTamed(), p.getUUID().equals(s.getOwnerUUID()), s.getHealth(), rock.getY(), rock.getX(),
                            rock.getZ(), p.position());
        }));
    }

    /** Watches the stag for {@code ticks} ticks and notes how low it got, how often a climb began and whether it ends on the ground. */
    private void watch(Steps steps, String what, int ticks) {
        double[] low = {Double.MAX_VALUE};
        int[] climbs = {0};
        int[] n = {0};
        boolean[] was = {false};
        steps.waitUntil("watching " + what, ticks + 20, () -> {
            double[] s = ServerQuery.ask(p -> {
                LumenStag l = server(p);
                return l == null ? new double[] {-1, 0, 0} : new double[] {l.getY(), l.isRescuing() ? 1 : 0, l.onGround() ? 1 : 0};
            });
            if (s[0] < 0) {
                failures.add(what + ": the stag is gone (it fell out of the world)");
                return true;
            }
            low[0] = Math.min(low[0], s[0]);
            if (s[1] > 0 && !was[0]) {
                climbs[0]++;
            }
            was[0] = s[1] > 0;
            return ++n[0] >= ticks;
        });
        steps.run("it stayed on the ground: " + what, () -> {
            double[] end = ServerQuery.ask(p -> {
                LumenStag l = server(p);
                return l == null ? new double[] {-1, 0} : new double[] {l.getY(), l.onGround() ? 1 : 0};
            });
            if (end[0] < 0) {
                return;
            }
            String line = String.format(Locale.ROOT, "%s: ended at height %.1f (the rock's top is %d) on the ground %s, lowest %.1f, climbs begun after arriving %d",
                    what, end[0], rock.getY(), end[1] > 0, low[0], climbs[0]);
            results.add(line);
            expect(line, end[1] > 0 && Math.abs(end[0] - rock.getY()) < 0.6 && low[0] > rock.getY() - 1.0 && climbs[0] == 0);
        });
    }

    /**
     * A stowed stag is not set down against the rock's side, a cliff four blocks tall (the crystal keeps it, and says there
     * is no ground: set down in mid-air it would fall), and is set down on the rock's top, where it stands and stays.
     */
    private void releaseNeedsGround(Steps steps) {
        newStag(steps, 0.5, 0, 3.5);
        steps.command("give @s cosmicbreach:stable_crystal")
                .waitTicks(5)
                .run("hold the crystal", () -> select(Stable.STABLE_CRYSTAL.get()))
                .waitUntil("the server has it in hand", 40, () -> ServerQuery.ask(p -> p.getMainHandItem().is(Stable.STABLE_CRYSTAL.get())))
                .waitUntil("the client sees the stag", 60, () -> mc.level.getEntity(stag) != null)
                .run("aim at the stag", () -> CryptKit.aim(mc, mc.level.getEntity(stag).getBoundingBox().getCenter()))
                .press(mc.options.keyUse)
                .waitUntil("it went into the crystal", 40, () -> ServerQuery.ask(p -> server(p) == null)
                        && StableCrystalItem.stowed(mc.player.getMainHandItem()) != null)
                .run("out beside the rock's east face, halfway down it", () -> ColossusScenario.tp(mc, rock.getX() + 9.5, rock.getY() - 2.5,
                        rock.getZ() + 0.5, rock.getX() + 7.0, rock.getY() - 2.0, rock.getZ() + 0.5))
                .waitTicks(3)
                .run("and hover there", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitTicks(10)
                .run("aim at the face", () -> CryptKit.aim(mc, new Vec3(rock.getX() + 6.99, rock.getY() - 2.0, rock.getZ() + 0.5)))
                .press(mc.options.keyUse)
                .waitTicks(10)
                .run("it was not set down in the air", () -> {
                    boolean held = StableCrystalItem.stowed(mc.player.getMainHandItem()) != null;
                    int out = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(80), LumenStag::isTamed).size());
                    String line = "released against the rock's side: the crystal still holds it " + held + ", tamed stags out " + out;
                    results.add(line);
                    expect(line, held && out == 0);
                })
                .run("back onto the rock", () -> ColossusScenario.tp(mc, rock.getX() + 0.5, rock.getY(), rock.getZ() + 0.5,
                        rock.getX() + 0.5, rock.getY() + 1, rock.getZ() - 5.0))
                .waitTicks(10)
                .run("aim at the rock three blocks ahead", () -> CryptKit.aim(mc, Vec3.atCenterOf(rock.below().relative(mc.player.getDirection(), 3))))
                .press(mc.options.keyUse)
                .waitTicks(10)
                .run("find it", () -> stag = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(80),
                        LumenStag::isTamed).stream().findFirst().map(Entity::getId).orElse(-1)))
                .run("it came back out on the rock's top", () -> expect("the stag did not come back out on the rock's top", stag >= 0));
        watch(steps, "set down on the rock's top", 80);
    }

    /**
     * No remembered spot, dropped below level 1 into the shaft, its owner at the rock's east edge: the spot two blocks toward
     * the stag is over the edge, so the stag must come down on solid ground beside its owner.
     */
    private void ownersSideOnGround(Steps steps) {
        newStag(steps, 0.5, 0, 0.5);
        steps.run("its owner stands on the rock's east edge", () -> {
                    ColossusScenario.tp(mc, rock.getX() + 6.5, rock.getY(), rock.getZ() + 0.5, rock.getX() + 12.0, rock.getY(), rock.getZ() + 0.5);
                    mc.player.getAbilities().flying = false; // on foot: the rule asks for an owner standing on the ground
                    mc.player.onUpdateAbilities();
                });
        softWait(steps, "the server has its owner on the ground", 60, () -> ServerQuery.ask(p -> p.onGround()));
        steps.waitTicks(10)
                .run("it forgets where it was safe and is dropped below level 1, in the shaft", () -> ServerQuery.ask(p -> {
                    LumenStag s = server(p);
                    s.forgetSafe();
                    s.teleportTo(rock.getX() + 10.5, 305.0, rock.getZ() + 0.5);
                    s.setDeltaMovement(Vec3.ZERO);
                    return true;
                }));
        softWait(steps, "the climb starts though it remembers no spot", 60, () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s != null && s.isRescuing();
        }));
        softWait(steps, "it climbs back up to its owner", 1200, () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s != null && !s.isRescuing() && s.getY() >= rock.getY() - 1.0;
        }));
        watch(steps, "owner's side, no remembered spot", 100);
    }

    /** Its remembered spot is the middle of the rock, which is a hole now: it ends on the ground beside the hole. */
    private void spotWithoutGround(Steps steps) {
        newStag(steps, 4.5, 0, 0.5);
        steps.run("a hole right through the middle of the rock", () -> command("fill %d %d %d %d %d %d minecraft:air", rock.getX() - 1, rock.getY() - 4,
                        rock.getZ() - 1, rock.getX() + 1, rock.getY() - 1, rock.getZ() + 1))
                .waitUntil("the hole is there", 100, () -> mc.level.getBlockState(rock.below()).isAir())
                .run("its old safe spot is the middle of the rock, and it is dropped below level 1, in the shaft", () -> ServerQuery.ask(p -> {
                    LumenStag s = server(p);
                    CompoundTag data = s.saveWithoutId(new CompoundTag());
                    data.putDouble("CareSafeX", rock.getX() + 0.5);
                    data.putDouble("CareSafeY", rock.getY());
                    data.putDouble("CareSafeZ", rock.getZ() + 0.5);
                    data.putBoolean("CareRescue", false);
                    s.load(data);
                    s.teleportTo(rock.getX() + 10.5, 305.0, rock.getZ() + 0.5);
                    s.setDeltaMovement(Vec3.ZERO);
                    return true;
                }));
        softWait(steps, "the climb starts", 60, () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s != null && s.isRescuing();
        }));
        softWait(steps, "it climbs back up to the rock", 1200, () -> ServerQuery.ask(p -> {
            LumenStag s = server(p);
            return s != null && !s.isRescuing() && s.getY() >= rock.getY() - 1.0;
        }));
        watch(steps, "remembered spot with no ground", 100);
    }
}
