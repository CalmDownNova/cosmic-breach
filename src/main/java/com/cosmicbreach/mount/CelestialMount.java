package com.cosmicbreach.mount;

import com.cosmicbreach.mixin.world.LivingJumpingAccessor;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); // writes the saddle as "SaddleItem", whatever it is
        if (!tack.getItem(0).isEmpty()) {
            tag.put("MountTack", tack.getItem(0).save(registryAccess()));
        }
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
