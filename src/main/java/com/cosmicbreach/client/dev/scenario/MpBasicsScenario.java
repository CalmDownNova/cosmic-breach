package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gear.AstralForgeScreen;
import com.cosmicbreach.client.progression.AttunementScreen;
import com.cosmicbreach.client.progression.ProgressionKeys;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.registry.ModItems;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * {@code mp-basics} (join mode): the combat runtime and the menus against a real server. The server's own combat checks
 * for this remote player ({@code /cosmicbreach selftest}), combat data reaching the client after {@code /reload}, real
 * swings hurting a dummy, the dash and parry keys, and every screen that needs the server: the attunement screen, the
 * guide (the server asks the client to open it), the forge's menu and the accessory menu.
 */
public final class MpBasicsScenario implements Scenario {
    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 480;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        int[] mark = {0};
        Object[] data = {null};
        float[] hp = {0f};
        BlockPos[] forge = {null};
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode survival")
                .command("effect clear @s")
                .command("clear @s")
                .command("time set noon")
                .command("weather clear")
                .command("gamerule doMobSpawning false")
                .command("kill @e[type=minecraft:zombie]")
                .waitTicks(20)
                // the combat runtime's own server-side checks, run for this remote player
                .run("mark the chat", () -> mark[0] = MpKit.chatSize())
                .command("cosmicbreach selftest")
                .waitUntil("the selftest prints its verdict", 2400, () -> MpKit.chatSince(mark[0], "SELFTEST "))
                .check("the selftest passed", () -> MpKit.chatSince(mark[0], "SELFTEST PASS"))
                .waitTicks(40)
                // combat data follows a reload to this client
                .run("remember the client's combat data", () -> data[0] = CombatData.client().moves())
                .command("reload")
                .waitUntil("the reloaded combat data reached this client", 600,
                        () -> CombatData.client().moves() != data[0] && !CombatData.client().moves().isEmpty())
                // real swings at a still dummy
                .command("clear @s")
                .command("kill @e[type=minecraft:zombie]")
                .command("cosmicbreach give")
                .waitUntil("the weapon arrives", 100, () -> MpKit.count(ModItems.MERIDIAN.get()) >= 1)
                .run("hold it", () -> MpKit.select(ModItems.MERIDIAN.get()))
                .command("tp @s ~ ~ ~ 0 5")
                .waitTicks(10)
                .command("summon minecraft:zombie ~ ~ ~2.5 {NoAI:1b,PersistenceRequired:1b}")
                .waitUntil("the dummy is here", 100, () -> MpKit.nearest(Zombie.class, 8) != null)
                .run("remember its health", () -> hp[0] = MpKit.nearest(Zombie.class, 8).getHealth())
                .waitUntil("real swings hurt it", 400, MpKit.swingAt(Zombie.class, 8, () -> {
                    Zombie z = MpKit.nearest(Zombie.class, 8);
                    return z == null || z.getHealth() < hp[0];
                }))
                .command("kill @e[type=minecraft:zombie]")
                .run("dash", () -> MpKit.key(ModKeyMappings.DASH, true))
                .waitTicks(2)
                .run("let go", () -> KeyMapping.set(ModKeyMappings.DASH.getKey(), false))
                .waitTicks(30)
                .run("parry", () -> MpKit.key(ModKeyMappings.PARRY, true))
                .waitTicks(2)
                .run("let go", () -> KeyMapping.set(ModKeyMappings.PARRY.getKey(), false))
                .waitTicks(30)
                .check("still on the server after the combat keys", MpKit::remote)
                // the attunement screen
                .run("the attunement key", () -> MpKit.key(ProgressionKeys.ATTUNEMENT, true))
                .waitTicks(1)
                .run("let go", () -> KeyMapping.set(ProgressionKeys.ATTUNEMENT.getKey(), false))
                .waitUntil("the attunement screen opens", 60, () -> mc.screen instanceof AttunementScreen)
                .run("close it", () -> mc.screen.onClose())
                .waitUntil("it closed", 40, () -> mc.screen == null)
                // the guide: the server tells this client to open it
                .command("give @s cosmicbreach:starfall_codex")
                .waitUntil("the guide arrives", 100, () -> MpKit.count(OnboardingRegistry.STARFALL_CODEX.get()) >= 1)
                .run("hold it", () -> MpKit.select(OnboardingRegistry.STARFALL_CODEX.get()))
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitUntil("the guide opens", 200, () -> mc.screen instanceof guideme.internal.screen.GuideScreen)
                .run("close it", () -> mc.screen.onClose())
                .waitUntil("it closed", 40, () -> mc.screen == null)
                // the forge's menu (a server container)
                .run("a forge two blocks south", () -> {
                    forge[0] = mc.player.blockPosition().offset(0, 0, 2);
                    MpKit.send("setblock %d %d %d cosmicbreach:astral_forge", forge[0].getX(), forge[0].getY(), forge[0].getZ());
                })
                .waitUntil("the forge stands", 100, () -> mc.level.getBlockState(forge[0]).is(GearRegistry.ASTRAL_FORGE.get()))
                .run("an empty hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[8].getKey()))
                .waitTicks(3)
                .run("look at it", () -> MpKit.aim(Vec3.atCenterOf(forge[0])))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the forge's menu opens", 100, () -> mc.screen instanceof AstralForgeScreen)
                .run("close it", () -> mc.screen.onClose())
                .waitUntil("it closed", 40, () -> mc.screen == null)
                // the accessory menu (another server container)
                .run("the accessory key", () -> MpKit.key(MpKit.key("key.curios.open.desc"), true))
                .waitTicks(1)
                .run("let go", () -> KeyMapping.set(MpKit.key("key.curios.open.desc").getKey(), false))
                .waitUntil("the accessory menu opens", 100, () -> mc.screen != null
                        && mc.screen.getClass().getName().toLowerCase(Locale.ROOT).contains("curio"))
                .run("close it", () -> mc.screen.onClose())
                .waitUntil("it closed", 40, () -> mc.screen == null)
                .check("still on the server at the end", MpKit::remote);
    }
}
