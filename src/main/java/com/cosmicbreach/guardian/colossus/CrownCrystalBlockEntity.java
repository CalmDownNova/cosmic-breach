package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.GuardianRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A crown crystal's memory, on its controller block: which crystal of the ring it is ({@link #index}), the target
 * it points at (another crystal's index or {@link Refraction#CORE}), and the arena's centre. Synced to clients, who
 * draw the pointer on its top. While a Refraction charges the Colossus marks it turnable ({@link #turnableUntil});
 * a hit then turns it one step clockwise ({@link #tryTurn}).
 */
public class CrownCrystalBlockEntity extends BlockEntity {
    /** Hits closer together than this turn it only once (one swing's active ticks, a multi-hit). */
    public static final int TURN_GAP_TICKS = 4;

    private int index;
    private int target = Refraction.CORE;
    private BlockPos arenaCentre = BlockPos.ZERO;
    private long turnableUntil = Long.MIN_VALUE;
    private @Nullable UUID owner;
    private long lastTurn = Long.MIN_VALUE;
    private final Map<UUID, Integer> lastSerial = new HashMap<>();

    public CrownCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(GuardianRegistry.CROWN_CRYSTAL_ENTITY.get(), pos, state);
    }

    public int index() {
        return index;
    }

    public int target() {
        return target;
    }

    public BlockPos arenaCentre() {
        return arenaCentre;
    }

    public CrownArena arena() {
        return CrownArena.at(arenaCentre);
    }

    /** Set by the structure that places it. */
    public void setup(int index, int target, BlockPos arenaCentre) {
        this.index = index;
        this.target = Refraction.validTarget(index, target) ? target : Refraction.resting(index);
        this.arenaCentre = arenaCentre.immutable();
        changed();
    }

    /** The Colossus aims it (or settles it). */
    public void setTarget(int target) {
        if (Refraction.validTarget(index, target) && target != this.target) {
            this.target = target;
            changed();
        }
    }

    /** During a charge: hits on it turn it until {@code until}, and tell {@code owner}. */
    public void makeTurnable(long until, @Nullable UUID owner) {
        this.turnableUntil = until;
        this.owner = owner;
        this.lastSerial.clear();
    }

    public boolean turnable(long now) {
        return now < turnableUntil;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    /**
     * A hit at {@code now} by {@code attacker} ({@code serial}: its move, or -1 for a plain hit). Turns the crystal one
     * step clockwise if it is turnable and this is a new hit. Returns true if it turned.
     */
    public boolean tryTurn(long now, @Nullable UUID attacker, int serial) {
        if (!turnable(now) || (lastTurn != Long.MIN_VALUE && now - lastTurn < TURN_GAP_TICKS)) {
            return false;
        }
        if (attacker != null && serial >= 0) {
            Integer last = lastSerial.get(attacker);
            if (last != null && last == serial) {
                return false; // the same swing's next active tick
            }
            lastSerial.put(attacker, serial);
        }
        lastTurn = now;
        target = Refraction.turn(index, target);
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        index = Math.floorMod(tag.getInt("Index"), Refraction.CRYSTALS);
        int t = tag.contains("Target") ? tag.getInt("Target") : Refraction.resting(index);
        target = Refraction.validTarget(index, t) ? t : Refraction.resting(index);
        arenaCentre = NbtUtils.readBlockPos(tag, "Arena").orElse(BlockPos.ZERO);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Index", index);
        tag.putInt("Target", target);
        tag.put("Arena", NbtUtils.writeBlockPos(arenaCentre));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
