package com.cosmicbreach.shrine;

import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /cosmicbreach debug shrines list} (one chat line per placed shrine: {@code shrine <kind> <x> <y> <z> <facing>}),
 * {@code place} (places every missing shrine now, loading chunks, and finds the currents beside them),
 * {@code currents} (one line per rising current: {@code current <kind> <x> <z> from <y> to <y> land <x> <z>}; {@code currents reset}
 * forgets them all, so {@code place} finds them again as it would in a world saved before they existed), {@code mine} (the caller's save:
 * {@code save <kind> <x> <y> <z> active <true|false>}), {@code go <kind>} (into Aetheria, standing in front of the first
 * placed shrine of that kind and facing it), {@code here <kind>} (a shrine two blocks ahead, for looks).
 */
public final class ShrineCommands {
    private ShrineCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("shrines")
                                .then(Commands.literal("list").executes(ShrineCommands::list))
                                .then(Commands.literal("place").executes(ShrineCommands::place))
                                .then(Commands.literal("currents").executes(ShrineCommands::currents)
                                        .then(Commands.literal("reset").executes(ShrineCommands::resetCurrents)))
                                .then(Commands.literal("mine").executes(ShrineCommands::mine))
                                .then(Commands.literal("go")
                                        .then(Commands.argument("kind", StringArgumentType.word()).executes(ShrineCommands::go)))
                                .then(Commands.literal("here")
                                        .then(Commands.argument("kind", StringArgumentType.word()).executes(ShrineCommands::here))))));
    }

    private static int go(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = aetheria(c.getSource());
        ShrineKind kind = ShrineKind.ofGuardian(StringArgumentType.getString(c, "kind"));
        ShrineData.Placed s = kind == null ? null
                : ShrineData.get(level).all().values().stream().filter(x -> x.kind() == kind).findFirst().orElse(null);
        if (s == null) {
            c.getSource().sendFailure(Component.translatable("commands.cosmicbreach.debug.shrines.none"));
            return 0;
        }
        level.getChunk(s.pos().getX() >> 4, s.pos().getZ() >> 4);
        Vec3 at = ShrineSpots.standSpot(level, s.pos(), s.facing()).orElse(Vec3.atBottomCenterOf(s.pos().relative(s.facing())));
        double dx = s.pos().getX() + 0.5 - at.x;
        double dz = s.pos().getZ() + 0.5 - at.z;
        p.teleportTo(level, at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-dx, dz)), 15f);
        return 1;
    }

    private static ServerLevel aetheria(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(AetheriaWorld.LEVEL);
        if (level == null) {
            throw new IllegalStateException("Aetheria is missing from this world");
        }
        return level;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        ShrineData data = ShrineData.get(aetheria(c.getSource()));
        if (data.all().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.shrines.none"), false);
            return 0;
        }
        data.all().values().forEach(p -> c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "shrine %s %d %d %d %s", p.kind().id(), p.pos().getX(), p.pos().getY(), p.pos().getZ(), p.facing().getName())), false));
        return data.all().size();
    }

    private static int currents(CommandContext<CommandSourceStack> c) {
        ShrineData data = ShrineData.get(aetheria(c.getSource()));
        data.currents().forEach((key, cur) -> c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "current %s %.1f %.1f from %.0f to %.0f land %.1f %.1f", data.all().containsKey(key) ? data.all().get(key).kind().id() : "unknown", cur.x(), cur.z(), cur.bottom(),
                cur.top(), cur.landX(), cur.landZ())), false));
        return data.currents().size();
    }

    private static int resetCurrents(CommandContext<CommandSourceStack> c) {
        ShrineData.get(aetheria(c.getSource())).clearCurrents();
        ShrinePlacer.resetSearches();
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> c) {
        int n = ShrinePlacer.placePending(aetheria(c.getSource()), true);
        c.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.shrines.placed", n), false);
        return n;
    }

    private static int mine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ShrineSave.Saved s = p.getData(ShrineRegistry.SAVED);
        String line = s.none() ? "save none" : String.format(Locale.ROOT, "save %s %d %d %d active %s", s.kind(), s.pos().getX(), s.pos().getY(),
                s.pos().getZ(), ShrineSave.active(p));
        c.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ShrineKind kind = ShrineKind.ofGuardian(StringArgumentType.getString(c, "kind"));
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("kinds: colossus leviathan unsung heliarch"));
            return 0;
        }
        BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
        p.serverLevel().setBlock(at, ShrineRegistry.block(kind).defaultBlockState().setValue(ShrineBlock.FACING, p.getDirection().getOpposite()),
                Block.UPDATE_ALL);
        return 1;
    }
}
