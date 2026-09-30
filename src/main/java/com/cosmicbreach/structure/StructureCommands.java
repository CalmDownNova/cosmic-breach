package com.cosmicbreach.structure;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.gen.GyreObservatoryStructure;
import com.cosmicbreach.structure.gen.ObservatoryPiece;
import com.cosmicbreach.structure.gen.ReliquaryPiece;
import com.cosmicbreach.structure.lens.Lens;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The structures' debug commands (op level 2):
 *
 * <ul>
 *   <li>{@code /cosmicbreach debug place reliquary|observatory}: builds one ahead of you, where you look (a
 *       Reliquary on the ground 26 blocks away, an Observatory round an asteroid of its own 34 blocks away).</li>
 *   <li>{@code /cosmicbreach debug lens info|wake|solve|hint [seconds]|scramble}: the nearest Lens Array within 64
 *       blocks. {@code solve} sets every pedestal right (the 60-tick hold still runs), {@code hint} makes the hint
 *       come after {@code seconds} (default 5) instead of five minutes, {@code scramble} puts the grid back as it
 *       first woke.</li>
 *   <li>{@code /cosmicbreach debug locate} lists the nearest of each structure too ({@link #locate}).</li>
 * </ul>
 */
public final class StructureCommands {
    public static final ResourceKey<Structure> RELIQUARY = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("spire_reliquary"));
    public static final ResourceKey<Structure> OBSERVATORY = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("gyre_observatory"));

    /** The last structure {@code debug place} built (for scenarios). */
    private static volatile @Nullable StructurePiece lastPlaced;

    private StructureCommands() {
    }

    public static @Nullable StructurePiece lastPlaced() {
        return lastPlaced;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("place")
                                .then(Commands.literal("reliquary").executes(c -> place(c, true)))
                                .then(Commands.literal("observatory").executes(c -> place(c, false))))
                        .then(Commands.literal("lens")
                                .then(Commands.literal("info").executes(StructureCommands::info))
                                .then(Commands.literal("wake").executes(c -> lens(c, "wake", 0)))
                                .then(Commands.literal("solve").executes(c -> lens(c, "solve", 0)))
                                .then(Commands.literal("scramble").executes(c -> lens(c, "scramble", 0)))
                                .then(Commands.literal("hint").executes(c -> lens(c, "hint", 5))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                .executes(c -> lens(c, "hint", IntegerArgumentType.getInteger(c, "seconds"))))))));
    }

    // ------------------------------------------------------------------ place

    private static int place(CommandContext<CommandSourceStack> context, boolean reliquary) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        StructurePiece piece = placeAhead(player, reliquary);
        BlockPos at = piece instanceof ReliquaryPiece r ? r.origin() : ((ObservatoryPiece) piece).origin();
        BlockPos core = piece instanceof ReliquaryPiece r ? r.core() : ((ObservatoryPiece) piece).core();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.place", reliquary ? "reliquary" : "observatory",
                at.getX(), at.getY(), at.getZ(), core.getX(), core.getY(), core.getZ()), false);
        return 1;
    }

    /**
     * Builds a structure ahead of {@code player}: a Reliquary on the ground 26 blocks out (in Aetheria, on the nearest
     * spot worldgen would use), or an Observatory 34 blocks out (in Aetheria, round the nearest big asteroid there,
     * else round an asteroid of its own, its centre 16 below the player, kept inside the Drift's band).
     */
    public static StructurePiece placeAhead(ServerPlayer player, boolean reliquary) {
        return placeAhead(player, reliquary, player.serverLevel().random.nextLong());
    }

    /** {@link #placeAhead(ServerPlayer, boolean)} with a chosen structure seed (tests pick puzzles this way). */
    public static StructurePiece placeAhead(ServerPlayer player, boolean reliquary, long seed) {
        ServerLevel level = player.serverLevel();
        Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot());
        StructurePiece piece;
        if (reliquary) {
            BlockPos column = BlockPos.containing(player.position().add(ahead.scale(26)));
            BlockPos at = clearSite(level, column);
            if (at == null) {
                level.getChunk(column);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                at = new BlockPos(column.getX(), ground > level.getMinBuildHeight() ? ground : player.getBlockY(), column.getZ());
            }
            piece = new ReliquaryPiece(at, seed);
        } else {
            BlockPos column = BlockPos.containing(player.position().add(ahead.scale(34)));
            Optional<GyreObservatoryStructure.Site> site = Optional.empty();
            if (level.dimension() == com.cosmicbreach.world.AetheriaWorld.LEVEL) {
                var terrain = com.cosmicbreach.world.AetheriaSpots.terrain(level);
                for (int far = 34; far <= 130 && site.isEmpty(); far += 12) {
                    BlockPos c = BlockPos.containing(player.position().add(ahead.scale(far)));
                    site = GyreObservatoryStructure.site(terrain, c.getX(), c.getZ())
                            .filter(st -> st.centre().distSqr(player.blockPosition()) > 26 * 26);
                }
            }
            if (site.isPresent()) {
                // round a real asteroid, as worldgen would
                piece = new ObservatoryPiece(site.get().centre(), seed, site.get().radius(), site.get().headroom());
            } else {
                int radius = 12;
                int y = Mth.clamp(player.getBlockY() - 16, GyreObservatoryStructure.FLOOR_Y + radius + 12,
                        GyreObservatoryStructure.CEILING_Y - radius - 20 - 7 * 5);
                BlockPos at = new BlockPos(column.getX(), y, column.getZ());
                piece = new ObservatoryPiece(at, seed, radius, GyreObservatoryStructure.CEILING_Y - y);
            }
        }
        build(level, reliquary ? RELIQUARY : OBSERVATORY, piece);
        lastPlaced = piece;
        return piece;
    }

    /**
     * In Aetheria, the nearest place to {@code column} (within 40 blocks) where worldgen itself would stand a
     * Reliquary: well inside an island, level, no natural spire in the way. Null elsewhere or if there is none.
     */
    private static @Nullable BlockPos clearSite(ServerLevel level, BlockPos column) {
        if (level.dimension() != com.cosmicbreach.world.AetheriaWorld.LEVEL) {
            return null;
        }
        com.cosmicbreach.world.gen.AetheriaTerrain t = com.cosmicbreach.world.AetheriaSpots.terrain(level);
        for (int r = 0; r <= 40; r += 4) {
            int steps = Math.max(1, r * 2);
            for (int k = 0; k < steps; k++) {
                double a = k * 2 * Math.PI / steps;
                int x = column.getX() + (int) Math.round(Math.cos(a) * r);
                int z = column.getZ() + (int) Math.round(Math.sin(a) * r);
                Optional<BlockPos> site = com.cosmicbreach.structure.gen.SpireReliquaryStructure.site(t, x, z);
                if (site.isPresent()) {
                    return site.get();
                }
            }
        }
        return null;
    }

    /** Writes {@code piece} into every chunk it covers, as worldgen would. */
    public static void build(ServerLevel level, ResourceKey<Structure> key, StructurePiece piece) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(key);
        build(level, new StructureStart(structure, new ChunkPos(piece.getBoundingBox().getCenter()), 0,
                new PiecesContainer(List.of(piece))));
    }

    /**
     * Starts {@code key} in {@code chunk} the way worldgen does (its own site test and the biome check), or returns
     * null if worldgen would not. Nothing is written: {@link #build(ServerLevel, StructureStart)} does that.
     */
    public static @Nullable StructureStart startAt(ServerLevel level, ResourceKey<Structure> key, ChunkPos chunk) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(key);
        var source = level.getChunkSource();
        StructureStart start = structure.generate(level.registryAccess(), source.getGenerator(), source.getGenerator().getBiomeSource(),
                source.randomState(), level.getStructureManager(), source.getGeneratorState().getLevelSeed(), chunk, 0, level,
                structure.biomes()::contains);
        return start.isValid() ? start : null;
    }

    /** Writes a structure start into every chunk its pieces cover. */
    public static void build(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos from = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos to = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(from, to).forEach(chunk -> {
            level.getChunk(chunk.x, chunk.z);
            start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                            level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        });
    }

    // ------------------------------------------------------------------ lens

    private static @Nullable LensCoreBlockEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<LensCoreBlockEntity> near = LensArrays.near(player.level(), player.position(), 64);
        if (near.isEmpty()) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.lens.none"));
            return null;
        }
        return near.get(0);
    }

    private static int lens(CommandContext<CommandSourceStack> context, String what, int seconds) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        LensCoreBlockEntity core = nearest(source);
        if (core == null || !(core.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        switch (what) {
            case "wake" -> core.wake(level);
            case "solve" -> core.debugSolve(level);
            case "scramble" -> core.debugScramble(level);
            case "hint" -> {
                if (seconds == 0) {
                    core.showHint(level);
                } else {
                    core.hintIn(level, seconds * 20);
                }
            }
            default -> {
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.lens." + what, seconds), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        LensCoreBlockEntity core = nearest(source);
        if (core == null) {
            return 0;
        }
        BlockPos p = core.getBlockPos();
        int receptors = 0;
        for (int port : core.ports()) {
            receptors += Lens.isReceptor(port) ? 1 : 0;
        }
        int lit = Integer.bitCount(core.litMask());
        String phase = switch (core.phase()) {
            case LensCoreBlockEntity.ACTIVE -> "active";
            case LensCoreBlockEntity.SOLVED -> "solved";
            default -> "dormant";
        };
        int count = receptors;
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.lens.info", p.getX(), p.getY(), p.getZ(), phase,
                core.difficulty(), core.minMoves(), lit, count, core.lastTraceNanos() / 1000, core.traces()), false);
        return 1;
    }

    // ------------------------------------------------------------------ locate

    /**
     * Lines for {@code /cosmicbreach debug locate}: the nearest Spire Reliquary and Gyre Observatory. Walks the
     * structure sets' placement regions outward from {@code from} and asks each candidate chunk what worldgen would
     * (the same site test on the terrain model, and the biome there), so nothing is generated or loaded; vanilla's
     * {@code /locate structure} also works, but can hold the server for seconds while it generates chunks.
     */
    public static void locate(CommandSourceStack source, ServerLevel level, BlockPos from) {
        for (ResourceKey<Structure> key : List.of(RELIQUARY, OBSERVATORY)) {
                Found hit = nearest(level, key, from, LOCATE_REGIONS);
            BlockPos found = hit == null ? null : hit.stub();
            String where = found == null ? "none within " + LOCATE_REGIONS + " regions"
                    : found.getX() + ", " + found.getY() + ", " + found.getZ() + " (" + (int) Math.sqrt(found.distSqr(from.atY(found.getY()))) + " blocks)";
            source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.debug.locate.structure", key.location().toString(), where), false);
        }
    }

    /** How many placement regions out {@link #locate} looks (24 or 36 chunks each). */
    public static final int LOCATE_REGIONS = 24;

    /** A structure worldgen will start: the chunk it starts in and where it stands. */
    public record Found(ChunkPos chunk, BlockPos stub) {}

    /**
     * Where worldgen will start the nearest structure {@code key} to {@code from}, looking at most {@code regions}
     * placement regions out, or null. Nearest by region ring, then by distance within the ring.
     */
    public static @Nullable Found nearest(ServerLevel level, ResourceKey<Structure> key, BlockPos from, int regions) {
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var set = sets.get(ResourceKey.create(Registries.STRUCTURE_SET, key.location()));
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (set == null || structure == null
                || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread)) {
            return null;
        }
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        var terrain = com.cosmicbreach.world.AetheriaSpots.terrain(level);
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
                    BlockPos stub = key == RELIQUARY
                            ? com.cosmicbreach.structure.gen.SpireReliquaryStructure.site(terrain, c.getMiddleBlockX(), c.getMiddleBlockZ()).orElse(null)
                            : GyreObservatoryStructure.site(terrain, c.getMiddleBlockX(), c.getMiddleBlockZ()).map(GyreObservatoryStructure.Site::centre)
                                    .orElse(null);
                    if (stub == null || !structure.biomes().contains(biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(stub.getX()),
                            net.minecraft.core.QuartPos.fromBlock(stub.getY()), net.minecraft.core.QuartPos.fromBlock(stub.getZ()), sampler))) {
                        continue;
                    }
                    if (best == null || stub.distSqr(from) < best.stub().distSqr(from)) {
                        best = new Found(c, stub);
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
