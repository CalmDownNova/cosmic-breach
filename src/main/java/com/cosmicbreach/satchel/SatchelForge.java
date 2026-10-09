package com.cosmicbreach.satchel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Satchel as the Forge sees it (1.2 design section 3): its materials and its Gear tab as extra stacks to craft from.
 * {@link #view} lists them as copies (a material type as one stack with its whole count, then each piece of gear); the
 * Forge shrinks those copies while it takes its ingredients, and {@link #commit} turns what was taken into removals from the
 * Satchel as it is at that moment, so a material is only ever removed in the amount that was taken and a piece of gear only
 * if it is still in the slot it was taken from.
 */
public final class SatchelForge {
    /** The virtual stacks and what they were made from. */
    public record View(List<ItemStack> stacks, List<ResourceLocation> materialIds, List<Integer> gearSlots, SatchelContents source) {
        public static final View NONE = new View(List.of(), List.of(), List.of(), SatchelContents.EMPTY);
    }

    private SatchelForge() {
    }

    public static View view(Player player) {
        return SatchelLocator.contentsOf(player).map(SatchelForge::of).orElse(View.NONE);
    }

    static View of(SatchelContents c) {
        List<ItemStack> stacks = new ArrayList<>();
        List<ResourceLocation> ids = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        c.materials().forEach((id, n) -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (n > 0 && item != net.minecraft.world.item.Items.AIR) {
                stacks.add(new ItemStack(item, n));
                ids.add(id);
            }
        });
        for (int s = 0; s < SatchelContents.GEAR_SLOTS; s++) {
            if (!c.gear().get(s).isEmpty()) {
                stacks.add(c.gear().get(s).copy());
                slots.add(s);
            }
        }
        return new View(stacks, ids, slots, c);
    }

    /** Removes from the Satchel what the Forge took out of the view's stacks. */
    public static void commit(Player player, View view) {
        if (view.stacks().isEmpty()) {
            return;
        }
        SatchelLocator.update(player, current -> apply(current, view));
    }

    /** The contents after removing what was taken from the view, pure. */
    static SatchelContents apply(SatchelContents current, View view) {
        SatchelContents out = current;
        Map<ResourceLocation, Integer> taken = new LinkedHashMap<>();
        for (int i = 0; i < view.materialIds().size(); i++) {
            ResourceLocation id = view.materialIds().get(i);
            int left = view.stacks().get(i).getCount();
            taken.put(id, view.source().count(id) - Math.max(0, left));
        }
        for (Map.Entry<ResourceLocation, Integer> e : taken.entrySet()) {
            if (e.getValue() > 0) {
                out = out.withdraw(e.getKey(), e.getValue()).contents();
            }
        }
        int base = view.materialIds().size();
        for (int g = 0; g < view.gearSlots().size(); g++) {
            ItemStack left = view.stacks().get(base + g);
            int slot = view.gearSlots().get(g);
            ItemStack was = view.source().gear().get(slot);
            if (left.getCount() < was.getCount() && ItemStack.matches(out.gear().get(slot), was)) {
                out = out.withGear(slot, left.isEmpty() ? ItemStack.EMPTY : left);
            }
        }
        return out;
    }
}
