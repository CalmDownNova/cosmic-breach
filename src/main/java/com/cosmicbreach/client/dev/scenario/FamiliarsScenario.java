package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.familiar.FamiliarKeys;
import com.cosmicbreach.client.familiar.FamiliarsClient;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.familiar.BrazierBlock;
import com.cosmicbreach.familiar.BrazierBlockEntity;
import com.cosmicbreach.familiar.Emberwisp;
import com.cosmicbreach.familiar.FamiliarBond;
import com.cosmicbreach.familiar.FamiliarEntity;
import com.cosmicbreach.familiar.FamiliarKind;
import com.cosmicbreach.familiar.FamiliarLanternItem;
import com.cosmicbreach.familiar.FamiliarMode;
import com.cosmicbreach.familiar.FamiliarRegistry;
import com.cosmicbreach.familiar.FamiliarRules;
import com.cosmicbreach.familiar.FamiliarSessions;
import com.cosmicbreach.familiar.Gravikin;
import com.cosmicbreach.familiar.LanternState;
import com.cosmicbreach.familiar.PrismMoth;
import com.cosmicbreach.familiar.Refract;
import com.cosmicbreach.familiar.StarEggItem;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.status.Rift;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.SolarFlare;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The combat familiars (G10, GDD 8.2) through the real key path (the familiar key, H, held and tapped, the lantern and the brazier used,
 * an egg dropped with Q, attacks, parries and dashes), every rule measured on the server; dodges and parries timed on
 * the server tick (the attacker strikes on the first server tick that finds the window open):
 * <ul>
 *   <li>hatch: Star Eggs in 4000 rolls of each vault table (40, 45, 50%, each vault's kind); an egg set in a Brazier of
 *       Solenne hatches after the debug time of loaded ticks, and its lantern is taken out by hand;</li>
 *   <li>controls: the key held summons (health 20 and damage 4 at Resilience and Arcane 20), taps cycle Guard, Passive,
 *       Attack, Guard; held again dismisses; the lantern used summons and dismisses; one out at a time; beyond 16 blocks
 *       it teleports back, under 16 it flies; a death darkens the lantern for 1200 ticks and G does nothing till then;
 *       the Gravikin stands on a pressure plate without pressing it;</li>
 *   <li>Emberwisp: Scorch on the enemy hit last, every 60 ticks, its fire ticks counted; a parry and a perfect dodge
 *       give Kindled, and a Kindled L1 hits x1.10 of a plain one;</li>
 *   <li>Gravikin: its taunt turns two zombies within 8 blocks on it for 80 ticks and not one at 11, then lets them go;
 *       zombies beside it move at 0.8 of their speed;</li>
 *   <li>Prism Moth: Refract every 40 ticks to three stacks; the Zenith hits x1.30 of a plain one and consumes them; a
 *       Rift is cleansed at once, the next only 600 ticks after;</li>
 *   <li>Aetheria: an egg dropped under a Solar Flare hatches where it lies, a brazier hatches at once; a Gravikin's taunt
 *       gives 150 threat on the Hollow Heliarch; each familiar photographed beside the player in the Reach's daylight
 *       and in the Deep.</li>
 * </ul>
 */
public final class FamiliarsScenario implements Scenario {
    public enum Part { HATCH, CONTROLS, EMBER, GRAVIKIN, MOTH, AETHERIA, LOOKS }

    private static final String TAG = "cb_familiars";

    private final Set<Part> parts;
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;

    // ------------------------------------------------------------------ what the server saw
    private record Watch(Predicate<ServerPlayer> when, Consumer<ServerPlayer> then) {
    }

    private volatile @Nullable Watch watch;
    private volatile int watchLeft;
    private volatile boolean watchFired;

    private record Hurt(int entity, float amount, String type, long time) {
    }

    private final List<Hurt> hurts = new CopyOnWriteArrayList<>();

    private record Strike(int entity, MoveKind kind, double mv, long time) {
    }

    private final List<Strike> strikes = new CopyOnWriteArrayList<>();
    private final List<Boolean> parries = new CopyOnWriteArrayList<>();
    private final List<Long> perfectDodges = new CopyOnWriteArrayList<>();
    /** The active familiar's place each server tick while sampling, and the biggest jump between two. */
    private volatile boolean sampling;
    private volatile @Nullable Vec3 lastSample;
    private volatile double maxStep;
    private volatile boolean noCrits;

    // ------------------------------------------------------------------ numbers carried between steps
    private final long[] t = new long[8];
    private final double[] v = new double[8];
    private final int[] ids = new int[4];

    public FamiliarsScenario() {
        this(EnumSet.allOf(Part.class));
    }

    public FamiliarsScenario(Part... only) {
        this(EnumSet.copyOf(List.of(only)));
    }

    private FamiliarsScenario(Set<Part> parts) {
        this.parts = parts;
    }

    @Override
    public int timeBudgetSeconds() {
        return parts.size() > 2 ? 1100 : 600;
    }

    // ------------------------------------------------------------------ listeners

    private void listen() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, event -> {
            LivingEntity e = event.getEntity();
            if (!e.level().isClientSide() && (e.getTags().contains(TAG) || e instanceof FamiliarEntity)) {
                hurts.add(new Hurt(e.getId(), event.getAmount(), event.getSource().getMsgId(), e.level().getGameTime()));
            }
        });
        HitResolver.onStrike((player, move, target, point) -> {
            if (target.getTags().contains(TAG)) {
                strikes.add(new Strike(target.getId(), move.def().kind(), move.mv(), player.level().getGameTime()));
            }
        });
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onParried(ServerPlayer player, PlayerCombat combat, @Nullable LivingEntity attacker, float amount, boolean early) {
                parries.add(early);
            }

            @Override
            public void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
                if (event instanceof CombatEvent.PerfectDodge) {
                    perfectDodges.add(player.level().getGameTime());
                }
            }
        });
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critChanceBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
                return noCrits ? -1.0 : 0.0; // measured hits: no rolled crits
            }
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(0);
        if (sampling) {
            FamiliarEntity f = FamiliarSessions.active(player);
            if (f != null) {
                Vec3 p = f.position();
                Vec3 last = lastSample;
                if (last != null) {
                    double step = p.distanceTo(last);
                    maxStep = Math.max(maxStep, step);
                    if (step > 2) {
                        CosmicBreach.LOGGER.info("[cosmicbreach] familiars: the familiar moved {} blocks in tick {}",
                                String.format(Locale.ROOT, "%.2f", step), player.level().getGameTime());
                    }
                }
                lastSample = p;
            }
        }
        Watch w = watch;
        if (w == null) {
            return;
        }
        if (w.when().test(player)) {
            watch = null;
            try {
                w.then().accept(player);
                watchFired = true;
            } catch (RuntimeException e) {
                CosmicBreach.LOGGER.error("[cosmicbreach] familiars: the watch's action failed", e);
            }
            return;
        }
        if (--watchLeft <= 0) {
            watch = null;
        }
    }

    private void arm(Steps steps, String what, int ticks, Predicate<ServerPlayer> when, Consumer<ServerPlayer> then) {
        steps.run("watch the server's next " + ticks + " ticks: " + what, () -> {
            watchFired = false;
            watchLeft = ticks;
            watch = new Watch(when, then);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static <T> T server(Function<ServerPlayer, T> query) {
        return ServerQuery.ask(query);
    }

    private static @Nullable FamiliarEntity out(ServerPlayer p) {
        FamiliarEntity f = FamiliarSessions.active(p);
        return f == null || f.isRemoved() ? null : f;
    }

    private static CombatStateMachine serverMachine(ServerPlayer p) {
        return PlayerCombat.of(p).machine();
    }

    private CombatStateMachine machine() {
        return PlayerCombat.of(mc.player).machine();
    }

    private Vec3 at(double dx, double dy, double dz) {
        return new Vec3(base.getX() + 0.5 + dx, base.getY() + dy, base.getZ() + 0.5 + dz);
    }

    private void result(String line) {
        summary.add(line);
        CosmicBreach.LOGGER.info("[cosmicbreach] familiars: {}", line);
    }

    /** The hotbar slot of the lantern of {@code kind} the client holds, or -1. */
    private int lanternSlot(FamiliarKind kind) {
        for (int i = 0; i < 9; i++) {
            FamiliarBond b = FamiliarLanternItem.bond(mc.player.getInventory().getItem(i));
            if (b != null && b.kind() == kind) {
                return i;
            }
        }
        return -1;
    }

    private @Nullable FamiliarBond serverBond(ServerPlayer p, FamiliarKind kind) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            FamiliarBond b = FamiliarLanternItem.bond(p.getInventory().getItem(i));
            if (b != null && b.kind() == kind) {
                return b;
            }
        }
        return null;
    }

    /** A lantern of {@code kind} in the hotbar (given if there is none yet). */
    private void haveLantern(Steps steps, FamiliarKind kind) {
        steps.run("a " + kind.id() + " lantern in the hotbar", () -> {
                    if (lanternSlot(kind) < 0) {
                        mc.player.connection.sendCommand("cosmicbreach familiar lantern " + kind.id());
                    }
                })
                .waitUntil("the " + kind.id() + " lantern is in the hotbar", 40, () -> lanternSlot(kind) >= 0);
    }

    /** Summons {@code kind} the way a player does: its lantern in hand, used. */
    private void summonByUse(Steps steps, FamiliarKind kind) {
        haveLantern(steps, kind);
        steps.run("the " + kind.id() + " lantern in hand", () -> KeyPress.select(mc, lanternSlot(kind)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the " + kind.id() + " is out", 30, () -> server(p -> out(p) != null && out(p).kind() == kind))
                .press(mc.options.keyHotbarSlots[0])
                .waitTicks(2);
    }

    private void dismissAll(Steps steps) {
        steps.run("no familiar out", () -> server(p -> {
            FamiliarSessions.dismiss(p);
            return true;
        })).waitTicks(2);
    }

    private void setMode(Steps steps, FamiliarMode mode) {
        int[] wait = {0};
        steps.waitUntil("tap the key until it stands in " + mode.id(), 80, () -> {
            FamiliarMode now = server(p -> out(p) == null ? null : out(p).mode());
            if (now == mode) {
                return true;
            }
            if (wait[0]-- <= 0) {
                KeyPress.tap(FamiliarKeys.KEY);
                wait[0] = 6;
            }
            return false;
        });
    }

    /** Turns the player's view to {@code target} (a run step, read when it runs). */
    private void lookAt(Steps steps, java.util.function.Supplier<Vec3> target) {
        steps.run("look at the spot", () -> {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 d = target.get().subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
        }).waitTicks(2);
    }

    /** A zombie at {@code at}: with its AI (real pursuit) or standing still (a dummy), a pumpkin against the sun. */
    private static Zombie zombie(ServerPlayer p, Vec3 at, double attack, boolean ai, double health) {
        ServerLevel level = p.serverLevel();
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) {
            throw new Steps.Failure("could not create a zombie");
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z));
        z.moveTo(at.x, at.y, at.z, yaw, 0f);
        z.setYHeadRot(yaw);
        z.setYBodyRot(yaw);
        z.setBaby(false);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        if (!ai) {
            z.goalSelector.removeAllGoals(goal -> true);
            z.targetSelector.removeAllGoals(goal -> true);
            z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0); // a dummy stays where it stands
        }
        z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        z.setHealth((float) health);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
        z.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
        z.addTag(TAG);
        level.addFreshEntity(z);
        return z;
    }

    private static List<Zombie> zombies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(48), z -> z.getTags().contains(TAG));
    }

    private static @Nullable Zombie byId(ServerPlayer p, int id) {
        return p.serverLevel().getEntity(id) instanceof Zombie z ? z : null;
    }

    private void clearZombies(Steps steps) {
        steps.run("the test zombies are gone", () -> server(p -> {
            for (Zombie z : zombies(p)) {
                z.discard();
            }
            return true;
        })).waitTicks(2);
    }

    private static void strike(ServerPlayer player, Zombie zombie) {
        zombie.swing(InteractionHand.MAIN_HAND);
        player.invulnerableTime = 0;
        zombie.doHurtTarget(player);
    }

    private void teleport(Steps steps, double dx, double dz, float yaw) {
        steps.run("to " + dx + ", " + dz + " from the middle", () -> server(p -> {
                    Vec3 to = at(dx, 0, dz);
                    p.teleportTo(p.serverLevel(), to.x, to.y, to.z, yaw, 0f);
                    return true;
                }))
                .look(yaw, 0)
                .waitTicks(2);
    }

    /** Back to the middle facing south, Meridian in hand, full health, nothing out, no zombies. */
    private void reset(Steps steps) {
        clearZombies(steps);
        dismissAll(steps);
        steps.run("the player's own eyes", this::uncamera);
        teleport(steps, 0, 0, 0f);
        steps.press(mc.options.keyHotbarSlots[0])
                .run("full health, no statuses", () -> server(p -> {
                    p.setHealth(p.getMaxHealth());
                    p.removeEffect(FamiliarRegistry.KINDLED);
                    p.removeEffect(com.cosmicbreach.status.Statuses.RIFT);
                    return true;
                }))
                .run("clear the records", () -> {
                    hurts.clear();
                    strikes.clear();
                    parries.clear();
                    perfectDodges.clear();
                })
                .waitUntil("on the ground", 40, () -> mc.player.onGround());
    }

    private void camera(Vec3 eye, Vec3 target) {
        uncamera();
        camera = DevCamera.create(mc.level);
        camera.place(eye, target);
        camera.use();
        mc.options.hideGui = true;
    }

    private void uncamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
    }

    // ------------------------------------------------------------------ steps

    @Override
    public void steps(Steps steps) {
        listen();
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("clear @s")
                .command("difficulty normal")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doWeatherCycle false")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .command("gamerule naturalRegeneration false")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .command("cosmicbreach debug stats 0 0 20 20")
                .command("give @s cosmicbreach:meridian")
                .run("note the middle", () -> base = mc.player.blockPosition())
                .waitUntil("Meridian in hand", 20, () -> mc.player.getMainHandItem().is(com.cosmicbreach.registry.ModItems.MERIDIAN.get()));
        if (parts.contains(Part.HATCH)) {
            hatch(steps);
        }
        if (parts.contains(Part.CONTROLS)) {
            controls(steps);
        }
        if (parts.contains(Part.EMBER)) {
            ember(steps);
        }
        if (parts.contains(Part.GRAVIKIN)) {
            gravikin(steps);
        }
        if (parts.contains(Part.MOTH)) {
            moth(steps);
        }
        if (parts.contains(Part.AETHERIA) || parts.contains(Part.LOOKS)) {
            aetheria(steps);
        }
        steps.run("the player's own eyes", this::uncamera)
                .command("gamerule naturalRegeneration true")
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ hatching

    private void hatch(Steps steps) {
        steps.check("Star Eggs in the vaults: Reliquary 40%, Observatory 45%, Crypt 50%, each vault's kind", () -> server(p -> {
            String[] tables = {"reliquary", "observatory", "crypt"};
            double[] want = {0.40, 0.45, 0.50};
            FamiliarKind[] kinds = {FamiliarKind.EMBERWISP, FamiliarKind.GRAVIKIN, FamiliarKind.PRISM_MOTH};
            boolean ok = true;
            StringBuilder line = new StringBuilder("vault eggs in 4000 rolls:");
            for (int i = 0; i < 3; i++) {
                LootTable table = p.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                        CosmicBreach.id("vaults/" + tables[i])));
                LootParams params = new LootParams.Builder(p.serverLevel()).withParameter(LootContextParams.ORIGIN, p.position())
                        .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.VAULT);
                int eggs = 0;
                int right = 0;
                for (int n = 0; n < 4000; n++) {
                    for (ItemStack s : table.getRandomItems(params)) {
                        if (s.is(FamiliarRegistry.STAR_EGG.get())) {
                            eggs++;
                            if (StarEggItem.kind(s) == kinds[i]) {
                                right++;
                            }
                        }
                    }
                }
                double rate = eggs / 4000.0;
                line.append(String.format(Locale.ROOT, " %s %.1f%% (%s)", tables[i], rate * 100, kinds[i].id()));
                ok &= Math.abs(rate - want[i]) < 0.03 && right == eggs;
            }
            result(line.toString());
            return ok;
        }));
        BlockPos[] brazier = {BlockPos.ZERO};
        steps.command("cosmicbreach familiar hatchtime 100")
                .run("a Brazier of Solenne two blocks south", () -> server(p -> {
                    brazier[0] = base.offset(0, 0, 2);
                    p.serverLevel().setBlock(brazier[0], FamiliarRegistry.BRAZIER.get().defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .command("item replace entity @s hotbar.1 with cosmicbreach:star_egg[cosmicbreach:star_egg=\"emberwisp\"]")
                .waitUntil("the egg is in the hotbar", 20, () -> mc.player.getInventory().getItem(1).is(FamiliarRegistry.STAR_EGG.get()))
                .press(mc.options.keyHotbarSlots[1])
                .look(0f, 26f)
                .waitTicks(3);
        arm(steps, "the egg set in the bowl", 40, p -> p.serverLevel().getBlockEntity(brazier[0]) instanceof BrazierBlockEntity b && b.warming(),
                p -> t[0] = p.level().getGameTime());
        steps.press(mc.options.keyUse)
                .waitUntil("the brazier took the egg", 40, () -> watchFired)
                .waitUntil("it is lit and the egg left the hand", 40, () -> mc.player.getInventory().getItem(1).isEmpty()
                        && server(p -> p.serverLevel().getBlockState(brazier[0]).getValue(BrazierBlock.LIT)))
                .waitTicks(20)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("brazier_egg");
        arm(steps, "it hatches", 300, p -> p.serverLevel().getBlockEntity(brazier[0]) instanceof BrazierBlockEntity b
                && b.held().is(FamiliarRegistry.LANTERN.get()), p -> t[1] = p.level().getGameTime());
        steps.waitUntil("the egg hatched in the bowl", 300, () -> watchFired)
                .check("after 100 loaded ticks (the debug time)", () -> {
                    result(String.format(Locale.ROOT, "brazier: the egg hatched %d ticks after the tick it was set in, its %dth loaded tick (debug time 100; 10 minutes is %d)",
                            t[1] - t[0], t[1] - t[0] + 1, FamiliarRules.HATCH_TICKS));
                    return Math.abs((t[1] - t[0]) - 100) <= 1;
                })
                .waitTicks(10)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("brazier_lantern")
                .press(mc.options.keyHotbarSlots[4])
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the lantern came out into the inventory", 40, () -> lanternSlot(FamiliarKind.EMBERWISP) >= 0)
                .check("an Emberwisp's, in Guard, whole", () -> server(p -> {
                    FamiliarBond b = serverBond(p, FamiliarKind.EMBERWISP);
                    return b != null && b.mode() == FamiliarMode.GUARD && b.health() == 1.0f;
                }))
                .command("cosmicbreach familiar hatchtime reset")
                .waitTicks(3)
                .check("braziers hatch after 10 minutes again", () -> server(p -> BrazierBlockEntity.hatchTicks() == 12000))
                .run("the brazier goes", () -> server(p -> {
                    p.serverLevel().setBlock(brazier[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .look(0f, 0f);
    }

    // ------------------------------------------------------------------ the controls

    private void controls(Steps steps) {
        reset(steps);
        haveLantern(steps, FamiliarKind.EMBERWISP);
        steps.press(mc.options.keyHotbarSlots[0])
                .press(FamiliarKeys.KEY, 12)
                .waitUntil("the key held: the Emberwisp is out", 30, () -> server(p -> out(p) != null && out(p).kind() == FamiliarKind.EMBERWISP))
                .check("health 10 + 0.5 x 20 and damage 2 + 0.1 x 20", () -> server(p -> {
                    FamiliarEntity f = out(p);
                    result(String.format(Locale.ROOT, "scaling at Resilience 20 and Arcane 20: Emberwisp health %.1f (20), damage %.1f (4)",
                            f.getMaxHealth(), f.hitDamage()));
                    return Math.abs(f.getMaxHealth() - 20f) < 1e-4 && Math.abs(f.hitDamage() - 4.0) < 1e-9 && f.getHealth() == f.getMaxHealth();
                }))
                .waitUntil("the client sees it and its lantern shows it out", 20, () -> {
                    int slot = lanternSlot(FamiliarKind.EMBERWISP);
                    return slot >= 0 && FamiliarsClient.state(mc.player.getInventory().getItem(slot)) == LanternState.OUT
                            && !mc.level.getEntitiesOfClass(FamiliarEntity.class, mc.player.getBoundingBox().inflate(6)).isEmpty();
                });
        List<String> seen = new ArrayList<>();
        for (FamiliarMode want : new FamiliarMode[] {FamiliarMode.PASSIVE, FamiliarMode.ATTACK, FamiliarMode.GUARD}) {
            steps.waitTicks(3)
                    .press(FamiliarKeys.KEY, 2)
                    .waitUntil("a tap of the key: " + want.id(), 20, () -> server(p -> out(p) != null && out(p).mode() == want
                            && serverBond(p, FamiliarKind.EMBERWISP).mode() == want))
                    .run("note", () -> seen.add(want.id()));
        }
        steps.check("taps cycled Guard to Passive, Attack, Guard, still out", () -> {
                    result("taps of the key: guard -> " + String.join(" -> ", seen));
                    return seen.size() == 3 && server(p -> out(p) != null);
                })
                .waitTicks(3)
                .press(FamiliarKeys.KEY, 12)
                .waitUntil("the key held again: dismissed", 30, () -> server(p -> out(p) == null))
                .waitUntil("its lantern is lit again, its health kept", 40, () -> {
                    int slot = lanternSlot(FamiliarKind.EMBERWISP);
                    return FamiliarsClient.state(mc.player.getInventory().getItem(slot)) == LanternState.LIT
                            && server(p -> serverBond(p, FamiliarKind.EMBERWISP).health() == 1.0f);
                });
        summonByUse(steps, FamiliarKind.EMBERWISP);
        steps.run("the lantern in hand again", () -> KeyPress.select(mc, lanternSlot(FamiliarKind.EMBERWISP)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("used again: dismissed", 30, () -> server(p -> out(p) == null))
                .press(mc.options.keyHotbarSlots[0]);
        // one at a time
        summonByUse(steps, FamiliarKind.EMBERWISP);
        summonByUse(steps, FamiliarKind.GRAVIKIN);
        steps.waitTicks(3)
                .check("one out at a time: the Gravikin replaced the Emberwisp", () -> server(p -> {
                    int n = p.serverLevel().getEntitiesOfClass(FamiliarEntity.class, p.getBoundingBox().inflate(40),
                            f -> p.getUUID().equals(f.getOwnerUUID())).size();
                    result("one at a time: " + n + " familiar out after summoning a second (" + out(p).kind().id() + ")");
                    return n == 1 && out(p).kind() == FamiliarKind.GRAVIKIN;
                }));
        // the traps: the Gravikin stands on a pressure plate and never presses it
        steps.waitTicks(40)
                .run("stone pressure plates on every free block round the player", () -> server(p -> {
                    ServerLevel level = p.serverLevel();
                    BlockPos feet = p.blockPosition();
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos q = feet.offset(dx, 0, dz);
                            if ((dx != 0 || dz != 0) && level.getBlockState(q).isAir()) {
                                level.setBlock(q, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    return true;
                }))
                .waitTicks(40)
                .check("the Gravikin stands on a plate that stays up", () -> server(p -> {
                    FamiliarEntity f = out(p);
                    BlockPos under = f.blockPosition();
                    boolean onPlate = p.serverLevel().getBlockState(under).is(Blocks.STONE_PRESSURE_PLATE);
                    int pressed = 0;
                    for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-3, 0, -3), p.blockPosition().offset(3, 0, 3))) {
                        if (p.serverLevel().getBlockState(q).is(Blocks.STONE_PRESSURE_PLATE)
                                && p.serverLevel().getBlockState(q).getValue(PressurePlateBlock.POWERED)) {
                            pressed++;
                        }
                    }
                    result("traps: the Gravikin stands on a pressure plate: " + onPlate + ", plates pressed: " + pressed);
                    return onPlate && pressed == 0;
                }))
                .command("fill ~-3 ~ ~-3 ~3 ~ ~3 minecraft:air replace minecraft:stone_pressure_plate");
        dismissAll(steps);
        // under 16 it flies after its owner; beyond 16 it teleports back
        summonByUse(steps, FamiliarKind.EMBERWISP);
        steps.waitTicks(20)
                .run("sample its path from where it is now", () -> {
                    lastSample = server(p -> out(p).position());
                    maxStep = 0;
                    sampling = true;
                });
        teleport(steps, 12, 0, 0f);
        steps.waitTicks(40)
                .check("12 blocks away it flew after its owner", () -> {
                    double d = server(p -> out(p).distanceTo(p));
                    result(String.format(Locale.ROOT, "follow: owner moved 12 blocks: the wisp flew (largest step %.2f blocks a tick), now %.2f away",
                            maxStep, d));
                    return maxStep < 1.3 && d < 3.0;
                })
                .run("sample again from where it is now", () -> {
                    lastSample = server(p -> out(p).position());
                    maxStep = 0;
                });
        teleport(steps, 32, 0, 0f);
        steps.waitTicks(6)
                .check("20 blocks away it teleported back", () -> {
                    double d = server(p -> out(p).distanceTo(p));
                    double px = server(p -> p.getX() - base.getX());
                    double fx = server(p -> out(p).getX() - base.getX());
                    result(String.format(Locale.ROOT, "teleport: owner moved 20 blocks (now at x+%.1f, the wisp at x+%.1f): the wisp jumped %.1f blocks in one tick, now %.2f away",
                            px, fx, maxStep, d));
                    sampling = false;
                    return maxStep > 12 && d < 3.0;
                });
        teleport(steps, 0, 0, 0f);
        // a death darkens its lantern for 60 s (the Gravikin's lantern put away, so G has no other to summon)
        steps.run("put the Gravikin's lantern away", () -> server(p -> {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                FamiliarBond b = FamiliarLanternItem.bond(p.getInventory().getItem(i));
                if (b != null && b.kind() == FamiliarKind.GRAVIKIN) {
                    p.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
            return true;
        }));
        steps.waitTicks(10).command("cosmicbreach familiar kill")
                .waitUntil("it died", 20, () -> server(p -> out(p) == null))
                .run("note", () -> t[2] = server(p -> p.level().getGameTime()))
                .check("its lantern is dark for 1200 ticks", () -> server(p -> {
                    FamiliarBond b = serverBond(p, FamiliarKind.EMBERWISP);
                    v[0] = b.darkUntil();
                    long left = b.darkUntil() - p.level().getGameTime();
                    return left > 1180 && left <= 1200;
                }))
                .waitUntil("the client shows it dark", 20, () -> FamiliarsClient.state(mc.player.getInventory().getItem(
                        lanternSlot(FamiliarKind.EMBERWISP))) == LanternState.DARK)
                .press(FamiliarKeys.KEY, 12)
                .waitTicks(6)
                .check("the key held while it is dark: nothing comes", () -> server(p -> out(p) == null))
                .waitUntil("a tick before it relights", 1300, () -> server(p -> p.level().getGameTime() >= (long) v[0] - 12))
                .press(FamiliarKeys.KEY, 12)
                .waitTicks(2)
                .check("still dark just before", () -> server(p -> out(p) == null))
                .waitUntil("it relit", 40, () -> server(p -> p.level().getGameTime() >= (long) v[0]))
                .waitUntil("lit on the client", 20, () -> FamiliarsClient.state(mc.player.getInventory().getItem(
                        lanternSlot(FamiliarKind.EMBERWISP))) == LanternState.LIT)
                .press(FamiliarKeys.KEY, 12)
                .waitUntil("the key held: it comes back, whole", 30, () -> server(p -> out(p) != null && out(p).getHealth() == out(p).getMaxHealth()))
                .check("dark for 60 s", () -> {
                    long dark = (long) v[0] - t[2];
                    result(String.format(Locale.ROOT, "death: the lantern was dark %d ticks (1200); refused before, answered after", dark));
                    return Math.abs(dark - 1200) <= 2;
                });
        dismissAll(steps);
    }

    // ------------------------------------------------------------------ the Emberwisp

    private void ember(Steps steps) {
        reset(steps);
        summonByUse(steps, FamiliarKind.EMBERWISP);
        steps.run("a dummy zombie 2.5 south", () -> ids[0] = server(p -> zombie(p, at(0, 0, 2.5), 3.0, false, 400).getId()))
                .waitTicks(20)
                .run("clear", () -> {
                    hurts.clear();
                    strikes.clear();
                })
                .press(mc.options.keyAttack)
                .waitUntil("the hit landed", 20, () -> strikes.stream().anyMatch(s -> s.entity() == ids[0]))
                .waitUntil("the Emberwisp threw Scorch on it", 40, () -> server(p -> byId(p, ids[0]).hasEffect(GearRegistry.SCORCH)))
                .run("note", () -> {
                    t[0] = server(p -> ((Emberwisp) out(p)).lastScorch());
                    t[1] = strikes.get(0).time();
                })
                .waitTicks(110)
                .check("Scorch: burning ticks, and again 60 ticks after the first", () -> {
                    long last = server(p -> ((Emberwisp) out(p)).lastScorch());
                    List<Hurt> burns = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().contains("scorch")
                            && h.time() >= t[0] && h.time() < t[0] + 100).toList();
                    result(String.format(Locale.ROOT, "Scorch: thrown %d ticks after the hit, again %d ticks later; %d burns of %.1f in the 100 ticks after",
                            t[0] - t[1], last - t[0], burns.size(), burns.isEmpty() ? 0f : burns.get(0).amount()));
                    List<Hurt> own = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().equals("cosmicbreach.familiar")).toList();
                    result(String.format(Locale.ROOT, "the Emberwisp's own hits in Guard on its owner's target: %d, each %.1f (2 + 0.1 x 20 = 4), %s ticks apart",
                            own.size(), own.isEmpty() ? 0f : own.get(0).amount(),
                            own.size() < 2 ? "-" : String.valueOf(own.get(1).time() - own.get(0).time())));
                    return last - t[0] == FamiliarRules.SCORCH_EVERY && burns.size() >= 4 && burns.stream().allMatch(h -> h.amount() == 1.0f)
                            && t[0] - t[1] <= 2 && own.size() >= 2 && own.stream().allMatch(h -> h.amount() == 4.0f)
                            && own.get(1).time() - own.get(0).time() == FamiliarRules.ATTACK_INTERVAL;
                });
        // Kindled after a parry: +10% on the next hits (Passive: no more of its own hits or Scorch on the dummy; Kindled stays)
        setMode(steps, FamiliarMode.PASSIVE);
        steps.run("a striker 1.8 south, the dummy stays", () -> ids[1] = server(p -> zombie(p, at(0.6, 0, 1.8), 4.0, false, 400).getId()))
                .waitTicks(3)
                .run("clear", () -> parries.clear());
        arm(steps, "the striker strikes into the parry", 30, p -> serverMachine(p).isParrying(), p -> strike(p, byId(p, ids[1])));
        steps.press(ModKeyMappings.PARRY)
                .waitUntil("it struck into the server's parry", 60, () -> watchFired)
                .waitTicks(3)
                .check("the parry kindled the player for 60 ticks", () -> server(p -> {
                    var k = p.getEffect(FamiliarRegistry.KINDLED);
                    result("Kindled after a parry (" + parries + "): " + (k == null ? "none" : k.getDuration() + " ticks left"));
                    return !parries.isEmpty() && k != null && k.getDuration() > 50 && k.getDuration() <= FamiliarRules.KINDLED_TICKS;
                }))
                .run("the striker goes; the dummy back 2.5 south", () -> server(p -> {
                    byId(p, ids[1]).discard();
                    Vec3 d = at(0, 0, 2.5);
                    byId(p, ids[0]).teleportTo(d.x, d.y, d.z);
                    byId(p, ids[0]).setDeltaMovement(Vec3.ZERO);
                    return true;
                }))
                .waitTicks(20)
                .run("no rolled crits; clear", () -> {
                    noCrits = true;
                    strikes.clear();
                    hurts.clear();
                })
                .check("still Kindled", () -> server(p -> p.hasEffect(FamiliarRegistry.KINDLED)))
                .press(mc.options.keyAttack)
                .waitUntil("a Kindled L1 landed", 20, () -> hurts.stream().anyMatch(h -> h.entity() == ids[0] && h.type().equals("player")))
                .run("note", () -> {
                    v[1] = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().equals("player")).findFirst().get().amount();
                    v[3] = strikes.get(0).mv();
                })
                .waitUntil("Kindled ran out", 80, () -> server(p -> !p.hasEffect(FamiliarRegistry.KINDLED)))
                .waitTicks(20)
                .run("the dummy back 2.5 south", () -> server(p -> {
                    Vec3 d = at(0, 0, 2.5);
                    byId(p, ids[0]).teleportTo(d.x, d.y, d.z);
                    byId(p, ids[0]).setDeltaMovement(Vec3.ZERO);
                    return true;
                }))
                .waitTicks(3)
                .run("clear", () -> {
                    strikes.clear();
                    hurts.clear();
                })
                .press(mc.options.keyAttack)
                .waitUntil("a plain L1 landed", 20, () -> hurts.stream().anyMatch(h -> h.entity() == ids[0] && h.type().equals("player")))
                .check("the Kindled hit was x1.10 of the plain one", () -> {
                    v[2] = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().equals("player")).findFirst().get().amount();
                    double ratio = v[1] / v[2];
                    result(String.format(Locale.ROOT, "Kindled: L1 (motion value %.2f) %.3f with it, %.3f without: x%.3f", v[3], v[1], v[2], ratio));
                    noCrits = false;
                    return Math.abs(ratio - 1.10) < 0.002 && strikes.get(0).mv() == v[3];
                });
        // a perfect dodge kindles too
        steps.waitTicks(20)
                .run("a striker 1.8 south", () -> ids[1] = server(p -> zombie(p, at(0.6, 0, 1.8), 4.0, false, 400).getId()))
                .waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && !machine().isDashing())
                .run("clear", () -> perfectDodges.clear());
        arm(steps, "the striker strikes into the perfect-dodge window", 30, p -> serverMachine(p).inPerfectDodgeWindow(),
                p -> strike(p, byId(p, ids[1])));
        steps.press(ModKeyMappings.DASH)
                .waitUntil("it struck into the dodge", 60, () -> watchFired)
                .waitTicks(3)
                .check("the perfect dodge kindled the player", () -> server(p -> {
                    var k = p.getEffect(FamiliarRegistry.KINDLED);
                    result("Kindled after a perfect dodge (" + perfectDodges.size() + " dodge): " + (k == null ? "none" : k.getDuration() + " ticks left"));
                    return !perfectDodges.isEmpty() && k != null && k.getDuration() > 50;
                }));
        clearZombies(steps);
        dismissAll(steps);
    }

    // ------------------------------------------------------------------ the Gravikin

    private void gravikin(Steps steps) {
        reset(steps);
        summonByUse(steps, FamiliarKind.GRAVIKIN);
        setMode(steps, FamiliarMode.ATTACK);
        steps.check("health (10 + 0.5 x 20) x 1.5", () -> server(p -> {
                    result(String.format(Locale.ROOT, "scaling: Gravikin health %.1f (30)", out(p).getMaxHealth()));
                    return Math.abs(out(p).getMaxHealth() - 30f) < 1e-4;
                }))
                .waitUntil("it perched", 60, () -> server(p -> ((Gravikin) out(p)).perched()))
                .run("three zombies after the player: 5 and 7 blocks from the Gravikin, and one at 11", () -> server(p -> {
                    FamiliarEntity g = out(p);
                    t[0] = p.level().getGameTime();
                    ids[0] = zombie(p, g.position().add(5, 0, 0), 2.0, true, 60).getId();
                    ids[1] = zombie(p, g.position().add(-7, 0, 0.5), 2.0, true, 60).getId();
                    ids[2] = zombie(p, g.position().add(0, 0, -11.5), 2.0, true, 60).getId();
                    for (int i = 0; i < 3; i++) {
                        byId(p, ids[i]).setTarget(p);
                    }
                    return true;
                }))
                .waitUntil("it taunted", 40, () -> server(p -> ((Gravikin) out(p)).lastTaunt() >= t[0]))
                .waitTicks(2)
                .check("the two within 8 turned on it, the one at 11 did not", () -> server(p -> {
                    FamiliarEntity g = out(p);
                    t[1] = ((Gravikin) g).lastTaunt();
                    Zombie a = byId(p, ids[0]);
                    Zombie b = byId(p, ids[1]);
                    Zombie c = byId(p, ids[2]);
                    result(String.format(Locale.ROOT, "taunt at %d ticks: zombie at %.1f on %s, at %.1f on %s, at %.1f on %s",
                            t[1] - t[0], a.distanceTo(g), name(a.getTarget(), g, p), b.distanceTo(g), name(b.getTarget(), g, p),
                            c.distanceTo(g), name(c.getTarget(), g, p)));
                    return a.getTarget() == g && b.getTarget() == g && c.getTarget() != g;
                }));
        int[] held = {0};
        int[] slowed = {0};
        steps.run("no slow measured yet", () -> v[1] = 99);
        for (int i = 0; i < 7; i++) {
            steps.waitTicks(10).run("sample the pull", () -> server(p -> {
                FamiliarEntity g = out(p);
                for (int k = 0; k < 2; k++) {
                    Zombie z = byId(p, ids[k]);
                    if (z != null && z.getTarget() == g) {
                        held[0]++;
                    }
                    if (z != null && z.hasEffect(FamiliarRegistry.DRAG)) {
                        slowed[0]++;
                        AttributeInstance speed = z.getAttribute(Attributes.MOVEMENT_SPEED);
                        AttributeModifier mod = speed.getModifier(CosmicBreach.id("gravity_drag"));
                        double with = speed.getValue();
                        if (mod != null) {
                            speed.removeModifier(mod.id());
                            double without = speed.getValue();
                            speed.addTransientModifier(mod);
                            v[0] = with / without;
                            v[1] = Math.min(v[1], Math.sqrt(z.getBoundingBox().distanceToSqr(g.position().add(0, g.getBbHeight() * 0.5, 0))));
                        }
                    }
                }
                return true;
            }));
        }
        steps.check("they stayed on it through the taunt, and beside it they were slowed to 0.8", () -> {
                    result(String.format(Locale.ROOT, "taunt held %d of 14 samples over 70 ticks; slowed in %d, speed x%.3f (nearest slowed %.2f blocks from its middle)",
                            held[0], slowed[0], v[0], v[1]));
                    return held[0] == 14 && slowed[0] > 0 && Math.abs(v[0] - 0.8) < 1e-6;
                })
                .waitUntil("the taunt's 80 ticks are over", 40, () -> server(p -> p.level().getGameTime() >= t[1] + FamiliarRules.TAUNT_TICKS + 2))
                .check("then they were let go", () -> server(p -> {
                    FamiliarEntity g = out(p);
                    Zombie a = byId(p, ids[0]);
                    Zombie b = byId(p, ids[1]);
                    boolean free = (a == null || a.getTarget() != g) && (b == null || b.getTarget() != g);
                    result("after 80 ticks the taunted zombies target " + (a == null ? "-" : name(a.getTarget(), g, p)) + " and "
                            + (b == null ? "-" : name(b.getTarget(), g, p)) + "; the Gravikin has " + String.format(Locale.ROOT, "%.1f", g.getHealth())
                            + " health left");
                    return free;
                }));
        clearZombies(steps);
        dismissAll(steps);
    }

    private static String name(@Nullable LivingEntity target, LivingEntity gravikin, ServerPlayer p) {
        return target == null ? "nothing" : target == gravikin ? "the Gravikin" : target == p ? "the player" : target.getType().toShortString();
    }

    // ------------------------------------------------------------------ the Prism Moth

    private void moth(Steps steps) {
        reset(steps);
        summonByUse(steps, FamiliarKind.PRISM_MOTH);
        long[] stacksAt = new long[4];
        steps.run("a dummy zombie 1.8 south", () -> ids[0] = server(p -> zombie(p, at(0, 0, 1.8), 3.0, false, 400).getId()))
                .waitTicks(20)
                .run("clear", () -> strikes.clear())
                .press(mc.options.keyAttack)
                .waitUntil("the hit landed", 20, () -> strikes.stream().anyMatch(s -> s.entity() == ids[0]));
        for (int n = 1; n <= 3; n++) {
            int want = n;
            steps.waitUntil("Refract " + n, 60, () -> {
                int s = server(p -> Refract.stacks(byId(p, ids[0])));
                if (s >= want && stacksAt[want] == 0) {
                    stacksAt[want] = server(p -> p.level().getGameTime());
                }
                return s >= want;
            });
        }
        steps.check("a stack every 40 ticks, three at most", () -> {
                    result(String.format(Locale.ROOT, "Refract: stacks 1, 2, 3 at %d and %d ticks apart", stacksAt[2] - stacksAt[1],
                            stacksAt[3] - stacksAt[2]));
                    return Math.abs(stacksAt[2] - stacksAt[1] - 40) <= 1 && Math.abs(stacksAt[3] - stacksAt[2] - 40) <= 1;
                })
                .waitTicks(10)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .run("look from the side", () -> camera(at(3.2, 1.8, 1.4), at(0, 1.6, 1.8)))
                .waitTicks(3)
                .screenshot("moth_refract_three")
                .run("the player's eyes", this::uncamera)
                .check("still three", () -> server(p -> Refract.stacks(byId(p, ids[0])) == 3))
                .run("the dummy back 1.8 south (the L1 pushed it)", () -> server(p -> {
                    Vec3 d = at(0, 0, 1.8);
                    byId(p, ids[0]).teleportTo(d.x, d.y, d.z);
                    byId(p, ids[0]).setDeltaMovement(Vec3.ZERO);
                    return true;
                }))
                .command("cosmicbreach debug resonance 100")
                .waitUntil("the ability is ready", 40, () -> machine().abilityCooldown() == 0 && machine().resonance() >= 99)
                .run("no rolled crits; clear", () -> {
                    noCrits = true;
                    strikes.clear();
                    hurts.clear();
                })
                .press(mc.options.keyUse)
                .waitUntil("the Zenith hit the dummy", 30, () -> strikes.stream().anyMatch(s -> s.entity() == ids[0] && s.kind() == MoveKind.ABILITY))
                .waitTicks(2)
                .run("note", () -> {
                    v[0] = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().equals("player")).findFirst().get().amount();
                    v[3] = server(p -> (double) Refract.stacks(byId(p, ids[0])));
                })
                .check("the ability consumed the three stacks", () -> v[3] == 0);
        setMode(steps, FamiliarMode.PASSIVE);
        steps.run("the dummy back down, no Refract", () -> server(p -> {
                    Zombie z = byId(p, ids[0]);
                    z.removeEffect(FamiliarRegistry.REFRACT);
                    return true;
                }))
                .waitTicks(40)
                .run("the dummy back 1.8 south", () -> server(p -> {
                    Zombie z = byId(p, ids[0]);
                    Vec3 d = at(0, 0, 1.8);
                    z.teleportTo(d.x, d.y, d.z);
                    z.setDeltaMovement(Vec3.ZERO);
                    z.removeEffect(FamiliarRegistry.REFRACT);
                    return true;
                }))
                .command("cosmicbreach debug resonance 100")
                .waitUntil("the ability is ready again", 200, () -> machine().abilityCooldown() == 0 && mc.player.onGround()
                        && server(p -> byId(p, ids[0]).onGround()))
                .command("cosmicbreach debug resonance 100")
                .waitTicks(3)
                .run("clear", () -> {
                    strikes.clear();
                    hurts.clear();
                })
                .press(mc.options.keyUse)
                .waitUntil("a plain Zenith hit the dummy", 30, () -> hurts.stream().anyMatch(h -> h.entity() == ids[0] && h.type().equals("player")))
                .check("the Refracted Zenith hit x1.30 of the plain one", () -> {
                    v[1] = hurts.stream().filter(h -> h.entity() == ids[0] && h.type().equals("player")).findFirst().get().amount();
                    double ratio = v[0] / v[1];
                    result(String.format(Locale.ROOT, "Refract: Zenith %.3f on three stacks, %.3f plain: x%.3f; stacks after %d",
                            v[0], v[1], ratio, (int) v[3]));
                    noCrits = false;
                    return Math.abs(ratio - 1.30) < 0.002;
                });
        clearZombies(steps);
        // the cleanse: a Rift taken at once, the next only 600 ticks later
        steps.run("Rift on the player, two stacks", () -> server(p -> {
                    Rift.addStack(p);
                    Rift.addStack(p);
                    t[0] = p.level().getGameTime();
                    return true;
                }))
                .waitUntil("the moth cleansed it", 20, () -> server(p -> Rift.stacks(p) == 0))
                .run("note", () -> t[1] = server(p -> p.level().getGameTime()))
                .waitTicks(20)
                .run("Rift again, and a minute of Weakness (Rift itself runs out after 100 ticks)", () -> server(p -> {
                    Rift.addStack(p);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 1200));
                    return true;
                }))
                .waitTicks(40)
                .check("the second Rift stays while the cleanse rests", () -> server(p -> {
                    int left = ((PrismMoth) out(p)).cleanseLeft(p.level().getGameTime());
                    v[2] = left;
                    return Rift.stacks(p) == 1 && left > 500;
                }))
                .waitUntil("the second cleanse", 640, () -> server(p -> !p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)))
                .check("600 ticks after the first", () -> {
                    long second = server(p -> p.level().getGameTime());
                    result(String.format(Locale.ROOT, "cleanse: a Rift of 2 stacks gone after %d ticks; the next status (Weakness, 60 s) waited, gone %d ticks after the first cleanse",
                            t[1] - t[0], second - t[1]));
                    return t[1] - t[0] <= 2 && Math.abs(second - t[1] - FamiliarRules.CLEANSE_EVERY) <= 2;
                });
        dismissAll(steps);
    }

    // ------------------------------------------------------------------ Aetheria: the Flare, the boss, the looks

    private void aetheria(Steps steps) {
        steps.command("gamemode creative")
                .run("the player's own eyes", this::uncamera)
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 200 0.5")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .command("cosmicbreach weather clear")
                .command("cosmicbreach debug goto reach")
                .waitUntil("standing on a Reach island", 1600, standing(Layer.REACH))
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .command("time set noon");
        if (parts.contains(Part.AETHERIA)) {
            flare(steps);
        }
        looks(steps, "day");
        if (parts.contains(Part.AETHERIA)) {
            boss(steps);
        }
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto deep")
                .waitUntil("standing in the Deep", 1600, standing(Layer.DEEP))
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative());
        looks(steps, "deep");
    }

    private BooleanSupplier standing(Layer layer) {
        int[] still = {0};
        return () -> {
            boolean ok = mc.player != null && mc.level != null && AetheriaWorld.is(mc.level) && mc.player.onGround()
                    && Layer.at(mc.player.getY()) == layer && mc.levelRenderer.hasRenderedAllSections();
            still[0] = ok ? still[0] + 1 : 0;
            return still[0] >= 20;
        };
    }

    private void flare(Steps steps) {
        BlockPos[] brazier = {BlockPos.ZERO};
        steps.command("item replace entity @s hotbar.5 with cosmicbreach:star_egg[cosmicbreach:star_egg=\"prism_moth\"]")
                .waitUntil("the egg in the hotbar", 20, () -> mc.player.getInventory().getItem(5).is(FamiliarRegistry.STAR_EGG.get()))
                .press(mc.options.keyHotbarSlots[5])
                .run("look a little down", () -> {
                    mc.player.setXRot(10f);
                    mc.player.xRotO = 10f;
                })
                .waitTicks(2)
                .press(mc.options.keyDrop)
                .waitUntil("the egg lies on the ground", 60, () -> server(p -> p.serverLevel().getEntitiesOfClass(ItemEntity.class,
                        p.getBoundingBox().inflate(6), e -> e.getItem().is(FamiliarRegistry.STAR_EGG.get()) && e.onGround()).size() == 1))
                .check("the Flare isn't up yet: it doesn't hatch", () -> !SolarFlare.active(mc.level))
                .command("cosmicbreach weather flare reach")
                .command("cosmicbreach weather skip reach")
                .waitUntil("the Flare burns", 80, () -> SolarFlare.active(mc.level) && server(p -> SolarFlare.active(p.level())))
                .run("note", () -> t[0] = server(p -> p.level().getGameTime()))
                .waitUntil("the egg hatched where it lay", 40, () -> server(p -> !p.serverLevel().getEntitiesOfClass(ItemEntity.class,
                        p.getBoundingBox().inflate(6), e -> FamiliarLanternItem.bond(e.getItem()) != null).isEmpty()))
                .run("note", () -> t[1] = server(p -> p.level().getGameTime()))
                .run("walk to it", () -> server(p -> {
                    ItemEntity e = p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6),
                            i -> FamiliarLanternItem.bond(i.getItem()) != null).get(0);
                    e.setNoPickUpDelay();
                    p.teleportTo(e.getX(), e.getY(), e.getZ());
                    return true;
                }))
                .waitUntil("picked up: a Prism Moth's lantern", 40, () -> lanternSlot(FamiliarKind.PRISM_MOTH) >= 0
                        || mc.player.getInventory().items.stream().anyMatch(s -> FamiliarLanternItem.bond(s) != null
                        && FamiliarLanternItem.bond(s).kind() == FamiliarKind.PRISM_MOTH))
                .check("it hatched under the Flare within half a second", () -> {
                    result(String.format(Locale.ROOT, "flare: an egg lying out hatched %d ticks after the Flare broke; its lantern holds a Prism Moth", t[1] - t[0]));
                    return t[1] - t[0] <= 12;
                })
                .run("a brazier on the open ground ahead", () -> server(p -> {
                    ServerLevel level = p.serverLevel();
                    Vec3 ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2));
                    int x = (int) Math.floor(ahead.x);
                    int z = (int) Math.floor(ahead.z);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    brazier[0] = new BlockPos(x, y, z);
                    level.setBlock(brazier[0], FamiliarRegistry.BRAZIER.get().defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .command("item replace entity @s hotbar.6 with cosmicbreach:star_egg[cosmicbreach:star_egg=\"gravikin\"]")
                .waitUntil("that egg in the hotbar", 20, () -> mc.player.getInventory().getItem(6).is(FamiliarRegistry.STAR_EGG.get()))
                .press(mc.options.keyHotbarSlots[6]);
        lookAt(steps, () -> Vec3.atCenterOf(brazier[0]).add(0, 0.2, 0));
        arm(steps, "the egg set in the brazier", 40, p -> p.serverLevel().getBlockEntity(brazier[0]) instanceof BrazierBlockEntity b && b.warming(),
                p -> t[2] = p.level().getGameTime());
        steps.press(mc.options.keyUse)
                .waitUntil("the brazier took the egg", 40, () -> watchFired)
                .waitUntil("the brazier hatched it at once", 40, () -> server(p -> p.serverLevel().getBlockEntity(brazier[0]) instanceof BrazierBlockEntity b
                        && b.held().is(FamiliarRegistry.LANTERN.get())))
                .check("within a second under the Flare, not 10 minutes", () -> {
                    long took = server(p -> p.level().getGameTime()) - t[2];
                    result("flare: a brazier on open ground hatched its egg in " + took + " ticks (10 minutes is " + FamiliarRules.HATCH_TICKS + ")");
                    return took <= 22;
                })
                .run("the brazier goes", () -> server(p -> {
                    p.serverLevel().setBlock(brazier[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }))
                .command("kill @e[type=item,distance=..8]")
                .command("cosmicbreach weather clear reach")
                .waitUntil("calm", 60, () -> !SolarFlare.active(mc.level))
                .press(mc.options.keyHotbarSlots[0]);
    }

    private void boss(Steps steps) {
        steps.command("gamemode creative")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run fill -6 63 -6 6 63 6 minecraft:smooth_stone")
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 0.5 64 5.5 180 0")
                .waitUntil("on the platform", 400, () -> mc.player.onGround() && Math.abs(mc.player.getY() - 64) < 0.1)
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative());
        summonByUse(steps, FamiliarKind.GRAVIKIN);
        setMode(steps, FamiliarMode.GUARD);
        steps.waitUntil("it perched", 60, () -> server(p -> ((Gravikin) out(p)).perched()))
                .command("cosmicbreach debug heliarch summon")
                .waitUntil("the Heliarch rises", 80, HeliarchScenario::fighting)
                .command("cosmicbreach debug heliarch hold 100000")
                .waitUntil("the Gravikin's taunt reached it", 60, () -> HeliarchScenario.ask(h -> h.threat().threat(mc.player.getUUID())) > 0)
                .check("150 threat for its owner", () -> {
                    double threat = HeliarchScenario.ask(h -> h.threat().threat(mc.player.getUUID()));
                    result(String.format(Locale.ROOT, "boss: the Gravikin's taunt gave its owner %.1f threat on the Hollow Heliarch (150)", threat));
                    return Math.abs(threat - 150.0) < 1e-9;
                })
                .command("cosmicbreach debug heliarch reset")
                .waitUntil("the Heliarch is gone", 60, () -> !HeliarchScenario.fighting());
        dismissAll(steps);
        steps.command("execute in " + AetheriaWorld.LEVEL.location() + " run fill -6 63 -6 6 63 6 minecraft:air replace minecraft:smooth_stone");
    }

    /** Each familiar beside the player: from the side at 3 blocks, and close. */
    private void looks(Steps steps, String where) {
        for (FamiliarKind kind : FamiliarKind.values()) {
            summonByUse(steps, kind);
            setMode(steps, FamiliarMode.GUARD);
            steps.waitTicks(50)
                    .run("note how it settled", () -> result(where + " " + kind.id() + ": " + server(p -> {
                        FamiliarEntity f = out(p);
                        return f instanceof Gravikin g ? "hops " + g.hops() + ", perched " + g.perched()
                                : String.format(Locale.ROOT, "%.2f blocks from its owner", f.distanceTo(p));
                    })))
                    .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                    .run("a camera 3.2 blocks off the player's side", () -> {
                        Vec3 p = mc.player.position();
                        Vec3 side = Vec3.directionFromRotation(0, mc.player.getYRot() - 90f).scale(3.2);
                        camera(p.add(side).add(0, 1.5, 0), p.add(0, 1.0, 0));
                    })
                    .waitTicks(4)
                    .screenshot(where + "_" + kind.id())
                    .run("a camera 1.4 blocks from it", () -> {
                        FamiliarEntity f = mc.level.getEntitiesOfClass(FamiliarEntity.class, mc.player.getBoundingBox().inflate(8)).stream()
                                .findFirst().orElse(null);
                        if (f == null) {
                            throw new Steps.Failure("no familiar near the player to photograph");
                        }
                        Vec3 c = f.position().add(0, f.getBbHeight() * 0.5, 0);
                        Vec3 from = c.add(Vec3.directionFromRotation(15, mc.player.getYRot() + 140f).scale(-1.4));
                        camera(from, c);
                    })
                    .waitTicks(3)
                    .screenshot(where + "_" + kind.id() + "_close")
                    .run("the player's own eyes", this::uncamera);
            dismissAll(steps);
        }
    }

    /** Key helpers the steps call from inside a check. */
    private static final class KeyPress {
        private KeyPress() {
        }

        /** Presses the hotbar key of {@code slot}, as a player would. */
        static void select(Minecraft mc, int slot) {
            if (slot >= 0) {
                net.minecraft.client.KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
            }
        }

        static void tap(net.minecraft.client.KeyMapping key) {
            net.minecraft.client.KeyMapping.click(key.getKey());
        }
    }
}
