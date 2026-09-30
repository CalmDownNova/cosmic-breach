package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptGuards;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Void Pocket (GDD 6.4, {@link PocketRules}): a sealed room under a Void Rift. The first player to land in it wakes
 * it: three Hollow Stalkers come out of its corners (once {@code cosmicbreach:hollow_stalker} exists; until then,
 * nobody), and its way out, a wall of Rift Seal, opens when they are dead or after 45 s. With the pocket empty for
 * 10 s it seals again for the next fall. Checks four times a second while a player is near, once a second otherwise.
 */
public class VoidPocketBlockEntity extends BlockEntity {
    /** The room's inside, relative to this block (the floor's middle): min and max corners. */
    private BlockPos from = new BlockPos(-4, 1, -4);
    private BlockPos to = new BlockPos(4, 6, 4);
    /** The seal's blocks, relative to this block. */
    private final List<BlockPos> seal = new ArrayList<>();
    private long wokeAt = -1;
    private long openedAt = -1;
    private long emptySince = -1;
    private int spawned;
    private final List<UUID> stalkers = new ArrayList<>();

    public VoidPocketBlockEntity(BlockPos pos, BlockState state) {
        super(CryptRegistry.VOID_POCKET_ENTITY.get(), pos, state);
    }

    /** Sets the pocket up at worldgen: its inside (relative corners) and the seal's blocks (relative). */
    public void configure(BlockPos from, BlockPos to, List<BlockPos> seal) {
        this.from = from.immutable();
        this.to = to.immutable();
        this.seal.clear();
        seal.forEach(p -> this.seal.add(p.immutable()));
        setChanged();
    }

    public AABB inside() {
        return new AABB(Vec3.atLowerCornerOf(worldPosition.offset(from)), Vec3.atLowerCornerOf(worldPosition.offset(to).offset(1, 1, 1)));
    }

    public long wokeAt() {
        return wokeAt;
    }

    public long openedAt() {
        return openedAt;
    }

    public int spawned() {
        return spawned;
    }

    public boolean sealed() {
        return level != null && !seal.isEmpty() && level.getBlockState(worldPosition.offset(seal.get(0))).is(CryptRegistry.POCKET_SEAL.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VoidPocketBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();
        boolean near = be.wokeAt >= 0 || server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, false) != null;
        if ((now + pos.asLong()) % (near ? 5 : 20) != 0) {
            return;
        }
        List<ServerPlayer> inside = new ArrayList<>();
        AABB box = be.inside();
        for (ServerPlayer p : server.players()) {
            if (p.isAlive() && !p.isSpectator() && box.contains(p.position())) {
                inside.add(p);
            }
        }
        if (be.wokeAt < 0) {
            if (!inside.isEmpty()) {
                be.wake(server, inside, now);
            }
            return;
        }
        if (be.openedAt < 0) {
            int alive = 0;
            for (UUID id : be.stalkers) {
                Entity e = server.getEntity(id);
                alive += e != null && e.isAlive() ? 1 : 0;
            }
            if (PocketRules.opens(be.wokeAt, now, be.spawned, alive)) {
                be.open(server, now);
            }
            return;
        }
        if (inside.isEmpty()) {
            if (be.emptySince < 0) {
                be.emptySince = now;
            } else if (now - be.emptySince >= PocketRules.RESEAL_TICKS) {
                be.reseal(server);
            }
        } else {
            be.emptySince = -1;
        }
    }

    private void wake(ServerLevel level, List<ServerPlayer> inside, long now) {
        wokeAt = now;
        stalkers.clear();
        spawned = 0;
        if (CryptGuards.stalkerRegistered()) {
            int[][] corners = {{from.getX() + 1, from.getZ() + 1}, {to.getX() - 1, from.getZ() + 1}, {from.getX() + 1, to.getZ() - 1}};
            for (int[] c : corners) {
                Vec3 at = Vec3.atBottomCenterOf(worldPosition.offset(c[0], 1, c[1]));
                CryptGuards.stalker(level, at).ifPresent(e -> {
                    stalkers.add(e.getUUID());
                    spawned++;
                });
            }
        }
        for (ServerPlayer p : inside) {
            p.displayClientMessage(Component.translatable(spawned > 0 ? "cosmicbreach.pocket.hunted" : "cosmicbreach.pocket.sealed"), true);
        }
        setChanged();
    }

    private void open(ServerLevel level, long now) {
        openedAt = now;
        emptySince = -1;
        for (BlockPos rel : seal) {
            BlockPos p = worldPosition.offset(rel);
            if (level.getBlockState(p).is(CryptRegistry.POCKET_SEAL.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (!seal.isEmpty()) {
            level.playSound(null, worldPosition.offset(seal.get(seal.size() / 2)), CryptRegistry.SEAL_OPEN.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        }
        setChanged();
    }

    private void reseal(ServerLevel level) {
        for (BlockPos rel : seal) {
            BlockPos p = worldPosition.offset(rel);
            if (level.getBlockState(p).isAir() && level.getEntitiesOfClass(Entity.class, new AABB(p)).isEmpty()) {
                level.setBlock(p, CryptRegistry.POCKET_SEAL.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        wokeAt = -1;
        openedAt = -1;
        emptySince = -1;
        spawned = 0;
        stalkers.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("from", NbtUtils.writeBlockPos(from));
        tag.put("to", NbtUtils.writeBlockPos(to));
        long[] rel = new long[seal.size()];
        for (int i = 0; i < rel.length; i++) {
            rel[i] = seal.get(i).asLong();
        }
        tag.putLongArray("seal", rel);
        tag.putLong("woke_at", wokeAt);
        tag.putLong("opened_at", openedAt);
        tag.putInt("spawned", spawned);
        ListTag ids = new ListTag();
        stalkers.forEach(id -> ids.add(NbtUtils.createUUID(id)));
        tag.put("stalkers", ids);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        from = NbtUtils.readBlockPos(tag, "from").orElse(from);
        to = NbtUtils.readBlockPos(tag, "to").orElse(to);
        seal.clear();
        for (long l : tag.getLongArray("seal")) {
            seal.add(BlockPos.of(l));
        }
        wokeAt = tag.contains("woke_at") ? tag.getLong("woke_at") : -1;
        openedAt = tag.contains("opened_at") ? tag.getLong("opened_at") : -1;
        spawned = tag.getInt("spawned");
        stalkers.clear();
        for (Tag t : tag.getList("stalkers", Tag.TAG_INT_ARRAY)) {
            stalkers.add(NbtUtils.loadUUID(t));
        }
    }
}
