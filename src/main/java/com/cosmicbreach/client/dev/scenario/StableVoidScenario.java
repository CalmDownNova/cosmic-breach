package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.OwedRewards;
import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.mount.Stable;
import com.cosmicbreach.mount.StableCrystalItem;
import com.cosmicbreach.mount.StowedMount;
import com.cosmicbreach.world.AetheriaWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * A player who dies in the void carrying a stored mount (quality re-review, Important 1), part {@code stable-void}, with
 * real inputs. The crystal drops where they die, below the line where the world deletes items, while its owner is on the
 * death screen: it must come back with them when they respawn. Once with the mount never having stood safe since it was
 * set down (a fresh one: no last safe spot), over Aetheria; and once under the Overworld's floor (outside the mod's levels).
 * Before them, a full crystal is refused by a shulker box's slot and by a hopper feeding one (final re-review, Minor 1: inside a
 * box the guard cannot reach it), and an empty one is taken. The sections note an expectation that fails and go on; the run
 * fails on them at the end.
 */
public final class StableVoidScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    @Override
    public int timeBudgetSeconds() {
        return 400;
    }

    private void command(String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }

    private void expect(String what, boolean ok) {
        if (!ok) {
            failures.add(what);
        }
    }

    /** A full crystal: a tamed stingray, named, that has never stood anywhere safe. */
    private static ItemStack fullCrystal(ServerPlayer p) {
        DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
        m.moveTo(p.getX(), p.getY() + 2.0, p.getZ(), 0f, 0f);
        m.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
        m.setPersistenceRequired();
        p.serverLevel().addFreshEntity(m);
        m.tameTo(p);
        m.setCustomName(Component.literal("Drifter"));
        CompoundTag tag = new CompoundTag();
        m.saveAsPassenger(tag);
        ItemStack stack = new ItemStack(Stable.STABLE_CRYSTAL.get());
        stack.set(Stable.STOWED.get(), new StowedMount(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()), tag, m.getHealth(), m.getMaxHealth(),
                Optional.of("Drifter")));
        m.discard();
        return stack;
    }

    private static int fullCrystals(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (StableCrystalItem.stowed(p.getInventory().getItem(i)) != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void steps(Steps steps) {
        setUp(steps);
        nesting(steps);
        dieInTheVoid(steps, "over Aetheria", () -> command("tp @s ~ -80 ~"));
        dieInTheVoid(steps, "under the Overworld's floor", () -> command("tp @s 0.5 -140 0.5"));
        steps.log("results", () -> String.join(" | ", results));
        steps.log("expectations that failed", () -> failures.isEmpty() ? "none" : String.join(" | ", failures));
        steps.check("every expectation held", failures::isEmpty);
    }

    private void setUp(Steps steps) {
        steps.command("gamemode creative")
                .command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear");
    }

    /** A full crystal cannot go inside a shulker box, by the slot a player fills or through a hopper's face; an empty one can. */
    private void nesting(Steps steps) {
        steps.run("a shulker box refuses a full crystal and takes an empty one", () -> {
            boolean[] seen = ServerQuery.ask(p -> {
                ItemStack full = fullCrystal(p);
                ItemStack empty = new ItemStack(Stable.STABLE_CRYSTAL.get());
                ShulkerBoxSlot slot = new ShulkerBoxSlot(new SimpleContainer(27), 0, 0, 0);
                ShulkerBoxBlockEntity box = new ShulkerBoxBlockEntity(BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState());
                return new boolean[] {slot.mayPlace(full), slot.mayPlace(empty),
                        box.canPlaceItemThroughFace(0, full, Direction.UP), box.canPlaceItemThroughFace(0, empty, Direction.UP)};
            });
            String line = String.format(Locale.ROOT, "shulker box: slot takes a full crystal %b and an empty one %b, a hopper's face a full one %b and an empty one %b",
                    seen[0], seen[1], seen[2], seen[3]);
            results.add(line);
            expect(line, !seen[0] && seen[1] && !seen[2] && seen[3]);
        });
    }

    /**
     * In survival, with a stored mount in the pack (one is added if the last death's crystal did not come back, so each
     * section stands alone), the player goes under the floor, dies, waits on the death screen while the crystal drops
     * and has its first tick, respawns, and has the crystal in the pack and nothing still owed.
     */
    private void dieInTheVoid(Steps steps, String where, Runnable goUnder) {
        steps.command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("a stored mount in the pack", () -> ServerQuery.ask(p -> {
                    if (fullCrystals(p) == 0) {
                        p.getInventory().add(fullCrystal(p));
                    }
                    return true;
                }))
                .waitTicks(5)
                .run("under the floor (" + where + ")", goUnder)
                .waitTicks(3)
                .command("kill @s")
                .waitUntil("the player died", 80, () -> mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen)
                .waitTicks(15)
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("respawned", 200, () -> {
                    if (mc.player != null && mc.player.isAlive() && mc.screen instanceof DeathScreen) {
                        mc.setScreen(null);
                    }
                    return mc.player != null && mc.player.isAlive() && mc.screen == null;
                })
                .waitTicks(20)
                .run("the crystal came back with them (" + where + ")", () -> {
                    int[] seen = ServerQuery.ask(p -> new int[] {fullCrystals(p), OwedRewards.get(p.server).owes(p.getUUID()) ? 1 : 0});
                    String line = String.format(Locale.ROOT, "died %s: %d full crystal in the pack after respawning, still owed %d", where, seen[0], seen[1]);
                    results.add(line);
                    expect(line, seen[0] == 1 && seen[1] == 0);
                });
    }
}
