package com.cosmicbreach.world;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Debug commands for Aetheria (op level 2):
 *
 * <ul>
 *   <li>{@code /cosmicbreach debug goto reach|sunfield|drift|deep|breach}: into Aetheria onto safe ground in
 *       that band (a Shattered Spires island, a Sunfield island, a big asteroid, a Deep platform), or onto the
 *       Reach's rim of the Breach looking down into it. Searches from where the caller stands if already in
 *       Aetheria, else from X 0, Z 0.</li>
 *   <li>{@code /cosmicbreach debug locate}: the nearest of each biome and of each kind of safe ground.</li>
 * </ul>
 */
public final class WorldCommands {
    private WorldCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("goto")
                                .then(Commands.literal("reach").executes(c -> go(c, AetheriaSpots.Kind.SPIRES)))
                                .then(Commands.literal("sunfield").executes(c -> go(c, AetheriaSpots.Kind.SUNFIELD)))
                                .then(Commands.literal("drift").executes(c -> go(c, AetheriaSpots.Kind.DRIFT)))
                                .then(Commands.literal("deep").executes(c -> go(c, AetheriaSpots.Kind.DEEP)))
                                .then(Commands.literal("breach").executes(WorldCommands::breach)))
                        .then(Commands.literal("locate").executes(WorldCommands::locate))));
    }

    private static ServerLevel aetheria(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(AetheriaWorld.LEVEL);
        if (level == null) {
            throw new IllegalStateException("Aetheria is missing from this world (datapack not loaded?)");
        }
        return level;
    }

    private static BlockPos searchOrigin(ServerPlayer player) {
        return AetheriaWorld.is(player.level()) ? player.blockPosition() : BlockPos.ZERO;
    }

    private static int go(CommandContext<CommandSourceStack> context, AetheriaSpots.Kind kind) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = aetheria(source);
        Optional<AetheriaSpots.Spot> spot = AetheriaSpots.safeSpot(level, kind, searchOrigin(player));
        if (spot.isEmpty()) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.goto.none", kind.name().toLowerCase(Locale.ROOT)));
            return 0;
        }
        teleport(player, level, spot.get());
        BlockPos p = spot.get().feet();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.goto",
                kind.name().toLowerCase(Locale.ROOT), p.getX(), p.getY(), p.getZ()), false);
        return 1;
    }

    private static int breach(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = aetheria(source);
        Optional<AetheriaSpots.Spot> spot = AetheriaSpots.breachRim(level, searchOrigin(player));
        AetheriaSpots.Spot s = spot.orElse(new AetheriaSpots.Spot(new BlockPos(0, 400, 0), 0f, 90f));
        teleport(player, level, s);
        BlockPos p = s.feet();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.goto", "breach", p.getX(), p.getY(), p.getZ()), false);
        return 1;
    }

    public static void teleport(ServerPlayer player, ServerLevel level, AetheriaSpots.Spot spot) {
        BlockPos p = spot.feet();
        player.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, Set.of(), spot.yaw(), spot.pitch());
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.resetFallDistance();
    }

    private static int locate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = aetheria(source);
        BlockPos from = searchOrigin(player);
        AetheriaTerrain t = AetheriaSpots.terrain(level);
        for (ResourceKey<Biome> key : List.of(AetheriaWorld.SHATTERED_SPIRES, AetheriaWorld.SUNFIELD_TERRACES,
                AetheriaWorld.DRIFT_BELT, AetheriaWorld.RIFT_ABYSS)) {
            int y = key == AetheriaWorld.SHATTERED_SPIRES || key == AetheriaWorld.SUNFIELD_TERRACES ? 350
                    : key == AetheriaWorld.DRIFT_BELT ? 230 : 100;
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), from.atY(y), 6400, 32, 64);
            String where = found == null ? "none within 6400" : found.getFirst().toShortString()
                    + " (" + (int) Math.sqrt(found.getFirst().distSqr(from.atY(found.getFirst().getY()))) + " blocks)";
            source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.locate.biome", key.location().toString(), where), false);
        }
        for (AetheriaSpots.Kind kind : AetheriaSpots.Kind.values()) {
            List<BlockPos> c = AetheriaSpots.candidates(t, from.getX(), from.getZ(), kind, 3000, 1);
            String where = c.isEmpty() ? "none within 3000" : c.get(0).toShortString()
                    + " (" + (int) Math.sqrt(c.get(0).distSqr(from.atY(c.get(0).getY()))) + " blocks)";
            source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.locate.ground",
                    kind.name().toLowerCase(Locale.ROOT), where), false);
        }
        com.cosmicbreach.guardian.GuardianCommands.locateLines(source, level, from);
        com.cosmicbreach.structure.StructureCommands.locate(source, level, from);
        com.cosmicbreach.structure.crypt.CryptCommands.locate(source, level, from);
        return 1;
    }
}
