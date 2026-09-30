package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gyre.GyreFx;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreKnights;
import com.cosmicbreach.entity.gyre.GyreModes;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Gyre Knight (task G5, GDD 7.1), part {@code gyre-knight}: in the Drift (0.4x gravity) on a test platform, level 24
 * in the Driftweave with the Comet Maul, real inputs. The Knight from the front, the side and the back; each orbit mode and
 * its counter: arrows at its Shield Orbit from the front (deflected) and from behind (they land); a Sweep Orbit's gold
 * telegraph and a parry breaking a blade; a Lance Volley's red lines and a dash across each; a Recall Crash's lines and a
 * step out of them; the hum's pitch telling the modes; a poise break dropping it stunned, its core taking x1.5; the kill,
 * its drops and 600 Attunement XP; and its natural spawns in the Drift Belt.
 */
public final class GyreKnightScenario implements Scenario {
    private final List<String> summary = new ArrayList<>();
    private final List<Float> playerDamage = new CopyOnWriteArrayList<>();
    private final double[] marks = new double[8];
    private BlockPos centre = BlockPos.ZERO;
    /** A clear, level patch of real Drift ground near where the player arrives, for the looks. */
    private BlockPos ground = BlockPos.ZERO;
    /** Where the player arrived in the Drift (the test floor goes up over it). */
    private BlockPos arrival = BlockPos.ZERO;

    @Override
    public int timeBudgetSeconds() {
        return 500;
    }

    private static @Nullable GyreKnight knight(ServerPlayer p) {
        GyreKnight best = null;
        double bestD = Double.MAX_VALUE;
        for (GyreKnight k : p.serverLevel().getEntitiesOfClass(GyreKnight.class, p.getBoundingBox().inflate(96.0), GyreKnight::isAlive)) {
            double d = k.distanceToSqr(p);
            if (d < bestD) {
                best = k;
                bestD = d;
            }
        }
        return best;
    }

    private static <T> T ask(Function<GyreKnight, T> query) {
        return ServerQuery.ask(p -> {
            GyreKnight k = knight(p);
            if (k == null) {
                throw new Steps.Failure("no Gyre Knight near the player");
            }
            return query.apply(k);
        });
    }

    private static @Nullable GyreKnight clientKnight(Minecraft mc) {
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof GyreKnight k) {
                return k;
            }
        }
        return null;
    }

    /** The player to the middle of the test floor, facing the Knight (or north). */
    private void centre() {
        Minecraft mc = Minecraft.getInstance();
        GyreKnight k = ServerQuery.ask(GyreKnightScenario::knight);
        Vec3 look = k == null ? new Vec3(centre.getX() + 0.5, centre.getY() + 2, centre.getZ() - 5) : ServerQuery.ask(p -> knight(p).core());
        ColossusScenario.tp(mc, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, look.x, look.y, look.z);
    }

    private static void key(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    private static void click(KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
    }

    private float takenSince(int from) {
        float sum = 0f;
        for (int i = from; i < playerDamage.size(); i++) {
            sum += playerDamage.get(i);
        }
        return sum;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer && event.getSource().getEntity() instanceof GyreKnight) {
                        playerDamage.add(event.getNewDamage());
                    }
                });
        steps.command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the asteroid is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                .run("a clear, level patch of the asteroid's own ground", () -> {
                    arrival = ServerQuery.ask(ServerPlayer::blockPosition);
                    ground = ServerQuery.ask(GyreKnightScenario::findGround);
                })
                .log("ground", () -> "looks on the Drift's ground at " + ground);
        looks(steps, mc);
        steps.run("a test floor out in the Drift's air", () -> centre = ServerQuery.ask(p -> {
                    BlockPos c = arrival.offset(0, 14, 0);
                    var level = p.serverLevel();
                    for (int x = -16; x <= 16; x++) {
                        for (int z = -16; z <= 16; z++) {
                            for (int y = 0; y <= 18; y++) {
                                level.setBlock(c.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                            }
                            level.setBlock(c.offset(x, -1, z), com.cosmicbreach.registry.ModBlocks.DRIFTSTONE_BRICKS.get().defaultBlockState(), 2);
                        }
                    }
                    return c;
                }))
                .run("stand on it", () -> ColossusScenario.tp(mc, centre.getX() + 0.5, centre.getY(), centre.getZ() - 5.5, centre.getX() + 0.5,
                        centre.getY() + 4, centre.getZ() + 0.5))
                .waitTicks(10);
        LeviathanScenario helper = new LeviathanScenario(LeviathanScenario.Part.LOOKS);
        helper.equip(steps, mc);
        steps.command("gamerule naturalRegeneration true")
                .run("summary", () -> summary.addAll(helper.summary));
        shield(steps, mc);
        sweep(steps, mc);
        lance(steps, mc);
        recall(steps, mc);
        stunAndKill(steps, mc);
        spawns(steps, mc);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ looks: from three sides

    /**
     * The nearest patch of the asteroid's own ground round the player that is level (every column within 6 blocks tops
     * out within 2 of it) and open (nothing standing on it up to 5 over), for the Knight's portraits; the player's own
     * spot if there is none.
     */
    private static BlockPos findGround(ServerPlayer p) {
        var level = p.serverLevel();
        BlockPos at = p.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -30; dx <= 30; dx += 3) {
            for (int dz = -30; dz <= 30; dz += 3) {
                Integer top = topOf(level, at.getX() + dx, at.getY() + 8, at.getZ() + dz);
                if (top == null) {
                    continue;
                }
                boolean ok = true;
                for (int ox = -6; ox <= 6 && ok; ox += 2) {
                    for (int oz = -6; oz <= 6 && ok; oz += 2) {
                        Integer t = topOf(level, at.getX() + dx + ox, top + 6, at.getZ() + dz + oz);
                        ok = t != null && Math.abs(t - top) <= 2;
                    }
                }
                double d = dx * dx + dz * dz;
                if (ok && d < bestD) {
                    bestD = d;
                    best = new BlockPos(at.getX() + dx, top + 1, at.getZ() + dz);
                }
            }
        }
        return best != null ? best : at;
    }

    /** The top solid block of the column at (x, z) from {@code from} down 20, with open air over it to 5 blocks up; or null. */
    private static @Nullable Integer topOf(net.minecraft.server.level.ServerLevel level, int x, int from, int z) {
        for (int y = from; y > from - 20; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
                for (int up = 1; up <= 5; up++) {
                    if (!level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty()) {
                        return null;
                    }
                }
                return y;
            }
        }
        return null;
    }

    private void looks(Steps steps, Minecraft mc) {
        steps.command("gamemode creative")
                .run("a Knight over the Drift's own ground, facing north", () -> ServerQuery.ask(p -> {
                    GyreKnight k = GyreKnights.GYRE_KNIGHT.get().create(p.serverLevel());
                    k.moveTo(ground.getX() + 0.5, ground.getY() + 1.2, ground.getZ() + 0.5, 180f, 0f);
                    k.setYHeadRot(180f);
                    k.setYBodyRot(180f);
                    k.setPersistenceRequired();
                    return p.serverLevel().addFreshEntity(k);
                }))
                .waitUntil("it is here", 40, () -> clientKnight(mc) != null)
                .waitTicks(10);
        String[] names = {"gyre_front", "gyre_side", "gyre_back"};
        Vec3[] dirs = {new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++) {
            Vec3 d = dirs[i];
            steps.run("camera " + names[i] + ", a little above, so the ground is behind it", () -> {
                        Vec3 core = ask(GyreKnight::core);
                        LeviathanScenario.camera(mc, core.add(d.scale(4.6)).add(0, 2.1, 0), core.add(0, -0.3, 0));
                    })
                    .waitTicks(3)
                    .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                    .screenshot(names[i]);
        }
        steps.check("its hum plays, low and steady in its Shield Orbit", () -> {
                    GyreKnight k = clientKnight(mc);
                    return k != null && GyreFx.humming(k) && Math.abs(GyreFx.humPitch(k) - 0.75f) < 0.01f;
                })
                .command("kill @e[type=cosmicbreach:gyre_knight]")
                .command("kill @e[type=minecraft:item]")
                .waitTicks(10);
    }

    private void spawnFighting(Steps steps, Minecraft mc) {
        steps.command("kill @e[type=cosmicbreach:gyre_knight]")
                .waitTicks(5)
                .run("a Knight over the floor", () -> ServerQuery.ask(p -> {
                    GyreKnight k = GyreKnights.GYRE_KNIGHT.get().create(p.serverLevel());
                    k.moveTo(centre.getX() + 0.5, centre.getY() + 6.0, centre.getZ() + 4.5, 180f, 0f);
                    k.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(k);
                    k.holdModes(1_000_000);
                    return true;
                }))
                .waitTicks(5)
                .log("knight", () -> ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    return k == null ? "no knight" : String.format(Locale.ROOT, "knight at %s alive %s target %s, player at %s creative %s spectator %s difficulty %s",
                            k.position(), k.isAlive(), k.getTarget(), p.position(), p.isCreative(), p.isSpectator(), p.level().getDifficulty());
                }))
                .waitUntil("it targets the player", 60, () -> ServerQuery.ask(p -> knight(p) != null && knight(p).getTarget() == p));
    }

    // ------------------------------------------------------------------ Shield Orbit

    private void shield(Steps steps, Minecraft mc) {
        float[] health = {0};
        int[] deflected = {0};
        spawnFighting(steps, mc);
        steps.waitTicks(20)
                .run("an arrow at its front (from the player's side)", () -> ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    health[0] = k.getHealth();
                    deflected[0] = k.counts()[1];
                    shoot(p, k, p.position().add(0, 1.5, 0).subtract(k.core()).normalize());
                    return true;
                }))
                .waitTicks(20)
                .check("its Shield Orbit deflects it", () -> ask(k -> k.counts()[1] == deflected[0] + 1 && k.getHealth() == health[0]))
                .run("an arrow from behind it", () -> ServerQuery.ask(p -> {
                    GyreKnight k = knight(p);
                    shoot(p, k, k.core().subtract(p.position().add(0, 1.5, 0)).normalize());
                    return true;
                }))
                .waitTicks(20)
                .log("shield", () -> ask(k -> {
                    String r = String.format(Locale.ROOT, "Shield Orbit: an arrow at its front deflected (%d), one from behind took %.1f", k.counts()[1] - deflected[0],
                            health[0] - k.getHealth());
                    summary.add(r);
                    return r;
                }))
                .check("get behind it: the arrow from behind lands", () -> ask(k -> k.getHealth() < health[0]));
    }

    /** An arrow flying at the Knight's core from 3 blocks off along {@code from} (a unit vector away from the core). */
    private static void shoot(ServerPlayer p, GyreKnight k, Vec3 from) {
        Vec3 start = k.core().add(from.scale(3.0));
        Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
        arrow.setPos(start.x, start.y, start.z);
        arrow.setOwner(p);
        Vec3 d = k.core().subtract(start).normalize();
        arrow.shoot(d.x, d.y, d.z, 2.5f, 0f);
        p.serverLevel().addFreshEntity(arrow);
    }

    // ------------------------------------------------------------------ Sweep Orbit

    private void sweep(Steps steps, Minecraft mc) {
        int[] broken = {0};
        int[] from = {0};
        steps.run("count", () -> {
            broken[0] = ask(k -> k.counts()[0]);
            from[0] = playerDamage.size();
        });
        for (int attempt = 0; attempt < 3; attempt++) {
            steps.run("back to the middle of the floor", this::centre)
                    .waitTicks(3)
                    .command("cosmicbreach debug gyre mode sweep")
                    .waitUntil("it swoops level with the player", 60, () -> ask(k -> k.approachEnd() >= 0))
                    .waitUntil("the rings widening, gold glints on the blades", 20, () -> ask(k ->
                            k.level().getGameTime() - k.modeStart() - k.approachEnd() >= 10))
                    .run("face it", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)));
            if (attempt == 0) {
                steps.run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                        .screenshot("gyre_sweep_tell")
                        .log("whine", () -> {
                            GyreKnight k = clientKnight(mc);
                            String r = String.format(Locale.ROOT, "the hum rises into a whine through the sweep's telegraph: pitch %.2f (0.75 at rest)",
                                    k == null ? 0f : GyreFx.humPitch(k));
                            summary.add(r);
                            return r;
                        })
                        .check("the whine rises over the resting hum", () -> {
                            GyreKnight k = clientKnight(mc);
                            return k != null && GyreFx.humPitch(k) > 1.1f;
                        });
            }
            steps.waitUntil("the blades are about to cut", 20, () -> ask(k -> k.level().getGameTime() - k.modeStart() - k.approachEnd()
                            >= GyreModes.SWEEP_TELL - 1))
                    .run("parry", () -> click(ModKeyMappings.PARRY))
                    .waitTicks(1)
                    .run("", () -> key(ModKeyMappings.PARRY, false))
                    .waitUntil("the sweep is over", 80, () -> ask(k -> k.mode() == GyreModes.Mode.SHIELD))
                    .waitTicks(30);
        }
        steps.log("sweep", () -> ask(k -> {
                    String r = String.format(Locale.ROOT, "Sweep Orbit: parries broke %d blade(s) in three sweeps; sweeps dealt %.1f", k.counts()[0] - broken[0],
                            takenSince(from[0]));
                    summary.add(r);
                    return r;
                }))
                .check("a parry breaks a blade", () -> ask(k -> k.counts()[0] > broken[0]))
                .command("effect give @s minecraft:instant_health 1 3 true");
    }

    // ------------------------------------------------------------------ Lance Volley

    private void lance(Steps steps, Minecraft mc) {
        int[] from = {0};
        boolean[] exposed = {false};
        steps.waitUntil("its blades are all home", 200, () -> ask(k -> k.blade(0) == GyreKnight.Blade.ORBIT && k.blade(1) == GyreKnight.Blade.ORBIT
                        && k.blade(2) == GyreKnight.Blade.ORBIT))
                .run("count", () -> from[0] = playerDamage.size())
                .run("back to the middle of the floor", this::centre)
                .waitTicks(5)
                .command("cosmicbreach debug gyre mode lance");
        for (int i = 0; i < 3; i++) {
            int blade = i;
            boolean right = i % 2 == 0;
            steps.waitUntil("blade " + i + "'s red line", 60, () -> ask(k -> k.level().getGameTime() - k.modeStart() >= GyreModes.fireTick(blade) - 5))
                    .run("face it", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)));
            if (i == 0) {
                steps.run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("gyre_lance_lines");
            }
            steps.log("lance " + i, () -> ServerQuery.ask(p -> {
                GyreKnight k = knight(p);
                return k == null ? "no knight" : String.format(Locale.ROOT, "knight mode %s t %d blades %s %s %s at %s target %s", k.mode(),
                        k.level().getGameTime() - k.modeStart(), k.blade(0), k.blade(1), k.blade(2), k.position(), k.getTarget());
            }));
            steps.waitUntil("blade " + i + " fires", 10, () -> ask(k -> k.level().getGameTime() - k.modeStart() >= GyreModes.fireTick(blade)))
                    .run("strafe and dash across the line", () -> {
                        key(right ? mc.options.keyRight : mc.options.keyLeft, true);
                        click(ModKeyMappings.DASH);
                    })
                    .waitTicks(1)
                    .run("", () -> key(ModKeyMappings.DASH, false))
                    .waitTicks(4)
                    .run("", () -> key(right ? mc.options.keyRight : mc.options.keyLeft, false));
        }
        steps.run("is the core exposed with the blades out", () -> exposed[0] = ask(GyreKnight::coreExposed))
                .waitUntil("the volley is over", 150, () -> ask(k -> k.mode() == GyreModes.Mode.SHIELD))
                .log("lance", () -> {
                    String r = String.format(Locale.ROOT, "Lance Volley: dashing across the lines, took %.1f from three lances; core exposed while the blades were out %s",
                            takenSince(from[0]), exposed[0]);
                    summary.add(r);
                    return r;
                })
                .check("the dashes beat the volley (at most one lance landed)", () -> takenSince(from[0]) < 9f)
                .check("its core was exposed while the blades were out", () -> exposed[0])
                .command("effect give @s minecraft:instant_health 1 3 true");
    }

    // ------------------------------------------------------------------ Recall Crash

    private void recall(Steps steps, Minecraft mc) {
        int[] from = {0};
        steps.waitUntil("its blades are all home", 200, () -> ask(k -> k.blade(0) == GyreKnight.Blade.ORBIT && k.blade(1) == GyreKnight.Blade.ORBIT
                        && k.blade(2) == GyreKnight.Blade.ORBIT))
                .run("count", () -> from[0] = playerDamage.size())
                .run("back to the middle of the floor", this::centre)
                .waitTicks(5)
                .command("cosmicbreach debug gyre mode recall")
                .waitUntil("the blades are out, the red lines through where the player stood", 40, () -> ask(k -> k.level().getGameTime()
                        - k.modeStart() >= GyreModes.RECALL_OUT + 2))
                .run("face the core", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)))
                .run("step out of the lines", () -> key(mc.options.keyLeft, true))
                .waitTicks(8)
                .run("face the core", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("gyre_recall_lines")
                .waitTicks(4)
                .run("", () -> key(mc.options.keyLeft, false))
                .waitUntil("the recall is over", 80, () -> ask(k -> k.mode() == GyreModes.Mode.SHIELD))
                .log("recall", () -> {
                    String r = String.format(Locale.ROOT, "Recall Crash: stepped out of the lines, took %.1f", takenSince(from[0]));
                    summary.add(r);
                    return r;
                })
                .check("leaving the lines takes no hit", () -> takenSince(from[0]) == 0f);
    }

    // ------------------------------------------------------------------ poise break, the kill

    private void stunAndKill(Steps steps, Minecraft mc) {
        long[] xp = {0};
        float[] before = {0};
        steps.run("back to the middle of the floor", this::centre)
                .waitTicks(5)
                .run("before", () -> xp[0] = ServerQuery.ask(p -> Attunements.of(p).totalXp()))
                .command("cosmicbreach debug gyre mode stunned")
                .waitUntil("it drops to the floor, stunned", 60, () -> ask(k -> k.onGround() && k.mode() == GyreModes.Mode.STUNNED))
                .run("face it", () -> ColossusScenario.lookAt(mc, ask(GyreKnight::core)))
                .waitTicks(2)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("gyre_stunned")
                .check("stunned, its core is exposed", () -> ask(GyreKnight::coreExposed))
                .run("health before", () -> before[0] = ask(GyreKnight::getHealth));
        LeviathanMechanics.walk(steps, mc, "up to it", () -> ask(GyreKnight::position), 2.2, 80);
        steps.waitUntil("the kill (swinging at the exposed core, dropping it again when it rises)", 900, () -> {
                    GyreKnight k = ServerQuery.ask(GyreKnightScenario::knight);
                    if (k == null || !ServerQuery.ask(p -> knight(p) != null && knight(p).isAlive())) {
                        key(mc.options.keyAttack, false);
                        return true;
                    }
                    if (ask(kk -> kk.mode() != GyreModes.Mode.STUNNED)) {
                        mc.player.connection.sendCommand("cosmicbreach debug gyre mode stunned");
                    }
                    ColossusScenario.lookAt(mc, ask(GyreKnight::core));
                    if (mc.level.getGameTime() % 12 == 0) {
                        click(mc.options.keyAttack);
                    } else {
                        key(mc.options.keyAttack, false);
                    }
                    return false;
                })
                .waitTicks(30)
                .log("kill", () -> {
                    int[] drops = ServerQuery.ask(p -> {
                        int core = 0;
                        int ingots = 0;
                        for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16))) {
                            core += e.getItem().is(com.cosmicbreach.registry.ModMaterials.GYRE_CORE.get()) ? e.getItem().getCount() : 0;
                            ingots += e.getItem().is(com.cosmicbreach.registry.ModMaterials.NEBULITE_INGOT.get()) ? e.getItem().getCount() : 0;
                        }
                        core += p.getInventory().countItem(com.cosmicbreach.registry.ModMaterials.GYRE_CORE.get());
                        ingots += p.getInventory().countItem(com.cosmicbreach.registry.ModMaterials.NEBULITE_INGOT.get());
                        return new int[] {core, ingots};
                    });
                    marks[0] = drops[0];
                    marks[1] = drops[1];
                    String r = String.format(Locale.ROOT, "the kill: Gyre Core %d, Nebulite Ingots %d, Attunement XP +%d", drops[0], drops[1],
                            ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0]);
                    summary.add(r);
                    return r;
                })
                .check("a Gyre Core and 1 to 2 Nebulite Ingots", () -> marks[0] == 1 && marks[1] >= 1 && marks[1] <= 2)
                .check("600 Attunement XP", () -> ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xp[0] == 600);
    }

    private void spawns(Steps steps, Minecraft mc) {
        steps.check("the Drift Belt lists it among its monsters (rare: one natural attempt in six, none within 64 of another)", () -> ServerQuery.ask(p -> {
            var biome = p.serverLevel().getBiome(p.blockPosition()).value();
            for (var data : biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                if (data.type == GyreKnights.GYRE_KNIGHT.get()) {
                    return true;
                }
            }
            return false;
        }));
    }

    /** Items the Knight might drop (for logs). */
    static int count(Minecraft mc, Item item) {
        return ServerQuery.ask(p -> p.getInventory().countItem(item));
    }
}
