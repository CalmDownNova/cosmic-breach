package com.cosmicbreach.mount;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Resonance Chime (Forge II, GDD 8.1): near a wild Drift Manta a use starts its call; while it calls, each use
 * answers with its next note, judged on the server's tick ({@link DriftManta#onChime}). Anywhere else it just rings.
 */
public class ResonanceChimeItem extends Item {
    public ResonanceChimeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server && !Mounts.chime(server)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), Mounts.CHIME.get(), SoundSource.PLAYERS, 0.7f,
                    DriftManta.pitchOf(player.getRandom().nextInt(MantaCall.NOTE_PADS)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach.resonance_chime.desc").withStyle(ChatFormatting.GRAY));
    }
}
