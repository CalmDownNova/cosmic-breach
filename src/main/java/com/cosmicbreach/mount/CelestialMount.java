package com.cosmicbreach.mount;

import com.cosmicbreach.mixin.world.LivingJumpingAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * What the two celestial mounts share (GDD 8.1): the vanilla horse inventory with its saddle and armor slots plus one
 * tack slot ({@link MountMenu}), their own saddle, barding and tack ({@link MountGear}), riding only once saddled,
 * taming their own way (no riding it until it gives in, no breeding, no rearing), and GeckoLib.
 *
 * <p>The tack is synced ({@link #tackItem}) because the rider's client flies and jumps the mount and needs to know
 * what it wears. A rider's held jump is read through {@link #riderJumping}.
 */
public abstract class CelestialMount extends AbstractHorse implements GeoEntity {
    private static final EntityDataAccessor<ItemStack> DATA_TACK = SynchedEntityData.defineId(CelestialMount.class,
            EntityDataSerializers.ITEM_STACK);

    private final SimpleContainer tack = new SimpleContainer(1);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected CelestialMount(EntityType<? extends CelestialMount> type, Level level) {
        super(type, level);
        tack.addListener(container -> {
            if (!level().isClientSide) {
                entityData.set(DATA_TACK, tack.getItem(0).copy());
            }
        });
    }

    /** Which mount this is. */
    public abstract MountGear.Kind kind();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TACK, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ gear

    /** The tack worn, on either side. */
    public ItemStack tackItem() {
        return entityData.get(DATA_TACK);
    }

    /** True if the tack worn is {@code gear}. */
    public boolean wears(MountGear gear) {
        return MountGear.of(tackItem()) == gear;
    }

    /** The saddle worn (the Astral Saddle or the Drift Harness), server side; empty for none. */
    public ItemStack saddleItem() {
        return inventory.getItem(0);
    }

    public SimpleContainer tackContainer() {
        return tack;
    }

    public boolean isSaddleItem(ItemStack stack) {
        MountGear g = MountGear.of(stack);
        return g != null && g.slot() == MountGear.Slot.SADDLE && g.fits(kind());
    }

    public boolean isTackItem(ItemStack stack) {
        MountGear g = MountGear.of(stack);
        return g != null && g.slot() == MountGear.Slot.TACK && g.fits(kind());
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        MountGear g = MountGear.of(stack);
        return g != null && g.slot() == MountGear.Slot.ARMOR && g.fits(kind());
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    // ------------------------------------------------------------------ what a horse does that these don't

    @Override
    public boolean canJump() {
        return false; // no charged horse jump: each mount moves its own way while ridden
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public int getInventoryColumns() {
        return 0;
    }

    @Override
    protected void randomizeAttributes(net.minecraft.util.RandomSource random) {
        // fixed stats (GDD 8.1), no horse-style spread
    }

    /** No foals: a horse's spawn makes one in five a baby; these are always grown. */
    @Override
    public @Nullable net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType type,
            @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        return super.finalizeSpawn(level, difficulty, type, data == null ? new AgeableMob.AgeableMobGroupData(false) : data);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isVehicle()) {
            return super.mobInteract(player, hand);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!isTamed()) {
            return wildInteract(player, hand, stack);
        }
        boolean client = level().isClientSide;
        if (player.isSecondaryUseActive()) {
            openCustomInventoryScreen(player);
            return InteractionResult.sidedSuccess(client);
        }
        if (!stack.isEmpty()) {
            if (isSaddleItem(stack) && !isSaddled() && isSaddleable()) {
                if (!client) {
                    equipSaddle(stack.split(1), SoundSource.NEUTRAL);
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (isBodyArmorItem(stack) && !isWearingBodyArmor()) {
                equipBodyArmor(player, stack);
                playSound(SoundEvents.HORSE_ARMOR, 0.5f, 1.2f);
                return InteractionResult.sidedSuccess(client);
            }
            if (isTackItem(stack) && tackItem().isEmpty()) {
                if (!client) {
                    tack.setItem(0, stack.copyWithCount(1));
                    stack.consume(1, player);
                    playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6f, 1.2f);
                }
                return InteractionResult.sidedSuccess(client);
            }
            InteractionResult r = stack.interactLivingEntity(player, this, hand);
            if (r.consumesAction()) {
                return r;
            }
        }
        if (!isSaddled()) {
            if (!client) {
                player.displayClientMessage(Component.translatable("cosmicbreach.mount.needs_saddle." + kind().id()), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        doPlayerRide(player);
        return InteractionResult.sidedSuccess(client);
    }

    /** A right click on this mount while it is still wild. */
    protected abstract InteractionResult wildInteract(Player player, InteractionHand hand, ItemStack stack);

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!level().isClientSide && (!isVehicle() || hasPassenger(player)) && isTamed()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MountMenu(id, inventory, this), getDisplayName()),
                    buf -> buf.writeVarInt(getId()));
        }
    }

    /** Tames it to {@code player}: owner, the tamed flag, hearts. */
    public void tameTo(Player player) {
        tameWithName(player);
        setPersistenceRequired();
    }

    // ------------------------------------------------------------------ the rider

    /** True while the controlling rider holds jump (their input, synced to the server every tick while riding). */
    public boolean riderJumping() {
        LivingEntity rider = getControllingPassenger();
        return rider != null && ((LivingJumpingAccessor) rider).cosmicbreach$jumping();
    }

    // ------------------------------------------------------------------ care (1.1): the last safe spot and the climb back

    private @Nullable Vec3 lastSafe;
    private boolean rescuing;
    private int rescueStuck;
    private double rescueBest = Double.MAX_VALUE;
    /** True if the last rescue ended by the stuck rule (set down at the spot after 3 s without gaining ground), not by arriving. */
    private boolean rescueSetDown;
    /** True for a mount a Stable Crystal set down and that has not yet recorded a safe spot of its own: the old-world rescue leaves it where it was put. */
    private boolean placed;

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            MountCare.tick(this);
        }
    }

    /** Where it last was safe (a stag on its islands, a stingray in the Drift's air), or null if never seen safe. */
    public @Nullable Vec3 lastSafe() {
        return lastSafe;
    }

    void rememberSafe(Vec3 at) {
        lastSafe = at;
    }

    /**
     * Forgets its safe spot and any rescue under way: a mount just set down from a Stable Crystal stays where the player
     * put it, and is not pulled back to where it stood before it was stowed.
     */
    public void forgetSafe() {
        lastSafe = null;
        endRescue();
    }

    /** True if the last rescue ended by the stuck rule (a set down after 3 s without gaining ground) and not by arriving. */
    public boolean rescueEndedStuck() {
        return rescueSetDown;
    }

    /** True if a Stable Crystal set this mount down and it has not recorded a safe spot of its own since. */
    public boolean placedByCrystal() {
        return placed;
    }

    void markPlaced() {
        placed = true;
    }

    void clearPlaced() {
        placed = false;
    }

    /** True while it climbs back to its last safe spot. */
    public boolean isRescuing() {
        return rescuing;
    }

    void startRescue() {
        rescuing = true;
        rescueStuck = 0;
        rescueBest = Double.MAX_VALUE;
        rescueSetDown = false;
        setNoGravity(true);
    }

    void endRescue() {
        if (!rescuing) {
            return;
        }
        rescuing = false;
        setNoGravity(keepsNoGravity());
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
    }

    /** True for a mount that never falls (the stingray holds itself up). */
    protected boolean keepsNoGravity() {
        return false;
    }

    /** True for a mount that falls when nothing holds it up (a stag): it needs solid ground wherever it is put or sent. */
    public boolean needsGround() {
        return !keepsNoGravity();
    }

    /**
     * One server tick of the climb back: straight up, then across a block above the spot's height (so a spot on a rock's top
     * is reached over its edge, not through its side). A climb that gains no ground for 3 s ends with the mount set down at
     * the spot ({@link #rescueEndedStuck}): a little above it if the spot has been built over since, and not at all while
     * there is no room, so it is never set down inside blocks.
     */
    void rescueStep(Vec3 safe) {
        double d = position().distanceTo(safe);
        if (d < rescueBest - 0.05) {
            rescueBest = d;
            rescueStuck = 0;
        } else if (++rescueStuck >= MountCareRules.STUCK_TICKS) {
            Vec3 at = setDownSpot(safe);
            if (at != null) {
                teleportTo(at.x, at.y, at.z);
                rescueSetDown = true;
                endRescue();
                return;
            }
            rescueStuck = 0; // no room at the spot: it keeps pressing on, and is set down once there is
        }
        setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, MountCareRules.rescueVelocity(position(), safe, MountCareRules.CLEARANCE));
        resetFallDistance();
    }

    /**
     * The spot, or the first place up to 3 blocks above it where this mount's box fits clear of blocks; null if there is none.
     * A mount that falls is set down only where it can stand: at the spot if it has ground, else on the ground close to it.
     */
    private @Nullable Vec3 setDownSpot(Vec3 safe) {
        if (needsGround()) {
            return MountGround.standsAt(level(), this, safe) ? safe
                    : MountGround.groundNear(level(), getType(), BlockPos.containing(safe), MountGround.LEDGE_UP, MountGround.LEDGE_DOWN);
        }
        for (int up = 0; up <= 3; up++) {
            Vec3 at = safe.add(0, up, 0);
            if (level().noCollision(this, getBoundingBox().move(at.subtract(position())))) {
                return at;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); // writes the saddle as "SaddleItem", whatever it is
        if (!tack.getItem(0).isEmpty()) {
            tag.put("MountTack", tack.getItem(0).save(registryAccess()));
        }
        if (lastSafe != null) {
            tag.putDouble("CareSafeX", lastSafe.x);
            tag.putDouble("CareSafeY", lastSafe.y);
            tag.putDouble("CareSafeZ", lastSafe.z);
        }
        tag.putBoolean("CareRescue", rescuing);
        tag.putBoolean("CarePlaced", placed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); // restores only a vanilla saddle, so ours is read here
        if (tag.contains("SaddleItem", 10)) {
            ItemStack saddle = ItemStack.parse(registryAccess(), tag.getCompound("SaddleItem")).orElse(ItemStack.EMPTY);
            if (isSaddleItem(saddle)) {
                inventory.setItem(0, saddle);
            }
        }
        if (tag.contains("MountTack", 10)) {
            tack.setItem(0, ItemStack.parse(registryAccess(), tag.getCompound("MountTack")).orElse(ItemStack.EMPTY));
        }
        if (tag.contains("CareSafeY")) {
            lastSafe = new Vec3(tag.getDouble("CareSafeX"), tag.getDouble("CareSafeY"), tag.getDouble("CareSafeZ"));
        }
        if (tag.getBoolean("CareRescue")) {
            rescuing = true;
            setNoGravity(true);
        }
        placed = tag.getBoolean("CarePlaced");
        syncSaddleToClients();
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (!tack.getItem(0).isEmpty()) {
            spawnAtLocation(tack.removeItemNoUpdate(0));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
