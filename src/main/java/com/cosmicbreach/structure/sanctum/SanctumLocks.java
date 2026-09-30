package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.choir.ChoirFloors;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import com.cosmicbreach.world.AetheriaWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Eclipse Locks and the Throne Stair at work (GDD 6.1, the rules in {@link LockRule}): the west wing's Lens Array
 * ({@link LensArrays#onSolved}) and the east wing's Choir Floor ({@link ChoirFloors#onSolved}) each light their lock;
 * two seconds after the second one burns, the Throne Seal dissolves and the stair lies open. The world's blocks are
 * kept in step with {@link SanctumData} whenever someone is near (a chunk loaded later catches up).
 */
public final class SanctumLocks {
    private static final double NEAR = 96.0;

    private SanctumLocks() {
    }

    /** Hooks the two puzzles (once, at start up). */
    public static void register() {
        LensArrays.onSolved((level, core) -> {
            if (AetheriaWorld.is(level) && core.getBlockPos().equals(SanctumBuilder.at(Sanctums.layout(level).lensCore()))) {
                light(level, Wing.WEST);
            }
        });
        ChoirFloors.onSolved((level, conductor) -> {
            if (AetheriaWorld.is(level) && conductor.getBlockPos().equals(SanctumBuilder.at(Sanctums.layout(level).conductor()))) {
                light(level, Wing.EAST);
            }
        });
    }

    /** Lights {@code wing}'s lock (its puzzle was solved). Nothing if it burns already. */
    public static void light(ServerLevel level, Wing wing) {
        SanctumData data = SanctumData.get(level);
        LockRule before = data.locks();
        if (before.lit(wing)) {
            return;
        }
        LockRule after = before.light(wing);
        data.setLocks(after);
        if (before.opensStair(wing)) {
            data.setStairOpensAt(level.getGameTime() + LockRule.STAIR_DELAY);
        }
        SanctumLayout layout = Sanctums.layout(level);
        BlockPos lock = SanctumBuilder.at(layout.lock(wing));
        setLock(level, lock, true);
        level.playSound(null, lock, SanctumRegistry.LOCK_LIT.get(), SoundSource.BLOCKS, 2.5f, wing == Wing.WEST ? 1.0f : 1.12f);
        level.sendParticles(ParticleTypes.FLAME, lock.getX() + 0.5, lock.getY() + 0.5, lock.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.02);
        Component said = Component.translatable(wing == Wing.WEST ? "cosmicbreach.sanctum.lock.west" : "cosmicbreach.sanctum.lock.east",
                after.count());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(lock.getX() + 0.5, lock.getY(), lock.getZ() + 0.5) <= NEAR * NEAR) {
                p.displayClientMessage(said, true);
            }
        }
    }

    public static void tick(ServerLevel level) {
        SanctumData data = SanctumData.get(level);
        SanctumLayout layout = Sanctums.layout(level);
        long at = data.stairOpensAt();
        if (at >= 0 && level.getGameTime() >= at) {
            if (openStair(level, layout, true)) {
                data.setStairOpensAt(-1);
            }
            return;
        }
        int[] head = layout.stairHead();
        boolean someone = level.players().stream().anyMatch(p -> p.distanceToSqr(head[0] + 0.5, head[1], head[2] + 0.5) <= NEAR * NEAR);
        if (!someone) {
            return;
        }
        LockRule locks = data.locks();
        for (Wing w : Wing.values()) {
            if (locks.lit(w)) {
                setLock(level, SanctumBuilder.at(layout.lock(w)), true);
            }
        }
        if (locks.stair() && at < 0) {
            openStair(level, layout, false);
        }
    }

    /** Dissolves the Throne Seal (with its sound and embers when {@code loudly}). False if its blocks aren't loaded. */
    public static boolean openStair(ServerLevel level, SanctumLayout layout, boolean loudly) {
        boolean any = false;
        for (int[] b : layout.sealBlocks()) {
            BlockPos pos = SanctumBuilder.at(b);
            if (!level.isLoaded(pos)) {
                return false;
            }
            if (level.getBlockState(pos).is(SanctumRegistry.THRONE_SEAL.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                any = true;
                if (loudly) {
                    level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
        if (any && loudly) {
            int[] head = layout.stairHead();
            BlockPos h = new BlockPos(head[0], head[1] + 3, head[2]);
            level.playSound(null, h, SanctumRegistry.STAIR_OPEN.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(h.getX() + 0.5, h.getY(), h.getZ() + 0.5) <= NEAR * NEAR) {
                    Sanctums.subtitle(p, Component.translatable("cosmicbreach.sanctum.stair"));
                }
            }
        }
        return true;
    }

    /** Lights a lock, and its gold frame in the wall catches too: a ring of embers round the burning disc. */
    private static void setLock(ServerLevel level, BlockPos pos, boolean lit) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState s = level.getBlockState(pos);
        if (s.is(SanctumRegistry.ECLIPSE_LOCK.get()) && s.getValue(EclipseLockBlock.LIT) != lit) {
            level.setBlock(pos, s.setValue(EclipseLockBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (!s.is(SanctumRegistry.ECLIPSE_LOCK.get())) {
            return;
        }
        Direction face = s.getValue(EclipseLockBlock.FACING);
        Direction across = face.getClockWise();
        BlockState frame = (lit ? SanctumRegistry.SANCTUM_EMBER : SanctumRegistry.SANCTUM_GILT).get().defaultBlockState();
        for (int a = -1; a <= 1; a++) {
            for (int up = -1; up <= 1; up++) {
                if (a == 0 && up == 0) {
                    continue;
                }
                BlockPos p = pos.relative(across, a).above(up);
                BlockState here = level.getBlockState(p);
                if ((here.is(SanctumRegistry.SANCTUM_GILT.get()) || here.is(SanctumRegistry.SANCTUM_EMBER.get())) && !here.is(frame.getBlock())) {
                    level.setBlock(p, frame, Block.UPDATE_ALL);
                }
            }
        }
    }
}
