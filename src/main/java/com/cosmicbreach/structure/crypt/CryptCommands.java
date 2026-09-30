package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.StructureCommands;
import com.cosmicbreach.structure.choir.ChoirDifficulty;
import com.cosmicbreach.structure.choir.ChoirFloors;
import com.cosmicbreach.structure.choir.ChoirRoom;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.structure.vault.VaultBlock;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The crypt's debug commands (op level 2):
 *
 * <ul>
 *   <li>{@code /cosmicbreach debug place crypt}: a Hollow Crypt ahead of you (in the Deep, on the nearest pillar
 *       worldgen would use; elsewhere, floating with its roof at your feet 30 blocks ahead).</li>
 *   <li>{@code /cosmicbreach debug place choir}: a Choir Floor room of its own 14 blocks ahead, its door toward you.</li>
 *   <li>{@code /cosmicbreach debug choir info|solve|round|relaxed on|off}: the nearest Choir Floor within 64 blocks
 *       ({@code round} answers the current round; {@code relaxed} overrides the server config until it stops).</li>
 *   <li>{@code /cosmicbreach debug locate} lists the nearest Hollow Crypt too ({@link #locate}).</li>
 * </ul>
 */
public final class CryptCommands {
    public static final ResourceKey<Structure> CRYPT = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("hollow_crypt"));

    private static volatile @Nullable CryptPiece lastCrypt;
    private static volatile @Nullable BlockPos lastChoir;

    private CryptCommands() {
    }

    public static @Nullable CryptPiece lastCrypt() {
        return lastCrypt;
    }

    public static @Nullable BlockPos lastChoir() {
        return lastChoir;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("place")
                                .then(Commands.literal("crypt").executes(CryptCommands::placeCrypt))
                                .then(Commands.literal("choir").executes(CryptCommands::placeChoir)))
                        .then(Commands.literal("choir")
                                .then(Commands.literal("info").executes(c -> choir(c, "info")))
                                .then(Commands.literal("solve").executes(c -> choir(c, "solve")))
                                .then(Commands.literal("round").executes(c -> choir(c, "round")))
                                .then(Commands.literal("relaxed")
                                        .then(Commands.literal("on").executes(c -> relaxed(c, true)))
                                        .then(Commands.literal("off").executes(c -> relaxed(c, false)))))));
    }

    // ------------------------------------------------------------------ place

    private static int placeCrypt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CryptPiece piece = placeCryptAhead(player, player.serverLevel().random.nextLong());
        BlockPos gate = piece.entrance();
        BlockPos c = piece.conductor();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.place.crypt", gate.getX(), gate.getY(), gate.getZ(),
                piece.plan().levels, piece.plan().rooms(), c.getX(), c.getY(), c.getZ()), false);
        return 1;
    }

    /**
     * Builds a Hollow Crypt ahead of {@code player}: in the Deep, on the pillar worldgen would use nearest a point 48
     * blocks ahead (its gatehouse at least 20 blocks from the player); elsewhere, floating, its roof at the player's feet
     * with its middle 30 blocks ahead.
     */
    public static CryptPiece placeCryptAhead(ServerPlayer player, long seed) {
        ServerLevel level = player.serverLevel();
        Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos origin = null;
        if (level.dimension() == AetheriaWorld.LEVEL && player.getY() < 160) {
            AetheriaTerrain t = AetheriaSpots.terrain(level);
            for (int far = 48; far <= 160 && origin == null; far += 16) {
                BlockPos c = BlockPos.containing(player.position().add(ahead.scale(far)));
                Optional<HollowCryptStructure.Site> site = HollowCryptStructure.site(t, c.getX(), c.getZ())
                        .filter(s -> s.gate().distSqr(player.blockPosition()) > 20 * 20);
                if (site.isPresent()) {
                    origin = site.get().origin();
                }
            }
        }
        if (origin == null) {
            BlockPos c = BlockPos.containing(player.position().add(ahead.scale(30)));
            int f0 = Mth.clamp(player.getBlockY() - 9, level.getMinBuildHeight() + 30, level.getMaxBuildHeight() - 20);
            origin = new BlockPos(c.getX() - CryptLayout.SIZE / 2, f0, c.getZ() - CryptLayout.SIZE / 2);
        }
        CryptPiece piece = new CryptPiece(origin, seed);
        StructureCommands.build(level, CRYPT, piece);
        lastCrypt = piece;
        return piece;
    }

    private static int placeChoir(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos c = placeChoirAhead(player, player.serverLevel().random.nextLong(), ChoirDifficulty.CRYPT);
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.place.choir", c.getX(), c.getY(), c.getZ()), false);
        return 1;
    }

    /**
     * A Choir Floor room of its own, 21 by 21 and 8 tall, its middle 14 blocks ahead of {@code player} on the level of
     * their feet, its door facing them and its vault across the room. Returns where the Conductor stands.
     */
    public static BlockPos placeChoirAhead(ServerPlayer player, long seed, ChoirDifficulty difficulty) {
        ServerLevel level = player.serverLevel();
        Direction toward = Direction.fromYRot(player.getYRot());
        BlockPos centre = player.blockPosition().relative(toward, 14);
        int half = 10;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = -2; dy <= 8; dy++) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                    BlockState s;
                    if (dy == -2 || dy == 8) {
                        s = ModBlocks.UMBRAL_BASALT.get().defaultBlockState();
                    } else if (dy == -1 || dy == 7) {
                        s = dy == -1 ? ModBlocks.POLISHED_UMBRAL_BASALT.get().defaultBlockState() : ModBlocks.UMBRAL_BASALT.get().defaultBlockState();
                    } else if (edge) {
                        s = ModBlocks.UMBRAL_BASALT_BRICKS.get().defaultBlockState();
                    } else {
                        s = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(p, s, Block.UPDATE_CLIENTS);
                }
            }
        }
        // the door, toward the player
        Direction back = toward.getOpposite();
        for (int w = -1; w <= 1; w++) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos p = centre.relative(back, half).relative(back.getClockWise(), w).above(dy);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // lichen on the walls, a few of each colour
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int along : new int[] {-8, -2, 2, 8}) {
                BlockPos p = centre.relative(d, half - 1).relative(d.getClockWise(), along).above(3);
                Block b = along < 0 ? ModBlocks.MAGENTA_NEON_LICHEN.get() : ModBlocks.TEAL_NEON_LICHEN.get();
                level.setBlock(p, b.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos vault = centre.relative(toward, 9);
        level.setBlock(vault, CryptRegistry.CRYPT_VAULT.get().defaultBlockState().setValue(VaultBlock.FACING, back), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(vault) instanceof VaultBlockEntity v) {
            v.configure(CryptRegistry.VAULT_LOOT, 3);
        }
        ChoirRoom.place(level, BoundingBox.infinite(), centre, difficulty, seed, 3, vault);
        lastChoir = centre.immutable();
        return centre;
    }

    // ------------------------------------------------------------------ choir

    private static @Nullable ConductorBlockEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<ConductorBlockEntity> near = ChoirFloors.near(player.level(), player.position(), 64);
        if (near.isEmpty()) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.choir.none"));
            return null;
        }
        return near.get(0);
    }

    private static int choir(CommandContext<CommandSourceStack> context, String what) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ConductorBlockEntity floor = nearest(source);
        if (floor == null || !(floor.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        switch (what) {
            case "solve" -> floor.debugSolve(level);
            case "round" -> floor.debugRound(level);
            default -> {
            }
        }
        BlockPos p = floor.getBlockPos();
        String phrase = floor.phrase() == null ? "-" : floor.phrase().toString();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.choir." + what, p.getX(), p.getY(), p.getZ(),
                floor.phase().name().toLowerCase(java.util.Locale.ROOT), floor.round() + 1, floor.rotation(), floor.window(), phrase), false);
        return 1;
    }

    private static int relaxed(CommandContext<CommandSourceStack> context, boolean on) throws CommandSyntaxException {
        CryptConfig.override(on);
        ServerPlayer player = context.getSource().getPlayerOrException();
        for (ConductorBlockEntity floor : ChoirFloors.all(player.level())) {
            floor.setWindow(CryptConfig.window());
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.choir.relaxed", on ? "on" : "off",
                CryptConfig.window()), false);
        return 1;
    }

    // ------------------------------------------------------------------ locate

    /** A line for {@code /cosmicbreach debug locate}: the nearest Hollow Crypt, from the terrain model (nothing loads). */
    public static void locate(CommandSourceStack source, ServerLevel level, BlockPos from) {
        Found hit = nearest(level, from, StructureCommands.LOCATE_REGIONS);
        BlockPos found = hit == null ? null : hit.gate();
        String where = found == null ? "none within " + StructureCommands.LOCATE_REGIONS + " regions"
                : found.getX() + ", " + found.getY() + ", " + found.getZ() + " (" + (int) Math.sqrt(found.distSqr(from.atY(found.getY()))) + " blocks)";
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.locate.structure", CRYPT.location().toString(), where), false);
    }

    /** A Hollow Crypt worldgen will start: the chunk it starts in and its gatehouse's column. */
    public record Found(ChunkPos chunk, BlockPos gate) {}

    /** Where worldgen will put the nearest Hollow Crypt, at most {@code regions} placement regions out, or null. */
    public static @Nullable Found nearest(ServerLevel level, BlockPos from, int regions) {
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var set = sets.get(ResourceKey.create(Registries.STRUCTURE_SET, CRYPT.location()));
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CRYPT);
        if (set == null || structure == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
            return null;
        }
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        AetheriaTerrain terrain = AetheriaSpots.terrain(level);
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        int rx = Math.floorDiv(from.getX() >> 4, spread.spacing());
        int rz = Math.floorDiv(from.getZ() >> 4, spread.spacing());
        for (int ring = 0; ring <= regions; ring++) {
            Found best = null;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    ChunkPos c = spread.getPotentialStructureChunk(seed, rx + dx, rz + dz);
                    BlockPos gate = HollowCryptStructure.site(terrain, c.getMiddleBlockX(), c.getMiddleBlockZ())
                            .map(HollowCryptStructure.Site::gate).orElse(null);
                    if (gate == null || !structure.biomes().contains(biomes.getNoiseBiome(QuartPos.fromBlock(gate.getX()),
                            QuartPos.fromBlock(gate.getY()), QuartPos.fromBlock(gate.getZ()), sampler))) {
                        continue;
                    }
                    if (best == null || gate.distSqr(from) < best.gate().distSqr(from)) {
                        best = new Found(c, gate);
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
