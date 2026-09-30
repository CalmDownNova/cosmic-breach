package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.KineticTripwires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Starfall Chute (GDD 6.4, {@link ChuteRules}): every {@code period} ticks, at its {@code phase} on Vesper's clock,
 * it drops a row of star-rocks across the corridor; they land {@link ChuteRules#FALL_TICKS} ticks later, 8 damage to
 * whoever is under the strip. The glint before each drop (its sound to careful players, its sparkle drawn by the
 * client for careful players) is the tell. It keeps time only while a player is within 32 blocks.
 */
public class StarfallChuteBlockEntity extends BlockEntity {
    private int period = 48;
    private int phase;
    /** Blocks from this block's bottom face down to the floor's surface. */
    private int drop = 6;
    private boolean active;
    /** For tests: {last landing tick, entities hit, damage dealt}. */
    private final long[] last = {-1, 0, 0};

    public StarfallChuteBlockEntity(BlockPos pos, BlockState state) {
        super(CryptRegistry.STARFALL_CHUTE_ENTITY.get(), pos, state);
    }

    public void configure(int period, int phase, int drop) {
        this.period = period;
        this.phase = phase;
        this.drop = drop;
        setChanged();
    }

    public int period() {
        return period;
    }

    public int phase() {
        return phase;
    }

    public int drop() {
        return drop;
    }

    public long[] timeline() {
        return last.clone();
    }

    public Direction.Axis axis() {
        return getBlockState().getValue(StarfallChuteBlock.AXIS);
    }

    /** The strip of floor the rocks land on: three blocks along the axis, one across, 2 blocks tall. */
    public AABB strip() {
        Direction.Axis a = axis();
        int ax = a == Direction.Axis.X ? 1 : 0;
        int az = a == Direction.Axis.Z ? 1 : 0;
        double floor = worldPosition.getY() - drop;
        return new AABB(worldPosition.getX() - ax, floor, worldPosition.getZ() - az, worldPosition.getX() + 1 + ax, floor + 2.0,
                worldPosition.getZ() + 1 + az);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StarfallChuteBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();
        if ((now + pos.asLong()) % 20 == 0) {
            be.active = server.getNearestPlayer(pos.getX() + 0.5, pos.getY() - be.drop, pos.getZ() + 0.5, 32, false) != null;
        }
        if (!be.active) {
            return;
        }
        if (ChuteRules.untilRelease(now, be.period, be.phase) == ChuteRules.GLINT_LEAD) {
            be.glint(server);
        }
        if (ChuteRules.lands(now, be.period, be.phase)) {
            be.land(server, now);
        }
    }

    private void land(ServerLevel level, long now) {
        AABB strip = strip();
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, strip, LivingEntity::isAlive)) {
            if (e instanceof Player p && (p.isSpectator() || p.isCreative())) {
                continue;
            }
            if (e.hurt(level.damageSources().source(CryptRegistry.STAR_ROCK), ChuteRules.DAMAGE)) {
                hit++;
            }
        }
        last[0] = now;
        last[1] = hit;
        last[2] += hit * (long) ChuteRules.DAMAGE;
        double y = strip.minY + 0.1;
        level.sendParticles(ParticleTypes.CRIT, (strip.minX + strip.maxX) / 2, y, (strip.minZ + strip.maxZ) / 2, 18,
                (strip.maxX - strip.minX) / 3, 0.1, (strip.maxZ - strip.minZ) / 3, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, (strip.minX + strip.maxX) / 2, y + 0.2, (strip.minZ + strip.maxZ) / 2, 6,
                (strip.maxX - strip.minX) / 3, 0.1, (strip.maxZ - strip.minZ) / 3, 0.05);
        level.playSound(null, worldPosition.below(drop), CryptRegistry.CHUTE_IMPACT.get(), SoundSource.BLOCKS, 1.0f,
                0.9f + level.random.nextFloat() * 0.2f);
    }

    /** The glint's sound, for careful players near the strip. */
    private void glint(ServerLevel level) {
        AABB strip = strip();
        double cx = (strip.minX + strip.maxX) / 2;
        double cz = (strip.minZ + strip.maxZ) / 2;
        for (ServerPlayer p : level.players()) {
            if (Math.abs(p.getY() - strip.minY) > 3 || Math.hypot(p.getX() - cx, p.getZ() - cz) > com.cosmicbreach.accessory.TrapSight.radius(p, ChuteRules.REVEAL_RADIUS) + 1.5
                    || !KineticRules.careful(p.isSprinting(), KineticTripwires.speedOf(p)) && !com.cosmicbreach.accessory.TrapSight.anySpeed(p)) {
                continue;
            }
            p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(CryptRegistry.CHUTE_GLINT.get()),
                    SoundSource.BLOCKS, worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, 0.7f, 1.0f,
                    level.random.nextLong()));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("period", period);
        tag.putInt("phase", phase);
        tag.putInt("drop", drop);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        period = tag.contains("period") ? Math.max(12, tag.getInt("period")) : 48;
        phase = tag.getInt("phase");
        drop = tag.contains("drop") ? tag.getInt("drop") : 6;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
