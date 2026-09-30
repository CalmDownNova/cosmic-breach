package com.cosmicbreach.guardian;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A guardian's altar (GDD 3.5, "Guardian respawns"): it stands where the lair's lift arrives, keeps the lair's
 * cooldown and its guardian ({@link GuardianAltarBlockEntity}), and takes a Guardian Echo to wake the guardian
 * again for another fight. Using it empty-handed tells the lair's state. Each guardian registers its own altar
 * block (the Prism Altar for the Colossus) with its {@link GuardianType}'s name. Indestructible.
 */
public class GuardianAltarBlock extends BaseEntityBlock {
    public static final MapCodec<GuardianAltarBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.STRING.fieldOf("guardian").forGetter(GuardianAltarBlock::guardianName),
            propertiesCodec()).apply(i, GuardianAltarBlock::new));

    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 11, 12),
            Block.box(2, 11, 2, 14, 14, 14));

    private final String guardian;

    public GuardianAltarBlock(String guardian, Properties properties) {
        super(properties);
        this.guardian = guardian;
    }

    public String guardianName() {
        return guardian;
    }

    public GuardianType type() {
        return GuardianType.byName(guardian).orElseThrow(() -> new IllegalStateException("no guardian " + guardian));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardianAltarBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GuardianRegistry.ALTAR_ENTITY.get(), GuardianAltarBlockEntity::serverTick);
    }

    /** A Guardian Echo re-arms the lair's guardian (after its cooldown) and is used up. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(GuardianRegistry.GUARDIAN_ECHO.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (altar.useEcho(serverPlayer)) {
                stack.consume(1, player);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar) {
            Component status = altar.status();
            player.displayClientMessage(status, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
