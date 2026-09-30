package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.registry.ModItems;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The body moves: a ground dash forward (C with W held) and a backstep (C alone), each within 15% of
 * 5 blocks; an air dash over its 8 ticks, within 15% of 3.5; holding attack into a charge (screenshot
 * of the charge ring) and releasing it into Meridian Line; a charge cancelled by switching to an empty
 * hotbar slot, on both machines; a jump, a look straight down and an attack
 * into Falling Star, which must land on both machines; then Resonance 100 by the debug command and a
 * held right click: Zenith, and the player rises about 3 blocks and lands without fall damage.
 */
public final class MovementScenario implements Scenario {
    private static final ResourceLocation LINE = CosmicBreach.id("meridian/line");
    private static final ResourceLocation ZENITH = CosmicBreach.id("meridian/zenith");
    private static final double TOLERANCE = 0.15;

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        Options keys = mc.options;
        List<CombatEvent> events = new CopyOnWriteArrayList<>();
        ClientCombat.addEventListener(events::add);
        Vec3[] start = {Vec3.ZERO};
        float[] facing = {0f};
        long[] plungeMark = {0L};
        double[] peak = {0.0};
        float[] health = {0f};
        int[] airborne = {0};

        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach give")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitUntil("standing still and idle with both dash charges", 100,
                        () -> ready(mc) && machine(mc).dashCharges() == machine(mc).maxDashCharges())

                // Dash forward: W and C down in the same tick, W let go the next.
                .run("mark the forward dash's start", () -> mark(mc, start, facing))
                .hold(keys.keyUp)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .release(keys.keyUp)
                .waitTicks(20)
                .log("the forward dash", () -> describe("forward dash (20 ticks)", mc, start[0], facing[0]))
                .check("the forward dash covered 5 blocks give or take 15%", () -> within(distance(mc, start[0]), BodyMotion.DASH_GROUND_BLOCKS))
                .check("the forward dash went forward", () -> along(mc, start[0], facing[0]) > 0.95 * distance(mc, start[0]))

                // Backstep: C with no movement key.
                .waitUntil("standing still again", 40, () -> ready(mc))
                .run("mark the backstep's start", () -> mark(mc, start, facing))
                .press(ModKeyMappings.DASH)
                .waitTicks(20)
                .log("the backstep", () -> describe("backstep (20 ticks)", mc, start[0], facing[0]))
                .check("the backstep covered 5 blocks give or take 15%", () -> within(distance(mc, start[0]), BodyMotion.DASH_GROUND_BLOCKS))
                .check("the backstep went backwards", () -> along(mc, start[0], facing[0]) < -0.95 * distance(mc, start[0]))

                // Hold attack into the charge, then release into Meridian Line.
                .waitUntil("idle", 40, () -> ready(mc))
                .hold(keys.keyAttack)
                .waitTicks(20)
                .check("holding attack turned into a charge past its minimum", () -> machine(mc).phase() == Phase.CHARGING
                        && machine(mc).attackHeldTicks() >= 12)
                .screenshot("movement_charge_ring")
                .release(keys.keyAttack)
                .waitTicks(1)
                .check("releasing fired Meridian Line", () -> current(mc, LINE))
                .waitUntil("idle after the Line", 60, () -> ready(mc))

                // Swapping away mid-charge cancels it on both machines, and letting go then fires nothing.
                .hold(keys.keyAttack)
                .waitUntil("charging again", 30, () -> machine(mc).phase() == Phase.CHARGING)
                .press(keys.keyHotbarSlots[1])
                .check("the swap to an empty slot cancelled the charge here", () -> machine(mc).phase() == Phase.IDLE
                        && machine(mc).weapon() == null)
                .waitUntil("and on the server", 20, () -> ServerQuery.ask(player -> {
                    CombatStateMachine server = PlayerCombat.of(player).machine();
                    return server.phase() == Phase.IDLE && server.weapon() == null;
                }))
                .release(keys.keyAttack)
                .waitTicks(2)
                .check("letting go with an empty hand fired nothing", () -> machine(mc).current() == null && !machine(mc).isAttackHeld())
                .press(keys.keyHotbarSlots[0])
                .waitUntil("Meridian is back in hand", 20, () -> machine(mc).weapon() != null && ready(mc))

                // Falling Star: jump, look down, attack.
                .look(0, 90)
                .run("mark the plunge", () -> {
                    events.clear();
                    plungeMark[0] = ServerQuery.ask(player -> PlayerCombat.of(player).server().plungeLandedAt());
                })
                .press(keys.keyJump)
                .waitUntil("the player is in the air", 10, () -> !mc.player.onGround())
                .waitTicks(3)
                .hold(keys.keyAttack)
                .waitTicks(1)
                .release(keys.keyAttack)
                .check("attacking in the air while looking down started the plunge", () -> {
                    MoveInstance move = machine(mc).current();
                    return move != null && move.def().kind() == MoveKind.PLUNGE;
                })
                .waitUntil("the plunge landed on this client", 40,
                        () -> events.stream().anyMatch(e -> e instanceof CombatEvent.PlungeLanded))
                .waitUntil("the server's machine landed it too", 40,
                        () -> ServerQuery.ask(player -> PlayerCombat.of(player).server().plungeLandedAt()) > plungeMark[0])
                .log("the plunge", () -> events.stream().filter(e -> e instanceof CombatEvent.PlungeLanded)
                        .map(e -> String.format(Locale.ROOT, "plunge landed after falling %.2f blocks (client machine)",
                                ((CombatEvent.PlungeLanded) e).fallBlocks()))
                        .findFirst().orElse("no plunge landing"))

                // Air dash: jump, then C with W held; measured over the dash's own 8 ticks.
                .look(0, 0)
                .waitUntil("a dash charge is back and the player stands idle", 100,
                        () -> ready(mc) && machine(mc).dashCharges() >= 1)
                .press(keys.keyJump)
                .waitUntil("the player is in the air", 10, () -> !mc.player.onGround())
                .waitTicks(1)
                .run("mark the air dash's start", () -> mark(mc, start, facing))
                .hold(keys.keyUp)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .release(keys.keyUp)
                .waitTicks(BodyMotion.DASH_TICKS - 1)
                .log("the air dash", () -> describe("air dash (its 8 ticks)", mc, start[0], facing[0]))
                .check("the air dash covered 3.5 blocks in its 8 ticks, give or take 15%",
                        () -> within(distance(mc, start[0]), BodyMotion.DASH_AIR_BLOCKS))

                // Zenith: Resonance 100, hold right click, rise with it.
                .waitUntil("landed and idle", 60, () -> ready(mc))
                .command("cosmicbreach debug resonance 100")
                .waitUntil("the client's Resonance was synced to 100", 40, () -> machine(mc).resonance() >= 99.0)
                .waitUntil("standing idle with the ability ready", 60, () -> ready(mc) && machine(mc).abilityCooldown() == 0)
                .run("mark the Zenith", () -> {
                    mark(mc, start, facing);
                    peak[0] = mc.player.getY();
                    health[0] = mc.player.getHealth();
                    airborne[0] = 0;
                    events.clear();
                })
                .hold(keys.keyUse)
                .waitUntil("the ability button has been held 12 ticks", 13, () -> {
                    peak[0] = Math.max(peak[0], mc.player.getY());
                    return ++airborne[0] > 12;
                })
                .release(keys.keyUse)
                .waitUntil("the player came back down", 60, () -> {
                    peak[0] = Math.max(peak[0], mc.player.getY());
                    return mc.player.onGround();
                })
                .waitTicks(5)
                .log("the rise", () -> String.format(Locale.ROOT, "Zenith rise peaked %.3f blocks up; health %.1f before, %.1f after",
                        peak[0] - start[0].y, health[0], mc.player.getHealth()))
                .check("the held right click started Zenith", () -> events.stream().anyMatch(
                        e -> e instanceof CombatEvent.MoveStarted s && s.move().id().equals(ZENITH)))
                .check("the local machine lifted the player (a Rise event)", () -> events.stream().anyMatch(e -> e instanceof CombatEvent.Rise))
                .check("the player rose about 3 blocks (2.5 to 3.2)", () -> peak[0] - start[0].y >= 2.5 && peak[0] - start[0].y <= 3.2)
                .check("the landing after the rise did no fall damage", () -> mc.player.getHealth() >= health[0]);
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static boolean current(Minecraft mc, ResourceLocation move) {
        MoveInstance current = machine(mc).current();
        return current != null && current.id().equals(move);
    }

    /** On the ground, not moving, the machine idle and no dash going. */
    private static boolean ready(Minecraft mc) {
        CombatStateMachine m = machine(mc);
        return mc.player.onGround() && mc.player.getDeltaMovement().horizontalDistance() < 0.01
                && m.phase() == Phase.IDLE && !m.isDashing();
    }

    private static void mark(Minecraft mc, Vec3[] start, float[] facing) {
        start[0] = mc.player.position();
        facing[0] = mc.player.getYRot();
    }

    private static double distance(Minecraft mc, Vec3 from) {
        Vec3 d = mc.player.position().subtract(from);
        return Math.sqrt(d.x * d.x + d.z * d.z);
    }

    /** Horizontal distance travelled along the facing at the start (negative is backwards). */
    private static double along(Minecraft mc, Vec3 from, float yaw) {
        Vec3 d = mc.player.position().subtract(from);
        Vec3 f = BodyMotion.forward(yaw);
        return d.x * f.x + d.z * f.z;
    }

    private static boolean within(double measured, double expected) {
        return Math.abs(measured - expected) <= TOLERANCE * expected;
    }

    private static String describe(String what, Minecraft mc, Vec3 from, float yaw) {
        return String.format(Locale.ROOT, "%s: %.3f blocks, %.3f along the facing", what, distance(mc, from), along(mc, from, yaw));
    }

    @Override
    public int timeBudgetSeconds() {
        return 120;
    }
}
