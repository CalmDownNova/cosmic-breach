package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * {@code mp-final} (join mode): a failed attempt at the last encounter never strands anyone. The player sets the summoning
 * item on the seat by hand (it is used up), the encounter rises, the player leaves the arena, and once the encounter
 * withdraws the item comes back to the player's pack.
 */
public final class MpFinalScenario implements Scenario {
    @Override
    public boolean multiplayer() {
        return true;
    }

    @Override
    public int timeBudgetSeconds() {
        return 480;
    }

    @Override
    public void steps(com.cosmicbreach.client.dev.Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        MpKit.listen();
        steps.check("playing on a real server", MpKit::remote)
                .command("gamemode survival")
                .command("effect clear @s")
                .command("clear @s")
                .command("gamerule doMobSpawning false")
                .command("cosmicbreach debug goto sanctum throne")
                .waitUntil("across", 800, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the seat is loaded", 1600, () -> mc.level.getBlockState(SanctumArena.THRONE).is(SanctumRegistry.SANCTUM_THRONE.get()))
                .command("time set noon")
                .command("cosmicbreach weather clear")
                .command("give @s cosmicbreach:dying_star_heart")
                .waitUntil("the item arrives", 100, () -> MpKit.count(SanctumRegistry.DYING_STAR_HEART.get()) == 1)
                .run("hold it", () -> MpKit.select(SanctumRegistry.DYING_STAR_HEART.get()))
                .waitTicks(20)
                .run("look at the seat", () -> MpKit.aim(Vec3.atCenterOf(SanctumArena.THRONE).add(0, 0.3, 0)))
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitUntil("the item is used up", 60, () -> MpKit.count(SanctumRegistry.DYING_STAR_HEART.get()) == 0)
                .waitUntil("the encounter rises", 400, () -> MpKit.nearest(HollowHeliarch.class, 96) != null)
                .command("cosmicbreach debug goto sanctum edge")
                .waitTicks(40)
                .waitUntil("after it withdraws, the item is back in the pack", 2400,
                        () -> MpKit.count(SanctumRegistry.DYING_STAR_HEART.get()) == 1)
                .check("still on the server", MpKit::remote);
    }
}
