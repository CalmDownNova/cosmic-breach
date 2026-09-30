package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.net.CombatInputPayload;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs {@code /cosmicbreach selftest} (the server's combat runtime against real zombies) and passes
 * only on its {@code SELFTEST PASS} line; a {@code SELFTEST FAIL: ...} line fails the scenario with
 * that line as the reason. The per-check lines land in log.txt with the rest of the chat.
 *
 * <p>Then two things the selftest can't reach from the server: combat input sent from this client
 * (an attack press with an empty hand is ignored, a dash needs no weapon) and the combat data sync
 * after {@code /reload}.
 */
public final class SelftestScenario implements Scenario {
    private static final String VERDICT = "SELFTEST ";
    private static final String PASS = "SELFTEST PASS";

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        List<String> verdicts = new CopyOnWriteArrayList<>();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientChatReceivedEvent.class, event -> {
            String text = event.getMessage().getString();
            if (text.startsWith(VERDICT)) {
                verdicts.add(text);
            }
        });
        Object[] dataBefore = {null};
        steps.command("cosmicbreach selftest")
                .waitForChat("^selftest setup: ", 400)
                // Meridian in hand and the zombie ahead of the first swing (the swing comes 5 ticks after they spawn).
                .waitTicks(3)
                .screenshot("selftest_meridian")
                .waitUntil("the selftest prints its SELFTEST verdict", 1200, () -> !verdicts.isEmpty())
                .run("read the selftest verdict", () -> {
                    String verdict = verdicts.isEmpty() ? "no SELFTEST line was captured" : verdicts.get(verdicts.size() - 1);
                    if (!verdict.equals(PASS)) {
                        throw new Steps.Failure(verdict);
                    }
                })
                .waitUntil("the selftest gave back an empty hand", 40, () -> mc.player.getMainHandItem().isEmpty())
                .waitUntil("the server's machine is idle with full dash charges", 100, () -> onServer(combat -> {
                    CombatStateMachine m = combat.machine();
                    return m.phase() == CombatStateMachine.Phase.IDLE && !m.isDashing()
                            && m.dashCharges() == m.maxDashCharges() && !m.isAttackHeld();
                }))
                .run("send an attack press with an empty hand", () ->
                        PacketDistributor.sendToServer(CombatInputPayload.of(CombatAction.ATTACK_PRESS)))
                .waitTicks(5)
                .check("the server ignored the attack press", () -> onServer(combat -> !combat.machine().isAttackHeld()
                        && combat.machine().phase() == CombatStateMachine.Phase.IDLE))
                .run("send a dash with an empty hand", () ->
                        PacketDistributor.sendToServer(CombatInputPayload.of(CombatAction.DASH)))
                .waitUntil("the server's machine dashed", 20, () -> onServer(combat -> combat.machine().isDashing()
                        || combat.machine().dashCharges() < combat.machine().maxDashCharges()))
                .run("remember the client's combat data", () -> dataBefore[0] = CombatData.client().moves())
                .command("reload")
                .waitUntil("the client received the reloaded combat data", 400, () ->
                        CombatData.client().moves() != dataBefore[0] && !CombatData.client().moves().isEmpty()
                                && !CombatData.client().weapons().isEmpty());
    }

    /** Asks the integrated server about this player's server-side combat state, on the server thread. */
    private static boolean onServer(Predicate<PlayerCombat> query) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            throw new Steps.Failure("there is no integrated server");
        }
        UUID id = mc.player.getUUID();
        try {
            return server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                return player != null && query.test(PlayerCombat.of(player));
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new Steps.Failure("could not read the server's combat state: " + e);
        }
    }

    @Override
    public int timeBudgetSeconds() {
        return 150;
    }
}
