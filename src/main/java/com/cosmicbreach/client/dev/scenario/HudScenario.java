package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.registry.ModItems;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Screenshots of the combat HUD for a look by eye: Resonance at 0, 50 and 100 against the sky, the
 * dash pips with 2, 1 and 0 charges against grass (the empty one filling back up), Resonance 50
 * against grass, and the charge ring filling and at full. Checks only that each state was reached.
 */
public final class HudScenario implements Scenario {
    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("cosmicbreach give")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, -30)
                .waitTicks(10);
        resonance(steps, mc, 0, "hud_resonance_0_sky");
        resonance(steps, mc, 50, "hud_resonance_50_sky");
        resonance(steps, mc, 100, "hud_resonance_100_sky");
        steps.look(0, 35)
                .waitUntil("two dash charges", 60, () -> machine(mc).dashCharges() == 2)
                .screenshot("hud_dash_2_grass")
                .press(ModKeyMappings.DASH)
                .waitUntil("one dash charge left", 20, () -> machine(mc).dashCharges() == 1)
                .waitTicks(12)
                .screenshot("hud_dash_1_grass")
                .press(ModKeyMappings.DASH)
                .waitUntil("no dash charges left", 20, () -> machine(mc).dashCharges() == 0)
                .waitTicks(12)
                .log("the recharge", () -> String.format(Locale.ROOT, "dash charges %d, recharge %.2f",
                        machine(mc).dashCharges(), machine(mc).dashRechargeProgress()))
                .screenshot("hud_dash_0_grass");
        resonance(steps, mc, 50, "hud_resonance_50_grass");
        steps.hold(mc.options.keyAttack)
                .waitUntil("charging, two thirds of the way to ready", 30,
                        () -> machine(mc).phase() == Phase.CHARGING && machine(mc).attackHeldTicks() >= 8)
                .screenshot("hud_charge_filling")
                .waitUntil("charged to full", 30, () -> machine(mc).phase() == Phase.CHARGING && machine(mc).attackHeldTicks() >= 25)
                .screenshot("hud_charge_full")
                .release(mc.options.keyAttack)
                .waitTicks(30);
    }

    /** Sets Resonance through the server, waits for the sync, takes the screenshot. */
    private static void resonance(Steps steps, Minecraft mc, int value, String label) {
        steps.command("cosmicbreach debug resonance " + value)
                .waitUntil("the client's Resonance is " + value, 40, () -> Math.abs(machine(mc).resonance() - value) < 1.0)
                .log("Resonance before " + label, () -> String.format(Locale.ROOT, "Resonance %.2f of %d",
                        machine(mc).resonance(), machine(mc).maxResonance()))
                .screenshot(label);
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    @Override
    public int timeBudgetSeconds() {
        return 90;
    }
}
