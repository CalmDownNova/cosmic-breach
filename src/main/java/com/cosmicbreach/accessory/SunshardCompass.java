package com.cosmicbreach.accessory;

import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Sunshard Compass (GDD 5.2): once a second the nearest vault its wearer has not opened (a vault is per player,
 * GDD 6.1), among the loaded chunks within 8 of theirs, becomes the needle's target ({@link CompassTarget}, synced to
 * them alone). Sealed vaults count: their puzzle is still to solve. Its other half, trap tells within 8 blocks at any
 * speed, is {@link TrapSight}. Server only.
 */
final class SunshardCompass {
    private SunshardCompass() {
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % AccessoryRules.COMPASS_SCAN_EVERY != 0) {
            return;
        }
        CompassTarget now = player.getData(AccessoryRegistry.COMPASS);
        CompassTarget next = Worn.wears(player, Accessory.SUNSHARD_COMPASS) ? find(player) : CompassTarget.NONE;
        if (!next.equals(now)) {
            player.setData(AccessoryRegistry.COMPASS, next);
        }
    }

    /** Looks again now (the scenario asks after placing or opening a vault). */
    static CompassTarget refresh(ServerPlayer player) {
        CompassTarget next = Worn.wears(player, Accessory.SUNSHARD_COMPASS) ? find(player) : CompassTarget.NONE;
        player.setData(AccessoryRegistry.COMPASS, next);
        return next;
    }

    /** The nearest loaded vault {@code player} has not opened. */
    static CompassTarget find(ServerPlayer player) {
        BlockPos best = nearest(player.serverLevel(), player.blockPosition(), player.getUUID());
        return best == null ? CompassTarget.NONE : CompassTarget.at(best);
    }

    static @Nullable BlockPos nearest(ServerLevel level, BlockPos from, UUID player) {
        int cx = SectionPos.blockToSectionCoord(from.getX());
        int cz = SectionPos.blockToSectionCoord(from.getZ());
        int r = AccessoryRules.COMPASS_SCAN_CHUNKS;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof VaultBlockEntity vault && !vault.openedBy(player)) {
                        double d = be.getBlockPos().distSqr(from);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = be.getBlockPos();
                        }
                    }
                }
            }
        }
        return best;
    }
}
