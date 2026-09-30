package com.cosmicbreach.familiar;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Debug commands for the familiars (op level 2), under {@code /cosmicbreach familiar}: {@code lantern <kind>} (a bound
 * lantern), {@code egg <kind>}, {@code hatchtime <ticks>} (every brazier's hatching time; {@code hatchtime reset}),
 * {@code info} (the familiar you have out), {@code kill} (it dies, as if in a fight).
 */
public final class FamiliarCommands {
    private FamiliarCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> lantern = Commands.literal("lantern");
        LiteralArgumentBuilder<CommandSourceStack> egg = Commands.literal("egg").executes(c -> give(c, StarEggItem.of(FamiliarKind.EMBERWISP), true));
        for (FamiliarKind kind : FamiliarKind.values()) {
            lantern.then(Commands.literal(kind.id()).executes(c -> give(c,
                    FamiliarLanternItem.of(FamiliarBond.hatch(kind, java.util.UUID.randomUUID())), false)));
            egg.then(Commands.literal(kind.id()).executes(c -> give(c, StarEggItem.of(kind), false)));
        }
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("familiar")
                        .then(lantern)
                        .then(egg)
                        .then(Commands.literal("hatchtime")
                                .then(Commands.literal("reset").executes(c -> hatchTime(c, -1)))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000))
                                        .executes(c -> hatchTime(c, IntegerArgumentType.getInteger(c, "ticks")))))
                        .then(Commands.literal("info").executes(FamiliarCommands::info))
                        .then(Commands.literal("kill").executes(FamiliarCommands::kill))));
    }

    private static int give(CommandContext<CommandSourceStack> c, ItemStack stack, boolean plain) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = plain ? new ItemStack(FamiliarRegistry.STAR_EGG.get()) : stack;
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
        return 1;
    }

    private static int hatchTime(CommandContext<CommandSourceStack> c, int ticks) {
        BrazierBlockEntity.setHatchTicks(ticks);
        c.getSource().sendSuccess(() -> Component.literal("braziers hatch after " + BrazierBlockEntity.hatchTicks() + " ticks"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        FamiliarEntity e = FamiliarSessions.active(p);
        String s = e == null ? "no familiar out" : String.format(Locale.ROOT, "%s %s: health %.1f of %.1f, damage %.1f, at %.1f blocks",
                e.kind().id(), e.mode().id(), e.getHealth(), e.getMaxHealth(), e.hitDamage(), e.distanceTo(p));
        c.getSource().sendSuccess(() -> Component.literal(s), false);
        return e == null ? 0 : 1;
    }

    private static int kill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        FamiliarEntity e = FamiliarSessions.active(p);
        if (e == null) {
            return 0;
        }
        e.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        return 1;
    }
}
