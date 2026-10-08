package com.cosmicbreach.voice.boss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cosmicbreach debug voice status} (each fight's voice: every line said with its take, start and end ticks, and
 * flags for a cut, a placed start or words over a telegraph; then what waits), {@code say <boss> <line> [variant]} (that
 * take to you now, past every rule: for the caption and the take), {@code trigger <trigger> [event]} (fires a trigger
 * in the fight you are in, as the boss would place it), {@code meet <line>} (the moment of that line, raised free so the
 * boss's gate decides) and {@code fresh} (the voice forgets what it said, as a new fight) and {@code calm} (only the global gap has run). Operators only.
 */
final class BossVoiceCommands {
    private BossVoiceCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("voice")
                                .then(Commands.literal("status").executes(BossVoiceCommands::status))
                                .then(Commands.literal("say")
                                        .then(Commands.argument("boss", StringArgumentType.word())
                                                .then(Commands.argument("line", StringArgumentType.word())
                                                        .executes(c -> say(c, "all"))
                                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                                .executes(c -> say(c, StringArgumentType.getString(c, "variant")))))))
                                .then(Commands.literal("fresh").executes(BossVoiceCommands::fresh))
                                .then(Commands.literal("calm").executes(BossVoiceCommands::calm))
                                .then(Commands.literal("meet")
                                        .then(Commands.argument("line", StringArgumentType.word()).executes(BossVoiceCommands::meet)))
                                .then(Commands.literal("trigger")
                                        .then(Commands.argument("trigger", StringArgumentType.word())
                                                .executes(c -> trigger(c, ""))
                                                .then(Commands.argument("event", StringArgumentType.word())
                                                        .executes(c -> trigger(c, StringArgumentType.getString(c, "event")))))))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        List<FightVoice> all = BossVoices.all();
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("no boss voice"), false);
            return 0;
        }
        for (FightVoice v : all) {
            long now = v.level.getGameTime();
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s at %s, tick %d: said", v.catalog.boss(),
                    v.boss.voiceHome().toShortString(), now));
            for (FightVoice.Said said : v.said) {
                s.append(String.format(Locale.ROOT, " %s/%s[%d-%d%s%s%s]", said.line, said.variant, said.start, said.end,
                        said.cut ? " cut" : "", said.onCue ? " placed" : "", said.overTelegraph ? " OVER" : ""));
            }
            s.append("; waiting");
            v.director.pending().forEach(p -> s.append(' ').append(p.line().id()));
            String text = s.toString();
            c.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        return all.size();
    }

    private static int say(CommandContext<CommandSourceStack> c, String variant) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String boss = StringArgumentType.getString(c, "boss");
        if (!BossCatalog.BOSSES.contains(boss)) {
            c.getSource().sendFailure(Component.literal("bosses: " + BossCatalog.BOSSES));
            return 0;
        }
        VoiceLine line = BossCatalog.of(boss).line(StringArgumentType.getString(c, "line"));
        if (line == null || line.variant(variant) == null) {
            c.getSource().sendFailure(Component.literal("no such line or take"));
            return 0;
        }
        BossVoiceNet.say(List.of(p), boss, line.id(), line.variantKey(variant), false);
        return 1;
    }

    private static int fresh(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        FightVoice v = BossVoices.fightOf(c.getSource().getPlayerOrException());
        if (v == null) {
            c.getSource().sendFailure(Component.literal("not in a boss fight"));
            return 0;
        }
        v.fresh();
        c.getSource().sendSuccess(() -> Component.literal("the voice forgets what it said"), false);
        return 1;
    }

    private static int calm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        FightVoice v = BossVoices.fightOf(c.getSource().getPlayerOrException());
        if (v == null) {
            c.getSource().sendFailure(Component.literal("not in a boss fight"));
            return 0;
        }
        v.calm();
        c.getSource().sendSuccess(() -> Component.literal("the global gap has run"), false);
        return 1;
    }

    private static int meet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        FightVoice v = BossVoices.fightOf(c.getSource().getPlayerOrException());
        if (v == null) {
            c.getSource().sendFailure(Component.literal("not in a boss fight"));
            return 0;
        }
        VoiceLine line = v.catalog.line(StringArgumentType.getString(c, "line"));
        if (line == null) {
            c.getSource().sendFailure(Component.literal("no such line"));
            return 0;
        }
        v.meet(line);
        c.getSource().sendSuccess(() -> Component.literal("met " + line.id() + " (" + line.trigger().key() + ")"), false);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> c, String event) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        FightVoice v = BossVoices.fightOf(p);
        if (v == null) {
            c.getSource().sendFailure(Component.literal("not in a boss fight"));
            return 0;
        }
        Trigger t;
        try {
            t = Trigger.parse(StringArgumentType.getString(c, "trigger"));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        v.hook(t, event, v.level.getGameTime(), VoiceDirector.NO_LIMIT);
        c.getSource().sendSuccess(() -> Component.literal("fired " + t.key() + (event.isEmpty() ? "" : " on " + event)), false);
        return 1;
    }
}
