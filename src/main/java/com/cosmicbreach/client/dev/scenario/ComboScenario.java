package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.registry.ModItems;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Meridian's light chain through the real key path: a zombie with no AI 2.5 blocks ahead, three taps
 * of the attack key with gaps that keep the chain going, a screenshot in the first active tick. Passes
 * when the local machine (prediction) and the server's machine (authority) both ran L1, L2, L3 in
 * order, vanilla's attack never ran, the zombie lost at least two hits' worth of health, and the hits'
 * HitFx payloads came back and shook the camera.
 *
 * <p>Timing, in client ticks from the first tap T (L1 is 3 + 2 + 5 ticks, L2 3 + 2 + 6): L1's first
 * active tick is T+3. The second tap at T+8 lands in L1's recovery and is buffered, so L2 starts at
 * T+9; the third at T+17, in L2's recovery, starts L3 at T+19. Each tap is held one tick.
 */
public final class ComboScenario implements Scenario {
    static final ResourceLocation L1 = CosmicBreach.id("meridian/l1");
    static final ResourceLocation L2 = CosmicBreach.id("meridian/l2");
    static final ResourceLocation L3 = CosmicBreach.id("meridian/l3");
    static final String MOB_TAG = "cosmicbreach_autotest";
    /** Two taps of Meridian's base 5 against a zombie's 2 armor come to about 9.8. */
    private static final double TWO_HITS = 9.0;

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping attack = mc.options.keyAttack;
        boolean[] recording = {false};
        List<ResourceLocation> clientMoves = new CopyOnWriteArrayList<>();
        List<ResourceLocation> serverMoves = new CopyOnWriteArrayList<>();
        int[] serverSerial = {-1};
        ClientCombat.addEventListener(event -> {
            if (recording[0] && event instanceof CombatEvent.MoveStarted started) {
                clientMoves.add(started.move().id());
            }
        });
        // The server's machine, sampled on the server thread every server tick.
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (recording[0] && event.getEntity() instanceof ServerPlayer player) {
                MoveInstance current = PlayerCombat.of(player).machine().current();
                if (current != null && current.serial() != serverSerial[0]) {
                    serverSerial[0] = current.serial();
                    serverMoves.add(current.id());
                }
            }
        });
        // The camera shake the hits' HitFx payloads cause, sampled every client tick.
        double[] peakTrauma = {0.0};
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (recording[0]) {
                peakTrauma[0] = Math.max(peakTrauma[0], ClientCombat.cameraTrauma());
            }
        });
        Zombie[] zombie = {null};
        float[] healthBefore = {0f};

        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach give")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitTicks(5) // the server learns the facing before the summon uses it
                .command("summon minecraft:zombie ^ ^ ^2.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + MOB_TAG + "\"],"
                        + "ArmorItems:[{},{},{},{id:\"minecraft:carved_pumpkin\",count:1}],ArmorDropChances:[0f,0f,0f,0f]}")
                .waitUntil("the zombie stands 2.5 blocks ahead", 60, () -> (zombie[0] = zombieAhead(mc, 2.5)) != null)
                .waitUntil("the machine is idle with Meridian's data", 60, () -> machine(mc).weapon() != null
                        && machine(mc).phase() == Phase.IDLE && CombatData.client().move(L3) != null)
                .run("start recording", () -> {
                    healthBefore[0] = zombie[0].getHealth();
                    recording[0] = true;
                })
                // T: first tap.
                .hold(attack)
                .waitTicks(1)
                .check("vanilla's attack did not run (its swing timer was not reset)", () -> mc.player.getAttackStrengthScale(0f) >= 1f)
                .check("the hold window is open: nothing has swung yet", () -> machine(mc).phase() == Phase.IDLE)
                .release(attack)
                .waitTicks(1)
                .check("the tap fires L1 on release", () -> current(mc, L1))
                .waitTicks(2)
                // L1's first active tick (the 4 tick hold window moved L1's start to the release, one tick after the press).
                .check("this is L1's first active tick", () -> current(mc, L1)
                        && machine(mc).phase() == Phase.ACTIVE && machine(mc).phaseTick() <= 1)
                .screenshot("combo_l1_active")
                .waitTicks(4)
                // T+8: second tap, in L1's recovery.
                .hold(attack)
                .waitTicks(1)
                .release(attack)
                .waitTicks(8)
                // T+17: third tap, in L2's recovery.
                .hold(attack)
                .waitTicks(1)
                .release(attack)
                .waitUntil("the chain has run its three moves", 60, () -> clientMoves.size() >= 3 && machine(mc).phase() == Phase.IDLE)
                .waitTicks(5)
                .run("stop recording", () -> recording[0] = false)
                .log("the local machine's moves", () -> "local machine moves: " + clientMoves)
                .log("the server machine's moves", () -> "server machine moves: " + serverMoves)
                .check("the local machine ran L1, L2, L3 in order", () -> clientMoves.equals(List.of(L1, L2, L3)))
                .check("the server's machine ran L1, L2, L3 in order", () -> serverMoves.equals(List.of(L1, L2, L3)))
                .waitUntil("the zombie lost at least two hits' worth of health", 40,
                        () -> healthBefore[0] - zombie[0].getHealth() >= TWO_HITS || !zombie[0].isAlive())
                .log("the zombie's health", () -> String.format(Locale.ROOT, "the zombie went from %.2f to %.2f health%s",
                        healthBefore[0], zombie[0].getHealth(), zombie[0].isAlive() ? "" : " (dead)"))
                .log("the camera shake", () -> String.format(Locale.ROOT, "peak camera trauma %.3f", peakTrauma[0]))
                .check("the landed hits shook the camera (their HitFx reached this client)", () -> peakTrauma[0] > 0.1)
                .command("kill @e[tag=" + MOB_TAG + "]");
    }

    static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    static boolean current(Minecraft mc, ResourceLocation move) {
        MoveInstance current = machine(mc).current();
        return current != null && current.id().equals(move);
    }

    /** The zombie about {@code blocks} ahead of the player (horizontally, within 0.3), or null. */
    static Zombie zombieAhead(Minecraft mc, double blocks) {
        Vec3 feet = mc.player.position();
        Vec3 forward = BodyMotion.forward(mc.player.getYRot());
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(blocks + 2.0))) {
            Vec3 d = z.position().subtract(feet);
            double along = d.x * forward.x + d.z * forward.z;
            if (Math.abs(along - blocks) < 0.3 && Math.abs(d.x * forward.z - d.z * forward.x) < 0.3) {
                return z;
            }
        }
        return null;
    }

    @Override
    public int timeBudgetSeconds() {
        return 90;
    }
}
