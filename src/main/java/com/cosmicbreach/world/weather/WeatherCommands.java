package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Slot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Weather commands (op level 2):
 * <ul>
 *   <li>{@code /cosmicbreach weather <flare|shower|tide|surge> [reach|drift|deep]}: starts that event now, warning
 *       first, in the named layer (default: the caller's layer if the event can happen there, else its own).</li>
 *   <li>{@code /cosmicbreach weather clear [reach|drift|deep]}: ends the events (all layers by default).</li>
 *   <li>{@code /cosmicbreach weather skip [layer]}: jumps a warning to its event.</li>
 *   <li>{@code /cosmicbreach weather status}: each layer's weather and every slot's countdown.</li>
 * </ul>
 */
public final class WeatherCommands {
    private WeatherCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("weather")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (WeatherKind kind : WeatherKind.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(kind.id()).executes(c -> start(c, kind, null));
            for (Layer layer : Layer.values()) {
                node.then(Commands.literal(name(layer)).executes(c -> start(c, kind, layer)));
            }
            weather.then(node);
        }
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear").executes(c -> clear(c, null));
        LiteralArgumentBuilder<CommandSourceStack> skip = Commands.literal("skip").executes(c -> skip(c, null));
        for (Layer layer : Layer.values()) {
            clear.then(Commands.literal(name(layer)).executes(c -> clear(c, layer)));
            skip.then(Commands.literal(name(layer)).executes(c -> skip(c, layer)));
        }
        weather.then(clear).then(skip).then(Commands.literal("status").executes(WeatherCommands::status));
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(weather));
    }

    private static @Nullable ServerLevel aetheria(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(AetheriaWorld.LEVEL);
        if (level == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.weather.no_aetheria"));
        }
        return level;
    }

    /** The caller's layer when they stand in Aetheria, else null. */
    private static @Nullable Layer callerLayer(CommandSourceStack source) {
        return AetheriaWorld.is(source.getLevel()) ? Layer.at(source.getPosition().y) : null;
    }

    private static int start(CommandContext<CommandSourceStack> c, WeatherKind kind, @Nullable Layer named) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = aetheria(source);
        if (level == null) {
            return 0;
        }
        Layer layer = named != null ? named : kind.homeLayer(callerLayer(source));
        if (!kind.canHappenIn(layer)) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.weather.wrong_layer",
                    Component.translatable(key(kind)), name(layer)));
            return 0;
        }
        WeatherScheduler.start(level, layer, kind);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.started",
                Component.translatable(key(kind)), name(layer), kind.warningTicks / 20, kind.activeTicks / 20), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c, @Nullable Layer named) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = aetheria(source);
        if (level == null) {
            return 0;
        }
        int cleared = 0;
        for (Layer layer : Layer.values()) {
            if ((named == null || named == layer) && WeatherScheduler.clear(level, layer)) {
                cleared++;
            }
        }
        int n = cleared;
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.cleared", n), true);
        return Math.max(1, cleared);
    }

    private static int skip(CommandContext<CommandSourceStack> c, @Nullable Layer named) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = aetheria(source);
        if (level == null) {
            return 0;
        }
        int skipped = 0;
        for (Layer layer : Layer.values()) {
            if ((named == null || named == layer) && WeatherScheduler.skipWarning(level, layer)) {
                skipped++;
            }
        }
        int n = skipped;
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.skipped", n), true);
        return Math.max(1, skipped);
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = aetheria(source);
        if (level == null) {
            return 0;
        }
        WeatherData data = WeatherData.get(level);
        long time = level.getGameTime();
        for (Layer layer : Layer.values()) {
            LayerWeather w = data.snapshot(layer);
            WeatherSchedule.Now now = data.schedule().now(layer);
            String what = w.kind() == null ? "calm" : w.kind().id() + " " + w.phase().name().toLowerCase(Locale.ROOT)
                    + String.format(Locale.ROOT, ", %.0f s left", (now.length() - now.elapsed()) / 20.0);
            source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.status.layer", name(layer), what), false);
        }
        StringBuilder next = new StringBuilder();
        for (Slot slot : Slot.values()) {
            int t = data.schedule().countdown(slot);
            if (next.length() > 0) {
                next.append(", ");
            }
            next.append(slot.id()).append(' ').append(t < 0 ? "not drawn" : String.format(Locale.ROOT, "%d:%02d", t / 1200, (t / 20) % 60));
        }
        String nexts = next.toString();
        boolean fallen = data.schedule().heliarchFallen();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.status.next", nexts), false);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.weather.status.config",
                WeatherConfig.enabled() ? "on" : "off", fallen ? "yes" : "no", time), false);
        return 1;
    }

    private static String name(Layer layer) {
        return layer.name().toLowerCase(Locale.ROOT);
    }

    static String key(WeatherKind kind) {
        return "weather.cosmicbreach." + kind.id();
    }
}
