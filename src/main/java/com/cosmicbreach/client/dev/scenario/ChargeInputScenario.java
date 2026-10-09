package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.registry.ModItems;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Lane A, charge input (1.2 design section 1), through the real key path: a tap fires exactly one light attack and no
 * charge; a hold fires no light attack at all and goes straight into the charge, on the local machine and on the
 * server's; releasing the hold fires the charged move; a press released just inside the 4 tick window is still a tap.
 */
public final class ChargeInputScenario implements Scenario {
    private final AtomicInteger clientLights = new AtomicInteger();
    private final AtomicInteger clientCharges = new AtomicInteger();
    private final AtomicInteger clientCharged = new AtomicInteger();
    private final AtomicInteger serverLights = new AtomicInteger();
    private final AtomicInteger serverCharged = new AtomicInteger();
    private final boolean[] serverCharging = {false};
    private final int[] serverSerial = {-1};

    @Override
    public int timeBudgetSeconds() {
        return 120;
    }

    private void reset() {
        clientLights.set(0);
        clientCharges.set(0);
        clientCharged.set(0);
        serverLights.set(0);
        serverCharged.set(0);
        serverCharging[0] = false;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping attack = mc.options.keyAttack;
        boolean[] recording = {false};
        ClientCombat.addEventListener(event -> {
            if (!recording[0]) {
                return;
            }
            if (event instanceof CombatEvent.MoveStarted started) {
                MoveKind kind = started.move().def().kind();
                if (kind == MoveKind.LIGHT) {
                    clientLights.incrementAndGet();
                } else if (kind == MoveKind.CHARGED) {
                    clientCharged.incrementAndGet();
                }
            } else if (event instanceof CombatEvent.ChargeStarted) {
                clientCharges.incrementAndGet();
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (recording[0] && event.getEntity() instanceof ServerPlayer player) {
                var machine = PlayerCombat.of(player).machine();
                MoveInstance current = machine.current();
                if (current != null && current.serial() != serverSerial[0]) {
                    serverSerial[0] = current.serial();
                    if (current.def().kind() == MoveKind.LIGHT) {
                        serverLights.incrementAndGet();
                    } else if (current.def().kind() == MoveKind.CHARGED) {
                        serverCharged.incrementAndGet();
                    }
                }
                if (machine.phase() == Phase.CHARGING) {
                    serverCharging[0] = true;
                }
            }
        });

        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach give")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitUntil("the machine is idle", 60, () -> ComboScenario.machine(mc).weapon() != null
                        && ComboScenario.machine(mc).phase() == Phase.IDLE)
                .run("record", () -> {
                    reset();
                    recording[0] = true;
                })

                // a tap
                .hold(attack)
                .waitTicks(1)
                .check("nothing swings while the window is open", () -> clientLights.get() == 0 && ComboScenario.machine(mc).phase() == Phase.IDLE)
                .release(attack)
                .waitTicks(40)
                .log("tap", () -> "tap: client lights " + clientLights + " charges " + clientCharges + ", server lights " + serverLights)
                .check("a tap fires exactly one light attack (client)", () -> clientLights.get() == 1)
                .check("a tap fires exactly one light attack (server)", () -> serverLights.get() == 1)
                .check("a tap never starts a charge", () -> clientCharges.get() == 0 && !serverCharging[0])

                // a press released just inside the window
                .run("reset", this::reset)
                .waitUntil("idle and out of the combo window", 60, () -> ComboScenario.machine(mc).phase() == Phase.IDLE)
                .waitTicks(15)
                .hold(attack)
                .waitTicks(2)
                .release(attack)
                .waitTicks(40)
                .check("a press released inside the window is a tap", () -> clientLights.get() == 1 && serverLights.get() == 1
                        && clientCharges.get() == 0 && !serverCharging[0])

                // a hold: no swing, straight into the charge
                .run("reset", this::reset)
                .waitTicks(20)
                .hold(attack)
                .waitTicks(10)
                .check("a hold is charging on the local machine", () -> ComboScenario.machine(mc).phase() == Phase.CHARGING)
                .check("and on the server's", () -> serverCharging[0])
                .check("a hold fires no light attack (client)", () -> clientLights.get() == 0)
                .check("a hold fires no light attack (server)", () -> serverLights.get() == 0)
                .check("the charge started once", () -> clientCharges.get() == 1)
                .waitTicks(14)
                .release(attack)
                .waitTicks(40)
                .check("releasing the hold fires the charged move (client)", () -> clientCharged.get() == 1)
                .check("releasing the hold fires the charged move (server)", () -> serverCharged.get() == 1)
                .check("still no light attack at all", () -> clientLights.get() == 0 && serverLights.get() == 0)

                // a laggy link: the release of a tap reaches the server late
                .run("the server gets releases 3 ticks late", () -> {
                    reset();
                    com.cosmicbreach.combat.server.CombatServerEvents.debugReleaseDelayTicks = 3;
                })
                .waitTicks(20)
                .hold(attack)
                .waitTicks(2)
                .release(attack)
                .waitTicks(50)
                .check("a tap whose release is 3 ticks late is still one light attack on the server", () -> serverLights.get() == 1 && !serverCharging[0])
                .check("and on the client", () -> clientLights.get() == 1 && clientCharges.get() == 0)
                .run("the server gets releases 9 ticks late (past its grace of 2 ticks, with the transit)", () -> {
                    reset();
                    com.cosmicbreach.combat.server.CombatServerEvents.debugReleaseDelayTicks = 9;
                })
                .waitTicks(20)
                .hold(attack)
                .waitTicks(2)
                .release(attack)
                .waitTicks(60)
                .check("the server committed to a charge, then turned it into the tap it was: one light attack", () -> serverLights.get() == 1 && serverCharged.get() == 0)
                .check("the client saw one light attack too", () -> clientLights.get() == 1)
                .run("a real hold with a late release is still a hold", () -> {
                    reset();
                    com.cosmicbreach.combat.server.CombatServerEvents.debugReleaseDelayTicks = 5;
                })
                .waitTicks(20)
                .hold(attack)
                .waitTicks(20)
                .release(attack)
                .waitTicks(60)
                .check("no light attack on either side, and the charged move fired once on the server", () -> serverLights.get() == 0 && clientLights.get() == 0
                        && serverCharged.get() == 1)
                .run("no more delay", () -> com.cosmicbreach.combat.server.CombatServerEvents.debugReleaseDelayTicks = 0)
                .run("stop", () -> recording[0] = false)
                .run("summary", () -> com.cosmicbreach.CosmicBreach.LOGGER.info("[autotest] charge input: tap, short press and hold all behaved"));
    }
}
