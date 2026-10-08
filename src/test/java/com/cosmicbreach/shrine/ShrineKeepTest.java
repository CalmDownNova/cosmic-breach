package com.cosmicbreach.shrine;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No death path may duplicate or lose an item (1.1 design section 9). The keep moves the kept stacks out of the
 * inventory at the death, so a grave or death-chest mod, whichever listener runs first, either takes a stack or sees
 * nothing: an item is in exactly one place at the end, whoever else handles the death.
 */
class ShrineKeepTest {
    /** A player's inventory: 36 main slots, 4 armor, 1 off hand. */
    private static final int SLOTS = 41;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SimpleContainer carrying() {
        SimpleContainer inv = new SimpleContainer(SLOTS);
        inv.setItem(0, new ItemStack(Items.DIAMOND, 37));
        inv.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        inv.setItem(5, new ItemStack(Items.COBBLESTONE, 64));
        inv.setItem(6, new ItemStack(Items.COBBLESTONE, 12));
        inv.setItem(36, new ItemStack(Items.IRON_BOOTS));
        inv.setItem(40, new ItemStack(Items.SHIELD));
        return inv;
    }

    private static Map<String, Integer> count(Container... where) {
        Map<String, Integer> out = new TreeMap<>();
        for (Container c : where) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    out.merge(s.getItem().toString(), s.getCount(), Integer::sum);
                }
            }
        }
        return out;
    }

    private static Container asContainer(List<ItemStack> stacks) {
        SimpleContainer c = new SimpleContainer(Math.max(1, stacks.size()));
        for (int i = 0; i < stacks.size(); i++) {
            c.setItem(i, stacks.get(i));
        }
        return c;
    }

    /** What a grave mod does: takes every stack out of the inventory into its grave. */
    private static Container grave(Container inv) {
        SimpleContainer grave = new SimpleContainer(SLOTS);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            grave.setItem(i, inv.getItem(i));
            inv.setItem(i, ItemStack.EMPTY);
        }
        return grave;
    }

    @Test
    void takingEverythingEmptiesTheInventoryAndPuttingBackRestoresEverySlot() {
        SimpleContainer inv = carrying();
        Map<String, Integer> before = count(inv);
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        assertEquals(6, kept.size());
        assertTrue(inv.isEmpty(), "nothing is left in the inventory for a drop to find");
        assertTrue(ShrineKeep.putBack(inv, kept).isEmpty());
        assertEquals(before, count(inv));
        assertEquals(37, inv.getItem(0).getCount());
        assertEquals(Items.IRON_BOOTS, inv.getItem(36).getItem());
        assertEquals(Items.SHIELD, inv.getItem(40).getItem());
    }

    @Test
    void aGraveThatRunsBeforeTheKeepTakesEverythingAndTheKeepHasNothingToDuplicate() {
        SimpleContainer inv = carrying();
        Map<String, Integer> before = count(inv);
        Container grave = grave(inv);
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        assertTrue(kept.isEmpty());
        ShrineKeep.putBack(inv, kept);
        assertEquals(before, count(inv, grave), "each item exactly once");
        assertTrue(inv.isEmpty());
    }

    @Test
    void aGraveThatRunsAfterTheKeepSeesAnEmptyInventoryAndTheKeepStillHasEverything() {
        SimpleContainer inv = carrying();
        Map<String, Integer> before = count(inv);
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        Container grave = grave(inv);
        assertTrue(grave.isEmpty());
        ShrineKeep.putBack(inv, kept);
        assertEquals(before, count(inv, grave), "each item exactly once");
    }

    @Test
    void whateverAnotherModAddsToTheDropsIsNeverTouched() {
        // the keep cancels no drop list: a worn backpack's contents (not in the inventory) drop as that mod decides
        SimpleContainer inv = carrying();
        List<ItemStack> otherModsDrops = new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 9)));
        Map<String, Integer> before = count(inv, asContainer(otherModsDrops));
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        ShrineKeep.putBack(inv, kept);
        assertEquals(before, count(inv, asContainer(otherModsDrops)));
        assertEquals(9, otherModsDrops.get(0).getCount());
    }

    @Test
    void aDeathAnotherModCancelsLosesNothing() {
        SimpleContainer inv = carrying();
        Map<String, Integer> before = count(inv);
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        // the player lives on: the keep hands the stacks straight back
        assertTrue(ShrineKeep.putBack(inv, kept).isEmpty());
        assertEquals(before, count(inv));
    }

    @Test
    void aSlotTakenInTheMeantimeIsNeverOverwrittenAndItsKeptStackIsReturnedForTheCaller() {
        SimpleContainer inv = carrying();
        List<ShrineKeep.Slot> kept = ShrineKeep.takeAll(inv);
        inv.setItem(0, new ItemStack(Items.APPLE, 3));
        List<ItemStack> homeless = ShrineKeep.putBack(inv, kept);
        assertEquals(1, homeless.size());
        assertEquals(Items.DIAMOND, homeless.get(0).getItem());
        assertEquals(37, homeless.get(0).getCount());
        assertEquals(Items.APPLE, inv.getItem(0).getItem(), "what stands in a slot stays");
    }

    @Test
    void aKeptStackIsATrueCopyAndTheRecordSurvivesASave() {
        SimpleContainer inv = new SimpleContainer(SLOTS);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Kept"));
        named.setDamageValue(12);
        inv.setItem(3, named);
        ShrineKeep.Kept kept = new ShrineKeep.Kept(ShrineKeep.takeAll(inv), 23, 0.5f, 700, 4);
        DataResult<Tag> saved = ShrineKeep.Kept.CODEC.encodeStart(NbtOps.INSTANCE, kept);
        ShrineKeep.Kept back = ShrineKeep.Kept.CODEC.parse(NbtOps.INSTANCE, saved.getOrThrow()).getOrThrow();
        assertEquals(23, back.level());
        assertEquals(700, back.total());
        assertEquals(1, back.slots().size());
        ShrineKeep.putBack(inv, back.slots());
        assertEquals(12, inv.getItem(3).getDamageValue());
        assertEquals("Kept", inv.getItem(3).get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void aSecondDeathBeforeTheHandBackKeepsTheFirstDeathsStacksToo() {
        // another mod cancelled a first death after ours and restored the player's health; before the tick that hands the
        // stacks back, a second lethal hit kills the player again: the inventory is empty, the first record still holds all
        SimpleContainer inv = carrying();
        Map<String, Integer> before = count(inv);
        List<ShrineKeep.Slot> first = ShrineKeep.takeAll(inv);
        assertTrue(inv.isEmpty());
        List<ShrineKeep.Slot> second = ShrineKeep.takeAll(inv, first);
        assertEquals(before, count(asContainer(second.stream().map(ShrineKeep.Slot::stack).toList())), "nothing of the first record is lost");
        assertTrue(ShrineKeep.putBack(inv, second).isEmpty(), "every stack still has a slot of its own");
        assertEquals(before, count(inv));
    }

    @Test
    void aSecondDeathWithNewItemsInTheSameSlotsKeepsBothSetsOnce() {
        SimpleContainer inv = carrying();
        List<ShrineKeep.Slot> first = ShrineKeep.takeAll(inv);
        inv.setItem(0, new ItemStack(Items.APPLE, 3));         // the same slot as the first record's diamonds
        inv.setItem(2, new ItemStack(Items.BREAD, 2));
        Map<String, Integer> expected = count(asContainer(first.stream().map(ShrineKeep.Slot::stack).toList()), inv);
        List<ShrineKeep.Slot> second = ShrineKeep.takeAll(inv, first);
        assertTrue(inv.isEmpty());
        assertEquals(expected, count(asContainer(second.stream().map(ShrineKeep.Slot::stack).toList())));
        List<ItemStack> homeless = ShrineKeep.putBack(inv, second);
        assertEquals(1, homeless.size(), "the first record's stack whose slot the new items took finds another place");
        assertEquals(Items.DIAMOND, homeless.get(0).getItem());
        assertEquals(Items.APPLE, inv.getItem(0).getItem());
    }

    @Test
    void aFirstDeathHasNothingEarlierToMerge() {
        SimpleContainer inv = carrying();
        assertEquals(6, ShrineKeep.takeAll(inv, List.of()).size());
    }
}
