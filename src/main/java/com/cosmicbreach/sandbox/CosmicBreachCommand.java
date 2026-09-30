package com.cosmicbreach.sandbox;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.entity.shardling.ShardlingPack;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /cosmicbreach} (op level 2):
 * <ul>
 *   <li>{@code give [weapon]}: a combat weapon into the caller's inventory ({@code meridian} when not named;
 *       {@code comet_maul}, ...).</li>
 *   <li>{@code selftest}: checks the combat runtime against real zombies, see {@link SelfTest}.</li>
 *   <li>{@code sandbox}, {@code wave [size]}, {@code stop}: the sky arena and its Shardling packs, see
 *       {@link Sandbox}.</li>
 *   <li>{@code debug resonance <0..100>}: sets the caller's Resonance on the server; the regular
 *       sync corrects the client's copy on the next tick. For tests.</li>
 * </ul>
 */
public final class CosmicBreachCommand {
    private CosmicBreachCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give").executes(context -> give(context.getSource(), "meridian"))
                        .then(Commands.argument("weapon", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(weaponNames(), builder))
                                .executes(context -> give(context.getSource(), StringArgumentType.getString(context, "weapon")))))
                .then(Commands.literal("selftest").executes(context -> SelfTest.start(context.getSource())))
                .then(Commands.literal("sandbox").executes(context -> Sandbox.start(context.getSource())))
                .then(Commands.literal("wave")
                        .executes(context -> Sandbox.wave(context.getSource(), WaveSchedule.MIN_SIZE))
                        .then(Commands.argument("size", IntegerArgumentType.integer(1, ShardlingPack.MAX_SIZE))
                                .executes(context -> Sandbox.wave(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "size")))))
                .then(Commands.literal("stop").executes(context -> Sandbox.stop(context.getSource())))
                .then(Commands.literal("debug")
                        .then(Commands.literal("resonance")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> setResonance(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "value")))))));
    }

    /** The combat weapons by name ({@code meridian}, {@code comet_maul}, ...). */
    private static List<String> weaponNames() {
        List<String> names = new ArrayList<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            if (holder.get() instanceof CombatWeaponItem) {
                names.add(holder.getId().getPath());
            }
        }
        return names;
    }

    private static int give(CommandSourceStack source, String weapon) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Item item = null;
        for (var holder : ModItems.ITEMS.getEntries()) {
            if (holder.getId().getPath().equals(weapon) && holder.get() instanceof CombatWeaponItem) {
                item = holder.get();
            }
        }
        if (item == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.give.unknown", weapon, String.join(", ", weaponNames())));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        Component name = stack.getHoverName(); // before the inventory takes the stack (it leaves it empty)
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.give", name, player.getDisplayName()), true);
        return 1;
    }

    private static int setResonance(CommandSourceStack source, int value) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatStateMachine machine = PlayerCombat.of(player).machine();
        // The server's own numbers go through the same clamping door the client sync uses.
        machine.syncFromServer(value, machine.dashCharges(), machine.abilityCooldown());
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.resonance",
                Math.round(machine.resonance())), false);
        return 1;
    }
}
