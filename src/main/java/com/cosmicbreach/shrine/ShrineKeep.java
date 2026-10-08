package com.cosmicbreach.shrine;

import com.cosmicbreach.world.AetheriaWorld;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * While a player's respawn is a shrine, a death anywhere in Aetheria keeps everything (1.1 design section 9): every
 * inventory slot (armor and off hand too), the curios and the experience come back with them at the shrine. Rules in
 * {@link ShrineRules}; the order of the death's events is in the plan's section 1.
 *
 * <p>How it avoids copies beside other mods that handle a death (graves, death chests, soulbound items): at the death the
 * kept stacks are <em>moved</em> out of the inventory into the player's record, so whatever drops or collects the
 * inventory afterwards finds nothing of ours, and whatever took a stack before us left us nothing to keep. The drop
 * list itself is never cancelled, so what other mods add to it (a backpack's contents, say) still drops as they decide.
 * If another mod cancels the death after ours, the stacks go straight back into the inventory. Like vanilla's
 * keepInventory, the keep also keeps items cursed with vanishing.
 */
public final class ShrineKeep {
    /** One kept slot. */
    public record Slot(int index, ItemStack stack) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Slot::index),
                ItemStack.CODEC.fieldOf("item").forGetter(Slot::stack)).apply(i, Slot::new));
    }

    /** What a death kept: the non-empty slots and the experience. */
    public record Kept(List<Slot> slots, int level, float progress, int total, int score) {
        public static final Kept NONE = new Kept(List.of(), 0, 0f, 0, 0);
        public static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                Slot.CODEC.listOf().fieldOf("slots").forGetter(Kept::slots),
                Codec.INT.fieldOf("level").forGetter(Kept::level),
                Codec.FLOAT.fieldOf("progress").forGetter(Kept::progress),
                Codec.INT.fieldOf("total").forGetter(Kept::total),
                Codec.INT.fieldOf("score").forGetter(Kept::score)).apply(i, Kept::new));

        /**
         * Moves everything the player carries into a record (the inventory is left empty) with the experience. {@code earlier}
         * is a record the player still holds from a death another mod cancelled: its stacks stay in the new record.
         */
        static Kept capture(ServerPlayer p, Kept earlier) {
            return new Kept(takeAll(p.getInventory(), earlier == null ? List.of() : earlier.slots()),
                    p.experienceLevel, p.experienceProgress, p.totalExperience, p.getScore());
        }

        /** The stacks and the experience, for the respawned player. */
        void restore(ServerPlayer p) {
            returnItems(p);
            p.experienceLevel = level;
            p.experienceProgress = progress;
            p.totalExperience = total;
            p.setScore(score);
        }

        /** The stacks only: each to its slot, a stack whose slot is taken to wherever it fits (or drops at the player's feet). */
        void returnItems(ServerPlayer p) {
            for (ItemStack homeless : putBack(p.getInventory(), slots)) {
                p.getInventory().placeItemBackInInventory(homeless);
            }
        }
    }

    /** Moves every non-empty stack out of {@code inventory}, leaving it empty; each stack is the very stack that stood there. */
    static List<Slot> takeAll(Container inventory) {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) {
                slots.add(new Slot(i, s));
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return List.copyOf(slots);
    }

    /**
     * {@link #takeAll(Container)} for a player who still holds {@code earlier}, the stacks of a death another mod cancelled
     * after ours (the tick that hands them back has not run): they join the new record, so a second death in between cannot
     * replace them with an empty one. A stack whose slot the new items took gets no slot (-1) and is placed wherever it fits.
     */
    static List<Slot> takeAll(Container inventory, List<Slot> earlier) {
        List<Slot> now = takeAll(inventory);
        if (earlier.isEmpty()) {
            return now;
        }
        Set<Integer> used = new java.util.HashSet<>();
        now.forEach(s -> used.add(s.index()));
        List<Slot> all = new ArrayList<>(now);
        for (Slot s : earlier) {
            all.add(used.add(s.index()) ? s : new Slot(-1, s.stack()));
        }
        return List.copyOf(all);
    }

    /**
     * Puts each kept stack back in its own slot, never over a stack that stands there now; returns the stacks that could
     * not go (their slot is taken or gone) for the caller to place elsewhere.
     */
    static List<ItemStack> putBack(Container inventory, List<Slot> slots) {
        List<ItemStack> homeless = new ArrayList<>();
        for (Slot s : slots) {
            if (s.index() >= 0 && s.index() < inventory.getContainerSize() && inventory.getItem(s.index()).isEmpty()) {
                inventory.setItem(s.index(), s.stack().copy());
            } else {
                homeless.add(s.stack().copy());
            }
        }
        return homeless;
    }

    private static final Set<UUID> JUST_KEPT = ConcurrentHashMap.newKeySet();

    private ShrineKeep() {
    }

    static void register(IEventBus game) {
        game.addListener(EventPriority.LOWEST, LivingDeathEvent.class, ShrineKeep::onDeath);
        game.addListener(EventPriority.HIGHEST, LivingExperienceDropEvent.class, ShrineKeep::onExperience);
        game.addListener(DropRulesEvent.class, ShrineKeep::onCurioRules);
        game.addListener(PlayerEvent.Clone.class, ShrineKeep::onClone);
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, ShrineKeep::onRespawn);
        game.addListener(PlayerTickEvent.Post.class, ShrineKeep::onTick);
    }

    private static boolean keeping(Entity e) {
        return e instanceof ServerPlayer p && p.hasData(ShrineRegistry.KEPT);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        boolean keepRule = p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (ShrineRules.keeps(AetheriaWorld.is(p.level()), ShrineSave.active(p), keepRule, p.isSpectator())) {
            p.setData(ShrineRegistry.KEPT, Kept.capture(p, p.getExistingDataOrNull(ShrineRegistry.KEPT)));
        }
    }

    private static void onExperience(LivingExperienceDropEvent event) {
        if (keeping(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onCurioRules(DropRulesEvent event) {
        if (keeping(event.getEntity())) {
            event.addOverride(stack -> true, ICurio.DropRule.ALWAYS_KEEP);
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer fresh)) {
            return;
        }
        Kept kept = event.getOriginal().getExistingDataOrNull(ShrineRegistry.KEPT);
        if (kept != null) {
            kept.restore(fresh);
            JUST_KEPT.add(fresh.getUUID());
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && JUST_KEPT.remove(p.getUUID())) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ShrineRegistry.KEPT_SOUND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            p.displayClientMessage(Component.translatable("cosmicbreach.shrine.kept"), true);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        // a death another mod cancelled after ours: the player lives on, so everything goes straight back
        if (event.getEntity() instanceof ServerPlayer p && p.isAlive() && p.hasData(ShrineRegistry.KEPT)) {
            p.getData(ShrineRegistry.KEPT).returnItems(p);
            p.removeData(ShrineRegistry.KEPT);
        }
    }
}
