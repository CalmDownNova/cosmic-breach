package com.cosmicbreach.guardian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** A guardian that lives in a lair: its altar wakes it and hears when it dies. Implemented by the guardian entity. */
public interface LairGuardian {
    /** True while it is a dormant statue, waiting to be woken. */
    boolean dormant();

    /** Wakes it: {@code echo} if a Guardian Echo was used on the altar, {@code by} whoever did it. */
    void awaken(@Nullable ServerPlayer by, boolean echo);

    /** Binds it to its lair (the altar it reports to). */
    void bindLair(BlockPos arenaCentre, BlockPos altar);

    Entity guardianEntity();
}
