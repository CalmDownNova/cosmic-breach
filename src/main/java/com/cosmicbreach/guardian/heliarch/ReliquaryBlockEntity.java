package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.progression.AttunementXp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One participant's Reliquary: whose it is and whether they have opened it. Opening rolls their share (a first kill
 * when they don't hold "Breach Sealed" yet): the items spring out toward them (only they can pick them up), then the
 * Attunement XP, the stat points and the advancement.
 */
public class ReliquaryBlockEntity extends BlockEntity {
    /** The Heliarch's first-kill advancement ("Breach Sealed"): two stat points come with it. */
    public static final ResourceLocation BREACH_SEALED = CosmicBreach.id("guardian/breach_sealed");
    /** The client's own player, for the "yours to open" light (set by the client). */
    public static Supplier<UUID> localPlayer = () -> null;

    private @Nullable UUID owner;
    private String ownerName = "";
    private boolean opened;
    /** What the last opening gave (for checks). */
    private static HeliarchLoot.Reward lastReward;
    private static int openings;

    public ReliquaryBlockEntity(BlockPos pos, BlockState state) {
        super(HeliarchRegistry.RELIQUARY_ENTITY.get(), pos, state);
    }

    public void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Places {@code player}'s Reliquary at {@code pos}, facing {@code facing}. */
    public static boolean place(ServerLevel level, BlockPos pos, Direction facing, UUID owner, String name) {
        BlockState state = HeliarchRegistry.RELIQUARY.get().defaultBlockState().setValue(ReliquaryBlock.FACING, facing);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof ReliquaryBlockEntity r) {
            r.owner = owner;
            r.ownerName = name;
            r.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean opened() {
        return opened;
    }

    /** True on the client when this is the local player's own, not opened yet. */
    public boolean yoursToOpen() {
        return !opened && owner != null && owner.equals(localPlayer.get());
    }

    public static @Nullable HeliarchLoot.Reward lastReward() {
        return lastReward;
    }

    public static int openings() {
        return openings;
    }

    /** {@code player} opens it: their share if it is theirs and still closed. Returns what they got, or null. */
    public @Nullable HeliarchLoot.Reward open(ServerLevel level, ServerPlayer player) {
        if (owner == null || !owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("cosmicbreach.heliarch.reliquary.not_yours", ownerName), true);
            return null;
        }
        if (opened) {
            player.displayClientMessage(Component.translatable("cosmicbreach.heliarch.reliquary.empty"), true);
            return null;
        }
        boolean first = !GuardianRewards.hasAdvancement(player, BREACH_SEALED);
        List<ResourceLocation> charms = new ArrayList<>();
        for (ResourceLocation id : HeliarchLoot.CHARMS) {
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                charms.add(id);
            }
        }
        HeliarchLoot.Reward reward = HeliarchLoot.roll(first, charms, level.random::nextDouble);
        Direction facing = getBlockState().getValue(ReliquaryBlock.FACING);
        Vec3 mouth = Vec3.atCenterOf(worldPosition).add(0, 0.4, 0);
        Vec3 toward = player.position().subtract(mouth).multiply(1, 0, 1);
        toward = toward.lengthSqr() < 1e-4 ? new Vec3(facing.getStepX(), 0, facing.getStepZ()) : toward.normalize();
        for (HeliarchLoot.Drop drop : reward.drops()) {
            Item item = BuiltInRegistries.ITEM.get(drop.item());
            if (item == Items.AIR) {
                CosmicBreach.LOGGER.warn("[cosmicbreach] a reward entry names an item that is not registered; skipped");
                CosmicBreach.LOGGER.debug("[cosmicbreach] missing reward item {}", drop.item());
                continue;
            }
            ItemEntity e = new ItemEntity(level, mouth.x, mouth.y, mouth.z, new ItemStack(item, drop.count()));
            e.setDeltaMovement(toward.x * 0.18 + level.random.triangle(0.0, 0.05), 0.28, toward.z * 0.18 + level.random.triangle(0.0, 0.05));
            e.setTarget(player.getUUID());
            e.setPickUpDelay(GuardianRewards.PICKUP_DELAY);
            e.setExtendedLifetime();
            level.addFreshEntity(e);
        }
        if (reward.xp() > 0) {
            AttunementXp.award(player, reward.xp());
        }
        if (reward.statPoints() > 0) {
            AttunementXp.awardStatPoints(player, reward.statPoints());
        }
        if (reward.firstKill()) {
            GuardianRewards.grantAdvancement(player, BREACH_SEALED);
        }
        opened = true;
        lastReward = reward;
        openings++;
        setChanged();
        BlockState open = getBlockState().setValue(ReliquaryBlock.OPEN, true);
        level.setBlock(worldPosition, open, Block.UPDATE_ALL);
        level.scheduleTick(worldPosition, open.getBlock(), ReliquaryBlock.FADE_TICKS);
        level.playSound(null, worldPosition, HeliarchRegistry.RELIQUARY_OPEN.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, mouth.x, mouth.y + 0.4, mouth.z, 40, 0.3, 0.6, 0.3, 0.06);
        level.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 16, 0.3, 0.2, 0.3, 0.02);
        player.displayClientMessage(Component.translatable(reward.firstKill() ? "cosmicbreach.heliarch.reliquary.first"
                : "cosmicbreach.heliarch.reliquary.opened"), true);
        CosmicBreach.LOGGER.debug("[cosmicbreach] {} opened their Reliquary: {}", player.getGameProfile().getName(), reward);
        return reward;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("owner_name", ownerName);
        tag.putBoolean("opened", opened);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        opened = tag.getBoolean("opened");
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
