package com.cosmicbreach.astrolabe;

import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Parallax's decoy star (GDD 4.2, the Astrolabe's dash attack): left where the dash attack was made, it fires
 * {@code bolts} star bolts a few ticks apart at whatever its owner's crosshair is on (else along the owner's aim), then
 * fades. Each bolt carries the Parallax's motion value. Never saved.
 */
public class ParallaxDecoy extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ParallaxDecoy.class, EntityDataSerializers.INT);

    // server
    private @Nullable UUID ownerId;
    private @Nullable MoveInstance move;
    private @Nullable WeaponDef weapon;
    private ItemStack weaponStack = ItemStack.EMPTY;
    private int bolts = 2;
    private int first = 3;
    private int gap = 5;
    private int life = 14;
    private int fired;
    private final List<Integer> boltIds = new ArrayList<>();

    public ParallaxDecoy(EntityType<? extends ParallaxDecoy> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public ParallaxDecoy(Level level, ServerPlayer owner, Vec3 at) {
        this(Astrolabes.PARALLAX_DECOY.get(), level);
        setPos(at.x, at.y, at.z);
        ownerId = owner.getUUID();
        entityData.set(OWNER, owner.getId());
    }

    public ParallaxDecoy configure(MoveInstance move, WeaponDef weapon, ItemStack weaponStack, int bolts, int first, int gap, int life) {
        this.move = move;
        this.weapon = weapon;
        this.weaponStack = weaponStack;
        this.bolts = bolts;
        this.first = first;
        this.gap = Math.max(1, gap);
        this.life = life;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER);
    }

    /** Server: the bolts it fired so far. */
    public List<Integer> boltIds() {
        return List.copyOf(boltIds);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ServerPlayer owner = ownerId == null ? null
                : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != level() || move == null || weapon == null) {
            discard();
            return;
        }
        if (fired < bolts && tickCount >= first + fired * gap) {
            fire(owner);
        }
        if (tickCount >= life) {
            discard();
        }
    }

    private void fire(ServerPlayer owner) {
        Vec3 from = position();
        Vec3 aim = AstrolabeEffects.aimPoint(owner, Homing.RANGE);
        LivingEntity marked = AstrolabeEffects.underCrosshair(owner, Homing.RANGE, Homing.CONE);
        Vec3 to = marked != null ? marked.position().add(0, marked.getBbHeight() * 0.55, 0) : aim;
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-6) {
            dir = owner.getLookAngle();
        }
        StarBolt bolt = new StarBolt(level(), owner, from, dir.normalize().scale(Homing.SPEED), StarBolt.DECOY)
                .configure(move, weapon, weaponStack, move.mv(), move.def().hit().impact(), Homing.RANGE, Homing.CONE, Homing.TURN, 0.0);
        level().addFreshEntity(bolt);
        boltIds.add(bolt.getId());
        ServerCombatSounds.forEveryone(level(), from, Astrolabes.CHIME, 0.7f, AstrolabeTunes.pitch(AstrolabeTunes.PARALLAX[
                Math.min(fired, AstrolabeTunes.PARALLAX.length - 1)]));
        fired++;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
