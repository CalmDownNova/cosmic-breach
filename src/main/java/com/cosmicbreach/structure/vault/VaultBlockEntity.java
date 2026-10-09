package com.cosmicbreach.structure.vault;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.AttunementXp;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.structure.StructureRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A vault's memory: whether its puzzle has been solved (the block's {@link VaultBlock#READY}) and who has taken
 * their share. Each player opens it once (GDD 6.1); what they get is rolled from its loot table then, with their
 * luck, and ejected in front of it. Opening it also pays the structure's "found" Attunement XP (G1's
 * {@link XpSource#STRUCTURE_FOUND}), once per player per vault.
 *
 * <p>Loot tables ({@code data/cosmicbreach/loot_table/vaults/}): {@code reliquary} (T1) and {@code observatory}
 * (T2), each pulling a sub-table {@code accessories_t1} / {@code accessories_t2}, empty until the accessories
 * arrive; that task only fills those two files. A structure with rewards of its own (the Sanctum's wings) calls
 * {@link #configure} with its table and layer.
 */
public class VaultBlockEntity extends BlockEntity {
    public static final ResourceKey<LootTable> RELIQUARY = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("vaults/reliquary"));
    public static final ResourceKey<LootTable> OBSERVATORY = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("vaults/observatory"));
    /** The client's own player, for the "still yours to open" glints (set by the client). */
    public static Supplier<UUID> localPlayer = () -> null;

    private final Set<UUID> opened = new HashSet<>();
    /** A loot table set by the structure that placed this vault (the Sanctum's wings), else null for the tier's own. */
    private @Nullable ResourceKey<LootTable> table;
    /** The layer tier its XP is paid at: 1 Reach, 2 Drift, 3 Deep (0: from the block). */
    private int xpTier;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(StructureRegistry.VAULT_ENTITY.get(), pos, state);
    }

    public ResourceKey<LootTable> lootTable() {
        if (table != null) {
            return table;
        }
        return getBlockState().getBlock() instanceof VaultBlock v && v.tier() >= 2 ? OBSERVATORY : RELIQUARY;
    }

    /** For structures with their own rewards: this vault rolls {@code table} and pays XP at layer {@code tier} (1 to 3). */
    public void configure(@Nullable ResourceKey<LootTable> table, int tier) {
        this.table = table;
        this.xpTier = tier;
        setChanged();
    }

    private XpSource.LayerTier layer() {
        int t = xpTier > 0 ? xpTier : getBlockState().getBlock() instanceof VaultBlock v ? v.tier() : 1;
        return t >= 3 ? XpSource.LayerTier.DEEP : t == 2 ? XpSource.LayerTier.DRIFT : XpSource.LayerTier.REACH;
    }

    public boolean ready() {
        return getBlockState().hasProperty(VaultBlock.READY) && getBlockState().getValue(VaultBlock.READY);
    }

    public boolean openedBy(UUID player) {
        return opened.contains(player);
    }

    public boolean openedByLocal() {
        UUID me = localPlayer.get();
        return me != null && opened.contains(me);
    }

    /** The puzzle is solved: the vault unseals. */
    public void unlock(ServerLevel level) {
        if (!ready()) {
            level.setBlock(worldPosition, getBlockState().setValue(VaultBlock.READY, true), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, StructureRegistry.VAULT_OPEN.get(), SoundSource.BLOCKS, 1.3f, 0.8f);
        }
    }

    /** Seals it again (debug: the puzzle was scrambled back). Who already took their share still has. */
    public void seal(ServerLevel level) {
        if (ready()) {
            level.setBlock(worldPosition, getBlockState().setValue(VaultBlock.READY, false), Block.UPDATE_ALL);
        }
    }

    /** {@code player} tries to open it. Returns the items they got (empty if sealed or already theirs). */
    public List<ItemStack> open(ServerLevel level, ServerPlayer player) {
        if (!ready()) {
            player.displayClientMessage(Component.translatable("cosmicbreach.vault.sealed"), true);
            return List.of();
        }
        if (opened.contains(player.getUUID())) {
            player.displayClientMessage(Component.translatable("cosmicbreach.vault.empty"), true);
            return List.of();
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.VAULT);
        List<ItemStack> items = table.getRandomItems(params);
        Direction facing = getBlockState().getValue(VaultBlock.FACING);
        Vec3 mouth = Vec3.atCenterOf(worldPosition).add(facing.getStepX() * 0.7, 0.3, facing.getStepZ() * 0.7);
        for (ItemStack stack : items) {
            ItemEntity e = new ItemEntity(level, mouth.x, mouth.y, mouth.z, stack);
            e.setDeltaMovement(facing.getStepX() * 0.12 + level.random.triangle(0.0, 0.05), 0.2,
                    facing.getStepZ() * 0.12 + level.random.triangle(0.0, 0.05));
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        opened.add(player.getUUID());
        com.cosmicbreach.codex.PuzzleRoomLog.kindOf(getBlockState())
                .ifPresent(kind -> com.cosmicbreach.codex.PuzzleRoomLog.get(level).opened(worldPosition, kind, player.getUUID()));
        level.playSound(null, worldPosition, StructureRegistry.VAULT_OPEN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        AttunementXp.award(player, XpSource.STRUCTURE_FOUND.at(layer()));
        player.displayClientMessage(Component.translatable("cosmicbreach.vault.opened"), true);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return items;
    }

    /** Tells the Codex's puzzle-room log this vault exists and who has opened it (also catches vaults opened before it). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            com.cosmicbreach.codex.PuzzleRoomLog.kindOf(getBlockState())
                    .ifPresent(kind -> com.cosmicbreach.codex.PuzzleRoomLog.get(server).seen(worldPosition, kind, opened));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        opened.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put("opened", list);
        if (table != null) {
            tag.putString("loot_table", table.location().toString());
        }
        if (xpTier > 0) {
            tag.putInt("xp_tier", xpTier);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        opened.clear();
        for (Tag t : tag.getList("opened", Tag.TAG_INT_ARRAY)) {
            opened.add(NbtUtils.loadUUID(t));
        }
        table = tag.contains("loot_table")
                ? ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.ResourceLocation.parse(tag.getString("loot_table"))) : null;
        xpTier = tag.getInt("xp_tier");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
