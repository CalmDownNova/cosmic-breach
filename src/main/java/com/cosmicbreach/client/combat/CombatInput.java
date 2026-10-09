package com.cosmicbreach.client.combat;

import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.net.CombatInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Combat input for the local player. With a combat weapon in the main hand, vanilla's attack and use
 * do nothing (no swing, no mining, no item use): the buttons belong to the combat machine instead.
 * Every tick their held state becomes press and release edges ({@link ButtonEdges}); Dash and Parry
 * are clicks of their own keys. Each input goes into the local machine at once (prediction) and to
 * the server, in the same order on both sides: dash, parry, then attack, then the ability.
 *
 * <p>Presses need no open screen and a living, non-spectating player (attack and ability also a
 * combat weapon, which the server checks too). Releases always go out after their press. A right
 * click on a door, chest or other interactive block goes to vanilla instead of the ability
 * ({@link AbilityPassthrough}).
 */
final class CombatInput {
    private static final ButtonEdges ATTACK = new ButtonEdges();
    private static final ButtonEdges ABILITY = new ButtonEdges();

    private CombatInput() {
    }

    /**
     * Vanilla attack and use with a combat weapon in hand: cancelled, without the arm swing. A use on
     * an interactive block goes through to vanilla (the ability's press is held back in {@link #tick}).
     */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() && !event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && CombatWeaponItem.isCombatWeapon(player.getMainHandItem())) {
            if (event.isUseItem() && AbilityPassthrough.targetsInteractiveBlock(mc, player)) {
                return;
            }
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Reads this tick's input and feeds it to the machine and the server. Before the machine ticks. */
    static void tick(Minecraft mc, LocalPlayer player, PlayerCombat combat) {
        boolean free = mc.screen == null && mc.getOverlay() == null && player.isAlive() && !player.isSpectator();
        boolean armed = CombatWeaponItem.isCombatWeapon(player.getMainHandItem());

        // One dash and one parry per tick at most, however many clicks queued up.
        boolean dash = false;
        while (ModKeyMappings.DASH.consumeClick()) {
            dash = true;
        }
        boolean parry = false;
        while (ModKeyMappings.PARRY.consumeClick()) {
            parry = true;
        }
        if (dash && free && !player.isPassenger() && !player.isFallFlying()) {
            send(combat, CombatAction.DASH);
        } else if (dash && free && player.isPassenger()) {
            com.cosmicbreach.client.mount.MountInput.dashKey(player); // riding: the mount's own move (GDD 8.1)
        }
        if (parry && free) {
            send(combat, CombatAction.PARRY);
        }

        switch (ATTACK.update(mc.options.keyAttack.isDown(), free && armed)) {
            case PRESS -> send(combat, CombatAction.ATTACK_PRESS);
            case RELEASE -> send(combat, CombatAction.ATTACK_RELEASE);
            case NONE -> {
            }
        }
        // Only a press can fire the ability, so the block under the crosshair only matters when the key is down.
        boolean use = mc.options.keyUse.isDown();
        boolean forBlock = use && free && armed && AbilityPassthrough.targetsInteractiveBlock(mc, player);
        switch (ABILITY.update(use, free && armed && !forBlock)) {
            case PRESS -> send(combat, CombatAction.ABILITY_PRESS);
            case RELEASE -> send(combat, CombatAction.ABILITY_RELEASE);
            case NONE -> {
            }
        }
    }

    /** A new local player (joined, respawned, changed dimension): held buttons need a fresh press. */
    static void reset(Minecraft mc) {
        ATTACK.reset(mc.options.keyAttack.isDown());
        ABILITY.reset(mc.options.keyUse.isDown());
        while (ModKeyMappings.DASH.consumeClick()) {
            // drop clicks from before
        }
        while (ModKeyMappings.PARRY.consumeClick()) {
            // drop clicks from before
        }
    }

    /** Same as the server's input handler: the weapon from the hand, then the input. */
    private static void send(PlayerCombat combat, CombatAction action) {
        combat.syncWeapon();
        int held = combat.machine().attackHeldTicks(); // the count as the player felt it, before the machine applies the release
        combat.apply(action);
        PacketDistributor.sendToServer(action == CombatAction.ATTACK_RELEASE ? CombatInputPayload.release(held) : CombatInputPayload.of(action));
    }
}
