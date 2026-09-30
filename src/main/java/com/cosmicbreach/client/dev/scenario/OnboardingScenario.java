package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.onboarding.BreachRenderer;
import com.cosmicbreach.client.onboarding.FallUpClient;
import com.cosmicbreach.client.onboarding.StarfallSky;
import com.cosmicbreach.onboarding.BreachBlock;
import com.cosmicbreach.onboarding.BreachFrameBlock;
import com.cosmicbreach.onboarding.BreachRing;
import com.cosmicbreach.onboarding.Codex;
import com.cosmicbreach.onboarding.FallUp;
import com.cosmicbreach.onboarding.FallenRiftLayout;
import com.cosmicbreach.onboarding.FallenRiftPiece;
import com.cosmicbreach.onboarding.LandingLayout;
import com.cosmicbreach.onboarding.Landings;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.onboarding.StarfallSchedule;
import com.cosmicbreach.onboarding.Starfalls;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * The way in (W4), the whole route in a fresh normal world, timed:
 *
 * <ol>
 *   <li>STARFALL: the clock set to 11,900 on day one; the first Starfall falls inside the sunset window and
 *       lands 48 to 96 blocks away (both measured); screenshots of the streak in flight, the pillar from 60
 *       blocks at dusk, the crater; the shard broken by hand (survival, empty hand); the Codex arrives with it
 *       and opens on "Build a ring" (screenshot).</li>
 *   <li>RING: 12 frames crafted from 8 copper and 10 cobblestone at a crafting table (two crafts of 6, through
 *       the menu's slot clicks); the 4 by 4 ring placed with the use key, one frame a block too far east first
 *       (the action bar hint names the missing corner), fixed with a stone pickaxe; the shard opens it: all four
 *       blocks of the 2 by 2 middle become the Breach; the Breach from above and straight down; stepping in
 *       through its south-east block; the arrival in Aetheria 30 blocks over an island with Slow Falling and
 *       the 6 by 6 Landing built round its 4 by 4 return ring; after the cooldown, the return Breach; home
 *       beside the Overworld ring; breaking a frame closes all four blocks.</li>
 *   <li>RIFT: {@code /locate structure cosmicbreach:fallen_rift}, its eight frames of twelve (mineable with a
 *       stone pickaxe, the gaps open) and its chest's loot through the chest's own menu, screenshots.</li>
 * </ol>
 * The tick count of every stage goes to the log at the end.
 */
public final class OnboardingScenario implements Scenario {
    // the helpers without `private` are shared with VoiceScenario
    public enum Part { STARFALL, RING, RIFT }

    private final EnumSet<Part> parts;

    public OnboardingScenario() {
        this.parts = EnumSet.allOf(Part.class);
    }

    public OnboardingScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public boolean normalWorld() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 330;
    }

    /** What the steps learn as they go. */
    private static final class State {
        final List<String> stages = new ArrayList<>();
        long mark;
        Vec3 start;
        BlockPos shard;
        BlockPos ring;
        int groundY;
        BlockPos rift;
        BlockPos riftChest;
        Vec3 arrival;
        BlockPos landing;
        BlockPos broken;
        long arrivedAt;
        Component hint;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        State s = new State();
        steps.run("clouds off (the streak and the pillar in clear sky)", () -> mc.options.cloudStatus().set(CloudStatus.OFF))
                .command("gamemode survival")
                .command("difficulty peaceful")
                .run("start the clock", () -> s.mark = gameTime(mc));
        if (parts.contains(Part.STARFALL)) {
            starfall(steps, mc, s);
        } else {
            steps.command("give @s cosmicbreach:starfall_shard").waitTicks(5);
        }
        if (parts.contains(Part.RING)) {
            ring(steps, mc, s);
        }
        if (parts.contains(Part.RIFT)) {
            rift(steps, mc, s);
        }
        steps.log("stages (ticks)", () -> "stages: " + String.join("; ", s.stages));
    }

    // ------------------------------------------------------------------ the Starfall

    private void starfall(Steps steps, Minecraft mc, State s) {
        steps.waitTicks(40)
                .check("a new player's first Starfall is planned for the first sunset (12,000 to 12,500)", () -> {
                    long due = server(srv -> player(srv).getData(OnboardingRegistry.STATE).nextFall(), 5);
                    int tod = StarfallSchedule.timeOfDay(due);
                    return due >= 0 && tod >= 12_000 && tod < 12_500;
                })
                .run("step up into the open (a spawn under a tree would hide the sky)", () -> tp(server(srv -> {
                    ServerPlayer p = player(srv);
                    int y = srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
                    return new Vec3(p.getBlockX() + 0.5, y, p.getBlockZ() + 0.5);
                }, 5)))
                .waitTicks(10)
                .run("remember where the player stands", () -> s.start = mc.player.position())
                .command("time set 11900")
                .command("gamerule doDaylightCycle true")
                .run("mark", () -> stage(mc, s, "setup"))
                .waitUntil("the streak reaches this client", 900, () -> StarfallSky.seen() > 0)
                .run("mark", () -> stage(mc, s, "sunset to the streak"))
                .log("the fall", () -> String.format(Locale.ROOT, "streak seen at day time %d, head at %s",
                        dayTime(mc), StarfallSky.head()))
                .waitTicks(24)
                .run("look at the streak", () -> {
                    Vec3 head = StarfallSky.head();
                    if (head == null) {
                        throw new Steps.Failure("no streak on screen");
                    }
                    aim(mc, head.add(0, 6, 0));
                })
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("starfall_streak")
                .waitUntil("the shard lands", 80, () -> server(srv -> Starfalls.last() != null, 5))
                .run("mark", () -> stage(mc, s, "the streak (to the impact)"))
                .run("read the landing", () -> {
                    Starfalls.Fall f = server(srv -> Starfalls.last(), 5);
                    s.shard = f.shard();
                    CosmicBreach.LOGGER.info("[autotest] Starfall fell at day time {} (time of day {}), shard at {}, {} blocks from the player, crater radius {}",
                            f.dayTime(), StarfallSchedule.timeOfDay(f.dayTime()), f.shard().toShortString(),
                            String.format(Locale.ROOT, "%.1f", f.distance()), f.radius());
                })
                .log("litter", () -> "items round the crater: " + server(srv -> {
                    var items = srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(s.shard).inflate(8));
                    List<String> names = new ArrayList<>();
                    for (var e : items) {
                        names.add(e.getItem().getCount() + " " + e.getItem().getItem() + " at " + e.blockPosition().toShortString());
                    }
                    return names.toString();
                }, 5))
                .check("it fell inside the first-sunset window", () -> {
                    int tod = StarfallSchedule.timeOfDay(server(srv -> Starfalls.last().dayTime(), 5));
                    return tod >= 12_000 && tod < 12_500;
                })
                .check("it landed 48 to 96 blocks from the player", () -> {
                    double d = server(srv -> Starfalls.last().distance(), 5);
                    return d >= 47.5 && d <= 96.5;
                })
                .check("the shard block glows in its crater, lined with Starfall Stone", () -> server(srv -> {
                    ServerLevel ow = srv.overworld();
                    BlockState below = ow.getBlockState(s.shard.below());
                    return ow.getBlockState(s.shard).is(OnboardingRegistry.STARFALL_SHARD_BLOCK.get())
                            && ow.getLightEmission(s.shard) == 15
                            && (below.is(ModBlocks.STARFALL_STONE.get()) || below.is(ModBlocks.METEORITE.get()));
                }, 5))
                .check("the next Starfall is planned for the next night", () -> {
                    long due = server(srv -> player(srv).getData(OnboardingRegistry.STATE).nextFall(), 5);
                    int tod = StarfallSchedule.timeOfDay(due);
                    return due >= StarfallSchedule.DAY && tod >= 13_000 && tod < 18_000;
                })
                // the pillar from 60 blocks, back toward where the player stood (a spectator, for a clear view)
                .command("gamemode spectator")
                .run("stand 60 blocks from the shard", () -> server(srv -> {
                    ServerPlayer p = player(srv);
                    Vec3 away = new Vec3(s.start.x - s.shard.getX() - 0.5, 0, s.start.z - s.shard.getZ() - 0.5).normalize();
                    int x = Mth.floor(s.shard.getX() + 0.5 + away.x * 60);
                    int z = Mth.floor(s.shard.getZ() + 0.5 + away.z * 60);
                    int y = srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    p.teleportTo(srv.overworld(), x + 0.5, y + 1.5, z + 0.5, java.util.Set.of(), p.getYRot(), 0);
                    return null;
                }, 10))
                .waitTicks(50)
                .run("look at the pillar", () -> aim(mc, Vec3.atCenterOf(s.shard).add(0, 14, 0)))
                .log("the pillar", () -> String.format(Locale.ROOT, "%.1f blocks from the shard at day time %d",
                        horizontal(mc.player.position(), Vec3.atCenterOf(s.shard)), dayTime(mc)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("starfall_pillar_60")
                // the crater
                .run("stand over the crater, in clear sight of it", () -> tp(server(srv ->
                        viewpoint(srv.overworld(), Vec3.atCenterOf(s.shard), new double[] {5, 7, 4}, new double[] {4, 6, 3}), 10)))
                .waitTicks(20)
                .run("look into the crater", () -> aim(mc, Vec3.atCenterOf(s.shard)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("starfall_crater")
                .run("mark", () -> stage(mc, s, "screenshots"))
                // walk down and break it by hand
                .command("gamemode survival")
                .run("stand on the crater's edge", () -> tp(server(srv -> standNear(srv.overworld(), s.shard), 5)))
                .waitTicks(10)
                .run("an empty hand", () -> emptyHand(mc))
                .waitTicks(2)
                .check("the hand is empty", () -> mc.player.getMainHandItem().isEmpty())
                .run("look at the shard", () -> aim(mc, Vec3.atCenterOf(s.shard)))
                .waitTicks(1)
                .log("aiming", () -> "at " + mc.player.position() + ", the crosshair on " + (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b
                        ? mc.level.getBlockState(b.getBlockPos()) + " at " + b.getBlockPos().toShortString() : String.valueOf(mc.hitResult)))
                .hold(mc.options.keyAttack)
                .waitUntil("the shard breaks by hand", 20, () -> mc.level.getBlockState(s.shard).isAir())
                .release(mc.options.keyAttack)
                .run("mark", () -> stage(mc, s, "breaking the shard by hand"))
                .hold(mc.options.keyUp)
                .waitUntil("the shard is picked up (walking to where it dropped)", 100, () -> {
                    walkToward(mc, OnboardingRegistry.STARFALL_SHARD.get());
                    return count(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 1;
                })
                .release(mc.options.keyUp)
                .waitUntil("the Codex arrives with it", 40, () -> count(mc, OnboardingRegistry.STARFALL_CODEX.get()) == 1)
                .run("mark", () -> stage(mc, s, "pickup and the Codex"))
                .run("hold the Codex", () -> select(mc, OnboardingRegistry.STARFALL_CODEX.get()))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the Codex opens on Build a ring", 100, () -> mc.screen instanceof guideme.internal.screen.GuideScreen g
                        && Codex.BUILD_A_RING.equals(g.getCurrentPageId()))
                .waitTicks(30)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("codex_build_a_ring")
                .run("close the Codex", () -> mc.screen.onClose())
                .waitUntil("the Codex closed", 20, () -> mc.screen == null)
                .run("mark", () -> stage(mc, s, "reading the Codex"));
    }

    // ------------------------------------------------------------------ the ring, the Breach, Aetheria and home

    private void ring(Steps steps, Minecraft mc, State s) {
        steps.command("give @s minecraft:copper_ingot 8")
                .command("give @s minecraft:cobblestone 10")
                .command("give @s minecraft:stone_pickaxe")
                .waitTicks(5)
                .run("choose a flat spot for the ring and a crafting table", () -> server(srv -> {
                    ServerPlayer p = player(srv);
                    ServerLevel ow = srv.overworld();
                    BlockPos near = s.shard != null ? s.shard.offset(9, 0, 0) : p.blockPosition().offset(7, 0, 0);
                    int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()) - 1;
                    // the ring's origin: the north-west block of its 2 by 2 middle, on the ground
                    s.ring = new BlockPos(near.getX(), y + 1, near.getZ());
                    s.groundY = y;
                    return null;
                }, 10))
                .run("level the ground there", () -> {
                    int x = s.ring.getX();
                    int z = s.ring.getZ();
                    int y = s.groundY;
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", x - 4, y - 2, z - 4, x + 5, y, z + 6));
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x - 4, y + 1, z - 4, x + 5, y + 5, z + 6));
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", x - 1, y + 1, z + 5));
                })
                .waitTicks(10)
                .run("stand south of the ring site", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY(), s.ring.getZ() + 4.0)))
                .waitTicks(20)
                .run("mark", () -> stage(mc, s, "to the ring site"))
                // two crafts at the crafting table, through the menu's clicks
                .run("look at the crafting table", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(-1, 0, 5)).add(0, 0.5, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the crafting table opens", 40, () -> mc.screen instanceof CraftingScreen)
                .run("lay out copper and cobblestone", () -> layOutFrames(mc))
                .waitUntil("the table shows 6 frames", 20, () -> resultIsFrames(mc))
                .run("take them (shift-click)", () -> click(mc, mc.player.containerMenu.containerId, 0, 0, ClickType.QUICK_MOVE))
                .waitTicks(3)
                .run("lay out the second craft", () -> layOutFrames(mc))
                .waitUntil("the table shows 6 frames again", 20, () -> resultIsFrames(mc))
                .run("take them too", () -> click(mc, mc.player.containerMenu.containerId, 0, 0, ClickType.QUICK_MOVE))
                .waitTicks(5)
                .check("12 frames from 8 copper and 10 cobblestone", () -> count(mc, ModBlocks.BREACH_FRAME.get().asItem()) == 12
                        && count(mc, Items.COPPER_INGOT) == 0 && count(mc, Items.COBBLESTONE) == 0)
                .run("close the table", () -> mc.player.closeContainer())
                .waitTicks(5)
                .run("mark", () -> stage(mc, s, "crafting"))
                // laid from the middle of the ring: the corners first (while the sides are empty, nothing is in the
                // way of the aim), the north-east corner's frame put down one block too far east by mistake
                .run("stand in the middle of the ring site", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY(), s.ring.getZ() + 1.0)))
                .waitTicks(10)
                .run("hold the frames", () -> select(mc, ModBlocks.BREACH_FRAME.get().asItem()))
                .waitTicks(2);
        for (int[] o : new int[][] {{-1, -1}, {-1, 2}, {2, 2}, {3, -1}, {0, -1}, {1, -1}, {-1, 0}, {-1, 1}, {2, 0}, {2, 1}, {0, 2}, {1, 2}}) {
            placeFrame(steps, mc, s, o[0], o[1]);
        }
        steps.check("the wrong frame stands outside the ring", () -> mc.level.getBlockState(s.ring.offset(3, 0, -1)).is(ModBlocks.BREACH_FRAME.get()))
                .run("hold the shard", () -> select(mc, OnboardingRegistry.STARFALL_SHARD.get()))
                .waitTicks(2)
                .run("look at a south frame", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(0, 0, 2)).add(0, 0.5, 0)))
                .run("forget the last action bar message", () -> clearActionBar(mc))
                .press(mc.options.keyUse)
                .waitUntil("the action bar says what is wrong", 20, () -> {
                    s.hint = actionBar(mc);
                    return s.hint != null && !s.hint.getString().isEmpty();
                })
                .log("the hint", () -> "hint: " + s.hint.getString())
                .check("the hint names the missing north-east corner", () -> s.hint.getString().contains("north-east corner"))
                .check("the shard is not used up", () -> count(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 1)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("ring_hint")
                // fix it: pick the stray frame up with a stone pickaxe and put it in the corner
                .run("hold the stone pickaxe", () -> select(mc, Items.STONE_PICKAXE))
                .waitTicks(2)
                .run("look at the stray frame", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(3, 0, -1)).add(0, 0.5, 0)))
                .hold(mc.options.keyAttack)
                .waitUntil("the stray frame breaks", 80, () -> mc.level.getBlockState(s.ring.offset(3, 0, -1)).isAir())
                .release(mc.options.keyAttack)
                .waitTicks(10)
                .run("walk over to the dropped frame", () -> tp(server(srv -> droppedNear(srv, s.ring.offset(3, 0, -1),
                        ModBlocks.BREACH_FRAME.get().asItem()), 5)))
                .waitUntil("the frame is picked up", 60, () -> count(mc, ModBlocks.BREACH_FRAME.get().asItem()) == 1)
                // the corner can't be reached past the frames from inside: from the north-east, outside the ring
                .run("stand north-east of the ring", () -> tp(new Vec3(s.ring.getX() + 3.5, s.ring.getY(), s.ring.getZ() - 2.5)))
                .waitTicks(10)
                .run("hold the frame", () -> select(mc, ModBlocks.BREACH_FRAME.get().asItem()))
                .waitTicks(2);
        placeFrame(steps, mc, s, 2, -1);
        steps.run("stand south of the ring", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY(), s.ring.getZ() + 3.6)))
                .waitTicks(10)
                .run("hold the shard", () -> select(mc, OnboardingRegistry.STARFALL_SHARD.get()))
                .waitTicks(2)
                .run("look at a south frame", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(0, 0, 2)).add(0, 0.5, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the Breach opens", 60, () -> mc.level.getBlockState(s.ring).is(OnboardingRegistry.BREACH.get()))
                .check("the shard was spent", () -> count(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 0)
                .check("all four middle blocks are the Breach, each its own quarter", () -> {
                    for (int[] m : BreachRing.MIDDLE) {
                        BlockState b = mc.level.getBlockState(s.ring.offset(m[0], 0, m[1]));
                        if (!b.is(OnboardingRegistry.BREACH.get()) || b.getValue(BreachBlock.QUARTER) != BreachBlock.Quarter.at(m[0], m[1])) {
                            return false;
                        }
                    }
                    return true;
                })
                .check("all twelve frames glow", () -> {
                    for (int[] o : BreachRing.RING) {
                        BlockState f = mc.level.getBlockState(s.ring.offset(o[0], 0, o[1]));
                        if (!f.is(ModBlocks.BREACH_FRAME.get()) || !f.getValue(BreachFrameBlock.ACTIVE)) {
                            return false;
                        }
                    }
                    return mc.level.getLightEmission(s.ring.offset(2, 0, 2)) == BreachFrameBlock.ACTIVE_LIGHT;
                })
                .run("mark", () -> stage(mc, s, "building and opening the ring"))
                .command("gamemode spectator")
                .run("rise above the Breach, in clear sight of it", () -> tp(server(srv ->
                        viewpoint(srv.overworld(), middle(s.ring), new double[] {4.5, 6}, new double[] {4, 5.5}), 10)))
                .waitTicks(20)
                .run("look down into it", () -> aim(mc, middle(s.ring).add(0, -0.3, 0)))
                .waitTicks(5)
                .check("the Breach's sky is drawn", () -> BreachRenderer.frames() > 0)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("breach_from_above")
                .run("rise straight over it", () -> tp(new Vec3(s.ring.getX() + 1.0, s.ring.getY() + 3.2, s.ring.getZ() + 1.0)))
                .waitUntil("over its middle", 40, () -> horizontal(mc.player.position(), middle(s.ring)) < 0.05)
                .run("look straight down into it", () -> aim(mc, middle(s.ring).add(0.0, -2, 0.001)))
                .waitTicks(10)
                .log("the view down", () -> String.format(Locale.ROOT, "eye %.2f above the Breach's surface, pitch %.1f",
                        mc.player.getEyeY() - (s.ring.getY() + BreachBlock.SURFACE), mc.player.getXRot()))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("breach_straight_down")
                .command("gamemode survival")
                // step in through a corner block: from the south frame onto the south-east quarter
                .run("stand on a south frame, facing north", () -> tp(new Vec3(s.ring.getX() + 1.5, s.ring.getY() + 1, s.ring.getZ() + 2.5)))
                .waitTicks(10)
                .run("face the Breach", () -> aim(mc, Vec3.atCenterOf(s.ring.offset(1, 0, 1)).add(0, 1.2, 0)))
                .run("mark", () -> stage(mc, s, "Breach views"))
                .log("stepping in from", () -> "stepping in from " + mc.player.position() + " toward the south-east quarter "
                        + s.ring.offset(1, 0, 1).toShortString())
                .hold(mc.options.keyUp)
                .waitUntil("the pull starts", 80, () -> {
                    if (!FallUpClient.active()) {
                        aim(mc, new Vec3(s.ring.getX() + 1.5, mc.player.getEyeY() - 0.3, s.ring.getZ() + 1.5));
                    }
                    return FallUpClient.active();
                })
                .release(mc.options.keyUp)
                .run("mark", () -> stage(mc, s, "stepping in"))
                .waitUntil("in Aetheria", 200, () -> AetheriaWorld.is(mc.level))
                .run("mark", () -> stage(mc, s, "falling up (the pull and the move)"))
                .run("read the arrival", () -> {
                    FallUp.Arrival a = server(srv -> FallUp.lastArrival(player(srv).getUUID()), 5);
                    s.arrival = a.pos();
                    s.arrivedAt = gameTime(mc);
                    CosmicBreach.LOGGER.info("[autotest] arrived at {} (Landing built: {}), through {} of the ring at {}",
                            BlockPos.containing(a.pos()).toShortString(), a.landingBuilt(), a.entered().toShortString(), a.ring().toShortString());
                })
                .check("the ring remembered is the one stepped through, from its south-east quarter", () -> server(srv -> {
                    FallUp.Arrival a = FallUp.lastArrival(player(srv).getUUID());
                    return a.ring().equals(s.ring) && a.entered().equals(s.ring.offset(1, 0, 1))
                            && s.ring.equals(player(srv).getData(OnboardingRegistry.STATE).ring().orElse(null));
                }, 5))
                .waitUntil("Slow Falling (the effect reaches the client a tick after the move)", 20,
                        () -> mc.player.hasEffect(MobEffects.SLOW_FALLING))
                .check("8 s of it, counted from the arrival", () -> server(srv -> {
                    var effect = player(srv).getEffect(MobEffects.SLOW_FALLING);
                    FallUp.Arrival a = FallUp.lastArrival(player(srv).getUUID());
                    int total = effect == null ? -1 : effect.getDuration() + (srv.getTickCount() - a.tick());
                    CosmicBreach.LOGGER.info("[autotest] Slow Falling: {} ticks left, {} from the arrival", effect == null ? -1 : effect.getDuration(), total);
                    return Math.abs(total - FallUp.SLOW_FALLING_TICKS) <= 2;
                }, 5))
                .check("30 blocks over the island, the 6 by 6 Landing with its open 4 by 4 ring under the arrival column", () -> server(srv -> {
                    ServerLevel ae = srv.getLevel(AetheriaWorld.LEVEL);
                    BlockPos col = BlockPos.containing(s.arrival);
                    BlockPos floor = null;
                    for (int y = col.getY(); y > col.getY() - 40; y--) {
                        BlockPos p = new BlockPos(col.getX(), y, col.getZ());
                        if (!ae.getBlockState(p).isAir()) {
                            floor = p;
                            break;
                        }
                    }
                    if (floor == null || !ae.getBlockState(floor).is(ModBlocks.STARFALL_STONE_BRICKS.get())
                            || col.getY() - (floor.getY() + 1) != FallUp.ABOVE) {
                        return false;
                    }
                    int[] o = LandingLayout.originFor(col.getX(), col.getZ());
                    s.landing = new BlockPos(o[0], floor.getY(), o[1]);
                    int bricks = 0;
                    int frames = 0;
                    int breach = 0;
                    for (int dx = LandingLayout.MIN; dx <= LandingLayout.MAX; dx++) {
                        for (int dz = LandingLayout.MIN; dz <= LandingLayout.MAX; dz++) {
                            BlockState b = ae.getBlockState(s.landing.offset(dx, 0, dz));
                            switch (LandingLayout.partAt(dx, dz)) {
                                case BRICK -> bricks += b.is(ModBlocks.STARFALL_STONE_BRICKS.get()) ? 1 : 0;
                                case FRAME -> frames += b.is(ModBlocks.BREACH_FRAME.get()) && b.getValue(BreachFrameBlock.ACTIVE) ? 1 : 0;
                                case BREACH -> breach += b.is(OnboardingRegistry.BREACH.get())
                                        && b.getValue(BreachBlock.QUARTER) == BreachBlock.Quarter.at(dx, dz) ? 1 : 0;
                                default -> {
                                }
                            }
                        }
                    }
                    CosmicBreach.LOGGER.info("[autotest] Landing at {}: {} bricks, {} lit frames, {} Breach blocks",
                            s.landing.toShortString(), bricks, frames, breach);
                    return bricks == 20 && frames == 12 && breach == 4 && Landings.get(ae).centres().contains(s.landing);
                }, 10))
                .check("the Arrival cue has played", () -> server(srv -> player(srv).getData(com.cosmicbreach.world.AetheriaAudio.HEARD_ARRIVAL), 5))
                .check("mobs leave the new arrival alone", () -> server(srv -> com.cosmicbreach.onboarding.ArrivalGrace.shelters(player(srv)), 5))
                .waitUntil("the white has cleared", 100, () -> FallUpClient.white() < 0.05f)
                .run("look down at the Landing", () -> aim(mc, middle(s.landing)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("arrival_over_the_landing")
                .waitUntil("landed on the platform", 300, () -> mc.player.onGround())
                .run("mark", () -> stage(mc, s, "Slow Falling to the Landing"))
                .check("on the bricks, beside the ring", () -> {
                    BlockPos under = mc.player.blockPosition().below();
                    return mc.level.getBlockState(under).is(ModBlocks.STARFALL_STONE_BRICKS.get());
                })
                .run("look at the return ring", () -> aim(mc, middle(s.landing)))
                .waitTicks(10)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("landing")
                .command("gamemode spectator")
                .run("rise over the Landing", () -> tp(new Vec3(s.landing.getX() + 1.0, s.landing.getY() + 6.5, s.landing.getZ() + 6.0)))
                .waitTicks(10)
                .run("look down at it", () -> aim(mc, middle(s.landing)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("landing_from_above")
                .command("gamemode survival")
                .run("back on the arrival brick", () -> {
                    int[] a = LandingLayout.arrivalFor(s.landing.getX(), s.landing.getZ());
                    tp(new Vec3(a[0] + 0.5, s.landing.getY() + 1, a[1] + 0.5));
                })
                .waitTicks(10)
                // home again, once the Breach's cooldown after the arrival is over
                .waitUntil("the Breach's 10 s cooldown after the arrival has passed", 260,
                        () -> gameTime(mc) - s.arrivedAt > FallUp.COOLDOWN_TICKS + 10)
                .run("face the return Breach", () -> aim(mc, Vec3.atCenterOf(s.landing).add(0, 1.2, 0)))
                .hold(mc.options.keyUp)
                .waitUntil("the pull starts again", 100, () -> {
                    if (!FallUpClient.active()) {
                        aim(mc, new Vec3(s.landing.getX() + 0.5, mc.player.getEyeY() - 0.3, s.landing.getZ() + 0.5));
                    }
                    return FallUpClient.active();
                })
                .release(mc.options.keyUp)
                .waitUntil("back in the Overworld", 200, () -> mc.level.dimension() == Level.OVERWORLD)
                .run("mark", () -> stage(mc, s, "home through the return Breach"))
                .waitUntil("standing", 100, () -> mc.player.onGround())
                .log("home", () -> String.format(Locale.ROOT, "home at %s, %.1f blocks from the middle of the ring at %s",
                        mc.player.blockPosition().toShortString(), horizontal(mc.player.position(), middle(s.ring)), s.ring.toShortString()))
                .check("beside the ring it came through", () -> {
                    double d = horizontal(mc.player.position(), middle(s.ring));
                    return d >= 2.0 && d <= 3.6 && Math.abs(mc.player.getY() - s.ring.getY()) <= 3;
                })
                .waitUntil("the white has cleared", 100, () -> FallUpClient.white() < 0.05f)
                .run("look at the ring", () -> aim(mc, middle(s.ring)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("home_beside_the_ring")
                // breaking any frame closes all four quarters
                .run("hold the stone pickaxe", () -> select(mc, Items.STONE_PICKAXE))
                .waitTicks(2)
                .run("look at the nearest south frame", () -> aim(mc, Vec3.atCenterOf(nearestFrame(mc, s.ring)).add(0, 0.45, 0)))
                .run("remember it", () -> s.broken = nearestFrame(mc, s.ring))
                .hold(mc.options.keyAttack)
                .waitUntil("the frame breaks", 80, () -> mc.level.getBlockState(s.broken).isAir())
                .release(mc.options.keyAttack)
                .waitUntil("all four quarters close", 20, () -> {
                    for (int[] m : BreachRing.MIDDLE) {
                        if (mc.level.getBlockState(s.ring.offset(m[0], 0, m[1])).is(OnboardingRegistry.BREACH.get())) {
                            return false;
                        }
                    }
                    return true;
                })
                .check("the other frames go dark", () -> {
                    for (int[] o : BreachRing.RING) {
                        BlockState f = mc.level.getBlockState(s.ring.offset(o[0], 0, o[1]));
                        if (f.is(ModBlocks.BREACH_FRAME.get()) && f.getValue(BreachFrameBlock.ACTIVE)) {
                            return false;
                        }
                    }
                    return true;
                })
                .run("mark", () -> stage(mc, s, "breaking a frame closes the Breach"));
    }

    /** The point the four middle blocks of the ring at {@code origin} share, halfway up them. */
    static Vec3 middle(BlockPos origin) {
        return new Vec3(origin.getX() + 1.0, origin.getY() + 0.5, origin.getZ() + 1.0);
    }

    /** The frame of the ring at {@code origin} nearest the player. */
    private static BlockPos nearestFrame(Minecraft mc, BlockPos origin) {
        BlockPos best = null;
        for (int[] o : BreachRing.RING) {
            BlockPos p = origin.offset(o[0], 0, o[1]);
            if (mc.level.getBlockState(p).is(ModBlocks.BREACH_FRAME.get())
                    && (best == null || mc.player.distanceToSqr(Vec3.atCenterOf(p)) < mc.player.distanceToSqr(Vec3.atCenterOf(best)))) {
                best = p;
            }
        }
        if (best == null) {
            throw new Steps.Failure("no frame left at " + origin.toShortString());
        }
        return best;
    }

    /** Where a dropped {@code item} lies near {@code near} (a player walks there to pick it up). */
    private static Vec3 droppedNear(MinecraftServer srv, BlockPos near, Item item) {
        for (var e : srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(near).inflate(6))) {
            if (e.getItem().is(item)) {
                return e.position();
            }
        }
        throw new Steps.Failure("no dropped " + item + " near " + near.toShortString());
    }

    // ------------------------------------------------------------------ a Fallen Rift

    private void rift(Steps steps, Minecraft mc, State s) {
        steps.command("locate structure cosmicbreach:fallen_rift")
                .waitForChat("fallen_rift", 400)
                .run("find the nearest rift", () -> s.rift = server(srv -> {
                    ServerLevel ow = srv.overworld();
                    Holder<Structure> rift = ow.registryAccess().registryOrThrow(Registries.STRUCTURE)
                            .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("fallen_rift")));
                    Pair<BlockPos, Holder<Structure>> found = ow.getChunkSource().getGenerator()
                            .findNearestMapStructure(ow, HolderSet.direct(rift), player(srv).blockPosition(), 100, false);
                    if (found == null) {
                        throw new Steps.Failure("no Fallen Rift within 100 chunks");
                    }
                    return found.getFirst();
                }, 60))
                .log("the rift", () -> String.format(Locale.ROOT, "nearest Fallen Rift starts in the chunk at %s, %.0f blocks away",
                        s.rift.toShortString(), horizontal(mc.player.position(), Vec3.atCenterOf(s.rift))))
                .run("mark", () -> stage(mc, s, "locating a rift"))
                .run("a shorter view for the trip (fewer new chunks to make and save)", () -> mc.options.renderDistance().set(5))
                .command("gamemode spectator")
                .run("go there", () -> tp(new Vec3(s.rift.getX() + 8.5, 150, s.rift.getZ() + 8.5)))
                .waitUntil("its chunks have loaded", 400, () -> server(srv -> {
                    ServerLevel ow = srv.overworld();
                    BlockPos p = s.rift.offset(8, 0, 8);
                    return ow.isLoaded(p) && riftPiece(srv, s.rift) != null;
                }, 20))
                .run("read the rift", () -> server(srv -> {
                    FallenRiftPiece piece = riftPiece(srv, s.rift);
                    CosmicBreach.LOGGER.info("[autotest] rift piece box {}", piece.getBoundingBox());
                    s.rift = piece.ringOrigin();
                    s.riftChest = null;
                    ServerLevel ow = srv.overworld();
                    for (BlockPos p : BlockPos.betweenClosed(s.rift.offset(-5, -3, -5), s.rift.offset(6, 3, 6))) {
                        if (ow.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CHEST)) {
                            s.riftChest = p.immutable();
                        }
                    }
                    return null;
                }, 20))
                .log("the rift's ring", () -> "ring origin " + s.rift.toShortString() + ", chest " + (s.riftChest == null ? "none" : s.riftChest.toShortString()))
                .check("eight of the twelve frames lie there, mineable with a stone pickaxe", () -> server(srv -> {
                    ServerLevel ow = srv.overworld();
                    int frames = 0;
                    for (int[] o : BreachRing.RING) {
                        BlockState f = ow.getBlockState(s.rift.offset(o[0], 0, o[1]));
                        if (f.is(ModBlocks.BREACH_FRAME.get())) {
                            frames++;
                            if (!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(f)
                                    || new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(f)) {
                                return false;
                            }
                        }
                    }
                    return frames == FallenRiftLayout.FRAMES;
                }, 10))
                .check("the four gaps and the 2 by 2 middle lie open: one craft of six frames finishes the ring", () -> server(srv -> {
                    BreachRing.Result r = BreachRing.check(com.cosmicbreach.onboarding.BreachRings.view(srv.overworld()), true,
                            s.rift.getX(), s.rift.getY(), s.rift.getZ());
                    ServerLevel ow = srv.overworld();
                    for (int[] m : r.missing()) {
                        if (!ow.getBlockState(s.rift.offset(m[0], 0, m[1])).isAir()) {
                            return false;
                        }
                    }
                    CosmicBreach.LOGGER.info("[autotest] the rift's ring: {} with {} frames, {} missing", r.problem(), r.frames(), r.missing().size());
                    return r.problem() == BreachRing.Problem.MISSING_FRAMES && r.missing().size() == 4 && r.shifted().isEmpty();
                }, 10))
                .check("its chest is there", () -> s.riftChest != null)
                .run("view it from above", () -> tp(middle(s.rift).add(0, 16, 14)))
                .waitTicks(60)
                .run("look down at it", () -> aim(mc, middle(s.rift)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("fallen_rift")
                .run("closer, over the ring", () -> tp(middle(s.rift).add(0, 5, 4.5)))
                .waitTicks(20)
                .run("look down at the ring", () -> aim(mc, middle(s.rift)))
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("fallen_rift_ring")
                .command("gamemode survival")
                .run("stand by the chest", () -> tp(Vec3.atBottomCenterOf(s.riftChest).add(0, 1, 1.6)))
                .waitTicks(20)
                .run("look at the chest", () -> aim(mc, Vec3.atCenterOf(s.riftChest).add(0, 0.3, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the chest opens", 40, () -> mc.screen instanceof ContainerScreen)
                .waitTicks(5)
                .log("the chest", () -> "chest holds: " + chestContents(mc))
                .check("a Starfall Shard, 3 to 6 copper, a Starsteel Nugget, a Torn Codex Page", () -> {
                    int copper = chestCount(mc, Items.COPPER_INGOT);
                    return chestCount(mc, OnboardingRegistry.STARFALL_SHARD.get()) == 1 && copper >= 3 && copper <= 6
                            && chestCount(mc, com.cosmicbreach.registry.ModMaterials.STARSTEEL_NUGGET.get()) == 1
                            && chestCount(mc, OnboardingRegistry.TORN_CODEX_PAGE.get()) == 1;
                })
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false)).screenshot("fallen_rift_chest")
                .run("take the torn page (shift-click)", () -> {
                    var menu = mc.player.containerMenu;
                    for (int i = 0; i < menu.slots.size() - 36; i++) {
                        if (menu.getSlot(i).getItem().is(OnboardingRegistry.TORN_CODEX_PAGE.get())) {
                            click(mc, menu.containerId, i, 0, ClickType.QUICK_MOVE);
                            return;
                        }
                    }
                    throw new Steps.Failure("no torn page in the chest");
                })
                .waitTicks(3)
                .run("close the chest", () -> mc.player.closeContainer())
                .run("mark", () -> stage(mc, s, "the rift's frames and chest"))
                // a torn page binds itself into a new Codex for a player without one
                .command("clear @s cosmicbreach:starfall_codex")
                .waitUntil("no Codex left", 20, () -> count(mc, OnboardingRegistry.STARFALL_CODEX.get()) == 0)
                .run("hold the torn page", () -> select(mc, OnboardingRegistry.TORN_CODEX_PAGE.get()))
                .waitTicks(2)
                .run("look up at the sky", () -> aim(mc, mc.player.getEyePosition().add(0, 10, 1)))
                .press(mc.options.keyUse)
                .waitUntil("the page opens a new Codex on Build a ring", 100, () -> mc.screen instanceof guideme.internal.screen.GuideScreen g
                        && Codex.BUILD_A_RING.equals(g.getCurrentPageId()))
                .check("the Codex was given and the page used up", () -> count(mc, OnboardingRegistry.STARFALL_CODEX.get()) == 1
                        && count(mc, OnboardingRegistry.TORN_CODEX_PAGE.get()) == 0)
                .run("close the Codex", () -> mc.screen.onClose())
                .run("mark", () -> stage(mc, s, "the torn page"))
                .waitUntil("the world round the rift has finished loading (so the save at the end is quick)", 600,
                        () -> mc.screen == null && mc.levelRenderer.hasRenderedAllSections() && clientChunksAround(mc, 3));
        // the far terrain made for the trip settles and saves while the game runs, not all at once when it quits
        steps.waitTicks(200);
    }

    /** The Fallen Rift piece whose structure starts in the chunk at {@code origin} (or a neighbour). */
    private static FallenRiftPiece riftPiece(MinecraftServer srv, BlockPos origin) {
        ServerLevel ow = srv.overworld();
        Structure rift = ow.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CosmicBreach.id("fallen_rift"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var chunk = ow.getChunk((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz);
                StructureStart start = chunk.getStartForStructure(rift);
                if (start != null && start.isValid()) {
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece instanceof FallenRiftPiece f) {
                            return f;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static void placeFrame(Steps steps, Minecraft mc, State s, int dx, int dz) {
        steps.run("aim at the ground for a frame at " + dx + ", " + dz,
                        () -> aim(mc, Vec3.atCenterOf(s.ring.offset(dx, -1, dz)).add(0, 0.5, 0)))
                .press(mc.options.keyUse)
                .waitUntil("a frame at " + dx + ", " + dz, 10, () -> mc.level.getBlockState(s.ring.offset(dx, 0, dz)).is(ModBlocks.BREACH_FRAME.get()));
    }

    /** One craft's ingredients through the crafting menu's own clicks: copper in the corners, cobblestone in the rest. */
    private static void layOutFrames(Minecraft mc) {
        var menu = mc.player.containerMenu;
        int copper = menuSlotOf(mc, Items.COPPER_INGOT);
        int cobble = menuSlotOf(mc, Items.COBBLESTONE);
        click(mc, menu.containerId, copper, 0, ClickType.PICKUP);
        for (int grid : new int[] {1, 3, 7, 9}) {
            click(mc, menu.containerId, grid, 1, ClickType.PICKUP);
        }
        click(mc, menu.containerId, copper, 0, ClickType.PICKUP);
        click(mc, menu.containerId, cobble, 0, ClickType.PICKUP);
        for (int grid : new int[] {2, 4, 5, 6, 8}) {
            click(mc, menu.containerId, grid, 1, ClickType.PICKUP);
        }
        click(mc, menu.containerId, cobble, 0, ClickType.PICKUP);
    }

    /** The result slot (the server fills it) shows 6 Breach Frames. */
    private static boolean resultIsFrames(Minecraft mc) {
        ItemStack out = mc.player.containerMenu.getSlot(0).getItem();
        return out.is(ModBlocks.BREACH_FRAME.get().asItem()) && out.getCount() == 6;
    }

    private static void click(Minecraft mc, int container, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(container, slot, button, type, mc.player);
    }

    /** The crafting menu's slot holding {@code item} (10 to 45: inventory then hotbar). */
    private static int menuSlotOf(Minecraft mc, Item item) {
        var menu = mc.player.containerMenu;
        for (int i = 10; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().is(item)) {
                return i;
            }
        }
        throw new Steps.Failure("no " + item + " in the inventory");
    }

    /** Puts {@code item} in hand through the hotbar key of its slot (moving it to the hotbar first if needed). */
    static void select(Minecraft mc, Item item) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                pressHotbar(mc, i);
                return;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).is(item)) {
                int from = i;
                server(srv -> {
                    var pinv = player(srv).getInventory();
                    ItemStack a = pinv.getItem(from);
                    pinv.setItem(from, pinv.getItem(8));
                    pinv.setItem(8, a);
                    return null;
                }, 5);
                pressHotbar(mc, 8);
                return;
            }
        }
        throw new Steps.Failure("no " + item + " to hold");
    }

    /** Turns the walking player toward the nearest dropped {@code item} (what a player does to pick it up). */
    static void walkToward(Minecraft mc, Item item) {
        net.minecraft.world.entity.item.ItemEntity nearest = null;
        for (net.minecraft.world.entity.item.ItemEntity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                mc.player.getBoundingBox().inflate(10.0))) {
            if (e.getItem().is(item) && (nearest == null || e.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) {
                nearest = e;
            }
        }
        if (nearest != null) {
            Vec3 at = nearest.position();
            aim(mc, new Vec3(at.x, mc.player.getEyeY() - 0.4, at.z));
        }
    }

    /** True once the client holds every chunk within {@code radius} chunks of the player. */
    private static boolean clientChunksAround(Minecraft mc, int radius) {
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Selects an empty hotbar slot through its key. */
    static void emptyHand(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) {
                pressHotbar(mc, i);
                return;
            }
        }
        throw new Steps.Failure("no empty hotbar slot");
    }

    static void pressHotbar(Minecraft mc, int slot) {
        var key = mc.options.keyHotbarSlots[slot];
        net.minecraft.client.KeyMapping.click(key.getKey());
    }

    static int count(Minecraft mc, Item item) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static int chestCount(Minecraft mc, Item item) {
        var menu = mc.player.containerMenu;
        int n = 0;
        int chestSlots = menu.slots.size() - 36;
        for (int i = 0; i < chestSlots; i++) {
            if (menu.getSlot(i).getItem().is(item)) {
                n += menu.getSlot(i).getItem().getCount();
            }
        }
        return n;
    }

    private static String chestContents(Minecraft mc) {
        var menu = mc.player.containerMenu;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < menu.slots.size() - 36; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (!st.isEmpty()) {
                out.add(st.getCount() + " " + st.getItem());
            }
        }
        return String.join(", ", out);
    }

    /** Turns the player to look at {@code target} from its eyes, the way the harness's look step does. */
    static void aim(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
        var p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
        p.yBob = yaw;
        p.yBobO = yaw;
        p.xBob = pitch;
        p.xBobO = pitch;
    }

    /**
     * Feet for a camera that sees {@code target} with nothing in between, from one of the distances and heights
     * given, round the compass (spectators only: it may float).
     */
    private static Vec3 viewpoint(ServerLevel level, Vec3 target, double[] distances, double[] heights) {
        BlockPos goal = BlockPos.containing(target);
        for (double h : heights) {
            for (double d : distances) {
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8.0;
                    Vec3 eye = target.add(Math.cos(a) * d, h, Math.sin(a) * d);
                    BlockPos at = BlockPos.containing(eye);
                    if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isAir()) {
                        continue;
                    }
                    net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, target,
                            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE,
                            net.minecraft.world.phys.shapes.CollisionContext.empty()));
                    BlockPos hb = hit.getBlockPos();
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || Math.max(Math.abs(hb.getX() - goal.getX()),
                            Math.max(Math.abs(hb.getY() - goal.getY()), Math.abs(hb.getZ() - goal.getZ()))) <= 1) {
                        return eye.subtract(0, 1.62, 0);
                    }
                }
            }
        }
        return target.add(0, 6, 4);
    }

    /** A spot 2 blocks from {@code target} (or 1, or 3) where a player can stand and see it to reach it. */
    static Vec3 standNear(ServerLevel level, BlockPos target) {
        for (int d : new int[] {2, 1, 3}) {
            for (int[] o : new int[][] {{d, 0}, {-d, 0}, {0, d}, {0, -d}, {d, d}, {-d, d}, {d, -d}, {-d, -d}}) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos feet = target.offset(o[0], dy, o[1]);
                    if (com.cosmicbreach.world.AetheriaSpots.standable(level, feet)) {
                        Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, 1.62, 0);
                        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye,
                                Vec3.atCenterOf(target), net.minecraft.world.level.ClipContext.Block.OUTLINE,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
                            return Vec3.atBottomCenterOf(feet);
                        }
                    }
                }
            }
        }
        throw new Steps.Failure("nowhere to stand in sight of " + target.toShortString());
    }

    /** Moves the player (server side, same dimension), keeping where it looks. */
    static void tp(Vec3 to) {
        server(srv -> {
            ServerPlayer p = player(srv);
            p.teleportTo(p.serverLevel(), to.x, to.y, to.z, java.util.Set.of(), p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.resetFallDistance();
            return null;
        }, 10);
    }

    private static Component actionBar(Minecraft mc) {
        try {
            Field field = Gui.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            return (Component) field.get(mc.gui);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot read the action bar: " + e);
        }
    }

    private static void clearActionBar(Minecraft mc) {
        try {
            Field field = Gui.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            field.set(mc.gui, null);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("cannot clear the action bar: " + e);
        }
    }

    static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static long gameTime(Minecraft mc) {
        return mc.level.getGameTime();
    }

    private static long dayTime(Minecraft mc) {
        return mc.level.getDayTime();
    }

    private static void stage(Minecraft mc, State s, String name) {
        long now = gameTime(mc);
        s.stages.add(name + " " + (now - s.mark));
        CosmicBreach.LOGGER.info("[autotest] stage '{}' took {} ticks", name, now - s.mark);
        s.mark = now;
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    static <T> T server(Function<MinecraftServer, T> call, int timeoutSeconds) {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv == null) {
            throw new Steps.Failure("no integrated server");
        }
        try {
            return srv.submit(() -> call.apply(srv)).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Steps.Failure f) {
                throw f;
            }
            throw new Steps.Failure("server call failed: " + cause);
        }
    }
}
