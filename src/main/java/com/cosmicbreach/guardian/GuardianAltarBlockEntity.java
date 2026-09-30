package com.cosmicbreach.guardian;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A lair's memory, kept in its altar: which guardian lives here, where its arena's centre is, the lair's
 * cooldown ({@link LairClock}) and the guardian itself. Once a second on the server it registers the lair for
 * locating ({@link GuardianLairs}) and, while the lair is armed and its guardian gone, raises a new dormant one
 * (after a kill, when the 20 minutes are up; or when a saved statue went missing). The guardian reports its death
 * with {@link #guardianKilled}.
 */
public class GuardianAltarBlockEntity extends BlockEntity {
    private BlockPos arenaCentre = BlockPos.ZERO;
    private final LairClock clock = new LairClock();
    private @Nullable UUID guardian;
    private boolean registered;
    private int missingChecks;

    public GuardianAltarBlockEntity(BlockPos pos, BlockState state) {
        super(GuardianRegistry.ALTAR_ENTITY.get(), pos, state);
    }

    public GuardianType type() {
        return ((GuardianAltarBlock) getBlockState().getBlock()).type();
    }

    public BlockPos arenaCentre() {
        return arenaCentre;
    }

    /** Set by the structure (or command) that builds the lair. */
    public void setup(BlockPos arenaCentre) {
        this.arenaCentre = arenaCentre.immutable();
        this.registered = false;
        setChanged();
    }

    public LairClock clock() {
        return clock;
    }

    public boolean armed() {
        return level != null && clock.armed(level.getGameTime());
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, GuardianAltarBlockEntity altar) {
        if (level instanceof ServerLevel server && Math.floorMod(level.getGameTime() + pos.asLong(), 20L) == 0) {
            altar.tickLair(server);
        }
    }

    private void tickLair(ServerLevel level) {
        if (arenaCentre.equals(BlockPos.ZERO)) {
            return; // never set up
        }
        if (!registered) {
            GuardianLairs.get(level).register(worldPosition, type(), arenaCentre);
            registered = true;
        }
        if (!clock.armed(level.getGameTime()) || current(level) != null) {
            missingChecks = 0;
            return;
        }
        if (!level.areEntitiesLoaded(ChunkPos.asLong(arenaCentre))) {
            return; // a saved statue may still be loading
        }
        LairGuardian found = findInArena(level);
        if (found != null) {
            guardian = found.guardianEntity().getUUID();
            setChanged();
            return;
        }
        // twice in a row, a second apart: entities of a chunk that just loaded get a moment to show up
        if (++missingChecks >= 2) {
            missingChecks = 0;
            spawnGuardian(level);
        }
    }

    /** The guardian of this lair, if it is loaded. */
    public @Nullable LairGuardian current(ServerLevel level) {
        if (guardian == null) {
            return null;
        }
        Entity e = level.getEntity(guardian);
        return e instanceof LairGuardian g && e.isAlive() ? g : null;
    }

    private @Nullable LairGuardian findInArena(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(arenaCentre).inflate(6.0),
                e -> e instanceof LairGuardian && e.isAlive())) {
            return (LairGuardian) e;
        }
        return null;
    }

    /** Raises a dormant guardian now (the lair's statue re-forming). */
    public @Nullable LairGuardian spawnGuardian(ServerLevel level) {
        LairGuardian made = type().spawner().spawnDormant(level, arenaCentre, worldPosition);
        if (made != null) {
            guardian = made.guardianEntity().getUUID();
            setChanged();
        }
        return made;
    }

    /** Called by the guardian when it spawns or loads bound to this altar. */
    public void adopt(LairGuardian g) {
        guardian = g.guardianEntity().getUUID();
        setChanged();
    }

    /** The guardian died: the lair rests for 20 minutes. */
    public void guardianKilled(long now) {
        clock.killed(now);
        guardian = null;
        setChanged();
    }

    /** Ends the lair's rest (debug). */
    public void clearCooldown() {
        clock.clear();
        setChanged();
    }

    /** A Guardian Echo used on the altar: wakes the guardian if the lair is armed. True if the Echo was spent. */
    public boolean useEcho(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        long now = level.getGameTime();
        if (!clock.armed(now)) {
            player.displayClientMessage(Component.translatable("message.cosmicbreach.altar.resting",
                    minutes(clock.remaining(now))), true);
            return false;
        }
        LairGuardian g = current(level);
        if (g == null) {
            g = findInArena(level);
        }
        if (g == null) {
            g = spawnGuardian(level);
        }
        if (g == null) {
            return false;
        }
        if (!g.dormant()) {
            player.displayClientMessage(Component.translatable("message.cosmicbreach.altar.awake"), true);
            return false;
        }
        g.awaken(player, true);
        level.playSound(null, worldPosition, GuardianRegistry.ALTAR_ECHO.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    /** What using the altar empty-handed says. */
    public Component status() {
        if (level == null) {
            return Component.empty();
        }
        long now = level.getGameTime();
        if (!clock.armed(now)) {
            return Component.translatable("message.cosmicbreach.altar.resting", minutes(clock.remaining(now)));
        }
        LairGuardian g = level instanceof ServerLevel server ? current(server) : null;
        if (g != null && !g.dormant()) {
            return Component.translatable("message.cosmicbreach.altar.awake");
        }
        return Component.translatable("message.cosmicbreach.altar.armed");
    }

    private static int minutes(long ticks) {
        return (int) Math.max(1, Math.ceil(ticks / 1200.0));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        arenaCentre = NbtUtils.readBlockPos(tag, "Arena").orElse(BlockPos.ZERO);
        clock.restore(tag.contains("RestUntil") ? tag.getLong("RestUntil") : Long.MIN_VALUE);
        guardian = tag.hasUUID("Guardian") ? tag.getUUID("Guardian") : null;
        registered = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Arena", NbtUtils.writeBlockPos(arenaCentre));
        if (clock.restUntil() != Long.MIN_VALUE) {
            tag.putLong("RestUntil", clock.restUntil());
        }
        if (guardian != null) {
            tag.putUUID("Guardian", guardian);
        }
    }
}
