package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Fallen Rift's crater ({@link FallenRiftLayout}): cleared down to a bowl lined with Starfall Stone,
 * Meteorite, gravel and scorched dirt, a raised lip of ejecta round it, five frames of a Breach Ring flush
 * with the floor at the bottom (their middle open), and a chest sunk into the floor beside them with
 * {@link #LOOT}. Drawn chunk by chunk from the rift's seed, so the parts always match.
 */
public class FallenRiftPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("chests/fallen_rift"));
    /** Trees and plants this far above the ground are cleared out of the bowl. */
    private static final int CLEAR_UP = 12;

    private final BlockPos centre;
    private final int radius;
    private final long seed;

    public FallenRiftPiece(BlockPos centre, int radius, long seed) {
        super(OnboardingRegistry.FALLEN_RIFT_PIECE.get(), 0, box(centre, radius));
        this.centre = centre.immutable();
        this.radius = radius;
        this.seed = seed;
    }

    public FallenRiftPiece(CompoundTag tag) {
        super(OnboardingRegistry.FALLEN_RIFT_PIECE.get(), tag);
        this.centre = new BlockPos(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"));
        this.radius = tag.getInt("R");
        this.seed = tag.getLong("Seed");
    }

    private static BoundingBox box(BlockPos c, int radius) {
        int reach = radius + FallenRiftLayout.LIP + 1;
        return new BoundingBox(c.getX() - reach, c.getY() - FallenRiftLayout.depth(radius) - 5, c.getZ() - reach,
                c.getX() + reach + 1, c.getY() + CLEAR_UP + 8, c.getZ() + reach + 1);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", centre.getX());
        tag.putInt("CY", centre.getY());
        tag.putInt("CZ", centre.getZ());
        tag.putInt("R", radius);
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int reach = radius + FallenRiftLayout.LIP;
        int cx = centre.getX();
        int cy = centre.getY();
        int cz = centre.getZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach + 1; dx++) {
            for (int dz = -reach; dz <= reach + 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                    continue;
                }
                double d = FallenRiftLayout.fromMiddle(dx, dz);
                int offset = FallenRiftLayout.floorAt(radius, dx, dz);
                if (offset == Integer.MIN_VALUE) {
                    continue;
                }
                int floorY = cy + offset;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (d <= radius) {
                    for (int y = floorY + 1; y <= Math.max(top, cy) + CLEAR_UP; y++) {
                        if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                            set(level, chunkBox, p, Blocks.AIR.defaultBlockState());
                        }
                    }
                    for (int y = floorY - 1; y >= floorY - 4 && level.getBlockState(p.set(x, y, z)).canBeReplaced(); y--) {
                        set(level, chunkBox, p, ModBlocks.STARFALL_STONE.get().defaultBlockState());
                    }
                    set(level, chunkBox, p.set(x, floorY - 1, z), ModBlocks.STARFALL_STONE.get().defaultBlockState());
                    set(level, chunkBox, p.set(x, floorY, z), lining(x, floorY, z, d));
                } else {
                    for (int y = top + 1; y <= floorY; y++) {
                        set(level, chunkBox, p.set(x, y, z), ejecta(x, y, z));
                    }
                    if (floorY <= top && top - floorY <= 1) {
                        set(level, chunkBox, p.set(x, top, z), ejecta(x, top, z));
                    }
                }
            }
        }
        // the half-buried ring: eight of its twelve frames flush with the floor; the gaps where the others lay
        // and the 2 by 2 middle open, over Starfall Stone
        int ringY = cy - FallenRiftLayout.depth(radius);
        boolean[] present = FallenRiftLayout.framesPresent(seed);
        for (int i = 0; i < BreachRing.RING.length; i++) {
            set(level, chunkBox, p.set(cx + BreachRing.RING[i][0], ringY, cz + BreachRing.RING[i][1]),
                    present[i] ? ModBlocks.BREACH_FRAME.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
            set(level, chunkBox, p.set(cx + BreachRing.RING[i][0], ringY - 1, cz + BreachRing.RING[i][1]),
                    ModBlocks.STARFALL_STONE.get().defaultBlockState());
        }
        for (int[] m : BreachRing.MIDDLE) {
            set(level, chunkBox, p.set(cx + m[0], ringY, cz + m[1]), Blocks.AIR.defaultBlockState());
            set(level, chunkBox, p.set(cx + m[0], ringY - 1, cz + m[1]), ModBlocks.STARFALL_STONE.get().defaultBlockState());
        }
        // the chest, sunk into the floor beside the ring
        int[] c = FallenRiftLayout.chest(seed);
        int chestY = cy + FallenRiftLayout.floorAt(radius, c[0], c[1]);
        createChest(level, chunkBox, random, new BlockPos(cx + c[0], chestY, cz + c[1]), LOOT, null);
    }

    private static void set(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, state, 2);
        }
    }

    /** The bowl's floor: Starfall Stone with Meteorite chunks near the middle, scorched dirt and gravel toward the rim. */
    private BlockState lining(int x, int y, int z, double d) {
        double n = FallenRiftLayout.noise(seed, x, y, z);
        double rim = d / radius;
        if (n < 0.14 * (1.0 - rim)) {
            return ModBlocks.METEORITE.get().defaultBlockState();
        }
        if (n > 1.0 - 0.3 * rim) {
            return (n > 1.0 - 0.15 * rim ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState();
        }
        return ModBlocks.STARFALL_STONE.get().defaultBlockState();
    }

    /** The lip thrown up round the crater. */
    private BlockState ejecta(int x, int y, int z) {
        double n = FallenRiftLayout.noise(seed ^ 0x5DEECE66DL, x, y, z);
        if (n < 0.35) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (n < 0.6) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        return ModBlocks.STARFALL_STONE.get().defaultBlockState();
    }

    /** For checks: the ring's origin (its middle's north-west block). */
    public BlockPos ringOrigin() {
        return new BlockPos(centre.getX(), centre.getY() - FallenRiftLayout.depth(radius), centre.getZ());
    }
}
