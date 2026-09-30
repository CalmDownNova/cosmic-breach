package com.cosmicbreach.sandbox;

import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.combat.server.Suspension;
import com.cosmicbreach.registry.ModAttachments;
import com.cosmicbreach.registry.ModItems;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * {@code /cosmicbreach selftest}: checks the server combat runtime over real server ticks, against
 * real zombies spawned next to the caller, by driving the caller's own server-side state machine
 * (the same one client input drives) and letting the zombies attack for real.
 *
 * <p>The caller is switched to survival (creative players take no damage) with Meridian in the
 * selected hotbar slot; game mode, health, hunger, that slot and the position are restored afterwards.
 * Test zombies wear a carved pumpkin (no sun burn, no armor), have no AI goals (they only move when
 * hit), drop no loot or experience if a hit kills one, and are tagged {@value #MOB_TAG}; they are
 * removed after each check, and leftovers from an interrupted run are removed at the next start.
 *
 * <p>Prints one line per check to chat and the log, then {@code SELFTEST PASS} or
 * {@code SELFTEST FAIL: <checks>}. Run it on flat ground in a world above peaceful difficulty.
 */
public final class SelfTest {
    public static final String MOB_TAG = "cosmicbreach_selftest";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, SelfTest> RUNNING = new LinkedHashMap<>();
    /** A server player can't be hurt for its first 60 ticks; wait a little longer. */
    private static final int SPAWN_PROTECTION_TICKS = 80;
    private static final double L1_MIN_DAMAGE = 4.5;
    private static final double PLUNGE_HEIGHT = 6.0;
    /** A fall shorter than this would take no fall damage anyway, so it would prove nothing. */
    private static final double PLUNGE_MIN_FALL = 3.5;
    /** Held ticks before releasing the charge (the charged move needs 12). */
    private static final int CHARGE_HOLD = 14;
    /** How long the zenith check watches a Suspended zombie (Meridian Suspends for 30). */
    private static final int HOVER_SAMPLE_TICKS = 20;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final List<Check> checks = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    private final List<Mob> mobs = new ArrayList<>();
    private int checkIndex;
    private int stepIndex;
    private boolean done;
    private @Nullable Snapshot saved;

    // Where the checks happen, and what they measure; the checks run one after another.
    private Vec3 base = Vec3.ZERO;
    private Vec3 forward = new Vec3(0, 0, 1);
    private @Nullable Zombie zombie;
    private @Nullable Zombie otherZombie;
    private double zombieHealth;
    private double otherZombieHealth;
    private float playerHealth;
    private long markTick;
    private double l1Damage = Double.NaN;
    private double groundY;
    private double peakY;
    private double hoverStartY;
    private double lowestHoverY;

    private SelfTest(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
        setup();
        l1Front();
        parry();
        stagger();
        dash();
        charged();
        plunge();
        zenithLaunch();
    }

    // ------------------------------------------------------------------ entry points

    public static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (RUNNING.containsKey(player.getUUID())) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.selftest.busy"));
            return 0;
        }
        RUNNING.put(player.getUUID(), new SelfTest(player));
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.selftest.started"), false);
        LOGGER.info("[selftest] started for {}", player.getName().getString());
        return 1;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!RUNNING.isEmpty()) {
            for (SelfTest test : List.copyOf(RUNNING.values())) {
                test.tick();
            }
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SelfTest test = RUNNING.get(event.getEntity().getUUID());
        if (test != null) {
            test.abort("the player left");
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (SelfTest test : List.copyOf(RUNNING.values())) {
            test.abort("the server is stopping");
        }
    }

    /**
     * A test zombie that dies (a crit can kill one) drops no loot: the caller would pick it up, possibly
     * into the hotbar slot the selftest just gave back.
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(MOB_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Nor any experience. */
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(MOB_TAG)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ the checks

    private void setup() {
        check("setup", true)
                .run(() -> {
                    expect(level.getDifficulty() != Difficulty.PEACEFUL, "needs a difficulty above peaceful (zombies despawn)");
                    expect(CombatData.server().weapon(ModItems.MERIDIAN.getId()) != null, "Meridian's weapon data did not load");
                    removeLeftoverMobs();
                    saved = Snapshot.of(player);
                    player.setGameMode(GameType.SURVIVAL);
                    heal();
                    player.getInventory().setItem(player.getInventory().selected, new ItemStack(ModItems.MERIDIAN.get()));
                })
                .waitUntil("spawn protection is over, the player stands on the ground and holds Meridian", 300,
                        () -> player.tickCount >= SPAWN_PROTECTION_TICKS && player.onGround()
                                && machine().weapon() != null && machine().phase() == Phase.IDLE)
                .note("survival, full health, Meridian in hand");
    }

    private void l1Front() {
        check("l1_front", false)
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(2.0));
                    otherZombie = spawnZombie(at(-2.0));
                })
                .waitTicks(5)
                .run(() -> {
                    zombieHealth = zombie.getHealth();
                    otherZombieHealth = otherZombie.getHealth();
                    combat().apply(CombatAction.ATTACK_PRESS);
                    combat().apply(CombatAction.ATTACK_RELEASE);
                    WeaponDef weapon = machine().weapon();
                    MoveInstance move = machine().current();
                    expect(weapon != null && move != null && move.id().equals(weapon.combo().get(0)),
                            "a tap did not start the first combo move");
                })
                .waitUntil("the swing finishes", 40, () -> machine().phase() == Phase.IDLE)
                .run(check -> {
                    double front = zombieHealth - zombie.getHealth();
                    double behind = otherZombieHealth - otherZombie.getHealth();
                    l1Damage = front;
                    check.detail("zombie in front lost %.2f, the one behind %.2f", front, behind);
                    expect(front >= L1_MIN_DAMAGE, "the zombie in front lost only " + fmt(front));
                    expect(behind <= 1e-3, "the zombie behind lost " + fmt(behind));
                });
    }

    private void parry() {
        check("parry", false)
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(1.5));
                    heal();
                })
                .waitTicks(3)
                .run(() -> combat().apply(CombatAction.PARRY))
                .waitTicks(1)
                .run(check -> {
                    expect(machine().isParrying(), "the parry was not up one tick after pressing it");
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    double resonance = machine().resonance();
                    boolean landed = zombie.doHurtTarget(player);
                    expect(!landed, "the zombie's hit landed inside the parry window");
                    expect(player.getHealth() >= before, "health fell from " + fmt(before) + " to " + fmt(player.getHealth()));
                    expect(machine().resonance() > resonance, "the parry gave no Resonance");
                    check.detail("the hit was cancelled one tick into the parry, +%.0f Resonance, zombie staggered: %s",
                            machine().resonance() - resonance, PoiseTracker.isStaggered(zombie) ? "yes" : "no");
                });
    }

    private void stagger() {
        check("stagger", false)
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(1.5));
                    heal();
                })
                .waitTicks(3)
                .run(check -> {
                    double poise = PoiseTracker.poiseOf(zombie);
                    expect(!PoiseTracker.addImpact(zombie, poise - 1), "Impact under the zombie's poise staggered it");
                    expect(PoiseTracker.addImpact(zombie, 1), "reaching the zombie's poise did not stagger it");
                    expect(PoiseTracker.isStaggered(zombie) && zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "the zombie is not staggered");
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    expect(!zombie.doHurtTarget(player) && player.getHealth() >= before, "a staggered zombie's hit landed");
                    check.detail("poise %.0f reached; the staggered zombie deals nothing", poise);
                })
                .waitTicks(PoiseTracker.STAGGER_TICKS + 1)
                .run(check -> {
                    expect(!PoiseTracker.isStaggered(zombie), "the stagger did not end");
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    boolean landed = zombie.doHurtTarget(player);
                    expect(landed && player.getHealth() < before, "the zombie's hit did not land after its stagger ended");
                    check.detail("its hit lands again after %d ticks", PoiseTracker.STAGGER_TICKS);
                    heal();
                });
    }

    private void dash() {
        check("dash_iframes", false)
                .waitUntil("the player stands on the ground", 60, player::onGround)
                // the last check's zombie hit (Impact 10) staggered the player (poise 10): no dash until it ends
                .waitUntil("the player's stagger is over", 30, () -> !machine().isStaggered())
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(1.5));
                    heal();
                })
                .waitTicks(3)
                .run(() -> {
                    expect(machine().dashCharges() >= 1, "no dash charge left");
                    combat().apply(CombatAction.DASH);
                    expect(machine().isDashing(), "the dash did not start");
                })
                .waitTicks(1)
                .run(check -> {
                    expect(machine().isInvulnerable(), "no i-frames one tick into the dash");
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    boolean critBefore = machine().isCritGuaranteedWindow();
                    expect(!zombie.doHurtTarget(player) && player.getHealth() >= before,
                            "the zombie's hit landed during the dash's i-frames");
                    check.detail("the hit was cancelled one tick into the dash%s",
                            machine().isCritGuaranteedWindow() && !critBefore ? " (a perfect dodge)" : "");
                })
                .waitUntil("the dash and its i-frames are over", 20, () -> !machine().isDashing() && !machine().isInvulnerable())
                .run(check -> {
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    boolean landed = zombie.doHurtTarget(player);
                    float taken = before - player.getHealth();
                    expect(landed && taken > 0, "the zombie's hit did not land after the dash");
                    check.detail("after the dash its hit dealt %.2f", taken);
                    heal();
                });
    }

    private void charged() {
        check("charged", false)
                .waitUntil("the machine is idle", 60, () -> machine().phase() == Phase.IDLE && !machine().isDashing())
                .waitTicks(CombatRules.GUARANTEED_CRIT_WINDOW + 5) // no perfect-dodge crit or dash attack carries over
                .run(() -> {
                    markBase();
                    combat().apply(CombatAction.ATTACK_PRESS);
                })
                .waitUntil("holding attack turns into a charge", 60,
                        () -> machine().phase() == Phase.CHARGING && machine().attackHeldTicks() >= CHARGE_HOLD)
                .run(() -> zombie = spawnZombie(at(2.5)))
                .waitTicks(1)
                .run(check -> {
                    zombieHealth = zombie.getHealth();
                    combat().apply(CombatAction.ATTACK_RELEASE);
                    MoveInstance move = machine().current();
                    expect(move != null && move.def().kind() == MoveKind.CHARGED, "releasing did not fire the charged move");
                    check.detail("released after %d ticks, MV %.2f", machine().attackHeldTicks(), move.mv());
                })
                .waitUntil("the charged move hits the zombie", 20, () -> zombie.getHealth() < zombieHealth - 1e-3)
                .run(check -> {
                    double dealt = zombieHealth - zombie.getHealth();
                    expect(!Double.isNaN(l1Damage), "there is no L1 hit to compare with (l1_front failed)");
                    check.detail("the charged release dealt %.2f, L1 %.2f", dealt, l1Damage);
                    expect(dealt > l1Damage, "the charged release dealt " + fmt(dealt) + ", not more than L1's " + fmt(l1Damage));
                })
                .waitUntil("the charged move and its ground wave finish", 60,
                        () -> machine().phase() == Phase.IDLE && combat().server().waves().isEmpty());
    }

    private void plunge() {
        check("plunge", false)
                .waitUntil("the player stands on the ground", 60, player::onGround)
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(1.5));
                    heal();
                })
                .waitTicks(3)
                .run(() -> {
                    zombieHealth = zombie.getHealth();
                    playerHealth = player.getHealth();
                    player.teleportTo(player.getX(), player.getY() + PLUNGE_HEIGHT, player.getZ());
                })
                .waitUntil("the client reports the player in the air", 40, () -> !player.onGround())
                .run(() -> {
                    markTick = level.getGameTime();
                    Context aimingDown = new Context(false, 90f, player.getY());
                    combat().apply(CombatAction.ATTACK_PRESS, aimingDown);
                    combat().apply(CombatAction.ATTACK_RELEASE, aimingDown);
                    expect(combat().isPlunging(), "attacking in the air while aiming down did not start a plunge");
                })
                .waitUntil("the plunge lands", 100, () -> combat().server().plungeLandedAt() >= markTick)
                .waitTicks(2)
                .run(check -> {
                    double fall = combat().server().lastPlungeFall();
                    double dealt = zombieHealth - zombie.getHealth();
                    float taken = playerHealth - player.getHealth();
                    check.detail("fell %.1f blocks; the landing dealt %.2f, the player took %.2f", fall, dealt, taken);
                    expect(fall >= PLUNGE_MIN_FALL, "the fall was only " + fmt(fall) + " blocks");
                    expect(dealt > 0, "the landing did not damage the zombie next to it");
                    expect(taken <= 0, "the player took " + fmt(taken) + " fall damage");
                });
    }

    /** Zenith launches a zombie (poise 20, under the move's 40) to its launch height and Suspends it. */
    private void zenithLaunch() {
        check("zenith_launch", false)
                .waitUntil("the machine is idle", 60, () -> machine().phase() == Phase.IDLE)
                .waitUntil("the player stands on the ground", 60, player::onGround)
                .run(() -> {
                    markBase();
                    zombie = spawnZombie(at(2.0));
                    heal();
                })
                .waitTicks(3)
                .run(check -> {
                    WeaponDef weapon = machine().weapon();
                    expect(weapon != null && weapon.ability().isPresent(), "the weapon has no ability");
                    // Test setup: full Resonance and no cooldown, so the check doesn't depend on the ones before.
                    machine().syncFromServer(machine().maxResonance(), machine().dashCharges(), 0);
                    groundY = zombie.getY();
                    peakY = groundY;
                    zombieHealth = zombie.getHealth();
                    combat().apply(CombatAction.ABILITY_PRESS);
                    combat().apply(CombatAction.ABILITY_RELEASE);
                    MoveInstance move = machine().current();
                    expect(move != null && move.def().kind() == MoveKind.ABILITY, "pressing the ability did not start it");
                })
                .waitUntil("Zenith hits and launches the zombie", 20, () -> suspension() != null)
                .waitUntil("the zombie reaches its apex", 40, () -> {
                    peakY = Math.max(peakY, zombie.getY());
                    Suspension s = suspension();
                    return s != null && s.phase() == Suspension.Phase.HOVERING;
                })
                .run(() -> {
                    hoverStartY = zombie.getY();
                    lowestHoverY = hoverStartY;
                })
                .during(HOVER_SAMPLE_TICKS, () -> lowestHoverY = Math.min(lowestHoverY, zombie.getY()))
                .run(check -> {
                    check.detail("dealt %.2f, launched %.2f blocks, sank %.2f in %d hovering ticks",
                            zombieHealth - zombie.getHealth(), peakY - groundY, hoverStartY - lowestHoverY, HOVER_SAMPLE_TICKS);
                    expect(peakY - groundY >= 3.0 && peakY - groundY <= 4.2, "launched " + fmt(peakY - groundY) + " blocks, not about 3.5");
                    expect(suspension() != null && !zombie.onGround(), "the zombie is no longer Suspended after " + HOVER_SAMPLE_TICKS + " ticks");
                    expect(hoverStartY - lowestHoverY < 1.0, "the zombie sank " + fmt(hoverStartY - lowestHoverY) + " blocks while Suspended");
                })
                .waitUntil("the Suspension ends and the zombie lands", 80, () -> suspension() == null && zombie.onGround())
                .note("then it fell back down");
    }

    private @Nullable Suspension suspension() {
        return zombie == null ? null : zombie.getExistingDataOrNull(ModAttachments.SUSPENSION);
    }

    // ------------------------------------------------------------------ the runner

    private void tick() {
        if (done) {
            return;
        }
        if (player.isRemoved() || !player.isAlive() || player.serverLevel() != level) {
            abort("the player died or changed dimension");
            return;
        }
        while (checkIndex < checks.size()) {
            Check check = checks.get(checkIndex);
            String failure = null;
            try {
                while (stepIndex < check.steps.size()) {
                    if (!check.steps.get(stepIndex).tick(check)) {
                        return; // this step needs more ticks
                    }
                    stepIndex++;
                }
            } catch (CheckFailed e) {
                failure = e.getMessage();
            } catch (RuntimeException e) {
                LOGGER.error("[selftest] a check threw", e);
                LOGGER.debug("[selftest] the check was {}", check.name);
                failure = e.toString();
            }
            discardMobs();
            report(check, failure);
            checkIndex++;
            stepIndex = 0;
            if (failure != null && check.essential) {
                break;
            }
        }
        finish();
    }

    private void report(Check check, @Nullable String failure) {
        if (failure == null) {
            say("selftest " + check.name + ": PASS" + (check.notes.isEmpty() ? "" : " (" + String.join("; ", check.notes) + ")"));
        } else {
            failed.add(check.name);
            say("selftest " + check.name + ": FAIL (" + failure + ")");
        }
    }

    private void finish() {
        done = true;
        RUNNING.remove(player.getUUID());
        discardMobs();
        restore();
        say(failed.isEmpty() ? "SELFTEST PASS" : "SELFTEST FAIL: " + String.join(", ", failed));
    }

    private void abort(String reason) {
        if (done) {
            return;
        }
        String at = checkIndex < checks.size() ? checks.get(checkIndex).name : "the end";
        failed.add(at + " (aborted: " + reason + ")");
        finish();
    }

    private void say(String line) {
        LOGGER.info("[selftest] {}", line);
        player.sendSystemMessage(Component.literal(line));
    }

    // ------------------------------------------------------------------ helpers

    private PlayerCombat combat() {
        return PlayerCombat.of(player);
    }

    private CombatStateMachine machine() {
        return combat().machine();
    }

    private void markBase() {
        base = player.position();
        forward = HitShape.forward(player.getYRot());
    }

    /** A point {@code blocks} ahead of the player (negative is behind), at its feet. */
    private Vec3 at(double blocks) {
        return base.add(forward.scale(blocks));
    }

    private void heal() {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.invulnerableTime = 0;
    }

    private Zombie spawnZombie(Vec3 at) {
        Zombie spawned = EntityType.ZOMBIE.create(level);
        if (spawned == null) {
            throw new CheckFailed("could not create a zombie");
        }
        float yaw = player.getYRot() + 180.0f;
        spawned.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        spawned.setYHeadRot(yaw);
        spawned.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        spawned.setDropChance(EquipmentSlot.HEAD, 0.0f);
        spawned.goalSelector.removeAllGoals(goal -> true);
        spawned.targetSelector.removeAllGoals(goal -> true);
        spawned.addTag(MOB_TAG);
        level.addFreshEntity(spawned);
        mobs.add(spawned);
        return spawned;
    }

    private void discardMobs() {
        mobs.forEach(Entity::discard);
        mobs.clear();
        zombie = null;
        otherZombie = null;
    }

    private void removeLeftoverMobs() {
        level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0), mob -> mob.getTags().contains(MOB_TAG))
                .forEach(Entity::discard);
    }

    private void restore() {
        if (saved == null) {
            return;
        }
        Snapshot s = saved;
        saved = null;
        player.setGameMode(s.gameMode());
        if (!player.isAlive()) {
            // Died mid-test: never touch a dead player's health. Treat the hand item the way death
            // treated the rest of the inventory, so it isn't lost.
            if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                player.getInventory().setItem(s.slot(), s.slotItem());
            } else if (!s.slotItem().isEmpty()) {
                player.drop(s.slotItem(), true, false);
            }
            return;
        }
        player.getInventory().setItem(s.slot(), s.slotItem());
        player.setHealth(Math.min(s.health(), player.getMaxHealth()));
        player.getFoodData().setFoodLevel(s.food());
        player.getFoodData().setSaturation(s.saturation());
        player.getFoodData().setExhaustion(s.exhaustion());
        if (player.serverLevel() == level && player.position().distanceToSqr(s.position()) > 0.25) {
            player.teleportTo(s.position().x, s.position().y, s.position().z);
        }
        player.resetFallDistance();
        player.invulnerableTime = 0;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void expect(boolean ok, String failure) {
        if (!ok) {
            throw new CheckFailed(failure);
        }
    }

    // ------------------------------------------------------------------ check building

    private Check check(String name, boolean essential) {
        Check check = new Check(name, essential);
        checks.add(check);
        return check;
    }

    @FunctionalInterface
    private interface Step {
        /** Runs once per server tick while current; true when finished. Throws CheckFailed to fail. */
        boolean tick(Check check);
    }

    private static final class CheckFailed extends RuntimeException {
        CheckFailed(String message) {
            super(message, null, false, false);
        }
    }

    /** A named list of steps; like the client's autotest steps, but on server ticks. */
    private static final class Check {
        final String name;
        final boolean essential;
        final List<Step> steps = new ArrayList<>();
        final List<String> notes = new ArrayList<>();

        Check(String name, boolean essential) {
            this.name = name;
            this.essential = essential;
        }

        Check run(Runnable action) {
            steps.add(check -> {
                action.run();
                return true;
            });
            return this;
        }

        Check run(java.util.function.Consumer<Check> action) {
            steps.add(check -> {
                action.accept(check);
                return true;
            });
            return this;
        }

        /** The next step runs {@code ticks} server ticks later. */
        Check waitTicks(int ticks) {
            int[] elapsed = {-1};
            steps.add(check -> ++elapsed[0] >= ticks);
            return this;
        }

        /** Runs {@code sample} every tick for {@code ticks} ticks, starting with the current one. */
        Check during(int ticks, Runnable sample) {
            int[] elapsed = {-1};
            steps.add(check -> {
                sample.run();
                return ++elapsed[0] >= ticks;
            });
            return this;
        }

        /** Checks every tick, starting with the current one; fails after {@code timeoutTicks}. */
        Check waitUntil(String what, int timeoutTicks, BooleanSupplier condition) {
            int[] elapsed = {-1};
            steps.add(check -> {
                if (condition.getAsBoolean()) {
                    return true;
                }
                if (++elapsed[0] >= timeoutTicks) {
                    throw new CheckFailed("timed out after " + timeoutTicks + " ticks waiting until " + what);
                }
                return false;
            });
            return this;
        }

        /** A step that adds a fixed detail to the PASS line. */
        Check note(String text) {
            return run(check -> check.detail("%s", text));
        }

        /** Adds a detail to this check's PASS line. */
        void detail(String format, Object... args) {
            notes.add(String.format(Locale.ROOT, format, args));
        }
    }

    /** What the selftest changes on the player, to put back afterwards. */
    private record Snapshot(GameType gameMode, float health, int food, float saturation, float exhaustion,
                            int slot, ItemStack slotItem, Vec3 position) {
        static Snapshot of(ServerPlayer player) {
            int slot = player.getInventory().selected;
            return new Snapshot(player.gameMode.getGameModeForPlayer(), player.getHealth(),
                    player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                    player.getFoodData().getExhaustionLevel(), slot, player.getInventory().getItem(slot).copy(),
                    player.position());
        }
    }
}
