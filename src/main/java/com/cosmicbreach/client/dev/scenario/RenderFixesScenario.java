package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.gyre.GyreFx;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreKnights;
import com.cosmicbreach.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.1 render fixes (design section 8), part {@code render-fixes}, in the flat test world: every item model whose
 * second layer is a tier trim bakes that layer as its two faces only, at rest and at each of the three glow stages (a
 * stack in the player's main hand, because the Resonance property answers only for that stack); twelve first-person
 * frames of a reforged Meridian while walking, for the review; a Gyre Knight hanging in its Shield Orbit seen level
 * from 10, 30, 48, 60 and 90 blocks: rings drawn out to 48 (half faded there), gone by 60, none past where vanilla culls
 * its body. Past 56 blocks the fade has already taken the rings, so two more cases pin the gate that draws them only with
 * their body: the entity distance at 50 percent (at the test client's render distance 8 vanilla then culls the body at
 * about 44 blocks) seen from 48 blocks, which needs the gate's distance half, and the Knight behind the eye at 10 blocks,
 * which needs its frustum half. With the gate there neither shows a ring; with it gone each shows 120 segments.
 */
public final class RenderFixesScenario implements Scenario {
    private static final int[] DISTANCES = {10, 30, 48, 60, 90};
    /** Resonance that selects glow stage 1, 2 and 3 (the model's thresholds are 0.34, 0.67 and 1.0 of a full bar of 100). */
    private static final int[] STAGE_RESONANCE = {40, 70, 100};
    /** Items whose model has a tier trim: three weapons and twelve set pieces. Each stays trimmed at every glow stage. */
    private static final int TRIMMED_ITEMS = 15;
    private final List<String> results = new ArrayList<>();
    private Vec3 core = Vec3.ZERO;

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }

    /** The trim quads (tint index 1, a sprite ending in {@code _trim}) a model bakes in its null-side list. */
    private static int trimQuads(BakedModel model) {
        int trims = 0;
        for (BakedQuad q : model.getQuads(null, null, RandomSource.create(42L))) {
            if (q.getTintIndex() == 1 && q.getSprite().contents().name().getPath().endsWith("_trim")) {
                trims++;
            }
        }
        return trims;
    }

    /**
     * Fails unless every cosmicbreach item model bakes 0 or 2 trim quads, in the model as held and, for the Binary Edges'
     * separate transforms, in the pair drawn in the GUI (the two must agree: an item whose held model lost its trim must
     * not hide behind its icon), and at least {@code minTrimmed} bake 2. With {@code stage} above 0
     * each stack goes into the player's main hand on this client (put back after) so Resonance picks its glow stage, and
     * the Meridian must really be on that stage's model.
     */
    private void checkTrims(String when, int stage, int minTrimmed) {
        Minecraft mc = Minecraft.getInstance();
        Inventory inventory = mc.player.getInventory();
        int slot = inventory.selected;
        ItemStack original = inventory.getItem(slot);
        int trimmed = 0;
        List<String> bad = new ArrayList<>();
        try {
            for (Item item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals("cosmicbreach")) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                if (stage > 0) {
                    inventory.setItem(slot, stack);
                }
                BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                int held = trimQuads(model);
                int gui = trimQuads(model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false));
                if (held == 2 || gui == 2) {
                    trimmed++;
                }
                if ((held != 0 && held != 2) || (gui != 0 && gui != 2) || held != gui) {
                    bad.add(id.getPath() + "=" + held + "/" + gui);
                }
                if (stage > 0 && item == ModItems.MERIDIAN.get()) {
                    String want = "item/meridian_glow" + stage;
                    boolean onStage = false;
                    for (BakedQuad q : model.getQuads(null, null, RandomSource.create(42L))) {
                        onStage |= q.getTintIndex() == 0 && q.getSprite().contents().name().getPath().equals(want);
                    }
                    if (!onStage) {
                        bad.add("meridian is not on " + want);
                    }
                }
            }
        } finally {
            inventory.setItem(slot, original);
        }
        results.add(when + ": " + trimmed + " trimmed models, bad " + bad);
        if (!bad.isEmpty() || trimmed < minTrimmed) {
            throw new Steps.Failure(when + ": trim quads " + bad + ", " + trimmed + " trimmed models (" + minTrimmed + " or more expected)");
        }
    }

    /** The eye at {@code eye} looking at {@code at}, a few frames to draw, then the last frame's ring count must show rings (or none). */
    private static void ringsSeenFrom(Steps steps, Minecraft mc, String what, Supplier<Vec3> eye, Supplier<Vec3> at, boolean drawn) {
        steps.run(what, () -> LeviathanScenario.camera(mc, eye.get(), at.get()))
                .waitTicks(8)
                .log(what, () -> what + ": " + GyreFx.ringSegmentsLastFrame() + " segments")
                .check(what + ": rings " + (drawn ? "drawn" : "gone"), () -> (GyreFx.ringSegmentsLastFrame() > 0) == drawn);
    }

    private static CombatStateMachine machine() {
        return PlayerCombat.of(Minecraft.getInstance().player).machine();
    }

    /** Sets this player's Resonance to {@code value} on the server and waits until this client has it. */
    private void resonance(Steps steps, int value) {
        steps.run("mark the fight on this client", () -> machine().onDamaged())
                .waitUntil("and on the server", 5, () -> ServerQuery.ask(player -> {
                    PlayerCombat.of(player).machine().onDamaged();
                    return true;
                }))
                .command("cosmicbreach debug resonance " + value)
                .waitUntil("the client's Resonance is " + value, 40, () -> Math.abs(machine().resonance() - value) < 0.5)
                .waitTicks(2);
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("gamemode creative")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .waitTicks(20)
                .run("every trim bakes as two faces", () -> checkTrims("at rest", 0, TRIMMED_ITEMS));
        for (int stage = 1; stage <= STAGE_RESONANCE.length; stage++) {
            int s = stage;
            resonance(steps, STAGE_RESONANCE[stage - 1]);
            steps.run("every trim bakes as two faces at glow stage " + stage, () -> checkTrims("glow stage " + s, s, TRIMMED_ITEMS));
        }
        resonance(steps, 0);
        steps.command("clear @s")
                .command("give @s cosmicbreach:meridian[cosmicbreach:gear_tier=2]")
                .command("gamemode survival")
                .waitTicks(10)
                .run("hide the chat", () -> mc.gui.getChat().clearMessages(false))
                .look(-30f, -5f)
                .hold(mc.options.keyUp);
        for (int i = 1; i <= 12; i++) {
            steps.screenshot(String.format(Locale.ROOT, "edge_%02d", i));
        }
        steps.release(mc.options.keyUp)
                .run("hide the HUD, the chat and the hand for the Knight frames", () -> mc.options.hideGui = true)
                .command("gamemode creative")
                // any older Knight goes in the same server step that spawns the new one, so the two cannot race
                .run("a Knight hanging still in its Shield Orbit, 40 blocks up", () -> core = ServerQuery.ask(p -> {
                    p.serverLevel().getEntities(GyreKnights.GYRE_KNIGHT.get(), old -> true).forEach(Entity::discard);
                    GyreKnight k = GyreKnights.GYRE_KNIGHT.get().create(p.serverLevel());
                    k.moveTo(p.getX(), p.getY() + 40.0, p.getZ(), 0f, 0f);
                    k.setPersistenceRequired();
                    k.setNoAi(true);
                    p.serverLevel().addFreshEntity(k);
                    return k.position().add(0, GyreKnight.CORE_Y, 0);
                }))
                .waitTicks(10);
        for (int d : DISTANCES) {
            steps.run("the eye " + d + " blocks from it, level with its core", () -> LeviathanScenario.camera(mc, core.add(d, 0, 0), core))
                    .waitTicks(8)
                    .screenshot("knight_" + d)
                    .log("rings at " + d, () -> "rings at " + d + " blocks: " + GyreFx.ringSegmentsLastFrame() + " segments")
                    .check("rings at " + d + " blocks: " + (d <= 48 ? "drawn" : "gone"), () -> (GyreFx.ringSegmentsLastFrame() > 0) == (d <= 48));
        }
        // the gate that draws the rings only with their body, where the fade has not taken them already
        steps.run("the entity distance at 50 percent: vanilla culls the Knight's body at about 44 blocks", () -> mc.options.entityDistanceScaling().set(0.5))
                .waitTicks(3)
                .check("vanilla's view scale follows the setting (the gate's distance rule reads it every frame)", () -> Math.abs(Entity.getViewScale() - 0.5) < 1e-6);
        ringsSeenFrom(steps, mc, "the eye 48 blocks out, body culled by the entity distance", () -> core.add(48, 0, 0), () -> core, false);
        ringsSeenFrom(steps, mc, "the eye 30 blocks out, body still drawn", () -> core.add(30, 0, 0), () -> core, true);
        steps.run("the entity distance back at 100 percent", () -> mc.options.entityDistanceScaling().set(1.0));
        ringsSeenFrom(steps, mc, "the eye 10 blocks out, turned away from it", () -> core.add(10, 0, 0), () -> core.add(20, 0, 0), false);
        ringsSeenFrom(steps, mc, "the eye 10 blocks out, looking at it", () -> core.add(10, 0, 0), () -> core, true);
        steps.command("kill @e[type=cosmicbreach:gyre_knight]")
                .run("show the HUD", () -> mc.options.hideGui = false)
                .log("results", () -> String.join(" | ", results));
    }
}
