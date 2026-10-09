package com.cosmicbreach.satchel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Forge takes from the Satchel exactly what it used: no more, no less, and never a piece of gear that moved. */
class SatchelForgeTest {
    private static final ResourceLocation COBBLE = ResourceLocation.withDefaultNamespace("cobblestone");
    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SatchelContents stocked() {
        return SatchelContents.EMPTY.deposit(COBBLE, 1000).contents().deposit(STICK, 8).contents()
                .withGear(2, new ItemStack(Items.IRON_SWORD));
    }

    @Test
    void theViewListsMaterialsThenGearWithTheirFullCounts() {
        SatchelForge.View v = SatchelForge.of(stocked());
        assertEquals(3, v.stacks().size());
        assertEquals(Items.COBBLESTONE, v.stacks().get(0).getItem());
        assertEquals(1000, v.stacks().get(0).getCount(), "a material is one stack of its whole count");
        assertEquals(Items.IRON_SWORD, v.stacks().get(2).getItem());
        assertEquals(java.util.List.of(2), v.gearSlots());
    }

    @Test
    void whatTheForgeShrankIsWhatIsRemoved() {
        SatchelContents before = stocked();
        SatchelForge.View v = SatchelForge.of(before);
        v.stacks().get(0).shrink(300);   // 300 cobblestone used
        v.stacks().get(2).shrink(1);     // the sword used up
        SatchelContents after = SatchelForge.apply(before, v);
        assertEquals(700, after.count(COBBLE));
        assertEquals(8, after.count(STICK), "untouched types are untouched");
        assertTrue(after.gear().get(2).isEmpty());
        assertEquals(before.totalItems() - 301, after.totalItems());
    }

    @Test
    void nothingTakenChangesNothing() {
        SatchelContents before = stocked();
        assertEquals(before, SatchelForge.apply(before, SatchelForge.of(before)));
    }

    @Test
    void gearThatMovedAwayIsNotRemovedTwice() {
        SatchelContents before = stocked();
        SatchelForge.View v = SatchelForge.of(before);
        v.stacks().get(2).shrink(1);
        SatchelContents moved = before.withGear(2, new ItemStack(Items.DIAMOND_SWORD)); // another sword now sits there
        assertEquals(Items.DIAMOND_SWORD, SatchelForge.apply(moved, v).gear().get(2).getItem(), "the different piece stays");
    }

    @Test
    void materialsAlreadyGoneAreNotWithdrawnBelowZero() {
        SatchelContents before = stocked();
        SatchelForge.View v = SatchelForge.of(before);
        v.stacks().get(1).shrink(8);
        SatchelContents emptied = before.withdraw(STICK, 8).contents();
        assertEquals(0, SatchelForge.apply(emptied, v).count(STICK));
    }
}
