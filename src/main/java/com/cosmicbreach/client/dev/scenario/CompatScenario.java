package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.familiar.FamiliarKeys;
import com.cosmicbreach.client.gear.GearClient;
import com.cosmicbreach.client.progression.ProgressionKeys;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.registry.ModItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Better Combat and Combat Roll beside ours (F1; both are in the dev runtime, so every scenario runs with them): no key
 * of ours shares its key with any other mapping; no mixin failed to apply (the game log); with Meridian in hand the
 * attack key starts our move and Better Combat stays out of it; with an iron sword Better Combat swings and our engine
 * stays idle; Combat Roll's roll and our dash both move the player with Meridian in hand. Reflection only: neither mod
 * is a dependency.
 */
public final class CompatScenario implements Scenario {
    private static final Pattern MIXIN_TROUBLE = Pattern.compile(
            "Mixin apply failed|InvalidInjectionException|InjectionError|Critical injection failure|@Redirect conflict|"
                    + "InvalidMixinException|MixinTransformerError");

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] from = {Vec3.ZERO};
        float[] health = {0f};
        steps.check("Better Combat, Combat Roll and Cloth Config are loaded", () -> ModList.get().isLoaded("bettercombat")
                        && ModList.get().isLoaded("combat_roll") && ModList.get().isLoaded("cloth_config"))
                .log("versions", () -> String.join(", ", List.of("bettercombat", "combat_roll", "cloth_config", "playeranimator",
                        "geckolib", "curios", "guideme").stream().map(id -> id + " " + ModList.get().getModContainerById(id)
                        .map(c -> c.getModInfo().getVersion().toString()).orElse("missing")).toList()))
                .run("no key of ours shares its key", () -> {
                    List<String> clashes = new ArrayList<>();
                    for (KeyMapping ours : ours()) {
                        for (KeyMapping other : mc.options.keyMappings) {
                            if (other != ours && !other.isUnbound() && other.same(ours)
                                    && ours.getKeyConflictContext().conflicts(other.getKeyConflictContext())) {
                                clashes.add(ours.getName() + " and " + other.getName() + " on " + ours.getKey().getName());
                            }
                        }
                    }
                    if (!clashes.isEmpty()) {
                        throw new Steps.Failure("key clashes: " + clashes);
                    }
                })
                .log("keys", () -> String.join(", ", ours().stream().map(k -> k.getName() + " " + k.getTranslatedKeyMessage().getString()).toList())
                        + "; Combat Roll's roll " + rollKey().getTranslatedKeyMessage().getString())
                .run("no mixin failed to apply", () -> {
                    List<String> trouble = new ArrayList<>();
                    try {
                        for (String line : Files.readAllLines(Path.of("logs", "latest.log"), StandardCharsets.UTF_8)) {
                            if (MIXIN_TROUBLE.matcher(line).find()) {
                                trouble.add(line);
                            }
                        }
                    } catch (IOException e) {
                        throw new Steps.Failure("could not read logs/latest.log: " + e);
                    }
                    if (!trouble.isEmpty()) {
                        throw new Steps.Failure("mixin trouble: " + trouble.subList(0, Math.min(3, trouble.size())));
                    }
                })
                .command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("kill @e[type=!player]")
                .command("tp @s 0 -60 0 0 0")
                .command("summon minecraft:zombie 0 -60 2.5 {NoAI:1b,PersistenceRequired:1b}")
                .command("give @s cosmicbreach:meridian")
                .command("give @s minecraft:iron_sword")
                .waitTicks(20)

                // Meridian: our engine, not Better Combat's
                .run("Meridian in hand", () -> select(mc, ModItems.MERIDIAN.get()))
                .waitTicks(5)
                .press(mc.options.keyAttack)
                .waitUntil("our move starts", 10, () -> PlayerCombat.of(mc.player).machine().current() != null)
                .check("Better Combat stays out of it", () -> !betterCombatSwinging(mc))
                .waitTicks(30)

                // an iron sword: Better Combat's swing, our engine idle
                .run("the iron sword in hand", () -> select(mc, Items.IRON_SWORD))
                // Better Combat waits for vanilla's attack strength, which a change of item resets (12.5 ticks for a sword)
                .waitTicks(30)
                .run("the zombie's health", () -> health[0] = zombieHealth(mc))
                .log("in hand", () -> mc.player.getMainHandItem().toString() + ", Better Combat " + bcState(mc))
                .press(mc.options.keyAttack)
                .log("after the press", () -> "Better Combat " + bcState(mc))
                .waitUntil("Better Combat swings the sword", 20, () -> betterCombatSwinging(mc) || comboCount(mc) > 0)
                .check("our engine stays idle", () -> PlayerCombat.of(mc.player).machine().current() == null)
                .waitUntil("the swing lands through Better Combat", 30, () -> CryptKit.server(srv -> zombieHealthOn(srv)) < health[0])
                .waitTicks(20)

                // movement: Combat Roll's roll and our dash, Meridian in hand
                .run("Meridian in hand", () -> select(mc, ModItems.MERIDIAN.get()))
                .command("tp @s 0 -60 -6 180 0")
                .waitTicks(30)
                // from a standstill, so walking cannot pass for either
                .run("mark", () -> from[0] = mc.player.position())
                .run("roll", () -> {
                    KeyMapping roll = rollKey();
                    KeyMapping.set(roll.getKey(), true);
                    KeyMapping.click(roll.getKey());
                })
                .waitTicks(2)
                .run("let go", () -> KeyMapping.set(rollKey().getKey(), false))
                .waitTicks(12)
                .log("roll", () -> String.format(Locale.ROOT, "Combat Roll's roll moved %.2f blocks", horizontal(from[0], mc.player.position())))
                .check("Combat Roll's roll works with Meridian in hand", () -> horizontal(from[0], mc.player.position()) > 2.0)
                .waitTicks(40)
                .run("mark", () -> from[0] = mc.player.position())
                .press(ModKeyMappings.DASH)
                .waitTicks(12)
                .log("dash", () -> String.format(Locale.ROOT, "our dash (a backstep) moved %.2f blocks", horizontal(from[0], mc.player.position())))
                .check("and so does our dash", () -> horizontal(from[0], mc.player.position()) > 2.0)
                .command("kill @e[type=minecraft:zombie]");
    }

    private static List<KeyMapping> ours() {
        return List.of(ModKeyMappings.DASH, ModKeyMappings.PARRY, GearClient.SET_ABILITY, ProgressionKeys.ATTUNEMENT, FamiliarKeys.KEY);
    }

    private static KeyMapping rollKey() {
        try {
            return (KeyMapping) Class.forName("net.combat_roll.client.Keybindings").getField("roll").get(null);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("Combat Roll's roll key was not found: " + e);
        }
    }

    private static boolean betterCombatSwinging(Minecraft mc) {
        try {
            return (Boolean) Class.forName("net.bettercombat.api.MinecraftClient_BetterCombat").getMethod("isWeaponSwingInProgress").invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("Better Combat's client API was not found: " + e);
        }
    }

    private static String bcState(Minecraft mc) {
        try {
            Class<?> api = Class.forName("net.bettercombat.api.MinecraftClient_BetterCombat");
            return String.format(Locale.ROOT, "combo %s, swing progress %s, upswing %s, swinging %s", api.getMethod("getComboCount").invoke(mc),
                    api.getMethod("getSwingProgress").invoke(mc), api.getMethod("getUpswingTicks").invoke(mc),
                    api.getMethod("isWeaponSwingInProgress").invoke(mc));
        } catch (ReflectiveOperationException e) {
            return "unreadable: " + e;
        }
    }

    private static int comboCount(Minecraft mc) {
        try {
            return (Integer) Class.forName("net.bettercombat.api.MinecraftClient_BetterCombat").getMethod("getComboCount").invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new Steps.Failure("Better Combat's client API was not found: " + e);
        }
    }

    private static void select(Minecraft mc, Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[i].getKey());
                return;
            }
        }
        throw new Steps.Failure(item + " is not in the hotbar");
    }

    private static float zombieHealth(Minecraft mc) {
        return CryptKit.server(CompatScenario::zombieHealthOn);
    }

    private static float zombieHealthOn(net.minecraft.server.MinecraftServer srv) {
        return srv.overworld().getEntitiesOfClass(Zombie.class, new AABB(-10, -70, -10, 10, -50, 10)).stream()
                .findFirst().map(Zombie::getHealth).orElse(0f);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }
}
