package com.cosmicbreach.codex;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cosmicbreach codex} (op level 2, for tests): {@code status}; {@code unlock <chapter>|all};
 * {@code reset}; {@code mote} (throws the lair mote, as sneak-using the book does); {@code hint} (the Lens Array
 * page's hint link).
 */
public final class CodexCommands {
    private CodexCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("codex")
                        .then(Commands.literal("status").executes(CodexCommands::status))
                        .then(Commands.literal("unlock")
                                .then(Commands.literal("all").executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    CodexProgress all = CodexProgress.NONE;
                                    for (CodexChapter ch : CodexChapter.values()) {
                                        all = all.with(ch);
                                    }
                                    Codices.set(p, all);
                                    return status(c);
                                }))
                                .then(Commands.argument("chapter", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(keys(), b))
                                        .executes(c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            String key = StringArgumentType.getString(c, "chapter");
                                            CodexChapter ch = CodexChapter.byKey(key).orElse(null);
                                            if (ch == null) {
                                                c.getSource().sendFailure(Component.literal("No chapter " + key + "; one of " + keys()));
                                                return 0;
                                            }
                                            Codices.met(p, ch);
                                            return status(c);
                                        })))
                        .then(Commands.literal("reset").executes(c -> {
                            Codices.set(c.getSource().getPlayerOrException(), CodexProgress.NONE);
                            return status(c);
                        }))
                        .then(Commands.literal("mote").executes(c ->
                                LairMote.throwFor(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("hint").executes(c -> {
                            Codices.HintResult r = Codices.hint(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.literal("Codex hint: " + r), false);
                            return r == Codices.HintResult.LIT ? 1 : 0;
                        }))));
    }

    private static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (CodexChapter c : CodexChapter.values()) {
            out.add(c.key());
        }
        return out;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CodexProgress progress = Codices.of(p);
        List<String> open = new ArrayList<>();
        for (CodexChapter ch : CodexChapter.values()) {
            if (progress.has(ch)) {
                open.add(ch.key());
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("Codex chapters: " + (open.isEmpty() ? "none" : String.join(", ", open))), false);
        return open.size();
    }
}
