package com.cosmicbreach.gear.forge;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.item.GearTier;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Astral Forge (GDD 3.5): Aetheria's one crafting station. Its tier (I to IV) is a block state; using the
 * next guardian relic on it raises it one tier (Prism Heart to II, Leviathan Pearl to III, Solar Heart to IV),
 * and another orbit ring appears around the anvil ({@code AstralForgeRenderer}). Using it otherwise opens the
 * Forge: every recipe, crafting from what the player carries, and reforging.
 */
public class AstralForgeBlock extends BaseEntityBlock {
    public static final MapCodec<AstralForgeBlock> CODEC = simpleCodec(AstralForgeBlock::new);
    public static final IntegerProperty TIER = IntegerProperty.create("tier", GearTier.MIN, GearTier.MAX);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Block events the entity renderer answers: a craft's flash, a new ring. */
    public static final int EVENT_CRAFTED = 1;
    public static final int EVENT_TIER_UP = 2;

    private static final VoxelShape BASE = Block.box(1, 0, 1, 15, 4, 15);
    private static final VoxelShape WAIST = Block.box(4, 4, 4, 12, 9, 12);
    private static final VoxelShape SHAPE_X = Shapes.or(BASE, WAIST, Block.box(1, 9, 4, 15, 13, 12));
    private static final VoxelShape SHAPE_Z = Shapes.or(BASE, WAIST, Block.box(4, 9, 1, 12, 13, 15));

    public AstralForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER, 1).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_Z : SHAPE_X;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AstralForgeBlockEntity(pos, state);
    }

    /** A relic raises the Forge (only the next one); anything else opens it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int relic = ForgeTiers.relicTier(item);
        if (relic == 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int tier = state.getValue(TIER);
        Optional<Integer> next = ForgeTiers.upgradeWith(tier, item);
        if (next.isEmpty()) {
            if (relic > tier + 1) {
                if (!level.isClientSide()) {
                    Component needed = ForgeTiers.upgradeItem(tier + 1)
                            .map(id -> BuiltInRegistries.ITEM.get(id).getDescription())
                            .orElse(Component.empty());
                    player.displayClientMessage(Component.translatable("message.cosmicbreach.forge.needs_first",
                            needed, GearTier.roman(tier + 1)), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            raise((ServerLevel) level, pos, state, next.get());
            stack.consume(1, player);
            player.displayClientMessage(Component.translatable("message.cosmicbreach.forge.tier_up",
                    GearTier.roman(next.get())).withColor(GearTier.color(next.get())), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new AstralForgeMenu(id, inventory, ContainerLevelAccess.create(level, pos), pos),
                    Component.translatable("container.cosmicbreach.astral_forge")), buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Sets the tier, with the sound, sparks and the new ring's growth for everyone near. */
    public static void raise(ServerLevel level, BlockPos pos, BlockState state, int tier) {
        level.setBlock(pos, state.setValue(TIER, GearTier.clamp(tier)), Block.UPDATE_ALL);
        level.blockEvent(pos, state.getBlock(), EVENT_TIER_UP, tier);
        level.playSound(null, pos, GearRegistry.FORGE_TIER_UP.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.35, 0.25, 0.35, 0.02);
    }

    // BaseEntityBlock.triggerEvent hands the block events to the block entity.
}
