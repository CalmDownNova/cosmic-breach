package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.accessory.Accessories;
import com.cosmicbreach.accessory.Accessory;
import com.cosmicbreach.accessory.AccessoryRegistry;
import com.cosmicbreach.accessory.AccessoryRules;
import com.cosmicbreach.accessory.CompassTarget;
import com.cosmicbreach.accessory.HaloState;
import com.cosmicbreach.accessory.PullFields;
import com.cosmicbreach.client.accessory.CompassClient;
import com.cosmicbreach.client.accessory.HaloRenderer;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.structure.ThreadRenderer;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.server.CombatHooks;
import com.cosmicbreach.combat.server.HitModifiers;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlockEntity;
import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.KineticThreadBlock;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/**
 * The accessories (G6a, GDD 5.2) through the real key path, every rule measured on the server. Each accessory goes on
 * by using it from the hand (the Curios route: right click), into its slot. Dodges and parries are timed on the server
 * tick: the zombie strikes on the first server tick that finds the machine in the window, so they hold under load.
 * <ul>
 *   <li>the slots: two rings, a necklace and a charm; four worn at once, the Curios screen photographed; a second copy
 *       of a ring refused;</li>
 *   <li>Twin Comet Band: from a jump, two chained air dashes fly level, the first free (one charge spent), against a
 *       plain chain that sinks and spends two;</li>
 *   <li>Leechstar Signet: a parried zombie blow heals 2 plus a quarter of it;</li>
 *   <li>Perihelion Loop: 13% crit chance at zero Agility; the perfect dodge's crit hits x1.75 (not x1.5) inside the 40
 *       ticks after the dash, and the bonus is gone after them;</li>
 *   <li>Gravity Loop: a 12-block plunge hits past the motion value cap and opens a well that pulls two zombies in;</li>
 *   <li>Heart of a Dying Star: 24 max health; a hit to 25% health sets off the nova (8 on each zombie within 5, none
 *       farther), Resistance II for 60 ticks, then a 90 s rest;</li>
 *   <li>Choir Pendant: max Resonance 120; a Zenith through six zombies gives 15 (the cap), through two gives 6;</li>
 *   <li>Halo of Nine: three shards, photographed; three arrows each break one and hurt no one, the fourth hurts; they
 *       grow back after 120 ticks; at Arcane 20 they cut a zombie on the orbit for 3 about three times a second;</li>
 *   <li>Event Horizon Lens: a 6-tick parry; a parry in its first two ticks drops a black hole on the attacker that
 *       pulls two zombies in, a parry on its fifth tick (past the plain window) drops none;</li>
 *   <li>Hourglass of Vesper: a perfect dodge slows the zombies within 6 blocks to 30% for 20 ticks, not one at 9;</li>
 *   <li>Sunshard Compass: it points at the nearest vault, then at the next once that one is opened, shown beside the
 *       hotbar; a tripwire's thread shows at a sprint from 6 blocks, and hides again without it.</li>
 * </ul>
 */
public final class CuriosScenario implements Scenario {
    private static final String TAG = "cb_curios";
    private static final double MERIDIAN_BASE = 5.0;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;

    /** What the server watch does: on the first server tick {@code when} holds for the player, {@code then}. */
    private record Watch(Predicate<ServerPlayer> when, Consumer<ServerPlayer> then) {
    }

    private volatile @Nullable Watch watch;
    private volatile int watchLeft;
    private volatile boolean watchFired;
    /** Server ticks counted by the watch since it saw the player parrying (the Lens's late parry). */
    private volatile int parryTicks = -1;

    /** Damage the player was about to take (before any parry), and damage each test zombie took, as the server saw it. */
    private volatile float playerIncoming = Float.NaN;
    private record Hurt(int entity, float amount, String type, long time) {
    }

    private final List<Hurt> hurts = new CopyOnWriteArrayList<>();
    /** The engine's hits on test zombies: which move, its motion value, a guaranteed crit or not. */
    private record Strike(int entity, double mv, boolean critGuaranteed, long time) {
    }

    private final List<Strike> strikes = new CopyOnWriteArrayList<>();
    /** Every parry the server granted, as the engine told its gear: early or not. */
    private final List<Boolean> parries = new CopyOnWriteArrayList<>();

    /** Client samples while a dash chain runs, and where each dash started. */
    private volatile boolean sampling;
    private final List<Vec3> dashStarts = new CopyOnWriteArrayList<>();
    private final List<Vec3> samples = new CopyOnWriteArrayList<>();

    @Override
    public int timeBudgetSeconds() {
        return 480;
    }

    // ------------------------------------------------------------------ listeners

    private void onServerTick(ServerTickEvent.Post event) {
        Watch w = watch;
        if (w == null) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (w.when().test(player)) {
                watch = null;
                try {
                    w.then().accept(player);
                    watchFired = true;
                } catch (RuntimeException e) {
                    CosmicBreach.LOGGER.error("[cosmicbreach] curios: the watch's action failed", e); // never into the server's tick
                }
                return;
            }
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

    private void listen() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer) {
                playerIncoming = event.getAmount();
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(TAG)) {
                hurts.add(new Hurt(event.getEntity().getId(), event.getAmount(), event.getSource().getMsgId(),
                        event.getEntity().level().getGameTime()));
            }
        });
        HitResolver.onStrike((player, move, target, point) -> {
            if (target.getTags().contains(TAG)) {
                strikes.add(new Strike(target.getId(), move.mv(), move.critGuaranteed(), player.level().getGameTime()));
            }
        });
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public void onParried(ServerPlayer player, PlayerCombat combat, @Nullable LivingEntity attacker, float amount, boolean early) {
                parries.add(early);
            }
        });
        ClientCombat.addEventListener(events::add);
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.DashStarted && mc.player != null) {
                dashStarts.add(mc.player.position());
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (sampling && mc.player != null) {
                samples.add(mc.player.position());
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private CombatStateMachine machine() {
        return PlayerCombat.of(mc.player).machine();
    }

    private static CombatStateMachine serverMachine(ServerPlayer p) {
        return PlayerCombat.of(p).machine();
    }

    private Vec3 at(double dx, double dy, double dz) {
        return new Vec3(base.getX() + 0.5 + dx, base.getY() + dy, base.getZ() + 0.5 + dz);
    }

    /** A zombie that stands where it is put: AI on but no goals, a pumpkin against the sun, 200 health, no armor. */
    private static Zombie zombie(ServerPlayer p, Vec3 at, double attack) {
        ServerLevel level = p.serverLevel();
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) {
            throw new Steps.Failure("could not create a zombie");
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z)));
        z.moveTo(at.x, at.y, at.z, yaw, 0f);
        z.setYHeadRot(yaw);
        z.setYBodyRot(yaw);
        z.setBaby(false);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        z.goalSelector.removeAllGoals(goal -> true);
        z.targetSelector.removeAllGoals(goal -> true);
        z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        z.setHealth(200f);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
        z.addTag(TAG);
        level.addFreshEntity(z);
        return z;
    }

    private static List<Zombie> zombies(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40), z -> z.getTags().contains(TAG));
    }

    /** The test zombie nearest to {@code at} (server). */
    private static Zombie nearest(ServerPlayer p, Vec3 at) {
        Zombie best = null;
        for (Zombie z : zombies(p)) {
            if (best == null || z.position().distanceToSqr(at) < best.position().distanceToSqr(at)) {
                best = z;
            }
        }
        if (best == null) {
            throw new Steps.Failure("no test zombie");
        }
        return best;
    }

    private static void strike(ServerPlayer player, Zombie zombie) {
        zombie.swing(InteractionHand.MAIN_HAND);
        player.invulnerableTime = 0;
        zombie.doHurtTarget(player);
    }

    private void clearZombies(Steps steps) {
        steps.run("the test zombies are gone", () -> ServerQuery.ask(p -> {
            for (Zombie z : zombies(p)) {
                z.discard();
            }
            return true;
        })).waitTicks(2);
    }

    /** Back to the middle, facing south (+Z), Meridian in hand, nothing worn, full health. */
    private void reset(Steps steps) {
        clearZombies(steps);
        steps.run("back to the camera of the player", () -> {
                    if (camera != null) {
                        camera.remove();
                        camera = null;
                    }
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                })
                .command("curios clear @s");
        teleport(steps, 0, 0, 0f);
        steps.press(mc.options.keyHotbarSlots[0])
                .run("full health", () -> ServerQuery.ask(p -> {
                    p.setHealth(p.getMaxHealth());
                    return true;
                }))
                .waitUntil("on the ground with nothing worn", 40, () -> mc.player.onGround() && ServerQuery.ask(p ->
                        CuriosApi.getCuriosInventory(p).map(inv -> inv.findCurios(s -> !s.isEmpty()).isEmpty()).orElse(false)));
    }

    /** To the middle plus {@code dx}, {@code dz}, facing {@code yaw}, level (the middle is read when the step runs). */
    private void teleport(Steps steps, double dx, double dz, float yaw) {
        steps.run("to " + dx + ", " + dz + " from the middle", () -> ServerQuery.ask(p -> {
                    Vec3 to = at(dx, 0, dz);
                    p.teleportTo(p.serverLevel(), to.x, to.y, to.z, yaw, 0f);
                    return true;
                }))
                .look(yaw, 0)
                .waitTicks(2);
    }

    /** Puts {@code a} on the way a player does: into the hotbar's second slot, selected, used (right click). */
    private void equip(Steps steps, Accessory a) {
        steps.command("item replace entity @s hotbar.1 with " + a.id())
                .waitUntil("the " + a.path() + " is in the hotbar", 20, () -> mc.player.getInventory().getItem(1).is(a.item()))
                .press(mc.options.keyHotbarSlots[1])
                .waitUntil("it is in hand", 10, () -> mc.player.getMainHandItem().is(a.item()))
                .press(mc.options.keyUse)
                .waitUntil("the " + a.path() + " is worn in a " + a.slot().id() + " slot", 40, () -> ServerQuery.ask(p ->
                        CuriosApi.getCuriosInventory(p).flatMap(inv -> inv.findFirstCurio(a.item()))
                                .map(r -> r.slotContext().identifier().equals(a.slot().id())).orElse(false)))
                .waitUntil("and the client knows", 20, () -> CuriosApi.getCuriosInventory(mc.player)
                        .map(inv -> inv.isEquipped(a.item())).orElse(false))
                .press(mc.options.keyHotbarSlots[0]);
    }

    /** A camera at the middle plus {@code eye}, looking at the middle plus {@code target} (read when the step runs). */
    private void sideCamera(Steps steps, Vec3 eye, Vec3 target) {
        steps.run("a camera to the side", () -> {
            camera = DevCamera.create(mc.level);
            camera.place(at(eye.x, eye.y, eye.z), at(target.x, target.y, target.z));
            camera.use();
            mc.options.hideGui = true;
        }).waitTicks(3);
    }

    private void playerCamera(Steps steps) {
        steps.run("the player's eyes again", () -> {
            if (camera != null) {
                camera.remove();
                camera = null;
            }
            mc.options.hideGui = false;
        }).waitTicks(2);
    }

    private void result(String line) {
        summary.add(line);
        CosmicBreach.LOGGER.info("[cosmicbreach] curios: {}", line);
    }

    // ------------------------------------------------------------------ steps

    @Override
    public void steps(Steps steps) {
        listen();
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("clear @s")
                .command("gamerule doDaylightCycle false")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .command("gamerule naturalRegeneration false")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .command("cosmicbreach debug stats 0 0 0 0")
                .command("give @s cosmicbreach:meridian")
                .run("note the middle", () -> base = mc.player.blockPosition())
                .waitUntil("Meridian in hand", 20, () -> mc.player.getMainHandItem().is(com.cosmicbreach.registry.ModItems.MERIDIAN.get()));
        slots(steps);
        cometBand(steps);
        leechstar(steps);
        perihelion(steps);
        gravityLoop(steps);
        heart(steps);
        pendant(steps);
        halo(steps);
        lens(steps);
        hourglass(steps);
        compass(steps);
        reset(steps);
        steps.command("gamerule naturalRegeneration true")
                .log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ the slots

    private void slots(Steps steps) {
        steps.waitUntil("two ring slots, a necklace, a charm and the satchel's back slot, nothing else", 40, () -> ServerQuery.ask(p -> {
            ICuriosItemHandler inv = CuriosApi.getCuriosInventory(p).orElse(null);
            if (inv == null) {
                return false;
            }
            Map<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> c = inv.getCurios();
            boolean ok = c.size() == 4 && c.containsKey("back") && c.get("back").getSlots() == 1 && c.containsKey("ring") && c.get("ring").getSlots() == 2
                    && c.containsKey("necklace") && c.get("necklace").getSlots() == 1
                    && c.containsKey("charm") && c.get("charm").getSlots() == 1;
            if (ok) {
                result("slots: ring x2, necklace x1, charm x1, back x1 (" + inv.getSlots() + " in all)");
            }
            return ok;
        }));
        reset(steps);
        equip(steps, Accessory.TWIN_COMET_BAND);
        equip(steps, Accessory.PERIHELION_LOOP);
        equip(steps, Accessory.HEART_OF_A_DYING_STAR);
        equip(steps, Accessory.HALO_OF_NINE);
        steps.command("item replace entity @s hotbar.1 with cosmicbreach:twin_comet_band")
                .press(mc.options.keyHotbarSlots[1])
                .press(mc.options.keyUse)
                .waitTicks(6)
                .check("a second Twin Comet Band stays in the hand", () -> mc.player.getMainHandItem().is(Accessory.TWIN_COMET_BAND.item())
                        && ServerQuery.ask(p -> CuriosApi.getCuriosInventory(p).map(inv -> inv.findCurios(Accessory.TWIN_COMET_BAND.item()).size())
                        .orElse(0)) == 1)
                .command("item replace entity @s hotbar.1 with minecraft:air")
                .press(mc.options.keyHotbarSlots[0])
                .check("four worn: both rings, the necklace, the charm", () -> ServerQuery.ask(p -> {
                    List<SlotResult> worn = CuriosApi.getCuriosInventory(p).map(inv -> inv.findCurios(s -> !s.isEmpty())).orElse(List.of());
                    List<String> where = new ArrayList<>();
                    for (SlotResult r : worn) {
                        where.add(r.slotContext().identifier() + "/" + r.slotContext().index() + "=" + r.stack().getItem());
                    }
                    result("worn: " + where);
                    return worn.size() == 4;
                }))
                .run("open the Curios screen", () -> PacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY)))
                .waitUntil("the Curios screen is open", 40, () -> mc.screen instanceof CuriosScreen)
                .waitTicks(5)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("curios_screen")
                .run("close it", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null);
    }

    // ------------------------------------------------------------------ Twin Comet Band

    private void cometBand(Steps steps) {
        for (boolean band : new boolean[] {true, false}) {
            String name = band ? "with the band" : "without it";
            double[] y0 = {0};
            Vec3[] start = {Vec3.ZERO};
            int[] charges = {0};
            reset(steps);
            if (band) {
                equip(steps, Accessory.TWIN_COMET_BAND);
            }
            steps.waitUntil("two dash charges on both sides", 100, () -> machine().dashCharges() == 2
                            && ServerQuery.ask(p -> serverMachine(p).dashCharges() == 2))
                    .run("clear", () -> {
                        samples.clear();
                        events.clear();
                        dashStarts.clear();
                    })
                    .press(mc.options.keyJump)
                    .waitUntil("the top of the jump", 20, () -> !mc.player.onGround() && mc.player.getDeltaMovement().y < 0.02)
                    .run("mark", () -> {
                        y0[0] = mc.player.getY();
                        start[0] = mc.player.position();
                        sampling = true;
                    })
                    .hold(mc.options.keyUp)
                    .press(ModKeyMappings.DASH)
                    .waitTicks(6)
                    .press(ModKeyMappings.DASH)
                    .waitTicks(6)
                    .release(mc.options.keyUp)
                    .run("stop", () -> sampling = false)
                    .run("count the charges", () -> charges[0] = ServerQuery.ask(p -> serverMachine(p).dashCharges()))
                    .check("two dashes started " + name, () -> events.stream().filter(e -> e instanceof CombatEvent.DashStarted).count() == 2)
                    .check("the chain " + name + " measured", () -> {
                        double top = dashStarts.isEmpty() ? y0[0] : dashStarts.get(0).y;
                        double low = samples.stream().mapToDouble(v -> v.y).min().orElse(top);
                        Vec3 end = samples.isEmpty() ? start[0] : samples.get(samples.size() - 1);
                        double across = Math.hypot(end.x - start[0].x, end.z - start[0].z);
                        double drop = top - low;
                        result(String.format(Locale.ROOT, "comet %s: two air dashes, %.2f blocks across, height lost %.3f, charges left %d of 2",
                                name, across, drop, charges[0]));
                        if (band) {
                            return drop < 0.02 && across > 5.5 && charges[0] == 1;
                        }
                        return drop > 0.5 && charges[0] == 0;
                    });
            if (band) {
                steps.waitUntil("landed: the free air dash is back", 60, () -> mc.player.onGround() && machine().airDashesLeft() == 1);
            }
        }
    }

    // ------------------------------------------------------------------ Leechstar Signet

    private void leechstar(Steps steps) {
        float[] before = {0};
        float[] after = {0};
        reset(steps);
        equip(steps, Accessory.LEECHSTAR_SIGNET);
        steps.run("a zombie that hits for 8, 1.8 ahead", () -> ServerQuery.ask(p -> zombie(p, at(0, 0, 1.8), 8.0)))
                .run("health 10", () -> ServerQuery.ask(p -> {
                    p.setHealth(10f);
                    return true;
                }))
                .waitTicks(3)
                .run("clear", () -> playerIncoming = Float.NaN);
        arm(steps, "the zombie strikes into the parry", 30, p -> serverMachine(p).isParrying(), p -> {
            before[0] = p.getHealth();
            strike(p, nearest(p, p.position()));
        });
        steps.press(ModKeyMappings.PARRY)
                .waitUntil("the zombie struck into the server's parry", 60, () -> watchFired)
                .waitTicks(2)
                .run("health after", () -> after[0] = ServerQuery.ask(LivingEntity::getHealth))
                .check("the parry healed 2 plus a quarter of the blow", () -> {
                    double expected = AccessoryRules.leechHeal(playerIncoming);
                    result(String.format(Locale.ROOT, "leechstar: a parried blow of %.2f; health %.2f -> %.2f (+%.2f, expected +%.2f)",
                            playerIncoming, before[0], after[0], after[0] - before[0], expected));
                    return !Float.isNaN(playerIncoming) && Math.abs((after[0] - before[0]) - expected) < 0.01;
                });
    }

    // ------------------------------------------------------------------ Perihelion Loop

    private void perihelion(Steps steps) {
        int[] zombieId = {-1};
        reset(steps);
        equip(steps, Accessory.PERIHELION_LOOP);
        steps.check("crit chance 13% at zero Agility (5% + 8%)", () -> ServerQuery.ask(p ->
                        Math.abs(HitResolver.critChance(p, ProgressionStats.of(p), null) - 0.13) < 1e-9))
                .run("a zombie 1.8 ahead", () -> zombieId[0] = ServerQuery.ask(p -> zombie(p, at(0, 0, 1.8), 3.0).getId()))
                .waitTicks(3)
                .waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && !machine().isDashing())
                .run("clear", () -> {
                    events.clear();
                    strikes.clear();
                    hurts.clear();
                });
        arm(steps, "the zombie strikes into the perfect-dodge window", 30, p -> serverMachine(p).inPerfectDodgeWindow(),
                p -> strike(p, nearest(p, p.position())));
        steps.press(ModKeyMappings.DASH)
                .waitUntil("the zombie struck into the server's perfect dodge window", 60, () -> watchFired)
                .waitUntil("the perfect dodge reached this client", 10, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.PerfectDodge))
                .waitUntil("the dash is over and the player stands still", 40, () -> !machine().isDashing()
                        && mc.player.getDeltaMovement().horizontalDistance() < 0.01)
                .command("tp @e[tag=" + TAG + "] ^ ^ ^2.5")
                .waitTicks(1)
                .check("the crit window is still open", () -> machine().isCritGuaranteedWindow())
                .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                .waitUntil("the hit landed", 20, () -> !strikes.isEmpty() && !hurts.isEmpty())
                .check("the crit after the dash hit x1.75", () -> {
                    Strike s = strikes.get(0);
                    Hurt h = hurts.get(0);
                    double expected = MERIDIAN_BASE * s.mv() * (1.5 + AccessoryRules.PERIHELION_CRIT_DAMAGE);
                    result(String.format(Locale.ROOT, "perihelion: guaranteed crit %s, motion value %.2f, damage %.3f (x1.75 gives %.3f, x1.5 would be %.3f)",
                            s.critGuaranteed(), s.mv(), h.amount(), expected, MERIDIAN_BASE * s.mv() * 1.5));
                    return s.critGuaranteed() && Math.abs(h.amount() - expected) < 0.01;
                })
                .check("the bonus holds inside the window", () -> ServerQuery.ask(p ->
                        HitModifiers.critMultiplierBonus(p, null) == AccessoryRules.PERIHELION_CRIT_DAMAGE))
                .waitTicks(AccessoryRules.PERIHELION_WINDOW + 5)
                .check("and is gone 40 ticks after the dash", () -> ServerQuery.ask(p -> HitModifiers.critMultiplierBonus(p, null) == 0.0));
    }

    // ------------------------------------------------------------------ Gravity Loop

    private void gravityLoop(Steps steps) {
        long[] mark = {0};
        double[] fall = {0};
        double[][] before = {{0, 0}};
        Vec3[] centre = {Vec3.ZERO};
        reset(steps);
        equip(steps, Accessory.GRAVITY_LOOP);
        steps.run("a zombie beside the landing, two more 3.5 out", () -> ServerQuery.ask(p -> {
                    zombie(p, at(0, 0, 1.4), 3.0);
                    zombie(p, at(3.5, 0, 0), 3.0);
                    zombie(p, at(-3.5, 0, 0), 3.0);
                    return true;
                }))
                .waitTicks(3)
                .run("clear", () -> {
                    hurts.clear();
                    strikes.clear();
                    mark[0] = ServerQuery.ask(p -> p.level().getGameTime());
                })
                .command("tp @s ~ ~12 ~")
                .look(0, 80)
                .waitTicks(1)
                .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                .waitUntil("the plunge landed on the server", 100, () -> ServerQuery.ask(p ->
                        PlayerCombat.of(p).server().plungeLandedAt() >= mark[0]))
                .run("the fall and the well", () -> ServerQuery.ask(p -> {
                    fall[0] = PlayerCombat.of(p).server().lastPlungeFall();
                    for (PullFields.Field f : PullFields.live()) {
                        if (f.kind() == PullFields.Kind.WELL && f.owner() == p) {
                            centre[0] = f.centre();
                        }
                    }
                    before[0] = new double[] {flat(nearest(p, at(3.5, 0, 0)).position(), centre[0]),
                            flat(nearest(p, at(-3.5, 0, 0)).position(), centre[0])};
                    return true;
                }))
                .check("a well opened where the plunge landed, listed with the Gravity Wells", () -> ServerQuery.ask(p ->
                        centre[0] != Vec3.ZERO && flat(centre[0], p.position()) < 0.5
                                && GravityWell.live().stream().anyMatch(l -> l.owner() == p && l.radius() == AccessoryRules.LOOP_WELL_RADIUS)))
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("gravity_well")
                .waitTicks(AccessoryRules.LOOP_WELL_TICKS + 2)
                .check("the plunge hit past its cap, and the well pulled both zombies in", () -> ServerQuery.ask(p -> {
                    double d1 = flat(nearest(p, at(3.5, 0, 0)).position(), centre[0]);
                    double d2 = flat(nearest(p, at(-3.5, 0, 0)).position(), centre[0]);
                    Hurt plunge = hurts.isEmpty() ? null : hurts.get(0);
                    Strike s = strikes.isEmpty() ? null : strikes.get(0);
                    double mv = 1.4 + 0.1 * fall[0];
                    double amount = plunge == null ? Double.NaN : plunge.amount();
                    boolean crit = Math.abs(amount - MERIDIAN_BASE * mv * 1.5) < 0.02;
                    result(String.format(Locale.ROOT, "gravity loop: fell %.2f blocks, plunge damage %.3f (uncapped mv %.2f gives %.3f, the cap 2.4 gives %.1f)%s;"
                                    + " zombies 3.5 out pulled to %.2f and %.2f from the well (from %.2f and %.2f)",
                            fall[0], amount, mv, MERIDIAN_BASE * mv, MERIDIAN_BASE * 2.4, crit ? ", a crit" : "", d1, d2, before[0][0], before[0][1]));
                    boolean damage = s != null && (Math.abs(amount - MERIDIAN_BASE * mv) < 0.02 || crit);
                    return fall[0] >= AccessoryRules.LOOP_WELL_FALL && mv > 2.4 && damage
                            && d1 < before[0][0] - 1.5 && d2 < before[0][1] - 1.5;
                }))
                .check("the well is closed after its 20 ticks", () -> ServerQuery.ask(p -> PullFields.live().isEmpty()))
                .look(0, 0);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ------------------------------------------------------------------ Heart of a Dying Star

    private void heart(Steps steps) {
        float[] healthAfter = {0};
        long[] fired = {0};
        reset(steps);
        equip(steps, Accessory.HEART_OF_A_DYING_STAR);
        steps.waitUntil("max health 24 on both sides", 20, () -> mc.player.getMaxHealth() == 24f
                        && ServerQuery.ask(p -> p.getMaxHealth() == 24f))
                .run("three zombies 3 out, one 7.5 out", () -> ServerQuery.ask(p -> {
                    zombie(p, at(3, 0, 0), 3.0);
                    zombie(p, at(-3, 0, 0), 3.0);
                    zombie(p, at(0, 0, 3), 3.0);
                    zombie(p, at(0, 0, 7.5), 3.0);
                    p.setHealth(8f);
                    return true;
                }))
                .waitTicks(3)
                .run("clear", hurts::clear);
        sideCamera(steps, new Vec3(-4.5, 2.6, 4.5), new Vec3(0, 0.6, 0));
        steps.run("a hit of 2 takes the player from 33% to 25%", () -> ServerQuery.ask(p -> {
                    p.invulnerableTime = 0;
                    p.hurt(p.damageSources().generic(), 2f);
                    healthAfter[0] = p.getHealth();
                    fired[0] = p.level().getGameTime();
                    return true;
                }))
                .waitTicks(2)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("nova");
        playerCamera(steps);
        steps.check("the nova hit the three near zombies for 8 and not the far one; Resistance II for 60 ticks", () -> ServerQuery.ask(p -> {
                    List<String> hit = new ArrayList<>();
                    int near = 0;
                    boolean farHit = false;
                    for (Zombie z : zombies(p)) {
                        float lost = 200f - z.getHealth();
                        hit.add(String.format(Locale.ROOT, "%.1f blocks: -%.1f", flat(z.position(), p.position()), lost));
                        if (flat(z.position(), at(0, 0, 7.5)) < 1.0) {
                            farHit = lost > 0;
                        } else if (Math.abs(lost - AccessoryRules.NOVA_DAMAGE) < 0.01) {
                            near++;
                        }
                    }
                    MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
                    long ready = p.getData(AccessoryRegistry.HEART_READY);
                    result(String.format(Locale.ROOT, "heart: max health 24; hit to %.1f of 24 (%.0f%%); nova on %s; Resistance %s; ready again in %d ticks",
                            healthAfter[0], 100 * healthAfter[0] / 24.0, hit, res == null ? "none" : "amplifier " + res.getAmplifier() + " for " + res.getDuration(),
                            ready - fired[0]));
                    return near == 3 && !farHit && res != null && res.getAmplifier() == 1 && res.getDuration() <= 60
                            && ready - fired[0] == AccessoryRules.HEART_COOLDOWN;
                }))
                .run("clear", hurts::clear)
                .run("another hit, still under 30%", () -> ServerQuery.ask(p -> {
                    p.invulnerableTime = 0;
                    p.hurt(p.damageSources().generic(), 1f);
                    return true;
                }))
                .waitTicks(2)
                .check("no second nova while the Heart rests", () -> hurts.stream().noneMatch(h -> h.type().equals("cosmicbreach.dying_star")));
    }

    // ------------------------------------------------------------------ Choir Pendant

    private void pendant(Steps steps) {
        double[] r0 = {0};
        reset(steps);
        equip(steps, Accessory.CHOIR_PENDANT);
        steps.waitUntil("max Resonance 120 on both sides", 20, () -> machine().maxResonance() == 120
                        && ServerQuery.ask(p -> serverMachine(p).maxResonance() == 120))
                .check("Resonance fills to 120, past the plain 100", () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = serverMachine(p);
                    m.syncFromServer(500, m.dashCharges(), m.abilityCooldown()); // clamped to the maximum
                    return m.resonance() == 120.0;
                }));
        for (int enemies : new int[] {6, 2}) {
            int expected = Math.min(AccessoryRules.PENDANT_PER_CAST, AccessoryRules.PENDANT_PER_ENEMY * enemies);
            clearZombies(steps);
            steps.run(enemies + " zombies in front", () -> ServerQuery.ask(p -> {
                        double[][] spots = enemies == 6
                                ? new double[][] {{-0.9, 1.5}, {0, 1.7}, {0.9, 1.5}, {-0.8, 2.5}, {0, 2.7}, {0.8, 2.5}}
                                : new double[][] {{-0.6, 1.8}, {0.6, 1.8}};
                        for (double[] s : spots) {
                            zombie(p, at(s[0], 0, s[1]), 3.0);
                        }
                        return true;
                    }))
                    .waitUntil("the ability is ready", 140, () -> machine().abilityCooldown() == 0
                            && ServerQuery.ask(p -> serverMachine(p).abilityCooldown() == 0))
                    .command("cosmicbreach debug resonance 60")
                    .waitTicks(3)
                    .run("in a fight (no drift), note the Resonance", () -> r0[0] = ServerQuery.ask(p -> {
                        serverMachine(p).onDamaged();
                        return serverMachine(p).resonance();
                    }))
                    .run("in a fight on this side too", () -> machine().onDamaged())
                    .press(mc.options.keyUse)
                    .waitUntil("the Pendant gave " + expected, 40, () -> ServerQuery.ask(p -> Accessories.pendantGiven(p.getUUID())) == expected)
                    .waitTicks(4)
                    .check("Zenith through " + enemies + ": -30 for the cast, +" + expected + " from the Pendant", () -> {
                        double r1 = ServerQuery.ask(p -> serverMachine(p).resonance());
                        int given = ServerQuery.ask(p -> Accessories.pendantGiven(p.getUUID()));
                        result(String.format(Locale.ROOT, "pendant: Zenith through %d zombies: Resonance %.2f -> %.2f (cost 30, gained %d; +3 each, 15 at most)",
                                enemies, r0[0], r1, given));
                        return given == expected && Math.abs(r1 - (r0[0] - 30 + expected)) < 1.0;
                    });
        }
    }

    // ------------------------------------------------------------------ Halo of Nine

    private void halo(Steps steps) {
        float[] health = {0};
        long[] broken = {0};
        reset(steps);
        equip(steps, Accessory.HALO_OF_NINE);
        steps.waitUntil("three shards on both sides", 20, () -> haloCount(mc.player) == 3
                        && ServerQuery.ask(p -> Accessories.haloShards(p)) == 3)
                .waitTicks(2)
                .check("the renderer draws three shards", () -> HaloRenderer.drawnLastFrame() == 3);
        sideCamera(steps, new Vec3(3.2, 1.6, 2.2), new Vec3(0, 1.1, 0));
        steps.run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("halo_side")
                .waitTicks(3)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("halo_side_later");
        playerCamera(steps);
        steps.run("mark the health", () -> health[0] = ServerQuery.ask(LivingEntity::getHealth));
        for (int arrow = 1; arrow <= 4; arrow++) {
            int shot = arrow;
            int[] before = {0};
            steps.run("arrow " + shot + " from 6 blocks ahead", () -> ServerQuery.ask(p -> {
                        before[0] = Accessories.haloShards(p);
                        Arrow a = new Arrow(p.serverLevel(), p.getX(), p.getY() + 1.4, p.getZ() + 6.0, new ItemStack(Items.ARROW), null);
                        a.shoot(0, 0, -1, 1.6f, 0f);
                        a.addTag(TAG);
                        p.serverLevel().addFreshEntity(a);
                        return true;
                    }))
                    .waitUntil("arrow " + shot + " met the halo or the player", 40, () -> ServerQuery.ask(p ->
                            p.serverLevel().getEntitiesOfClass(Arrow.class, p.getBoundingBox().inflate(10),
                                    a -> a.getTags().contains(TAG) && a.getDeltaMovement().lengthSqr() > 0.01).isEmpty()));
            if (shot <= 3) {
                steps.check("arrow " + shot + " broke a shard and hurt no one", () -> ServerQuery.ask(p -> {
                    if (shot == 1) {
                        broken[0] = p.level().getGameTime();
                    }
                    return Accessories.haloShards(p) == before[0] - 1 && p.getHealth() == health[0];
                }));
            } else {
                steps.check("the fourth, with no shard left, hurt", () -> ServerQuery.ask(p -> {
                    result(String.format(Locale.ROOT, "halo: three arrows each broke a shard (3 -> 0) with health %.1f kept; the fourth took %.1f",
                            health[0], health[0] - p.getHealth()));
                    return Accessories.haloShards(p) == 0 && p.getHealth() < health[0];
                }));
            }
        }
        steps.waitUntil("the shards grow back 120 ticks after they broke", 200, () -> ServerQuery.ask(p -> Accessories.haloShards(p) == 3))
                .check("not before", () -> ServerQuery.ask(p -> {
                    long waited = p.level().getGameTime() - broken[0];
                    result("halo: all three back " + waited + " ticks after the first broke (each regrows 120 ticks after it broke)");
                    return waited >= AccessoryRules.HALO_REGROW_TICKS;
                }))
                .command("kill @e[type=minecraft:arrow]")
                .command("cosmicbreach debug stats 0 0 20 0")
                .waitUntil("Arcane 20", 20, () -> ServerQuery.ask(p -> ProgressionStats.of(p).arcane() == 20))
                .run("a zombie on the orbit, planted", () -> ServerQuery.ask(p -> {
                    Zombie z = zombie(p, new Vec3(p.getX() + AccessoryRules.HALO_RADIUS, p.getY(), p.getZ()), 3.0);
                    z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                    return true;
                }))
                .waitTicks(2)
                .run("clear", hurts::clear)
                .waitTicks(40)
                .check("at Arcane 20 the shards cut it for 3, each shard at most every 10 ticks", () -> {
                    List<Hurt> cuts = hurts.stream().filter(h -> h.type().equals("cosmicbreach.halo_shard")).toList();
                    boolean threes = cuts.stream().allMatch(h -> Math.abs(h.amount() - AccessoryRules.HALO_CUT_DAMAGE) < 1e-6);
                    long minGap = Long.MAX_VALUE;
                    for (int i = 1; i < cuts.size(); i++) {
                        minGap = Math.min(minGap, cuts.get(i).time() - cuts.get(i - 1).time());
                    }
                    result(String.format(Locale.ROOT, "halo: at Arcane 20, %d cuts of 3 in 40 ticks on a zombie on the orbit (closest two %d ticks apart)",
                            cuts.size(), minGap == Long.MAX_VALUE ? -1 : minGap));
                    return threes && cuts.size() >= 5 && cuts.size() <= 7;
                })
                .command("cosmicbreach debug stats 0 0 0 0")
                .waitUntil("Arcane 0", 20, () -> ServerQuery.ask(p -> ProgressionStats.of(p).arcane() == 0))
                .run("clear", hurts::clear)
                .waitTicks(30)
                .check("under Arcane 20 they don't cut", () -> hurts.stream().noneMatch(h -> h.type().equals("cosmicbreach.halo_shard")));
    }

    private static int haloCount(net.minecraft.world.entity.player.Player player) {
        HaloState state = player.getData(AccessoryRegistry.HALO);
        return state.worn() ? state.count(player.level().getGameTime()) : 0;
    }

    // ------------------------------------------------------------------ Event Horizon Lens

    private void lens(Steps steps) {
        Vec3[] hole = {Vec3.ZERO};
        double[][] before = {{0, 0}};
        reset(steps);
        equip(steps, Accessory.EVENT_HORIZON_LENS);
        steps.check("the parry lasts 6 ticks", () -> ServerQuery.ask(p -> serverMachine(p).parryWindow()) == 6 && machine().parryWindow() == 6)
                .run("the attacker 1.8 ahead, two zombies 3 to either side of it", () -> ServerQuery.ask(p -> {
                    zombie(p, at(0, 0, 1.8), 3.0);
                    zombie(p, at(3, 0, 1.8), 3.0);
                    zombie(p, at(-3, 0, 1.8), 3.0);
                    return true;
                }))
                .waitTicks(3);
        steps.run("clear", parries::clear);
        arm(steps, "the attacker strikes in the parry's first two ticks", 30, p -> serverMachine(p).inPerfectParryWindow(),
                p -> strike(p, nearest(p, at(0, 0, 1.8))));
        steps.press(ModKeyMappings.PARRY)
                .waitUntil("it struck into the early parry", 60, () -> watchFired)
                .waitTicks(1)
                .check("an early parry", () -> parries.size() == 1 && parries.get(0))
                .check("a black hole on the attacker", () -> ServerQuery.ask(p -> {
                    for (PullFields.Field f : PullFields.live()) {
                        if (f.kind() == PullFields.Kind.BLACK_HOLE && f.owner() == p) {
                            hole[0] = f.centre();
                        }
                    }
                    if (hole[0] == Vec3.ZERO) {
                        return false;
                    }
                    before[0] = new double[] {flat(nearest(p, at(3, 0, 1.8)).position(), hole[0]),
                            flat(nearest(p, at(-3, 0, 1.8)).position(), hole[0])};
                    return flat(hole[0], at(0, 0, 1.8)) < 0.6;
                }))
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("black_hole")
                .waitTicks(AccessoryRules.HOLE_TICKS + 2)
                .check("it pulled both zombies in, then closed", () -> ServerQuery.ask(p -> {
                    double d1 = flat(nearest(p, at(3, 0, 1.8)).position(), hole[0]);
                    double d2 = flat(nearest(p, at(-3, 0, 1.8)).position(), hole[0]);
                    result(String.format(Locale.ROOT, "lens: parry window 6 ticks; an early parry dropped a black hole on the attacker; zombies 3 out pulled to %.2f and %.2f (from %.2f and %.2f)",
                            d1, d2, before[0][0], before[0][1]));
                    return d1 < before[0][0] - 1.5 && d2 < before[0][1] - 1.5 && PullFields.live().isEmpty();
                }))
                .waitTicks(5)
                .run("count the parry's ticks from here", () -> {
                    parryTicks = -1;
                    parries.clear();
                });
        arm(steps, "the attacker strikes on the parry's fifth server tick (a plain parry has whiffed by then)", 40, p -> {
            if (!serverMachine(p).isParrying()) {
                return false;
            }
            parryTicks++;
            return parryTicks >= 4;
        }, p -> strike(p, nearest(p, at(0, 0, 1.8))));
        steps.press(ModKeyMappings.PARRY)
                .waitUntil("it struck into the late parry", 60, () -> watchFired)
                .waitTicks(2)
                .check("still parried (the Lens's two extra ticks), late, and no black hole", () -> ServerQuery.ask(p -> {
                    result("lens: a blow on the parry's fifth tick was parried (early: " + parries + ") with no black hole");
                    return parries.size() == 1 && !parries.get(0) && PullFields.live().isEmpty() && p.getHealth() == p.getMaxHealth();
                }));
    }

    // ------------------------------------------------------------------ Hourglass of Vesper

    private void hourglass(Steps steps) {
        double[] speed = {0};
        reset(steps);
        equip(steps, Accessory.HOURGLASS_OF_VESPER);
        steps.run("the striker 1.8 ahead, two more within 6, one 9.5 out", () -> ServerQuery.ask(p -> {
                    zombie(p, at(0, 0, 1.8), 3.0);
                    zombie(p, at(2, 0, 3), 3.0);
                    zombie(p, at(-3, 0, 2), 3.0);
                    Zombie far = zombie(p, at(0, 0, 9.5), 3.0);
                    speed[0] = far.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
                    return true;
                }))
                .waitTicks(3)
                .waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && !machine().isDashing());
        arm(steps, "the striker strikes into the perfect-dodge window", 30, p -> serverMachine(p).inPerfectDodgeWindow(),
                p -> strike(p, nearest(p, at(0, 0, 1.8))));
        steps.press(ModKeyMappings.DASH)
                .waitUntil("it struck into the perfect dodge", 60, () -> watchFired)
                .waitUntil("the zombies near are slowed", 10, () -> ServerQuery.ask(p ->
                        zombies(p).stream().anyMatch(z -> z.hasEffect(AccessoryRegistry.SLOWED))))
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("hourglass_slow")
                .check("the three within 6 blocks walk at 30%, the one at 9.5 doesn't", () -> ServerQuery.ask(p -> {
                    List<String> seen = new ArrayList<>();
                    int slowed = 0;
                    boolean farSlowed = false;
                    for (Zombie z : zombies(p)) {
                        AttributeInstance move = z.getAttribute(Attributes.MOVEMENT_SPEED);
                        MobEffectInstance e = z.getEffect(AccessoryRegistry.SLOWED);
                        double share = move.getValue() / speed[0];
                        seen.add(String.format(Locale.ROOT, "%.1f from the start: speed x%.2f%s", flat(z.position(), at(0, 0, 0)), share,
                                e == null ? "" : " for " + e.getDuration()));
                        if (flat(z.position(), at(0, 0, 9.5)) < 1.0) {
                            farSlowed = e != null;
                        } else if (e != null && Math.abs(share - (1 - AccessoryRules.HOURGLASS_SLOW)) < 1e-6 && e.getDuration() <= 20) {
                            slowed++;
                        }
                    }
                    result("hourglass: " + seen);
                    return slowed == 3 && !farSlowed;
                }))
                .waitTicks(AccessoryRules.HOURGLASS_TICKS + 2)
                .check("20 ticks later they walk at full speed", () -> ServerQuery.ask(p -> zombies(p).stream().noneMatch(z ->
                        z.hasEffect(AccessoryRegistry.SLOWED) || Math.abs(z.getAttribute(Attributes.MOVEMENT_SPEED).getValue() - speed[0]) > 1e-9)));
    }

    // ------------------------------------------------------------------ Sunshard Compass

    private void compass(Steps steps) {
        BlockPos[] vaults = {BlockPos.ZERO, BlockPos.ZERO};
        BlockPos[] emitter = {BlockPos.ZERO};
        int[] shown = {0};
        reset(steps);
        steps.run("two vaults: 12 east, 20 north", () -> ServerQuery.ask(p -> {
            ServerLevel level = p.serverLevel();
            vaults[0] = base.offset(12, 0, 0);
            vaults[1] = base.offset(0, 0, -20);
            for (BlockPos v : vaults) {
                level.setBlock(v, StructureRegistry.RELIQUARY_VAULT.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            return true;
        }));
        equip(steps, Accessory.SUNSHARD_COMPASS);
        steps.check("the Compass points at the nearest: 12 east", () -> ServerQuery.ask(p -> Accessories.refreshCompass(p))
                        .equals(CompassTarget.at(vaults[0])))
                .waitUntil("and the client knows", 40, () -> vaults[0].equals(CompassClient.target()))
                .look(0, 0)
                .waitTicks(2)
                .check("facing south, the needle turns three quarters (east is to the left)", () -> {
                    double needle = CompassClient.needle(mc.player);
                    result(String.format(Locale.ROOT, "compass: nearest vault 12 east, needle %.3f facing south (0.75 expected), frame %d; beside the hotbar: %s",
                            needle, AccessoryRules.needleFrame(needle), CompassClient.showing()));
                    return Math.abs(needle - 0.75) < 0.02 && CompassClient.showing();
                })
                .look(-45, 0)
                .waitTicks(3)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("compass_hud")
                .run("the player opens the east vault", () -> ServerQuery.ask(p -> {
                    if (p.serverLevel().getBlockEntity(vaults[0]) instanceof VaultBlockEntity v) {
                        v.unlock(p.serverLevel());
                        v.open(p.serverLevel(), p);
                        return true;
                    }
                    throw new Steps.Failure("no vault at " + vaults[0]);
                }))
                .check("now it points at the next: 20 north", () -> ServerQuery.ask(p -> Accessories.refreshCompass(p))
                        .equals(CompassTarget.at(vaults[1])))
                .waitUntil("and the client knows", 40, () -> vaults[1].equals(CompassClient.target()))
                .look(0, 0)
                .waitTicks(2)
                .check("facing south, the needle points behind", () -> {
                    double needle = CompassClient.needle(mc.player);
                    result(String.format(Locale.ROOT, "compass: after opening it, the next vault 20 north, needle %.3f facing south (0.5 expected)", needle));
                    return Math.abs(needle - 0.5) < 0.02;
                })
                .run("open the Curios screen", () -> PacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY)))
                .waitUntil("the Curios screen is open", 40, () -> mc.screen instanceof CuriosScreen)
                .waitTicks(5)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("curios_screen_compass")
                .run("close it", () -> mc.player.closeContainer())
                .waitUntil("closed", 20, () -> mc.screen == null)
                .run("a tripwire 6 blocks east, running north to south", () -> ServerQuery.ask(p -> {
                    ServerLevel level = p.serverLevel();
                    BlockPos a = base.offset(6, 0, -3);
                    BlockPos b = base.offset(6, 0, 7);
                    int length = a.distManhattan(b) - 1;
                    level.setBlock(a, StructureRegistry.KINETIC_EMITTER.get().defaultBlockState().setValue(KineticEmitterBlock.FACING, Direction.SOUTH),
                            Block.UPDATE_ALL);
                    level.setBlock(b, StructureRegistry.KINETIC_EMITTER.get().defaultBlockState().setValue(KineticEmitterBlock.FACING, Direction.NORTH),
                            Block.UPDATE_ALL);
                    if (level.getBlockEntity(a) instanceof KineticEmitterBlockEntity be) {
                        be.setLength(length);
                    }
                    for (int i = 1; i <= length; i++) {
                        level.setBlock(a.relative(Direction.SOUTH, i), StructureRegistry.KINETIC_THREAD.get().defaultBlockState()
                                .setValue(KineticThreadBlock.AXIS, Direction.Axis.Z), Block.UPDATE_ALL);
                    }
                    emitter[0] = a;
                    return true;
                }))
                .waitTicks(1);
        teleport(steps, 0, -3, 0f);
        steps.waitTicks(10);
        for (boolean worn : new boolean[] {true, false}) {
            if (!worn) {
                steps.command("curios clear @s");
                teleport(steps, 0, -3, 0f);
                steps.waitTicks(10);
            }
            int[] tick = {0};
            steps.run("count", () -> {
                        shown[0] = 0;
                        tick[0] = 0;
                    })
                    .hold(mc.options.keySprint)
                    .hold(mc.options.keyUp)
                    .waitUntil("sprinted 30 ticks alongside the thread, 6 blocks off", 60, () -> {
                        tick[0]++;
                        double sp = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
                        Long seen = ThreadRenderer.shownAt(emitter[0]);
                        if (mc.player.isSprinting() && sp > KineticRules.WALK_SPEED * 1.2 && seen != null && seen >= mc.level.getGameTime()) {
                            shown[0]++;
                        }
                        return tick[0] >= 30;
                    })
                    .release(mc.options.keyUp)
                    .release(mc.options.keySprint)
                    .check(worn ? "with the Compass the thread showed at a sprint from 6 blocks" : "without it, it stayed hidden", () -> {
                        result(String.format(Locale.ROOT, "compass: sprinting 6 blocks from a tripwire %s the Compass, the thread showed on %d of 30 ticks",
                                worn ? "with" : "without", shown[0]));
                        return worn ? shown[0] > 5 : shown[0] == 0;
                    });
            if (worn) {
                teleport(steps, 0, -1, -60f);
                steps.look(-60, 10)
                        .hold(mc.options.keySprint)
                        .hold(mc.options.keyUp)
                        .waitTicks(8)
                        .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("compass_tripwire_sprint")
                        .release(mc.options.keyUp)
                        .release(mc.options.keySprint);
            }
        }
    }
}
