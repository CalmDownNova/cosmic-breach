package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.gen.Hashing;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Leviathan Rift as a structure ({@code cosmicbreach:leviathan_rift}): rare, in the Drift Belt, placed by a random
 * spread of 48 chunks with 18 of separation (its structure set). The sphere's centre is the start chunk's middle at
 * Y {@value RiftLayout#CENTRE_Y}, the height that keeps the whole sphere inside the Drift's band (placement needs no
 * terrain: the Rift clears its own inside), and never within {@value #BREACH_CLEARANCE} blocks of the Breach. Built in
 * the last decoration step, after the asteroids' decoration, so nothing grows through it.
 */
public class LeviathanRiftStructure extends Structure {
    public static final MapCodec<LeviathanRiftStructure> CODEC = simpleCodec(LeviathanRiftStructure::new);
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("leviathan_rift"));
    /** The sphere stays this far from the Breach's axis (the chasm is about 300 across). */
    public static final int BREACH_CLEARANCE = 260;
    private static final int TAG = 0x1EF7;

    public LeviathanRiftStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return site(context.seed(), context.chunkPos()).map(layout -> new GenerationStub(layout.centreBlock(),
                builder -> builder.addPiece(new LeviathanRiftPiece(layout))));
    }

    @Override
    public StructureType<?> type() {
        return LeviathanRegistry.LEVIATHAN_RIFT_TYPE.get();
    }

    /** The Rift a start in {@code chunk} builds, unless it would touch the Breach. */
    public static Optional<RiftLayout> site(long worldSeed, ChunkPos chunk) {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (Math.hypot(x, z) < BREACH_CLEARANCE) {
            return Optional.empty();
        }
        return Optional.of(layout(worldSeed, new BlockPos(x, RiftLayout.CENTRE_Y, z)));
    }

    /** The layout of the Rift whose centre is {@code centre} (its seed follows from the world's and the centre). */
    public static RiftLayout layout(long worldSeed, BlockPos centre) {
        return new RiftLayout(centre.getX(), centre.getY(), centre.getZ(), Hashing.hash(worldSeed, TAG, centre.getX(), centre.getY(), centre.getZ()));
    }

    /** The arena centre of the lair starting in {@code chunk} (for the lair locate API). */
    public static Optional<BlockPos> siteFor(ServerLevel level, ChunkPos chunk) {
        return site(level.getSeed(), chunk).map(RiftLayout::centreBlock);
    }
}
