package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.FrameStats;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.jelly.DriftJelly;
import com.cosmicbreach.jelly.JellyBloom;
import com.cosmicbreach.jelly.JellyRules;
import com.cosmicbreach.jelly.Jellies;
import com.cosmicbreach.sandbox.StressScene;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The drift jelly (Lane C, 1.2 design section 7) in a real Aetheria Deep, through the real key path where the client
 * decides (a landing, Skim's air speed) and measured on the server where it does not. Parts:
 * <ul>
 *   <li>{@code MECHANICS}: a jelly hovers 2 to 5 blocks over a floor; a player dropped on its bell is bounced higher than
 *       a slime block would, takes no fall damage and gets Skim; sneaking on landing is a soft stop and sneaking on it
 *       rests there; under it the tendrils sting a heart a second and push nobody; hit, it flees slowly; Skim makes a
 *       jump 30 percent longer;</li>
 *   <li>{@code DROPS}: a kill drops Drift Gel (1 to 3), 400 rolls of the table average 2; raw it gives night vision for 30 s
 *       and a little food, candied gel more food and no glow; a furnace, a smoker and a campfire each cook it;</li>
 *   <li>{@code BLOOM}: a bloom of 25 starts under the layer, rises, drifts, sinks and is gone with nothing left; what 25
 *       cost the server a tick and the client a frame;</li>
 *   <li>{@code SPAWNS}: natural spawn placement, sampled the way the spawner picks positions, over a real stretch of the
 *       Deep (land against void), and a stretch of real natural spawning;</li>
 *   <li>{@code LOOKS}: the jelly from the front, three quarters, below and above, bounced on, hurt and dying;</li>
 *   <li>{@code ZONES}: in each of layer 3's four zones: the zone's biome carries the jelly's spawns (the dense ones more),
 *       the spawn rule takes positions over its real terrain, and natural spawning puts jellies there.</li>
 * </ul>
 */
public final class JellyScenario implements Scenario {
    public enum Part { MECHANICS, DROPS, BLOOM, SPAWNS, LOOKS, ZONES }

    private static final int X = 4000;
    private static final int Z = 4000;
    private static final int FLOOR = 100;

    private final Part part;
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private @Nullable UUID jellyId;

    // sampling during the bounce test
    private volatile boolean sampling;
    private final List<Double> launchYs = new CopyOnWriteArrayList<>();
    private final List<Double> apexes = new CopyOnWriteArrayList<>();
    private volatile double segPeak;
    private volatile double minHealth = 40.0;
    private volatile int lastBounces;
    private volatile int skimAtBounce;
    private volatile double healthBefore;
    private volatile double startY;

    public JellyScenario(Part part) {
        this.part = part;
    }

    @Override
    public int timeBudgetSeconds() {
        return switch (part) {
            case MECHANICS -> 300;
            case DROPS -> 240;
            case BLOOM -> 600;
            case SPAWNS -> 600;
            case ZONES -> 1200;
            case LOOKS -> 240;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static ServerLevel aetheria(MinecraftServer srv) {
        return srv.getLevel(AetheriaWorld.LEVEL);
    }

    private @Nullable DriftJelly jelly(MinecraftServer srv) {
        return jellyId == null ? null : aetheria(srv).getEntity(jellyId) instanceof DriftJelly j ? j : null;
    }

    private DriftJelly needJelly(MinecraftServer srv) {
        DriftJelly j = jelly(srv);
        if (j == null) {
            throw new Steps.Failure("the test jelly is gone");
        }
        return j;
    }

    private static void fillFloor(ServerLevel level) {
        for (int dx = -21; dx <= 21; dx++) {
            for (int dz = -21; dz <= 21; dz++) {
                level.setBlock(new BlockPos(X + dx, FLOOR, Z + dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                for (int y = FLOOR + 1; y <= FLOOR + 40; y++) {
                    level.setBlock(new BlockPos(X + dx, y, Z + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // a few lanterns round the edge, so the model has light to be seen by
        for (int i = -20; i <= 20; i += 8) {
            for (int[] edge : new int[][] {{i, -21}, {i, 21}, {-21, i}, {21, i}}) {
                level.setBlock(new BlockPos(X + edge[0], FLOOR + 1, Z + edge[1]), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        }
    }

    private DriftJelly spawn(MinecraftServer srv, double dx, double y, double dz, boolean ai) {
        ServerLevel level = aetheria(srv);
        DriftJelly j = Jellies.DRIFT_JELLY.get().create(level);
        j.moveTo(X + 0.5 + dx, y, Z + 0.5 + dz, 0f, 0f);
        j.setPersistenceRequired();
        j.setNoAi(!ai);
        level.addFreshEntity(j);
        jellyId = j.getUUID();
        return j;
    }

    private static void clearJellies(MinecraftServer srv) {
        for (DriftJelly j : new ArrayList<>(aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), e -> true))) {
            j.discard();
        }
    }

    private static void put(MinecraftServer srv, double x, double y, double z, float yaw, float pitch) {
        ServerPlayer p = CryptKit.player(srv);
        p.teleportTo(aetheria(srv), x, y, z, yaw, pitch);
        p.fallDistance = 0.0f;
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Teleports the player into Aetheria's Deep (by command, so the client follows) and builds the floor. */
    private void setup(Steps steps, Minecraft mc) {
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("gamerule naturalRegeneration false")
                .command("time set 6000")
                .command("difficulty normal")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + X + ".5 " + (FLOOR + 2) + " " + Z + ".5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null)
                .waitTicks(40)
                .run("build the floor", () -> CryptKit.server(srv -> {
                    fillFloor(aetheria(srv));
                    return null;
                }))
                .waitUntil("the floor is drawn", 1200, CryptKit.settled(mc, 900));
    }

    private void sampler() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (!sampling) {
                return;
            }
            MinecraftServer srv = e.getServer();
            ServerPlayer p = CryptKit.player(srv);
            DriftJelly j = jelly(srv);
            if (j == null) {
                return;
            }
            segPeak = Math.max(segPeak, p.getY());
            minHealth = Math.min(minHealth, p.getHealth());
            if (j.bounces() != lastBounces) {
                if (lastBounces > 0) {
                    apexes.add(segPeak - launchYs.get(launchYs.size() - 1));
                } else {
                    skimAtBounce = p.getEffect(Jellies.SKIM) == null ? 0 : p.getEffect(Jellies.SKIM).getDuration();
                }
                launchYs.add(p.getY());
                lastBounces = j.bounces();
                segPeak = p.getY();
            }
        });
    }

    // ------------------------------------------------------------------ steps

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        switch (part) {
            case MECHANICS -> mechanics(steps, mc);
            case DROPS -> drops(steps, mc);
            case BLOOM -> bloom(steps, mc);
            case SPAWNS -> spawns(steps, mc);
            case LOOKS -> looks(steps, mc);
            case ZONES -> zones(steps, mc);
        }
        steps.log("summary", () -> String.join(System.lineSeparator(), summary));
    }

    // ------------------------------------------------------------------ mechanics

    private void mechanics(Steps steps, Minecraft mc) {
        sampler();
        setup(steps, mc);
        double top = FLOOR + 1 + 7 + JellyRules.HEIGHT;       // the bell's top, for a jelly 7 blocks over the floor's top
        steps
                // hover: dropped in 9 blocks over the floor it settles into 2 to 5
                .run("a jelly, 9 blocks over the floor", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    spawn(srv, 0, FLOOR + 1 + 9, 0, true);
                    put(srv, X + 14.5, FLOOR + 1, Z + 0.5, 90f, 0f);
                    return null;
                }))
                .command("gamemode survival")
                .waitTicks(60)
                .log("the jelly at 60 ticks", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    return String.format(Locale.ROOT, "y %.3f dm %s ticks %d depth %d alive %s ai %s", j.getY(), j.getDeltaMovement(), j.tickCount,
                            j.groundDepthNow(), j.isAlive(), !j.isNoAi());
                }))
                .waitTicks(60)
                .log("the jelly at 120 ticks", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    return String.format(Locale.ROOT, "y %.3f dm %s ticks %d depth %d", j.getY(), j.getDeltaMovement(), j.tickCount, j.groundDepthNow());
                }))
                .waitUntil("it has settled to hover 2 to 5 blocks up", 600, () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    double h = j.getY() - (FLOOR + 1);
                    return h >= 1.9 && h <= 5.4 && j.tickCount > 150;
                }))
                .waitTicks(100)
                .run("hover held", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    double h = j.getY() - (FLOOR + 1);
                    summary.add(String.format(Locale.ROOT, "hover: %.2f blocks over the floor after %d ticks, ground probe says %d", h, j.tickCount, j.groundDepthNow()));
                    if (h < 1.9 || h > 5.4) {
                        throw new Steps.Failure("hover left the band: " + h);
                    }
                    return null;
                }))

                // flee: hurt, it drifts away from the player slowly
                .run("hurt it", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    flee[0] = j.position().subtract(p.position()).multiply(1, 0, 1).length();
                    flee[1] = j.getHealth();
                    j.hurt(aetheria(srv).damageSources().playerAttack(p), 1.0f);
                    return null;
                }))
                .waitTicks(60)
                .run("it fled", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    double away = j.position().subtract(p.position()).multiply(1, 0, 1).length() - flee[0];
                    summary.add(String.format(Locale.ROOT, "flee: %.2f blocks further away after 60 ticks, speed %.3f b/t, flee ticks left %d, health %.1f to %.1f",
                            away, j.getDeltaMovement().horizontalDistance(), j.fleeTicksLeft(), flee[1], j.getHealth()));
                    if (!j.isFleeing() || away < 1.0 || away > 9.0 || j.getHealth() >= flee[1]) {
                        throw new Steps.Failure("it did not flee slowly: " + away);
                    }
                    if (j.getDeltaMovement().horizontalDistance() > 0.15) {
                        throw new Steps.Failure("it fled too fast: " + j.getDeltaMovement().horizontalDistance());
                    }
                    return null;
                }))

                // the bounce: dropped from 8 blocks onto a still jelly
                .run("a still jelly, the player 8 blocks over its bell", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    DriftJelly j = spawn(srv, 0, FLOOR + 8, 0, false);
                    ServerPlayer p = CryptKit.player(srv);
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p.setHealth(p.getMaxHealth());
                    sampling = false;
                    launchYs.clear();
                    apexes.clear();
                    lastBounces = 0;
                    healthBefore = p.getHealth();
                    minHealth = p.getHealth();
                    startY = j.getY() + JellyRules.HEIGHT + 8.0;
                    segPeak = startY;
                    put(srv, X + 0.5, startY, Z + 0.5, 0f, 0f);
                    sampling = true;
                    return null;
                }))
                .waitTicks(100)
                .log("the bounce so far", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    return String.format(Locale.ROOT, "player %.2f %.2f %.2f onGround %s health %.1f; jelly %.2f %.2f %.2f bounces %d; launches %s apexes %s peak %.2f",
                            p.getX(), p.getY(), p.getZ(), p.onGround(), p.getHealth(), j.getX(), j.getY(), j.getZ(), j.bounces(), launchYs, apexes, segPeak);
                }))
                .waitUntil("it has bounced the player three times", 400, () -> apexes.size() >= 2)
                .run("the bounce", () -> {
                    sampling = false;
                    double fall = 8.0;
                    double predicted = JellyRules.apexHeight(JellyRules.launchSpeed(fall));
                    double slime = JellyRules.apexHeight(JellyRules.slimeLaunch(fall));
                    summary.add(String.format(Locale.ROOT, "bounce: fall %.1f, first apex %.2f over the bell (predicted %.2f, a slime block %.2f), second %.2f, "
                                    + "health %.1f to min %.1f, skim %d ticks left at the first bounce",
                            fall, apexes.get(0), predicted, slime, apexes.get(1), healthBefore, minHealth, skimAtBounce));
                    if (minHealth < healthBefore) {
                        throw new Steps.Failure("the landing hurt: " + minHealth);
                    }
                    if (apexes.get(0) < fall + 1.5 || apexes.get(0) < slime + 1.0) {
                        throw new Steps.Failure("not higher than a slime block would: " + apexes.get(0));
                    }
                    if (Math.abs(apexes.get(0) - predicted) > predicted * 0.35) {
                        throw new Steps.Failure("the apex is not the rule's: " + apexes.get(0) + " vs " + predicted);
                    }
                    if (apexes.get(1) > 17.0) {
                        throw new Steps.Failure("the bounces grow: " + apexes.get(1));
                    }
                    if (skimAtBounce < 70) {
                        throw new Steps.Failure("no Skim after a bounce: " + skimAtBounce);
                    }
                })

                // sneaking: land and stay; let go and bounce
                .run("put the player on the bell, still", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    stand[0] = j.bounces();
                    put(srv, X + 0.5, j.getY() + JellyRules.HEIGHT + 3.0, Z + 0.5, 0f, 0f);
                    return null;
                }))
                .hold(mc.options.keyShift)
                .waitTicks(120)
                .run("sneaking rests", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    summary.add(String.format(Locale.ROOT, "sneak: %d bounces since the landing, the player at %.2f (bell top %.2f)", j.bounces() - stand[0],
                            p.getY(), j.getY() + JellyRules.HEIGHT));
                    if (j.bounces() != stand[0] || Math.abs(p.getY() - (j.getY() + JellyRules.HEIGHT)) > 0.3) {
                        throw new Steps.Failure("sneaking did not rest on the bell");
                    }
                    return null;
                }))
                .release(mc.options.keyShift)
                .waitUntil("standing without sneaking bounces", 60, () -> CryptKit.server(srv -> needJelly(srv).bounces() > stand[0]))

                // the sting: under the bell, a heart a second, no push
                .run("the player under a still jelly", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    DriftJelly j = spawn(srv, 0, FLOOR + 1 + 3, 0, false);
                    ServerPlayer p = CryptKit.player(srv);
                    p.setHealth(p.getMaxHealth());
                    put(srv, X + 0.5, FLOOR + 1, Z + 0.5, 0f, 0f);
                    sting[0] = p.getX();
                    sting[1] = p.getZ();
                    sting[2] = p.getHealth();
                    return null;
                }))
                .waitTicks(75)
                .run("stung, and not pushed", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    double moved = Math.hypot(p.getX() - sting[0], p.getZ() - sting[1]);
                    double lost = sting[2] - p.getHealth();
                    summary.add(String.format(Locale.ROOT, "sting: %d stings in 75 ticks, %.1f health lost, the player moved %.3f blocks", j.stings(), lost, moved));
                    if (j.stings() < 3 || j.stings() > 4) {
                        throw new Steps.Failure("about one sting a second: " + j.stings());
                    }
                    if (Math.abs(lost - JellyRules.STING_DAMAGE * j.stings()) > 0.01) {
                        throw new Steps.Failure("each sting is " + JellyRules.STING_DAMAGE + ": lost " + lost);
                    }
                    if (moved > 0.05) {
                        throw new Steps.Failure("the sting pushed the player: " + moved);
                    }
                    return null;
                }))

                // skim: a jump from rest with forward held, without and with the effect
                .run("clear the way", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    p.setHealth(p.getMaxHealth());
                    p.removeAllEffects();
                    put(srv, X - 12.5, FLOOR + 1, Z + 0.5, -90f, 0f);
                    return null;
                }))
                .waitTicks(15)
                .run("jump 1 begins", () -> {
                    jumpStart = mc.player.position();
                })
                .hold(mc.options.keyUp)
                .press(mc.options.keyJump, 1)
                .waitTicks(2)
                .waitUntil("landed", 60, () -> mc.player.onGround())
                .release(mc.options.keyUp)
                .run("jump 1 measured", () -> {
                    plain = mc.player.position().subtract(jumpStart).horizontalDistance();
                })
                .waitTicks(20)
                .command("effect give @s cosmicbreach:skim 30 0 true")
                .run("back to the start", () -> CryptKit.server(srv -> {
                    put(srv, X - 12.5, FLOOR + 1, Z + 0.5, -90f, 0f);
                    return null;
                }))
                .waitTicks(20)
                .run("jump 2 begins", () -> {
                    jumpStart = mc.player.position();
                })
                .hold(mc.options.keyUp)
                .press(mc.options.keyJump, 1)
                .waitTicks(2)
                .waitUntil("landed", 60, () -> mc.player.onGround())
                .release(mc.options.keyUp)
                .run("skim measured", () -> {
                    skim = mc.player.position().subtract(jumpStart).horizontalDistance();
                    summary.add(String.format(Locale.ROOT, "skim: a jump goes %.3f blocks, with Skim %.3f (x%.2f)", plain, skim, skim / plain));
                    if (plain < 0.5 || skim / plain < 1.12 || skim / plain > 1.5) {
                        throw new Steps.Failure("Skim should lengthen the jump by about 30 percent: " + skim / plain);
                    }
                });
    }

    private final double[] flee = new double[2];
    private final int[] stand = new int[1];
    private final double[] sting = new double[3];
    private Vec3 jumpStart = Vec3.ZERO;
    private double plain;
    private double skim;

    // ------------------------------------------------------------------ drops and cooking

    private void drops(Steps steps, Minecraft mc) {
        setup(steps, mc);
        steps
                .command("gamemode survival")
                .run("kill one with the player's own blow", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    DriftJelly j = spawn(srv, 0, FLOOR + 4, 0, false);
                    ServerPlayer p = CryptKit.player(srv);
                    put(srv, X + 4.5, FLOOR + 1, Z + 0.5, 90f, 0f);
                    j.hurt(aetheria(srv).damageSources().playerAttack(p), 1000f);
                    return null;
                }))
                .waitTicks(60)
                .run("the drops", () -> CryptKit.server(srv -> {
                    int gel = 0;
                    for (ItemEntity e : aetheria(srv).getEntitiesOfClass(ItemEntity.class, new AABB(X, FLOOR, Z, X + 8, FLOOR + 10, Z + 1).inflate(6))) {
                        if (e.getItem().is(Jellies.DRIFT_GEL.get())) {
                            gel += e.getItem().getCount();
                        }
                    }
                    summary.add("a kill dropped " + gel + " drift gel");
                    if (gel < 1 || gel > 4) {
                        throw new Steps.Failure("1 to 3 drift gel a kill (4 with Looting): " + gel);
                    }
                    if (!aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), e -> true).isEmpty()) {
                        throw new Steps.Failure("the jelly is still there");
                    }
                    return null;
                }))
                .run("400 rolls of the table", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    DriftJelly j = Jellies.DRIFT_JELLY.get().create(level);
                    LootTable table = srv.reloadableRegistries().getLootTable(Jellies.DRIFT_JELLY.get().getDefaultLootTable());
                    RandomSource random = RandomSource.create(11L);
                    int total = 0;
                    int min = 99;
                    int max = 0;
                    for (int i = 0; i < 400; i++) {
                        LootParams params = new LootParams.Builder(level)
                                .withParameter(LootContextParams.THIS_ENTITY, j)
                                .withParameter(LootContextParams.ORIGIN, j.position())
                                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(p))
                                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
                                .create(LootContextParamSets.ENTITY);
                        int n = 0;
                        for (ItemStack s : table.getRandomItems(params, random)) {
                            if (s.is(Jellies.DRIFT_GEL.get())) {
                                n += s.getCount();
                            } else {
                                throw new Steps.Failure("an unexpected drop " + s);
                            }
                        }
                        total += n;
                        min = Math.min(min, n);
                        max = Math.max(max, n);
                    }
                    j.discard();
                    double mean = total / 400.0;
                    summary.add(String.format(Locale.ROOT, "loot: 400 player kills gave %d gel, mean %.2f, between %d and %d", total, mean, min, max));
                    if (min < 1 || max > 3 || Math.abs(mean - 2.0) > 0.2) {
                        throw new Steps.Failure("the table is not 1 to 3, mean 2: " + mean);
                    }
                    return null;
                }))
                .run("eat raw and candied", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    ServerLevel level = aetheria(srv);
                    p.removeAllEffects();
                    p.getFoodData().setFoodLevel(6);
                    p.getFoodData().setSaturation(0.0f);
                    ItemStack raw = new ItemStack(Jellies.DRIFT_GEL.get(), 2);
                    raw.finishUsingItem(level, p);
                    int rawFood = p.getFoodData().getFoodLevel();
                    MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
                    summary.add(String.format(Locale.ROOT, "raw gel: food 6 to %d, night vision %s ticks", rawFood, nv == null ? "none" : nv.getDuration()));
                    if (rawFood != 8 || nv == null || nv.getDuration() < 590 || nv.getDuration() > 600) {
                        throw new Steps.Failure("raw gel: a little food and 30 s of night vision");
                    }
                    p.removeAllEffects();
                    p.getFoodData().setFoodLevel(6);
                    new ItemStack(Jellies.CANDIED_GEL.get()).finishUsingItem(level, p);
                    summary.add("candied gel: food 6 to " + p.getFoodData().getFoodLevel() + ", night vision " + (p.hasEffect(MobEffects.NIGHT_VISION) ? "yes" : "none"));
                    if (p.getFoodData().getFoodLevel() != 12 || p.hasEffect(MobEffects.NIGHT_VISION)) {
                        throw new Steps.Failure("candied gel: more food, no glow");
                    }
                    return null;
                }))
                .run("the three recipes", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    ItemStack raw = new ItemStack(Jellies.DRIFT_GEL.get());
                    SingleRecipeInput input = new SingleRecipeInput(raw);
                    expect(level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level), 200, "furnace");
                    expect(level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, input, level), 100, "smoker");
                    expect(level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level), 600, "campfire");
                    return null;
                }))
                .run("a furnace, a smoker and a campfire, each with gel", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    BlockPos furnace = new BlockPos(X + 8, FLOOR + 1, Z);
                    BlockPos smoker = new BlockPos(X + 8, FLOOR + 1, Z + 2);
                    BlockPos fire = new BlockPos(X + 8, FLOOR + 1, Z + 4);
                    level.setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
                    level.setBlock(smoker, Blocks.SMOKER.defaultBlockState(), 3);
                    level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
                    for (BlockPos pos : new BlockPos[] {furnace, smoker}) {
                        AbstractFurnaceBlockEntity be = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
                        be.setItem(0, new ItemStack(Jellies.DRIFT_GEL.get(), 2));
                        be.setItem(1, new ItemStack(Items.COAL, 4));
                    }
                    CampfireBlockEntity camp = (CampfireBlockEntity) level.getBlockEntity(fire);
                    camp.placeFood(null, new ItemStack(Jellies.DRIFT_GEL.get()), 600);
                    return null;
                }))
                .waitUntil("the smoker has cooked one", 300, () -> CryptKit.server(srv -> {
                    AbstractFurnaceBlockEntity be = (AbstractFurnaceBlockEntity) aetheria(srv).getBlockEntity(new BlockPos(X + 8, FLOOR + 1, Z + 2));
                    return be.getItem(2).is(Jellies.CANDIED_GEL.get());
                }))
                .waitUntil("the furnace has cooked one", 300, () -> CryptKit.server(srv -> {
                    AbstractFurnaceBlockEntity be = (AbstractFurnaceBlockEntity) aetheria(srv).getBlockEntity(new BlockPos(X + 8, FLOOR + 1, Z));
                    return be.getItem(2).is(Jellies.CANDIED_GEL.get());
                }))
                .waitUntil("the campfire has cooked one", 700, () -> CryptKit.server(srv -> {
                    int n = 0;
                    for (ItemEntity e : aetheria(srv).getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(X + 8, FLOOR + 1, Z + 4)).inflate(3))) {
                        if (e.getItem().is(Jellies.CANDIED_GEL.get())) {
                            n += e.getItem().getCount();
                        }
                    }
                    return n >= 1;
                }))
                .run("cooked", () -> summary.add("a smoker (100 ticks), a furnace (200) and a campfire (600) each made candied gel"));
    }

    private <T extends AbstractCookingRecipe> void expect(Optional<RecipeHolder<T>> found, int ticks, String what) {
        if (found.isEmpty()) {
            throw new Steps.Failure("no " + what + " recipe for drift gel");
        }
        AbstractCookingRecipe r = found.get().value();
        ItemStack out = r.getResultItem(null);
        if (!out.is(Jellies.CANDIED_GEL.get()) || r.getCookingTime() != ticks) {
            throw new Steps.Failure(what + " recipe: " + out + " in " + r.getCookingTime());
        }
        summary.add(what + ": candied gel in " + r.getCookingTime() + " ticks");
    }

    // ------------------------------------------------------------------ bloom

    private final List<Double> meanY = new CopyOnWriteArrayList<>();
    private volatile int bloomSize;
    private volatile long bloomLifeTicks;
    private volatile double bloomStartY;

    private void bloom(Steps steps, Minecraft mc) {
        FrameStats.install();
        setup(steps, mc);
        steps
                .command("gamemode creative")
                .command("cosmicbreach debug jelly clear")
                // a quiet reference: what the server and the client cost with none
                .command("cosmicbreach stress measure")
                .run("frames: none", () -> {
                    FrameStats.reset();
                    FrameStats.enabled = true;
                })
                .waitTicks(200)
                .run("the reference", () -> {
                    FrameStats.enabled = false;
                    StressScene.Stats s = StressScene.stats();
                    s.stop();
                    emptyTick = s.tickMedianMs();
                    emptyFrame = FrameStats.cpuMedianMs();
                    summary.add(String.format(Locale.ROOT, "none: server tick median %.2f ms; client %s", emptyTick, FrameStats.report()));
                })
                // 25 still jellies in view: the render cost of the swarm
                .run("25 still jellies in front of the player", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    RandomSource random = RandomSource.create(5L);
                    for (int i = 0; i < 25; i++) {
                        spawn(srv, -22 + random.nextInt(45), FLOOR + 4 + random.nextInt(28), 8 + random.nextInt(24), false);
                    }
                    put(srv, X + 0.5, FLOOR + 1, Z - 6.5, 0f, 0f);
                    return null;
                }))
                .look(0f, -8f)
                .waitUntil("the swarm is drawn", 900, CryptKit.settled(mc, 600))
                .run("frames: 25", () -> {
                    FrameStats.reset();
                    FrameStats.enabled = true;
                })
                .waitTicks(200)
                .run("the swarm's frame", () -> {
                    FrameStats.enabled = false;
                    swarmFrame = FrameStats.cpuMedianMs();
                    summary.add(String.format(Locale.ROOT, "25 in view: client %s", FrameStats.report()));
                })
                .screenshot("jelly_swarm_view")
                .run("clear them", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    return null;
                }))
                // the real thing: a bloom of 25, AI on, from under the layer
                .run("a bloom of 25", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    ServerPlayer p = CryptKit.player(srv);
                    List<DriftJelly> placed = JellyBloom.start(level, p.position(), 25);
                    bloomSize = placed.size();
                    meanY.clear();
                    double sum = 0;
                    for (DriftJelly j : placed) {
                        sum += j.getY();
                        if (!j.inBloom() || j.getY() > 15.0) {
                            throw new Steps.Failure("a bloom jelly outside the plan: " + j.getY());
                        }
                    }
                    bloomStartY = sum / Math.max(1, placed.size());
                    summary.add(String.format(Locale.ROOT, "bloom: %d placed, mean start height %.1f", bloomSize, bloomStartY));
                    if (bloomSize < 15) {
                        throw new Steps.Failure("a bloom is at least 15: " + bloomSize);
                    }
                    return null;
                }))
                .waitTicks(300)             // warm up (the JIT, the first collisions), then measure
                .run("reset the timing", () -> CryptKit.server(srv -> {
                    for (DriftJelly j : aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), e -> true)) {
                        j.resetTiming();
                    }
                    return null;
                }))
                .command("cosmicbreach stress measure")
                .waitTicks(300)
                .run("the cost of the bloom while it rises", () -> {
                    StressScene.Stats s = StressScene.stats();
                    s.stop();
                    var type = net.minecraft.resources.ResourceLocation.parse("cosmicbreach:drift_jelly");
                    double micros = CryptKit.server(srv -> aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), e -> true).stream()
                            .mapToDouble(DriftJelly::meanStepNanos).average().orElse(0) / 1000.0);
                    summary.add(String.format(Locale.ROOT, "bloom of %d: server tick median %.2f ms (none: %.2f), jelly entities %.3f ms a tick (%.1f a tick), "
                            + "mean per-jelly aiStep %.1f microseconds", bloomSize, s.tickMedianMs(), emptyTick, s.typeMsPerTick(type), s.typeCount(type), micros));
                    if (s.typeMsPerTick(type) > 0.6) {
                        throw new Steps.Failure("25 jellies cost over 0.6 ms a tick: " + s.typeMsPerTick(type));
                    }
                })
                // the plan, fast: the server runs the rest of the bloom at once
                .run("sample the plan", () -> CryptKit.server(srv -> {
                    meanY.add(meanBloomY(srv));
                    return null;
                }))
                ;
        sprint(steps, 1800);
        steps.run("sample at 30 percent", () -> CryptKit.server(srv -> {
            meanY.add(meanBloomY(srv));
            return null;
        }));
        sprint(steps, 2000);
        steps.run("sample at 60 percent", () -> CryptKit.server(srv -> {
            meanY.add(meanBloomY(srv));
            return null;
        }));
        sprint(steps, 2000);
        steps.run("sample at 90 percent", () -> CryptKit.server(srv -> {
            meanY.add(meanBloomY(srv));
            return null;
        }));
        sprint(steps, 2800);
        // a jelly that drifted out past the simulation distance stops ticking where it is and finishes its plan on its first
        // tick back in range, so only the ones still ticking must be gone; the rest are listed
        steps.waitUntil("the bloom is gone (every jelly still ticking)", 300, () -> CryptKit.server(srv -> aetheria(srv)
                        .getEntities(Jellies.DRIFT_JELLY.get(), e -> aetheria(srv).isPositionEntityTicking(e.blockPosition())).isEmpty()))
                .log("left over", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    StringBuilder b = new StringBuilder("jellies left (out of ticking range): ");
                    int n = 0;
                    for (DriftJelly j : aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), e -> true)) {
                        n++;
                        b.append(String.format(Locale.ROOT, "[%.0f %.1f %.0f, %.0f from the player, ticking %s] ", j.getX(), j.getY(), j.getZ(),
                                j.distanceTo(p), aetheria(srv).isPositionEntityTicking(j.blockPosition())));
                    }
                    return n == 0 ? b.append("none").toString() : b.toString();
                }))
                .run("the plan", () -> {
                    summary.add("bloom mean heights through its life: " + meanY);
                    if (meanY.size() < 4 || !(meanY.get(1) > bloomStartY + 35.0 && meanY.get(1) >= meanY.get(0))) {
                        throw new Steps.Failure("it did not rise: " + meanY);
                    }
                    if (meanY.get(3) > 0 && !(meanY.get(3) < meanY.get(2) - 5.0)) {
                        throw new Steps.Failure("it did not sink away: " + meanY);
                    }
                    if (meanY.get(3) < 0) {
                        summary.add("(by 90 percent of the longest life every jelly was already gone: they had drifted beyond the despawn distance of a still player)");
                    }
                
                    summary.add(String.format(Locale.ROOT, "client frame: none %.2f ms, 25 in view %.2f ms (%+.2f)", emptyFrame, swarmFrame, swarmFrame - emptyFrame));
                });
    }

    private long sprintFrom;

    /** Runs {@code ticks} server ticks at once ({@code /tick sprint}) and waits until the game time has moved that far. */
    private void sprint(Steps steps, int ticks) {
        steps.run("note the time", () -> sprintFrom = CryptKit.server(srv -> aetheria(srv).getGameTime()))
                .command("tick sprint " + ticks)
                .waitUntil("the server has run " + ticks + " ticks", ticks + 1200,
                        () -> CryptKit.server(srv -> aetheria(srv).getGameTime() >= sprintFrom + ticks));
    }

    private double emptyTick;
    private double emptyFrame;
    private double swarmFrame;

    private double meanBloomY(MinecraftServer srv) {
        List<DriftJelly> all = new ArrayList<>(aetheria(srv).getEntities(Jellies.DRIFT_JELLY.get(), DriftJelly::inBloom));
        if (all.isEmpty()) {
            return -1.0;
        }
        double sum = 0;
        for (DriftJelly j : all) {
            sum += j.getY();
        }
        return sum / all.size();
    }

    // ------------------------------------------------------------------ spawns

    private void spawns(Steps steps, Minecraft mc) {
        int sx = 3000;
        int sz = 3000;
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("time set 6000")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s " + sx + ".5 90 " + sz + ".5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitUntil("the Deep is drawn", 1500, CryptKit.settled(mc, 1200))
                .run("sample the spawner's positions over the real Deep", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    RandomSource random = RandomSource.create(77L);
                    int[] seen = new int[3];       // land 2..5, other ground, void
                    int[] took = new int[3];
                    int air = 0;
                    int tries = 0;
                    for (int cx = -5; cx <= 5; cx++) {
                        for (int cz = -5; cz <= 5; cz++) {
                            ChunkAccess chunk = level.getChunk((sx >> 4) + cx, (sz >> 4) + cz);
                            for (int k = 0; k < 400; k++) {
                                int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
                                int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
                                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                                int y = net.minecraft.util.Mth.randomBetweenInclusive(random, level.getMinBuildHeight(), top);
                                BlockPos pos = new BlockPos(x, y, z);
                                tries++;
                                if (!level.getBlockState(pos).isAir() || !level.noCollision(Jellies.DRIFT_JELLY.get().getSpawnAABB(x + 0.5, y, z + 0.5))) {
                                    continue;
                                }
                                if (y < 8 || y >= 160) {
                                    continue;      // other layers and the floor are the rule's, tested below
                                }
                                air++;
                                int depth = DriftJelly.groundDepth(level, pos);
                                int cat = depth >= 2 && depth <= 5 ? 0 : depth >= 0 ? 1 : 2;
                                seen[cat]++;
                                if (Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), level, MobSpawnType.NATURAL, pos, random)) {
                                    took[cat]++;
                                }
                            }
                        }
                    }
                    int accepted = took[0] + took[1] + took[2];
                    summary.add(String.format(Locale.ROOT, "spawn placement over %d tries (%d in the Deep's air): ground 2 to 5 below %d seen, %d taken; other ground %d seen, %d taken; void %d seen, %d taken",
                            tries, air, seen[0], took[0], seen[1], took[1], seen[2], took[2]));
                    summary.add(String.format(Locale.ROOT, "of the %d taken: %.0f percent over ground at 2 to 5, %.0f percent over other ground, %.0f percent over void", accepted,
                            100.0 * took[0] / Math.max(1, accepted), 100.0 * took[1] / Math.max(1, accepted), 100.0 * took[2] / Math.max(1, accepted)));
                    if (seen[0] < 20 || seen[2] < 100) {
                        throw new Steps.Failure("too little terrain sampled to say: " + java.util.Arrays.toString(seen));
                    }
                    if (took[0] < 0.55 * accepted) {
                        throw new Steps.Failure("most spawns should hover 2 to 5 blocks over ground: " + took[0] + " of " + accepted + "; " + String.join(" / ", summary));
                    }
                    if (took[2] == 0 && seen[2] > 5000) {
                        summary.add("(no void spawn taken in this sample; the rate is 1 in 500)");
                    }
                    // the rule's edges
                    BlockPos inDeep = new BlockPos(sx, 90, sz);
                    BlockPos inDrift = new BlockPos(sx, 200, sz);
                    BlockPos belowFloor = new BlockPos(sx, 4, sz);
                    if (Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), level, MobSpawnType.NATURAL, inDrift, random)
                            || Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), level, MobSpawnType.NATURAL, belowFloor, random)) {
                        throw new Steps.Failure("natural spawns are layer 3's, off its floor");
                    }
                    if (!Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), level, MobSpawnType.COMMAND, inDrift, random)) {
                        throw new Steps.Failure("a command may spawn it anywhere");
                    }
                    if (Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), srv.overworld(), MobSpawnType.NATURAL, inDeep, random)) {
                        throw new Steps.Failure("never in another dimension");
                    }
                    return null;
                }))
                // and the real thing: the spawner at work for a few minutes of game time
                .command("gamerule doMobSpawning true");
        sprint(steps, 4800);
        steps.run("natural spawns", () -> CryptKit.server(srv -> {
                    ServerLevel level = aetheria(srv);
                    List<? extends DriftJelly> all = level.getEntities(Jellies.DRIFT_JELLY.get(), e -> true);
                    int land = 0;
                    int voids = 0;
                    int other = 0;
                    for (DriftJelly j : all) {
                        int depth = DriftJelly.groundDepth(level, j.blockPosition());
                        if (depth >= 2 && depth <= 5) {
                            land++;
                        } else if (depth < 0) {
                            voids++;
                        } else {
                            other++;
                        }
                    }
                    summary.add(String.format(Locale.ROOT, "natural spawning for 4800 ticks: %d jellies (%d over ground at 2 to 5, %d over other ground, %d over void)", all.size(), land, other, voids));
                    if (all.isEmpty()) {
                        throw new Steps.Failure("nothing spawned in 4800 ticks");
                    }
                    return null;
                }))
                .command("cosmicbreach debug jelly clear");
    }

    // ------------------------------------------------------------------ zones

    private static final String[] ZONE_NAMES = {"spans", "lichen_gardens", "hanging_wood", "shattered_field"};
    private static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> ZONE_BIOMES = List.of(
            AetheriaWorld.RIFT_ABYSS, AetheriaWorld.LICHEN_GARDENS, AetheriaWorld.HANGING_WOOD, AetheriaWorld.SHATTERED_FIELD);
    private static final int ZONE_SPRINT = 2400;

    /** A pillar well inside zone {@code zone} (away from the Breach and with the zone all round it for 90 blocks). */
    private static @Nullable BlockPos zoneSpot(com.cosmicbreach.world.gen.AetheriaTerrain t, int zone) {
        for (int ring = 7; ring < 60; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int[] c : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                    com.cosmicbreach.world.gen.DeepSpans.Pillar p = t.deep.pillar(c[0], c[1]);
                    if (!p.exists || p.zone != zone || Math.hypot(p.cx, p.cz) < com.cosmicbreach.world.gen.DeepZones.HOME_RADIUS + 150) {
                        continue;
                    }
                    boolean inside = t.zones.zoneAt(p.cx, p.cz) == zone;
                    for (int a = 0; a < 360 && inside; a += 45) {
                        double r = Math.toRadians(a);
                        inside = t.zones.zoneAt(p.cx + Math.cos(r) * 90, p.cz + Math.sin(r) * 90) == zone;
                    }
                    if (inside) {
                        return BlockPos.containing(p.cx, 100, p.cz);
                    }
                }
            }
        }
        return null;
    }

    private void zones(Steps steps, Minecraft mc) {
        BlockPos[] spot = {null};
        int[] naturalTotal = {0};
        steps.command("gamerule sendCommandFeedback false")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("gamerule doMobSpawning false")
                .command("time set 6000")
                .command("difficulty normal")
                .command("gamemode creative")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 700 120 700")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && mc.level.dimension() == AetheriaWorld.LEVEL && mc.player != null)
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                });
        for (int zone = 0; zone < ZONE_NAMES.length; zone++) {
            int z = zone;
            String name = ZONE_NAMES[z];
            var biomeKey = ZONE_BIOMES.get(z);
            steps.run("find " + name, () -> CryptKit.server(srv -> {
                        clearJellies(srv);
                        BlockPos at = zoneSpot(com.cosmicbreach.world.AetheriaSpots.terrain(aetheria(srv)), z);
                        if (at == null) {
                            throw new Steps.Failure("no spot found inside " + name);
                        }
                        spot[0] = at;
                        ServerPlayer p = CryptKit.player(srv);
                        p.teleportTo(aetheria(srv), at.getX() + 0.5, 150, at.getZ() + 0.5, 0f, 0f);
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                        p.setDeltaMovement(Vec3.ZERO);
                        return null;
                    }))
                    .waitUntil(name + " is drawn", 1500, CryptKit.settled(mc, 1200))
                    .run("spawn settings and placement in " + name, () -> CryptKit.server(srv -> {
                        ServerLevel level = aetheria(srv);
                        BlockPos at = spot[0];
                        var biome = level.getBiome(at.atY(90));
                        if (!biome.is(biomeKey)) {
                            throw new Steps.Failure(name + ": the spot's biome is " + biome.unwrapKey().map(k -> k.location().toString()).orElse("?"));
                        }
                        int weight = 0;
                        for (var data : biome.value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.AMBIENT).unwrap()) {
                            if (data.type == Jellies.DRIFT_JELLY.get()) {
                                weight += data.getWeight().asInt();
                            }
                        }
                        boolean dense = z == com.cosmicbreach.world.gen.DeepZones.HANGING || z == com.cosmicbreach.world.gen.DeepZones.SHATTERED;
                        if (weight <= 0 || (dense && weight <= 6) || (!dense && weight > 6)) {
                            throw new Steps.Failure(name + ": jelly spawn weight " + weight + (dense ? " (dense zone)" : ""));
                        }
                        RandomSource random = RandomSource.create(91L + z);
                        int air = 0;
                        int took = 0;
                        int land = 0;
                        for (int cx = -3; cx <= 3; cx++) {
                            for (int cz = -3; cz <= 3; cz++) {
                                ChunkAccess chunk = level.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
                                for (int k = 0; k < 400; k++) {
                                    int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
                                    int zz = chunk.getPos().getMinBlockZ() + random.nextInt(16);
                                    int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, zz) + 1;
                                    int y = net.minecraft.util.Mth.randomBetweenInclusive(random, level.getMinBuildHeight(), top);
                                    BlockPos pos = new BlockPos(x, y, zz);
                                    if (y < 8 || y >= 160 || !level.getBlockState(pos).isAir()
                                            || !level.noCollision(Jellies.DRIFT_JELLY.get().getSpawnAABB(x + 0.5, y, zz + 0.5))
                                            || !level.getBiome(pos).is(biomeKey)) {
                                        continue;
                                    }
                                    air++;
                                    if (Jellies.canSpawn(Jellies.DRIFT_JELLY.get(), level, MobSpawnType.NATURAL, pos, random)) {
                                        took++;
                                        int depth = DriftJelly.groundDepth(level, pos);
                                        if (depth >= 2 && depth <= 5) {
                                            land++;
                                        }
                                    }
                                }
                            }
                        }
                        summary.add(String.format(Locale.ROOT, "%s at %s: spawn weight %d; placement: %d positions in the zone's air, %d taken (%d over ground at 2 to 5)",
                                name, at.toShortString(), weight, air, took, land));
                        if (took == 0) {
                            throw new Steps.Failure(name + ": the spawn rule took none of " + air + " positions");
                        }
                        return null;
                    }))
                    .command("gamerule doMobSpawning true");
            sprint(steps, ZONE_SPRINT);
            steps.command("gamerule doMobSpawning false")
                    .run("natural jellies in " + name, () -> CryptKit.server(srv -> {
                        ServerLevel level = aetheria(srv);
                        int inZone = 0;
                        int other = 0;
                        for (DriftJelly j : level.getEntities(Jellies.DRIFT_JELLY.get(), e -> true)) {
                            if (level.getBiome(j.blockPosition()).is(biomeKey)) {
                                inZone++;
                            } else {
                                other++;
                            }
                        }
                        naturalTotal[0] += inZone;
                        summary.add(String.format(Locale.ROOT, "%s: natural spawning for %d ticks gave %d jellies in the zone (%d elsewhere)",
                                name, ZONE_SPRINT, inZone, other));
                        return null;
                    }))
                    .command("cosmicbreach debug jelly clear");
        }
        steps.run("jellies spawned naturally in the zones", () -> {
            if (naturalTotal[0] == 0) {
                throw new Steps.Failure("no jelly spawned naturally in any zone");
            }
        });
    }

    // ------------------------------------------------------------------ looks

    private void looks(Steps steps, Minecraft mc) {
        setup(steps, mc);
        steps.command("gamemode creative")
                .run("a jelly 4 blocks up", () -> CryptKit.server(srv -> {
                    clearJellies(srv);
                    DriftJelly j = spawn(srv, 0, FLOOR + 5, 0, false);
                    j.setYRot(0f);
                    j.yBodyRot = 0f;
                    return null;
                }));
        for (Object[] shot : new Object[][] {{"front", 0.0, 6.0, 10.0}, {"threequarter", 40.0, 14.0, 10.0}, {"side", 90.0, 4.0, 10.0},
                {"below", 25.0, -30.0, 7.0}, {"above", 25.0, 50.0, 9.0}}) {
            view(steps, mc, (Double) shot[1], (Double) shot[2], (Double) shot[3]);
            steps.screenshot("jelly_" + shot[0]);
        }
        view(steps, mc, 40.0, 10.0, 10.0);
        steps.run("hurt it", () -> CryptKit.server(srv -> {
                    DriftJelly j = needJelly(srv);
                    j.hurt(aetheria(srv).damageSources().generic(), 1.0f);
                    return null;
                }))
                .waitTicks(4)
                .screenshot("jelly_hurt")
                .waitTicks(60)
                .run("kill it", () -> CryptKit.server(srv -> {
                    needJelly(srv).hurt(aetheria(srv).damageSources().generic(), 1000f);
                    return null;
                }))
                .waitTicks(9)
                .screenshot("jelly_dying_a")
                .waitTicks(8)
                .screenshot("jelly_dying_b");
    }

    /** The camera on a circle round the jelly's middle: azimuth from the front, elevation, distance in blocks; settled and aimed. */
    private void view(Steps steps, Minecraft mc, double azimuthDeg, double elevationDeg, double distance) {
        Vec3 centre = new Vec3(X + 0.5, FLOOR + 5 + 0.6, Z + 0.5);
        double az = Math.toRadians(azimuthDeg);
        double el = Math.toRadians(elevationDeg);
        Vec3 eye = centre.add(Math.sin(az) * Math.cos(el) * distance, Math.sin(el) * distance, -Math.cos(az) * Math.cos(el) * distance);
        steps.run("camera at " + azimuthDeg + " " + elevationDeg, () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    put(srv, eye.x, eye.y - 1.62, eye.z, 0f, 0f);
                    return null;
                }))
                .waitTicks(6)
                .run("aim", () -> CryptKit.aim(mc, centre))
                .waitTicks(24);
    }
}
