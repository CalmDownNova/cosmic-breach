package com.cosmicbreach.mount;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Stable Crystal (1.1 design section 4): holds one tamed mod mount its owner puts in, with all of it (health,
 * saddle, barding, tack, name, owner). Use it on your mount to stow it; use it on a block to set it down beside you
 * (a mount that falls needs solid ground to stand on). Works for every mod mount (anything that is a {@link CelestialMount}).
 *
 * <p>A crystal holding a mount is never lost as an item, whatever happens to it in the world: that is {@link CrystalGuard}
 * (which is why a full one cannot be put inside a shulker box or a bundle, where the guard does not reach).
 */
public class StableCrystalItem extends Item {
    public StableCrystalItem(Properties properties) {
        super(properties);
    }

    /** The mount this crystal holds, or null if empty. */
    public static @Nullable StowedMount stowed(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(Stable.STOWED.get());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CelestialMount mount) || !mount.isAlive()) {
            return InteractionResult.PASS; // a dying mount is not rescued by a crystal: it would come out and finish dying
        }
        Level level = player.level();
        if (stowed(stack) != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("cosmicbreach.stable.full"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!mount.isTamed() || !player.getUUID().equals(mount.getOwnerUUID())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("cosmicbreach.stable.not_yours"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (mount.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            CompoundTag data = new CompoundTag();
            if (!mount.saveAsPassenger(data)) {
                return InteractionResult.PASS;
            }
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType());
            Optional<String> name = mount.hasCustomName() ? Optional.of(mount.getCustomName().getString()) : Optional.empty();
            player.getItemInHand(hand).set(Stable.STOWED.get(), new StowedMount(type, data, mount.getHealth(), mount.getMaxHealth(), name));
            level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), Mounts.PHASE_BLINK.get(), SoundSource.NEUTRAL, 0.8f, 1.3f);
            ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, mount.getX(), mount.getY() + 0.6, mount.getZ(), 20, 0.6, 0.4, 0.6, 0.02);
            mount.discard();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        StowedMount s = stowed(stack);
        if (s == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel server = (ServerLevel) level;
        Player player = context.getPlayer();
        Placed placed = place(server, s, context.getClickedPos().relative(context.getClickedFace()), player == null ? 0f : player.getYRot() + 180f);
        if (placed.mount() == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(placed.problem()), true);
            }
            return InteractionResult.FAIL; // the crystal keeps it
        }
        stack.remove(Stable.STOWED.get());
        Vec3 at = placed.mount().position();
        server.playSound(null, at.x, at.y, at.z, Mounts.TAMED.get(), SoundSource.NEUTRAL, 0.8f, 1.2f);
        server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6, at.z, 20, 0.6, 0.4, 0.6, 0.02);
        return InteractionResult.CONSUME;
    }

    /** The mount made from a crystal's data and set down, or the language key of what is wrong (the world is unchanged then). */
    record Placed(@Nullable CelestialMount mount, @Nullable String problem) {
    }

    /** Makes the mount {@code s} holds and sets it down near {@code at}, facing {@code yaw}. */
    static Placed place(ServerLevel level, StowedMount s, BlockPos at, float yaw) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.type());
        if (type.isEmpty()) {
            return new Placed(null, "cosmicbreach.stable.unknown");
        }
        Optional<Entity> made = EntityType.create(s.data(), level);
        // a crystal sets down only a mount of the kind it says it holds, whatever its data names
        if (made.isEmpty() || !(made.get() instanceof CelestialMount mount) || mount.getType() != type.get()) {
            return new Placed(null, "cosmicbreach.stable.unknown");
        }
        Vec3 spot = MountGround.releaseSpot(level, mount, at);
        if (spot == null) {
            return new Placed(null, mount.needsGround() ? "cosmicbreach.stable.no_ground" : "cosmicbreach.stable.no_room");
        }
        mount.forgetSafe(); // set down here, it stays here: not pulled back to where it stood before it was stowed
        mount.markPlaced(); // and not pulled up to its owner by the old-world rescue either, till it has a safe spot of its own
        mount.moveTo(spot.x, spot.y, spot.z, yaw, 0f);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.resetFallDistance();
        if (!level.addFreshEntity(mount)) {
            return new Placed(null, "cosmicbreach.stable.already_out"); // a copy is already in this level: the crystal keeps it
        }
        return new Placed(mount, null);
    }

    /** A full crystal stays out of shulker boxes and bundles (slots, hoppers and item handlers all ask here): the guard cannot reach it inside one. */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return CrystalFate.fitsInsideItems(stowed(stack) != null);
    }

    // ------------------------------------------------------------------ the item, lying in the world (CrystalGuard)

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        return CrystalGuard.onTick(stack, entity);
    }

    /** Destroyed by damage (a creative player's blow bypasses invulnerability): the mount is not lost with the item. */
    @Override
    public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        CrystalGuard.destroyed(itemEntity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        StowedMount s = stowed(stack);
        if (s == null) {
            lines.add(Component.translatable("item.cosmicbreach.stable_crystal.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component who = s.name().<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("entity." + s.type().getNamespace() + "." + s.type().getPath()));
        lines.add(Component.translatable("item.cosmicbreach.stable_crystal.holds", who, Math.round(s.health()), Math.round(s.maxHealth()))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.cosmicbreach.stable_crystal.release").withStyle(ChatFormatting.GRAY));
    }
}
