package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.KineticTripwires;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Crushing Gravity Plate (GDD 6.4, {@link GravityPlateRules}), run from its piston in the ceiling. A player stepping
 * onto the sigil ({@link CryptTraps}) starts a cycle if the plate is quiet, and turns heavy for 60 ticks; on the
 * cycle's tick 40 the piston slams onto the sigil: 10 damage and a stagger to anyone still inside. Its hum reaches
 * careful players within 6 blocks, every two seconds. The cycle's start is synced; the client draws the slam.
 */
public class GravityPistonBlockEntity extends BlockEntity {
    /** Blocks from this block's bottom face down to the sigil's surface. */
    private int drop = 6;
    private long cycleStart = -1;
    /** For tests: {cycle start, slam tick, entities hit}. */
    private final long[] last = {-1, -1, 0};

    public GravityPistonBlockEntity(BlockPos pos, BlockState state) {
        super(CryptRegistry.GRAVITY_PISTON_ENTITY.get(), pos, state);
    }

    public void configure(int drop) {
        this.drop = drop;
        setChanged();
    }

    public int drop() {
        return drop;
    }

    public long cycleStart() {
        return cycleStart;
    }

    public long[] timeline() {
        return last.clone();
    }

    /** The sigil's middle tile. */
    public BlockPos sigil() {
        return worldPosition.below(drop + 1);
    }

    /** The space over the sigil the piston crushes: 5 by 5, from the sigil to the ceiling. */
    public AABB crushBox() {
        return new AABB(worldPosition.getX() - 2, worldPosition.getY() - drop, worldPosition.getZ() - 2,
                worldPosition.getX() + 3, worldPosition.getY(), worldPosition.getZ() + 3);
    }

    /** {@code player} stepped onto the sigil on tick {@code now}. */
    void stepOn(ServerLevel level, ServerPlayer player, long now) {
        if (GravityPlateRules.rearmed(cycleStart, now)) {
            cycleStart = now;
            last[0] = now;
            last[1] = -1;
            last[2] = 0;
            level.playSound(null, sigil().above(), CryptRegistry.PLATE_HUM.get(), SoundSource.BLOCKS, 1.0f, 0.7f);
            sync();
        }
        if (cycleStart >= 0 && now < cycleStart + GravityPlateRules.HEAVY_TICKS) {
            CryptTraps.makeHeavy(player, now);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityPistonBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();
        if (GravityPlateRules.slams(be.cycleStart, now)) {
            be.slam(server, now);
        }
        if ((now + pos.asLong()) % 40 == 0) {
            be.hum(server);
        }
    }

    private void slam(ServerLevel level, long now) {
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, crushBox(), LivingEntity::isAlive)) {
            if (e instanceof Player p && (p.isSpectator() || p.isCreative())) {
                continue;
            }
            e.hurt(level.damageSources().source(CryptRegistry.GRAVITY_PISTON), GravityPlateRules.PISTON_DAMAGE);
            PoiseTracker.addImpact(e, PoiseTracker.poiseOf(e) + 1.0);
            hit++;
        }
        last[1] = now;
        last[2] = hit;
        BlockPos s = sigil();
        level.playSound(null, s.above(), CryptRegistry.PISTON_SLAM.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
        level.sendParticles(ParticleTypes.CLOUD, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 30, 2.0, 0.1, 2.0, 0.02);
    }

    /** The ceiling's hum, to each careful player within {@link GravityPlateRules#HUM_RADIUS} blocks of the sigil. */
    private void hum(ServerLevel level) {
        Vec3 at = Vec3.atCenterOf(sigil()).add(0, drop, 0);
        Vec3 floor = Vec3.atCenterOf(sigil());
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - floor.x;
            double dz = p.getZ() - floor.z;
            if (Math.abs(p.getY() - (floor.y + 0.5)) > 3 || Math.hypot(dx, dz) > com.cosmicbreach.accessory.TrapSight.radius(p, GravityPlateRules.HUM_RADIUS)
                    || !KineticRules.careful(p.isSprinting(), KineticTripwires.speedOf(p)) && !com.cosmicbreach.accessory.TrapSight.anySpeed(p)) {
                continue;
            }
            p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(CryptRegistry.PLATE_HUM.get()),
                    SoundSource.BLOCKS, at.x, at.y, at.z, 0.6f, 1.0f, level.random.nextLong()));
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("drop", drop);
        tag.putLong("cycle", cycleStart);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drop = tag.contains("drop") ? tag.getInt("drop") : 6;
        cycleStart = tag.contains("cycle") ? tag.getLong("cycle") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("drop", drop);
        tag.putLong("cycle", cycleStart);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
