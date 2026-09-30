package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.progression.AttunementHud;
import com.cosmicbreach.client.progression.AttunementScreen;
import com.cosmicbreach.client.progression.ProgressionKeys;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.progression.Allocation;
import com.cosmicbreach.progression.Attunement;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.progression.ProgressionRegistry;
import com.cosmicbreach.progression.ProgressionStats;
import com.cosmicbreach.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The progression system end to end, through the real input paths where there are any:
 * <ol>
 *   <li>6,200 Attunement XP from a command: level 10 with 9 points, the level-up message, XP line,
 *       burst and chime (screenshot), and the free Reverie Draught with its message. Then a Shardling
 *       killed 8 blocks away gives the player standing by its 25 XP.</li>
 *   <li>Level 50; the Attunement key opens the screen; its buttons are clicked (20 Agility, 2 Power,
 *       one Power taken back), screenshot, Confirm; the server spent exactly that; K closes it.</li>
 *   <li>Agility 20 measured: 3 dash charges on both sides, three dashes in a row on the server, and
 *       +8% move speed.</li>
 *   <li>The draught drunk with the use key: every point back.</li>
 *   <li>Resilience 30 measured: max health 32, poise 25, parry window 6, a 10-damage hit taken as 8.5,
 *       and all of it kept through a death.</li>
 *   <li>Power 30 measured: an L1 on a zombie deals Meridian's B-grade damage, 5 x (1 + 0.55 x 0.5)
 *       = 6.375 before armor (11.475 if it rolled a crit).</li>
 * </ol>
 */
public final class ProgressionScenario implements Scenario {
    private static final String MOB_TAG = "cosmicbreach_progression";
    private static final double L1_POWER_30 = 5.0 * (1.0 + 0.55 * 0.5);
    private static final double CRIT_POWER_30 = 1.5 + 0.01 * 30;

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        // Server-side probes: the damage a test zombie is dealt before and after armor, and what the player takes.
        List<Float> zombieIncoming = new CopyOnWriteArrayList<>();
        List<Float> zombieTaken = new CopyOnWriteArrayList<>();
        List<Float> playerTaken = new CopyOnWriteArrayList<>();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity().getTags().contains(MOB_TAG)) {
                zombieIncoming.add(event.getAmount());
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            if (event.getEntity().level().isClientSide()) {
                return;
            }
            if (event.getEntity().getTags().contains(MOB_TAG)) {
                zombieTaken.add(event.getNewDamage());
            } else if (event.getEntity() instanceof ServerPlayer) {
                playerTaken.add(event.getNewDamage());
            }
        });
        int[] dashSerial = {0};
        long[] totalXp = {0};
        LocalPlayer[] before = {null};

        steps.look(0, 35)
                .check("a fresh Attunement: level 1, nothing to spend", () -> state(mc).level() == 1 && state(mc).unspent() == 0)
                // 1. Level up from XP.
                .command("cosmicbreach debug xp 6200")
                .waitForChat("Reverie Draught is yours", 60)
                .waitUntil("the client's Attunement reached level 10", 40, () -> state(mc).level() == 10)
                .check("9 points to spend and 198 XP toward level 11", () -> state(mc).unspent() == 9 && state(mc).xp() == 198)
                .check("the level-up message says Attunement 10", () -> AttunementHud.messageLevel() == 10)
                .check("the XP line is showing", AttunementHud::xpLineVisible)
                .waitTicks(11)
                .screenshot("levelup")
                .waitUntil("the free Reverie Draught is in the inventory", 40,
                        () -> mc.player.getInventory().countItem(ProgressionRegistry.REVERIE_DRAUGHT.get()) == 1)
                .waitUntil("the XP line faded after its 3 s", 100, () -> !AttunementHud.xpLineVisible())
                // A Shardling's kill: 25 XP (Reach trash) to everyone within 24 blocks.
                .command("summon cosmicbreach:shardling ~ ~ ~8 {NoAI:1b,PersistenceRequired:1b}")
                .waitUntil("the Shardling stands 8 blocks away", 40, () -> !mc.level.getEntitiesOfClass(Shardling.class,
                        mc.player.getBoundingBox().inflate(12)).isEmpty())
                .run("remember the server's total XP", () -> totalXp[0] = ServerQuery.ask(p -> Attunements.of(p).totalXp()))
                .command("kill @e[type=cosmicbreach:shardling]")
                .waitUntil("its kill gave the player standing by 25 Attunement XP", 40,
                        () -> ServerQuery.ask(p -> Attunements.of(p).totalXp()) - totalXp[0] == 25)
                .check("so level 10 now has 223 XP", () -> state(mc).level() == 10 && state(mc).xp() == 223)
                // 2. The screen, opened with the key and clicked (Meridian in hand, so its grades show).
                .command("cosmicbreach give")
                .waitUntil("Meridian is in the inventory", 40, () -> slotOf(mc, ModItems.MERIDIAN.get()) >= 0)
                .run("select Meridian with its hotbar key", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .waitUntil("Meridian is in hand", 10, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .command("cosmicbreach debug level 50")
                .waitUntil("level 50 with 49 points", 40, () -> state(mc).level() == 50 && state(mc).unspent() == 49)
                .press(ProgressionKeys.ATTUNEMENT)
                .waitUntil("the Attunement key opened the screen", 10, () -> mc.screen instanceof AttunementScreen)
                .waitTicks(2)
                .screenshot("screen_open")
                .run("click Agility + twenty times", () -> {
                    for (int i = 0; i < 20; i++) {
                        click(mc, screen(mc).plusButton(Stat.AGILITY));
                    }
                })
                .run("click Power + twice", () -> {
                    click(mc, screen(mc).plusButton(Stat.POWER));
                    click(mc, screen(mc).plusButton(Stat.POWER));
                })
                .check("a - shows for Power and Agility only", () -> screen(mc).minusButton(Stat.POWER).visible
                        && screen(mc).minusButton(Stat.AGILITY).visible && !screen(mc).minusButton(Stat.ARCANE).visible
                        && !screen(mc).minusButton(Stat.RESILIENCE).visible)
                .run("click Power - once", () -> click(mc, screen(mc).minusButton(Stat.POWER)))
                .check("pending: Power 1, Agility 20, 28 left to spend", () -> screen(mc).pending(Stat.POWER) == 1
                        && screen(mc).pending(Stat.AGILITY) == 20 && screen(mc).available() == 28)
                .check("nothing is spent before Confirm", () -> ServerQuery.ask(p -> Attunements.of(p).spent().isZero()))
                .waitTicks(2)
                .screenshot("screen_pending")
                .run("click Confirm", () -> click(mc, screen(mc).confirmButton()))
                .waitUntil("the server spent Power 1 and Agility 20", 40,
                        () -> ServerQuery.ask(p -> Attunements.of(p).spent()).equals(new Allocation(1, 20, 0, 0)))
                .waitUntil("the client's synced state and attributes follow", 40, () -> state(mc).spent().equals(new Allocation(1, 20, 0, 0))
                        && state(mc).unspent() == 28 && ProgressionStats.of(mc.player).agility() == 20)
                .waitTicks(3)
                .screenshot("screen_confirmed")
                .run("press K in the screen", () -> screen(mc).keyPressed(GLFW.GLFW_KEY_K, 0, 0))
                .check("K closed the screen", () -> mc.screen == null)
                // 3. Agility 20: three dash charges and faster feet.
                .log("move speed", () -> String.format(Locale.ROOT, "movement speed attribute at Agility 20: %.5f",
                        mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED)))
                .check("Agility 20 gives +8% move speed", () -> Math.abs(mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        - 0.1 * 1.08) < 1e-6)
                .check("Meridian is still in hand", () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .waitUntil("the server's machine has 3 of 3 dash charges", 80, () -> ServerQuery.ask(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    return m.maxDashCharges() == 3 && m.dashCharges() == 3;
                }))
                .check("the client's machine agrees: 3 of 3", () -> machine(mc).maxDashCharges() == 3 && machine(mc).dashCharges() == 3)
                .run("remember the server's dash count", () -> dashSerial[0] = ServerQuery.ask(p -> PlayerCombat.of(p).dashSerial()))
                .press(ModKeyMappings.DASH)
                .waitTicks(3)
                .press(ModKeyMappings.DASH)
                .waitTicks(3)
                .press(ModKeyMappings.DASH)
                .waitTicks(3)
                .log("dashes", () -> "server dashes started: " + (ServerQuery.ask(p -> PlayerCombat.of(p).dashSerial()) - dashSerial[0])
                        + ", charges left " + ServerQuery.ask(p -> PlayerCombat.of(p).machine().dashCharges()))
                .check("three dashes in a row on the server, spending all three charges", () ->
                        ServerQuery.ask(p -> PlayerCombat.of(p).dashSerial()) - dashSerial[0] == 3
                                && ServerQuery.ask(p -> PlayerCombat.of(p).machine().dashCharges()) == 0)
                .waitTicks(10)
                // 4. Respec: drink the draught.
                .run("select the draught with its hotbar key", () -> selectHotbar(mc, ProgressionRegistry.REVERIE_DRAUGHT.get()))
                .waitUntil("the draught is in hand", 10, () -> mc.player.getMainHandItem().is(ProgressionRegistry.REVERIE_DRAUGHT.get()))
                .hold(mc.options.keyUse)
                .waitUntil("drinking it refunded every point on the server", 80,
                        () -> ServerQuery.ask(p -> Attunements.of(p).spent().isZero()))
                .release(mc.options.keyUse)
                .waitUntil("the client sees 49 points to spend, Agility 0 and 2 dash charges", 40, () -> state(mc).spent().isZero()
                        && state(mc).unspent() == 49 && ProgressionStats.of(mc.player).agility() == 0 && machine(mc).maxDashCharges() == 2)
                // 5. Resilience 30: health, poise, parry, reduction, and a death that costs nothing.
                .command("cosmicbreach debug stats 0 0 0 30")
                .waitUntil("max health is 32 on the client", 40, () -> Math.abs(mc.player.getMaxHealth() - 32f) < 1e-4)
                .check("and on the server", () -> Math.abs(ServerQuery.ask(ServerPlayer::getMaxHealth) - 32f) < 1e-4)
                .check("poise 25", () -> Math.abs(ServerQuery.ask(p -> PoiseTracker.poiseOf(p)) - 25.0) < 1e-9)
                .check("a 6-tick parry window", () -> ServerQuery.ask(p -> CombatMath.parryWindow(PlayerCombat.of(p).machine().stats(), 0)) == 6)
                .command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .run("start recording damage taken", playerTaken::clear)
                .command("damage @s 10 minecraft:generic")
                .waitUntil("the hit landed", 40, () -> !playerTaken.isEmpty())
                .log("damage taken", () -> "10 generic damage at Resilience 30 took " + playerTaken.get(0))
                .check("15% came off: 8.5 taken", () -> Math.abs(playerTaken.get(0) - 8.5f) < 1e-4)
                .command("gamerule keepInventory true")
                .command("gamerule doImmediateRespawn true")
                .run("remember this player object", () -> before[0] = mc.player)
                .command("kill @s")
                .waitUntil("the player respawned", 200, () -> mc.player != null && mc.player != before[0] && mc.player.isAlive()
                        && mc.screen == null)
                .waitUntil("death cost nothing: level 50, Resilience 30, 32 health, full", 60, () -> state(mc).level() == 50
                        && state(mc).spent().equals(new Allocation(0, 0, 0, 30)) && Math.abs(mc.player.getMaxHealth() - 32f) < 1e-4
                        && Math.abs(mc.player.getHealth() - 32f) < 1e-4)
                // 6. Power 30: an L1's damage.
                .command("cosmicbreach debug stats 30 0 0 0")
                .waitUntil("Power 30 on the client", 40, () -> ProgressionStats.of(mc.player).power() == 30)
                .run("select Meridian with its hotbar key", () -> selectHotbar(mc, ModItems.MERIDIAN.get()))
                .waitUntil("Meridian is in hand", 10, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitTicks(5)
                .command("summon minecraft:zombie ^ ^ ^2.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"" + MOB_TAG + "\"],"
                        + "ArmorItems:[{},{},{},{id:\"minecraft:carved_pumpkin\",count:1}],ArmorDropChances:[0f,0f,0f,0f]}")
                .waitUntil("the zombie stands 2.5 blocks ahead", 60, () -> ComboScenario.zombieAhead(mc, 2.5) != null)
                .waitUntil("the machine is idle with Power 30", 60, () -> machine(mc).weapon() != null
                        && machine(mc).phase() == CombatStateMachine.Phase.IDLE && machine(mc).stats().power() == 30)
                .run("start recording the zombie's damage", () -> {
                    zombieIncoming.clear();
                    zombieTaken.clear();
                })
                .press(mc.options.keyAttack)
                .waitUntil("the L1 hit the zombie on the server", 30, () -> !zombieIncoming.isEmpty() && !zombieTaken.isEmpty())
                .log("the L1's damage", () -> String.format(Locale.ROOT, "L1 at Power 30: %.4f before armor, %.4f after (expected %.4f, or %.4f on a crit)",
                        zombieIncoming.get(0), zombieTaken.get(0), L1_POWER_30, L1_POWER_30 * CRIT_POWER_30))
                .check("the L1 dealt 5 x (1 + 0.55 x 0.5) before armor (x1.8 on a crit)", () -> {
                    double dealt = zombieIncoming.get(0);
                    return Math.abs(dealt - L1_POWER_30) < 1e-3 || Math.abs(dealt - L1_POWER_30 * CRIT_POWER_30) < 1e-3;
                })
                .check("the zombie's armor then took a little off", () -> zombieTaken.get(0) < zombieIncoming.get(0))
                .command("kill @e[tag=" + MOB_TAG + "]")
                .command("gamemode creative");
    }

    private static Attunement state(Minecraft mc) {
        return Attunements.of(mc.player);
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static AttunementScreen screen(Minecraft mc) {
        if (!(mc.screen instanceof AttunementScreen screen)) {
            throw new Steps.Failure("the Attunement screen is not open (" + mc.screen + ")");
        }
        return screen;
    }

    /** A left click in the middle of {@code widget}, through the screen as the mouse would send it. */
    private static void click(Minecraft mc, AbstractWidget widget) {
        Screen screen = mc.screen;
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (screen == null || !screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new Steps.Failure("a click at " + x + ", " + y + " hit no active widget (" + widget.getMessage().getString() + ")");
        }
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static int slotOf(Minecraft mc, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** Clicks the hotbar key of the slot holding {@code item}, as pressing 1 to 9 would. */
    private static void selectHotbar(Minecraft mc, Item item) {
        int slot = slotOf(mc, item);
        if (slot < 0) {
            throw new Steps.Failure(item + " is not in the hotbar");
        }
        KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
    }

    @Override
    public int timeBudgetSeconds() {
        return 180;
    }
}
