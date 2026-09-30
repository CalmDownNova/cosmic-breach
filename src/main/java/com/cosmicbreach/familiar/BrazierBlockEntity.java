package com.cosmicbreach.familiar;

import com.cosmicbreach.world.weather.SolarFlare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a Brazier of Solenne holds: a Star Egg warming (with the loaded ticks it has had, saved) or the Familiar Lantern
 * it hatched into. The egg counts only while the brazier is loaded and ticking; every second the server also checks
 * for a Solar Flare burning on the open bowl, which hatches it at once.
 */
public class BrazierBlockEntity extends BlockEntity {
    /** Test and debug only: the hatching time for every brazier ({@code /cosmicbreach familiar hatchtime}); -1 is the GDD's 10 minutes. */
    private static int debugHatchTicks = -1;

    private ItemStack held = ItemStack.EMPTY;
    private int progress;

    public BrazierBlockEntity(BlockPos pos, BlockState state) {
        super(FamiliarRegistry.BRAZIER_ENTITY.get(), pos, state);
    }

    /** The loaded ticks an egg needs now. */
    public static int hatchTicks() {
        return debugHatchTicks > 0 ? debugHatchTicks : FamiliarRules.HATCH_TICKS;
    }

    /** Test and debug: every brazier hatches after {@code ticks} (0 or less: back to 10 minutes). */
    public static void setHatchTicks(int ticks) {
        debugHatchTicks = ticks > 0 ? ticks : -1;
    }

    public ItemStack held() {
        return held;
    }

    /** Loaded ticks the egg has had. */
    public int progress() {
        return progress;
    }

    /** True while it warms an egg. */
    public boolean warming() {
        return held.is(FamiliarRegistry.STAR_EGG.get());
    }

    void setEgg(ItemStack egg) {
        held = egg;
        progress = 0;
        if (level instanceof ServerLevel server) {
            server.setBlock(worldPosition, getBlockState().setValue(BrazierBlock.LIT, true), Block.UPDATE_ALL);
            server.playSound(null, worldPosition, FamiliarRegistry.EGG_SET.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        }
        changed();
    }

    /** Takes out what it holds (an egg loses its warmth). */
    ItemStack take() {
        ItemStack out = held;
        held = ItemStack.EMPTY;
        progress = 0;
        if (level instanceof ServerLevel server && getBlockState().getValue(BrazierBlock.LIT)) {
            server.setBlock(worldPosition, getBlockState().setValue(BrazierBlock.LIT, false), Block.UPDATE_ALL);
        }
        changed();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrazierBlockEntity be) {
        if (!be.warming()) {
            return;
        }
        be.progress++;
        boolean flare = be.progress % 20 == 0 && SolarFlare.exposed(level, pos.above());
        if (flare || FamiliarRules.hatched(be.progress, hatchTicks())) {
            be.hatch((ServerLevel) level, flare);
        } else if (be.progress % 200 == 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BrazierBlockEntity be) {
        if (be.warming()) {
            be.progress++;
        }
    }

    /** The egg hatches: its lantern waits in the bowl. */
    void hatch(ServerLevel level, boolean flare) {
        ItemStack lantern = StarEggItem.hatch(held, level.random);
        held = lantern;
        progress = 0;
        level.setBlock(worldPosition, getBlockState().setValue(BrazierBlock.LIT, false), Block.UPDATE_ALL);
        Vec3 at = Vec3.atCenterOf(worldPosition).add(0, 0.55, 0);
        level.playSound(null, at.x, at.y, at.z, FamiliarRegistry.HATCH.get(), SoundSource.BLOCKS, 1.0f, flare ? 1.1f : 1.0f);
        FamiliarBond b = FamiliarLanternItem.bond(lantern);
        FamiliarNet.fxAt(level, at, FamiliarFxPayload.HATCH, -1, b == null ? 0 : b.kind().ordinal());
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put("held", held.save(registries));
        }
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains("held") ? ItemStack.parseOptional(registries, tag.getCompound("held")) : ItemStack.EMPTY;
        progress = tag.getInt("progress");
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
